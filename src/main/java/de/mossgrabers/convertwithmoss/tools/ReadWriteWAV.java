// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.tools;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Reads and writes a WAV file to check if it is identical.
 *
 * @author Jürgen Moßgraber
 */
public class ReadWriteWAV
{
    /**
     * The main function.
     *
     * @param arguments First parameter is the file to read (full-path), 2nd parameter the file
     *            (full-path) to write to
     */
    public static void main (final String [] arguments)
    {
        if (arguments == null || arguments.length != 2)
        {
            System.out.println ("First parameter is the file to read (full-path), 2nd parameter the file (full-path) to write to...");
            return;
        }

        final File file = new File (arguments[0]);
        if (!file.exists ())
        {
            System.out.println ("The given source file does not exist: " + file.getAbsolutePath ());
            return;
        }

        try
        {
            final WaveFile wavFile = new WaveFile (file, true);
            final File outputFile = new File (arguments[1]);
            wavFile.write (outputFile);

            System.out.println ("Success.");
        }
        catch (final IOException | ParseException ex)
        {
            ex.printStackTrace ();
        }
    }
}
