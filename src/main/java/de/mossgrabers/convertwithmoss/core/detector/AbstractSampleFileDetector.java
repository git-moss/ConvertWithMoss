// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;


/**
 * Base class for detector descriptors based on pure sample files.
 *
 * @param <T> The type of the descriptor task
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractSampleFileDetector<T extends AbstractSampleFileDetectorTask> extends AbstractDetectorWithMetadataPane<T>
{
    private static final String DETECTION_PATTERN    = "DetectionPattern";
    private static final String IS_ASCENDING         = "IsAscending";
    private static final String MONO_SPLITS_PATTERN  = "MonoSPlitPattern";
    private static final String CROSSFADE_NOTES      = "CrossfadeNotes";
    private static final String CROSSFADE_VELOCITIES = "CrossfadeVelocities";
    private static final String POSTFIX              = "Postfix";

    private TextField           detectionPatternField;
    private ToggleGroup         sortAscendingGroup;
    private TextField           monoSplitsField;
    private TextField           crossfadeNotesField;
    private TextField           crossfadeVelocitiesField;
    private TextField           postfixField;


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param notifier The notifier
     * @param prefix The prefix to use for the metadata properties tags
     */
    protected AbstractSampleFileDetector (final String name, final INotifier notifier, final String prefix)
    {
        super (name, notifier, prefix);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.metadataPane.saveSettings (config);

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
        // Groups

        panel.createSeparator ("@IDS_FILE_GROUPS");

        // Layer detection pattern
        this.detectionPatternField = panel.createField ("@IDS_FILE_DETECTION", comma, -1);

        // Order of group numbering
        final BoxPanel orderPanel = new BoxPanel (Orientation.HORIZONTAL);
        this.sortAscendingGroup = new ToggleGroup ();
        final RadioButton order1 = orderPanel.createRadioButton ("@IDS_FILE_GROUPS_DESC");
        order1.setToggleGroup (this.sortAscendingGroup);
        final RadioButton order2 = orderPanel.createRadioButton ("@IDS_FILE_GROUPS_ASC");
        order2.setToggleGroup (this.sortAscendingGroup);
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

        final TitledSeparator separator = panel.createSeparator ("@IDS_FILE_OPTIONS");
        separator.getStyleClass ().add ("titled-separator-pane");

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
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        final boolean isAscending = this.sortAscendingGroup.getToggles ().get (1).isSelected ();

        final String [] groupPatterns = StringUtils.splitByComma (this.detectionPatternField.getText ());
        for (final String groupPattern: groupPatterns)
            if (!groupPattern.contains ("*"))
            {
                Functions.message ("@IDS_NOTIFY_ERR_SPLIT_REGEX", groupPattern);
                this.notifier.updateButtonStates (true);
                this.detectionPatternField.selectAll ();
                return;
            }

        final String [] monoSplitPatterns = StringUtils.splitByComma (this.monoSplitsField.getText ());
        final String [] postfixTexts = StringUtils.splitByComma (this.postfixField.getText ());

        int crossfadeNotes;
        try
        {
            crossfadeNotes = Integer.parseInt (this.crossfadeNotesField.getText ());
        }
        catch (final NumberFormatException ex)
        {
            crossfadeNotes = 0;
        }
        if (crossfadeNotes > 127)
        {
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_NOTES");
            this.notifier.updateButtonStates (true);
            this.crossfadeNotesField.selectAll ();
            return;
        }

        int crossfadeVelocities;
        try
        {
            crossfadeVelocities = Integer.parseInt (this.crossfadeVelocitiesField.getText ());
        }
        catch (final NumberFormatException ex)
        {
            crossfadeVelocities = 0;
        }
        if (crossfadeVelocities > 127)
        {
            this.notifier.updateButtonStates (true);
            Functions.message ("@IDS_NOTIFY_ERR_CROSSFADE_VELOCITIES");
            return;
        }

        this.startDetection (this.createDetectorTask (folder, consumer, isAscending, groupPatterns, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities));
    }


    /**
     * Create the detector task.
     * 
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param folder The top source folder for the detection
     * @param isAscending Are groups ordered ascending?
     * @param groupPatterns Detection patterns for groups
     * @param monoSplitPatterns Detection pattern for mono splits (to be combined to stereo files)
     * @param postfixTexts Post-fix text to remove
     * @param crossfadeNotes Number of notes to cross-fade
     * @param crossfadeVelocities The number of velocity steps to cross-fade ranges
     * @return The detector task
     */
    protected abstract T createDetectorTask (final File folder, final Consumer<IMultisampleSource> consumer, final boolean isAscending, final String [] groupPatterns, final String [] monoSplitPatterns, final String [] postfixTexts, int crossfadeNotes, int crossfadeVelocities);
}
