// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Logic EXS24 files in folders. Files must end with <i>.exs</i>.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24DetectorTask extends AbstractDetectorTask
{
    private static final String ENDING_EXS = ".exs";


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
     * @param file The EXS24 file
     * @param in The input stream to read from
     * @return The parsed multi-sample source
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> readEXSFile (final File file, final InputStream in) throws IOException
    {
        EXS24Instrument instrument = null;
        final List<EXS24Zone> zones = new ArrayList<> ();
        final List<EXS24Sample> samples = new ArrayList<> ();
        final Map<Integer, EXS24Group> groups = new TreeMap<> ();
        final EXS24Parameters parameters = new EXS24Parameters ();

        while (in.available () > 0)
        {
            final EXS24Block block = new EXS24Block (in);
            switch (block.type)
            {
                case EXS24Block.TYPE_INSTRUMENT:
                    instrument = new EXS24Instrument (block);
                    break;

                case EXS24Block.TYPE_ZONE:
                    zones.add (new EXS24Zone (block));
                    break;

                case EXS24Block.TYPE_GROUP:
                    final EXS24Group group = new EXS24Group (block);
                    // Workaround for some files which have not a proper index set!
                    final int idx = block.index == 0 && groups.containsKey (Integer.valueOf (block.index)) ? groups.size () : block.index;
                    groups.put (Integer.valueOf (idx), group);
                    break;

                case EXS24Block.TYPE_SAMPLE:
                    samples.add (new EXS24Sample (block));
                    break;

                case EXS24Block.TYPE_PARAMS:
                    parameters.read (block);
                    break;

                case EXS24Block.TYPE_UNKNOWN:
                    // No idea what that is but it is 4 bytes long...
                    break;

                default:
                    this.notifier.logError ("IDS_EXS_UNKNOWN_EXS_BLOCK_TYPE", Integer.toString (block.type));
                    break;
            }
        }

        if (instrument == null)
            return Collections.emptyList ();

        final Optional<IMultisampleSource> multisample = this.createMultisample (file, groups, zones, samples, parameters);
        if (multisample.isEmpty ())
            return Collections.emptyList ();
        return Collections.singletonList (multisample.get ());
    }


    /**
     * Create a multi-sample from the read EXS information.
     *
     * @param file The source file
     * @param exs24Groups All .doubleValue () / 127.0 * 10.0 * @param exs24Zones All read EXS zones
     * @param exs24Samples All read EXS samples
     * @param parameters The global parameters
     * @return The multi-sample source
     * @throws IOException Could not create a multi-sample
     */
    private Optional<IMultisampleSource> createMultisample (final File file, final Map<Integer, EXS24Group> exs24Groups, final List<EXS24Zone> exs24Zones, final List<EXS24Sample> exs24Samples, final EXS24Parameters parameters) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final File parentFile = file.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();
        final Map<IGroup, EXS24Group> groupMapping = new HashMap<> ();

        for (final EXS24Zone exs24Zone: exs24Zones)
        {
            if (this.waitForDelivery ())
                return Optional.empty ();

            final ISampleZone zone = this.createAndCheckSampleZone (parentFile, exs24Zone, exs24Samples);
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
            final IGroup group = getOrCreateGroup (exs24Groups, groupsMap, groupMapping, exs24Zone);

            final EXS24Group exs24Group = groupMapping.get (group);
            if (exs24Group == null || limitByGroupAttributes (exs24Group, zone))
                group.addSampleZone (zone);
        }

        multisampleSource.setGroups (new ArrayList<> (groupsMap.values ()));
        applyGlobalParameters (multisampleSource, parameters);
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Optional.of (multisampleSource);
    }


    private static IGroup getOrCreateGroup (final Map<Integer, EXS24Group> exs24Groups, final Map<Integer, IGroup> groupsMap, final Map<IGroup, EXS24Group> groupMapping, final EXS24Zone exs24Zone)
    {
        return groupsMap.computeIfAbsent (Integer.valueOf (exs24Zone.groupIndex), key -> {

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

        final File sampleFile = this.findSampleFile (parentFile, exs24Sample.fileName);
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return null;
        }
        return this.createSampleZone (sampleFile);
    }


    private static void applyGlobalParameters (final IMultisampleSource multisampleSource, final EXS24Parameters parameters)
    {
        applyFilterParameters (multisampleSource, parameters);

        // Pitch bend up/down
        final Integer pitchBendUp = parameters.get (EXS24Parameters.PITCH_BEND_UP);
        final Integer pitchBendDown = parameters.get (EXS24Parameters.PITCH_BEND_DOWN);
        final int bendUp = pitchBendUp != null ? pitchBendUp.intValue () * 100 : 200;
        int bendDown = -200;
        if (pitchBendDown != null)
            bendDown = pitchBendDown.intValue () == -1 ? -bendUp : pitchBendDown.intValue () * -100;

        final Integer globalCoarseTune = parameters.get (EXS24Parameters.COARSE_TUNE);
        final int coarseTune = globalCoarseTune == null ? 0 : globalCoarseTune.intValue ();
        final Integer globalFineTune = parameters.get (EXS24Parameters.FINE_TUNE);
        final int fineTune = globalFineTune == null ? 0 : globalFineTune.intValue ();
        final double tuneOffset = coarseTune + fineTune / 100.0;

        final IEnvelope globalAmplitudeEnvelope = createEnvelope (parameters, 1);

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (bendUp);
                zone.setBendDown (bendDown);
                zone.setTune (zone.getTune () + tuneOffset);

                final IModulator amplitudeModulator = zone.getAmplitudeModulator ();
                amplitudeModulator.setDepth (1.0);
                amplitudeModulator.setSource (globalAmplitudeEnvelope);
            }
    }


    private static IEnvelope createEnvelope (final EXS24Parameters parameters, final int envelopeIndex)
    {
        final Integer delay = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_DELAY_START : EXS24Parameters.ENV2_DELAY_START);
        final Integer attack = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_HI_VEL : EXS24Parameters.ENV2_ATK_HI_VEL);
        final Integer hold = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_HOLD : EXS24Parameters.ENV2_HOLD);
        final Integer decay = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_DECAY : EXS24Parameters.ENV2_DECAY);
        final Integer sustain = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_SUSTAIN : EXS24Parameters.ENV2_SUSTAIN);
        final Integer release = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_RELEASE : EXS24Parameters.ENV2_RELEASE);

        final IEnvelope envelope = new DefaultEnvelope ();
        // Maximum time for each step are 10 seconds
        envelope.setDelay (delay == null ? 0 : delay.doubleValue () / 127.0 * 10.0);
        envelope.setAttack (attack == null ? 0 : attack.intValue ());
        envelope.setHold (hold == null ? 0 : hold.doubleValue () / 127.0 * 10.0);
        envelope.setDecay (decay == null ? 0 : decay.doubleValue () / 127.0 * 10.0);
        envelope.setSustain (sustain == null ? 1.0 : sustain.doubleValue () / 127.0);
        envelope.setRelease (release == null ? 0 : release.doubleValue () / 127.0 * 10.0);

        return envelope;
    }


    private static void applyFilterParameters (final IMultisampleSource multisampleSource, final EXS24Parameters parameters)
    {
        final Integer isFilterEnabled = parameters.get (EXS24Parameters.FILTER1_TOGGLE);
        if (isFilterEnabled == null || isFilterEnabled.intValue () <= 0)
            return;

        final Integer filterTypeIndex = parameters.get (EXS24Parameters.FILTER1_TYPE);
        if (filterTypeIndex == null)
            return;

        final IEnvelope globalFilterEnvelope = createEnvelope (parameters, 2);

        final FilterType filterType;
        final int poles;
        switch (filterTypeIndex.intValue ())
        {
            default:
            case 0:
                filterType = FilterType.LOW_PASS;
                poles = 4;
                break;
            case 1:
                filterType = FilterType.LOW_PASS;
                poles = 3;
                break;
            case 2:
                filterType = FilterType.LOW_PASS;
                poles = 2;
                break;
            case 3:
                filterType = FilterType.LOW_PASS;
                poles = 1;
                break;
            case 4:
                filterType = FilterType.HIGH_PASS;
                poles = 2;
                break;
            case 5:
                filterType = FilterType.BAND_PASS;
                poles = 2;
                break;
        }

        final Integer filterFrequency = parameters.get (EXS24Parameters.FILTER1_CUTOFF);
        final Integer filterResonance = parameters.get (EXS24Parameters.FILTER1_RESO);

        final int frequency = filterFrequency == null ? 1000 : filterFrequency.intValue ();
        final int resonance = filterResonance == null ? 0 : filterResonance.intValue ();

        final double cutoff = MathUtils.denormalize (frequency / 1000.0, 0, IFilter.MAX_FREQUENCY);
        final double denormalize = MathUtils.denormalize (resonance / 1000.0, 0, 40.0);

        final IFilter filter = new DefaultFilter (filterType, poles, cutoff, denormalize);
        filter.getCutoffModulator ().setSource (globalFilterEnvelope);
        multisampleSource.setGlobalFilter (filter);
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
        final File found = this.findSampleFileRecursively (folder.getParentFile (), fileName);
        // Returning the original file triggers the expected error...
        return found == null ? sampleFile : found;
    }


    private static boolean limitByGroupAttributes (final EXS24Group exs24Group, final ISampleZone zone)
    {
        if (exs24Group.volume != 0)
            zone.setGain (zone.getGain () + exs24Group.volume);
        if (exs24Group.pan != 0)
            zone.setPanorama (zone.getPanorama () + exs24Group.pan);

        // Zone is completely outside of the groups' velocity range
        if (zone.getVelocityHigh () < exs24Group.minVelocity)
            return false;
        if (exs24Group.minVelocity != 0 && zone.getVelocityLow () < exs24Group.minVelocity)
            zone.setVelocityLow (exs24Group.minVelocity);
        // Zone is completely outside of the groups' velocity range
        if (zone.getVelocityLow () > exs24Group.maxVelocity)
            return false;
        if (exs24Group.maxVelocity != 0 && zone.getVelocityHigh () > exs24Group.maxVelocity)
            zone.setVelocityHigh (exs24Group.maxVelocity);

        // Zone is completely outside of the groups' note range
        if (zone.getKeyHigh () < exs24Group.startNote)
            return false;
        if (exs24Group.startNote != 0 && zone.getKeyLow () < exs24Group.startNote)
            zone.setKeyLow (exs24Group.startNote);
        // Zone is completely outside of the groups' note range
        if (zone.getKeyLow () > exs24Group.endNote)
            return false;
        if (exs24Group.endNote != 0 && zone.getKeyHigh () > exs24Group.endNote)
            zone.setKeyHigh (exs24Group.endNote);

        return true;
    }


    private File findSampleFileRecursively (final File folder, final String fileName)
    {
        File sampleFile = new File (folder, fileName);
        if (sampleFile.exists ())
            return sampleFile;

        for (final File subFolder: folder.listFiles (File::isDirectory))
        {
            sampleFile = this.findSampleFileRecursively (subFolder, fileName);
            if (sampleFile != null)
                return sampleFile;
        }

        return null;
    }
}
