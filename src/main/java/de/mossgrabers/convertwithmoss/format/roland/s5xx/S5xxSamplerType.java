// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

/**
 * Identifies the Roland sampler model from the 4-byte ASCII field at disk-image offset 4.
 * 
 * @author Jürgen Moßgraber
 */
public enum S5xxSamplerType
{
    /** Roland S-550. */
    S550("S550", "Roland S-550"),
    /** Roland S-330. */
    S330("S330", "Roland S-330"),
    /** Roland S-50. */
    S_50("S-50", "Roland S-50"),
    /** Roland S-550 tag 2. */
    S_51("S-51", "Roland S-50 (Variant)"),
    /** Roland S-50 tag 2. */
    S500("S500", "Roland S-50 (No OS / Sound Disk Only)"),
    /** Roland W-30. */
    W_30("W-30", "Roland W-30"),
    /** Roland Hard Drive / ZIP / CD-ROM. */
    LAND("LAND", "Hard Drive / ZIP / CD-ROM (multi-disk container)"),
    /** Roland S-770. */
    S770("S770", "SP-700 / S-750 / S-760 / S-770 / DJ-70 — unsupported"),
    /** Unknown format. */
    UNKNOWN("????", "Unknown");


    private final String id;
    private final String description;


    /**
     * Constructor.
     * 
     * @param id The ID of the type
     * @param description The description
     */
    private S5xxSamplerType (final String id, final String description)
    {
        this.id = id;
        this.description = description;
    }


    /**
     * The 4-byte ASCII identifier stored at offset 4.
     * 
     * @return The ID
     */
    public String getId ()
    {
        return this.id;
    }


    /**
     * Human-readable model description.
     * 
     * @return The description
     */
    public String getDescription ()
    {
        return this.description;
    }


    /**
     * Resolves from the raw 4-character string; returns {@link #UNKNOWN} on no match.
     * 
     * @param id The ID of the sampler type
     * @return The instance
     */
    public static S5xxSamplerType fromId (final String id)
    {
        for (final S5xxSamplerType t: values ())
            if (t.id.equals (id))
                return t;
        return UNKNOWN;
    }


    /**
     * S-50, S-51, S500 family: 512-byte patch blocks, 8 patches (P1–P8), and the output-jack field
     * is suppressed in display.
     * 
     * @return True if it from the S50 series
     */
    public boolean isS50 ()
    {
        return this == S_50 || this == S_51 || this == S500;
    }


    /**
     * Whether the output-jack field should be shown (false for S-50).
     * 
     * @return True to show the output jack
     */
    public boolean showOutputJack ()
    {
        return !this.isS50 ();
    }


    /**
     * Patch block size: 512 bytes for S-50, 256 bytes for all others.
     * 
     * @return The size of a block on the disk
     */
    public int patchBlockSize ()
    {
        return this.isS50 () ? 512 : 256;
    }


    /**
     * Number of patches the original tool reads: 8 for S-50 family, 16 for all others.
     * 
     * @return The number of patches of the format
     */
    public int patchCount ()
    {
        return this.isS50 () ? 8 : 16;
    }
}