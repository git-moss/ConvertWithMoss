// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil.pc3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilEnvelope;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilProgram;


/**
 * A Kurzweil PC3 series / Forte program object. The object data starts with a fixed header (the
 * 'PC3' signatures with the version numbers, the program parameters, the effect chain references
 * and the KB3 organ block) followed by one fixed size record per layer and a trailing block with
 * the controller information of the program. A layer record holds the segments known from the
 * K2000/K2500/K2600 programs in a fixed order (layer, ASRs, FUNs, LFOs, envelope control, the
 * three envelopes, the keymap/pitch block and five DSP function pages), each opened by its tag
 * byte, followed by the layer effect reference and the layer name.
 *
 * A program which is created for writing copies the header, the layer record and the trailing
 * block of a program written by a PC3K and sets only the fields which the conversion controls.
 *
 * @author Jürgen Moßgraber
 */
public class PC3Program
{
    /** The length of the fixed header which precedes the layer records. */
    private static final int      HEADER_LENGTH           = 229;
    /** The length of a layer record. */
    private static final int      LAYER_LENGTH            = 318;
    /** The position of the number of layers in the header. */
    private static final int      NUM_LAYERS_OFFSET       = 40;

    // The positions in a layer record
    private static final int      LAYER_TAG               = 9;
    private static final int      LYR_ENABLE_LOW          = 1;
    private static final int      LYR_LOW_KEY             = 2;
    private static final int      LYR_HIGH_KEY            = 3;
    private static final int      LYR_VELOCITY            = 4;
    private static final int      LYR_ENABLE              = 5;
    private static final int      LYR_FLAGS               = 6;
    private static final int      LYR_MORE_FLAGS          = 7;
    private static final int      LYR_TRIGGER             = 8;
    private static final int      LYR_ENABLE_HIGH         = 9;
    private static final int      LYR_DELAY               = 10;
    private static final int      LYR_CROSSFADE           = 13;
    private static final int      ENVC_OFFSET             = 61;
    private static final int      ENV1_OFFSET             = 82;
    private static final int      ENV2_OFFSET             = 98;
    private static final int      CAL_OFFSET              = 130;
    private static final int      CAL_TRANSPOSE           = 2;
    private static final int      CAL_KEYMAP_2            = 8;
    private static final int      CAL_KEYMAP              = 12;
    private static final int      CAL_LAYER_COUNTER       = 17;
    private static final int      PAGES_OFFSET            = 162;
    private static final int      PAGE_LENGTH             = 22;
    private static final int      NUM_PAGES               = 5;
    private static final int      PAGE_FUNCTION           = 1;
    private static final int      PAGE_COARSE             = 2;
    private static final int      PAGE_SOURCE_1           = 6;
    private static final int      PAGE_DEPTH_1            = 7;

    /** The layer flag which is set on all layers written by the devices. */
    private static final int      MORE_FLAGS_DEFAULT      = 0x04;
    /** The layer plays its keymap in stereo: keymap 1 on the left, keymap 2 on the right. */
    private static final int      MORE_FLAGS_STEREO       = 0x20;
    /** The envelope control flag: the 'natural' envelope of the samples is used. */
    private static final int      ENVC_NATURAL            = 0x01;

    /** The control source code 'OFF'. */
    private static final int      CONTROL_SOURCE_OFF      = 0;
    /** The control source code 'ON'. */
    private static final int      CONTROL_SOURCE_ON       = 0x7F;
    /** The control source code of the attack velocity. */
    private static final int      CONTROL_SOURCE_VELOCITY = 100;
    /** The control source code of the second envelope (ENV2). */
    private static final int      CONTROL_SOURCE_ENV2     = 121;

    // The DSP functions of the K2000/K2500/K2600 which implement a filter
    private static final int      FUNCTION_LOW_PASS_2P    = 2;
    private static final int      FUNCTION_BAND_PASS_2P   = 3;
    private static final int      FUNCTION_LOW_PASS_1P    = 15;
    private static final int      FUNCTION_LOW_PASS_4P    = 50;
    private static final int      FUNCTION_HIGH_PASS_4P   = 54;
    private static final int      FUNCTION_BAND_PASS_4P   = 55;
    private static final int      FUNCTION_NOTCH_4P       = 56;

