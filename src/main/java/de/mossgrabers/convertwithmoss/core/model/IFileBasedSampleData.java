// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

/**
 * Interface to the data of a sample which is stored in a file.
 *
 * @author Jürgen Moßgraber
 */
public interface IFileBasedSampleData extends ISampleData
{
    /**
     * Get the name of the file in which the sample is stored.
     *
     * @return The name of the file
     */
    String getFilename ();


    /**
     * Update the metadata from data available in the file.
     *
     * @param metadata The metadata
     */
    void updateMetadata (IMetadata metadata);
}
