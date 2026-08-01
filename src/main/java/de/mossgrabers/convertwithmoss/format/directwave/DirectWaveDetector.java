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
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
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
                    return this.parseChunks (file, chunks);
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
     * @return The multi-sample source
     * @throws IOException Could not read the sample files
     */
    private List<IMultisampleSource> parseChunks (final File file, final List<DirectWaveChunk> chunks) throws IOException
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
                    final ISampleZone zone = this.parseSampleContainer (file, chunk);
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
     * @return The zone or null if the sample file could not be found
     * @throws IOException Could not read the sample file
     */
    private ISampleZone parseSampleContainer (final File file, final DirectWaveChunk containerChunk) throws IOException
    {
        final List<DirectWaveChunk> chunks = DirectWaveChunk.parseAll (containerChunk.getPayload (), 0);
        if (chunks == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", file.getAbsolutePath ());
            return null;
        }

        byte [] mapping = null;
        String sampleName = null;
        String samplePath = null;
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
                default:
                    // All other blocks contain no information required for the conversion
                    break;
            }

        final File sampleFile = findSampleFile (file, sampleName, samplePath);
        if (sampleFile == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleName == null ? file.getAbsolutePath () : sampleName);
            return null;
        }

        final ISampleData sampleData = createSampleData (sampleFile, this.notifier);
        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);

        if (mapping != null && mapping.length > DirectWaveTag.MAPPING_HIGH_VELOCITY)
        {
            zone.setKeyRoot (Math.min (127, mapping[DirectWaveTag.MAPPING_ROOT_KEY] & 0xFF));
            zone.setKeyLow (Math.min (127, mapping[DirectWaveTag.MAPPING_LOW_KEY] & 0xFF));
            zone.setKeyHigh (Math.min (127, mapping[DirectWaveTag.MAPPING_HIGH_KEY] & 0xFF));
            zone.setVelocityLow (Math.clamp (mapping[DirectWaveTag.MAPPING_LOW_VELOCITY] & 0xFF, 1, 127));
            zone.setVelocityHigh (Math.clamp (mapping[DirectWaveTag.MAPPING_HIGH_VELOCITY] & 0xFF, 1, 127));
        }

        // Read the loops from the sample chunks; the root key is taken from the mapping above
        sampleData.addZoneData (zone, false, true);
        return zone;
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