    /** The envelope times of the first codes of the time list: 0, 2, 5 and 10 milliseconds. */
    private static final double[] SHORT_TIMES             =
    {
        0,
        0.002,
        0.005,
        0.01
    };

    /**
     * The header of a two layer program written by a PC3K (version 4.5): no effect chains, no KB3
     * organ; the number of layers is patched in.
     */
    private static final byte []  HEADER_TEMPLATE         = HexFormat.of ().parseHex ("50433304055043330303000000000000136b6579776f7264312c206b6579776f7264320003050805020037400000ffffffffffffffffffffffff370000000000001000000000001800000014000000000018000000030351000000000000007f7f030352000000000000007f7f030353000000000000007f7f50726f6772616d524655303132353637000000000000000000000000000000000000000000000000000000000000000000000000000000006e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000048616d6d");

    /**
     * A layer record written by a PC3K which plays a RAM sample keymap straight through the
     * amplifier (algorithm 5 without DSP functions).
     */
    private static final byte []  LAYER_TEMPLATE          = HexFormat.of ().parseHex ("09f834600082001400d100000064030500006a0000107f01353535117f01353535180100001902000014002e00000415002e0000041a0300001b0400002001000000490000004800f1004800270000004600210064000000000064000000000000002200640000000000640000000000000023006400000000006400000000000000407f00002b000000040900400409000000020000000000000100000000000500500000000000000001000000030000000000000100005100000000000000010000000000000000000001000053000140002d00000100000000002000000000010000520000000000000001000000000003000000000100005400000000000000000000000300000000000001c040030351000000000000007f7f00000000001420202020202020202020202020202020202020204c61796572524655");

    /** The envelope control segment: user envelope, no real-time control of the envelope rates. */
    private static final byte []  ENVC_TEMPLATE           = HexFormat.of ().parseHex ("200000000049000000480000004800");

    /** The trailing block of a program written by a PC3K: no controller information. */
    private static final byte []  TAIL_TEMPLATE           = HexFormat.of ().parseHex ("0001020000000300000105007f0000000000000008006400000164000018020000000000000004000000000001000002e80000000f0000000124282f2b373b3e");


    /**
     * One layer of a program: a keymap mapped to a key and velocity range with its envelope and
     * DSP settings.
     */
    public static class Layer
    {
        private int              keymapID;
        private int              secondKeymapID;
        private boolean          isStereo;
        private boolean          isEnabled              = true;
        private int              lowKey                 = 0;
        private int              highKey                = 127;
        private int              transpose              = 0;
        private int              velocityLow            = 1;
        private int              velocityHigh           = 127;

        private boolean          isNaturalEnvelope      = false;
        private KurzweilEnvelope amplitudeEnvelope      = null;
        private KurzweilEnvelope filterEnvelope         = null;
        private int              cutoffModulationSource = 0;
        private int              cutoffModulationDepth  = 0;

        private int              filterFunction         = 0;
        private int              cutoff                 = 0;
        private int              resonance              = 0;


        /**
         * Constructor for a layer which is created for writing.
         */
        public Layer ()
        {
            // Intentionally empty
        }


