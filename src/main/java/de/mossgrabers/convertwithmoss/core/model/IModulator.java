// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to a modulator.
 *
 * @author Jürgen Moßgraber
 */
public interface IModulator
{
    /**
     * Get the modulation depth.
     *
     * @return The depth in the range of [-1..1]
     */
    double getDepth ();


    /**
     * Set the modulation depth.
     *
     * @param depth The modulation depth in the range of [-1..1]
     */
    void setDepth (double depth);


    /**
     * Get the curve which is applied to the modulation source before it is scaled by the depth. A
     * value from -1 to 1 that determines the shape of the curve. -1 is a concave (logarithmic)
     * curve, which is more sensitive at low input values, 0 is a linear curve and 1 is a convex
     * (exponential) curve, which is more sensitive at high input values.
     * <p>
     * This is primarily meaningful for velocity modulators, e.g. the modulation of the amplitude or
     * of the filter cutoff. Envelope modulators normally leave the curve at 0 since
     * {@link IEnvelope} already describes the shape of each of its segments with a dedicated slope.
     * <p>
     * Known encodings of this attribute are: Roland S7xx <code>velocityCurveType</code>, Yamaha
     * YSFC <code>levelSensKeyCurve</code>, Elektron Tonverk <code>gen_multi_velocity_curve</code>,
     * the MIDI velocity curve of the Expert Sleepers Disting EX and the soft/hard level pairs of
     * the Ensoniq EPS/ASR.
     *
     * @return The curve in the range of [-1..1], 0 is a linear curve
     */
    double getCurve ();


    /**
     * Set the curve which is applied to the modulation source before it is scaled by the depth.
     *
     * @param curve The curve in the range of [-1..1], 0 for a linear curve
     */
    void setCurve (double curve);
}
