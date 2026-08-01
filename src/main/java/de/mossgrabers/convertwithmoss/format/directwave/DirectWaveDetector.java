// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.directwave.DirectWaveFileNameParser.ParsedZone;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively DirectWave program files in folders. Files must end with <i>.dwp</i> or
 * <i>.dwb</i>. Programs saved with the 'Monolithic file' option disabled are read from the binary
 * block structure; for files which do not follow that structure (e.g. banks) the mapping is
 * reconstructed from the names of the WAV files which DirectWave writes when sampling a plugin
 * (see DIRECTWAVE_DWP_FORMAT.md in the design documentation).
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DirectWaveDetector (final INotifier notifier)
    {
        super ("DirectWave", "DirectWave", notifier, new MetadataSettingsUI ("DirectWave"), ".dwp", ".dwb");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final byte [] content = Files.readAllBytes (file.toPath ());
            if (content.length >= DirectWaveTag.PREAMBLE_SIZE + 12 && Arrays.equals (content, 0, DirectWaveTag.MAGIC.length, DirectWaveTag.MAGIC, 0, DirectWaveTag.MAGIC.length))
            {
                final List<DirectWaveChunk> chunks = DirectWaveChunk.parseAll (content, DirectWaveTag.PREAMBLE_SIZE);
                if (chunks != null)
                    return this.parseChunks (file, chunks, content[DirectWaveTag.PREAMBLE_VERSION_OFFSET] & 0xFF);
            }

            // No parseable DWP structure (e.g. a bank): reconstruct the mapping from the names
            // of the sample files
            return this.parseFromFileNames (file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Create the multi-sample from the parsed block structure of a DWP file.
     *
     * @param file The DWP file
     * @param chunks The parsed top level chunks
     * @param version The format version (the byte at offset 4)
     * @return The multi-sample source
     * @throws IOException Could not read the sample files
     */
    private List<IMultisampleSource> parseChunks (final File file, final List<DirectWaveChunk> chunks, final int version) throws IOException
    {
        String name = FileUtils.getNameWithoutType (file);
        final IGroup group = new DefaultGroup ("Group #1");
        int containerCount = 0;

        for (final DirectWaveChunk chunk: chunks)
            switch (chunk.getTag ())
            {
                case DirectWaveTag.TAG_INSTRUMENT_NAME:
                    final String instrumentName = chunk.getPayloadAsText ().trim ();
                    if (!instrumentName.isEmpty ())
                        name = instrumentName;
                    break;

                case DirectWaveTag.TAG_SAMPLE_CONTAINER:
                    containerCount++;
                    final ISampleZone zone = this.parseSampleContainer (file, chunk, version);
                    if (zone != null)
                        group.addSampleZone (zone);
                    break;

                default:
                    // Not used
                    break;
            }

        if (group.getSampleZones ().isEmpty ())
        {
            this.notifier.logError ("IDS_DWP_NO_SAMPLES_FOUND", Integer.toString (containerCount));
            return Collections.emptyList ();
        }

        final IMultisampleSource multisampleSource = this.createMultisampleSource (file, name);
        multisampleSource.setGroups (Collections.singletonList (group));
        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse one sample container chunk into a sample zone.
     *
     * @param file The DWP file
     * @param containerChunk The sample container chunk
     * @param version The format version (the byte at offset 4)
     * @return The zone or null if the sample file could not be found
     * @throws IOException Could not read the sample file
     */
    private ISampleZone parseSampleContainer (final File file, final DirectWaveChunk containerChunk, final int version) throws IOException
    {
        final List<DirectWaveChunk> chunks = DirectWaveChunk.parseAll (containerChunk.getPayload (), 0);
        if (chunks == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", file.getAbsolutePath ());
            return null;
        }

        byte [] mapping = null;
        byte [] audioFormat = null;
        byte [] ampEnvelope = null;
        String sampleName = null;
        String samplePath = null;
        final List<byte []> unknownChunks = new ArrayList<> ();
        for (final DirectWaveChunk chunk: chunks)
            switch (chunk.getTag ())
            {
                case DirectWaveTag.TAG_ZONE_MAPPING:
                    mapping = chunk.getPayload ();
                    break;
                case DirectWaveTag.TAG_SAMPLE_NAME:
                    sampleName = chunk.getPayloadAsText ().trim ();
                    break;
                case DirectWaveTag.TAG_SAMPLE_PATH:
                    samplePath = chunk.getPayloadAsText ().trim ();
                    break;
                case DirectWaveTag.TAG_AUDIO_FORMAT:
                    audioFormat = chunk.getPayload ();
                    break;
                case DirectWaveTag.TAG_AMP_ENVELOPE:
                    ampEnvelope = chunk.getPayload ();
                    break;
                case DirectWaveTag.TAG_BLOCK_01F8, DirectWaveTag.TAG_BLOCK_01F9, DirectWaveTag.TAG_BLOCK_01FA, DirectWaveTag.TAG_BLOCK_01FB, DirectWaveTag.TAG_BLOCK_01FC, DirectWaveTag.TAG_BLOCK_01FE, DirectWaveTag.TAG_BLOCK_01FF, DirectWaveTag.TAG_BLOCK_0200, DirectWaveTag.TAG_BLOCK_0201, DirectWaveTag.TAG_BLOCK_0202, DirectWaveTag.TAG_BLOCK_0203, DirectWaveTag.TAG_BLOCK_0204, DirectWaveTag.TAG_SAMPLE_TERMINATOR:
                    // Known parameter blocks which contain no information required for the
                    // conversion
                    break;
                default:
                    // In a monolithic file one of the unknown blocks is the embedded audio
                    unknownChunks.add (chunk.getPayload ());
                    break;
            }

        final File sampleFile = findSampleFile (file, sampleName, samplePath);
        final ISampleData sampleData;
        final String zoneName;
        if (sampleFile != null)
        {
            sampleData = createSampleData (sampleFile, this.notifier);
            zoneName = FileUtils.getNameWithoutType (sampleFile);
        }
        else
        {
            // Monolithic file: the audio is embedded in the container
            sampleData = createEmbeddedSampleData (unknownChunks, audioFormat);
            if (sampleData == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleName == null ? file.getAbsolutePath () : sampleName);
                return null;
            }
            zoneName = sampleName == null || sampleName.isEmpty () ? FileUtils.getNameWithoutType (file) : sampleName;
        }

        final ISampleZone zone = new DefaultSampleZone (zoneName, sampleData);

        if (mapping != null && mapping.length > DirectWaveTag.MAPPING_HIGH_VELOCITY)
        {
            zone.setKeyRoot (Math.min (127, mapping[DirectWaveTag.MAPPING_ROOT_KEY] & 0xFF));
            zone.setKeyLow (Math.min (127, mapping[DirectWaveTag.MAPPING_LOW_KEY] & 0xFF));
            zone.setKeyHigh (Math.min (127, mapping[DirectWaveTag.MAPPING_HIGH_KEY] & 0xFF));
            zone.setVelocityLow (Math.clamp (mapping[DirectWaveTag.MAPPING_LOW_VELOCITY] & 0xFF, 1, 127));
            zone.setVelocityHigh (Math.clamp (mapping[DirectWaveTag.MAPPING_HIGH_VELOCITY] & 0xFF, 1, 127));
        }

        // The loop is stored in the audio format block
        if (audioFormat != null && audioFormat.length > DirectWaveTag.FORMAT_LOOP_END + 3 && DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_MODE) != 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_START));
            loop.setEnd (DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_END));
            zone.getLoops ().add (loop);
        }

        applyAmplitudeEnvelope (zone, ampEnvelope, version);

        // The root key is taken from the mapping above; look for loops in the sample chunks only
        // when the program has none
        sampleData.addZoneData (zone, false, zone.getLoops ().isEmpty ());
        return zone;
    }


    /**
     * Apply the amplitude envelope of the zone. The four floats of the envelope block are the
     * attack, decay, sustain and release knob positions. When they are exactly the defaults of
     * the format version nothing is set, so that the default envelope handling of the conversion
     * applies. The time knobs are mapped with the provisional cubic law described in the design
     * document; the sustain is a level and therefore exact.
     *
     * @param zone The zone
     * @param ampEnvelope The payload of the amplitude envelope block
     * @param version The format version (the byte at offset 4)
     */
    private static void applyAmplitudeEnvelope (final ISampleZone zone, final byte [] ampEnvelope, final int version)
    {
        if (ampEnvelope == null || ampEnvelope.length < 16)
            return;

        final float [] positions = new float [4];
        final float [] defaults = version == DirectWaveTag.VERSION_26 ? DirectWaveTag.ENVELOPE_DEFAULTS_V26 : DirectWaveTag.ENVELOPE_DEFAULTS_V25;
        boolean isDefault = true;
        for (int i = 0; i < 4; i++)
        {
            positions[i] = Math.clamp (DirectWaveChunk.readFloatLE (ampEnvelope, i * 4), 0, 1);
            if (Math.abs (positions[i] - defaults[i]) > 0.005)
                isDefault = false;
        }
        if (isDefault)
            return;

        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        envelope.setAttackTime (knobToTime (positions[0]));
        envelope.setDecayTime (knobToTime (positions[1]));
        envelope.setSustainLevel (positions[2]);
        envelope.setReleaseTime (knobToTime (positions[3]));
    }


    private static double knobToTime (final float position)
    {
        return DirectWaveTag.ENVELOPE_MAX_TIME * position * position * position;
    }


    /**
     * Identify the embedded audio of a monolithic file among the unknown blocks of a sample
     * container. The audio format block provides the frame count, the channel count and the
     * sample rate, therefore the audio data is the block whose size is exactly frame count times
     * channel count times 2, 3 or 4 bytes per sample - a check which cannot match by accident.
     * (The bytes-per-frame field of the audio format block is not used since it does not hold
     * bytes-per-frame in all DirectWave versions, see the design document.) 16 and 24 bit integer
     * data is taken over as-is, 4 bytes per sample are the 32-bit float format of DirectWave and
     * are converted to 24 bit.
     *
     * @param unknownChunks The payloads of all unknown blocks of the sample container
     * @param audioFormat The payload of the audio format block
     * @return The sample data or null if no block matches
     */
    private static ISampleData createEmbeddedSampleData (final List<byte []> unknownChunks, final byte [] audioFormat)
    {
        if (audioFormat == null || audioFormat.length < 20)
            return null;

        final int frames = DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_FRAME_COUNT);
        final int channels = DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_CHANNELS);
        final float sampleRate = Float.intBitsToFloat (DirectWaveChunk.readIntLE (audioFormat, DirectWaveTag.FORMAT_SAMPLE_RATE));
        if (frames <= 0 || channels < 1 || channels > 8 || sampleRate <= 0)
            return null;

        for (final byte [] payload: unknownChunks)
            for (int bytesPerSample = 2; bytesPerSample <= 4; bytesPerSample++)
            {
                if (payload.length != (long) frames * channels * bytesPerSample)
                    continue;
                if (bytesPerSample == 4)
                    return new InMemorySampleData (new DefaultAudioMetadata (channels, Math.round (sampleRate), 24, frames), convertFloat32ToInt24 (payload));
                return new InMemorySampleData (new DefaultAudioMetadata (channels, Math.round (sampleRate), bytesPerSample * 8, frames), payload);
            }
        return null;
    }


    /**
     * Convert 32-bit float samples to 24-bit integer samples.
     *
     * @param data The float samples as bytes (little-endian)
     * @return The 24-bit samples as bytes (little-endian)
     */
    private static byte [] convertFloat32ToInt24 (final byte [] data)
    {
        final int numSamples = data.length / 4;
        final byte [] result = new byte [numSamples * 3];
        for (int i = 0; i < numSamples; i++)
        {
            final float value = Float.intBitsToFloat (DirectWaveChunk.readIntLE (data, i * 4));
            final int intValue = Math.clamp (Math.round (value * 8388607.0), -8388608, 8388607);
            result[i * 3] = (byte) (intValue & 0xFF);
            result[i * 3 + 1] = (byte) (intValue >> 8 & 0xFF);
            result[i * 3 + 2] = (byte) (intValue >> 16 & 0xFF);
        }
        return result;
    }


    /**
     * Find the sample file. The absolute path stored in the DWP file belongs to the machine which
     * saved it and is therefore only used for its file and folder name: the file is searched next
     * to the DWP file (the FL Studio Mobile layout), in a sub-folder named like the DWP file (the
     * FL Studio Desktop layout) and finally in a sub-folder named like the folder of the stored
     * path.
     *
     * @param file The DWP file
     * @param sampleName The name of the sample without extension
     * @param samplePath The path stored in the DWP file
     * @return The sample file or null if none of the candidates exists
     */
    private static File findSampleFile (final File file, final String sampleName, final String samplePath)
    {
        final File parent = file.getParentFile ();

        String fileName = null;
        String folderName = null;
        if (samplePath != null && !samplePath.isEmpty ())
        {
            final String [] parts = samplePath.replace ('\\', '/').split ("/");
            fileName = parts[parts.length - 1];
            if (parts.length > 1)
                folderName = parts[parts.length - 2];
        }
        if ((fileName == null || fileName.isEmpty ()) && sampleName != null && !sampleName.isEmpty ())
            fileName = sampleName + ".wav";
        if (fileName == null)
            return null;

        final List<File> candidates = new ArrayList<> ();
        candidates.add (new File (parent, fileName));
        candidates.add (new File (new File (parent, FileUtils.getNameWithoutType (file)), fileName));
        if (folderName != null && !folderName.isEmpty ())
            candidates.add (new File (new File (parent, folderName), fileName));

        for (final File candidate: candidates)
            if (candidate.exists ())
                return candidate;
        return null;
    }


    /**
     * Create the multi-sample by parsing the names of the WAV files which belong to the preset
     * file. The files are looked up in a sub-folder named like the preset file or, if there is no
     * such folder, next to it.
     *
     * @param file The preset file (e.g. a DWB bank)
     * @return The multi-sample sources
     * @throws IOException Could not read the sample files
     */
    private List<IMultisampleSource> parseFromFileNames (final File file) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final File parent = file.getParentFile ();
        File sampleFolder = new File (parent, name);
        if (!sampleFolder.isDirectory ())
            sampleFolder = parent;

        final File [] wavFiles = sampleFolder.listFiles ((_, fileName) -> fileName.toLowerCase (Locale.US).endsWith (".wav"));
        final List<ParsedZone> parsedZones = new ArrayList<> ();
        int skipped = 0;
        if (wavFiles != null)
        {
            Arrays.sort (wavFiles);
            for (final File wavFile: wavFiles)
            {
                final ParsedZone parsedZone = DirectWaveFileNameParser.parseFileName (wavFile);
                if (parsedZone == null)
                    skipped++;
                else
                    parsedZones.add (parsedZone);
            }
        }

        if (parsedZones.isEmpty ())
        {
            this.notifier.logError ("IDS_DWP_NO_ZONES", sampleFolder.getAbsolutePath ());
            return Collections.emptyList ();
        }
        if (skipped > 0)
            this.notifier.log ("IDS_DWP_SKIPPED_FILES", Integer.toString (skipped), sampleFolder.getAbsolutePath ());

        DirectWaveFileNameParser.calculateMissingRanges (parsedZones);

        final Map<Integer, IGroup> groups = new LinkedHashMap<> ();
        for (final ParsedZone parsedZone: parsedZones)
        {
            final ISampleData sampleData = createSampleData (parsedZone.file, this.notifier);
            final ISampleZone zone = new DefaultSampleZone (parsedZone.name, sampleData);
            zone.setKeyRoot (parsedZone.rootKey);
            zone.setKeyLow (parsedZone.keyLow);
            zone.setKeyHigh (parsedZone.keyHigh);
            zone.setVelocityLow (Math.clamp (parsedZone.velocityLow, 1, 127));
            zone.setVelocityHigh (Math.clamp (parsedZone.velocityHigh, 1, 127));

            final int groupIndex;
            if (parsedZone.cycle >= 1)
            {
                groupIndex = parsedZone.cycle;
                zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                zone.setSequencePosition (parsedZone.cycle);
            }
            else if (parsedZone.triggerGroup >= 0)
            {
                groupIndex = 1000 + parsedZone.triggerGroup;
                if (parsedZone.triggerType == 1)
                    zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                else if (parsedZone.triggerType == 2 || parsedZone.triggerType == 3)
                    zone.setPlayLogic (PlayLogic.RANDOM);
            }
            else
                groupIndex = 0;

            sampleData.addZoneData (zone, false, true);
            groups.computeIfAbsent (Integer.valueOf (groupIndex), _ -> new DefaultGroup ("Group #" + (groups.size () + 1))).addSampleZone (zone);
        }

        final IMultisampleSource multisampleSource = this.createMultisampleSource (file, name);
        multisampleSource.setGroups (new ArrayList<> (groups.values ()));
        return Collections.singletonList (multisampleSource);
    }
}
