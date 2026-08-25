// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.casio;

import java.io.IOException;


/**
 * A bank of a Casio FZ-1/FZ-10M/FZ-20M: 656 bytes of parameters which distribute up to 64 voices
 * (areas) across key ranges, velocity ranges and MIDI channels. All multi-byte values are stored
 * little-endian.
 *
 * @author Jürgen Moßgraber
 */
public class CasioFZBank
{
    /** The size of the bank parameters in bytes. */
    public static final int SIZE         = 656;

    /** The maximum number of areas of a bank. */
    public static final int MAX_AREAS    = 64;

    int                     numberOfAreas;
    final int []            highKey      = new int [MAX_AREAS];
    final int []            lowKey       = new int [MAX_AREAS];
    final int []            highVelocity = new int [MAX_AREAS];
    final int []            lowVelocity  = new int [MAX_AREAS];
    final int []            centerKey    = new int [MAX_AREAS];
    final int []            midiChannel  = new int [MAX_AREAS];
    final int []            generators   = new int [MAX_AREAS];
    final int []            volume       = new int [MAX_AREAS];
    final int []            voicePointer = new int [MAX_AREAS];
    String                  name         = "";


    /**
     * Read the bank parameters.
     *
     * @param data The data to read from
     * @param offset The offset of the first byte of the bank
     * @throws IOException The data is malformed
     */
    public void read (final byte [] data, final int offset) throws IOException
    {
        if (offset + SIZE > data.length)
            throw new IOException ("Bank data is too short.");

        this.numberOfAreas = CasioFZVoice.readUnsigned16 (data, offset);
        for (int i = 0; i < MAX_AREAS; i++)
        {
            this.highKey[i] = data[offset + 0x02 + i] & 0xFF;
            this.lowKey[i] = data[offset + 0x42 + i] & 0xFF;
            this.highVelocity[i] = data[offset + 0x82 + i] & 0xFF;
            this.lowVelocity[i] = data[offset + 0xC2 + i] & 0xFF;
            this.centerKey[i] = data[offset + 0x102 + i] & 0xFF;
            this.midiChannel[i] = data[offset + 0x142 + i] & 0xFF;
            this.generators[i] = data[offset + 0x182 + i] & 0xFF;
            this.volume[i] = data[offset + 0x1C2 + i] & 0xFF;
            this.voicePointer[i] = CasioFZVoice.readUnsigned16 (data, offset + 0x202 + i * 2);
        }
        this.name = CasioFZVoice.readName (data, offset + 0x282);
    }


    /**
     * Write the bank parameters.
     *
     * @param data The data to write to
     * @param offset The offset of the first byte of the bank
     */
    public void write (final byte [] data, final int offset)
    {
        CasioFZVoice.writeUnsigned16 (data, offset, this.numberOfAreas);
        for (int i = 0; i < MAX_AREAS; i++)
        {
            data[offset + 0x02 + i] = (byte) this.highKey[i];
            data[offset + 0x42 + i] = (byte) this.lowKey[i];
            data[offset + 0x82 + i] = (byte) this.highVelocity[i];
            data[offset + 0xC2 + i] = (byte) this.lowVelocity[i];
            data[offset + 0x102 + i] = (byte) this.centerKey[i];
            data[offset + 0x142 + i] = (byte) this.midiChannel[i];
            data[offset + 0x182 + i] = (byte) this.generators[i];
            data[offset + 0x1C2 + i] = (byte) this.volume[i];
            CasioFZVoice.writeUnsigned16 (data, offset + 0x202 + i * 2, this.voicePointer[i]);
        }
        CasioFZVoice.writeName (data, offset + 0x282, this.name);
    }
}
