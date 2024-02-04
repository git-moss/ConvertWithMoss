// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;


/**
 * A chunk which contains the data of a BNI preset.
 *
 * @author Jürgen Moßgraber
 */
public class BNIPresetChunkData implements IChunkData
{
    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        // Not used
    }
}
