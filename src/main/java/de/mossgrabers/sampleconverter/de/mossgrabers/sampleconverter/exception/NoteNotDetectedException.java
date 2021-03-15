// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.exception;

/**
 * Exception that indicates that no note could be detected in a file name.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class NoteNotDetectedException extends Exception
{
    private static final long serialVersionUID = -9146162134386762053L;


    /**
     * Constructor.
     *
     * @param filename The filename in which no note could be detected
     */
    public NoteNotDetectedException (final String filename)
    {
        super (filename);
    }
}
