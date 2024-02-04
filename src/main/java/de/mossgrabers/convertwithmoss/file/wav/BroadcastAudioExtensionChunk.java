// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;

import java.util.Arrays;


/**
 * Accessor for an broadcast audio extension chunk ("bext") in a WAV file.
 *
 * @author Jürgen Moßgraber
 */
public class BroadcastAudioExtensionChunk extends WavChunk
{
    // Not fully correct; last field has variable size (note counted in this value)
    private static final int CHUNK_SIZE = 602;


    /**
     * Constructor. Creates an empty broadcast audio extension chunk.
     */
    public BroadcastAudioExtensionChunk ()
    {
        super (RiffID.BEXT_ID, new RIFFChunk (0, RiffID.BEXT_ID.getId (), CHUNK_SIZE));

        this.chunk.setData (new byte [CHUNK_SIZE]);
    }


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public BroadcastAudioExtensionChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.BEXT_ID, chunk, CHUNK_SIZE);
    }


    /**
     * ASCII string (maximum 256 characters) containing a free description of the sequence. To help
     * applications which display only a short description, it is recommended that a resume of the
     * description is contained in the first 64 characters and the last 192 characters are used for
     * details. If the length of the string is less than 256 characters the last one shall be
     * followed by a null character (00).
     *
     * @return The description
     */
    public String getDescription ()
    {
        return this.chunk.getNullTerminatedString (0, 256, "");
    }


    /**
     * ASCII string (maximum 32 characters) containing the name of the originator/ producer of the
     * audio file. If the length of the string is less than 32 characters the field shall be ended
     * by a null character.
     *
     * @return The originator
     */
    public String getOriginator ()
    {
        return this.chunk.getNullTerminatedString (256, 32, "");
    }


    /**
     * ASCII string (maximum 32 characters) containing the name of the originator/ producer of the
     * audio file. If the length of the string is less than 32 characters the field shall be ended
     * by a null character.
     *
     * @return The originator
     */
    public String getOriginatorReference ()
    {
        return this.chunk.getNullTerminatedString (288, 32, "");
    }


    /**
     * ASCII characters containing the date of creation of the audio sequence. The format shall be «
     * ‘,year’,-,’month,’-‘,day,’» with 4 characters for the year and 2 characters per other item.
     * Year is defined from 0000 to 9999 Month is defined from 1 to 12 Day is defined from 1 to 28,
     * 29, 30 or 31 The separator between the items can be anything but it is recommended that one
     * of the following characters be used: ‘-’ hyphen ‘_’ underscore ‘:’ colon ‘ ’ space ‘.’ stop.
     *
     * @return The origination date
     */
    public String getOriginationDate ()
    {
        return this.chunk.getNullTerminatedString (320, 10, "");
    }


    /**
     * 8 ASCII characters containing the time of creation of the audio sequence. The format shall be
     * « ‘hour’-‘minute’-‘second’» with 2 characters per item. Hour is defined from 0 to 23. Minute
     * and second are defined from 0 to 59. The separator between the items can be anything but it
     * is recommended that one of the following characters be used: ‘-’ hyphen ‘_’ underscore ‘:’
     * colon ‘ ’ space ‘.’ stop
     *
     * @return The origination time
     */
    public String getOriginationTime ()
    {
        return this.chunk.getNullTerminatedString (330, 8, "");
    }


    /**
     * These fields shall contain the time-code of the sequence. It is a 64-bit value which contains
     * the first sample count since midnight. The number of samples per second depends on the sample
     * frequency which is defined in the field 'nSamplesPerSec' from the format chunk.
     *
     * @return The time reference
     */
    public int getTimeReference ()
    {
        return (this.chunk.fourBytesAsInt (342) << 32) + (this.chunk.fourBytesAsInt (338));
    }


    /**
     * An unsigned binary number giving the version of the BWF. This number is particularly relevant
     * for the carriage of the UMID and loudness information. For Version 1 it shall be set to 0001h
     * and for Version 2 it shall be set to 0002h.
     *
     * @return The version
     */
    public int getVersion ()
    {
        return this.chunk.twoBytesAsInt (346);
    }


    /**
     * 64 bytes containing a UMID (Unique Material Identifier) to standard SMPTE 330M [1]. If only a
     * 32 byte "basic UMID" is used, the last 32 bytes should be set to zero. (The length of the
     * UMID is given internally.)
     *
     * @return The UMID
     */
    public byte [] getUMID ()
    {
        final byte [] umid = new byte [64];
        System.arraycopy (this.chunk.getData (), 348, umid, 0, 64);
        return umid;
    }


    /**
     * A 16-bit signed integer, equal to round (100x the Integrated Loudness Value of the file in
     * LUFS).
     *
     * @return The loudness value
     */
    public int getLoudnessValue ()
    {
        return this.chunk.twoBytesAsInt (412);
    }


    /**
     * A 16-bit signed integer, equal to round (100x the Loudness Range of the file in LU).
     *
     * @return The loudness range
     */
    public int getLoudnessRange ()
    {
        return this.chunk.twoBytesAsInt (414);
    }


    /**
     * A 16-bit signed integer, equal to round (100x the Maximum True Peak Value of the file in
     * dBTP).
     *
     * @return The maximum true peak level
     */
    public int getMaxTruePeakLevel ()
    {
        return this.chunk.twoBytesAsInt (416);
    }


    /**
     * A 16-bit signed integer, equal to round (100x the highest value of the Momentary Loudness
     * Level of the file in LUFS).
     *
     * @return The maximum momentary loudness
     */
    public int getMaxMomentaryLoudness ()
    {
        return this.chunk.twoBytesAsInt (418);
    }


    /**
     * A 16-bit signed integer, equal to round (100x the highest value of the Short-term Loudness
     * Level of the file in LUFS).
     *
     * @return The maximum short term loudness
     */
    public int getMaxShortTermLoudness ()
    {
        return this.chunk.twoBytesAsInt (420);
    }


    /**
     * Unrestricted ASCII characters containing a collection of strings terminated by CR/LF. Each
     * string shall contain a description of a coding process applied to the audio data. Each new
     * coding application shall add a new string with the appropriate information.
     *
     * @return The coding history
     */
    public String getCodingHistory ()
    {
        final byte [] data = this.chunk.getData ();
        final int offset = 422 + 180;
        return new String (data, offset, data.length - offset);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();

        final String description = this.getDescription ();
        if (!description.isBlank ())
            sb.append ("");

        sb.append ("Description: ").append (this.getDescription ()).append ('\n');
        sb.append ("Originator: ").append (this.getOriginator ()).append ('\n');
        sb.append ("Originator Reference: ").append (this.getOriginatorReference ()).append ('\n');
        sb.append ("Origination Date: ").append (this.getOriginationDate ()).append ('\n');
        sb.append ("Origination Time: ").append (this.getOriginationTime ()).append ('\n');
        sb.append ("Time Reference: ").append (this.getTimeReference ()).append ('\n');
        sb.append ("Version: ").append (this.getVersion ()).append ('\n');
        sb.append ("Unique Material Identifier (UMID): ").append (Arrays.toString (this.getUMID ())).append ('\n');
        sb.append ("Loudness Value: ").append (this.getLoudnessValue ()).append ('\n');
        sb.append ("Loudness Range: ").append (this.getLoudnessRange ()).append ('\n');
        sb.append ("Max True Peak Level: ").append (this.getMaxTruePeakLevel ()).append ('\n');
        sb.append ("Max Short Term Loudness: ").append (this.getMaxShortTermLoudness ()).append ('\n');
        sb.append ("Coding History: ").append (this.getCodingHistory ());
        return sb.toString ();
    }
}
