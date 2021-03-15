// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.exception;

/**
 * Exception that indicates that two wave files cannot be combined into one.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class CombinationNotPossibleException extends Exception
{
    private static final long serialVersionUID = 4187825436861158467L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public CombinationNotPossibleException (final String message)
    {
        super (message);
    }
}
