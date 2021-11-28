// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.panel;

import de.mossgrabers.sampleconverter.ui.tools.Functions;

import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;


/**
 * Panel with two columns. The left column contains the labels and the right column the controls.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class TwoColsPanel extends BoxPanel
{
    protected GridPane gridpane = new GridPane ();
    private int        rowCounter;


    /**
     * Creates a Pane with two columns.
     */
    public TwoColsPanel ()
    {
        this (true);
    }


    /**
     * Creates a Pane with two columns.
     *
     * @param addSpace Adds space around the panel if true
     */
    public TwoColsPanel (final boolean addSpace)
    {
        super (Orientation.VERTICAL, addSpace);

        this.gridpane.getStyleClass ().add ("grid");

        final ColumnConstraints column1 = new ColumnConstraints ();
        final ColumnConstraints column2 = new ColumnConstraints ();
        column2.setFillWidth (true);
        column2.setHgrow (Priority.ALWAYS);
        this.gridpane.getColumnConstraints ().addAll (column1, column2);

        if (!addSpace)
            this.gridpane.getStyleClass ().add ("no-padding");
    }


    /** {@inheritDoc} */
    @Override
    public void addComponent (final Node component, final Label label, final String tooltip, final boolean addScrollPane, final Button button)
    {
        final Label l = label == null ? new Label ("") : label;
        l.setLabelFor (component);
        l.setMnemonicParsing (true);

        if (tooltip != null)
        {
            final Tooltip tip = new Tooltip (Functions.getText (tooltip));
            if (label != null)
                label.setTooltip (tip);
            if (component instanceof final Control control)
                control.setTooltip (tip);
        }

        this.addRow (button == null ? component : new BorderPane (component, null, button, null, null), l, addScrollPane);
    }


    /**
     * Adds a row.
     *
     * @param component The component to add (right column)
     * @param labelNode The node to use as the label (left column)
     * @param addScrollPane If true a scroll pane is added to the component
     */
    public void addRow (final Node component, final Node labelNode, final boolean addScrollPane)
    {
        if (addScrollPane)
        {
            final ScrollPane sp = new ScrollPane ();
            sp.setContent (component);
            this.gridpane.add (sp, 1, this.rowCounter);
        }
        else
            this.gridpane.add (component, 1, this.rowCounter);

        this.gridpane.add (labelNode, 0, this.rowCounter);
        GridPane.setValignment (labelNode, VPos.TOP);
        this.rowCounter++;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getPane ()
    {
        return this.gridpane;
    }
}
