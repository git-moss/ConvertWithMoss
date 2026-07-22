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
import de.mossgrabers.tools.ui.panel.BasePanel;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import de.mossgrabers.tools.ui.panel.TwoColsPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
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
    private static final List<String> LOOP_CROSSFADES = new ArrayList<> ();
    private static final List<String> TRANSPOSE       = new ArrayList<> ();
    private static final int          TRANSPOSE_RANGE = 24;

    static
    {
        for (int i = -TRANSPOSE_RANGE; i <= TRANSPOSE_RANGE; i++)
            TRANSPOSE.add (i == 0 ? "Off" : String.format ("%+d", Integer.valueOf (i)));
        Collections.addAll (BIT_DEPTH, "Ignore", "24 bit", "16 bit", "8 bit");
        Collections.addAll (FREQ_RESOLUTiON, "Ignore", "48 kHz", "44.1 kHz", "32 kHz", "31.25 kHz", "30 kHz", "28 kHz", "27 kHz", "24 kHz", "22.05 kHz", "16 kHz", "12 kHz", "11.025 kHz", "8 kHz");
        Collections.addAll (LOOP_CROSSFADES, "Off", "0%", "1%", "2%", "3%", "4%", "5%", "6%", "7%", "8%", "9%", "10%", "11%", "12%", "13%", "14%", "15%", "16%", "17%", "18%", "19%", "20%", "21%", "22%", "23%", "24%", "25%", "26%", "27%", "28%", "29%", "30%", "31%", "32%", "33%", "34%", "35%", "36%", "37%", "38%", "39%", "40%", "41%", "42%", "43%", "44%", "45%", "46%", "47%", "48%", "49%", "50%", "51%", "52%", "53%", "54%", "55%", "56%", "57%", "58%", "59%", "60%", "61%", "62%", "63%", "64%", "65%", "66%", "67%", "68%", "69%", "70%", "71%", "72%", "73%", "74%", "75%", "76%", "77%", "78%", "79%", "80%", "81%", "82%", "83%", "84%", "85%", "86%", "87%", "88%", "89%", "90%", "91%", "92%", "93%", "94%", "95%", "96%", "97%", "98%", "99%", "100%");
    }

    private final TraversalManager traversalManager = new TraversalManager ();

    /** Check-box to enable processing globally. */
    public CheckBox                enableProcessingCheckbox;
    /** Check-box to enable normalizing samples. */
    public CheckBox                normalizeCheckbox;
    /** Check-box for enabling making all samples mono. */
    public CheckBox                makeMonoCheckbox;
    /** Check-box for enabling the trim start/end option. */
    public CheckBox                trimSample;
    /** Text field for the maximum number of samples. */
    public TextField               maxSamplesField;
    /** Combo-box for the target bit-depth. */
    public ComboBox<String>        reduceBitDepthCombobox;
    /** Combo-box for the target sample frequency. */
    public ComboBox<String>        reduceFrequencyCombobox;
    /** Check-box to enable always re-sample option. */
    public CheckBox                alwaysResampleCheckbox;
    /** Combo-box for the loop cross-fades. */
    public ComboBox<String>        loopCrossfadesCombobox;
    /** Check-box to snap forward loop boundaries to zero-crossings. */
    public CheckBox                snapLoopsCheckbox;
    /** Combo-box for transposing playback by semitones. */
    public ComboBox<String>        transposeCombobox;


    /**
     * Constructor.
     *
     * @param owner The owner of the dialog, may be null
     * @param isModal Should the dialog be modal?
     */
    protected ProcessingDialog (final Window owner, final boolean isModal)
    {
        super (owner, "@IDS_PROCESSING_DIALOG", isModal, true, 400, 340);

        this.setResizable (false);

        this.basicInit ();

        ControlFunctions.setFocusOn (this.normalizeCheckbox);
    }


    /**
     * Select the bit depth.
     *
     * @param bitDepth The bit depth (8, 16, 24, all other values are off)
     */
    public void selectBitDepth (final int bitDepth)
    {
        final int itemIndex;
        switch (bitDepth)
        {
            case 24 -> itemIndex = 1;
            case 16 -> itemIndex = 2;
            case 8 -> itemIndex = 3;
            default -> itemIndex = 0;
        }

        this.reduceBitDepthCombobox.getSelectionModel ().select (itemIndex);
    }


    /**
     * Select the bit depth.
     *
     * @return The bit depth (8, 16, 24, -1 for off)
     */
    public int getBitDepth ()
    {
        final int itemIndex = this.reduceBitDepthCombobox.getSelectionModel ().getSelectedIndex ();
        final int bitDepth;
        switch (itemIndex)
        {
            case 1 -> bitDepth = 24;
            case 2 -> bitDepth = 16;
            case 3 -> bitDepth = 8;
            default -> bitDepth = -1;
        }
        return bitDepth;
    }


    /**
     * Select the maximum frequency.
     *
     * @param frequency The frequency, e.g. 44100
     */
    public void selectFrequency (final int frequency)
    {
        final int itemIndex;
        switch (frequency)
        {
            case 48000 -> itemIndex = 1;
            case 44100 -> itemIndex = 2;
            case 32000 -> itemIndex = 3;
            case 31250 -> itemIndex = 4;
            case 30000 -> itemIndex = 5;
            case 28000 -> itemIndex = 6;
            case 27000 -> itemIndex = 7;
            case 24000 -> itemIndex = 8;
            case 22050 -> itemIndex = 9;
            case 16000 -> itemIndex = 10;
            case 12000 -> itemIndex = 11;
            case 11025 -> itemIndex = 12;
            case 8000 -> itemIndex = 13;
            default -> itemIndex = 0;
        }

        this.reduceFrequencyCombobox.getSelectionModel ().select (itemIndex);
    }


    /**
     * Select the loop cross-fades.
     *
     * @param loopCrossfades The loop cross-fades, 0 = Off, 1 = 0%, 2= 1%, ...
     */
    public void selectLoopCrossfades (final int loopCrossfades)
    {
        this.loopCrossfadesCombobox.getSelectionModel ().select (loopCrossfades);
    }


    /**
     * Get the the maximum frequency.
     *
     * @return The frequency, e.g. 44100, -1 to ignore
     */
    public int getFrequency ()
    {
        final int itemIndex = this.reduceFrequencyCombobox.getSelectionModel ().getSelectedIndex ();
        final int frequency;
        switch (itemIndex)
        {
            case 1 -> frequency = 48000;
            case 2 -> frequency = 44100;
            case 3 -> frequency = 32000;
            case 4 -> frequency = 31250;
            case 5 -> frequency = 30000;
            case 6 -> frequency = 28000;
            case 7 -> frequency = 27000;
            case 8 -> frequency = 24000;
            case 9 -> frequency = 22050;
            case 10 -> frequency = 16000;
            case 11 -> frequency = 12000;
            case 12 -> frequency = 11025;
            case 13 -> frequency = 8000;
            default -> frequency = -1;
        }

        return frequency;
    }


    /**
     * Get the loop cross-fades.
     *
     * @return The loop cross-fades
     */
    public int getLoopCrossfades ()
    {
        return this.loopCrossfadesCombobox.getSelectionModel ().getSelectedIndex ();
    }


    /**
     * Select the transpose value.
     *
     * @param semitones The number of semitones in the range of [-24..24]
     */
    public void selectTranspose (final int semitones)
    {
        this.transposeCombobox.getSelectionModel ().select (Math.clamp (semitones, -TRANSPOSE_RANGE, TRANSPOSE_RANGE) + TRANSPOSE_RANGE);
    }


    /**
     * Get the transpose value.
     *
     * @return The number of semitones in the range of [-24..24]
     */
    public int getTranspose ()
    {
        final int itemIndex = this.transposeCombobox.getSelectionModel ().getSelectedIndex ();
        return itemIndex < 0 ? 0 : itemIndex - TRANSPOSE_RANGE;
    }


    /** {@inheritDoc} */
    @Override
    protected Pane init ()
    {
        final BoxPanel panel1 = new BoxPanel (Orientation.VERTICAL);
        this.normalizeCheckbox = panel1.createCheckBox ("@IDS_PROCESSING_NORMALIZE", "@IDS_PROCESSING_NORMALIZE_TOOLTIP");

        final BoxPanel panel2 = new BoxPanel (Orientation.VERTICAL);
        this.makeMonoCheckbox = panel2.createCheckBox ("@IDS_PROCESSING_MONO", "@IDS_PROCESSING_MONO_TOOLTIP");
        this.trimSample = panel2.createCheckBox ("@IDS_PROCESSING_TRUNCATE_START", "@IDS_PROCESSING_TRUNCATE_START_TOOLTIP");
        this.maxSamplesField = panel2.createPositiveIntegerField ("@IDS_PROCESSING_MAX_SAMPLES", "@IDS_PROCESSING_MAX_SAMPLES_TOOLTIP");
        BasePanel.limitToNumbers (this.maxSamplesField);

        final BoxPanel panel3 = new TwoColsPanel ();
        this.reduceBitDepthCombobox = panel3.createComboBox ("@IDS_PROCESSING_REDUCE_BIT_DEPTH", "@IDS_PROCESSING_REDUCE_BIT_DEPTH_TOOLTIP", BIT_DEPTH);
        this.reduceFrequencyCombobox = panel3.createComboBox ("@IDS_PROCESSING_REDUCE_FREQUENCY", "@IDS_PROCESSING_REDUCE_FREQUENCY_TOOLTIP", FREQ_RESOLUTiON);
        this.alwaysResampleCheckbox = panel3.createCheckBox ("@IDS_PROCESSING_ALWAYS_RESAMPLE_LABEL", "@IDS_PROCESSING_ALWAYS_RESAMPLE_TOOLTIP");

        final BoxPanel panel4 = new TwoColsPanel ();
        this.loopCrossfadesCombobox = panel4.createComboBox ("@IDS_PROCESSING_LOOP_CROSSFADE", "@IDS_PROCESSING_LOOP_CROSSFADE_TOOLTIP", LOOP_CROSSFADES);
        this.snapLoopsCheckbox = panel4.createCheckBox ("@IDS_PROCESSING_SNAP_LOOPS_LABEL", "@IDS_PROCESSING_SNAP_LOOPS_TOOLTIP");

        final BoxPanel panel5 = new TwoColsPanel ();
        this.transposeCombobox = panel5.createComboBox ("@IDS_PROCESSING_TRANSPOSE_LABEL", "@IDS_PROCESSING_TRANSPOSE_TOOLTIP", TRANSPOSE);

        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.enableProcessingCheckbox = panel.createCheckBox ("@IDS_PROCESSING_ENABLE", "@IDS_PROCESSING_ENABLE_TOOLTIP");
        panel.addComponent (layoutPane (panel1, "@IDS_PROCESSING_NORMALIZE_HEADER"));
        panel.addComponent (layoutPane (panel2, "@IDS_PROCESSING_MINIMIZE_HEADER"));
        panel.addComponent (layoutPane (panel3, "@IDS_PROCESSING_RESOLUTION"));
        panel.addComponent (layoutPane (panel4, "@IDS_PROCESSING_LOOPS"));
        panel.addComponent (layoutPane (panel5, "@IDS_PROCESSING_PITCH"));
        this.setButtons ("@IDS_SETTINGS_DLG_OK", "@IDS_SETTINGS_DLG_CANCEL");

        this.traversalManager.add (this.normalizeCheckbox);
        this.traversalManager.add (this.makeMonoCheckbox);
        this.traversalManager.add (this.trimSample);
        this.traversalManager.add (this.maxSamplesField);
        this.traversalManager.add (this.reduceBitDepthCombobox);
        this.traversalManager.add (this.reduceFrequencyCombobox);
        this.traversalManager.add (this.alwaysResampleCheckbox);
        this.traversalManager.add (this.loopCrossfadesCombobox);
        this.traversalManager.add (this.snapLoopsCheckbox);
        this.traversalManager.add (this.transposeCombobox);
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


    private static final TitledPane layoutPane (final BoxPanel panel, final String label)
    {
        final TitledPane titledPane = panel.wrapInTitledPane (label);
        titledPane.setExpanded (true);
        titledPane.setCollapsible (false);
        return titledPane;
    }
}
