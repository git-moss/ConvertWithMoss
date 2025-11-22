// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.samplefile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;


/**
 * Settings for the sample file detector.
 *
 * @author Jürgen Moßgraber
 */
public class SampleFileDetectorUI extends MetadataSettingsUI
{
    private static final String                      SAMPLEFILE_TYPE                    = "samplefileType";
    private static final String                      SAMPLEFILE_GROUP_DETECTION_PATTERN = "samplefileDetectionPattern";
    private static final String                      SAMPLEFILE_IS_ASCENDING            = "samplefileIsAscending";
    private static final String                      SAMPLEFILE_MONO_SPLITS_PATTERN     = "samplefileMonoSplitPattern";
    private static final String                      SAMPLEFILE_CROSSFADE_NOTES         = "samplefileCrossfadeNotes";
    private static final String                      SAMPLEFILE_CROSSFADE_VELOCITIES    = "samplefileCrossfadeVelocities";
    private static final String                      SAMPLEFILE_POSTFIX                 = "samplefilePostfix";
    private static final String                      SAMPLEFILE_IGNORE_LOOPS            = "samplefileIgnoreLoops";

    private static final SampleFileType []           FILE_TYPES                         =
    {
        new AiffSampleFileType (),
        new FlacSampleFileType (),
        new NcwSampleFileType (),
        new OggSampleFileType (),
        new WavSampleFileType ()
    };

    private static final Map<String, SampleFileType> SAMPLE_FILE_TYPES_BY_NAME          = new HashMap<> ();
    static
    {
        SAMPLE_FILE_TYPES_BY_NAME.put ("AIFF", FILE_TYPES[0]);
        SAMPLE_FILE_TYPES_BY_NAME.put ("FLAC", FILE_TYPES[1]);
        SAMPLE_FILE_TYPES_BY_NAME.put ("NCW", FILE_TYPES[2]);
        SAMPLE_FILE_TYPES_BY_NAME.put ("OGG", FILE_TYPES[3]);
        SAMPLE_FILE_TYPES_BY_NAME.put ("WAV", FILE_TYPES[4]);
    }

    private TextField                  detectionPatternField;
    private ToggleGroup                sortAscendingGroup;
    private TextField                  monoSplitsField;
    private TextField                  crossfadeNotesField;
    private TextField                  crossfadeVelocitiesField;
    private TextField                  postfixField;
    private CheckBox                   ignoreLoops;
    private final CheckBox []          sampleFileTypeCheckBoxes = new CheckBox [FILE_TYPES.length];

