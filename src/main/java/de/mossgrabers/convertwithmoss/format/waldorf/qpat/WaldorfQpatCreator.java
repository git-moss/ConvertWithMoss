// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
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
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
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
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * Creator for Waldorf Quantum/Iridium files.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreator extends AbstractCreator
{
    private static final String                                SLOPE_RC              = "RC";
    private static final String                                SLOPE_LINEAR          = "Lin";
    private static final String                                SLOPE_EXP             = "Exp";
    private static final String                                SLOPE_EXP_ALT         = "Exp alt";

    private static final String                                FORMAT_SECONDS        = "%.2f secs";
    private static final int                                   PRESET_VERSION        = 14;
    private static final WaldorfQpatResourceHeader             EMPTY_RESOURCE_HEADER = new WaldorfQpatResourceHeader ();
    private static final Map<Integer, WaldorfQpatResourceType> TYPE_LOOKUP           = HashMap.newHashMap (3);

    static
    {
        TYPE_LOOKUP.put (Integer.valueOf (0), WaldorfQpatResourceType.USER_SAMPLE_MAP1);
        TYPE_LOOKUP.put (Integer.valueOf (1), WaldorfQpatResourceType.USER_SAMPLE_MAP2);
        TYPE_LOOKUP.put (Integer.valueOf (2), WaldorfQpatResourceType.USER_SAMPLE_MAP3);
    }


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
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "QPAT");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "QPAT");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = new File (destinationFolder, sampleName + ".qpat");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final String relativeSamplePath = "samples/" + sampleName;

        storeMultisample (multisampleSource, multiFile, relativeSamplePath);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeSamplePath);
        safeCreateDirectory (sampleFolder);

        this.writeSamples (sampleFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the QPAT file and store it.
     *
     * @param multisampleSource The multi-sample source
     * @param multiFile The file in which to store
     * @param relativeSamplePath The relative sample path
     * @throws IOException Could not store the file
     */
    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final String relativeSamplePath) throws IOException
    {
        final List<IGroup> groups = reduceGroups (multisampleSource.getNonEmptyGroups (true));
        final List<WaldorfQpatParameter> parameters = createParameters (groups);
        final List<String> sampleMaps = createSampleMaps (groups, relativeSamplePath);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            writeHeader (out, multisampleSource);

            StreamUtils.writeUnsigned16 (out, parameters.size (), false);
            StreamUtils.padBytes (out, 2);

            // Write up to 3 sample maps ...
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
            for (int i = 0; i < sampleMaps.size (); i++)
                out.write (sampleMaps.get (i).getBytes ());
        }
    }


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
                final int numSampleFrames = sampleData.getAudioMetadata ().getNumberOfSamples ();

                // Sample Path
                sb.append ('"').append (relativeSamplePath).append ('/').append (StringUtils.fixASCII (zone.getName ())).append (".wav\"\t");

                // Pitch
                double tune = zone.getTune ();
                // Add only the fractions
                tune -= (int) tune;
                sb.append (formatMapDouble (zone.getKeyRoot () + tune)).append ('\t');

                // FromNote / ToNote
                sb.append (zone.getKeyLow ()).append ('\t').append (zone.getKeyHigh ()).append ('\t');

                // Gain
                final double v = Math.clamp (zone.getGain (), Double.NEGATIVE_INFINITY, 20);
                sb.append (formatMapDouble (Math.pow (10, v / 20))).append ('\t');

                // FromVelo / ToVelo
                sb.append (zone.getVelocityLow ()).append ('\t').append (zone.getVelocityHigh ()).append ('\t');

                // Pan
                sb.append (formatMapDouble ((zone.getPanorama () + 1.0) / 2.0)).append ('\t');

                // Start / End
                sb.append (formatMapDouble (zone.getStart () / (double) numSampleFrames)).append ('\t');
                sb.append (formatMapDouble (zone.getStop () / (double) numSampleFrames)).append ('\t');

                // Loop mode, start, stop
                final List<ISampleLoop> loops = zone.getLoops ();
                if (loops.isEmpty ())
                    sb.append ("0\t0\t").append (formatMapDouble (zone.getStop () / numSampleFrames)).append ('\t');
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (loop.getType () == LoopType.FORWARDS ? 1 : 2).append ('\t');
                    sb.append (formatMapDouble (loop.getStart () / (double) numSampleFrames)).append ('\t');
                    sb.append (formatMapDouble (loop.getEnd () / (double) numSampleFrames)).append ('\t');
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
            {
                for (final ISampleZone zone: groups.get (i).getSampleZones ())
                    lastGroup.addSampleZone (zone);
            }
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

        for (int groupIndex = 0; groupIndex < groups.size (); groupIndex++)
        {
            final List<ISampleZone> sampleZones = groups.get (groupIndex).getSampleZones ();
            // Empty groups are already removed!
            final ISampleZone firstZone = sampleZones.get (0);

            // Particle
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Type", "Particle", 2));

            // Osc1CoarsePitch / Osc1FinePitch: already set in the sample maps!
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "CoarsePitch", "+0 semi", 24));
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "FinePitch", "+0.0 cents", 0.5f));

            // Osc1PitchBendRange: [0..48] ~ [-24..24]
            final int pitchbend = MathUtils.clamp (firstZone.getBendUp (), -24, 24);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "PitchBendRange", (pitchbend < 0 ? "-" : "+") + pitchbend, pitchbend + 24));

            // Osc1Keytrack: [0..1] ~ [-200..200]
            final double keyTracking = firstZone.getKeyTracking () * 100.0;
            final String keyTrackingStr = (keyTracking < 0 ? "-" : "+") + String.format ("%.2f", Double.valueOf (keyTracking)) + " %";
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Keytrack", keyTrackingStr, (float) ((keyTracking + 200.0) / 400.0)));

            // Osc1Vol: [0..1] ~ [-inf dB..0.000 dB]
            final double volumeDB = firstZone.getGain ();
            final double volume = convertFromDecibels (volumeDB);
            final String volumeStr;
            if (volumeDB == Double.NEGATIVE_INFINITY)
                volumeStr = "-inf dB";
            else
                volumeStr = ((volumeDB < 0 ? "-" : "+") + String.format ("%.3f dB", Double.valueOf (volumeDB)));
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Vol", volumeStr, (float) volume));

            // Osc1Pan: [0..1] ~ [L..R]
            final double panorama = firstZone.getPanorama ();
            final String panoramaStr;
            if (panorama == -1)
                panoramaStr = "L";
            else if (panorama == 1)
                panoramaStr = "R";
            else if (panorama == 0)
                panoramaStr = "Center";
            else if (panorama < 0)
                panoramaStr = (int) (panorama * 100.0) + "% L";
            else
                panoramaStr = (int) (panorama * 100.0) + "% R";
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Pan", panoramaStr, (float) ((panorama + 1.0) / 2.0)));

            if (groupIndex == 0)
            {
                createFilterParameters (parameters, firstZone.getFilter ());

                createAmplitudeParameters (parameters, firstZone.getAmplitudeEnvelopeModulator ());

                // AmpVeloAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
                final double ampVeloAmount = firstZone.getAmplitudeVelocityModulator ().getDepth ();
                parameters.add (new WaldorfQpatParameter ("AmpVeloAmount", String.format ("%.2f", Double.valueOf (ampVeloAmount * 100.0)) + " %", (float) ((ampVeloAmount + 1.0) / 2.0)));
            }
        }

        return parameters;
    }


    /**
     * Create all filter parameters.
     *
     * @param parameters Where to add the filter parameters
     * @param optFilter The filter for which to create the parameters
     */
    private static void createFilterParameters (final List<WaldorfQpatParameter> parameters, final Optional<IFilter> optFilter)
    {
        if (optFilter.isEmpty () || optFilter.get ().getType () != FilterType.LOW_PASS)
        {
            parameters.add (new WaldorfQpatParameter ("FilterState", "Off", 2));
            return;
        }

        final IFilter filter = optFilter.get ();

        // FilterState: [0] "Active" [1] "Bypass" [2] "Off"
        parameters.add (new WaldorfQpatParameter ("FilterState", "Active", 0));

        // Filter12Type: [0] "12dB LP" [1] "12dB sat. LP" [2] "12dB dirty LP" [3] "24dB LP" [4]
        // "24dB sat. LP" [5] "24dB dirty LP"
        final boolean is12 = filter.getPoles () == 2;
        parameters.add (new WaldorfQpatParameter ("Filter12Type", is12 ? "12dB LP" : "24dB LP", is12 ? 0 : 3));

        // Filter1CutOff: [0.00] "8.1758 Hz" ... [1.00] "19912.2 Hz"
        final double cutoff = Math.log (filter.getCutoff () / 8.1758) / (Math.log (2) * 11.25);
        parameters.add (new WaldorfQpatParameter ("Filter1CutOff", String.format ("%.4f Hz", Double.valueOf (cutoff)), (float) cutoff));

        // Filter1Reso: [0.00] "0.00 %" ... [1.00] "100.00 %"
        final double resonance = filter.getResonance ();
        parameters.add (new WaldorfQpatParameter ("Filter1Reso", String.format ("%.2f", Double.valueOf (resonance * 100.0)) + " %", (float) resonance));

        // Filter1EnvAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final double filterVeloAmount = filter.getCutoffVelocityModulator ().getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1VeloAmount", String.format ("%.2f", Double.valueOf (filterVeloAmount * 100.0)) + " %", (float) ((filterVeloAmount + 1.0) / 2.0)));

        final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
        final double filterEnvAmount = modulator.getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1EnvAmount", String.format ("%.2f", Double.valueOf (filterEnvAmount * 100.0)) + " %", (float) ((filterEnvAmount + 1.0) / 2.0)));

        final IEnvelope envelope = modulator.getSource ();

        // Filter1EnvDelay
        final double delayTime = MathUtils.clamp (envelope.getDelayTime (), 0, 2);
        parameters.add (new WaldorfQpatParameter ("Filter1EnvDelay", String.format (FORMAT_SECONDS, Double.valueOf (delayTime)), (float) (convertFromDelayTime (delayTime))));

        // Filter1EnvAttack
        final double attackTime = MathUtils.clamp (envelope.getAttackTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("Filter1EnvAttack", String.format (FORMAT_SECONDS, Double.valueOf (attackTime)), (float) (convertFromTime (attackTime))));
        // Filter1EnvDecay
        final double decayTime = MathUtils.clamp (envelope.getDecayTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("Filter1EnvDecay", String.format (FORMAT_SECONDS, Double.valueOf (decayTime)), (float) (convertFromTime (decayTime))));
        // Filter1EnvRelease
        final double releaseTime = MathUtils.clamp (envelope.getReleaseTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("Filter1EnvRelease", String.format (FORMAT_SECONDS, Double.valueOf (releaseTime)), (float) (convertFromTime (releaseTime))));

        // Filter1EnvSustain
        final double sustainLevel = envelope.getSustainLevel ();
        parameters.add (new WaldorfQpatParameter ("Filter1EnvSustain", String.format ("%.2f", Double.valueOf (sustainLevel * 100.0)) + " %", (float) (sustainLevel)));

        // Filter1AttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
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
        parameters.add (new WaldorfQpatParameter ("Filter1AttackCurve", attackSlopeStr, (float) attackSlopeValue));

        // Filter1DecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final double decaySlope = envelope.getDecaySlope ();
        String decaySlopeStr = SLOPE_LINEAR;
        double decaySlopeValue = 2;
        if (decaySlope > 0.5)
        {
            decaySlopeStr = SLOPE_EXP;
            decaySlopeValue = 0;
        }
        else if (decaySlope > 0)
        {
            decaySlopeStr = SLOPE_EXP_ALT;
            decaySlopeValue = 0.5;
        }
        parameters.add (new WaldorfQpatParameter ("Filter1DecayCurve", decaySlopeStr, (float) decaySlopeValue));

        // Filter1ReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final double releaseSlope = envelope.getReleaseSlope ();
        String releaseSlopeStr = SLOPE_LINEAR;
        double releaseSlopeValue = 2;
        if (releaseSlope > 0.5)
        {
            releaseSlopeStr = SLOPE_EXP;
            releaseSlopeValue = 0;
        }
        else if (releaseSlope > 0)
        {
            releaseSlopeStr = SLOPE_EXP_ALT;
            releaseSlopeValue = 0.5;
        }
        parameters.add (new WaldorfQpatParameter ("Filter1ReleaseCurve", releaseSlopeStr, (float) releaseSlopeValue));
    }


    /**
     * Create the amplitude envelope parameters.
     *
     * @param parameters Where to add the filter parameters
     * @param amplitudeEnvelopeModulator The amplitude envelope modulator
     */
    private static void createAmplitudeParameters (final List<WaldorfQpatParameter> parameters, final IEnvelopeModulator amplitudeEnvelopeModulator)
    {
        final IEnvelope envelope = amplitudeEnvelopeModulator.getSource ();

        // AmpEnvDelay
        final double delayTime = MathUtils.clamp (envelope.getDelayTime (), 0, 2);
        parameters.add (new WaldorfQpatParameter ("AmpEnvDelay", String.format (FORMAT_SECONDS, Double.valueOf (delayTime)), (float) (convertFromDelayTime (delayTime))));

        // AmpEnvAttack
        final double attackTime = MathUtils.clamp (envelope.getAttackTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("AmpEnvAttack", String.format (FORMAT_SECONDS, Double.valueOf (attackTime)), (float) (convertFromTime (attackTime))));
        // AmpEnvDecay
        final double decayTime = MathUtils.clamp (envelope.getDecayTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("AmpEnvDecay", String.format (FORMAT_SECONDS, Double.valueOf (decayTime)), (float) (convertFromTime (decayTime))));
        // AmpEnvRelease
        final double releaseTime = MathUtils.clamp (envelope.getReleaseTime (), 0, 60);
        parameters.add (new WaldorfQpatParameter ("AmpEnvRelease", String.format (FORMAT_SECONDS, Double.valueOf (releaseTime)), (float) (convertFromTime (releaseTime))));

        // AmpEnvSustain
        final double sustainLevel = envelope.getSustainLevel ();
        parameters.add (new WaldorfQpatParameter ("AmpEnvSustain", String.format ("%.2f", Double.valueOf (sustainLevel * 100.0)) + " %", (float) (sustainLevel)));

        // AmpAttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
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
        parameters.add (new WaldorfQpatParameter ("AmpAttackCurve", attackSlopeStr, (float) attackSlopeValue));

        // AmpDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final double decaySlope = envelope.getDecaySlope ();
        String decaySlopeStr = SLOPE_LINEAR;
        double decaySlopeValue = 2;
        if (decaySlope > 0.5)
        {
            decaySlopeStr = SLOPE_EXP;
            decaySlopeValue = 0;
        }
        else if (decaySlope > 0)
        {
            decaySlopeStr = SLOPE_EXP_ALT;
            decaySlopeValue = 0.5;
        }
        parameters.add (new WaldorfQpatParameter ("AmpDecayCurve", decaySlopeStr, (float) decaySlopeValue));

        // AmpReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
        final double releaseSlope = envelope.getReleaseSlope ();
        String releaseSlopeStr = SLOPE_LINEAR;
        double releaseSlopeValue = 2;
        if (releaseSlope > 0.5)
        {
            releaseSlopeStr = SLOPE_EXP;
            releaseSlopeValue = 0;
        }
        else if (releaseSlope > 0)
        {
            releaseSlopeStr = SLOPE_EXP_ALT;
            releaseSlopeValue = 0.5;
        }
        parameters.add (new WaldorfQpatParameter ("AmpReleaseCurve", releaseSlopeStr, (float) releaseSlopeValue));
    }


    /**
     * Writes the header information preceding the actual data.
     *
     * @param out The output stream to write to
     * @param multisampleSource The multi-sample source
     * @throws IOException Could not write
     */
    private static void writeHeader (final OutputStream out, final IMultisampleSource multisampleSource) throws IOException
    {
        final IMetadata metadata = multisampleSource.getMetadata ();

        StreamUtils.writeUnsigned32 (out, WaldorfQpatConstants.MAGIC, false);
        StreamUtils.writeUnsigned32 (out, PRESET_VERSION, false);
        StreamUtils.writeASCII (out, StringUtils.fixASCII (multisampleSource.getName ()), WaldorfQpatConstants.MAX_STRING_LENGTH);
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
        return MathUtils.clamp (Math.pow (10, db / 40), 0, 1);
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