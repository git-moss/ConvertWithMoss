// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * Different types of filters.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public enum FilterType
{
    /** A low pass filter. */
    LOW_PASS,
    /** A high pass filter. */
    HIGH_PASS,
    /** A band pass filter. */
    BAND_PASS,
    /** A band rejection filter. */
    BAND_REJECTION,
}
