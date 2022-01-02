// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model.implementation;

import de.mossgrabers.sampleconverter.core.model.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.model.IVelocityLayer;

import java.util.ArrayList;
import java.util.List;


/**
 * A velocity layer.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DefaultVelocityLayer implements IVelocityLayer
{
    private List<ISampleMetadata> samples = new ArrayList<> ();
    private String                name;


    /**
     * Constructor.
     */
    public DefaultVelocityLayer ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param name The layers' name
     */
    public DefaultVelocityLayer (final String name)
    {
        this.name = name;
    }


    /**
     * Constructor.
     *
     * @param samples The layers' samples
     */
    public DefaultVelocityLayer (final List<ISampleMetadata> samples)
    {
        this.samples = samples;
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
    public List<ISampleMetadata> getSampleMetadata ()
    {
        return this.samples;
    }


    /** {@inheritDoc} */
    @Override
    public void setSampleMetadata (final List<ISampleMetadata> samples)
    {
        this.samples = samples;
    }


    /** {@inheritDoc} */
    @Override
    public void addSampleMetadata (final ISampleMetadata sample)
    {
        this.samples.add (sample);
    }
}
