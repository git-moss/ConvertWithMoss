// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.AudioSampleReducer;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.flac.FlacEncoder;
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
    private static final String                 BROKEN_WAV             = "IDS_NOTIFY_ERR_BROKEN_WAV";

    /** FLAC supports at maximum a resolution of 24 bit. */
    private static final DestinationAudioFormat FLAC_COMPATIBLE_FORMAT = new DestinationAudioFormat (new int []
    {
        8,
        16,
        24
    }, -1, false);


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
     * @return The WAV data of the output file
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
     * @return The WAV data of the output file
     * @throws IOException Could not read or write
     */
    private static byte [] convertToWav (final byte [] inputData, final DestinationAudioFormat destinationFormat) throws IOException
    {
        byte [] data = inputData;

        try
        {
            // Convert a different sample rate with the band-limited resampler before the
            // AudioSystem conversion below. The AudioSystem converter produces a slightly wrong
            // number of frames and drifts in phase, which shifts the audio against the sample and
            // loop positions which the creators re-calculate with the exact rate ratio - audible
            // as a click in the loop.
            final AudioFormat audioFormat = AudioSystem.getAudioFileFormat (new ByteArrayInputStream (data)).getFormat ();
            final int sampleRate = (int) audioFormat.getSampleRate ();
            final int destinationSampleRate = getMatchingSampleRate (sampleRate, destinationFormat);
            if (destinationSampleRate != sampleRate)
                data = AudioSampleReducer.resampleFrequency (data, destinationSampleRate, true);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }

        return convertToWav (new ByteArrayInputStream (data), destinationFormat);
    }


    private static byte [] convertToWav (final InputStream inputStream, final DestinationAudioFormat destinationFormat) throws IOException
    {
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (inputStream))
        {
            final AudioFormat audioFormat = audioInputStream.getFormat ();
            final int [] bitResolutions = destinationFormat.getBitResolutions ();
            final int bitResolution = getMatchingBitResolution (audioFormat.getSampleSizeInBits (), bitResolutions);

            final int sampleRate = getMatchingSampleRate ((int) audioFormat.getSampleRate (), destinationFormat);

            final Encoding encoding = audioFormat.getEncoding ();
            if (encoding == Encoding.PCM_FLOAT && audioFormat.getSampleSizeInBits () == 32)
            {
                // A destination which does not restrict the resolution keeps the float format,
                // otherwise the audio is converted to the integer PCM of the matching resolution.
                // AudioSystem handles 32bit float values incorrect. We need our own implementation.
                if (bitResolutions == null)
                    return doConvertToWav (audioInputStream, new AudioFormat (Encoding.PCM_FLOAT, sampleRate, 32, audioFormat.getChannels (), audioFormat.getFrameSize (), sampleRate, audioFormat.isBigEndian ()));

                final AudioFormat newAudioFormat = new AudioFormat (sampleRate, bitResolution, audioFormat.getChannels (), true, audioFormat.isBigEndian ());
                try (AudioInputStream convertedAudioInputStream = convertAudioStreamFrom32BitFloatToPCM (audioInputStream, audioFormat, newAudioFormat))
                {
                    return doConvertToWav (convertedAudioInputStream, newAudioFormat);
                }
            }

            final AudioFormat newAudioFormat = new AudioFormat (sampleRate, bitResolution, audioFormat.getChannels (), encoding == Encoding.PCM_SIGNED, audioFormat.isBigEndian ());
            return doConvertToWav (audioInputStream, newAudioFormat);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Convert 32-bit float audio to signed integer PCM of 16, 24 or 32 bit. The nominal float range
     * of -1..1 is mapped onto the full scale of the integer format, values beyond it are clipped.
     *
     * @param inputStream The float audio
     * @param sourceAudioFormat The format of the float audio
     * @param destinationAudioFormat The integer PCM format to convert to
     * @return The converted audio
     * @throws IOException Could not read the audio or the resolution is not supported
     */
    private static AudioInputStream convertAudioStreamFrom32BitFloatToPCM (final AudioInputStream inputStream, final AudioFormat sourceAudioFormat, final AudioFormat destinationAudioFormat) throws IOException
    {
        final int bits = destinationAudioFormat.getSampleSizeInBits ();
        if (bits != 16 && bits != 24 && bits != 32)
            throw new IOException (Functions.getMessage ("IDS_WAV_FLOAT_CONVERSION_NOT_SUPPORTED", Integer.toString (bits)));

        final byte [] sourceData = inputStream.readAllBytes ();
        final ByteBuffer inputBuffer = ByteBuffer.wrap (sourceData).order (sourceAudioFormat.isBigEndian () ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        final boolean isBigEndian = destinationAudioFormat.isBigEndian ();
        final int bytesPerSample = bits / 8;
        final int numSamples = sourceData.length / 4;
        final byte [] outputData = new byte [numSamples * bytesPerSample];
        final long maxValue = (1L << bits - 1) - 1;
        for (int i = 0; i < numSamples; i++)
        {
            final long value = Math.clamp (Math.round (inputBuffer.getFloat (i * 4) * (double) maxValue), -maxValue - 1, maxValue);
            final int offset = i * bytesPerSample;
            for (int b = 0; b < bytesPerSample; b++)
                outputData[offset + (isBigEndian ? bytesPerSample - 1 - b : b)] = (byte) (value >> 8 * b & 0xFF);
        }

        final long frameLength = outputData.length / destinationAudioFormat.getFrameSize ();
        return new AudioInputStream (new ByteArrayInputStream (outputData), destinationAudioFormat, frameLength);
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
     * Read a WAV file which does not contain PCM data but a complete Ogg stream, as written by some
     * hosts with one of the Ogg Vorbis WAVE format codes (e.g. the sample files of the legacy
     * DirectWave packs of FL Studio). The Ogg stream is taken out of the data chunk and decoded
     * with the normal Ogg support; the result is a WAV file with PCM data.
     *
     * @param wavFile The file to read
     * @return The de-compressed WAV data or null if the file is not such a WAV file
     * @throws IOException Could not read or decode the file
     */
    public static Optional<byte []> decompressOggInWav (final File wavFile) throws IOException
    {
        final Optional<byte []> oggData = extractOggStream (wavFile);
        if (oggData.isEmpty ())
            return Optional.empty ();
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        decompressToWav (new ByteArrayInputStream (oggData.get ()), out);
        return Optional.of (out.toByteArray ());
    }


    /**
     * Get the Ogg stream from the data chunk of a WAV file which uses one of the Ogg Vorbis WAVE
     * format codes.
     *
     * @param wavFile The file to read
     * @return The Ogg stream or null if the file does not contain one
     * @throws IOException Could not read the file
     */
    private static Optional<byte []> extractOggStream (final File wavFile) throws IOException
    {
        final byte [] content = Files.readAllBytes (wavFile.toPath ());
        if (content.length < 12 || !"RIFF".equals (new String (content, 0, 4, StandardCharsets.US_ASCII)) || !"WAVE".equals (new String (content, 8, 4, StandardCharsets.US_ASCII)))
            return Optional.empty ();

        boolean isOggFormat = false;
        int position = 12;
        while (position + 8 <= content.length)
        {
            final String chunkID = new String (content, position, 4, StandardCharsets.US_ASCII);
            final long chunkSize = Integer.toUnsignedLong (readIntLE (content, position + 4));
            final int dataStart = position + 8;

            if ("fmt ".equals (chunkID) && dataStart + 2 <= content.length)
            {
                final int formatTag = (content[dataStart] & 0xFF) | (content[dataStart + 1] & 0xFF) << 8;
                isOggFormat = formatTag >= FormatChunk.WAVE_FORMAT_OGG_VORBIS_1 && formatTag <= FormatChunk.WAVE_FORMAT_OGG_VORBIS_3 || formatTag >= FormatChunk.WAVE_FORMAT_OGG_VORBIS_1P && formatTag <= FormatChunk.WAVE_FORMAT_OGG_VORBIS_3P;
            }
            else if ("data".equals (chunkID))
            {
                if (!isOggFormat)
                    return Optional.empty ();
                final int length = (int) Math.min (chunkSize, content.length - (long) dataStart);
                // Only a complete Ogg stream can be decoded, other modes store raw Vorbis packets
                if (length < 4 || !"OggS".equals (new String (content, dataStart, 4, StandardCharsets.US_ASCII)))
                    return Optional.empty ();
                final byte [] oggData = new byte [length];
                System.arraycopy (content, dataStart, oggData, 0, length);
                return Optional.of (oggData);
            }

            position = dataStart + (int) chunkSize + ((chunkSize & 1) == 0 ? 0 : 1);
        }
        return Optional.empty ();
    }


    private static int readIntLE (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
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
        try (final FileInputStream fileInputStream = new FileInputStream (inputFile))
        {
            decompressToWav (fileInputStream, outputStream);
        }
    }


    /**
     * De-compresses the input file and writes audio data in WAV format to the given output stream.
     *
     * @param inputStream The input stream to convert
     * @param outputStream The output stream to write to
     * @throws IOException Could not convert or write the file
     */
    public static void decompressToWav (final InputStream inputStream, final OutputStream outputStream) throws IOException
    {
        // AudioSystem.getAudioInputStream requires a stream which supports mark/reset to probe the
        // audio format. A raw ZIP entry stream does not, so wrap it if necessary.
        final InputStream markableStream = inputStream.markSupported () ? inputStream : new BufferedInputStream (inputStream);

        // The conversion needs to be a 2 step process to get the length of the data
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (markableStream))
        {
            final AudioFormat sourceFormat = audioInputStream.getFormat ();
            final int channels = sourceFormat.getChannels ();
            int sampleSizeInBits = sourceFormat.getSampleSizeInBits ();
            if (sampleSizeInBits < 0)
                sampleSizeInBits = 16;

            final AudioFormat convertFormat = new AudioFormat (sourceFormat.getSampleRate (), sampleSizeInBits, channels, true, false);

            // 2-step approach to prevent conversion not supported for a specific reader/writer
            // format combination

            // Step 1 - First convert to raw sample data
            final byte [] audioDataBytes = readAudioData (audioInputStream, convertFormat);

            // Step 2 - Convert from raw data to WAV format. Note: getFrameSize() already accounts
            // for all channels (bytes-per-sample * channels), so it must not be multiplied by the
            // channel count again - doing so halved the frame count for stereo samples and
            // truncated them to half their length.
            final int numFrames = audioDataBytes.length / convertFormat.getFrameSize ();
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


    private static byte [] readAudioData (final AudioInputStream audioInputStream, final AudioFormat convertFormat) throws IOException
    {
        try (final AudioInputStream convertedAudioInputStream = AudioSystem.getAudioInputStream (convertFormat, audioInputStream))
        {
            return convertedAudioInputStream.readAllBytes ();
        }
        catch (final IllegalArgumentException _)
        {
            // Fallback for, e.g., 32-bit FLAC: many FLAC SPIs provide already-decoded PCM bytes
            // during direct reading, even if the format tag still indicates FLAC.
            return audioInputStream.readAllBytes ();
        }
    }


    /**
     * Compresses the given sample data contained in the sampleData object into a FLAC file. Since
     * FLAC supports at maximum a resolution of 24 bit, 32 bit samples are reduced (16 bit for
     * 32-bit float).
     *
     * @param sampleData The sample data
     * @param file The file to write to
     * @throws IOException Could not read/write
     */
    public static void compressToFLAC (final ISampleData sampleData, final File file) throws IOException
    {
        Files.write (file.toPath (), compressToFLAC (sampleData));
    }


    /**
     * Compresses the given sample data contained in the sampleData object into FLAC. Since FLAC
     * supports at maximum a resolution of 24 bit, 32 bit samples are reduced (16 bit for 32-bit
     * float).
     *
     * @param sampleData The sample data
     * @return The FLAC data
     * @throws IOException Could not read the sample data
     */
    public static byte [] compressToFLAC (final ISampleData sampleData) throws IOException
    {
        final WaveFile waveFile = convertToWav (sampleData, FLAC_COMPATIBLE_FORMAT);
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        final int bitsPerSample = formatChunk.getSignificantBitsPerSample ();
        final int bytesPerSample = bitsPerSample / 8;
        final byte [] data = waveFile.getDataChunk ().getData ();

        // De-interleave the samples of the channels; 8 bit WAV data is unsigned and therefore
        // converted to the signed form required by FLAC
        final int numberOfSamples = data.length / (numberOfChannels * bytesPerSample);
        final int [] [] channels = new int [numberOfChannels] [numberOfSamples];
        int position = 0;
        for (int i = 0; i < numberOfSamples; i++)
            for (int channel = 0; channel < numberOfChannels; channel++)
            {
                final int value;
                switch (bytesPerSample)
                {
                    case 1:
                        value = (data[position] & 0xFF) - 128;
                        break;
                    case 2:
                        value = data[position] & 0xFF | data[position + 1] << 8;
                        break;
                    case 3:
                        value = data[position] & 0xFF | (data[position + 1] & 0xFF) << 8 | data[position + 2] << 16;
                        break;
                    default:
                        throw new IOException ("FLAC: Unsupported bit resolution: " + bitsPerSample);
                }
                channels[channel][i] = value;
                position += bytesPerSample;
            }

        return FlacEncoder.encode (channels, formatChunk.getSampleRate (), bitsPerSample);
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
        while (f != null && !f.equals (sourceFolder))
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
     * Checks if the audio described by the given metadata needs to be re-sampled to fulfill the
     * restrictions of the given destination format.
     *
     * @param audioMetadata The metadata of the audio to check
     * @param destinationFormat The destination WAV format configuration
     * @return The resulting bit resolution and sample rate or null if the audio already fulfills
     *         the restrictions
     */
    public static Optional<int []> getRequiredResampling (final IAudioMetadata audioMetadata, final DestinationAudioFormat destinationFormat)
    {
        final int bitResolution = audioMetadata.getBitResolution ();
        final int sampleRate = audioMetadata.getSampleRate ();
        final int destinationBitResolution = getMatchingBitResolution (bitResolution, destinationFormat.getBitResolutions ());
        final int destinationSampleRate = getMatchingSampleRate (sampleRate, destinationFormat);
        if (destinationBitResolution == bitResolution && destinationSampleRate == sampleRate)
            return Optional.empty ();
        return Optional.of (new int []
        {
            destinationBitResolution,
            destinationSampleRate
        });
    }


    /**
     * Get the sample rate to which the given destination format converts the given sample rate: the
     * maximum sample rate if the rate lies above it - or below it with up-sampling enabled -
     * otherwise the rate stays as it is.
     *
     * @param sampleRate The sample rate of the source audio
     * @param destinationFormat The destination WAV format configuration
     * @return The resulting sample rate
     */
    private static int getMatchingSampleRate (final int sampleRate, final DestinationAudioFormat destinationFormat)
    {
        final int maxSampleRate = destinationFormat.getMaxSampleRate ();
        if (maxSampleRate != -1 && (sampleRate > maxSampleRate || destinationFormat.isUpSample ()))
            return maxSampleRate;
        return sampleRate;
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
