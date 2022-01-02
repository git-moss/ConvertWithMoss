// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.ui.tools.action;

import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;


/**
 * Action for opening a browse dialog and writing the selected file in the associated text field.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class BrowseAction extends Action
{
    private final TextField                   dest;
    private final String                      title;
    private final BasicConfig                 config;
    private final FileChooser.ExtensionFilter filter;
    private final boolean                     open;


    /**
     * Constructor.
     *
     * @param dest The text field which will receive the selected file name
     * @param open If true an open dialog is displayed otherwise a save dialog
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file
     */
    public BrowseAction (final TextField dest, final boolean open, final String title)
    {
        this (dest, open, title, null, null);
    }


    /**
     * Constructor.
     *
     * @param dest The text field which will receive the selected file name
     * @param open If true an open dialog is displayed otherwise a save dialog
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file
     * @param filter The file filter is used by the file chooser to filter out files from the user's
     *            view may be null
     */
    public BrowseAction (final TextField dest, final boolean open, final String title, final FileChooser.ExtensionFilter filter)
    {
        this (dest, open, title, null, filter);
    }


    /**
     * Constructor.
     *
     * @param dest The text field which will receive the selected file name
     * @param open If true an open dialog is displayed otherwise a save dialog
     * @param title The title of the dialog. If it starts with '@' the matching string is loaded
     *            from the properties file
     * @param config The configuration file
     * @param filter The filter to use
     */
    public BrowseAction (final TextField dest, final boolean open, final String title, final BasicConfig config, final FileChooser.ExtensionFilter filter)
    {
        this.dest = dest;
        this.open = open;
        this.title = title;
        this.config = config;
        this.filter = filter;
    }


    /**
     * Show the file chooser dialog and enter the selected filename into the text field.
     *
     * @param e Information about the action event
     */
    @Override
    public void handle (final ActionEvent e)
    {
        final Optional<File> file = Functions.getFileFromUser (this.dest.getScene ().getWindow (), this.open, this.title, this.config, this.filter);
        if (file.isPresent ())
            this.dest.setText (file.get ().getAbsolutePath ());
    }
}
