// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Additional instrument data in a Kontakt 5+ multi-configuration.
 *
 * @author Jürgen Moßgraber
 */
public class MultiInstrument
{
    private int     midiChannel;
    private boolean isMuted  = false;
    private boolean isSoloed = false;


    /**
     * Constructor.
     *
     * @param midiChannel The MIDI channel
     */
    public MultiInstrument (final int midiChannel)
    {
        this.midiChannel = midiChannel;
    }


    /**
     * Get the MIDI channel.
     *
     * @return The MIDI channel from 0 to 16 where 0 = All
     */
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Is the instrument muted?
     *
     * @return True if muted
     */
    public boolean isMuted ()
    {
        return this.isMuted;
    }


    /**
     * Is the instrument soloed?
     *
     * @return True if soloed
     */
    public boolean isSoloed ()
    {
        return this.isSoloed;
    }


    /**
     * Parse the multi-instrument data.
     *
     * @param in Where to read the data from
     * @throws IOException Could not read the data
     */
    public void parse (final InputStream in) throws IOException
    {
        this.midiChannel = StreamUtils.readUnsigned16 (in, false);

        this.isMuted = in.read () > 0;
        this.isSoloed = in.read () > 0;
        // The number of aux sends, the levels are not used
        final int numSends = (int) StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numSends; i++)
            StreamUtils.readFloatLE (in);
    }
}