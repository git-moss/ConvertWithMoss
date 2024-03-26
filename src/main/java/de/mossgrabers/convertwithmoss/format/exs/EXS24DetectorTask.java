// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Logic EXS24 files in folders. Files must end with <i>.exs</i>.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24DetectorTask extends AbstractDetectorTask
{
    private static final String      ENDING_EXS            = ".exs";

    private static final int         CHUNK_TYPE_INSTRUMENT = 0x00;
    private static final int         CHUNK_TYPE_ZONE       = 0x01;
    private static final int         CHUNK_TYPE_GROUP      = 0x02;
    private static final int         CHUNK_TYPE_SAMPLE     = 0x03;
    private static final int         CHUNK_TYPE_PARAMS     = 0x04;

    private static final Set<String> BIG_ENDIAN_MAGIC      = new HashSet<> (2);
    private static final Set<String> LITTLE_ENDIAN_MAGIC   = new HashSet<> (2);
    static
    {
        Collections.addAll (BIG_ENDIAN_MAGIC, "SOBT", "SOBJ");
        Collections.addAll (LITTLE_ENDIAN_MAGIC, "TBOS", "JBOS");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected EXS24DetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDING_EXS);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.readEXSFile (file, in);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Load the EXS file.
     *
     * @param file
     *
     * @param in The input stream to read from
     * @return The parsed multi-sample source
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> readEXSFile (final File file, final InputStream in) throws IOException
    {
        final List<EXS24Zone> zones = new ArrayList<> ();
        final List<EXS24Sample> samples = new ArrayList<> ();
        final Map<Integer, EXS24Group> groups = new TreeMap<> ();

        while (in.available () > 0)
        {
            final boolean isBigEndian = in.read () == 0;

            final int version1 = in.read ();
            final int version2 = in.read ();
            if (version1 != 1 && version2 != 0)
                throw new IOException (Functions.getMessage ("IDS_EXS_UNKNOWN_VERSION", Integer.toString (version1), Integer.toString (version2)));

            final int blockType = in.read ();
            final int blockSize = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

            final int index = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

            // Flags -> Found: 03 (on a group), 64 (instrument), 2, 3, 2147483650 (zone), 2 (sample)
            StreamUtils.readUnsigned32 (in, isBigEndian);

            final String magic = StreamUtils.readASCII (in, 4);
            if (!(isBigEndian ? BIG_ENDIAN_MAGIC : LITTLE_ENDIAN_MAGIC).contains (magic))
                throw new IOException (Functions.getMessage ("IDS_EXS_UNKNOWN_MAGIC", magic));

            final String blockName = cleanName (StreamUtils.readASCII (in, 64));
            final byte [] content = in.readNBytes (blockSize);

            switch (blockType)
            {
                case CHUNK_TYPE_INSTRUMENT:
                    // No idea about the values in the instrument content yet
                    break;

                case CHUNK_TYPE_ZONE:
                    zones.add (readZone (content, isBigEndian));
                    break;

                case CHUNK_TYPE_GROUP:
                    groups.put (Integer.valueOf (index), readGroup (blockName, content, isBigEndian));
                    break;

                case CHUNK_TYPE_SAMPLE:
                    samples.add (readSample (index, blockName, content, isBigEndian));
                    break;

                case CHUNK_TYPE_PARAMS:
                    // Currently not used
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_EXS_UNKNOWN_EXS_BLOCK_TYPE", Integer.toString (blockType)));
            }
        }

        final Optional<IMultisampleSource> multisample = this.createMultisample (file, groups, zones, samples);
        if (multisample.isEmpty ())
            return Collections.emptyList ();
        return Collections.singletonList (multisample.get ());
    }


    private static String cleanName (final String ascii)
    {
        return ascii.split ("\0")[0];
    }


    /**
     * Create a multi-sample from the read EXS information.
     *
     * @param file The source file
     * @param exs24Groups All read EXS groups
     * @param exs24Zones All read EXS zones
     * @param exs24Samples All read EXS samples
     * @return The multi-sample source
     * @throws IOException Could not create a multi-sample
     */
    private Optional<IMultisampleSource> createMultisample (final File file, final Map<Integer, EXS24Group> exs24Groups, final List<EXS24Zone> exs24Zones, final List<EXS24Sample> exs24Samples) throws IOException
    {
        final String name = file.getName ();
        final File parentFile = file.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();
        final Map<IGroup, EXS24Group> groupMapping = new HashMap<> ();

        for (final EXS24Zone exs24Zone: exs24Zones)
        {
            if (this.waitForDelivery ())
                return Optional.empty ();

            final ISampleZone zone = createAndCheckSampleZone (parentFile, exs24Zone, exs24Samples);
            if (zone == null)
                continue;

            zone.setKeyRoot (exs24Zone.key);
            zone.setKeyLow (exs24Zone.keyLow);
            zone.setKeyHigh (exs24Zone.keyHigh);
            zone.setVelocityLow (exs24Zone.velocityRangeOn ? exs24Zone.velocityLow : 0);
            zone.setVelocityHigh (exs24Zone.velocityRangeOn ? exs24Zone.velocityHigh : 127);
            zone.setStart (exs24Zone.sampleStart);
            zone.setStop (exs24Zone.sampleEnd);
            zone.setReversed (exs24Zone.reverse);
            zone.setGain (exs24Zone.volumeAdjust);

            if (exs24Zone.pitch && (exs24Zone.coarseTuning != 0 || exs24Zone.fineTuning != 0))
                zone.setTune (exs24Zone.coarseTuning + exs24Zone.fineTuning / 100.0);

            zone.setPanorama (MathUtils.clamp (exs24Zone.pan, -50, 50) / 50.0);

            if (exs24Zone.loopOn)
            {
                final DefaultSampleLoop loop = new DefaultSampleLoop ();
                loop.setStart (exs24Zone.loopStart);
                loop.setEnd (exs24Zone.loopEnd);
                if (exs24Zone.loopCrossfade != 0)
                {
                    // Existence has been checked above...
                    final EXS24Sample exs24Sample = exs24Samples.get (exs24Zone.sampleIndex);
                    loop.setCrossfadeInSeconds (exs24Zone.loopCrossfade / 1000.0, exs24Sample.sampleRate);
                }
                zone.getLoops ().add (loop);
            }
            // Add group data from exs24Groups
            final IGroup group = groupsMap.computeIfAbsent (Integer.valueOf (exs24Zone.groupIndex), key -> {

                final IGroup newGroup = new DefaultGroup ();
                final EXS24Group exs24Group = exs24Groups.get (key);
                if (exs24Group != null)
                {
                    groupMapping.put (newGroup, exs24Group);
                    newGroup.setName (exs24Group.name.replace ((char) 0, ' '));
                    if (exs24Group.releaseTrigger)
                        newGroup.setTrigger (TriggerType.RELEASE);
                }
                return newGroup;

            });

            final EXS24Group exs24Group = groupMapping.get (group);
            if (exs24Group != null)
            {
                if (exs24Group.volume != 0)
                    zone.setGain (zone.getGain () + exs24Group.volume);
                if (exs24Group.pan != 0)
                    zone.setPanorama (zone.getPanorama () + exs24Group.pan);

                // Zone is completely outside of the groups' velocity range
                if (zone.getVelocityHigh () < exs24Group.minVelocity)
                    continue;
                if (exs24Group.minVelocity != 0 && zone.getVelocityLow () < exs24Group.minVelocity)
                    zone.setVelocityLow (exs24Group.minVelocity);
                // Zone is completely outside of the groups' velocity range
                if (zone.getVelocityLow () > exs24Group.maxVelocity)
                    continue;
                if (exs24Group.maxVelocity != 0 && zone.getVelocityHigh () > exs24Group.maxVelocity)
                    zone.setVelocityHigh (exs24Group.maxVelocity);

                // Zone is completely outside of the groups' note range
                if (zone.getKeyHigh () < exs24Group.startNote)
                    continue;
                if (exs24Group.startNote != 0 && zone.getKeyLow () < exs24Group.startNote)
                    zone.setKeyLow (exs24Group.startNote);
                // Zone is completely outside of the groups' note range
                if (zone.getKeyLow () > exs24Group.endNote)
                    continue;
                if (exs24Group.endNote != 0 && zone.getKeyHigh () > exs24Group.endNote)
                    zone.setKeyHigh (exs24Group.endNote);
            }

            group.addSampleZone (zone);
        }

        multisampleSource.setGroups (new ArrayList<> (groupsMap.values ()));
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Optional.of (multisampleSource);
    }


    private ISampleZone createAndCheckSampleZone (final File parentFile, final EXS24Zone exs24Zone, final List<EXS24Sample> exs24Samples) throws IOException
    {
        if (exs24Zone.sampleIndex >= exs24Samples.size ())
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Integer.toString (exs24Zone.sampleIndex));
            return null;
        }

        final EXS24Sample exs24Sample = exs24Samples.get (exs24Zone.sampleIndex);
        if (exs24Sample == null)
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Integer.toString (exs24Zone.sampleIndex));
            return null;
        }

        final File sampleFile = findSampleFile (parentFile, exs24Sample.fileName);
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return null;
        }
        return createSampleZone (sampleFile);
    }


    /**
     * If the sample is not found in the given folder, a search is started from one folder up and
     * search recursively for the wave file.
     * 
     * @param folder The folder where the sample is expected
     * @param fileName The name of the sample file
     * @return The sample file
     */
    private File findSampleFile (final File folder, final String fileName)
    {
        final File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
            return sampleFile;

        // Go one folder up and search recursively...
        final File found = findSampleFileRecursively (folder.getParentFile (), fileName);
        // Returning the original file triggers the expected error...
        return found == null ? sampleFile : found;
    }


    private File findSampleFileRecursively (final File folder, final String fileName)
    {
        File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
        {
            this.notifier.log ("IDS_EXS_FOUND_FILE", sampleFile.getAbsolutePath ());
            return sampleFile;
        }

        for (final File subFolder: folder.listFiles (File::isDirectory))
        {
            sampleFile = findSampleFileRecursively (subFolder, fileName);
            if (sampleFile != null)
                return sampleFile;
        }

        return null;
    }


    /**
     * Read a zone block.
     * 
     * @param content The data of the block
     * @param isBigEndian True if big-endian otherwise little-endian
     * @throws IOException Could not read the data
     */
    private static EXS24Zone readZone (final byte [] content, final boolean isBigEndian) throws IOException
    {
        final EXS24Zone zone = new EXS24Zone ();

        final ByteArrayInputStream in = new ByteArrayInputStream (content);

        final int zoneOpts = in.read ();
        zone.pitch = (zoneOpts & 1 << 1) == 0;
        zone.oneshot = (zoneOpts & 1 << 0) != 0;
        zone.reverse = (zoneOpts & 1 << 2) != 0;
        zone.velocityRangeOn = (zoneOpts & 1 << 3) != 0;

        zone.key = in.read ();
        zone.fineTuning = twosComplement (in.read (), 8);
        zone.pan = twosComplement (in.read (), 8);
        zone.volumeAdjust = twosComplement (in.read (), 8);
        zone.volumeScale = in.read ();
        zone.keyLow = in.read ();
        zone.keyHigh = in.read ();
        in.skipNBytes (1);
        zone.velocityLow = in.read ();
        zone.velocityHigh = in.read ();
        in.skipNBytes (1);
        zone.sampleStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.sampleEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.loopStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.loopEnd = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.loopCrossfade = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.loopTune = in.read ();

        final int loopOptions = in.read ();
        zone.loopOn = (loopOptions & 1) != 0;
        zone.loopEqualPower = (loopOptions & 2) != 0;
        zone.loopPlayToEndOnRelease = (loopOptions & 4) != 0;

        zone.loopDirection = in.read ();
        in.skipNBytes (42);
        zone.flexOptions = in.read ();
        zone.flexSpeed = in.read ();
        zone.tailTune = in.read ();
        zone.coarseTuning = twosComplement (in.read (), 8);

        in.skipNBytes (1);

        zone.output = in.read ();
        if ((zoneOpts & 1 << 6) == 0)
            zone.output = -1;

        in.skipNBytes (5);

        zone.groupIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        zone.sampleIndex = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        // There are files with more data (mostly about a tail part) which is currently not used

        return zone;
    }


    /**
     * Read a group block.
     * 
     * @param content The data of the block
     * @param isBigEndian True if big-endian otherwise little-endian
     * @throws IOException Could not read the data
     */
    private static EXS24Group readGroup (final String name, final byte [] content, final boolean isBigEndian) throws IOException
    {
        final EXS24Group group = new EXS24Group ();

        group.name = name;

        final ByteArrayInputStream in = new ByteArrayInputStream (content);

        group.volume = in.read ();
        group.pan = in.read ();
        group.polyphony = in.read ();
        group.options = in.read ();
        group.mute = (group.options & 16) > 0; // 0 = OFF, 1 = ON
        group.releaseTriggerDecay = (group.options & 64) > 0; // 0 = OFF, 1 = ON
        group.fixedSampleSelect = (group.options & 128) > 0; // 0 = OFF, 1 = ON

        group.exclusive = in.read ();
        group.minVelocity = in.read ();
        group.maxVelocity = in.read ();
        group.sampleSelectRandomOffset = in.read ();

        in.skipNBytes (8);

        group.releaseTriggerTime = StreamUtils.readUnsigned16 (in, isBigEndian);

        in.skipNBytes (14);

        group.velocityRangExFade = in.read () - 128;
        group.velocityRangExFadeType = in.read ();
        group.keyrangExFadeType = in.read ();
        group.keyrangExFade = in.read () - 128;

        in.skipNBytes (2);

        group.enableByTempoLow = in.read ();
        group.enableByTempoHigh = in.read ();

        in.skipNBytes (1);

        group.cutoffOffset = in.read ();
        in.skipNBytes (1);

        group.resoOffset = in.read ();
        in.skipNBytes (12);

        group.env1AttackOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        group.env1DecayOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        group.env1SustainOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        group.env1ReleaseOffset = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        in.skipNBytes (1);

        group.releaseTrigger = in.read () > 0;
        group.output = in.read ();
        group.enableByNoteValue = in.read ();

        if (in.available () > 0)
        {
            in.skipNBytes (4);

            group.roundRobinGroupPos = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
            group.enableByType = in.read ();
            group.enableByNote = group.enableByType == 1;
            group.enableByRoundRobin = group.enableByType == 2;
            group.enableByControl = group.enableByType == 3;
            group.enableByBend = group.enableByType == 4;
            group.enableByChannel = group.enableByType == 5;
            group.enableByArticulation = group.enableByType == 6;
            group.enablebyTempo = group.enableByType == 7;

            group.enableByControlValue = in.read ();
            group.enableByControlLow = in.read ();
            group.enableByControlHigh = in.read ();
            group.startNote = in.read ();
            group.endNote = in.read ();
            group.enableByMidiChannel = in.read ();
            group.enableByArticulationValue = in.read ();
        }

        // There are files with more data which is currently not used

        return group;
    }


    /**
     * Read a sample block.
     * 
     * @param content The data of the block
     * @param isBigEndian True if big-endian otherwise little-endian
     * @throws IOException Could not read the data
     */
    private static EXS24Sample readSample (final int sampleIndex, final String name, final byte [] content, final boolean isBigEndian) throws IOException
    {
        final EXS24Sample sample = new EXS24Sample ();
        sample.id = sampleIndex;

        final ByteArrayInputStream in = new ByteArrayInputStream (content);

        sample.waveDataStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        sample.length = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        sample.sampleRate = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        sample.bitDepth = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        sample.channels = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        sample.channels2 = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        in.skipNBytes (4);

        sample.type = StreamUtils.readASCII (in, 4);
        sample.size = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        sample.isCompressed = StreamUtils.readUnsigned32 (in, isBigEndian) > 0;

        in.skipNBytes (40);

        sample.filePath = cleanName (StreamUtils.readASCII (in, 256));

        // If not present the name from the header is used!
        sample.fileName = in.available () > 0 ? cleanName (StreamUtils.readASCII (in, 256)) : name;

        return sample;
    }


    private static int twosComplement (int value, final int bits)
    {
        if ((value & 1 << bits - 1) != 0)
            value -= 1 << bits;
        return value;
    }
}