        /**
         * Constructor. Decodes a layer record.
         *
         * @param record The layer record
         */
        Layer (final byte [] record)
        {
            this.lowKey = record[LYR_LOW_KEY] & 0x7F;
            this.highKey = record[LYR_HIGH_KEY] & 0x7F;
            // The velocity window: the low mark in bits 3-5, the high mark inverted in bits 0-2
            final int window = record[LYR_VELOCITY] & 0xFF;
            this.velocityLow = Math.max (1, (window >> 3 & 7) * 16);
            this.velocityHigh = (7 - (window & 7)) * 16 + 15;
            this.isEnabled = (record[LYR_ENABLE] & 0xFF) != CONTROL_SOURCE_OFF;
            this.isStereo = (record[LYR_MORE_FLAGS] & MORE_FLAGS_STEREO) != 0;

            this.isNaturalEnvelope = (record[ENVC_OFFSET + 1] & ENVC_NATURAL) != 0;
            this.amplitudeEnvelope = readEnvelope (record, ENV1_OFFSET);
            this.filterEnvelope = readEnvelope (record, ENV2_OFFSET);

            this.transpose = record[CAL_OFFSET + CAL_TRANSPOSE];
            this.secondKeymapID = readUnsigned16 (record, CAL_OFFSET + CAL_KEYMAP_2);
            this.keymapID = readUnsigned16 (record, CAL_OFFSET + CAL_KEYMAP);

            // The filter is the first DSP function page which holds one of the filter functions
            // of the K2x00 family; a 2-block filter keeps its resonance on the following page
            for (int page = 0; page < NUM_PAGES; page++)
            {
                final int offset = PAGES_OFFSET + page * PAGE_LENGTH;
                final int function = record[offset + PAGE_FUNCTION] & 0xFF;
                if (!isFilterFunction (function))
                    continue;
                this.filterFunction = function;
                this.cutoff = record[offset + PAGE_COARSE];
                this.cutoffModulationSource = record[offset + PAGE_SOURCE_1] & 0xFF;
                this.cutoffModulationDepth = KurzweilProgram.decodeModulationDepth (record[offset + PAGE_DEPTH_1]);
                if (hasResonance (function) && page + 1 < NUM_PAGES)
                {
                    // 0.5dB steps in the range of the Res parameter (-12 to 24 dB)
                    final int value = record[offset + PAGE_LENGTH + PAGE_COARSE];
                    if (value >= -24 && value <= 48)
                        this.resonance = value;
                }
                break;
            }
        }


        /**
         * Create the record of this layer for writing.
         *
         * @return The record
         */
        byte [] createRecord ()
        {
            final byte [] record = LAYER_TEMPLATE.clone ();

            record[LYR_ENABLE_LOW] = 0;
            record[LYR_LOW_KEY] = (byte) Math.clamp (this.lowKey, 0, 127);
            record[LYR_HIGH_KEY] = (byte) Math.clamp (this.highKey, 0, 127);
            record[LYR_VELOCITY] = (byte) encodeVelocityRange (this.velocityLow, this.velocityHigh);
            record[LYR_ENABLE] = (byte) CONTROL_SOURCE_ON;
            record[LYR_FLAGS] = 0;
            record[LYR_MORE_FLAGS] = (byte) (MORE_FLAGS_DEFAULT | (this.isStereo ? MORE_FLAGS_STEREO : 0));
            record[LYR_TRIGGER] = 0;
            record[LYR_ENABLE_HIGH] = 0;
            for (int i = LYR_DELAY; i <= LYR_CROSSFADE; i++)
                record[i] = 0;

            // The user envelope replaces the natural one of the samples
            System.arraycopy (ENVC_TEMPLATE, 0, record, ENVC_OFFSET, ENVC_TEMPLATE.length);
            KurzweilEnvelope envelope = this.amplitudeEnvelope;
            if (envelope == null)
            {
                // Full sustain
                envelope = new KurzweilEnvelope ();
                envelope.setStage (0, 0, 100);
                envelope.setStage (3, 0, 100);
            }
            writeEnvelope (record, ENV1_OFFSET, envelope);

            // The keymap block: a stereo layer plays the same keymap on both sides
            record[CAL_OFFSET + CAL_TRANSPOSE] = (byte) this.transpose;
            writeUnsigned16 (record, CAL_OFFSET + CAL_KEYMAP_2, this.keymapID);
            writeUnsigned16 (record, CAL_OFFSET + CAL_KEYMAP, this.keymapID);
            record[CAL_OFFSET + CAL_LAYER_COUNTER] = 0;

            return record;
        }


        /**
         * Get the ID of the keymap object of this layer.
         *
         * @return The keymap object ID
         */
        public int getKeymapID ()
        {
            return this.keymapID;
        }


        /**
         * Set the ID of the keymap object of this layer.
         *
         * @param keymapID The keymap object ID
         */
        public void setKeymapID (final int keymapID)
        {
            this.keymapID = keymapID;
        }


