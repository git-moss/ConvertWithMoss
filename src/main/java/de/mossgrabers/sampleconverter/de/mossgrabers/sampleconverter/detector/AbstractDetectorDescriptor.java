// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector;

import de.mossgrabers.sampleconverter.core.AbstractObjectDescriptor;
import de.mossgrabers.sampleconverter.core.IDetectorDescriptor;


/**
 * Base class for detector descriptors.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractDetectorDescriptor extends AbstractObjectDescriptor implements IDetectorDescriptor
{
    /**
     * Constructor.
     *
     * @param name The name of the object.
     */
    public AbstractDetectorDescriptor (final String name)
    {
        super (name);
    }
}
