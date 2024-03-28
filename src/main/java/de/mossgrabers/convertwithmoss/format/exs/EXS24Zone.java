// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a EXS24 this.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Zone extends EXS24Object
{
    boolean pitch;
    boolean oneshot;
    boolean reverse;
    int     key;
    int     fineTuning;
    int     pan;
    int     volumeAdjust;
    int     volumeScale;
    int     coarseTuning;
    int     keyLow;
    int     keyHigh;
    boolean velocityRangeOn;
    int     velocityLow;
    int     velocityHigh;
    int     sampleStart;
    int     sampleEnd;
    int     loopStart;
    int     loopEnd;
    int     loopCrossfade;
    int     loopTune;
    boolean loopOn;
    boolean loopEqualPower;
    boolean loopPlayToEndOnRelease;
    int     loopDirection;
    int     flexOptions;
    int     flexSpeed;
    int     tailTune;
    int     output;
    int     groupIndex;
    int     sampleIndex;
    int     sampleFadeOut = 0;
    int     offset        = 0;


    /**
     * Constructor.
     */
    public EXS24Zone ()
    {
        super (EXS24Block.TYPE_ZONE);
    }


    /**
     * Constructor.
     * 
     * @param block The block to read
     * @throws IOException Could not read the block
     */
    public EXS24Zone (final EXS24Block block) throws IOException
    {
        this ();
        this.read (block);
    }


    /** {@inheritDoc} */
    @Override
    protected void read (final InputStream in, final boolean isBigEndian) throws IOException
    {
        final int zoneOpts = in.read ();
        this.pitch = (zoneOpts & 1 << 1) == 0;
        this.oneshot = (zoneOpts & 1 << 0) != 0;
        this.reverse = (zoneOpts & 1 << 2) != 0;
        this.velocityRangeOn = (zoneOpts & 1 << 3) != 0;

        this.key = in.read ();
        this.fineTuning = twosComplement (in.read ());
        this.pan = twosComplement (in.read ());
        this.volumeAdjust = twosComplement (in.read ());
        this.volumeScale = in.read ();
        this.keyLow = in.read ();
        this.keyHigh = in.read ();
        in.skipNBytes (1);
        this.velocityLow = in.read ();
        this.velocityHigh = in.read ();
        in.skipNBytes (1);
        this.sampleStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.sampleEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopCrossfade = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.loopTune = in.read ();

        final int loopOptions = in.read ();
        this.loopOn = (loopOptions & 1) != 0;
        this.loopEqualPower = (loopOptions & 2) != 0;
        this.loopPlayToEndOnRelease = (loopOptions & 4) != 0;

        this.loopDirection = in.read ();
        in.skipNBytes (42);
        this.flexOptions = in.read ();
        this.flexSpeed = in.read ();
        this.tailTune = in.read ();
        this.coarseTuning = twosComplement (in.read ());

        in.skipNBytes (1);

        this.output = in.read ();
        if ((zoneOpts & 1 << 6) == 0)
            this.output = -1;

        in.skipNBytes (5);

        this.groupIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.sampleIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        // There are files with more data (mostly about a tail part) which is currently not used
    }


    private static int twosComplement (final int value)
    {
        return (value & 0x80) != 0 ? value - 256 : value;
    }


    /** {@inheritDoc} */
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        // TODO

        // StreamUtils.writeUnsigned32 (out, this.env1ReleaseOffset, isBigEndian);
        // StreamUtils.padBytes (out, 1);
        // out.write (this.releaseTrigger ? 1 : 0);
    }
}
