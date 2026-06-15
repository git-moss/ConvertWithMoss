// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.dls;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A DLS region.
 *
 * @author Jürgen Moßgraber
 */
public class DlsRegion
{
    /** The length of the RGNH structure. */
    private static final int        LENGTH_RGNH = 12;

    // Region header data
    private final int               keyRangeLow;
    private final int               keyRangeHigh;
    private final int               velocityRangeLow;
    private final int               velocityRangeHigh;
    private final int               options;
    private final int               keyGroup;
    private int                     layer       = 0;

    // Wave Sample data
    private int                     unityNote;
    private int                     fineTune;
    private long                    gain;
    private long                    waveOptions;
    private final List<ISampleLoop> loops       = new ArrayList<> ();

    // Wave Sample Link data
    private int                     linkOptions;
    private int                     phaseGroup;
    private long                    channelPlacement;
    private long                    tableIndex;


    /**
     * Constructor.
     *
     * @param regionHeaderChunk The region header chunk from which to initialize the region
     * @throws ParseException Could not read the data
     */
    public DlsRegion (final RawRIFFChunk regionHeaderChunk) throws ParseException
    {
        if (regionHeaderChunk.getId ().getFourCC () != DlsRiffChunkId.RGNH_ID.getFourCC ())
            throw new ParseException ("Given chunk is not a RGNH chunk.");

        if (regionHeaderChunk.getDataSize () < LENGTH_RGNH)
            throw new ParseException ("Unsound RGNH chunk.");

        this.keyRangeLow = regionHeaderChunk.getTwoBytesAsUnsignedInt (0);
        this.keyRangeHigh = regionHeaderChunk.getTwoBytesAsUnsignedInt (2);
        this.velocityRangeLow = regionHeaderChunk.getTwoBytesAsUnsignedInt (4);
        this.velocityRangeHigh = regionHeaderChunk.getTwoBytesAsUnsignedInt (6);
        this.options = regionHeaderChunk.getTwoBytesAsUnsignedInt (8);
        this.keyGroup = regionHeaderChunk.getTwoBytesAsUnsignedInt (10);
        if (regionHeaderChunk.getDataSize () > LENGTH_RGNH)
            this.layer = regionHeaderChunk.getTwoBytesAsUnsignedInt (12);
    }


    /**
     * Parse the wave sample chunk of the region.
     *
     * @param waveSampleChunk The wave sample chunk
     * @throws ParseException Could not read the chunk
     */
    public void parseWaveSampleChunk (final RawRIFFChunk waveSampleChunk) throws ParseException
    {
        if (waveSampleChunk.getId ().getFourCC () != DlsRiffChunkId.WSMP_ID.getFourCC ())
            throw new ParseException ("Given chunk is not a WSMP chunk.");

        // This is the start of the loop section
        final long size = waveSampleChunk.getFourBytesAsUnsignedLong (0);
        this.unityNote = waveSampleChunk.getTwoBytesAsUnsignedInt (4);
        this.fineTune = waveSampleChunk.getTwoBytesAsSignedInt (6);

        this.gain = waveSampleChunk.getFourBytesAsUnsignedLong (8);
        this.waveOptions = waveSampleChunk.getFourBytesAsUnsignedLong (12);
        final long numLoops = waveSampleChunk.getFourBytesAsUnsignedLong (16);

        int offset = (int) size;
        for (long i = 0; i < numLoops; i++)
        {
            final long loopStructSize = waveSampleChunk.getFourBytesAsUnsignedLong (offset);

            @SuppressWarnings("unused")
            final long loopType = waveSampleChunk.getFourBytesAsUnsignedLong (offset + 4);
            final long loopStart = waveSampleChunk.getFourBytesAsUnsignedLong (offset + 8);
            final long loopLength = waveSampleChunk.getFourBytesAsUnsignedLong (offset + 12);

            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart ((int) loopStart);
            loop.setEnd ((int) (loopStart + loopLength));
            this.loops.add (loop);

            offset += loopStructSize;
        }
    }


