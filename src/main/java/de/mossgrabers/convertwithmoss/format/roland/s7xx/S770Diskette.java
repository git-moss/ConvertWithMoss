// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Parser for a Roland S-770 3.5" HD floppy-diskette image.
 *
 * @author Jürgen Moßgraber
 */
public class S770Diskette implements IS770Image
{
    private static final int                SAMPLE_BLOCK_SIZE = 0x2400;

    private final S770Header                header;
    private final S770DisketteDirectoryArea directoryArea;

    private List<S770Performance>           performances;
    private List<S770Patch>                 patches;
    private List<S770Partial>               partials;
    private List<S770Sample>                samples;

    private final List<byte []>             continuationData;


    /**
     * Parses a Roland S-770 diskette image from an already-open {@link InputStream}. The stream
     * must be positioned after the header area (0x200).
     *
     * @param in Stream positioned at 0x200
     * @param header The already read header
     * @param continuationData If the disk content is split over several disks, this array contains
     *            the additional sample data from the continuation disks
     * @throws IOException if the stream cannot be read
     */
    public S770Diskette (final InputStream in, final S770Header header, final List<byte []> continuationData) throws IOException
    {
        this.header = header;
        this.continuationData = continuationData;
        this.directoryArea = this.readDirectoryArea (in);

        this.readParameterArea (in);
    }


    /** {@inheritDoc} */
    @Override
    public S770Header getHeader ()
    {
        return this.header;
    }


    /**
     * Looks for continuation disks. A continuation disk must match the given name, the expected
     * index and the number of overall continuation disks.
     *
     * @param diskName The name of the disk
     * @param index The index of the continuation disk
     * @param numContinuationDiskettes The overall number of continuation disks
     * @param parentPath The path in which to look for the disk file
     * @return The data of the file if found otherwise null
     */
    public static Optional<byte []> findContinuationDisk (final String diskName, final int index, final int numContinuationDiskettes, final File parentPath)
    {
        // Search the continuation file in the same folder as the 1st file
        for (final File childFile: parentPath.listFiles ())
        {
            if (!childFile.isFile ())
                continue;

            try (final RandomAccessFile raf = new RandomAccessFile (childFile, "r"))
            {
                // Must be longer than the start of the sample area
                if (raf.length () < 0x1F800)
                    continue;

                raf.seek (4);
                if (!"S770".equals (StreamUtils.readAscii (raf, 4)))
                    continue;

                // Check if the file matches the continuation index and number of expected diskettes
                raf.seek (0x100);
                if (raf.readUnsignedByte () == index && raf.readUnsignedByte () == numContinuationDiskettes)
                {
                    // Disk names must match as well
                    raf.seek (0x180);
                    final String name = StreamUtils.readAscii (raf, 16);
                    if (diskName.equals (name))
                    {
                        raf.seek (0x1F800);
                        final long sampleLength = raf.length () - raf.getFilePointer ();
                        final byte [] sampleData = new byte [(int) sampleLength];
                        raf.readFully (sampleData);
                        return Optional.of (sampleData);
                    }
                }
            }
            catch (final IOException _)
            {
                // Ignore and continue
            }
        }

        return Optional.empty ();
    }


    /**
     * Reads the compact directory area.
     *
     * @param input The input stream to read from
     * @return The read directory
     * @throws IOException Could not read the directory
     */
    private S770DisketteDirectoryArea readDirectoryArea (final InputStream input) throws IOException
    {
        return new S770DisketteDirectoryArea (input, this.header.getNumPerformances (), this.header.getNumPatches (), this.header.getNumPartials (), this.header.getNumSamples ());
    }


