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
 * three envelopes, the keymap/pitch block and five DSP records), each opened by its tag byte,
 * followed by the layer effect reference and the layer name.
 *
 * The five DSP records hold the four function blocks of the algorithm between its PITCH and AMP
 * blocks - F1 and F2 in the first two records, F3 and F4 in the last two - and the amplifier in
 * the third record. A 2-block function (e.g. the 2-pole low-pass with its resonance) occupies its
 * record and the following one, whose function byte is then not significant. The records of
 * algorithm 1 are [2-block F1/F2] [1-block F3] [1-block F4], the layout written for a filter.
 *
 * A program which is created for writing copies the header and the trailing block of the default
 * program of the target device generation and the layer record of a program written by a PC3K,
 * and sets only the fields which the conversion controls.
 *
 * @author Jürgen Moßgraber
 */
public class PC3Program
{
    /** The velocity tracking of the amplifier in dB which the devices give a new layer. */
    public static final int       DEFAULT_VELOCITY_TRACKING = 35;
    /** A full cutoff velocity modulation is 8 octaves (the SFZ 'fil_veltrack' range). */
    public static final int       MAX_VELOCITY_MODULATION_CENTS = 9600;

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
    private static final int      CAL_ALGORITHM           = 30;
    private static final int      PAGES_OFFSET            = 162;
    private static final int      PAGE_LENGTH             = 22;

    // The five DSP records
    private static final int      RECORD_F1               = 0;
    private static final int      RECORD_F2               = 1;
    private static final int      RECORD_AMPLIFIER        = 2;
    private static final int      RECORD_F3               = 3;
    private static final int      RECORD_F4               = 4;

    // The fields of a DSP record
    private static final int      PAGE_FUNCTION           = 1;
    private static final int      PAGE_COARSE             = 2;
    private static final int      PAGE_SOURCE_1           = 6;
    private static final int      PAGE_DEPTH_1            = 7;
    private static final int      PAGE_VELOCITY_TRACKING  = 5;
    private static final int      PAGE_TYPE               = 12;
    private static final int      PAGE_OUTPUT             = 14;

    /** The type field of the record of a 2-block function. */
    private static final int      PAGE_TYPE_TWO_BLOCK     = 0x0200;
    /** The type field of the record of a 1-block function. */
    private static final int      PAGE_TYPE_ONE_BLOCK     = 0x0300;

    /** The algorithm whose F1 slot takes a 2-block function, followed by two 1-block slots. */
    private static final int      ALGORITHM_TWO_BLOCK     = 1;

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

    // The DSP functions (the numbering of the K2000/K2500/K2600 functions)
    private static final int      FUNCTION_NONE           = 0;
    private static final int      FUNCTION_LOW_PASS_2P    = 2;
    private static final int      FUNCTION_BAND_PASS_2P   = 3;
    private static final int      FUNCTION_NOTCH_2P       = 4;
    private static final int      FUNCTION_LOW_PASS_1P    = 15;
    private static final int      FUNCTION_HIGH_PASS_1P   = 16;
    private static final int      FUNCTION_LOW_PASS_4P    = 50;
    private static final int      FUNCTION_HIGH_PASS_4P   = 54;
    private static final int      FUNCTION_BAND_PASS_4P   = 55;
    private static final int      FUNCTION_NOTCH_4P       = 56;
    /** The function byte which the devices write into the second record of a 2-block function. */
    private static final int      FUNCTION_SECOND_BLOCK   = 60;

    /**
     * The width of the 2-pole bandpass and notch filters on their second record - the value of most
     * factory layers.
     */
    private static final int      BAND_WIDTH_DEFAULT      = 53;

    /** The envelope times of the first codes of the time list: 0, 2, 5 and 10 milliseconds. */
    private static final double[] SHORT_TIMES             =
    {
        0,
        0.002,
        0.005,
        0.01
    };

