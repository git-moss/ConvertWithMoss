// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.exception;

/**
 * Exception that indicates that a chunk has no data, e.g. because it was too large to be loaded or
 * was not initialized.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class NoDataInChunkException extends RuntimeException
{
    private static final long serialVersionUID = 888807809368471952L;


    /**
     * Constructor.
     *
     * @param message The message
     */
    public NoDataInChunkException (final String message)
    {
        super (message);
    }
}
