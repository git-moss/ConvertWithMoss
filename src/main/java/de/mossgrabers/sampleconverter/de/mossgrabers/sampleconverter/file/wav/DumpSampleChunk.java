// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.SampleChunk.SampleChunkLoop;

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
public class DumpSampleChunk
{
    private static final Logger LOGGER;
    static
    {
        System.setProperty ("java.util.logging.SimpleFormatter.format", "%5$s%n");
        LOGGER = Logger.getLogger ("de.mossgrabers.wav.DumpSampleChunk");

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
        Arrays.asList (folder.listFiles ()).forEach (file -> {
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

                final InstrumentChunk instrumentChunk = sampleFile.getInstrumentChunk ();
                if (instrumentChunk == null)
                    log ("Instrument chunk: None");
                else
                {
                    log ("Instrument chunk:");
                    log ("  " + instrumentChunk.infoText ().replace ("\n", "\n  "));
                }

                final FormatChunk formatChunk = sampleFile.getFormatChunk ();
                if (formatChunk == null)
                    log ("No format chunk: None");
                else
                {
                    log ("Format chunk:");
                    log ("  " + formatChunk.infoText ().replace ("\n", "\n  "));
                }

                final SampleChunk sampleChunk = sampleFile.getSampleChunk ();
                if (sampleChunk == null)
                {
                    log ("No SMPL chunk.");
                    return;
                }

                final int midiUnityNote = sampleChunk.getMIDIUnityNote ();
                if (midiUnityNote != 0)
                    log ("Found MIDI unity note " + midiUnityNote);
                final int midiPitchFraction = sampleChunk.getMIDIPitchFraction ();
                if (midiPitchFraction != 0)
                    log ("Found MIDI pitch fraction " + midiPitchFraction);

                final List<SampleChunkLoop> loops = sampleChunk.getLoops ();
                final int loopSize = loops.size ();
                if (loopSize > 1)
                    log ("Found " + loopSize + " loops");
                if (!loops.isEmpty ())
                {
                    final SampleChunkLoop loop = loops.get (0);
                    final int loopFraction = loop.getFraction ();
                    if (loopFraction != 0)
                        log ("Found loop with fraction " + loopFraction);
                    final int loopType = loop.getType ();
                    if (loopType > 0)
                        log ("Found loop type " + loopType);
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
