// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.exception.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


/**
 * Command line utility to look for specific settings in a sample chunk.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DumpWaveFileChunks
{
    private static final Logger LOGGER;
    static
    {
        System.setProperty ("java.util.logging.SimpleFormatter.format", "%5$s%n");
        LOGGER = Logger.getLogger ("de.mossgrabers.convertwithmoss.file.wav.DumpWaveFileChunks");

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
     * @param args First parameter is the folder to scan
     */
    public static void main (final String [] args)
    {
        if (args == null || args.length == 0)
            LOGGER.info ("Give a folder as parameter...");
        else
            detect (new File (args[0]));
    }


    /**
     * Detect all wave files in the given folder and dump sample chunk data.
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
        Arrays.asList (files).forEach (file -> {
            if (file.isDirectory ())
            {
                detect (file);
                return;
            }

            if (!file.getName ().toLowerCase (Locale.US).endsWith (".wav"))
                return;

            try
            {
                final WaveFile sampleFile = new WaveFile (file, true);
                log ("\n" + file.getAbsolutePath ());

                final FormatChunk formatChunk = sampleFile.getFormatChunk ();
                if (formatChunk != null)
                {
                    log ("Format chunk:");
                    log ("  " + formatChunk.infoText ().replace ("\n", "\n  "));
                }

                final InstrumentChunk instrumentChunk = sampleFile.getInstrumentChunk ();
                if (instrumentChunk != null)
                {
                    log ("Instrument chunk:");
                    log ("  " + instrumentChunk.infoText ().replace ("\n", "\n  "));
                }

                final SampleChunk sampleChunk = sampleFile.getSampleChunk ();
                if (sampleChunk != null)
                {
                    log ("Sample chunk:");
                    log ("  " + sampleChunk.infoText ().replace ("\n", "\n  "));
                }

                final List<String> unhandledChunks = sampleFile.getUnhandledChunks ();
                if (!unhandledChunks.isEmpty ())
                {
                    log ("Unhandled chunks:");
                    for (final String riffID: unhandledChunks)
                        log ("  * " + riffID);
                }
            }
            catch (IOException | ParseException ex)
            {
                log (ex.getMessage ());
            }
        });
    }


    private static void log (final String message)
    {
        LOGGER.log (Level.INFO, "{0}", message);
    }
}
