// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

/**
 * Base class for object descriptors.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractObjectDescriptor implements IObjectDescriptor
{
    private final String name;


    /**
     * Constructor.
     *
     * @param name The name of the object.
     */
    public AbstractObjectDescriptor (final String name)
    {
        this.name = name;
    }


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return this.name;
    }
}
