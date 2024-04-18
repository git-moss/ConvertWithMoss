// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
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
    public static final double    MINUS_12_DB     = 0.353000;
    /** Normalized value of +6dB. */
    public static final double    PLUS_6_DB       = 1.0;
    /** The range between -12dB and +6dB. */
    public static final double    VALUE_RANGE     = PLUS_6_DB - MINUS_12_DB;

    /** The current file format version to set. */
    public static final String    CURRENT_VERSION = "9";
    /** The IDs to for the 4 layers. */
    public static final String [] LAYERS          = new String []
    {
        "a",
        "b",
        "c",
        "d"
    };

    private static final double   INDEX_OFFSET    = 0.0833333333333333;
    private static final int []   LOW_PASS_POLES  = new int []
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
        final int filterIndex = MathUtils.clamp ((int) Math.round (value / INDEX_OFFSET), 0, 12);
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
     * Each part of a TAL Sampler envelope can be as long as the sample (value = 1). Since the multi
     * sample source has several samples of different lengths, this function calculates the medium
     * length of all samples and converts it to seconds depending on the sample rate.
     *
     * @param groups The multi groups which contains the samples
     * @return The medium sample length in seconds
     * @throws IOException Could not get the sample rate from the audio data
     */
    public static double getMediumSampleLength (final List<IGroup> groups) throws IOException
    {
        int numSamples = 0;
        int lengths = 0;
        int sampleRate = -1;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                lengths += zone.getStop ();
                numSamples++;
                if (sampleRate < 0)
                    sampleRate = zone.getSampleData ().getAudioMetadata ().getSampleRate ();
            }
        return lengths == 0 ? 0.001 : Math.max (0.001, lengths / (double) numSamples / sampleRate);
    }


    /**
     * Constant class.
     */
    private TALSamplerConstants ()
    {
        // Intentionally empty
    }
}
