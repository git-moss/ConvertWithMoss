// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file;

import java.io.File;
import java.io.IOException;


/**
 * File utility functions.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public final class Utils
{
    /**
     * Private due to helper class.
     */
    private Utils ()
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
}
