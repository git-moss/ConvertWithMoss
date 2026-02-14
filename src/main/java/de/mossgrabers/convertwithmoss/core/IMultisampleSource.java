// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * A detected source for a multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public interface IMultisampleSource extends ISource
{
    /**
     * Get the folder which contains the multi-sample source or the file itself.
     *
     * @return The folder
     */
    File getSourceFile ();


    /**
     * Set the folder which contains the multi-sample source or the file itself.
     *
     * @param folder The folder
     */
    void setFolder (File folder);


    /**
     * Get the sub-folders which contain the samples up to the source path.
     *
     * @return The sub folders
     */
    String [] getSubPath ();


    /**
     * Set the sub-folders which contain the samples up to the source path.
     *
     * @param subFolders The sub folders
     */
    void setSubPath (String [] subFolders);


    /**
     * Get the description of the groups which belong to the multi-sample.
     *
     * @return The descriptions
     */
    List<IGroup> getGroups ();


    /**
     * Get all groups which are considered to be part of a round-robin sequence. See
     * {@link IGroup#isGroupRoundRobin ()}.
     *
     * @return All round-robin groups with the sequence position index, see
     *         {@link ISampleZone#getSequencePosition ()}
     */
    default Map<IGroup, Integer> getRoundRobinGroups ()
    {
        final Map<IGroup, Integer> roundRobinGroups = new HashMap<> ();
        for (final IGroup group: this.getGroups ())
            if (group.isGroupRoundRobin ())
                roundRobinGroups.put (group, Integer.valueOf (group.getSampleZones ().get (0).getSequencePosition ()));
        return roundRobinGroups;
    }


    /**
     * Get only the groups which do contain at least one sample.
     *
     * @param filterReleaseTriggers Removes all groups which do only contain release triggers
     * @return The group without empty ones
     */
    List<IGroup> getNonEmptyGroups (final boolean filterReleaseTriggers);


    /**
     * Set the groups with the related sample zones.
     *
     * @param groups The groups with the related sample zones
     */
    void setGroups (List<IGroup> groups);


    /**
     * Get the name to display for the mapping process.
     *
     * @return The name, usually the source file
     */
    String getMappingName ();


    /**
     * Checks all samples in all groups for filter settings. Only if all samples contain the same
     * filter settings a result is returned.
     *
     * @return The filter if a global filter setting is found
     */
    Optional<IFilter> getGlobalFilter ();


    /**
     * Sets a filter on all samples in all groups.
     *
     * @param filter The filter to set
     */
    void setGlobalFilter (IFilter filter);


    /**
     * Checks all samples in all groups for velocity modulation on the amplitude settings. Only if
     * all samples contain the same modulation value a result is returned.
     *
     * @return The modulation value
     */
    Optional<Double> getGlobalAmplitudeVelocity ();


    /**
     * Checks all samples in all groups for amplitude envelope modulation settings. Only if all
     * samples contain the same settings a result is returned.
     *
     * @return The amplitude if a global envelope setting is found
     */
    Optional<IEnvelopeModulator> getGlobalAmplitudeModulator ();


    /**
     * Checks all samples in all groups for panning. Only if all samples contain the same panning
     * value a result is returned.
     *
     * @return The global panning value in the range of [-1..1], -1 is full left, 0 centered and 1
     *         full right
     */
    Optional<Double> getGlobalPanning ();


    /**
     * Checks all samples in all groups for gain. Only if all samples contain the same gain value a
     * result is returned.
     *
     * @return The global gain value
     */
    Optional<Double> getGlobalGain ();


    /**
     * Checks all samples in all groups for tuning. Only if all samples contain the same tuning
     * value a result is returned.
     *
     * @return The global tuning value
     */
    Optional<Double> getGlobalTuning ();


    /**
     * Get the lowest key covered by a sample in all of the groups.
     *
     * @return The lowest playing key (0-127)
     */
    int getLowestKey ();


    /**
     * Get the highest key covered by a sample in all of the groups.
     *
     * @return The highest playing key (0-127)
     */
    int getHighestKey ();
}
