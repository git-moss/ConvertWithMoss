// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.io.IOException;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * One sample of an Emulator X sample pool. A sample file (*.ebl) is a container with a table of
 * contents of exactly one 'E5S1' chunk; the single sample pool file of a bank holds one such chunk
 * per sample. The payload of the chunk holds a 188 byte header (184 bytes in version 1) followed by
 * the audio data - 16 bit little-endian PCM or 32 bit little-endian floating-point values - which
 * is stored one channel after the other instead of interleaved, and optionally a trailer with the
 * loop markers. All numeric fields of the header are little-endian except the format marker. See
 * documentation/design/EMULATORX_FORMAT.md for the layout.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXSampleFile
{
    private String  name       = "";
    private int     sampleRate = 44100;
    private int     numChannels;
    private int     numFrames;
    private boolean isFloat;
    private byte [] pcm;
    private boolean hasLoop;
    private int     loopStart;
    private int     loopEnd;


    /**
     * Constructor.
     */
    public EmulatorXSampleFile ()
    {
        // Intentionally empty
    }


    /**
     * Parse a sample file.
     *
     * @param fileData The content of the file
     * @return The parsed sample
     * @throws ParseException The file is not a valid sample file
     */
    public static EmulatorXSampleFile read (final byte [] fileData) throws ParseException
    {
        // The single table of contents entry points to the sample chunk; its payload follows the
        // 8 byte chunk header and the 2 byte chunk index
        final Optional<int []> toc = EmulatorXConstants.findTableOfContents (fileData);
        if (toc.isEmpty () || toc.get ()[1] < EmulatorXConstants.TOC_ENTRY_SIZE || !EmulatorXConstants.hasTag (fileData, toc.get ()[0], EmulatorXConstants.SAMPLE_TAG))
            throw new ParseException ("IDS_EXB_NOT_A_SAMPLE");
        final int payload = (int) EmulatorXConstants.getU32BE (fileData, toc.get ()[0] + 8) + EmulatorXConstants.CHUNK_OVERHEAD;
        if (payload < 0 || payload + EmulatorXConstants.SAMPLE_DATA_OFFSET > fileData.length)
            throw new ParseException ("IDS_EXB_NOT_A_SAMPLE");

        return read (fileData, payload, fileData.length - payload);
    }


    /**
     * Parse the payload of one sample chunk.
     *
     * @param fileData The content of the file which contains the chunk
     * @param payload The offset of the payload of the chunk, which follows its 2 byte index
     * @param payloadSize The size of the payload
     * @return The parsed sample
     * @throws ParseException The chunk does not contain a valid sample
     */
    public static EmulatorXSampleFile read (final byte [] fileData, final int payload, final int payloadSize) throws ParseException
    {
        final int payloadEnd = payload + payloadSize;
        if (payload < 0 || payloadSize < 0 || payloadEnd > fileData.length || payload + EmulatorXConstants.SAMPLE_DATA_OFFSET > payloadEnd)
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");

        final EmulatorXSampleFile sample = new EmulatorXSampleFile ();
        sample.name = EmulatorXConstants.decodeName (fileData, payload + EmulatorXConstants.SAMPLE_NAME);
        sample.sampleRate = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RATE);
        if (sample.sampleRate <= 0)
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");

        // 16 bit samples store 2 bytes per sample; samples which were recorded, imported or
        // processed in a higher resolution store 32 bit floating-point values
        sample.isFloat = fileData[payload + EmulatorXConstants.SAMPLE_BYTES_PER_SAMPLE] == EmulatorXConstants.BYTES_PER_FLOAT_FRAME;
        final int bytesPerSample = sample.isFloat ? EmulatorXConstants.BYTES_PER_FLOAT_FRAME : EmulatorXConstants.BYTES_PER_FRAME;

        final int leftStart = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_START);
        final int leftEnd = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_END);
        final int rightStart = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_START);
        final int rightEnd = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_END);

        // A mono sample either leaves the right channel empty or points it at the data of the left
        // channel; only a right channel with its own data makes the sample stereo
        final int leftLength = leftEnd - leftStart;
        final int rightLength = rightEnd - rightStart;
        final boolean isStereo = rightStart != leftStart && rightLength > 0 && rightLength == leftLength;
        if (leftStart < 0 || leftLength <= 0 || payload + leftStart + leftLength > payloadEnd || (isStereo && (rightStart < 0 || payload + rightStart + rightLength > payloadEnd)))
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");

        sample.numChannels = isStereo ? 2 : 1;
        sample.numFrames = leftLength / bytesPerSample;
        sample.pcm = isStereo ? interleave (fileData, payload + leftStart, payload + rightStart, sample.numFrames, bytesPerSample) : extract (fileData, payload + leftStart, sample.numFrames * bytesPerSample);

        if (EmulatorXConstants.getU16LE (fileData, payload + EmulatorXConstants.SAMPLE_LOOP_FLAG) > 0)
        {
            final int start = ((int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_START) - leftStart) / bytesPerSample;
            // The stored loop end addresses the last frame of the loop, like the end of the model
            final int end = ((int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_END) - leftStart) / bytesPerSample;
            if (start >= 0 && end > start && start < sample.numFrames)
            {
                sample.hasLoop = true;
                sample.loopStart = start;
                sample.loopEnd = Math.min (end, sample.numFrames - 1);
            }
        }

        return sample;
    }


    /**
     * Assemble the complete content of a sample file. A version 2 header is written, which is what
     * the Emulator X3 uses.
     *
     * @return The content of the file
     * @throws IOException The sample cannot be written
     */
    public byte [] write () throws IOException
    {
        if (this.pcm == null || this.numFrames <= 0)
            throw new IOException ("IDS_EXB_MALFORMED_SAMPLE");

        // A stereo sample stores the two channels one after the other, the right one starting at
        // the next 4 byte boundary behind the left one
        final int channelLength = this.numFrames * EmulatorXConstants.BYTES_PER_FRAME;
        final boolean isStereo = this.numChannels == 2;
        final int leftStart = EmulatorXConstants.SAMPLE_DATA_OFFSET;
        final int leftEnd = leftStart + channelLength;
        final int rightStart = isStereo ? alignChannelStart (leftEnd) : leftStart;
        final int rightEnd = isStereo ? rightStart + channelLength : leftEnd;
        final int trailerOffset = Math.max (leftEnd, rightEnd) + EmulatorXConstants.SAMPLE_DATA_POSTFIX;
        final int trailerLength = this.hasLoop ? EmulatorXConstants.SAMPLE_TRAILER_SIZE + 8 : 0;
        final int payloadSize = trailerOffset + trailerLength;

        final byte [] fileData = new byte [EmulatorXConstants.SAMPLE_PAYLOAD_OFFSET + payloadSize];
        System.arraycopy (EmulatorXConstants.FORM_MAGIC.getBytes (), 0, fileData, 0, 4);
        EmulatorXConstants.putU32BE (fileData, 4, fileData.length - 8L);
        System.arraycopy (EmulatorXConstants.FORM_TYPE.getBytes (), 0, fileData, 8, 8);
        EmulatorXConstants.putU32BE (fileData, 16, EmulatorXConstants.TOC_ENTRY_SIZE);

        // The single table of contents entry and the chunk header of the sample chunk
        final int chunkOffset = EmulatorXConstants.TOC_OFFSET + EmulatorXConstants.TOC_ENTRY_SIZE;
        System.arraycopy (EmulatorXConstants.SAMPLE_TAG.getBytes (), 0, fileData, EmulatorXConstants.TOC_OFFSET, 4);
        EmulatorXConstants.putU32BE (fileData, EmulatorXConstants.TOC_OFFSET + 4, payloadSize);
        EmulatorXConstants.putU32BE (fileData, EmulatorXConstants.TOC_OFFSET + 8, chunkOffset);
        EmulatorXConstants.encodeName (fileData, EmulatorXConstants.TOC_OFFSET + 14, this.name);
        System.arraycopy (EmulatorXConstants.SAMPLE_TAG.getBytes (), 0, fileData, chunkOffset, 4);
        EmulatorXConstants.putU32BE (fileData, chunkOffset + 4, payloadSize + 2L);

        final int payload = EmulatorXConstants.SAMPLE_PAYLOAD_OFFSET;
        EmulatorXConstants.putU16LE (fileData, payload + EmulatorXConstants.SAMPLE_VERSION, EmulatorXConstants.VERSION_2);
        EmulatorXConstants.encodeName (fileData, payload + EmulatorXConstants.SAMPLE_NAME, this.name);
        EmulatorXConstants.putU32BE (fileData, payload + EmulatorXConstants.SAMPLE_MARKER, EmulatorXConstants.SAMPLE_MARKER_VALUE);

        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_START, leftStart);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_START, rightStart);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_END, leftEnd);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_END, rightEnd);

        // Without a loop the end is put in front of the start, which is what the factory banks do
        final int loopStartOffset = this.hasLoop ? this.loopStart * EmulatorXConstants.BYTES_PER_FRAME : 0;
        final int loopEndOffset = this.hasLoop ? this.loopEnd * EmulatorXConstants.BYTES_PER_FRAME : -EmulatorXConstants.BYTES_PER_FRAME;
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_START, leftStart + (long) loopStartOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_LOOP_START, rightStart + (long) loopStartOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_END, leftStart + (long) loopEndOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_LOOP_END, rightStart + (long) loopEndOffset);

        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RATE, this.sampleRate);
        EmulatorXConstants.putU16LE (fileData, payload + EmulatorXConstants.SAMPLE_LOOP_FLAG, this.hasLoop ? 1 : 0);
        // The four flag bytes: the first and the last are constant, the other two describe the
        // channels - a mono sample uses only the left one, a stereo sample both
        fileData[payload + EmulatorXConstants.SAMPLE_FLAGS] = 1;
        fileData[payload + EmulatorXConstants.SAMPLE_EXTRA_CHANNELS] = (byte) (this.numChannels - 1);
        fileData[payload + EmulatorXConstants.SAMPLE_CHANNEL_MASK] = (byte) ((1 << this.numChannels) - 1);
        fileData[payload + EmulatorXConstants.SAMPLE_FLAGS + 3] = 2;
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_TRAILER_POINTER, this.hasLoop ? trailerOffset : 0);

        deinterleave (this.pcm, fileData, payload + leftStart, payload + rightStart, this.numFrames, this.numChannels);

        if (this.hasLoop)
        {
            int position = payload + trailerOffset;
            System.arraycopy (EmulatorXConstants.SAMPLE_TRAILER_TAG.getBytes (), 0, fileData, position, 4);
            EmulatorXConstants.putU32LE (fileData, position + 4, EmulatorXConstants.SAMPLE_TRAILER_SIZE);
            position += 8;
            System.arraycopy (EmulatorXConstants.SAMPLE_INFO_TAG.getBytes (), 0, fileData, position, 4);
            EmulatorXConstants.putU32LE (fileData, position + 4, 8);
            EmulatorXConstants.putU32LE (fileData, position + 8, 1);
            EmulatorXConstants.putU32LE (fileData, position + 12, 1);
            position += 16;
            System.arraycopy (EmulatorXConstants.SAMPLE_MARKER_TAG.getBytes (), 0, fileData, position, 4);
            EmulatorXConstants.putU32LE (fileData, position + 4, 8);
            EmulatorXConstants.putU32LE (fileData, position + 8, this.loopStart);
            // Unlike the header, the marker holds the frame behind the loop
            EmulatorXConstants.putU32LE (fileData, position + 12, this.loopEnd + 1L);
        }

        return fileData;
    }


    /**
     * Calculate the start of the right channel, which is the next 4 byte boundary behind the end of
     * the left channel. This leaves a gap of 4 bytes if the left channel already ends on such a
     * boundary and a gap of 2 bytes if it does not.
     *
     * @param leftEnd The end of the left channel
     * @return The start of the right channel
     */
    private static int alignChannelStart (final int leftEnd)
    {
        return (leftEnd / EmulatorXConstants.SAMPLE_CHANNEL_GAP + 1) * EmulatorXConstants.SAMPLE_CHANNEL_GAP;
    }


    /**
     * Copy a section of a buffer.
     *
     * @param data The buffer to copy from
     * @param offset The offset of the section
     * @param length The length of the section
     * @return The copy
     */
    private static byte [] extract (final byte [] data, final int offset, final int length)
    {
        final byte [] result = new byte [length];
        System.arraycopy (data, offset, result, 0, length);
        return result;
    }


    /**
     * Interleave the two channels of a stereo sample.
     *
     * @param data The buffer which holds both channels
     * @param leftOffset The offset of the left channel
     * @param rightOffset The offset of the right channel
     * @param numFrames The number of sample frames
     * @param bytesPerSample The number of bytes of one sample of one channel
     * @return The interleaved data
     */
    private static byte [] interleave (final byte [] data, final int leftOffset, final int rightOffset, final int numFrames, final int bytesPerSample)
    {
        final byte [] result = new byte [numFrames * 2 * bytesPerSample];
        for (int frame = 0; frame < numFrames; frame++)
        {
            System.arraycopy (data, leftOffset + frame * bytesPerSample, result, 2 * frame * bytesPerSample, bytesPerSample);
            System.arraycopy (data, rightOffset + frame * bytesPerSample, result, (2 * frame + 1) * bytesPerSample, bytesPerSample);
        }
        return result;
    }


    /**
     * Split interleaved PCM data into one block per channel.
     *
     * @param pcm The interleaved 16 bit little-endian data
     * @param data The buffer to write to
     * @param leftOffset The offset of the left channel
     * @param rightOffset The offset of the right channel
     * @param numFrames The number of sample frames
     * @param numChannels The number of channels
     */
    private static void deinterleave (final byte [] pcm, final byte [] data, final int leftOffset, final int rightOffset, final int numFrames, final int numChannels)
    {
        if (numChannels == 1)
        {
            System.arraycopy (pcm, 0, data, leftOffset, numFrames * 2);
            return;
        }
        for (int frame = 0; frame < numFrames; frame++)
        {
            data[leftOffset + frame * 2] = pcm[frame * 4];
            data[leftOffset + frame * 2 + 1] = pcm[frame * 4 + 1];
            data[rightOffset + frame * 2] = pcm[frame * 4 + 2];
            data[rightOffset + frame * 2 + 1] = pcm[frame * 4 + 3];
        }
    }


    /**
     * Get the name of the sample.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the sample.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Get the sample rate.
     *
     * @return The sample rate in Hertz
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * Set the sample rate.
     *
     * @param sampleRate The sample rate in Hertz
     */
    public void setSampleRate (final int sampleRate)
    {
        this.sampleRate = sampleRate;
    }


    /**
     * Get the number of channels.
     *
     * @return 1 for mono and 2 for stereo
     */
    public int getNumChannels ()
    {
        return this.numChannels;
    }


    /**
     * Get the number of sample frames.
     *
     * @return The number of frames
     */
    public int getNumFrames ()
    {
        return this.numFrames;
    }


    /**
     * Check if the audio data consists of 32 bit floating-point values instead of 16 bit PCM.
     *
     * @return True if it is floating-point data
     */
    public boolean isFloat ()
    {
        return this.isFloat;
    }


    /**
     * Get the resolution of the audio data.
     *
     * @return 32 for floating-point data, otherwise 16
     */
    public int getBitsPerSample ()
    {
        return 8 * (this.isFloat ? EmulatorXConstants.BYTES_PER_FLOAT_FRAME : EmulatorXConstants.BYTES_PER_FRAME);
    }


    /**
     * Get the interleaved little-endian audio data: 16 bit PCM or, if {@link #isFloat()} is true,
     * 32 bit floating-point values.
     *
     * @return The data
     */
    public byte [] getPcm ()
    {
        return this.pcm;
    }


    /**
     * Set the audio data.
     *
     * @param pcm The interleaved 16 bit little-endian PCM data
     * @param numChannels The number of channels, 1 or 2
     */
    public void setPcm (final byte [] pcm, final int numChannels)
    {
        this.pcm = pcm;
        this.isFloat = false;
        this.numChannels = numChannels;
        this.numFrames = pcm.length / (EmulatorXConstants.BYTES_PER_FRAME * numChannels);
    }


    /**
     * Check if the sample is looped.
     *
     * @return True if it has a loop
     */
    public boolean hasLoop ()
    {
        return this.hasLoop;
    }


    /**
     * Get the start of the loop.
     *
     * @return The first sample frame of the loop
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Get the end of the loop.
     *
     * @return The last sample frame of the loop
     */
    public int getLoopEnd ()
    {
        return this.loopEnd;
    }


    /**
     * Set the loop of the sample.
     *
     * @param start The first sample frame of the loop
     * @param end The last sample frame of the loop
     */
    public void setLoop (final int start, final int end)
    {
        this.hasLoop = true;
        this.loopStart = start;
        this.loopEnd = end;
    }
}
