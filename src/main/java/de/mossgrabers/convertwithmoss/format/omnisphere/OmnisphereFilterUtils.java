// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;


/**
 * Some helper functions for converting filter settings.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereFilterUtils
{
    /** The float index of the default classic 4-pole low-pass */
    public static final float                                   DEFAULT_FILTER_INDEX  = 0.86f;

    private static final double                                 MIN_HZ                = 50.0;
    private static final double                                 MAX_HZ                = 19000.0;
    private static final double                                 RANGE                 = MAX_HZ - MIN_HZ;

    private static final Map<Float, Integer>                    FILTER_TYPES          = new HashMap<> ();
    private static final Map<Integer, Float>                    INVERTED_FILTER_TYPES = new HashMap<> ();
    private static final Map<Integer, IFilter>                  FILTERS               = new HashMap<> ();
    private static final Map<FilterType, Map<Integer, Integer>> FILTER_INDICES        = new HashMap<> ();
    static
    {
        // LPF
        FILTER_TYPES.put (Float.valueOf (0.990001f), Integer.valueOf (0));
        FILTER_TYPES.put (Float.valueOf (0.9900011f), Integer.valueOf (1));
        FILTER_TYPES.put (Float.valueOf (0.8f), Integer.valueOf (2));
        FILTER_TYPES.put (Float.valueOf (0.82f), Integer.valueOf (3));
        FILTER_TYPES.put (Float.valueOf (0.84f), Integer.valueOf (4));
        FILTER_TYPES.put (Float.valueOf (0.86f), Integer.valueOf (5));
        FILTER_TYPES.put (Float.valueOf (0.9900001f), Integer.valueOf (6));
        FILTER_TYPES.put (Float.valueOf (0.9900003f), Integer.valueOf (7));
        FILTER_TYPES.put (Float.valueOf (0.9900004f), Integer.valueOf (8));
        FILTER_TYPES.put (Float.valueOf (0.9900005f), Integer.valueOf (9));
        FILTER_TYPES.put (Float.valueOf (0.88f), Integer.valueOf (10));
        FILTER_TYPES.put (Float.valueOf (0.9900008f), Integer.valueOf (11));
        FILTER_TYPES.put (Float.valueOf (0.92f), Integer.valueOf (12));
        FILTER_TYPES.put (Float.valueOf (0.9900012f), Integer.valueOf (13));
        FILTER_TYPES.put (Float.valueOf (0.9900006f), Integer.valueOf (14));
        FILTER_TYPES.put (Float.valueOf (0.9900007f), Integer.valueOf (15));
        FILTER_TYPES.put (Float.valueOf (0.9900012f), Integer.valueOf (16));
        FILTER_TYPES.put (Float.valueOf (0.9900013f), Integer.valueOf (17));
        // HPF
        FILTER_TYPES.put (Float.valueOf (0.9f), Integer.valueOf (18));
        FILTER_TYPES.put (Float.valueOf (0.9900016f), Integer.valueOf (19));
        FILTER_TYPES.put (Float.valueOf (0.9900034f), Integer.valueOf (20));
        FILTER_TYPES.put (Float.valueOf (0.94f), Integer.valueOf (21));
        FILTER_TYPES.put (Float.valueOf (0.9900017f), Integer.valueOf (22));
        FILTER_TYPES.put (Float.valueOf (0.9900014f), Integer.valueOf (23));
        FILTER_TYPES.put (Float.valueOf (0.9900015f), Integer.valueOf (24));
        // BPF
        FILTER_TYPES.put (Float.valueOf (0.9900023f), Integer.valueOf (25));
        FILTER_TYPES.put (Float.valueOf (0.9900024f), Integer.valueOf (26));
        FILTER_TYPES.put (Float.valueOf (0.96f), Integer.valueOf (27));
        FILTER_TYPES.put (Float.valueOf (0.9900022f), Integer.valueOf (28));
        FILTER_TYPES.put (Float.valueOf (0.990002f), Integer.valueOf (29));
        FILTER_TYPES.put (Float.valueOf (0.9900021f), Integer.valueOf (30));
        // Notch
        FILTER_TYPES.put (Float.valueOf (0.9900026f), Integer.valueOf (31));

        for (final Map.Entry<Float, Integer> e: FILTER_TYPES.entrySet ())
            INVERTED_FILTER_TYPES.put (e.getValue (), e.getKey ());

        //@formatter:off
        FILTERS.put (Integer.valueOf (0), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0));  // Beefy LPF 2-pole
        FILTERS.put (Integer.valueOf (1), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0));  // Beefy LPF 4-pole
        FILTERS.put (Integer.valueOf (2), new DefaultFilter (FilterType.LOW_PASS, 1, 0, 0));  // Classic LPF 1-pole
        FILTERS.put (Integer.valueOf (3), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0));  // Classic LPF 2-pole
        FILTERS.put (Integer.valueOf (4), new DefaultFilter (FilterType.LOW_PASS, 3, 0, 0));  // Classic LPF 3-pole
        FILTERS.put (Integer.valueOf (5), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0));  // Classic LPF 4-pole
        FILTERS.put (Integer.valueOf (6), new DefaultFilter (FilterType.LOW_PASS, 6, 0, 0));  // Classic LPF 6-pole
        FILTERS.put (Integer.valueOf (7), new DefaultFilter (FilterType.LOW_PASS, 8, 0, 0));  // Classic LPF 8-pole
        FILTERS.put (Integer.valueOf (8), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0));  // French LPF 2-pole
        FILTERS.put (Integer.valueOf (9), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0));  // French LPF 4-pole
        FILTERS.put (Integer.valueOf (10), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0)); // Jupiter LPF 2-pole
        FILTERS.put (Integer.valueOf (11), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0)); // Jupiter LPF 4-pole
        FILTERS.put (Integer.valueOf (12), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0)); // OB LPF 2-pole
        FILTERS.put (Integer.valueOf (13), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0)); // OB LPF 4-pole
        FILTERS.put (Integer.valueOf (14), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0)); // Sauce LPF 2-pole
        FILTERS.put (Integer.valueOf (15), new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0)); // Sauce LPF 4-pole
        FILTERS.put (Integer.valueOf (16), new DefaultFilter (FilterType.LOW_PASS, 1, 0, 0)); // Subtle LPF 1-pole
        FILTERS.put (Integer.valueOf (17), new DefaultFilter (FilterType.LOW_PASS, 2, 0, 0)); // Subtle LPF 2-pole
        
        FILTERS.put (Integer.valueOf (18), new DefaultFilter (FilterType.HIGH_PASS, 1, 0, 0)); // Jupiter HPF 1-pole
        FILTERS.put (Integer.valueOf (19), new DefaultFilter (FilterType.HIGH_PASS, 2, 0, 0)); // Jupiter HPF 2-pole
        FILTERS.put (Integer.valueOf (20), new DefaultFilter (FilterType.HIGH_PASS, 4, 0, 0)); // Jupiter HPF 4-pole
        FILTERS.put (Integer.valueOf (21), new DefaultFilter (FilterType.HIGH_PASS, 2, 0, 0)); // OB HPF 2-pole
        FILTERS.put (Integer.valueOf (22), new DefaultFilter (FilterType.HIGH_PASS, 4, 0, 0)); // OB HPF 4-pole
        FILTERS.put (Integer.valueOf (23), new DefaultFilter (FilterType.HIGH_PASS, 2, 0, 0)); // Sauce HPF 2-pole
        FILTERS.put (Integer.valueOf (24), new DefaultFilter (FilterType.HIGH_PASS, 4, 0, 0)); // Sauce HPF 4-pole
        
        FILTERS.put (Integer.valueOf (25), new DefaultFilter (FilterType.BAND_PASS, 2, 0, 0)); // Beefy BPF 2-pole
        FILTERS.put (Integer.valueOf (26), new DefaultFilter (FilterType.BAND_PASS, 4, 0, 0)); // Beefy BPF 4-pole
        FILTERS.put (Integer.valueOf (27), new DefaultFilter (FilterType.BAND_PASS, 2, 0, 0)); // OB BPF 2-pole
        FILTERS.put (Integer.valueOf (28), new DefaultFilter (FilterType.BAND_PASS, 4, 0, 0)); // OB BPF 4-pole
        FILTERS.put (Integer.valueOf (29), new DefaultFilter (FilterType.BAND_PASS, 2, 0, 0)); // Sauce BPF 2-pole
        FILTERS.put (Integer.valueOf (30), new DefaultFilter (FilterType.BAND_PASS, 4, 0, 0)); // Sauce BPF 4-pole

        FILTERS.put (Integer.valueOf (31), new DefaultFilter (FilterType.BAND_REJECTION, 4, 0, 0)); // Notch Filter
        //@formatter:on

        // Classic LPF 1-pole to 8-pole
        final Map<Integer, Integer> lowPassPoleIndices = new HashMap<> ();
        lowPassPoleIndices.put (Integer.valueOf (1), Integer.valueOf (2));
        lowPassPoleIndices.put (Integer.valueOf (2), Integer.valueOf (3));
        lowPassPoleIndices.put (Integer.valueOf (3), Integer.valueOf (4));
        lowPassPoleIndices.put (Integer.valueOf (4), Integer.valueOf (5));
        lowPassPoleIndices.put (Integer.valueOf (6), Integer.valueOf (6));
        lowPassPoleIndices.put (Integer.valueOf (8), Integer.valueOf (7));
        FILTER_INDICES.put (FilterType.LOW_PASS, lowPassPoleIndices);

        // Jupiter HPF 1-pole to 4-pole
        final Map<Integer, Integer> highPassPoleIndices = new HashMap<> ();
        highPassPoleIndices.put (Integer.valueOf (1), Integer.valueOf (18));
        highPassPoleIndices.put (Integer.valueOf (2), Integer.valueOf (19));
        highPassPoleIndices.put (Integer.valueOf (4), Integer.valueOf (20));
        FILTER_INDICES.put (FilterType.HIGH_PASS, highPassPoleIndices);

        // Beefy BPF 2-pole or 4-pole
        final Map<Integer, Integer> bandPassPoleIndices = new HashMap<> ();
        bandPassPoleIndices.put (Integer.valueOf (2), Integer.valueOf (25));
        bandPassPoleIndices.put (Integer.valueOf (4), Integer.valueOf (26));
        FILTER_INDICES.put (FilterType.BAND_PASS, bandPassPoleIndices);

        // Notch Filter
        final Map<Integer, Integer> notchPoleIndices = new HashMap<> ();
        notchPoleIndices.put (Integer.valueOf (4), Integer.valueOf (31));
    }


    /**
     * Get the filter which matches the given float value.
     * 
     * @param indexValue The float index value
     * @return The matching filter or a default 4 pole low-pass
     */
    public static IFilter getFilter (final float indexValue)
    {
        final Integer filterIndex = FILTER_TYPES.get (Float.valueOf (indexValue));
        return FILTERS.get (filterIndex == null ? Integer.valueOf (5) : filterIndex);
    }


    /**
     * Get the float value which matches the given filter.
     * 
     * @param filter The filter for which to get the float index
     * @return The float index of the filter (returns Classic 4-pole low-pass if it could not
     *         matched)
     */
    public static float getFilterIndex (final IFilter filter)
    {
        final Map<Integer, Integer> filterIndices = FILTER_INDICES.get (filter.getType ());
        if (filterIndices == null)
            return DEFAULT_FILTER_INDEX;
        final Integer poles = filterIndices.get (Integer.valueOf (filter.getPoles ()));
        if (poles == null)
            return DEFAULT_FILTER_INDEX;
        final Float result = INVERTED_FILTER_TYPES.get (poles);
        return result == null ? DEFAULT_FILTER_INDEX : result.floatValue ();
    }


    /**
     * Maps normalized value [0..1] to frequency [50..19000] Hz
     * 
     * @param normalizedValue The normalized value
     * @return The value in Hertz
     */
    public static double normalizedToHertz (final double normalizedValue)
    {
        final double normalizedValueClamped = clamp01 (normalizedValue);
        return MIN_HZ + RANGE * Math.pow (normalizedValueClamped, 3.0);
    }


    /**
     * Maps frequency [50..19000] Hz back to normalized [0..1].
     * 
     * @param hz The value in Hertz
     * @return The normalized
     */
    public static double hertzToNormalized (final double hz)
    {
        final double hzClamped = Math.clamp (hz, MIN_HZ, MAX_HZ);
        return Math.cbrt ((hzClamped - MIN_HZ) / RANGE);
    }


    private static double clamp01 (double v)
    {
        return Math.clamp (v, 0.0, 1.0);
    }
}
