// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.dls;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.dls.DlsArticulation;
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

        try
        {
            return this.readDlsFile (file);
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
     * @return The parsed multi-sample source
     * @throws IOException Could not read from the file
     * @throws ParseException Could not parse the file
     */
    private List<IMultisampleSource> readDlsFile (final File file) throws IOException, ParseException
    {
        final DlsFile dlsFile = new DlsFile (file);

        final List<DlsInstrument> instruments = dlsFile.getInstruments ();
        if (instruments == null)
            return Collections.emptyList ();

        final List<WavFileSampleData> waveSampleData = dlsFile.createWaveSampleData ();
        final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
        for (final DlsInstrument instrument: instruments)
        {
            if (this.waitForDelivery ())
                return Collections.emptyList ();

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
     */
    private Optional<IMultisampleSource> createMultisample (final File sourceFile, final DlsFile dlsFile, final DlsInstrument instrument, final List<WavFileSampleData> waveSampleData)
    {
        final Map<Integer, IGroup> groupsMap = new TreeMap<> ();

        for (final DlsRegion dlsRegion: instrument.getRegions ())
        {
            if (this.waitForDelivery ())
                return Optional.empty ();

            final Optional<ISampleZone> zone = this.createAndCheckSampleZone (instrument, dlsRegion, dlsFile.getWaveInfoFileNames (), waveSampleData);
            if (zone.isEmpty ())
                continue;
            final Integer layerIndex = Integer.valueOf (dlsRegion.getLayer ());
            final IGroup group = groupsMap.computeIfAbsent (layerIndex, index -> new DefaultGroup ("Layer " + index));
            group.addSampleZone (zone.get ());
        }

        final String name = FileUtils.getNameWithoutType (sourceFile);
        final File parentFile = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);
        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, parts, instrument.getName (), new ArrayList<> (groupsMap.values ()));
        this.fillMetadata (dlsFile, parts, multisampleSource.getMetadata (), name, instrument);
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


    private Optional<ISampleZone> createAndCheckSampleZone (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final List<String> waveInfoChunks, final List<WavFileSampleData> waveSampleData)
    {
        final long sampleIndex = dlsRegion.getTableIndex ();
        if (sampleIndex >= waveSampleData.size ())
        {
            this.notifier.logError ("IDS_EXS_SAMPLE_INDEX_OUT_OF_BOUNDS", Long.toString (sampleIndex));
            return Optional.empty ();
        }

        final ISampleZone zone = new DefaultSampleZone (waveInfoChunks.get ((int) sampleIndex), waveSampleData.get ((int) sampleIndex));

        zone.setKeyRoot (dlsRegion.getUnityNote ());
        zone.setKeyLow (dlsRegion.getKeyRangeLow ());
        zone.setKeyHigh (dlsRegion.getKeyRangeHigh ());
        zone.setVelocityLow (dlsRegion.getVelocityRangeLow ());
        zone.setVelocityHigh (dlsRegion.getVelocityRangeHigh ());

        // The DLS key group is the exclusive group, 0 means that the region is not assigned to any
        // group, valid groups are 1 to 15
        zone.setExclusiveGroup (Math.clamp (dlsRegion.getKeyGroup (), 0, 15));

        zone.setGain (dlsRegion.getGain ());

        final double fineTuning = dlsRegion.getFineTune ();
        if (fineTuning != 0)
            zone.setTuning (fineTuning);

        // Could be used for panning
        final long channelPlacement = dlsRegion.getChannelPlacement ();
        if (channelPlacement != 1)
            this.notifier.logError ("IDS_DLS_CHANNEL_PLACEMENT");

        applyArticulations (dlsInstrument, dlsRegion, zone);

        zone.getLoops ().addAll (dlsRegion.getLoops ());

        return Optional.of (zone);
    }


    private static void applyArticulations (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final ISampleZone zone)
    {
        // Amplitude
        final IEnvelope amplitudeEnvelope = createEnvelope (dlsInstrument, dlsRegion, true);
        final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
        amplitudeModulator.setDepth (1.0);
        amplitudeModulator.setSource (amplitudeEnvelope);

        final Optional<DlsArticulation> velocityModulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_KEYONVELOCITY, DlsArticulation.CONN_DST_GAIN);
        if (velocityModulation.isPresent ())
            zone.getAmplitudeVelocityModulator ().setDepth (gainToLinear (velocityModulation.get ().getScale ()));

        // Amplitude key tracking
        final Optional<DlsArticulation> keyModulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_KEYNUMBER, DlsArticulation.CONN_DST_GAIN);
        if (keyModulation.isPresent ())
            zone.setAmplitudeKeyTracking (normalizeKeyNumberToGain (keyModulation.get ().getScale ()));

        // Pitch bend up/down
        final Optional<DlsArticulation> pitchBendModulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_PITCHWHEEL, DlsArticulation.CONN_DST_PITCH);
        if (pitchBendModulation.isPresent ())
        {
            final int bendValue = pitchBendModulation.get ().getScale ();
            zone.setBendUp (bendValue);
            zone.setBendDown (-bendValue);
        }

        // Pitch envelope
        final Optional<DlsArticulation> pitchModulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_EG2, DlsArticulation.CONN_DST_PITCH);
        if (pitchModulation.isPresent ())
        {
            final IEnvelope pitchEnvelope = createEnvelope (dlsInstrument, dlsRegion, true);
            final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
            // The scale is a relative pitch in cent, stored as a 32-bit fixed point number with
            // 65536 representing one cent
            final double depthCents = pitchModulation.get ().getScale () / 65536.0;
            pitchEnvelopeModulator.setDepth (Math.clamp (depthCents, -IEnvelope.MAX_ENVELOPE_DEPTH, IEnvelope.MAX_ENVELOPE_DEPTH) / IEnvelope.MAX_ENVELOPE_DEPTH);
            pitchEnvelopeModulator.setSource (pitchEnvelope);
        }

        // Pitch LFO (vibrato). The low frequency oscillator which modulates the pitch is the
        // vibrato; its frequency and delay are set by their own connections.
        // Only an *uncontrolled* connection is read: the format additionally defines the same
        // connection controlled by the modulation wheel, which is the amount the wheel can dial in
        // and must not be applied as a permanently sounding vibrato.
        final Optional<DlsArticulation> pitchLfoModulation = getUncontrolledArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_LFO, DlsArticulation.CONN_DST_PITCH);
        if (pitchLfoModulation.isPresent ())
        {
            final double depthCents = DlsArticulation.relativePitchToCents (pitchLfoModulation.get ().getScale ());
            if (depthCents != 0)
            {
                final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
                pitchLfoModulator.setDepth (Math.clamp (depthCents, -IEnvelope.MAX_ENVELOPE_DEPTH, IEnvelope.MAX_ENVELOPE_DEPTH) / IEnvelope.MAX_ENVELOPE_DEPTH);

                final ILfo pitchLfo = pitchLfoModulator.getSource ();
                final Optional<DlsArticulation> lfoFrequency = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_NONE, DlsArticulation.CONN_DST_LFO_FREQUENCY);
                if (lfoFrequency.isPresent ())
                    pitchLfo.setRate (DlsArticulation.absolutePitchToHertz (lfoFrequency.get ().getScale ()));
                pitchLfo.setDelay (getTime (dlsInstrument, dlsRegion, DlsArticulation.CONN_DST_LFO_STARTDELAY));
            }
        }

        // Pitch tuning
        final Optional<DlsArticulation> pitchTuning = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_NONE, DlsArticulation.CONN_DST_PITCH);
        if (pitchTuning.isPresent ())
        {
            // The scale is a relative pitch in cent (65536 per cent), the tuning is in semi-tones
            final double tuning = pitchTuning.get ().getScale () / 65536.0 / 100.0;
            zone.setTuning (zone.getTuning () + tuning);
        }

        // Note: filters are supported but there is no example file...
    }


    private static Optional<DlsArticulation> getArticulation (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final int source, final int destination)
    {
        final Optional<DlsArticulation> articulation = getArticulation (dlsRegion.getArticulations (), source, destination);
        if (articulation.isPresent ())
            return articulation;
        return getArticulation (dlsInstrument.getArticulations (), source, destination);
    }


    /**
     * Get a connection which is not modulated by a controller, e.g. the modulation wheel.
     *
     * @param dlsInstrument The instrument
     * @param dlsRegion The region
     * @param source The source, see the CONN_SRC_* constants
     * @param destination The destination, see the CONN_DST_* constants
     * @return The connection, if any
     */
    private static Optional<DlsArticulation> getUncontrolledArticulation (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final int source, final int destination)
    {
        final Optional<DlsArticulation> articulation = getUncontrolledArticulation (dlsRegion.getArticulations (), source, destination);
        if (articulation.isPresent ())
            return articulation;
        return getUncontrolledArticulation (dlsInstrument.getArticulations (), source, destination);
    }


    private static Optional<DlsArticulation> getUncontrolledArticulation (final List<DlsArticulation> articulations, final int source, final int destination)
    {
        for (final DlsArticulation articulation: articulations)
            if (articulation.getSource () == source && articulation.getDestination () == destination && articulation.getControl () == DlsArticulation.CONN_SRC_NONE)
                return Optional.of (articulation);
        return Optional.empty ();
    }


    private static Optional<DlsArticulation> getArticulation (final List<DlsArticulation> articulations, final int source, final int destination)
    {
        for (final DlsArticulation articulation: articulations)
            if (articulation.getSource () == source && articulation.getDestination () == destination)
                return Optional.of (articulation);
        return Optional.empty ();
    }


    private static IEnvelope createEnvelope (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final boolean isEnvelope1)
    {
        final double delay = getTime (dlsInstrument, dlsRegion, isEnvelope1 ? DlsArticulation.CONN_DST_EG1_DELAYTIME : DlsArticulation.CONN_DST_EG2_DELAYTIME);
        final double attack = getTime (dlsInstrument, dlsRegion, isEnvelope1 ? DlsArticulation.CONN_DST_EG1_ATTACKTIME : DlsArticulation.CONN_DST_EG2_ATTACKTIME);
        final double hold = getTime (dlsInstrument, dlsRegion, isEnvelope1 ? DlsArticulation.CONN_DST_EG1_HOLDTIME : DlsArticulation.CONN_DST_EG2_HOLDTIME);
        final double decay = getTime (dlsInstrument, dlsRegion, isEnvelope1 ? DlsArticulation.CONN_DST_EG1_DECAYTIME : DlsArticulation.CONN_DST_EG2_DECAYTIME);
        final double release = getTime (dlsInstrument, dlsRegion, isEnvelope1 ? DlsArticulation.CONN_DST_EG1_RELEASETIME : DlsArticulation.CONN_DST_EG2_RELEASETIME);

        final double sustain;
        if (isEnvelope1)
            sustain = getLevel (dlsInstrument, dlsRegion, DlsArticulation.CONN_DST_EG1_SUSTAINLEVEL);
        else
            sustain = getLevel (dlsInstrument, dlsRegion, DlsArticulation.CONN_DST_EG2_SUSTAINLEVEL);

        final IEnvelope envelope = new DefaultEnvelope ();
        if (delay >= 0)
            envelope.setDelayTime (delay);
        if (attack >= 0)
            envelope.setAttackTime (attack);
        if (hold >= 0)
            envelope.setHoldTime (hold);
        if (decay >= 0)
            envelope.setDecayTime (decay);
        if (sustain >= 0)
            envelope.setSustainLevel (sustain);
        if (release >= 0)
            envelope.setReleaseTime (release);

        return envelope;
    }


    private static double getTime (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final int destination)
    {
        final Optional<DlsArticulation> articulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_NONE, destination);
        return articulation.isEmpty () ? -1 : DlsArticulation.absoluteTimeToSeconds (articulation.get ().getScale ());
    }


    private static double getLevel (final DlsInstrument dlsInstrument, final DlsRegion dlsRegion, final int destination)
    {
        final Optional<DlsArticulation> articulation = getArticulation (dlsInstrument, dlsRegion, DlsArticulation.CONN_SRC_NONE, destination);
        return articulation.isEmpty () ? -1 : DlsArticulation.normalizeSustainLevel (articulation.get ().getScale ());
    }


    /**
     * Converts a DLS Gain scale value to a normalized linear amplitude [0.0, 1.0]. Per Section
     * 1.14.4: Gain Units = 200 * log10(v/V) * 65536 1.0 = 0 dB (unity), values below represent
     * attenuation.
     *
     * @param value Raw 32-bit signed gain value
     * @return Linear amplitude in range [0.0, 1.0]
     */
    private static double gainToLinear (final int value)
    {
        return Math.pow (10.0, value / (200.0 * 65536.0));
    }


    /**
     * Converts the scale value of a DLS key-number to gain connection into the normalized amplitude
     * key tracking range [-1..1]. The key-number source is normalized across the full MIDI range,
     * therefore the scale is the gain change from key 0 to key 127. Like all DLS gain values it is
     * given in units of 1/655360 dB (see {@link #gainToLinear(int)}). 100% key tracking is defined
     * as 1 dB per key which equals 127 dB across the full range.
     *
     * @param scale The raw 32-bit signed gain value
     * @return The amplitude key tracking in the range [-1.0, 1.0]
     */
    private static double normalizeKeyNumberToGain (final int scale)
    {
        return Math.clamp (scale / (655360.0 * 127.0), -1, 1);
    }
}
