// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Enumeration for known Wave RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
public enum WaveRiffChunkId implements RiffChunkId
{
    /** ID for WAVE chunk. */
    WAVE_ID("Wave", "WAVE"),
    /** ID for "inst" chunk. */
    INST_ID("Instrument", "inst"),
    /** ID for "smpl" chunk. */
    SMPL_ID("Sample", "smpl"),
    /** ID for "fmt " chunk. */
    FMT_ID("Format", "fmt "),
    /** ID for "data" chunk. */
    DATA_ID("Data", "data"),
    /** ID for "fact" chunk. */
    FACT_ID("Fact", "fact"),
    /** ID for "wavl" chunk. */
    WAVL_ID("Wave List", "wavl"),
    /** ID for "slnt" chunk. */
    SLNT_ID("Silent", "slnt"),
    /** ID for "cue " chunk. */
    CUE_ID("Cue", "cue "),
    /** ID for "plst" chunk. */
    PLST_ID("Playlist", "plst"),
    /** ID for "labl" chunk. */
    LABL_ID("Label", "labl"),
    /** ID for "note" chunk. */
    NOTE_ID("Note", "note"),
    /** ID for "ltxt" chunk. */
    LTXT_ID("Labeled Text", "ltxt"),

    /**
     * Apple software often creates WAVE files with a non-standard (but RIFF specification conform)
     * "FLLR" sub-chunk after the format sub-chunk and before the data sub-chunk. "FLLR" stands for
     * "filler", and the purpose of the sub-chunk is to enable some sort of data alignment
     * optimization. The sub-chunk is usually about 4000 bytes long, but its actual length can vary
     * depending on the length of the data preceding it.
     */
    FILLER_ID("Apple Filler", "FLLR"),

    /** MD5 checksum. */
    MD5_ID("MD5 Checksum", "MD5 "),

    /** Broadcast Audio Extension Chunk. **/
    BEXT_ID("Broadcast Audio Extension", "bext");

    // Additional unknown tags are: srob, r64m, acid, LGBM, atem, meta


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private WaveRiffChunkId (final String description, final String asciiID)
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