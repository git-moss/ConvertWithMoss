// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a EXS24 zone.
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
        this.fineTuning = decodeTwosComplement (in.read ());
        this.pan = decodeTwosComplement (in.read ());
        this.volumeAdjust = decodeTwosComplement (in.read ());
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
        this.coarseTuning = decodeTwosComplement (in.read ());

        in.skipNBytes (1);

        this.output = in.read ();
        if ((zoneOpts & 1 << 6) == 0)
            this.output = -1;

        in.skipNBytes (5);

        this.groupIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.sampleIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        // There are files with more data (mostly about a tail part) which is currently not used
    }


    /** {@inheritDoc} */
    @Override
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        out.write ((this.oneshot ? 1 : 0) | (this.pitch ? 0 : 2) | (this.reverse ? 4 : 0) | (this.velocityRangeOn ? 8 : 0));
        out.write (this.key);
        out.write (encodeTwosComplement (this.fineTuning));
        out.write (encodeTwosComplement (this.pan));
        out.write (encodeTwosComplement (this.volumeAdjust));
        out.write (this.volumeScale);
        out.write (this.keyLow);
        out.write (this.keyHigh);
        StreamUtils.padBytes (out, 1);
        out.write (this.velocityLow);
        out.write (this.velocityHigh);
        StreamUtils.padBytes (out, 1);
        StreamUtils.writeUnsigned32 (out, this.sampleStart, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.sampleEnd, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.loopStart, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.loopEnd, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.loopCrossfade, isBigEndian);
        out.write (this.loopTune);
        out.write ((this.loopOn ? 1 : 0) | (this.loopEqualPower ? 2 : 0) | (this.loopPlayToEndOnRelease ? 4 : 0));
        out.write (this.loopDirection);
        StreamUtils.padBytes (out, 42);
        out.write (this.flexOptions);
        out.write (this.flexSpeed);
        out.write (this.tailTune);
        out.write (encodeTwosComplement (this.coarseTuning));
        StreamUtils.padBytes (out, 1);
        out.write (this.output);
        StreamUtils.padBytes (out, 5);
        StreamUtils.writeUnsigned32 (out, this.groupIndex, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.sampleIndex, isBigEndian);
    }


    private static int decodeTwosComplement (final int value)
    {
        return (value & 0x80) != 0 ? value - 256 : value;
    }


    private static int encodeTwosComplement (final int value)
    {
        return value < 0 ? value + 256 : value;
    }
}
