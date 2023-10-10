// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
     * Get metadata information about the given audio file.
     *
     * @param audioFile An audio file
     * @return The metadata
     * @throws IOException If the file cannot be read or it's format is not supported
     */
    public static IAudioMetadata getMetadata (final File audioFile) throws IOException
    {
        try
        {
            return getMetadata (AudioSystem.getAudioFileFormat (audioFile));
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException ("Could not retrieve audio file format.", ex);
        }
    }


    /**
     * Get metadata information about the given audio file.
     *
     * @param audioFileStream A streamed audio file
     * @return The metadata
     * @throws IOException If the file cannot be read or it's format is not supported
     */
    public static IAudioMetadata getMetadata (final InputStream audioFileStream) throws IOException
    {
        try
        {
            return getMetadata (AudioSystem.getAudioFileFormat (audioFileStream));
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException ("Could not retrieve audio file format.", ex);
        }
    }


    private static IAudioMetadata getMetadata (final AudioFileFormat audioFileFormat)
    {
        final AudioFormat format = audioFileFormat.getFormat ();
        return new DefaultAudioMetadata (format.getChannels () == 1, (int) format.getSampleRate (), format.getSampleSizeInBits ());
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
     * Write the audio file from the input stream to the output stream. The resulting streamed file
     * has a maximum bit resolution and sample rate of the given parameters.
     *
     * @param inputData The data of the input file
     * @param bitResolutions The maximum bit resolution to convert to
     * @param maxSampleRate The maximum sample rate to convert to
     * @return The data of the output file
     * @throws IOException Could not read or write
     * @throws UnsupportedAudioFileException The format of the audio file is not supported
     */
    public static byte [] convertToFormat (final byte [] inputData, final int [] bitResolutions, final int maxSampleRate) throws IOException, UnsupportedAudioFileException
    {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();
        convertToFormat (new ByteArrayInputStream (inputData), outputStream, bitResolutions, maxSampleRate);
        return outputStream.toByteArray ();
    }


    /**
     * Write the audio file from the input stream to the output stream. The resulting file has a
     * maximum bit resolution and sample rate of the given parameters.
     *
     * @param inputStream From where to read the input file
     * @param outputStream Where to stream the output file
     * @param bitResolutions The maximum bit resolution to convert to
     * @param maxSampleRate The maximum sample rate to convert to
     * @throws IOException Could not read or write
     * @throws UnsupportedAudioFileException The format of the audio file is not supported
     */
    public static void convertToFormat (final InputStream inputStream, final OutputStream outputStream, final int [] bitResolutions, final int maxSampleRate) throws IOException, UnsupportedAudioFileException
    {
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (inputStream))
        {
            final AudioFormat audioFormat = audioInputStream.getFormat ();
            final int bitResolution = getMatchingBitResolution (audioFormat.getSampleSizeInBits (), bitResolutions);
            int sampleRate = (int) audioFormat.getSampleRate ();
            if (sampleRate > maxSampleRate)
                sampleRate = maxSampleRate;
            final AudioFormat newAudioFormat = new AudioFormat (sampleRate, bitResolution, audioFormat.getChannels (), audioFormat.getEncoding () == Encoding.PCM_SIGNED, audioFormat.isBigEndian ());
            try (final AudioInputStream convertedAudioInputStream = AudioSystem.getAudioInputStream (newAudioFormat, audioInputStream))
            {
                AudioSystem.write (convertedAudioInputStream, AudioFileFormat.Type.WAVE, outputStream);
            }
        }
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


    private static int getMatchingBitResolution (final int bitResolution, final int [] bitResolutions)
    {
        int maxBitResolution = 0;
        for (final int bitResolution2: bitResolutions)
        {
            if (bitResolution == bitResolution2)
                return bitResolution;
            if (bitResolution2 > maxBitResolution)
                maxBitResolution = bitResolution2;
        }
        return maxBitResolution;
    }
}
