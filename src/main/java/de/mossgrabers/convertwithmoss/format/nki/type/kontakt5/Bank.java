// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kontakt 5+ bank.
 *
 * @author Jürgen Moßgraber
 */
public class Bank
{
    private String name;


    /**
     * Constructor.
     */
    public Bank ()
    {
        // Intentionally empty
    }


    /**
     * Get the name of the bank.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Parse the bank data.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void parse (final PresetChunk chunk) throws IOException
    {
        if (chunk.getId () != PresetChunkID.BANK)
            throw new IOException ("Not a bank chunk!");

        this.readMasterData (chunk.getPublicData ());
    }


    /**
     * Parses the Bank data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readMasterData (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        // The master volume
        StreamUtils.readFloatLE (in);
        // The master tune
        StreamUtils.readFloatLE (in);
        // The master tempo
        StreamUtils.readUnsigned32 (in, false);

        this.name = StreamUtils.readWithLengthUTF16 (in);
    }
}