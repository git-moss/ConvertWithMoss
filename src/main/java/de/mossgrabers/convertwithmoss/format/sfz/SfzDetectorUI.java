// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sfz;

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
 * Settings for the SFZ detector.
 *
 * @author Jürgen Moßgraber
 */
public class SfzDetectorUI extends MetadataSettingsUI
{
    private static final String SFZ_LOG_OPCODES = "SFZLogSupportedOpcodes";

    private CheckBox            logUnsupportedOpcodesCheckBox;
    private boolean             logUnsupportedOpcodes;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public SfzDetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        ////////////////////////////////////////////////////////////
        // Naming

        panel.createSeparator ("@IDS_SFZ_OPTIONS");

        this.logUnsupportedOpcodesCheckBox = panel.createCheckBox ("@IDS_SFZ_LOG_UNSUPPORTED_OPCODES");

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

        config.setBoolean (SFZ_LOG_OPCODES, this.logUnsupportedOpcodesCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.logUnsupportedOpcodesCheckBox.setSelected (config.getBoolean (SFZ_LOG_OPCODES, false));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.logUnsupportedOpcodes = this.logUnsupportedOpcodesCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (SFZ_LOG_OPCODES);
        this.logUnsupportedOpcodes = "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (SFZ_LOG_OPCODES);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should unsupported opcodes be logged?
     *
     * @return True to activate logging
     */
    public boolean logUnsupportedOpcodes ()
    {
        return this.logUnsupportedOpcodes;
    }
}
