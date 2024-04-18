// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

/**
 * Settings for destination audio files.
 *
 * @author Jürgen Moßgraber
 */
public class DestinationAudioFormat
{
    private int []  bitResolutions = null;
    private int     maxSampleRate  = -1;
    private boolean upSample       = false;


    /**
     * Default constructor.
     */
    public DestinationAudioFormat ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param bitResolutions The allowed destination resolutions
     * @param maxSampleRate The maximum supported sample rate
     * @param upSample If true, the wave form is up-sampled if it is lower than the maximum sample
     *            rate
     */
    public DestinationAudioFormat (final int [] bitResolutions, final int maxSampleRate, final boolean upSample)
    {
        this.bitResolutions = bitResolutions;
        this.maxSampleRate = maxSampleRate;
        this.upSample = upSample;
    }


    /**
     * Get the allowed destination resolutions.
     *
     * @return The resolutions as bits (e.g. 24 bit)
     */
    public int [] getBitResolutions ()
    {
        return this.bitResolutions;
    }


    /**
     * Set the allowed destination resolutions.
     *
     * @param bitResolutions The resolutions to set
     */
    public void setBitResolutions (final int [] bitResolutions)
    {
        this.bitResolutions = bitResolutions;
    }


    /**
     * Get the maximum supported sample rate.
     *
     * @return The maximum sample rate
     */
    public int getMaxSampleRate ()
    {
        return this.maxSampleRate;
    }


    /**
     * Set the maximum supported sample rate.
     *
     * @param maxSampleRate The maximum sample rate to set
     */
    public void setMaxSampleRate (final int maxSampleRate)
    {
        this.maxSampleRate = maxSampleRate;
    }


    /**
     * If true, the wave form is up-sampled if it is lower than the maximum sample rate.
     *
     * @return True to up-sample
     */
    public boolean isUpSample ()
    {
        return this.upSample;
    }


    /**
     * Set the up-sample flag.
     *
     * @param upSample If true, up-sampling is enabled
     */
    public void setUpSample (final boolean upSample)
    {
        this.upSample = upSample;
    }
}
