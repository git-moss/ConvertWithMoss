// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.ui.tools.BasicConfig;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.ui.tools.panel.BoxPanel;

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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * Descriptor for WAV files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WaveDetectorDescriptor extends AbstractDetectorDescriptor
{
    private static final String         COMMA_SPLIT             = ",";

    private static final String         WAV_DETECTION_PATTERN   = "WavDetectionPattern";
    private static final String         WAV_IS_ASCENDING        = "WavIsAscending";
    private static final String         WAV_MONO_SPLITS_PATTERN = "WavMonoSPlitPattern";
    private static final String         WAV_PREFER_FOLDER_NAME  = "WavPreferFolderName";
    private static final String         WAV_DEFAULT_CREATOR     = "WavDefaultCreator";
    private static final String         WAV_CREATORS            = "WavCreators";
    private static final String         WAV_CROSSFADE_NOTES     = "WavCrossfadeNotes";
    private static final String         WAV_POSTFIX             = "WavPostfix";

    private TextField                   detectionPatternField;
    private ToggleGroup                 sortAscendingGroup;
    private TextField                   monoSplitsField;
    private CheckBox                    preferFolderNameCheckBox;
    private TextField                   defaultCreatorField;
    private TextField                   creatorsField;
    private TextField                   crossfadeNotesField;
    private TextField                   postfixField;

    private INotifier                   notifier;
    private final ExecutorService       executor                = Executors.newSingleThreadExecutor ();
    private WaveMultisampleDetectorTask detector;


    /**
     * Constructor.
     */
    public WaveDetectorDescriptor ()
    {
        super ("WAV");
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final INotifier notifier, final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.notifier = notifier;

        final boolean isAscending = this.sortAscendingGroup.getToggles ().get (1).isSelected ();

        final String [] velocityLayerPatterns = this.detectionPatternField.getText ().split (COMMA_SPLIT);
        final String [] monoSplitPatterns = this.monoSplitsField.getText ().split (COMMA_SPLIT);

        final boolean isPreferFolderName = this.preferFolderNameCheckBox.isSelected ();
        final String creatorName = this.defaultCreatorField.getText ();
        final String [] creatorTags = this.creatorsField.getText ().split (COMMA_SPLIT);
        final String [] postfixTexts = this.postfixField.getText ().split (COMMA_SPLIT);

        int crossfadeNotes;
        try
        {
            crossfadeNotes = Integer.parseInt (this.crossfadeNotesField.getText ());
        }
        catch (final NumberFormatException ex)
        {
            crossfadeNotes = 0;
        }

        this.detector = new WaveMultisampleDetectorTask ();
        this.detector.configure (notifier, consumer, folder, velocityLayerPatterns, isAscending, monoSplitPatterns, postfixTexts, isPreferFolderName, crossfadeNotes, creatorTags, creatorName);
        this.detector.setOnCancelled (event -> this.updateButtonStates (true));
        this.detector.setOnFailed (event -> this.updateButtonStates (true));
        this.detector.setOnSucceeded (event -> this.updateButtonStates (true));
        this.detector.setOnRunning (event -> this.updateButtonStates (false));
        this.detector.setOnScheduled (event -> this.updateButtonStates (false));
        this.executor.execute (this.detector);
    }


    private void updateButtonStates (final boolean canClose)
    {
        if (this.notifier != null)
            this.notifier.updateButtonStates (canClose);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setProperty (WAV_DETECTION_PATTERN, this.detectionPatternField.getText ());
        config.setProperty (WAV_IS_ASCENDING, Boolean.toString (this.sortAscendingGroup.getToggles ().get (1).isSelected ()));
        config.setProperty (WAV_MONO_SPLITS_PATTERN, this.monoSplitsField.getText ());
        config.setProperty (WAV_PREFER_FOLDER_NAME, Boolean.toString (this.preferFolderNameCheckBox.isSelected ()));
        config.setProperty (WAV_DEFAULT_CREATOR, this.defaultCreatorField.getText ());
        config.setProperty (WAV_CREATORS, this.creatorsField.getText ());
        config.setProperty (WAV_CROSSFADE_NOTES, this.crossfadeNotesField.getText ());
        config.setProperty (WAV_POSTFIX, this.postfixField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.detectionPatternField.setText (config.getProperty (WAV_DETECTION_PATTERN, "_ms*_,S_*_"));
        this.sortAscendingGroup.selectToggle (this.sortAscendingGroup.getToggles ().get (config.getBoolean (WAV_IS_ASCENDING, true) ? 1 : 0));
        this.monoSplitsField.setText (config.getProperty (WAV_MONO_SPLITS_PATTERN, "_L"));
        this.preferFolderNameCheckBox.setSelected (config.getBoolean (WAV_PREFER_FOLDER_NAME, false));
        this.defaultCreatorField.setText (config.getProperty (WAV_DEFAULT_CREATOR, "moss"));
        this.creatorsField.setText (config.getProperty (WAV_CREATORS, ""));
        this.crossfadeNotesField.setText (Integer.toString (config.getInteger (WAV_CROSSFADE_NOTES, 0)));
        this.postfixField.setText (config.getProperty (WAV_POSTFIX, ""));
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        final String comma = Functions.getMessage ("IDS_NOTIFY_COMMA");

        ////////////////////////////////////////////////////////////
        // Velocity layers

        panel.createSeparator ("@IDS_WAV_VEL_LAYERS");

        // Layer detection pattern
        this.detectionPatternField = panel.createField ("@IDS_WAV_DETECTION", comma, -1);

        // Order of layer numbering
        final BoxPanel orderPanel = new BoxPanel (Orientation.HORIZONTAL);
        this.sortAscendingGroup = new ToggleGroup ();
        final RadioButton order1 = orderPanel.createRadioButton ("@IDS_WAV_LAYERS_DESC");
        order1.setToggleGroup (this.sortAscendingGroup);
        final RadioButton order2 = orderPanel.createRadioButton ("@IDS_WAV_LAYERS_ASC");
        order2.setToggleGroup (this.sortAscendingGroup);
        final BorderPane borderPane = new BorderPane ();
        final Label orderLabel = orderPanel.createLabel ("@IDS_WAV_LAYER_NUMBERING");
        borderPane.setLeft (orderLabel);
        BorderPane.setAlignment (orderLabel, Pos.CENTER_LEFT);
        borderPane.setRight (orderPanel.getPane ());
        panel.addComponent (borderPane);

        this.monoSplitsField = panel.createField ("@IDS_WAV_MONO_STEREO", comma, -1);

        ////////////////////////////////////////////////////////////
        // Metadata

        panel.createSeparator ("@IDS_WAV_META");

        this.preferFolderNameCheckBox = panel.createCheckBox ("@IDS_WAV_PREFER_FOLDER");
        this.defaultCreatorField = panel.createField ("@IDS_WAV_DEFAULT_CREATOR");
        this.creatorsField = panel.createField ("@IDS_WAV_CREATORS", comma, -1);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_WAV_OPTIONS");

        this.crossfadeNotesField = panel.createPositiveIntegerField ("@IDS_WAV_CROSSFADE");
        this.postfixField = panel.createField ("@IDS_WAV_POSTFIX", comma, -1);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.executor.shutdown ();
    }


    /** {@inheritDoc} */
    @Override
    public void cancel ()
    {
        if (this.detector == null)
            return;
        this.detector.cancel (true);
        this.detector = null;
    }
}
