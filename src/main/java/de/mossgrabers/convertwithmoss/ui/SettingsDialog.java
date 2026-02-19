// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.util.Optional;

import de.mossgrabers.tools.ui.AbstractDialog;
import de.mossgrabers.tools.ui.ControlFunctions;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.TraversalManager;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;


/**
 * Dialog for some global settings.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SettingsDialog extends AbstractDialog
{
    private final TraversalManager traversalManager    = new TraversalManager ();

    /** Check-box for creating a folder structure option. */
    public CheckBox                createFolderStructureCheckbox;
    /** Check-box for only adding new files option. */
    public CheckBox                addNewFilesCheckbox;
    /** Check-box for enabling the dark mode option. */
    public CheckBox                enableDarkModeCheckbox;
    /** Check-box for enabling the rename option. */
    public CheckBox                renameCheckbox;
    /** The path to the renaming mapping file. */
    public final TextField         renameFilePathField = new TextField ();

    private Button                 renameFilePathSelectButton;


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     */
    protected SettingsDialog (final Window owner)
    {
        super (owner, "@IDS_SETTINGS_DIALOG", true, true, 400, 300);

        this.basicInit ();

        ControlFunctions.setFocusOn (this.createFolderStructureCheckbox);
    }


    /** {@inheritDoc} */
    @Override
    protected Pane init ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.createFolderStructureCheckbox = panel.createCheckBox ("@IDS_MAIN_CREATE_FOLDERS", "@IDS_MAIN_CREATE_FOLDERS_TOOLTIP");
        this.addNewFilesCheckbox = panel.createCheckBox ("@IDS_MAIN_ADD_NEW", "@IDS_MAIN_ADD_NEW_TOOLTIP");
        this.enableDarkModeCheckbox = panel.createCheckBox ("@IDS_MAIN_ENABLE_DARK_MODE", "@IDS_MAIN_ENABLE_DARK_MODE_TOOLTIP");
        panel.createSeparator ("@IDS_MAIN_RENAMING_HEADER");

        // Rename CSV file section
        this.renameCheckbox = panel.createCheckBox ("@IDS_MAIN_RENAMING", "@IDS_MAIN_RENAMING_TOOLTIP");
        this.renameCheckbox.getStyleClass ().add ("paddingRight");
        this.renameCheckbox.setOnAction (_ -> this.updateRenamingControls ());
        this.renameFilePathSelectButton = new Button (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE"));
        this.renameFilePathSelectButton.setOnAction (_ -> {

            final Optional<File> file = Functions.getFileFromUser (this.getWindow (), true, Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_HEADER"), null, new FileChooser.ExtensionFilter (Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_DESCRIPTION"), Functions.getText ("@IDS_MAIN_SELECT_RENAMING_FILE_FILTER")));
            if (file.isPresent ())
                this.renameFilePathField.setText (file.get ().getAbsolutePath ());

        });

        final BorderPane sourceBottomPane = new BorderPane (this.renameFilePathField);
        sourceBottomPane.setRight (this.renameFilePathSelectButton);
        panel.addComponent (sourceBottomPane);

        this.setButtons ("@IDS_SETTINGS_DLG_OK", "@IDS_SETTINGS_DLG_CANCEL");

        this.traversalManager.add (this.createFolderStructureCheckbox);
        this.traversalManager.add (this.addNewFilesCheckbox);
        this.traversalManager.add (this.enableDarkModeCheckbox);
        this.traversalManager.add (this.renameCheckbox);
        this.traversalManager.add (this.renameFilePathField);
        this.traversalManager.add (this.renameFilePathSelectButton);
        this.traversalManager.add (this.getOKButton ());
        this.traversalManager.add (this.getCancelButton ());

        final Stage stage = (Stage) this.getDialogPane ().getScene ().getWindow ();
        this.traversalManager.register (stage);

        return panel.getPane ();
    }


    /**
     * Enables/disables the renaming controls depending on the selection status of the renaming
     * checkbox.
     */
    private void updateRenamingControls ()
    {
        this.renameFilePathField.setDisable (!this.renameCheckbox.isSelected ());
        this.renameFilePathSelectButton.setDisable (!this.renameCheckbox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    protected boolean onOk ()
    {
        return this.verifyRenameFile ();
    }


    /**
     * Set and check folder for existence.
     *
     * @return True if OK
     */
    private boolean verifyRenameFile ()
    {
        if (!this.renameCheckbox.isSelected ())
            return true;

        final String renamingCSVFile = this.renameFilePathField.getText ();
        if (renamingCSVFile.isBlank ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NO_FILE_SPECIFIED");
            this.renameFilePathField.requestFocus ();
            return false;
        }

        final File renamingCSV = new File (renamingCSVFile);
        if (!renamingCSV.exists ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_DOES_NOT_EXIST", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        if (!renamingCSV.canRead ())
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NOT_READABLE", renamingCSVFile);
            this.renameFilePathField.requestFocus ();
            return false;
        }

        return true;
    }

}
