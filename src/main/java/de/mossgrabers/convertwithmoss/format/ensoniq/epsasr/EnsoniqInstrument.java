// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Represents an Ensoniq EPS / EPS-16+ / ASR instrument.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqInstrument
{
    /** Maximum waveband slots per EPS instrument. */
    public static final int                       MAX_WAVEBANDS = 8;

    private final String                          name;
    private final int                             instrumentID;
    private final int                             keyRangeLow;
    private final int                             keyRangeHigh;
    private final int                             transposition;
    private final int []                          patches       = new int [4];
    private final int                             keyDownLayers;
    private final int                             keyUpLayers;
    private final List<EnsoniqLayer>              layers        = new ArrayList<> ();
    private final Map<Integer, EnsoniqWaveSample> waveSamples   = new HashMap<> ();


    /**
     * Parses an instrument file from the given Ensoniq directory entry.
     *
     * @param file a directory entry of type {@link EnsoniqFile#TYPE_EPS_INST}
     * @throws IOException on read failure
     * @throws IllegalArgumentException if {@code file} is not an instrument
     */
    public EnsoniqInstrument (final EnsoniqFile file) throws IOException
    {
        if (!EnsoniqFile.INSTRUMENT_TYPES.contains (Integer.valueOf (file.getType ())))
            throw new IllegalArgumentException ("Not an instrument file: " + file);

        final byte [] rawData = file.readData ();
        final ByteBuffer buffer = ByteBuffer.wrap (rawData).order (ByteOrder.BIG_ENDIAN);

        @SuppressWarnings("unused")
        final int totalFileSizeBytes = EnsoniqDisk.parseEpsLong (buffer);
        @SuppressWarnings("unused")
        final int hardwareSlotAddress = EnsoniqDisk.parseEpsLong (buffer);

        // Unknown
        buffer.getShort ();

        final int [] layerOffsets = new int [8];
        final int [] waveSampleOffsets = new int [128];

        try (final InputStream input = new ByteArrayInputStream (rawData, buffer.position (), buffer.capacity () - buffer.position ()))
        {
            final String parsed = StreamUtils.readAsciiLoByte (input, 12).trim ();
            this.name = StringUtils.fixASCII (parsed.isBlank () ? file.getName ().trim () : parsed).replace ('*', '-');

            // 0-15
            @SuppressWarnings("unused")
            final int midiOutput = StreamUtils.readSigned8FromWord (input);
            // 1-127
            @SuppressWarnings("unused")
            final int midiProgramNumber = StreamUtils.readSigned8FromWord (input);
            // 0-2 (Off-Key-Channel)
            @SuppressWarnings("unused")
            final int pressureMode = StreamUtils.readSigned8FromWord (input);
            // 0-l0000
            @SuppressWarnings("unused")
            final int totalInstrumentSizeInBlocks = StreamUtils.readUnsigned16 (input, true);
            // LOCAL, MIDI, BOTH
            @SuppressWarnings("unused")
            final int keyDestination = StreamUtils.readSigned8FromWord (input);

            // Bit-map of layers, bit 0=LYR 1, bit l=LYR 2, ...
            for (int i = 0; i < 4; i++)
                this.patches[i] = StreamUtils.readSigned8FromWord (input) & 0xFF;
            this.keyDownLayers = StreamUtils.readSigned8FromWord (input) & 0xFF;
            this.keyUpLayers = StreamUtils.readSigned8FromWord (input) & 0xFF;

            // 0-5 (LIVE, 00, 0*, *0, **, HELD)
            @SuppressWarnings("unused")
            final int currentPatchSelectMode = StreamUtils.readSigned8FromWord (input);

            // Reserved
            input.skipNBytes (2);

            this.instrumentID = StreamUtils.readUnsigned16 (input, true);

            this.keyRangeLow = StreamUtils.readSigned8FromWord (input);
            this.keyRangeHigh = StreamUtils.readSigned8FromWord (input);
            this.transposition = StreamUtils.readSigned8FromWord (input);

            // Pitch Table Offsets
            input.skipNBytes (32);

            final byte [] layerOffsetsData = input.readNBytes (32);
            final ByteBuffer layerOffsetsBuffer = ByteBuffer.wrap (layerOffsetsData).order (ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < 8; i++)
                layerOffsets[i] = readEncoded24BitValue (layerOffsetsBuffer);

            final byte [] waveSampleOffsetsData = input.readNBytes (512);
            final ByteBuffer waveSampleOffsetsBuffer = ByteBuffer.wrap (waveSampleOffsetsData).order (ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < 128; i++)
                waveSampleOffsets[i] = readEncoded24BitValue (waveSampleOffsetsBuffer);
        }

        // Read all layers
        for (final int layerOffset: layerOffsets)
        {
            if (layerOffset == 0)
                break;

            buffer.position (layerOffset);

            final int blockSize = EnsoniqDisk.parseEpsLong (buffer);
            if (blockSize == 0)
                break;

            final byte [] data = new byte [blockSize - 4];
            buffer.get (data);
            this.layers.add (new EnsoniqLayer (data));
        }

        // Read all wave-samples
        for (int i = 0; i < waveSampleOffsets.length; i++)
        {
            if (waveSampleOffsets[i] == 0)
                continue;

            buffer.position (waveSampleOffsets[i]);

            final int blockSize = EnsoniqDisk.parseEpsLong (buffer);
            if (blockSize == 0)
                break;

            final byte [] data = new byte [blockSize - 4];
            buffer.get (data);
            this.waveSamples.put (Integer.valueOf (i), new EnsoniqWaveSample (i, data));
        }
    }


    /**
     * Get the Instrument name from file bytes.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the 4 patches.
     * 
     * @return The patches 8-bit configurations (which layers are enabled)
     */
    public int [] getPatches ()
    {
        return this.patches;
    }


    /**
     * Get the key down layers.
     * 
     * @return The key down layers 8-bit configuration (which layers sound on key-press)
     */
    public int getKeyDownLayers ()
    {
        return this.keyDownLayers;
    }


    /**
     * Get the key up layers.
     * 
     * @return The key up layers 8-bit configuration (which layers sound on key-release)
     */
    public int getKeyUpLayers ()
    {
        return this.keyUpLayers;
    }


    /**
     * Get the instrument ID.
     * 
     * @return The instrument ID
     */
    public int getInstrumentID ()
    {
        return this.instrumentID;
    }


    /**
     * Get the lowest MIDI note of the instrument key-range.
     * 
     * @return The key range low, 0-127
     */
    public int getKeyRangeLow ()
    {
        return this.keyRangeLow;
    }


    /**
     * Get the highest MIDI note of the instrument key-range.
     * 
     * @return The key range high, 0-127
     */
    public int getKeyRangeHigh ()
    {
        return this.keyRangeHigh;
    }


    /**
     * Get the transposition.
     * 
     * @return The number of semi-tones, signed
     */
    public int getTransposition ()
    {
        return this.transposition;
    }


    /**
     * Get the layers.
     *
     * @return The layers
     */
    public List<EnsoniqLayer> getLayers ()
    {
        return this.layers;
    }


    /**
     * Get all wave-samples.
     * 
     * @return The wave samples
     */
    public Map<Integer, EnsoniqWaveSample> getWaveSamples ()
    {
        return this.waveSamples;
    }


    private static int readEncoded24BitValue (final ByteBuffer buffer)
    {
        int b0 = buffer.get () & 0xFF;
        buffer.get ();
        int b2 = buffer.get () & 0xFF;
        int b3 = buffer.get () & 0xFF;

        int high = (b3 >> 4) & 0x0F; // upper 4 bits
        int low = b3 & 0x0F; // lower 4 bits

        return (high << 20) // front 4 bits
                | (b0 << 12) | (b2 << 4) | low; // last 4 bits
    }
}