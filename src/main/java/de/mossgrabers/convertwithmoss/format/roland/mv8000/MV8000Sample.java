// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mv8000;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A sample of a Roland MV-8000 patch file: the 38 byte parameter block of the sample 'PRM ' chunk
 * plus the raw wave data of the 'WAVE' chunk. Wave data is head-less 16-bit big-endian signed mono
 * PCM at a fixed rate of 44.1 kHz. Stereo samples are stored as 2 mono samples with consecutive
 * IDs; their names end with 0x7F followed by 'L' or 'R'.
 *
 * @author Jürgen Moßgraber
 */
public class MV8000Sample
{
    /** The fixed sample rate of the MV-8000. */
    public static final int  SAMPLE_RATE      = 44100;

    private static final int TYPE_TAG         = 2;
    private static final int STRUCTURE_TAG    = 0x27;
    private static final int DEFAULT_BPM      = 12000;

    private static final int STEREO_SEPARATOR = 0x7F;

    private int              id;
    private String           name;
    private int              startPoint;
    private int              loopStart;
    private int              endPoint;
    private int              rootKey;
    private int              bpm              = DEFAULT_BPM;
    private byte []          waveData;


    /**
     * Constructor. Reads the 38 byte parameter block.
     *
     * @param input The input stream to read the parameters from
     * @throws IOException Could not read
     */
    public MV8000Sample (final InputStream input) throws IOException
    {
        StreamUtils.readUnsigned32 (input, true);
        this.id = (int) StreamUtils.readUnsigned32 (input, true);
        this.name = StreamUtils.readAscii (input, 12);
        this.startPoint = (int) StreamUtils.readUnsigned32 (input, true);
        this.loopStart = (int) StreamUtils.readUnsigned32 (input, true);
        this.endPoint = (int) StreamUtils.readUnsigned32 (input, true);
        StreamUtils.readUnsigned8 (input);
        this.rootKey = StreamUtils.readUnsigned8 (input);
        this.bpm = (int) StreamUtils.readUnsigned32 (input, true);
    }


    /**
     * Constructor for creating a new sample.
     *
     * @param id The unique sample ID
     * @param name The sample name (max. 12 characters)
     */
    public MV8000Sample (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Write the 38 byte parameter block.
     *
     * @param output The output stream to write to
     * @throws IOException Could not write
     */
    public void write (final OutputStream output) throws IOException
    {
        StreamUtils.writeUnsigned32 (output, TYPE_TAG, true);
        StreamUtils.writeUnsigned32 (output, this.id, true);
        final StringBuilder sb = new StringBuilder (this.name);
        while (sb.length () < 12)
            sb.append (' ');
        for (int i = 0; i < 12; i++)
        {
            final char c = sb.charAt (i);
            // 0x7F is the separator of the stereo L/R suffix
            output.write (c <= 127 ? c : ' ');
        }
        StreamUtils.writeUnsigned32 (output, this.startPoint, true);
        StreamUtils.writeUnsigned32 (output, this.loopStart, true);
        StreamUtils.writeUnsigned32 (output, this.endPoint, true);
        output.write (STRUCTURE_TAG);
        output.write (this.rootKey);
        StreamUtils.writeUnsigned32 (output, this.bpm, true);
    }


    /**
     * Get the unique ID of the sample which is referenced by SMT slots.
     *
     * @return The ID
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the name. For stereo halves this includes the 0x7F-L/R suffix.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the name without the stereo suffix.
     *
     * @return The cleaned name
     */
    public String getCleanName ()
    {
        final int pos = this.name.indexOf (STEREO_SEPARATOR);
        return (pos < 0 ? this.name : this.name.substring (0, pos)).trim ();
    }


    /**
     * Is this the left half of a stereo pair?
     *
     * @return True if it is the left half
     */
    public boolean isStereoLeft ()
    {
        return this.name.length () == 12 && this.name.charAt (10) == STEREO_SEPARATOR && this.name.charAt (11) == 'L';
    }


    /**
     * Is this sample the right half matching the given left half?
     *
     * @param leftSample The left half to match
     * @return True if it is the matching right half
     */
    public boolean isStereoRightOf (final MV8000Sample leftSample)
    {
        return this.name.length () == 12 && this.name.charAt (10) == STEREO_SEPARATOR && this.name.charAt (11) == 'R' && this.name.substring (0, 10).equals (leftSample.name.substring (0, 10));
    }


    /**
     * Get the start point in sample frames.
     *
     * @return The start point
     */
    public int getStartPoint ()
    {
        return this.startPoint;
    }


    /**
     * Set the start point in sample frames.
     *
     * @param startPoint The start point
     */
    public void setStartPoint (final int startPoint)
    {
        this.startPoint = startPoint;
    }


    /**
     * Get the loop start point in sample frames.
     *
     * @return The loop start
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Set the loop start point in sample frames.
     *
     * @param loopStart The loop start
     */
    public void setLoopStart (final int loopStart)
    {
        this.loopStart = loopStart;
    }


    /**
     * Get the end point in sample frames. This is also the loop end. The wave data may contain
     * more frames.
     *
     * @return The end point
     */
    public int getEndPoint ()
    {
        return this.endPoint;
    }


    /**
     * Set the end point in sample frames.
     *
     * @param endPoint The end point
     */
    public void setEndPoint (final int endPoint)
    {
        this.endPoint = endPoint;
    }


    /**
     * Get the root key.
     *
     * @return The root key (MIDI note)
     */
    public int getRootKey ()
    {
        return this.rootKey;
    }


    /**
     * Set the root key.
     *
     * @param rootKey The root key (MIDI note)
     */
    public void setRootKey (final int rootKey)
    {
        this.rootKey = rootKey;
    }


    /**
     * Get the raw wave data (16-bit big-endian signed mono PCM, 44.1 kHz).
     *
     * @return The wave data
     */
    public byte [] getWaveData ()
    {
        return this.waveData;
    }


    /**
     * Set the raw wave data.
     *
     * @param waveData The wave data (16-bit big-endian signed mono PCM, 44.1 kHz)
     */
    public void setWaveData (final byte [] waveData)
    {
        this.waveData = waveData;
    }


    /**
     * Get the number of sample frames in the wave data.
     *
     * @return The number of frames
     */
    public int getFrameCount ()
    {
        return this.waveData == null ? 0 : this.waveData.length / 2;
    }
}
