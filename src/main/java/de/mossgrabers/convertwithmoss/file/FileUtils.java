// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.File;
import java.io.IOException;


/**
 * File utility functions.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public final class FileUtils
{
    /**
     * Private due to helper class.
     */
    private FileUtils ()
    {
        // Intentionally empty
    }


    /**
     * Tries to convert the file to a canonical file (unique file).
     *
     * @param file The file to make canonical
     * @return The canonical file or the given file if it could not be converted
     */
    public static File makeCanonical (final File file)
    {
        try
        {
            return file.getCanonicalFile ();
        }
        catch (final IOException ex)
        {
            return file;
        }
    }


    /**
     * Gets the name of the file without the ending. E.g. the filename 'aFile.jpeg' will return
     * 'aFile'.
     *
     * @param file The file from which to get the name
     * @return The name of the file without the ending
     */
    public static String getNameWithoutType (final File file)
    {
        final String filename = file.getName ();
        final int pos = filename.lastIndexOf ('.');
        return pos == -1 ? filename : filename.substring (0, pos);
    }
}
