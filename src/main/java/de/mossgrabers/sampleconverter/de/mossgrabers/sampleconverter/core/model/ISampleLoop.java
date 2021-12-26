// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

import de.mossgrabers.sampleconverter.core.model.enumeration.LoopType;

/**
 * Interface to the loop of a sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
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
     * Get the end of the loop.
     *
     * @return The end of the loop
     */
    int getEnd ();


    /**
     * Set the end of the loop.
     *
     * @param loopEnd The end of the loop
     */
    void setEnd (int loopEnd);


    /**
     * Get the loop crossfade.
     *
     * @return The crossfade value in the range of [0..1] which is [0..100%]
     */
    double getCrossfade ();


    /**
     * Set the loop crossfade.
     *
     * @param crossfade The crossfade value in the range of [0..1] which is [0..100%]
     */
    void setCrossfade (double crossfade);
}
