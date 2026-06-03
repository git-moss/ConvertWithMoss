// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 sample parameter (48 bytes).
 *
 * @author Jürgen Moßgraber
 */
public class S770Sample
{
    private final String          sampleName;
    private final S770SamplePoint startSample;
    private final S770SamplePoint sustainLoopStart;
    private final S770SamplePoint sustainLoopEnd;
    private final S770SamplePoint releaseLoopStart;
    private final S770SamplePoint releaseLoopEnd;
    private final int             loopMode;
    private final int             sustainLoopEnable;
    private final int             sustainLoopTune;
    private final int             releaseLoopTune;
    private final int             segTop;
    private final int             segLength;
    private final int             sampleMode;
    private final int             originalKey;


    public S770Sample (final InputStream in) throws IOException
    {
        this.sampleName = StreamUtils.readAscii (in, 16);
        this.startSample = new S770SamplePoint (in);
        this.sustainLoopStart = new S770SamplePoint (in);
        this.sustainLoopEnd = new S770SamplePoint (in);
        this.releaseLoopStart = new S770SamplePoint (in);
        this.releaseLoopEnd = new S770SamplePoint (in);
        this.loopMode = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.sustainLoopEnable = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.sustainLoopTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.releaseLoopTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.segTop = StreamUtils.readUnsigned16 (in, false);
        this.segLength = StreamUtils.readUnsigned16 (in, false);
        this.sampleMode = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.originalKey = StreamUtils.readUnsigned8 (in) & 0xFF;
        in.skipNBytes (2);
    }


    public String getSampleName ()
    {
        return this.sampleName;
    }


    public S770SamplePoint getStartSample ()
    {
        return this.startSample;
    }


    public S770SamplePoint getSustainLoopStart ()
    {
        return this.sustainLoopStart;
    }


    public S770SamplePoint getSustainLoopEnd ()
    {
        return this.sustainLoopEnd;
    }


    public S770SamplePoint getReleaseLoopStart ()
    {
        return this.releaseLoopStart;
    }


    public S770SamplePoint getReleaseLoopEnd ()
    {
        return this.releaseLoopEnd;
    }


    public int getLoopMode ()
    {
        return this.loopMode;
    }


    public int getSustainLoopEnable ()
    {
        return this.sustainLoopEnable;
    }


    public int getSustainLoopTune ()
    {
        return this.sustainLoopTune;
    }


    public int getReleaseLoopTune ()
    {
        return this.releaseLoopTune;
    }


    public int getSegTop ()
    {
        return this.segTop;
    }


    public int getSegLength ()
    {
        return this.segLength;
    }


    public int getSampleMode ()
    {
        return this.sampleMode;
    }


    public int getOriginalKey ()
    {
        return this.originalKey;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770SampleParameter [\n" + "  sampleName='" + this.sampleName.trim () + "'\n" + "  startSample=" + this.startSample + "\n" + "  sustainLoopStart=" + this.sustainLoopStart + "\n" + "  sustainLoopEnd=" + this.sustainLoopEnd + "\n" + "  releaseLoopStart=" + this.releaseLoopStart + "\n" + "  releaseLoopEnd=" + this.releaseLoopEnd + "\n" + "  loopMode=" + this.loopMode + "\n" + "  sustainLoopEnable=" + this.sustainLoopEnable + "\n" + "  sustainLoopTune=" + this.sustainLoopTune + "\n" + "  releaseLoopTune=" + this.releaseLoopTune + "\n" + "  segTop=" + this.segTop + "\n" + "  segLength=" + this.segLength + "\n" + "  sampleMode=" + this.sampleMode + "\n" + "  originalKey=" + this.originalKey + "\n]";
    }
}