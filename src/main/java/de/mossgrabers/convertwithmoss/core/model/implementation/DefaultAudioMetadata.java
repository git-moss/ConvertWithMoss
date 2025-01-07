// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;


/**
 * Default implementation for audio metadata.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultAudioMetadata implements IAudioMetadata
{
    private final int channels;
    private final int sampleRate;
    private final int sampleResolution;
    private final int numberOfSamples;


    /**
     * Constructor.
     *
     * @param channels The number of audio channels
     * @param sampleRate The number of samples per second in Hertz, e.g. 44100
     * @param sampleResolution The number of bits used by 1 sample, e.g. 16
     * @param numberOfSamples The number of samples of 1 channel
     */
    public DefaultAudioMetadata (final int channels, final int sampleRate, final int sampleResolution, final int numberOfSamples)
    {
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.sampleResolution = sampleResolution;
        this.numberOfSamples = numberOfSamples;
    }


    /** {@inheritDoc} */
    @Override
    public int getChannels ()
    {
        return this.channels;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isMono ()
    {
        return this.channels == 1;
    }


    /** {@inheritDoc} */
    @Override
    public int getSampleRate ()
    {
        return this.sampleRate;
    }


    /** {@inheritDoc} */
    @Override
    public int getBitResolution ()
    {
        return this.sampleResolution;
    }


    /** {@inheritDoc} */
    @Override
    public int getNumberOfSamples ()
    {
        return this.numberOfSamples;
    }
}
