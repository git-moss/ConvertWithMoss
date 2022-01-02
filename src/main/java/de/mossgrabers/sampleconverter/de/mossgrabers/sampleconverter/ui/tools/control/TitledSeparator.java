// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.control;

import de.mossgrabers.sampleconverter.ui.tools.Functions;

import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;


/**
 * A titled separator. This is a text followed by a horizontal separator line.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class TitledSeparator extends BorderPane
{
    private final Label     label     = new Label ();
    private final Separator separator = new Separator (Orientation.HORIZONTAL);


    /**
     * Constructor.
     *
     * @param title The text to display as a title
     */
    public TitledSeparator (final String title)
    {
        this.setCenter (this.separator);
        this.setLeft (this.label);
        this.label.setText (Functions.getText (title));
        this.label.getStyleClass ().add ("titled-separator");
    }
}
