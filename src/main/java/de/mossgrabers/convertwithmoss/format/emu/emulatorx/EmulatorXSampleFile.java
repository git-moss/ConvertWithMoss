// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulatorx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * One sample file (*.ebl) of an Emulator X sample pool. The file is a container with a table of
 * contents of exactly one 'E5S1' chunk. Its payload holds a 188 byte header followed by 16 bit
 * little-endian PCM data, which is stored one channel after the other instead of interleaved, and
 * optionally a trailer with the loop markers. All numeric fields of the header are little-endian
 * except the format marker. See documentation/design/EMULATORX_FORMAT.md for the layout.
 *
 * @author Jürgen Moßgraber
 */
public class EmulatorXSampleFile
{
    private String  name       = "";
    private int     sampleRate = 44100;
    private int     numChannels;
    private int     numFrames;
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
        if (!EmulatorXConstants.hasTag (fileData, 0, EmulatorXConstants.FORM_MAGIC) || !EmulatorXConstants.hasTag (fileData, 8, EmulatorXConstants.FORM_TYPE))
            throw new ParseException ("IDS_EXB_NOT_A_SAMPLE");

        // The single table of contents entry points to the sample chunk; its payload follows the
        // 8 byte chunk header and the 2 byte chunk index
        final int tocSize = (int) EmulatorXConstants.getU32BE (fileData, 16);
        if (tocSize < EmulatorXConstants.TOC_ENTRY_SIZE || !EmulatorXConstants.hasTag (fileData, EmulatorXConstants.TOC_OFFSET, EmulatorXConstants.SAMPLE_TAG))
            throw new ParseException ("IDS_EXB_NOT_A_SAMPLE");
        final int payload = (int) EmulatorXConstants.getU32BE (fileData, EmulatorXConstants.TOC_OFFSET + 8) + EmulatorXConstants.CHUNK_OVERHEAD;
        if (payload < 0 || payload + EmulatorXConstants.SAMPLE_DATA_OFFSET > fileData.length)
            throw new ParseException ("IDS_EXB_NOT_A_SAMPLE");

        final EmulatorXSampleFile sample = new EmulatorXSampleFile ();
        sample.name = EmulatorXConstants.decodeName (fileData, payload + EmulatorXConstants.SAMPLE_NAME);
        sample.sampleRate = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RATE);
        if (sample.sampleRate <= 0)
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");

        final int leftStart = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_START);
        final int leftEnd = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_END);
        final int rightStart = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_START);
        final int rightEnd = (int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_END);

        // A mono sample either leaves the right channel empty or points it at the data of the left
        // channel; only a right channel with its own data makes the sample stereo
        final int leftLength = leftEnd - leftStart;
        final int rightLength = rightEnd - rightStart;
        final boolean isStereo = rightStart != leftStart && rightLength > 0 && rightLength == leftLength;
        if (leftLength <= 0 || payload + leftStart + leftLength > fileData.length)
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");
        if (isStereo && payload + rightStart + rightLength > fileData.length)
            throw new ParseException ("IDS_EXB_MALFORMED_SAMPLE");

        sample.numChannels = isStereo ? 2 : 1;
        sample.numFrames = leftLength / EmulatorXConstants.BYTES_PER_FRAME;
        sample.pcm = isStereo ? interleave (fileData, payload + leftStart, payload + rightStart, sample.numFrames) : extract (fileData, payload + leftStart, sample.numFrames * EmulatorXConstants.BYTES_PER_FRAME);

        if (EmulatorXConstants.getU16LE (fileData, payload + EmulatorXConstants.SAMPLE_LOOP_FLAG) > 0)
        {
            final int start = ((int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_START) - leftStart) / EmulatorXConstants.BYTES_PER_FRAME;
            // The stored loop end addresses the last frame of the loop, the model the one behind it
            final int end = ((int) EmulatorXConstants.getU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_END) - leftStart) / EmulatorXConstants.BYTES_PER_FRAME + 1;
            if (start >= 0 && end > start && start < sample.numFrames)
            {
                sample.hasLoop = true;
                sample.loopStart = start;
                sample.loopEnd = Math.min (end, sample.numFrames);
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

        final int channelLength = this.numFrames * EmulatorXConstants.BYTES_PER_FRAME;
        final int dataLength = channelLength * this.numChannels;
        final int trailerOffset = EmulatorXConstants.SAMPLE_DATA_OFFSET + dataLength + EmulatorXConstants.SAMPLE_DATA_POSTFIX;
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

        final int leftStart = EmulatorXConstants.SAMPLE_DATA_OFFSET;
        final int leftEnd = leftStart + channelLength;
        final boolean isStereo = this.numChannels == 2;
        final int rightStart = isStereo ? leftEnd : leftStart;
        final int rightEnd = isStereo ? leftEnd + channelLength : leftEnd;
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_START, leftStart);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_START, rightStart);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_END, leftEnd);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_END, rightEnd);

        // Without a loop the end is put in front of the start, which is what the factory banks do
        final int loopStartOffset = this.hasLoop ? this.loopStart * EmulatorXConstants.BYTES_PER_FRAME : 0;
        final int loopEndOffset = this.hasLoop ? (this.loopEnd - 1) * EmulatorXConstants.BYTES_PER_FRAME : -EmulatorXConstants.BYTES_PER_FRAME;
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_START, leftStart + loopStartOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_LOOP_START, rightStart + loopStartOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_LEFT_LOOP_END, leftStart + loopEndOffset);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RIGHT_LOOP_END, rightStart + loopEndOffset);

        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_RATE, this.sampleRate);
        EmulatorXConstants.putU16LE (fileData, payload + EmulatorXConstants.SAMPLE_LOOP_FLAG, this.hasLoop ? 1 : 0);
        EmulatorXConstants.putU32LE (fileData, payload + EmulatorXConstants.SAMPLE_FLAGS, EmulatorXConstants.SAMPLE_FLAGS_VALUE);
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
            EmulatorXConstants.putU32LE (fileData, position + 12, this.loopEnd);
        }

        return fileData;
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
     * @return The interleaved 16 bit little-endian data
     */
    private static byte [] interleave (final byte [] data, final int leftOffset, final int rightOffset, final int numFrames)
    {
        final byte [] result = new byte [numFrames * 4];
        for (int frame = 0; frame < numFrames; frame++)
        {
            result[frame * 4] = data[leftOffset + frame * 2];
            result[frame * 4 + 1] = data[leftOffset + frame * 2 + 1];
            result[frame * 4 + 2] = data[rightOffset + frame * 2];
            result[frame * 4 + 3] = data[rightOffset + frame * 2 + 1];
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
     * Get the interleaved 16 bit little-endian PCM data.
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
     * @return The sample frame behind the loop
     */
    public int getLoopEnd ()
    {
        return this.loopEnd;
    }


    /**
     * Set the loop of the sample.
     *
     * @param start The first sample frame of the loop
     * @param end The sample frame behind the loop
     */
    public void setLoop (final int start, final int end)
    {
        this.hasLoop = true;
        this.loopStart = start;
        this.loopEnd = end;
    }
}
