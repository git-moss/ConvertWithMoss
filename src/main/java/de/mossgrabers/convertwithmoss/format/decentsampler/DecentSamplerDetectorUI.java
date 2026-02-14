// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;


/**
 * Settings of the DecentSampler detector.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerDetectorUI extends MetadataSettingsUI
{
    private static final String DECENT_SAMPLER_LOG_UNSUPPORTED_ATTRIBUTES = "DecentSamplerLogUnsupportedAttributes";

    private CheckBox            logUnsupportedAttributesCheckBox;
    private boolean             logUnsupportedAttributes;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public DecentSamplerDetectorUI (final String prefix)
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

        panel.createSeparator ("@IDS_DS_OPTIONS");

        this.logUnsupportedAttributesCheckBox = panel.createCheckBox ("@IDS_DS_LOG_UNSUPPORTED_ATTRIBUTES");

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

        config.setBoolean (DECENT_SAMPLER_LOG_UNSUPPORTED_ATTRIBUTES, this.logUnsupportedAttributesCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.logUnsupportedAttributesCheckBox.setSelected (config.getBoolean (DECENT_SAMPLER_LOG_UNSUPPORTED_ATTRIBUTES, false));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.logUnsupportedAttributes = this.logUnsupportedAttributesCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (DECENT_SAMPLER_LOG_UNSUPPORTED_ATTRIBUTES);
        this.logUnsupportedAttributes = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (DECENT_SAMPLER_LOG_UNSUPPORTED_ATTRIBUTES);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should unsupported attributes be logged?
     *
     * @return True if they should be logged
     */
    public boolean logUnsupportedAttributes ()
    {
        return this.logUnsupportedAttributes;
    }
}
