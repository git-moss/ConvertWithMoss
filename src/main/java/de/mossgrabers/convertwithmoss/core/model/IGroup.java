// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


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
     * Add a sample zone.
     *
     * @param sample The sample description
     */
    void addSampleZone (ISampleZone sample);


    /**
     * If all zones in this group are set to play round-robin and contain the same one and only
     * sequence position, it is considered that round-robin happens on a group-level. This means
     * e.g. 3 groups are played round-robin.
     *
     * @return True if round-robin happens on a group-level
     */
    default boolean isGroupRoundRobin ()
    {
        final List<ISampleZone> sampleZones = this.getSampleZones ();
        if (sampleZones.isEmpty ())
            return false;

        int sequencePosition = -1;
        for (final ISampleZone zone: sampleZones)
        {
            if (zone.getPlayLogic () != PlayLogic.ROUND_ROBIN)
                return false;
            if (sequencePosition == -1)
                sequencePosition = zone.getSequencePosition ();
            else if (sequencePosition != zone.getSequencePosition ())
                return false;
        }
        return true;
    }
}
