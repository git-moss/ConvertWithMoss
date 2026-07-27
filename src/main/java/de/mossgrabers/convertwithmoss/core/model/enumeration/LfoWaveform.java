// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * Different waveforms of a low frequency oscillator. Only the shapes which are present in most
 * formats are listed, a format which knows further shapes needs to map them to the closest one.
 *
 * @author Jürgen Moßgraber
 */
public enum LfoWaveform
{
    /** A sine wave. */
    SINE,
    /** A triangle wave. */
    TRIANGLE,
    /** A square wave. */
    SQUARE,
    /** A rising saw-tooth wave. */
    SAWTOOTH_UP,
    /** A falling saw-tooth wave. */
    SAWTOOTH_DOWN,
    /** A random (sample and hold) wave. */
    RANDOM,
}
