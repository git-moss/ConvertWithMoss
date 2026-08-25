// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emax;

/**
 * The two samplers which use this bank format. They share all of their structures and differ only in
 * how they store their audio and in how much memory they have for it.
 *
 * @author Jürgen Moßgraber
 */
public enum EmaxModel
{
    /** The Emax of 1986, which stores one companded byte per frame in 512 KB. */
    EMAX("E-mu Emax", 1, EmaxConstants.MEMORY_FRAMES_EMAX, "em1"),
    /** The Emax II of 1989, which stores 16 bit samples in 2 MB or more. */
    EMAX_2("E-mu Emax II", 2, EmaxConstants.MEMORY_FRAMES_EMAX_2, "eb2");


    private final String name;
    private final int    bytesPerFrame;
    private final int    memoryFrames;
    private final String fileEnding;


    private EmaxModel (final String name, final int bytesPerFrame, final int memoryFrames, final String fileEnding)
    {
        this.name = name;
        this.bytesPerFrame = bytesPerFrame;
        this.memoryFrames = memoryFrames;
        this.fileEnding = fileEnding;
    }


    /**
     * Get the name of the sampler.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get how many bytes of a bank one frame of audio takes.
     *
     * @return 1 for the Emax, 2 for the Emax II
     */
    public int getBytesPerFrame ()
    {
        return this.bytesPerFrame;
    }


    /**
     * Get how many frames the sample memory holds.
     *
     * @return The number of frames
     */
    public int getMemoryFrames ()
    {
        return this.memoryFrames;
    }


    /**
     * Get the file ending which is written for this sampler.
     *
     * @return The ending without the dot
     */
    public String getFileEnding ()
    {
        return this.fileEnding;
    }


    /**
     * Get the sampler which a bank belongs to. An unused sequence slot holds the number of frames of
     * the sample memory, and the 512 KB of the Emax are the smallest memory of the two.
     *
     * @param memoryFrames The number of frames which the sequence table reports
     * @return The sampler
     */
    public static EmaxModel fromMemoryFrames (final int memoryFrames)
    {
        return memoryFrames > EmaxConstants.MEMORY_FRAMES_EMAX ? EMAX_2 : EMAX;
    }
}