    /**
     * Parse the wave sample link chunk of the region.
     *
     * @param waveSampleLinkChunk The wave sample link chunk
     * @throws ParseException Could not read the chunk
     */
    public void parseWaveSampleLinkChunk (final RawRIFFChunk waveSampleLinkChunk) throws ParseException
    {
        if (waveSampleLinkChunk.getId ().getFourCC () != DlsRiffChunkId.WLNK_ID.getFourCC ())
            throw new ParseException ("Given chunk is not a WLNK chunk.");

        // This is the start of the loop section
        this.linkOptions = waveSampleLinkChunk.getTwoBytesAsUnsignedInt (0);
        this.phaseGroup = waveSampleLinkChunk.getTwoBytesAsUnsignedInt (2);
        this.channelPlacement = waveSampleLinkChunk.getFourBytesAsUnsignedLong (4);
        this.tableIndex = waveSampleLinkChunk.getFourBytesAsUnsignedLong (8);
    }


    /**
     * Get the low key-range.
     *
     * @return The key low range [0..127]
     */
    public int getKeyRangeLow ()
    {
        return this.keyRangeLow;
    }


    /**
     * Get the high key-range.
     *
     * @return The key high range [0..127]
     */
    public int getKeyRangeHigh ()
    {
        return this.keyRangeHigh;
    }


    /**
     * Get the low velocity range.
     *
     * @return The low velocity [0..127]
     */
    public int getVelocityRangeLow ()
    {
        return this.velocityRangeLow;
    }


    /**
     * Get the high velocity range.
     *
     * @return The high velocity [0..127]
     */
    public int getVelocityRangeHigh ()
    {
        return this.velocityRangeHigh;
    }


    /**
     * Get the flag options for the synthesis of this region. Current options are:
     * F_RGN_OPTION_SELFNONEXCLUSIVE (0x0001). This option specifies that if a second Note-On of the
     * same note is received by the synthesis engine, then the second note will be played as well as
     * the first. This option is off by default and the synthesis engine will force a Note-Off of
     * the prior note if a second note is received of the same value.
     *
     * @return The options
     */
    public int getOptions ()
    {
        return this.options;
    }


    /**
     * Get the key group for a drum instrument. Key group values allow multiple regions within a
     * drum instrument to belong to the same "key group." If a synthesis engine is instructed to
     * play a note with a key group setting and any other notes are currently playing with this same
     * key group, then the synthesis engine should turn off all notes with the same key group value
     * as soon as possible.
     *
     * @return The key-group, 0 = no key group, valid key groups are 1 to 15.
     */
    public int getKeyGroup ()
    {
        return this.keyGroup;
    }


    /**
     * [Optional] Indicates the layer of this region for editing purposes. For example, if a piano
     * sound and a string section are overlapped to create a piano/string pad, all the regions of
     * the piano might be labeled as layer 1, and all the regions of the string section might be
     * labeled as layer 2.
     *
     * @return The layer, 0 = no layer information, non-zero = valid layer
     */
    public int getLayer ()
    {
        return this.layer;
    }


    /**
     * Get the MIDI note which will replay the sample at original pitch.
     *
     * @return This value ranges from 0 to 127 (a value of 60 represents Middle C, as defined by the
     *         MIDI specification)
     */
    public int getUnityNote ()
    {
        return this.unityNote;
    }


    /**
     * Get the tuning offset from the unity note.
     *
     * @return The fine tune in 16 bit relative pitch (in log tuning)
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the gain to be applied to this sample.
     *
     * @return The gain in 32 bit relative gain units
     */
    public long getGain ()
    {
        return this.gain;
    }


