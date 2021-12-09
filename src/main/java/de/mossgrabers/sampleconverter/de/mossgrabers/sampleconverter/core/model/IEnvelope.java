// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

/**
 * Interface to an envelope e.g. volume, filter and pitch.
 *
 * @author J&uuml();rgen Mo&szlig();graber
 */
public interface IEnvelope
{
    /**
     * The delay to start the envelope.
     *
     * @return The delay in seconds, a negative value indicates that it is not set
     */
    double getDelay ();


    /**
     * Set the delay to start the envelope.
     *
     * @param delay The delay in seconds, -1 to ignore the parameter
     */
    void setDelay (double delay);


    /**
     * The start value of the attack phase.
     *
     * @return The start value in the range of [0..1], a negative value indicates that it is not set
     */
    double getStart ();


    /**
     * Set the start value of the attack phase.
     *
     * @param start The start value in the range of [0..1], -1 to ignore the parameter
     */
    void setStart (double start);


    /**
     * Get the duration time of the attack phase of the envelope.
     *
     * @return The duration of the attack phase in seconds, a negative value indicates that it is
     *         not set
     */
    double getAttack ();


    /**
     * Set the duration time of the attack phase of the envelope.
     *
     * @param attack The duration of the attack phase in seconds, -1 to ignore the parameter
     */
    void setAttack (double attack);


    /**
     * The duration time of the hold phase of the envelope. This is the time that the maximum value
     * of the attack is held.
     *
     * @return The duration of the hold phase in seconds, a negative value indicates that it is not
     *         set
     */
    double getHold ();


    /**
     * Set the duration time of the hold phase of the envelope. This is the time that the maximum
     * value of the attack is held.
     *
     * @param hold The duration of the hold phase in seconds, -1 to ignore the parameter
     */
    void setHold (double hold);


    /**
     * The duration time of the decay phase of the envelope. This is the time that the maximum value
     * of the attack changes to the sustain value.
     *
     * @return The duration of the decay phase in seconds, a negative value indicates that it is not
     *         set
     */
    double getDecay ();


    /**
     * Set the duration time of the decay phase of the envelope. This is the time that the maximum
     * value of the attack changes to the sustain value.
     *
     * @param decay The duration of the decay phase in seconds, -1 to ignore the parameter
     */
    void setDecay (double decay);


    /**
     * The value of the sustain phase.
     *
     * @return The sustain value in the range of [0..1], a negative value indicates that it is not
     *         set
     */
    double getSustain ();


    /**
     * Set the value of the sustain phase.
     *
     * @param sustain The sustain value in the range of [0..1], -1 to ignore the parameter
     */
    void setSustain (double sustain);


    /**
     * The duration time of the release phase of the envelope. This is the time after the key was
     * released till the envelope reaches zero.
     *
     * @return The duration of the release phase in seconds, a negative value indicates that it is
     *         not set
     */
    double getRelease ();


    /**
     * Set the duration time of the release phase of the envelope. This is the time after the key
     * was released till the envelope reaches zero.
     *
     * @param release The duration of the release phase in seconds, -1 to ignore the parameter
     */
    void setRelease (double release);
}
