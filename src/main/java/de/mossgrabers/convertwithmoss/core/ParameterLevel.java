// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * The level on which a parameter should be applied.
 *
 * @author Jürgen Moßgraber
 */
public enum ParameterLevel
{
    /** The parameter should be applied to the whole instrument. */
    INSTRUMENT,
    /** The parameter should be applied to each group of the instrument. */
    GROUP,
    /** The parameter should be applied to each sample zone of the instrument. */
    ZONE
}
