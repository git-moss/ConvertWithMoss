// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A Kontakt 5+ multi-configuration.
 *
 * @author Jürgen Moßgraber
 */
public class MultiConfiguration
{
    private static final int      NUM_INSTRUMENTS  = 64;

    private List<MultiInstrument> multiInstruments = new ArrayList<> ();


    /**
     * Constructor.
     */
    public MultiConfiguration ()
    {
        // Intentionally empty
    }


    /**
     * Get the multi-instruments.
     *
     * @return The multi-instruments
     */
    public List<MultiInstrument> getMultiInstruments ()
    {
        return this.multiInstruments;
    }


    /**
     * Parse the multi-configuration data.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void parse (final KontaktPresetChunk chunk) throws IOException
    {
        final byte [] data = chunk.getPublicData ();
        if (chunk.getId () != KontaktPresetChunkID.MULTI_CONFIGURATION || data.length != NUM_INSTRUMENTS * 24)
            throw new IOException ("Not a proper multi-configuration chunk!");

        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        for (int i = 0; i < NUM_INSTRUMENTS; i++)
        {
            final MultiInstrument multiInstrument = new MultiInstrument ();
            multiInstrument.parse (in);
            this.multiInstruments.add (multiInstrument);
        }
    }
}