        /**
         * Get the ID of the second keymap object of a stereo layer, which plays on the right side.
         * If it is the ID of the first keymap, the keymap references stereo samples.
         *
         * @return The keymap object ID
         */
        public int getSecondKeymapID ()
        {
            return this.secondKeymapID;
        }


        /**
         * Does the layer play its keymap(s) in stereo?
         *
         * @return True if stereo
         */
        public boolean isStereo ()
        {
            return this.isStereo;
        }


        /**
         * Set if the layer plays its keymap in stereo (stereo sample objects).
         *
         * @param isStereo True if stereo
         */
        public void setStereo (final boolean isStereo)
        {
            this.isStereo = isStereo;
        }


        /**
         * Is the layer enabled? A layer whose enable control source is 'OFF' never plays.
         *
         * @return True if enabled
         */
        public boolean isEnabled ()
        {
            return this.isEnabled;
        }


        /**
         * Get the lowest MIDI note of the layer.
         *
         * @return The lowest note
         */
        public int getLowKey ()
        {
            return this.lowKey;
        }


        /**
         * Get the highest MIDI note of the layer.
         *
         * @return The highest note
         */
        public int getHighKey ()
        {
            return this.highKey;
        }


        /**
         * Set the key range of the layer.
         *
         * @param lowKey The lowest MIDI note
         * @param highKey The highest MIDI note
         */
        public void setKeyRange (final int lowKey, final int highKey)
        {
            this.lowKey = lowKey;
            this.highKey = highKey;
        }


        /**
         * Get the transposition of the layer.
         *
         * @return The transposition in semi-tones
         */
        public int getTranspose ()
        {
            return this.transpose;
        }


        /**
         * Get the lowest MIDI velocity of the layer. The device stores the velocity window as two
         * 0..7 dynamic marks (ppp..fff); each mark covers 16 velocities.
         *
         * @return The lowest velocity
         */
        public int getVelocityLow ()
        {
            return this.velocityLow;
        }


        /**
         * Get the highest MIDI velocity of the layer.
         *
         * @return The highest velocity
         */
        public int getVelocityHigh ()
        {
            return this.velocityHigh;
        }


        /**
         * Set the velocity range of the layer.
         *
         * @param velocityLow The lowest MIDI velocity
         * @param velocityHigh The highest MIDI velocity
         */
        public void setVelocityRange (final int velocityLow, final int velocityHigh)
        {
            this.velocityLow = velocityLow;
            this.velocityHigh = velocityHigh;
        }


        /**
         * Get the amplitude envelope of the layer.
         *
         * @return The envelope or null if the layer uses the 'natural' envelope of the samples
         */
        public KurzweilEnvelope getAmplitudeEnvelope ()
        {
            return this.isNaturalEnvelope ? null : this.amplitudeEnvelope;
        }


        /**
         * Set the amplitude envelope of the layer.
         *
         * @param envelope The envelope
         */
        public void setAmplitudeEnvelope (final KurzweilEnvelope envelope)
        {
            this.amplitudeEnvelope = envelope;
            this.isNaturalEnvelope = false;
        }


        /**
         * Get the filter envelope (ENV2) of the layer.
         *
         * @return The envelope or null if it is not routed to the filter frequency
         */
        public KurzweilEnvelope getFilterEnvelope ()
        {
            return this.cutoffModulationSource == CONTROL_SOURCE_ENV2 && this.cutoffModulationDepth != 0 ? this.filterEnvelope : null;
        }


        /**
         * Get the modulation depth of the filter envelope.
         *
         * @return The depth in cents
         */
        public int getFilterEnvelopeDepth ()
        {
            return this.cutoffModulationDepth;
        }


        /**
         * Get the depth of the velocity modulation of the filter frequency (the attack velocity as
         * the cutoff control source).
         *
         * @return The depth in cents, 0 if the velocity is not routed to the filter frequency
         */
        public int getCutoffVelocityDepth ()
        {
            return this.cutoffModulationSource == CONTROL_SOURCE_VELOCITY ? this.cutoffModulationDepth : 0;
        }