    /**
     * The program layout of a device generation: the header (229 bytes) and the trailing block of
     * the default program of the generation, which a written program copies. The number of layers
     * is patched into the header, the effect chain references are cleared.
     */
    public enum Layout
    {
        /** PC3K (program version 4.5, the trailing block holds no controller information). */
        PC3K("50433304055043330303000000000000136b6579776f7264312c206b6579776f7264320003050805020037400000ffffffffffffffffffffffff370000000000001000000000001800000014000000000018000000030351000000000000007f7f030352000000000000007f7f030353000000000000007f7f50726f6772616d524655303132353637000000000000000000000000000000000000000000000000000000000000000000000000000000006e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000048616d6d", "0001020000000300000105007f0000000000000008006400000164000018020000000000000004000000000001000002e80000000f0000000124282f2b373b3e"),
        /** Forte and Forte SE (program version 4.9, the default program of the Forte OS 4.4). */
        FORTE("50433304095043330303140000000000136b6579776f7264312c206b6579776f72643200030508050104374000000000000000000000000000003700000001000011000000000019091c5a1500000000001b000000030351000000000000007f7f030352000000000000007f7f030353000000000000007f7f50726f6772616d524655303132353637000000000000000000000000000000000000000000000000000000000000000000000000000000006e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000048616d6d", "000102000f00000040ffff00085375737461696e00000000160000000f416d7020456e762041747461636b000000001700000010416d7020456e762052656c65617365000000001c0000000e52657665726220416d6f756e7400000000520000000f416d7020456e7620496d70616374000000000b007f000b45787072657373696f6e000000005a0000000e52657665726220456e61626c65000000000c007f000f4c502046696c746572204672657100000000500000000c566172696174696f6e203200000000510000000c566172696174696f6e203300000000530000001b52656c656173652053616d706c6573204f6e2f4f6666202020200000000042ffff000b536f7374656e75746f20000000000d0000000f4c502046696c74657220526573200000000043ffff000b536f667420506564616c0000000080000000194b75727a7765696c20466163746f72792050726f6772616d2e0003001801160016011700170140ff40011c001c01520052010bff0b015a005a0150005001510051015300530142ff42010c7f0c010d000d0143ff43020900090456005604194019041a401a04570057041b0f1b04590059041d001d040400040401000100020109007f000000000000000800640000016400001802000000000000000400000000000000000000000000000001000000000001000000000001000000000000000000010000000000010000007100000014000000013c3d3e3f4041424300010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000124000000010000000000002ee00000000400000004000000000000000000000000000000000000000000000000000000010000000c0000000000000064000000000000000000000000000000000000000000000000000000000000007f0000004000000060000000400000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff0000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff0000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff000000060000012800000001000000000000000000000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606000000000000000000000000000000000ffffffffffffffffffffff"),
        /** PC4, PC4 SE and K2700 (program version 4.9, the default program of the OS 4.5). */
        K2700("504333040950433303030d0000000000136b6579776f7264312c206b6579776f726432000305080501003740000000000000000000000000000037000000010000110000000000190000001500000000001b000000030351000000000000007f7f030352000000000000007f7f030353000000000000007f7f50726f6772616d524655303132353637000000000000000000000000000000000000000000000000000000000000000000000000000000006e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000048616d6d", "000102001000000040ffff00085375737461696e00000000160000000741747461636b00000000170000000852656c65617365000000001c0000000e52657665726220416d6f756e74000000005200000007496d70616374000000000b007f000b45787072657373696f6e000000005a0000000e52657665726220456e61626c65000000000c007f000c46696c74657220467265710000000042ffff000b536f7374656e75746f20000000000d0000000c46696c74657220526573200000000043ffff0006536f667420000000000a0040008050616e007765696c20466163746f72792050726f6772616d2e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000085669627261746f000000004500000007467265657a650000000015ffff0080417578205069746368000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000080000000000003002401160016011700170140ff40011c001c01520052010bff0b015a005a0142ff42010c7f0c010d000d0143ff43010a400a01010001014500530209100904190019041a001a041b001b0410001004030003044f404f0448404804494049044740470450005004464046044e404e04120012045700570459005904560056041100110418001804550055040e000e0451005100020109007f000000000000000800640000016400001802000000000000000400000000000000000000000000000001000000000001000000000001000000000000000000010000000000010000007100000014000000013c3d3e3f4041424300010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000100000124000000010000000000002ee00000000400000004000000000000000000000000000000000000000000000000000000010000000c0000000000000064000000000000000000000000000000000000000000000000000000000000007f0000004000000060000000400000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff0000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff0000000000000000000000000000006400000000000000000000000000000000000000000000000000000000ffffffffffffffffffffffffffffffff000000060000012800000001000000000000000000000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff6060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060606060000000000000000000000000000000006060606060606060606060");


