package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class CDVirtualFloppy extends Struct
{
    private final PatchParameter [] patchParameter    = new PatchParameter [16];
    private final FunctionParameter functionParameter = new FunctionParameter ();
    private final MidiParameter     midiParameter     = new MidiParameter ();
    private final ToneParameter []  toneParameter     = new ToneParameter [32];
    private final ToneList []       toneList          = new ToneList [32];
    private final CDWaveData []     waveDataA         = new CDWaveData [18];
    private final CDWaveData []     waveDataB         = new CDWaveData [18];


    public CDVirtualFloppy ()
    {
        for (int i = 0; i < this.patchParameter.length; i++)
            this.patchParameter[i] = new PatchParameter ();
        for (int i = 0; i < this.toneParameter.length; i++)
            this.toneParameter[i] = new ToneParameter ();
        for (int i = 0; i < this.toneList.length; i++)
            this.toneList[i] = new ToneList ();
        for (int i = 0; i < this.waveDataA.length; i++)
            this.waveDataA[i] = new CDWaveData ();
        for (int i = 0; i < this.waveDataB.length; i++)
            this.waveDataB[i] = new CDWaveData ();
    }


    public PatchParameter [] getPatchParameters ()
    {
        return Arrays.copyOf (this.patchParameter, this.patchParameter.length);
    }


    public PatchParameter getPatchParameter (int i)
    {
        return this.patchParameter[i];
    }


    public ToneParameter [] getToneParameters ()
    {
        return Arrays.copyOf (this.toneParameter, this.toneParameter.length);
    }


    public ToneParameter getToneParameter (int i)
    {
        return this.toneParameter[i];
    }


    public ToneList [] getToneLists ()
    {
        return Arrays.copyOf (this.toneList, this.toneList.length);
    }


    public ToneList getToneList (int i)
    {
        return this.toneList[i];
    }


    public CDWaveData getWaveDataA (int i)
    {
        return this.waveDataA[i];
    }


    public CDWaveData getWaveDataB (int i)
    {
        return this.waveDataB[i];
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        for (final PatchParameter element: this.patchParameter)
            element.read (in);
        this.functionParameter.read (in);
        this.midiParameter.read (in);
        for (final ToneParameter element: this.toneParameter)
            element.read (in);
        for (final ToneList element: this.toneList)
            element.read (in);
        for (final CDWaveData element: this.waveDataA)
            element.read (in);
        for (final CDWaveData element: this.waveDataB)
            element.read (in);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        for (final PatchParameter element: this.patchParameter)
            element.write (out);
        this.functionParameter.write (out);
        this.midiParameter.write (out);
        for (final ToneParameter element: this.toneParameter)
            element.write (out);
        for (final ToneList element: this.toneList)
            element.write (out);
        for (final CDWaveData element: this.waveDataA)
            element.write (out);
        for (final CDWaveData element: this.waveDataB)
            element.write (out);
    }


    public void writeFloppy (WordOutputStream out, SystemProgram systemProgram) throws IOException
    {
        systemProgram.write (out);
        for (final PatchParameter element: this.patchParameter)
            element.write (out);
        this.functionParameter.write (out);
        this.midiParameter.write (out);
        for (final ToneParameter element: this.toneParameter)
            element.write (out);
        for (final ToneList element: this.toneList)
            element.write (out);
        WaveData wave = new WaveData ();
        for (final CDWaveData element: this.waveDataA)
        {
            wave.setSamples (element.getSamples ());
            wave.write (out);
        }
        for (final CDWaveData element: this.waveDataB)
        {
            wave.setSamples (element.getSamples ());
            wave.write (out);
        }
    }
}
