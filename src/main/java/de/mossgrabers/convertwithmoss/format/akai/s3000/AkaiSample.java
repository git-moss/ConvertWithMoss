// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s3000;

import java.io.IOException;


/**
 * An Akai sample. Note: the word 'sample' is used on two levels. On one hand it represents a single
 * measurement value (e.g. see the method getSamples()) and on the other hand it is used for a whole
 * series of such measurements (as in the class name AkaiSample).
 *
 * @author Jürgen Moßgraber
 */
public class AkaiSample extends AkaiDiskElement
{
    /** Position in the image where the sample starts. */
    private int                     imageOffset;

    private String                  name;
    private byte                    midiRootNote;

    /** -50...+50 */
    private byte                    tuneCents;
    /** -50...+50 */
    private byte                    tuneSemitones;

    private int                     startMarker;
    private int                     endMarker;

    private byte                    activeLoops;
    /** 0 = No Loop. */
    private byte                    firstActiveLoop;
    /** Loop mode: 0=in release 1=until release, 2=none, 3=play to end. */
    private byte                    loopMode;
    @SuppressWarnings("unused")
    private byte                    loopTuneOffset;

    private final AkaiSampleLoop [] loops = new AkaiSampleLoop [8];

    private int                     samplingFrequency;
    private int                     numberOfSamples;
    private short []                samples;


    /**
     * Constructor.
     * 
     * @param disk The disk to read from
     * @param volume The volume which contains the sample
     * @param dirEntry The directory entry of the sample
     * @throws IOException Could not read the sample
     */
    public AkaiSample (final AkaiDiskImage disk, final AkaiVolume volume, final AkaiDirEntry dirEntry) throws IOException
    {
        super (disk.getPos ());

        disk.setPos (volume.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE, AkaiStreamWhence.START);

        if (disk.readInt8 () != AkaiDiskElement.AKAI_SAMPLE_ID)
            throw new IOException ("This is not an Akai Sample.");

        // 0 for 22050Hz, 1 for 44100Hz - skip
        disk.readInt8 ();
        this.midiRootNote = disk.readInt8 ();

        this.name = disk.readText ();

        // Always 128 - skip
        disk.readInt8 ();
        this.activeLoops = disk.readInt8 ();
        this.firstActiveLoop = disk.readInt8 ();
        // Always 0 - skip
        disk.readInt8 ();
        this.loopMode = disk.readInt8 ();
        this.tuneCents = disk.readInt8 ();
        this.tuneSemitones = disk.readInt8 ();

        // Always 0, 8, 2, 0 - skip
        for (int i = 0; i < 4; i++)
            disk.readInt8 ();

        this.numberOfSamples = disk.readInt32 ();
        this.startMarker = disk.readInt32 ();
        this.endMarker = disk.readInt32 ();

        for (int i = 0; i < 8; i++)
            this.loops[i] = new AkaiSampleLoop (disk);

        // 0, 0, 255, 255 - skip
        disk.readInt32 ();
        this.samplingFrequency = disk.readInt16 () & 0xFFFF;
        this.loopTuneOffset = disk.readInt8 ();

        this.imageOffset = volume.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE + 150;

        disk.setPos (this.imageOffset, AkaiStreamWhence.START);
        this.samples = new short [this.numberOfSamples];
        disk.readInt16 (this.samples, this.numberOfSamples);
    }


    /**
     * Get the name of the sample.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the root note of the sample.
     * 
     * @return The root note in the range of [0..127]
     */
    public byte getMidiRootNote ()
    {
        return this.midiRootNote;
    }


    /**
     * Get the fine-tuning.
     *
     * @return The tuning by in the range of [-128..127], needs to be scaled to [-50..+50]!
     */
    public int getTuneCents ()
    {
        return this.tuneCents;
    }


    /**
     * Get the semi-tone tuning.
     *
     * @return The tuning in the range of [-50..+50]
     */
    public int getTuneSemitones ()
    {
        return this.tuneSemitones;
    }


    /**
     * Get the play-back start of the sample.
     * 
     * @return The start sample
     */
    public int getStartMarker ()
    {
        return this.startMarker;
    }


    /**
     * Get the play-back end of the sample.
     * 
     * @return The end sample
     */
    public int getEndMarker ()
    {
        return this.endMarker;
    }


    /**
     * Get the number of active loops.
     * 
     * @return The number of active loops
     */
    public byte getActiveLoops ()
    {
        return this.activeLoops;
    }


    /**
     * Get the first active loop.
     * 
     * @return The first active loop, 0 means none
     */
    public byte getFirstActiveLoop ()
    {
        return this.firstActiveLoop;
    }


    /**
     * Get the loop mode.
     * 
     * @return 0=in release 1=until release, 2=none, 3=play to end
     */
    public byte getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Get all loops.
     * 
     * @return The loops
     */
    public AkaiSampleLoop [] getLoops ()
    {
        return this.loops;
    }


    /**
     * Get the sampling frequency.
     * 
     * @return The frequency in Hertz, e.g. 44100
     */
    public int getSamplingFrequency ()
    {
        return this.samplingFrequency;
    }


    /**
     * Get the number of samples (= individual measurements).
     * 
     * @return The number of samples
     */
    public int getNumberOfSamples ()
    {
        return this.numberOfSamples;
    }


    /**
     * Get all samples.
     * 
     * @return All samples, the length matches the result of getNumberOfSamples()
     */
    public short [] getSamples ()
    {
        return this.samples;
    }
}