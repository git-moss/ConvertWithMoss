// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a EXS24 group.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Group extends EXS24Object
{
    int     volume                    = 0;
    int     pan                       = 0;
    int     polyphony                 = 0;    // = Max
    int     exclusive                 = 0;
    int     minVelocity               = 0;
    int     maxVelocity               = 127;
    int     sampleSelectRandomOffset  = 0;
    int     releaseTriggerTime        = 0;
    int     velocityRangExFade        = 0;
    int     velocityRangExFadeType    = 0;
    int     keyrangExFadeType         = 0;
    int     keyrangExFade             = 0;
    int     enableByTempoLow          = 80;
    int     enableByTempoHigh         = 140;
    int     cutoffOffset              = 0;
    int     resoOffset                = 0;
    int     env1AttackOffset          = 0;
    int     env1DecayOffset           = 0;
    int     env1SustainOffset         = 0;
    int     env1ReleaseOffset         = 0;
    boolean releaseTrigger            = false;
    int     output                    = 0;
    int     enableByNoteValue         = 0;
    int     roundRobinGroupPos        = -1;
    int     enableByType              = 0;
    int     enableByControlValue      = 0;
    int     enableByControlLow        = 0;
    int     enableByControlHigh       = 0;
    int     startNote                 = 0;
    int     endNote                   = 127;
    int     enableByMidiChannel       = 0;
    int     enableByArticulationValue = 0;
    int     enableByBenderLow         = 0;
    int     enableByBenderHigh        = 0;
    int     env1HoldOffset            = 0;
    int     env2AttackOffset          = 0;
    int     env2DecayOffset           = 0;
    int     env2SustainOffset         = 0;
    int     env2ReleaseOffset         = 0;
    int     env2HoldOffset            = 0;
    int     env1DelayOffset           = 0;
    int     env2DelayOffset           = 0;

    boolean mute                      = false;
    boolean releaseTriggerDecay       = false;
    boolean fixedSampleSelect         = false;

    boolean enableByNote              = false;
    boolean enableByRoundRobin        = false;
    boolean enableByControl           = false;
    boolean enableByBend              = false;
    boolean enableByChannel           = false;
    boolean enableByArticulation      = false;
    boolean enablebyTempo             = false;


    /**
     * Default constructor.
     */
    public EXS24Group ()
    {
        super (EXS24Block.TYPE_GROUP);
    }


    /**
     * Constructor.
     *
     * @param block The block to read
     * @throws IOException Could not read the block
     */
    public EXS24Group (final EXS24Block block) throws IOException
    {
        this ();
        this.read (block);
    }


    /** {@inheritDoc} */
    @Override
    protected void read (final InputStream in, final boolean isBigEndian) throws IOException
    {
        this.volume = in.read ();
        this.pan = in.read ();
        this.polyphony = in.read ();
        final int options = in.read ();
        this.mute = (options & 16) > 0; // 0 = OFF, 1 = ON
        this.releaseTriggerDecay = (options & 64) > 0; // 0 = OFF, 1 = ON
        this.fixedSampleSelect = (options & 128) > 0; // 0 = OFF, 1 = ON

        this.exclusive = in.read ();
        this.minVelocity = in.read ();
        this.maxVelocity = in.read ();
        this.sampleSelectRandomOffset = in.read ();

        in.skipNBytes (8);

        this.releaseTriggerTime = StreamUtils.readUnsigned16 (in, isBigEndian);

        in.skipNBytes (14);

        this.velocityRangExFade = in.read () - 128;
        this.velocityRangExFadeType = in.read ();
        this.keyrangExFadeType = in.read ();
        this.keyrangExFade = in.read () - 128;

        in.skipNBytes (2);

        this.enableByTempoLow = in.read ();
        this.enableByTempoHigh = in.read ();

        in.skipNBytes (1);

        this.cutoffOffset = in.read ();
        in.skipNBytes (1);

        this.resoOffset = in.read ();
        in.skipNBytes (12);

        this.env1AttackOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.env1DecayOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.env1SustainOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.env1ReleaseOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        in.skipNBytes (1);

        this.releaseTrigger = in.read () > 0;
        this.output = in.read ();
        this.enableByNoteValue = in.read ();

        if (in.available () > 0)
        {
            in.skipNBytes (4);

            this.roundRobinGroupPos = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
            this.enableByType = in.read ();
            this.enableByNote = this.enableByType == 1;
            this.enableByRoundRobin = this.enableByType == 2;
            this.enableByControl = this.enableByType == 3;
            this.enableByBend = this.enableByType == 4;
            this.enableByChannel = this.enableByType == 5;
            this.enableByArticulation = this.enableByType == 6;
            this.enablebyTempo = this.enableByType == 7;

            this.enableByControlValue = in.read ();
            this.enableByControlLow = in.read ();
            this.enableByControlHigh = in.read ();
            this.startNote = in.read ();
            this.endNote = in.read ();
            this.enableByMidiChannel = in.read ();
            this.enableByArticulationValue = in.read ();
        }

        // There are files with more data which is currently not used
    }


    /** {@inheritDoc} */
    @Override
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        out.write (this.volume);
        out.write (this.pan);
        out.write (this.polyphony);
        out.write ((this.mute ? 16 : 0) | (this.releaseTriggerDecay ? 64 : 0) | (this.fixedSampleSelect ? 128 : 0));
        out.write (this.exclusive);
        out.write (this.minVelocity);
        out.write (this.maxVelocity);
        out.write (this.sampleSelectRandomOffset);
        StreamUtils.padBytes (out, 8);
        StreamUtils.writeUnsigned16 (out, this.releaseTriggerTime, isBigEndian);
        StreamUtils.padBytes (out, 14);
        out.write (this.velocityRangExFade + 128);
        out.write (this.velocityRangExFadeType);
        out.write (this.keyrangExFadeType);
        out.write (this.keyrangExFade + 128);
        StreamUtils.padBytes (out, 2);
        out.write (this.enableByTempoLow);
        out.write (this.enableByTempoHigh);
        StreamUtils.padBytes (out, 1);
        out.write (this.cutoffOffset);
        StreamUtils.padBytes (out, 1);
        out.write (this.resoOffset);
        StreamUtils.padBytes (out, 12);
        StreamUtils.writeUnsigned32 (out, this.env1AttackOffset, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.env1DecayOffset, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.env1SustainOffset, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.env1ReleaseOffset, isBigEndian);
        StreamUtils.padBytes (out, 1);
        out.write (this.releaseTrigger ? 1 : 0);
        out.write (this.output);
        out.write (this.enableByNoteValue);
    }
}
