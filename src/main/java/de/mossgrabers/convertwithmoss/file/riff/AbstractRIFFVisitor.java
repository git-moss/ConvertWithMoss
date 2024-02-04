// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

import de.mossgrabers.convertwithmoss.exception.ParseException;


/**
 * Abstract implementation for a RIFF visitor. Uncommon methods are implemented empty.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractRIFFVisitor implements RIFFVisitor
{
    /** {@inheritDoc} */
    @Override
    public boolean enteringGroup (final RIFFChunk group)
    {
        // Return true to enter and parse the group
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void enterGroup (final RIFFChunk group) throws ParseException
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void leaveGroup (final RIFFChunk group) throws ParseException
    {
        // Intentionally empty
    }
}
