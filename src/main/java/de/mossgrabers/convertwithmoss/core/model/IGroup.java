// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;

import java.util.List;


/**
 * A group in a multi-sample which groups several sample zones.
 *
 * @author Jürgen Moßgraber
 */
public interface IGroup
{
    /**
     * Get the name of the group.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the group.
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
     * Get the description of the samples zones which belong to the group.
     *
     * @return The zones in an ordered map
     */
    List<ISampleZone> getSampleZones ();


    /**
     * Set the description of the sample zones which belong to the group.
     *
     * @param zones The sample zones
     */
    void setSampleZones (List<ISampleZone> zones);


    /**
     * Add a sample description.
     *
     * @param sample The sample description
     */
    void addSampleMetadata (ISampleZone sample);
}
