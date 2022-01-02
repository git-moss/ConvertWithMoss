// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model;

import java.util.List;


/**
 * A velocity layer in a multi-sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IVelocityLayer
{
    /**
     * Get the name of the velocity layer.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the velocity layer.
     *
     * @param name The name
     */
    void setName (String name);


    /**
     * Get the description of the samples which belong to the velocity layer.
     *
     * @return The descriptions in an ordered map
     */
    List<ISampleMetadata> getSampleMetadata ();


    /**
     * Set the description of the samples which belong to the velocity layer.
     *
     * @param samples The sample descriptions
     */
    void setSampleMetadata (List<ISampleMetadata> samples);


    /**
     * Add a sample description.
     *
     * @param sample The sample description
     */
    void addSampleMetadata (ISampleMetadata sample);
}
