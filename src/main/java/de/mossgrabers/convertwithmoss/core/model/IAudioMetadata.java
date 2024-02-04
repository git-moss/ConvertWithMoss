// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Metadata for an audio file.
 *
 * @author Jürgen Moßgraber
 */
public interface IAudioMetadata
{
    /**
     * Get the number of channels.
     *
     * @return The number of channels.
     */
    int getChannels ();


    /**
     * Returns true if the sample is mono (otherwise stereo).
     *
     * @return True if mono
     */
    boolean isMono ();


    /**
     * The number of sample slices per second. This value is unaffected by the number of channels.
     *
     * @return The four bytes converted to an integer
     */
    int getSampleRate ();


    /**
     * The number of bits used by 1 sample.
     *
     * @return The bit resolution
     */
    int getBitResolution ();


    /**
     * Get the number of samples of 1 channel.
     *
     * @return The number of samples
     */
    int getNumberOfSamples ();
}
