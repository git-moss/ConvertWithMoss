// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.panel;

import javafx.geometry.Orientation;
import javafx.scene.control.ButtonBase;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.TilePane;


/**
 * A panel in horizontal or vertical order with several helper functions for creating controls.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ButtonPanel extends BasePanel
{
    /**
     * Creates a Panel in horizontal or vertical order.
     *
     * @param orientation Sets the orientation of the panel to horizontal or vertical
     */
    public ButtonPanel (final Orientation orientation)
    {
        this (orientation, true);
    }


    /**
     * Creates a Panel in horizontal or vertical order.
     *
     * @param orientation Sets the orientation of the panel to horizontal or vertical
     * @param addSpace Adds space around the panel if true
     */
    public ButtonPanel (final Orientation orientation, final boolean addSpace)
    {
        super (createPane (orientation, addSpace));
    }


    /**
     * {@inheritDoc} Sets all buttons to the same size.
     */
    @Override
    protected void addButton (final ButtonBase button, final Image icon, final String label, final String tooltip)
    {
        super.addButton (button, icon, label, tooltip);
        button.setMaxSize (Double.MAX_VALUE, Double.MAX_VALUE);
    }


    /**
     * Creates a TilePane and adds spacing and gaps between the buttons.
     *
     * @param orientation The orientation of the pane
     * @param addSpace If true padding is added to the pane
     * @return The created pane
     */
    private static Pane createPane (final Orientation orientation, final boolean addSpace)
    {
        final TilePane pane = new TilePane (orientation);
        if (addSpace)
            pane.getStyleClass ().add ("padding");
        pane.getStyleClass ().add ("tile-pane");
        return pane;
    }
}
