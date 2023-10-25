// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A zone in a Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Zone
{
    private final int            groupIndex;
    private int                  sampleStart;
    private int                  sampleEnd;
    private int                  lowVelocity;
    private int                  highVelocity;
    private int                  lowKey;
    private int                  highKey;
    private int                  fadeLowVelocity;
    private int                  fadeHighVelocity;
    private int                  fadeLowKey;
    private int                  fadeHighKey;
    private int                  rootKey;
    private float                zoneVolume;
    private float                zonePan;
    private float                zoneTune;
    private int                  filenameId = -1;
    private int                  sampleDataType;
    private int                  sampleRate;
    private int                  numChannels;
    private int                  numFrames;
    private int                  rootNote;
    private float                tuning;
    private final List<ZoneLoop> loops      = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param groupIndex The index of the group to which the zone belongs
     */
    public Zone (final int groupIndex)
    {
        this.groupIndex = groupIndex;
    }


    /**
     * Parse the zone data.
     *
     * Known zone structure versions:
     * <ul>
     * <li>0x93: 4.2.x
     * <li>0x98: 5.3.0 - 5.6.8
     * <li>0x99: 5.8.1
     * <li>0x9A: 6.5.2 - 6.8.0
     * </ul>
     *
     * @param data The data to parse
     * @param version The version of the zone structure
     * @throws IOException Error parsing the data
     */
    public void parse (final byte [] data, final int version) throws IOException
    {
        if (version > 0x9A)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_ZONE_VERSION", Integer.toHexString (version).toUpperCase ()));

        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        this.sampleStart = (int) StreamUtils.readUnsigned32 (in, false);
        this.sampleEnd = (int) StreamUtils.readUnsigned32 (in, false);

        // Sample start modulation range
        StreamUtils.readUnsigned32 (in, false);

        this.lowVelocity = StreamUtils.readUnsigned16 (in, false);
        this.highVelocity = StreamUtils.readUnsigned16 (in, false);
        this.lowKey = StreamUtils.readUnsigned16 (in, false);
        this.highKey = StreamUtils.readUnsigned16 (in, false);
        this.fadeLowVelocity = StreamUtils.readUnsigned16 (in, false);
        this.fadeHighVelocity = StreamUtils.readUnsigned16 (in, false);
        this.fadeLowKey = StreamUtils.readUnsigned16 (in, false);
        this.fadeHighKey = StreamUtils.readUnsigned16 (in, false);
        this.rootKey = StreamUtils.readUnsigned16 (in, false);
        this.zoneVolume = StreamUtils.readFloatLE (in);
        this.zonePan = StreamUtils.readFloatLE (in);
        this.zoneTune = StreamUtils.readFloatLE (in);

        if (version == 0x9A)
        {
            // Unknown
            in.read ();
            in.read ();
            StreamUtils.readUnsigned32 (in, false);

            // Found in a NKI which only contains a script
            if (in.available () == 0)
                return;
        }

        this.filenameId = (int) StreamUtils.readUnsigned32 (in, false);
        this.sampleDataType = (int) StreamUtils.readUnsigned32 (in, false);
        this.sampleRate = (int) StreamUtils.readUnsigned32 (in, false);
        this.numChannels = in.read ();
        this.numFrames = (int) StreamUtils.readUnsigned32 (in, false);

        // Unknown
        StreamUtils.readUnsigned32 (in, false);

        if (version <= 0x93)
        {
            // Unknown
            StreamUtils.readUnsigned32 (in, false);
        }

        // Not sure what this is actually doing, some time 0, 60 or identical to rootKey
        this.rootNote = (int) StreamUtils.readUnsigned32 (in, false);
        // Seems never to be set to anything but 1.0, no idea where this might be set in Kontakt
        this.tuning = StreamUtils.readFloatLE (in);

        // Unknown
        in.read ();
        StreamUtils.readUnsigned32 (in, false);
    }


    /**
     * Get the group index.
     *
     * @return The group index
     */
    public int getGroupIndex ()
    {
        return this.groupIndex;
    }


    /**
     * Get the start of the sample.
     *
     * @return The sample start
     */
    public int getSampleStart ()
    {
        return this.sampleStart;
    }


    /**
     * Get the end of the sample.
     *
     * @return The negative offset from the end of the sample (numFrames)
     */
    public int getSampleEnd ()
    {
        return this.sampleEnd;
    }


    /**
     * Get the lower bound velocity (including the value).
     *
     * @return The velocity in the range of [1..127]
     */
    public int getLowVelocity ()
    {
        return this.lowVelocity;
    }


    /**
     * Get the upper bound velocity (including the value).
     *
     * @return The velocity in the range of [1..127]
     */
    public int getHighVelocity ()
    {
        return this.highVelocity;
    }


    /**
     * Get the lower bound key (including the value).
     *
     * @return The key in the range of [0..127]
     */
    public int getLowKey ()
    {
        return this.lowKey;
    }


    /**
     * Get the upper bound key (including the value).
     *
     * @return The key in the range of [0..127]
     */
    public int getHighKey ()
    {
        return this.highKey;
    }


    /**
     * Get the lower velocity fade value.
     *
     * @return The number of velocity steps to crossfade in the range of [0..127]
     */
    public int getFadeLowVelocity ()
    {
        return this.fadeLowVelocity;
    }


    /**
     * Get the upper velocity fade value.
     *
     * @return The number of velocity steps to crossfade in the range of [0..127]
     */
    public int getFadeHighVelocity ()
    {
        return this.fadeHighVelocity;
    }


    /**
     * Get the lower key fade value.
     *
     * @return The number of semitones to crossfade in the range of [0..127]
     */
    public int getFadeLowKey ()
    {
        return this.fadeLowKey;
    }


    /**
     * Get the upper key fade value.
     *
     * @return The number of semitones to crossfade in the range of [0..127]
     */
    public int getFadeHighKey ()
    {
        return this.fadeHighKey;
    }


    /**
     * Get the root key at which to play the sample (the assigned MIDI note).
     *
     * @return The root key of the sample in the range of [0..127]
     */
    public int getRootKey ()
    {
        return this.rootKey;
    }


    /**
     * The root note at which the sample was recorded. Ignore if set to 0.
     *
     * @return The root note in the range of [0..127]
     */
    public int getRootNote ()
    {
        return this.rootNote;
    }


    /**
     * Get the volume of the zone.
     *
     * @return The volume, 0.0dB = 1.0, 0.015625 = -36dB, 64 = +36dB
     */
    public float getZoneVolume ()
    {
        return this.zoneVolume;
    }


    /**
     * Get the panorama.
     *
     * @return The panorama in the range of [-1..1]
     */
    public float getZonePan ()
    {
        return this.zonePan;
    }


    /**
     * Get the zone tuning.
     *
     * @return The tune setting in the range of [-32..32] semitones which is returned as 2^octave
     *         which maps to the range [0.125, 8.0], 2^(-32/12) = 0.125
     */
    public float getZoneTune ()
    {
        return this.zoneTune;
    }


    /**
     * Get the index of the filename.
     *
     * @return The index into the filename list
     */
    public int getFilenameId ()
    {
        return this.filenameId;
    }


    /**
     * Get the type of the sample data.
     *
     * @return The type: 2 = 16 bit, 3 = 24 bit
     */
    public int getSampleDataType ()
    {
        return this.sampleDataType;
    }


    /**
     * The sample rate.
     *
     * @return The sample rate in Hertz, e.g. 44100
     */
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /**
     * The number of channels.
     *
     * @return The number of channels, e.g. 2
     */
    public int getNumChannels ()
    {
        return this.numChannels;
    }


    /**
     * The number of frames.
     *
     * @return The number of frames (number of samples of 1 channel)
     */
    public int getNumFrames ()
    {
        return this.numFrames;
    }


    /**
     * Get the tuning.
     *
     * @return The tuning
     */
    public float getTuning ()
    {
        return this.tuning;
    }


    /**
     * Add a loop.
     *
     * @param loop The loop to add
     */
    public void addLoop (final ZoneLoop loop)
    {
        this.loops.add (loop);
    }


    /**
     * Get the loops.
     *
     * @return The loops
     */
    public List<ZoneLoop> getLoops ()
    {
        return this.loops;
    }
}
