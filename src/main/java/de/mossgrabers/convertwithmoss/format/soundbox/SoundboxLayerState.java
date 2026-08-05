// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * The state of one of the 4 layers of a Soundbox preset (the AMLayer parameters), encoded in the
 * 'state' attribute of a L element. See SOUNDBOX_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxLayerState
{
    private static final int SIZE           = 11;

    /** The default layer volume (unity gain position). */
    public static final double DEFAULT_VOLUME = 0.75;

    /** True if the layer is active. */
    public boolean active  = false;
    /** True if the layer is soloed. */
    public boolean solo    = false;
    /** True if the layer is linked. */
    public boolean link    = false;
    /** The panning (-1..1). */
    public double  panning = 0;
    /** The volume (0..1, default 0.75). */
    public double  volume  = DEFAULT_VOLUME;


    /**
     * Parses the layer state from its binary form.
     *
     * @param data The decoded blob
     * @return The parsed state
     * @throws IOException If the data is too short
     */
    public static SoundboxLayerState parse (final byte [] data) throws IOException
    {
        if (data.length < SIZE)
            throw new IOException ("Layer state structure too short: " + data.length + " bytes.");

        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
        final SoundboxLayerState state = new SoundboxLayerState ();
        state.active = buffer.get (0x00) != 0;
        state.solo = buffer.get (0x01) != 0;
        state.link = buffer.get (0x02) != 0;
        state.panning = buffer.getFloat (0x03);
        state.volume = buffer.getFloat (0x07);
        return state;
    }


    /**
     * Writes the layer state in its binary form.
     *
     * @return The binary form
     */
    public byte [] write ()
    {
        final ByteBuffer buffer = ByteBuffer.allocate (SIZE).order (ByteOrder.LITTLE_ENDIAN);
        buffer.put (0x00, (byte) (this.active ? 1 : 0));
        buffer.put (0x01, (byte) (this.solo ? 1 : 0));
        buffer.put (0x02, (byte) (this.link ? 1 : 0));
        buffer.putFloat (0x03, (float) this.panning);
        buffer.putFloat (0x07, (float) this.volume);
        return buffer.array ();
    }
}
