// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * Provides access to an AKAI S900/S950 image stored in a file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900DiskImage
{
    /** Number of samples per compression group */
    private static final int                  SAMPLE900COMPR_GROUP_SAMPNUM        = 10;

    /** Offset between bit-count for magnitude and negative code */
    private static final int                  SAMPLE900COMPR_UPBITNUM_NEGCODE_OFF = 16;

    private static final int                  NUM_DIRECTORY_ENTRIES               = 64;
    private static final int                  BLOCK_SIZE                          = 1024;

    private final List<AkaiS900Program>       programs                            = new ArrayList<> ();
    private final Map<String, AkaiS900Sample> samples                             = new HashMap<> ();


    /**
     * Open an image from a file path.
     *
     * @param file The AKAI image file to access
     * @throws IOException If file cannot be opened or partitions could not be loaded
     */
    public AkaiS900DiskImage (final File file) throws IOException
    {
        try (final RandomAccessFile randomAccessFile = new RandomAccessFile (file, "r"))
        {
            for (final AkaiS900DirectoryEntry entry: readDirectory (randomAccessFile))
            {
                final int start = entry.getStartBlock () * BLOCK_SIZE;
                final int length = entry.getLength ();
                randomAccessFile.seek (start);
                final byte [] data = new byte [length];
                if (randomAccessFile.read (data) != length)
                    throw new IOException (Functions.getMessage ("IDS_S900_UNSOUND_FILE", "Could not read item: " + entry.getName ()));

                switch (entry.getType ())
                {
                    case 'P':
                        this.programs.add (readProgram (data));
                        break;
                    case 'S':
                        final AkaiS900Sample sample = readSample (data, entry.getCompression ());
                        this.samples.put (sample.getName (), sample);
                        break;
                    default:
                        // Ignore others
                        break;
                }
            }
        }
    }


    /**
     * Get all read programs.
     *
     * @return THe programs
     */
    public List<AkaiS900Program> getPrograms ()
    {
        return this.programs;
    }


    /**
     * Get all read samples.
     *
     * @return The samples indexed by their name
     */
    public Map<String, AkaiS900Sample> getSamples ()
    {
        return this.samples;
    }


    /**
     * Read the whole directory.
     *
     * @param randomAccessFile The random access file to read from
     * @return The directory entries
     * @throws IOException Could not read
     */
    private static List<AkaiS900DirectoryEntry> readDirectory (final RandomAccessFile randomAccessFile) throws IOException
    {
        final List<AkaiS900DirectoryEntry> entries = new ArrayList<> (NUM_DIRECTORY_ENTRIES);
        for (int i = 0; i < 64; i++)
        {
            final AkaiS900DirectoryEntry entry = new AkaiS900DirectoryEntry (randomAccessFile);
            if (entry.getStartBlock () > 0 && entry.getLength () > 0)
                entries.add (entry);
        }
        return entries;
    }


    /**
     * Read a program.
     *
     * @param data The data of the program
     * @return The read program
     * @throws IOException Could not read the data
     */
    private static AkaiS900Program readProgram (final byte [] data) throws IOException
    {
        try (final ByteArrayInputStream in = new ByteArrayInputStream (data))
        {
            return new AkaiS900Program (in);
        }
    }


    /**
     * Read a sample.
     *
     * @param data The data of the program
     * @param compression The compression info
     * @return The read sample
     * @throws IOException Could not read the data
     */
    private static AkaiS900Sample readSample (final byte [] data, final int compression) throws IOException
    {
        try (final ByteArrayInputStream in = new ByteArrayInputStream (data))
        {
            return new AkaiS900Sample (in, compression);
        }
    }


    /**
     * Creates a WaveFile from the given sample.
     * 
     * @param sample The Akai S900 sample to convert
     * @return The created WaveFile
     * @throws IOException Could not convert the data
     */
    public static WaveFile writeSample (final AkaiS900Sample sample) throws IOException
    {
        final byte [] sampleData = sample.getSampleData ();

        long sampleCount = sample.getSampleLength ();
        if (sampleCount == 0)
            return null;

        // Correct sample count
        // Round up to even number
        final long sampleCountPart = (sampleCount + 1) / 2;

        // *2 for 16bit per WAV sample word
        final byte [] wavBuffer = new byte [(int) (2 * sampleCountPart * 2)];

        if (sample.getCompression () == 0)
            convertNonCompressedSampleToWav (sampleData, wavBuffer, (int) sampleCountPart);
        else
            decodeS900CompressedToWav (sampleData, wavBuffer);

        // Write: Mono, 16-bit (from 12-bit)
        final int sampleRate = sample.getSampleRate ();

        final WaveFile wavFile = new WaveFile (1, sampleRate, 16, (int) sampleCountPart);
        final DataChunk dataChunk = wavFile.getDataChunk ();
        dataChunk.setData (wavBuffer);
        return wavFile;
    }


    private static void convertNonCompressedSampleToWav (final byte [] sampleBuffer, final byte [] wavBuffer, final int sampleCountPart)
    {
        if (sampleBuffer == null || wavBuffer == null || sampleCountPart == 0)
            return;

        // Convert 12bit S900 non-compressed sample format into 16bit WAV sample format

        // The header is followed by the (12-bit signed) sample data, packed in a very strange way:
        // For a sample of N words, the upper 4 bits of the first byte contains the lower 4 bits of
        // the first word. The lower 4 bits of the first byte contain the lower 4 bits of word N/2.
        // The second byte contains the upper 8 bits of the first word. This repeats for the first N
        // bytes, after which there are N/2 bytes containing the upper 8 bits of the last N/2 words.

        // First part
        for (int i = 0; i < sampleCountPart; i++)
        {
            wavBuffer[i * 2 + 1] = sampleBuffer[i * 2 + 1];
            wavBuffer[i * 2] = (byte) (0xF0 & sampleBuffer[i * 2] & 0xFF);
        }

        // Second part
        for (int i = 0; i < sampleCountPart; i++)
        {
            wavBuffer[sampleCountPart * 2 + i * 2 + 1] = sampleBuffer[sampleCountPart * 2 + i];
            wavBuffer[sampleCountPart * 2 + i * 2] = (byte) (0xF0 & (sampleBuffer[i * 2] & 0xFF) << 4);
        }
    }


    private static int decodeS900CompressedToWav (final byte [] source, final byte [] destination)
    {
        if (source == null || destination == null)
            return 0;
        final int sourceSize = source.length;
        final int destinationSize = destination.length;
        if (sourceSize == 0 || destinationSize == 0)
            return 0;

        short currentValue = 0;
        short currentIncrement = 0;

        int remainingBits = sourceSize * 8;
        int bitPosition = 0;
        int outputPosition = 0;

        while (remainingBits > 0 && outputPosition + 1 < destinationSize)
        {
            if (remainingBits < 4)
                break;

            final int code = getBits (source, bitPosition, 4);
            bitPosition += 4;
            remainingBits -= 4;
            if (code == 0)
            {
                for (int i = 0; i < SAMPLE900COMPR_GROUP_SAMPNUM && outputPosition + 1 < destinationSize; i++)
                {
                    currentValue += currentIncrement;
                    writeSample (destination, outputPosition, currentValue);
                    outputPosition += 2;
                }
                continue;
            }

            final int bitsPerValue = SAMPLE900COMPR_UPBITNUM_NEGCODE_OFF - code;
            final int requiredBits = SAMPLE900COMPR_GROUP_SAMPNUM * (1 + bitsPerValue);
            if (remainingBits < requiredBits)
                break;

            for (int i = 0; i < SAMPLE900COMPR_GROUP_SAMPNUM && outputPosition + 1 < destinationSize; i++)
            {
                final int sign = getBits (source, bitPosition + i, 1);
                final int magnitude = getBits (source, bitPosition + SAMPLE900COMPR_GROUP_SAMPNUM + i * bitsPerValue, bitsPerValue);
                if (sign == 0)
                    currentIncrement += magnitude;
                else
                    currentIncrement -= magnitude;

                currentValue += currentIncrement;
                writeSample (destination, outputPosition, currentValue);
                outputPosition += 2;
            }

            bitPosition += requiredBits;
            remainingBits -= requiredBits;
        }

        return outputPosition;
    }


    private static int getBits (final byte [] buffer, final int bitOffsetIn, final int bitCount)
    {
        if (buffer == null || bitCount == 0)
            return 0;

        int bitOffset = bitOffsetIn;
        int value = 0;
        for (int i = 0; i < bitCount; i++, bitOffset++)
        {
            final int byteIndex = bitOffset >>> 3;
            final int mask = 1 << 7 - (bitOffset & 7);
            value <<= 1;
            if ((buffer[byteIndex] & mask) != 0)
                value |= 1;
        }

        return value;
    }


    private static void writeSample (final byte [] output, final int position, final short sample)
    {
        output[position] = (byte) (0xF0 & sample << 4 & 0xFF);
        output[position + 1] = (byte) (sample >> 4 & 0xFF);
    }
}