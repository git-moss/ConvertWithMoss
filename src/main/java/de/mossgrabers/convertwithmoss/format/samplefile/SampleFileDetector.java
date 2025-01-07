// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.samplefile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;
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
 * Detector for pure sample files (e.g. AIFF, WAV, ...).
 *
 * @author Jürgen Moßgraber
 */
public class SampleFileDetector extends AbstractDetectorWithMetadataPane<SampleFileDetectorTask>
{
    private static final String            SAMPLEFILE_TYPE          = "SamplefileType";
    private static final String            DETECTION_PATTERN        = "DetectionPattern";
    private static final String            IS_ASCENDING             = "IsAscending";
    private static final String            MONO_SPLITS_PATTERN      = "MonoSPlitPattern";
    private static final String            CROSSFADE_NOTES          = "CrossfadeNotes";
    private static final String            CROSSFADE_VELOCITIES     = "CrossfadeVelocities";
    private static final String            POSTFIX                  = "Postfix";

    private static final SampleFileType [] FILE_TYPES               =
    {
        new AiffSampleFileType (),
        new FlacSampleFileType (),
        new NcwSampleFileType (),
        new OggSampleFileType (),
        new WavSampleFileType ()
    };

    private TextField                      detectionPatternField;
    private ToggleGroup                    sortAscendingGroup;
    private TextField                      monoSplitsField;
    private TextField                      crossfadeNotesField;
    private TextField                      crossfadeVelocitiesField;
    private TextField                      postfixField;
    private final CheckBox []              sampleFileTypeCheckBoxes = new CheckBox [FILE_TYPES.length];


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SampleFileDetector (final INotifier notifier)
    {
        super ("Sample Files", notifier, "samplefile");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.metadataPane.saveSettings (config);

        for (int i = 0; i < FILE_TYPES.length; i++)
            config.setBoolean (this.prefix + SAMPLEFILE_TYPE + i, this.sampleFileTypeCheckBoxes[i].isSelected ());

        config.setProperty (this.prefix + DETECTION_PATTERN, this.detectionPatternField.getText ());
        config.setProperty (this.prefix + IS_ASCENDING, Boolean.toString (this.sortAscendingGroup.getToggles ().get (1).isSelected ()));
        config.setProperty (this.prefix + MONO_SPLITS_PATTERN, this.monoSplitsField.getText ());
        config.setProperty (this.prefix + CROSSFADE_NOTES, this.crossfadeNotesField.getText ());
        config.setProperty (this.prefix + CROSSFADE_VELOCITIES, this.crossfadeVelocitiesField.getText ());
        config.setProperty (this.prefix + POSTFIX, this.postfixField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);

        for (int i = 0; i < FILE_TYPES.length; i++)
            this.sampleFileTypeCheckBoxes[i].setSelected (config.getBoolean (this.prefix + SAMPLEFILE_TYPE + i, true));

        this.detectionPatternField.setText (config.getProperty (this.prefix + DETECTION_PATTERN, "_ms*_,S_*_"));
        this.sortAscendingGroup.selectToggle (this.sortAscendingGroup.getToggles ().get (config.getBoolean (this.prefix + IS_ASCENDING, true) ? 1 : 0));
        this.monoSplitsField.setText (config.getProperty (this.prefix + MONO_SPLITS_PATTERN, "_L"));
        this.crossfadeNotesField.setText (Integer.toString (config.getInteger (this.prefix + CROSSFADE_NOTES, 0)));
        this.crossfadeVelocitiesField.setText (Integer.toString (config.getInteger (this.prefix + CROSSFADE_VELOCITIES, 0)));
        this.postfixField.setText (config.getProperty (this.prefix + POSTFIX, ""));
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

        this.metadataPane.addTo (panel);
        this.metadataPane.getSeparator ().getStyleClass ().add ("titled-separator-pane");

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_FILE_OPTIONS").getStyleClass ().add ("titled-separator-pane");

        this.crossfadeNotesField = panel.createPositiveIntegerField ("@IDS_FILE_CROSSFADE_NOTES");
        this.crossfadeVelocitiesField = panel.createPositiveIntegerField ("@IDS_FILE_CROSSFADE_VELOCITIES");
        this.postfixField = panel.createField ("@IDS_FILE_POSTFIX", comma, -1);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public boolean validateParameters ()
    {
        boolean hasSampleFileTypeSelection = false;
        for (int i = 0; i < FILE_TYPES.length; i++)
            if (this.sampleFileTypeCheckBoxes[i].isSelected ())
            {
                hasSampleFileTypeSelection = true;
                break;
            }
        if (!hasSampleFileTypeSelection)
        {
            Functions.message ("@IDS_NOTIFY_ERR_NO_TYPE_SELECTED");
            return false;
        }

        final String [] groupPatterns = StringUtils.splitByComma (this.detectionPatternField.getText ());
        for (final String groupPattern: groupPatterns)
            if (!groupPattern.contains ("*"))
            {
                Functions.message ("@IDS_NOTIFY_ERR_SPLIT_REGEX", groupPattern);
                this.notifier.updateButtonStates (true);
                this.detectionPatternField.selectAll ();
                return false;
            }

        if (this.getCrossfadeNotes () > 127)
        {
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_NOTES");
            this.notifier.updateButtonStates (true);
            this.crossfadeNotesField.selectAll ();
            return false;
        }

        if (this.getCrossfadeVelocities () > 127)
        {
            this.notifier.updateButtonStates (true);
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_VELOCITIES");
            return false;
        }

        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        final List<SampleFileType> sampleFileTypes = new ArrayList<> ();
        for (int i = 0; i < FILE_TYPES.length; i++)
            if (this.sampleFileTypeCheckBoxes[i].isSelected ())
                sampleFileTypes.add (FILE_TYPES[i]);

        final boolean isAscending = this.sortAscendingGroup.getToggles ().get (1).isSelected ();
        final String [] groupPatterns = StringUtils.splitByComma (this.detectionPatternField.getText ());
        final String [] monoSplitPatterns = StringUtils.splitByComma (this.monoSplitsField.getText ());
        final String [] postfixTexts = StringUtils.splitByComma (this.postfixField.getText ());
        final int crossfadeNotes = this.getCrossfadeNotes ();
        final int crossfadeVelocities = this.getCrossfadeVelocities ();

        this.startDetection (new SampleFileDetectorTask (this.notifier, consumer, folder, groupPatterns, isAscending, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities, this.metadataPane, sampleFileTypes));
    }


    private int getCrossfadeVelocities ()
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


    private int getCrossfadeNotes ()
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
}
