// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s3000;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;


/**
 * Provides access to an AKAI image stored in a file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiDiskImage implements AutoCloseable
{
    private static final int          MAX_TEXT_LENGTH         = 12;
    private static final int          DISK_CLUSTER_SIZE       = 61440;
    private static final int          AKAI_PARTITION_END_MARK = 0x8000;

    private RandomAccessFile          randomAccessFile        = null;
    private int                       pos                     = 0;
    private int                       cluster                 = -1;
    private final int                 size;
    private byte []                   cache                   = new byte [DISK_CLUSTER_SIZE];
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
     * Get the current position in the stream.
     *
     * @return The position
     */
    public int getPos ()
    {
        return this.pos;
    }


    /**
     * Set the position in the stream relative to a given start position (START, END or CURRENT).
     *
     * @param offset Target position
     * @param whence Reference point for position
     * @return The new position
     */
    public int setPos (final int offset, final AkaiStreamWhence whence)
    {
        this.pos = switch (whence)
        {
            case START -> offset;
            case CURRENT_POSITION -> this.pos + offset;
            case END -> Math.max (0, this.size - offset);
            default -> offset;
        };
        return this.pos;
    }


    /**
     * Get the number of available bytes from the current read position.
     *
     * @return The number of available bytes
     */
    public int available ()
    {
        return this.size - this.pos;
    }


    /**
     * Reads a text in 12-byte Akai format.
     *
     * @return The read text, trimmed ASCII
     * @throws IOException Could not read the text
     */
    public String readText () throws IOException
    {
        final byte [] buffer = new byte [MAX_TEXT_LENGTH + 1];
        this.read (buffer, MAX_TEXT_LENGTH, 1);
        akaiToAscii (buffer, MAX_TEXT_LENGTH);
        return new String (buffer, 0, MAX_TEXT_LENGTH).trim ();
    }


    /**
     * Read single 8-bit value.
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    public byte readInt8 () throws IOException
    {
        final byte [] word = new byte [1];
        this.read (word, 1, 1);
        return word[0];
    }


    /**
     * Read single 16-bit value (little-endian).
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    public short readInt16 () throws IOException
    {
        final byte [] word = new byte [2];
        this.read (word, 1, 2);
        return ByteBuffer.wrap (word).order (ByteOrder.LITTLE_ENDIAN).getShort ();
    }


    /**
     * Read single 32-bit value (little-endian).
     *
     * @return The value
     * @throws IOException Could not read the value
     */
    public int readInt32 () throws IOException
    {
        final byte [] word = new byte [4];
        this.read (word, 1, 4);
        return ByteBuffer.wrap (word).order (ByteOrder.LITTLE_ENDIAN).getInt ();
    }


    /**
     * Reads an array of 8-bit values.
     *
     * @param data The array in which to store the read data
     * @param wordCount Number of words to read
     * @throws IOException Could not read the value
     */
    public void readInt8 (final byte [] data, final int wordCount) throws IOException
    {
        this.read (data, wordCount, 1);
    }


    /**
     * Read array of 16-bit values (little-endian).
     *
     * @param data The array in which to store the read data
     * @param wordCount Number of words to read
     * @throws IOException Could not read the value
     */
    public void readInt16 (final short [] data, final int wordCount) throws IOException
    {
        for (int i = 0; i < wordCount; i++)
            data[i] = this.readInt16 ();
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


    private static void akaiToAscii (final byte [] buffer, final int length)
    {
        for (int i = 0; i < length; i++)
        {
            final int b = buffer[i] & 0xFF;
            if (b >= 0 && b <= 9)
                buffer[i] = (byte) (b + 48);
            else if (b == 10)
                buffer[i] = 32;
            else if (b >= 11 && b <= 36)
                buffer[i] = (byte) (64 + b - 10);
            else
                buffer[i] = 32;
        }
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
        int readBytes = 0;
        int sizeToRead = wordCount * wordSize;

        while (sizeToRead > 0)
        {
            if (this.size <= this.pos)
                return readBytes / wordSize;

            // Read the requested cluster into the cache
            final int requestedCluster = this.pos / AkaiDiskImage.DISK_CLUSTER_SIZE;
            if (this.cluster != requestedCluster)
            {
                this.cluster = requestedCluster;

                final long seekPos = (long) this.cluster * AkaiDiskImage.DISK_CLUSTER_SIZE;
                this.randomAccessFile.seek (seekPos);
                final int bytesRead = this.randomAccessFile.read (this.cache, 0, AkaiDiskImage.DISK_CLUSTER_SIZE);
                if (bytesRead < AkaiDiskImage.DISK_CLUSTER_SIZE)
                    // Fill with zeros if cluster is shorter
                    for (int i = bytesRead; i < AkaiDiskImage.DISK_CLUSTER_SIZE; i++)
                        this.cache[i] = 0;
            }

            int currentReadSize = sizeToRead;
            final int posInCluster = this.pos % AkaiDiskImage.DISK_CLUSTER_SIZE;
            if (currentReadSize > AkaiDiskImage.DISK_CLUSTER_SIZE - posInCluster)
                currentReadSize = AkaiDiskImage.DISK_CLUSTER_SIZE - posInCluster;

            System.arraycopy (this.cache, posInCluster, data, readBytes, currentReadSize);

            this.pos += currentReadSize;
            readBytes += currentReadSize;
            sizeToRead -= currentReadSize;
        }

        return readBytes / wordSize;
    }


    private void loadPartitions () throws IOException
    {
        int offset = 0;
        short size = 0;

        int partitionIndex = 0;
        while (size != (short) AKAI_PARTITION_END_MARK && size != (short) 0x0fff && size != (short) 0xffff && size < 30720 && this.partitions.size () < 9)
        {
            final AkaiPartition partition = new AkaiPartition (this, partitionIndex);
            partition.setOffset (offset);

            if (!partition.getVolumes ().isEmpty ())
                this.partitions.add (partition);

            this.setPos (offset, AkaiStreamWhence.START);
            size = this.readInt16 ();
            if (size <= 0)
                break;

            offset += AkaiDiskElement.AKAI_BLOCK_SIZE * (size & 0xFFFF);
            partitionIndex++;
        }
    }
}