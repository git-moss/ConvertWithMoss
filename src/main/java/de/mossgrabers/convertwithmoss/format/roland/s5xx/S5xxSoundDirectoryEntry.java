// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * One slot in a CD-ROM Sound Directory. Each slot occupies 64 bytes; up to 50 printable ASCII
 * characters are read as the contained disk name.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxSoundDirectoryEntry
{
    private final String name;
    private final String origin;
    private final long   offset;
    private final long   size;
    private final int    groupIdentifier;


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read the directory entry
     */
    public S5xxSoundDirectoryEntry (final InputStream input) throws IOException
    {
        final String n = StreamUtils.readAscii (input, 32);
        this.name = n.charAt (0) > 127 ? "" : n.trim ();
        this.origin = StreamUtils.readAscii (input, 16).trim ();
        this.offset = StreamUtils.readUnsigned32 (input, true);
        this.size = StreamUtils.readUnsigned32 (input, true);

        // Unknown byte (0x41 in samples)
        input.skipNBytes (1);

        this.groupIdentifier = StreamUtils.readUnsigned8 (input);

        // 6 padding with 0xFF
        input.skipNBytes (6);
    }


    /**
     * Get the disk name.
     *
     * @return Up to 32 printable ASCII chars padded with spaces
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the origin of the sound, e.g. the name of the floppy disk.
     *
     * @return Up to 16 printable ASCII chars padded with spaces
     */
    public String getOrigin ()
    {
        return this.origin;
    }


    /**
     * Get the offset in sectors (multiply by 0x200 for byte offset).
     * 
     * @return The offset
     */
    public long getOffset ()
    {
        return this.offset;
    }


    /**
     * Get the size in sectors.
     * 
     * @return The size in sectors
     */
    public long getSize ()
    {
        return this.size;
    }


    /**
     * Get the group identifier.
     * 
     * @return THe group identifier
     */
    public int getGroupIdentifier ()
    {
        return this.groupIdentifier;
    }
}