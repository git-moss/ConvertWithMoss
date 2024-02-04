// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import java.io.IOException;
import java.io.OutputStream;


/**
 * Interface to the data of a sample.
 *
 * @author Jürgen Moßgraber
 */
public interface ISampleData
{
    /**
     * Get information abut the format of the audio data.
     *
     * @return The information
     * @throws IOException Could not read the audio metadata
     */
    IAudioMetadata getAudioMetadata () throws IOException;


    /**
     * Write the data of the sample to the given output stream. The implementation must write a
     * fully well-formed WAV file.
     *
     * @param outputStream The stream to where to write the data
     * @throws IOException Could not write the data
     */
    void writeSample (OutputStream outputStream) throws IOException;


    /**
     * Add metadata information to the given zone which might be stored in the underlying sample
     * data format.
     *
     * @param zone The zone to which to add the data
     * @param addRootKey If true, set the root key
     * @param addLoops If true, found loops are added
     * @throws IOException Could not read or parse the underlying data
     */
    void addMetadata (ISampleZone zone, boolean addRootKey, boolean addLoops) throws IOException;
}
