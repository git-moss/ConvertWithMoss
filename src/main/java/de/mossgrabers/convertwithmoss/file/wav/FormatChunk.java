// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Wrapper of a format chunk ("fmt ") in a WAV file.
 *
 * @author Jürgen Moßgraber
 */
public class FormatChunk extends RIFFChunk
{
    private static final int                  CHUNK_SIZE                 = 16;
    private static final int                  CHUNK_SIZE_EX              = 20;

    /** Unknown data format. */
    public static final int                   WAVE_FORMAT_UNKNOWN        = 0x0000;
    /** PCM data format. */
    public static final int                   WAVE_FORMAT_PCM            = 0x0001;
    /** ADPCM data format. */
    public static final int                   WAVE_FORMAT_ADPCM          = 0x0002;
    /** IEEE float 32 bit data format. */
    public static final int                   WAVE_FORMAT_IEEE_FLOAT     = 0x0003;
    /** ALAW data format. */
    public static final int                   WAVE_FORMAT_ALAW           = 0x0006;
    /** MULAW data format. */
    public static final int                   WAVE_FORMAT_MULAW          = 0x0007;
    /** OKI ADPCM data format. */
    public static final int                   WAVE_FORMAT_OKI_ADPCM      = 0x0010;
    /** IMA ADPCM data format. */
    public static final int                   WAVE_FORMAT_DVI_ADPCM      = 0x0011;
    /** DIGI data format. */
    public static final int                   WAVE_FORMAT_DIGISTD        = 0x0015;
    /** ITU G.723 ADPCM (Yamaha) data format. */
    public static final int                   WAVE_FORMAT_DIGIFIX        = 0x0016;
    /** Microsoft GSM 6.10 data format. */
    public static final int                   WAVE_FORMAT_GSM_6_10       = 0x0031;
    /** ITU G.721 ADPCM data format. */
    public static final int                   WAVE_FORMAT_ITU_G721_ADPCM = 0x0040;
    /** MPEG data format. */
    public static final int                   WAVE_FORMAT_MPEG           = 0x0050;
    /** IBM FORMAT MULAW data format. */
    public static final int                   WAVE_IBM_FORMAT_MULAW      = 0x0101;
    /** IBM FORMAT ALAW data format. */
    public static final int                   WAVE_IBM_FORMAT_ALAW       = 0x0102;
    /** IBM FORMAT ADPCM data format. */
    public static final int                   WAVE_IBM_FORMAT_ADPCM      = 0x0103;
    /** SX7383 data format. */
    public static final int                   WAVE_FORMAT_SX7383         = 0x1C07;
    /** Extensible data format. */
    public static final int                   WAVE_FORMAT_EXTENSIBLE     = 0xFFFE;
    /** Experimental data format. */
    public static final int                   WAVE_FORMAT_EXPERIMENTAL   = 0xFFFF;

