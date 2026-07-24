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

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil program object (type 36). Consists of a sequence of segments, each a tag byte followed
 * by a fixed length data block. Only the segments needed to map layers to keymaps are interpreted;
 * when creating a program the same segment values are written which KurzFiler uses for its
 * generated instrument programs (verified to load on the devices).
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilProgram
{
    private static final int TAG_PROGRAM          = 8;
    private static final int TAG_LAYER            = 9;
    private static final int TAG_FX               = 15;
    private static final int TAG_ENVELOPE_CONTROL = 32;
    private static final int TAG_ENVELOPE         = 33;
    private static final int TAG_CALIBRATION      = 64;

    /** A program which only uses K2000 features. */
    public static final int  MODE_K2000           = 2;


    /** One layer of a program: a keymap mapped to a key range. */
    public static class Layer
    {
        private int keymapID;
        private int lowKey    = 0;
        private int highKey   = 127;
        private int transpose = 0;


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
     * Get the layers of the program with their keymap references. A layer is opened by a layer
     * segment; the following calibration segment holds the ID of its keymap and its transposition.
     *
     * @return The layers
     */
    public List<Layer> getLayers ()
    {
        final List<Layer> layers = new ArrayList<> ();
        Layer currentLayer = null;
        for (final Segment segment: this.segments)
            if (segment.tag () == TAG_LAYER)
            {
                currentLayer = new Layer ();
                currentLayer.lowKey = segment.data ()[3] & 0x7F;
                currentLayer.highKey = segment.data ()[4] & 0x7F;
                layers.add (currentLayer);
            }
            else if (segment.tag () == TAG_CALIBRATION && currentLayer != null)
            {
                currentLayer.transpose = segment.data ()[1];
                currentLayer.keymapID = (segment.data ()[7] & 0xFF) << 8 | segment.data ()[8] & 0xFF;
                if (currentLayer.keymapID == 0)
                    currentLayer.keymapID = (segment.data ()[11] & 0xFF) << 8 | segment.data ()[12] & 0xFF;
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
     * Add a layer which plays the given keymap on the full key and velocity range. Writes the same
     * segment sequence as KurzFiler: layer, envelope control, amplitude envelope, calibration and
     * the four output blocks.
     *
     * @param keymapID The ID of the keymap object of the layer
     * @param isStereo True if the keymap references stereo samples
     */
    public void addLayer (final int keymapID, final boolean isStereo)
    {
        byte [] data = new byte [15];
        data[1] = 0x18;
        // Low and high key
        data[3] = 0;
        data[4] = 0x7F;
        // Low and high velocity
        data[5] = 0;
        data[6] = 0x7F;
        // Enable flags
        data[8] = (byte) (isStereo ? 0x24 : 0x04);
        this.segments.add (new Segment (TAG_LAYER, data));

        // Envelope control: all zero = user envelope instead of the natural one
        this.segments.add (new Segment (TAG_ENVELOPE_CONTROL, new byte [15]));

        // Amplitude envelope: full sustain
        data = new byte [15];
        data[1] = 100;
        data[7] = 100;
        this.segments.add (new Segment (TAG_ENVELOPE, data));

        // Calibration: references the keymap (twice, as KurzFiler does)
        data = new byte [31];
        data[0] = 0x7F;
        // data[1] is the keymap transpose
        data[3] = 0x2B;
        data[7] = (byte) (keymapID >>> 8 & 0xFF);
        data[8] = (byte) (keymapID & 0xFF);
        data[11] = (byte) (keymapID >>> 8 & 0xFF);
        data[12] = (byte) (keymapID & 0xFF);
        data[29] = 1;
        this.segments.add (new Segment (TAG_CALIBRATION, data));

        // The output blocks
        data = new byte [15];
        data[0] = 62;
        this.segments.add (new Segment (0x50, data));
        data = new byte [15];
        data[0] = 60;
        this.segments.add (new Segment (0x51, data));
        data = new byte [15];
        data[0] = 60;
        this.segments.add (new Segment (0x52, data));
        data = new byte [15];
        data[0] = 1;
        data[2] = 0x70;
        data[13] = 4;
        data[14] = (byte) (isStereo ? 0x90 : 0x00);
        this.segments.add (new Segment (0x53, data));

        // Increase the layer counter in the program block
        for (final Segment segment: this.segments)
            if (segment.tag () == TAG_PROGRAM)
            {
                segment.data ()[1]++;
                break;
            }
    }
}
