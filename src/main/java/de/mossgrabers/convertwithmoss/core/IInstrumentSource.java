// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * A detected source for an instrument. An instrument contains one multi-sample source and its
 * configuration like the MIDI channel.
 *
 * @author Jürgen Moßgraber
 */
public interface IInstrumentSource extends ISource
{
    /**
     * Get the multi-sample source of the instrument.
     *
     * @return The multi-sample source
     */
    IMultisampleSource getMultisampleSource ();


    /**
     * Get the MIDI channel of the instrument.
     *
     * @return The MIDI channel in the range of [0..15], -1 and all other values are considered
     *         OMNI/all
     */
    int getMidiChannel ();


    /**
     * The lower note which should limit the key-range (this note should still sound).
     * 
     * @return The note [0..127]
     */
    int getClipKeyLow ();


    /**
     * The upper note which should limit the key-range (this note should still sound).
     * 
     * @return The note [0..127]
     */
    int getClipKeyHigh ();


    /**
     * Set the lower note which should limit the key-range (this note should still sound).
     * 
     * @param clipKeyLow The note [0..127]
     */
    void setClipKeyLow (int clipKeyLow);


    /**
     * Set the upper note which should limit the key-range (this note should still sound).
     * 
     * @param clipKeyHigh The note [0..127]
     */
    void setClipKeyHigh (int clipKeyHigh);


    /**
     * Clip all samples (or remove them fully) if there are outside of the lower and upper key.
     */
    void clipKeyRange ();
}
