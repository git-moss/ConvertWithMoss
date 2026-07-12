// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.github.trilarion.sound.vorbis.jcraft.jogg.Packet;
import com.github.trilarion.sound.vorbis.jcraft.jogg.Page;
import com.github.trilarion.sound.vorbis.jcraft.jogg.StreamState;
import com.github.trilarion.sound.vorbis.jcraft.jogg.SyncState;
import com.github.trilarion.sound.vorbis.jcraft.jorbis.Block;
import com.github.trilarion.sound.vorbis.jcraft.jorbis.Comment;
import com.github.trilarion.sound.vorbis.jcraft.jorbis.DspState;
import com.github.trilarion.sound.vorbis.jcraft.jorbis.Info;


/**
 * Decodes an Ogg Vorbis file to a 16-bit WAV file. The Java Sound path (the SPI wrapper around the
 * JOrbis decoder) stops draining the decoder at the last whole block and ignores the end-of-stream
 * length stored in the granule position of the last Ogg page. It therefore loses the final block of
 * every file (e.g. about 10ms for 44.1kHz files), which breaks sample loops that end at (or near)
 * the end of the file. This class drives the same JOrbis decoder directly, drains it fully and so
 * emits all sample frames - the output length matches the granule position of the last Ogg page.
 *
 * @author Jürgen Moßgraber
 */
public class OggVorbisDecoder
{
    private static final int READ_CHUNK_SIZE    = 8192;
    private static final int NUM_HEADER_PACKETS = 3;


    /**
     * Private due to utility class.
     */
    private OggVorbisDecoder ()
    {
        // Intentionally empty
    }


    /**
     * Decodes an Ogg Vorbis file and writes the audio data in WAV format (16-bit signed PCM) to the
     * given output stream. The WAV data is fully created in-memory before anything is written, so
     * nothing has been written to the output stream if an exception occurs.
     *
     * @param inputFile The Ogg Vorbis file to decode
     * @param outputStream The output stream to write the WAV file to
     * @throws IOException Could not read or decode the file
     */
    public static void decodeToWav (final File inputFile, final OutputStream outputStream) throws IOException
    {
        try (final InputStream inputStream = new BufferedInputStream (new FileInputStream (inputFile)))
        {
            decodeToWav (inputStream, outputStream);
        }
    }


    /**
     * Decodes an Ogg Vorbis stream and writes the audio data in WAV format (16-bit signed PCM) to
     * the given output stream. The WAV data is fully created in-memory before anything is written,
     * so nothing has been written to the output stream if an exception occurs.
     *
     * @param inputStream The Ogg Vorbis stream to decode
     * @param outputStream The output stream to write the WAV file to
     * @throws IOException Could not read or decode the stream
     */
    public static void decodeToWav (final InputStream inputStream, final OutputStream outputStream) throws IOException
    {
        final SyncState syncState = new SyncState ();
        final StreamState streamState = new StreamState ();
        final Page page = new Page ();
        final Packet packet = new Packet ();
        final Info info = new Info ();
        final Comment comment = new Comment ();
        final DspState dspState = new DspState ();
        final Block block = new Block (dspState);

        syncState.init ();

        final int serialNumber = readHeaders (inputStream, syncState, streamState, page, packet, info, comment);

        dspState.synthesis_init (info);
        block.init (dspState);

        final ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream ();
        decodeStream (inputStream, syncState, streamState, page, packet, dspState, block, serialNumber, info.channels, pcmOutput);

        // Wrap the raw PCM data into a WAV file - created fully in-memory so that nothing has
        // been written to the output stream yet if the decoding above failed
        final byte [] pcmData = pcmOutput.toByteArray ();
        final AudioFormat audioFormat = new AudioFormat (info.rate, 16, info.channels, true, false);
        final int numFrames = pcmData.length / audioFormat.getFrameSize ();
        final ByteArrayOutputStream wavOutput = new ByteArrayOutputStream ();
        try (final AudioInputStream audioInputStream = new AudioInputStream (new ByteArrayInputStream (pcmData), audioFormat, numFrames))
        {
            AudioSystem.write (audioInputStream, AudioFileFormat.Type.WAVE, wavOutput);
        }
        wavOutput.writeTo (outputStream);
    }


    /**
     * Reads the first Ogg page and the 3 Vorbis header packets (identification, comment, setup) and
     * initializes the stream and codec state from them.
     *
     * @param inputStream Where to read the data from
     * @param syncState The Ogg page synchronization state
     * @param streamState The Ogg stream state, initialized with the serial number of the stream
     * @param page The page to fill
     * @param packet The packet to fill
     * @param info The codec info to fill
     * @param comment The comments to fill
     * @return The serial number of the Vorbis stream
     * @throws IOException Could not read the data or it is not a Vorbis stream
     */
    private static int readHeaders (final InputStream inputStream, final SyncState syncState, final StreamState streamState, final Page page, final Packet packet, final Info info, final Comment comment) throws IOException
    {
        if (readIntoSyncState (inputStream, syncState) <= 0 || syncState.pageout (page) != 1)
            throw new IOException ("Not an Ogg stream.");

        final int serialNumber = page.serialno ();
        streamState.init (serialNumber);
        info.init ();
        comment.init ();

        if (streamState.pagein (page) < 0 || streamState.packetout (packet) != 1 || info.synthesis_headerin (comment, packet) < 0)
            throw new IOException ("Not an Ogg Vorbis stream.");

        int numHeaders = 1;
        while (numHeaders < NUM_HEADER_PACKETS)
        {
            final int result = streamState.packetout (packet);
            if (result < 0)
                throw new IOException ("Corrupt Ogg Vorbis header.");
            if (result == 1)
            {
                if (info.synthesis_headerin (comment, packet) < 0)
                    throw new IOException ("Corrupt Ogg Vorbis header.");
                numHeaders++;
                continue;
            }

            // Get the next page - skip pages of other multiplexed streams
            if (syncState.pageout (page) == 1)
            {
                if (page.serialno () == serialNumber)
                    streamState.pagein (page);
                continue;
            }
            if (readIntoSyncState (inputStream, syncState) <= 0)
                throw new IOException ("Unexpected end of stream in Ogg Vorbis headers.");
        }

        return serialNumber;
    }


