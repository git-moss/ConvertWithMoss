// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.akai.s900.AkaiS900Keygroup.KeygroupLayer;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Detects Akai S900/S950 image files. Files must end with <i>.img</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Detector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiS900Detector (final INotifier notifier)
    {
        super ("Akai S900/S950", "S900", notifier, new MetadataSettingsUI ("S900"), ".img", ".akai");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final AkaiS900DiskImage image = new AkaiS900DiskImage (sourceFile);
            final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, sourceFile.getName ());
            for (final AkaiS900Program program: image.getPrograms ())
            {
                final String programName = program.getName ();
                final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);
                final IGroup group = new DefaultGroup ();
                multisampleSource.setGroups (Collections.singletonList (group));
                this.createSampleZones (group, program, image.getSamples ());

                // Detect metadata
                final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
                tokens[tokens.length - 1] = programName;
                multisampleSource.getMetadata ().detectMetadata (this.settingsConfiguration, tokens);

                multiSampleSources.add (multisampleSource);
            }

            return multiSampleSources;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private void createSampleZones (final IGroup group, final AkaiS900Program program, final Map<String, AkaiS900Sample> samples) throws IOException
    {
        for (final AkaiS900Keygroup keygroup: program.getKeygroups ())
        {
            // Each key-group has 1 or 2 velocity layers (soft and loud) depending on the velocity
            // split value

            final int velocitySwitchValue = keygroup.getVelocitySwitchValue ();
            final KeygroupLayer [] velocityLayers = keygroup.getVelocityLayers ();
            // Soft velocity layer
            if (velocitySwitchValue > 0)
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (keygroup, velocityLayers[0], samples);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setVelocityLow (0);
                    sampleZone.setVelocityHigh (Math.min (velocitySwitchValue, 127));
                    group.addSampleZone (sampleZone);
                }
            }
            // Loud velocity layer
            if (velocitySwitchValue < 128)
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (keygroup, velocityLayers[1], samples);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setVelocityLow (Math.min (velocitySwitchValue + 1, 127));
                    sampleZone.setVelocityHigh (127);
                    group.addSampleZone (sampleZone);
                }
            }
        }

        if (program.isKeygroupCrossfadeEnable ())
            calculateCrossfades (group.getSampleZones ());
    }


    private Optional<ISampleZone> createSampleZone (final AkaiS900Keygroup keygroup, final KeygroupLayer velocityLayer, final Map<String, AkaiS900Sample> samples) throws IOException
    {
        final String sampleName = velocityLayer.getSample ();
        final AkaiS900Sample sample = samples.get (sampleName);
        if (sample == null)
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final WaveFile waveFile = AkaiS900DiskImage.writeSample (sample);
        if (waveFile == null)
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final int lowKey = keygroup.getKeyLow ();
        final int highKey = keygroup.getKeyHigh ();
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, lowKey, highKey);
        sampleZone.setSampleData (new WavFileSampleData (waveFile));

        sampleZone.setReversed (sample.getDirection () == 'R');

        // Pitch
        final double nominalPitch = sample.getNominalPitch () / 16.0;
        final int rootNote = (int) Math.round (nominalPitch);
        sampleZone.setKeyRoot (rootNote);
        sampleZone.setTuning (nominalPitch - rootNote);
        sampleZone.setKeyTracking ((keygroup.getFlags () & 0x01) > 0 ? 0 : 1.0);

        // Mixing

        // There is no panning only a hard assignment to left/right channel otherwise it is mono
        final int outputChannel = keygroup.getOutputChannel ();
        if (outputChannel == 0x08)
            sampleZone.setPanning (-1.0);
        else if (outputChannel == 0x09)
            sampleZone.setPanning (1.0);

        // Unclear of the +/- range of the loudness parameter, assume +/-6dB
        sampleZone.setGain (Math.clamp (sample.getLoudness (), -50, 50) / 50.0 * 6.0);
        sampleZone.getAmplitudeEnvelopeModulator ().setSource (convertEnvelope (keygroup));

        // Play-back
        sampleZone.setStart ((int) sample.getStart ());
        sampleZone.setStop ((int) sample.getEnd ());

        // Loop
        if (sample.getLoopLength () > 0)
        {
            final char loopMode = sample.getPlaybackMode ();
            if (loopMode == 'L' || loopMode == 'A')
            {
                final long marker = sample.getEnd ();
                final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                sampleLoop.setStart ((int) (marker - sample.getLoopLength ()));
                sampleLoop.setEnd ((int) marker);
                sampleZone.getLoops ().add (sampleLoop);
            }
        }

        // Filter
        final int filterCutoff = velocityLayer.getFilter ();
        IFilter filter = null;
        if (filterCutoff < 99)
        {
            final double cutoff = filterCutoff / 99.0 * IFilter.MAX_FREQUENCY;
            filter = new DefaultFilter (FilterType.LOW_PASS, 3, cutoff, 0);
            sampleZone.setFilter (filter);
        }

        // Velocity-Modulations available for but unknown which of the unknown values are which:
        // LOUDNESS, FILTER, ATTACK, RELEASE (0-99)

        return Optional.of (sampleZone);
    }


    private static IEnvelope convertEnvelope (final AkaiS900Keygroup keygroup)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (keygroup.getAttack (), false));
        envelope.setDecayTime (toSeconds (keygroup.getDecay (), true));
        envelope.setSustainLevel (keygroup.getSustain () / 99.0);
        envelope.setReleaseTime (toSeconds (keygroup.getRelease (), false));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 99.0 * (isLong ? 6.0 : 2.0);
    }


    /**
     * Calculate the overlap between key-groups and set them as cross-fades.
     * 
     * @param zones The zones to update to cross-fades
     */
    private static void calculateCrossfades (final List<ISampleZone> zones)
    {
        for (int i = 0; i < zones.size (); i++)
        {
            final ISampleZone currentZone = zones.get (i);

            // Calculate lower cross-fade (overlap with previous DIFFERENT zone)
            int lowerCrossfade = 0;
            for (int j = i - 1; j >= 0; j--)
            {
                final ISampleZone prevZone = zones.get (j);
                if (prevZone.getKeyLow () != currentZone.getKeyLow () || prevZone.getKeyHigh () != currentZone.getKeyHigh ())
                {
                    // Found a different zone - calculate overlap
                    if (prevZone.getKeyHigh () >= currentZone.getKeyLow ())
                        lowerCrossfade = Math.min (prevZone.getKeyHigh (), currentZone.getKeyHigh ()) - currentZone.getKeyLow () + 1;
                    break;
                }
            }
            currentZone.setNoteCrossfadeLow (lowerCrossfade);

            // Calculate upper cross-fade (overlap with next DIFFERENT zone)
            int upperCrossfade = 0;
            for (int j = i + 1; j < zones.size (); j++)
            {
                final ISampleZone nextZone = zones.get (j);
                if (nextZone.getKeyLow () != currentZone.getKeyLow () || nextZone.getKeyHigh () != currentZone.getKeyHigh ())
                {
                    // Found a different zone - calculate overlap
                    if (currentZone.getKeyHigh () >= nextZone.getKeyLow ())
                        upperCrossfade = currentZone.getKeyHigh () - Math.max (nextZone.getKeyLow (), currentZone.getKeyLow ()) + 1;
                    break;
                }
            }
            currentZone.setNoteCrossfadeHigh (upperCrossfade);
        }
    }
}
