// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
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
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Converts instances of AkaiProgram to MultiSampleSources.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS1000ProgramConverter
{
    /** The number of voices of an Akai S1000/S3000. */
    private static final int MAX_POLYPHONY = 16;

    private final INotifier  notifier;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public AkaiS1000ProgramConverter (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Convert a program to a multi-sample group.
     *
     * @param program The program to convert
     * @param samples THe referenced samples
     * @return The converted multi-sample group
     */
    public IGroup createGroup (final AkaiS1000Program program, final List<AkaiS1000Sample> samples)
    {
        final IGroup group = new DefaultGroup ();
        this.createSampleZones (group, program.getKeygroups (), samples);

        // Set global values
        final int pitchBendRange = program.getBendToPitch () & 0xFF;
        final double gain = (program.getVolume () & 0xFF) / 99.0;
        final double velocityToVolume = Math.clamp (program.getVelocityToVolume () / 50.0, -1.0, 1.0);
        // The native range of the key to volume intensity is [-50..50] which maps to the model
        // range of [-1..1]
        final double keyToVolume = Math.clamp (program.getKeyToVolume () / 50.0, -1.0, 1.0);
        for (final ISampleZone zone: group.getSampleZones ())
        {
            zone.setBendUp (pitchBendRange);
            zone.setBendDown (-pitchBendRange);
            zone.setGain (gain);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);
            zone.setAmplitudeKeyTracking (keyToVolume);
        }

        return group;
    }


    /**
     * Apply the voice settings of the program which are stored on the level of the whole program
     * and not on the level of a key-group.
     *
     * @param multisampleSource The multi-sample source to which to apply the settings
     * @param program The program from which to read the settings
     */
    public static void applyVoiceSettings (final IMultisampleSource multisampleSource, final AkaiS1000Program program)
    {
        // The program limits itself to 1-16 of the 16 voices of the sampler
        final int polyphony = program.getPolyphony ();
        if (polyphony > 0)
            multisampleSource.setPolyphony (Math.clamp (polyphony, 1, MAX_POLYPHONY));

        // The voice priority (LOW/NORM/HIGH/HOLD) and the voice re-assign method (OLDEST/QUIETEST)
        // only steer the voice stealing and have no representation in the model. There is neither a
        // legato nor a portamento parameter in an Akai S1000/S3000 program.
    }


    private void createSampleZones (final IGroup group, final AkaiS1000Keygroup [] keygroups, final List<AkaiS1000Sample> samples)
    {
        for (final AkaiS1000Keygroup keygroup: keygroups)
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
            IFilter filter = null;
            if (filterCutoff < 99 || cutoffModulation != 0)
            {
                final double cutoff = filterCutoff / 99.0 * IFilter.MAX_FREQUENCY;
                filter = new DefaultFilter (FilterType.LOW_PASS, 3, cutoff, 0);
                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                cutoffEnvelopeModulator.setSource (auxEnvelope);
                cutoffEnvelopeModulator.setDepth (cutoffModulation);

                filter.getCutoffVelocityModulator ().setDepth (keygroup.getVelocityToFilter () / 50.0);
                filter.setCutoffKeyTracking (Math.clamp (keygroup.getKeyToFilter () / 12.0, 0, 1));
            }

            // Pitch modulation
            final double pitchModulation = keygroup.getEnvelope2ToPitch () / 50.0;

            final AkaiS1000KeygroupSample [] keygroupSamples = keygroup.getSamples ();
            for (int i = 0; i < keygroupSamples.length; i++)
            {
                final AkaiS1000KeygroupSample keygroupSample = keygroupSamples[i];

                // Is the layer used?
                final String sampleName = keygroupSample.getName ();
                if (sampleName == null || sampleName.isBlank ())
                    continue;

                final Optional<AkaiS1000Sample> sampleOpt = lookupSample (samples, sampleName);
                if (sampleOpt.isEmpty ())
                {
                    this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
                    continue;
                }

                final AkaiS1000Sample sample = sampleOpt.get ();
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
                // The key-group sample loop mode 0 (AS_SAMPLE) falls back to the one of the
                // sample, all others are the sample loop modes shifted by 1
                final int keygroupSampleLoopMode = keygroupSample.getLoopMode ();
                final int loopMode = keygroupSampleLoopMode == 0 ? sample.getLoopMode () : keygroupSampleLoopMode - 1;
                // PLAY_TO_END ignores a note-off and plays the sample up to its end
                sampleZone.setOneShot (loopMode == AkaiS1000Sample.LOOP_MODE_PLAY_TO_END);
                if (sample.getActiveLoops () > 0 && loopMode < 2)
                {
                    final byte firstActiveLoop = sample.getFirstActiveLoop ();
                    final AkaiS1000SampleLoop loop = sample.getLoops ()[firstActiveLoop];
                    final int marker = loop.getEndMarker ();
                    final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                    sampleLoop.setStart (marker - loop.getCoarseLength ());
                    sampleLoop.setEnd (marker);
                    sampleZone.getLoops ().add (sampleLoop);
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


    private static Optional<AkaiS1000Sample> lookupSample (final List<AkaiS1000Sample> samples, final String sampleName)
    {
        if (sampleName == null)
            return Optional.empty ();
        for (final AkaiS1000Sample sample: samples)
            if (sampleName.equals (sample.getName ()))
                return Optional.of (sample);
        return Optional.empty ();
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


    private static IEnvelope convertEnvelope (final AkaiS1000Envelope akaiEnvelope)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (akaiEnvelope.getAttack (), false));
        envelope.setDecayTime (toSeconds (akaiEnvelope.getDecay (), true));
        envelope.setSustainLevel (akaiEnvelope.getSustain () / 99.0);
        envelope.setReleaseTime (toSeconds (akaiEnvelope.getRelease (), false));

        // The native range of both intensities is [-50..50] which maps to the model range of
        // [-1..1]. The polarity is inverted: the Akai adds the intensity to the envelope time
        // parameters, a positive value therefore lengthens the times towards higher velocities
        // resp. higher keys ("Setting this to a negative value means that the higher the note
        // played on the keyboard, the shorter the decay and release times"), while a positive value
        // of the model shortens them. The model has only one intensity for all times of an
        // envelope, therefore the separate 'velocity to release' and 'off velocity to release'
        // intensities of the Akai cannot be represented.
        envelope.setTimeVelocityTracking (Math.clamp (-akaiEnvelope.getVelocityToAttack () / 50.0, -1, 1));
        envelope.setTimeKeyTracking (Math.clamp (-akaiEnvelope.getKeyToDecayAndRelease () / 50.0, -1, 1));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 99.0 * (isLong ? 6.0 : 2.0);
    }
}
