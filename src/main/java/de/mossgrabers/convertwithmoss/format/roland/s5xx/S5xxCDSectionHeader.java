// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The header of a section.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxCDSectionHeader
{
    /** The section identifier for the Instrument Group. */
    public static final String SECTION_INSTRUMENT_GROUP     = "Instrument Group";
    /** The alternative section identifier for the Instrument Group. */
    public static final String SECTION_INSTRUMENT_GROUP_ALT = "InstrumentGroup";
    /** The section identifier for the Sound Directory. */
    public static final String SECTION_SOUND_DIRECTORY      = "Sound Directory";
    /** The alternative section identifier for the Sound Directory. */
    public static final String SECTION_SOUND_DIRECTORY_ALT  = "SoundDirectory";
    /** The section identifier for the Map 1 Instrument. */
    public static final String SECTION_INSTRUMENT_MAP       = "map1 Instrument";

    private String             name;
    private long               offset;
    private long               size;


    /**
     * COnstructor.
     * 
     * @param input The input stream to read the section values from
     * @throws IOException Could not read the section header
     */
    public S5xxCDSectionHeader (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readAscii (input, 16).trim ();
        this.offset = StreamUtils.readUnsigned32 (input, true);
        this.size = StreamUtils.readUnsigned32 (input, true);
        // 3 unknown, 5 padding with 0xFF
        input.skipNBytes (8);
    }


    /**
     * Get the name of the section.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
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
}
