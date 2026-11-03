// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.util.Optional;

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
    Z48_ID("Z4/Z8 Multi", "z48 "),
    MPC_ID("MPC", "mpc "),
    FX_ID("Effects", "fx  "),
    PART_ID("Part", "part"),
    VERSION_ID("Multi Version", "mver"),
    Z48_MULTI_ID("Z4/Z8 Multi Header", "mult"),
    Z48_QLINK_ID("Z4/Z8 Q-Links", "qlnk"),
    Z48_MIDI_FILTER_ID("Z4/Z8 MIDI Filter", "mflt");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    AkmRiffChunkId (final String description, final String asciiID)
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
     * Get the chunk ID which matches the given FourCC.
     *
     * @param fourCC The FourCC
     * @return The matching ID if found
     */
    public static Optional<AkmRiffChunkId> fromFourCC (final int fourCC)
    {
        for (final AkmRiffChunkId id: values ())
            if (id.fourCC == fourCC)
                return Optional.of (id);
        return Optional.empty ();
    }
}
