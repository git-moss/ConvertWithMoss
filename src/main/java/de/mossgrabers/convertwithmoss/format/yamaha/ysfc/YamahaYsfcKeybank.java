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

    // output format for the case of writing
    private int version1TotalSampleOffset = -1;
    private int version1TotalChannelOffset = -1;
    private boolean isVersion1;
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
    private int fixedPitch = 0xFF;
    private int loopTune;


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
        final boolean isMotif = version < 103;
        final boolean isBigEndian = isVersion1 && !isMotif;

        this.keyRangeLower = in.read ();
        this.keyRangeUpper = in.read ();
        this.velocityRangeLower = in.read ();
        this.velocityRangeUpper = in.read ();
        this.level = in.read ();
        // Range is only 0-128
        if (isVersion1)
            this.level = Math.clamp (2L * this.level, 0, 255);
        this.panorama = in.read () & 0x7F;

        // Reserved 00
        in.skipNBytes (1);
        // Always 0xFF on Montage - 0: Normal, 1: Fixed
        this.fixedPitch = in.read ();

        this.rootNote = in.read ();
        this.coarseTune = in.read ();
        this.fineTune = in.read ();
        this.channels = in.read ();

        this.loopTune = in.read ();
        // Ignore
        in.skipNBytes (1);
        final int waveFormat = in.read ();
        if (waveFormat != 0 && waveFormat != 5)
            throw new IOException (Functions.getMessage ("IDS_YSFC_WAVE_FORMAT_NOT_SUPPORTED", waveFormat == 4 ? " WXC" : Integer.toString (waveFormat)));

        this.loopMode = in.read ();

        final int isEncrypted = in.read ();
        if (isEncrypted > 0 && !isVersion1)
            throw new IOException (Functions.getMessage ("IDS_YSFC_ENCRYPTED_SAMPLES"));
        in.skipNBytes (1);

        final int loopPointRest = in.read ();

        in.skipNBytes (1);

        // Compression/Encryption information
        in.skipNBytes (12);

        if (!isVersion1)
            in.skipNBytes (4); // 0x00 0x00 0x00 0xFF

        this.sampleFrequency = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.playStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopPoint = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        if (!isVersion1)
            this.loopPoint = 16 * this.loopPoint + loopPointRest;
        this.playEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        // Padding / reserved
        if (!isVersion1)
        {
            in.skipNBytes (4);
            this.number = (int) StreamUtils.readUnsigned32 (in, false);
        }
        this.sampleLength = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        if (!isVersion1)
            return;

        if (isMotif)
        {
            // 00 00 00 00 - 00 00 00 00 - FF FF FF FF - FF FF FF FF
            in.skipNBytes (16);
            return;
        }

        // v1.0.3 MOXF

        // Offset to something?!
        StreamUtils.readUnsigned32 (in, true);
        // Always FF FF FF FF
        StreamUtils.readUnsigned32 (in, true);

        this.number = StreamUtils.readUnsigned16 (in, true);

        // Padding
        in.skipNBytes (2);

        // Size of something?!
        StreamUtils.readUnsigned32 (in, true);

        // Padding
        in.skipNBytes (4);
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
        out.write (!this.isVersion1 ? this.level : this.level / 2);
        out.write (this.panorama);

        out.write (0x00);

        if (!this.isVersion1) {
            out.write(this.fixedPitch);
        } else {
            out.write(0x00);
        }

        out.write (this.rootNote);
        out.write (this.coarseTune);
        out.write (this.fineTune);
        out.write (this.channels);

        out.write (0x00); // loopTune
        // Always 2 for Montage, 0 for MOXF
        out.write (!this.isVersion1 ? 0x02 : 0x00);
        // 16-bit linear
        out.write (!this.isVersion1 ? 0x05 : 0x00); // for Montage - 16-bit 0x05 , for MOXF - 16-bit 0x00, 8-bit 0x02

        out.write (this.loopMode);

        // Unknown but should be only padding
        out.write (0x00); // isEncrypted
        out.write (0x00); // padding

        if (!this.isVersion1) {
            out.write(this.loopPoint % 16); // loopPointRest
        } else {
            // MOXF has this 0
            out.write(0x00);
        }

        // Unknown but should work
        out.write (!this.isVersion1 ? 0x01 : 0xFF);

        StreamUtils.padBytes (out, 12);

        // Padding / reserved
        if (!this.isVersion1) {
            StreamUtils.padBytes(out, 3);
            out.write(0xFF);
        }

        StreamUtils.writeUnsigned32 (out, this.sampleFrequency, false);
        StreamUtils.writeUnsigned32 (out, this.playStart, false);
        if (!this.isVersion1) {
            StreamUtils.writeUnsigned32(out, this.loopPoint / 16, false);
        } else {
            StreamUtils.writeUnsigned32(out, this.loopPoint, false);
        }
        StreamUtils.writeUnsigned32 (out, this.playEnd, false);

        // Padding / reserved
        if (!this.isVersion1) {
            StreamUtils.padBytes(out, 4);

            StreamUtils.writeUnsigned32(out, this.number, false);
        }
        StreamUtils.writeUnsigned32 (out, this.sampleLength, false);

        // TODO implement handling of MOTIF
