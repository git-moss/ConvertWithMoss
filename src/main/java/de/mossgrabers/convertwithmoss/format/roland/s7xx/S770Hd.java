// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;


/**
 * Reads Roland S-770 CD-ROM or hard-disk images.
 *
 * <h3>On-disk layout (CD-ROM / HD)</h3> <pre>
 *   Offset      Size        Content
 *   0x000000    0x000200    ID Area        (512 B)
 *   0x000200    0x000600    Reserved Area  (1 536 B – skipped)
 *   0x000800    0x080000    Program Text   (512 KB – skipped)
 *   0x080800    0x020000    FAT Area       (128 KB – skipped)
 *   0x0A0800    0x06D000    Directory Area
 *   0x10D800    0x1A8000    Parameter Area
 * </pre>
 *
 * @author Jürgen Moßgraber
 */
public class S770Hd
{
    private static final long       SIZE_RESERVED     = 0x600L;
    private static final long       SIZE_PROGRAM_TEXT = 0x80000L;
    private static final long       SIZE_FAT          = 0x20000L;

    private final S770Header        idArea;
    private final S770HdDirectoryArea directoryArea;
    private final S770ParameterArea parameterArea;


    /**
     * Parses a Roland S-770 CD-ROM / HD image from an already-open {@link InputStream}. The stream
     * must be positioned at byte 0 (start of the disk image). The caller is responsible for closing
     * the stream.
     *
     * @param in Stream positioned at the beginning of the disk image
     * @param header The already read header of the disk
     * @throws IOException if the stream cannot be read or is not a CD-ROM/HD format image
     */
    public S770Hd (final InputStream in, final S770Header header) throws IOException
    {
        this.idArea = header;
        this.directoryArea = parseDirectoryArea (in);
        this.parameterArea = new S770ParameterArea (in);
    }


    /**
     * Skips the three large non-parsed regions (Reserved / Program-Text / FAT) that sit between the
     * ID area and the directory area, then reads the directory area.
     *
     * @param in Stream positioned immediately after the ID area (offset 0x200)
     * @return The parsed area
     * @throws IOException on read error
     */
    private static S770HdDirectoryArea parseDirectoryArea (final InputStream in) throws IOException
    {
        in.skipNBytes (SIZE_RESERVED + SIZE_PROGRAM_TEXT + SIZE_FAT);
        return new S770HdDirectoryArea (in);
    }


    /** @return The parsed ID area (disk name, capacities, object counts …). */
    public S770Header getIdArea ()
    {
        return this.idArea;
    }


    /**
     * @return The parsed directory area (volume / performance / patch / partial / sample
     *         directories).
     */
    public S770HdDirectoryArea getDirectoryArea ()
    {
        return this.directoryArea;
    }


    /** @return The parsed parameter area (all entry tables). */
    public S770ParameterArea getParameterArea ()
    {
        return this.parameterArea;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "RolandS770 {\n\n" + this.idArea + "\n\n" + this.directoryArea + "\n\n" + this.parameterArea + "\n}";
    }
}