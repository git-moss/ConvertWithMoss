// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.settings;

import java.util.Map;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Settings for writing WAV chunk updates.
 *
 * @author Jürgen Moßgraber
 */
public class WavChunkSettingsUI implements ICoreTaskSettings
{
    // Metadata constants
    private static final String WRITE_BROADCAST_AUDIO_CHUNK  = "WriteBroadcastAudioChunk";
    private static final String WRITE_INSTRUMENT_CHUNK       = "WriteInstrumentChunk";
    private static final String WRITE_SAMPLE_CHUNK           = "WriteSampleChunk";
    private static final String REMOVE_JUNK_CHUNK            = "RemoveJunkChunk";

    protected final String      prefix;

    // Metadata options
    private CheckBox            updateBroadcastAudioChunkBox = null;
    private CheckBox            updateInstrumentChunkBox     = null;
    private CheckBox            updateSampleChunkBox         = null;
    private CheckBox            removeJunkChunksBox          = null;

    private boolean             updateBroadcastAudioChunk    = false;
    private boolean             updateInstrumentChunk        = false;
    private boolean             updateSampleChunk            = false;
    private boolean             removeJunkChunks             = false;


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
     */
    public WavChunkSettingsUI (final String prefix, final boolean updateBroadcastAudioChunk, final boolean updateInstrumentChunk, final boolean updateSampleChunk, final boolean removeJunkChunks)
    {
        this.prefix = prefix;
        this.updateBroadcastAudioChunk = updateBroadcastAudioChunk;
        this.updateInstrumentChunk = updateInstrumentChunk;
        this.updateSampleChunk = updateSampleChunk;
        this.removeJunkChunks = removeJunkChunks;
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    protected TitledSeparator addWavChunkOptions (final BoxPanel panel)
    {
        final TitledSeparator separator = panel.createSeparator ("@IDS_WAV_CHUNK_TITLE");
        this.updateBroadcastAudioChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_BEXT_CHUNK");
        this.updateInstrumentChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_INSTRUMENT_CHUNK");
        this.updateSampleChunkBox = panel.createCheckBox ("@IDS_WAV_WRITE_SAMPLE_CHUNK");
        this.removeJunkChunksBox = panel.createCheckBox ("@IDS_WAV_CHUNK_REMOVE");
        return separator;
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig configuration)
    {
        configuration.setBoolean (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK, this.updateBroadcastAudioChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + WRITE_INSTRUMENT_CHUNK, this.updateInstrumentChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + WRITE_SAMPLE_CHUNK, this.updateSampleChunkBox.isSelected ());
        configuration.setBoolean (this.prefix + REMOVE_JUNK_CHUNK, this.removeJunkChunksBox.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig configuration)
    {
        this.updateBroadcastAudioChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_BROADCAST_AUDIO_CHUNK, this.updateBroadcastAudioChunk));
        this.updateInstrumentChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_INSTRUMENT_CHUNK, this.updateInstrumentChunk));
        this.updateSampleChunkBox.setSelected (configuration.getBoolean (this.prefix + WRITE_SAMPLE_CHUNK, this.updateSampleChunk));
        this.removeJunkChunksBox.setSelected (configuration.getBoolean (this.prefix + REMOVE_JUNK_CHUNK, this.removeJunkChunks));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkSettingsUI (final INotifier notifier)
    {
        this.updateBroadcastAudioChunk = this.updateBroadcastAudioChunkBox.isSelected ();
        this.updateInstrumentChunk = this.updateInstrumentChunkBox.isSelected ();
        this.updateSampleChunk = this.updateSampleChunkBox.isSelected ();
        this.removeJunkChunks = this.removeJunkChunksBox.isSelected ();
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
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public String [] getCLIParameterNames ()
    {
        return new String []
        {
            this.prefix + WRITE_BROADCAST_AUDIO_CHUNK,
            this.prefix + WRITE_INSTRUMENT_CHUNK,
            this.prefix + WRITE_SAMPLE_CHUNK,
            this.prefix + REMOVE_JUNK_CHUNK
        };
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
     * Check if either a chunk option is enabled or bit resolution / frequency needs to be
     * re-sampled.
     *
     * @param destinationFormat The destination audio format
     * @return True if at least one is enabled
     */
    public boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        return this.isUpdateBroadcastAudioChunk () || this.isUpdateInstrumentChunk () || this.isUpdateSampleChunk () || this.isRemoveJunkChunks () || destinationFormat.getBitResolutions () != null || destinationFormat.getMaxSampleRate () != -1;
    }
}
