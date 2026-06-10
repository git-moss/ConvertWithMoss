// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.file.wav.WaveRiffChunkId;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronKeyZone;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronSampleSlot;
import de.mossgrabers.convertwithmoss.format.elektron.ElektronMultiFile.ElektronVelocityLayer;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for preset files for the Elektron Tonverk (elmulti). A preset has a description file and
 * the related samples are in the same folder.
 *
 * @author Jürgen Moßgraber
 */
public class ElektronMultiCreator extends AbstractWavCreator<ElektronMultiCreatorUI>
{
    /**
     * The factory default velocity. The Tonverk rejects the whole preset file if a velocity layer
     * has a velocity of exactly 0.0 (the WAV files are then imported as loose samples instead)!
     */
    private static final double                 DEFAULT_VELOCITY       = 0.49411765;

    /**
     * Loops with a length up to this number of samples (after re-sampling) are considered to be
     * single-cycle waveforms for which the loop length is kept as exact as possible.
     */
    private static final int                    SINGLE_CYCLE_THRESHOLD = 512;

    private static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    private static final DestinationAudioFormat DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat (new int []
    {
        16,
        24
    }, -1, false);
    private static final Set<Integer>           SUPPORTED_BIT_DEPTHS   = new HashSet<> ();
    static
    {
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (16));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (24));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ElektronMultiCreator (final INotifier notifier)
    {
        super ("Elektron Tonverk", "Emulti", notifier, new ElektronMultiCreatorUI ("Emulti"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean resample = this.settingsConfiguration.resampleTo2448 ();

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        if (!presetFolder.mkdir ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());
            return;
        }
        final String presetName = presetFolder.getName ();

        multisampleSource.setGroups (this.combineSplitStereo (multisampleSource));

        // Rename all zones to the Elektron sample naming convention and make sure that all zones
        // have a valid start/stop range set which is required for trimming
        prepareZones (presetName, multisampleSource);

        // Must be done before the samples are written and before the preset data is created!
        if (resample)
            recalculateForResample (multisampleSource);

        // Store all samples - they are physically trimmed to the zone start/stop since the
        // Tonverk does not support 'trim-start'/'trim-end' on separate per-sample WAV files and
        // rejects such presets. Trimming updates the zone and loop positions accordingly!
        this.writeSamples (presetFolder, multisampleSource, resample ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT, true);

        // Create the preset file - must be done after the samples were written since trimming
        // does update the zone/loop positions!
        final ElektronMultiFile elektronMulti = createPreset (multisampleSource);
        final String presetFile = presetName + ".elmulti";
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile);
        elektronMulti.write (new File (presetFolder, presetFile).toPath ());

        this.progress.notifyDone ();
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
        if (wavFile.getDataChunk () == null)
            throw new IOException (Functions.getMessage ("IDS_WAV_CONVERSION_FAILED", zone.getName ()));

        // Trim sample from zone start to end. Afterwards, clamp all loops into the trimmed
        // boundaries (inclusive end convention) like the reference implementation
        if (trim)
        {
            trimStartToEnd (wavFile, zone);
            clampLoops (zone);
        }

        // Update information chunks
        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
            updateBroadcastAudioChunk (multisampleSource.getMetadata (), wavFile);
        if (this.settingsConfiguration.isUpdateInstrumentChunk ())
            updateInstrumentChunk (zone, wavFile);
        // Contrary to the base class, only write a sample chunk when there is a loop. The factory
        // WAV files do not contain empty sample chunks and the Tonverk WAV parser is strict!
        if (this.settingsConfiguration.isUpdateSampleChunk () && hasLoop (zone))
            updateSampleChunk (zone, wavFile);
        if (this.settingsConfiguration.isRemoveJunkChunks ())
            wavFile.removeChunks (CommonRiffChunkId.JUNK_ID, CommonRiffChunkId.JUNK2_ID, WaveRiffChunkId.FILLER_ID, WaveRiffChunkId.MD5_ID);

        wavFile.write (outputStream);
    }


    /**
     * Renames all zones to the Elektron sample file naming convention
     * ('InstrumentName-VVV-NNN-note') and makes sure that the start/stop range of all zones is
     * fully set, which is required for trimming the WAV files.
     *
     * @param presetName The name of the preset which is used as the prefix of all sample names
     * @param multiSampleSource The multi-sample source
     * @throws IOException Could not read the audio metadata of a sample
     */
    private static void prepareZones (final String presetName, final IMultisampleSource multiSampleSource) throws IOException
    {
        for (final Entry<Integer, TreeMap<Integer, List<ISampleZone>>> velocityLayerMapEntry: multiSampleSource.getOrderedSampleZones (false).entrySet ())
        {
            final int keyRoot = Math.clamp (velocityLayerMapEntry.getKey ().intValue (), 0, 127);

            int velocityLayerIndex = 0;
            for (final List<ISampleZone> sampleZones: velocityLayerMapEntry.getValue ().values ())
            {
                for (int roundRobinIndex = 0; roundRobinIndex < sampleZones.size (); roundRobinIndex++)
                {
                    final ISampleZone zone = sampleZones.get (roundRobinIndex);
                    zone.setName (ElektronMultiFile.createSampleName (presetName, velocityLayerIndex, keyRoot, roundRobinIndex));

                    final ISampleData sampleData = zone.getSampleData ();
                    if (sampleData == null)
                        continue;
                    final int numberOfSamples = sampleData.getAudioMetadata ().getNumberOfSamples ();
                    final int stop = zone.getStop ();
                    if (stop <= 0 || stop > numberOfSamples)
                        zone.setStop (numberOfSamples);
                    final int start = zone.getStart ();
                    if (start < 0 || start >= zone.getStop ())
                        zone.setStart (0);
                }

                velocityLayerIndex++;
            }
        }
    }


    private static ElektronMultiFile createPreset (final IMultisampleSource multiSampleSource)
    {
        final ElektronMultiFile elektronMulti = new ElektronMultiFile ();
        elektronMulti.name = multiSampleSource.getName ();

        for (final Entry<Integer, TreeMap<Integer, List<ISampleZone>>> velocityLayerMapEntry: multiSampleSource.getOrderedSampleZones (false).entrySet ())
        {
            final ElektronKeyZone keyZone = new ElektronKeyZone ();
            elektronMulti.keyZones.add (keyZone);

            final int keyRoot = Math.clamp (velocityLayerMapEntry.getKey ().intValue (), 0, 127);
            keyZone.pitch = keyRoot;

            Double tuning = null;
            boolean tuningIsSame = true;

            for (final Entry<Integer, List<ISampleZone>> sampleZonesEntry: velocityLayerMapEntry.getValue ().entrySet ())
            {
                final ElektronVelocityLayer velocityLayer = new ElektronVelocityLayer ();
                keyZone.velocityLayers.add (velocityLayer);

                // The Tonverk rejects a velocity of exactly 0.0, use the factory default instead
                final double velocity = Math.clamp (sampleZonesEntry.getKey ().intValue (), 0, 127) / 127.0;
                velocityLayer.velocity = velocity <= 0 ? DEFAULT_VELOCITY : velocity;

                for (final ISampleZone sampleZone: sampleZonesEntry.getValue ())
                {
                    final ElektronSampleSlot sampleSlot = new ElektronSampleSlot ();
                    velocityLayer.sampleSlots.add (sampleSlot);

                    // Must be identical to the file name created in writeSamples!
                    sampleSlot.sample = createSafeFilename (sampleZone.getName ()) + ".wav";

                    // Note: 'trim-start'/'trim-end' must not be set! They are only supported for
                    // single-file multi-samples. The WAV files are physically trimmed instead.

                    final boolean isLooped = hasLoop (sampleZone);
                    sampleSlot.loopMode = isLooped ? "Forward" : "Off";
                    if (isLooped)
                    {
                        final ISampleLoop sampleLoop = sampleZone.getLoops ().get (0);
                        sampleSlot.loopStart = Integer.valueOf (Math.max (0, sampleLoop.getStart ()));
                        sampleSlot.loopEnd = Integer.valueOf (sampleLoop.getEnd ());
                        final int crossfade = sampleLoop.getCrossfadeInSamples ();
                        if (crossfade > 0)
                            sampleSlot.loopCrossfade = Integer.valueOf (crossfade);
                        // Continue to loop in the release phase which is the default behavior of
                        // all source formats
                        sampleSlot.keepLoopingOnRelease = Boolean.TRUE;
                    }

                    if (tuningIsSame)
                        if (tuning == null)
                            tuning = Double.valueOf (sampleZone.getTuning ());
                        else if (tuning.doubleValue () != sampleZone.getTuning ())
                            tuningIsSame = false;
                }
            }

            // The key-center moves in the opposite direction of the tuning: the Tonverk
            // transposes the sample by 'pitch - key-center' semi-tones
            keyZone.keyCenter = keyRoot - (tuningIsSame && tuning != null ? tuning.doubleValue () : 0);
        }

        return elektronMulti;
    }


    private static boolean hasLoop (final ISampleZone zone)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            return false;
        final ISampleLoop loop = loops.get (0);
        return loop.getEnd () > Math.max (0, loop.getStart ());
    }


    /**
     * Re-calculates the sample start, stop and loop positions for 48kHz. Contrary to
     * recalculateSamplePositions, the loop points are handled like in the reference
     * implementation: normal loop points are truncated; short 'single-cycle' loops keep their
     * exact length to prevent pitch drift (rounding both end points individually can change the
     * loop length by 1 sample which detunes a e.g. 90 samples long waveform by about 19 cents).
     *
     * @param multisampleSource The multi-sample source
     * @throws IOException Could not retrieve the current sample rate
     */
    private static void recalculateForResample (final IMultisampleSource multisampleSource) throws IOException
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final ISampleData sampleData = zone.getSampleData ();
                if (sampleData == null)
                    continue;
                final double ratio = 48000.0 / sampleData.getAudioMetadata ().getSampleRate ();
                if (ratio == 1)
                    continue;

                final int start = zone.getStart ();
                if (start > 0)
                    zone.setStart ((int) Math.round (start * ratio));
                final int stop = zone.getStop ();
                if (stop > 0)
                    zone.setStop ((int) Math.round (stop * ratio));

                for (final ISampleLoop loop: zone.getLoops ())
                {
                    final int loopStart = loop.getStart ();
                    final int loopEnd = loop.getEnd ();
                    if (loopEnd <= 0)
                        continue;

                    // Loop ends are inclusive
                    final int scaledLength = Math.max (1, (int) Math.round ((loopEnd - Math.max (0, loopStart) + 1) * ratio));
                    if (scaledLength <= SINGLE_CYCLE_THRESHOLD)
                    {
                        // Single-cycle waveform: scale the length and round only once
                        final int newStart = Math.max (0, (int) Math.round (loopStart * ratio));
                        loop.setStart (newStart);
                        loop.setEnd (newStart + scaledLength - 1);
                    }
                    else
                    {
                        // Normal loop: truncate like the reference implementation
                        if (loopStart > 0)
                            loop.setStart ((int) (loopStart * ratio));
                        loop.setEnd ((int) (loopEnd * ratio));
                    }
                }
            }
    }


    /**
     * Clamps all loops of the zone into the sample boundaries (loop ends are inclusive). Must be
     * called after trimming since the zone stop is then equal to the number of samples of the
     * written WAV file.
     *
     * @param zone The zone
     */
    private static void clampLoops (final ISampleZone zone)
    {
        final int lastIndex = zone.getStop () - 1;
        if (lastIndex < 0)
            return;
        for (final ISampleLoop loop: zone.getLoops ())
        {
            loop.setStart (Math.clamp (loop.getStart (), 0, lastIndex));
            loop.setEnd (Math.clamp (loop.getEnd (), loop.getStart (), lastIndex));
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BITE_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16, 24");
        return false;
    }
}
