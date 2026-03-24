// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Enumeration for known Sf2 RIFF IDs.
 *
 * @author Jürgen Moßgraber
 */
public enum Sf2RiffChunkId implements RiffChunkId
{
    /** ID for SoundFont 2 chunk. */
    SFBK_ID("SoundFont 2", "sfbk"),

    /** ID for IFIL chunk. */
    IFIL_ID("SoundFont Specification Version Level", "ifil"),
    /** ID for ISNG chunk. */
    ISNG_ID("SoundFont Wavetable Sound Engine", "isng"),
    /** ID for IROM chunk. */
    IROM_ID("Wavetable Sound Data ROM", "irom"),
    /** ID for IVER chunk. */
    IVER_ID("Wavetable Sound Data ROM Revision", "iver"),

    /** ID for SoundFont Data list chunk. */
    DATA_ID("SoundFont Data list", "sdta"),
    /** ID for "smpl" chunk. */
    SMPL_ID("Sample", "smpl"),
    /** ID for Sample Data 24bit chunk. */
    SM24_ID("Sample Data 24bit", "sm24"),

    /** ID for list chunk containing the Preset, Instrument, and Sample Header data. */
    PDTA_ID("The Preset, Instrument, and Sample Header data list", "pdta"),
    /** ID for Preset chunk. */
    PHDR_ID("Preset", "phdr"),
    /** ID for Preset Zone chunk. */
    PBAG_ID("Preset Zone", "pbag"),
    /** ID for preset Zone Generator chunk. */
    PGEN_ID("Preset Zone Generators", "pgen"),
    /** ID for preset Zone Generator chunk. */
    PMOD_ID("Preset Zone Modulators", "pmod"),

    /** ID for "inst" chunk. */
    INST_ID("Instrument", "inst"),
    /** ID for instrument zones chunk. */
    IBAG_ID("Instrument Zones", "ibag"),
    /** ID for instrument zone modulators chunk. */
    IMOD_ID("Instrument Zone Modulators", "imod"),
    /** ID for instrument zone generators chunk. */
    IGEN_ID("Instrument Zone Generators", "igen"),
    /** ID for sample descriptors chunk. */
    SHDR_ID("Sample Descriptors", "shdr");


    private final int    fourCC;
    private final String description;


    /**
     * Constructor.
     *
     * @param description A descriptive text for the chunk
     * @param asciiID The ASCII representation of the ID
     */
    private Sf2RiffChunkId (final String description, final String asciiID)
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