    private static final Map<Integer, String> COMPRESSION_NAMES          = new HashMap<> ();
    static
    {
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_UNKNOWN), "Unknown");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_PCM), "PCM/uncompressed");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_ADPCM), "Microsoft ADPCM");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_IEEE_FLOAT), "IEEE Float");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_ALAW), "ITU G.711 a-law");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_MULAW), "ITU G.711 µ-law");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_OKI_ADPCM), "OKI ADPCM");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_DVI_ADPCM), "IMA ADPCM");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_DIGISTD), "DIGISTD");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_DIGIFIX), "ITU G.723 ADPCM (Yamaha)");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_GSM_6_10), "Microsoft GSM 6.10");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_ITU_G721_ADPCM), "ITU G.721 ADPCM");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_MPEG), "MPEG");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_IBM_FORMAT_MULAW), "IBM FORMAT MULAW");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_IBM_FORMAT_ALAW), "IBM FORMAT ALAW");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_IBM_FORMAT_ADPCM), "IBM FORMAT ADPCM");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_SX7383), "SX7383");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_EXTENSIBLE), "Extensible");
        COMPRESSION_NAMES.put (Integer.valueOf (WAVE_FORMAT_EXPERIMENTAL), "Experimental");
    }


    /**
     * Constructor. Creates an empty format chunk and initializes it as PCM format.
     *
     * @param numberOfChannels The number of channels of the sample
     * @param sampleRate The sample rate
     * @param bitsPerSample The resolution the sample in bits
     * @param addFormatEx If true, 4 additional empty bytes will be written
     */
    public FormatChunk (final int numberOfChannels, final int sampleRate, final int bitsPerSample, final boolean addFormatEx)
    {
        super (RiffID.FMT_ID, new byte [addFormatEx ? CHUNK_SIZE_EX : CHUNK_SIZE], addFormatEx ? CHUNK_SIZE_EX : CHUNK_SIZE);

        this.setCompressionCode (WAVE_FORMAT_PCM);
        this.setNumberOfChannels (numberOfChannels);
        this.setSampleRate (sampleRate);
        this.setSignificantBitsPerSample (bitsPerSample);
    }


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public FormatChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.FMT_ID, chunk.getData (), chunk.getData ().length);
    }


    /**
     * Get the name if a compression format.
     *
     * @param key The ID of the format
     * @return The name
     */
    public static String getCompression (final int key)
    {
        final String compressionName = COMPRESSION_NAMES.get (Integer.valueOf (key));
        return compressionName == null ? "Unknown " + Integer.toHexString (key) : compressionName;
    }


    /**
     * The first word of format data specifies the type of compression used on the Wave data
     * included in the Wave chunk found in this "RIFF" chunk.
     *
     * @return See the WAVE_FORMAT_* constants in this class
     */
    public int getCompressionCode ()
    {
        return this.getTwoBytesAsInt (0x00);
    }


    /**
     * The first word of format data specifies the type of compression used on the Wave data
     * included in the Wave chunk found in this "RIFF" chunk.
     *
     * @param compressionCode Use the WAVE_FORMAT_* constants in this class
     */
    public void setCompressionCode (final int compressionCode)
    {
        this.setIntAsTwoBytes (0x00, compressionCode);
    }


    /**
     * The number of channels specifies how many separate audio signals that are encoded in the wave
     * data chunk. A value of 1 means a mono signal, a value of 2 means a stereo signal, etc.
     *
     * @return The four bytes converted to an integer
     */
    public int getNumberOfChannels ()
    {
        return this.getTwoBytesAsInt (0x02);
    }


    /**
     * Set the number of channels.
     *
     * @param channels The number of channels
     */
    public void setNumberOfChannels (final int channels)
    {
        this.setIntAsTwoBytes (0x02, channels);

        this.updateBlockAlign ();
    }


    /**
     * The number of sample slices per second. This value is unaffected by the number of channels.
     *
     * @return The four bytes converted to an integer
     */
    public int getSampleRate ()
    {
        return this.getFourBytesAsInt (0x04);
    }


    /**
     * Set the number of sample slices per second. This value is unaffected by the number of
     * channels.
     *
     * @param sampleRate The four bytes converted to an integer
     */
    public void setSampleRate (final int sampleRate)
    {
        this.setIntAsFourBytes (0x04, sampleRate);

        this.updateAverageBytesPerSecond ();
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
        return this.getFourBytesAsInt (0x08);
    }


    /**
     * Update the average bytes per second, which is: sampleRate * blockAlign * numberOfChannels
     */
    private void updateAverageBytesPerSecond ()
    {
        final int sampleRate = this.getSampleRate ();
        final int blockAlign = this.getBlockAlign ();
        final int averageBytesPerSecond = sampleRate * blockAlign;
        this.setIntAsFourBytes (0x08, averageBytesPerSecond);
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
        return this.getTwoBytesAsInt (0x0C);
    }


    /**
     * Update the number of bytes per sample slice. This value is not affected by the number of
     * channels and can be calculated with the formula: BlockAlign = SignificantBitsPerSample / 8 *
     * NumChannels
     */
    private void updateBlockAlign ()
    {
        final int bitsPerSample = this.getSignificantBitsPerSample ();
        final int numberOfChannels = this.getNumberOfChannels ();
        final int blockAlign = bitsPerSample / 8 * numberOfChannels;
        this.setIntAsTwoBytes (0x0C, blockAlign);

        this.updateAverageBytesPerSecond ();
    }


    /**
     * This is an extension of the format WAVE_FORMAT_PCM.<br/>
     * This value specifies the number of bits used to define each sample. This value is usually 8,
     * 16, 24 or 32. If the number of bits is not byte aligned (a multiple of 8) then the number of
     * bytes used per sample is rounded up to the nearest byte size and the unused bytes are set to
     * 0 and ignored.
     *
     * @return The four bytes converted to an integer
     */
    public int getSignificantBitsPerSample ()
    {
        if (0x0E < this.getData ().length)
            return this.getTwoBytesAsInt (0x0E);
        return 0;
    }


    /**
     * This is an extension of the format WAVE_FORMAT_PCM.<br/>
     * This value specifies the number of bits used to define each sample. This value is usually 8,
     * 16, 24 or 32. If the number of bits is not byte aligned (a multiple of 8) then the number of
     * bytes used per sample is rounded up to the nearest byte size and the unused bytes are set to
     * 0 and ignored.
     *
     * @param bitsPerSample The bits per sample
     */
    public void setSignificantBitsPerSample (final int bitsPerSample)
    {
        this.setIntAsTwoBytes (0x0E, bitsPerSample);

        this.updateBlockAlign ();
    }


    /**
     * Calculates the length of the data in samples.
     *
     * @param data The data
     * @return The length of the sample in samples (frames) of 1 channel
     */
    public int calculateLength (final byte [] data)
    {
        return data.length / this.calculateBytesPerSample ();
    }


    /**
     * Calculates the data size.
     *
     * @param lengthInSamples The length of the sample (number of samples)
     * @return The size of the data block
     */
    public int calculateDataSize (final int lengthInSamples)
    {
        return lengthInSamples * this.calculateBytesPerSample ();
    }


    /**
     * Calculate the number of bytes which are used for one sample depending on the significant bite
     * per sample and the number of channels.
     *
     * @return The number of bytes
     */
    public int calculateBytesPerSample ()
    {
        return this.getNumberOfChannels () * this.getSignificantBitsPerSample () / 8;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Compression Code: ").append (FormatChunk.getCompression (this.getCompressionCode ())).append ('\n');
        sb.append ("Number of Channels: ").append (this.getNumberOfChannels ()).append ('\n');
        sb.append ("Sample Rate: ").append (this.getSampleRate ()).append ('\n');
        sb.append ("Average bytes per second: ").append (this.getAverageBytesPerSecond ()).append ('\n');
        sb.append ("Block align: ").append (this.getBlockAlign ()).append ('\n');
        sb.append ("Significant bits per sample: ").append (this.getSignificantBitsPerSample ()).append ('\n');
        sb.append ("Extra bytes: ").append (this.getSize () - CHUNK_SIZE);
        return sb.toString ();
    }
}