    private final List<SampleFileType> sampleFileTypes          = new ArrayList<> ();
    private int                        crossfadeNotes;
    private int                        crossfadeVelocities;
    private String []                  groupPatterns;
    private boolean                    isAscending;
    private String []                  monoSplitPatterns;
    private String []                  postfixTexts;
    private boolean                    shouldIgnoreLoops;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the properties tags
     */
    public SampleFileDetectorUI (final String prefix)
    {
        super (prefix);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        super.saveSettings (config);

        for (int i = 0; i < FILE_TYPES.length; i++)
            config.setBoolean (SAMPLEFILE_TYPE + i, this.sampleFileTypeCheckBoxes[i].isSelected ());

        config.setProperty (SAMPLEFILE_GROUP_DETECTION_PATTERN, this.detectionPatternField.getText ());
        config.setProperty (SAMPLEFILE_IS_ASCENDING, Boolean.toString (this.sortAscendingGroup.getToggles ().get (1).isSelected ()));
        config.setProperty (SAMPLEFILE_MONO_SPLITS_PATTERN, this.monoSplitsField.getText ());
        config.setProperty (SAMPLEFILE_CROSSFADE_NOTES, this.crossfadeNotesField.getText ());
        config.setProperty (SAMPLEFILE_CROSSFADE_VELOCITIES, this.crossfadeVelocitiesField.getText ());
        config.setProperty (SAMPLEFILE_POSTFIX, this.postfixField.getText ());
        config.setBoolean (SAMPLEFILE_IGNORE_LOOPS, this.ignoreLoops.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        super.loadSettings (config);

        for (int i = 0; i < FILE_TYPES.length; i++)
            this.sampleFileTypeCheckBoxes[i].setSelected (config.getBoolean (SAMPLEFILE_TYPE + i, true));

        this.detectionPatternField.setText (config.getProperty (SAMPLEFILE_GROUP_DETECTION_PATTERN, "_ms*_,S_*_"));
        this.sortAscendingGroup.selectToggle (this.sortAscendingGroup.getToggles ().get (config.getBoolean (SAMPLEFILE_IS_ASCENDING, true) ? 1 : 0));
        this.monoSplitsField.setText (config.getProperty (SAMPLEFILE_MONO_SPLITS_PATTERN, "_L"));
        this.crossfadeNotesField.setText (Integer.toString (config.getInteger (SAMPLEFILE_CROSSFADE_NOTES, 0)));
        this.crossfadeVelocitiesField.setText (Integer.toString (config.getInteger (SAMPLEFILE_CROSSFADE_VELOCITIES, 0)));
        this.postfixField.setText (config.getProperty (SAMPLEFILE_POSTFIX, ""));
        this.ignoreLoops.setSelected (config.getBoolean (SAMPLEFILE_IGNORE_LOOPS, false));
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        final String comma = Functions.getMessage ("IDS_NOTIFY_COMMA");

        ////////////////////////////////////////////////////////////
        // Sample file types

        panel.createSeparator ("@IDS_FILE_TYPES");
        for (int i = 0; i < FILE_TYPES.length; i++)
            this.sampleFileTypeCheckBoxes[i] = panel.createCheckBox (FILE_TYPES[i].getName ());

        ////////////////////////////////////////////////////////////
        // Groups

        panel.createSeparator ("@IDS_FILE_GROUPS").getStyleClass ().add ("titled-separator-pane");

        // Layer detection pattern
        this.detectionPatternField = panel.createField ("@IDS_FILE_DETECTION", comma, -1);

        // Order of group numbering
        final BoxPanel orderPanel = new BoxPanel (Orientation.HORIZONTAL);
        this.sortAscendingGroup = new ToggleGroup ();
        final RadioButton order1 = orderPanel.createRadioButton ("@IDS_FILE_GROUPS_DESC");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_FILE_GROUP_NUMBERING"));
        order1.setToggleGroup (this.sortAscendingGroup);
        final RadioButton order2 = orderPanel.createRadioButton ("@IDS_FILE_GROUPS_ASC");
        order2.setToggleGroup (this.sortAscendingGroup);
        order2.setAccessibleHelp (Functions.getMessage ("IDS_FILE_GROUP_NUMBERING"));
        final BorderPane borderPane = new BorderPane ();
        final Label orderLabel = orderPanel.createLabel ("@IDS_FILE_GROUP_NUMBERING");
        borderPane.setLeft (orderLabel);
        BorderPane.setAlignment (orderLabel, Pos.CENTER_LEFT);
        borderPane.setRight (orderPanel.getPane ());
        panel.addComponent (borderPane);

        this.monoSplitsField = panel.createField ("@IDS_FILE_MONO_STEREO", comma, -1);

        ////////////////////////////////////////////////////////////
        // Metadata

        this.addTo (panel);
        this.getSeparator ().getStyleClass ().add ("titled-separator-pane");

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_FILE_OPTIONS").getStyleClass ().add ("titled-separator-pane");

        this.crossfadeNotesField = panel.createPositiveIntegerField ("@IDS_FILE_CROSSFADE_NOTES");
        this.crossfadeVelocitiesField = panel.createPositiveIntegerField ("@IDS_FILE_CROSSFADE_VELOCITIES");
        this.postfixField = panel.createField ("@IDS_FILE_POSTFIX", comma, -1);
        this.ignoreLoops = panel.createCheckBox ("@IDS_WAV_IGNORE_LOOPS");

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        this.sampleFileTypes.clear ();
        for (int i = 0; i < FILE_TYPES.length; i++)
            if (this.sampleFileTypeCheckBoxes[i].isSelected ())
                this.sampleFileTypes.add (FILE_TYPES[i]);
        if (this.sampleFileTypes.isEmpty ())
        {
            Functions.message ("@IDS_NOTIFY_ERR_NO_TYPE_SELECTED");
            return false;
        }

        final String [] groupPatterns = StringUtils.splitByComma (this.detectionPatternField.getText ());
        for (final String groupPattern: groupPatterns)
            if (!groupPattern.contains ("*"))
            {
                Functions.message ("@IDS_NOTIFY_ERR_SPLIT_REGEX", groupPattern);
                notifier.updateButtonStates (true);
                this.detectionPatternField.selectAll ();
                return false;
            }

        final int crossfadeNotes = this.parseCrossfadeNotes ();
        if (crossfadeNotes > 127)
        {
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_NOTES");
            notifier.updateButtonStates (true);
            this.crossfadeNotesField.selectAll ();
            return false;
        }

        final int crossfadeVelocities = this.parseCrossfadeVelocities ();
        if (crossfadeVelocities > 127)
        {
            notifier.updateButtonStates (true);
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_VELOCITIES");
            return false;
        }

        this.groupPatterns = groupPatterns;
        this.isAscending = this.sortAscendingGroup.getToggles ().get (1).isSelected ();
        this.monoSplitPatterns = StringUtils.splitByComma (this.monoSplitsField.getText ());
        this.postfixTexts = StringUtils.splitByComma (this.postfixField.getText ());
        this.crossfadeNotes = crossfadeNotes;
        this.crossfadeVelocities = crossfadeVelocities;
        this.shouldIgnoreLoops = this.ignoreLoops.isSelected ();

        return true;
    }


    private int parseCrossfadeVelocities ()
    {
        int crossfadeVelocities;
        try
        {
            crossfadeVelocities = Integer.parseInt (this.crossfadeVelocitiesField.getText ());
        }
        catch (final NumberFormatException ex)
        {
            crossfadeVelocities = 0;
        }
        return crossfadeVelocities;
    }


    private int parseCrossfadeNotes ()
    {
        int crossfadeNotes;
        try
        {
            crossfadeNotes = Integer.parseInt (this.crossfadeNotesField.getText ());
        }
        catch (final NumberFormatException ex)
        {
            crossfadeNotes = 0;
        }
        return crossfadeNotes;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String [] types = StringUtils.splitByComma (parameters.get (SAMPLEFILE_TYPE));
        this.sampleFileTypes.clear ();
        for (final String type: types)
        {
            final SampleFileType sampleFileType = SAMPLE_FILE_TYPES_BY_NAME.get (type.toUpperCase ());
            if (sampleFileType == null)
            {
                notifier.logError ("IDS_CLI_UNKNOWN_FILE_TYPE", type);
                return false;
            }
            this.sampleFileTypes.add (sampleFileType);
        }
        if (this.sampleFileTypes.isEmpty ())
            this.sampleFileTypes.add (FILE_TYPES[4]);

        this.crossfadeNotes = parsePositiveInt (notifier, parameters, SAMPLEFILE_CROSSFADE_NOTES, 0);
        if (this.crossfadeNotes < 0)
            return false;
        this.crossfadeVelocities = parsePositiveInt (notifier, parameters, SAMPLEFILE_CROSSFADE_VELOCITIES, 0);
        if (this.crossfadeVelocities < 0)
            return false;
        this.crossfadeNotes = Math.clamp (this.crossfadeNotes, 0, 127);
        this.crossfadeVelocities = Math.clamp (this.crossfadeVelocities, 0, 127);

        this.groupPatterns = StringUtils.splitByComma (parameters.get (SAMPLEFILE_GROUP_DETECTION_PATTERN));
        for (final String groupPattern: this.groupPatterns)
            if (!groupPattern.contains ("*"))
            {
                notifier.logError ("IDS_NOTIFY_ERR_SPLIT_REGEX", groupPattern);
                return false;
            }

        String value = parameters.get (SAMPLEFILE_IS_ASCENDING);
        this.isAscending = value == null || "1".equals (value);

        this.monoSplitPatterns = StringUtils.splitByComma (parameters.get (SAMPLEFILE_MONO_SPLITS_PATTERN));
        this.postfixTexts = StringUtils.splitByComma (parameters.get (SAMPLEFILE_POSTFIX));

        value = parameters.get (SAMPLEFILE_IGNORE_LOOPS);
        this.shouldIgnoreLoops = "1".equals (value);

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (SAMPLEFILE_TYPE);
        parameterNames.add (SAMPLEFILE_GROUP_DETECTION_PATTERN);
        parameterNames.add (SAMPLEFILE_IS_ASCENDING);
        parameterNames.add (SAMPLEFILE_MONO_SPLITS_PATTERN);
        parameterNames.add (SAMPLEFILE_CROSSFADE_NOTES);
        parameterNames.add (SAMPLEFILE_CROSSFADE_VELOCITIES);
        parameterNames.add (SAMPLEFILE_POSTFIX);
        parameterNames.add (SAMPLEFILE_IGNORE_LOOPS);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Get the sample file types to search for.
     *
     * @return The sample file types
     */
    public List<SampleFileType> getSampleFileTypes ()
    {
        return this.sampleFileTypes;
    }


    /**
     * Get the cross-fade velocity value.
     *
     * @return The cross-fade velocity value
     */
    public int getCrossfadeVelocities ()
    {
        return this.crossfadeVelocities;
    }


    /**
     * Get the cross-fade notes value.
     *
     * @return The cross-fade notes value
     */
    public int getCrossfadeNotes ()
    {
        return this.crossfadeNotes;
    }


    /**
     * Get the patterns to detect groups.
     *
     * @return The group detection patterns
     */
    public String [] getGroupPatterns ()
    {
        return this.groupPatterns;
    }


    /**
     * Get how to sort groups.
     *
     * @return True if they should be sorted ascending
     */
    public boolean isAscending ()
    {
        return this.isAscending;
    }


    /**
     * Get the patterns to detect mono files.
     *
     * @return The mono split patterns
     */
    public String [] getMonoSplitPatterns ()
    {
        return this.monoSplitPatterns;
    }


    /**
     * Get the post-fix texts to remove.
     *
     * @return The texts
     */
    public String [] getPostfixTexts ()
    {
        return this.postfixTexts;
    }


    /**
     * Should loop values be ignored?
     *
     * @return True to ignore loops
     */
    public boolean isShouldIgnoreLoops ()
    {
        return this.shouldIgnoreLoops;
    }


    private static int parsePositiveInt (final INotifier notifier, final Map<String, String> parameters, final String identifier, final int defaultValue)
    {
        try
        {
            final String value = parameters.remove (identifier);
            if (value == null || value.isBlank ())
                return defaultValue;
            return Integer.parseInt (value);
        }
        catch (final NumberFormatException ex)
        {
            notifier.logError ("IDS_CLI_VALUE_MUST_BE_INTEGER", identifier);
            return -1;
        }
    }
}
