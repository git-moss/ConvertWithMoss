// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;


/**
 * An AIFF Instrument Chunk as defined in the AIFF specification.
 *
 * @author Jürgen Moßgraber
 */
public class AiffInstrumentChunk extends AiffChunk
{
    int      baseNote;
    /** Units are in cents (1/100 of a semi-tone) and range from -50 to +50. */
    int      detune;
    int      lowNote;
    int      highNote;
    int      lowVelocity;
    int      highVelocity;
    /** Gain adjustment in dB. */
    int      gain;
    AiffLoop sustainLoop = new AiffLoop ();
    AiffLoop releaseLoop = new AiffLoop ();


    /**
     * Constructor.
     *
     * @param chunk The IFF chunk
     */
    protected AiffInstrumentChunk (final IffChunk chunk)
    {
        super (chunk);
    }


    /**
     * Get the base note.
     *
     * @return The base note
     */
    public int getBaseNote ()
    {
        return this.baseNote;
    }


    /**
     * Get the de-tuning.
     *
     * @return Value in the range of -50 to 50 cents
     */
    public int getDetune ()
    {
        return this.detune;
    }


    /**
     * Get the low key range note.
     *
     * @return The low note
     */
    public int getLowNote ()
    {
        return this.lowNote;
    }


    /**
     * Get the high key range note.
     *
     * @return The high note
     */
    public int getHighNote ()
    {
        return this.highNote;
    }


    /**
     * Get the low velocity range.
     *
     * @return The low velocity
     */
    public int getLowVelocity ()
    {
        return this.lowVelocity;
    }


    /**
     * Get the high velocity range.
     *
     * @return The high velocity
     */
    public int getHighVelocity ()
    {
        return this.highVelocity;
    }


    /**
     * Get the gain.
     *
     * @return Value in dB
     */
    public int getGain ()
    {
        return this.gain;
    }


    /**
     * Get the loop in the sustain.
     *
     * @return The loop
     */
    public AiffLoop getSustainLoop ()
    {
        return this.sustainLoop;
    }


    /**
     * Get the loop in the release.
     *
     * @return The loop
     */
    public AiffLoop getReleaseLoop ()
    {
        return this.releaseLoop;
    }


    /**
     * Read the AIFF Common chunk data.
     *
     * @param chunk The chunk to read from
     * @throws IOException Could not read the data
     */
    public void read (final IffChunk chunk) throws IOException
    {
        try (final InputStream in = chunk.streamData ())
        {
            this.baseNote = in.read ();
            this.detune = in.read ();
            this.lowNote = in.read ();
            this.highNote = in.read ();
            this.lowVelocity = in.read ();
            this.highVelocity = in.read ();
            this.gain = StreamUtils.readSigned16 (in, true);
            this.sustainLoop.read (in);
            this.releaseLoop.read (in);
        }
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Base Note: ").append (this.baseNote).append ('\n');
        sb.append ("Detune: ").append (this.detune).append (" Cents\n");
        sb.append ("Low Note: ").append (this.lowNote).append ('\n');
        sb.append ("High Note: ").append (this.highNote).append ('\n');
        sb.append ("Low Velocity: ").append (this.lowVelocity).append ('\n');
        sb.append ("High Velocity: ").append (this.highVelocity).append ('\n');
        sb.append ("Gain: ").append (this.gain).append (" dB\n");
        sb.append ("Sustain Loop: Play Mode: ").append (this.sustainLoop.playMode).append (" Marker Start: ").append (this.sustainLoop.beginLoopMarkerID).append (" Marker End: ").append (this.sustainLoop.endLoopMarkerID).append ('\n');
        sb.append ("Release Loop: ").append (this.releaseLoop.playMode);
        return sb.toString ();
    }
}
