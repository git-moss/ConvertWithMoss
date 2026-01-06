// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * Interface to the loop of a sample.
 *
 * @author Jürgen Moßgraber
 */
public interface ISampleLoop
{
    /**
     * Is there a loop section?
     *
     * @return The type of the loop
     */
    LoopType getType ();


    /**
     * Is there a loop section.
     *
     * @param type The type of the loop
     */
    void setType (LoopType type);


    /**
     * Get the start of the loop.
     *
     * @return The start of the loop
     */
    int getStart ();


    /**
     * Set the start of the loop.
     *
     * @param loopStart The start of the loop
     */
    void setStart (int loopStart);


    /**
     * Get the end of the loop. This is inclusive - the sample specified is played as part of the
     * loop.
     *
     * @return The end of the loop
     */
    int getEnd ();


    /**
     * Set the end of the loop. This is inclusive - the sample specified is played as part of the
     * loop.
     *
     * @param loopEnd The end of the loop
     */
    void setEnd (int loopEnd);


    /**
     * Get the loop length in samples.
     *
     * @return The loop length
     */
    int getLength ();


    /**
     * Get the loop cross-fade.
     *
     * @return The cross-fade value in the range of [0..1] which is [0..100%]
     */
    double getCrossfade ();


    /**
     * Get the loop cross-fade in samples (frames).
     *
     * @return The cross-fade value in samples
     */
    int getCrossfadeInSamples ();


    /**
     * Set the loop cross-fade which is relative to the length of the loop. 100% cross-fades the
     * whole loop. 0% creates no cross-fade.
     *
     * @param crossfade The cross-fade value in the range of [0..1] which is [0..100%]
     */
    void setCrossfade (double crossfade);


    /**
     * Set the loop cross-fade in samples (frames). Calculates the relative value from the loop
     * length which means that the loop start and end must have been already set correctly.
     *
     * @param crossfadeSamples The cross-fade value in samples
     */
    void setCrossfadeInSamples (int crossfadeSamples);


    /**
     * Set the loop cross-fade in milli-seconds. Calculates the relative value from the loop length
     * which means that the loop start and end must have been already set correctly.
     *
     * @param crossfadeSeconds The cross-fade value in seconds
     * @param sampleRate The sample rate to calculate between samples and milli-seconds
     */
    void setCrossfadeInSeconds (double crossfadeSeconds, int sampleRate);
}
