// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;


/**
 * A detected source for a multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public interface IMultisampleSource
{
    /**
     * Get the metadata description for the source.
     *
     * @return The folder
     */
    IMetadata getMetadata ();


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
     * Get only the groups which do contain at least one sample.
     *
     * @param filterReleaseTriggers Removes all groups which do only contain release triggers
     * @return The group without empty ones
     */
    List<IGroup> getNonEmptyGroups (final boolean filterReleaseTriggers);


    /**
     * Get the name of the multi-sample.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the multi-sample.
     *
     * @param name The name
     */
    void setName (String name);


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
}
