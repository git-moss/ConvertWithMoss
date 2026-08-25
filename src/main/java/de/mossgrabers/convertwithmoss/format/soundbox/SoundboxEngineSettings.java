// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * The sampler engine settings of one of the 4 layers of a Soundbox preset (the AMSamplerEngineMPE
 * parameters), encoded in the 'settings' attribute of a L element. See SOUNDBOX_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxEngineSettings
{
    private static final int   SIZE                = 33;

    /** The voice mode value for monophonic play-back. */
    public static final int    VOICE_MODE_MONO     = 0;
    /** The voice mode value for monophonic play-back with legato. */
    public static final int    VOICE_MODE_LEGATO   = 1;
    /** The voice mode value for polyphonic play-back. */
    public static final int    VOICE_MODE_POLY     = 2;
    /** The octave index which means no octave offset. */
    public static final int    OCTAVE_CENTER       = 2;
    /**
     * The attack time in seconds when the attack knob is at 100%. Measured with plug-in 1.2.1: the
     * knob maps linearly to the time (25% = 1.0s, 50% = 2.0s, 100% = 4.0s) and the ramp itself is
     * linear as well.
     */
    public static final double MAX_ATTACK_SECONDS  = 4.0;
    /**
     * The release time in seconds when the release knob is at 100%. Measured with plug-in 1.2.1:
     * the knob maps linearly to the time until silence (10% = 2s, 25% = 5s), the ramp is slightly
     * convex. The decay knob is assumed to follow the same law.
     */
    public static final double MAX_RELEASE_SECONDS = 20.0;

    /** The voice mode (2 = polyphonic). */
    public int                 voiceMode           = VOICE_MODE_POLY;
    /** The glide amount as the normalized knob position (0..1 = 0-100%). */
    public double              glide               = 0;
    /** The transpose in semi-tones. */
    public double              transposeSemitones  = 0;
    /** The fine tune in cents. */
    public double              fineTuneCents       = 0;
    /** The octave offset as an index with center 2 (= no offset). */
    public int                 octaveIndex         = OCTAVE_CENTER;
    /** The amplitude envelope attack as the normalized knob position (0..1 = 0-100%). */
    public double              attack              = 0;
    /** The amplitude envelope decay as the normalized knob position (0..1, default 20%). */
    public double              decay               = 0.2;
    /** The amplitude envelope sustain level (0..1). */
    public double              sustain             = 1;
    /** The amplitude envelope release as the normalized knob position (0..1). */
    public double              release             = 0;


    /**
     * Parses the engine settings from their binary form.
     *
     * @param data The decoded blob
     * @return The parsed settings
     * @throws IOException If the data is too short
     */
    public static SoundboxEngineSettings parse (final byte [] data) throws IOException
    {
        if (data.length < SIZE)
            throw new IOException ("Engine settings structure too short: " + data.length + " bytes.");

        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
        final SoundboxEngineSettings settings = new SoundboxEngineSettings ();
        settings.voiceMode = buffer.get (0x00);
        settings.glide = buffer.getFloat (0x01);
        settings.transposeSemitones = buffer.getFloat (0x05);
        settings.fineTuneCents = buffer.getFloat (0x09);
        settings.octaveIndex = buffer.getInt (0x0D);
        settings.attack = buffer.getFloat (0x11);
        settings.decay = buffer.getFloat (0x15);
        settings.sustain = buffer.getFloat (0x19);
        settings.release = buffer.getFloat (0x1D);
        return settings;
    }


    /**
     * Writes the engine settings in their binary form.
     *
     * @return The binary form
     */
    public byte [] write ()
    {
        final ByteBuffer buffer = ByteBuffer.allocate (SIZE).order (ByteOrder.LITTLE_ENDIAN);
        buffer.put (0x00, (byte) this.voiceMode);
        buffer.putFloat (0x01, (float) this.glide);
        buffer.putFloat (0x05, (float) this.transposeSemitones);
        buffer.putFloat (0x09, (float) this.fineTuneCents);
        buffer.putInt (0x0D, this.octaveIndex);
        buffer.putFloat (0x11, (float) this.attack);
        buffer.putFloat (0x15, (float) this.decay);
        buffer.putFloat (0x19, (float) this.sustain);
        buffer.putFloat (0x1D, (float) this.release);
        return buffer.array ();
    }
}
