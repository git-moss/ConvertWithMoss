// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RiffID;


/**
 * Accessor for an instrument chunk ("inst") in a WAV file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class InstrumentChunk extends WavChunk
{
    private static final int CHUNK_SIZE = 7;


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public InstrumentChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.INST_ID, chunk, CHUNK_SIZE);
    }


    /**
     * The MIDI note number that corresponds to the unshifted pitch of the sample. Valid values
     * range from 0 to 127.
     *
     * @return The MIDI note number
     */
    public int getUnshiftedNote ()
    {
        return this.chunk.byteAsUnsignedInt (0x00);
    }


    /**
     * The pitch shift adjustment in cents (or 100ths of a semitone) needed to hit UnshiftedNote
     * value exactly. chFineTune can be used to compensate for tuning errors in the sampling
     * process. Valid values range from -50 to 50.
     *
     * @return The MIDI note number
     */
    public int getFineTune ()
    {
        return this.chunk.byteAsSignedInt (0x01);
    }


    /**
     * The suggested volume setting for the sample in decibels. A value of zero decibels suggests no
     * change in the volume. A value of -6 decibels suggests reducing the amplitude of the sample by
     * two.
     *
     * @return The gain
     */
    public int getGain ()
    {
        return this.chunk.byteAsSignedInt (0x02);
    }


    /**
     * The suggested usable MIDI note number range of the sample. Valid values range from 0 to 127.
     *
     * @return The low range MIDI note number
     */
    public int getLowNote ()
    {
        return this.chunk.byteAsUnsignedInt (0x03);
    }


    /**
     * The suggested usable MIDI note number range of the sample. Valid values range from 0 to 127.
     *
     * @return The high range MIDI note number
     */
    public int getHighNote ()
    {
        return this.chunk.byteAsUnsignedInt (0x04);
    }


    /**
     * The suggested usable MIDI velocity range of the sample. Valid values range from 0 to 127.
     *
     * @return The low range MIDI note number
     */
    public int getLowVelocity ()
    {
        return this.chunk.byteAsUnsignedInt (0x05);
    }


    /**
     * The suggested usable MIDI velocity range of the sample. Valid values range from 0 to 127.
     *
     * @return The high range MIDI note number
     */
    public int getHighVelocity ()
    {
        return this.chunk.byteAsUnsignedInt (0x06);
    }


    /**
     * Format all values as a string for dumping it out.
     *
     * @return The formatted string
     */
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Unshifted Note: ").append (this.getUnshiftedNote ()).append ('\n');
        sb.append ("Fine Tune: ").append (this.getFineTune ()).append ('\n');
        sb.append ("Gain: ").append (this.getGain ()).append ('\n');
        sb.append ("Low Note: ").append (this.getLowNote ()).append ('\n');
        sb.append ("High Note: ").append (this.getHighNote ()).append ('\n');
        sb.append ("Low Velocity: ").append (this.getLowVelocity ()).append ('\n');
        sb.append ("High Velocity: ").append (this.getHighVelocity ()).append ('\n');
        return sb.toString ();
    }
}
