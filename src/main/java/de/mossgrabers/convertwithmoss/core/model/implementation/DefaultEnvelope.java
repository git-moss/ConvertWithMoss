// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.format.TagDetector;

import java.util.HashMap;
import java.util.Map;


/**
 * Default implementation of an envelope e.g. volume, filter and pitch.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultEnvelope implements IEnvelope
{
    private static final IEnvelope              ENVELOPE_PERCUSSIVE = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, 0.003);
    private static final IEnvelope              ENVELOPE_PLUCKED    = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, 0.7);
    private static final IEnvelope              ENVELOPE_KEYS       = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, 1);
    private static final IEnvelope              ENVELOPE_PADS       = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, 4);

    private static final Map<String, IEnvelope> DEFAULT_ENVELOPES   = new HashMap<> ();
    static
    {
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_BASS, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_BELL, ENVELOPE_KEYS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_BRASS, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_CHIP, ENVELOPE_KEYS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_VOCAL, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_CHROMATIC_PERCUSSION, ENVELOPE_KEYS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_CLAP, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_DESTRUCTION, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_DRONE, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_DRUM, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_ENSEMBLE, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_FX, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_GUITAR, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_HI_HAT, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_KEYBOARD, ENVELOPE_KEYS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_KICK, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_LEAD, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_MONOSYNTH, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_ORCHESTRAL, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_ORGAN, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_PAD, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_PERCUSSION, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_PIANO, ENVELOPE_KEYS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_PIPE, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_PLUCK, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_SNARE, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_STRINGS, ENVELOPE_PADS);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_SYNTH, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_WINDS, ENVELOPE_PLUCKED);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_LOOPS, ENVELOPE_PERCUSSIVE);
        DEFAULT_ENVELOPES.put (TagDetector.CATEGORY_WORLD, ENVELOPE_KEYS);
    }

    private double delay   = -1;
    private double start   = -1;
    private double attack  = -1;
    private double hold    = -1;
    private double decay   = -1;
    private double sustain = -1;
    private double release = -1;


    /**
     * Default constructor.
     */
    public DefaultEnvelope ()
    {
        // Intentionally empty
    }


    /**
     * Default constructor.
     *
     * @param delay The delay value in seconds, -1 to ignore the parameter
     * @param start The start value in the range of [0..1], -1 to ignore the parameter
     * @param attack The attack value in seconds, -1 to ignore the parameter
     * @param hold The hold value in seconds, -1 to ignore the parameter
     * @param decay The decay value in seconds, -1 to ignore the parameter
     * @param sustain The sustain value in the range of [0..1], -1 to ignore the parameter
     * @param release The release value in seconds, -1 to ignore the parameter
     */
    public DefaultEnvelope (final double delay, final double start, final double attack, final double hold, final double decay, final double sustain, final double release)
    {
        this.delay = delay;
        this.start = start;
        this.attack = attack;
        this.hold = hold;
        this.decay = decay;
        this.sustain = sustain;
        this.release = release;
    }


    /**
     * Get a default envelope for the given category. If the category cannot be found or category is
     * null a 'plucked' envelope is returned.
     *
     * @param category A category
     * @return The envelope
     */
    public static IEnvelope getDefaultEnvelope (final String category)
    {
        if (category == null)
            return ENVELOPE_PLUCKED;
        return DEFAULT_ENVELOPES.getOrDefault (category, ENVELOPE_PLUCKED);
    }


    /** {@inheritDoc} */
    @Override
    public double getDelay ()
    {
        return this.delay;
    }


    /** {@inheritDoc} */
    @Override
    public void setDelay (final double delay)
    {
        this.delay = delay;
    }


    /** {@inheritDoc} */
    @Override
    public double getStart ()
    {
        return this.start;
    }


    /** {@inheritDoc} */
    @Override
    public void setStart (final double start)
    {
        this.start = start;
    }


    /** {@inheritDoc} */
    @Override
    public double getAttack ()
    {
        return this.attack;
    }


    /** {@inheritDoc} */
    @Override
    public void setAttack (final double attack)
    {
        this.attack = attack;
    }


    /** {@inheritDoc} */
    @Override
    public double getHold ()
    {
        return this.hold;
    }


    /** {@inheritDoc} */
    @Override
    public void setHold (final double hold)
    {
        this.hold = hold;
    }


    /** {@inheritDoc} */
    @Override
    public double getDecay ()
    {
        return this.decay;
    }


    /** {@inheritDoc} */
    @Override
    public void setDecay (final double decay)
    {
        this.decay = decay;
    }


    /** {@inheritDoc} */
    @Override
    public double getSustain ()
    {
        return this.sustain;
    }


    /** {@inheritDoc} */
    @Override
    public void setSustain (final double sustain)
    {
        this.sustain = sustain;
    }


    /** {@inheritDoc} */
    @Override
    public double getRelease ()
    {
        return this.release;
    }


    /** {@inheritDoc} */
    @Override
    public void setRelease (final double release)
    {
        this.release = release;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits (this.attack);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.decay);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.delay);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.hold);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.release);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.start);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.sustain);
        result = prime * result + (int) (temp ^ temp >>> 32);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final DefaultEnvelope other = (DefaultEnvelope) obj;
        if (Double.doubleToLongBits (this.attack) != Double.doubleToLongBits (other.attack) || Double.doubleToLongBits (this.decay) != Double.doubleToLongBits (other.decay) || Double.doubleToLongBits (this.delay) != Double.doubleToLongBits (other.delay) || Double.doubleToLongBits (this.hold) != Double.doubleToLongBits (other.hold))
            return false;
        if (Double.doubleToLongBits (this.release) != Double.doubleToLongBits (other.release) || Double.doubleToLongBits (this.start) != Double.doubleToLongBits (other.start))
            return false;
        return Double.doubleToLongBits (this.sustain) == Double.doubleToLongBits (other.sustain);
    }


    /** {@inheritDoc} */
    @Override
    public void set (final IEnvelope sourceEnvelope)
    {
        this.delay = sourceEnvelope.getDelay ();
        this.start = sourceEnvelope.getStart ();
        this.attack = sourceEnvelope.getAttack ();
        this.hold = sourceEnvelope.getHold ();
        this.decay = sourceEnvelope.getDecay ();
        this.sustain = sourceEnvelope.getSustain ();
        this.release = sourceEnvelope.getRelease ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isSet ()
    {
        return this.delay != -1 || this.start != -1 || this.attack != -1 || this.hold != -1 || this.decay != -1 || this.sustain != -1 || this.release != -1;
    }
}
