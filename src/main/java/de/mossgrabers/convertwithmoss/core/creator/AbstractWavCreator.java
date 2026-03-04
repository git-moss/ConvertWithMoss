// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.creator;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.wav.BroadcastAudioExtensionChunk;
import de.mossgrabers.convertwithmoss.file.wav.InstrumentChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk.SampleChunkLoop;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.file.wav.WaveRiffChunkId;


/**
 * A creator which uses WAV files to store the samples.
 *
 * @param <T> The type of the settings
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractWavCreator<T extends WavChunkSettingsUI> extends AbstractCreator<T>
{
    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     * @param settingsConfiguration The configuration of the settings
     */
    protected AbstractWavCreator (final String name, final String prefix, final INotifier notifier, final T settingsConfiguration)
    {
        super (name, prefix, notifier, settingsConfiguration);
    }


    /** {@inheritDoc} */
    @Override
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return zone.getName () + ".wav";
    }


    /** {@inheritDoc} */
    @Override
    protected boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        return this.settingsConfiguration.requiresRewrite (destinationFormat);
    }


    /** {@inheritDoc} */
    @Override
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final ISampleData sampleData = zone.getSampleData ();
        if (sampleData == null)
            return;

        // Convert resolution
        final WaveFile wavFile = AudioFileUtils.convertToWav (sampleData, destinationFormat);

        // Trim sample from zone start to end
        if (trim)
            trimStartToEnd (wavFile, zone);

        // Update information chunks
        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
            updateBroadcastAudioChunk (multisampleSource.getMetadata (), wavFile);
        final int unityNote = Math.clamp (zone.getKeyRoot (), 0, 127);
        if (this.settingsConfiguration.isUpdateInstrumentChunk ())
            updateInstrumentChunk (zone, wavFile, unityNote);
        if (this.settingsConfiguration.isUpdateSampleChunk ())
            updateSampleChunk (zone, wavFile, unityNote);
        if (this.settingsConfiguration.isRemoveJunkChunks ())
            wavFile.removeChunks (CommonRiffChunkId.JUNK_ID, CommonRiffChunkId.JUNK2_ID, WaveRiffChunkId.FILLER_ID, WaveRiffChunkId.MD5_ID);

        wavFile.write (outputStream);
    }


    private static void updateSampleChunk (final ISampleZone zone, final WaveFile wavFile, final int unityNote)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        final SampleChunk sampleChunk = new SampleChunk (loops.size ());
        sampleChunk.setSamplePeriod ((int) Math.round (1000000000.0 / wavFile.getFormatChunk ().getSampleRate ()));
        sampleChunk.setPitch (unityNote, (int) Math.round (zone.getTuning () * 100.0));

        final List<SampleChunkLoop> chunkLoops = sampleChunk.getLoops ();
        for (int i = 0; i < loops.size (); i++)
        {
            final ISampleLoop sampleLoop = loops.get (i);
            final SampleChunkLoop sampleChunkLoop = chunkLoops.get (i);
            switch (sampleLoop.getType ())
            {
                default:
                case FORWARDS:
                    sampleChunkLoop.setType (SampleChunk.LOOP_FORWARD);
                    break;
                case ALTERNATING:
                    sampleChunkLoop.setType (SampleChunk.LOOP_ALTERNATING);
                    break;
                case BACKWARDS:
                    sampleChunkLoop.setType (SampleChunk.LOOP_BACKWARDS);
                    break;
            }
            sampleChunkLoop.setStart (sampleLoop.getStart ());
            sampleChunkLoop.setEnd (sampleLoop.getEnd ());
        }

        wavFile.setSampleChunk (sampleChunk);
    }


    private static void updateInstrumentChunk (final ISampleZone zone, final WaveFile wavFile, final int unityNote)
    {
        InstrumentChunk instrumentChunk = wavFile.getInstrumentChunk ();
        if (instrumentChunk == null)
        {
            instrumentChunk = new InstrumentChunk ();
            wavFile.setInstrumentChunk (instrumentChunk);
        }

        instrumentChunk.setUnshiftedNote (unityNote);
        instrumentChunk.setFineTune (Math.clamp ((int) (zone.getTuning () * 100), -50, 50));
        instrumentChunk.setGain (Math.clamp ((int) zone.getGain (), -127, 127));
        instrumentChunk.setLowNote (Math.clamp (zone.getKeyLow (), 0, 127));
        instrumentChunk.setHighNote (Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127));
        instrumentChunk.setLowVelocity (Math.clamp (zone.getVelocityLow (), 0, 127));
        instrumentChunk.setHighVelocity (Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 0, 127));
    }


    private static void updateBroadcastAudioChunk (final IMetadata metadata, final WaveFile wavFile)
    {
        BroadcastAudioExtensionChunk broadcastAudioChunk = wavFile.getBroadcastAudioExtensionChunk ();
        if (broadcastAudioChunk == null)
        {
            broadcastAudioChunk = new BroadcastAudioExtensionChunk ();
            wavFile.setBroadcastAudioExtensionChunk (broadcastAudioChunk);
        }

        broadcastAudioChunk.setDescription (metadata.getDescription ());
        broadcastAudioChunk.setOriginator (metadata.getCreator ());
        Date creationDateTime = metadata.getCreationDateTime ();
        if (creationDateTime == null)
            creationDateTime = new Date ();
        broadcastAudioChunk.setOriginationDateTime (creationDateTime);
    }
}
