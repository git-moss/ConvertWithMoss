// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * One mapped sample ('sound') of a Soundbox group. This is the structure encoded in the JUCE Base64
 * text of a S element. The structure has 75 bytes, plug-in versions from 1.0.8 on append another 12
 * bytes (stretch parameters) which are ignored. See SOUNDBOX_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxSound
{
    private static final int SIZE          = 75;

    /** The lowest MIDI key of the mapping, inclusive. */
    public int               keyLow        = 0;
    /** The MIDI root note. */
    public int               keyRoot       = 60;
    /** The first MIDI key above the mapping (exclusive upper bound). */
    public int               keyHighExcl   = 128;
    /** True if the loop is active. */
    public boolean           loopActive    = false;
    /** True if the loop is alternating ('ping-pong'). */
    public boolean           pingPong      = false;
    /** The start of the play-back region as a fraction (0..1) of the sample length. */
    public double            sampleStart   = 0;
    /** The end of the play-back region as a fraction (0..1) of the sample length. */
    public double            sampleEnd     = 1;
    /** The loop start as a fraction (0..1) of the sample length. */
    public double            loopStart     = 0;
    /** The loop end as a fraction (0..1) of the sample length. */
    public double            loopEnd       = 1;
    /** The fade-in as a fraction (0..1) of the sample length. */
    public double            fadeIn        = 0;
    /** The fade-out as a fraction (0..1) of the sample length. */
    public double            fadeOut       = 0;
    /** The loop cross-fade (0..0.5), relative to the loop length. */
    public double            loopCrossfade = 0;
    /** True if the sample is played reversed. */
    public boolean           reverse       = false;
    /** The panning (-1..1). */
    public double            panning       = 0;
    /** The volume in percent (0..100). */
    public int               volumePercent = 100;
    /** The coarse tune in semi-tones. */
    public int               tuneSemitones = 0;
    /** The lowest velocity of the mapping, inclusive. */
    public int               velocityLow   = 0;
    /** The highest velocity of the mapping, inclusive. */
    public int               velocityHigh  = 127;


    /**
     * Parses the sound structure from its binary form.
     *
     * @param data The decoded blob, 75 or more bytes
     * @return The parsed sound
     * @throws IOException If the data is too short
     */
    public static SoundboxSound parse (final byte [] data) throws IOException
    {
        if (data.length < SIZE)
            throw new IOException ("Sound structure too short: " + data.length + " bytes.");

        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
        final SoundboxSound sound = new SoundboxSound ();
        // 0x00: a copy of the mapping low key, always identical to the value at 0x26
        sound.keyRoot = buffer.getInt (0x04);
        sound.loopActive = buffer.get (0x08) != 0;
        sound.pingPong = buffer.get (0x09) != 0;
        sound.sampleStart = buffer.getFloat (0x0A);
        sound.sampleEnd = buffer.getFloat (0x0E);
        sound.loopStart = buffer.getFloat (0x12);
        sound.loopEnd = buffer.getFloat (0x16);
        sound.fadeIn = buffer.getFloat (0x1A);
        sound.fadeOut = buffer.getFloat (0x1E);
        sound.loopCrossfade = buffer.getFloat (0x22);
        sound.keyLow = buffer.getInt (0x26);
        sound.keyHighExcl = buffer.getInt (0x2A);
        sound.reverse = buffer.get (0x2E) != 0;
        sound.panning = buffer.getFloat (0x2F);
        // 0x33: play-back speed (always 1.0), 0x3B: unknown (always 0)
        sound.volumePercent = buffer.getInt (0x37);
        sound.tuneSemitones = buffer.getInt (0x3F);
        sound.velocityLow = buffer.getInt (0x43);
        sound.velocityHigh = buffer.getInt (0x47);
        return sound;
    }


    /**
     * Writes the sound structure in its 75 byte binary form.
     *
     * @return The binary form
     */
    public byte [] write ()
    {
        final ByteBuffer buffer = ByteBuffer.allocate (SIZE).order (ByteOrder.LITTLE_ENDIAN);
        buffer.putInt (0x00, this.keyLow);
        buffer.putInt (0x04, this.keyRoot);
        buffer.put (0x08, (byte) (this.loopActive ? 1 : 0));
        buffer.put (0x09, (byte) (this.pingPong ? 1 : 0));
        buffer.putFloat (0x0A, (float) this.sampleStart);
        buffer.putFloat (0x0E, (float) this.sampleEnd);
        buffer.putFloat (0x12, (float) this.loopStart);
        buffer.putFloat (0x16, (float) this.loopEnd);
        buffer.putFloat (0x1A, (float) this.fadeIn);
        buffer.putFloat (0x1E, (float) this.fadeOut);
        buffer.putFloat (0x22, (float) this.loopCrossfade);
        buffer.putInt (0x26, this.keyLow);
        buffer.putInt (0x2A, this.keyHighExcl);
        buffer.put (0x2E, (byte) (this.reverse ? 1 : 0));
        buffer.putFloat (0x2F, (float) this.panning);
        buffer.putFloat (0x33, 1.0f);
        buffer.putInt (0x37, this.volumePercent);
        buffer.putInt (0x3B, 0);
        buffer.putInt (0x3F, this.tuneSemitones);
        buffer.putInt (0x43, this.velocityLow);
        buffer.putInt (0x47, this.velocityHigh);
        return buffer.array ();
    }
}
