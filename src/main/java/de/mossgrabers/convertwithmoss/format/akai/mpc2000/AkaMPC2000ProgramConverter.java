// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;


/**
 * Converts instances of AkaiMPC2000Program to MultiSampleSources.
 *
 * @author Jürgen Moßgraber
 */
public class AkaMPC2000ProgramConverter
{
    private final INotifier       notifier;
    private final IMetadataConfig configuration;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     * @param configuration Configuration for metadata lookup
     */
    public AkaMPC2000ProgramConverter (final INotifier notifier, final IMetadataConfig configuration)
    {
        this.notifier = notifier;
        this.configuration = configuration;
    }


    /**
     * Convert a program to a multi-sample source.
     *
     * @param sourceFile The source file
     * @param parts The folder parts for metadata lookup
     * @param samples THe referenced samples
     * @param program The program to convert
     * @param programName The name of the program
     * @return The converted multi-sample source
     * @throws IOException Could not read the metadata
     */
    public IMultisampleSource createMultiSample (final File sourceFile, final String [] parts, final AkaiMPC2000Program program, final Map<String, ISampleData> samples, final String programName) throws IOException
    {
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);
        multisampleSource.setGroups (this.createSampleZones (program, samples));

        // Detect metadata
        final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = programName;
        multisampleSource.getMetadata ().detectMetadata (this.configuration, tokens);

