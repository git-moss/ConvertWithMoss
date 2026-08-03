// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for the sample file creator: the audio file format to write and the WAV chunk options.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreatorUI extends WavChunkSettingsUI
{
    private static final String       OUTPUT_FORMAT = "OutputFormat";

    private static final List<String> FORMAT_NAMES  = new ArrayList<> ();
    static
    {
        for (final SampleFileFormat format: SampleFileFormat.values ())
            FORMAT_NAMES.add (format.getName ());
    }

    private ComboBox<String> outputFormatComboBox;
    private SampleFileFormat outputFormat = SampleFileFormat.WAV;


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     * @param updateBroadcastAudioChunk Should the broadcast audio chunk be added/updated?
     * @param updateInstrumentChunk Should the instrument chunk be added/updated?
     * @param updateSampleChunk Should the sample chunk be added/updated?
     * @param removeJunkChunks Shall junk chunks be removed?
     */
    public WavCreatorUI (final String prefix, final boolean updateBroadcastAudioChunk, final boolean updateInstrumentChunk, final boolean updateSampleChunk, final boolean removeJunkChunks)
    {
        super (prefix, updateBroadcastAudioChunk, updateInstrumentChunk, updateSampleChunk, removeJunkChunks);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_WAV_OUTPUT_FORMAT_TITLE");
        this.outputFormatComboBox = panel.createComboBox ("@IDS_WAV_OUTPUT_FORMAT", FORMAT_NAMES);
        this.outputFormatComboBox.getSelectionModel ().select (SampleFileFormat.WAV.getName ());

        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig configuration)
    {
        super.saveSettings (configuration);

        configuration.setProperty (this.prefix + OUTPUT_FORMAT, this.outputFormatComboBox.getSelectionModel ().getSelectedItem ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig configuration)
    {
        super.loadSettings (configuration);

        final SampleFileFormat format = SampleFileFormat.getByName (configuration.getProperty (this.prefix + OUTPUT_FORMAT, SampleFileFormat.WAV.getName ()));
        this.outputFormatComboBox.getSelectionModel ().select ((format == null ? SampleFileFormat.WAV : format).getName ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        if (!super.checkSettingsUI (notifier))
            return false;

        final SampleFileFormat format = SampleFileFormat.getByName (this.outputFormatComboBox.getSelectionModel ().getSelectedItem ());
        this.outputFormat = format == null ? SampleFileFormat.WAV : format;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        if (!super.checkSettingsCLI (notifier, parameters))
            return false;

        final String value = parameters.remove (this.prefix + OUTPUT_FORMAT);
        if (value == null || value.isBlank ())
        {
            this.outputFormat = SampleFileFormat.WAV;
            return true;
        }

        final SampleFileFormat format = SampleFileFormat.getByName (value);
        if (format == null)
        {
            notifier.logError ("IDS_CLI_UNKNOWN_FILE_TYPE", value);
            return false;
        }
        this.outputFormat = format;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (super.getCLIParameterNames ()));
        parameterNames.add (this.prefix + OUTPUT_FORMAT);
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /** {@inheritDoc} */
    @Override
    public boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        // All formats except WAV need to be re-written in any case
        return this.outputFormat != SampleFileFormat.WAV || super.requiresRewrite (destinationFormat);
    }


    /**
     * Get the audio file format to write.
     *
     * @return The format
     */
    public SampleFileFormat getOutputFormat ()
    {
        return this.outputFormat;
    }
}
