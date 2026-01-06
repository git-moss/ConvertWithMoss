// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.exception;

/**
 * Exception that indicates that there is a problem with reading a file due to format errors.
 *
 * @author Jürgen Moßgraber
 */
public class FormatException extends Exception
{
    private static final long serialVersionUID = -8648299631574438260L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public FormatException (final String message)
    {
        super (message);
    }
}
