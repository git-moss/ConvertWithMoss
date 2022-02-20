// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import de.mossgrabers.convertwithmoss.core.ICoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;

import java.io.File;
import java.io.IOException;


/**
 * Creates and stores a multi-sample file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface ICreator extends ICoreTask
{
    /**
     * Create and store a multi-sample file.
     *
     * @param destinationFolder Where to store the created file
     * @param multisampleSource The multi-sample source from which to create
     * @throws IOException Could not store the file
     */
    void create (File destinationFolder, IMultisampleSource multisampleSource) throws IOException;
}
