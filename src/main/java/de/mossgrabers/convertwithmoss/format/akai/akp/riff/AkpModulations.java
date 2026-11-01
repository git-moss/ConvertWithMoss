// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 mods chunk. After a 4 byte header it contains 17 pairs of a fixed destination and
 * the selected modulation source. The matching modulation amounts are stored in the output, LFO,
 * key-group location and filter chunks.
 *
 * @author Jürgen Moßgraber
 */
public class AkpModulations extends AbstractAkpChunk
{
    /**
     * The modulation slots and the offset of their source in the chunk data.
     */
    public enum Slot
    {
        /** Amplitude modulation 1 of the program. */
        AMP_MOD_1(0x05, AkpModulations.SOURCE_KEYBOARD),
        /** Amplitude modulation 2 of the program. */
        AMP_MOD_2(0x07, AkpModulations.SOURCE_AFTERTOUCH),
        /** Panning modulation 1 of the program. */
        PAN_MOD_1(0x09, AkpModulations.SOURCE_LFO2),
        /** Panning modulation 2 of the program. */
        PAN_MOD_2(0x0B, AkpModulations.SOURCE_KEYBOARD),
        /** Panning modulation 3 of the program. */
        PAN_MOD_3(0x0D, AkpModulations.SOURCE_MODWHEEL),
        /** Rate modulation of LFO 1. */
        LFO1_RATE(0x0F, AkpModulations.SOURCE_KEYBOARD),
        /** Delay modulation of LFO 1. */
        LFO1_DELAY(0x11, AkpModulations.SOURCE_KEYBOARD),
        /** Depth modulation of LFO 1. */
        LFO1_DEPTH(0x13, AkpModulations.SOURCE_KEYBOARD),
        /** Rate modulation of LFO 2. */
        LFO2_RATE(0x15, AkpModulations.SOURCE_NO_SOURCE),
        /** Delay modulation of LFO 2. */
        LFO2_DELAY(0x17, AkpModulations.SOURCE_NO_SOURCE),
        /** Depth modulation of LFO 2. */
        LFO2_DEPTH(0x19, AkpModulations.SOURCE_NO_SOURCE),
        /** Pitch modulation 1 of the key-groups. */
        PITCH_MOD_1(0x1B, AkpModulations.SOURCE_LFO1),
        /** Pitch modulation 2 of the key-groups. */
        PITCH_MOD_2(0x1D, AkpModulations.SOURCE_AUX_ENV),
        /** Amplitude modulation of the key-groups. */
        AMP_MOD(0x1F, AkpModulations.SOURCE_VELOCITY),
        /** Filter modulation input 1 of the key-groups. */
        FILTER_MOD_1(0x21, AkpModulations.SOURCE_VELOCITY),
        /** Filter modulation input 2 of the key-groups. */
        FILTER_MOD_2(0x23, AkpModulations.SOURCE_LFO2),
        /** Filter modulation input 3 of the key-groups. */
        FILTER_MOD_3(0x25, AkpModulations.SOURCE_AMP_ENV);


        private final int offset;
        private final int defaultSource;


        /**
         * Constructor.
         *
         * @param offset The offset of the source in the chunk data
         * @param defaultSource The default source
         */
        Slot (final int offset, final int defaultSource)
        {
            this.offset = offset;
            this.defaultSource = defaultSource;
        }
    }


    /** Modulation source: NO SOURCE. */
    public static final int  SOURCE_NO_SOURCE  = 0;
    /** Modulation source: MODWHEEL. */
    public static final int  SOURCE_MODWHEEL   = 1;
    /** Modulation source: BEND. */
    public static final int  SOURCE_BEND       = 2;
    /** Modulation source: AFT'TOUCH. */
    public static final int  SOURCE_AFTERTOUCH = 3;
    /** Modulation source: EXTERNAL. */
    public static final int  SOURCE_EXTERNAL   = 4;
    /** Modulation source: VELOCITY. */
    public static final int  SOURCE_VELOCITY   = 5;
    /** Modulation source: KEYBOARD. */
    public static final int  SOURCE_KEYBOARD   = 6;
    /** Modulation source: LFO1. */
    public static final int  SOURCE_LFO1       = 7;
    /** Modulation source: LFO2. */
    public static final int  SOURCE_LFO2       = 8;
    /** Modulation source: AMP ENV. */
    public static final int  SOURCE_AMP_ENV    = 9;
    /** Modulation source: FILT ENV. */
    public static final int  SOURCE_FILTER_ENV = 10;
    /** Modulation source: AUX ENV. */
    public static final int  SOURCE_AUX_ENV    = 11;
    /** Modulation source: dMODWHEEL. */
    public static final int  SOURCE_D_MODWHEEL = 12;
    /** Modulation source: dBEND. */
    public static final int  SOURCE_D_BEND     = 13;
    /** Modulation source: dEXTERNAL. */
    public static final int  SOURCE_D_EXTERNAL = 14;

    /** The length of the Modulations structure. */
    private static final int LENGTH_MODS       = 38;

    private final int []     sources           = new int [Slot.values ().length];


    /**
     * Default constructor.
     */
    public AkpModulations ()
    {
        super (AkpRiffChunkId.MODS_ID, LENGTH_MODS, 1);

        for (final Slot slot: Slot.values ())
            this.sources[slot.ordinal ()] = slot.defaultSource;
    }


    /**
     * Get the modulation source of a slot.
     *
     * @param slot The slot
     * @return The source, see the SOURCE_* constants
     */
    public int getSource (final Slot slot)
    {
        return this.sources[slot.ordinal ()];
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        // The destinations at the even offsets are fixed, therefore only the sources are read
        for (final Slot slot: Slot.values ())
            this.sources[slot.ordinal ()] = getUnsigned (data, slot.offset);
    }
}
