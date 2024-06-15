// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
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
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.FormatException;
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
public class WaldorfQpatDetectorTask extends AbstractDetectorTask
{
    private static final String                         ENDING_QPAT = ".qpat";

    private static Map<Integer, String>                 SYNTH_CODES = new HashMap<> (3);
    private static Map<WaldorfQpatResourceType, String> GROUP_NAMES = new HashMap<> (3);
    static
    {
        SYNTH_CODES.put (Integer.valueOf (0), "Quantum");
        SYNTH_CODES.put (Integer.valueOf (1), "Iridium");
        SYNTH_CODES.put (Integer.valueOf (2), "IridiumCore");

        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP1, "Sample Map 1");
        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP2, "Sample Map 2");
        GROUP_NAMES.put (WaldorfQpatResourceType.USER_SAMPLE_MAP3, "Sample Map 3");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected WaldorfQpatDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ENDING_QPAT);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
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
        catch (final FormatException ex)
        {
            this.notifier.logError ("IDS_WS_EXPECTED_TAG", ex.getMessage ());
        }
        return Collections.emptyList ();
    }


    /**
     * Load and parse the QPAT file.
     *
     * @param in The input stream to read from
     * @param file The source file
     * @return The parsed multi-sample source
     * @throws FormatException Error in the format of the file
     * @throws IOException Could not read from the file
     */
    private List<IMultisampleSource> parseFile (final InputStream in, final File file) throws FormatException, IOException
    {
        final String name = FileUtils.getNameWithoutType (file);
        final String [] parts = AudioFileUtils.createPathParts (file.getParentFile (), this.sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (file, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, file));

        final long version = readHeader (in, multisampleSource);

        final long numParams = StreamUtils.readUnsigned16 (in, false);
        // Skip padding
        in.skipNBytes (2);

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

        final int flags = StreamUtils.readUnsigned16 (in, false);

        // True, if a 2nd layer is present
        final boolean isMulti = (flags & 1) > 0;
        // Multi-mode, only valid when multi-flag is set: Single / Split / Layered
        final int timbreMode = StreamUtils.readUnsigned16 (in, false);
        // For multi-layer patches the alternate layer is stored directly after the main layer.
        // This points to the beginning byte of the alternate layer.
        final long altTimbreOffset = StreamUtils.readUnsigned32 (in, false);

        // Available from version 9 onwards. Instrument type on which the patch was saved last.
        int synthCode = in.read ();
        if (version < 9)
            synthCode = 0;
        this.notifier.log ("IDS_QPAT_VERSION", Long.toString (version), SYNTH_CODES.get (Integer.valueOf (synthCode)));

        // Padding up to 512 bytes.
        in.skipNBytes (75);

        // Read all parameters
        final Map<String, WaldorfQpatParameter> parameters = new TreeMap<> ();
        for (int i = 0; i < numParams; i++)
        {
            final WaldorfQpatParameter param = new WaldorfQpatParameter (in);
            parameters.put (param.name, param);
        }

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

        if (isMulti)
        {
            // TODO read the 2nd layer
        }

        return Collections.singletonList (multisampleSource);
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
                tune = (int) coarseParameter.value - 24;

            // Osc1FinePitch: [0..1] ~ [-100..100]
            final WaldorfQpatParameter fineParameter = parameters.get ("Osc" + groupIndex + "FinePitch");
            if (fineParameter != null)
                tune += fineParameter.value * 2.0 - 1.0;

            // Osc1PitchBendRange: [0..48] ~ [-24..24]
            int pitchbend = 0;
            final WaldorfQpatParameter pitchbendParameter = parameters.get ("Osc" + groupIndex + "PitchBendRange");
            if (pitchbendParameter != null)
                pitchbend = (int) pitchbendParameter.value - 24;

            // Osc1Keytrack: [0..1] ~ [-200..200]
            double keyTracking = 100;
            final WaldorfQpatParameter keyTrackingParameter = parameters.get ("Osc" + groupIndex + "Keytrack");
            if (keyTrackingParameter != null)
                keyTracking = MathUtils.clamp (keyTrackingParameter.value * 400.0 - 200.0, 0, 100) / 100.0;

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
            double panorama = 0.5;
            final WaldorfQpatParameter panoramaParameter = parameters.get ("Osc" + groupIndex + "Pan");
            if (panoramaParameter != null)
                panorama = panoramaParameter.value * 2.0 - 1.0;

            // Osc1MinNote - C-2 - 0.0 -> Only relevant for splits!
            // Osc1MaxNote - G8 - 127.0 -> Only relevant for splits!

            final Optional<IFilter> filter = parseFilter (parameters);

            final IEnvelope ampEnvelope = parseAmpEnvelope (parameters);
            // AmpVeloAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
            double ampVeloAmount = 1;
            final WaldorfQpatParameter ampVeloAmountParameter = parameters.get ("AmpVeloAmount");
            if (ampVeloAmountParameter != null)
                ampVeloAmount = ampVeloAmountParameter.value * 2.0 - 1.0;

            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setTune (zone.getTune () + tune);
                zone.setKeyTracking (keyTracking);
                zone.setBendUp (pitchbend);
                zone.setBendDown (-pitchbend);
                zone.setGain (volume);
                zone.setPanorama (panorama);
                zone.getAmplitudeVelocityModulator ().setDepth (ampVeloAmount);
                zone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);
                if (filter.isPresent ())
                    zone.setFilter (filter.get ());
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
        // "24dB sat. LP" [5] "24dB dirty LP"
        int poles = 2;
        final WaldorfQpatParameter filterTypeParameter = parameters.get ("Filter12Type");
        if (filterTypeParameter != null)
            poles = filterTypeParameter.value < 3 ? 2 : 4;

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

        final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, poles, cutoff, resonance);

        // Filter1EnvAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final WaldorfQpatParameter filterVeloAmountParameter = parameters.get ("Filter1VeloAmount");
        if (filterVeloAmountParameter != null)
            filter.getCutoffVelocityModulator ().setDepth (filterVeloAmountParameter.value * 2.0 - 1.0);

        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        final WaldorfQpatParameter filterEnvAmountParameter = parameters.get ("Filter1EnvAmount");
        if (filterEnvAmountParameter != null)
            cutoffEnvelopeModulator.setDepth (filterEnvAmountParameter.value * 2.0 - 1.0);

        final IEnvelope envelope = cutoffEnvelopeModulator.getSource ();

        // Filter1EnvDelay
        final WaldorfQpatParameter envDelayParameter = parameters.get ("Filter1EnvDelay");
        if (envDelayParameter != null)
            envelope.setDelayTime (convertDelayTime (envDelayParameter.value));
        // Filter1EnvAttack
        final WaldorfQpatParameter envAttackParameter = parameters.get ("Filter1EnvAttack");
        if (envAttackParameter != null)
            envelope.setAttackTime (convertTime (envAttackParameter.value));
        // Filter1EnvDecay
        final WaldorfQpatParameter envDecayParameter = parameters.get ("Filter1EnvDecay");
        if (envDecayParameter != null)
            envelope.setDecayTime (convertTime (envDecayParameter.value));
        // Filter1EnvRelease
        final WaldorfQpatParameter envReleaseParameter = parameters.get ("Filter1EnvRelease");
        if (envReleaseParameter != null)
            envelope.setReleaseTime (convertTime (envReleaseParameter.value));

        // Filter1EnvSustain
        final WaldorfQpatParameter envSustainParameter = parameters.get ("Filter1EnvSustain");
        if (envSustainParameter != null)
            envelope.setSustainLevel (envSustainParameter.value);

        // Filter1AttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
        final WaldorfQpatParameter envAttackCurveParameter = parameters.get ("Filter1AttackCurve");
        if (envAttackCurveParameter != null && envAttackCurveParameter.value != 2)
            envelope.setAttackSlope (envAttackCurveParameter.value == 0 ? 1 : -1);

        // Filter1DecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envDecayCurveParameter = parameters.get ("Filter1DecayCurve");
        if (envDecayCurveParameter != null && envDecayCurveParameter.value != 2)
            envelope.setDecaySlope (envDecayCurveParameter.value == 0 ? 1 : 0.5);

        // Filter1ReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envReleaseCurveParameter = parameters.get ("Filter1ReleaseCurve");
        if (envReleaseCurveParameter != null && envReleaseCurveParameter.value != 2)
            envelope.setReleaseSlope (envReleaseCurveParameter.value == 0 ? 1 : 0.5);

        return Optional.of (filter);
    }


    /**
     * Parse the amplitude envelope.
     *
     * @param parameters The parameters
     * @return The parsed amplitude envelope
     */
    private static IEnvelope parseAmpEnvelope (final Map<String, WaldorfQpatParameter> parameters)
    {
        final IEnvelope envelope = new DefaultEnvelope ();

        // AmpEnvDelay
        final WaldorfQpatParameter envDelayParameter = parameters.get ("AmpEnvDelay");
        if (envDelayParameter != null)
            envelope.setDelayTime (convertDelayTime (envDelayParameter.value));
        // AmpEnvAttack
        final WaldorfQpatParameter envAttackParameter = parameters.get ("AmpEnvAttack");
        if (envAttackParameter != null)
            envelope.setAttackTime (convertTime (envAttackParameter.value));
        // AmpEnvDecay
        final WaldorfQpatParameter envDecayParameter = parameters.get ("AmpEnvDecay");
        if (envDecayParameter != null)
            envelope.setDecayTime (convertTime (envDecayParameter.value));
        // AmpEnvRelease
        final WaldorfQpatParameter envReleaseParameter = parameters.get ("AmpEnvRelease");
        if (envReleaseParameter != null)
            envelope.setReleaseTime (convertTime (envReleaseParameter.value));

        // AmpEnvSustain
        final WaldorfQpatParameter envSustainParameter = parameters.get ("AmpEnvSustain");
        if (envSustainParameter != null)
            envelope.setSustainLevel (envSustainParameter.value);

        // AmpAttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
        final WaldorfQpatParameter envAttackCurveParameter = parameters.get ("AmpEnvAttackCurve");
        if (envAttackCurveParameter != null && envAttackCurveParameter.value != 2)
            envelope.setAttackSlope (envAttackCurveParameter.value == 0 ? 1 : -1);

        // AmpDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envDecayCurveParameter = parameters.get ("AmpEnvDecayCurve");
        if (envDecayCurveParameter != null && envDecayCurveParameter.value != 2)
            envelope.setDecaySlope (envDecayCurveParameter.value == 0 ? 1 : 0.5);

        // AmpReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final WaldorfQpatParameter envReleaseCurveParameter = parameters.get ("AmpEnvReleaseCurve");
        if (envReleaseCurveParameter != null && envReleaseCurveParameter.value != 2)
            envelope.setReleaseSlope (envReleaseCurveParameter.value == 0 ? 1 : 0.5);

        return envelope;
    }


    /**
     * Reads and checks the header information preceding the actual data.
     *
     * @param in The input stream to read from
     * @param multisampleSource The multi-sample source
     * @throws IOException Could not read
     * @throws FormatException Found unexpected format of the file
     */
    private static long readHeader (final InputStream in, final IMultisampleSource multisampleSource) throws IOException, FormatException
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
            // "3:" references the internal partition, "4:" is the USB drive
            if (samplePath.length () > 2 && samplePath.charAt (1) == ':')
                samplePath = samplePath.substring (2);
            if (samplePath.length () == 0)
                break;
            final File sampleFile = new File (parentFolder, samplePath);
            final ISampleData sampleData = this.createSampleData (sampleFile);

            final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
            group.addSampleZone (zone);
            final int numSampleFrames = sampleData.getAudioMetadata ().getNumberOfSamples ();

            // Pitch
            if (params.length <= 1)
                continue;
            final double pitch = Double.parseDouble (params[1]);
            final int coarse = (int) Math.round (pitch);
            final double fineTune = pitch - coarse;
            zone.setKeyRoot (coarse);
            zone.setTune (fineTune);

            // FromNote
            if (params.length <= 2)
                continue;
            zone.setKeyLow (MathUtils.clamp (Integer.parseInt (params[2]), 0, 127));

            // ToNote
            if (params.length <= 3)
                continue;
            zone.setKeyHigh (MathUtils.clamp (Integer.parseInt (params[3]), 0, 127));

            // Gain
            if (params.length <= 4)
                continue;
            final double gain = Double.parseDouble (params[4]);
            zone.setGain (Math.floor (20.0 * Math.log10 (gain) * 100.0 + 0.5) * 0.01);

            // FromVelo
            if (params.length <= 5)
                continue;
            zone.setVelocityLow (MathUtils.clamp (Integer.parseInt (params[5]), 1, 127));

            // ToVelo
            if (params.length <= 6)
                continue;
            zone.setVelocityHigh (MathUtils.clamp (Integer.parseInt (params[6]), 1, 127));

            // Pan
            if (params.length <= 7)
                continue;
            zone.setPanorama (MathUtils.clamp (Double.parseDouble (params[7]) * 2.0 - 1.0, -1.0, 1.0));

            // Start
            if (params.length <= 8)
                continue;
            zone.setStart ((int) (Double.parseDouble (params[8]) * numSampleFrames));

            // End
            if (params.length <= 9)
                continue;
            zone.setStop ((int) (Double.parseDouble (params[9]) * numSampleFrames));

            // LoopMode
            if (params.length <= 10)
                continue;
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
                continue;
            if (Integer.parseInt (params[13]) == 1)
                zone.setReversed (true);

            // TrackPitch
            if (params.length > 15)
                zone.setKeyTracking (Double.parseDouble (params[15]));
        }

        return group;
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