        private final byte [] header;
        private final byte [] tail;


        Layout (final String header, final String tail)
        {
            this.header = HexFormat.of ().parseHex (header);
            this.tail = HexFormat.of ().parseHex (tail);
        }
    }


    /**
     * A layer record written by a PC3K which plays a RAM sample keymap straight through the
     * amplifier (algorithm 5 without DSP functions).
     */
    private static final byte []  LAYER_TEMPLATE          = HexFormat.of ().parseHex ("09f834600082001400d100000064030500006a0000107f01353535117f01353535180100001902000014002e00000415002e0000041a0300001b0400002001000000490000004800f1004800270000004600210064000000000064000000000000002200640000000000640000000000000023006400000000006400000000000000407f00002b000000040900400409000000020000000000000100000000000500500000000000000001000000030000000000000100005100000000000000010000000000000000000001000053000140002d00000100000000002000000000010000520000000000000001000000000003000000000100005400000000000000000000000300000000000001c040030351000000000000007f7f00000000001420202020202020202020202020202020202020204c61796572524655");

    /** The envelope control segment: user envelope, no real-time control of the envelope rates. */
    private static final byte []  ENVC_TEMPLATE           = HexFormat.of ().parseHex ("200000000049000000480000004800");

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
        private int              velocityTracking       = DEFAULT_VELOCITY_TRACKING;

        private int              filterFunction         = FUNCTION_NONE;
        private int              cutoff                 = 0;
        private int              secondParameter        = 0;


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

            // The amplifier: its velocity tracking in dB
            this.velocityTracking = record[PAGES_OFFSET + RECORD_AMPLIFIER * PAGE_LENGTH + PAGE_VELOCITY_TRACKING];

