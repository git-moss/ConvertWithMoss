// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.creator.SampleFileFormat;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;


/**
 * Settings for writing WAV chunk updates. Optionally, the audio file format in which the sample
 * files are written can be selected, if the destination format supports more than WAV.
 *
 * @author Jürgen Moßgraber
 */
public class WavChunkSettingsUI implements ICoreTaskSettings
{
    // Metadata constants
    private static final String          WRITE_BROADCAST_AUDIO_CHUNK  = "WriteBroadcastAudioChunk";
    private static final String          WRITE_INSTRUMENT_CHUNK       = "WriteInstrumentChunk";
    private static final String          WRITE_SAMPLE_CHUNK           = "WriteSampleChunk";
    private static final String          REMOVE_JUNK_CHUNK            = "RemoveJunkChunk";
    private static final String          SAMPLE_FILE_FORMAT           = "SampleFileFormat";
    private static final String          CSS_TITLED_SEPARATOR_PANE    = "titled-separator-pane";

    protected final String               prefix;

    // Metadata options
    private CheckBox                     updateBroadcastAudioChunkBox = null;
    private CheckBox                     updateInstrumentChunkBox     = null;
    private CheckBox                     updateSampleChunkBox         = null;
    private CheckBox                     removeJunkChunksBox          = null;
    private ComboBox<String>             sampleFileFormatComboBox     = null;

    private boolean                      updateBroadcastAudioChunk    = false;
    private boolean                      updateInstrumentChunk        = false;
    private boolean                      updateSampleChunk            = false;
    private boolean                      removeJunkChunks             = false;

    private final List<SampleFileFormat> sampleFileFormats;
    private SampleFileFormat             sampleFileFormat;


    /**
     * Constructor. All chunk creations are set to false.
     *
     * @param prefix The prefix to use for the identifier
     */
    public WavChunkSettingsUI (final String prefix)
    {
        this (prefix, false, false, false, false);
    }


    /**
     * Constructor.
     *
     * @param prefix The prefix to use for the identifier
     * @param updateBroadcastAudioChunk Should the broadcast audio chunk be added/updated?
     * @param updateInstrumentChunk Should the instrument chunk be added/updated?
     * @param updateSampleChunk Should the sample chunk be added/updated?
     * @param removeJunkChunks Shall junk chunks be removed?
     * @param sampleFileFormats The audio file formats in which the destination format can store its
     *            samples. The first one is the default. If none or only one is given, no selection
     *            is offered and the first one (or WAV) is always used
     */
    public WavChunkSettingsUI (final String prefix, final boolean updateBroadcastAudioChunk, final boolean updateInstrumentChunk, final boolean updateSampleChunk, final boolean removeJunkChunks, final SampleFileFormat... sampleFileFormats)
    {
        this.prefix = prefix;
        this.updateBroadcastAudioChunk = updateBroadcastAudioChunk;
        this.updateInstrumentChunk = updateInstrumentChunk;
        this.updateSampleChunk = updateSampleChunk;
        this.removeJunkChunks = removeJunkChunks;
        this.sampleFileFormats = sampleFileFormats.length == 0 ? List.of (SampleFileFormat.WAV) : List.of (sampleFileFormats);
        this.sampleFileFormat = this.sampleFileFormats.get (0);
    }


