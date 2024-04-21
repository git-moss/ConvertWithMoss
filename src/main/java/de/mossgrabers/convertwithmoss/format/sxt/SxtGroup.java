// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a SXT group.
 *
 * @author Jürgen Moßgraber
 */
class SxtGroup
{
    /** MinValue = 1, MaxValue = 99, Default = 8. */
    int keyPolyphony = 8;

    /** Legato = 46, Retrigger = 47, Default = 47. */
    int keyMode      = 47;

    /** MinValue = 0, MaxValue = 1, Default = 0 */
    int groupMono    = 0;

    /** MinValue = 0, MaxValue = 127, Default = 0 */
    int portamento   = 0;

    /** MinValue = -9700, MaxValue = 4500, Default = -536 */
    int lfo1Freq     = -536;


    /**
     * Default constructor.
     */
    public SxtGroup ()
    {
        // Intentionally empty
    }


    /**
     * Read all group parameters.
     *
     * @param in The input stream from which to read
     * @param version The version of the group chunk
     * @throws IOException Could not read the parameters
     */
    public void read (final InputStream in, final int version) throws IOException
    {
        this.keyPolyphony = in.read ();
        this.keyMode = in.read ();
        if (version >= SxtChunkConstants.VERSION_3_0_0)
            this.groupMono = in.read ();
        this.portamento = in.read ();
        this.lfo1Freq = StreamUtils.readSigned32 (in, true);
    }


    /**
     * Write all group parameters.
     *
     * @param out The output stream from which to read
     * @param version The version of the group chunk
     * @throws IOException Could not read the parameters
     */
    public void write (final OutputStream out, final int version) throws IOException
    {
        out.write (this.keyPolyphony);
        out.write (this.keyMode);
        if (version >= SxtChunkConstants.VERSION_3_0_0)
            out.write (this.groupMono);
        out.write (this.portamento);
        StreamUtils.writeSigned32 (out, this.lfo1Freq, true);
    }
}
