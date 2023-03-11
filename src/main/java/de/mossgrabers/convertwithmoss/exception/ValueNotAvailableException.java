// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.exception;

/**
 * Indicates that a value is not available.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 * @author Philip Stolz
 */
public class ValueNotAvailableException extends Exception
{
    private static final long serialVersionUID = 7547848923691939612L;


    /**
     * Standard constructor.
     *
     * @param parameterName The name of the parameter for which the value was missing
     */
    public ValueNotAvailableException (final String parameterName)
    {
        super (parameterName);
    }
}
