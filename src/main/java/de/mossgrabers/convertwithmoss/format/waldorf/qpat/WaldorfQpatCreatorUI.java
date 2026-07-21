// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;


/**
 * Settings for the Waldorf QPAT creator.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreatorUI extends WavChunkSettingsUI
{
    private static final String QPAT_LIMIT_TO_16_441    = "QPATLimitTo16441";
    private static final String QPAT_AUTHOR             = "QPATAuthor";
    private static final String QPAT_BANK               = "QPATBank";
    private static final String QPAT_NUMBER_PREFIX      = "QPATNumberPrefix";
    private static final String QPAT_NUMBER_PREFIX_START = "QPATNumberPrefixStart";

    private CheckBox            limitTo16441CheckBox;
    private TextField           authorField;
    private TextField           bankField;
    private CheckBox            numberPrefixCheckBox;
    private TextField           numberPrefixStartField;
    private boolean             limitTo16441;
    private String              author                  = "";
    private String              bank                    = "";
    private boolean             numberPrefix;
    private int                 numberPrefixStart       = 0;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public WaldorfQpatCreatorUI (final String prefix)
    {
        super (prefix, true, false, false, false);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_QPAT_SEPARATOR");
        this.limitTo16441CheckBox = panel.createCheckBox ("@IDS_QPAT_RESAMPLE_TO_16_441");
        this.authorField = panel.createField ("@IDS_QPAT_AUTHOR");
        this.bankField = panel.createField ("@IDS_QPAT_BANK");
        this.numberPrefixCheckBox = panel.createCheckBox ("@IDS_QPAT_NUMBER_PREFIX");
        this.numberPrefixStartField = panel.createPositiveIntegerField ("@IDS_QPAT_NUMBER_PREFIX_START");
        this.numberPrefixStartField.disableProperty ().bind (this.numberPrefixCheckBox.selectedProperty ().not ());

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.limitTo16441CheckBox.setSelected (config.getBoolean (QPAT_LIMIT_TO_16_441, true));
        this.authorField.setText (config.getProperty (QPAT_AUTHOR, ""));
        this.bankField.setText (config.getProperty (QPAT_BANK, ""));
        this.numberPrefixCheckBox.setSelected (config.getBoolean (QPAT_NUMBER_PREFIX, false));
        this.numberPrefixStartField.setText (Integer.toString (config.getInteger (QPAT_NUMBER_PREFIX_START, 0)));

        super.loadSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (QPAT_LIMIT_TO_16_441, this.limitTo16441CheckBox.isSelected ());
        config.setProperty (QPAT_AUTHOR, this.authorField.getText ());
        config.setProperty (QPAT_BANK, this.bankField.getText ());
        config.setBoolean (QPAT_NUMBER_PREFIX, this.numberPrefixCheckBox.isSelected ());
        config.setInteger (QPAT_NUMBER_PREFIX_START, this.parseNumberPrefixStart ());

        super.saveSettings (config);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.limitTo16441 = this.limitTo16441CheckBox.isSelected ();
        this.author = this.authorField.getText ();
        this.bank = this.bankField.getText ();
        this.numberPrefix = this.numberPrefixCheckBox.isSelected ();
        this.numberPrefixStart = this.parseNumberPrefixStart ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (QPAT_LIMIT_TO_16_441);
        this.limitTo16441 = "1".equals (value);

        final String authorValue = parameters.remove (QPAT_AUTHOR);
        this.author = authorValue == null ? "" : authorValue;
        final String bankValue = parameters.remove (QPAT_BANK);
        this.bank = bankValue == null ? "" : bankValue;

        this.numberPrefix = "1".equals (parameters.remove (QPAT_NUMBER_PREFIX));
        final String startValue = parameters.remove (QPAT_NUMBER_PREFIX_START);
        if (startValue == null || startValue.isBlank ())
            this.numberPrefixStart = 0;
        else
            try
            {
                this.numberPrefixStart = Integer.parseInt (startValue);
            }
            catch (final NumberFormatException _)
            {
                notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", QPAT_NUMBER_PREFIX_START);
                return false;
            }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (QPAT_LIMIT_TO_16_441);
        parameterNames.add (QPAT_AUTHOR);
        parameterNames.add (QPAT_BANK);
        parameterNames.add (QPAT_NUMBER_PREFIX);
        parameterNames.add (QPAT_NUMBER_PREFIX_START);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    private int parseNumberPrefixStart ()
    {
        try
        {
            return Math.max (0, Integer.parseInt (this.numberPrefixStartField.getText ().trim ()));
        }
        catch (final NumberFormatException _)
        {
            return 0;
        }
    }


    /**
     * Should the output samples be limited to a maximum of 16bit / 44.1kHz?
     *
     * @return True to limit
     */
    public boolean limitTo16441 ()
    {
        return this.limitTo16441;
    }


    /**
     * Get the author (creator) to write into the preset. When not empty it overrides the source
     * metadata creator; the device shows it as the preset's Author.
     *
     * @return The author, or an empty string to keep the source's creator
     */
    public String getAuthor ()
    {
        return this.author;
    }


    /**
     * Get the bank to write into the preset. When not empty it overrides the source metadata
     * description; the device shows it as the preset's Bank.
     *
     * @return The bank, or an empty string to keep the source's value
     */
    public String getBank ()
    {
        return this.bank;
    }


    /**
     * Should the file names be prefixed with an import number? This mirrors the naming of the
     * device's own preset export (e.g. '05002-Name.qpat'); on import the device assigns the preset
     * to that number.
     *
     * @return True to add the number prefix
     */
    public boolean addNumberPrefix ()
    {
        return this.numberPrefix;
    }


    /**
     * Get the import number to use for the first written preset; each further preset increases the
     * number by one.
     *
     * @return The first import number
     */
    public int getNumberPrefixStart ()
    {
        return this.numberPrefixStart;
    }
}
