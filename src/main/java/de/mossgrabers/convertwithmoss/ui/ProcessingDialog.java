// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.tools.ui.AbstractDialog;
import de.mossgrabers.tools.ui.ControlFunctions;
import de.mossgrabers.tools.ui.TraversalManager;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;


/**
 * Dialog for processing settings.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class ProcessingDialog extends AbstractDialog
{
    private static final List<String> BIT_DEPTH       = new ArrayList<> ();
    private static final List<String> FREQ_RESOLUTiON = new ArrayList<> ();

    static
    {
        Collections.addAll (BIT_DEPTH, "Ignore", "24 bit", "16 bit", "12 bit");
        Collections.addAll (FREQ_RESOLUTiON, "Ignore", "44.1 kHz", "32 kHz", "31.25 kHz", "30 kHz", "28 kHz", "27 kHz", "24 kHz", "22.05 kHz", "16 kHz", "12 kHz", "11.025 kHz", "8 kHz");
    }

    private final TraversalManager traversalManager = new TraversalManager ();

    /** Check-box to enable processing globally. */
    public CheckBox                enableProcessingCheckbox;
    /** Check-box to enable normalizing samples. */
    public CheckBox                normalizeCheckbox;
    /** Check-box for enabling making all samples mono. */
    public CheckBox                makeMonoCheckbox;
    /** Check-box for enabling the truncate-sample-before-playback-start option. */
    public CheckBox                truncateSampleBeforePlaybackStartCheckbox;
    /** Check-box for enabling the truncate-sample-after-loop-end option. */
    public CheckBox                truncateSampleAfterLoopEndCheckbox;
    /** Text field for the maximum number of samples. */
    public TextField               maxSamplesField;
    /** Combo-box for the target bit-depth. */
    public ComboBox<Object>        reduceBitDepthCombobox;
    /** Combo-box for the target sample frequency. */
    public ComboBox<Object>        reduceFrequencyCombobox;


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog
     */
    protected ProcessingDialog (final Window owner)
    {
        super (owner, "@IDS_PROCESSING_DIALOG", true, true, 400, 300);

        this.basicInit ();

        ControlFunctions.setFocusOn (this.normalizeCheckbox);
    }


    /** {@inheritDoc} */
    @Override
    protected Pane init ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.enableProcessingCheckbox = panel.createCheckBox ("@IDS_PROCESSING_ENABLE", "@IDS_PROCESSING_ENABLE_TOOLTIP");

        panel.createSeparator ("@IDS_PROCESSING_NORMALIZE_HEADER");
        this.normalizeCheckbox = panel.createCheckBox ("@IDS_PROCESSING_NORMALIZE", "@IDS_PROCESSING_NORMALIZE_TOOLTIP");

        panel.createSeparator ("@IDS_PROCESSING_MINIMIZE_HEADER");
        this.makeMonoCheckbox = panel.createCheckBox ("@IDS_PROCESSING_MONO", "@IDS_PROCESSING_MONO_TOOLTIP");
        this.truncateSampleBeforePlaybackStartCheckbox = panel.createCheckBox ("@IDS_PROCESSING_TRUNCATE_START", "@IDS_PROCESSING_TRUNCATE_START_TOOLTIP");
        this.truncateSampleAfterLoopEndCheckbox = panel.createCheckBox ("@IDS_PROCESSING_TRUNCATE_AFTER_LOOP", "@IDS_PROCESSING_TRUNCATE_AFTER_LOOP_TOOLTIP");
        this.maxSamplesField = panel.createPositiveIntegerField ("@IDS_PROCESSING_MAX_SAMPLES", "@IDS_PROCESSING_MAX_SAMPLES_TOOLTIP");
        this.reduceBitDepthCombobox = panel.createComboBox ("@IDS_PROCESSING_REDUCE_BIT_DEPTH", "@IDS_PROCESSING_REDUCE_BIT_DEPTH_TOOLTIP", BIT_DEPTH);
        this.reduceFrequencyCombobox = panel.createComboBox ("@IDS_PROCESSING_REDUCE_FREQUENCY", "@IDS_PROCESSING_REDUCE_FREQUENCY_TOOLTIP", FREQ_RESOLUTiON);

        this.setButtons ("@IDS_SETTINGS_DLG_OK", "@IDS_SETTINGS_DLG_CANCEL");

        this.traversalManager.add (this.normalizeCheckbox);
        this.traversalManager.add (this.makeMonoCheckbox);
        this.traversalManager.add (this.truncateSampleBeforePlaybackStartCheckbox);
        this.traversalManager.add (this.truncateSampleAfterLoopEndCheckbox);
        this.traversalManager.add (this.maxSamplesField);
        this.traversalManager.add (this.reduceBitDepthCombobox);
        this.traversalManager.add (this.reduceFrequencyCombobox);

        this.traversalManager.add (this.getOKButton ());
        this.traversalManager.add (this.getCancelButton ());

        final Stage stage = (Stage) this.getDialogPane ().getScene ().getWindow ();
        this.traversalManager.register (stage);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    protected boolean onOk ()
    {
        return true;
    }
}
