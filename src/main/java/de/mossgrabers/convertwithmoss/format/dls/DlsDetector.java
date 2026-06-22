// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.dls;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.dls.DlsFile;
import de.mossgrabers.convertwithmoss.file.dls.DlsInstrument;
import de.mossgrabers.convertwithmoss.file.dls.DlsRegion;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively DLS files in folders. Files must end with <i>.dls</i>.
 *
 * @author Jürgen Moßgraber
 */
public class DlsDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DlsDetector (final INotifier notifier)
    {
        super ("Downloadable Sounds", "DLS", notifier, new MetadataSettingsUI ("DLS"), ".dls");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.readDlsFile (file, in);
        }
        catch (final Exception ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Load the DLS file.
     *
     * @param file The DLS file
     * @param in The input stream to read from
     * @return The parsed multi-sample source
     * @throws IOException Could not read from the file
     * @throws ParseException Could not parse the file
     */
    private List<IMultisampleSource> readDlsFile (final File file, final InputStream in) throws IOException, ParseException
    {
        final DlsFile dlsFile = new DlsFile (file);

        final List<DlsInstrument> instruments = dlsFile.getInstruments ();
        if (instruments == null)
            return Collections.emptyList ();

        final List<WavFileSampleData> waveSampleData = dlsFile.createWaveSampleData ();
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        for (final DlsInstrument instrument: instruments)
        {
            this.notifier.log ("IDS_DLS_FOUND_INSTRUMENT", instrument.getName ());
            final Optional<IMultisampleSource> multisample = this.createMultisample (file, dlsFile, instrument, waveSampleData);
            if (multisample.isPresent ())
                multiSampleSources.add (multisample.get ());
        }

        return multiSampleSources;
    }


    /**
     * Create a multi-sample from the read EXS information.
     *
     * @param sourceFile The source file
     * @param dlsFile The read DLS file
     * @param instrument The DLS instrument
     * @param waveSampleData The wave samples
     * @return The multi-sample source
     * @throws IOException Could not create a multi-sample
     */
    private Optional<IMultisampleSource> createMultisample (final File sourceFile, final DlsFile dlsFile, final DlsInstrument instrument, final List<WavFileSampleData> waveSampleData) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (sourceFile);
        final File parentFile = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, instrument.getName ());

        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();

        for (final DlsRegion dlsRegion: instrument.getRegions ())
        {
            if (this.waitForDelivery ())
                return Optional.empty ();

            final ISampleZone zone = this.createAndCheckSampleZone (dlsRegion, dlsFile.getWaveInfoFileNames (), waveSampleData);
            if (zone == null)
                continue;
            final Integer layerIndex = Integer.valueOf (dlsRegion.getLayer ());
            IGroup group = groupsMap.get (layerIndex);
            if (group == null)
            {
                group = new DefaultGroup ("Layer " + layerIndex);
                groupsMap.put (layerIndex, group);
            }

            zone.setKeyRoot (dlsRegion.getUnityNote ());
            zone.setKeyLow (dlsRegion.getKeyRangeLow ());
            zone.setKeyHigh (dlsRegion.getKeyRangeHigh ());
            zone.setVelocityLow (dlsRegion.getVelocityRangeLow ());
            zone.setVelocityHigh (dlsRegion.getVelocityRangeHigh ());

            zone.setGain (dlsRegion.getGain ());

            final double fineTuning = dlsRegion.getFineTune ();
            if (fineTuning != 0)
                zone.setTuning (fineTuning);

            // Could be used for panning
            final long channelPlacement = dlsRegion.getChannelPlacement ();
            if (channelPlacement != 1)
                this.notifier.logError ("IDS_DLS_CHANNEL_PLACEMENT");

            zone.getLoops ().addAll (dlsRegion.getLoops ());

            group.addSampleZone (zone);
        }

        multisampleSource.setGroups (new ArrayList<> (groupsMap.values ()));

        // TODO applyGlobalParameters (multisampleSource, dlsFile.getParameters ());

        final IMetadata metadata = multisampleSource.getMetadata ();
        this.fillMetadata (dlsFile, parts, metadata, name, instrument);
        return Optional.of (multisampleSource);
    }


    private void fillMetadata (final DlsFile dlsFile, final String [] parts, final IMetadata metadata, final String name, final DlsInstrument instrument)
    {
        String description = dlsFile.formatInfoFields (InfoRiffChunkId.INFO_CMNT, InfoRiffChunkId.INFO_ICMT, InfoRiffChunkId.INFO_COMM, InfoRiffChunkId.INFO_ICOP, InfoRiffChunkId.INFO_IMIT, InfoRiffChunkId.INFO_IMIU, InfoRiffChunkId.INFO_TORG, InfoRiffChunkId.INFO_TORG);
        // Remove unnecessary 'Comment' labels. Order is important!
        description = description.replace (InfoRiffChunkId.INFO_COMM.getDescription () + ": ", "").replace (InfoRiffChunkId.INFO_ICMT.getDescription () + ": ", "").replace (InfoRiffChunkId.INFO_CMNT.getDescription () + ": ", "");

        final List<String> tags = new ArrayList<> ();
        Collections.addAll (tags, parts);
        tags.add (name);
        tags.add (instrument.getName ());
        metadata.detectMetadata (this.settingsConfiguration, tags.toArray (new String [tags.size ()]), instrument.isDrumInstrument () ? TagDetector.CATEGORY_DRUM : null);

        if (TagDetector.CATEGORY_UNKNOWN.equals (metadata.getCategory ()))
            metadata.setCategory (TagDetector.detectCategory (description.split ("\n")));

        metadata.setCreator (dlsFile.getSoundDesigner ());
        metadata.setCreationDateTime (dlsFile.getParsedCreationDate ());
        metadata.setDescription (description);
    }


    private ISampleZone createAndCheckSampleZone (final DlsRegion dlsRegion, final List<String> waveInfoChunks, final List<WavFileSampleData> waveSampleData)
    {
        long sampleIndex = dlsRegion.getTableIndex ();
        if (sampleIndex >= waveSampleData.size ())
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Long.toString (sampleIndex));
            return null;
        }

        return new DefaultSampleZone (waveInfoChunks.get ((int) sampleIndex), waveSampleData.get ((int) sampleIndex));
    }

    // TODO
    // private static void applyGlobalParameters (final IMultisampleSource multisampleSource, final
    // DlsParameters parameters)
    // {
    // applyFilterParameters (multisampleSource, parameters);
    //
    // // Pitch bend up/down
    // final Integer pitchBendUp = parameters.get (DlsParameters.PITCH_BEND_UP);
    // final Integer pitchBendDown = parameters.get (DlsParameters.PITCH_BEND_DOWN);
    // final int bendUp = pitchBendUp != null ? pitchBendUp.intValue () * 100 : 200;
    // int bendDown = -200;
    // if (pitchBendDown != null)
    // bendDown = pitchBendDown.intValue () == -1 ? -bendUp : pitchBendDown.intValue () * -100;
    //
    // final Integer globalCoarseTune = parameters.get (DlsParameters.COARSE_TUNE);
    // final int coarseTune = globalCoarseTune == null ? 0 : globalCoarseTune.intValue ();
    // final Integer globalFineTune = parameters.get (DlsParameters.FINE_TUNE);
    // final int fineTune = globalFineTune == null ? 0 : globalFineTune.intValue ();
    // final double tuneOffset = coarseTune + fineTune / 100.0;
    //
    // final IEnvelope globalAmplitudeEnvelope = createEnvelope (parameters, 1);
    // final Integer env1Velocity = parameters.get (DlsParameters.ENV1_VEL_SENS);
    // final double velocityModulation = env1Velocity == null ? 1 : 1 - Math.clamp
    // (env1Velocity.intValue () / -60.0, 0, 1);
    //
    // for (final IGroup group: multisampleSource.getGroups ())
    // for (final ISampleZone zone: group.getSampleZones ())
    // {
    // zone.setBendUp (bendUp);
    // zone.setBendDown (bendDown);
    // zone.setTuning (zone.getTuning () + tuneOffset);
    //
    // final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
    // amplitudeModulator.setDepth (1.0);
    // amplitudeModulator.setSource (globalAmplitudeEnvelope);
    //
    // zone.getAmplitudeVelocityModulator ().setDepth (velocityModulation);
    // }
    // }
    //
    //
    // private static IEnvelope createEnvelope (final DlsParameters parameters, final int
    // envelopeIndex)
    // {
    // final Integer delay = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_DELAY_START :
    // DlsParameters.ENV2_DELAY_START);
    // final Integer attack = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_ATK_HI_VEL :
    // DlsParameters.ENV2_ATK_HI_VEL);
    // final Integer hold = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_HOLD :
    // DlsParameters.ENV2_HOLD);
    // final Integer decay = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_DECAY :
    // DlsParameters.ENV2_DECAY);
    // final Integer sustain = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_SUSTAIN :
    // DlsParameters.ENV2_SUSTAIN);
    // final Integer release = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_RELEASE :
    // DlsParameters.ENV2_RELEASE);
    // final Integer attackCurve = parameters.get (envelopeIndex == 1 ? DlsParameters.ENV1_ATK_CURVE
    // : DlsParameters.ENV2_ATK_CURVE);
    //
    // final IEnvelope envelope = new DefaultEnvelope ();
    // // Maximum time for each step are 10 seconds
    // envelope.setDelayTime (delay == null ? 0 : delay.doubleValue () / 127.0 * 10.0);
    // envelope.setAttackTime (attack == null ? 0 : attack.intValue ());
    // envelope.setHoldTime (hold == null ? 0 : hold.doubleValue () / 127.0 * 10.0);
    // envelope.setDecayTime (decay == null ? 0 : decay.doubleValue () / 127.0 * 10.0);
    // envelope.setSustainLevel (sustain == null ? 1.0 : sustain.doubleValue () / 127.0);
    // envelope.setReleaseTime (release == null ? 0 : release.doubleValue () / 127.0 * 10.0);
    //
    // if (attackCurve != null)
    // {
    // int v = attackCurve.intValue ();
    // if (v >= 0xFF00)
    // v = v - 0xFF00 - 0x100;
    // envelope.setAttackSlope (Math.clamp (v / 99.0, -1, 1));
    // }
    //
    // return envelope;
    // }
    //
    //
    // private static void applyFilterParameters (final IMultisampleSource multisampleSource, final
    // DlsParameters parameters)
    // {
    // final Integer isFilterEnabled = parameters.get (DlsParameters.FILTER1_TOGGLE);
    // if (isFilterEnabled == null || isFilterEnabled.intValue () <= 0)
    // return;
    //
    // final Integer filterTypeIndex = parameters.get (DlsParameters.FILTER1_TYPE);
    // if (filterTypeIndex == null)
    // return;
    //
    // final IEnvelope globalFilterEnvelope = createEnvelope (parameters, 2);
    //
    // final FilterType filterType;
    // final int poles;
    // switch (filterTypeIndex.intValue ())
    // {
    // default:
    // case 0:
    // filterType = FilterType.LOW_PASS;
    // poles = 4;
    // break;
    // case 1:
    // filterType = FilterType.LOW_PASS;
    // poles = 3;
    // break;
    // case 2:
    // filterType = FilterType.LOW_PASS;
    // poles = 2;
    // break;
    // case 3:
    // filterType = FilterType.LOW_PASS;
    // poles = 1;
    // break;
    // case 4:
    // filterType = FilterType.HIGH_PASS;
    // poles = 2;
    // break;
    // case 5:
    // filterType = FilterType.BAND_PASS;
    // poles = 2;
    // break;
    // }
    //
    // final Integer filterFrequency = parameters.get (DlsParameters.FILTER1_CUTOFF);
    // final Integer filterResonance = parameters.get (DlsParameters.FILTER1_RESO);
    //
    // final int frequency = filterFrequency == null ? 1000 : filterFrequency.intValue ();
    // final int resonance = filterResonance == null ? 0 : filterResonance.intValue ();
    //
    // final double cutoff = MathUtils.denormalize (frequency / 1000.0, 0, IFilter.MAX_FREQUENCY);
    // final IFilter filter = new DefaultFilter (filterType, poles, cutoff, Math.clamp (resonance /
    // 1000.0, 0, 1));
    // final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
    // cutoffModulator.setDepth (1.0);
    // cutoffModulator.setSource (globalFilterEnvelope);
    // multisampleSource.setGlobalFilter (filter);
    // }
    //
    //
    // private static boolean limitByGroupAttributes (final DlsGroup DlsGroup, final ISampleZone
    // zone)
    // {
    // // Note: volume values can be added since the zone volume is relative!
    // if (DlsGroup.volume != 0)
    // zone.setGain (DlsGroup.volume + zone.getGain ());
    // if (DlsGroup.pan != 0)
    // zone.setPanning (zone.getPanning () + DlsGroup.pan);
    //
    // // Zone is completely outside of the groups' velocity range
    // if (zone.getVelocityHigh () < DlsGroup.minVelocity)
    // return false;
    // if (DlsGroup.minVelocity != 0 && zone.getVelocityLow () < DlsGroup.minVelocity)
    // zone.setVelocityLow (DlsGroup.minVelocity);
    // // Zone is completely outside of the groups' velocity range
    // if (zone.getVelocityLow () > DlsGroup.maxVelocity)
    // return false;
    // if (DlsGroup.maxVelocity != 0 && zone.getVelocityHigh () > DlsGroup.maxVelocity)
    // zone.setVelocityHigh (DlsGroup.maxVelocity);
    //
    // // Zone is completely outside of the groups' note range
    // if (zone.getKeyHigh () < DlsGroup.startNote)
    // return false;
    // if (DlsGroup.startNote != 0 && zone.getKeyLow () < DlsGroup.startNote)
    // zone.setKeyLow (DlsGroup.startNote);
    // // Zone is completely outside of the groups' note range
    // if (zone.getKeyLow () > DlsGroup.endNote)
    // return false;
    // if (DlsGroup.endNote != 0 && zone.getKeyHigh () > DlsGroup.endNote)
    // zone.setKeyHigh (DlsGroup.endNote);
    //
    // return true;
    // }
}
