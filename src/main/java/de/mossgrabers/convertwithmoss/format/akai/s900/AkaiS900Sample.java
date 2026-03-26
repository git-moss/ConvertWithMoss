// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A S900/S950 sample.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Sample
{
    private final String  name;
    private final long    sampleLength;
    private final int     sampleRate;
    private final int     nominalPitch;
    private final int     loudness;
    private final char    playbackMode;
    private final long    end;
    private final long    start;
    private final long    loopLength;
    private final int     type;
    private final int     direction;

    private final byte [] sampleData;

    private int           compression;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @param compression The compression info
     * @throws IOException Could not read
     */
    public AkaiS900Sample (final InputStream input, final int compression) throws IOException
    {
        this.compression = compression;

        this.name = StreamUtils.readASCII (input, 10).trim ();
        // Padding
        input.skip (6);
        this.sampleLength = StreamUtils.readUnsigned32 (input, false);
        this.sampleRate = StreamUtils.readUnsigned16 (input, false);
        this.nominalPitch = StreamUtils.readUnsigned16 (input, false);
        this.loudness = StreamUtils.readUnsigned16 (input, false);
        this.playbackMode = (char) input.read ();
        input.skip (1);
        this.end = StreamUtils.readUnsigned32 (input, false);
        this.start = StreamUtils.readUnsigned32 (input, false);
        this.loopLength = StreamUtils.readUnsigned32 (input, false);
        input.skip (2);
        this.type = input.read ();
        this.direction = input.read ();
        // Unknown content in 11-13
        input.skip (16);
        this.sampleData = input.readAllBytes ();
    }


    /**
     * Get the name of the entry.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the number of samples.
     * 
     * @return The number of samples
     */
    public long getSampleLength ()
    {
        return this.sampleLength;
    }


    /**
     * Get the sample rate in Hz.
     * 
     * @return The sample rate
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * The tuning is stored as nominal pitch in 1/16 semi-tones. Default: (60*16) = 960 = C3.
     * 
     * @return The nominal pitch
     */
    public int getNominalPitch ()
    {
        return this.nominalPitch;
    }


    /**
     * Get the loudness offset (signed).
     * 
     * @return The loudness value
     */
    public int getLoudness ()
    {
        return this.loudness;
    }


    /**
     * Get the play-back mode / loop-type.
     * 
     * @return 'O' = One-shot, 'L' = Loop, 'A' = AltLoop
     */
    public char getPlaybackMode ()
    {
        return this.playbackMode;
    }


    /**
     * Get the end marker.
     * 
     * @return The end marker
     */
    public long getEnd ()
    {
        return this.end;
    }


    /**
     * Get the start marker.
     * 
     * @return The start marker
     */
    public long getStart ()
    {
        return this.start;
    }


    /**
     * Get the loop length.
     * 
     * @return The loop length
     */
    public long getLoopLength ()
    {
        return this.loopLength;
    }


    /**
     * Get the cross-fade behavior.
     * 
     * @return 0x00 = normal, 0xff = velocity cross-fade
     */
    public int getType ()
    {
        return this.type;
    }


    /**
     * Get the play-back direction.
     * 
     * @return 'N' = normal, 'R' = reversed
     */
    public int getDirection ()
    {
        return this.direction;
    }


    /**
     * Get the sample data.
     * 
     * @return The sample data
     */
    public byte [] getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Get the compression info
     * 
     * @return The compression info
     */
    public int getCompression ()
    {
        return this.compression;
    }
}
