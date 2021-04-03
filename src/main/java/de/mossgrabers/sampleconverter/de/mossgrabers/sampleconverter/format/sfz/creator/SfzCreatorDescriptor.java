// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sfz.creator;

import de.mossgrabers.sampleconverter.core.creator.AbstractCreatorDescriptor;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;


/**
 * Descriptor for the SFZ creator.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzCreatorDescriptor extends AbstractCreatorDescriptor
{
    /**
     * Constructor.
     */
    public SfzCreatorDescriptor ()
    {
        super ("SFZ", new SfzCreator ());
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        return new BorderPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        // Intentionally empty
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        // Intentionally empty
    }
}
