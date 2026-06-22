// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.dls;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Enumeration for known DLS RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
public enum DlsRiffChunkId implements RiffChunkId
{
    /** ID for "Downloadable Sounds" chunk. */
    DLS_ID("Downloadable Sounds", "DLS "),

    /** ID for CDL chunk for querying/restricting to specific devices (optional). */
    CDL_ID("Conditional Chunk (optional)", "cdl "),
    /** ID for VERS chunk. */
    VERS_ID("Version (optional)", "vers"),
    /** ID for DLID chunk. */
    DLID_ID("DLS globally unique identifier (optional)", "dlid"),
    /** ID for COLH chunk. */
    COLH_ID("Number of instruments in collection", "colh"),
    /** ID for LINS list. */
    LINS_ID("Instrument list", "lins"),
    /** ID for PTBL chunk. */
    PTBL_ID("Reference table to digital audio data", "ptbl"),
    /** ID for WVPL list. */
    WVPL_ID("Samples list", "wvpl"),
    /** ID for MSYN chunk (proprietary). */
    MSYN_ID("Version (optional)", "msyn"),

    /** ID for single Instrument list. */
    INS_ID("Instrument", "ins "),
    /** ID for a Instrument header chunk. */
    INSH_ID("Instrument Header", "insh"),
    /** ID for a region list. */
    LRGN_ID("Region list", "lrgn"),
    /** ID for a single region list. */
    RGN_ID("Region", "rgn "),
    /** ID for a single region list v2. */
    RGN2_ID("Region v2", "rgn2"),
    /** ID for a region header chunk. */
    RGNH_ID("Region Header", "rgnh"),
    /** ID for a Wave Sample info chunk. */
    WSMP_ID("Wave Sample info", "wsmp"),
    /** ID for a Wave Sample chunk. */
    WLNK_ID("Link: Sample ID within the wave pool", "wlnk"),

    /** ID for a Wave Sample chunk. */
    WAVE_ID("Wave sample", "wave"),
    /** ID for a sample format chunk. */
    FMT_ID("Wave format", "fmt "),
    /** ID for a data chunk. */
    DATA_ID("Sample Data", "data"),

    /** ID for an articulation list chunk v1. */
    LART_ID("Articulation list", "lart"),
    /** ID for an articulation chunk v1. */
    ART1_ID("Articulation v1", "art1"),
    /** ID for an articulation list chunk v2. */
    LAR2_ID("Articulation list", "lar2"),
    /** ID for an articulation chunk v2. */
    ART2_ID("Articulation v2", "art2");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private DlsRiffChunkId (final String description, final String asciiID)
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