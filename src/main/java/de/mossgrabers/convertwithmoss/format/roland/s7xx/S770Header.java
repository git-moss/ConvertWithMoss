// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 ID Area.
 *
 * @author Jürgen Moßgraber
 */
public class S770Header
{
    private static final int     DISKETTE_SIZE    = 1474560;

    private final long           revision;
    private final String         s70Str;
    private final String         versionStr;
    private final String         copyrightStr;
    private final S770DiskFormat diskFormat;
    private String               diskName;
    private long                 diskCapacity;
    private int                  numVolumes;
    private int                  numPerformances;
    private int                  numPatches;
    private int                  numPartials;
    private int                  numSamples;

    private final int []         disketteFatWords = new int [4];


    /**
     * Reads and parses the ID area from the stream. Automatically detects whether the image is a
     * CD-ROM or diskette.
     *
     * @param input Stream positioned at the very start of the S-770 image
     * @throws IOException on read error or unexpected EOF
     */
    public S770Header (final InputStream input) throws IOException
    {
        this.diskFormat = input.available () > DISKETTE_SIZE ? S770DiskFormat.CD_ROM : S770DiskFormat.DISKETTE;

        // Common header (bytes 0x00–0x5F, 96 bytes total)
        this.revision = StreamUtils.readUnsigned32 (input, false);
        this.s70Str = StreamUtils.readAscii (input, 10);
        input.skipNBytes (2);
        // Empty string
        StreamUtils.readAscii (input, 15);
        input.skipNBytes (1);
        this.versionStr = StreamUtils.readAscii (input, 31);
        input.skipNBytes (1);
        this.copyrightStr = StreamUtils.readAscii (input, 31);
        input.skipNBytes (161);

        // Media-specific tail (bytes 0x100–0x1FF, 256 bytes)
        if (this.diskFormat == S770DiskFormat.DISKETTE)
            this.readDisketteTail (input);
        else
            this.readCdRomTail (input);
    }


    /**
     * Reads the CD-ROM tail fields from the current stream position (0x100). Consumes exactly 256
     * bytes.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read the tail
     */
    private void readCdRomTail (final InputStream input) throws IOException
    {
        this.diskName = StreamUtils.readAscii (input, 16); // 0x100–0x10F
        this.diskCapacity = StreamUtils.readUnsigned32 (input, false); // 0x110–0x113
        this.numVolumes = StreamUtils.readUnsigned16 (input, false); // 0x114–0x115
        this.numPerformances = StreamUtils.readUnsigned16 (input, false); // 0x116–0x117
        this.numPatches = StreamUtils.readUnsigned16 (input, false); // 0x118–0x119
        this.numPartials = StreamUtils.readUnsigned16 (input, false); // 0x11A–0x11B
        this.numSamples = StreamUtils.readUnsigned16 (input, false); // 0x11C–0x11D
        input.skipNBytes (226); // 0x11E–0x1FF
    }


    /**
     * Reads the diskette tail fields from the current stream position (0x100).
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read the tail
     */
    private void readDisketteTail (final InputStream input) throws IOException
    {
        for (int i = 0; i < 4; i++)
            this.disketteFatWords[i] = StreamUtils.readUnsigned16 (input, false); // 0x100–0x107
        this.numVolumes = 1;
        this.numPerformances = StreamUtils.readUnsigned16 (input, false); // 0x108–0x109
        this.numPatches = StreamUtils.readUnsigned16 (input, false); // 0x10A–0x10B
        this.numPartials = StreamUtils.readUnsigned16 (input, false); // 0x10C–0x10D
        this.numSamples = StreamUtils.readUnsigned16 (input, false); // 0x10E–0x10F
        this.diskCapacity = StreamUtils.readUnsigned16 (input, false); // 0x110–0x111
        input.skipNBytes (110); // 0x112–0x17F (0xFF pad)
        this.diskName = StreamUtils.readAscii (input, 16); // 0x180–0x18F
        input.skipNBytes (16); // 0x190–0x19F

        // Unclear 1A0-1FF
        input.skipNBytes (96);
    }


    /** @return Detected storage medium type. */
    public S770DiskFormat getDiskFormat ()
    {
        return this.diskFormat;
    }


    /**
     * Get the revision number.
     * 
     * @return The revision
     */
    public long getRevision ()
    {
        return this.revision;
    }


    /**
     * Get the S770 version text.
     * 
     * @return The text
     */
    public String getVersionStr ()
    {
        return this.versionStr;
    }


    /**
     * Get the S770 copyright text.
     * 
     * @return The text
     */
    public String getCopyrightStr ()
    {
        return this.copyrightStr;
    }


    /**
     * Get the name of the disk.
     * 
     * @return The name, might be empty
     */
    public String getDiskName ()
    {
        return this.diskName;
    }


    /**
     * Get the disk capacity.
     * 
     * @return The capacity
     */
    public long getDiskCapacity ()
    {
        return this.diskCapacity;
    }


    /**
     * The number of volumes.
     * 
     * @return Always returns 1 for diskettes
     */
    public int getNumVolumes ()
    {
        return this.numVolumes;
    }


    /**
     * Get the number of performances.
     * 
     * @return The number of performances
     */
    public int getNumPerformances ()
    {
        return this.numPerformances;
    }


    /**
     * Get the number of patches.
     * 
     * @return The number of patches
     */
    public int getNumPatches ()
    {
        return this.numPatches;
    }


    /**
     * Get the number of partials.
     * 
     * @return The number of partials
     */
    public int getNumPartials ()
    {
        return this.numPartials;
    }


    /**
     * Get the number of samples.
     * 
     * @return The number of samples
     */
    public int getNumSamples ()
    {
        return this.numSamples;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("S770IdArea [\n");
        sb.append ("  diskFormat=").append (this.diskFormat).append ('\n');
        sb.append ("  revision=").append (this.revision).append ('\n');
        sb.append ("  s70Str='").append (this.s70Str.trim ()).append ("'\n");
        sb.append ("  versionStr='").append (this.versionStr.trim ()).append ("'\n");
        sb.append ("  copyrightStr='").append (this.copyrightStr.trim ()).append ("'\n");
        sb.append ("  diskName='").append (this.diskName.trim ()).append ("'\n");
        sb.append ("  diskCapacity=").append (this.diskCapacity).append ('\n');
        sb.append ("  numVolumes=").append (this.numVolumes).append ('\n');
        sb.append ("  numPerformances=").append (this.numPerformances).append ('\n');
        sb.append ("  numPatches=").append (this.numPatches).append ('\n');
        sb.append ("  numPartials=").append (this.numPartials).append ('\n');
        sb.append ("  numSamples=").append (this.numSamples).append ('\n');
        if (this.diskFormat == S770DiskFormat.DISKETTE)
            for (int i = 0; i < this.disketteFatWords.length; i++)
                sb.append ("  disketteFatWord" + i + "=0x").append (Integer.toHexString (this.disketteFatWords[i])).append ('\n');
        sb.append (']');
        return sb.toString ();
    }
}