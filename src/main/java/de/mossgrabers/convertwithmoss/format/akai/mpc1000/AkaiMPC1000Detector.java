// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai MPC1000 program files. Files must end with <i>.pgm</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC1000Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Map<Integer, FilterType> FILTER_MAP = new HashMap<> ();
    static
    {
        FILTER_MAP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_MAP.put (Integer.valueOf (2), FilterType.BAND_PASS);
        FILTER_MAP.put (Integer.valueOf (3), FilterType.HIGH_PASS);
        // Not sure about this one but there is a LOW PASS 2 in the manual
        FILTER_MAP.put (Integer.valueOf (4), FilterType.LOW_PASS);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiMPC1000Detector (final INotifier notifier)
    {
        super ("Akai MPC500/1000/2500", "MPC1000", notifier, new MetadataSettingsUI ("MPC1000"), ".pgm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final FileInputStream input = new FileInputStream (sourceFile))
        {
            final AkaiMPC1000Program program = new AkaiMPC1000Program (input);
            final String programName = FileUtils.getNameWithoutType (sourceFile).trim ();
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, programName);

            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);
            final IGroup group = new DefaultGroup ();
            multisampleSource.setGroups (Collections.singletonList (group));
            this.createSampleZones (group, program, sourceFile.getParentFile ());

            // Detect metadata
            final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
            tokens[tokens.length - 1] = programName;
            multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

            return Collections.singletonList (multisampleSource);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private void createSampleZones (final IGroup group, final AkaiMPC1000Program program, final File sourceFileParent) throws IOException
    {
        final byte [] midiNotes = program.getMidiNotes ();
        final List<AkaiMPC1000Pad> pads = program.getPads ();
        for (int i = 0; i < pads.size (); i++)
        {
            final AkaiMPC1000Pad pad = pads.get (i);
            for (final AkaiMPC1000Sample sample: pad.getSamples ())
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (pad, sample, sourceFileParent);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setKeyLow (midiNotes[i]);
                    sampleZone.setKeyHigh (midiNotes[i]);
                    group.addSampleZone (sampleZone);
                }
            }
        }
    }


    private Optional<ISampleZone> createSampleZone (final AkaiMPC1000Pad pad, final AkaiMPC1000Sample sample, final File sourceFileParent) throws IOException
    {
        final String sampleName = sample.getName ();
        if (sampleName == null || sampleName.isBlank ())
            return Optional.empty ();

        File sampleFile = new File (sourceFileParent, sampleName + ".wav");
        if (!sampleFile.exists ())
            sampleFile = new File (sourceFileParent, sampleName + ".WAV");
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final WavFileSampleData sampleData = new WavFileSampleData (sampleFile);
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
        sampleData.addZoneData (sampleZone, true, true);

        sampleZone.setVelocityLow (sample.getVelocityRangeLower ());
        sampleZone.setVelocityCrossfadeHigh (sample.getVelocityRangeUpper ());

        // Pitch
        sampleZone.setTuning (sample.getTuning () / 100.0);
        sampleZone.setKeyTracking (0);

        // Mixing
        sampleZone.setGain (MathUtils.valueToDb ((pad.getMixerLevel () + sample.getLevel ()) / 200.0));
        sampleZone.setPanning ((pad.getMixerPan () - 50) / 50.0);

        final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
        final int sampleLength = audioMetadata.getNumberOfSamples ();
        final int sampleLengthAsSeconds = sampleLength / audioMetadata.getSampleRate ();
        final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
        amplitudeEnvelopeModulator.setSource (convertEnvelope (pad, sample.getPlayMode () == 0, sampleLengthAsSeconds));
        amplitudeEnvelopeModulator.setDepth (pad.getVelocityToLevel () / 100.0);

        // Play-back
        sampleZone.setStart (0);
        sampleZone.setStop (sampleLength);

        // Filter

        pad.getFilter1Res ();

        final int filterCutoff = pad.getFilter1Freq ();
        final FilterType filterType = FILTER_MAP.get (Integer.valueOf (pad.getFilter1Type ()));
        IFilter filter = null;
        if (filterType != null && filterCutoff < 100)
        {
            final double cutoff = filterCutoff / 99.0 * IFilter.MAX_FREQUENCY;
            // Poles are unknown, let's go with 4
            filter = new DefaultFilter (filterType, 4, cutoff, 0);

            // There is a filter envelope with +/- intensity in the unknown data

            filter.getCutoffVelocityModulator ().setDepth (pad.getFilter1VelocityToFrequency () / 100.0);
            sampleZone.setFilter (filter);
        }

        return Optional.of (sampleZone);
    }


    private static IEnvelope convertEnvelope (final AkaiMPC1000Pad pad, final boolean isOneShot, final int sampleLength)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (pad.getAttack (), false));
        if (pad.getDecayMode () == 0)
            envelope.setDecayTime (toSeconds (pad.getDecay (), true));
        envelope.setSustainLevel (isOneShot ? 1.0 : 0.0);
        // This is not fully correct but cannot be mapped otherwise...
        if (pad.getDecayMode () == 1)
            envelope.setReleaseTime (toSeconds (pad.getDecay (), false));
        else if (isOneShot)
            envelope.setReleaseTime (sampleLength);
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 100.0 * (isLong ? 6.0 : 2.0);
    }
}
