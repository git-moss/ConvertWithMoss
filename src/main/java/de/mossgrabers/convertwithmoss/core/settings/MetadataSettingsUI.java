// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;


/**
 * Encapsulates metadata fields used by different detectors.
 *
 * @author Jürgen Moßgraber
 */
public class MetadataSettingsUI implements IMetadataConfig, ICoreTaskSettings
{
    private static final String PREFER_FOLDER_NAME = "PreferFolderName";
    private static final String DEFAULT_CREATOR    = "DefaultCreator";
    private static final String CREATORS           = "Creators";

    private TitledSeparator     separator;
    protected final String      prefix;

    private CheckBox            preferFolderNameCheckBox;
    private TextField           defaultCreatorField;
    private TextField           creatorsField;

    private boolean             preferFolderName;
    private String              defaultCreator;
    private String []           creators;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public MetadataSettingsUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addTo (panel);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /**
     * Add the widgets to the given panel.
     *
     * @param panel The panel
     */
    public void addTo (final BoxPanel panel)
    {
        this.separator = panel.createSeparator ("@IDS_METADATA_HEADER");
        this.preferFolderNameCheckBox = panel.createCheckBox ("@IDS_METADATA_PREFER_FOLDER");
        this.defaultCreatorField = panel.createField ("@IDS_METADATA_DEFAULT_CREATOR");
        this.creatorsField = panel.createField ("@IDS_METADATA_CREATORS", "@IDS_NOTIFY_COMMA", -1);
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig configuration)
    {
        this.preferFolderNameCheckBox.setSelected (configuration.getBoolean (this.prefix + PREFER_FOLDER_NAME, false));
        this.defaultCreatorField.setText (configuration.getProperty (this.prefix + DEFAULT_CREATOR, "moss"));
        this.creatorsField.setText (configuration.getProperty (this.prefix + CREATORS, ""));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig configuration)
    {
        configuration.setProperty (this.prefix + PREFER_FOLDER_NAME, Boolean.toString (this.preferFolderNameCheckBox.isSelected ()));
        configuration.setProperty (this.prefix + DEFAULT_CREATOR, this.defaultCreatorField.getText ());
        configuration.setProperty (this.prefix + CREATORS, this.creatorsField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.preferFolderName = this.preferFolderNameCheckBox.isSelected ();
        this.defaultCreator = this.defaultCreatorField.getText ();
        this.creators = StringUtils.splitByComma (this.creatorsField.getText ());
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        String value = parameters.remove (this.prefix + PREFER_FOLDER_NAME);
        this.preferFolderName = "1".equals (value);

        this.defaultCreator = parameters.remove (this.prefix + DEFAULT_CREATOR);

        value = parameters.remove (this.prefix + CREATORS);
        this.creators = value == null ? new String [0] : StringUtils.splitByComma (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + PREFER_FOLDER_NAME,
            this.prefix + DEFAULT_CREATOR,
            this.prefix + CREATORS
        };
    }


    /** {@inheritDoc} */
    @Override
    public boolean isPreferFolderName ()
    {
        return this.preferFolderName;
    }


    /** {@inheritDoc} */
    @Override
    public String getCreatorName ()
    {
        return this.defaultCreator;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCreatorTags ()
    {
        return this.creators;
    }


    /**
     * Get the separator.
     *
     * @return The separator
     */
    public TitledSeparator getSeparator ()
    {
        return this.separator;
    }
}
