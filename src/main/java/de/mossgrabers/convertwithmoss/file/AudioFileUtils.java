// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Audio file utility functions.
 *
 * @author Jürgen Moßgraber
 */
public final class AudioFileUtils
{
    private static final String BROKEN_WAV = "IDS_NOTIFY_ERR_BROKEN_WAV";


    /**
     * Private due to helper class.
     */
    private AudioFileUtils ()
    {
        // Intentionally empty
    }


    /**
     * Get the number of samples of an audio file.
     *
     * @param audioFile The audio file from which to get its' length in samples
     * @return The duration or 0 if it could not be retrieved
     * @throws IOException Could not read or access the file
     */
    public static long getLength (final File audioFile) throws IOException
    {
        try
        {
            final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat (audioFile);
            return getLength (audioFileFormat);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException ("Could not retrieve audio file format.", ex);
        }
    }


    /**
     * Get the length of an audio file from its' audio file format.
     *
     * @param audioFileFormat The audio file format
     * @return The length or 0 if it could not be retrieved
     */
    public static long getLength (final AudioFileFormat audioFileFormat)
    {
        final AudioFormat format = audioFileFormat.getFormat ();
        if (format.getEncoding () != Encoding.PCM_SIGNED)
            return 0;

        final int frameLength = audioFileFormat.getFrameLength ();

        // Make sure there is a frame length
        if (frameLength == AudioSystem.NOT_SPECIFIED)
            return 0;

        return frameLength;
    }


    /**
     * Test the sample file for compatibility.
     *
     * @param wavFile The sample file to check
     * @param notifier Where to report errors
     * @return True if OK
     */
    public static boolean checkSampleFile (final File wavFile, final INotifier notifier)
    {
        if (!wavFile.exists ())
        {
            notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", wavFile.getAbsolutePath ());
            return false;
        }

        try
        {
            final WaveFile waveFile = new WaveFile (wavFile, true);
            checkSampleFile (wavFile.getAbsolutePath (), waveFile, notifier);
        }
        catch (final IOException | ParseException ex)
        {
            notifier.logError (BROKEN_WAV, ex);
            return false;
        }

        return true;
    }


    /**
     * Test the sample file for compatibility.
     *
     * @param filename The filename to include into error reporting
     * @param waveFile The sample file to check
     * @param notifier Where to report errors
     * @return True if OK
     */
    public static boolean checkSampleFile (final String filename, final WaveFile waveFile, final INotifier notifier)
    {
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        if (formatChunk == null)
        {
            notifier.logError (BROKEN_WAV, filename);
            return false;
        }

        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        if (numberOfChannels > 2)
        {
            notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), filename);
            return false;
        }

        return true;
    }


    /**
     * Split the parts of the path offset between the selected source folder and the currently
     * processed sub-folder.
     *
     * @param msSourceFolder The currently processed sub-folder
     * @param sourceFolder The source folder
     * @param name The name of the multisample
     * @return The array with all parts and the name in reverse order
     */
    public static String [] createPathParts (final File msSourceFolder, final File sourceFolder, final String name)
    {
        File f = msSourceFolder;
        final List<String> pathNames = new ArrayList<> ();
        while (!f.equals (sourceFolder))
        {
            pathNames.add (f.getName ());
            f = f.getParentFile ();
        }
        pathNames.add (sourceFolder.getName ());

        final String [] result = new String [pathNames.size () + 1];
        result[0] = name;
        for (int i = 0; i < pathNames.size (); i++)
            result[i + 1] = pathNames.get (i);
        return result;
    }


    /**
     * Get the relative path of the sub-folder.
     *
     * @param sourceFolder The parent folder
     * @param folder The sub-folder
     * @return The relative path starting from the parent folder
     */
    public static String subtractPaths (final File sourceFolder, final File folder)
    {
        final String analysePath = folder.getAbsolutePath ();
        final String sourcePath = sourceFolder.getAbsolutePath ();
        if (analysePath.startsWith (sourcePath))
        {
            final String n = analysePath.substring (sourcePath.length ());
            return n.isEmpty () ? analysePath : n;
        }

        return analysePath;
    }
}
