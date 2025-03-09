// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ZoneChannels;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
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
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Creator for Waldorf Quantum/Iridium files.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreator extends AbstractCreator
{
    private static final String                                SLOPE_RC               = "RC";
    private static final String                                SLOPE_LINEAR           = "Lin";
    private static final String                                SLOPE_EXP              = "Exp";
    private static final String                                SLOPE_EXP_ALT          = "Exp alt";

    private static final String                                FORMAT_SECONDS         = "%.2f secs";
    private static final int                                   PRESET_VERSION         = 14;
    private static final WaldorfQpatResourceHeader             EMPTY_RESOURCE_HEADER  = new WaldorfQpatResourceHeader ();

    private static final String                                QPAT_LIMIT_TO_16_441   = "QpatLimitTo16441";
    private static final DestinationAudioFormat                OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, true);
    private static final DestinationAudioFormat                DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat ();

    private static final Map<Integer, WaldorfQpatResourceType> TYPE_LOOKUP            = HashMap.newHashMap (3);
    static
    {
        TYPE_LOOKUP.put (Integer.valueOf (0), WaldorfQpatResourceType.USER_SAMPLE_MAP1);
        TYPE_LOOKUP.put (Integer.valueOf (1), WaldorfQpatResourceType.USER_SAMPLE_MAP2);
        TYPE_LOOKUP.put (Integer.valueOf (2), WaldorfQpatResourceType.USER_SAMPLE_MAP3);
    }

    private CheckBox limitTo16441;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WaldorfQpatCreator (final INotifier notifier)
    {
        super ("Waldorf Quantum/Iridium", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_QPAT_SEPARATOR");
        this.limitTo16441 = panel.createCheckBox ("@IDS_QPAT_RESAMPLE_TO_16_441");

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.limitTo16441.setSelected (config.getBoolean (QPAT_LIMIT_TO_16_441, true));

        this.loadWavChunkSettings (config, "QPAT");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (QPAT_LIMIT_TO_16_441, this.limitTo16441.isSelected ());

        this.saveWavChunkSettings (config, "QPAT");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, sampleName, "qpat");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final String relativeSamplePath = "samples/" + sampleName;

        List<IGroup> groups = reduceGroups (multisampleSource.getNonEmptyGroups (true));

        // Since panning is not working on the sample level, combine split stereo to stereo files
        // If the combination fails, the file is created anyway but might contain wrong panning.
        if (ZoneChannels.detectChannelConfiguration (groups) == ZoneChannels.SPLIT_STEREO)
        {
            final Optional<IGroup> stereoGroup = ZoneChannels.combineSplitStereo (groups);
            if (stereoGroup.isPresent ())
            {
                this.notifier.log ("IDS_QPAT_COMBINED_TO_STEREO");
                groups = Collections.singletonList (stereoGroup.get ());
            }
        }

        multisampleSource.setGroups (groups);
        storeMultisample (multisampleSource, multiFile, groups, relativeSamplePath);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeSamplePath);
        safeCreateDirectory (sampleFolder);

        final boolean doLimit = this.limitTo16441.isSelected ();
        if (doLimit)
            recalculateSamplePositions (multisampleSource, 44100);
        this.writeSamples (sampleFolder, multisampleSource, doLimit ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the QPAT file and store it.
     *
     * @param multisampleSource The multi-sample source
     * @param multiFile The file in which to store
     * @param groups The pre-processed groups
     * @param relativeSamplePath The relative sample path
     * @throws IOException Could not store the file
     */
    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final List<IGroup> groups, final String relativeSamplePath) throws IOException
    {
        final List<WaldorfQpatParameter> parameters = createParameters (groups);
        final List<String> sampleMaps = createSampleMaps (groups, relativeSamplePath);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            writeHeader (out, multisampleSource.getMetadata (), multisampleSource.getName ());

            StreamUtils.writeUnsigned16 (out, parameters.size (), false);
            StreamUtils.padBytes (out, 2);

            // Write up to 3 sample maps (groups have already been reduced to a max. of 3) ...
            for (int i = 0; i < sampleMaps.size (); i++)
            {
                final String sampleMap = sampleMaps.get (i);
                final WaldorfQpatResourceHeader resourceHeader = new WaldorfQpatResourceHeader ();
                resourceHeader.type = TYPE_LOOKUP.get (Integer.valueOf (i));
                resourceHeader.length = sampleMap.getBytes ().length;
                resourceHeader.write (out);
            }
            // .... and pad with empty resources
            for (int i = 0; i < WaldorfQpatConstants.MAX_RESOURCES - sampleMaps.size (); i++)
                EMPTY_RESOURCE_HEADER.write (out);

            // No 2nd layer
            StreamUtils.writeUnsigned16 (out, 0, false);
            StreamUtils.writeUnsigned16 (out, 0, false);
            StreamUtils.writeUnsigned32 (out, 0, false);
            // Instrument type on which the patch was saved last. Set to Quantum.
            out.write (0);

            // Padding up to 512 bytes.
            StreamUtils.padBytes (out, 75);

            // Write all parameters
            for (final WaldorfQpatParameter param: parameters)
                param.write (out);

            // Write resource(s)
            for (final String sampleMap: sampleMaps)
                out.write (sampleMap.getBytes ());
        }
    }


    /**
     * Create a sample map for each group. A sample map is a text file which describes a basic
     * multi-sample configuration.
     *
     * @param groups The groups
     * @param relativeSamplePath The relative path to the samples
     * @return The sample maps
     * @throws IOException Could not read the necessary audio metadata of a sample
     */
    private static List<String> createSampleMaps (final List<IGroup> groups, final String relativeSamplePath) throws IOException
    {
        final List<String> sampleMaps = new ArrayList<> ();

        for (final IGroup group: groups)
        {
            final StringBuilder sb = new StringBuilder ();

            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (sb.length () > 0)
                    sb.append ('\n');

                final ISampleData sampleData = zone.getSampleData ();
                final double numSampleFrames = sampleData.getAudioMetadata ().getNumberOfSamples ();

                // Sample Path - '4:' refers to the USB drive. This is required to trigger the
                // copying of the samples to the internal memory
                sb.append ("\"4:").append (relativeSamplePath).append ('/').append (StringUtils.fixASCII (zone.getName ())).append (".wav\"\t");

                // Pitch - tuning needs to be subtracted since the sample plays high if the root
                // note is lower!
                sb.append (formatMapDouble (zone.getKeyRoot () - zone.getTune ())).append ('\t');

                // FromNote / ToNote
                sb.append (zone.getKeyLow ()).append ('\t').append (zone.getKeyHigh ()).append ('\t');

                // Gain
                final double v = Math.clamp (zone.getGain (), Double.NEGATIVE_INFINITY, 20);
                sb.append (formatMapDouble (Math.pow (10, v / 20))).append ('\t');

                // FromVelo / ToVelo
                sb.append (zone.getVelocityLow ()).append ('\t').append (zone.getVelocityHigh ()).append ('\t');

                // Pan - CURRENTLY IGNORED
                sb.append (formatMapDouble ((zone.getPanorama () + 1.0) / 2.0)).append ('\t');

                // Start / End
                sb.append (formatMapDouble (zone.getStart () / numSampleFrames)).append ('\t');
                sb.append (formatMapDouble (zone.getStop () / numSampleFrames)).append ('\t');

                // Loop mode, start, stop
                final List<ISampleLoop> loops = zone.getLoops ();
                if (loops.isEmpty ())
                    sb.append ("0\t0\t").append (formatMapDouble (zone.getStop () / numSampleFrames)).append ('\t');
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (loop.getType () == LoopType.FORWARDS ? 1 : 2).append ('\t');
                    sb.append (formatMapDouble (loop.getStart () / numSampleFrames)).append ('\t');
                    sb.append (formatMapDouble (loop.getEnd () / numSampleFrames)).append ('\t');
                }

                // Direction
                sb.append (zone.isReversed () ? 1 : 0).append ('\t');

                if (loops.isEmpty ())
                    sb.append ("0\t");
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (formatMapDouble (loop.getCrossfade ())).append ('\t');
                }

                // TrackPitch
                sb.append (zone.getKeyTracking () == 0 ? "0" : "1");
            }

            sampleMaps.add (sb.toString ());
        }

        return sampleMaps;
    }


    /**
     * Reduces the groups of the multi-sample to a maximum of 3. The sample zones of all other
     * groups are added to the 3rd group.
     *
     * @param groups The groups
     * @return The reduced groups
     */
    private static List<IGroup> reduceGroups (final List<IGroup> groups)
    {
        if (groups.size () > 3)
        {
            // Add all sample zones of groups 4..last to group 3
            final IGroup lastGroup = groups.get (2);
            for (int i = 3; i < groups.size (); i++)
                for (final ISampleZone zone: groups.get (i).getSampleZones ())
                    lastGroup.addSampleZone (zone);
            // Remove groups 4..last
            final int count = groups.size () - 3;
            for (int i = 0; i < count; i++)
                groups.removeLast ();
        }
        return groups;
    }


    private static List<WaldorfQpatParameter> createParameters (final List<IGroup> groups)
    {
        final List<WaldorfQpatParameter> parameters = new ArrayList<> ();

        for (int i = 0; i < groups.size (); i++)
        {
            final String groupIndex = Integer.toString (i + 1);

            final List<ISampleZone> sampleZones = groups.get (i).getSampleZones ();
            // Empty groups are already removed!
            final ISampleZone firstZone = sampleZones.get (0);

            // Particle
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Type", "Particle", 2.0f));

            // Osc1CoarsePitch / Osc1FinePitch: already set in the sample maps!
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "CoarsePitch", "+0 semi", 24.0f));
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "FinePitch", "+0.0 cents", 0.5f));

            // Osc1PitchBendRange: [0..48] ~ [-24..24]
            final int pitchbend = Math.clamp (Math.round (firstZone.getBendUp () / 100.0), -24, 24);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "PitchBendRange", (pitchbend < 0 ? "-" : "+") + pitchbend, pitchbend + 24));

            // Osc1Keytrack: [0..1] ~ [-200..200] - already set in the sample maps
            final double keyTracking = 100.0;
            final String keyTrackingStr = "+" + String.format (Locale.US, "%.2f", Double.valueOf (keyTracking)) + " %";
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Keytrack", keyTrackingStr, (float) ((keyTracking + 200.0) / 400.0)));

            // Osc1Vol: [0..1] ~ [-inf dB..0.000 dB] - already set in the sample maps
            final String volumeStr = "+" + String.format (Locale.US, "%.3f dB", Double.valueOf (0));
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Vol", volumeStr, (float) convertFromDecibels (0)));

            // Osc1Pan: [0..1] ~ [L..R] - already set in the sample maps
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Pan", "Center", 0.5f));

            createPitchEnvelopeModulator (parameters, firstZone.getPitchModulator (), i + 1);

            if (i == 0)
            {
                createFilterParameters (parameters, firstZone.getFilter ());

                final IEnvelopeModulator amplitudeEnvelopeModulator = firstZone.getAmplitudeEnvelopeModulator ();
                final IEnvelope envelope = amplitudeEnvelopeModulator.getSource ();
                createEnvelope (parameters, envelope, "AmpEnv", "AmpEnv");

                // AmpVeloAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
                final double ampVeloAmount = amplitudeEnvelopeModulator.getDepth ();
                parameters.add (new WaldorfQpatParameter ("AmpVeloAmount", String.format (Locale.US, "%.2f", Double.valueOf (ampVeloAmount * 100.0)) + " %", (float) ((ampVeloAmount + 1.0) / 2.0)));
            }
        }

        return parameters;
    }


    private static void createPitchEnvelopeModulator (final List<WaldorfQpatParameter> parameters, final IEnvelopeModulator pitchEnvelopeModulator, final int oscIndex)
    {
        // Use the matrix slots 1-3 and free envelopes 1-3 for the respective oscillator 1-3
        // modulation
        double depth = pitchEnvelopeModulator.getDepth ();
        if (depth == 0)
            return;

        // MatrixOnOffX: [0] "Disabled" [1] "Active"
        parameters.add (new WaldorfQpatParameter ("MatrixOnOff" + oscIndex, "Active", 1.0f));

        // MatrixSrcX: [4] "Free Env1" [5] "Free Env2" [6] "Free Env3"
        parameters.add (new WaldorfQpatParameter ("MatrixSrc" + oscIndex, "Free Env" + oscIndex, oscIndex + 3.0f));

        // MatrixDstX: [2] "Osc1 Pitch" [3] "Osc2 Pitch" [4] "Osc3 Pitch"
        parameters.add (new WaldorfQpatParameter ("MatrixDst" + oscIndex, "Osc" + oscIndex + " Pitch", oscIndex + 1.0f));

        // MatrixAmount1 - Can only pitch -24..24 semi-tones (instead of -48..48)
        depth = Math.clamp (depth, -0.5, 0.5) * 2;
        final String depthStr = String.format (Locale.US, "%.2f", Double.valueOf (depth * 100.0)) + " %";
        parameters.add (new WaldorfQpatParameter ("MatrixAmount" + oscIndex, depthStr, (float) ((depth + 1.0) / 2.0)));

        final String prefix = "FreeEnv" + oscIndex;
        createEnvelope (parameters, pitchEnvelopeModulator.getSource (), prefix, prefix);
    }


    /**
     * Create all filter parameters.
     *
     * @param parameters Where to add the filter parameters
     * @param optFilter The filter for which to create the parameters
     */
    private static void createFilterParameters (final List<WaldorfQpatParameter> parameters, final Optional<IFilter> optFilter)
    {
        if (optFilter.isEmpty () || optFilter.get ().getType () == FilterType.BAND_REJECTION)
        {
            parameters.add (new WaldorfQpatParameter ("FilterState", "Bypass", 1));
            return;
        }

        final IFilter filter = optFilter.get ();

        // FilterState: [0] "Active" [1] "Bypass" [2] "Off"
        parameters.add (new WaldorfQpatParameter ("FilterState", "Active", 0));

        // Filter12Type: [0] "12dB LP" [1] "12dB sat. LP" [2] "12dB dirty LP" [3] "24dB LP" [4]
        // "24dB sat. LP" [5] "24dB dirty LP" [6] "12dB HP" [7] "12dB sat. HP" [8] "12dB dirty HP"
        // [9] "24dB HP" [10] "24dB sat. HP" [11] "24dB dirty HP" [12] "12dB BP" [13] "12dB sat. BP"
        // [14] "12dB dirty BP" [15] "24dB BP" [16] "24dB sat. BP" [17] "24dB dirty BP"
        int pos;
        String filterName;
        switch (filter.getType ())
        {
            default:
            case LOW_PASS:
                filterName = "dB LP";
                pos = 0;
                break;
            case HIGH_PASS:
                filterName = "dB HP";
                pos = 6;
                break;
            case BAND_PASS:
                filterName = "dB BP";
                pos = 12;
                break;
        }
        final boolean is24 = filter.getPoles () == 4;
        if (is24)
            pos += 3;
        parameters.add (new WaldorfQpatParameter ("Filter12Type", (is24 ? "24" : "12") + filterName, pos));

        // Filter1CutOff: [0.00] "8.1758 Hz" ... [1.00] "19912.2 Hz"
        final double cutoff = Math.log (filter.getCutoff () / 8.1758) / (Math.log (2) * 11.25);
        parameters.add (new WaldorfQpatParameter ("Filter1CutOff", String.format (Locale.US, "%.4f Hz", Double.valueOf (cutoff)), (float) cutoff));

        // Filter1Reso: [0.00] "0.00 %" ... [1.00] "100.00 %"
        final double resonance = filter.getResonance ();
        parameters.add (new WaldorfQpatParameter ("Filter1Reso", String.format (Locale.US, "%.2f", Double.valueOf (resonance * 100.0)) + " %", (float) resonance));

        // Filter1EnvAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final double filterVeloAmount = filter.getCutoffVelocityModulator ().getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1VeloAmount", String.format (Locale.US, "%.2f", Double.valueOf (filterVeloAmount * 100.0)) + " %", (float) ((filterVeloAmount + 1.0) / 2.0)));

        final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
        final double filterEnvAmount = modulator.getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1EnvAmount", String.format (Locale.US, "%.2f", Double.valueOf (filterEnvAmount * 100.0)) + " %", (float) ((filterEnvAmount + 1.0) / 2.0)));

        final IEnvelope envelope = modulator.getSource ();

        createEnvelope (parameters, envelope, "Filter1Env", "Filter1");
    }


    private static void createEnvelope (final List<WaldorfQpatParameter> parameters, final IEnvelope envelope, final String prefix, final String slopePrefix)
    {
        final boolean isPitch = prefix.startsWith ("Free");

        if (isPitch && envelope.getStartLevel () != 0)
        {
            // xxxEnvDelay
            parameters.add (new WaldorfQpatParameter (prefix + "Delay", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (0)), 0));
            // xxxEnvAttack
            parameters.add (new WaldorfQpatParameter (prefix + "Attack", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (0)), 0));
            // xxxEnvDecay
            final double decayTime = Math.clamp (envelope.getAttackTime (), 0, 60);
            parameters.add (new WaldorfQpatParameter (prefix + "Decay", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (decayTime)), (float) convertFromTime (decayTime)));
        }
        else
        {
            // xxxEnvDelay
            final double delayTime = Math.clamp (envelope.getDelayTime (), 0, 2);
            parameters.add (new WaldorfQpatParameter (prefix + "Delay", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (delayTime)), (float) convertFromDelayTime (delayTime)));
            // xxxEnvAttack
            final double attackTime = Math.clamp (envelope.getAttackTime (), 0, 60);
            parameters.add (new WaldorfQpatParameter (prefix + "Attack", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (attackTime)), (float) convertFromTime (attackTime)));
            // xxxEnvDecay
            final double decayTime = Math.clamp (envelope.getDecayTime (), 0, 60);
            parameters.add (new WaldorfQpatParameter (prefix + "Decay", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (decayTime)), (float) convertFromTime (decayTime)));
        }

        // xxxEnvRelease
        final double releaseTime = Math.clamp (envelope.getReleaseTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter (prefix + "Release", String.format (Locale.US, FORMAT_SECONDS, Double.valueOf (releaseTime)), (float) convertFromTime (releaseTime)));

        // xxxEnvSustain
        double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel == -1)
            sustainLevel = isPitch ? 0 : 1;
        parameters.add (new WaldorfQpatParameter (prefix + "Sustain", String.format (Locale.US, "%.2f", Double.valueOf (sustainLevel * 100.0)) + " %", (float) sustainLevel));

        if (isPitch && envelope.getStartLevel () != 0)
        {
            // xxxDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double decaySlope = envelope.getAttackSlope ();
            String decaySlopeStr = SLOPE_LINEAR;
            double decaySlopeValue = 2;
            if (decaySlope == -1)
            {
                decaySlopeStr = SLOPE_EXP;
                decaySlopeValue = 0;
            }
            else if (decaySlope < 0)
            {
                decaySlopeStr = SLOPE_EXP_ALT;
                decaySlopeValue = 0.5;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "DecayCurve", decaySlopeStr, (float) decaySlopeValue));
        }
        else
        {
            // xxxAttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
            final double attackSlope = envelope.getAttackSlope ();
            String attackSlopeStr = SLOPE_LINEAR;
            double attackSlopeValue = 2;
            if (attackSlope > 0)
            {
                attackSlopeStr = SLOPE_EXP;
                attackSlopeValue = 0;
            }
            else if (attackSlope < 0)
            {
                attackSlopeStr = SLOPE_RC;
                attackSlopeValue = 1;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "AttackCurve", attackSlopeStr, (float) attackSlopeValue));

            // xxxDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double decaySlope = envelope.getDecaySlope ();
            String decaySlopeStr = SLOPE_LINEAR;
            double decaySlopeValue = 2;
            if (decaySlope == -1)
            {
                decaySlopeStr = SLOPE_EXP;
                decaySlopeValue = 0;
            }
            else if (decaySlope < 0)
            {
                decaySlopeStr = SLOPE_EXP_ALT;
                decaySlopeValue = 0.5;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "DecayCurve", decaySlopeStr, (float) decaySlopeValue));

            // xxxReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double releaseSlope = envelope.getReleaseSlope ();
            String releaseSlopeStr = SLOPE_LINEAR;
            double releaseSlopeValue = 2;
            if (releaseSlope == -1)
            {
                releaseSlopeStr = SLOPE_EXP;
                releaseSlopeValue = 0;
            }
            else if (releaseSlope < 0)
            {
                releaseSlopeStr = SLOPE_EXP_ALT;
                releaseSlopeValue = 0.5;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "ReleaseCurve", releaseSlopeStr, (float) releaseSlopeValue));
        }
    }


    /**
     * Writes the header information preceding the actual data.
     *
     * @param out The output stream to write to
     * @param metadata The metadata
     * @param name The name of the multi-sample
     * @throws IOException Could not write
     */
    private static void writeHeader (final OutputStream out, final IMetadata metadata, final String name) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, WaldorfQpatConstants.MAGIC, false);
        StreamUtils.writeUnsigned32 (out, PRESET_VERSION, false);
        StreamUtils.writeASCII (out, StringUtils.fixASCII (name), WaldorfQpatConstants.MAX_STRING_LENGTH);
        StreamUtils.writeASCII (out, StringUtils.fixASCII (metadata.getCreator ()), WaldorfQpatConstants.MAX_STRING_LENGTH);
        StreamUtils.writeASCII (out, StringUtils.fixASCII (metadata.getDescription ()).replace ('\r', ' ').replace ('\n', ' '), WaldorfQpatConstants.MAX_STRING_LENGTH);

        final List<String> categories = new ArrayList<> ();
        categories.add (metadata.getCategory ());
        Collections.addAll (categories, metadata.getKeywords ());
        while (categories.size () < 4)
            categories.add ("");
        for (int i = 0; i < 4; i++)
            StreamUtils.writeASCII (out, StringUtils.fixASCII (categories.get (i)), WaldorfQpatConstants.MAX_STRING_LENGTH);
    }


    private static double convertFromDecibels (final double db)
    {
        if (db == Double.NEGATIVE_INFINITY)
            return 0;
        return Math.clamp (Math.pow (10, db / 40), 0, 1);
    }


    private static double convertFromDelayTime (final double y)
    {
        return Math.sqrt (y / 2);
    }


    private static double convertFromTime (final double y)
    {
        return Math.log (y / 0.06) / Math.log (1000);
    }


    private static String formatMapDouble (final double value)
    {
        return String.format (Locale.US, "%.8f", Double.valueOf (value));
    }
}