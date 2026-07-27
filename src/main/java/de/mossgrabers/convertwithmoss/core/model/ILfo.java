// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * Interface to a low frequency oscillator. All times are given in seconds and the rate is given in
 * Hertz, so that the values are comparable across formats. A format which stores a tempo
 * synchronized rate needs to convert it, since the tempo is not part of a multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public interface ILfo
{
    /**
     * Get the waveform.
     *
     * @return The waveform
     */
    LfoWaveform getWaveform ();


    /**
     * Set the waveform.
     *
     * @param waveform The waveform
     */
    void setWaveform (LfoWaveform waveform);


    /**
     * Get the rate.
     *
     * @return The rate in Hertz or -1 if not set
     */
    double getRate ();


    /**
     * Set the rate.
     *
     * @param rate The rate in Hertz
     */
    void setRate (double rate);


    /**
     * Get the delay, which is the time before the modulation starts.
     *
     * @return The delay in seconds or -1 if not set
     */
    double getDelay ();


    /**
     * Set the delay, which is the time before the modulation starts.
     *
     * @param delay The delay in seconds
     */
    void setDelay (double delay);


    /**
     * Get the fade-in, which is the time it takes to reach the full modulation depth.
     *
     * @return The fade-in in seconds or -1 if not set
     */
    double getFadeIn ();


    /**
     * Set the fade-in, which is the time it takes to reach the full modulation depth.
     *
     * @param fadeIn The fade-in in seconds
     */
    void setFadeIn (double fadeIn);


    /**
     * Get the phase at which the waveform starts.
     *
     * @return The start phase in the range of [0..1] or -1 if not set
     */
    double getStartPhase ();


    /**
     * Set the phase at which the waveform starts.
     *
     * @param startPhase The start phase in the range of [0..1]
     */
    void setStartPhase (double startPhase);


    /**
     * Check if the oscillator restarts when a note is triggered. If it does not, it runs freely in
     * the background.
     *
     * @return True if it restarts on a note
     */
    boolean isKeySync ();


    /**
     * Set if the oscillator restarts when a note is triggered.
     *
     * @param isKeySync True if it restarts on a note
     */
    void setKeySync (boolean isKeySync);


    /**
     * Check if any value was set. A rate needs to be present for the oscillator to be of any use.
     *
     * @return True if a rate was set
     */
    boolean isSet ();
}
