package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEOutputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.Endianess;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class ToneParameter extends Struct
{
    public static final byte ORG              = 0;
    public static final byte SUB              = 1;
    public static final byte FREQ_30k         = 0;
    public static final byte FREQ_15k         = 1;
    public static final byte WAVEBANK_A       = 0;
    public static final byte WAVEBANK_B       = 1;
    public static final byte LOOP_FORWARD     = 0;
    public static final byte LOOP_ALTERNATING = 1;
    public static final byte LOOP_ONESHOT     = 2;
    public static final byte LOOP_REVERSE     = 3;

    private final RawString  toneName         = new RawString (8);
    private byte             outputAssign;
    private byte             sourceTone;
    private byte             orgSubTone;
    private byte             samplingFrequency;
    private byte             origKeyNumber;
    private byte             waveBank;
    private byte             waveSegmentTop;
    private byte             waveSegmentLength;
    private final byte []    startPoint       = new byte [3];
    private final byte []    endPoint         = new byte [3];
    private final byte []    loopPoint        = new byte [3];
    private byte             loopMode;
    private byte             tvaLfoDepth;
    private byte             lfoRate;
    private byte             lfoSync;
    private byte             lfoDelay;
    private byte             lfoMode;
    private byte             oscLfoDepth;
    private byte             lfoPolarity;
    private byte             lfoOffset;
    private byte             transpose;
    private byte             fineTune;
    private byte             tvfCutOff;
    private byte             tvfResonance;
    private byte             tvfKeyFollow;
    private byte             tvfLfoDepth;
    private byte             tvfEgDepth;
    private byte             tvfEgPolarity;
    private byte             tvfLevelCurve;
    private byte             tvfKeyRateFollow;
    private byte             tvfVelocityRateFollow;
    private byte             tvfZoom;
    private byte             tvfSwitch;
    private byte             benderSwitch;
    private byte             tvaEnvSustainPoint;
    private byte             tvaEnvEndPoint;
    private byte             tvaEnvLevel1;
    private byte             tvaEnvRate1;
    private byte             tvaEnvLevel2;
    private byte             tvaEnvRate2;
    private byte             tvaEnvLevel3;
    private byte             tvaEnvRate3;
    private byte             tvaEnvLevel4;
    private byte             tvaEnvRate4;
    private byte             tvaEnvLevel5;
    private byte             tvaEnvRate5;
    private byte             tvaEnvLevel6;
    private byte             tvaEnvRate6;
    private byte             tvaEnvLevel7;
    private byte             tvaEnvRate7;
    private byte             tvaEnvLevel8;
    private byte             tvaEnvRate8;
    private byte             tvaEnvKeyRate;
    private byte             level;
    private byte             envVelRate;
    private byte             recThreshold;
    private byte             recPreTrigger;
    private byte             recSamplingFrequency;
    private final byte []    recStartPoint    = new byte [3];
    private final byte []    recEndPoint      = new byte [3];
    private final byte []    recLoopPoint     = new byte [3];
    private byte             zoomT;
    private byte             zoomL;
    private byte             copySource;
    private byte             loopTune;
    private byte             tvaLevelCurve;
    private final byte []    loopLength       = new byte [3];
    private byte             pitchFollow;
    private byte             tvaZoom;
    private byte             tvfEnvSustainPoint;
    private byte             tvfEnvEndPoint;
    private byte             tvfEnvLevel1;
    private byte             tvfEnvRate1;
    private byte             tvfEnvLevel2;
    private byte             tvfEnvRate2;
    private byte             tvfEnvLevel3;
    private byte             tvfEnvRate3;
    private byte             tvfEnvLevel4;
    private byte             tvfEnvRate4;
    private byte             tvfEnvLevel5;
    private byte             tvfEnvRate5;
    private byte             tvfEnvLevel6;
    private byte             tvfEnvRate6;
    private byte             tvfEnvLevel7;
    private byte             tvfEnvRate7;
    private byte             tvfEnvLevel8;
    private byte             tvfEnvRate8;
    private byte             afterTouchSwitch;


    public String getName ()
    {
        return this.toneName.get ();
    }


    public void setName (String name)
    {
        this.toneName.set (name);
    }


    public byte getOutputAssign ()
    {
        return this.outputAssign;
    }


    public void setOutputAssign (byte outputAssign)
    {
        if (outputAssign < 0 || outputAssign > 7)
            throw new IllegalArgumentException ("invalid output assign");
        this.outputAssign = outputAssign;
    }


    public byte getSourceTone ()
    {
        return this.sourceTone;
    }


    public void setSourceTone (byte sourceTone)
    {
        if (sourceTone < 0 || sourceTone > 31)
            throw new IllegalArgumentException ("invalid source tone");
        this.sourceTone = sourceTone;
    }


    public byte getOrigSubTone ()
    {
        return this.orgSubTone;
    }


    public void setOrigSubTone (byte orgSubTone)
    {
        if (orgSubTone < 0 || orgSubTone > 1)
            throw new IllegalArgumentException ("invalid org/sub tone");
        this.orgSubTone = orgSubTone;
    }


    public byte getSamplingFrequency ()
    {
        return this.samplingFrequency;
    }


    public void setSamplingFrequency (byte samplingFrequency)
    {
        if (samplingFrequency < 0 || samplingFrequency > 1)
            throw new IllegalArgumentException ("invalid sampling frequency");
        this.samplingFrequency = samplingFrequency;
    }


    public byte getOrigKeyNumber ()
    {
        return this.origKeyNumber;
    }


    public void setOrigKeyNumber (byte origKeyNumber)
    {
        if (origKeyNumber < 11 || origKeyNumber > 120)
            throw new IllegalArgumentException ("invalid orig key number");
        this.origKeyNumber = origKeyNumber;
    }


    public byte getWaveBank ()
    {
        return this.waveBank;
    }


    public void setWaveBank (byte waveBank)
    {
        if (waveBank < 0 || waveBank > 1)
            throw new IllegalArgumentException ("invalid wave bank");
        this.waveBank = waveBank;
    }


    public byte getWaveSegmentTop ()
    {
        return this.waveSegmentTop;
    }


    public void setWaveSegmentTop (byte waveSegmentTop)
    {
        if (waveSegmentTop < 0 || waveSegmentTop > 17)
            throw new IllegalArgumentException ("invalid wave segment top");
        this.waveSegmentTop = waveSegmentTop;
    }


    public byte getWaveSegmentLength ()
    {
        return this.waveSegmentLength;
    }


    public void setWaveSegmentLength (byte waveSegmentLength)
    {
        if (waveSegmentLength < 0 || waveSegmentLength > 18)
            throw new IllegalArgumentException ("invalid wave segment length");
        this.waveSegmentLength = waveSegmentLength;
    }


    public int getStartPoint ()
    {
        return Endianess.get24bitBE (this.startPoint);
    }


    public void setStartPoint (int startPoint)
    {
        if (startPoint < 0 || startPoint > 221180)
            throw new IllegalArgumentException ("invalid start point");
        Endianess.set24bitBE (this.startPoint, startPoint);
    }


    public int getEndPoint ()
    {
        return Endianess.get24bitBE (this.endPoint);
    }


    public void setEndPoint (int endPoint)
    {
        if (endPoint < 4 || endPoint > 221184)
            throw new IllegalArgumentException ("invalid end point");
        Endianess.set24bitBE (this.endPoint, endPoint);
    }


    public int getLoopPoint ()
    {
        return Endianess.get24bitBE (this.loopPoint);
    }


    public void setLoopPoint (int loopPoint)
    {
        if (loopPoint < 0 || loopPoint > 221184)
            throw new IllegalArgumentException ("invalid loop point");
        Endianess.set24bitBE (this.loopPoint, loopPoint);
    }


    public byte getLoopMode ()
    {
        return this.loopMode;
    }


    public void setLoopMode (byte loopMode)
    {
        if (loopMode < 0 || loopMode > 3)
            throw new IllegalArgumentException ("invalid loop mode");
        this.loopMode = loopMode;
    }


    public byte getTvaLfoDepth ()
    {
        return this.tvaLfoDepth;
    }


    public void setTvaLfoDepth (byte tvaLfoDepth)
    {
        if (tvaLfoDepth < 0)
            throw new IllegalArgumentException ("invalid TVA LFO depth");
        this.tvaLfoDepth = tvaLfoDepth;
    }


    public byte getLfoRate ()
    {
        return this.lfoRate;
    }


    public void setLfoRate (byte lfoRate)
    {
        if (lfoRate < 0 || lfoRate > 1)
            throw new IllegalArgumentException ("invalid LFO rate");
        this.lfoRate = lfoRate;
    }


    public byte getLfoMode ()
    {
        return this.lfoMode;
    }


    public void setLfoMode (byte lfoMode)
    {
        if (lfoMode < 0 || lfoMode > 1)
            throw new IllegalArgumentException ("invalid LFO mode");
        this.lfoMode = lfoMode;
    }


    public byte getOscLfoDepth ()
    {
        return this.oscLfoDepth;
    }


    public void setOscLfoDepth (byte oscLfoDepth)
    {
        if (oscLfoDepth < 0)
            throw new IllegalArgumentException ("invalid OSC LFO depth");
        this.oscLfoDepth = oscLfoDepth;
    }


    public byte getLfoPolarity ()
    {
        return this.lfoPolarity;
    }


    public void setLfoPolarity (byte lfoPolarity)
    {
        if (lfoPolarity < 0 || lfoPolarity > 1)
            throw new IllegalArgumentException ("invalid LFO polarity");
        this.lfoPolarity = lfoPolarity;
    }


    public byte getLfoOffset ()
    {
        return this.lfoOffset;
    }


    public void setLfoOffset (byte lfoOffset)
    {
        if (lfoOffset < 0)
            throw new IllegalArgumentException ("invalid LFO offset");
        this.lfoOffset = lfoOffset;
    }


    public byte getTranspose ()
    {
        return this.transpose;
    }


    public void setTranspose (byte transpose)
    {
        if (transpose < 0)
            throw new IllegalArgumentException ("invalid tranpsose");
        this.transpose = transpose;
    }


    public byte getFineTune ()
    {
        return (byte) (this.fineTune << 1 >> 1);
    }


    public void setFineTune (byte fineTune)
    {
        if (fineTune < -64 || fineTune > 63)
            throw new IllegalArgumentException ("invalid fine tune");
        this.fineTune = fineTune;
    }


    public byte getTvfCutoff ()
    {
        return this.tvfCutOff;
    }


    public void setTvfCutoff (byte tvfCutoff)
    {
        if (tvfCutoff < 0)
            throw new IllegalArgumentException ("invalid TVF cutoff");
        this.tvfCutOff = tvfCutoff;
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.toneName.read (in);
        this.outputAssign = in.read8bit ();
        this.sourceTone = in.read8bit ();
        this.orgSubTone = in.read8bit ();
        this.samplingFrequency = in.read8bit ();
        this.origKeyNumber = in.read8bit ();
        this.waveBank = in.read8bit ();
        this.waveSegmentTop = in.read8bit ();
        this.waveSegmentLength = in.read8bit ();
        in.read (this.startPoint);
        in.read (this.endPoint);
        in.read (this.loopPoint);
        this.loopMode = in.read8bit ();
        this.tvaLfoDepth = in.read8bit ();
        in.skip (1);
        this.lfoRate = in.read8bit ();
        this.lfoSync = in.read8bit ();
        this.lfoDelay = in.read8bit ();
        in.skip (1);
        this.lfoMode = in.read8bit ();
        this.oscLfoDepth = in.read8bit ();
        this.lfoPolarity = in.read8bit ();
        this.lfoOffset = in.read8bit ();
        this.transpose = in.read8bit ();
        this.fineTune = in.read8bit ();
        this.tvfCutOff = in.read8bit ();
        this.tvfResonance = in.read8bit ();
        this.tvfKeyFollow = in.read8bit ();
        in.skip (1);
        this.tvfLfoDepth = in.read8bit ();
        this.tvfEgDepth = in.read8bit ();
        this.tvfEgPolarity = in.read8bit ();
        this.tvfLevelCurve = in.read8bit ();
        this.tvfKeyRateFollow = in.read8bit ();
        this.tvfVelocityRateFollow = in.read8bit ();
        this.tvfZoom = in.read8bit ();
        this.tvfSwitch = in.read8bit ();
        this.benderSwitch = in.read8bit ();
        this.tvaEnvSustainPoint = in.read8bit ();
        this.tvaEnvEndPoint = in.read8bit ();
        this.tvaEnvLevel1 = in.read8bit ();
        this.tvaEnvRate1 = in.read8bit ();
        this.tvaEnvLevel2 = in.read8bit ();
        this.tvaEnvRate2 = in.read8bit ();
        this.tvaEnvLevel3 = in.read8bit ();
        this.tvaEnvRate3 = in.read8bit ();
        this.tvaEnvLevel4 = in.read8bit ();
        this.tvaEnvRate4 = in.read8bit ();
        this.tvaEnvLevel5 = in.read8bit ();
        this.tvaEnvRate5 = in.read8bit ();
        this.tvaEnvLevel6 = in.read8bit ();
        this.tvaEnvRate6 = in.read8bit ();
        this.tvaEnvLevel7 = in.read8bit ();
        this.tvaEnvRate7 = in.read8bit ();
        this.tvaEnvLevel8 = in.read8bit ();
        this.tvaEnvRate8 = in.read8bit ();
        in.skip (1);
        this.tvaEnvKeyRate = in.read8bit ();
        this.level = in.read8bit ();
        this.envVelRate = in.read8bit ();
        this.recThreshold = in.read8bit ();
        this.recPreTrigger = in.read8bit ();
        this.recSamplingFrequency = in.read8bit ();
        in.read (this.recStartPoint);
        in.read (this.recEndPoint);
        in.read (this.recLoopPoint);
        this.zoomT = in.read8bit ();
        this.zoomL = in.read8bit ();
        this.copySource = in.read8bit ();
        this.loopTune = in.read8bit ();
        this.tvaLevelCurve = in.read8bit ();
        in.skip (12);
        in.read (this.loopLength);
        this.pitchFollow = in.read8bit ();
        this.tvaZoom = in.read8bit ();
        this.tvfEnvSustainPoint = in.read8bit ();
        this.tvfEnvEndPoint = in.read8bit ();
        this.tvfEnvLevel1 = in.read8bit ();
        this.tvfEnvRate1 = in.read8bit ();
        this.tvfEnvLevel2 = in.read8bit ();
        this.tvfEnvRate2 = in.read8bit ();
        this.tvfEnvLevel3 = in.read8bit ();
        this.tvfEnvRate3 = in.read8bit ();
        this.tvfEnvLevel4 = in.read8bit ();
        this.tvfEnvRate4 = in.read8bit ();
        this.tvfEnvLevel5 = in.read8bit ();
        this.tvfEnvRate5 = in.read8bit ();
        this.tvfEnvLevel6 = in.read8bit ();
        this.tvfEnvRate6 = in.read8bit ();
        this.tvfEnvLevel7 = in.read8bit ();
        this.tvfEnvRate7 = in.read8bit ();
        this.tvfEnvLevel8 = in.read8bit ();
        this.tvfEnvRate8 = in.read8bit ();
        this.afterTouchSwitch = in.read8bit ();
        in.skip (2);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.toneName.write (out);
        out.write8bit (this.outputAssign);
        out.write8bit (this.sourceTone);
        out.write8bit (this.orgSubTone);
        out.write8bit (this.samplingFrequency);
        out.write8bit (this.origKeyNumber);
        out.write8bit (this.waveBank);
        out.write8bit (this.waveSegmentTop);
        out.write8bit (this.waveSegmentLength);
        out.write (this.startPoint);
        out.write (this.endPoint);
        out.write (this.loopPoint);
        out.write8bit (this.loopMode);
        out.write8bit (this.tvaLfoDepth);
        out.write8bit ((byte) 0);
        out.write8bit (this.lfoRate);
        out.write8bit (this.lfoSync);
        out.write8bit (this.lfoDelay);
        out.write8bit ((byte) 0);
        out.write8bit (this.lfoMode);
        out.write8bit (this.oscLfoDepth);
        out.write8bit (this.lfoPolarity);
        out.write8bit (this.lfoOffset);
        out.write8bit (this.transpose);
        out.write8bit (this.fineTune);
        out.write8bit (this.tvfCutOff);
        out.write8bit (this.tvfResonance);
        out.write8bit (this.tvfKeyFollow);
        out.write8bit ((byte) 0);
        out.write8bit (this.tvfLfoDepth);
        out.write8bit (this.tvfEgDepth);
        out.write8bit (this.tvfEgPolarity);
        out.write8bit (this.tvfLevelCurve);
        out.write8bit (this.tvfKeyRateFollow);
        out.write8bit (this.tvfVelocityRateFollow);
        out.write8bit (this.tvfZoom);
        out.write8bit (this.tvfSwitch);
        out.write8bit (this.benderSwitch);
        out.write8bit (this.tvaEnvSustainPoint);
        out.write8bit (this.tvaEnvEndPoint);
        out.write8bit (this.tvaEnvLevel1);
        out.write8bit (this.tvaEnvRate1);
        out.write8bit (this.tvaEnvLevel2);
        out.write8bit (this.tvaEnvRate2);
        out.write8bit (this.tvaEnvLevel3);
        out.write8bit (this.tvaEnvRate3);
        out.write8bit (this.tvaEnvLevel4);
        out.write8bit (this.tvaEnvRate4);
        out.write8bit (this.tvaEnvLevel5);
        out.write8bit (this.tvaEnvRate5);
        out.write8bit (this.tvaEnvLevel6);
        out.write8bit (this.tvaEnvRate6);
        out.write8bit (this.tvaEnvLevel7);
        out.write8bit (this.tvaEnvRate7);
        out.write8bit (this.tvaEnvLevel8);
        out.write8bit (this.tvaEnvRate8);
        out.write8bit ((byte) 0);
        out.write8bit (this.tvaEnvKeyRate);
        out.write8bit (this.level);
        out.write8bit (this.envVelRate);
        out.write8bit (this.recThreshold);
        out.write8bit (this.recPreTrigger);
        out.write8bit (this.recSamplingFrequency);
        out.write (this.recStartPoint);
        out.write (this.recEndPoint);
        out.write (this.recLoopPoint);
        out.write8bit (this.zoomT);
        out.write8bit (this.zoomL);
        out.write8bit (this.copySource);
        out.write8bit (this.loopTune);
        out.write8bit (this.tvaLevelCurve);
        out.write ((byte) 0, 12);
        out.write (this.loopLength);
        out.write8bit (this.pitchFollow);
        out.write8bit (this.tvaZoom);
        out.write8bit (this.tvfEnvSustainPoint);
        out.write8bit (this.tvfEnvEndPoint);
        out.write8bit (this.tvfEnvLevel1);
        out.write8bit (this.tvfEnvRate1);
        out.write8bit (this.tvfEnvLevel2);
        out.write8bit (this.tvfEnvRate2);
        out.write8bit (this.tvfEnvLevel3);
        out.write8bit (this.tvfEnvRate3);
        out.write8bit (this.tvfEnvLevel4);
        out.write8bit (this.tvfEnvRate4);
        out.write8bit (this.tvfEnvLevel5);
        out.write8bit (this.tvfEnvRate5);
        out.write8bit (this.tvfEnvLevel6);
        out.write8bit (this.tvfEnvRate6);
        out.write8bit (this.tvfEnvLevel7);
        out.write8bit (this.tvfEnvRate7);
        out.write8bit (this.tvfEnvLevel8);
        out.write8bit (this.tvfEnvRate8);
        out.write8bit (this.afterTouchSwitch);
        out.write ((byte) 0, 2);
    }


    public void copyFrom (ToneParameter param)
    {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream (); WordOutputStream words = new BEOutputStream (bos))
        {
            param.write (words);
            words.flush ();
            byte [] data = bos.toByteArray ();
            try (WordInputStream in = new BEInputStream (new ByteArrayInputStream (data)))
            {
                this.read (in);
            }
        }
        catch (IOException e)
        {
            // this should never happen
            e.printStackTrace ();
        }
    }
}
