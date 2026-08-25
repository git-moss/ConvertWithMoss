// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * A detected source for an instrument. An instrument contains one multi-sample source and its
 * performance configurations like the MIDI channel.
 *
 * @author Jürgen Moßgraber
 */
public interface IInstrumentSource extends ISource
{
    /** Constant for a disabled MIDI channel. */
    int MIDI_CHANNEL_OFF  = -1;
    /** Constant for an OMNI MIDI channel (reacts on all channels). */
    int MIDI_CHANNEL_OMNI = 64;


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
     * Set the MIDI channel of the instrument.
     *
     * @param midiChannel The MIDI channel in the range of [0..15], -1 and all other values are
     *            considered OMNI/all
     */
    void setMidiChannel (int midiChannel);


    /**
     * Get the transposition of the instrument.
     *
     * @return The transposition in semi-tones
     */
    int getTranspose ();


    /**
     * Set the transposition of the instrument.
     *
     * @param transpose The transposition in semi-tones
     */
    void setTranspose (int transpose);


    /**
     * Get the tuning of the instrument.
     *
     * @return The tuning in cents in the range of [-50..50]
     */
    int getTuning ();


    /**
     * Set the tuning of the instrument.
     *
     * @param tuning The tuning in cents in the range of [-50..50]
     */
    void setTuning (int tuning);


    /**
     * Get the gain of the sample.
     *
     * @return The gain in dB, assume the range to be -Inf to 24dB
     */
    double getGain ();


    /**
     * Set the gain of the sample.
     *
     * @param gain The gain in dB, assume the range to be -Inf to 24dB
     */
    void setGain (double gain);


    /**
     * Get the panning.
     *
     * @return The panning in the range of [-1..1], -1 is full left, 0 centered and 1 full right
     */
    double getPanning ();


    /**
     * Set the panning in the range of [-1..1], -1 is full left, 0 centered and 1 full right.
     *
     * @param panning The panning
     */
    void setPanning (double panning);


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
