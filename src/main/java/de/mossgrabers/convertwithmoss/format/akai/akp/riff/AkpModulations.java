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
        AMP_MOD_1(0x05, AkpModulations.DESTINATION_AMP, AkpModulations.SOURCE_KEYBOARD),
        /** Amplitude modulation 2 of the program. */
        AMP_MOD_2(0x07, AkpModulations.DESTINATION_AMP, AkpModulations.SOURCE_AFTERTOUCH),
        /** Panning modulation 1 of the program. */
        PAN_MOD_1(0x09, AkpModulations.DESTINATION_PAN, AkpModulations.SOURCE_LFO2),
        /** Panning modulation 2 of the program. */
        PAN_MOD_2(0x0B, AkpModulations.DESTINATION_PAN, AkpModulations.SOURCE_KEYBOARD),
        /** Panning modulation 3 of the program. */
        PAN_MOD_3(0x0D, AkpModulations.DESTINATION_PAN, AkpModulations.SOURCE_MODWHEEL),
        /** Rate modulation of LFO 1. */
        LFO1_RATE(0x0F, AkpModulations.DESTINATION_LFO1_RATE, AkpModulations.SOURCE_KEYBOARD),
        /** Delay modulation of LFO 1. */
        LFO1_DELAY(0x11, AkpModulations.DESTINATION_LFO1_DELAY, AkpModulations.SOURCE_KEYBOARD),
        /** Depth modulation of LFO 1. */
        LFO1_DEPTH(0x13, AkpModulations.DESTINATION_LFO1_DEPTH, AkpModulations.SOURCE_KEYBOARD),
        /** Rate modulation of LFO 2. */
        LFO2_RATE(0x15, AkpModulations.DESTINATION_LFO2_RATE, AkpModulations.SOURCE_NO_SOURCE),
        /** Delay modulation of LFO 2. */
        LFO2_DELAY(0x17, AkpModulations.DESTINATION_LFO2_DELAY, AkpModulations.SOURCE_NO_SOURCE),
        /** Depth modulation of LFO 2. */
        LFO2_DEPTH(0x19, AkpModulations.DESTINATION_LFO2_DEPTH, AkpModulations.SOURCE_NO_SOURCE),
        /** Pitch modulation 1 of the key-groups. */
        PITCH_MOD_1(0x1B, AkpModulations.DESTINATION_PITCH, AkpModulations.SOURCE_LFO1),
        /** Pitch modulation 2 of the key-groups. */
        PITCH_MOD_2(0x1D, AkpModulations.DESTINATION_PITCH, AkpModulations.SOURCE_AUX_ENV),
        /** Amplitude modulation of the key-groups. */
        AMP_MOD(0x1F, AkpModulations.DESTINATION_AMP, AkpModulations.SOURCE_VELOCITY),
        /** Filter modulation input 1 of the key-groups. */
        FILTER_MOD_1(0x21, AkpModulations.DESTINATION_FILTER, AkpModulations.SOURCE_VELOCITY),
        /** Filter modulation input 2 of the key-groups. */
        FILTER_MOD_2(0x23, AkpModulations.DESTINATION_FILTER, AkpModulations.SOURCE_LFO2),
        /**
         * Filter modulation input 3 of the key-groups. The specification lists AMP ENV as the
         * default source but all analysed files contain FILT ENV.
         */
        FILTER_MOD_3(0x25, AkpModulations.DESTINATION_FILTER, AkpModulations.SOURCE_FILTER_ENV);


        private final int offset;
        private final int destination;
        private final int defaultSource;


        /**
         * Constructor.
         *
         * @param offset The offset of the source in the chunk data
         * @param destination The fixed destination which is stored in front of the source
         * @param defaultSource The default source
         */
        Slot (final int offset, final int destination, final int defaultSource)
        {
            this.offset = offset;
            this.destination = destination;
            this.defaultSource = defaultSource;
        }
    }


    /** Modulation destination: Pitch. */
    public static final int  DESTINATION_PITCH      = 0;
    /** Modulation destination: Pan. */
    public static final int  DESTINATION_PAN        = 1;
    /** Modulation destination: Amp. */
    public static final int  DESTINATION_AMP        = 2;
    /** Modulation destination: LFO 1 Depth. */
    public static final int  DESTINATION_LFO1_DEPTH = 3;
    /** Modulation destination: LFO 1 Rate. */
    public static final int  DESTINATION_LFO1_RATE  = 4;
    /** Modulation destination: LFO 1 Delay. */
    public static final int  DESTINATION_LFO1_DELAY = 5;
    /** Modulation destination: LFO 2 Depth. */
    public static final int  DESTINATION_LFO2_DEPTH = 6;
    /** Modulation destination: LFO 2 Rate. */
    public static final int  DESTINATION_LFO2_RATE  = 7;
    /** Modulation destination: LFO 2 Delay. */
    public static final int  DESTINATION_LFO2_DELAY = 8;
    /** Modulation destination: Filter. */
    public static final int  DESTINATION_FILTER     = 9;

    /** Modulation source: NO SOURCE. */
    public static final int  SOURCE_NO_SOURCE       = 0;
    /** Modulation source: MODWHEEL. */
    public static final int  SOURCE_MODWHEEL        = 1;
    /** Modulation source: BEND. */
    public static final int  SOURCE_BEND            = 2;
    /** Modulation source: AFT'TOUCH. */
    public static final int  SOURCE_AFTERTOUCH      = 3;
    /** Modulation source: EXTERNAL. */
    public static final int  SOURCE_EXTERNAL        = 4;
    /** Modulation source: VELOCITY. */
    public static final int  SOURCE_VELOCITY        = 5;
    /** Modulation source: KEYBOARD. */
    public static final int  SOURCE_KEYBOARD        = 6;
    /** Modulation source: LFO1. */
    public static final int  SOURCE_LFO1            = 7;
    /** Modulation source: LFO2. */
    public static final int  SOURCE_LFO2            = 8;
    /** Modulation source: AMP ENV. */
    public static final int  SOURCE_AMP_ENV         = 9;
    /** Modulation source: FILT ENV. */
    public static final int  SOURCE_FILTER_ENV      = 10;
    /** Modulation source: AUX ENV. */
    public static final int  SOURCE_AUX_ENV         = 11;
    /** Modulation source: dMODWHEEL. */
    public static final int  SOURCE_D_MODWHEEL      = 12;
    /** Modulation source: dBEND. */
    public static final int  SOURCE_D_BEND          = 13;
    /** Modulation source: dEXTERNAL. */
    public static final int  SOURCE_D_EXTERNAL      = 14;

    /** The length of the Modulations structure. */
    private static final int LENGTH_MODS            = 38;

    private final int []     sources                = new int [Slot.values ().length];


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


    /**
     * Set the source of a modulation slot.
     *
     * @param slot The slot
     * @param source The source, see the SOURCE constants
     */
    public void setSource (final Slot slot, final int source)
    {
        this.sources[slot.ordinal ()] = source;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        // The destinations at the even offsets are fixed, therefore only the sources are read
        for (final Slot slot: Slot.values ())
            this.sources[slot.ordinal ()] = getUnsigned (data, slot.offset);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // The header contains the number of destination/source pairs
        data[0x02] = (byte) Slot.values ().length;
        for (final Slot slot: Slot.values ())
            setByte (data, slot.offset - 1, slot.destination);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        for (final Slot slot: Slot.values ())
            setByte (data, slot.offset, this.sources[slot.ordinal ()]);
    }
}
