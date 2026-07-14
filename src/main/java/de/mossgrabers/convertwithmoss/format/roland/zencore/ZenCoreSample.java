// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;


/**
 * A user sample of a Roland FANTOM / FANTOM-0 as described by a <i>USPa</i> record in a
 * <i>.SVZ</i>, together with the linked audio from the embedded <i>USDa</i> block.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreSample
{
    /** Loop is off, the sample plays once to its end. */
    public static final int    LOOP_MODE_ONE_SHOT = 1;

    private String             name               = "";
    private int                originalKey        = 60;
    private int                loopMode           = LOOP_MODE_ONE_SHOT;
    private int                level              = 127;
    private int                gain               = 0;
    private int                fineTune           = 0;
    private int                startPoint         = 0;
    private int                loopStart          = 0;
    private int                endPoint           = 0;
    private InMemorySampleData sampleData;


    /**
     * Get the sample name.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the sample name.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Get the original (root) key.
     *
     * @return The MIDI note of the original key
     */
    public int getOriginalKey ()
    {
        return this.originalKey;
    }


    /**
     * Set the original (root) key.
     *
     * @param originalKey The MIDI note of the original key
     */
    public void setOriginalKey (final int originalKey)
    {
        this.originalKey = originalKey;
    }


    /**
     * Get the loop mode.
     *
     * @return The loop mode ({@link #LOOP_MODE_ONE_SHOT} = no loop)
     */
    public int getLoopMode ()
    {
        return this.loopMode;
    }


    /**
     * Set the loop mode.
     *
     * @param loopMode The loop mode
     */
    public void setLoopMode (final int loopMode)
    {
        this.loopMode = loopMode;
    }


    /**
     * Get the level.
     *
     * @return The level in the range of 0..127
     */
    public int getLevel ()
    {
        return this.level;
    }


    /**
     * Set the level.
     *
     * @param level The level in the range of 0..127
     */
    public void setLevel (final int level)
    {
        this.level = level;
    }


    /**
     * Get the gain.
     *
     * @return The gain in dB (0/6/12/18)
     */
    public int getGain ()
    {
        return this.gain;
    }


    /**
     * Set the gain.
     *
     * @param gain The gain in dB
     */
    public void setGain (final int gain)
    {
        this.gain = gain;
    }


    /**
     * Get the fine tune.
     *
     * @return The fine tune in cents
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Set the fine tune.
     *
     * @param fineTune The fine tune in cents
     */
    public void setFineTune (final int fineTune)
    {
        this.fineTune = fineTune;
    }


    /**
     * Get the sample start point in frames.
     *
     * @return The start point
     */
    public int getStartPoint ()
    {
        return this.startPoint;
    }


    /**
     * Set the sample start point in frames.
     *
     * @param startPoint The start point
     */
    public void setStartPoint (final int startPoint)
    {
        this.startPoint = startPoint;
    }


    /**
     * Get the loop start point in frames.
     *
     * @return The loop start point
     */
    public int getLoopStart ()
    {
        return this.loopStart;
    }


    /**
     * Set the loop start point in frames.
     *
     * @param loopStart The loop start point
     */
    public void setLoopStart (final int loopStart)
    {
        this.loopStart = loopStart;
    }


    /**
     * Get the sample end point in frames.
     *
     * @return The end point
     */
    public int getEndPoint ()
    {
        return this.endPoint;
    }


    /**
     * Set the sample end point in frames.
     *
     * @param endPoint The end point
     */
    public void setEndPoint (final int endPoint)
    {
        this.endPoint = endPoint;
    }


    /**
     * Get the audio data.
     *
     * @return The sample data (or null if not resolved)
     */
    public InMemorySampleData getSampleData ()
    {
        return this.sampleData;
    }


    /**
     * Set the audio data.
     *
     * @param sampleData The sample data
     */
    public void setSampleData (final InMemorySampleData sampleData)
    {
        this.sampleData = sampleData;
    }
}