        /**
         * Get the type of the filter which the layer implements.
         *
         * @return The filter type or null if the layer has no or an unsupported filter function
         */
        public FilterType getFilterType ()
        {
            return switch (this.filterFunction)
            {
                case FUNCTION_LOW_PASS_1P, FUNCTION_LOW_PASS_2P, FUNCTION_LOW_PASS_4P -> FilterType.LOW_PASS;
                case FUNCTION_HIGH_PASS_4P -> FilterType.HIGH_PASS;
                case FUNCTION_BAND_PASS_2P, FUNCTION_BAND_PASS_4P -> FilterType.BAND_PASS;
                case FUNCTION_NOTCH_4P -> FilterType.BAND_REJECTION;
                default -> null;
            };
        }


        /**
         * Get the number of poles of the filter.
         *
         * @return The number of poles
         */
        public int getFilterPoles ()
        {
            return switch (this.filterFunction)
            {
                case FUNCTION_LOW_PASS_1P -> 1;
                case FUNCTION_LOW_PASS_2P, FUNCTION_BAND_PASS_2P -> 2;
                default -> 4;
            };
        }


        /**
         * Get the cutoff frequency of the filter.
         *
         * @return The cutoff frequency in Hertz
         */
        public double getCutoffFrequency ()
        {
            return KurzweilProgram.decodeCutoff (this.cutoff);
        }


        /**
         * Get the resonance of the filter.
         *
         * @return The resonance in the range of [0..1] where 1 represents 40dB
         */
        public double getResonance ()
        {
            // The value is stored in 0.5dB steps with a maximum of 24dB
            return Math.clamp (this.resonance, 0, 48) / 80.0;
        }


        private static boolean isFilterFunction (final int function)
        {
            return switch (function)
            {
                case FUNCTION_LOW_PASS_1P, FUNCTION_LOW_PASS_2P, FUNCTION_BAND_PASS_2P, FUNCTION_LOW_PASS_4P, FUNCTION_HIGH_PASS_4P, FUNCTION_BAND_PASS_4P, FUNCTION_NOTCH_4P -> true;
                default -> false;
            };
        }


        private static boolean hasResonance (final int function)
        {
            return function != FUNCTION_LOW_PASS_1P && function != FUNCTION_BAND_PASS_2P;
        }
    }


    private final int         id;
    private final String      name;
    private final List<Layer> layers      = new ArrayList<> ();
    private String            version     = "";
    private boolean           isTruncated = false;


    /**
     * Constructor for a new empty program.
     *
     * @param id The object ID
     * @param name The name of the program, maximum 16 characters
     */
    public PC3Program (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Constructor. Decodes the object data (the part after the object name).
     *
     * @param id The object ID
     * @param name The name of the program
     * @param data The object data
     */
    public PC3Program (final int id, final String name, final byte [] data)
    {
        this.id = id;
        this.name = name;

        if (data.length < HEADER_LENGTH || data[0] != 'P' || data[1] != 'C' || data[2] != '3')
        {
            this.isTruncated = true;
            return;
        }
        this.version = data[3] + "." + data[4];

        final int numLayers = data[NUM_LAYERS_OFFSET] & 0xFF;
        for (int i = 0; i < numLayers; i++)
        {
            final int start = HEADER_LENGTH + i * LAYER_LENGTH;
            if (start + LAYER_LENGTH > data.length || data[start] != LAYER_TAG)
            {
                this.isTruncated = true;
                break;
            }
            this.layers.add (new Layer (Arrays.copyOfRange (data, start, start + LAYER_LENGTH)));
        }
    }


    /**
     * Create the object data (the part after the object name) for writing.
     *
     * @return The object data
     * @throws IOException Could not create the data
     */
    public byte [] createObjectData () throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        final byte [] header = HEADER_TEMPLATE.clone ();
        header[NUM_LAYERS_OFFSET] = (byte) this.layers.size ();
        out.write (header);
        for (final Layer layer: this.layers)
            out.write (layer.createRecord ());
        out.write (TAIL_TEMPLATE);
        return out.toByteArray ();
    }


