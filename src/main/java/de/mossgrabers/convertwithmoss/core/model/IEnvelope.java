// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
     * Get the key tracking of the envelope times. This scales all times (rates) of the envelope
     * depending on the played key: a positive value shortens the times towards higher keys and
     * lengthens them towards lower keys, a negative value does the opposite. The times are
     * unchanged at the center key (MIDI note 60). This is different to the attack, decay and
     * release slopes, which describe the curvature of a segment and not its duration.
     * <p>
     * Known encodings of this attribute are: Akai S1000 <code>keyToDecayAndRelease</code>, Yamaha
     * YSFC <code>aegTimeKeyFollowSensitivity</code>, Ensoniq EPS/ASR <code>kbTimeScaling</code>,
     * Roland S7xx <code>envTimeKf</code>, SoundFont 2 generators 31 and 32
     * (<code>keynumToModEnvHold</code> and <code>keynumToModEnvDecay</code>) as well as 39 and 40
     * (<code>keynumToVolEnvHold</code> and <code>keynumToVolEnvDecay</code>) and Reason SXT
     * <code>ampEnvKeyToDecay</code>.
     *
     * @return The amount of scaling in the range of [-1..1], 0 means that the envelope times do not
     *         depend on the played key
     */
    double getTimeKeyTracking ();


    /**
     * Set the key tracking of the envelope times.
     *
     * @param timeKeyTracking The amount of scaling in the range of [-1..1], 0 for no scaling
     */
    void setTimeKeyTracking (double timeKeyTracking);


    /**
     * Get the velocity tracking of the envelope times. This scales all times (rates) of the
     * envelope depending on the velocity of the played note: a positive value shortens the times
     * towards higher velocities and lengthens them towards lower velocities, a negative value does
     * the opposite. The times are unchanged at the center velocity (64). This is different to the
     * attack, decay and release slopes, which describe the curvature of a segment and not its
     * duration.
     * <p>
     * Known encodings of this attribute are: Akai S1000 <code>velocityToAttack</code>, Yamaha YSFC
     * <code>aegTimeVelocitySensitivity</code>, Ensoniq EPS/ASR <code>time1VelSens</code> and Roland
     * S7xx <code>timeVelocitySensitivity</code>.
     *
     * @return The amount of scaling in the range of [-1..1], 0 means that the envelope times do not
     *         depend on the velocity
     */
    double getTimeVelocityTracking ();


    /**
     * Set the velocity tracking of the envelope times.
     *
     * @param timeVelocityTracking The amount of scaling in the range of [-1..1], 0 for no scaling
     */
    void setTimeVelocityTracking (double timeVelocityTracking);


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
