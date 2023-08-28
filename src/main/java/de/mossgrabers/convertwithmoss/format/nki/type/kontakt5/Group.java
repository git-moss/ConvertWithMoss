// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * A group in a Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Group
{
    private String  name;
    private float   volume;
    private float   pan;
    private float   tune;
    private boolean keyTracking;
    private boolean reverse;
    private boolean releaseTrigger;
    private boolean releaseTriggerNoteMonophonic;
    private boolean muted;
    private boolean soloed;
    private int     rlsTrigCounter;
    private int     voiceGroupIdx;
    private int     fxIdxAmpSplitPoint;
    private int     interpQuality;
    private int     midiChannel;


    /**
     * Parse the group data.
     *
     * @param data The data to parse
     * @param version The version of the group structure
     * @throws IOException Error parsing the data
     */
    public void parse (final byte [] data, final int version) throws IOException
    {
        if (version > 0x9A)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_ZONE_VERSION", Integer.toHexString (version).toUpperCase ()));

        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        this.name = StreamUtils.readWithLengthUTF16 (in);
        this.volume = StreamUtils.readFloatLE (in);
        this.pan = StreamUtils.readFloatLE (in);
        this.tune = StreamUtils.readFloatLE (in);
        this.keyTracking = in.read () > 0;
        this.reverse = in.read () > 0;
        this.releaseTrigger = in.read () > 0;
        this.releaseTriggerNoteMonophonic = in.read () > 0;
        this.rlsTrigCounter = StreamUtils.readUnsigned32 (in, false);
        this.midiChannel = StreamUtils.readSigned16 (in, false);
        this.voiceGroupIdx = StreamUtils.readUnsigned32 (in, false);
        this.fxIdxAmpSplitPoint = StreamUtils.readUnsigned32 (in, false);
        this.muted = in.read () > 0;
        this.soloed = in.read () > 0;
        this.interpQuality = StreamUtils.readUnsigned32 (in, false);
    }


    /**
     * Get the name of the group.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the volume of the group.
     *
     * @return The volume, see Zone
     */
    public float getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the panorama of the group.
     *
     * @return The panorama, see Zone
     */
    public float getPan ()
    {
        return this.pan;
    }


    /**
     * Get the tuning of the group.
     *
     * @return The tuning, see Zone
     */
    public float getTune ()
    {
        return this.tune;
    }


    /**
     * Is key tracking enabled?
     *
     * @return True if enabled
     */
    public boolean isKeyTracking ()
    {
        return this.keyTracking;
    }


    /**
     * Is reverse playback enabled.
     *
     * @return True if enabled
     */
    public boolean isReverse ()
    {
        return this.reverse;
    }


    /**
     * Is triggered on release?
     *
     * @return True if triggered on release
     */
    public boolean isReleaseTrigger ()
    {
        return this.releaseTrigger;
    }


    /**
     * Is the release trigger monophonic?
     *
     * @return True if monophonic
     */
    public boolean isReleaseTriggerNoteMonophonic ()
    {
        return this.releaseTriggerNoteMonophonic;
    }


    /**
     * Is the group muted?
     *
     * @return True if muted
     */
    public boolean isMuted ()
    {
        return this.muted;
    }


    /**
     * Is the group soloed?
     *
     * @return True if soloed
     */
    public boolean isSoloed ()
    {
        return this.soloed;
    }


    /**
     * Value of the release trigger counter.
     *
     * @return The value
     */
    public int getRlsTrigCounter ()
    {
        return this.rlsTrigCounter;
    }


    /**
     * The index of the voice group.
     *
     * @return The voice group index, -1 if not set
     */
    public int getVoiceGroupIdx ()
    {
        return this.voiceGroupIdx;
    }


    /**
     * Get the FX index amplitude split point.
     *
     * @return The split point
     */
    public int getFxIdxAmpSplitPoint ()
    {
        return this.fxIdxAmpSplitPoint;
    }


    /**
     * Get the interpolation quality.
     *
     * @return The interpolation quality
     */
    public int getInterpQuality ()
    {
        return this.interpQuality;
    }


    /**
     * Get the MIDI channel.
     *
     * @return The MIDI channel, -1 if not set
     */
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }
}
