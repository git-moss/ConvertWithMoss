// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

import java.util.HexFormat;


/**
 * A partial of a Roland MV-8000 patch. A partial is one of the 96 bit-packed 163 byte records in
 * the patch parameter block. It consists of a name, 4 sample-mix-table (SMT) slots and
 * pitch/filter/amplifier/LFO settings (the latter are not decoded).
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Partial
{
    /** The size of a partial record in bytes. */
    public static final int      SIZE              = 163;

    /** The number of SMT slots. */
    public static final int      NUM_SMT_SLOTS     = 4;

    /** TVF filter type: off. */
    public static final int      FILTER_OFF        = 0;
    /** TVF filter type: low-pass. */
    public static final int      FILTER_LPF        = 1;
    /** TVF filter type: band-pass. */
    public static final int      FILTER_BPF        = 2;
    /** TVF filter type: high-pass. */
    public static final int      FILTER_HPF        = 3;

    private static final int     NAME_LENGTH       = 12;
    private static final int     OFFSET_SLOT_1     = 146;
    private static final int     USED_TAG_OFFSET   = 84;
    private static final int     USED_TAG_VALUE    = 0x27;

    // The TVF (filter) and TVA (amplifier) block, decoded from the parameter descriptor tables of
    // the MV-8000 firmware (see documentation/design/MV8000_FORMAT.md)
    private static final int     OFFSET_TVF_TYPE   = 993;
    private static final int     OFFSET_TVF_CUTOFF = 997;
    private static final int     OFFSET_TVF_RESO   = 1004;
    private static final int     OFFSET_TVF_DEPTH  = 1034;
    private static final int     OFFSET_TVF_LEVELS = 1041;
    private static final int     OFFSET_TVF_TIMES  = 1069;
    private static final int     OFFSET_TVA_CURVE  = 1136;
    private static final int     OFFSET_TVA_LEVELS = 1159;
    private static final int     OFFSET_TVA_TIMES  = 1180;

    /** An unused partial record ('Init Partial') as found in the factory patches. */
    private static final byte [] INIT_PARTIAL      = HexFormat.of ().parseHex ("93bb4f441430f2e9a70ec01fe007fe4c9020400000107f810200101fc000000000000000000000000000000008000004" + "1fe040800407f00000000000000000000000000000000200000107f810200101fc000000000000000000000000000000" + "0080000041fe040800407f000000000000000000000000000000002007f00c0810207ffffc0000505004080f20406040" + "81fffff000a0a0a79020066000102040810000");

    private final MV8000BitArray bits;
    private final MV8000Smt []   smtSlots          = new MV8000Smt [NUM_SMT_SLOTS];


    /**
     * Constructor.
     *
     * @param data The 163 bytes of the partial record
     */
    public MV8000Partial (final byte [] data)
    {
        this.bits = new MV8000BitArray (data);
        for (int i = 0; i < NUM_SMT_SLOTS; i++)
            this.smtSlots[i] = new MV8000Smt (this.bits, OFFSET_SLOT_1 + i * MV8000Smt.SIZE_BITS);
    }


    /**
     * Constructor for a new initialized partial.
     */
    public MV8000Partial ()
    {
        this (INIT_PARTIAL.clone ());
    }


    /**
     * Get the record data.
     *
     * @return The 163 bytes of the partial record
     */
    public byte [] getData ()
    {
        return this.bits.getData ();
    }


    /**
     * Get the name of the partial.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.bits.getText (0, NAME_LENGTH).trim ();
    }


    /**
     * Set the name of the partial and tag the record as used.
     *
     * @param name The name, shortened/padded to 12 characters
     */
    public void setName (final String name)
    {
        this.bits.setText (0, NAME_LENGTH, name);
        // All used partials of the factory patches carry this tag value
        this.bits.setBits (USED_TAG_OFFSET, 7, USED_TAG_VALUE);
    }


    /**
     * Get a SMT slot.
     *
     * @param index The index of the slot (0-3)
     * @return The slot
     */
    public MV8000Smt getSmtSlot (final int index)
    {
        return this.smtSlots[index];
    }


    /**
     * Get the TVA velocity curve.
     *
     * @return The curve in the range of 0..3, 0 means velocity does not affect the volume
     */
    public int getTvaVelocityCurve ()
    {
        return this.bits.getBits (OFFSET_TVA_CURVE, 2);
    }


    /**
     * Get a level of the TVA (amplifier) envelope. The envelope raises to level 1 in time 1,
     * continues to level 2 in time 2 and to level 3 (the sustain level) in time 3. Time 4 is the
     * release time.
     *
     * @param index The index of the level (0-2)
     * @return The level in the range of 0..127
     */
    public int getTvaEnvelopeLevel (final int index)
    {
        return this.bits.getBits (OFFSET_TVA_LEVELS + index * 7, 7);
    }


    /**
     * Set a level of the TVA envelope.
     *
     * @param index The index of the level (0-2)
     * @param level The level in the range of 0..127
     */
    public void setTvaEnvelopeLevel (final int index, final int level)
    {
        this.bits.setBits (OFFSET_TVA_LEVELS + index * 7, 7, level);
    }


    /**
     * Get a time of the TVA (amplifier) envelope.
     *
     * @param index The index of the time (0-3)
     * @return The time in the range of 0..127
     */
    public int getTvaEnvelopeTime (final int index)
    {
        return this.bits.getBits (OFFSET_TVA_TIMES + index * 8, 8);
    }


    /**
     * Set a time of the TVA envelope.
     *
     * @param index The index of the time (0-3)
     * @param time The time in the range of 0..127
     */
    public void setTvaEnvelopeTime (final int index, final int time)
    {
        this.bits.setBits (OFFSET_TVA_TIMES + index * 8, 8, time);
    }


    /**
     * Get the TVF filter type.
     *
     * @return 0: off, 1: low-pass, 2: band-pass, 3: high-pass
     */
    public int getFilterType ()
    {
        return this.bits.getBits (OFFSET_TVF_TYPE, 4);
    }


    /**
     * Set the TVF filter type.
     *
     * @param filterType 0: off, 1: low-pass, 2: band-pass, 3: high-pass
     */
    public void setFilterType (final int filterType)
    {
        this.bits.setBits (OFFSET_TVF_TYPE, 4, filterType);
    }


    /**
     * Get the TVF cutoff frequency.
     *
     * @return The cutoff in the range of 0..127
     */
    public int getFilterCutoff ()
    {
        return this.bits.getBits (OFFSET_TVF_CUTOFF, 7);
    }


    /**
     * Set the TVF cutoff frequency.
     *
     * @param cutoff The cutoff in the range of 0..127
     */
    public void setFilterCutoff (final int cutoff)
    {
        this.bits.setBits (OFFSET_TVF_CUTOFF, 7, cutoff);
    }


    /**
     * Get the TVF resonance.
     *
     * @return The resonance in the range of 0..127
     */
    public int getFilterResonance ()
    {
        return this.bits.getBits (OFFSET_TVF_RESO, 7);
    }


    /**
     * Set the TVF resonance.
     *
     * @param resonance The resonance in the range of 0..127
     */
    public void setFilterResonance (final int resonance)
    {
        this.bits.setBits (OFFSET_TVF_RESO, 7, resonance);
    }


    /**
     * Get the TVF envelope depth.
     *
     * @return The depth in the range of 1..127, 64 is zero depth
     */
    public int getFilterEnvelopeDepth ()
    {
        return this.bits.getBits (OFFSET_TVF_DEPTH, 7);
    }


    /**
     * Set the TVF envelope depth.
     *
     * @param depth The depth in the range of 1..127, 64 is zero depth
     */
    public void setFilterEnvelopeDepth (final int depth)
    {
        this.bits.setBits (OFFSET_TVF_DEPTH, 7, depth);
    }


    /**
     * Get a level of the TVF (filter) envelope.
     *
     * @param index The index of the level (0-3)
     * @return The level in the range of 0..127
     */
    public int getTvfEnvelopeLevel (final int index)
    {
        return this.bits.getBits (OFFSET_TVF_LEVELS + index * 7, 7);
    }


    /**
     * Set a level of the TVF envelope.
     *
     * @param index The index of the level (0-3)
     * @param level The level in the range of 0..127
     */
    public void setTvfEnvelopeLevel (final int index, final int level)
    {
        this.bits.setBits (OFFSET_TVF_LEVELS + index * 7, 7, level);
    }


    /**
     * Get a time of the TVF (filter) envelope.
     *
     * @param index The index of the time (0-3)
     * @return The time in the range of 0..127
     */
    public int getTvfEnvelopeTime (final int index)
    {
        return this.bits.getBits (OFFSET_TVF_TIMES + index * 8, 8);
    }


    /**
     * Set a time of the TVF envelope.
     *
     * @param index The index of the time (0-3)
     * @param time The time in the range of 0..127
     */
    public void setTvfEnvelopeTime (final int index, final int time)
    {
        this.bits.setBits (OFFSET_TVF_TIMES + index * 8, 8, time);
    }
}