    /**
     * Decodes all audio packets of the stream. The decoder is fully drained after each block, and
     * the JOrbis engine trims the output of the final block to the length given by the granule
     * position of the last page, so exactly all sample frames of the stream are emitted.
     *
     * @param inputStream Where to read the data from
     * @param syncState The Ogg page synchronization state
     * @param streamState The Ogg stream state
     * @param page The page to fill
     * @param packet The packet to fill
     * @param dspState The decoder state
     * @param block The decoder working block
     * @param serialNumber The serial number of the Vorbis stream
     * @param numChannels The number of audio channels
     * @param pcmOutput Where to write the decoded, interleaved 16-bit signed PCM data
     * @throws IOException Could not read the data
     */
    private static void decodeStream (final InputStream inputStream, final SyncState syncState, final StreamState streamState, final Page page, final Packet packet, final DspState dspState, final Block block, final int serialNumber, final int numChannels, final ByteArrayOutputStream pcmOutput) throws IOException
    {
        final float [] [] [] pcmChannels = new float [1] [] [];
        final int [] pcmIndices = new int [numChannels];
        byte [] conversionBuffer = new byte [READ_CHUNK_SIZE * 2 * numChannels];

        boolean endOfStream = false;
        while (!endOfStream)
        {
            final int pageResult = syncState.pageout (page);
            if (pageResult == 0)
            {
                // Need more data - the end of the file also ends the stream
                if (readIntoSyncState (inputStream, syncState) <= 0)
                    endOfStream = true;
                continue;
            }
            // Ignore holes in the data
            // Skip pages of other multiplexed streams
            if ((pageResult < 0) || (page.serialno () != serialNumber))
                continue;

            streamState.pagein (page);

            while (true)
            {
                final int packetResult = streamState.packetout (packet);
                if (packetResult == 0)
                    break;
                // Ignore holes in the data
                if (packetResult < 0)
                    continue;

                if (block.synthesis (packet) == 0)
                    dspState.synthesis_blockin (block);

                int numSamples;
                while ((numSamples = dspState.synthesis_pcmout (pcmChannels, pcmIndices)) > 0)
                {
                    final int numBytes = 2 * numChannels * numSamples;
                    if (conversionBuffer.length < numBytes)
                        conversionBuffer = new byte [numBytes];
                    convertToPCM16 (pcmChannels[0], pcmIndices, numChannels, numSamples, conversionBuffer);
                    pcmOutput.write (conversionBuffer, 0, numBytes);
                    dspState.synthesis_read (numSamples);
                }
            }

            if (page.eos () != 0)
                endOfStream = true;
        }
    }


    /**
     * Converts float samples to interleaved 16-bit signed little-endian PCM.
     *
     * @param pcm The float samples of all channels
     * @param pcmIndices The offset of the first sample for each channel
     * @param numChannels The number of audio channels
     * @param numSamples The number of sample frames to convert
     * @param output Where to write the converted data, must hold at least 2 * numChannels *
     *            numSamples bytes
     */
    private static void convertToPCM16 (final float [] [] pcm, final int [] pcmIndices, final int numChannels, final int numSamples, final byte [] output)
    {
        for (int channel = 0; channel < numChannels; channel++)
        {
            final float [] channelData = pcm[channel];
            final int offset = pcmIndices[channel];
            int outputPosition = channel * 2;
            for (int i = 0; i < numSamples; i++)
            {
                // Multiply as double - this matches the rounding of the Java Sound decode path
                final int value = Math.clamp ((int) (channelData[offset + i] * 32767.0), Short.MIN_VALUE, Short.MAX_VALUE);
                output[outputPosition] = (byte) value;
                output[outputPosition + 1] = (byte) (value >>> 8);
                outputPosition += 2 * numChannels;
            }
        }
    }


    /**
     * Reads the next chunk of the file into the Ogg page synchronization state.
     *
     * @param inputStream Where to read the data from
     * @param syncState The Ogg page synchronization state
     * @return The number of bytes read, -1 if the end of the stream was reached
     * @throws IOException Could not read the data
     */
    private static int readIntoSyncState (final InputStream inputStream, final SyncState syncState) throws IOException
    {
        final int index = syncState.buffer (READ_CHUNK_SIZE);
        final int numBytes = inputStream.read (syncState.data, index, READ_CHUNK_SIZE);
        if (numBytes > 0)
            syncState.wrote (numBytes);
        return numBytes;
    }
}