    /**
     * Reads the parameter area.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the parameters
     */
    private void readParameterArea (final InputStream input) throws IOException
    {
        final int numPerformances = this.header.getNumPerformances ();
        final int numPatches = this.header.getNumPatches ();
        final int numPartials = this.header.getNumPartials ();
        final int numSamples = this.header.getNumSamples ();

        // Performance entries
        this.performances = new ArrayList<> (numPerformances);
        for (int i = 0; i < numPerformances; i++)
            this.performances.add (new S770Performance (input, true));

        input.skipNBytes ((64 - numPerformances) * 256L);

        // Patch entries
        this.patches = new ArrayList<> (numPatches);
        for (int i = 0; i < numPatches; i++)
            this.patches.add (new S770Patch (input, true));

        input.skipNBytes ((128 - numPatches) * 256L);

        // Partial entries
        this.partials = new ArrayList<> (numPartials);
        for (int i = 0; i < numPartials; i++)
            this.partials.add (new S770Partial (input));

        input.skipNBytes ((256 - numPartials) * 128L);

        // Sample entries
        int read = 0;
        this.samples = new ArrayList<> (numSamples);
        for (int i = 0; i < numSamples; i++)
        {
            this.samples.add (new S770Sample (input));
            read += 48;
            // Each 10th sample seem to have additional 32 bytes appended
            if ((i + 1) % 10 == 0)
            {
                input.skipNBytes (32);
                read += 32;
            }
        }

        // Samples start at 0x1F800
        input.skipNBytes (0x6A00 - (long) read);

        this.loadWaveData (input);
    }


    private void loadWaveData (final InputStream input) throws IOException
    {
        byte [] fullSampleData = input.readAllBytes ();

        // 720KB = 66 blocks, 1.44MB = 146 blocks
        final int numBlocks = fullSampleData.length / SAMPLE_BLOCK_SIZE;

        // Load all samples
        if (!this.continuationData.isEmpty ())
        {
            final List<byte []> allParts = new ArrayList<> ();
            allParts.add (fullSampleData);
            allParts.addAll (this.continuationData);
            int fullLength = 0;
            for (final byte [] part: allParts)
                fullLength += part.length;
            fullSampleData = new byte [fullLength];
            int pos = 0;
            for (final byte [] part: allParts)
            {
                System.arraycopy (part, 0, fullSampleData, pos, part.length);
                pos += part.length;
            }
        }

        for (final S770Sample sample: this.samples)
        {
            final int startBlock = sample.getSegmentTop ();
            // Sample data split among several diskettes use 256 virtual blocks. But 720KB diskettes
            // contain 66 and 1.44MB diskettes contain 148 physical blocks. The following line
            // removes the empty gaps...
            final int physicalStartBlock = startBlock / 256 * numBlocks + startBlock % 256;
            final int sampleStart = physicalStartBlock * SAMPLE_BLOCK_SIZE;
            if (sampleStart > fullSampleData.length)
                throw new IOException (Functions.getMessage ("IDS_S7XX_SAMPLE_DATA_MISSING"));

            final int sampleLength = sample.getSegmentLength () * SAMPLE_BLOCK_SIZE;
            if (sampleStart + sampleLength > fullSampleData.length)
                throw new IOException (Functions.getMessage ("IDS_S7XX_SAMPLE_DATA_MISSING"));

            final byte [] sampleData = new byte [sampleLength];
            System.arraycopy (fullSampleData, sampleStart, sampleData, 0, sampleLength);
            sample.setWaveData (sampleData);
        }
    }


    /**
     * Get the diskette directories.
     *
     * @return The compact directory area
     */
    public S770DisketteDirectoryArea getDirectoryArea ()
    {
        return this.directoryArea;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Performance> getPerformances ()
    {
        return this.performances;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Patch> getPatches ()
    {
        return this.patches;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Partial> getPartials ()
    {
        return this.partials;
    }


    /** {@inheritDoc} */
    @Override
    public List<S770Sample> getSamples ()
    {
        return this.samples;
    }


    private String parameterAreaToString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("S770DisketteParameterArea [\n");
        sb.append ("  performanceEntries=").append (this.performances.size ()).append ('\n');
        sb.append ("  patchEntries=").append (this.patches.size ()).append ('\n');
        sb.append ("  partialEntries=").append (this.partials.size ()).append ('\n');
        sb.append ("  sampleEntries=").append (this.samples.size ()).append ('\n');
        sb.append (']');
        return sb.toString ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "RolandS770Diskette {\n\n" + this.header + "\n\n" + this.directoryArea + "\n\n" + this.parameterAreaToString () + "\n}";
    }
}