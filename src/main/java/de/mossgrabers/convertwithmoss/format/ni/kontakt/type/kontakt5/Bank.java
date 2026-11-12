// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
    private float  masterVolume = 1.0f;
    private float  masterTune   = 1.0f;


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
     * Get the master volume of the bank.
     *
     * @return The master volume as a linear factor, 1.0 = 0dB
     */
    public float getMasterVolume ()
    {
        return this.masterVolume;
    }


    /**
     * Get the master tuning of the bank.
     *
     * @return The master tuning as a frequency ratio, 1.0 = no tuning
     */
    public float getMasterTune ()
    {
        return this.masterTune;
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

        this.masterVolume = StreamUtils.readFloatLE (in);
        this.masterTune = StreamUtils.readFloatLE (in);
        // The master tempo, always 0 in the analyzed files
        StreamUtils.readUnsigned32 (in, false);

        this.name = StreamUtils.readUtf16WithLength (in);
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

        // More unknown data: in Kontakt 4.2 the 64 MIDI channels are followed by 128 bytes (most
        // likely the mute and solo states) and the aux send levels of the slots
    }
}