    /**
     * Get the flag options for the digital audio sample.
     *
     * @return F_WSMP_NO_TRUNCATION (0x0001): This option specifies that a synthesis engine is not
     *         allowed to truncate the bit depth of the sample if it cannot synthesize at the bit
     *         depth of the digital audio. If the NO_TRUNCATION bit is set, the device is not
     *         allowed to truncate the data to a bit depth less than that of the original sample.
     *         Note that, if truncation is disallowed, the wave may take up more memory in the
     *         device and thus may fail to download due to memory constraints. If the bit is clear,
     *         the synthesis engine may truncate the bit depth, if required. F_WSMP_NO_COMPRESSION
     *         (0x0002): This option specifies that a synthesis engine is not allowed to use
     *         compression in its internal synthesis engine for the digital audio sample. If the
     *         NO_COMPRESSION bit is set, the device is not allowed to compress the data. Note that,
     *         if compression is disallowed, the wave may take up more memory in the device and thus
     *         may fail to download due to memory constraints. If the bit is clear, the synthesis
     *         engine may compress the digital audio samples, if required.
     */
    public long getWaveOptions ()
    {
        return this.waveOptions;
    }


    /**
     * Get the loops.
     *
     * @return the loops
     */
    public List<ISampleLoop> getLoops ()
    {
        return this.loops;
    }


    /**
     * Get the flag options for this wave link. All bits not defined must be set to 0. The define
     * flags are as follows:
     * <ul>
     * <li>F_WAVELINK_PHASE_MASTER (0x0001): Specifies that this link is the master in a group of
     * phase locked wave links.
     * <li>F_WAVELINK_MULTICHANNEL (0x0002): Indicates that the ChannelPlacement field provides the
     * channel steering information and all the channel steering data in the articulation chunk
     * should be ignored.
     * </ul>
     * 
     * @return The link options
     */
    public int getLinkOptions ()
    {
        return this.linkOptions;
    }


    /**
     * Get a group number for samples which are phase locked. All waves in a set of wave links with
     * the same group are phase locked and follow the wave in the group with the
     * F_WAVELINK_PHASE_MASTER flag set. If a wave is not a member of a phase locked group, this
     * value should be set to 0.
     * 
     * @return The phase group
     */
    public int getPhaseGroup ()
    {
        return this.phaseGroup;
    }


    /**
     * Specifies the channel placement of the sample. This is used to place mono sounds within a
     * stereo pair or for multi-track placement. Each bit position within the ChannelPlacement field
     * specifies a channel placement with bit 0 specifying a mono sample or the left channel of a
     * stereo file. Bit assignments are as follows: 0 Left (or mono), 1 Right Channel, 2 Center, 3
     * Low Frequency Energy, 4 Surround Left, 5 Surround Right, 6 Left of Center, 7 Right of Center,
     * 8 Surround Center, 9 Side Left, 10 Side Right, 11 Top, 12 Top Front Left, 13 Top Front
     * Center, 14 Top Front Right, 15 Top Rear Left, 16 Top Rear Center, 17 Top Rear Right, 18-31
     * Reserved (DO NOT USE).
     * 
     * @return The channel placement
     */
    public long getChannelPlacement ()
    {
        return this.channelPlacement;
    }


    /**
     * Get the 0 based index of the cue entry in the wave pool table.
     * 
     * @return The table index
     */
    public long getTableIndex ()
    {
        return this.tableIndex;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("  * Region: ").append (this.keyRangeLow).append ("-").append (this.keyRangeHigh).append ("/");
        sb.append (this.velocityRangeLow).append ("-").append (this.velocityRangeHigh);
        sb.append (" Options: ").append (this.options).append (" Keygroup: ").append (this.keyGroup).append (" Layer: ").append (this.layer).append ('\n');
        sb.append ("    * Unity Note: ").append (this.unityNote).append (" Fine Tune: ").append (this.fineTune).append (" Gain: ").append (this.gain).append (" Options: ").append (this.waveOptions).append ("\n");
        for (final ISampleLoop loop: this.loops)
            sb.append ("    * Loop: ").append (loop.getStart ()).append (" - ").append (loop.getEnd ()).append ("\n");
        sb.append ("    * Link Options: ").append (this.linkOptions).append (" Phase Group: ").append (this.phaseGroup).append (" Channel Placement: ").append (this.channelPlacement).append (" Table Index: ").append (this.tableIndex).append ("\n");
        return sb.toString ();
    }
}
