// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.diskformat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.format.akai.s1000.AkaiS1000Volume;
import de.mossgrabers.tools.ui.Functions;


/**
 * Provides access to an AKAI image stored in a file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiDiskImage extends AbstractAkaiImage
{
    /** Size of a block. */
    public static final int           AKAI_BLOCK_SIZE         = 0x2000;

    /**
     * The cache window size defined here is NOT the file-system allocation block size used by the
     * Akai sampler volume. It is only a performance optimization for RandomAccessFile I/O. Akai
     * sampler file-systems (S900/S1000/S2000/S3000/MPC raw volumes) typically use allocation block
     * sizes of 512 bytes 1024 bytes 2048 bytes depending on sampler generation and volume type.
     * These values must be handled separately when resolving object offsets.
     */
    private static final int          CACHE_WINDOW_SIZE       = 61440;

    private static final int          AKAI_PARTITION_END_MARK = 0x8000;

    private RandomAccessFile          randomAccessFile        = null;
    private int                       position                = 0;
    private int                       cacheWindowIndex        = -1;
    private final int                 size;
    private byte []                   cache                   = new byte [CACHE_WINDOW_SIZE];
    private final List<AkaiPartition> partitions              = new ArrayList<> ();


    /**
     * Open an image from a file path.
     *
     * @param file The AKAI image file to access
     * @throws IOException If file cannot be opened or partitions could not be loaded
     */
    public AkaiDiskImage (final File file) throws IOException
    {
        this.randomAccessFile = new RandomAccessFile (file, "r");
        this.size = (int) file.length ();

        this.loadPartitions ();
    }


    /** {@inheritDoc} */
    @Override
    public void close () throws IOException
    {
        if (this.randomAccessFile != null)
        {
            this.randomAccessFile.close ();
            this.randomAccessFile = null;
        }
        this.cache = null;
    }


    /**
     * Read the content of a volume in a specific format (S1000, S3000, MPC2000, ...).
     * 
     * @param partition The partition which contains the volume to read
     * @param entry The directory entry of the volume
     * @return The content if supported and the volume is not empty
     * @throws IOException Could not read the volume
     */
    public Optional<IAkaiVolume> readVolume (final AkaiPartition partition, final AkaiDirEntry entry) throws IOException
    {
        final AkaiVolumeType type = AkaiVolumeType.fromTypeId (entry.getType ());
        switch (type)
        {
            case S1000:
                return Optional.of (new AkaiS1000Volume (this, partition, entry, false));
            case S3000_PRE:
            case S3000:
                return Optional.of (new AkaiS1000Volume (this, partition, entry, true));
            case NOT_USED:
                return Optional.empty ();
            default:
                throw new IOException (Functions.getMessage ("IDS_ISO_UNSUPPORTED_FORMAT", type.getName ()));
        }
    }


    /**
     * Get the current position in the stream.
     *
     * @return The position
     */
    public int getPosition ()
    {
        return this.position;
    }


    /**
     * Set the position in the stream relative to a given start position (START, END or CURRENT).
     *
     * @param offset Target position
     * @param whence Reference point for position
     * @return The new position
     */
    public int setPosition (final int offset, final AkaiStreamWhence whence)
    {
        this.position = switch (whence)
        {
            case START -> offset;
            case CURRENT_POSITION -> this.position + offset;
            case END -> Math.max (0, this.size - offset);
            default -> offset;
        };
        return this.position;
    }


    /** {@inheritDoc} */
    @Override
    public int available ()
    {
        return this.size - this.position;
    }


    /** {@inheritDoc} */
    @Override
    public String readText () throws IOException
    {
        return this.readText (MAX_TEXT_LENGTH);
    }


    /** {@inheritDoc} */
    @Override
    public String readText (final int length) throws IOException
    {
        final byte [] buffer = new byte [length];
        this.read (buffer, length, 1);
        IAkaiImage.akaiToAscii (buffer, length);
        return new String (buffer, 0, length).trim ();
    }


    /** {@inheritDoc} */
    @Override
    public byte readInt8 () throws IOException
    {
        final byte [] word = new byte [1];
        this.read (word, 1, 1);
        return word[0];
    }


    /** {@inheritDoc} */
    @Override
    public void readInt8 (final byte [] data, final int wordCount) throws IOException
    {
        this.read (data, wordCount, 1);
    }


    /** {@inheritDoc} */
    @Override
    public short readInt16 () throws IOException
    {
        final byte [] word = new byte [2];
        this.read (word, 1, 2);
        return ByteBuffer.wrap (word).order (ByteOrder.LITTLE_ENDIAN).getShort ();
    }


    /** {@inheritDoc} */
    @Override
    public void readInt16 (final short [] data, final int wordCount) throws IOException
    {
        for (int i = 0; i < wordCount; i++)
            data[i] = this.readInt16 ();
    }


    /** {@inheritDoc} */
    @Override
    public int readInt32 () throws IOException
    {
        final byte [] word = new byte [4];
        this.read (word, 1, 4);
        return ByteBuffer.wrap (word).order (ByteOrder.LITTLE_ENDIAN).getInt ();
    }


    /**
     * Get size of the image.
     *
     * @return The size in bytes
     */
    public int getSize ()
    {
        return this.size;
    }


    /**
     * Get the number of partitions.
     *
     * @return The number of partitions in the range of [0..9]
     */
    public int getPartitionCount ()
    {
        return this.partitions.size ();
    }


    /**
     * Get a single partition.
     *
     * @param index The index of partition
     * @return The partition or null if the index is out of range
     */
    public AkaiPartition getPartition (final int index)
    {
        return index >= 0 && index < this.partitions.size () ? this.partitions.get (index) : null;
    }


    /**
     * Get the partitions.
     *
     * @return The partitions
     */
    public List<AkaiPartition> getPartitions ()
    {
        return this.partitions;
    }


    /**
     * Read data from stream with caching.
     *
     * @param data Buffer to read into
     * @param wordCount Number of words to read
     * @param wordSize Size of each word in bytes
     * @return Number of successfully read words
     * @throws IOException Error during reading
     */
    private int read (final byte [] data, final int wordCount, final int wordSize) throws IOException
    {
        int bytesReadTotal = 0;
        int bytesRemaining = wordCount * wordSize;

        while (bytesRemaining > 0)
        {
            // Stop if logical read position exceeds disk image size
            if (this.size <= this.position)
                return bytesReadTotal / wordSize;

            // Determine which cache window contains the requested position. The cache window is a
            // fixed-size sliding region of the disk image. It has NO relationship to Akai
            // file system allocation units.
            final int requestedWindow = this.position / CACHE_WINDOW_SIZE;

            // Load cache window if necessary
            if (this.cacheWindowIndex != requestedWindow)
            {
                this.cacheWindowIndex = requestedWindow;

                final long seekPosition = (long) this.cacheWindowIndex * CACHE_WINDOW_SIZE;
                this.randomAccessFile.seek (seekPosition);
                final int windowBytesRead = this.randomAccessFile.read (this.cache, 0, CACHE_WINDOW_SIZE);

                // If the final window is shorter than expected, pad with zeros. This simplifies
                // boundary handling during partial reads.
                if (windowBytesRead < CACHE_WINDOW_SIZE)
                    for (int i = windowBytesRead; i < CACHE_WINDOW_SIZE; i++)
                        this.cache[i] = 0;
            }

            int bytesThisIteration = bytesRemaining;

            final int offsetInsideWindow = this.position % CACHE_WINDOW_SIZE;

            final int windowBytesAvailable = CACHE_WINDOW_SIZE - offsetInsideWindow;
            if (bytesThisIteration > windowBytesAvailable)
                bytesThisIteration = windowBytesAvailable;

            // Copy data from cache window into destination buffer
            System.arraycopy (this.cache, offsetInsideWindow, data, bytesReadTotal, bytesThisIteration);

            // Advance logical read position
            this.position += bytesThisIteration;
            bytesReadTotal += bytesThisIteration;
            bytesRemaining -= bytesThisIteration;
        }

        return bytesReadTotal / wordSize;
    }


    private void loadPartitions () throws IOException
    {
        int offset = 0;
        short size = 0;

        int partitionIndex = 0;
        while (size != (short) AKAI_PARTITION_END_MARK && size != (short) 0x0fff && size != (short) 0xffff && size < 30720 && this.partitions.size () < 9)
        {
            final AkaiPartition partition = new AkaiPartition (this, offset, partitionIndex);

            if (!partition.getVolumes ().isEmpty ())
                this.partitions.add (partition);

            this.setPosition (offset, AkaiStreamWhence.START);
            size = this.readInt16 ();
            if (size <= 0)
                break;

            offset += AKAI_BLOCK_SIZE * (size & 0xFFFF);
            partitionIndex++;
        }
    }
}