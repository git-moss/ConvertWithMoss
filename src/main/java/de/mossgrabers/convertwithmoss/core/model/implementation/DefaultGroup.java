// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


/**
 * Default implementation for a group.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultGroup implements IGroup
{
    private List<ISampleZone> sampleZones = new ArrayList<> ();
    private String            name;
    protected TriggerType     triggerType = TriggerType.ATTACK;
    private double            gain        = 0;
    private double            panning     = 0;
    private double            tuning      = 0;


    /**
     * Constructor.
     */
    public DefaultGroup ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param name The group's name
     */
    public DefaultGroup (final String name)
    {
        this.name = name;
    }


    /**
     * Constructor.
     *
     * @param samples The group's samples
     */
    public DefaultGroup (final List<ISampleZone> samples)
    {
        this.sampleZones = samples;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }


    /** {@inheritDoc} */
    @Override
    public void setName (final String name)
    {
        this.name = name;
    }


    /** {@inheritDoc} */
    @Override
    public List<ISampleZone> getSampleZones ()
    {
        return this.sampleZones;
    }


    /** {@inheritDoc} */
    @Override
    public void setSampleZones (final List<ISampleZone> sampleZones)
    {
        this.sampleZones = sampleZones;
    }


    /** {@inheritDoc} */
    @Override
    public void addSampleZone (final ISampleZone sampleZone)
    {
        this.sampleZones.add (sampleZone);
    }


    /** {@inheritDoc} */
    @Override
    public TriggerType getTrigger ()
    {
        return this.triggerType;
    }


    /** {@inheritDoc} */
    @Override
    public void setTrigger (final TriggerType trigger)
    {
        this.triggerType = trigger;
    }


    /** {@inheritDoc} */
    @Override
    public double getGain ()
    {
        return this.gain;
    }


    /** {@inheritDoc} */
    @Override
    public void setGain (final double gain)
    {
        this.gain = gain;
    }


    /** {@inheritDoc} */
    @Override
    public double getPanning ()
    {
        return this.panning;
    }


    /** {@inheritDoc} */
    @Override
    public void setPanning (final double panning)
    {
        this.panning = panning;
    }


    /** {@inheritDoc} */
    @Override
    public double getTuning ()
    {
        return this.tuning;
    }


    /** {@inheritDoc} */
    @Override
    public void setTuning (final double tuning)
    {
        this.tuning = tuning;
    }
}
