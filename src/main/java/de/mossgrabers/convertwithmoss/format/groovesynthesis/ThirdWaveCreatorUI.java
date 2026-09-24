// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.ICoreTaskSettings;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the Groove Synthesis 3rd Wave creator: whether a unified program file with its
 * samples (*.pgdata) or multi-sample slot files (*.bin) are written.
 *
 * @author Jürgen Moßgraber
 */
public class ThirdWaveCreatorUI implements ICoreTaskSettings
{
    private static final String OUTPUT_FORMAT  = "OutputFormat";
    private static final String FORMAT_PROGRAM = "pgdata";
    private static final String FORMAT_SLOTS   = "bin";

    private final String        prefix;

    private ComboBox<String>    outputFormatBox;
    private boolean             writeProgram   = true;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     */
    public ThirdWaveCreatorUI (final String prefix)
    {
        this.prefix = prefix;
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        this.outputFormatBox = panel.createComboBox ("@IDS_THIRD_WAVE_OUTPUT_FORMAT", List.of (Functions.getMessage ("IDS_THIRD_WAVE_OUTPUT_PROGRAM"), Functions.getMessage ("IDS_THIRD_WAVE_OUTPUT_SLOTS")));
        this.outputFormatBox.setMaxWidth (Double.MAX_VALUE);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.outputFormatBox.getSelectionModel ().select (config.getBoolean (this.prefix + OUTPUT_FORMAT, true) ? 0 : 1);
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (this.prefix + OUTPUT_FORMAT, this.outputFormatBox.getSelectionModel ().getSelectedIndex () != 1);
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.writeProgram = this.outputFormatBox.getSelectionModel ().getSelectedIndex () != 1;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        final String format = parameters.remove (this.prefix + OUTPUT_FORMAT);
        this.writeProgram = true;
        if (format == null)
            return true;
        switch (format.trim ().toLowerCase (Locale.US))
        {
            case FORMAT_PROGRAM:
                return true;
            case FORMAT_SLOTS:
                this.writeProgram = false;
                return true;
            default:
                notifier.logError ("IDS_THIRD_WAVE_UNKNOWN_OUTPUT_FORMAT", format);
                return false;
        }
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + OUTPUT_FORMAT
        };
    }


    /**
     * Should a unified program file be written instead of multi-sample slot files?
     *
     * @return True to write a unified program file
     */
    public boolean isWriteProgram ()
    {
        return this.writeProgram;
    }
}
