// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.ui.MetadataPane;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
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

import java.io.File;
import java.util.function.Consumer;


/**
 * Descriptor for WAV files detector.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WavDetector extends AbstractDetector<WavMultisampleDetectorTask>
{
    private static final String WAV_DETECTION_PATTERN    = "WavDetectionPattern";
    private static final String WAV_IS_ASCENDING         = "WavIsAscending";
    private static final String WAV_MONO_SPLITS_PATTERN  = "WavMonoSPlitPattern";
    private static final String WAV_CROSSFADE_NOTES      = "WavCrossfadeNotes";
    private static final String WAV_CROSSFADE_VELOCITIES = "WavCrossfadeVelocities";
    private static final String WAV_POSTFIX              = "WavPostfix";

    private TextField           detectionPatternField;
    private ToggleGroup         sortAscendingGroup;
    private TextField           monoSplitsField;
    private TextField           crossfadeNotesField;
    private TextField           crossfadeVelocitiesField;
    private TextField           postfixField;

    private final MetadataPane  metadataPane             = new MetadataPane ("Wav");


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavDetector (final INotifier notifier)
    {
        super ("WAV", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        final boolean isAscending = this.sortAscendingGroup.getToggles ().get (1).isSelected ();

        final String [] velocityLayerPatterns = StringUtils.splitByComma (this.detectionPatternField.getText ());
        for (final String velocityLayerPattern: velocityLayerPatterns)
        {
            if (!velocityLayerPattern.contains ("*"))
            {
                Functions.message ("@IDS_NOTIFY_ERR_SPLIT_REGEX", velocityLayerPattern);
                this.notifier.updateButtonStates (true);
                this.detectionPatternField.selectAll ();
                return;
            }
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

        this.startDetection (new WavMultisampleDetectorTask (this.notifier, consumer, folder, velocityLayerPatterns, isAscending, monoSplitPatterns, postfixTexts, crossfadeNotes, crossfadeVelocities, this.metadataPane));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.metadataPane.saveSettings (config);

        config.setProperty (WAV_DETECTION_PATTERN, this.detectionPatternField.getText ());
        config.setProperty (WAV_IS_ASCENDING, Boolean.toString (this.sortAscendingGroup.getToggles ().get (1).isSelected ()));
        config.setProperty (WAV_MONO_SPLITS_PATTERN, this.monoSplitsField.getText ());
        config.setProperty (WAV_CROSSFADE_NOTES, this.crossfadeNotesField.getText ());
        config.setProperty (WAV_CROSSFADE_VELOCITIES, this.crossfadeVelocitiesField.getText ());
        config.setProperty (WAV_POSTFIX, this.postfixField.getText ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.metadataPane.loadSettings (config);

        this.detectionPatternField.setText (config.getProperty (WAV_DETECTION_PATTERN, "_ms*_,S_*_"));
        this.sortAscendingGroup.selectToggle (this.sortAscendingGroup.getToggles ().get (config.getBoolean (WAV_IS_ASCENDING, true) ? 1 : 0));
        this.monoSplitsField.setText (config.getProperty (WAV_MONO_SPLITS_PATTERN, "_L"));
        this.crossfadeNotesField.setText (Integer.toString (config.getInteger (WAV_CROSSFADE_NOTES, 0)));
        this.crossfadeVelocitiesField.setText (Integer.toString (config.getInteger (WAV_CROSSFADE_VELOCITIES, 0)));
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

        this.metadataPane.addTo (panel);

        ////////////////////////////////////////////////////////////
        // Options

        panel.createSeparator ("@IDS_WAV_OPTIONS");

        this.crossfadeNotesField = panel.createPositiveIntegerField ("@IDS_WAV_CROSSFADE_NOTES");
        this.crossfadeVelocitiesField = panel.createPositiveIntegerField ("@IDS_WAV_CROSSFADE_VELOCITIES");
        this.postfixField = panel.createField ("@IDS_WAV_POSTFIX", comma, -1);

        final ScrollPane scrollPane = new ScrollPane (panel.getPane ());
        scrollPane.fitToWidthProperty ().set (true);
        scrollPane.fitToHeightProperty ().set (true);
        return scrollPane;
    }
}
