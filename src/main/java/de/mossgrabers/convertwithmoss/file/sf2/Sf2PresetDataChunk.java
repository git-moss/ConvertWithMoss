// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import de.mossgrabers.convertwithmoss.file.riff.AbstractListChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * A Sf2 preset data list chunk.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2PresetDataChunk extends AbstractListChunk
{
    /**
     * Constructor.
     */
    public Sf2PresetDataChunk ()
    {
        super (RiffID.SF_PDTA_ID.getId ());
    }
}
