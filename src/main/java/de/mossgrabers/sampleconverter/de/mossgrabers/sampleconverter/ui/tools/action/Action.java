// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.action;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.image.Image;


/**
 * An action to execute.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class Action implements EventHandler<ActionEvent>
{
    private final SimpleStringProperty  name        = new SimpleStringProperty ();
    private final SimpleStringProperty  description = new SimpleStringProperty ();
    private String                      handler;
    private Image                       image;
    private String                      accelerator;
    private final SimpleBooleanProperty enabled     = new SimpleBooleanProperty (true);
    private final SimpleBooleanProperty selected    = new SimpleBooleanProperty (false);


    /**
     * Get the name property of the action.
     *
     * @return The name property of the action
     */
    public SimpleStringProperty getNameProperty ()
    {
        return this.name;
    }


    /**
     * Get the name of the action.
     *
     * @return The name of the action
     */
    public String getName ()
    {
        return this.name.getValue ();
    }


    /**
     * Set the name of the action.
     *
     * @param name The name to set
     */
    public void setName (final String name)
    {
        this.name.setValue (name);
    }


    /**
     * Get the description property of the action
     *
     * @return A descriptive text for the action
     */
    public SimpleStringProperty getDescriptionProperty ()
    {
        return this.description;
    }


    /**
     * Get the description of the action
     *
     * @return A descriptive text for the action
     */
    public String getDescription ()
    {
        return this.description.getValue ();
    }


    /**
     * Set the description of the action.
     *
     * @param description A description
     */
    public void setDescription (final String description)
    {
        this.description.setValue (description);
    }


    /**
     * Get the icon to use for the action.
     *
     * @return The image icon
     */
    public Image getImage ()
    {
        return this.image;
    }


    /**
     * Set the image icon of the action.
     *
     * @param image An image
     */
    public void setImage (final Image image)
    {
        this.image = image;
    }


    /**
     * Set the accelerator key combination for this action.
     *
     * @param keyStroke A keystroke combination
     * @see javafx.scene.input.KeyCombination#getName
     */
    public void setAccelerator (final String keyStroke)
    {
        this.accelerator = keyStroke;
    }


    /**
     * Get the accelerator key combination for this action.
     *
     * @return The accelerator
     */
    public String getAccelerator ()
    {
        return this.accelerator;
    }


    /**
     * Dis-/enables the action.
     *
     * @param enable Enabled the action if true
     */
    public final void setEnabled (final boolean enable)
    {
        this.enabled.set (enable);
    }


    /**
     * Returns true if the action is enabled.
     *
     * @return True if the action is enabled
     */
    public boolean isEnabled ()
    {
        return this.enabled.getValue ().booleanValue ();
    }


    /**
     * Binds the given property to the enable property.
     *
     * @param property The property to bind to
     */
    public void bindEnable (final Property<Boolean> property)
    {
        property.bind (this.enabled);
    }


    /**
     * Binds the given property to the negated enable property.
     *
     * @param property The property to bind to
     */
    public void bindDisable (final Property<Boolean> property)
    {
        property.bind (this.enabled.not ());
    }


    /**
     * @return The selected property
     */
    public SimpleBooleanProperty getSelectedProperty ()
    {
        return this.selected;
    }


    /**
     * Set the selected state.
     *
     * @param select True if selected otherwise false
     */
    public void setSelected (final boolean select)
    {
        this.selected.set (select);
    }


    /**
     * Returns true if the action is selected.
     *
     * @return True if the action is selected
     */
    public boolean isSelected ()
    {
        return this.selected.getValue ().booleanValue ();
    }


    /**
     * Set the handler for the action.
     *
     * @param handler The handler
     */
    public void setHandler (final String handler)
    {
        this.handler = handler;
    }


    /**
     * Get the handler for the action.
     *
     * @return The handler
     */
    public String getHandler ()
    {
        return this.handler;
    }
}
