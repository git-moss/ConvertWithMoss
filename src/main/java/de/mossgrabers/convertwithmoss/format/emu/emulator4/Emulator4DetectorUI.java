// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;


/**
 * Settings of the E-mu Emulator IV detector.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4DetectorUI extends MetadataSettingsUI
{
    private static final String PREPEND_BANK_NAME = "PrependBankName";

    private CheckBox            prependBankNameCheckBox;
    private boolean             prependBankName;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public Emulator4DetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        // -----------------------------------------------------------
        // Options

        panel.createSeparator ("@IDS_E4B_SOURCE_OPTIONS");

        this.prependBankNameCheckBox = panel.createCheckBox ("@IDS_E4B_PREPEND_BANK_NAME");

        // -----------------------------------------------------------
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        config.setBoolean (this.prefix + PREPEND_BANK_NAME, this.prependBankNameCheckBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        this.prependBankNameCheckBox.setSelected (config.getBoolean (this.prefix + PREPEND_BANK_NAME, true));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.prependBankName = this.prependBankNameCheckBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.prefix + PREPEND_BANK_NAME);
        this.prependBankName = value == null || "1".equals (value);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + PREPEND_BANK_NAME);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the name of the bank be prepended to the name of a preset?
     *
     * @return True to prepend the bank name
     */
    public boolean prependBankName ()
    {
        return this.prependBankName;
    }
}
