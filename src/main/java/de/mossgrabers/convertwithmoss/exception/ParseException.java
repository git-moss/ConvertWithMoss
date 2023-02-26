// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.exception;

/**
 * Exception indicating a parsing error.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ParseException extends Exception
{
    private static final long serialVersionUID = 8066528352488161934L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public ParseException (final String message)
    {
        super (message);
    }


    /**
     * Constructor.
     *
     * @param message The message
     * @param cause The original cause
     */
    public ParseException (final String message, final Throwable cause)
    {
        super (message, cause);
    }
}
