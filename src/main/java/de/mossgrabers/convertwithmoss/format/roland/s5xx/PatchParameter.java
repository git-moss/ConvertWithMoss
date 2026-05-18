package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEOutputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class PatchParameter extends Struct
{
    // S-50 leftovers
    private byte            modulationDepth;

    // S-550 stuff
    private final RawString patchName  = new RawString (12);
    private byte            bendRange;
    private byte            afterTouchSense;
    private byte            keyMode;
    private byte            velocitySwThreshold;
    private final byte []   toneToKey1 = new byte [109];
    private final byte []   toneToKey2 = new byte [109];
    private byte            copySource;
    private byte            octaveShift;
    private byte            outputLevel;
    private byte            detune;
    private byte            velocityMixRatio;
    private byte            afterTouchAssign;
    private byte            keyAssign;
    private byte            outputAssign;


    public String getName ()
    {
        return this.patchName.get ();
    }


    public void setName (String name)
    {
        this.patchName.set (name);
    }


    public byte getBendRange ()
    {
        return this.bendRange;
    }


    public void setBendRange (byte bendRange)
    {
        if (bendRange < 0 || bendRange > 12)
            throw new IllegalArgumentException ("invalid bend range");
        this.bendRange = bendRange;
    }


    public byte getAfterTouchSense ()
    {
        return this.afterTouchSense;
    }


    public void setAfterTouchSense (byte afterTouchSense)
    {
        if (afterTouchSense < 0)
            throw new IllegalArgumentException ("invalid after touch sense");
        this.afterTouchSense = afterTouchSense;
    }


    public byte getKeyMode ()
    {
        return this.keyMode;
    }


    public void setKeyMode (byte keyMode)
    {
        if (keyMode < 0 || keyMode > 4)
            throw new IllegalArgumentException ("invalid key mode");
        this.keyMode = keyMode;
    }


    public byte getVelocitySwitchThreshold ()
    {
        return this.velocitySwThreshold;
    }


    public void setVelocitySwitchThreshold (byte velocitySwitchThreshold)
    {
        if (velocitySwitchThreshold < 0)
            throw new IllegalArgumentException ("invalid velocity switch threshold");
        this.velocitySwThreshold = velocitySwitchThreshold;
    }


    public byte getCopySource ()
    {
        return this.copySource;
    }


    public void setCopySource (byte copySource)
    {
        if (copySource < 0 || copySource > 7)
            throw new IllegalArgumentException ("invalid copy source");
        this.copySource = copySource;
    }


    public byte getOctaveShift ()
    {
        return this.octaveShift;
    }


    public void setOctaveShift (byte octaveShift)
    {
        if (octaveShift < -2 || octaveShift > 2)
            throw new IllegalArgumentException ("invalid octave shift");
        this.octaveShift = octaveShift;
    }


    public byte getOutputLevel ()
    {
        return this.outputLevel;
    }


    public void setOutputLevel (byte outputLevel)
    {
        if (outputLevel < 0)
            throw new IllegalArgumentException ("invalid output level");
        this.outputLevel = outputLevel;
    }


    public byte getDetune ()
    {
        return this.detune;
    }


    public void setDetune (byte detune)
    {
        if (detune < -64 || detune > 63)
            throw new IllegalArgumentException ("invalid detune");
        this.detune = detune;
    }


    public byte getVelocityMixRatio ()
    {
        return this.velocityMixRatio;
    }


    public void setVelocityMixRatio (byte velocityMixRatio)
    {
        if (velocityMixRatio < 0)
            throw new IllegalArgumentException ("invalid velocity mix ratio");
        this.velocityMixRatio = velocityMixRatio;
    }


    public byte getAfterTouchAssign ()
    {
        return this.afterTouchAssign;
    }


    public void setAfterTouchAssign (byte afterTouchAssign)
    {
        if (afterTouchAssign < 0 || afterTouchAssign > 4)
            throw new IllegalArgumentException ("invalid after touch assign");
        this.afterTouchAssign = afterTouchAssign;
    }


    public byte getKeyAssign ()
    {
        return this.keyAssign;
    }


    public void setKeyAssign (byte keyAssign)
    {
        if (keyAssign < 0 || keyAssign > 1)
            throw new IllegalArgumentException ("invalid key assign");
        this.keyAssign = keyAssign;
    }


    public byte getOutputAssign ()
    {
        return this.outputAssign;
    }


    public void setOutputAssign (byte outputAssign)
    {
        if (outputAssign < 0 || outputAssign > 8)
            throw new IllegalArgumentException ("invalid output assign");
        this.outputAssign = outputAssign;
    }


    public byte getToneToKey (int ab, int key)
    {
        if (key < 0 || key > 108)
            throw new IllegalArgumentException ("invalid key");
        if (ab == 0)
            return (byte) (this.toneToKey1[key] & 0x1F);
        else if (ab == 1)
            return (byte) (this.toneToKey2[key] & 0x1F);
        else
            throw new IllegalArgumentException ("invalid table");
    }


    public void setToneToKey (int ab, int key, byte tone)
    {
        if (key < 0 || key > 108)
            throw new IllegalArgumentException ("invalid key");
        if (tone < 0 || tone >= 32)
            throw new IllegalArgumentException ("invalid tone");
        if (ab == 0)
            this.toneToKey1[key] = tone;
        else if (ab == 1)
            this.toneToKey2[key] = tone;
        else
            throw new IllegalArgumentException ("invalid table");
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.patchName.read (in);
        this.bendRange = in.read8bit ();
        in.skip (1);
        this.afterTouchSense = in.read8bit ();
        this.keyMode = in.read8bit ();
        this.velocitySwThreshold = in.read8bit ();
        in.read (this.toneToKey1);
        in.read (this.toneToKey2);
        this.copySource = in.read8bit ();
        this.octaveShift = in.read8bit ();
        this.outputLevel = in.read8bit ();
        this.modulationDepth = in.read8bit ();
        this.detune = in.read8bit ();
        this.velocityMixRatio = in.read8bit ();
        this.afterTouchAssign = in.read8bit ();
        this.keyAssign = in.read8bit ();
        this.outputAssign = in.read8bit ();
        in.skip (12);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.patchName.write (out);
        out.write8bit (this.bendRange);
        out.write8bit ((byte) 0);
        out.write8bit (this.afterTouchSense);
        out.write8bit (this.keyMode);
        out.write8bit (this.velocitySwThreshold);
        out.write (this.toneToKey1);
        out.write (this.toneToKey2);
        out.write8bit (this.copySource);
        out.write8bit (this.octaveShift);
        out.write8bit (this.outputLevel);
        out.write8bit (this.modulationDepth);
        out.write8bit (this.detune);
        out.write8bit (this.velocityMixRatio);
        out.write8bit (this.afterTouchAssign);
        out.write8bit (this.keyAssign);
        out.write8bit (this.outputAssign);
        out.write ((byte) 0, 12); // TODO: figure this out
    }


    @Override
    public String toString ()
    {
        return "PatchParameter[patchName=" + this.patchName.get () + "]";
    }


    public void copyFrom (PatchParameter param)
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
