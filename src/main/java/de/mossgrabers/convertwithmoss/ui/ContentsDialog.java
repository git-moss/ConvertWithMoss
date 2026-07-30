// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.ContentsEntry;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.PseudoModalDialog;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.util.StringConverter;


/**
 * Dialog which shows all sources found in the source folder as a tree of their containers, e.g. the
 * presets of all banks of a disk image. The sources to convert can be selected there, which is
 * useful for a source which contains far more presets than the ones which should be converted.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ContentsDialog extends PseudoModalDialog
{
    private TreeView<Object>         treeView;
    private TextField                searchField;
    private Label                    selectionLabel;
    private List<ContentsEntry>      entries         = new ArrayList<> ();
    private final Set<ContentsEntry> selectedEntries = new HashSet<> ();


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     */
    protected ContentsDialog (final Stage owner)
    {
        super (owner, "@IDS_CONTENTS_DIALOG");
    }


    /** {@inheritDoc} */
    @Override
    protected Pane init ()
    {
        final BorderPane pane = new BorderPane ();

        this.searchField = new TextField ();
        this.searchField.setPromptText (Functions.getMessage ("IDS_CONTENTS_SEARCH"));
        this.searchField.textProperty ().addListener ((_, _, _) -> this.fillTree ());

        final Button selectAllButton = new Button (Functions.getText ("@IDS_CONTENTS_SELECT_ALL"));
        selectAllButton.setOnAction (_ -> this.setAllSelected (true));
        final Button selectNoneButton = new Button (Functions.getText ("@IDS_CONTENTS_SELECT_NONE"));
        selectNoneButton.setOnAction (_ -> this.setAllSelected (false));

        final HBox topRow = new HBox (this.searchField, selectAllButton, selectNoneButton);
        topRow.getStyleClass ().add ("contentsToolbar");
        topRow.setAlignment (Pos.CENTER_LEFT);
        HBox.setHgrow (this.searchField, Priority.ALWAYS);

        this.treeView = new TreeView<> ();
        this.treeView.setShowRoot (false);
        this.treeView.setCellFactory (CheckBoxTreeCell.forTreeView (item -> ((CheckBoxTreeItem<Object>) item).selectedProperty (), new ContentsStringConverter ()));
        this.treeView.setPrefHeight (520);
        this.treeView.setPrefWidth (760);

        this.selectionLabel = new Label ();

        pane.setTop (topRow);
        pane.setCenter (this.treeView);
        pane.setBottom (this.selectionLabel);

        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.addComponent (pane);

        this.setButtons ("@IDS_CONTENTS_DLG_OK", "@IDS_CONTENTS_DLG_CANCEL");

        this.traversalManager.add (this.searchField);
        this.traversalManager.add (selectAllButton);
        this.traversalManager.add (selectNoneButton);
        this.traversalManager.add (this.treeView);
        this.traversalManager.add (this.getOkButton ());
        this.traversalManager.add (this.getCancelButton ());
        this.traversalManager.register (this.owner);

        return panel.getPane ();
    }


    /**
     * Set the sources to display and the ones which are already selected.
     *
     * @param entries The found sources, all of them are selected
     */
    public void setEntries (final List<ContentsEntry> entries)
    {
        this.entries = entries;
        this.selectedEntries.clear ();
        this.selectedEntries.addAll (entries);
        this.searchField.setText ("");
        this.fillTree ();
    }


    /**
     * Get the selected sources.
     *
     * @return The selected sources
     */
    public Set<ContentsEntry> getSelectedEntries ()
    {
        return this.selectedEntries;
    }


    /**
     * Check if all found sources are selected, in which case no filtering is necessary at all.
     *
     * @return True if all are selected
     */
    public boolean areAllSelected ()
    {
        return this.selectedEntries.size () == this.entries.size ();
    }


    private void setAllSelected (final boolean isSelected)
    {
        this.selectedEntries.clear ();
        if (isSelected)
            this.selectedEntries.addAll (this.entries);
        this.fillTree ();
    }


    /**
     * Create the tree from the found sources. Each source file and each of its containers becomes a
     * folder, the sources themselves are the leaves.
     */
    private void fillTree ()
    {
        final String filterText = this.searchField.getText ().toLowerCase ();
        final CheckBoxTreeItem<Object> root = new CheckBoxTreeItem<> ("");
        final Map<String, CheckBoxTreeItem<Object>> folders = new HashMap<> ();

        for (final ContentsEntry entry: this.entries)
        {
            if (!filterText.isBlank () && !entry.getName ().toLowerCase ().contains (filterText))
                continue;

            // Create the folders of the file and of all containers of the source
            CheckBoxTreeItem<Object> parent = root;
            final StringBuilder path = new StringBuilder ();
            final List<String> folderNames = new ArrayList<> ();
            folderNames.add (entry.getSourceFile ().getName ());
            folderNames.addAll (entry.getContainerPath ());
            for (final String folderName: folderNames)
            {
                path.append ('/').append (folderName);
                final String key = path.toString ();
                CheckBoxTreeItem<Object> folder = folders.get (key);
                if (folder == null)
                {
                    folder = new CheckBoxTreeItem<> (folderName);
                    folder.setExpanded (true);
                    folders.put (key, folder);
                    parent.getChildren ().add (folder);
                }
                parent = folder;
            }

            final CheckBoxTreeItem<Object> item = new CheckBoxTreeItem<> (entry);
            item.setSelected (this.selectedEntries.contains (entry));
            item.selectedProperty ().addListener ((_, _, isSelected) -> {
                if (isSelected.booleanValue ())
                    this.selectedEntries.add (entry);
                else
                    this.selectedEntries.remove (entry);
                this.updateSelectionLabel ();
            });
            parent.getChildren ().add (item);
        }

        this.treeView.setRoot (root);
        this.updateSelectionLabel ();
    }


    private void updateSelectionLabel ()
    {
        this.selectionLabel.setText (Functions.getMessage ("IDS_CONTENTS_SELECTED", Integer.toString (this.selectedEntries.size ()), Integer.toString (this.entries.size ())));
    }


    /**
     * Formats the tree items: the containers show their name, the sources additionally show their
     * number of zones, key range and category.
     */
    private static class ContentsStringConverter extends StringConverter<TreeItem<Object>>
    {
        /** {@inheritDoc} */
        @Override
        public String toString (final TreeItem<Object> item)
        {
            final Object value = item == null ? null : item.getValue ();
            if (value instanceof final ContentsEntry entry)
                return entry.getName () + "   (" + entry.getInfo () + ")";
            return value == null ? "" : value.toString ();
        }


        /** {@inheritDoc} */
        @Override
        public TreeItem<Object> fromString (final String string)
        {
            return null;
        }
    }
}
