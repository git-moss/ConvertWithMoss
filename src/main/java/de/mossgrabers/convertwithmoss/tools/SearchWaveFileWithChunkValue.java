// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.tools;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.InstrumentChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Command line utility to search for a chunk with a specific attribute value.
 *
 * @author Jürgen Moßgraber
 */
public class SearchWaveFileWithChunkValue
{
    /**
     * The main function.
     *
     * @param arguments First parameter is the folder to scan
     */
    public static void main (final String [] arguments)
    {
        if (arguments == null || arguments.length == 0)
            log ("Give a folder which contains WAV files as parameter...");
        else
            detect (new File (arguments[0]));
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

        for (final File file: Arrays.asList (files))
        {
            if (file.isDirectory ())
            {
                detect (file);
                continue;
            }

            if (!file.getName ().toLowerCase (Locale.US).endsWith (".wav"))
                continue;

            try
            {
                final WaveFile sampleFile = new WaveFile (file, true);

                final SampleChunk sampleChunk = sampleFile.getSampleChunk ();
                if (sampleChunk == null)
                    continue;

                final long pitchFraction = sampleChunk.getMIDIPitchFraction ();
                if (pitchFraction == 0)
                    continue;

                log (file.getAbsolutePath ());

                final int midiUnityNote = sampleChunk.getMIDIUnityNote ();
                log ("  Sample Chunk: Unity Note    : " + midiUnityNote + " " + NoteParser.formatNoteSharps (midiUnityNote));
                log ("  Sample Chunk: Pitch Fraction: " + pitchFraction + " (= " + sampleChunk.getMIDIPitchFractionAsCents () + " cents)");

                final InstrumentChunk instrumentChunk = sampleFile.getInstrumentChunk ();
                if (instrumentChunk == null)
                    continue;
                final int fineTune = instrumentChunk.getFineTune ();
                if (fineTune != 0)
                {
                    log ("  Instrument Chunk: Unshifted Note: " + instrumentChunk.getUnshiftedNote ());
                    log ("  Instrument Chunk: Fine Tune     : " + fineTune + " cents");
                }
            }
            catch (final IOException | ParseException ex)
            {
                log (ex.getMessage ());
            }
        }
    }


    private static void log (final String message)
    {
        System.out.println (message);
    }
}
