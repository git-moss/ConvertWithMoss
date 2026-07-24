// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

/**
 * Object type constants and hash calculations for Kurzweil K2000/K2500/K2600 objects. The 16-bit
 * hash of an object combines its type and ID: for the types up to 42 it is (type &lt;&lt; 10) | ID
 * with IDs from 0 to 1023 (usable on the device are 1 to 999). Other types use an 8-bit type with a
 * mangled ID which is not interpreted here.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilObjectID
{
    /** The object type of a program. */
    public static final int TYPE_PROGRAM = 36;
    /** The object type of a keymap. */
    public static final int TYPE_KEYMAP  = 37;
    /** The object type of a sample. */
    public static final int TYPE_SAMPLE  = 38;

    /** The first object ID to use for newly created objects. */
    public static final int FIRST_ID     = 200;
    /** The highest object ID usable on the device. */
    public static final int LAST_ID      = 999;


    /**
     * Private constructor for utility class.
     */
    private KurzweilObjectID ()
    {
        // Intentionally empty
    }


    /**
     * Create the hash for an object type and ID.
     *
     * @param type The object type, e.g. TYPE_SAMPLE
     * @param id The object ID (0-1023)
     * @return The hash
     */
    public static int createHash (final int type, final int id)
    {
        return type << 10 | id;
    }


    /**
     * Get the object type from a hash.
     *
     * @param hash The hash
     * @return The object type
     */
    public static int getType (final int hash)
    {
        return (hash & 0x8000) > 0 ? hash >>> 10 : hash >>> 8;
    }


    /**
     * Get the object ID from a hash.
     *
     * @param hash The hash
     * @return The object ID
     */
    public static int getID (final int hash)
    {
        return (hash & 0x8000) > 0 ? hash & 1023 : hash & 255;
    }
}
