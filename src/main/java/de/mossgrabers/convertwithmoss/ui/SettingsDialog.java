// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.tools.ui.ControlFunctions;
import de.mossgrabers.tools.ui.PseudoModalDialog;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;


/**
 * Dialog for some global settings.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SettingsDialog extends PseudoModalDialog
{
    /** Check-box for creating a folder structure option. */
    public CheckBox createFolderStructureCheckbox;
    /** Check-box for only adding new files option. */
    public CheckBox addNewFilesCheckbox;
    /** Check-box for enabling the dark mode option. */
    public CheckBox enableDarkModeCheckbox;


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     */
    protected SettingsDialog (final Stage owner)
    {
        super (owner, "@IDS_SETTINGS_DIALOG");

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

        this.setButtons ("@IDS_SETTINGS_DLG_OK", "@IDS_SETTINGS_DLG_CANCEL");

        this.traversalManager.add (this.createFolderStructureCheckbox);
        this.traversalManager.add (this.addNewFilesCheckbox);
        this.traversalManager.add (this.enableDarkModeCheckbox);
        this.traversalManager.add (this.getOkButton ());
        this.traversalManager.add (this.getCancelButton ());
        this.traversalManager.register (this.owner);

        return panel.getPane ();
    }
}