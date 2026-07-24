// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil program object (type 36). Consists of a sequence of segments, each a tag byte followed
 * by a fixed length data block. A layer segment opens a layer with its key and velocity window; the
 * following segments up to the next layer describe it: the envelope control and envelope segments
 * hold the amplitude and filter envelopes, the calibration segment references the keymap and
 * selects the DSP algorithm and the four function page segments configure the DSP functions F1-F3
 * (the filter) and the final amplifier. When creating a program the same segment values are written
 * which KurzFiler uses for its generated instrument programs (verified to load on the devices).
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilProgram
{
    private static final int TAG_PROGRAM             = 8;
    private static final int TAG_LAYER               = 9;
    private static final int TAG_FX                  = 15;
    private static final int TAG_ENVELOPE_CONTROL    = 32;
    private static final int TAG_ENVELOPE            = 33;
    private static final int TAG_ENVELOPE_2          = 34;
    private static final int TAG_CALIBRATION         = 64;
    private static final int TAG_F1_PAGE             = 80;
    private static final int TAG_F2_PAGE             = 81;
    private static final int TAG_F3_PAGE             = 82;
    private static final int TAG_AMPLIFIER_PAGE      = 83;

    /** A program which only uses K2000 features. */
    public static final int  MODE_K2000              = 2;

    /** The control source code of the second envelope (ENV2). */
    private static final int CONTROL_SOURCE_ENV2     = 121;
    /** The control source code for 'always on'. */
    private static final int CONTROL_SOURCE_ON       = 0x7F;

    // The DSP function types of the F1 page which implement a filter
    private static final int FILTER_NONE             = 62;
    private static final int FILTER_LOW_PASS_2P      = 2;
    private static final int FILTER_BAND_PASS_2P     = 3;
    private static final int FILTER_LOW_PASS_1P      = 15;
    private static final int FILTER_LOW_PASS_4P      = 50;
    private static final int FILTER_HIGH_PASS_4P     = 54;
    private static final int FILTER_BAND_PASS_4P     = 55;
    private static final int FILTER_NOTCH_4P         = 56;

    // The DSP function types of the F2 and F3 pages
    private static final int F2_RESONANCE            = 16;
    private static final int F2_NONE                 = 61;
    private static final int F3_SEPARATION           = 18;
    private static final int F3_NONE                 = 60;

    /** The F2 page of the 2-pole bandpass holds the width instead of the resonance. */
    private static final int BAND_PASS_DEFAULT_WIDTH = 64;


    /**
     * One layer of a program: a keymap mapped to a key and velocity range with its DSP settings.
     */
    public static class Layer
    {
        private int              keymapID;
        private int              lowKey               = 0;
        private int              highKey              = 127;
        private int              transpose            = 0;
        private int              velocityLow          = 1;
        private int              velocityHigh         = 127;

        private boolean          isNaturalEnvelope    = true;
        private KurzweilEnvelope amplitudeEnvelope    = null;
        private KurzweilEnvelope filterEnvelope       = null;
        private int              filterEnvelopeSource = 0;
        private int              filterEnvelopeDepth  = 0;

        private int              algorithm            = 1;
        private int              filterType           = FILTER_NONE;
        private int              cutoff               = 0;
        private int              f2Type               = F3_NONE;
        private int              f2Value              = 0;
        private int              f3Type               = F3_NONE;


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
            return this.filterEnvelopeSource == CONTROL_SOURCE_ENV2 && this.filterEnvelopeDepth != 0 ? this.filterEnvelope : null;
        }


        /**
         * Get the modulation depth of the filter envelope.
         *
         * @return The depth in cents
         */
        public int getFilterEnvelopeDepth ()
        {
            return this.filterEnvelopeDepth;
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
            this.filterEnvelopeSource = CONTROL_SOURCE_ENV2;
            this.filterEnvelopeDepth = depth;
        }


        /**
         * Get the type of the filter which the F1 function page implements.
         *
         * @return The filter type or null if the page holds no or an unsupported DSP function
         */
        public FilterType getFilterType ()
        {
            return switch (this.filterType)
            {
                case FILTER_LOW_PASS_1P, FILTER_LOW_PASS_2P, FILTER_LOW_PASS_4P -> FilterType.LOW_PASS;
                case FILTER_HIGH_PASS_4P -> FilterType.HIGH_PASS;
                case FILTER_BAND_PASS_2P, FILTER_BAND_PASS_4P -> FilterType.BAND_PASS;
                case FILTER_NOTCH_4P -> FilterType.BAND_REJECTION;
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
            return switch (this.filterType)
            {
                case FILTER_LOW_PASS_1P -> 1;
                case FILTER_LOW_PASS_2P, FILTER_BAND_PASS_2P -> 2;
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
            return decodeCutoff (this.cutoff);
        }


        /**
         * Get the resonance of the filter. The 1-pole low-pass has a fixed resonance and the F2
         * page of the 2-pole bandpass holds the width; both report no resonance.
         *
         * @return The resonance in the range of [0..1] where 1 represents 40dB
         */
        public double getResonance ()
        {
            if (this.f2Type != F2_RESONANCE || !hasResonance (this.filterType))
                return 0;
            // The value is stored in 0.5dB steps with a maximum of 24dB
            return Math.clamp (this.f2Value, 0, 48) / 80.0;
        }


        /**
         * Set the filter of the layer. Selects the DSP algorithm and the function types of the
         * F1-F3 pages which implement the nearest matching filter of the device: the 1-pole and
         * 2-pole low-pass, the 2-pole bandpass and the 4-pole low-pass, high-pass, bandpass and
         * notch filters.
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
                        // The 1-pole low-pass has a fixed resonance, its F2 page is unused
                        this.algorithm = 16;
                        this.filterType = FILTER_LOW_PASS_1P;
                        this.f2Type = F2_NONE;
                        this.f3Type = F3_SEPARATION;
                    }
                    else if (poles == 2)
                    {
                        this.algorithm = 5;
                        this.filterType = FILTER_LOW_PASS_2P;
                        this.f2Type = F2_RESONANCE;
                        this.f3Type = F3_NONE;
                    }
                    else
                    {
                        this.algorithm = 1;
                        this.filterType = FILTER_LOW_PASS_4P;
                        this.f2Type = F2_RESONANCE;
                        this.f3Type = F3_SEPARATION;
                    }
                    break;

                case HIGH_PASS:
                    this.algorithm = 1;
                    this.filterType = FILTER_HIGH_PASS_4P;
                    this.f2Type = F2_RESONANCE;
                    this.f3Type = F3_SEPARATION;
                    break;

                case BAND_PASS:
                    if (poles <= 2)
                    {
                        this.algorithm = 5;
                        this.filterType = FILTER_BAND_PASS_2P;
                        this.f2Type = F2_RESONANCE;
                        this.f3Type = F3_NONE;
                    }
                    else
                    {
                        this.algorithm = 1;
                        this.filterType = FILTER_BAND_PASS_4P;
                        this.f2Type = F2_RESONANCE;
                        this.f3Type = F3_SEPARATION;
                    }
                    break;

                default:
                case BAND_REJECTION:
                    this.algorithm = 1;
                    this.filterType = FILTER_NOTCH_4P;
                    this.f2Type = F2_RESONANCE;
                    this.f3Type = F3_SEPARATION;
                    break;
            }

            this.cutoff = encodeCutoff (cutoffFrequency);
            if (this.filterType == FILTER_BAND_PASS_2P)
                this.f2Value = BAND_PASS_DEFAULT_WIDTH;
            else if (hasResonance (this.filterType))
                this.f2Value = Math.clamp ((int) Math.round (resonance * 80), 0, 48);
        }


        /**
         * Check if the F1 DSP function is a filter with a resonance on its F2 page. The 1-pole
         * low-pass has a fixed resonance and the F2 page of the 2-pole bandpass holds the width.
         *
         * @param filterType The DSP function type of the F1 page
         * @return True if the F2 page holds the resonance
         */
        private static boolean hasResonance (final int filterType)
        {
            return switch (filterType)
            {
                case FILTER_LOW_PASS_2P, FILTER_LOW_PASS_4P, FILTER_HIGH_PASS_4P, FILTER_BAND_PASS_4P, FILTER_NOTCH_4P -> true;
                default -> false;
            };
        }
    }


    /**
     * One segment: a tag with its fixed length data.
     *
     * @param tag The tag
     * @param data The data
     */
    private record Segment (int tag, byte [] data)
    {
        /** {@inheritDoc} */
        @Override
        public int hashCode ()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode (this.data);
            result = prime * result + Objects.hash (Integer.valueOf (this.tag));
            return result;
        }


        /** {@inheritDoc} */
        @Override
        public boolean equals (final Object obj)
        {
            if (this == obj)
                return true;
            if ((obj == null) || (this.getClass () != obj.getClass ()))
                return false;
            final Segment other = (Segment) obj;
            return Arrays.equals (this.data, other.data) && this.tag == other.tag;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "Segment [tag=" + this.tag + ", data=" + Arrays.toString (this.data) + "]";
        }
    }


    private final int           id;
    private final String        name;
    private final List<Segment> segments = new ArrayList<> ();


    /**
     * Constructor for a new empty program.
     *
     * @param id The object ID
     * @param name The name of the program, maximum 16 characters
     */
    public KurzweilProgram (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Constructor. Reads the object data (the part after the object name) from the stream.
     *
     * @param id The object ID
     * @param name The name of the program
     * @param in The input stream to read from
     * @throws IOException Could not read the object
     */
    public KurzweilProgram (final int id, final String name, final InputStream in) throws IOException
    {
        this.id = id;
        this.name = name;

        while (true)
        {
            final int tag = in.read ();
            if (tag <= 0)
                break;
            final int length = getSegmentDataLength (tag);
            final byte [] data = in.readNBytes (length);
            if (data.length < length)
                break;
            this.segments.add (new Segment (tag, data));
        }
    }


    /**
     * Get the length of the data block of a segment.
     *
     * @param tag The tag of the segment
     * @return The length in bytes
     */
    private static int getSegmentDataLength (final int tag)
    {
        if (tag == TAG_PROGRAM || tag == TAG_LAYER)
            return 15;
        if (tag == TAG_FX)
            return 7;

        return switch (tag & 0xF8)
        {
            // ASR and LFO blocks
            case 16 -> 7;
            // Function blocks
            case 24 -> 3;
            // Envelope control, envelope and impact blocks
            case 32 -> 15;
            // Calibration blocks
            case 64 -> 31;
            // Output blocks
            case 80 -> 15;
            // KDFX blocks
            case 104 -> 7;
            // KB3 blocks
            case 120 -> 31;
            default -> 0;
        };
    }


    /**
     * Write the object data (the part after the object name) to the stream.
     *
     * @return The object data
     * @throws IOException Could not write the object
     */
    public byte [] createObjectData () throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        for (final Segment segment: this.segments)
        {
            out.write (segment.tag ());
            out.write (segment.data ());
        }
        // The segment list ends with an empty word
        StreamUtils.writeSigned16 (out, 0, true);
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
     * Get the layers of the program. A layer is opened by a layer segment which holds its key and
     * velocity window; the following segments up to the next layer hold its envelopes, the keymap
     * reference and the DSP function pages with the filter.
     *
     * @return The layers
     */
    public List<Layer> getLayers ()
    {
        final List<Layer> layers = new ArrayList<> ();
        Layer currentLayer = null;
        for (final Segment segment: this.segments)
        {
            final byte [] data = segment.data ();
            if (segment.tag () == TAG_LAYER)
            {
                currentLayer = new Layer ();
                currentLayer.lowKey = data[3] & 0x7F;
                currentLayer.highKey = data[4] & 0x7F;
                // The velocity window: the low mark in bits 3-5, the high mark inverted in bits 0-2
                currentLayer.velocityLow = Math.max (1, (data[5] >> 3 & 7) * 16);
                currentLayer.velocityHigh = (7 - (data[5] & 7)) * 16 + 15;
                layers.add (currentLayer);
                continue;
            }
            if (currentLayer == null)
                continue;

            switch (segment.tag ())
            {
                case TAG_ENVELOPE_CONTROL:
                    // 1 = the 'natural' envelope of the samples is active instead of the user one
                    currentLayer.isNaturalEnvelope = data[1] == 1;
                    break;

                case TAG_ENVELOPE:
                    currentLayer.amplitudeEnvelope = new KurzweilEnvelope (data);
                    break;

                case TAG_ENVELOPE_2:
                    currentLayer.filterEnvelope = new KurzweilEnvelope (data);
                    break;

                case TAG_CALIBRATION:
                    currentLayer.transpose = data[1];
                    currentLayer.keymapID = (data[7] & 0xFF) << 8 | data[8] & 0xFF;
                    if (currentLayer.keymapID == 0)
                        currentLayer.keymapID = (data[11] & 0xFF) << 8 | data[12] & 0xFF;
                    currentLayer.algorithm = data[29] & 0xFF;
                    break;

                case TAG_F1_PAGE:
                    currentLayer.filterType = data[0] & 0xFF;
                    currentLayer.cutoff = data[1];
                    currentLayer.filterEnvelopeSource = data[5] & 0xFF;
                    currentLayer.filterEnvelopeDepth = decodeFilterEnvelopeDepth (data[6]);
                    break;

                case TAG_F2_PAGE:
                    currentLayer.f2Type = data[0] & 0xFF;
                    currentLayer.f2Value = data[1] & 0xFF;
                    break;

                default:
                    break;
            }
        }
        return layers;
    }


    /**
     * Add the program block. Must be called once before adding layers.
     */
    public void addProgramBlock ()
    {
        final byte [] data = new byte [15];
        data[0] = MODE_K2000;
        // data[1] holds the number of layers and is increased by addLayer
        // Bend range
        data[3] = 0x37;
        // Portamento
        data[4] = 64;
        this.segments.add (new Segment (TAG_PROGRAM, data));
    }


    /**
     * Add a layer which plays a keymap. Writes the same segment sequence as KurzFiler: layer,
     * envelope control, amplitude envelope, optionally the filter envelope, calibration and the
     * four DSP function pages.
     *
     * @param layer The parameters of the layer
     * @param isStereo True if the keymap references stereo samples
     */
    public void addLayer (final Layer layer, final boolean isStereo)
    {
        byte [] data = new byte [15];
        data[1] = 0x18;
        // Low and high key
        data[3] = (byte) Math.clamp (layer.lowKey, 0, 127);
        data[4] = (byte) Math.clamp (layer.highKey, 0, 127);
        // The velocity window as two 0..7 dynamic marks; the full range encodes as 0
        data[5] = (byte) encodeVelocityRange (layer.velocityLow, layer.velocityHigh);
        // The layer enable control source: always on
        data[6] = CONTROL_SOURCE_ON;
        // Mono / stereo flags
        data[8] = (byte) (isStereo ? 0x24 : 0x04);
        this.segments.add (new Segment (TAG_LAYER, data));

        // Envelope control: all zero = user envelopes instead of the natural ones
        this.segments.add (new Segment (TAG_ENVELOPE_CONTROL, new byte [15]));

        // Amplitude envelope
        data = new byte [15];
        if (layer.amplitudeEnvelope == null)
        {
            // Full sustain
            data[1] = 100;
            data[7] = 100;
        }
        else
            layer.amplitudeEnvelope.write (data);
        this.segments.add (new Segment (TAG_ENVELOPE, data));

        // The filter envelope (ENV2)
        final boolean hasFilterEnvelope = layer.filterEnvelope != null && layer.filterEnvelopeDepth != 0;
        if (hasFilterEnvelope)
        {
            data = new byte [15];
            layer.filterEnvelope.write (data);
            this.segments.add (new Segment (TAG_ENVELOPE_2, data));
        }

        // Calibration: references the keymap and selects the DSP algorithm. The keymap ID must
        // only be in bytes 11/12; writing it to the second keymap slot in bytes 7/8 as well makes
        // the layer claim two keymaps which overflows the device at 4 or more layers
        data = new byte [31];
        data[0] = 0x7F;
        data[1] = (byte) layer.transpose;
        data[3] = 0x2B;
        data[11] = (byte) (layer.keymapID >>> 8 & 0xFF);
        data[12] = (byte) (layer.keymapID & 0xFF);
        data[29] = (byte) layer.algorithm;
        this.segments.add (new Segment (TAG_CALIBRATION, data));

        // The F1 page with the filter type, the cutoff and the filter envelope routing
        data = new byte [15];
        data[0] = (byte) layer.filterType;
        data[1] = (byte) layer.cutoff;
        if (hasFilterEnvelope)
        {
            data[5] = CONTROL_SOURCE_ENV2;
            data[6] = (byte) encodeFilterEnvelopeDepth (layer.filterEnvelopeDepth);
        }
        this.segments.add (new Segment (TAG_F1_PAGE, data));

        // The F2 page with the resonance (or the width of the 2-pole bandpass)
        data = new byte [15];
        data[0] = (byte) layer.f2Type;
        data[1] = (byte) layer.f2Value;
        this.segments.add (new Segment (TAG_F2_PAGE, data));

        // The F3 page
        data = new byte [15];
        data[0] = (byte) layer.f3Type;
        this.segments.add (new Segment (TAG_F3_PAGE, data));

        // The amplifier page
        data = new byte [15];
        data[0] = 1;
        data[2] = 0x70;
        data[13] = 4;
        data[14] = (byte) (isStereo ? 0x90 : 0x00);
        this.segments.add (new Segment (TAG_AMPLIFIER_PAGE, data));

        // Increase the layer counter in the program block
        for (final Segment segment: this.segments)
            if (segment.tag () == TAG_PROGRAM)
            {
                segment.data ()[1]++;
                break;
            }
    }


    /**
     * Encode a MIDI velocity range into the packed velocity window byte of a layer segment: the low
     * and high velocity as 0..7 dynamic marks (ppp..fff), the high mark stored inverted. A full
     * range therefore encodes as 0.
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


    /**
     * Decode the cutoff frequency of a filter function. The byte holds signed semi-tones; 9 is 440
     * Hertz (A4), the device range is -48 (16 Hertz) to 79 (25088 Hertz).
     *
     * @param cutoffSemitones The cutoff in semi-tones
     * @return The cutoff frequency in Hertz
     */
    public static double decodeCutoff (final int cutoffSemitones)
    {
        return 440.0 * Math.pow (2, (cutoffSemitones - 9) / 12.0);
    }


    /**
     * Encode a cutoff frequency into the signed semi-tone byte of a filter function.
     *
     * @param frequency The cutoff frequency in Hertz
     * @return The cutoff in semi-tones in the range of -48..79
     */
    public static int encodeCutoff (final double frequency)
    {
        if (frequency <= 0)
            return -48;
        return Math.clamp ((int) Math.round (9 + 12 * Math.log (frequency / 440.0) / Math.log (2)), -48, 79);
    }


    /**
     * Decode the modulation depth of the filter envelope into cents. Piece-wise linear through the
     * calibration points measured on the device: 0 = 0 cents, 40 = 1200 cents and 127 = 10800
     * cents.
     *
     * @param depthByte The signed depth byte
     * @return The depth in cents
     */
    public static int decodeFilterEnvelopeDepth (final int depthByte)
    {
        final int absDepth = Math.abs (depthByte);
        final int cents = absDepth <= 40 ? absDepth * 30 : (int) Math.round ((absDepth - 29) / 0.0090625);
        return depthByte < 0 ? -cents : cents;
    }


    /**
     * Encode a modulation depth of the filter envelope in cents into the signed depth byte.
     *
     * @param cents The depth in cents
     * @return The signed depth byte in the range of -127..127
     */
    public static int encodeFilterEnvelopeDepth (final int cents)
    {
        final int absCents = Math.abs (cents);
        final int depthByte = Math.clamp (absCents <= 1200 ? (int) Math.round (absCents / 30.0) : (int) Math.round (29 + absCents * 0.0090625), 0, 127);
        return cents < 0 ? -depthByte : depthByte;
    }
}
