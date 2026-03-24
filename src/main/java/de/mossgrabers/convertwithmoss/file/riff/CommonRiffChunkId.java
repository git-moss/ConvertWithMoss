// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

/**
 * Enumeration for common RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
public enum CommonRiffChunkId implements RiffChunkId
{
    /** ID for main RIFF ID. */
    RIFF_ID("RIFF", "RIFF"),
    /** ID for a chunk LIST. */
    LIST_ID("List", "LIST"),
    /** ID for NULL chunk. */
    NULL_ID("Null", "    "),
    /** ID for NULL chunk. */
    NULL_NUL_ID("Null Nul", "\0\0\0\0"),
    /** ID for JUNK chunk. */
    JUNK_ID("JUNK", "JUNK"),
    /** ID for junk chunk. */
    JUNK2_ID("junk", "junk"),
    /** Unsupported ID. */
    UNSUPPORTED("Unsupported", null);


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private CommonRiffChunkId (final String description, final String asciiID)
    {
        this.description = description;
        this.fourCC = asciiID == null ? -1 : RiffChunkId.toFourCC (asciiID);
    }


    /** {@inheritDoc} */
    @Override
    public int getFourCC ()
    {
        return this.fourCC;
    }


    /** {@inheritDoc} */
    @Override
    public String getDescription ()
    {
        return this.description;
    }


    /**
     * Checks if the argument represents a valid RIFF ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the ID is a valid IFF chunk ID
     */
    public static boolean isValidId (final int id)
    {
        final int c0 = id >> 24;
        final int c1 = id >> 16 & 0xff;
        final int c2 = id >> 8 & 0xff;
        final int c3 = id & 0xff;
        return NULL_NUL_ID.getFourCC () == id || c0 >= 0x20 && c0 <= 0x7e && c1 >= 0x20 && c1 <= 0x7e && c2 >= 0x20 && c2 <= 0x7e && c3 >= 0x20 && c3 <= 0x7e;
    }


    /**
     * Checks whether the argument represents a valid RIFF Group ID.
     *
     * @param id Chunk ID to be checked
     * @return True when the chunk ID is a valid Group ID
     */
    public static boolean isGroupId (final int id)
    {
        return LIST_ID.getFourCC () == id || RIFF_ID.getFourCC () == id;
    }
}