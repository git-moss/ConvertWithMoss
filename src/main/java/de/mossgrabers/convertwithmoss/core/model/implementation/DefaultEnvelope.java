// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * Default implementation of an envelope e.g. volume, filter and pitch.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultEnvelope implements IEnvelope
{
    private static final IEnvelope              ENVELOPE_PERCUSSIVE = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, -1, 0.003, 0);
    private static final IEnvelope              ENVELOPE_PLUCKED    = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, -1, 0.7, 0);
    private static final IEnvelope              ENVELOPE_KEYS       = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, -1, 1, 0);
    private static final IEnvelope              ENVELOPE_PADS       = new DefaultEnvelope (-1, -1, 0, -1, -1, -1, -1, 4, 0);

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

    private double delayTime    = -1;
    private double attackTime   = -1;
    private double holdTime     = -1;
    private double decayTime    = -1;
    private double releaseTime  = -1;

    private double startLevel   = -1;
    private double holdLevel    = -1;
    private double sustainLevel = -1;
    private double endLevel     = -1;

    private double attackSlope  = 0;
    private double decaySlope   = 0;
    private double releaseSlope = 0;


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
     * @param delayTime The delay value in seconds, -1 to ignore the parameter
     * @param startLevel The start value in the range of [0..1], -1 to ignore the parameter
     * @param attackTime The attack value in seconds, -1 to ignore the parameter
     * @param holdLevel The hold level value in the range of [0..1], -1 to ignore the parameter
     * @param holdTime The hold value in seconds, -1 to ignore the parameter
     * @param decayTime The decay value in seconds, -1 to ignore the parameter
     * @param sustainTime The sustain value in the range of [0..1], -1 to ignore the parameter
     * @param releaseTime The release value in seconds, -1 to ignore the parameter
     * @param endLevel The end level value in the range of [0..1], -1 to ignore the parameter
     */
    private DefaultEnvelope (final double delayTime, final double startLevel, final double attackTime, final double holdLevel, final double holdTime, final double decayTime, final double sustainTime, final double releaseTime, final double endLevel)
    {
        this.delayTime = delayTime;
        this.startLevel = startLevel;
        this.attackTime = attackTime;
        this.holdLevel = holdLevel;
        this.holdTime = holdTime;
        this.decayTime = decayTime;
        this.sustainLevel = sustainTime;
        this.releaseTime = releaseTime;
        this.endLevel = endLevel;
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

        final IEnvelope envelope = DEFAULT_ENVELOPES.get (category);
        if (envelope != null)
            return envelope;

        // Seems the category is not one of the normalized ones
        final String normalizedCategory = TagDetector.detectCategory (category.split (" "));
        return DEFAULT_ENVELOPES.getOrDefault (normalizedCategory, ENVELOPE_PLUCKED);
    }


    /** {@inheritDoc} */
    @Override
    public double getDelayTime ()
    {
        return this.delayTime;
    }


    /** {@inheritDoc} */
    @Override
    public void setDelayTime (final double delayTime)
    {
        this.delayTime = delayTime;
    }


    /** {@inheritDoc} */
    @Override
    public double getStartLevel ()
    {
        return this.startLevel;
    }


    /** {@inheritDoc} */
    @Override
    public void setStartLevel (final double startLevel)
    {
        this.startLevel = startLevel;
    }


    /** {@inheritDoc} */
    @Override
    public double getAttackTime ()
    {
        return this.attackTime;
    }


    /** {@inheritDoc} */
    @Override
    public void setAttackTime (final double attackTime)
    {
        this.attackTime = attackTime;
    }


    /** {@inheritDoc} */
    @Override
    public double getAttackSlope ()
    {
        return this.attackSlope;
    }


    /** {@inheritDoc} */
    @Override
    public void setAttackSlope (final double slope)
    {
        this.attackSlope = slope;
    }


    /** {@inheritDoc} */
    @Override
    public double getHoldLevel ()
    {
        return this.holdLevel;
    }


    /** {@inheritDoc} */
    @Override
    public void setHoldLevel (final double holdLevel)
    {
        this.holdLevel = holdLevel;
    }


    /** {@inheritDoc} */
    @Override
    public double getHoldTime ()
    {
        return this.holdTime;
    }


    /** {@inheritDoc} */
    @Override
    public void setHoldTime (final double holdTime)
    {
        this.holdTime = holdTime;
    }


    /** {@inheritDoc} */
    @Override
    public double getDecayTime ()
    {
        return this.decayTime;
    }


    /** {@inheritDoc} */
    @Override
    public void setDecayTime (final double decayTime)
    {
        this.decayTime = decayTime;
    }


    /** {@inheritDoc} */
    @Override
    public double getDecaySlope ()
    {
        return this.decaySlope;
    }


    /** {@inheritDoc} */
    @Override
    public void setDecaySlope (final double slope)
    {
        this.decaySlope = slope;
    }


    /** {@inheritDoc} */
    @Override
    public double getSustainLevel ()
    {
        return this.sustainLevel;
    }


    /** {@inheritDoc} */
    @Override
    public void setSustainLevel (final double sustainLevel)
    {
        this.sustainLevel = sustainLevel;
    }


    /** {@inheritDoc} */
    @Override
    public double getReleaseTime ()
    {
        return this.releaseTime;
    }


    /** {@inheritDoc} */
    @Override
    public void setReleaseTime (final double releaseTime)
    {
        this.releaseTime = releaseTime;
    }


    /** {@inheritDoc} */
    @Override
    public double getReleaseSlope ()
    {
        return this.releaseSlope;
    }


    /** {@inheritDoc} */
    @Override
    public void setReleaseSlope (final double slope)
    {
        this.releaseSlope = slope;
    }


    /** {@inheritDoc} */
    @Override
    public double getEndLevel ()
    {
        return this.endLevel;
    }


    /** {@inheritDoc} */
    @Override
    public void setEndLevel (final double endLevel)
    {
        this.endLevel = endLevel;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        long temp = Double.doubleToLongBits (this.delayTime);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.attackTime);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.holdTime);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.decayTime);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.releaseTime);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.startLevel);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.holdLevel);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.sustainLevel);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.endLevel);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.attackSlope);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.decaySlope);
        result = prime * result + (int) (temp ^ temp >>> 32);
        temp = Double.doubleToLongBits (this.releaseSlope);
        return prime * result + (int) (temp ^ temp >>> 32);
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
        if (Double.doubleToLongBits (this.delayTime) != Double.doubleToLongBits (other.delayTime) || Double.doubleToLongBits (this.attackTime) != Double.doubleToLongBits (other.attackTime) || Double.doubleToLongBits (this.holdTime) != Double.doubleToLongBits (other.holdTime) || Double.doubleToLongBits (this.decayTime) != Double.doubleToLongBits (other.decayTime))
            return false;
        if ((Double.doubleToLongBits (this.releaseTime) != Double.doubleToLongBits (other.releaseTime)) || (Double.doubleToLongBits (this.startLevel) != Double.doubleToLongBits (other.startLevel)) || (Double.doubleToLongBits (this.holdLevel) != Double.doubleToLongBits (other.holdLevel)) || (Double.doubleToLongBits (this.sustainLevel) != Double.doubleToLongBits (other.sustainLevel)))
            return false;
        if (Double.doubleToLongBits (this.attackSlope) != Double.doubleToLongBits (other.attackSlope))
            return false;
        if (Double.doubleToLongBits (this.decaySlope) != Double.doubleToLongBits (other.decaySlope))
            return false;
        if (Double.doubleToLongBits (this.releaseSlope) != Double.doubleToLongBits (other.releaseSlope))
            return false;
        return Double.doubleToLongBits (this.endLevel) == Double.doubleToLongBits (other.endLevel);
    }


    /** {@inheritDoc} */
    @Override
    public void set (final IEnvelope sourceEnvelope)
    {
        this.delayTime = sourceEnvelope.getDelayTime ();
        this.attackTime = sourceEnvelope.getAttackTime ();
        this.holdTime = sourceEnvelope.getHoldTime ();
        this.decayTime = sourceEnvelope.getDecayTime ();
        this.releaseTime = sourceEnvelope.getReleaseTime ();

        this.startLevel = sourceEnvelope.getStartLevel ();
        this.holdLevel = sourceEnvelope.getHoldLevel ();
        this.sustainLevel = sourceEnvelope.getSustainLevel ();
        this.endLevel = sourceEnvelope.getEndLevel ();

        this.attackSlope = sourceEnvelope.getAttackSlope ();
        this.decaySlope = sourceEnvelope.getDecaySlope ();
        this.releaseSlope = sourceEnvelope.getReleaseSlope ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isSet ()
    {
        return this.delayTime != -1 || this.startLevel != -1 || this.attackTime != -1 || this.holdLevel != -1 || this.holdTime != -1 || this.decayTime != -1 || this.sustainLevel != -1 || this.releaseTime != -1 || this.endLevel != -1 || this.attackSlope != 0 || this.decaySlope != 0 || this.releaseSlope != 0;
    }
}
