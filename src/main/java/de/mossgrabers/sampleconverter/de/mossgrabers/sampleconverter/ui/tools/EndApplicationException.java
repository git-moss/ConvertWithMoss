// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

/**
 * Thrown to signal that the application had an initialization problem and should shut down.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class EndApplicationException extends Exception
{
    private static final long serialVersionUID = 3617007542409443641L;


    /**
     * Constructor.
     *
     * @param cause Wrapped cause
     */
    public EndApplicationException (final Throwable cause)
    {
        super (cause);
    }


    /**
     * Constructor.
     *
     * @param message The message of the cause
     * @param cause Wrapped cause
     */
    public EndApplicationException (final String message, final Throwable cause)
    {
        super (message, cause);
    }
}
