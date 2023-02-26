// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;

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
     * Get the event that triggers the playback of the sample.
     *
     * @return The trigger type
     */
    TriggerType getTrigger ();


    /**
     * Set the event that triggers the playback of the sample.
     *
     * @param trigger The trigger type
     */
    void setTrigger (TriggerType trigger);


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