    /** {@inheritDoc} */
    @Override
    public Pane getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /**
     * Add the selection of the audio file format (if there is more than one supported) and the
     * options for the WAV chunks to the given panel.
     *
     * @param panel The panel to which to add the widgets
     * @return The first created separator
     */
    protected TitledSeparator addWavChunkOptions (final BoxPanel panel)
    {
        TitledSeparator formatSeparator = null;
        if (this.hasSampleFileFormatSelection ())
        {
            formatSeparator = panel.createSeparator ("@IDS_WAV_OUTPUT_FORMAT_TITLE");
            final List<String> formatNames = new ArrayList<> ();
            for (final SampleFileFormat format: this.sampleFileFormats)
                formatNames.add (format.getName ());
            this.sampleFileFormatComboBox = panel.createComboBox ("@IDS_WAV_OUTPUT_FORMAT", formatNames);
            this.sampleFileFormatComboBox.getSelectionModel ().select (0);
        }

        final TitledSeparator separator = panel.createSeparator ("@IDS_WAV_CHUNK_TITLE");
        if (formatSeparator != null)
            separator.getStyleClass ().add (CSS_TITLED_SEPARATOR_PANE);
        this.updateBroadcastAudioChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_BEXT_CHUNK");
        this.updateInstrumentChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_INSTRUMENT_CHUNK");
        this.updateSampleChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_SAMPLE_CHUNK");
        this.removeJunkChunksBox = panel.createCheckBox ("@IDS_WAV_CHUNK_REMOVE");
        return formatSeparator == null ? separator : formatSeparator;
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig configuration)
    {
        configuration.setBoolean (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK, this.updateBroadcastAudioChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + WRITE_INSTRUMENT_CHUNK, this.updateInstrumentChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + WRITE_SAMPLE_CHUNK, this.updateSampleChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + REMOVE_JUNK_CHUNK, this.removeJunkChunksBox.isSelected ());
        if (this.sampleFileFormatComboBox != null)
            configuration.setProperty (this.getSampleFileFormatKey (), this.sampleFileFormatComboBox.getSelectionModel ().getSelectedItem ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig configuration)
    {
        this.updateBroadcastAudioChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK, this.updateBroadcastAudioChunk));
        this.updateInstrumentChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_INSTRUMENT_CHUNK, this.updateInstrumentChunk));
        this.updateSampleChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_SAMPLE_CHUNK, this.updateSampleChunk));
        this.removeJunkChunksBox.setSelected (configuration.getBoolean (this.prefix + REMOVE_JUNK_CHUNK, this.removeJunkChunks));
        if (this.sampleFileFormatComboBox != null)
        {
            final SampleFileFormat format = this.getSupportedFormat (configuration.getProperty (this.getSampleFileFormatKey (), this.sampleFileFormats.get (0).getName ()));
            this.sampleFileFormatComboBox.getSelectionModel ().select ((format == null ? this.sampleFileFormats.get (0) : format).getName ());
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.updateBroadcastAudioChunk = this.updateBroadcastAudioChunkBox.isSelected ();
        this.updateInstrumentChunk = this.updateInstrumentChunkBox.isSelected ();
        this.updateSampleChunk = this.updateSampleChunkBox.isSelected ();
        this.removeJunkChunks = this.removeJunkChunksBox.isSelected ();
        if (this.sampleFileFormatComboBox != null)
        {
            final SampleFileFormat format = this.getSupportedFormat (this.sampleFileFormatComboBox.getSelectionModel ().getSelectedItem ());
            this.sampleFileFormat = format == null ? this.sampleFileFormats.get (0) : format;
        }
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsCLI (final INotifier notifier, final Map<String, String> parameters)
    {
        String value = parameters.remove (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK);
        this.updateBroadcastAudioChunk = "1".equals (value);

        value = parameters.remove (this.prefix + WRITE_INSTRUMENT_CHUNK);
        this.updateInstrumentChunk = "1".equals (value);

        value = parameters.remove (this.prefix + WRITE_SAMPLE_CHUNK);
        this.updateSampleChunk = "1".equals (value);

        value = parameters.remove (this.prefix + REMOVE_JUNK_CHUNK);
        this.removeJunkChunks = "1".equals (value);

        if (!this.hasSampleFileFormatSelection ())
            return true;
        value = parameters.remove (this.getSampleFileFormatKey ());
        if (value == null || value.isBlank ())
        {
            this.sampleFileFormat = this.sampleFileFormats.get (0);
            return true;
        }
        final SampleFileFormat format = this.getSupportedFormat (value);
        if (format == null)
        {
            notifier.logError ("IDS_WAV_UNSUPPORTED_OUTPUT_FORMAT", value, this.getSampleFileFormatNames ());
            return false;
        }
        this.sampleFileFormat = format;
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        final List<String> parameterNames = new ArrayList<> (Arrays.asList (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK, this.prefix + WRITE_INSTRUMENT_CHUNK, this.prefix + WRITE_SAMPLE_CHUNK, this.prefix + REMOVE_JUNK_CHUNK));
        if (this.hasSampleFileFormatSelection ())
            parameterNames.add (this.getSampleFileFormatKey ());
        return parameterNames.toArray (new String [parameterNames.size ()]);
    }


    /**
     * Should the broadcast audio chunk be added/updated?
     *
     * @return True if the broadcast audio chunk be updated
     */
    public boolean isUpdateBroadcastAudioChunk ()
    {
        return this.updateBroadcastAudioChunk;
    }


    /**
     * Should the instrument chunk be added/updated?
     *
     * @return True if the instrument chunk be updated
     */
    public boolean isUpdateInstrumentChunk ()
    {
        return this.updateInstrumentChunk;
    }


    /**
     * Should the sample chunk be added/updated?
     *
     * @return True if the sample chunk be updated
     */
    public boolean isUpdateSampleChunk ()
    {
        return this.updateSampleChunk;
    }


    /**
     * Shall junk chunks be removed?
     *
     * @return True if junk chunks should be removed
     */
    public boolean isRemoveJunkChunks ()
    {
        return this.removeJunkChunks;
    }


    /**
     * Get the audio file format in which to write the sample files.
     *
     * @return The format
     */
    public SampleFileFormat getSampleFileFormat ()
    {
        return this.sampleFileFormat;
    }


    /**
     * Set the audio file format in which to write the sample files. Ignored if the format is not
     * supported.
     *
     * @param sampleFileFormat The format
     */
    protected void setSampleFileFormat (final SampleFileFormat sampleFileFormat)
    {
        if (sampleFileFormat != null && this.sampleFileFormats.contains (sampleFileFormat))
            this.sampleFileFormat = sampleFileFormat;
    }


    /**
     * Check if either a chunk option is enabled, bit resolution / frequency needs to be re-sampled
     * or the sample needs to be stored in a different audio file format than WAV.
     *
     * @param destinationFormat The destination audio format
     * @return True if at least one is enabled
     */
    public boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        return this.sampleFileFormat != SampleFileFormat.WAV || this.isUpdateBroadcastAudioChunk () || this.isUpdateInstrumentChunk () || this.isUpdateSampleChunk () || this.isRemoveJunkChunks () || destinationFormat.getBitResolutions () != null || destinationFormat.getMaxSampleRate () != -1;
    }


    /**
     * Get the identifier under which the selected audio file format is stored in the settings and
     * which is the name of the CLI parameter.
     *
     * @return The identifier including the prefix
     */
    protected String getSampleFileFormatKey ()
    {
        return this.prefix + SAMPLE_FILE_FORMAT;
    }


    /**
     * Is there more than one supported audio file format to select from?
     *
     * @return True if there is a selection
     */
    private boolean hasSampleFileFormatSelection ()
    {
        return this.sampleFileFormats.size () > 1;
    }


    /**
     * Look up a supported audio file format by its name.
     *
     * @param name The name of the format, case does not matter
     * @return The format or null if it is unknown or not supported
     */
    private SampleFileFormat getSupportedFormat (final String name)
    {
        final SampleFileFormat format = SampleFileFormat.getByName (name);
        return format != null && this.sampleFileFormats.contains (format) ? format : null;
    }


    /**
     * Get the names of all supported audio file formats.
     *
     * @return The names separated by commas
     */
    private String getSampleFileFormatNames ()
    {
        final List<String> names = new ArrayList<> ();
        for (final SampleFileFormat format: this.sampleFileFormats)
            names.add (format.getName ());
        return String.join (", ", names);
    }
}