    /**
     * Get the object ID.
     *
     * @return The ID
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the name of the program.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the version of the program object (4.0 = PC3, 4.5 = PC3K, 4.7 = Forte).
     *
     * @return The version
     */
    public String getVersion ()
    {
        return this.version;
    }


    /**
     * Could the layers of the program not be read completely? This happens when the program has an
     * unknown layout.
     *
     * @return True if layers are missing
     */
    public boolean isTruncated ()
    {
        return this.isTruncated;
    }


    /**
     * Get the layers of the program.
     *
     * @return The layers
     */
    public List<Layer> getLayers ()
    {
        return this.layers;
    }


    /**
     * Add a layer.
     *
     * @param layer The layer to add
     */
    public void addLayer (final Layer layer)
    {
        this.layers.add (layer);
    }


    /**
     * Decode a time code of an envelope stage into seconds. The first codes are 0, 2, 5 and 10
     * milliseconds, from 0.02 seconds on the list follows the time grid of the K2000/K2500/K2600
     * envelopes in which the code 4 is the first grid step.
     *
     * @param timeCode The time code (0-255)
     * @return The time in seconds
     */
    public static double decodeTime (final int timeCode)
    {
        if (timeCode < SHORT_TIMES.length)
            return SHORT_TIMES[Math.max (0, timeCode)];
        return KurzweilEnvelope.decodeTime (timeCode);
    }


    /**
     * Encode a time in seconds into the time code of an envelope stage.
     *
     * @param seconds The time in seconds
     * @return The time code in the range of 0..255
     */
    public static int encodeTime (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        if (seconds < 0.0035)
            return 1;
        if (seconds < 0.0075)
            return 2;
        if (seconds < 0.015)
            return 3;
        return Math.max (SHORT_TIMES.length, KurzweilEnvelope.encodeTime (seconds));
    }


    /**
     * Read an envelope segment: the tag, a flags byte and 7 (level, time) byte pairs.
     *
     * @param record The layer record
     * @param offset The offset of the segment
     * @return The envelope
     */
    private static KurzweilEnvelope readEnvelope (final byte [] record, final int offset)
    {
        final KurzweilEnvelope envelope = new KurzweilEnvelope ();
        for (int stage = 0; stage < KurzweilEnvelope.NUM_STAGES; stage++)
            envelope.setStage (stage, decodeTime (record[offset + 3 + stage * 2] & 0xFF), record[offset + 2 + stage * 2]);
        return envelope;
    }


    /**
     * Write an envelope segment: the flags byte and 7 (level, time) byte pairs behind the tag.
     *
     * @param record The layer record
     * @param offset The offset of the segment
     * @param envelope The envelope
     */
    private static void writeEnvelope (final byte [] record, final int offset, final KurzweilEnvelope envelope)
    {
        // No envelope loop
        record[offset + 1] = 0;
        for (int stage = 0; stage < KurzweilEnvelope.NUM_STAGES; stage++)
        {
            record[offset + 2 + stage * 2] = (byte) Math.clamp (envelope.getLevel (stage), 0, 100);
            record[offset + 3 + stage * 2] = (byte) encodeTime (envelope.getTime (stage));
        }
    }


    /**
     * Encode a MIDI velocity range into the packed velocity window byte of a layer: the low and
     * high velocity as 0..7 dynamic marks (ppp..fff), the high mark stored inverted. A full range
     * therefore encodes as 0.
     *
     * @param velocityLow The lowest MIDI velocity
     * @param velocityHigh The highest MIDI velocity
     * @return The packed velocity window byte
     */
    private static int encodeVelocityRange (final int velocityLow, final int velocityHigh)
    {
        final int lowMark = Math.clamp ((int) Math.round (velocityLow * 7 / 127.0), 0, 7);
        final int highMark = Math.clamp ((int) Math.round (velocityHigh * 7 / 127.0), 0, 7);
        return (lowMark & 7) << 3 | 7 - highMark & 7;
    }


    private static int readUnsigned16 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }


    private static void writeUnsigned16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value >>> 8 & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }
}
