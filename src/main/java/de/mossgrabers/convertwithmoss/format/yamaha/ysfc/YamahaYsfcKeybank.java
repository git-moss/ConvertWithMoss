// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A key-bank which is the metadata description of a sample in YSFC terms.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcKeybank
{
    private int keyRangeLower;
    private int keyRangeUpper;
    private int velocityRangeLower;
    private int velocityRangeUpper;
    private int level;
    private int panorama;
    private int coarseTune;
    private int fineTune;
    private int rootNote;
    private int sampleFrequency;
    private int playStart;
    private int loopPoint;
    private int playEnd;
    private int number;
    private int sampleLength;
    private int channels;
    private int loopMode;


    /**
     * Default constructor.
     */
    public YamahaYsfcKeybank ()
    {
        // Intentionally empty
    }


    /**
     * Constructor which reads the wave metadata from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcKeybank (final InputStream in, final int version) throws IOException
    {
        this.read (in, version);
    }


    /**
     * Read a key-bank from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final int version) throws IOException
    {
        final boolean isVersion1 = version < 400;

        this.keyRangeLower = in.read ();
        this.keyRangeUpper = in.read ();
        this.velocityRangeLower = in.read ();
        this.velocityRangeUpper = in.read ();
        this.level = in.read ();
        // Range is only 0-128
        if (isVersion1)
            this.level = Math.clamp (2 * this.level, 0, 255);
        this.panorama = in.read ();

        final int unknown1 = in.read ();
        if (unknown1 != 0 && unknown1 != 3)
            throw new IOException ("Found unknown1 not to be 0 but " + unknown1);
        final int unknown2 = in.read ();
        if (unknown2 != 0xFF && unknown2 != 0x00)
            throw new IOException ("Found unknown2 not to be 0xFF but " + unknown2);

        this.rootNote = in.read ();
        this.coarseTune = in.read ();
        this.fineTune = in.read ();
        this.channels = in.read ();

        final int unknown3 = in.read ();
        if (unknown3 != 0)
            throw new IOException ("Found unknown3 not to be 0 but " + unknown3);
        final int unknown4 = in.read ();
        if (unknown4 != 2 && unknown4 != 0)
            throw new IOException ("Found unknown4 not to be 2 but " + unknown4);
        final int unknown5 = in.read ();
        if (unknown5 != 0 && unknown5 != 5 && unknown5 != 4 && unknown5 != 3)
            throw new IOException ("Found unknown5 not to be 5 but " + unknown5);

        this.loopMode = in.read ();

        final int unknown6 = in.read ();
        // if (unknown6 != 0 && unknown6 != 8)
        // throw new IOException ("Found unknown6 not to be 0 but " + unknown6);
        final int unknown7 = in.read ();
        // if (unknown7 != 0)
        // throw new IOException ("Found unknown7 not to be 0 but " + unknown7);

        final int loopPointRest = in.read ();

        final int unknown8 = in.read ();
        if (unknown8 != 1 && unknown8 != 255)
            throw new IOException ("Found unknown8 not to be 1 but " + unknown8);

        final byte [] compressionInfo = in.readNBytes (12);
        for (final byte element: compressionInfo)
            if (element != 0)
                throw new IOException (Functions.getMessage ("IDS_YSFC_ENCRYPTED_SAMPLES"));

        // Padding / reserved
        in.skipNBytes (4);

        this.sampleFrequency = (int) StreamUtils.readUnsigned32 (in, isVersion1);
        this.playStart = (int) StreamUtils.readUnsigned32 (in, isVersion1);
        this.loopPoint = (int) StreamUtils.readUnsigned32 (in, isVersion1);
        if (!isVersion1)
            this.loopPoint = 16 * this.loopPoint + loopPointRest;
        this.playEnd = (int) StreamUtils.readUnsigned32 (in, isVersion1);

        // Padding / reserved
        if (!isVersion1)
        {
            in.skipNBytes (4);
            this.number = (int) StreamUtils.readUnsigned32 (in, false);
        }
        this.sampleLength = (int) StreamUtils.readUnsigned32 (in, isVersion1);

        // No idea about these 20 bytes
        // 40 0C 00 10 FF FF FF FF 00 01 00 00 00 02 20 30 00 00 00 00
        // 40 0D 10 30 FF FF FF FF 00 02 00 00 00 02 15 24 00 00 00 00
        // 40 0E 1A D0 FF FF FF FF 00 03 00 00 00 02 0C 60 00 00 00 00
        // 40 0F 21 00 FF FF FF FF 00 04 00 00 00 02 02 74 00 00 00 00
        if (isVersion1)
            in.skip (20);
    }


    /**
     * Write a key-bank to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        out.write (this.keyRangeLower);
        out.write (this.keyRangeUpper);
        out.write (this.velocityRangeLower);
        out.write (this.velocityRangeUpper);
        out.write (this.level);
        out.write (this.panorama);

        // Unknown but should work
        out.write (0x00);
        out.write (0xFF);

        out.write (this.rootNote);
        out.write (this.coarseTune);
        out.write (this.fineTune);
        out.write (this.channels);

        // Unknown but should work
        out.write (0x00);
        out.write (0x02);
        out.write (0x05);

        out.write (this.loopMode);

        // Unknown but should be only padding
        out.write (0x00);
        out.write (0x00);

        out.write (this.loopPoint % 16);

        // Unknown but should work
        out.write (1);

        StreamUtils.padBytes (out, 12);

        // Padding / reserved
        StreamUtils.padBytes (out, 3);
        out.write (0xFF);

        StreamUtils.writeUnsigned32 (out, this.sampleFrequency, false);
        StreamUtils.writeUnsigned32 (out, this.playStart, false);
        StreamUtils.writeUnsigned32 (out, this.loopPoint / 16, false);
        StreamUtils.writeUnsigned32 (out, this.playEnd, false);

        // Padding / reserved
        StreamUtils.padBytes (out, 4);

        StreamUtils.writeUnsigned32 (out, this.number, false);
        StreamUtils.writeUnsigned32 (out, this.sampleLength, false);
    }


    /**
     * Create a text description of the object.
     *
     * @return The text
     */
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("\nSample Number: ").append (this.number).append ('\n');
        sb.append ("Frequency: ").append (this.sampleFrequency).append ('\n');
        sb.append ("Channels: ").append (this.channels).append ('\n');
        sb.append ("Root Note: ").append (this.rootNote).append ('\n');
        sb.append ("Low Note: ").append (this.keyRangeLower).append ('\n');
        sb.append ("High Note: ").append (this.keyRangeUpper).append ('\n');
        sb.append ("Low Velocity: ").append (this.velocityRangeLower).append ('\n');
        sb.append ("High Velocity: ").append (this.velocityRangeUpper).append ('\n');
        sb.append ("Sample Length: ").append (this.sampleLength).append ('\n');
        sb.append ("Play Start: ").append (this.playStart).append ('\n');
        sb.append ("Play End: ").append (this.playEnd).append ('\n');
        sb.append ("Loop Mode: ").append (this.loopMode).append ('\n');
        sb.append ("Loop Point: ").append (this.loopPoint).append ('\n');
        sb.append ("Coarse Tune: ").append (this.getCoarseTune ()).append (" (").append (this.coarseTune).append (")\n");
        sb.append ("Fine Tune: ").append (this.getFineTune ()).append (" (").append (this.fineTune).append (")\n");
        sb.append ("Level: ").append (this.level).append ('\n');
        sb.append ("Panorama: ").append (this.getPanorama ()).append (" (").append (this.panorama).append (")\n\n");
        return sb.toString ();
    }


    /**
     * Get the lower bound of the key range.
     *
     * @return The lower bound as a MIDI note number [0..127]
     */
    public int getKeyRangeLower ()
    {
        return this.keyRangeLower;
    }


    /**
     * Set the lower bound of the key range.
     *
     * @param keyRangeLower The lower bound as a MIDI note number [0..127]
     */
    public void setKeyRangeLower (final int keyRangeLower)
    {
        this.keyRangeLower = keyRangeLower;
    }


    /**
     * Get the upper bound of the key range.
     *
     * @return The upper bound as a MIDI note number [0..127]
     */
    public int getKeyRangeUpper ()
    {
        return this.keyRangeUpper;
    }


    /**
     * Set the upper bound of the key range.
     *
     * @param keyRangeUpper The upper bound as a MIDI note number [0..127]
     */
    public void setKeyRangeUpper (final int keyRangeUpper)
    {
        this.keyRangeUpper = keyRangeUpper;
    }


    /**
     * Get the lower bound of the velocity range.
     *
     * @return The lower bound in the range of [1..127]
     */
    public int getVelocityRangeLower ()
    {
        return this.velocityRangeLower;
    }


    /**
     * Set the lower bound of the velocity range.
     *
     * @param velocityRangeLower The lower bound in the range of [1..127]
     */
    public void setVelocityRangeLower (final int velocityRangeLower)
    {
        this.velocityRangeLower = velocityRangeLower;
    }


    /**
     * Get the upper bound of the velocity range.
     *
     * @return The upper bound in the range of [1..127]
     */
    public int getVelocityRangeUpper ()
    {
        return this.velocityRangeUpper;
    }


    /**
     * Get the upper bound of the velocity range.
     *
     * @param velocityRangeUpper The upper bound in the range of [1..127]
     */
    public void setVelocityRangeUpper (final int velocityRangeUpper)
    {
        this.velocityRangeUpper = velocityRangeUpper;
    }


    /**
     * Get the level at which the sample should be played.
     *
     * @return The level in the range of [0..255]
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Set the level at which the sample should be played.
     *
     * @param level The level in the range of [0..255]
     */
    public void setLevel (final int level)
    {
        this.level = level;
    }


    /**
     * Get the panorama of the sample.
     *
     * @return The panorama in the range of [-64 to 63]
     */
    public int getPanorama ()
    {
        return this.panorama - 64;
    }


    /**
     * Set the panorama of the sample.
     *
     * @param panorama The panorama in the range of [-64 to 63]
     */
    public void setPanorama (final int panorama)
    {
        this.panorama = panorama + 64;
    }


    /**
     * Get the coarse tune.
     *
     * @return The coarse tune in semi-tones in the range of [-64...+63]
     */
    public int getCoarseTune ()
    {
        return this.coarseTune - 64;
    }


    /**
     * Set the coarse tune.
     *
     * @param coarseTune The coarse tune in semi-tones in the range of [-64...+63]
     */
    public void setCoarseTune (final int coarseTune)
    {
        this.coarseTune = coarseTune + 64;
    }


    /**
     * Get the fine tune.
     *
     * @return The fine tune in cents in the range of [-64...+63]
     */
    public int getFineTune ()
    {
        return this.fineTune - 64;
    }


    /**
     * Set the fine tune.
     *
     * @param fineTune The fine tune in cents in the range of [-64...+63]
     */
    public void setFineTune (final int fineTune)
    {
        this.fineTune = fineTune + 64;
    }


    /**
     * Get the root note.
     *
     * @return The root note as a MIDI note number
     */
    public int getRootNote ()
    {
        return this.rootNote;
    }


    /**
     * Set the root note.
     *
     * @param rootNote The root note as a MIDI note number
     */
    public void setRootNote (final int rootNote)
    {
        this.rootNote = rootNote;
    }


    /**
     * Get the sample frequency in Hertz.
     *
     * @return E.g. 44100
     */
    public int getSampleFrequency ()
    {
        return this.sampleFrequency;
    }


    /**
     * Set the sample frequency in Hertz.
     *
     * @param sampleFrequency E.g. 44100
     */
    public void setSampleFrequency (final int sampleFrequency)
    {
        this.sampleFrequency = sampleFrequency;
    }


    /**
     * Get the start of the sample play-back.
     *
     * @return The start in sample frames
     */
    public int getPlayStart ()
    {
        return this.playStart;
    }


    /**
     * Set the start of the sample play-back.
     *
     * @param playStart The start in sample frames
     */
    public void setPlayStart (final int playStart)
    {
        this.playStart = playStart;
    }


    /**
     * Get the end of the sample play-back.
     *
     * @return The end in sample frames
     */
    public int getPlayEnd ()
    {
        return this.playEnd;
    }


    /**
     * Set the end of the sample play-back.
     *
     * @param playEnd The end in sample frames
     */
    public void setPlayEnd (final int playEnd)
    {
        this.playEnd = playEnd;
    }


    /**
     * Get the loop start.
     *
     * @return The start of the loop in sample frames
     */
    public int getLoopPoint ()
    {
        return this.loopPoint;
    }


    /**
     * Set the loop start.
     *
     * @param loopPoint The start of the loop in sample frames
     */
    public void setLoopPoint (final int loopPoint)
    {
        this.loopPoint = loopPoint;
    }


    /**
     * Get the position of the sample in the device memory.
     *
     * @return The position
     */
    public int getNumber ()
    {
        return this.number;
    }


    /**
     * Set the position of the sample in the device memory.
     *
     * @param number The position
     */
    public void setNumber (final int number)
    {
        this.number = number;
    }


    /**
     * Get the length of the sample.
     *
     * @return The length in sample frames
     */
    public int getSampleLength ()
    {
        return this.sampleLength;
    }


    /**
     * Set the length of the sample.
     *
     * @param sampleLength The length in sample frames
     */
    public void setSampleLength (final int sampleLength)
    {
        this.sampleLength = sampleLength;
    }


    /**
     * Get the number of channels.
     *
     * @return The number of channels, 1 = Mono, 2 = Stereo
     */
    public int getChannels ()
    {
        return this.channels;
    }


    /**
     * Set the number of channels.
     *
     * @param channels The number of channels, 1 = Mono, 2 = Stereo
     */
    public void setChannels (final int channels)
    {
        this.channels = channels;
    }


    /**
     * Get the loop mode.
     *
     * @return The loop mode, 0 = normal, 1 = one-shot, 2 = reverse
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Get the loop mode.
     *
     * @param loopMode The loop mode, 0 = normal, 1 = one-shot, 2 = reverse
     */
    public void setLoopMode (final int loopMode)
    {
        this.loopMode = loopMode;
    }
}
