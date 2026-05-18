package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.BEOutputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordInputStream;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.io.WordOutputStream;


public class FloppyDisk extends Struct
{
    public static final int         PATCH_COUNT       = 16;
    public static final int         TONE_COUNT        = 32;
    public static final int         SEGMENT_COUNT     = 18;

    private final SystemProgram     systemProgram     = new SystemProgram ();
    private final PatchParameter [] patchParameter    = new PatchParameter [16];
    private final FunctionParameter functionParameter = new FunctionParameter ();
    private final MidiParameter     midiParameter     = new MidiParameter ();
    private final ToneParameter []  toneParameter     = new ToneParameter [32];
    private final ToneList []       toneList          = new ToneList [32];
    private final WaveData []       waveDataA         = new WaveData [18];
    private final WaveData []       waveDataB         = new WaveData [18];


    public FloppyDisk ()
    {
        for (int i = 0; i < this.patchParameter.length; i++)
            this.patchParameter[i] = new PatchParameter ();
        for (int i = 0; i < this.toneParameter.length; i++)
            this.toneParameter[i] = new ToneParameter ();
        for (int i = 0; i < this.toneList.length; i++)
            this.toneList[i] = new ToneList ();
        for (int i = 0; i < this.waveDataA.length; i++)
            this.waveDataA[i] = new WaveData ();
        for (int i = 0; i < this.waveDataB.length; i++)
            this.waveDataB[i] = new WaveData ();
    }


    public SystemProgram getSystemProgram ()
    {
        return this.systemProgram;
    }


    public PatchParameter getPatchParameter (int i)
    {
        return this.patchParameter[i];
    }


    public FunctionParameter getFunctionParameter ()
    {
        return this.functionParameter;
    }


    public MidiParameter getMidiParameter ()
    {
        return this.midiParameter;
    }


    public ToneParameter getToneParameter (int i)
    {
        return this.toneParameter[i];
    }


    public WaveData getWaveDataA (int i)
    {
        return this.waveDataA[i];
    }


    public WaveData getWaveDataB (int i)
    {
        return this.waveDataB[i];
    }


    public void copyFrom (FloppyDisk disk)
    {
        byte [] data;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            disk.write (new BEOutputStream (out));
            out.flush ();
            data = out.toByteArray ();
        }
        catch (IOException e)
        {
            e.printStackTrace ();
            return;
        }
        try (WordInputStream in = new BEInputStream (new ByteArrayInputStream (data)))
        {
            this.read (in);
        }
        catch (IOException e)
        {
            e.printStackTrace ();
        }
    }


    @Override
    public void read (WordInputStream in) throws IOException
    {
        this.systemProgram.read (in);
        for (final PatchParameter element: this.patchParameter)
            element.read (in);
        this.functionParameter.read (in);
        this.midiParameter.read (in);
        for (final ToneParameter element: this.toneParameter)
            element.read (in);
        for (final ToneList element: this.toneList)
            element.read (in);
        for (final WaveData element: this.waveDataA)
            element.read (in);
        for (final WaveData element: this.waveDataB)
            element.read (in);
    }


    @Override
    public void write (WordOutputStream out) throws IOException
    {
        this.systemProgram.write (out);
        for (final PatchParameter element: this.patchParameter)
            element.write (out);
        this.functionParameter.write (out);
        this.midiParameter.write (out);
        for (final ToneParameter element: this.toneParameter)
            element.write (out);
        for (final ToneList element: this.toneList)
            element.write (out);
        for (final WaveData element: this.waveDataA)
            element.write (out);
        for (final WaveData element: this.waveDataB)
            element.write (out);
    }
}
