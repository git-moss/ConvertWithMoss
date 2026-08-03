// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * The CAF Instrument chunk. Describes how to use the audio data as the sample of a MIDI
 * instrument.
 *
 * @author Jürgen Moßgraber
 */
public class CafInstrumentChunk
{
    /** The MIDI note number and fractional pitch of the base note. 60 represents middle C. */
    float baseNote;
    /** The lowest note of the key range, 0 to 127. */
    int   lowNote;
    /** The highest note of the key range, 0 to 127. */
    int   highNote;
    /** The lowest MIDI velocity for playing the region, 0 to 127. */
    int   lowVelocity;
    /** The highest MIDI velocity for playing the region, 0 to 127. */
    int   highVelocity;
    /** Gain adjustment in dB. */
    float gain;
    /** The ID of the region to use while the key is pressed. */
    long  startRegionID;
    /** The ID of the region to loop while the note is sustained. */
    long  sustainRegionID;
    /** The ID of the region to play after the key is released. */
    long  releaseRegionID;
    /** The ID of the instrument. */
    long  instrumentID;


    /**
     * Read the chunk data.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    public void read (final InputStream in) throws IOException
    {
        this.baseNote = StreamUtils.readFloat (in, true);
        this.lowNote = StreamUtils.readUnsigned8 (in);
        this.highNote = StreamUtils.readUnsigned8 (in);
        this.lowVelocity = StreamUtils.readUnsigned8 (in);
        this.highVelocity = StreamUtils.readUnsigned8 (in);
        this.gain = StreamUtils.readFloat (in, true);
        this.startRegionID = StreamUtils.readUnsigned32 (in, true);
        this.sustainRegionID = StreamUtils.readUnsigned32 (in, true);
        this.releaseRegionID = StreamUtils.readUnsigned32 (in, true);
        this.instrumentID = StreamUtils.readUnsigned32 (in, true);
    }


    /**
     * Write the chunk data.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, Float.floatToIntBits (this.baseNote) & 0xFFFFFFFFL, true);
        out.write (this.lowNote);
        out.write (this.highNote);
        out.write (this.lowVelocity);
        out.write (this.highVelocity);
        StreamUtils.writeUnsigned32 (out, Float.floatToIntBits (this.gain) & 0xFFFFFFFFL, true);
        StreamUtils.writeUnsigned32 (out, this.startRegionID, true);
        StreamUtils.writeUnsigned32 (out, this.sustainRegionID, true);
        StreamUtils.writeUnsigned32 (out, this.releaseRegionID, true);
        StreamUtils.writeUnsigned32 (out, this.instrumentID, true);
    }


    /**
     * Get the base note.
     *
     * @return The MIDI note number and fractional pitch of the base note
     */
    public float getBaseNote ()
    {
        return this.baseNote;
    }


    /**
     * Set the base note.
     *
     * @param baseNote The MIDI note number and fractional pitch of the base note
     */
    public void setBaseNote (final float baseNote)
    {
        this.baseNote = baseNote;
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
     * Set the low key range note.
     *
     * @param lowNote The low note
     */
    public void setLowNote (final int lowNote)
    {
        this.lowNote = lowNote;
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
     * Set the high key range note.
     *
     * @param highNote The high note
     */
    public void setHighNote (final int highNote)
    {
        this.highNote = highNote;
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
     * Set the low velocity range.
     *
     * @param lowVelocity The low velocity
     */
    public void setLowVelocity (final int lowVelocity)
    {
        this.lowVelocity = lowVelocity;
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
     * Set the high velocity range.
     *
     * @param highVelocity The high velocity
     */
    public void setHighVelocity (final int highVelocity)
    {
        this.highVelocity = highVelocity;
    }


    /**
     * Get the gain.
     *
     * @return Value in dB
     */
    public float getGain ()
    {
        return this.gain;
    }


    /**
     * Set the gain.
     *
     * @param gain Value in dB
     */
    public void setGain (final float gain)
    {
        this.gain = gain;
    }


    /**
     * Get the ID of the region to use while the key is pressed.
     *
     * @return The region ID
     */
    public long getStartRegionID ()
    {
        return this.startRegionID;
    }


    /**
     * Set the ID of the region to use while the key is pressed.
     *
     * @param startRegionID The region ID
     */
    public void setStartRegionID (final long startRegionID)
    {
        this.startRegionID = startRegionID;
    }


    /**
     * Get the ID of the region to loop while the note is sustained.
     *
     * @return The region ID
     */
    public long getSustainRegionID ()
    {
        return this.sustainRegionID;
    }


    /**
     * Set the ID of the region to loop while the note is sustained.
     *
     * @param sustainRegionID The region ID
     */
    public void setSustainRegionID (final long sustainRegionID)
    {
        this.sustainRegionID = sustainRegionID;
    }


    /**
     * Get the ID of the region to play after the key is released.
     *
     * @return The region ID
     */
    public long getReleaseRegionID ()
    {
        return this.releaseRegionID;
    }


    /**
     * Set the ID of the region to play after the key is released.
     *
     * @param releaseRegionID The region ID
     */
    public void setReleaseRegionID (final long releaseRegionID)
    {
        this.releaseRegionID = releaseRegionID;
    }


    /**
     * Get the ID of the instrument.
     *
     * @return The instrument ID
     */
    public long getInstrumentID ()
    {
        return this.instrumentID;
    }


    /**
     * Set the ID of the instrument.
     *
     * @param instrumentID The instrument ID
     */
    public void setInstrumentID (final long instrumentID)
    {
        this.instrumentID = instrumentID;
    }
}
