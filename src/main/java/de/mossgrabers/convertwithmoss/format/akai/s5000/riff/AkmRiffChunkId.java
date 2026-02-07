// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Enumeration for known AKM RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public enum AkmRiffChunkId implements RiffChunkId
{
    AMUL_ID("Akai Multi", "AMUL"),
    FX_ID("Effects", "fx  "),
    PART_ID("Part", "part"),
    VERSION_ID("Multi Version", "mver ");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private AkmRiffChunkId (final String description, final String asciiID)
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
}