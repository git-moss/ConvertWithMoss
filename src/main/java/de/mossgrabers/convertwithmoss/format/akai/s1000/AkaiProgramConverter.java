// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.File;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Converts instances of AkaiProgram to MultiSampleSources.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiProgramConverter
{
    private final INotifier       notifier;
    private final IMetadataConfig configuration;


    /**
     * Constructor.
     * 
     * @param notifier Where to report errors
     * @param configuration Configuration for metadata lookup
     */
    public AkaiProgramConverter (final INotifier notifier, final IMetadataConfig configuration)
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
     * @param volumeName The name to prefix, might be null or empty
     * @return The converted multi-sample source
     */
    public DefaultMultisampleSource createMultiSample (final File sourceFile, final String [] parts, final List<AkaiSample> samples, final AkaiProgram program, final String volumeName)
    {
        String programName = program.getName ();
        if (volumeName != null && !volumeName.isBlank ())
            programName = volumeName.trim () + " " + programName;
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, programName, programName);

        final IGroup group = new DefaultGroup ();
        multisampleSource.setGroups (Collections.singletonList (group));
        this.createSampleZones (group, program.getKeygroups (), samples);

        // Set global values
        final int pitchBendRange = program.getBendToPitch () & 0xFF;
        final double gain = (program.getVolume () & 0xFF) / 99.0;
        final double velocityToVolume = Math.clamp (program.getVelocityToVolume () / 50.0, -1.0, 1.0);
        for (final ISampleZone zone: group.getSampleZones ())
        {
            zone.setBendUp (pitchBendRange);
            zone.setBendDown (-pitchBendRange);
            zone.setGain (gain);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);
        }

        // Detect metadata
        final String [] tokens = java.util.Arrays.copyOf (parts, parts.length + 1);
        tokens[tokens.length - 1] = programName;
        multisampleSource.getMetadata ().detectMetadata (this.configuration, tokens);

        return multisampleSource;
    }


    private void createSampleZones (final IGroup group, final AkaiKeygroup [] keygroups, final List<AkaiSample> samples)
    {
        for (final AkaiKeygroup keygroup: keygroups)
        {
            // Each key-group can have up to 4 velocity layers, therefore an individual ISampleZone
            // needs to be created for each layer

            final int lowKey = keygroup.getLowKey ();
            final int highKey = keygroup.getHighKey ();

            final boolean [] sampleKeyTracking = keygroup.getSampleKeyTracking ();
            final double keygroupTuning = calculateTuning (keygroup.getTuneSemitones (), keygroup.getTuneCents ());
            final IEnvelope amplitudeEnvelope = convertEnvelope (keygroup.getAmplitudeEnvelope ());
            final IEnvelope auxEnvelope = convertEnvelope (keygroup.getAuxEnvelope ());

            // Filter
            final int filterCutoff = keygroup.getFilter ();
            final double cutoffModulation = keygroup.getEnvelope2ToFilter () / 50.0;
            final double velocityToFilter = keygroup.getVelocityToFilter () / 50.0;
            IFilter filter = null;
            if (filterCutoff < 99 || cutoffModulation != 0)
            {
                final double cutoff = filterCutoff / 99.0 * IFilter.MAX_FREQUENCY;
                filter = new DefaultFilter (FilterType.LOW_PASS, 3, cutoff, 0);
                filter.getCutoffEnvelopeModulator ().setSource (auxEnvelope);
                filter.getCutoffEnvelopeModulator ().setDepth (cutoffModulation);
                filter.getCutoffVelocityModulator ().setDepth (velocityToFilter);
            }

            // Pitch modulation
            final double pitchModulation = keygroup.getEnvelope2ToPitch () / 50.0;

            final AkaiKeygroupSample [] keygroupSamples = keygroup.getSamples ();
            for (int i = 0; i < keygroupSamples.length; i++)
            {
                final AkaiKeygroupSample keygroupSample = keygroupSamples[i];

                // Is the layer used?
                final String sampleName = keygroupSample.getName ();
                if (sampleName == null || sampleName.isBlank ())
                    continue;

                final AkaiSample sample = lookupSample (samples, sampleName);
                if (sample == null)
                {
                    this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                    continue;
                }

                final ISampleZone sampleZone = new DefaultSampleZone (sampleName, lowKey, highKey);

                final short [] samples16bit = sample.getSamples ();
                if (samples16bit == null)
                {
                    final WavFileSampleData wavFileSampleData = sample.getWavFileSampleData ();
                    if (wavFileSampleData == null)
                    {
                        this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                        continue;
                    }
                    sampleZone.setSampleData (wavFileSampleData);
                }
                else
                {
                    final IAudioMetadata audioMetadata = new DefaultAudioMetadata (1, sample.getSamplingFrequency (), 16, sample.getNumberOfSamples ());
                    sampleZone.setSampleData (new InMemorySampleData (audioMetadata, samples16bit));
                }

                sampleZone.setKeyRoot (sample.getMidiRootNote ());
                sampleZone.setVelocityLow (keygroupSample.getLowVelocity ());
                sampleZone.setVelocityHigh (keygroupSample.getHighVelocity ());

                // Mixing
                sampleZone.setPanning (Math.clamp (keygroupSample.getPan (), -50, 50) / 50.0);
                // Unclear of the +/- range of the loudness parameter, assume +/-6dB
                sampleZone.setGain (Math.clamp (keygroupSample.getLoudness (), -50, 50) / 50.0 * 6.0);
                sampleZone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);

                // Play-back
                sampleZone.setStart (sample.getStartMarker ());
                sampleZone.setStop (sample.getEndMarker ());

                // Loop
                if (sample.getActiveLoops () > 0)
                {
                    int loopMode = keygroupSample.getLoopMode ();
                    loopMode = loopMode == 0 ? sample.getLoopMode () : loopMode - 1;
                    if (loopMode < 2)
                    {
                        final byte firstActiveLoop = sample.getFirstActiveLoop ();
                        if (firstActiveLoop > 0)
                        {
                            final AkaiSampleLoop loop = sample.getLoops ()[firstActiveLoop - 1];
                            final int marker = loop.getEndMarker ();
                            final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                            sampleLoop.setStart (marker - loop.getCoarseLength ());
                            sampleLoop.setEnd (marker);
                            sampleZone.getLoops ().add (sampleLoop);
                        }
                    }
                }

                // Filter
                if (filter != null)
                    sampleZone.setFilter (filter);

                // Pitch
                sampleZone.setKeyTracking (sampleKeyTracking[i] ? 1 : 0);
                final double keygroupSampleTuning = calculateTuning (keygroupSample.getTuneSemitones (), keygroupSample.getTuneCents ());
                final double sampleTuning = calculateTuning (sample.getTuneSemitones (), sample.getTuneCents ());
                sampleZone.setTuning (keygroupTuning + keygroupSampleTuning + sampleTuning);
                if (pitchModulation != 0)
                {
                    final IEnvelopeModulator pitchEnvelopeModulator = sampleZone.getPitchEnvelopeModulator ();
                    pitchEnvelopeModulator.setDepth (pitchModulation);
                    pitchEnvelopeModulator.setSource (auxEnvelope);
                }

                group.addSampleZone (sampleZone);
            }
        }
    }


    private static AkaiSample lookupSample (final List<AkaiSample> samples, final String sampleName)
    {
        if (sampleName == null)
            return null;
        for (final AkaiSample sample: samples)
        {
            if (sampleName.equals (sample.getName ()))
                return sample;
        }
        return null;
    }


    /**
     * Combine the semi-tone and fine tuning.
     * 
     * @param tuneSemitones The semi-tones in the range of [-50..50]
     * @param tuneCents The tuning by in the range of [-128..127], needs to be scaled to [-50..+50]
     * @return The semi-tones with cents as fractions
     */
    private static double calculateTuning (final int tuneSemitones, final int tuneCents)
    {
        double cents = 0;
        if (tuneCents < 0)
            cents = tuneCents / 128.0 * 0.5;
        else if (tuneCents > 0)
            cents = tuneCents / 127.0 * 0.5;
        return tuneSemitones + cents;
    }


    private static IEnvelope convertEnvelope (final AkaiEnvelope akaiEnvelope)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (akaiEnvelope.getAttack (), false));
        envelope.setDecayTime (toSeconds (akaiEnvelope.getDecay (), true));
        envelope.setSustainLevel (akaiEnvelope.getSustain () / 99.0);
        envelope.setReleaseTime (toSeconds (akaiEnvelope.getRelease (), false));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 99.0 * (isLong ? 6.0 : 2.0);
    }
}
