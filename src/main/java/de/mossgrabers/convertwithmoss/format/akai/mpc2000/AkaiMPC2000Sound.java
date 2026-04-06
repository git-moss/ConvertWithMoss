// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc2000;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import de.mossgrabers.tools.ui.Functions;


/**
 * Reader class for Akai MPC2000 SND (sample) format files. The SND format consists of a 42-byte
 * header followed by raw sample data. The header contains metadata about the sample including name,
 * tuning, loop points, and play-back parameters. Sample data is stored as signed 16-bit PCM audio,
 * little-endian.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC2000Sound
{
    /** Size of the SND file header in bytes. */
    private static final int HEADER_SIZE   = 42;

    /** Loop mode: No looping. */
    public static final int  LOOP_MODE_OFF = 0;

    /** Loop mode: Loop enabled. */
    public static final int  LOOP_MODE_ON  = 1;

    private String           name;
    private int              pad;
    private int              level;
    private int              tune;
    private int              channels;
    private long             start;
    private long             loopEnd;
    private long             end;
    private long             loopLength;
    private int              loopMode;
    private int              beatsInLoop;
    private int              sampleRate;

    // Sample data
    private short []         sampleData;


    /**
     * Creates an SND reader from a file.
     *
     *
     * @param file The SND file
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000Sound (final File file) throws IOException
    {
        this (Files.readAllBytes (file.toPath ()));
    }


    /**
     * Creates an SND reader from a byte array.
     *
     *
     * @param data The raw SND file data
     * @throws IOException Could not read the file
     */
    public AkaiMPC2000Sound (final byte [] data) throws IOException
    {
        if (data.length < HEADER_SIZE)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_INVALID_SND_FILE", "too small (minimum " + HEADER_SIZE + " bytes)"));

        this.parseHeader (data);
        this.parseSampleData (data);
    }


    /**
     * Constructor.
     * 
     * @param inputStream The input stream from which to read the file
     * @throws IOException Could not read the SND file
     */
    public AkaiMPC2000Sound (final InputStream inputStream) throws IOException
    {
        this (inputStream.readAllBytes ());
    }


    /**
     * Parse the SND file header.
     *
     * @param data The raw file data
     * @throws IOException Could not read the file
     */
    private void parseHeader (final byte [] data) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (data, 0, HEADER_SIZE);
        buffer.order (ByteOrder.LITTLE_ENDIAN);

        // Check bytes (2 bytes)
        final int checkByte1 = buffer.get () & 0xFF;
        if (checkByte1 != 0x01)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_INVALID_SND_FILE", "not a MPC2000/MPC3000 SND file"));
        final int checkByte2 = buffer.get () & 0xFF;
        final boolean isMPC3000 = checkByte2 == 0x02;
        if (!isMPC3000 && checkByte2 != 0x04)
            throw new IOException (Functions.getMessage ("IDS_MPC2000_INVALID_SND_FILE", "not a MPC2000/MPC3000 SND file"));

        // Sample name (16 bytes)
        byte [] nameBytes = new byte [16];
        buffer.get (nameBytes);
        this.name = new String (nameBytes).trim ();

        // Parameters (4 bytes)
        this.pad = buffer.get () & 0xFF;
        this.level = buffer.get () & 0xFF;
        this.tune = buffer.get () & 0xFF;
        // 0=Mono 1=Stereo
        this.channels = (buffer.get () & 0xFF) + 1;

        // Position markers (16 bytes)
        this.start = buffer.getInt () & 0xFFFFFFFFL; // unsigned 32-bit
        this.loopEnd = buffer.getInt () & 0xFFFFFFFFL;
        this.end = buffer.getInt () & 0xFFFFFFFFL;
        if (isMPC3000)
        {
            // Unknown
            @SuppressWarnings("unused")
            final long unknown = buffer.getInt () & 0xFFFFFFFFL;
            this.loopMode = buffer.get () & 0xFF;
            this.beatsInLoop = buffer.get () & 0xFF;
        }
        else
        {
            this.loopLength = buffer.getInt () & 0xFFFFFFFFL;
            // Loop parameters (4 bytes)
            this.loopMode = buffer.get () & 0xFF;
            this.beatsInLoop = buffer.get () & 0xFF;
            this.sampleRate = buffer.getShort () & 0xFFFF; // unsigned 16-bit
        }
    }


    /**
     * Parses the sample data following the header.
     *
     * @param data The raw file data
     */
    private void parseSampleData (final byte [] data)
    {
        if (data.length <= HEADER_SIZE)
        {
            this.sampleData = new short [0];
            return;
        }

        final ByteBuffer buffer = ByteBuffer.wrap (data, HEADER_SIZE, data.length - HEADER_SIZE);
        buffer.order (ByteOrder.LITTLE_ENDIAN);

        // 16-bit samples
        final int numSamples = (data.length - HEADER_SIZE) / 2;
        this.sampleData = new short [numSamples];
        for (int i = 0; i < numSamples; i++)
            this.sampleData[i] = buffer.getShort ();
    }


    /**
     * Gets the sample name (max 16 characters).
     *
     * @return The sample name, trimmed of whitespace and null terminators
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Gets the assigned pad.
     *
     * @return The pad index
     */
    public int getPad ()
    {
        return this.pad;
    }


    /**
     * Gets the play-back level/volume.
     *
     * @return The level value, 0...200 (default 100)
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Gets the tuning adjustment in semi-tones. Typically centered at 60 (middle C). Values below
     * 60 lower the pitch, values above 60 raise it.
     *
     * @return The tune value, -120...+120
     */
    public int getTune ()
    {
        return this.tune;
    }


    /**
     * Gets the number of audio channels.
     *
     * @return The number of channels (1 = mono, 2 = stereo)
     */
    public int getChannels ()
    {
        return this.channels;
    }


    /**
     * Checks if the sample is mono.
     *
     * @return True if mono (1 channel), false otherwise
     */
    public boolean isMono ()
    {
        return this.channels == 1;
    }


    /**
     * Checks if the sample is stereo.
     *
     * @return True if stereo (2 channels), false otherwise
     */
    public boolean isStereo ()
    {
        return this.channels == 2;
    }


    /**
     * Gets the start position in samples.
     *
     * @return the start position
     */
    public long getStart ()
    {
        return this.start;
    }


    /**
     * Gets the loop end position in samples.
     *
     * @return The loop end position
     */
    public long getLoopEnd ()
    {
        return this.loopEnd;
    }


    /**
     * Gets the end position in samples.
     *
     * @return The end position
     */
    public long getEnd ()
    {
        return this.end;
    }


    /**
     * Gets the loop length in samples.
     *
     * @return The loop length
     */
    public long getLoopLength ()
    {
        return this.loopLength;
    }


    /**
     * Gets the loop mode.
     *
     * @return The loop mode (0 = off, 1 = on)
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Checks if looping is enabled.
     *
     * @return True if loop mode is on or until-release
     */
    public boolean isLoopEnabled ()
    {
        return this.loopMode == LOOP_MODE_ON;
    }


    /**
     * Gets the number of beats in the loop. Used for time-stretching and tempo matching.
     *
     * @return The number of beats (0-255)
     */
    public int getBeatsInLoop ()
    {
        return this.beatsInLoop;
    }


    /**
     * Gets the sample frequency in Hz.
     *
     * @return The sample frequency (typically 22050 or 44100 Hz)
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Gets the raw sample data as 16-bit PCM samples. For stereo samples, channels are interleaved
     * (L, R, L, R, ...).
     *
     * @return Array of 16-bit signed PCM samples
     */
    public short [] getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Gets the number of sample points. For stereo, this is the total number of values (L+R pairs).
     * Use {@link #getDurationInSamples()} for the actual audio length.
     *
     * @return The number of sample values
     */
    public int getSampleCount ()
    {
        return this.sampleData.length;
    }


    /**
     * Gets the duration in sample frames. For stereo, this accounts for interleaving (actual audio
     * length).
     *
     * @return The duration in samples
     */
    public int getDurationInSamples ()
    {
        return this.channels > 0 ? this.sampleData.length / this.channels : this.sampleData.length;
    }


    /**
     * Gets the duration in seconds.
     *
     * @return The duration in seconds
     */
    public double getDurationInSeconds ()
    {
        if (this.sampleRate == 0)
            return 0.0;
        return (double) this.getDurationInSamples () / this.sampleRate;
    }


    /**
     * Extracts a single channel from stereo sample data.
     *
     * @param channelIndex The channel to extract (0 = left, 1 = right)
     * @return Array of samples for the specified channel
     * @throws IllegalArgumentException If not stereo or invalid channel index
     */
    public short [] getChannel (final int channelIndex)
    {
        if (!this.isStereo ())
            throw new IllegalArgumentException ("Sample is not stereo");
        if (channelIndex < 0 || channelIndex >= this.channels)
            throw new IllegalArgumentException ("Invalid channel index: " + channelIndex);

        final int frameCount = this.sampleData.length / this.channels;
        final short [] channel = new short [frameCount];
        for (int i = 0; i < frameCount; i++)
            channel[i] = this.sampleData[i * this.channels + channelIndex];

        return channel;
    }
}