// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

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
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;


/**
 * Settings for the Waldorf QPAT creator.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreatorUI extends WavChunkSettingsUI
{
    private static final String QPAT_LIMIT_TO_16_441     = "QPATLimitTo16441";
    private static final String QPAT_AUTHOR              = "QPATAuthor";
    private static final String QPAT_BANK                = "QPATBank";
    private static final String QPAT_NUMBER_PREFIX       = "QPATNumberPrefix";
    private static final String QPAT_SHORT_FILE_NAMES    = "QPATShortFileNames";
    private static final String QPAT_NUMBER_PREFIX_START = "QPATNumberPrefixStart";
    private static final String QPAT_LAYERS              = "QPATLayers";
    /**
     * The option which the layers option replaced, still read so that its setting is taken over.
     */
    private static final String QPAT_SECOND_LAYER        = "QPATUseSecondLayer";
    /**
     * The default of the layers option: 2 layers, which every instrument of the family plays. A
     * preset which needs more than the 3 oscillators of one layer otherwise plays two of its groups
     * alternately.
     */
    private static final int    DEFAULT_LAYERS           = 2;
    /** The choices of the layers option: the maximum number of layers of a patch. */
    private static final int [] LAYER_OPTIONS            =
    {
        1,
        2,
        3,
        4
    };

    private CheckBox            limitTo16441CheckBox;
    private TextField           authorField;
    private TextField           bankField;
    private CheckBox            numberPrefixCheckBox;
    private TextField           numberPrefixStartField;
    private CheckBox            shortFileNamesCheckBox;
    private ComboBox<String>    layersBox;
    private boolean             limitTo16441;
    private String              author                   = "";
    private String              bank                     = "";
    private boolean             numberPrefix;
    private int                 numberPrefixStart        = 0;
    private boolean             shortFileNames;
    private int                 maximumLayers            = DEFAULT_LAYERS;


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
        limitToPatchInfo (this.authorField);
        this.bankField = panel.createField ("@IDS_QPAT_BANK");
        limitToPatchInfo (this.bankField);
        this.numberPrefixCheckBox = panel.createCheckBox ("@IDS_QPAT_NUMBER_PREFIX");
        this.numberPrefixStartField = panel.createPositiveIntegerField ("@IDS_QPAT_NUMBER_PREFIX_START");
        this.numberPrefixStartField.disableProperty ().bind (this.numberPrefixCheckBox.selectedProperty ().not ());
        this.shortFileNamesCheckBox = panel.createCheckBox ("@IDS_QPAT_SHORT_FILE_NAMES");
        this.layersBox = panel.createComboBox ("@IDS_QPAT_LAYERS", List.of (Functions.getText ("@IDS_QPAT_LAYERS_ONE"), Functions.getText ("@IDS_QPAT_LAYERS_TWO"), Functions.getText ("@IDS_QPAT_LAYERS_THREE"), Functions.getText ("@IDS_QPAT_LAYERS_FOUR")));

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
        this.shortFileNamesCheckBox.setSelected (config.getBoolean (QPAT_SHORT_FILE_NAMES, false));
        // The setting of the option which this one replaced is taken over
        // Only a setting of the replaced option which leaves the second layer off selects 1 layer
        final int layers = config.getInteger (QPAT_LAYERS, config.getBoolean (QPAT_SECOND_LAYER, true) ? DEFAULT_LAYERS : 1);
        this.layersBox.getSelectionModel ().select (layersToIndex (layers));

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
        config.setBoolean (QPAT_SHORT_FILE_NAMES, this.shortFileNamesCheckBox.isSelected ());
        config.setInteger (QPAT_LAYERS, LAYER_OPTIONS[selectedLayerIndex (this.layersBox)]);

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
        this.shortFileNames = this.shortFileNamesCheckBox.isSelected ();
        this.maximumLayers = LAYER_OPTIONS[selectedLayerIndex (this.layersBox)];
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (QPAT_LIMIT_TO_16_441);
        this.limitTo16441 = ICoreTaskSettings.parseBoolean (value, true);

        final String authorValue = parameters.remove (QPAT_AUTHOR);
        this.author = authorValue == null ? "" : authorValue;
        final String bankValue = parameters.remove (QPAT_BANK);
        this.bank = bankValue == null ? "" : bankValue;
        if (!checkFitsIntoPatchInfo (notifier, QPAT_AUTHOR, this.author) || !checkFitsIntoPatchInfo (notifier, QPAT_BANK, this.bank))
            return false;

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

        this.shortFileNames = "1".equals (parameters.remove (QPAT_SHORT_FILE_NAMES));

        final String layersValue = parameters.remove (QPAT_LAYERS);
        final String secondLayerValue = parameters.remove (QPAT_SECOND_LAYER);
        if (layersValue == null || layersValue.isBlank ())
            this.maximumLayers = secondLayerValue == null || "1".equals (secondLayerValue) ? DEFAULT_LAYERS : 1;
        else
        {
            this.maximumLayers = parseLayers (layersValue);
            if (this.maximumLayers < 0)
            {
                notifier.logError ("IDS_QPAT_CLI_LAYERS", QPAT_LAYERS);
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
        parameterNames.add (QPAT_LIMIT_TO_16_441);
        parameterNames.add (QPAT_AUTHOR);
        parameterNames.add (QPAT_BANK);
        parameterNames.add (QPAT_NUMBER_PREFIX);
        parameterNames.add (QPAT_NUMBER_PREFIX_START);
        parameterNames.add (QPAT_SHORT_FILE_NAMES);
        parameterNames.add (QPAT_LAYERS);
        parameterNames.add (QPAT_SECOND_LAYER);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the maximum number of layers to write into one patch. Each layer plays up to 3 sample
     * maps, so one layer holds 3 groups of the source, two hold 6, three hold 9 and four hold 12.
     *
     * @return The maximum number of layers: 1 to 4
     */
    public int getMaximumLayers ()
    {
        return this.maximumLayers;
    }


    /**
     * Limit a field to the text which the device shows completely in the patch information of its
     * 'Load Patch' page. Of a longer text, e.g. a pasted one, only the part which fits is taken.
     *
     * @param field The field to limit
     */
    private static void limitToPatchInfo (final TextField field)
    {
        field.setTooltip (new Tooltip (Functions.getText ("@IDS_QPAT_PATCH_INFO_TOOLTIP")));
        field.setTextFormatter (new TextFormatter<> (change -> {

            // Removing characters never makes a text wider
            final String addedText = change.getText ();
            if (addedText.isEmpty () || WaldorfQpatDisplay.fitsIntoPatchInfo (change.getControlNewText ()))
                return change;

            final String controlText = change.getControlText ();
            final String head = controlText.substring (0, change.getRangeStart ());
            final String tail = controlText.substring (change.getRangeEnd ());
            int length = addedText.length ();
            while (length > 0 && !WaldorfQpatDisplay.fitsIntoPatchInfo (head + addedText.substring (0, length) + tail))
                length--;
            if (length == 0)
                return null;

            change.setText (addedText.substring (0, length));
            final int caret = change.getRangeStart () + length;
            change.selectRange (caret, caret);
            return change;

        }));
    }


    /**
     * Check if the value of a parameter is shown completely in the patch information of the 'Load
     * Patch' page of the device.
     *
     * @param notifier Where to report an error
     * @param parameterName The name of the parameter
     * @param value The value of the parameter
     * @return True if it fits
     */
    private static boolean checkFitsIntoPatchInfo (final INotifier notifier, final String parameterName, final String value)
    {
        if (WaldorfQpatDisplay.fitsIntoPatchInfo (value))
            return true;
        notifier.logError ("IDS_QPAT_TEXT_DOES_NOT_FIT", parameterName, WaldorfQpatDisplay.getVisiblePart (value));
        return false;
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
     * Should the file name be shortened to the name which the device displays? A preset which comes
     * from a bank carries the bank in front of its name, and the file name normally keeps that
     * whole name, which is far more than the import screen of the device shows.
     *
     * @return True to use the displayed name as the file name
     */
    public boolean useShortFileNames ()
    {
        return this.shortFileNames;
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


    /**
     * Get the index of the selected layer option.
     *
     * @param box The combo box
     * @return The index into LAYER_OPTIONS
     */
    private static int selectedLayerIndex (final ComboBox<String> box)
    {
        return Math.clamp (box.getSelectionModel ().getSelectedIndex (), 0, LAYER_OPTIONS.length - 1);
    }


    /**
     * Get the index of the option which holds the given number of layers.
     *
     * @param layers The number of layers
     * @return The index into LAYER_OPTIONS, the first one if the number is not an option
     */
    private static int layersToIndex (final int layers)
    {
        for (int i = 0; i < LAYER_OPTIONS.length; i++)
            if (LAYER_OPTIONS[i] == layers)
                return i;
        return 0;
    }


    /**
     * Parse the value of the layers parameter.
     *
     * @param value The value
     * @return The number of layers or -1 if the value is not one of the options
     */
    private static int parseLayers (final String value)
    {
        try
        {
            final int layers = Integer.parseInt (value.trim ());
            return LAYER_OPTIONS[layersToIndex (layers)] == layers ? layers : -1;
        }
        catch (final NumberFormatException _)
        {
            return -1;
        }
    }
}
