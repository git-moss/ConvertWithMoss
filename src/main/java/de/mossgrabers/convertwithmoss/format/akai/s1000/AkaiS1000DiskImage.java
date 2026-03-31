// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

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
public class AkaiS1000DiskImage implements AutoCloseable, IAkaiImage
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
    private final boolean             isS3000;


    /**
     * Open an image from a file path.
     *
     * @param file The AKAI image file to access
     * @param isS3000 True if it is a S3000 series image otherwise S1000 series
     * @throws IOException If file cannot be opened or partitions could not be loaded
     */
    public AkaiS1000DiskImage (final File file, final boolean isS3000) throws IOException
    {
        this.isS3000 = isS3000;
        this.randomAccessFile = new RandomAccessFile (file, "r");
        this.size = (int) file.length ();

        this.loadPartitions ();
    }


    /**
     * Check if it is a S3000 series image otherwise S1000 series.
     * 
     * @return True if it is a S3000 series image otherwise S1000 series
     */
    public boolean isS3000 ()
    {
        return this.isS3000;
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


    /** {@inheritDoc} */
    @Override
    public int available ()
    {
        return this.size - this.pos;
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
        akaiToAscii (buffer, length);
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
        int readBytes = 0;
        int sizeToRead = wordCount * wordSize;

        while (sizeToRead > 0)
        {
            if (this.size <= this.pos)
                return readBytes / wordSize;

            // Read the requested cluster into the cache
            final int requestedCluster = this.pos / AkaiS1000DiskImage.DISK_CLUSTER_SIZE;
            if (this.cluster != requestedCluster)
            {
                this.cluster = requestedCluster;

                final long seekPos = (long) this.cluster * AkaiS1000DiskImage.DISK_CLUSTER_SIZE;
                this.randomAccessFile.seek (seekPos);
                final int bytesRead = this.randomAccessFile.read (this.cache, 0, AkaiS1000DiskImage.DISK_CLUSTER_SIZE);
                if (bytesRead < AkaiS1000DiskImage.DISK_CLUSTER_SIZE)
                    // Fill with zeros if cluster is shorter
                    for (int i = bytesRead; i < AkaiS1000DiskImage.DISK_CLUSTER_SIZE; i++)
                        this.cache[i] = 0;
            }

            int currentReadSize = sizeToRead;
            final int posInCluster = this.pos % AkaiS1000DiskImage.DISK_CLUSTER_SIZE;
            if (currentReadSize > AkaiS1000DiskImage.DISK_CLUSTER_SIZE - posInCluster)
                currentReadSize = AkaiS1000DiskImage.DISK_CLUSTER_SIZE - posInCluster;

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