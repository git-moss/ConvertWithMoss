// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A group in a Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Group
{
    private String                  name;
    private float                   volume;
    private float                   pan;
    private float                   tune;
    private boolean                 keyTracking;
    private boolean                 reverse;
    private boolean                 releaseTrigger;
    private boolean                 releaseTriggerNoteMonophonic;
    private boolean                 muted;
    private boolean                 soloed;
    private int                     releaseTriggerCounter;
    private int                     voiceGroupIdx;
    private int                     fxIdxAmpSplitPoint;
    private int                     interpQuality;
    private int                     midiChannel;
    private final List<InternalModulator> internalModulators = new ArrayList<> ();


    /**
     * Parse the group data.
     *
     * @param groupChunk The data to parse
     * @throws IOException Error parsing the data
     */
    public void parse (final KontaktPresetChunk groupChunk) throws IOException
    {
        final int version = groupChunk.getVersion ();
        if (version > 0x9C)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_GROUP_VERSION", Integer.toHexString (version).toUpperCase ()));

        final ByteArrayInputStream in = new ByteArrayInputStream (groupChunk.getPublicData ());

        this.name = StreamUtils.readWithLengthUTF16 (in);
        this.volume = StreamUtils.readFloatLE (in);
        this.pan = StreamUtils.readFloatLE (in);
        this.tune = StreamUtils.readFloatLE (in);
        this.keyTracking = in.read () > 0;
        this.reverse = in.read () > 0;
        this.releaseTrigger = in.read () > 0;
        this.releaseTriggerNoteMonophonic = in.read () > 0;
        this.releaseTriggerCounter = StreamUtils.readSigned32 (in, false);
        this.midiChannel = StreamUtils.readSigned16 (in, false);
        this.voiceGroupIdx = StreamUtils.readSigned32 (in, false);
        this.fxIdxAmpSplitPoint = StreamUtils.readSigned32 (in, false);
        this.muted = in.read () > 0;
        this.soloed = in.read () > 0;
        this.interpQuality = StreamUtils.readSigned32 (in, false);

        this.parseModulators (groupChunk.getChildren ());
    }


    private void parseModulators (final List<KontaktPresetChunk> children) throws IOException
    {
        for (final KontaktPresetChunk childChunk: children)
            switch (childChunk.getId ())
            {
                case KontaktPresetChunkID.PARAMETER_ARRAY_16:
                    this.parseEnvelopes (childChunk.getChildren ());
                    break;

                case KontaktPresetChunkID.PARAMETER_ARRAY_32:
                    // TODO handle the pitch bend
                    break;

                default:
                    // Not used
                    break;
            }
    }


    private void parseEnvelopes (final List<KontaktPresetChunk> children) throws IOException
    {
        for (final KontaktPresetChunk childChunk: children)
        {
            final int id = childChunk.getId ();
            if (id == KontaktPresetChunkID.PAR_INTERNAL_MOD || id == KontaktPresetChunkID.PAR_MOD_BASE)
            {
                final InternalModulator internalModulator = new InternalModulator ();
                internalModulator.read (childChunk);
                this.internalModulators.add (internalModulator);
            }
        }
    }


    /**
     * Create a public data block from the group data.
     *
     * @param version The version of the group structure
     * @return The created data
     * @throws IOException Error creating the data
     */
    public byte [] create (final int version) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        StreamUtils.writeWithLengthUTF16 (out, this.name);
        StreamUtils.writeFloatLE (out, this.volume);
        StreamUtils.writeFloatLE (out, this.pan);
        StreamUtils.writeFloatLE (out, this.tune);
        out.write (this.keyTracking ? 1 : 0);
        out.write (this.reverse ? 1 : 0);
        out.write (this.releaseTrigger ? 1 : 0);
        out.write (this.releaseTriggerNoteMonophonic ? 1 : 0);
        StreamUtils.writeSigned32 (out, this.releaseTriggerCounter, false);
        StreamUtils.writeSigned16 (out, this.midiChannel, false);
        StreamUtils.writeSigned32 (out, this.voiceGroupIdx, false);
        StreamUtils.writeSigned32 (out, this.fxIdxAmpSplitPoint, false);
        out.write (this.muted ? 1 : 0);
        out.write (this.soloed ? 1 : 0);
        StreamUtils.writeSigned32 (out, this.interpQuality, false);

        return out.toByteArray ();
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
     * Get the panning of the group.
     *
     * @return The panning, see Zone
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
        return this.releaseTriggerCounter;
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


    /**
     * Get the internal modulators.
     *
     * @return The modulators
     */
    public List<InternalModulator> getInternalModulators ()
    {
        return this.internalModulators;
    }
}
