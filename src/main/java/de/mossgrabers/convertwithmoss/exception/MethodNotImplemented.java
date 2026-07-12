// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.exception;

/**
 * Exception that indicates that a method is not implemented.
 *
 * @author Jürgen Moßgraber
 */
public class MethodNotImplemented extends RuntimeException
{
    private static final long serialVersionUID = -6346559190363705731L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public MethodNotImplemented (final String message)
    {
        super (message);
    }
}
