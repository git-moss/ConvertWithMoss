// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.IStreamable;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A key-bank which is the metadata description of a sample in YSFC terms.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcKeybank implements IStreamable
{
    private final YamahaYsfcFileFormat version;

    private int                        keyRangeLower;
    private int                        keyRangeUpper;
    private int                        velocityRangeLower;
    private int                        velocityRangeUpper;
    private int                        level;
    private int                        panning            = 64;
    private int                        coarseTune;
    private int                        fineTune;
    private int                        rootNote;
    private int                        sampleFrequency;
    private int                        playStart;
    private int                        loopStart;
    private int                        loopEnd;
    private int                        sampleNumber;
    private int                        sampleLength;
    private int                        channels;
    private int                        loopMode;
    private int                        fixedPitch         = 0xFF;
    private int                        loopTune;

    private int                        totalSampleOffset  = -1;
    private int                        totalChannelOffset = -1;


    /**
     * Default constructor.
     *
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     */
    public YamahaYsfcKeybank (final YamahaYsfcFileFormat version)
    {
        this.version = version;
    }


    /**
     * Constructor which reads the wave metadata from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcKeybank (final InputStream in, final YamahaYsfcFileFormat version) throws IOException
    {
        this.version = version;
        this.read (in);
    }


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        final boolean isVersion1 = this.version.isVersion1 ();
        final boolean isMotif = this.version.isMotif ();
        final boolean isBigEndian = isVersion1 && !isMotif;

        this.keyRangeLower = in.read ();
        this.keyRangeUpper = in.read ();
        this.velocityRangeLower = in.read ();
        this.velocityRangeUpper = in.read ();
        this.level = in.read ();
        // Range is only 0-128 in version 1.x
        if (isVersion1)
            this.level = Math.clamp (2L * this.level, 0, 255);
        // Bit 7 contains the Pan-Curve
        this.panning = in.read () & 0x7F;

        // Reserved 00
        in.skipNBytes (1);
        // Always 0xFF on Montage - 0: Normal, 1: Fixed
        this.fixedPitch = in.read ();

        this.rootNote = in.read ();
        this.coarseTune = in.read ();
        this.fineTune = in.read ();
        this.channels = in.read ();

        this.loopTune = in.read ();

        // Play-form on Montage / loop fraction on older formats (Motif)
        in.read ();

        final int waveFormat = in.read ();
        if (waveFormat != 0 && waveFormat != 5)
            throw new IOException (Functions.getMessage ("IDS_YSFC_WAVE_FORMAT_NOT_SUPPORTED", waveFormat == 4 ? " WXC" : Integer.toString (waveFormat)));

        this.loopMode = in.read ();

        final int isEncrypted = in.read ();
        if (isEncrypted > 0 && !isVersion1)
            throw new IOException (Functions.getMessage ("IDS_YSFC_ENCRYPTED_SAMPLES"));
        in.skipNBytes (1);

        final int loopPointRest = in.read ();

        // Compression/Encryption information
        in.skipNBytes (13);

        if (!isMotif)
            in.skipNBytes (4); // 0x00 0x00 0x00 0xFF

        this.sampleFrequency = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.playStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        if (!isVersion1)
        {
            // Only correct for waveFormat == 0 and 5!
            this.loopStart = 16 * this.loopStart + loopPointRest;

            // Padding / reserved
            in.skipNBytes (4);
            this.sampleNumber = (int) StreamUtils.readUnsigned32 (in, false);
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
        StreamUtils.readUnsigned16 (in, true);
        StreamUtils.readUnsigned16 (in, true);

        // Always FF FF FF FF
        in.skipNBytes (4);

        this.sampleNumber = StreamUtils.readUnsigned16 (in, true);

        // Padding
        in.skipNBytes (2);

        // Sample Size of all channels
        StreamUtils.readUnsigned32 (in, true);

        // Padding
        in.skipNBytes (4);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        final boolean isVersion1 = this.version.isVersion1 ();
        final boolean isMotif = this.version.isMotif ();
        final boolean isBigEndian = isVersion1 && !isMotif;

        out.write (this.keyRangeLower);
        out.write (this.keyRangeUpper);
        out.write (this.velocityRangeLower);
        out.write (this.velocityRangeUpper);
        out.write (isVersion1 ? this.level / 2 : this.level);
        out.write (this.panning);

        out.write (0x00);
        out.write (isVersion1 ? 0x00 : this.fixedPitch);

        out.write (this.rootNote);
        out.write (this.coarseTune);
        out.write (this.fineTune);
        out.write (this.channels);

        // Loop Tune - always 2 for Montage, 0 for MOXF
        out.write (0x00);
        // Play Form, always 2
        out.write (isVersion1 ? 0x00 : 0x02);
        // 16-bit linear - Montage: 16-bit: 0x05, MOXF - 16-bit: 0x0, 8-bit: 0x02
        out.write (isVersion1 ? 0x00 : 0x05);

        out.write (this.loopMode);

        // No encryption
        out.write (0x00);
        // Pitch up limit
        out.write (0x00);

        out.write (isVersion1 ? 0x00 : this.loopStart % 16);

        // Unknown but should work
        out.write (isVersion1 ? 0xFF : 1);

        // Encryption
        StreamUtils.padBytes (out, 12);

        if (!isMotif)
        {
            // Padding / reserved
            StreamUtils.padBytes (out, 3);
            out.write (0xFF);
        }

        StreamUtils.writeUnsigned32 (out, this.sampleFrequency, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.playStart, isBigEndian);
        StreamUtils.writeUnsigned32 (out, isVersion1 ? this.loopStart : this.loopStart / 16, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.loopEnd, isBigEndian);

        if (!isVersion1)
        {
            // Padding / reserved
            StreamUtils.padBytes (out, 4);
            StreamUtils.writeUnsigned32 (out, this.sampleNumber, isBigEndian);
        }

        StreamUtils.writeUnsigned32 (out, this.sampleLength, isBigEndian);

        if (!isVersion1)
            return;

        if (isMotif)
        {
            StreamUtils.padBytes (out, 8, 0x00);
            StreamUtils.padBytes (out, 8, 0xFF);
            return;
        }

        // MOXF only

        StreamUtils.writeUnsigned16 (out, this.totalChannelOffset, isBigEndian);

        // IMPROVE MOXF The calculation is not correct
        StreamUtils.writeUnsigned16 (out, this.totalSampleOffset + this.sampleLength * this.channels, isBigEndian);

        // 1 channel only, pad information for second channel with 0xFF 0xFF 0xFF 0xFF
        StreamUtils.padBytes (out, 4, 0xFF);

        StreamUtils.writeUnsigned16 (out, this.sampleNumber, true);

        // Padding
        StreamUtils.padBytes (out, 2, 0x00);

        // Size of all channels - only stereo samples with both mono of same length are supported
        StreamUtils.writeUnsigned32 (out, this.channels * this.sampleLength, true);

        // Padding
        StreamUtils.padBytes (out, 4, 0x00);
    }


    /**
     * Set the global sample and channel offset, relevant for Yamaha MOXF.
     * 
     * @param totalChannelOffset a field that for each channel data stores its total index in DWIM
     *            block (counting channels of all samples in the library starting at 1.
     * @param totalSampleOffset a field that for each key-group (across all key-banks) stores total
     *            index of the samples in the library starting at 0x10
     */
    public void setOffsets (final int totalChannelOffset, final int totalSampleOffset)
    {
        this.totalChannelOffset = totalChannelOffset;
        this.totalSampleOffset = totalSampleOffset;
    }


    /**
     * Create a text description of the object.
     *
     * @return The text
     */
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("\nSample Number: ").append (this.sampleNumber).append ('\n');
        sb.append ("Frequency: ").append (this.sampleFrequency).append ('\n');
        sb.append ("Channels: ").append (this.channels).append ('\n');
        sb.append ("Root Note: ").append (this.rootNote).append ('\n');
        sb.append ("Low Note: ").append (this.keyRangeLower).append ('\n');
        sb.append ("High Note: ").append (this.keyRangeUpper).append ('\n');
        sb.append ("Low Velocity: ").append (this.velocityRangeLower).append ('\n');
        sb.append ("High Velocity: ").append (this.velocityRangeUpper).append ('\n');
        sb.append ("Sample Length: ").append (this.sampleLength).append ('\n');
        sb.append ("Play Start: ").append (this.playStart).append ('\n');
        sb.append ("Loop Mode: ").append (this.loopMode).append ('\n');
        sb.append ("Loop Start: ").append (this.loopStart).append ('\n');
        sb.append ("Loop End: ").append (this.loopEnd).append ('\n');
        sb.append ("Loop Tuning: ").append (this.loopTune).append ('\n');
        sb.append ("Coarse Tune: ").append (this.getCoarseTune ()).append (" (").append (this.coarseTune).append (")\n");
        sb.append ("Fine Tune: ").append (this.getFineTune ()).append (" (").append (this.fineTune).append (")\n");
        sb.append ("Level: ").append (this.level).append ('\n');
        sb.append ("Panning: ").append (this.getPanning ()).append (" (").append (this.panning).append (")\n\n");
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
     * Get the panning of the sample.
     *
     * @return The panning in the range of [1..127] which relates to [-63 to 63]
     */
    public int getPanning ()
    {
        return this.panning;
    }


    /**
     * Set the panning of the sample.
     *
     * @param panning The panning in the range of [1..127] which relates to [-63 to 63]
     */
    public void setPanning (final int panning)
    {
        this.panning = Math.clamp (panning, 1, 127);
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
     * Get the loop start.
     *
     * @return The start of the loop in sample frames
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Set the loop start.
     *
     * @param loopStart The start of the loop in sample frames
     */
    public void setLoopStart (final int loopStart)
    {
        this.loopStart = loopStart;
    }


    /**
     * Get the end of the sample loop.
     *
     * @return The end in sample frames
     */
    public int getLoopEnd ()
    {
        return this.loopEnd;
    }


    /**
     * Set the end of the sample loop.
     *
     * @param loopEnd The end in sample frames
     */
    public void setLoopEnd (final int loopEnd)
    {
        this.loopEnd = loopEnd;
    }


    /**
     * Get the number of the sample.
     *
     * @return The number, 1-based
     */
    public int getNumber ()
    {
        return this.sampleNumber;
    }


    /**
     * Set the number of the sample.
     *
     * @param number The numer, 1-based
     */
    public void setNumber (final int number)
    {
        this.sampleNumber = number;
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
