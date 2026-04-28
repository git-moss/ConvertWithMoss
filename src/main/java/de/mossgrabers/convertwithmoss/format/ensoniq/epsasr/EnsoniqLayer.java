// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A layer in a Ensoniq EPS / EPS-16+ / ASR instrument file.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqLayer
{
    private final int    index;
    private final String name;
    private final int    stereoLink;
    private final int    velocityLow;
    private final int    velocityHigh;
    private final int [] waveSamples = new int [88];


    /**
     * Constructor.
     *
     * @param data The data of the layer
     * @throws IOException Could not read the layer
     */
    public EnsoniqLayer (final byte [] data) throws IOException
    {
        final ByteArrayInputStream input = new ByteArrayInputStream (data);

        // Not used
        input.skipNBytes (2);
        this.index = StreamUtils.readSigned8FromWord (input);
        input.skipNBytes (2);

        this.name = StreamUtils.readAsciiLoByte (input, 12);

        @SuppressWarnings("unused")
        final int glideMode = input.read ();
        @SuppressWarnings("unused")
        final int delayModulationByVelocity = input.read ();
        @SuppressWarnings("unused")
        final int glideTime = input.read ();
        @SuppressWarnings("unused")
        final int restrikeDecayTime = input.read ();
        @SuppressWarnings("unused")
        final int legatoLayerNumber = input.read ();

        this.stereoLink = input.read ();
        this.velocityLow = StreamUtils.readUnsigned8FromWord (input);
        this.velocityHigh = StreamUtils.readUnsigned8FromWord (input);

        @SuppressWarnings("unused")
        final int pitchTable = StreamUtils.readUnsigned8FromWord (input);
        @SuppressWarnings("unused")
        final int delayTime = StreamUtils.readUnsigned8FromWord (input);

        for (int i = 0; i < 88; i++)
            this.waveSamples[i] = StreamUtils.readUnsigned8FromWord (input);
    }


    /**
     * Get the index of the layer. 1-based.
     * 
     * @return The index
     */
    public int getIndex ()
    {
        return this.index;
    }


    /**
     * Get the name of the layer.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Check if stereo linking should be applied.
     * 
     * @return Is stereo link enabled?
     */
    public boolean isStereoLink ()
    {
        return this.stereoLink > 0;
    }


    /**
     * Get the lowest velocity value of the layer.
     * 
     * @return The velocity low, 0-127
     */
    public int getVelocityLow ()
    {
        return this.velocityLow;
    }


    /**
     * Get the highest velocity value of the layer.
     * 
     * @return The velocity high, 0-127
     */
    public int getVelocityHigh ()
    {
        return this.velocityHigh;
    }


    /**
     * Get the wave sample references.
     * 
     * @return The 88 wave sample indices, 1-based
     */
    public int [] getWaveSamples ()
    {
        return this.waveSamples;
    }
}