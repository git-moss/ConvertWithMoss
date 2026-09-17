// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sequential.prophetx;

import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;


/**
 * Reads the files of an instrument folder, which is either a plain folder or an archive.
 *
 * @author Jürgen Moßgraber
 */
interface IInstrumentFolder
{
    /**
     * Load the data of a sample.
     *
     * @param filePath The file path from the group file, relative to the instrument folder
     * @return The sample data or null if the sample was not found, which is already reported
     * @throws IOException Could not read the sample
     */
    ISampleData loadSample (String filePath) throws IOException;


    /**
     * Load the optional file with the velocity volumes.
     *
     * @return The text of the file or null if the instrument has none
     * @throws IOException Could not read the file
     */
    String loadVelocityVolumes () throws IOException;
}