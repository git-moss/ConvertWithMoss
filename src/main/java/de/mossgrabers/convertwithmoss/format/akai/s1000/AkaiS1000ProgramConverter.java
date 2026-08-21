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
    private static final int     MAX_POLYPHONY       = 16;

    /**
     * The sampler scales every parameter which it displays in the range of [0..99] - and every
     * signed one in the range of [-50..50] - to the [0..255] resp. [-128..127] range of its sound
     * hardware with this table, which its operating system applies with a single table look-up.
     * This is what turns a filter or loudness setting into the value the hardware receives.
     */
    private static final int []  PARAMETER_SCALE     =
    {
        0, 3, 5, 8, 10, 13, 15, 18, 20, 23,
        26, 28, 31, 33, 36, 38, 41, 43, 46, 48,
        51, 54, 56, 59, 61, 64, 66, 69, 71, 74,
        76, 79, 82, 84, 87, 89, 92, 94, 97, 99,
        102, 105, 107, 110, 112, 115, 117, 120, 122, 125,
        127, 130, 133, 135, 138, 140, 143, 145, 148, 150,
        153, 156, 158, 161, 163, 166, 168, 171, 173, 176,
        178, 181, 184, 186, 189, 191, 194, 196, 199, 201,
        204, 207, 209, 212, 214, 217, 219, 222, 224, 227,
        229, 232, 235, 237, 240, 242, 245, 247, 250, 252
    };

    /**
     * The cut-off frequency in Hertz which each of the 100 filter settings produces, one entry per
     * setting. The sampler scales the setting with {@link #PARAMETER_SCALE} and looks the
     * coefficient of its low-pass up with the result; the frequencies here are the -3dB points
     * which those coefficients give for its three one-pole stages in series at 44.1 kHz. The
     * filter is therefore far from a straight line over its range: it only leaves the audible band
     * in the upper third, while the middle of the range - where sample libraries place their
     * evolving layers - sits between 300 Hz and 1 kHz.
     */
    private static final int []  FILTER_CUTOFF       =
    {
        13, 14, 15, 16, 17, 19, 20, 22, 23, 25,
        27, 29, 31, 33, 36, 38, 42, 44, 48, 51,
        56, 61, 64, 70, 74, 81, 86, 94, 99, 108,
        115, 125, 137, 145, 158, 167, 182, 193, 210, 223,
        243, 265, 280, 306, 324, 353, 374, 408, 432, 470,
        498, 543, 592, 627, 683, 723, 788, 834, 909, 962,
        1048, 1142, 1208, 1316, 1393, 1516, 1605, 1747, 1848, 2011,
        2127, 2314, 2517, 2661, 2893, 3057, 3321, 3508, 3807, 4019,
        4356, 4715, 4968, 5364, 5640, 6067, 6359, 6802, 7096, 7530,
        7808, 8199, 8555, 8774, 9088, 9303, 9696, 10064, 11005, 12275
    };

    /**
     * The rate at which the sound hardware moves an envelope towards its next level, one entry per
     * setting of an envelope stage. The sampler looks the rate up with <code>99 - setting</code>,
     * so a setting of 0 gives the fastest rate and 99 the slowest, and it uses this one table for
     * the attack, the decay and the release of both of its envelopes alike. The rates span a range
     * of 16384 to 1, which is why the times are anything but a straight line over the range of a
     * setting.
     */
    private static final int []  ENVELOPE_RATE       =
    {
        2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
        5, 6, 6, 7, 8, 9, 10, 11, 12, 13,
        14, 16, 17, 19, 21, 23, 26, 28, 31, 34,
        38, 42, 46, 51, 56, 62, 68, 75, 83, 91,
        101, 111, 123, 135, 149, 165, 182, 200, 221, 244,
        269, 297, 327, 361, 398, 439, 484, 534, 589, 650,
        716, 790, 872, 961, 1060, 1170, 1290, 1423, 1570, 1731,
        1909, 2106, 2323, 2562, 2826, 3117, 3438, 3792, 4183, 4614,
        5089, 5613, 6191, 6828, 7532, 8307, 9163, 10106, 11147, 12295,
        13562, 14958, 16499, 18198, 20072, 22139, 24419, 26934, 29707, 32767
    };

    /**
     * The distance which an envelope covers between silence and the full level, in the units in
     * which {@link #ENVELOPE_RATE} advances it. The hardware adds the rate to its envelope
     * accumulator once per sample at 44.1 kHz; the width of that accumulator is the one part of
     * the law which the operating system does not state, it is taken from the emulation of the
     * sound hardware. The ratios between the settings do not depend on it.
     */
    private static final double  ENVELOPE_FULL_SWING = 32767.0 * 128.0;

    /** The rate at which the sound hardware advances an envelope. */
    private static final double  ENVELOPE_RATE_HZ    = 44100.0;

    /**
     * One step of the scaled parameter range on the loudness scale of the sound hardware, which
     * spans 60.5 dB over its 255 steps.
     */
    private static final double  DECIBEL_PER_STEP    = 0.2372;

    /** The scaled value which the loudness of the sound hardware plays at its full level. */
    private static final int     FULL_LEVEL          = 255;

    private final INotifier      notifier;


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
        final double gain = scaledLoudnessToDecibel (program.getVolume () & 0xFF) - FULL_LEVEL * DECIBEL_PER_STEP;
        final double velocityToVolume = Math.clamp (program.getVelocityToVolume () / 50.0, -1.0, 1.0);
        // The native range of the key to volume intensity is [-50..50] which maps to the model
        // range of [-1..1]
        final double keyToVolume = Math.clamp (program.getKeyToVolume () / 50.0, -1.0, 1.0);
        for (final ISampleZone zone: group.getSampleZones ())
        {
            zone.setBendUp (pitchBendRange * 100);
            zone.setBendDown (-pitchBendRange * 100);
            zone.setGain (zone.getGain () + gain);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToVolume);
            zone.setAmplitudeKeyTracking (keyToVolume);
        }

        return group;
    }


    /**
     * Apply the voice settings of the programs which are stored on the level of the whole program
     * and not on the level of a key-group.
     *
     * @param multisampleSource The multi-sample source to which to apply the settings
     * @param programs The programs from which to read the settings, several if they play layered
     */
    public static void applyVoiceSettings (final IMultisampleSource multisampleSource, final List<AkaiS1000Program> programs)
    {
        // Each program limits itself to 1-16 of the 16 voices of the sampler. A note occupies a
        // voice in every layered program, therefore the smallest limit wins
        int polyphony = 0;
        for (final AkaiS1000Program program: programs)
        {
            final int programPolyphony = program.getPolyphony ();
            if (programPolyphony > 0)
                polyphony = polyphony == 0 ? programPolyphony : Math.min (polyphony, programPolyphony);
        }
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
            applyLoudnessCurve (amplitudeEnvelope);
            final IEnvelope auxEnvelope = convertEnvelope (keygroup.getAuxEnvelope ());

            // Filter
            final int filterCutoff = keygroup.getFilter ();
            final double cutoffModulation = keygroup.getEnvelope2ToFilter () / 50.0;
            IFilter filter = null;
            if (filterCutoff < 99 || cutoffModulation != 0)
            {
                final double cutoff = FILTER_CUTOFF[Math.clamp (filterCutoff, 0, 99)];
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

                // A zone with an upper velocity of 0 can never be triggered since a velocity of 0
                // is a note-off. CD-ROM manufacturers use such zones to mute the layers which
                // belong to a partner program of a layered stack and to add their copyright info
                if (keygroupSample.getHighVelocity () == 0)
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
                sampleZone.setGain (scaledLoudnessToDecibel (keygroupSample.getLoudness ()));
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
                // The sample header stores literal cents in contrast to the fixed point values
                final double sampleTuning = sample.getTuneSemitones () + sample.getTuneCents () / 100.0;
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
     * Combine the semi-tone and fine tuning of a fixed point tune value. The keygroup and
     * keygroup-sample tunings are 16 bit fixed point values: a signed semi-tone byte and an
     * unsigned fraction byte in 1/256 semi-tone steps.
     *
     * @param tuneSemitones The semi-tones in the range of [-50..50]
     * @param tuneFraction The fraction of a semi-tone in 1/256 steps
     * @return The semi-tones with the fraction
     */
    private static double calculateTuning (final int tuneSemitones, final int tuneFraction)
    {
        return tuneSemitones + (tuneFraction & 0xFF) / 256.0;
    }


    /**
     * Convert a loudness parameter of the sampler into decibel. The sampler scales the parameter to
     * the range of its sound hardware and adds all of them up - the loudness of the program, the
     * offset of the key-group sample, the velocity and the key - to one index into its loudness
     * scale, which is why an offset converts to decibel with the same law as an absolute value.
     *
     * @param loudness The loudness in the range of [0..99] for an absolute value resp. [-50..50]
     *            for an offset
     * @return The loudness in decibel, relative to the full level for an absolute value
     */
    private static double scaledLoudnessToDecibel (final int loudness)
    {
        final int magnitude = PARAMETER_SCALE[Math.clamp (Math.abs (loudness), 0, 99)];
        return (loudness < 0 ? -magnitude : magnitude) * DECIBEL_PER_STEP;
    }


    /**
     * Convert the sustain of an envelope into the level at which it holds a note. The sustain is
     * not a part of the level but an attenuation on the loudness scale of the sound hardware: the
     * operating system scales the parameter with the same table as every other loudness and stores
     * the remainder to the full level as the attenuation of the voice, which the hardware subtracts
     * from the index into its gain table while a note is held. A sustain of 50 therefore does not
     * hold a note at half of its level but 30.4 dB below it, which is 3 % - read as half the level
     * such a note sits about 24 dB too loud, and the difference a sound designer set between the
     * layers of a preset disappears.
     *
     * @param sustain The sustain in the range of [0..99]
     * @return The level in the range of [0..1]
     */
    private static double scaledSustainToLevel (final int sustain)
    {
        final int scaledSustain = PARAMETER_SCALE[Math.clamp (sustain, 0, 99)];
        if (scaledSustain <= 0)
            return 0;
        return Math.pow (10, (scaledSustain - FULL_LEVEL) * DECIBEL_PER_STEP / 20.0);
    }


    /**
     * Shape the envelope of the loudness the way the sound hardware moves it. The level of a voice
     * is an index into the gain table of the sampler, which is exponential with a step of 0.237 dB,
     * and the operating system adds the rate of the envelope to that index once per tick - so a
     * stage of the envelope is a straight line in decibel and therefore a curve in loudness. Since
     * the destination formats describe a stage in loudness, a stage which is written as a straight
     * line keeps a note near its full level for most of its length and then drops it: an electric
     * piano with a decay of 9.5 seconds does not sound like one at all.
     *
     * The envelope of the filter is deliberately left as it is: its index addresses the cut-off
     * table, which is exponential in frequency, so a straight line there already is a straight line
     * in the cut-off of the destination.
     *
     * @param envelope The envelope of the loudness
     */
    private static void applyLoudnessCurve (final IEnvelope envelope)
    {
        // The loudness rises the slower the quieter it is, which is the exponential shape of the
        // model, and falls the faster the louder it is, which is its logarithmic one
        envelope.setAttackSlope (1);
        envelope.setDecaySlope (-1);
        envelope.setReleaseSlope (-1);
    }


    private static IEnvelope convertEnvelope (final AkaiS1000Envelope akaiEnvelope)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (akaiEnvelope.getAttack ()));
        envelope.setDecayTime (toSeconds (akaiEnvelope.getDecay ()));
        envelope.setSustainLevel (scaledSustainToLevel (akaiEnvelope.getSustain ()));
        envelope.setReleaseTime (toSeconds (akaiEnvelope.getRelease ()));

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


    /**
     * Convert a setting of an envelope stage into the time which that stage takes.
     *
     * @param value The setting in the range of [0..99], 0 being the fastest
     * @return The time in seconds
     */
    private static double toSeconds (final int value)
    {
        return ENVELOPE_FULL_SWING / ENVELOPE_RATE[99 - Math.clamp (value, 0, 99)] / ENVELOPE_RATE_HZ;
    }
}