//        if (isMotif) {
//            // 00 00 00 00 - 00 00 00 00 - FF FF FF FF - FF FF FF FF
//
//        }

        if (this.isVersion1) {
            StreamUtils.writeUnsigned16(out, this.version1TotalSampleOffset + this.sampleLength, false);
            out.write(0x00);
            out.write(0xC0);
            if (this.channels == 2) {
                StreamUtils.writeUnsigned16(out, this.version1TotalSampleOffset + this.sampleLength * 2, false);
                out.write(0x00);
                out.write(0xC0);
            } else {
                // 1 channel only, pad information for second channel wiwht 0xFF 0xFF 0xFF 0xFF
                StreamUtils.padBytes(out, 4, 0xFF);
            }

            // Index of keygroup wave in DWIM block across all Data entries in the DWIM section.
            // The code above supports single sample per keygroup and
            // maximum of 2 channels.
            // The indexing starts from 1.
            StreamUtils.writeUnsigned16(out, version1TotalChannelOffset + 1, false);
            StreamUtils.padBytes(out, 2);
            if (channels == 2) {
                StreamUtils.writeUnsigned16(out, version1TotalChannelOffset + 2, false);
                StreamUtils.padBytes(out, 2);
            } else {
                // If mono, the second channel data is padded with 0xFF 0xFF 0xFF 0xFF
                StreamUtils.padBytes(out, 4, 0xFF);
            }
        }
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
        sb.append ("Loop Tuning: ").append (this.loopTune).append ('\n');
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

    public void setVersion1TotalChannelOffset(int totalChannelOffset) {
        this.isVersion1 = true;
        this.version1TotalChannelOffset = totalChannelOffset;
    }

    public void setVersion1TotalSampleOffset(int totalSampleOffset) {
        this.isVersion1 = true;
        this.version1TotalSampleOffset = totalSampleOffset;
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
     * @return The fine tune in cents in the range of [-100...+98.4375]
     */
    public int getFineTune ()
    {
        return (int) Math.round ((this.fineTune - 64) * 1.5625); // 100/64
    }


    /**
     * Set the fine tune.
     *
     * @param fineTune The fine tune in cents in the range of [-100...+98.4375]
     */
    public void setFineTune (final int fineTune)
    {
        this.fineTune = Math.clamp (Math.round (fineTune / 1.5625 + 64), 0, 127);
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
     * Get if the samples pitch is fixed.
     *
     * @return 1 if fixed
     */
    public int getFixedPitch ()
    {
        return this.fixedPitch;
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
     * Get the loop tuning.
     *
     * @return The loop tuning
     */
    public int getLoopTune ()
    {
        return this.loopTune;
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
