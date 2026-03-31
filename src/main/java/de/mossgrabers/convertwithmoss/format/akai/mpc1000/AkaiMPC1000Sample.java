// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A MPC1000 sample.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC1000Sample
{
    private final String name;
    private final int    level;
    private final int    velocityRangeLower;
    private final int    velocityRangeUpper;
    private final int    tuning;
    private final int    playMode;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiMPC1000Sample (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readASCII (input, 16).trim ();

        // Padding
        input.skipNBytes (1);

        this.level = input.read ();
        this.velocityRangeLower = input.read ();
        this.velocityRangeUpper = input.read ();
        this.tuning = StreamUtils.readSigned16 (input, false);
        this.playMode = input.read ();

        // Padding
        input.skipNBytes (1);
    }


    /**
     * Get the name without the file extension of assigned sample file. ASCII, right-padded to 16
     * bytes with 0x00. Only space, alphanumeric characters, and !#$%&'()-@_{} are valid.
     * 
     * @return The name, trimmed
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the level.
     * 
     * @return The level in the range of [0..100]
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Get the lower velocity value.
     * 
     * @return The lower velocity value, in the range of [0..velocityRangeUpper]
     */
    public int getVelocityRangeLower ()
    {
        return this.velocityRangeLower;
    }


    /**
     * Get the upper velocity value.
     * 
     * @return The upper velocity value, in the range of [velocityRangeLower..127]
     */
    public int getVelocityRangeUpper ()
    {
        return this.velocityRangeUpper;
    }


    /**
     * Get the tuning.
     * 
     * @return Tuning in cents (1 semitone = 100 cents) in the range of [-3600..3600]
     */
    public int getTuning ()
    {
        return this.tuning;
    }


    /**
     * Get the play mode.
     * 
     * @return 0=One Shot, 1=Note On (samples are played as long as the pad is pressed)
     */
    public int getPlayMode ()
    {
        return this.playMode;
    }
}
