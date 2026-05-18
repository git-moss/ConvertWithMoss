package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class FunctionParameter extends Struct
{
    // S-50 leftovers
    private byte            systemMode;
    private final byte []   multiToneNumber  = new byte [8];

    // S-550 stuff
    private byte            masterTune;
    private byte            audioTrig;
    private byte            voiceMode;
    private final byte []   multiMidiRxCh    = new byte [8];
    private final byte []   multiPatchNumber = new byte [8];
    private byte            keyboardDisplay;
    private final byte []   multiLevel       = new byte [8];
    private final RawString diskLabel        = new RawString (60);
    private byte            externalController;


    public byte getMasterTune ()
    {
        return this.masterTune;
    }


    public void setMasterTune (byte masterTune)
    {
        if (masterTune < -64 || masterTune > 63)
            throw new IllegalArgumentException ("invalid master tune");
        this.masterTune = masterTune;
    }


    public byte getAudioTrig ()
    {
        return this.audioTrig;
    }


    public void setAudioTrig (byte audioTrig)
    {
        this.audioTrig = audioTrig;
    }


    public byte getVoiceMode ()
    {
        return this.voiceMode;
    }


    public void setVoiceMode (byte voiceMode)
    {
        if (voiceMode < 0 || voiceMode > 23)
            throw new IllegalArgumentException ("invalid voice mode");
        this.voiceMode = voiceMode;
    }


    public byte [] getMultiMidiRxCh ()
    {
        return getArray (this.multiMidiRxCh);
    }


    public byte getMultiMidiRxCh (int part)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        return (byte) (this.multiMidiRxCh[part] & 0x0F);
    }


    public void setMultiMidiRxCh (byte [] multiMidiRxCh)
    {
        setArray (multiMidiRxCh, this.multiMidiRxCh);
    }


    public void setMultiMidiRxCh (int part, byte ch)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        if (ch < 0 || ch > 15)
            throw new IllegalArgumentException ("invalid multi midi rx-ch");
        this.multiMidiRxCh[part] = ch;
    }


    public byte [] getMultiPatchNumber ()
    {
        return getArray (this.multiPatchNumber);
    }


    public byte getMultiPatchNumber (int part)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        return (byte) (this.multiPatchNumber[part] & 0x1F);
    }


    public void setMultiPatchNumber (byte [] multiPatchNumber)
    {
        setArray (multiPatchNumber, this.multiPatchNumber);
    }


    public void setMultiPatchNumber (int part, byte patch)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        if (patch < 0 || patch > 31)
            throw new IllegalArgumentException ("invalid patch number");
        this.multiPatchNumber[part] = patch;
    }


    public byte getKeyboardDisplay ()
    {
        return this.keyboardDisplay;
    }


    public void setKeyboardDisplay (byte keyboardDisplay)
    {
        this.keyboardDisplay = keyboardDisplay;
    }


    public byte [] getMultiLevel ()
    {
        return getArray (this.multiLevel);
    }


    public byte getMultiLevel (int part)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        return this.multiLevel[part];
    }


    public void setMultiLevel (byte [] multiLevel)
    {
        setArray (multiLevel, this.multiLevel);
    }


    public void setMultiLevel (int part, byte level)
    {
        if (part < 0 || part > 7)
            throw new IllegalArgumentException ("invalid multi part");
        if (level < 0)
            throw new IllegalArgumentException ("invalid level");
        this.multiLevel[part] = level;
    }


    public String [] getDiskLabel ()
    {
        String decoded = DiskLabel.decode (this.diskLabel.get ());
        String [] result = new String [5];
        for (int i = 0; i < 5; i++)
            result[i] = decoded.substring (i * 12, (i + 1) * 12);
        return result;
    }


    public void setDiskLabel (String [] label)
    {
        if (label.length != 5)
            throw new IllegalArgumentException ("need 5 lines");
        for (String line: label)
            if (line.length () != 12)
                throw new IllegalArgumentException ("invalid line length");
        String decoded = String.join ("", label);
        this.diskLabel.set (DiskLabel.encode (decoded));
    }


    public byte getExternalController ()
    {
        return this.externalController;
    }


    public void setExternalController (byte externalController)
    {
        if (externalController < 0 || externalController > 2)
            throw new IllegalArgumentException ("invalid exteranl controller");
        this.externalController = externalController;
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.masterTune = in.read8bit ();
        in.skip (13);
        this.audioTrig = in.read8bit ();
        this.systemMode = in.read8bit ();
        this.voiceMode = in.read8bit ();
        in.read (this.multiMidiRxCh);
        in.read (this.multiPatchNumber);
        in.read (this.multiToneNumber);
        in.skip (1);
        this.keyboardDisplay = in.read8bit ();
        in.read (this.multiLevel);
        this.diskLabel.read (in);
        in.skip (4);
        this.externalController = in.read8bit ();
        in.skip (140);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        out.write8bit (this.masterTune);
        out.write ((byte) 0, 13);
        out.write8bit (this.audioTrig);
        out.write8bit (this.systemMode);
        out.write8bit (this.voiceMode);
        out.write (this.multiMidiRxCh);
        out.write (this.multiPatchNumber);
        out.write (this.multiToneNumber);
        out.write8bit ((byte) 0);
        out.write8bit (this.keyboardDisplay);
        out.write (this.multiLevel);
        this.diskLabel.write (out);
        out.write ((byte) 0, 4);
        out.write8bit (this.externalController);
        out.write ((byte) 0, 140);
    }
}