        return multisampleSource;
    }


    private List<IGroup> createSampleZones (final AkaiMPC2000Program program, final Map<String, ISampleData> samples) throws IOException
    {
        final IGroup velocityGroup1 = new DefaultGroup ("VelLayer 1");
        final IGroup velocityGroup2 = new DefaultGroup ("VelLayer 2");
        final IGroup velocityGroup3 = new DefaultGroup ("VelLayer 3");
        final List<IGroup> groups = new ArrayList<> ();
        groups.add (velocityGroup1);

        final Map<Integer, ISampleZone> zonesByPadIndex = new HashMap<> ();
        final Map<Integer, ISampleZone> zonesByMidiNote = new HashMap<> ();

        final byte [] midiNotes = program.getMidiNotes ();
        final List<String> sampleNames = program.getSampleNames ();
        final List<AkaiMPC2000Pad> pads = program.getPads ();
        for (int i = 0; i < pads.size (); i++)
        {
            final AkaiMPC2000Pad pad = pads.get (i);
            final int sampleIndex = pad.getSampleNumber ();
            if (sampleIndex == 0xFF)
                continue;
            if (sampleIndex < 0 || sampleIndex >= sampleNames.size ())
            {
                this.notifier.logError ("IDS_MPC2000_MISSING_SAMPLE", Integer.toString (sampleNames.size ()), Integer.toString (sampleIndex));
                continue;
            }

            final String sampleName = sampleNames.get (sampleIndex);
            // Some PGM files contain unknown data in the first sample name, indicated by the 1st
            // byte being 00
            if (sampleName == null || sampleName.isBlank ())
                continue;

            final ISampleData sampleData = samples.get (sampleName);
            if (sampleData == null)
            {
                this.notifier.logError ("IDS_MPC2000_MISSING_SAMPLE_FILE", sampleName);
                continue;
            }

            final Optional<ISampleZone> zoneOpt = createSampleZone (pad, sampleName, sampleData);
            if (zoneOpt.isPresent ())
            {
                final ISampleZone sampleZone = zoneOpt.get ();
                sampleZone.setKeyLow (midiNotes[i]);
                sampleZone.setKeyHigh (midiNotes[i]);
                velocityGroup1.addSampleZone (sampleZone);
                zonesByPadIndex.put (Integer.valueOf (i), sampleZone);
                zonesByMidiNote.put (Integer.valueOf (midiNotes[i]), sampleZone);
            }
        }

        // Second run to create up to 3 velocity layers, 2nd/3rd velocity layers are stored in
        // separate groups
        for (int i = 0; i < pads.size (); i++)
        {
            final ISampleZone sampleZoneVel1 = zonesByPadIndex.get (Integer.valueOf (i));
            if (sampleZoneVel1 == null)
                continue;

            final AkaiMPC2000Pad pad = pads.get (i);
            final int velocitySwitchMode = pad.getVelocitySwitchMode ();
            if (velocitySwitchMode > 0)
            {
                final int note1 = pad.getVelocityNote1 ();
                final ISampleZone sampleZoneDest2 = zonesByMidiNote.get (Integer.valueOf (note1));
                final ISampleZone sampleZoneVel2 = sampleZoneDest2 == null ? null : new DefaultSampleZone (sampleZoneDest2);
                if (sampleZoneVel2 != null)
                    velocityGroup2.addSampleZone (sampleZoneVel2);
                final int note2 = pad.getVelocityNote2 ();
                final ISampleZone sampleZoneDest3 = zonesByMidiNote.get (Integer.valueOf (note2));
                final ISampleZone sampleZoneVel3 = sampleZoneDest3 == null ? null : new DefaultSampleZone (sampleZoneDest3);
                if (sampleZoneVel3 != null)
                    velocityGroup3.addSampleZone (sampleZoneVel3);

                // velocitySwitchMode == 1 -> all samples play simultaneously

                // For Decay Switching (=3) this is not correct but does a similar job...
                if (velocitySwitchMode == 2 || velocitySwitchMode == 3)
                {
                    if (sampleZoneVel2 != null)
                    {
                        sampleZoneVel1.setVelocityHigh (Math.max (pad.getVelocitySwitch1 () - 1, 0));
                        sampleZoneVel2.setVelocityLow (pad.getVelocitySwitch1 ());
                    }
                    if (sampleZoneVel3 != null)
                    {
                        if (sampleZoneVel2 != null)
                            sampleZoneVel2.setVelocityHigh (Math.max (pad.getVelocitySwitch2 () - 1, 0));
                        sampleZoneVel3.setVelocityLow (pad.getVelocitySwitch2 ());
                        sampleZoneVel3.setVelocityHigh (127);
                    }
                }
            }
        }

        if (!velocityGroup2.getSampleZones ().isEmpty ())
            groups.add (velocityGroup2);
        if (!velocityGroup3.getSampleZones ().isEmpty ())
            groups.add (velocityGroup3);
        return groups;
    }


    private static Optional<ISampleZone> createSampleZone (final AkaiMPC2000Pad pad, final String sampleName, final ISampleData sampleData) throws IOException
    {
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
        sampleData.addZoneData (sampleZone, true, true);

        // Pitch
        sampleZone.setTuning (pad.getTuning () / 100.0);
        sampleZone.setKeyTracking (0);

        // Mixing
        sampleZone.setGain (MathUtils.valueToDb ((pad.getMixerLevel () + pad.getLevel ()) / 200.0));
        sampleZone.setPanning ((pad.getMixerPan () - 50) / 50.0);

        final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
        final int sampleLength = audioMetadata.getNumberOfSamples ();
        final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
        amplitudeEnvelopeModulator.setSource (convertEnvelope (pad));
        amplitudeEnvelopeModulator.setDepth (pad.getVelocityToLevel () / 100.0);

        // Play-back
        sampleZone.setStart (0);
        sampleZone.setStop (sampleLength);

        // Filter
        final int filterCutoff = pad.getFilterFreq ();
        IFilter filter = null;
        if (filterCutoff < 100)
        {
            final double cutoff = filterCutoff / 99.0 * IFilter.MAX_FREQUENCY;
            // Poles are unknown, let's go with 4
            filter = new DefaultFilter (FilterType.LOW_PASS, 4, cutoff, pad.getFilterRes () / 100.0);

            // There is a filter envelope with +/- intensity in the unknown data

            filter.getCutoffVelocityModulator ().setDepth (pad.getFilterVelocityToFrequency () / 100.0);
            sampleZone.setFilter (filter);
        }

        return Optional.of (sampleZone);
    }


    private static IEnvelope convertEnvelope (final AkaiMPC2000Pad pad)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (pad.getAttack (), false));
        if (pad.getDecayMode () == 0)
            envelope.setDecayTime (toSeconds (pad.getDecay (), true));
        envelope.setSustainLevel (0);
        // This is not fully correct but cannot be mapped otherwise...
        if (pad.getDecayMode () == 1)
            envelope.setReleaseTime (toSeconds (pad.getDecay (), false));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 100.0 * (isLong ? 6.0 : 2.0);
    }
}
