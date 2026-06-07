// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

/**
 * File type enumeration for Roland S-770 directory entries.
 *
 * @author Jürgen Moßgraber
 */
public enum S770FileType
{
    /** Entry for a volume on a disk. */
    VOLUME(0x40),
    /** A performance entry. */
    PERFORMANCE(0x41),
    /** A patch entry. */
    PATCH(0x42),
    /** A partial entry. */
    PARTIAL(0x43),
    /** A sample entry. */
    SAMPLE(0x44),
    /** An unknown entry (should never appear). */
    UNKNOWN(-1);


    private final int value;


    /**
     * Constructor.
     *
     * @param value The ID of the type
     */
    private S770FileType (final int value)
    {
        this.value = value;
    }


    /**
     * Get the ID of the type.
     *
     * @return The ID
     */
    public int getValue ()
    {
        return this.value;
    }


    /**
     * Get an enumeration instance from the value.
     *
     * @param value The type ID
     * @return The file type
     */
    public static S770FileType fromValue (final int value)
    {
        for (final S770FileType type: values ())
            if (type.value == value)
                return type;
        return UNKNOWN;
    }
}