            // The filter: the first function block which holds one of the filter functions of the
            // K2x00 family. F2 and F4 are blocks of their own only when F1 and F3 hold a 1-block
            // function - a 2-block function keeps its second parameter (the resonance) in the
            // following record, whose function byte is not significant
            for (final int rec: new int []
            {
                RECORD_F1,
                RECORD_F2,
                RECORD_F3,
                RECORD_F4
            })
            {
                final int offset = PAGES_OFFSET + rec * PAGE_LENGTH;
                if ((rec == RECORD_F2 || rec == RECORD_F4) && !isOneBlockFunction (record[offset - PAGE_LENGTH + PAGE_FUNCTION] & 0xFF))
                    continue;
                final int function = record[offset + PAGE_FUNCTION] & 0xFF;
                if (!isFilterFunction (function))
                    continue;
                this.filterFunction = function;
                this.cutoff = record[offset + PAGE_COARSE];
                this.cutoffModulationSource = record[offset + PAGE_SOURCE_1] & 0xFF;
                this.cutoffModulationDepth = KurzweilProgram.decodeModulationDepth (record[offset + PAGE_DEPTH_1]);
                if (hasResonance (function) && (rec == RECORD_F1 || rec == RECORD_F3))
                {
                    // 0.5dB steps in the range of the Res parameter (-12 to 24 dB)
                    final int value = record[offset + PAGE_LENGTH + PAGE_COARSE];
                    if (value >= -24 && value <= 48)
                        this.secondParameter = value;
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

            // The filter uses algorithm 1: a 2-block function (the 2-pole filters with their
            // resonance or width) takes the slot F1/F2, a 1-block function (the 1-pole filters)
            // the slot F3; the other slots stay empty
            if (this.filterFunction != FUNCTION_NONE)
            {
                record[CAL_OFFSET + CAL_ALGORITHM] = (byte) ALGORITHM_TWO_BLOCK;
                if (isOneBlockFunction (this.filterFunction))
                {
                    writePage (record, RECORD_F1, FUNCTION_NONE, 0, PAGE_TYPE_ONE_BLOCK);
                    writePage (record, RECORD_F2, FUNCTION_NONE, 0, 0);
                    this.writeFilterPage (record, RECORD_F3, PAGE_TYPE_ONE_BLOCK);
                }
                else
                {
                    this.writeFilterPage (record, RECORD_F1, PAGE_TYPE_TWO_BLOCK);
                    writePage (record, RECORD_F2, FUNCTION_SECOND_BLOCK, this.secondParameter, 0);
                    writePage (record, RECORD_F3, FUNCTION_NONE, 0, PAGE_TYPE_ONE_BLOCK);
                }
                writePage (record, RECORD_F4, FUNCTION_NONE, 0, PAGE_TYPE_ONE_BLOCK);

                if (this.getFilterEnvelope () != null)
                    writeEnvelope (record, ENV2_OFFSET, this.filterEnvelope);
            }

            // The amplifier: the velocity tracking
            record[PAGES_OFFSET + RECORD_AMPLIFIER * PAGE_LENGTH + PAGE_VELOCITY_TRACKING] = (byte) Math.clamp (this.velocityTracking, 0, 96);

            return record;
        }


        /**
         * Write the DSP record of the filter: the function, the cutoff and the modulation of the
         * cutoff by the filter envelope or the attack velocity.
         *
         * @param record The layer record
         * @param rec The index of the DSP record
         * @param type The type field of the record
         */
        private void writeFilterPage (final byte [] record, final int rec, final int type)
        {
            writePage (record, rec, this.filterFunction, this.cutoff, type);
            if (this.cutoffModulationSource == CONTROL_SOURCE_OFF || this.cutoffModulationDepth == 0)
                return;
            final int offset = PAGES_OFFSET + rec * PAGE_LENGTH;
            record[offset + PAGE_SOURCE_1] = (byte) this.cutoffModulationSource;
            record[offset + PAGE_DEPTH_1] = (byte) KurzweilProgram.encodeModulationDepth (this.cutoffModulationDepth);
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
         * Get the velocity tracking of the amplifier: the level range which the attack velocity
         * covers.
         *
         * @return The velocity tracking in dB
         */
        public int getVelocityTracking ()
        {
            return this.velocityTracking;
        }


        /**
         * Set the velocity tracking of the amplifier.
         *
         * @param velocityTracking The velocity tracking in dB (0-96)
         */
        public void setVelocityTracking (final int velocityTracking)
        {
            this.velocityTracking = velocityTracking;
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
         * Set the filter envelope (ENV2) of the layer and route it to the filter frequency.
         *
         * @param envelope The envelope
         * @param depth The modulation depth in cents
         */
        public void setFilterEnvelope (final KurzweilEnvelope envelope, final int depth)
        {
            this.filterEnvelope = envelope;
            this.cutoffModulationSource = CONTROL_SOURCE_ENV2;
            this.cutoffModulationDepth = depth;
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
         * Route the attack velocity to the filter frequency. Note that the filter page has only one
         * modulation slot: this replaces a set filter envelope.
         *
         * @param depth The modulation depth in cents
         */
        public void setCutoffVelocityModulation (final int depth)
        {
            this.cutoffModulationSource = CONTROL_SOURCE_VELOCITY;
            this.cutoffModulationDepth = depth;
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
                case FUNCTION_HIGH_PASS_1P, FUNCTION_HIGH_PASS_4P -> FilterType.HIGH_PASS;
                case FUNCTION_BAND_PASS_2P, FUNCTION_BAND_PASS_4P -> FilterType.BAND_PASS;
                case FUNCTION_NOTCH_2P, FUNCTION_NOTCH_4P -> FilterType.BAND_REJECTION;
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
                case FUNCTION_LOW_PASS_1P, FUNCTION_HIGH_PASS_1P -> 1;
                case FUNCTION_LOW_PASS_2P, FUNCTION_BAND_PASS_2P, FUNCTION_NOTCH_2P -> 2;
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
            if (!hasResonance (this.filterFunction))
                return 0;
            // The value is stored in 0.5dB steps with a maximum of 24dB
            return Math.clamp (this.secondParameter, 0, 48) / 80.0;
        }


        /**
         * Set the filter of the layer. A low-pass becomes the 1-pole low-pass or, with more poles,
         * the 2-pole low-pass with its resonance, a high-pass the 1-pole high-pass, a bandpass the
         * 2-pole bandpass and a notch filter the 2-pole notch filter of the devices.
         *
         * @param type The filter type
         * @param poles The number of poles
         * @param cutoffFrequency The cutoff frequency in Hertz
         * @param resonance The resonance in the range of [0..1] where 1 represents 40dB
         */
        public void setFilter (final FilterType type, final int poles, final double cutoffFrequency, final double resonance)
        {
            switch (type)
            {
                case LOW_PASS:
                    if (poles <= 1)
                    {
                        // The 1-pole low-pass has a fixed resonance
                        this.filterFunction = FUNCTION_LOW_PASS_1P;
                        this.secondParameter = 0;
                    }
                    else
                    {
                        this.filterFunction = FUNCTION_LOW_PASS_2P;
                        this.secondParameter = Math.clamp ((int) Math.round (resonance * 80), 0, 48);
                    }
                    break;

                case HIGH_PASS:
                    this.filterFunction = FUNCTION_HIGH_PASS_1P;
                    this.secondParameter = 0;
                    break;

                case BAND_PASS:
                    this.filterFunction = FUNCTION_BAND_PASS_2P;
                    this.secondParameter = BAND_WIDTH_DEFAULT;
                    break;

                default:
                case BAND_REJECTION:
                    this.filterFunction = FUNCTION_NOTCH_2P;
                    this.secondParameter = BAND_WIDTH_DEFAULT;
                    break;
            }
            this.cutoff = KurzweilProgram.encodeCutoff (cutoffFrequency);
        }


        private static boolean isFilterFunction (final int function)
        {
            return switch (function)
            {
                case FUNCTION_LOW_PASS_1P, FUNCTION_HIGH_PASS_1P, FUNCTION_LOW_PASS_2P, FUNCTION_BAND_PASS_2P, FUNCTION_NOTCH_2P, FUNCTION_LOW_PASS_4P, FUNCTION_HIGH_PASS_4P, FUNCTION_BAND_PASS_4P, FUNCTION_NOTCH_4P -> true;
                default -> false;
            };
        }


        /**
         * Is the function one of the known 1-block functions, which leave the following record to
         * the next block?
         *
         * @param function The function
         * @return True if 1-block
         */
        private static boolean isOneBlockFunction (final int function)
        {
            return function == FUNCTION_NONE || function == FUNCTION_LOW_PASS_1P || function == FUNCTION_HIGH_PASS_1P;
        }


        /**
         * Does the second record of the filter function hold its resonance? The 1-pole filters have
         * a fixed resonance, the second record of the 2-pole bandpass and notch filters holds the
         * width.
         *
         * @param function The filter function
         * @return True if the second record holds the resonance
         */
        private static boolean hasResonance (final int function)
        {
            return !isOneBlockFunction (function) && function != FUNCTION_BAND_PASS_2P && function != FUNCTION_NOTCH_2P;
        }
    }


    private final int         id;
    private final String      name;
    private final Layout      layout;
    private final List<Layer> layers      = new ArrayList<> ();
    private String            version     = "";
    private boolean           isTruncated = false;


    /**
     * Constructor for a new empty program.
     *
     * @param id The object ID
     * @param name The name of the program, maximum 16 characters
     * @param layout The program layout of the target device generation
     */
    public PC3Program (final int id, final String name, final Layout layout)
    {
        this.id = id;
        this.name = name;
        this.layout = layout;
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
        this.layout = Layout.PC3K;

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
        final byte [] header = this.layout.header.clone ();
        header[NUM_LAYERS_OFFSET] = (byte) this.layers.size ();
        out.write (header);
        for (final Layer layer: this.layers)
            out.write (layer.createRecord ());
        out.write (this.layout.tail);
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
     * Get the version of the program object (4.0 = PC3, 4.5 = PC3K, 4.7 = Forte, 4.9 = K2700).
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
     * Write the function and the main parameter of a DSP record and clear its modulation and
     * output fields. The tag and the pan fields of the template are kept.
     *
     * @param record The layer record
     * @param rec The index of the DSP record
     * @param function The DSP function
     * @param coarse The main parameter
     * @param type The type field of the record
     */
    private static void writePage (final byte [] record, final int rec, final int function, final int coarse, final int type)
    {
        final int offset = PAGES_OFFSET + rec * PAGE_LENGTH;
        record[offset + PAGE_FUNCTION] = (byte) function;
        record[offset + PAGE_COARSE] = (byte) coarse;
        for (int i = PAGE_COARSE + 1; i < PAGE_TYPE; i++)
            record[offset + i] = 0;
        writeUnsigned16 (record, offset + PAGE_TYPE, type);
        writeUnsigned16 (record, offset + PAGE_OUTPUT, 0);
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
