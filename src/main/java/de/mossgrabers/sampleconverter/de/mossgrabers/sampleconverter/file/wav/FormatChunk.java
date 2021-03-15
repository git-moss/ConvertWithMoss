// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.wav;

import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RiffID;


/**
 * Accessor for a format chunk ("fmt ") in a WAV file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class FormatChunk extends WavChunk
{
    private static final int CHUNK_SIZE = 16;


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public FormatChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.FMT_ID, chunk, CHUNK_SIZE);
    }


    /**
     * The first word of format data specifies the type of compression used on the Wave data
     * included in the Wave chunk found in this "RIFF" chunk. The following is a list of the common
     * compression codes used today.
     *
     * @return 0 (0x0000) Unknown, 1 (0x0001) PCM/uncompressed, 2 (0x0002) Microsoft ADPCM, 6
     *         (0x0006) ITU G.711 a-law, 7 (0x0007) ITU G.711 Âμ-law, 17 (0x0011) IMA ADPCM, 20
     *         (0x0016) ITU G.723 ADPCM (Yamaha), 49 (0x0031) GSM 6.10, 64 (0x0040) ITU G.721 ADPCM,
     *         80 (0x0050) MPEG, 65,536 (0xFFFF) Experimental
     */
    public int getCompressionCode ()
    {
        return this.twoBytesAsInt (0x00);
    }


    /**
     * The number of channels specifies how many separate audio signals that are encoded in the wave
     * data chunk. A value of 1 means a mono signal, a value of 2 means a stereo signal, etc.
     *
     * @return The four bytes converted to an integer
     */
    public int getNumberOfChannels ()
    {
        return this.twoBytesAsInt (0x02);
    }


    /**
     * Set the number of channels.
     *
     * @param channels The number of channels
     */
    public void setNumberOfChannels (final int channels)
    {
        this.intAsTwoBytes (0x02, channels);
    }


    /**
     * The number of sample slices per second. This value is unaffected by the number of channels.
     *
     * @return The four bytes converted to an integer
     */
    public int getSampleRate ()
    {
        return this.fourBytesAsInt (0x04);
    }


    /**
     * This value indicates how many bytes of wave data must be streamed to a D/A converter per
     * second in order to play the wave file. This information is useful when determining if data
     * can be streamed from the source fast enough to keep up with playback. This value can be
     * easily calculated with the formula: AvgBytesPerSec = SampleRate * BlockAlign * Channels
     *
     * @return The four bytes converted to an integer
     */
    public int getAverageBytesPerSecond ()
    {
        return this.fourBytesAsInt (0x08);
    }


    /**
     * Set the average bytes per second.
     *
     * @param averageBytesPerSecond The average bytes per second
     */
    public void setAverageBytesPerSecond (final int averageBytesPerSecond)
    {
        this.intAsFourBytes (0x08, averageBytesPerSecond);
    }


    /**
     * The number of bytes per sample slice. This value is not affected by the number of channels
     * and can be calculated with the formula: BlockAlign = SignificantBitsPerSample / 8 *
     * NumChannels
     *
     * @return The four bytes converted to an integer
     */
    public int getBlockAlign ()
    {
        return this.twoBytesAsInt (0x0C);
    }


    /**
     * This value specifies the number of bits used to define each sample. This value is usually 8,
     * 16, 24 or 32. If the number of bits is not byte aligned (a multiple of 8) then the number of
     * bytes used per sample is rounded up to the nearest byte size and the unused bytes are set to
     * 0 and ignored.
     *
     * @return The four bytes converted to an integer
     */
    public int getSignicantBitsPerSample ()
    {
        return this.twoBytesAsInt (0x0E);
    }


    /**
     * Format all values as a string for dumping it out.
     *
     * @return The formatted string
     */
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Compression Code: ").append (this.getCompressionCode ()).append ('\n');
        sb.append ("Number of Channels: ").append (this.getNumberOfChannels ()).append ('\n');
        sb.append ("Sample Rate: ").append (this.getSampleRate ()).append ('\n');
        sb.append ("Average bytes per second: ").append (this.getAverageBytesPerSecond ()).append ('\n');
        sb.append ("Block align: ").append (this.getBlockAlign ()).append ('\n');
        sb.append ("Significant bits per sample: ").append (this.getSignicantBitsPerSample ()).append ('\n');
        return sb.toString ();
    }
}
