// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sequential.prophetx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Sequential Prophet X creator: the category and the user bank of the device in
 * which to store the instrument, whether to write the import archive or a plain folder, plus the
 * shared WAV chunk options.
 *
 * @author Jürgen Moßgraber
 */
public class ProphetXCreatorUI extends WavChunkSettingsUI
{
    private static final String CATEGORY      = "Category";
    private static final String BANK          = "Bank";
    private static final String WRITE_ARCHIVE = "Archive";

    private ComboBox<String>    categoryBox;
    private ComboBox<String>    bankBox;
    private CheckBox            writeArchiveBox;
    private int                 categoryIndex = -1;
    private int                 bankIndex     = 0;
    private boolean             writeArchive  = true;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public ProphetXCreatorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_PROPHETX_SEPARATOR");

        final List<String> categories = new ArrayList<> ();
        categories.add (Functions.getMessage ("IDS_PROPHETX_CATEGORY_FROM_SOURCE"));
        categories.addAll (Arrays.asList (ProphetXTag.CATEGORY_FOLDERS));
        this.categoryBox = panel.createComboBox ("@IDS_PROPHETX_CATEGORY", categories);

        final List<String> banks = new ArrayList<> ();
        for (int i = 0; i < ProphetXTag.NUM_USER_BANKS; i++)
            banks.add (ProphetXTag.getUserBank (i));
        this.bankBox = panel.createComboBox ("@IDS_PROPHETX_BANK", banks);

        this.writeArchiveBox = panel.createCheckBox ("@IDS_PROPHETX_WRITE_ARCHIVE");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.categoryBox.getSelectionModel ().select (Math.clamp (config.getInteger (this.prefix + CATEGORY, -1) + 1L, 0, ProphetXTag.CATEGORY_FOLDERS.length));
        this.bankBox.getSelectionModel ().select (Math.clamp (config.getInteger (this.prefix + BANK, 0), 0, ProphetXTag.NUM_USER_BANKS - 1));
        this.writeArchiveBox.setSelected (config.getBoolean (this.prefix + WRITE_ARCHIVE, true));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setInteger (this.prefix + CATEGORY, this.categoryBox.getSelectionModel ().getSelectedIndex () - 1);
        config.setInteger (this.prefix + BANK, this.bankBox.getSelectionModel ().getSelectedIndex ());
        config.setBoolean (this.prefix + WRITE_ARCHIVE, this.writeArchiveBox.isSelected ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.categoryIndex = this.categoryBox.getSelectionModel ().getSelectedIndex () - 1;
        this.bankIndex = Math.max (this.bankBox.getSelectionModel ().getSelectedIndex (), 0);
        this.writeArchive = this.writeArchiveBox.isSelected ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String category = parameters.remove (this.prefix + CATEGORY);
        if (category == null || category.isBlank ())
            this.categoryIndex = -1;
        else
        {
            this.categoryIndex = ProphetXTag.getCategoryIndex (category);
            if (this.categoryIndex < 0)
            {
                notifier.logError ("IDS_PROPHETX_UNKNOWN_CATEGORY", category);
                return false;
            }
        }

        final String bank = parameters.remove (this.prefix + BANK);
        if (bank == null || bank.isBlank ())
            this.bankIndex = 0;
        else
        {
            this.bankIndex = ProphetXTag.getUserBankIndex (bank);
            if (this.bankIndex < 0)
            {
                notifier.logError ("IDS_PROPHETX_UNKNOWN_BANK", bank);
                return false;
            }
        }

        this.writeArchive = ICoreTaskSettings.parseBoolean (parameters.remove (this.prefix + WRITE_ARCHIVE), true);
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + CATEGORY);
        parameterNames.add (this.prefix + BANK);
        parameterNames.add (this.prefix + WRITE_ARCHIVE);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the selected category of the device.
     *
     * @return The index of the category or -1 to take the closest one to the category of the source
     */
    public int getCategoryIndex ()
    {
        return this.categoryIndex;
    }


    /**
     * Get the selected user bank of the device.
     *
     * @return The index of the bank, 0 to {@link ProphetXTag#NUM_USER_BANKS} - 1
     */
    public int getBankIndex ()
    {
        return this.bankIndex;
    }


    /**
     * Should the instrument be written as the import archive in the folder layout of the USB drive
     * instead of a plain folder?
     *
     * @return True to write the archive
     */
    public boolean isWriteArchive ()
    {
        return this.writeArchive;
    }
}
