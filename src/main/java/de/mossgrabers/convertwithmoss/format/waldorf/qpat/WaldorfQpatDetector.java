// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Waldorf Quantum/Iridium files in folders. Files must end with <i>.qpat</i>.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Map<Integer, String>                 SYNTH_CODES = HashMap.newHashMap (3);
    private static final Map<Integer, String>                 LAYER_CODES = HashMap.newHashMap (3);
    private static final Map<WaldorfQpatResourceType, String> GROUP_NAMES = HashMap.newHashMap (3);
    static
    {
        SYNTH_CODES.put (Integer.valueOf (0), "Quantum");
        SYNTH_CODES.put (Integer.valueOf (1), "Iridium");
        SYNTH_CODES.put (Integer.valueOf (2), "Iridium Core");

        LAYER_CODES.put (Integer.valueOf (0), "1 Layer");
        LAYER_CODES.put (Integer.valueOf (1), "2 Layers (Split)");
        LAYER_CODES.put (Integer.valueOf (2), "2 Layers (Layered)");

        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP1, "Sample Map 1");
        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP2, "Sample Map 2");
        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP3, "Sample Map 3");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WaldorfQpatDetector (final INotifier notifier)
    {
        super ("Waldorf Quantum/Iridium", "QPAT", notifier, new MetadataSettingsUI ("KorgMultisample"), ".qpat");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.parseFile (in, file);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Load and parse the QPAT file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multi-sample source
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);

        in.mark (in.available () + 1);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();

        boolean readSecondLayer = false;
        boolean isMulti = true;
        while (isMulti)
        {
            final IMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

            final long version = readHeader (in, multisampleSource);

            final long numParams = StreamUtils.readUnsigned16 (in, false);
            // Skip padding
            in.skipNBytes (2);

            final WaldorfQpatResourceHeader [] resources = readResourceHeaders (in);

            final int flags = StreamUtils.readUnsigned16 (in, false);

            // True, if a 2nd layer is present
            isMulti = (flags & 1) > 0;
            // Multi-mode, only valid when multi-flag is set: Single / Split / Layered
            final int timbreMode = StreamUtils.readUnsigned16 (in, false);
            // For multi-layer patches the alternate layer is stored directly after the main layer.
            // This points to the beginning byte of the alternate layer. This is a complete(!) patch
            // including a the header!
            final long altTimbreOffset = StreamUtils.readUnsigned32 (in, false);

            // Available from version 9 onwards. Instrument type on which the patch was saved last.
            int synthCode = in.read ();
            if (version < 9)
                synthCode = 0;
            this.notifier.log ("IDS_QPAT_VERSION", Long.toString (version), SYNTH_CODES.get (Integer.valueOf (synthCode)), LAYER_CODES.get (Integer.valueOf (timbreMode)));

            // Padding up to 512 bytes.
            in.skipNBytes (75);

            // Read all parameters
            final Map<String, WaldorfQpatParameter> parameters = new TreeMap<> ();
            for (int i = 0; i < numParams; i++)
            {
                final WaldorfQpatParameter param = new WaldorfQpatParameter (in);
                parameters.put (param.name, param);
            }

            this.readSampleMaps (in, file, multisampleSource, resources, parameters);

            multisampleSources.add (multisampleSource);

            if (readSecondLayer)
            {
                multisampleSource.setName (multisampleSource.getName () + " 2");
                break;
            }

            if (isMulti)
            {
                readSecondLayer = true;
                in.reset ();
                in.skipNBytes (altTimbreOffset);
            }
        }

        return multisampleSources;
    }


    private void readSampleMaps (final InputStream in, final File file, final IMultisampleSource multisampleSource, final WaldorfQpatResourceHeader [] resources, final Map<String, WaldorfQpatParameter> parameters) throws IOException
    {
        // Read all sample maps (max. 3, one for each oscillator)
        final byte [] resourcesData = in.readAllBytes ();
        final IGroup [] groupsArray = new IGroup [3];
        final List<IGroup> groups = new ArrayList<> ();
        for (int i = 0; i < 3; i++)
        {
            if (resources[i] == null)
                continue;
            final String sampleMap = new String (resourcesData, resources[i].offset, resources[i].length, StandardCharsets.US_ASCII);
            groupsArray[i] = this.parseSampleMap (sampleMap, file.getParentFile ());
            final String groupName = GROUP_NAMES.get (resources[i].type);
            if (groupName != null)
                groupsArray[i].setName (groupName);
            groups.add (groupsArray[i]);
        }
        if (groups.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_QPAT_NOT_SAMPLE_BASED"));
        multisampleSource.setGroups (groups);

        this.applyParameters (groupsArray, parameters);
    }


    /**
     * Read all 3 resource headers.
     *
     * @param in The input stream to read from
     * @return The resource headers
     * @throws IOException Could not read the headers
     */
    private static WaldorfQpatResourceHeader [] readResourceHeaders (final InputStream in) throws IOException
    {
        final WaldorfQpatResourceHeader [] resources = new WaldorfQpatResourceHeader [3];
        for (int i = 0; i < WaldorfQpatConstants.MAX_RESOURCES; i++)
        {
            final WaldorfQpatResourceHeader resourceHeader = new WaldorfQpatResourceHeader ();
            resourceHeader.read (in);
            if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP1)
                resources[0] = resourceHeader;
            else if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP2)
                resources[1] = resourceHeader;
            else if (resourceHeader.type == WaldorfQpatResourceType.USER_SAMPLE_MAP3)
                resources[2] = resourceHeader;
        }
        return resources;
    }


    /**
     * Apply all parameters to the groups/zones.
     *
     * @param groups The 3 groups, might contain null entries!
     * @param parameters The parameters to apply
     */
    private void applyParameters (final IGroup [] groups, final Map<String, WaldorfQpatParameter> parameters)
    {
        for (int i = 0; i < groups.length; i++)
        {
            final IGroup group = groups[i];
            if (group == null)
                continue;

            final int groupIndex = i + 1;

            // Osc1CoarsePitch: [0..48] ~ [-24..24]
            double tune = 0;
            final WaldorfQpatParameter coarseParameter = parameters.get ("Osc" + groupIndex + "CoarsePitch");
            if (coarseParameter != null)
                tune = coarseParameter.value - 24.0;

            // Osc1FinePitch: [0..1] ~ [-100..100]
            final WaldorfQpatParameter fineParameter = parameters.get ("Osc" + groupIndex + "FinePitch");
            if (fineParameter != null)
                tune += fineParameter.value * 2.0 - 1.0;

            // Osc1PitchBendRange: [0..48] ~ [-24..24]
            int pitchbend = 0;
            final WaldorfQpatParameter pitchbendParameter = parameters.get ("Osc" + groupIndex + "PitchBendRange");
            if (pitchbendParameter != null)
                pitchbend = Math.round ((pitchbendParameter.value - 24) * 100);

            // Osc1Keytrack: [0..1] ~ [-200..200]
            double keyTracking = 100;
            final WaldorfQpatParameter keyTrackingParameter = parameters.get ("Osc" + groupIndex + "Keytrack");
            if (keyTrackingParameter != null)
                keyTracking = Math.clamp (keyTrackingParameter.value * 400.0 - 200.0, 0, 100) / 100.0;

            // Osc1Vol: [0..1] ~ [-inf dB..0.000 dB]
            double volume = 0;
            final WaldorfQpatParameter volumeParameter = parameters.get ("Osc" + groupIndex + "Vol");
            if (volumeParameter != null)
            {
                volume = convertToDecibels (volumeParameter.value);
                if (volume == Double.NEGATIVE_INFINITY)
                    this.notifier.logError ("IDS_QPAT_OSC_SILENCED", Integer.toString (groupIndex));
            }

            // Osc1Pan: [0..1] ~ [L..R]
            double panning = 0.5;
            final WaldorfQpatParameter panningParameter = parameters.get ("Osc" + groupIndex + "Pan");
            if (panningParameter != null)
                panning = panningParameter.value * 2.0 - 1.0;

            // Osc1MinNote - C-2 - 0.0 -> Only relevant for splits!
            // Osc1MaxNote - G8 - 127.0 -> Only relevant for splits!

            final Optional<IFilter> filter = parseFilter (parameters);

            final IEnvelope ampEnvelope = parseEnvelope (parameters, "AmpEnv", "AmpEnv");
            // AmpVeloAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
            double ampVeloAmount = 1;
            final WaldorfQpatParameter ampVeloAmountParameter = parameters.get ("AmpVeloAmount");
            if (ampVeloAmountParameter != null)
                ampVeloAmount = ampVeloAmountParameter.value * 2.0 - 1.0;

            final Optional<IEnvelopeModulator> modulator = findPitchEnvelopeModMatrixEntry (parameters, i + 1);

            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setTuning (zone.getTuning () + tune);
                zone.setKeyTracking (keyTracking);
                zone.setBendUp (pitchbend);
                zone.setBendDown (-pitchbend);
                zone.setGain (volume);
                zone.setPanning (panning);
                zone.getAmplitudeVelocityModulator ().setDepth (ampVeloAmount);
                zone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);
                if (filter.isPresent ())
                    zone.setFilter (filter.get ());
                if (modulator.isPresent ())
                {
                    final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
                    pitchModulator.setDepth (modulator.get ().getDepth ());
                    pitchModulator.setSource (modulator.get ().getSource ());
                }
            }
        }
    }


    /**
     * Parse all filter parameters.
     *
     * @param parameters The parameters
     * @return The parsed filter
     */
    private static Optional<IFilter> parseFilter (final Map<String, WaldorfQpatParameter> parameters)
    {
        // FilterState: [0] "Active" [1] "Bypass" [2] "Off"
        final WaldorfQpatParameter filterStateParameter = parameters.get ("FilterState");
        if (filterStateParameter == null || filterStateParameter.value != 0)
            return Optional.empty ();

        // Filter12Type: [0] "12dB LP" [1] "12dB sat. LP" [2] "12dB dirty LP" [3] "24dB LP" [4]
        // "24dB sat. LP" [5] "24dB dirty LP" [6] "12dB HP" [7] "12dB sat. HP" [8] "12dB dirty HP"
        // [9] "24dB HP" [10] "24dB sat. HP" [11] "24dB dirty HP" [12] "12dB BP" [13] "12dB sat. BP"
        // [14] "12dB dirty BP" [15] "24dB BP" [16] "24dB sat. BP" [17] "24dB dirty BP"
        final WaldorfQpatParameter filterTypeParameter = parameters.get ("Filter12Type");
        if (filterTypeParameter == null)
            return Optional.empty ();
        final FilterType type;
        switch ((int) filterTypeParameter.value / 6)
        {
            default:
            case 0:
                type = FilterType.LOW_PASS;
                break;
            case 1:
                type = FilterType.HIGH_PASS;
                break;
            case 2:
                type = FilterType.BAND_PASS;
                break;
        }
        final int poles = (int) filterTypeParameter.value % 6 < 3 ? 2 : 4;

        // Filter1CutOff: [0.00] "8.1758 Hz" ... [1.00] "19912.2 Hz"
        double cutoff = 19912.2;
        final WaldorfQpatParameter filterCutoffParameter = parameters.get ("Filter1CutOff");
        if (filterCutoffParameter != null)
            cutoff = Math.round (8.1758 * Math.pow (2, 11.25 * filterCutoffParameter.value));

        // Filter1Reso: [0.00] "0.00 %" ... [1.00] "100.00 %"
        double resonance = 0;
        final WaldorfQpatParameter filterResonanceParameter = parameters.get ("Filter1Reso");
        if (filterResonanceParameter != null)
            resonance = filterResonanceParameter.value;

        final IFilter filter = new DefaultFilter (type, poles, cutoff, resonance);

        // Filter1EnvAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final WaldorfQpatParameter filterVeloAmountParameter = parameters.get ("Filter1VeloAmount");
        if (filterVeloAmountParameter != null)
            filter.getCutoffVelocityModulator ().setDepth (filterVeloAmountParameter.value * 2.0 - 1.0);

        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        final WaldorfQpatParameter filterEnvAmountParameter = parameters.get ("Filter1EnvAmount");
        if (filterEnvAmountParameter != null)
            cutoffEnvelopeModulator.setDepth (filterEnvAmountParameter.value * 2.0 - 1.0);

        cutoffEnvelopeModulator.setSource (parseEnvelope (parameters, "Filter1Env", "Filter1"));

        return Optional.of (filter);
    }


    /**
     * Parse the amplitude envelope.
     *
     * @param parameters The parameters
     * @param prefix The prefix for the envelope parameters
     * @param slopePrefix The prefix for the slope envelope parameters
     * @return The parsed amplitude envelope
     */
    private static IEnvelope parseEnvelope (final Map<String, WaldorfQpatParameter> parameters, final String prefix, final String slopePrefix)
    {
        final IEnvelope envelope = new DefaultEnvelope ();

        // xxxEnvDelay
        final WaldorfQpatParameter envDelayParameter = parameters.get (prefix + "Delay");
        if (envDelayParameter != null)
            envelope.setDelayTime (convertDelayTime (envDelayParameter.value));
        // xxxEnvAttack
        final WaldorfQpatParameter envAttackParameter = parameters.get (prefix + "Attack");
        if (envAttackParameter != null)
            envelope.setAttackTime (convertTime (envAttackParameter.value));
        // xxxEnvDecay
        final WaldorfQpatParameter envDecayParameter = parameters.get (prefix + "Decay");
        if (envDecayParameter != null)
            envelope.setDecayTime (convertTime (envDecayParameter.value));
        // xxxEnvRelease
        final WaldorfQpatParameter envReleaseParameter = parameters.get (prefix + "Release");
        if (envReleaseParameter != null)
            envelope.setReleaseTime (convertTime (envReleaseParameter.value));

        // xxxEnvSustain
        final WaldorfQpatParameter envSustainParameter = parameters.get (prefix + "Sustain");
        if (envSustainParameter != null)
            envelope.setSustainLevel (envSustainParameter.value);

        // xxxAttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
        final WaldorfQpatParameter envAttackCurveParameter = parameters.get (slopePrefix + "AttackCurve");
        if (envAttackCurveParameter != null && envAttackCurveParameter.value != 2)
            envelope.setAttackSlope (envAttackCurveParameter.value == 0 ? 1 : -1);

        // xxxDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envDecayCurveParameter = parameters.get (slopePrefix + "DecayCurve");
        if (envDecayCurveParameter != null && envDecayCurveParameter.value != 2)
            envelope.setDecaySlope (envDecayCurveParameter.value == 0 ? -1 : -0.5);

        // xxxReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envReleaseCurveParameter = parameters.get (slopePrefix + "ReleaseCurve");
        if (envReleaseCurveParameter != null && envReleaseCurveParameter.value != 2)
            envelope.setReleaseSlope (envReleaseCurveParameter.value == 0 ? -1 : -0.5);

        return envelope;
    }


    /**
     * Reads and checks the header information preceding the actual data.
     *
     * @param in The input stream to read from
     * @param multisampleSource The multi-sample source
     * @return The QPAT version
     * @throws IOException Could not read
     */
    private static long readHeader (final InputStream in, final IMultisampleSource multisampleSource) throws IOException
    {
        final long magic = StreamUtils.readUnsigned32 (in, false);
        if (magic != WaldorfQpatConstants.MAGIC)
            throw new IOException (Functions.getMessage ("IDS_QPAT_UNKNOWN_TYPE"));

        final long version = StreamUtils.readUnsigned32 (in, false);

        final String name = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
        final String author = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
        final String bank = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();

        final List<String> categories = new ArrayList<> ();
        for (int i = 0; i < 4; i++)
        {
            final String category = StreamUtils.readASCII (in, WaldorfQpatConstants.MAX_STRING_LENGTH).trim ();
            if (!category.isBlank ())
                categories.add (category);
        }

        multisampleSource.setName (name);
        final IMetadata metadata = multisampleSource.getMetadata ();
        metadata.setCreator (author);
        metadata.setDescription (bank);
        metadata.setCategory (TagDetector.detectCategory (categories));
        metadata.setKeywords (TagDetector.detectKeywords (categories));

        return version;
    }


    /**
     * Parses the sample map.
     *
     * @param sampleMap The sample map to parse
     * @param parentFolder The parent folder which contains the relative sample paths
     * @return A group with all parsed zones
     * @throws IOException Could not parse the sample map
     */
    private IGroup parseSampleMap (final String sampleMap, final File parentFolder) throws IOException
    {
        final IGroup group = new DefaultGroup ();
        for (final String line: sampleMap.split ("\n"))
        {
            final String [] params = line.trim ().split ("\t");
            if (params.length == 0 || params[0].isBlank ())
                break;

            // Sample Path
            String samplePath = params[0];
            if (samplePath.startsWith ("\""))
                samplePath = samplePath.substring (1);
            if (samplePath.endsWith ("\""))
                samplePath = samplePath.substring (0, samplePath.length () - 1);
            // "3:" or empty references the internal partition, "4:" is the USB drive
            if (samplePath.length () > 2 && samplePath.charAt (1) == ':')
                samplePath = samplePath.substring (2);
            if (!samplePath.isEmpty ())
                this.createSampleZone (parentFolder, group, params, samplePath);
        }

        return group;
    }


    private void createSampleZone (final File parentFolder, final IGroup group, final String [] params, final String samplePath) throws IOException
    {
        final File sampleFile = new File (parentFolder, samplePath);
        final ISampleData sampleData = createSampleData (sampleFile, this.notifier);

        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
        group.addSampleZone (zone);
        final int numSampleFrames = sampleData.getAudioMetadata ().getNumberOfSamples ();

        // Pitch
        if (params.length <= 1)
            return;
        final double pitch = Double.parseDouble (params[1]);
        final int coarse = (int) Math.round (pitch);
        final double fineTune = pitch - coarse;
        zone.setKeyRoot (coarse);
        zone.setTuning (fineTune);

        // FromNote
        if (params.length <= 2)
            return;
        zone.setKeyLow (Math.clamp (Integer.parseInt (params[2]), 0, 127));

        // ToNote
        if (params.length <= 3)
            return;
        zone.setKeyHigh (Math.clamp (Integer.parseInt (params[3]), 0, 127));

        // Gain
        if (params.length <= 4)
            return;
        final double gain = Double.parseDouble (params[4]);
        zone.setGain (Math.floor (20.0 * Math.log10 (gain) * 100.0 + 0.5) * 0.01);

        // FromVelo
        if (params.length <= 5)
            return;
        zone.setVelocityLow (Math.clamp (Integer.parseInt (params[5]), 1, 127));

        // ToVelo
        if (params.length <= 6)
            return;
        zone.setVelocityHigh (Math.clamp (Integer.parseInt (params[6]), 1, 127));

        // Pan
        if (params.length <= 7)
            return;
        zone.setPanning (Math.clamp (Double.parseDouble (params[7]) * 2.0 - 1.0, -1.0, 1.0));

        // Start
        if (params.length <= 8)
            return;
        zone.setStart ((int) (Double.parseDouble (params[8]) * numSampleFrames));

        // End
        if (params.length <= 9)
            return;
        zone.setStop ((int) (Double.parseDouble (params[9]) * numSampleFrames));

        // LoopMode
        if (params.length <= 10)
            return;
        final int loopMode = Integer.parseInt (params[10]);
        if (loopMode == 1 || loopMode == 2)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            zone.getLoops ().add (loop);
            loop.setType (loopMode == 1 ? LoopType.FORWARDS : LoopType.BACKWARDS);

            // LoopStart
            if (params.length > 11)
                loop.setStart ((int) (Double.parseDouble (params[11]) * numSampleFrames));

            // LoopEnd
            if (params.length > 12)
                loop.setEnd ((int) (Double.parseDouble (params[12]) * numSampleFrames));

            // CrossFade
            if (params.length > 14)
                loop.setCrossfade (Double.parseDouble (params[14]));
        }

        // Direction
        if (params.length <= 13)
            return;
        if (Integer.parseInt (params[13]) == 1)
            zone.setReversed (true);

        // TrackPitch
        if (params.length > 15)
            zone.setKeyTracking (Double.parseDouble (params[15]));
    }


    private static Optional<IEnvelopeModulator> findPitchEnvelopeModMatrixEntry (final Map<String, WaldorfQpatParameter> parameters, final int oscIndex)
    {
        for (int i = 1; i <= 40; i++)
        {
            // MatrixOnOffX: [0] "Disabled" [1] "Active"
            final WaldorfQpatParameter isActiveParam = parameters.get ("MatrixOnOff" + i);
            if (isActiveParam == null || isActiveParam.value != 1.0)
                continue;

            // MatrixSrcX: [4] "Free Env1" [5] "Free Env2" [6] "Free Env3"
            final WaldorfQpatParameter sourceParam = parameters.get ("MatrixSrc" + i);
            if (sourceParam.value == 4.0 || sourceParam.value == 5.0 || sourceParam.value == 6.0)
            {
                // MatrixDstX: [2] "Osc1 Pitch" [3] "Osc2 Pitch" [4] "Osc3 Pitch"
                final WaldorfQpatParameter destParam = parameters.get ("MatrixDst" + i);
                if (destParam != null && destParam.value == oscIndex + 1.0)
                {
                    // MatrixAmount1
                    final WaldorfQpatParameter amountParam = parameters.get ("MatrixAmount" + i);
                    if (amountParam.value != 0.5)
                    {
                        // -24..24 needs to translate to -0.5 to 0.5
                        final IEnvelopeModulator modulator = new DefaultEnvelopeModulator (amountParam.value - 1.0);
                        final String prefix = "FreeEnv" + (int) (sourceParam.value - 3.0);
                        final IEnvelope envelope = parseEnvelope (parameters, prefix, prefix);
                        modulator.setSource (envelope);
                        return Optional.of (modulator);
                    }
                }
            }
        }

        return Optional.empty ();
    }


    private static double convertToDecibels (final double x)
    {
        if (x == 0)
            return Double.NEGATIVE_INFINITY;
        return 40 * Math.log10 (x);
    }


    private static double convertDelayTime (final double x)
    {
        // Converts [0..1] to [0..2] seconds
        return 2 * Math.pow (x, 2);
    }


    private static double convertTime (final double x)
    {
        // Converts [0..1] to [0..60] seconds
        return 0.06 * Math.pow (1000, x);
    }
}
