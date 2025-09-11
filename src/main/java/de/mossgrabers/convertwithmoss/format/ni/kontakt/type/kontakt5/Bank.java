// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

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
    private int [] midiChannels;


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
     * Get the MIDI channels for the bank presets.
     * 
     * @return The MIDI channels
     */
    public int [] getMidiChannels ()
    {
        return this.midiChannels;
    }


    /**
     * Parse the bank data.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void parse (final KontaktPresetChunk chunk) throws IOException
    {
        if (chunk.getId () != KontaktPresetChunkID.BANK)
            throw new IOException ("Not a bank chunk!");

        this.readPublicData (chunk.getPublicData ());
        this.readPrivateData (chunk.getPrivateData ());
    }


    /**
     * Parses the public Bank data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readPublicData (final byte [] data) throws IOException
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


    /**
     * Parses the private Bank data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readPrivateData (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        // Seem to be always 00 00 01
        in.readNBytes (3);

        if (in.available () == 0)
            return;

        final int numMidiChannels = (int) StreamUtils.readUnsigned32 (in, false);
        this.midiChannels = new int [numMidiChannels];
        for (int i = 0; i < numMidiChannels; i++)
            this.midiChannels[i] = StreamUtils.readUnsigned16 (in, false);

        // More unknown data, could be mute, solo and sends like in MultiConfiguration
    }
}