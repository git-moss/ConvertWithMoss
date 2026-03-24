// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;


/**
 * The settings for the Sf2 detector.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2DetectorUI extends MetadataSettingsUI
{
    private static final String SF2_LOG_UNSUPPORTED_ATTRIBUTES = "Sf2LogUnsupportedAttributes";
    private static final String SF2_ADD_FILE_NAME_TAG          = "Sf2AddFileName";
    private static final String SF2_ADD_PROGRAM_NUMBER_TAG     = "Sf2AddProgramNumber";

    private CheckBox            logUnsupportedAttributesCheckBox;
    private CheckBox            addFileNameCheckBox;
    private CheckBox            addProgramNumberCheckBox;

    private boolean             logUnsupportedAttributes;
    private boolean             addFileName;
    private boolean             addProgramNumber;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public Sf2DetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_SF2_OPTIONS");
        this.logUnsupportedAttributesCheckBox = panel.createCheckBox ("@IDS_SF2_LOG_UNSUPPORTED_ATTRIBUTES");

        ////////////////////////////////////////////////////////////
        // Naming

        final TitledSeparator separator = panel.createSeparator ("@IDS_SF2_NAMING");
        separator.getStyleClass ().add ("titled-separator-pane");

        this.addFileNameCheckBox = panel.createCheckBox ("@IDS_SF2_NAMING_ADD_FILE_NAME");
        this.addProgramNumberCheckBox = panel.createCheckBox ("@IDS_SF2_NAMING_ADD_PROGRAM_NUMBER");

        ////////////////////////////////////////////////////////////
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");

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

        config.setBoolean (SF2_LOG_UNSUPPORTED_ATTRIBUTES, this.logUnsupportedAttributesCheckBox.isSelected ());
        config.setBoolean (SF2_ADD_FILE_NAME_TAG, this.addFileNameCheckBox.isSelected ());
        config.setBoolean (SF2_ADD_PROGRAM_NUMBER_TAG, this.addProgramNumberCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.logUnsupportedAttributesCheckBox.setSelected (config.getBoolean (SF2_LOG_UNSUPPORTED_ATTRIBUTES, false));
        this.addFileNameCheckBox.setSelected (config.getBoolean (SF2_ADD_FILE_NAME_TAG, false));
        this.addProgramNumberCheckBox.setSelected (config.getBoolean (SF2_ADD_PROGRAM_NUMBER_TAG, false));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.logUnsupportedAttributes = this.logUnsupportedAttributesCheckBox.isSelected ();
        this.addFileName = this.addFileNameCheckBox.isSelected ();
        this.addProgramNumber = this.addProgramNumberCheckBox.isSelected ();

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        String value = parameters.remove (SF2_LOG_UNSUPPORTED_ATTRIBUTES);
        this.logUnsupportedAttributes = "1".equals (value);

        value = parameters.remove (SF2_ADD_FILE_NAME_TAG);
        this.addFileName = "1".equals (value);

        value = parameters.remove (SF2_ADD_PROGRAM_NUMBER_TAG);
        this.addProgramNumber = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (SF2_LOG_UNSUPPORTED_ATTRIBUTES);
        parameterNames.add (SF2_ADD_FILE_NAME_TAG);
        parameterNames.add (SF2_ADD_PROGRAM_NUMBER_TAG);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the preset name be added to the filename?
     *
     * @return True to add it
     */
    public boolean addFileName ()
    {
        return this.addFileName;
    }


    /**
     * Should a program number be added?
     *
     * @return True if a program number should be added
     */
    public boolean addProgramNumber ()
    {
        return this.addProgramNumber;
    }


    /**
     * Should unsupported attributes in the source be logged?
     *
     * @return True if they should be logged
     */
    public boolean logUnsupportedAttributes ()
    {
        return this.logUnsupportedAttributes;
    }
}
