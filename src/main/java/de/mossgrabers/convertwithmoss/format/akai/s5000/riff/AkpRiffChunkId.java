// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Enumeration for known AKP RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public enum AkpRiffChunkId implements RiffChunkId
{
    APRG_ID("Akai Program", "APRG"),
    PRG_ID("Program", "prg "),
    MODS_ID("Modulations", "mods"),
    OUT_ID("Output", "out "),
    LFO_ID("LFO", "lfo "),
    TUNE_ID("Tuning", "tune"),
    KGRP_ID("Keygroups", "kgrp"),
    KGRP_LOC_ID("Keygroup Location", "kloc"),
    KGRP_ENV_ID("Keygroup Envelope", "env "),
    KGRP_FILTER_ID("Keygroup Filter", "filt"),
    KGRP_ZONE_ID("Keygroup Zone", "zone");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private AkpRiffChunkId (final String description, final String asciiID)
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