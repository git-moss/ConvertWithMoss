// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.panel;

import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.ui.tools.control.DropDownButton;
import de.mossgrabers.sampleconverter.ui.tools.control.TitledSeparator;

import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Wraps a pane and adds several helper functions for creating controls.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BasePanel
{
    private final Pane pane;


    /**
     * Creates a Panel in horizontal or vertical order.
     *
     * @param pane The pane to use for this panel
     */
    public BasePanel (final Pane pane)
    {
        this.pane = pane;
    }


    /**
     * Creates and adds a text area to the panel.
     *
     * @param label The name of the label which is added to the field
     * @return The created text area
     */
    public TextArea createTextArea (final String label)
    {
        return this.createTextArea (label, -1);
    }


    /**
     * Creates and adds a text area to the panel.
     *
     * @param label The name of the label which is added to the field
     * @param maxChars If not -1 the characters are limited to this number
     * @return The created text area
     */
    public TextArea createTextArea (final String label, final int maxChars)
    {
        final TextArea area = new TextArea ();
        this.addComponent (area, label, null, false);
        if (maxChars > 0)
            maxLength (area, maxChars);
        return area;
    }


    /**
     * Creates and adds a text field to the panel which is limited to positive integers.
     *
     * @param label The name of the label which is added to the field
     * @param button Adds the button to the right of the field
     * @return The created text field
     */
    public TextField createPositiveIntegerField (final String label, final Button button)
    {
        final TextField field = new TextField ();
        limitToNumbers (field);
        this.addComponent (field, this.createLabel (label), null, false, button);
        return field;
    }


    /**
     * Creates and adds a text field to the panel which is limited to positive integers.
     *
     * @param label The name of the label which is added to the field
     * @return The created text field
     */
    public TextField createPositiveIntegerField (final String label)
    {
        final TextField field = this.createField (label, -1);
        limitToNumbers (field);
        return field;
    }


    /**
     * Limits the textfield to numbers.
     *
     * @param field The field to limit
     */
    private static void limitToNumbers (final TextField field)
    {
        field.addEventFilter (KeyEvent.KEY_TYPED, keyEvent -> {
            if (!"0123456789".contains (keyEvent.getCharacter ()))
                keyEvent.consume ();
        });
    }


    /**
     * Creates and adds a text field to the panel.
     *
     * @param label The name of the label which is added to the field
     * @return The created text field
     */
    public TextField createField (final String label)
    {
        return this.createField (label, -1);
    }


    /**
     * Creates and adds a text field to the panel.
     *
     * @param label The name of the label which is added to the field
     * @param maxChars If not -1 the characters are limited to this number
     * @return The created text field
     */
    public TextField createField (final String label, final int maxChars)
    {
        return this.createField (label, null, maxChars);
    }


    /**
     * Creates and adds a text field to the panel.
     *
     * @param label The name of the label which is added to the field
     * @param tooltip Tooltip text
     * @param maxChars If not -1 the characters are limited to this number
     * @return The created text field
     */
    public TextField createField (final String label, final String tooltip, final int maxChars)
    {
        final TextField field = new TextField ();
        this.addComponent (field, label, tooltip);
        if (maxChars > 0)
            maxLength (field, maxChars);
        return field;
    }


    /**
     * Creates and adds a password field to the panel.
     *
     * @param label The name of the label which is added to the field
     * @param tooltip Tooltip text
     * @param maxChars If not -1 the characters are limited to this number
     * @return The created text field
     */
    public PasswordField createPasswordField (final String label, final String tooltip, final int maxChars)
    {
        final PasswordField field = new PasswordField ();
        this.addComponent (field, label, tooltip);
        if (maxChars > 0)
            maxLength (field, maxChars);
        return field;
    }


    /**
     * Creates and adds a password field to the panel.
     *
     * @param label The name of the label which is added to the field
     * @param tooltip Tooltip text
     * @param maxChars If not -1 the characters are limited to this number
     * @return The created text field
     */
    public PasswordField createPasswordField (final Label label, final String tooltip, final int maxChars)
    {
        final PasswordField field = new PasswordField ();
        this.addComponent (field, label, tooltip);
        if (maxChars > 0)
            maxLength (field, maxChars);
        return field;
    }


    /**
     * Creates and adds a combobox to the panel.
     *
     * @param <E> The type of the combobox's content
     * @param label The name of the label which is added to the combobox
     * @param content The content of the combobox
     * @return The created combobox
     */
    public <E> ComboBox<E> createComboBox (final String label, @SuppressWarnings("unchecked") final E... content)
    {
        return this.createComboBox (label, Arrays.asList (content));
    }


    /**
     * Creates and adds a combobox to the panel.
     *
     * @param <E> The type of the combobox's content
     * @param label The name of the label which is added to the combobox
     * @param content The content of the combobox
     * @return The created combobox
     */
    public <E> ComboBox<E> createComboBox (final String label, final List<E> content)
    {
        final ComboBox<E> combobox = new ComboBox<> (FXCollections.observableList (content));
        this.addComponent (combobox, label);
        return combobox;
    }


    /**
     * Creates and adds a listbox to the panel.
     *
     * @param <E> The type of the combobox's content
     * @param label The name of the label which is added to the listbox
     * @param content The content of the listbox
     * @return The created list box
     */
    public <E> ListView<E> createListBox (final String label, final Collection<E> content)
    {
        final ListView<E> list = new ListView<> ();
        list.getItems ().addAll (content);
        return this.createListBox (label, list);
    }


    /**
     * Creates and adds a listbox to the panel.
     *
     * @param <E> The type of the combobox's content
     * @param label The name of the label which is added to the listbox
     * @param content The content of the listbox
     * @return The created list box
     */
    public <E> ListView<E> createListBox (final String label, @SuppressWarnings("unchecked") final E... content)
    {
        return this.createListBox (label, Arrays.asList (content));
    }


    /**
     * Adds a listbox to the panel.
     *
     * @param <E> The type of the combobox's content
     * @param label The name of the label which is added to the listbox
     * @param list The listbox to add
     * @return The created list box
     */
    public <E> ListView<E> createListBox (final String label, final ListView<E> list)
    {
        this.addComponent (list, label, null, false);
        return list;
    }


    /**
     * Creates a flat button.
     *
     * @param icon An icon which is displayed on the button
     * @return The created button
     */
    public Button createFlatButton (final Image icon)
    {
        return this.createFlatButton (icon, null);
    }


    /**
     * Creates a flat button.
     *
     * @param icon An icon which is displayed on the button
     * @param tooltip Tooltip text
     * @return The created button
     */
    public Button createFlatButton (final Image icon, final String tooltip)
    {
        final Button button = this.createButton (icon, null, tooltip);
        button.getStyleClass ().add ("flat-button");
        return button;
    }


    /**
     * Creates and adds a toggle button to the panel.
     *
     * @param icon An icon which is displayed on the button
     * @param label The name of the label which is displayed on the button
     * @return The created button
     */
    public ToggleButton createToggleButton (final Image icon, final String label)
    {
        final ToggleButton button = new ToggleButton ();
        this.addButton (button, icon, label, null);
        return button;
    }


    /**
     * Creates and adds a radio button to the panel.
     *
     * @param label The name of the label which is added to the button
     * @return The created radio button
     */
    public RadioButton createRadioButton (final String label)
    {
        return this.createRadioButton (label, null);
    }


    /**
     * Creates and adds a radio button to the panel.
     *
     * @param label The name of the label which is added to the button
     * @param tooltip Tooltip text
     * @return The created radio button
     */
    public RadioButton createRadioButton (final String label, final String tooltip)
    {
        final RadioButton button = new RadioButton ();
        this.addButton (button, null, label, tooltip);
        return button;
    }


    /**
     * Creates and adds a button to the panel.
     *
     * @param label The name of the label which is added to the button
     * @return The created button
     */
    public Button createButton (final String label)
    {
        final Button button = new Button ();
        this.addButton (button, null, label, null);
        return button;
    }


    /**
     * Creates and adds a button to the panel.
     *
     * @param icon An icon which is displayed on the button
     * @return The created button
     */
    public Button createButton (final Image icon)
    {
        final Button button = new Button ();
        this.addButton (button, icon, null, null);
        return button;
    }


    /**
     * Creates and adds a button to the panel.
     *
     * @param icon An icon which is displayed on the button
     * @param label The name of the label which is displayed on the button
     * @return The created button
     */
    public Button createButton (final Image icon, final String label)
    {
        final Button button = new Button ();
        this.addButton (button, icon, label, null);
        return button;
    }


    /**
     * Creates and adds a button to the panel.
     *
     * @param icon An icon which is displayed on the button
     * @param label The name of the label which is displayed on the button
     * @param tooltip Tooltip text
     * @return The created button
     */
    public Button createButton (final Image icon, final String label, final String tooltip)
    {
        final Button button = new Button ();
        this.addButton (button, icon, label, tooltip);
        return button;
    }


    /**
     * Creates and adds a button to the panel. Beneath the button there is a drop down button
     * displayed which displays the given popup menu when clicked. The component checks for changes
     * in the menu. If it is empty the drop down button is disabled.
     *
     * @param icon An icon which is displayed on the button
     * @param label The name of the label which is displayed on the button
     * @param tooltip Tooltip text
     * @return The created button
     */
    public DropDownButton createDropDownButton (final Image icon, final String label, final String tooltip)
    {
        final DropDownButton dropDownButton = new DropDownButton (Functions.getText (label), icon);
        this.addComponent (dropDownButton, (Label) null, tooltip);
        return dropDownButton;
    }


    /**
     * Creates and adds a check box to the panel.
     *
     * @param label The text that is displayed beneath the check box
     * @return The created check box button
     */
    public CheckBox createCheckBox (final String label)
    {
        return this.createCheckBox (label, null);
    }


    /**
     * Creates and adds a check box to the panel.
     *
     * @param label The text that is displayed beneath the check box
     * @param tooltip Tooltip text
     * @return The created check box button
     */
    public CheckBox createCheckBox (final String label, final String tooltip)
    {
        final CheckBox button = new CheckBox ();
        this.addButton (button, null, label, tooltip);
        return button;
    }


    /**
     * Adds an abstract button to the panel.
     *
     * @param button The button to add
     * @param icon An icon which is displayed on the button
     * @param label The name of the label which is added to the button
     * @param tooltip Tooltip text
     */
    protected void addButton (final ButtonBase button, final Image icon, final String label, final String tooltip)
    {
        if (icon != null)
            button.setGraphic (new ImageView (icon));
        button.setText (Functions.getText (label));
        button.setMnemonicParsing (true);
        this.addComponent (button, (Label) null, tooltip);
    }


    /**
     * Creates a label.
     *
     * @param label The text of the label
     * @return The label
     */
    public Label createLabel (final String label)
    {
        return label == null ? null : new Label (Functions.getText (label));
    }


    /**
     * Adds a titled horizontal separator
     *
     * @param label The text to use for the separator
     * @return The separator
     */
    public TitledSeparator createSeparator (final String label)
    {
        final TitledSeparator separator = new TitledSeparator (label);
        this.addComponent (separator);
        return separator;
    }


    /**
     * Adds a label to the panel.
     *
     * @param label The label to add
     */
    public void addLabel (final Label label)
    {
        this.addComponent (label);
        label.setMnemonicParsing (true);
    }


    /**
     * Adds a component to the panel.
     *
     * @param component The component to add
     */
    public final void addComponent (final Node component)
    {
        this.addComponent (component, (Label) null, null, false, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The name of the label which is added to the component
     */
    public final void addComponent (final Node component, final String label)
    {
        this.addComponent (component, label, null, false);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     */
    public void addComponent (final Node component, final Label label)
    {
        this.addComponent (component, label, null, false, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The name of the label which is added to the component
     * @param tooltip Tooltip text
     */
    public void addComponent (final Node component, final String label, final String tooltip)
    {
        this.addComponent (component, label, tooltip, false);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     * @param tooltip Tooltip text
     */
    public void addComponent (final Node component, final Label label, final String tooltip)
    {
        this.addComponent (component, label, tooltip, false, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     * @param button Adds the button to the right of the component, may be null
     */
    public void addComponent (final Node component, final Label label, final Button button)
    {
        this.addComponent (component, label, null, false, button);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The name of the label which is added to the component
     * @param addScrollPane If true a the component is wrapped in a scrollpane
     */
    public void addComponent (final Node component, final String label, final boolean addScrollPane)
    {
        this.addComponent (component, label, null, addScrollPane);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     * @param addScrollPane If true a the component is wrapped in a scrollpane
     */
    public void addComponent (final Node component, final Label label, final boolean addScrollPane)
    {
        this.addComponent (component, label, null, addScrollPane, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The name of the label which is added to the component
     * @param tooltip Tooltip text
     * @param addScrollPane If true a the component is wrapped in a scrollpane
     */
    public void addComponent (final Node component, final String label, final String tooltip, final boolean addScrollPane)
    {
        this.addComponent (component, this.createLabel (label), tooltip, addScrollPane, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     * @param tooltip Tooltip text
     * @param addScrollPane If true a the component is wrapped in a scrollpane
     */
    public void addComponent (final Node component, final Label label, final String tooltip, final boolean addScrollPane)
    {
        this.addComponent (component, label, tooltip, addScrollPane, null);
    }


    /**
     * Adds a component with a label to the panel.
     *
     * @param component The component to add
     * @param label The label which is added to the component
     * @param tooltip Tooltip text
     * @param addScrollPane If true a the component is wrapped in a scrollpane
     * @param button Adds the button to the right of the component, may be null
     */
    public void addComponent (final Node component, final Label label, final String tooltip, final boolean addScrollPane, final Button button)
    {
        if (label != null)
        {
            this.addLabel (label);
            label.setLabelFor (component);
        }

        if (tooltip != null)
        {
            final Tooltip tip = new Tooltip (Functions.getText (tooltip));
            if (label != null)
                label.setTooltip (tip);
            if (component instanceof Control)
                ((Control) component).setTooltip (tip);
        }

        final Node wrappedComponent = addScrollPane ? new ScrollPane (component) : component;
        this.pane.getChildren ().add (button == null ? wrappedComponent : new BorderPane (wrappedComponent, null, button, null, null));
    }


    /**
     * Add a box panel to this box panel.
     *
     * @param boxPanel The box panel to add
     */
    public final void addComponent (final BasePanel boxPanel)
    {
        this.addComponent (boxPanel.getPane ());
    }


    /**
     * Removes the given component from the box panel.
     *
     * @param component The component to remove
     */
    public void removeComponent (final Node component)
    {
        this.pane.getChildren ().remove (component);
    }


    /**
     * Wraps this panel into a TitledPane.
     *
     * @param label The label to use for the titled pane
     * @return The titled pane
     */
    public TitledPane wrapInTitledPane (final String label)
    {
        final TitledPane wrapper = new TitledPane (Functions.getText (label), this.getPane ());
        wrapper.setMnemonicParsing (true);
        wrapper.setExpanded (false);
        return wrapper;
    }


    /**
     * Get the pane which contains all added widgets of the BoxPanel.
     *
     * @return A pane
     */
    public Pane getPane ()
    {
        return this.pane;
    }


    /**
     * Limits the given text control to a maximum number of characters.
     *
     * @param textControl The text control
     * @param max The maximum number of characters
     */
    public static void maxLength (final TextInputControl textControl, final int max)
    {
        textControl.addEventFilter (KeyEvent.KEY_TYPED, createMaxLengthFilter (max));
    }


    /**
     * Limits the given text control to only number characters (digits).
     *
     * @param textControl The text control
     */
    public static void onlyNumbers (final TextInputControl textControl)
    {
        textControl.addEventFilter (KeyEvent.KEY_TYPED, createOnlyNumbersFilter ());
    }


    /**
     * Creates an key event handler which can be assigned to a text input field to limit the number
     * of its characters.
     *
     * @param max The maximum number of characters to allow
     * @return The handler
     */
    public static EventHandler<KeyEvent> createMaxLengthFilter (final int max)
    {
        return e -> {
            final TextInputControl tx = (TextInputControl) e.getSource ();
            final String text = tx.getText ();
            if (text != null && text.length () >= max)
            {
                e.consume ();
            }
        };
    }


    /**
     * Creates an key event handler which can be assigned to a text input field to limit the input
     * to numbers.
     *
     * @return The handler
     */
    public static EventHandler<KeyEvent> createOnlyNumbersFilter ()
    {
        return e -> {
            for (final char c: e.getCharacter ().toCharArray ())
            {
                if (!Character.isDigit (c))
                {
                    e.consume ();
                    return;
                }
            }
        };
    }
}
