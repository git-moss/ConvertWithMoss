// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Wrapper of an instrument chunk ("inst") in a WAV file.
 *
 * @author Jürgen Moßgraber
 */
public class InstrumentChunk extends AbstractSpecificRIFFChunk
{
    private static final int CHUNK_SIZE = 7;


    /**
     * Constructor. Creates an empty instrument chunk.
     */
    public InstrumentChunk ()
    {
        super (RiffID.INST_ID, CHUNK_SIZE);
    }


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException The raw chunk is not of the specific type or the length of data does
     *             not match the expected chunk size
     */
    public InstrumentChunk (final RawRIFFChunk chunk) throws ParseException
    {
        super (RiffID.INST_ID, chunk);
    }


    /**
     * The MIDI note number that corresponds to the un-shifted pitch of the sample. Valid values
     * range from 0 to 127.
     *
     * @return The MIDI note number
     */
    public int getUnshiftedNote ()
    {
        return this.rawRiffChunk.getByteAsUnsignedInt (0x00);
    }


    /**
     * Sets the MIDI note number that corresponds to the un-shifted pitch of the sample. Valid
     * values range from 0 to 127.
     *
     * @param unshiftedNote The MIDI note number
     */
    public void setUnshiftedNote (final int unshiftedNote)
    {
        this.rawRiffChunk.setUnsignedIntAsByte (0x00, unshiftedNote);
    }


    /**
     * The pitch shift adjustment in cents (100ths of a semi-tone) needed to hit UnshiftedNote value
     * exactly. chFineTune can be used to compensate for tuning errors in the sampling process.
     * Valid values .
     *
     * @return The adjustment value in cents in the range from -50 to 50
     */
    public int getFineTune ()
    {
        return this.rawRiffChunk.getByteAsSignedInt (0x01);
    }


    /**
     * Set the pitch shift adjustment in cents (100ths of a semi-tone) needed to hit UnshiftedNote
     * value exactly. Fine tune can be used to compensate for tuning errors in the sampling process.
     * Valid values range from -50 to 50.
     *
     * @param fineTune The adjustment value in cents in the range from -50 to 50
     */
    public void setFineTune (final int fineTune)
    {
        this.rawRiffChunk.setSignedIntAsByte (0x01, fineTune);
    }


    /**
     * The suggested volume setting for the sample in deci-bels. A value of zero deci-bels suggests
     * no change in the volume. A value of -6 deci-bels suggests reducing the amplitude of the
     * sample by two.
     *
     * @return The gain
     */
    public int getGain ()
    {
        return this.rawRiffChunk.getByteAsSignedInt (0x02);
    }


    /**
     * Set the volume setting for the sample in deci-bels. A value of zero deci-bels suggests no
     * change in the volume. A value of -6 deci-bels suggests reducing the amplitude of the sample
     * by two.
     *
     * @param gain The gain
     */
    public void setGain (final int gain)
    {
        this.rawRiffChunk.setSignedIntAsByte (0x02, gain);
    }


    /**
     * The low MIDI note number range of the sample. Valid values range from 0 to 127.
     *
     * @return The low range MIDI note number
     */
    public int getLowNote ()
    {
        return this.rawRiffChunk.getByteAsUnsignedInt (0x03);
    }


    /**
     * Sets the low MIDI note number range of the sample.
     *
     * @param lowNote The MIDI note number
     */
    public void setLowNote (final int lowNote)
    {
        this.rawRiffChunk.setUnsignedIntAsByte (0x03, lowNote);
    }


    /**
     * The high MIDI note number range of the sample. Valid values range from 0 to 127.
     *
     * @return The high range MIDI note number
     */
    public int getHighNote ()
    {
        return this.rawRiffChunk.getByteAsUnsignedInt (0x04);
    }


    /**
     * Sets the high MIDI note number range of the sample.
     *
     * @param highNote The MIDI note number
     */
    public void setHighNote (final int highNote)
    {
        this.rawRiffChunk.setUnsignedIntAsByte (0x04, highNote);
    }


    /**
     * The low MIDI velocity range of the sample. Valid values range from 0 to 127.
     *
     * @return The low range MIDI note number
     */
    public int getLowVelocity ()
    {
        return this.rawRiffChunk.getByteAsUnsignedInt (0x05);
    }


    /**
     * Sets the low velocity range of the sample.
     *
     * @param lowVelocity The velocity number
     */
    public void setLowVelocity (final int lowVelocity)
    {
        this.rawRiffChunk.setUnsignedIntAsByte (0x05, lowVelocity);
    }


    /**
     * The high MIDI velocity range of the sample. Valid values range from 0 to 127.
     *
     * @return The high range MIDI note number
     */
    public int getHighVelocity ()
    {
        return this.rawRiffChunk.getByteAsUnsignedInt (0x06);
    }


    /**
     * Sets the high velocity range of the sample.
     *
     * @param highVelocity The velocity number
     */
    public void setHighVelocity (final int highVelocity)
    {
        this.rawRiffChunk.setUnsignedIntAsByte (0x06, highVelocity);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Unshifted Note: ").append (this.getUnshiftedNote ()).append ('\n');
        sb.append ("Fine Tune: ").append (this.getFineTune ()).append ('\n');
        sb.append ("Gain: ").append (this.getGain ()).append ('\n');
        sb.append ("Low Note: ").append (this.getLowNote ()).append ('\n');
        sb.append ("High Note: ").append (this.getHighNote ()).append ('\n');
        sb.append ("Low Velocity: ").append (this.getLowVelocity ()).append ('\n');
        sb.append ("High Velocity: ").append (this.getHighVelocity ());
        return sb.toString ();
    }
}
