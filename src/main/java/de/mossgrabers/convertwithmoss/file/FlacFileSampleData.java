// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;


/**
 * The data of an FLAC sample file.
 *
 * @author Jürgen Moßgraber
 */
public class FlacFileSampleData extends AbstractFileSampleData
{
    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public FlacFileSampleData (final File file) throws IOException
    {
        super (file);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        AudioFileUtils.decompressToWav (this.sampleFile, outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // No info available in FLAC
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        // Could be implemented with e.g. JAudioTagger
    }
}
