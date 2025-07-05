// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;


/**
 * Encapsulates metadata fields and the search height field used by different detectors.
 *
 * @author Jürgen Moßgraber
 */
public class MetadataWithSearchHeightSettingsUI extends MetadataSettingsUI
{
    private static final String DIRECTORY_SEARCH = "DirectorySearch";

    private ComboBox<Integer>   directorySearchComboBox;
    private int                 directorySearch;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public MetadataWithSearchHeightSettingsUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_SAMPLE_FILE_SEARCH");
        this.directorySearchComboBox = panel.createComboBox ("@IDS_DIRECTORY_SEARCH", Integer.valueOf (0), Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (3), Integer.valueOf (4), Integer.valueOf (5), Integer.valueOf (6));
        this.directorySearchComboBox.getSelectionModel ().select (Integer.valueOf (1));

        this.addTo (panel);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setProperty (this.prefix + DIRECTORY_SEARCH, this.directorySearchComboBox.getSelectionModel ().getSelectedItem ().toString ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        final String value = config.getProperty (this.prefix + DIRECTORY_SEARCH, "1");
        this.directorySearchComboBox.getSelectionModel ().select (Integer.decode (value));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.directorySearch = this.directorySearchComboBox.getSelectionModel ().getSelectedItem ().intValue ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.prefix + DIRECTORY_SEARCH);
        if (value == null || value.isBlank ())
            this.directorySearch = 1;
        else
        {
            try
            {
                this.directorySearch = Integer.parseInt (value);
            }
            catch (final NumberFormatException ex)
            {
                notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", this.prefix + DIRECTORY_SEARCH);
                return false;
            }
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + DIRECTORY_SEARCH);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the directory search height.
     * 
     * @return The number of directories to go upwards to start searching for files.
     */
    public int getDirectorySearchHeight ()
    {
        return this.directorySearch;
    }
}
