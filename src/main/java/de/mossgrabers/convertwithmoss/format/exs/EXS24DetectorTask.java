// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
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
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import javafx.scene.control.ComboBox;


/**
 * Detects recursively Logic EXS24 files in folders. Files must end with <i>.exs</i>.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24DetectorTask extends AbstractDetectorTask
{
    private static final String     ENDING_EXS = ".exs";

    private final ComboBox<Integer> levelsOfDirectorySearch;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadataConfig Additional metadata configuration parameters
     * @param levelsOfDirectorySearch Combo box to read the directory search level
     */
    protected EXS24DetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadataConfig, final ComboBox<Integer> levelsOfDirectorySearch)
    {
        super (notifier, consumer, sourceFolder, metadataConfig, ENDING_EXS);

        this.levelsOfDirectorySearch = levelsOfDirectorySearch;
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
        catch (final Exception ex)
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
        final EXS24File exs24File = new EXS24File (this.notifier);
        exs24File.read (in);

        if (exs24File.getInstrument () == null)
            return Collections.emptyList ();

        // Fix IDs if not set...
        final List<EXS24Zone> zones = exs24File.getZones ();
        for (int i = 0; i < zones.size (); i++)
        {
            final EXS24Zone zone = zones.get (i);
            if (zone.id <= 0)
                zone.id = i;
        }

        final List<EXS24Sample> samples = exs24File.getSamples ();
        if (samples.isEmpty () && !zones.isEmpty ())
        {
            this.notifier.logError ("IDS_EXS_NO_SAMPLES", Integer.toString (zones.size ()));
            return Collections.emptyList ();
        }

        final Optional<IMultisampleSource> multisample = this.createMultisample (file, exs24File);
        if (multisample.isEmpty ())
            return Collections.emptyList ();
        return Collections.singletonList (multisample.get ());
    }


    /**
     * Create a multi-sample from the read EXS information.
     *
     * @param file The source file
     * @param exs24File The read EXS24 file
     * @return The multi-sample source
     * @throws IOException Could not create a multi-sample
     */
    private Optional<IMultisampleSource> createMultisample (final File file, final EXS24File exs24File) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final File parentFile = file.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();
        final Map<IGroup, EXS24Group> groupMapping = new HashMap<> ();

        File previousFolder = null;
        final List<EXS24Sample> exs24Samples = exs24File.getSamples ();
        final Map<Integer, EXS24Group> exs24Groups = exs24File.getGroups ();
        for (final EXS24Zone exs24Zone: exs24File.getZones ())
        {
            if (this.waitForDelivery ())
                return Optional.empty ();

            final Pair<ISampleZone, File> zonePair = this.createAndCheckSampleZone (parentFile, previousFolder, exs24Zone, exs24Samples);
            if (zonePair == null)
                continue;
            previousFolder = zonePair.getValue ();

            final ISampleZone zone = zonePair.getKey ();
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

            zone.setPanorama (Math.clamp (exs24Zone.pan, -50, 50) / 50.0);

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
            if (exs24Group != null && exs24Group.enableByRoundRobin && exs24Group.roundRobinGroupPos >= -1)
            {
                zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                // roundRobinGroupPos seems to be -1, 0, 1, ...
                zone.setSequencePosition (exs24Group.roundRobinGroupPos + 2);
            }
        }

        multisampleSource.setGroups (new ArrayList<> (groupsMap.values ()));
        applyGlobalParameters (multisampleSource, exs24File.getParameters ());
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


    private Pair<ISampleZone, File> createAndCheckSampleZone (final File parentFile, final File previousFolder, final EXS24Zone exs24Zone, final List<EXS24Sample> exs24Samples) throws IOException
    {
        // If sample index is not set, use the zone id (index)
        int sampleIndex = exs24Zone.sampleIndex;
        if (sampleIndex < 0)
        {
            sampleIndex = exs24Zone.id;
            exs24Zone.sampleIndex = sampleIndex;
        }

        if (sampleIndex >= exs24Samples.size ())
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Integer.toString (sampleIndex));
            return null;
        }

        final EXS24Sample exs24Sample = exs24Samples.get (sampleIndex);
        if (exs24Sample == null)
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Integer.toString (sampleIndex));
            return null;
        }

        final int height = this.levelsOfDirectorySearch.getSelectionModel ().getSelectedItem ().intValue ();
        final File sampleFile = findSampleFile (this.notifier, parentFile, previousFolder, exs24Sample.fileName, height);
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return null;
        }
        return new Pair<> (this.createSampleZone (sampleFile), sampleFile.getParentFile ());
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
        final Integer env1Velocity = parameters.get (EXS24Parameters.ENV1_VEL_SENS);
        final double velocityModulation = env1Velocity == null ? 1 : 1 - Math.clamp (env1Velocity.intValue () / -60.0, 0, 1);

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (bendUp);
                zone.setBendDown (bendDown);
                zone.setTune (zone.getTune () + tuneOffset);

                final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
                amplitudeModulator.setDepth (1.0);
                amplitudeModulator.setSource (globalAmplitudeEnvelope);

                zone.getAmplitudeVelocityModulator ().setDepth (velocityModulation);
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
        final Integer attackCurve = parameters.get (envelopeIndex == 1 ? EXS24Parameters.ENV1_ATK_CURVE : EXS24Parameters.ENV2_ATK_CURVE);

        final IEnvelope envelope = new DefaultEnvelope ();
        // Maximum time for each step are 10 seconds
        envelope.setDelayTime (delay == null ? 0 : delay.doubleValue () / 127.0 * 10.0);
        envelope.setAttackTime (attack == null ? 0 : attack.intValue ());
        envelope.setHoldTime (hold == null ? 0 : hold.doubleValue () / 127.0 * 10.0);
        envelope.setDecayTime (decay == null ? 0 : decay.doubleValue () / 127.0 * 10.0);
        envelope.setSustainLevel (sustain == null ? 1.0 : sustain.doubleValue () / 127.0);
        envelope.setReleaseTime (release == null ? 0 : release.doubleValue () / 127.0 * 10.0);

        if (attackCurve != null)
        {
            int v = attackCurve.intValue ();
            if (v >= 0xFF00)
                v = (v - 0xFF00) - 0x100;
            envelope.setAttackSlope (Math.clamp (v / 99.0, -1, 1));
        }

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
        final IFilter filter = new DefaultFilter (filterType, poles, cutoff, Math.clamp (resonance / 1000.0, 0, 1));
        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        cutoffModulator.setDepth (1.0);
        cutoffModulator.setSource (globalFilterEnvelope);
        multisampleSource.setGlobalFilter (filter);
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
}
