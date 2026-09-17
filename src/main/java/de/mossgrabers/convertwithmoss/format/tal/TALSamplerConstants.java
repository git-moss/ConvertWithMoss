// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;


/**
 * Some constants for the TAL Sampler.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerConstants
{
    /** Normalized value of -12dB. */
    public static final double       MINUS_12_DB       = 0.353000;
    /** Normalized value of +6dB. */
    public static final double       PLUS_6_DB         = 1.0;
    /** The range between -12dB and +6dB. */
    public static final double       VALUE_RANGE       = PLUS_6_DB - MINUS_12_DB;

    /** The current file format version to set. */
    public static final String       CURRENT_VERSION   = "9";

    /**
     * The longest stage of an envelope in seconds, which the value 1 of an attack, hold, decay or
     * release selects. The times of the envelopes of TAL-Sampler do not depend on the length of the
     * samples: rendered offline (version 4.7.2) with the values 0.4, 0.6, 0.8 and 1.0 of the
     * amplitude release, a note fades out in 0.19, 0.93, 2.95 and 7.2 seconds, which is 7.2 x
     * value^4 within the accuracy of the measurement, and the attack follows the same law; the hold
     * and the decay are taken to follow it as well.
     */
    public static final double       MAX_ENVELOPE_TIME = 7.2;

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
    protected static final String [] LAYERS            = new String []
    {
        "a",
        "b",
        "c",
        "d"
    };

    private static final double      INDEX_OFFSET      = 0.0833333333333333;
    private static final int []      LOW_PASS_POLES    = new int []
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
     * Constant class.
     */
    private TALSamplerConstants ()
    {
        // Intentionally empty
    }
}
