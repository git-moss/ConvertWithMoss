// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.renoise;

import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;


/**
 * Maps between the ConvertWithMoss {@link FilterType} model and the Renoise sampler filter
 * representation.
 * <p>
 * The per-sample filter in Renoise is the sampler filter selected by the integer
 * {@code SampleModulationSet/FilterType} together with {@code FilterBankVersion}. For the current
 * filter bank (version 3) the 0-based index list is (the entries relevant for this converter):
 *
 * <pre>
 *  0:None        1:LP Clean   2:LP K35   3:LP Moog   4:LP Diode
 *  5:HP Clean    6:HP K35     7:HP Moog
 *  8:BP Clean    9:BP K35    10:BP Moog  11:BandPass
 * 12:BandStop  (13+: Vowel/Comb/Decimator/distortion/AM - not plain filters)
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public final class RenoiseFilterType
{
    /**
     * The Renoise filter bank version that the {@link #toFilterTypeIndex(FilterType)} indices use.
     */
    public static final int  FILTER_BANK_VERSION = 3;

    /** Filter type index: no filter. */
    public static final int  INDEX_NONE          = 0;

    private static final int INDEX_LP_CLEAN      = 1;
    private static final int INDEX_LP_DIODE      = 4;
    private static final int INDEX_HP_CLEAN      = 5;
    private static final int INDEX_HP_MOOG       = 7;
    private static final int INDEX_BP_CLEAN      = 8;
    private static final int INDEX_BANDPASS      = 11;
    private static final int INDEX_BANDSTOP      = 12;


    /**
     * Private constructor for utility class.
     */
    private RenoiseFilterType ()
    {
        // Intentionally empty
    }


    /**
     * Get the Renoise sampler filter index (for filter bank version 3) for a model filter type.
     *
     * @param filterType The model filter type
     * @return The Renoise filter type index
     */
    public static int toFilterTypeIndex (final FilterType filterType)
    {
        return switch (filterType)
        {
            case LOW_PASS -> INDEX_LP_CLEAN;
            case HIGH_PASS -> INDEX_HP_CLEAN;
            case BAND_PASS -> INDEX_BP_CLEAN;
            case BAND_REJECTION -> INDEX_BANDSTOP;
            default -> INDEX_LP_CLEAN;
        };
    }


    /**
     * Get the model filter type for a Renoise sampler filter index (filter bank version 3).
     *
     * @param index The Renoise filter type index
     * @return The model filter type or null if the index is not a plain filter (None or a special
     *         effect type)
     */
    public static FilterType fromFilterTypeIndex (final int index)
    {
        if (index >= INDEX_LP_CLEAN && index <= INDEX_LP_DIODE)
            return FilterType.LOW_PASS;
        if (index >= INDEX_HP_CLEAN && index <= INDEX_HP_MOOG)
            return FilterType.HIGH_PASS;
        if (index >= INDEX_BP_CLEAN && index <= INDEX_BANDPASS)
            return FilterType.BAND_PASS;
        if (index == INDEX_BANDSTOP)
            return FilterType.BAND_REJECTION;
        return null;
    }
}
