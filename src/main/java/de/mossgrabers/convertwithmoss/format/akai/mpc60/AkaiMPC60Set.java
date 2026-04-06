// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc60;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Reads an Akai MPC60 SET file.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC60Set
{
    private List<AkaiMPC60Pad> pads = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param fileContent The content of the SET file
     * @throws IOException Could not read the file
     */
    public AkaiMPC60Set (final byte [] fileContent) throws IOException
    {
        if (fileContent.length == 0 || fileContent[0] != 2)
            throw new IOException (Functions.getMessage ("IDS_MPC60_UNKNOWN_FILE"));

        // Found mostly 0 in fileContent[1] but 1 occurrence of 1 as well

        // The bytes 3-5 contain the number of sample frames (little-endian). Two 12-bit samples are
        // packed into 3 bytes, which means that the sample size is calculated:
        // size = frames / 2 * 3

        final int padStart = 0x05;
        try (final InputStream input = new ByteArrayInputStream (fileContent, padStart, 32 * 0x3B))
        {
            for (int i = 0; i < 32; i++)
                this.pads.add (readPad (input));
        }

        // The samples contain 1 final padding byte: full-size = size + 1, e.g. with 518.220, this
        // gives 518.220 / 2 * 3 + 1 = 777331 bytes
        // ... but we can also simply read till the end of the file...
        final int sampleStart = 0xC00 - 1;
        try (final InputStream input = new ByteArrayInputStream (fileContent, sampleStart, fileContent.length - sampleStart))
        {
            final short [] sampleData = unpack12bitTo16bit (input);

            // Assign all samples to the pads and prevent duplicates
            final Map<Integer, ISampleData> samples = new HashMap<> ();
            for (final AkaiMPC60Pad pad: this.pads)
            {
                final ISampleData inMemorySampleData = samples.computeIfAbsent (Integer.valueOf (pad.startInFrames), _ -> {

                    int length = pad.lengthInFrames;
                    final short [] padData = new short [length];
                    if (pad.startInFrames < sampleData.length)
                    {
                        final int last = pad.startInFrames + pad.lengthInFrames;
                        if (last > sampleData.length)
                            length = Math.max (0, sampleData.length - pad.startInFrames);
                        System.arraycopy (sampleData, pad.startInFrames, padData, 0, length);
                    }
                    return new InMemorySampleData (new DefaultAudioMetadata (1, 40000, 16, pad.lengthInFrames), padData);

                });

                pad.sampleData = inMemorySampleData;
            }
        }
    }


    /**
     * Get all pads.
     * 
     * @return The pads
     */
    public List<AkaiMPC60Pad> getPads ()
    {
        return this.pads;
    }


    private static AkaiMPC60Pad readPad (final InputStream input) throws IOException
    {
        final AkaiMPC60Pad pad = new AkaiMPC60Pad ();

        pad.name = StreamUtils.readASCII (input, 16);
        input.skipNBytes (1); // 00

        // Found values in the range of [0..33]
        @SuppressWarnings("unused")
        final int padIndex = input.read ();

        pad.startInFrames = StreamUtils.readUnsigned24 (input, false);
        input.skipNBytes (1); // Padding

        pad.lengthInFrames = StreamUtils.readUnsigned24 (input, false);
        input.skipNBytes (1); // Padding

        // END?
        // Value can be outside of sample range (lower than start and larger than end)
        @SuppressWarnings("unused")
        int pos1 = StreamUtils.readUnsigned24 (input, false);
        input.skipNBytes (1); // Padding

        pad.playStartInFrames = StreamUtils.readUnsigned24 (input, false);
        input.skipNBytes (1); // Padding

        // Found 26, 30 but mostly 0 -> also plays?
        @SuppressWarnings("unused")
        final int alsoPlays = input.read ();
        input.skipNBytes (1); // Padding

        // 0..5195 ~500
        @SuppressWarnings("unused")
        final int unknownShort1 = StreamUtils.readUnsigned16 (input, false);

        // Found 1 occurrence of "200" in both values all others are "0"
        @SuppressWarnings("unused")
        int rare1 = input.read ();
        @SuppressWarnings("unused")
        int rare2 = input.read ();

        // 0..4000 ~500 -> always smaller than unknownShort1!
        @SuppressWarnings("unused")
        final int unknownShort2 = StreamUtils.readUnsigned16 (input, false);

        // Could also be 2 bytes! First: 0-255, 2nd: 0-22
        // Short: 0, 2054-5793
        @SuppressWarnings("unused")
        final int unknownShort3 = StreamUtils.readUnsigned16 (input, false);

        // 0, 100, 200, 255 (= 0xFF, most common)
        @SuppressWarnings("unused")
        final int unknownByte1 = input.read ();

        // 0, 100, 127
        @SuppressWarnings("unused")
        final int unknownByte2 = input.read ();

        // 0-254
        @SuppressWarnings("unused")
        final int unknownByte3 = input.read ();

        // 0, 128, 245-255
        @SuppressWarnings("unused")
        final int unknownByte4 = input.read ();

        // 0-255
        pad.decay = input.read ();

        // 0, 1 - Velocity switch?
        @SuppressWarnings("unused")
        final int unknownByte6 = input.read ();

        // 0-254: Could be tuning since it is increasing in meaningful steps for the Bass but that
        // would mean extreme settings for other samples
        @SuppressWarnings("unused")
        final int unknownByte7 = input.read ();

        // 0, 1 - Velocity switch?
        @SuppressWarnings("unused")
        final int unknownByte8 = input.read ();

        // 0, 07-0F
        @SuppressWarnings("unused")
        final int unknownByte9 = input.read ();

        pad.volume = input.read ();
        pad.panning = input.read ();

        // 0-253
        @SuppressWarnings("unused")
        final int unknownByte12 = input.read ();

        // 0-253
        @SuppressWarnings("unused")
        final int unknownByte13 = input.read ();

        // 0-254 -> much rarer numbers than the previous 2
        @SuppressWarnings("unused")
        final int unknownByte14 = input.read ();

        // Values: 0,1,2,3 -> the three available HiHat settings?
        @SuppressWarnings("unused")
        final int exclusive = input.read ();

        return pad;
    }


    /**
     * Converts 3 bytes of 12-bit samples into 4 bytes of 16-bit samples.
     * 
     * @param input The 12-bit sample stream
     * @return The unpacked 16-bit samples
     * @throws IOException Could not read from the stream
     */
    private static short [] unpack12bitTo16bit (final InputStream input) throws IOException
    {
        final int length = input.available ();
        final short [] sampleData = new short [length / 3 * 2 + 3];
        int outIndex = 0;
        while ((input.available ()) >= 3)
        {
            final int b0 = input.read () & 0xFF;
            final int b1 = input.read () & 0xFF;
            final int b2 = input.read () & 0xFF;

            // Extract two 12-bit samples
            sampleData[outIndex++] = swapBytes ((short) (b0 | ((b2 & 0x0F) << 8)));
            sampleData[outIndex++] = swapBytes ((short) (b1 | ((b2 & 0xF0) << 4)));
        }

        return sampleData;
    }


    private static short swapBytes (short value)
    {
        return (short) (((value & 0xFF) << 8) | ((value >> 8) & 0xFF));
    }
}