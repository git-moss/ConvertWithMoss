// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;


/**
 * A descriptor providing some metadata for a creator.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface ICreatorDescriptor extends IObjectDescriptor
{
    /**
     * Get the creator.
     *
     * @return The creator
     */
    ICreator getCreator ();


    /**
     * Save the settings of the creator.
     *
     * @param config Where to store to
     */
    void saveSettings (BasicConfig config);


    /**
     * Load the settings of the creator.
     *
     * @param config Where to load from
     */
    void loadSettings (BasicConfig config);
}
