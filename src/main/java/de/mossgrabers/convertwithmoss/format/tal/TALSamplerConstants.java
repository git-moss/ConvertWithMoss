// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;


/**
 * Some constants for the TAL Sampler.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerConstants
{
    /**
     * The volume of a sample which TAL-Sampler plays at 0dB. The volume of a sample is a linear
     * amplitude factor relative to it: rendered offline (version 4.7.2), the volumes 0.1, 0.353,
     * 0.5 and 1.0 play a sample at -17.9, -6.9, -3.9 and +2.1dB, which is 20 log (volume / 0.7843)
     * within 0.01dB.
     */
    public static final double       ZONE_VOLUME_0_DB  = 0.7843;

    /** The current file format version to set. */
    public static final String       CURRENT_VERSION   = "9";

    /**
     * The longest stage of an envelope in seconds, which the value 1 of an attack, hold or release
     * selects. The times of the envelopes of TAL-Sampler do not depend on the length of the
     * samples: rendered offline (version 4.7.2) with the values 0.4, 0.6, 0.8 and 1.0 of the
     * amplitude release, a note fades out in 0.19, 0.93, 2.95 and 7.2 seconds, which is 7.2 x
     * value^4 within the accuracy of the measurement, and the attack follows the same law; the hold
     * is taken to follow it as well. The decay has a law of its own, see
     * {@link #DECAY_TIME_FACTOR}.
     */
    public static final double       MAX_ENVELOPE_TIME = 7.2;
    /**
     * The decay of the plug-in approaches the sustain level exponentially and much slower than the
     * other stages. Rendered offline (version 4.7.2) with a sustain of 0, the decay values 0.3,
     * 0.4, 0.5, 0.6, 0.7 and 0.8 reach -6 dB after 0.11, 0.35, 0.70, 1.35, 2.70 and 4.56 seconds
     * and every further 6 dB after the same time again; towards a sustain level of 0.5 the decay
     * has the same time constant. The model expects the time of a linear decay, which is how the
     * destinations play it: the linear decay which reaches -20 dB at the same moment as the decay
     * of the plug-in lasts 37.1 x value^3.75 seconds, within 9 % of these measurements.
     */
    public static final double       DECAY_TIME_FACTOR = 37.1;
    private static final double      DECAY_EXPONENT    = 3.75;

    /** The modulation range of the master tune in cents: the amount is -12..12 semitones. */
    public static final double       TUNE_RANGE_CENTS  = 1200.0;
    /**
     * The modulation range of the cutoff in cents: the normalized cutoff covers 140 semitones, see
     * {@link MathUtils#denormalizeCutoff(double)}.
     */
    public static final double       CUTOFF_RANGE      = 14000.0;
    /** The highest rate of an LFO in Hertz. */
    private static final double      LFO_MAX_RATE      = 200.0;
    /** The smallest volume of a tremolo to avoid an infinite dB value. */
    private static final double      MIN_VOLUME        = 0.001;

    /**
     * The maximum number of voices which can be played. Like all other parameters of the format,
     * the number of voices is not stored as a plain count but normalized into the range of [0..1].
     * It selects one of 12 discrete steps, therefore the normalized value is
     * <code>(voices - 1) / 11</code>. This was confirmed with the factory presets, which only
     * contain the values 0.0 (1 voice, used by the monophonic bass and lead presets), 1/11, 4/11,
     * 5/11 and 7/11 (8 voices, which is the most frequently used value).
     */
    public static final int          MAX_VOICES        = 12;
    /** The IDs to for the 4 layers. */
    protected static final String [] LAYERS            =
    {
        "a",
        "b",
        "c",
        "d"
    };

    private static final double      INDEX_OFFSET      = 0.0833333333333333;
    private static final int []      LOW_PASS_POLES    =
    {
        4,
        2,
        1,
        4,
        3,
        2,
        1
    };


    /**
     * Convert a number of voices into the normalized value of the number of voices parameter.
     *
     * @param voices The number of voices
     * @return The normalized value in the range of [0..1]
     */
    public static double normalizeVoices (final int voices)
    {
        return (Math.clamp (voices, 1, MAX_VOICES) - 1) / (double) (MAX_VOICES - 1);
    }


    /**
     * Convert the normalized value of the number of voices parameter into a number of voices.
     *
     * @param normalizedVoices The normalized value in the range of [0..1]
     * @return The number of voices in the range of [1..12]
     */
    public static int denormalizeVoices (final double normalizedVoices)
    {
        return (int) Math.round (Math.clamp (normalizedVoices, 0.0, 1.0) * (MAX_VOICES - 1)) + 1;
    }


    /**
     * Get the matching filter mode value for the FilterType and number of poles.
     *
     * @param filter The for which to get the matching double value
     * @return The value between 0.0 and 1.0, since this setting is (strangely) normalized as well
     */
    public static double getFilterValue (final IFilter filter)
    {
        switch (filter.getType ())
        {
            default:
            case LOW_PASS:
                switch (filter.getPoles ())
                {
                    case 1:
                        return 2 * INDEX_OFFSET;
                    case 2:
                        return 1 * INDEX_OFFSET;
                    default:
                    case 4:
                        return 0 * INDEX_OFFSET;
                }

            case HIGH_PASS:
                switch (filter.getPoles ())
                {
                    default:
                    case 2:
                        return 7 * INDEX_OFFSET;
                    case 3:
                        return 8 * INDEX_OFFSET;
                }

            case BAND_PASS:
                return 9 * INDEX_OFFSET;

            case BAND_REJECTION:
                return 10 * INDEX_OFFSET;
        }
    }


    /**
     * Gets a new filter instance which has the type and poles set depending on the given value.
     * <ul>
     * <li>LP 4P : 0.0
     * <li>LP 2P : 0.0833333358168602
     * <li>LP 1P : ...
     * <li>LP 4PN :
     * <li>LP 3PN :
     * <li>LP 2PN :
     * <li>LP 1PN :
     * <li>HP 2PN :
     * <li>HP 3PN :
     * <li>BP 4PN :
     * <li>Notch 2P:
     * <li>All Pass:
     * <li>BW 6P : 1.0
     * </ul>
     *
     * @param value The filter type value
     * @return The filter on which to set the values
     */
    public static Optional<IFilter> getFilterType (final double value)
    {
        final int filterIndex = Math.clamp ((int) Math.round (value / INDEX_OFFSET), 0, 12);
        switch (filterIndex)
        {
            case 0, 1, 2, 3, 4, 5, 6:
                return Optional.of (new DefaultFilter (FilterType.LOW_PASS, LOW_PASS_POLES[filterIndex], 0, 0));

            case 7, 8:
                return Optional.of (new DefaultFilter (FilterType.HIGH_PASS, filterIndex == 7 ? 2 : 3, 0, 0));

            case 9:
                return Optional.of (new DefaultFilter (FilterType.BAND_PASS, 4, 0, 0));

            case 10:
                return Optional.of (new DefaultFilter (FilterType.BAND_REJECTION, 2, 0, 0));

            default:
                return Optional.empty ();
        }
    }


    /**
     * Convert the normalized value of an envelope stage into seconds: {@link #MAX_ENVELOPE_TIME} x
     * value^4, the law of the plug-in.
     *
     * @param normalizedTime The value of the stage in the range of [0..1]
     * @return The time in seconds
     */
    public static double denormalizeEnvelopeTime (final double normalizedTime)
    {
        final double value = Math.clamp (normalizedTime, 0.0, 1.0);
        return MAX_ENVELOPE_TIME * value * value * value * value;
    }


    /**
     * Convert the time of an envelope stage into the normalized value of the stage, the inverse of
     * {@link #denormalizeEnvelopeTime(double)}. A time beyond the longest stage is written as the
     * longest stage.
     *
     * @param seconds The time in seconds
     * @return The value of the stage in the range of [0..1]
     */
    public static double normalizeEnvelopeTime (final double seconds)
    {
        return Math.clamp (Math.pow (Math.max (0, seconds) / MAX_ENVELOPE_TIME, 0.25), 0.0, 1.0);
    }


    /**
     * Convert the normalized value of a decay into seconds: {@link #DECAY_TIME_FACTOR} x
     * value^3.75, the law of the decay of the plug-in.
     *
     * @param normalizedTime The value of the decay in the range of [0..1]
     * @return The time in seconds
     */
    public static double denormalizeDecayTime (final double normalizedTime)
    {
        return DECAY_TIME_FACTOR * Math.pow (Math.clamp (normalizedTime, 0.0, 1.0), DECAY_EXPONENT);
    }


    /**
     * Convert the time of a decay into the normalized value of the decay, the inverse of
     * {@link #denormalizeDecayTime(double)}. A time beyond the longest decay is written as the
     * longest decay.
     *
     * @param seconds The time in seconds
     * @return The value of the decay in the range of [0..1]
     */
    public static double normalizeDecayTime (final double seconds)
    {
        return Math.clamp (Math.pow (Math.max (0, seconds) / DECAY_TIME_FACTOR, 1.0 / DECAY_EXPONENT), 0.0, 1.0);
    }


    /**
     * Convert the volume of a sample into dB: 20 log (volume / {@link #ZONE_VOLUME_0_DB}), the
     * largest volume 1.0 is +2.1dB.
     *
     * @param volume The volume of the sample in the range of [0..1]
     * @return The volume in dB
     */
    public static double zoneVolumeToDb (final double volume)
    {
        return MathUtils.valueToDb (volume / ZONE_VOLUME_0_DB);
    }


    /**
     * Convert a volume in dB into the volume of a sample, the inverse of
     * {@link #zoneVolumeToDb(double)}. A volume above +2.1dB is written as the largest volume.
     *
     * @param volumeDB The volume in dB
     * @return The volume of the sample in the range of [0..1]
     */
    public static double dbToZoneVolume (final double volumeDB)
    {
        return Math.clamp (ZONE_VOLUME_0_DB * Math.pow (10.0, volumeDB / 20.0), 0.0, 1.0);
    }


    /**
     * Convert the main volume or the volume of a layer into dB. TAL-Sampler shows both as 40 log (2
     * x) dB: 0.5 is 0dB, 1.0 is +12dB, which rendering the main volumes 0.25, 0.75 and 1.0 offline
     * confirms.
     *
     * @param volume The volume in the range of [0..1], larger than 0
     * @return The volume in dB
     */
    public static double volumeToDb (final double volume)
    {
        return 40.0 * Math.log10 (2.0 * volume);
    }


    /**
     * Convert a volume in dB into the main volume or the volume of a layer, the inverse of
     * {@link #volumeToDb(double)}. A volume above +12dB is written as the largest volume.
     *
     * @param volumeDB The volume in dB
     * @return The volume in the range of [0..1]
     */
    public static double dbToVolume (final double volumeDB)
    {
        return Math.clamp (Math.pow (10.0, volumeDB / 40.0) / 2.0, 0.0, 1.0);
    }


    /**
     * Convert the LFO rate attribute to Hertz. The plug-in displays the rate as 200 x^3 Hz: 0.3 is
     * 5.40Hz, 0.7216 is 75.14Hz and 1.0 is 200Hz.
     *
     * @param value The rate attribute in the range of [0..1]
     * @return The rate in Hertz
     */
    public static double lfoRateToHertz (final double value)
    {
        final double rate = Math.clamp (value, 0.0, 1.0);
        return LFO_MAX_RATE * rate * rate * rate;
    }


    /**
     * Inverse of {@link #lfoRateToHertz(double)}.
     *
     * @param hertz The rate in Hertz
     * @return The rate attribute in the range of [0..1]
     */
    public static double hertzToLfoRate (final double hertz)
    {
        return Math.clamp (Math.cbrt (hertz / LFO_MAX_RATE), 0.0, 1.0);
    }


    /**
     * Get the LFO wave form from the wave form attribute. The plug-in offers Triangle, Saw,
     * Rectangle, S&amp;H Random, Noise and Sine. Since version 9 the attribute is the index divided
     * by 5, the presets of version 1.5 store the index divided by 2 (with the first 5 wave forms
     * only).
     *
     * @param value The wave form attribute
     * @param isOldVersion True if the preset was written by a version before 2
     * @return The wave form
     */
    public static LfoWaveform getLfoWaveform (final double value, final boolean isOldVersion)
    {
        final int index = (int) Math.round (value * (isOldVersion ? 2.0 : 5.0));
        return switch (index)
        {
            case 1 -> LfoWaveform.SAWTOOTH_UP;
            case 2 -> LfoWaveform.SQUARE;
            case 3, 4 -> LfoWaveform.RANDOM;
            case 5 -> LfoWaveform.SINE;
            default -> LfoWaveform.TRIANGLE;
        };
    }


    /**
     * Get the wave form attribute of an LFO wave form, the inverse of
     * {@link #getLfoWaveform(double, boolean)} for the current version. The falling saw-tooth is
     * written as the rising one (the depth of the modulation needs to be inverted).
     *
     * @param waveform The wave form
     * @return The wave form attribute
     */
    public static double getLfoWaveformValue (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case SAWTOOTH_UP, SAWTOOTH_DOWN -> 0.2;
            case SQUARE -> 0.4;
            case RANDOM -> 0.6;
            case SINE -> 1.0;
            default -> 0;
        };
    }


    /**
     * Convert the displayed amount of a modulation of the master volume into the attenuation in dB
     * at the negative peak of a bipolar source. The amount is added to the main volume, which is
     * converted with {@link #volumeToDb(double)}.
     *
     * @param mainVolume The main volume in the range of [0..1]
     * @param displayAmount The displayed amount of the modulation in the range of [-1..1]
     * @return The attenuation in dB, negative for a negative amount
     */
    public static double volumeAmountToDb (final double mainVolume, final double displayAmount)
    {
        final double volume = Math.max (mainVolume, MIN_VOLUME);
        final double lowered = Math.max (volume - Math.abs (displayAmount), MIN_VOLUME);
        return Math.signum (displayAmount) * (volumeToDb (volume) - volumeToDb (lowered));
    }


    /**
     * Inverse of {@link #volumeAmountToDb(double, double)}.
     *
     * @param mainVolume The main volume in the range of [0..1]
     * @param db The attenuation in dB, negative for a negative amount
     * @return The displayed amount of the modulation in the range of [-1..1]
     */
    public static double dbToVolumeAmount (final double mainVolume, final double db)
    {
        final double volume = Math.max (mainVolume, MIN_VOLUME);
        final double lowered = dbToVolume (volumeToDb (volume) - Math.abs (db));
        return Math.signum (db) * Math.clamp (volume - lowered, 0.0, 1.0);
    }


    /**
     * Constant class.
     */
    private TALSamplerConstants ()
    {
        // Intentionally empty
    }
}
