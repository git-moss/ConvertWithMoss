// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.creator;

import de.mossgrabers.sampleconverter.core.AbstractObjectDescriptor;
import de.mossgrabers.sampleconverter.core.ICreator;
import de.mossgrabers.sampleconverter.core.ICreatorDescriptor;


/**
 * Base class for creator descriptors.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractCreatorDescriptor extends AbstractObjectDescriptor implements ICreatorDescriptor
{
    private final ICreator creator;


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param creator The creator to describe
     */
    public AbstractCreatorDescriptor (final String name, final ICreator creator)
    {
        super (name);

        this.creator = creator;
    }


    /** {@inheritDoc} */
    @Override
    public ICreator getCreator ()
    {
        return this.creator;
    }
}
