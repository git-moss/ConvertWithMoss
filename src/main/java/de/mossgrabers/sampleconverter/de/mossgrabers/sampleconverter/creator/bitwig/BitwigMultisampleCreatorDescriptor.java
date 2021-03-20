// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.creator.bitwig;

import de.mossgrabers.sampleconverter.creator.AbstractCreatorDescriptor;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;

import javafx.scene.Node;
import javafx.scene.layout.BorderPane;


/**
 * Descriptor for the Bitwig Multisample creator.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BitwigMultisampleCreatorDescriptor extends AbstractCreatorDescriptor
{
    /**
     * Constructor.
     */
    public BitwigMultisampleCreatorDescriptor ()
    {
        super ("Bitwig Multisample", new BitwigMultisampleCreator ());
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
