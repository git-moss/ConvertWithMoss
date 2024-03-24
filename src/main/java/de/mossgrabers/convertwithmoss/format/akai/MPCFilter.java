// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;


/**
 * MPC extension to a filters' settings.
 *
 * @author Jürgen Moßgraber
 */
public class MPCFilter extends DefaultFilter
{
    private static final int           MAX          = 30;

    private static final FilterType [] FILTER_TYPES = new FilterType [MAX];
    private static final int []        FILTER_POLES = new int [MAX];

    static
    {
        // Low-pass 1 Pole
        FILTER_TYPES[1] = FilterType.LOW_PASS;
        FILTER_POLES[1] = 1;

        // Low-pass 2 Pole
        FILTER_TYPES[2] = FilterType.LOW_PASS;
        FILTER_POLES[2] = 2;

        // Low-pass 4 Pole
        FILTER_TYPES[3] = FilterType.LOW_PASS;
        FILTER_POLES[3] = 4;

        // Low-pass 6 Pole
        FILTER_TYPES[4] = FilterType.LOW_PASS;
        FILTER_POLES[4] = 6;

        // Low-pass 8 Pole
        FILTER_TYPES[5] = FilterType.LOW_PASS;
        FILTER_POLES[5] = 8;

        // High-pass 1 Pole
        FILTER_TYPES[6] = FilterType.HIGH_PASS;
        FILTER_POLES[6] = 1;

        // High-pass 2 Pole
        FILTER_TYPES[7] = FilterType.HIGH_PASS;
        FILTER_POLES[7] = 2;

        // High-pass 4 Pole
        FILTER_TYPES[8] = FilterType.HIGH_PASS;
        FILTER_POLES[8] = 4;

        // High-pass 6 Pole
        FILTER_TYPES[9] = FilterType.HIGH_PASS;
        FILTER_POLES[9] = 6;

        // High-pass 8 Pole
        FILTER_TYPES[10] = FilterType.HIGH_PASS;
        FILTER_POLES[10] = 8;

        // Bandpass 2 Pole
        FILTER_TYPES[11] = FilterType.BAND_PASS;
        FILTER_POLES[11] = 2;

        // Bandpass 4 Pole
        FILTER_TYPES[12] = FilterType.BAND_PASS;
        FILTER_POLES[12] = 4;

        // Bandpass 6 Pole
        FILTER_TYPES[13] = FilterType.BAND_PASS;
        FILTER_POLES[13] = 6;

        // Bandpass 8 Pole
        FILTER_TYPES[14] = FilterType.BAND_PASS;
        FILTER_POLES[14] = 8;

        // Band-stop 2 Pole
        FILTER_TYPES[15] = FilterType.BAND_REJECTION;
        FILTER_POLES[15] = 2;

        // Band-stop 4 Pole
        FILTER_TYPES[16] = FilterType.BAND_REJECTION;
        FILTER_POLES[16] = 4;

        // Band-stop 6 Pole
        FILTER_TYPES[17] = FilterType.BAND_REJECTION;
        FILTER_POLES[17] = 6;

        // Band-stop 8 Pole
        FILTER_TYPES[18] = FilterType.BAND_REJECTION;
        FILTER_POLES[18] = 8;

        // MPC Low-pass
        FILTER_TYPES[29] = FilterType.LOW_PASS;
        FILTER_POLES[29] = 4;
    }


    /**
     * Constructor.
     *
     * @param id The index of the MPC filter
     * @param cutoff The cutoff frequency
     * @param resonance The resonance
     */
    public MPCFilter (final int id, final double cutoff, final double resonance)
    {
        super (null, 0, cutoff * MAX_FREQUENCY, resonance * 40.0);

        if (id >= MAX)
            return;

        this.type = FILTER_TYPES[id];
        this.poles = FILTER_POLES[id];
    }


    /**
     * Get the index of the MPC filter depending on the given filter type and number of poles.
     *
     * @param filter The filter for which to get the index
     * @return The index or 0 if it could not be mapped
     */
    public static int getFilterIndex (final IFilter filter)
    {
        final FilterType type = filter.getType ();
        final int poles = filter.getPoles ();
        for (int index = 1; index < MAX; index++)
            if (FILTER_TYPES[index] == type && FILTER_POLES[index] == poles)
                return index;
        return 0;
    }
}
