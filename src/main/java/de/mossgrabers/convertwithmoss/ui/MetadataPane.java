// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;


/**
 * Encapsulates metadata fields used by different detectors.
 *
 * @author Jürgen Moßgraber
 */
public class MetadataPane implements IMetadataConfig
{
    private static final String PREFER_FOLDER_NAME = "PreferFolderName";
    private static final String DEFAULT_CREATOR    = "DefaultCreator";
    private static final String CREATORS           = "Creators";

    private final String        prefix;

    private CheckBox            preferFolderNameCheckBox;
    private TextField           defaultCreatorField;
    private TextField           creatorsField;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public MetadataPane (final String prefix)
    {
        this.prefix = prefix;
    }


    /**
     * Add the widgets to the given panel.
     *
     * @param panel The panel
     */
    public void addTo (final BoxPanel panel)
    {
        panel.createSeparator ("@IDS_METADATA_HEADER");

        this.preferFolderNameCheckBox = panel.createCheckBox ("@IDS_METADATA_PREFER_FOLDER");
        this.defaultCreatorField = panel.createField ("@IDS_METADATA_DEFAULT_CREATOR");
        this.creatorsField = panel.createField ("@IDS_METADATA_CREATORS", "@IDS_NOTIFY_COMMA", -1);
    }


    /**
     * Load the metadata settings.
     *
     * @param config The configuration file where the settings are stored
     */
    public void loadSettings (final BasicConfig config)
    {
        this.preferFolderNameCheckBox.setSelected (config.getBoolean (this.prefix + PREFER_FOLDER_NAME, false));
        this.defaultCreatorField.setText (config.getProperty (this.prefix + DEFAULT_CREATOR, "moss"));
        this.creatorsField.setText (config.getProperty (this.prefix + CREATORS, ""));
    }


    /**
     * Save the metadata settings.
     *
     * @param config The configuration file where the settings should be stored
     */
    public void saveSettings (final BasicConfig config)
    {
        config.setProperty (this.prefix + PREFER_FOLDER_NAME, Boolean.toString (this.preferFolderNameCheckBox.isSelected ()));
        config.setProperty (this.prefix + DEFAULT_CREATOR, this.defaultCreatorField.getText ());
        config.setProperty (this.prefix + CREATORS, this.creatorsField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean isPreferFolderName ()
    {
        return this.preferFolderNameCheckBox.isSelected ();
    }


    /** {@inheritDoc} */
    @Override
    public String getCreatorName ()
    {
        return this.defaultCreatorField.getText ();
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCreatorTags ()
    {
        return StringUtils.splitByComma (this.creatorsField.getText ());
    }
}
