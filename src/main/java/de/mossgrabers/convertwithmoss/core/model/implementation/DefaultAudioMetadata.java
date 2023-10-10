// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
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
    private final boolean isMono;
    private final int     sampleRate;
    private final int     bitResolution;


    /**
     * Constructor.
     *
     * @param isMono True if the sample is mono (otherwise stereo)
     * @param sampleRate The number of sample slices per second.
     * @param bitResolution The number of bits used by 1 sample.
     */
    public DefaultAudioMetadata (final boolean isMono, final int sampleRate, final int bitResolution)
    {
        this.isMono = isMono;
        this.sampleRate = sampleRate;
        this.bitResolution = bitResolution;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isMono ()
    {
        return this.isMono;
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
        return this.bitResolution;
    }
}
