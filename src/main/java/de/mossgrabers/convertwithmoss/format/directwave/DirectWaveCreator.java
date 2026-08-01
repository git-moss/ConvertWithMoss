// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;


/**
 * Creator for DirectWave programs. A program is written as a folder which contains the DWP file
 * and all samples as 16-bit WAV files, the layout which FL Studio Mobile imports (the folder can
 * also be packed into a ZIP file for the transfer). FL Studio Desktop loads the DWP file directly.
 * The DWP file is created from the block structure of a real DirectWave export, see
 * DIRECTWAVE_DWP_FORMAT.md in the design documentation.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DirectWaveCreator (final INotifier notifier)
    {
        super ("DirectWave", "DirectWave", notifier, new WavChunkSettingsUI ("DirectWave"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String safeName = createSafeFilename (multisampleSource.getName ());

        // The folder, the DWP file and the sample names must all carry the same name, therefore
        // make the folder unique instead of only the DWP file
        String name = safeName;
        File folder = new File (destinationFolder, name);
        int counter = 2;
        while (folder.exists ())
        {
            name = safeName + " (" + counter + ")";
            folder = new File (destinationFolder, name);
            counter++;
        }
        if (!folder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", folder.getAbsolutePath ());
            return;
        }

        final File multiFile = new File (folder, name + ".dwp");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.filterRoundRobinZones (multisampleSource);
        renameZonesToConvention (name, multisampleSource);

        this.writeSamples (folder, multisampleSource, DESTINATION_FORMAT);
        Files.write (multiFile.toPath (), createDwpContent (name, multisampleSource));

        this.progress.notifyDone ();
    }


    /**
     * Create the content of the DWP file.
     *
     * @param name The name of the instrument
     * @param multisampleSource The multi-sample source
     * @return The bytes of the DWP file
     * @throws IOException Could not create the content
     */
    private static byte [] createDwpContent (final String name, final IMultisampleSource multisampleSource) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_INSTRUMENT_NAME, name);
        DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_INSTRUMENT_PATH, "D:\\\\" + name + ".dwp");
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_SHADOW_NAME, DirectWaveTag.SHADOW_NAME_LENGTH);
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_SHADOW_PATH, DirectWaveTag.SHADOW_PATH_LENGTH);
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_1, 17);
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_2, 17);
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_3, 20);
        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_3, 20);
        for (int i = 0; i < 4; i++)
            DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_METADATA_4, 4);

        for (int i = 0; i < DirectWaveTag.NUM_PARAMETER_SLOTS; i++)
        {
            final byte [] slot = new byte [13];
            DirectWaveChunk.writeIntLE (slot, 0, i);
            DirectWaveChunk.writeFloatLE (slot, 5, 1.0f);
            DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_PARAMETER_SLOT, slot);
        }

        int sampleCount = 0;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_CONTAINER, createSampleContainer (name, zone));
                sampleCount++;
            }

        DirectWaveChunk.writeZeroedChunk (out, DirectWaveTag.TAG_TERMINATOR, 0);

        // Assemble the file and patch the size and sample count fields in the preamble
        final byte [] blockStream = out.toByteArray ();
        final byte [] content = new byte [DirectWaveTag.TEMPLATE_PREAMBLE.length + blockStream.length];
        System.arraycopy (DirectWaveTag.TEMPLATE_PREAMBLE, 0, content, 0, DirectWaveTag.TEMPLATE_PREAMBLE.length);
        System.arraycopy (blockStream, 0, content, DirectWaveTag.TEMPLATE_PREAMBLE.length, blockStream.length);
        DirectWaveChunk.writeIntLE (content, DirectWaveTag.PREAMBLE_SIZE_OFFSET, content.length - DirectWaveTag.PREAMBLE_SIZE_DELTA);
        DirectWaveChunk.writeIntLE (content, DirectWaveTag.PREAMBLE_COUNT_OFFSET, sampleCount);
        return content;
    }


    /**
     * Create the content of one sample container block. All blocks are copied from the container
     * template of a factory program; only the zone mapping, the name, the path and the audio
     * format (frame count, channels, sample rate and loop) are filled from the zone.
     *
     * @param instrumentName The name of the instrument
     * @param zone The sample zone
     * @return The bytes of the container
     * @throws IOException Could not create the content
     */
    private static byte [] createSampleContainer (final String instrumentName, final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final String sampleName = createSafeFilename (zone.getName ());

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        for (final DirectWaveChunk chunk: DirectWaveChunk.parseAll (DirectWaveTag.TEMPLATE_CONTAINER, 0))
            switch (chunk.getTag ())
            {
                case DirectWaveTag.TAG_ZONE_MAPPING:
                    final byte [] mapping = chunk.getPayload ().clone ();
                    mapping[DirectWaveTag.MAPPING_ROOT_KEY] = (byte) getAdjustedRootKey (zone);
                    mapping[DirectWaveTag.MAPPING_LOW_KEY] = (byte) Math.clamp (limitToDefault (zone.getKeyLow (), 0), 0, 127);
                    mapping[DirectWaveTag.MAPPING_HIGH_KEY] = (byte) Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127);
                    final int velocityLow = Math.clamp (limitToDefault (zone.getVelocityLow (), 1), 1, 127);
                    mapping[DirectWaveTag.MAPPING_LOW_VELOCITY] = (byte) (velocityLow <= 1 ? 0 : velocityLow);
                    mapping[DirectWaveTag.MAPPING_HIGH_VELOCITY] = (byte) Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 1, 127);
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_ZONE_MAPPING, mapping);
                    break;

                case DirectWaveTag.TAG_SAMPLE_NAME:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_NAME, sampleName);
                    break;

                case DirectWaveTag.TAG_SAMPLE_PATH:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_SAMPLE_PATH, "D:\\" + instrumentName + "\\" + sampleName + ".wav");
                    break;

                case DirectWaveTag.TAG_AUDIO_FORMAT:
                    DirectWaveChunk.writeChunk (out, DirectWaveTag.TAG_AUDIO_FORMAT, createAudioFormat (chunk.getPayload (), zone, audioMetadata));
                    break;

                default:
                    DirectWaveChunk.writeChunk (out, chunk.getTag (), chunk.getPayload ());
                    break;
            }
        return out.toByteArray ();
    }


    /**
     * Fill the audio format block with the frame count, channels, sample rate and the loop of the
     * zone.
     *
     * @param template The template payload of the audio format block
     * @param zone The sample zone
     * @param audioMetadata The metadata of the audio of the zone
     * @return The filled block payload
     */
    private static byte [] createAudioFormat (final byte [] template, final ISampleZone zone, final IAudioMetadata audioMetadata)
    {
        final byte [] audioFormat = template.clone ();
        final int frames = audioMetadata.getNumberOfSamples ();
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_FRAME_COUNT, frames);
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_CHANNELS, audioMetadata.getChannels ());
        // The samples are always written as 16-bit
        DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_BYTES_PER_FRAME, audioMetadata.getChannels () * 2);
        DirectWaveChunk.writeFloatLE (audioFormat, DirectWaveTag.FORMAT_SAMPLE_RATE, audioMetadata.getSampleRate ());

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_MODE, 0);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_START, 0);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_END, 0);
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_MODE, DirectWaveTag.LOOP_MODE_ON);
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_START, Math.clamp (loop.getStart (), 0, frames));
            DirectWaveChunk.writeIntLE (audioFormat, DirectWaveTag.FORMAT_LOOP_END, Math.clamp (loop.getEnd (), 0, frames));
        }
        return audioFormat;
    }


    /**
     * Rename all zones to the naming convention which DirectWave uses itself when saving a
     * program: <i>InstrumentName_Note_Velocity</i>. This makes sure that the file names, the names
     * in the DWP file and the names which DirectWave expects are identical.
     *
     * @param name The name of the instrument
     * @param multisampleSource The multi-sample source
     */
    private static void renameZonesToConvention (final String name, final IMultisampleSource multisampleSource)
    {
        final Set<String> usedNames = new HashSet<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final String baseName = name + "_" + DirectWaveFileNameParser.formatNote (getAdjustedRootKey (zone)) + "_" + Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 1, 127);
                String zoneName = baseName;
                int counter = 2;
                while (!usedNames.add (zoneName))
                {
                    zoneName = baseName + "_" + counter;
                    counter++;
                }
                zone.setName (zoneName);
            }
    }


    /**
     * Get the root key of the zone with the semitones of the zone tuning applied. DirectWave does
     * not have a documented tuning field, therefore the tuning is baked into the root key as good
     * as possible (the cents rest is lost).
     *
     * @param zone The zone
     * @return The adjusted root key
     */
    private static int getAdjustedRootKey (final ISampleZone zone)
    {
        final int rootKey = limitToDefault (zone.getKeyRoot (), Math.clamp (limitToDefault (zone.getKeyLow (), 60), 0, 127));
        return Math.clamp (rootKey - Math.round (zone.getTuning ()), 0, 127);
    }


    /**
     * Remove all zones which belong to alternating play-back (round-robin or random) except those
     * of the first cycle. DirectWave supports trigger groups but their location in the DWP
     * structure is not known; keeping the zones would make them all play at the same time.
     *
     * @param multisampleSource The multi-sample source to filter
     */
    private void filterRoundRobinZones (final IMultisampleSource multisampleSource)
    {
        final List<IGroup> keptGroups = new ArrayList<> ();
        int dropped = 0;
        boolean firstCycleGroupSeen = false;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
        {
            final DefaultGroup keptGroup = new DefaultGroup (group.getName ());
            boolean groupHasUnsequencedZones = false;
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (zone.getPlayLogic () == PlayLogic.ALWAYS)
                {
                    keptGroup.addSampleZone (zone);
                    continue;
                }

                // Alternating zones with a sequence position keep only the first cycle; without
                // one, each group is one cycle and only the first such group is kept
                final int sequencePosition = zone.getSequencePosition ();
                if (sequencePosition == 1 || sequencePosition < 1 && (!firstCycleGroupSeen || groupHasUnsequencedZones))
                {
                    keptGroup.addSampleZone (zone);
                    if (sequencePosition < 1)
                        groupHasUnsequencedZones = true;
                }
                else
                    dropped++;
            }
            if (groupHasUnsequencedZones)
                firstCycleGroupSeen = true;
            if (!keptGroup.getSampleZones ().isEmpty ())
                keptGroups.add (keptGroup);
        }

        if (dropped == 0)
            return;
        multisampleSource.setGroups (keptGroups);
        this.notifier.log ("IDS_DWP_DROPPED_ROUND_ROBIN", Integer.toString (dropped));
    }
}
