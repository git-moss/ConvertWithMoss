// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;


/**
 * An Akai sample loop.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiSampleLoop
{
    private int marker;
    private int fineLength;
    private int coarseLength;
    private int time;


    /**
     * Default constructor.
     */
    public AkaiSampleLoop ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     * 
     * @param disk The disk to read from
     * @throws IOException Could not read the loop
     */
    public AkaiSampleLoop (final AkaiS1000DiskImage disk) throws IOException
    {
        this.marker = disk.readInt32 ();
        this.fineLength = disk.readInt16 () & 0xFFFF;
        this.coarseLength = disk.readInt32 ();
        this.time = disk.readInt16 () & 0xFFFF;
    }


    /**
     * Get the end of the looped region (not the start!).
     * 
     * @return The position which marks the end of the loop region
     */
    public int getEndMarker ()
    {
        return this.marker;
    }


    /**
     * Set the end of the looped region (not the start!).
     * 
     * @param endMarker The position which marks the end of the loop region
     */
    public void setEndMarker (final int endMarker)
    {
        this.marker = endMarker;
    }


    /**
     * Get the fine value of the loop length (65536ths).
     * 
     * @return The fine value
     */
    public int getFineLength ()
    {
        return this.fineLength;
    }


    /**
     * Get the length of the loop.
     * 
     * @return The length of the loop
     */
    public int getCoarseLength ()
    {
        return this.coarseLength;
    }


    /**
     * Set the length of the loop.
     * 
     * @param length The length of the loop
     */
    public void setCoarseLength (final int length)
    {
        this.coarseLength = length;
    }


    /**
     * Get the time in milli-seconds for which the loop plays.
     * 
     * @return The value as milli-seconds or 9999=infinite
     */
    public int getTime ()
    {
        return this.time;
    }
}