// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

/**
 * The key-map of a Roland FANTOM user multisample (<i>MSPa</i> block in a <i>.SVZ</i>). The record is
 * 1040 bytes: a 16 byte name followed by a flat table of 128 entries (one per MIDI key), each 8
 * bytes. Each entry references a sample by a 1-based index into the container's <i>USPa</i>/<i>USDa</i>
 * pool (0 = the key is unassigned); the FANTOM has no ranged/velocity zones - the mapping is strictly
 * per key.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreKeyMap
{
    /** The size of a MSPa record in bytes. */
    public static final int RECORD_SIZE = 1040;
    /** The number of keys in the map. */
    public static final int NUM_KEYS    = 128;
    /** The size of one per-key entry in bytes. */
    public static final int ENTRY_SIZE  = 8;
    private static final int NAME_LENGTH = 16;

    private final String    name;
    private final int []    sampleIndex = new int [NUM_KEYS];


    /**
     * Constructor which parses a record.
     *
     * @param data The record data
     * @param offset The offset of the record inside the data
     */
    public ZenCoreKeyMap (final byte [] data, final int offset)
    {
        this.name = ZenCoreUtil.readName (data, offset, NAME_LENGTH);
        for (int key = 0; key < NUM_KEYS; key++)
            this.sampleIndex[key] = ZenCoreUtil.readUnsigned16 (data, offset + NAME_LENGTH + key * ENTRY_SIZE, false);
    }


    /**
     * Get the multisample name.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Is the given key assigned to a sample? A key is assigned when its 1-based sample index is not
     * zero.
     *
     * @param key The MIDI key
     * @return True if assigned
     */
    public boolean isAssigned (final int key)
    {
        return key >= 0 && key < NUM_KEYS && this.sampleIndex[key] != 0;
    }


    /**
     * Get the 1-based sample index assigned to a key (into the <i>USPa</i>/<i>USDa</i> pool).
     *
     * @param key The MIDI key
     * @return The 1-based sample index, or 0 if the key is unassigned
     */
    public int getSampleIndex (final int key)
    {
        return this.sampleIndex[key];
    }
}
