// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.panel;

import javafx.geometry.Orientation;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;


/**
 * A panel in horizontal or vertical order with several helper functions for creating controls.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BoxPanel extends BasePanel
{
    /**
     * Creates a Panel in horizontal or vertical order.
     *
     * @param orientation Sets the orientation of the panel to horizontal or vertical
     */
    public BoxPanel (final Orientation orientation)
    {
        this (orientation, true);
    }


    /**
     * Creates a Panel in horizontal or vertical order.
     *
     * @param orientation Sets the orientation of the panel to horizontal or vertical
     * @param addSpace Adds space around the panel if true
     */
    public BoxPanel (final Orientation orientation, final boolean addSpace)
    {
        super (createPane (orientation, addSpace));
    }


    private static Pane createPane (final Orientation orientation, final boolean addSpace)
    {
        Pane pane;
        if (orientation == Orientation.HORIZONTAL)
        {
            pane = new HBox ();
            pane.getStyleClass ().add ("hbox");
        }
        else
        {
            pane = new VBox ();
            pane.getStyleClass ().add ("vbox");
        }
        if (!addSpace)
            pane.getStyleClass ().add ("no-padding");
        return pane;
    }
}
