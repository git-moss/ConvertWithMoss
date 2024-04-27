// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import de.mossgrabers.convertwithmoss.file.aiff.AiffFile;


/**
 * Command line utility to dump AIFF file chunks.
 *
 * @author Jürgen Moßgraber
 */
public class DumpAiffFileChunks
{
    private static final Logger LOGGER;
    static
    {
        System.setProperty ("java.util.logging.SimpleFormatter.format", "%5$s%n");
        LOGGER = Logger.getLogger ("de.mossgrabers.convertwithmoss.tools.DumpAiffFileChunks");

        try
        {
            // This block configure the logger with handler and formatter
            final FileHandler fh = new FileHandler ("C:/temp/MyLogFile.log");
            LOGGER.addHandler (fh);
            final SimpleFormatter formatter = new SimpleFormatter ();
            fh.setFormatter (formatter);
        }
        catch (final SecurityException | IOException ex)
        {
            ex.printStackTrace ();
        }
    }


    /**
     * The main function.
     *
     * @param arguments First parameter is the folder to scan
     */
    public static void main (final String [] arguments)
    {
        if (arguments == null || arguments.length == 0)
            LOGGER.info ("Give a folder as parameter...");
        else
            detect (new File (arguments[0]));
    }


    /**
     * Detect all AIFF files in the given folder and dump sample chunk data.
     *
     * @param folder The folder in which to search
     */
    private static void detect (final File folder)
    {
        final File [] files = folder.listFiles ();
        if (files == null)
        {
            log ("Not a valid folder: " + folder.getAbsolutePath ());
            return;
        }

        for (final File file: Arrays.asList (files))
        {
            if (file.isDirectory ())
            {
                detect (file);
                continue;
            }

            final String lowerCase = file.getName ().toLowerCase (Locale.US);
            if (lowerCase.endsWith (".aif") || lowerCase.endsWith (".aiff"))
            {
                log ("\n" + file.getAbsolutePath ());
                try
                {
                    final AiffFile sampleFile = new AiffFile (file);
                    log (sampleFile.infoText ());
                }
                catch (final IOException ex)
                {
                    log ("  " + ex.getClass ().getName ());
                    log ("  " + ex.getMessage ());
                }
            }
        }
    }


    private static void log (final String message)
    {
        LOGGER.log (Level.INFO, "{0}", message);
    }
}
