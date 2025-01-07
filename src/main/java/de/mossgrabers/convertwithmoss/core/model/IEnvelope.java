// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to an envelope e.g. volume, filter and pitch. The envelope starts after the
 * <i>delay</i> phase. The <i>attack</i> phase starts at the <i>start</i> level. It ends at the
 * <i>hold<i> level. The envelope continues with the <i>hold</i>. The <i>delay</i> phase ends at the
 * <i>sustain</i> level. After key release the <i>release</i> phase moves to the <i>end</i> level
 * (normally 0).
 *
 * @author Jürgen Moßgraber
 */
public interface IEnvelope
{
    /** The maximum filter envelope depth. 12000 cents which are 10 octaves. */
    public static final int MAX_ENVELOPE_DEPTH = 12000;


    /**
     * The delay to start the envelope.
     *
     * @return The delay in seconds, a negative value indicates that it is not set
     */
    double getDelayTime ();


    /**
     * Set the delay to start the envelope.
     *
     * @param delay The delay in seconds, -1 to ignore the parameter
     */
    void setDelayTime (double delay);


    /**
     * The start value of the attack phase.
     *
     * @return The start value in the range of [0..1], a negative value indicates that it is not set
     */
    double getStartLevel ();


    /**
     * Set the start value of the attack phase.
     *
     * @param start The start value in the range of [0..1], -1 to ignore the parameter
     */
    void setStartLevel (double start);


    /**
     * Get the duration time of the attack phase of the envelope.
     *
     * @return The duration of the attack phase in seconds, a negative value indicates that it is
     *         not set
     */
    double getAttackTime ();


    /**
     * Set the duration time of the attack phase of the envelope.
     *
     * @param attack The duration of the attack phase in seconds, -1 to ignore the parameter
     */
    void setAttackTime (double attack);


    /**
     * Get the slope of the attack phase of the envelope. A value from -1 to 1 that determines the
     * shape of the curve. -1 is a logarithmic curve (fast start, slow end), 0 is a linear curve and
     * 1 is an exponential curve.
     *
     * @return The slope in the range of [-1..1]
     */
    double getAttackSlope ();


    /**
     * Set the slope of the attack phase of the envelope.
     *
     * @param slope The slope in the range of [-1..1]
     */
    void setAttackSlope (double slope);


    /**
     * Get the level of the end of the attack phase.
     *
     * @return The value in the range of [0..1], a negative value indicates that it is not set
     */
    double getHoldLevel ();


    /**
     * Set the level of the end of the attack phase.
     *
     * @param holdLevel The value in the range of [0..1], -1 to ignore the parameter
     */
    void setHoldLevel (double holdLevel);


    /**
     * The duration time of the hold phase of the envelope. This is the time that the maximum value
     * of the attack is held.
     *
     * @return The duration of the hold phase in seconds, a negative value indicates that it is not
     *         set
     */
    double getHoldTime ();


    /**
     * Set the duration time of the hold phase of the envelope. This is the time that the maximum
     * value of the attack is held.
     *
     * @param hold The duration of the hold phase in seconds, -1 to ignore the parameter
     */
    void setHoldTime (double hold);


    /**
     * The duration time of the decay phase of the envelope. This is the time that the maximum value
     * of the attack changes to the sustain value.
     *
     * @return The duration of the decay phase in seconds, a negative value indicates that it is not
     *         set
     */
    double getDecayTime ();


    /**
     * Set the duration time of the decay phase of the envelope. This is the time that the maximum
     * value of the attack changes to the sustain value.
     *
     * @param decay The duration of the decay phase in seconds, -1 to ignore the parameter
     */
    void setDecayTime (double decay);


    /**
     * Get the slope of the decay phase of the envelope. A value from -1 to 1 that determines the
     * shape of the curve. -1 is a logarithmic curve (fast start, slow end), 0 is a linear curve and
     * 1 is an exponential curve.
     *
     * @return The slope in the range of [-1..1]
     */
    double getDecaySlope ();


    /**
     * Set the slope of the decay phase of the envelope.
     *
     * @param slope The slope in the range of [-1..1]
     */
    void setDecaySlope (double slope);


    /**
     * Get the level of the sustain phase.
     *
     * @return The value in the range of [0..1], a negative value indicates that it is not set
     */
    double getSustainLevel ();


    /**
     * Set the level of the sustain phase.
     *
     * @param sustain The sustain value in the range of [0..1], -1 to ignore the parameter
     */
    void setSustainLevel (double sustain);


    /**
     * The duration time of the release phase of the envelope. This is the time after the key was
     * released till the envelope reaches zero.
     *
     * @return The duration of the release phase in seconds, a negative value indicates that it is
     *         not set
     */
    double getReleaseTime ();


    /**
     * Set the duration time of the release phase of the envelope. This is the time after the key
     * was released till the envelope reaches zero.
     *
     * @param release The duration of the release phase in seconds, -1 to ignore the parameter
     */
    void setReleaseTime (double release);


    /**
     * Get the slope of the release phase of the envelope. A value from -1 to 1 that determines the
     * shape of the curve. -1 is a logarithmic curve (fast start, slow end), 0 is a linear curve and
     * 1 is an exponential curve.
     *
     * @return The slope in the range of [-1..1]
     */
    double getReleaseSlope ();


    /**
     * Set the slope of the release phase of the envelope.
     *
     * @param slope The slope in the range of [-1..1]
     */
    void setReleaseSlope (double slope);


    /**
     * Get the level at the end of the release phase.
     *
     * @return The value in the range of [0..1], a negative value indicates that it is not set
     */
    double getEndLevel ();


    /**
     * Set the level at the end of the release phase.
     *
     * @param endLevel The value in the range of [0..1], -1 to ignore the parameter
     */
    void setEndLevel (double endLevel);


    /**
     * Copies all values from the source envelope to this envelope.
     *
     * @param sourceEnvelope The source envelope
     */
    void set (IEnvelope sourceEnvelope);


    /**
     * Returns true if at least one of the envelope attributes is not -1.
     *
     * @return True if set
     */
    boolean isSet ();
}
