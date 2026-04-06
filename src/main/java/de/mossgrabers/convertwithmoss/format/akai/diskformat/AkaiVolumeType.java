// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.diskformat;

import java.util.HashMap;
import java.util.Map;


/**
 * The specific type of a partition on an AKAI disk media.
 *
 * @author Jürgen Moßgraber
 */
public enum AkaiVolumeType
{
    /** Unknown format ID. */
    UNKNOWN(-1, "Unknown Format"),
    /** Type ID for an empty/unused volume. Empty slot. */
    NOT_USED(0, "Empty"),
    /** Type ID for S1000 format. Baseline Akai 'classic' volume. */
    S1000(1, "Akai S1000"),
    /** Type ID for MPC2000 format. Transitional format between S1000 and S3000. */
    S3000_PRE(3, "Akai S3000"),
    /** Type ID for S3000 format. */
    S3000(7, "Akai S3000");


    private static final Map<Integer, AkaiVolumeType> LOOKUP = new HashMap<> ();
    static
    {
        for (final AkaiVolumeType type: AkaiVolumeType.values ())
            LOOKUP.put (Integer.valueOf (type.typeId), type);
    }

    private final int    typeId;
    private final String name;


    /**
     * Constructor.
     * 
     * @param typeId The type ID
     * @param name The readable name
     */
    private AkaiVolumeType (final int typeId, final String name)
    {
        this.typeId = typeId;
        this.name = name;
    }


    /**
     * Get the readable name.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the type enumeration value from the type ID.
     * 
     * @param typeId The type ID
     * @return The volume type, never null
     */
    public static AkaiVolumeType fromTypeId (final int typeId)
    {
        final AkaiVolumeType akaiVolumeType = LOOKUP.get (Integer.valueOf (typeId));
        return akaiVolumeType == null ? AkaiVolumeType.UNKNOWN : akaiVolumeType;
    }
}
