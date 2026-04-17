// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.mirage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A Mirage disk contains 3 Sounds. Each sound has an lower and upper layer of 8 wave-samples each.
 * This class wraps the data 1 layer (lower or upper). It contains one long sample block with the up
 * to 8 concatenated wave-samples. The parameters describe the multi-sample layout. Furthermore,
 * there are 4 programs with different synthesizer settings for this multi-sample.
 * 
 * @author Jürgen Moßgraber
 */
public class MirageLayer
{
    final String                 name;
    final byte []                pcm;
    final List<MirageWaveSample> waveSamples = new ArrayList<> (8);
    final List<MirageProgram>    programs    = new ArrayList<> (4);


    /**
     * Constructor.
     * 
     * @param name The to use for this layer
     * @param soundData The long sample data block of 8-bit samples with the prefixed 1024 byte
     *            parameters
     * @throws IOException Could not parse the parameters
     */
    public MirageLayer (final String name, final byte [] soundData) throws IOException
    {
        this.name = name;

        // Split 1024 byte parameter block and 64kB sample block
        this.pcm = Arrays.copyOfRange (soundData, 1024, soundData.length);
        final byte [] parameterBlock = Arrays.copyOfRange (soundData, 0, 1024);

        try (final ByteArrayInputStream input = new ByteArrayInputStream (parameterBlock))
        {
            @SuppressWarnings("unused")
            int soundRevLevel = input.read ();

            for (int i = 0; i < 8; i++)
                this.waveSamples.add (new MirageWaveSample (input));

            // 8 Segment lists + 1 Spare Segment list of 32 bytes each, not relevant
            input.skipNBytes (288);

            for (int i = 0; i < 4; i++)
                this.programs.add (new MirageProgram (input));
        }
    }


    /**
     * Extract the wave-sample data for the wave-sample at the given index.
     * 
     * @param waveSampleIndex The index from 0-7
     * @return The wave-sample data
     */
    public byte [] getWaveSampleData (final int waveSampleIndex)
    {
        final MirageWaveSample waveParams = this.waveSamples.get (waveSampleIndex);
        final int size = waveParams.sampleEnd - waveParams.sampleStart;
        if (size < 0)
            return new byte [0];
        final byte [] waveSamplePcm = new byte [size];
        System.arraycopy (this.pcm, waveParams.sampleStart, waveSamplePcm, 0, size);
        fixSampleClicks (waveSamplePcm);
        return waveSamplePcm;
    }


    /**
     * The amplitude can go from a level of +127 (0xFF Hex) to a level of -127 (0x01 Hex). The value
     * 0x00 has a special function: it acts as a marker which tells the oscillators where to stop.
     * If the loop is on, 16 zeros are inserted into the wave-sample memory immediately following
     * the loop. Otherwise the 16 zeros are put at the very end of the wave-sample data. If this
     * area is played back it causes a loud click. To fix this all 0x00 will be replaced with 0x80,
     * which represents silence.
     * 
     * @param waveSamplePcm The PCM data to fix
     */
    private static void fixSampleClicks (final byte [] waveSamplePcm)
    {
        for (int s = 0; s < waveSamplePcm.length; s++)
            if (waveSamplePcm[s] == 0)
                waveSamplePcm[s] = (byte) 0x80;
    }
}
