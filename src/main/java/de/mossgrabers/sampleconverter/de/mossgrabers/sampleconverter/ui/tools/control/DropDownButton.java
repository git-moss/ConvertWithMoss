// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.control;

import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;


/**
 * A button with an additional drop down button which displays a popup menu.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DropDownButton extends BorderPane
{
    private final Button     pushButton = new Button ();
    private final MenuButton menuButton = new MenuButton ();


    /**
     * Constructor.
     *
     * @param text The text of the button, may be null
     * @param pushGraphic The graphic to put on the push button, may be null
     */
    public DropDownButton (final String text, final Image pushGraphic)
    {
        this.pushButton.setMnemonicParsing (true);
        this.pushButton.setText (text);
        this.pushButton.setGraphic (new ImageView (pushGraphic));
        this.setCenter (this.pushButton);
        this.setRight (this.menuButton);

        this.menuButton.heightProperty ().addListener ((ChangeListener<Number>) (observable, oldValue, newValue) -> {
            final double height = newValue.doubleValue ();
            this.menuButton.setMinHeight (height);
            this.menuButton.setMaxHeight (height);
            this.menuButton.setMinWidth (height);
            this.menuButton.setMaxWidth (height);
        });
    }


    /**
     * Register a handler for when the button gets pushed.
     *
     * @param handler The handler to invoke when the button is pushed
     */
    public void setOnAction (final EventHandler<ActionEvent> handler)
    {
        this.pushButton.setOnAction (handler);
    }


    /**
     * Get the items of the menu.
     *
     * @return The observable items
     */
    public ObservableList<MenuItem> getItems ()
    {
        return this.menuButton.getItems ();
    }
}
