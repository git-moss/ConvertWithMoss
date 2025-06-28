// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

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
    private int midiChannel;


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
     * Parse the multi-instrument data.
     *
     * @param in Where to read the data from
     * @throws IOException Could not read the data
     */
    public void parse (final InputStream in) throws IOException
    {
        this.midiChannel = StreamUtils.readUnsigned16 (in, false);

        // Is muted?
        in.read ();
        // Is soloed?
        in.read ();
        // The number of sends
        final int numSends = (int) StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numSends; i++)
            StreamUtils.readFloatLE (in);
    }
}