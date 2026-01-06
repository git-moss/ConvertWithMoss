// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


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
            return getLength (AudioSystem.getAudioFileFormat (audioFile));
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (getErrorMessage (), ex);
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
            throw new IOException (getErrorMessage (), ex);
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
            return getMetadata (AudioSystem.getAudioFileFormat (new BufferedInputStream (audioFileStream)));
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (getErrorMessage (), ex);
        }
    }


    private static IAudioMetadata getMetadata (final AudioFileFormat audioFileFormat)
    {
        final AudioFormat format = audioFileFormat.getFormat ();
        return new DefaultAudioMetadata (format.getChannels (), (int) format.getSampleRate (), format.getSampleSizeInBits (), audioFileFormat.getFrameLength ());
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
            if (notifier != null)
                notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", wavFile.getAbsolutePath ());
            return false;
        }

        try
        {
            final WaveFile waveFile = new WaveFile (wavFile, true);
            checkSampleFile (wavFile.getAbsolutePath (), waveFile, notifier);
        }
        catch (final IOException | ParseException | RuntimeException ex)
        {
            if (notifier != null)
                notifier.logError (BROKEN_WAV, wavFile.getAbsolutePath (), ex.getMessage ());
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
            notifier.logError (BROKEN_WAV, filename, "Missing format chunk.");
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
     * Converts the sample data contained in the given object into a WAV file. The resulting WAV
     * file is converted to match the given destination format.
     *
     * @param sampleData The input sample data
     * @param destinationFormat The destination WAV format configuration
     * @return The data of the output file parse into a WaveFile object
     * @throws IOException Could not read or write
     */
    public static WaveFile convertToWav (final ISampleData sampleData, final DestinationAudioFormat destinationFormat) throws IOException
    {
        try
        {
            final WaveFile waveFile = new WaveFile ();
            waveFile.read (new ByteArrayInputStream (convertToWavData (sampleData, destinationFormat)), true);
            return waveFile;
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Converts the sample data contained in the given object into a WAV file. The resulting WAV
     * file is converted to match the given destination format. The WAV file is returned as bytes.
     *
     * @param sampleData The input sample data
     * @param destinationFormat The destination WAV format configuration
     * @return The data of the output file
     * @throws IOException Could not read or write
     */
    public static byte [] convertToWavData (final ISampleData sampleData, final DestinationAudioFormat destinationFormat) throws IOException
    {
        final ByteArrayOutputStream dataOut = new ByteArrayOutputStream ();
        sampleData.writeSample (dataOut);
        return convertToWav (dataOut.toByteArray (), destinationFormat);
    }


    /**
     * Converts the input data to the output data. Having the data in-memory is important because
     * the audio input stream needs to get the length of data! The resulting streamed file has a
     * maximum bit resolution and sample rate of the given parameters.
     *
     * @param inputData The data of the input file
     * @param destinationFormat The destination WAV format configuration
     * @return The data of the output file
     * @throws IOException Could not read or write
     */
    private static byte [] convertToWav (final byte [] inputData, final DestinationAudioFormat destinationFormat) throws IOException
    {
        return convertToWav (new ByteArrayInputStream (inputData), destinationFormat);
    }


    private static byte [] convertToWav (final InputStream inputStream, final DestinationAudioFormat destinationFormat) throws IOException
    {
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (inputStream))
        {
            final AudioFormat audioFormat = audioInputStream.getFormat ();
            final int bitResolution = getMatchingBitResolution (audioFormat.getSampleSizeInBits (), destinationFormat.getBitResolutions ());

            int sampleRate = (int) audioFormat.getSampleRate ();
            final int maxSampleRate = destinationFormat.getMaxSampleRate ();
            if (maxSampleRate != -1 && (sampleRate > maxSampleRate || destinationFormat.isUpSample ()))
                sampleRate = maxSampleRate;

            final Encoding encoding = audioFormat.getEncoding ();
            final boolean is32BitFloat = encoding == Encoding.PCM_FLOAT && audioFormat.getSampleSizeInBits () == 32;
            final AudioFormat newAudioFormat = new AudioFormat (sampleRate, is32BitFloat ? 16 : bitResolution, audioFormat.getChannels (), encoding == Encoding.PCM_SIGNED || is32BitFloat, audioFormat.isBigEndian ());

            // AudioSystem handles 32bit float values incorrect. We need our own implementation.
            if (is32BitFloat)
                try (AudioInputStream convertedAudioInputStream = convertAudioStreamFrom32BitFloatTo16BitPCM (audioInputStream, audioFormat, newAudioFormat))
                {
                    return doConvertToWav (convertedAudioInputStream, newAudioFormat);
                }

            return doConvertToWav (audioInputStream, newAudioFormat);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
    }


    private static AudioInputStream convertAudioStreamFrom32BitFloatTo16BitPCM (final AudioInputStream inputStream, final AudioFormat sourceAudioFormat, final AudioFormat destinationAudioFormat) throws IOException
    {
        if (destinationAudioFormat.getSampleSizeInBits () != 16)
            throw new IOException (Functions.getMessage ("IDS_WAV_ONLY_16_BIT_SUPPORTED", Integer.toString (destinationAudioFormat.getSampleSizeInBits ())));

        final byte [] sourceData = inputStream.readAllBytes ();
        final ByteBuffer inputBuffer = ByteBuffer.wrap (sourceData).order (sourceAudioFormat.isBigEndian () ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer outputBuffer = ByteBuffer.allocate (sourceData.length / 2).order (destinationAudioFormat.isBigEndian () ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < sourceData.length; i += 4)
        {
            final float floatValue = inputBuffer.getFloat (i);

            // Convert float to 16-bit PCM
            outputBuffer.putShort ((short) (floatValue * Short.MAX_VALUE));
        }

        return new AudioInputStream (new ByteArrayInputStream (outputBuffer.array ()), destinationAudioFormat, inputStream.getFrameLength ());
    }


    private static byte [] doConvertToWav (final AudioInputStream audioInputStream, final AudioFormat newAudioFormat) throws IOException
    {
        File tempFile = null;
        try (final AudioInputStream convertedAudioInputStream = AudioSystem.getAudioInputStream (newAudioFormat, audioInputStream))
        {
            // Cannot write to a stream since the length is not known and therefore the WAV
            // header cannot be written and write method crashes
            tempFile = File.createTempFile ("wav", "tmp");
            AudioSystem.write (convertedAudioInputStream, AudioFileFormat.Type.WAVE, tempFile);
            return Files.readAllBytes (tempFile.toPath ());
        }
        finally
        {
            if (tempFile != null)
                Files.delete (tempFile.toPath ());
        }
    }


    /**
     * De-compresses the input file and writes audio data in WAV format to the given output stream.
     *
     * @param inputFile The input file to convert
     * @param outputStream The output stream to write to
     * @throws IOException Could not convert or write the file
     */
    public static void decompressToWav (final File inputFile, final OutputStream outputStream) throws IOException
    {
        // The conversion needs to be a 2 step process to get the length of the data
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (inputFile))
        {
            final AudioFormat sourceFormat = audioInputStream.getFormat ();
            final int channels = sourceFormat.getChannels ();
            int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            if (sampleSizeInBits < 0)
                sampleSizeInBits = 16;
            final AudioFormat convertFormat = new AudioFormat (sourceFormat.getSampleRate (), sampleSizeInBits, channels, true, false);

            // Step 1 - First convert to raw sample data
            final byte [] audioDataBytes;
            try (final AudioInputStream convertedAudioInputStream = AudioSystem.getAudioInputStream (convertFormat, audioInputStream))
            {
                audioDataBytes = convertedAudioInputStream.readAllBytes ();
            }

            // Step 2 - Convert from raw data to WAV format
            final int numFrames = audioDataBytes.length / (convertFormat.getFrameSize () * channels);
            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream (audioDataBytes);
            try (final AudioInputStream wavAudioInputStream = new AudioInputStream (byteArrayInputStream, convertFormat, numFrames))
            {
                AudioSystem.write (wavAudioInputStream, AudioFileFormat.Type.WAVE, outputStream);
            }
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Compresses the given sample data contained in the sampleData object into a specific file
     * format (e.g. FLAC).
     *
     * @param sampleData The sample data
     * @param targetFormat The target format
     * @return The byte contents of the file
     * @throws IOException Could not read/write
     * @throws UnsupportedAudioFileException The target audio format is not supported
     */
    public static byte [] compressToFLAC (final ISampleData sampleData, final AudioFileFormat.Type targetFormat) throws IOException, UnsupportedAudioFileException
    {
        final byte [] wavData = convertToWavData (sampleData, new DestinationAudioFormat ());

        // Create a ByteArrayInputStream from the input data array
        try (final ByteArrayInputStream bais = new ByteArrayInputStream (wavData); AudioInputStream ais = AudioSystem.getAudioInputStream (bais))
        {
            final AudioFormat sourceFormat = ais.getFormat ();
            final AudioFormat targetAudioFormat = new AudioFormat (sourceFormat.getSampleRate (), sourceFormat.getSampleSizeInBits (), sourceFormat.getChannels (), true, sourceFormat.isBigEndian ());
            final AudioInputStream convertedAIS = AudioSystem.getAudioInputStream (targetAudioFormat, ais);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();
            AudioSystem.write (convertedAIS, targetFormat, outputStream);
            return outputStream.toByteArray ();
        }
    }


    /**
     * Split the parts of the path offset between the selected source folder and the currently
     * processed sub-folder.
     *
     * @param msSourceFolder The currently processed sub-folder
     * @param sourceFolder The source folder
     * @param name The name of the multi-sample
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


    /**
     * Checks for a matching bit resolution. If the given resolution is among the given resolutions
     * it is returned. If it is not among them the highest resolution in the array is returned.
     *
     * @param bitResolution The resolution to check
     * @param bitResolutions The supported resolutions
     * @return The matching resolution
     */
    private static int getMatchingBitResolution (final int bitResolution, final int [] bitResolutions)
    {
        if (bitResolutions == null)
            return bitResolution;

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


    private static String getErrorMessage ()
    {
        return Functions.getMessage ("IDS_NOTIFY_ERR_COULD_NOT_RETRIEVE_FILE_FORMAT");
    }
}
