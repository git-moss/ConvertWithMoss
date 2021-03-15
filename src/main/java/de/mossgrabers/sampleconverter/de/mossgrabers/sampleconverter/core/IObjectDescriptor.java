// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core;

import javafx.scene.Node;


/**
 * A descriptor providing some metadata for an object.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IObjectDescriptor
{
    /**
     * Get the name of the object.
     *
     * @return The name
     */
    String getName ();


    /**
     * Get the pane with the edit widgets.
     *
     * @return The pane
     */
    Node getEditPane ();
}
