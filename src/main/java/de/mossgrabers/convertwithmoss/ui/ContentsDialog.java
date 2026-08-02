// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.ContentsEntry;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.ControlFunctions;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.PseudoModalDialog;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
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
    private Label                    auditionLabel;
    private Button                   auditionButton;
    private List<ContentsEntry>      entries         = new ArrayList<> ();
    private final Set<ContentsEntry> selectedEntries = new HashSet<> ();
    private final Map<File, Integer> entriesPerFile  = new HashMap<> ();
    private Set<File>                filesWithOwnFolder;

    private final AuditionPlayer     auditionPlayer  = new AuditionPlayer ();
    private ISourceReader            sourceReader;
    private boolean                  isReading       = false;


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
        this.searchField.textProperty ().addListener ((_, _, _) -> this.fillTree ());

        final Button selectAllButton = new Button (Functions.getText ("@IDS_CONTENTS_SELECT_ALL"));
        selectAllButton.setOnAction (_ -> this.setAllSelected (true));
        final Button selectNoneButton = new Button (Functions.getText ("@IDS_CONTENTS_SELECT_NONE"));
        selectNoneButton.setOnAction (_ -> this.setAllSelected (false));

        final StackPane searchBox = ControlFunctions.addClearButton (this.searchField, "IDS_CONTENTS_SEARCH", "text-field-with-clear", "text-field-clear-button");
        final HBox topRow = new HBox (searchBox, selectAllButton, selectNoneButton);
        topRow.getStyleClass ().addAll ("contentsToolbar", "contentsDialogRow");
        topRow.setAlignment (Pos.CENTER_LEFT);
        HBox.setHgrow (searchBox, Priority.ALWAYS);

        this.treeView = new TreeView<> ();
        this.treeView.setShowRoot (false);
        // Make sure filesWithOwnFolder already exists
        this.filesWithOwnFolder = new HashSet<> ();
        this.treeView.setCellFactory (CheckBoxTreeCell.forTreeView (item -> ((CheckBoxTreeItem<Object>) item).selectedProperty (), new ContentsStringConverter (this.filesWithOwnFolder)));
        this.treeView.setPrefHeight (520);
        this.treeView.setPrefWidth (760);
        this.treeView.getSelectionModel ().selectedItemProperty ().addListener ((_, _, _) -> this.updateAuditionButton ());
        this.treeView.setOnMouseClicked (event -> {
            if (event.getClickCount () == 2)
                this.startAudition ();
        });

        this.selectionLabel = new Label ();
        this.auditionLabel = new Label ();
        this.auditionLabel.setMaxWidth (Double.MAX_VALUE);
        this.auditionLabel.setAlignment (Pos.CENTER_RIGHT);

        this.auditionButton = new Button (Functions.getText ("@IDS_CONTENTS_AUDITION"));
        this.auditionButton.setTooltip (new Tooltip (Functions.getText ("@IDS_CONTENTS_AUDITION_TOOLTIP")));
        this.auditionButton.setOnAction (_ -> this.toggleAudition ());
        this.auditionButton.setDisable (true);

        final HBox bottomRow = new HBox (this.selectionLabel, this.auditionLabel, this.auditionButton);
        bottomRow.getStyleClass ().addAll ("contentsToolbar", "contentsDialogRow");
        bottomRow.setAlignment (Pos.CENTER_LEFT);
        HBox.setHgrow (this.auditionLabel, Priority.ALWAYS);

        pane.setTop (topRow);
        pane.setCenter (this.treeView);
        pane.setBottom (bottomRow);

        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.addComponent (pane);

        this.setButtons ("@IDS_CONTENTS_DLG_OK", "@IDS_CONTENTS_DLG_CANCEL");

        this.traversalManager.add (this.searchField);
        this.traversalManager.add (selectAllButton);
        this.traversalManager.add (selectNoneButton);
        this.traversalManager.add (this.treeView);
        this.traversalManager.add (this.auditionButton);
        this.traversalManager.add (this.getOkButton ());
        this.traversalManager.add (this.getCancelButton ());
        this.traversalManager.register (this.owner);

        return panel.getPane ();
    }


    /**
     * Set the sources to display and the ones which are already selected.
     *
     * @param entries The found sources, all of them are selected
     * @param sourceReader Reads the source of one entry, so that it can be listened to
     */
    public void setEntries (final List<ContentsEntry> entries, final ISourceReader sourceReader)
    {
        this.entries = entries;
        this.sourceReader = sourceReader;
        this.selectedEntries.clear ();
        this.selectedEntries.addAll (entries);

        // Counted over all entries and not only over the displayed ones, so that the shape of the
        // tree does not change while a search is typed
        this.entriesPerFile.clear ();
        for (final ContentsEntry entry: entries)
            this.entriesPerFile.merge (entry.getSourceFile (), Integer.valueOf (1), Integer::sum);

        this.searchField.setText ("");
        this.auditionLabel.setText ("");
        this.fillTree ();
        this.updateAuditionButton ();
    }


    /**
     * Stop a note which is currently playing. Must be called when the dialog is closed, otherwise
     * the note keeps playing.
     */
    public void stopAudition ()
    {
        this.auditionPlayer.stop ();
    }


    /**
     * Start or stop playing the highlighted source.
     */
    private void toggleAudition ()
    {
        if (this.auditionPlayer.isPlaying ())
        {
            this.auditionPlayer.stop ();
            this.auditionLabel.setText ("");
            this.updateAuditionButton ();
            return;
        }
        this.startAudition ();
    }


    /**
     * Play one note of the highlighted source. Reading the source is done in the background, since
     * it means reading its file again, which takes a moment for a large disk image.
     */
    private void startAudition ()
    {
        final ContentsEntry entry = this.getHighlightedEntry ();
        if (entry == null || this.sourceReader == null || this.isReading)
            return;

        this.auditionPlayer.stop ();
        this.isReading = true;
        this.auditionLabel.setText (Functions.getMessage ("IDS_CONTENTS_AUDITION_READING", entry.getName ()));
        this.updateAuditionButton ();

        final Thread readThread = new Thread (() -> {

            String message = "";
            try
            {
                final IMultisampleSource source = this.sourceReader.read (entry);
                if (source == null)
                    message = Functions.getMessage ("IDS_CONTENTS_AUDITION_FAILED", entry.getName ());
                else if (!this.auditionPlayer.play (source, () -> Platform.runLater (this::endAudition)))
                    message = Functions.getMessage ("IDS_CONTENTS_AUDITION_SILENT", entry.getName ());
            }
            catch (final IOException | RuntimeException | OutOfMemoryError _)
            {
                message = Functions.getMessage ("IDS_CONTENTS_AUDITION_FAILED", entry.getName ());
            }

            final String labelText = message;
            Platform.runLater (() -> {

                this.isReading = false;
                this.auditionLabel.setText (labelText);
                this.updateAuditionButton ();

            });

        }, "AuditionRead");
        readThread.setDaemon (true);
        readThread.start ();
    }


    /**
     * The note was played to its end. Ignored if the next one is already being read, since that one
     * shows its own message.
     */
    private void endAudition ()
    {
        if (this.isReading)
            return;
        this.auditionLabel.setText ("");
        this.updateAuditionButton ();
    }


    /**
     * Show whether pressing the button starts or stops a note and only offer it if there is
     * something to play.
     */
    private void updateAuditionButton ()
    {
        final boolean isPlaying = this.auditionPlayer.isPlaying ();
        this.auditionButton.setText (Functions.getText (isPlaying ? "@IDS_CONTENTS_AUDITION_STOP" : "@IDS_CONTENTS_AUDITION"));
        this.auditionButton.setDisable (this.isReading || !isPlaying && this.getHighlightedEntry () == null);
    }


    /**
     * Get the source which is currently highlighted in the tree. This is not the same as the
     * selected sources, which are the ones with a checked box.
     *
     * @return The highlighted source, null if a container or nothing at all is highlighted
     */
    private ContentsEntry getHighlightedEntry ()
    {
        final TreeItem<Object> item = this.treeView.getSelectionModel ().getSelectedItem ();
        return item != null && item.getValue () instanceof final ContentsEntry entry ? entry : null;
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
        {
            final String filterText = this.searchField.getText ().toLowerCase ();
            if (filterText.isBlank ())
                this.selectedEntries.addAll (this.entries);
            else
                for (final ContentsEntry entry: this.entries)
                    if (entry.getName ().toLowerCase ().contains (filterText))
                        this.selectedEntries.add (entry);
        }

        this.fillTree ();
    }


    /**
     * Create the tree from the found sources. The folders of the source folder, each source file
     * which holds more than the source itself and each of its containers become a folder, the
     * sources themselves are the leaves.
     */
    private void fillTree ()
    {
        final String filterText = this.searchField.getText ().toLowerCase ();
        final CheckBoxTreeItem<Object> root = new CheckBoxTreeItem<> ("");
        final Map<String, CheckBoxTreeItem<Object>> folders = new HashMap<> ();
        this.filesWithOwnFolder.clear ();

        for (final ContentsEntry entry: this.entries)
        {
            if (!filterText.isBlank () && !entry.getName ().toLowerCase ().contains (filterText))
                continue;

            // Create the folders of the source folder, of the file and of all containers of the
            // source
            CheckBoxTreeItem<Object> parent = root;
            final StringBuilder path = new StringBuilder ();
            final List<String> folderNames = new ArrayList<> (entry.getFolderPath ());
            if (this.hasOwnFolder (entry))
            {
                this.filesWithOwnFolder.add (entry.getSourceFile ());
                folderNames.add (entry.getSourceFile ().getName ());
            }
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


    /**
     * Check if the file of a source becomes a folder of its own in the tree. This is only useful if
     * the file holds more than the one source, e.g. a bank or a disk image. A format where one file
     * simply is one preset would otherwise add a folder for each of its files, which only doubles
     * the presets in the tree.
     *
     * @param entry The source to check
     * @return True if the file becomes a folder
     */
    private boolean hasOwnFolder (final ContentsEntry entry)
    {
        if (!entry.getContainerPath ().isEmpty ())
            return true;
        final Integer count = this.entriesPerFile.get (entry.getSourceFile ());
        return count != null && count.intValue () > 1;
    }


    private void updateSelectionLabel ()
    {
        this.selectionLabel.setText (Functions.getMessage ("IDS_CONTENTS_SELECTED", Integer.toString (this.selectedEntries.size ()), Integer.toString (this.entries.size ())));
    }


    /**
     * Reads the source which belongs to one entry. The entries themselves only hold the information
     * to display, therefore the source needs to be read again to listen to it.
     */
    @FunctionalInterface
    public interface ISourceReader
    {
        /**
         * Read the source of the given entry.
         *
         * @param entry The entry
         * @return The source, null if it could not be read
         */
        IMultisampleSource read (ContentsEntry entry);
    }


    /**
     * Formats the tree items: the containers show their name, the sources additionally show their
     * number of zones, key range and category.
     */
    private static class ContentsStringConverter extends StringConverter<TreeItem<Object>>
    {
        private final Set<File> filesWithOwnFolder;


        /**
         * Constructor.
         *
         * @param filesWithOwnFolder The files which are displayed as a folder of their own
         */
        ContentsStringConverter (final Set<File> filesWithOwnFolder)
        {
            this.filesWithOwnFolder = filesWithOwnFolder;
        }


        /** {@inheritDoc} */
        @Override
        public String toString (final TreeItem<Object> item)
        {
            final Object value = item == null ? null : item.getValue ();
            if (value instanceof final ContentsEntry entry)
                return entry.getName () + "   (" + entry.getInfo () + this.formatFileName (entry) + ")";
            return value == null ? "" : value.toString ();
        }


        /**
         * Get the name of the file of a source, if it is not displayed anyway. The file is only
         * shown as a folder of its own when it holds more than the one source; otherwise its name
         * is the only hint which file a preset comes from, which matters when the preset is named
         * differently than its file.
         *
         * @param entry The source
         * @return The formatted file name, empty if the file is displayed as a folder or is named
         *         like the preset
         */
        private String formatFileName (final ContentsEntry entry)
        {
            final File sourceFile = entry.getSourceFile ();
            if (sourceFile == null || this.filesWithOwnFolder.contains (sourceFile))
                return "";
            final String fileName = sourceFile.getName ();
            return FileUtils.getNameWithoutType (sourceFile).equalsIgnoreCase (entry.getName ()) ? "" : ", " + fileName;
        }


        /** {@inheritDoc} */
        @Override
        public TreeItem<Object> fromString (final String string)
        {
            return null;
        }
    }
}
