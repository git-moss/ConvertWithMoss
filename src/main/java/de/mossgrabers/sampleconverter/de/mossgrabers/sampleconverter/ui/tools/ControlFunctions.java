// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Provides some useful static functions for controls (ListViews, buttons).
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public final class ControlFunctions
{
    /**
     * Private constructor because this is a utility class.
     */
    private ControlFunctions ()
    {
        // Empty by intention
    }


    /**
     * Moves the selected item up in the given list box. If none is selected or it is the first
     * nothing happens.
     *
     * @param list A list view
     */
    public static void moveItemUp (final ListView<?> list)
    {
        final MultipleSelectionModel<?> selectionModel = list.getSelectionModel ();
        final int index = selectionModel.getSelectedIndex ();
        final ObservableList<?> items = list.getItems ();
        if (index > 0)
            Collections.swap (items, index, index - 1);
        selectionModel.select (index - 1);
    }


    /**
     * Moves the selected item down in the given list box. If none is selected or it is the last
     * nothing happens.
     *
     * @param list A list view
     */
    public static void moveItemDown (final ListView<?> list)
    {
        final MultipleSelectionModel<?> selectionModel = list.getSelectionModel ();
        final int index = selectionModel.getSelectedIndex ();
        final ObservableList<?> items = list.getItems ();
        if (index < items.size () - 1)
            Collections.swap (items, index, index + 1);
        selectionModel.select (index + 1);
    }


    /**
     * Adds the elements to the list view. If an element is already in the list view it is not added
     * again.
     *
     * @param <T> The content type of the list
     *
     * @param list The where to add the elements
     * @param elements The elements to add
     */
    public static <T> void addIfNotPresent (final ListView<T> list, final List<T> elements)
    {
        final ObservableList<T> items = list.getItems ();
        for (final T element: elements)
            if (!items.contains (element))
                items.add (element);
    }


    /**
     * Removes all selected items from the list view.
     *
     * @param <T> The content type of the list
     *
     * @param list The list view
     */
    public static <T> void removeSelectedItems (final ListView<T> list)
    {
        list.getItems ().removeAll (new ArrayList<> (list.getSelectionModel ().getSelectedItems ()));
    }


    /**
     * Returns the selected status of a toggle button (e.g. RadioButton) or a CheckBox (which is
     * strangely not a sub-class of ToggleButton).
     *
     * @param button The button to check
     * @return The selected status, false if it is not a ToggleButton or CheckBox
     */
    public static boolean isSelected (final ButtonBase button)
    {
        if (button instanceof final ToggleButton toggleButton)
            return toggleButton.isSelected ();
        else if (button instanceof final CheckBox checkbox)
            return checkbox.isSelected ();
        return false;
    }


    /**
     * Selects (checks) a toggle button (e.g. RadioButton) or a CheckBox (which is strangely not a
     * sub-class of ToggleButton).
     *
     * @param button The button to de-/selected
     * @param isSelected The selected state
     */
    public static void setSelected (final ButtonBase button, final boolean isSelected)
    {
        if (button instanceof final ToggleButton toggleButton)
            toggleButton.setSelected (isSelected);
        else if (button instanceof final CheckBox checkbox)
            checkbox.setSelected (isSelected);
    }


    /**
     * Sets the focus on the given control.
     *
     * @param control The control on which to set the focus
     */
    public static void setFocusOn (final Control control)
    {
        if (control != null)
            Platform.runLater (control::requestFocus);
    }
}
