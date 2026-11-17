// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Ableton ADV files. Such a file is a renamed GZIP file with the ending "adv" and
 * contains a metadata description file in XML format.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonCreator extends AbstractWavCreator<AbletonCreatorUI>
{
    private static final String                  TRUE            = "true";
    private static final String                  FALSE           = "false";

    private static final String                  TEMPLATE_FOLDER = "de/mossgrabers/convertwithmoss/templates/adv/";
    private static final String                  EMPTY_SLOT      = "\t\t\t\t<Value />";

    private static final Map<FilterType, String> FILTER_TYPES    = new EnumMap<> (FilterType.class);
    static
    {
        FILTER_TYPES.put (FilterType.LOW_PASS, "0");
        FILTER_TYPES.put (FilterType.HIGH_PASS, "1");
        FILTER_TYPES.put (FilterType.BAND_PASS, "2");
        FILTER_TYPES.put (FilterType.BAND_REJECTION, "3");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AbletonCreator (final INotifier notifier)
    {
        super ("Ableton Sampler", "Ableton", notifier, new AbletonCreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String safeFileName = SafeFileNames.create (multisampleSource.getName ());

        final File sampleFolder = new File (destinationFolder, "Samples/Imported/" + safeFileName);
        if (!sampleFolder.exists () && !sampleFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", sampleFolder.getAbsolutePath ());
            return;
        }

        final File multiSampleFolder = new File (destinationFolder, "Presets/Instruments/Sampler/");
        if (!multiSampleFolder.exists () && !multiSampleFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", multiSampleFolder.getAbsolutePath ());
            return;
        }

        final File multiFile = this.createUniqueFilename (multiSampleFolder, SafeFileNames.create (multisampleSource.getName ()), "adv");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        final Map<String, File> writtenSamples = new HashMap<> ();
        for (final File sampleFile: this.writeSamples (sampleFolder, multisampleSource))
            writtenSamples.put (sampleFile.getName (), sampleFile);

        final Optional<String> xmlCode = this.createXMLDocument (multiFile.getName (), multisampleSource, writtenSamples);
        if (xmlCode.isEmpty ())
            return;

        try (final GZIPOutputStream gos = new GZIPOutputStream (new FileOutputStream (multiFile)))
        {
            gos.write (xmlCode.get ().getBytes (StandardCharsets.UTF_8));
        }

        this.progress.notifyDone ();
    }


    /**
     * Create the text of the description file.
     *
     * @param filename The name of the ADV file
     * @param multisampleSource The multi-sample
     * @param writtenSamples The already stored samples
     * @return The XML structure
     */
    private Optional<String> createXMLDocument (final String filename, final IMultisampleSource multisampleSource, final Map<String, File> writtenSamples)
    {
        try
        {
            final boolean isVersion12 = this.settingsConfiguration.getAbletonVersion () == 12;
            final boolean nativeRoundRobin = isVersion12 && multisampleSource.hasRoundRobin () && multisampleSource.isFullRoundRobin ();
            // Without the native round-robin flag the cycles are spread across the sample-select
            // (selector) axis, otherwise all cycles would play at the same time
            final boolean selectorRoundRobin = !nativeRoundRobin && multisampleSource.hasRoundRobin ();
            if (selectorRoundRobin)
                this.notifier.log ("IDS_ADV_ROUND_ROBIN_VIA_SELECTOR");

            // Add all groups
            final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
            final String multiSamplePartTemplate = Functions.textFileFor (TEMPLATE_FOLDER + "ADV-MultiSamplePart-Template.xml");
            final String multisampleParts = addGroups (groups, multiSamplePartTemplate, SafeFileNames.create (multisampleSource.getName ()), writtenSamples, selectorRoundRobin);

            String text = Functions.textFileFor (TEMPLATE_FOLDER + (isVersion12 ? "ADV12-Template.xml" : "ADV11-Template.xml"));

            int pitchBend = 2;
            if (!groups.isEmpty ())
            {
                final List<ISampleZone> zones = groups.get (0).getSampleZones ();
                if (!zones.isEmpty ())
                {
                    final ISampleZone zone = zones.get (0);
                    pitchBend = zone.getBendUp ();

                    // Amplitude envelope
                    final IEnvelopeModulator ampModulator = zone.getAmplitudeEnvelopeModulator ();
                    final IEnvelope ampEnvelope = ampModulator.getSource ();
                    text = text.replace ("%AMP_EG_ATTACK_TIME%", formatEnvTime (ampEnvelope.getAttackTime ()));
                    text = text.replace ("%AMP_EG_DECAY_TIME%", formatEnvTime (Math.max (0, ampEnvelope.getHoldTime ()) + Math.max (0, ampEnvelope.getDecayTime ())));
                    text = text.replace ("%AMP_EG_RELEASE_TIME%", formatEnvTime (ampEnvelope.getReleaseTime ()));

                    text = text.replace ("%AMP_EG_START_LEVEL%", formatEnvVolume (ampEnvelope.getStartLevel (), 0));
                    text = text.replace ("%AMP_EG_HOLD_LEVEL%", formatEnvVolume (ampEnvelope.getHoldLevel (), 1));
                    text = text.replace ("%AMP_EG_SUSTAIN_LEVEL%", formatEnvVolume (ampEnvelope.getSustainLevel (), 1));
                    text = text.replace ("%AMP_EG_END_LEVEL%", formatEnvVolume (ampEnvelope.getEndLevel (), 0));

                    text = text.replace ("%AMP_EG_ATTACK_SLOPE%", formatDouble (-ampEnvelope.getAttackSlope ()));
                    text = text.replace ("%AMP_EG_DECAY_SLOPE%", formatDouble (-ampEnvelope.getDecaySlope ()));
                    text = text.replace ("%AMP_EG_RELEASE_SLOPE%", formatDouble (-ampEnvelope.getReleaseSlope ()));

                    // Pitch Envelope
                    final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                    final double pitchModDepth = pitchModulator.getDepth ();
                    text = text.replace ("%PITCH_EG_ENABLED%", pitchModDepth == 0 ? FALSE : TRUE);

                    final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                    text = text.replace ("%PITCH_EG_AMOUNT%", Integer.toString ((int) Math.round (pitchModDepth * 100)));

                    text = text.replace ("%PITCH_EG_ATTACK_TIME%", formatEnvTime (pitchEnvelope.getAttackTime ()));
                    text = text.replace ("%PITCH_EG_DECAY_TIME%", formatEnvTime (Math.max (0, pitchEnvelope.getHoldTime ()) + Math.max (0, pitchEnvelope.getDecayTime ())));
                    text = text.replace ("%PITCH_EG_RELEASE_TIME%", formatEnvTime (pitchEnvelope.getReleaseTime ()));

                    text = text.replace ("%PITCH_EG_START_LEVEL%", formatEnvVolume (pitchEnvelope.getStartLevel (), 0));
                    text = text.replace ("%PITCH_EG_HOLD_LEVEL%", formatEnvVolume (pitchEnvelope.getHoldLevel (), 1));
                    text = text.replace ("%PITCH_EG_SUSTAIN_LEVEL%", formatEnvVolume (pitchEnvelope.getSustainLevel (), 1));
                    text = text.replace ("%PITCH_EG_END_LEVEL%", formatEnvVolume (pitchEnvelope.getEndLevel (), 0));

                    text = text.replace ("%PITCH_EG_ATTACK_SLOPE%", formatDouble (-pitchEnvelope.getAttackSlope ()));
                    text = text.replace ("%PITCH_EG_DECAY_SLOPE%", formatDouble (-pitchEnvelope.getDecaySlope ()));
                    text = text.replace ("%PITCH_EG_RELEASE_SLOPE%", formatDouble (-pitchEnvelope.getReleaseSlope ()));

                    // Velocity modulation
                    final Optional<Double> globalAmplitudeVelocity = multisampleSource.getGlobalAmplitudeVelocity ();
                    double depth = 1;
                    if (globalAmplitudeVelocity.isPresent ())
                        depth = globalAmplitudeVelocity.get ().doubleValue ();
                    text = text.replace ("%VOLUME_VELOCITY_MOD%", formatDouble (depth));
                }
            }

            text = text.replace ("%PRESET_NAME%", XMLUtils.escapeAttribute (multisampleSource.getName ().replace ('.', '_')));
            text = text.replace ("%FILE_NAME%", filename);
            text = text.replace ("%MULTI_SAMPLE_PARTS%", multisampleParts);
            if (isVersion12)
            {
                text = text.replace ("%ROUND_ROBIN%", nativeRoundRobin ? TRUE : FALSE);
                text = text.replace ("%ROUND_ROBIN_MODE%", Integer.toString (getRoundRobinMode (multisampleSource)));
            }
            text = text.replace ("%PITCHBEND_RANGE%", Integer.toString (pitchBend));
            text = addFilter (multisampleSource.getGlobalFilter (), text);
            text = addLfos (text, groups, multisampleSource.getGlobalFilter ());
            return Optional.of (text);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
        }

        return Optional.empty ();
    }


    /**
     * Add the low frequency oscillators. Since each Sampler oscillator has its own waveform and
     * rate, the volume modulation is written to LFO 1 (fixed volume amount), the pitch modulation to
     * LFO 2 and the filter cutoff modulation to LFO 3 (both via destination A). The routing amount
     * is set to the maximum depth (modulation wheel fully up), the modulation wheel amount is the
     * share of it which is controlled by the wheel. The modulation wheel can only have 2 routings,
     * therefore the 2 strongest modulation wheel depths are used. Without any modulation wheel
     * depth, the default routing of the modulation wheel to the filter cutoff is kept.
     *
     * @param sourceTemplate The template text
     * @param groups The groups
     * @param globalFilter The global filter, if any
     * @return The text with the replaced placeholders
     * @throws IOException Could not read the oscillator templates
     */
    private static String addLfos (final String sourceTemplate, final List<IGroup> groups, final Optional<IFilter> globalFilter) throws IOException
    {
        ILfoModulator volumeModulator = null;
        ILfoModulator pitchModulator = null;
        ILfoModulator cutoffModulator = null;
        if (!groups.isEmpty () && !groups.get (0).getSampleZones ().isEmpty ())
        {
            final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
            if (zone.getAmplitudeLfoModulator ().isActive ())
                volumeModulator = zone.getAmplitudeLfoModulator ();
            if (zone.getPitchLfoModulator ().isActive ())
                pitchModulator = zone.getPitchLfoModulator ();
        }
        if (globalFilter.isPresent () && globalFilter.get ().getCutoffLfoModulator ().isActive ())
            cutoffModulator = globalFilter.get ().getCutoffLfoModulator ();

        final String lfoTemplate = Functions.textFileFor (TEMPLATE_FOLDER + "ADV-Lfo-Template.xml").stripTrailing ();
        final String auxLfoTemplate = Functions.textFileFor (TEMPLATE_FOLDER + "ADV-AuxLfo-Template.xml").stripTrailing ();

        // LFO 1: volume
        String text = sourceTemplate.replace ("%LFO1_ENABLED%", volumeModulator == null ? FALSE : TRUE);
        text = text.replace ("%LFO1_SLOT%", volumeModulator == null ? EMPTY_SLOT : createLfoSlot (lfoTemplate, volumeModulator.getSource (), 0, 0));
        final double volumeAmount = volumeModulator == null ? 0 : Math.clamp (Math.abs (getMaximumDepth (volumeModulator)) / AbletonLfo.VOLUME_SCALE, 0, 1);
        text = text.replace ("%LFO1_VOLUME%", formatDouble (volumeAmount));
        text = text.replace ("%LFO1_PITCH%", "0");
        text = text.replace ("%LFO1_FILTER%", "0");

        // LFO 2: pitch
        text = text.replace ("%LFO2_ENABLED%", pitchModulator == null ? FALSE : TRUE);
        text = text.replace ("%LFO2_SLOT%", pitchModulator == null ? EMPTY_SLOT : createLfoSlot (auxLfoTemplate, pitchModulator.getSource (), AbletonLfo.DESTINATION_PITCH, toAmount (getMaximumDepth (pitchModulator), AbletonLfo.PITCH_SCALE)));

        // LFO 3: filter cutoff
        text = text.replace ("%LFO3_ENABLED%", cutoffModulator == null ? FALSE : TRUE);
        text = text.replace ("%LFO3_SLOT%", cutoffModulator == null ? EMPTY_SLOT : createLfoSlot (auxLfoTemplate, cutoffModulator.getSource (), AbletonLfo.DESTINATION_FILTER_FREQUENCY, toAmount (getMaximumDepth (cutoffModulator), AbletonLfo.CUTOFF_SCALE)));

        // Modulation wheel: destination and amount of the 2 strongest routings
        final List<double []> modWheelRoutings = new ArrayList<> ();
        if (volumeModulator != null)
            modWheelRoutings.add (new double []
            {
                AbletonLfo.DESTINATION_LFO1_VOLUME,
                getModWheelShare (volumeModulator)
            });
        if (pitchModulator != null)
            modWheelRoutings.add (new double []
            {
                AbletonLfo.DESTINATION_LFO2_AMOUNT_A,
                getModWheelShare (pitchModulator)
            });
        if (cutoffModulator != null)
            modWheelRoutings.add (new double []
            {
                AbletonLfo.DESTINATION_LFO3_AMOUNT_A,
                getModWheelShare (cutoffModulator)
            });
        modWheelRoutings.removeIf (routing -> routing[1] == 0);
        modWheelRoutings.sort ((routing1, routing2) -> Double.compare (Math.abs (routing2[1]), Math.abs (routing1[1])));

        if (modWheelRoutings.isEmpty ())
        {
            text = text.replace ("%MOD_WHEEL_DESTINATION_1%", Integer.toString (AbletonLfo.DESTINATION_FILTER_FREQUENCY));
            text = text.replace ("%MOD_WHEEL_AMOUNT_1%", "33.3333321");
        }
        else
        {
            text = text.replace ("%MOD_WHEEL_DESTINATION_1%", Integer.toString ((int) modWheelRoutings.get (0)[0]));
            text = text.replace ("%MOD_WHEEL_AMOUNT_1%", formatDouble (modWheelRoutings.get (0)[1]));
        }
        final boolean hasSecond = modWheelRoutings.size () > 1;
        text = text.replace ("%MOD_WHEEL_DESTINATION_2%", hasSecond ? Integer.toString ((int) modWheelRoutings.get (1)[0]) : "0");
        return text.replace ("%MOD_WHEEL_AMOUNT_2%", hasSecond ? formatDouble (modWheelRoutings.get (1)[1]) : "0");
    }


    /**
     * Fill the template of an oscillator.
     *
     * @param template The oscillator template
     * @param lfo The oscillator
     * @param destination The modulation destination of LFO 2 and 3
     * @param amount The modulation amount of LFO 2 and 3 in percent
     * @return The filled template
     */
    private static String createLfoSlot (final String template, final ILfo lfo, final int destination, final double amount)
    {
        final double rate = lfo.getRate () > 0 ? lfo.getRate () : 1;
        String text = template.replace ("%LFO_TYPE%", Integer.toString (AbletonLfo.fromWaveform (lfo.getWaveform ())));
        text = text.replace ("%LFO_FREQUENCY%", formatDouble (Math.clamp (rate, 0.01, 30.0)));
        text = text.replace ("%LFO_RETRIGGER%", lfo.isKeySync () ? TRUE : FALSE);
        text = text.replace ("%LFO_DESTINATION%", Integer.toString (destination));
        return text.replace ("%LFO_AMOUNT%", formatDouble (amount));
    }


    /**
     * Get the maximum depth of a modulator which is reached with the modulation wheel fully up.
     *
     * @param modulator The modulator
     * @return The depth
     */
    private static double getMaximumDepth (final ILfoModulator modulator)
    {
        return modulator.getDepth () + modulator.getDepthModWheelModulator ().getDepth ();
    }


    /**
     * Get the share of the maximum depth of a modulator which is controlled by the modulation wheel.
     *
     * @param modulator The modulator
     * @return The share in percent in the range of [0..100]
     */
    private static double getModWheelShare (final ILfoModulator modulator)
    {
        final double maximumDepth = getMaximumDepth (modulator);
        if (maximumDepth == 0)
            return 0;
        return Math.clamp (modulator.getDepthModWheelModulator ().getDepth () / maximumDepth * 100.0, 0, 100.0);
    }


    /**
     * Convert a modulation depth to a modulation amount in percent.
     *
     * @param depth The depth
     * @param scale The scaling factor of the destination (depth of an amount of 100%)
     * @return The amount in the range of [-100..100]
     */
    private static double toAmount (final double depth, final double scale)
    {
        return Math.clamp (depth / scale * 100.0, -100.0, 100.0);
    }


    private static String addFilter (final Optional<IFilter> globalFilter, final String sourceTemplate)
    {
        final boolean isFilterOn = globalFilter.isPresent ();
        String text = sourceTemplate.replace ("%FILTER_ENABLED%", Boolean.toString (isFilterOn));
        final IFilter filter = isFilterOn ? globalFilter.get () : new DefaultFilter (FilterType.LOW_PASS, 4, IFilter.MAX_FREQUENCY, 0);

        final String filterType = FILTER_TYPES.get (filter.getType ());
        text = text.replace ("%FILTER_TYPE%", filterType == null ? "0" : filterType);
        text = text.replace ("%FILTER_SLOPE%", filter.getPoles () < 3 ? FALSE : TRUE);
        text = text.replace ("%FILTER_FREQ%", formatDouble (filter.getCutoff ()));
        text = text.replace ("%FILTER_RES%", formatDouble (filter.getResonance () * 1.25));

        // Filter envelope
        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double filterModDepth = cutoffModulator.getDepth ();
        text = text.replace ("%FILTER_EG_ENABLED%", filterModDepth == 0 ? FALSE : TRUE);
        // The amount is in semitones (+-72), the depth of the model covers MAX_ENVELOPE_DEPTH cent
        text = text.replace ("%FILTER_EG_AMOUNT%", Integer.toString ((int) Math.round (Math.clamp (filterModDepth * IEnvelope.MAX_ENVELOPE_DEPTH / 100.0, -72, 72))));

        final IEnvelope filterEnvelope = cutoffModulator.getSource ();

        text = text.replace ("%FILTER_EG_ATTACK_TIME%", formatEnvTime (filterEnvelope.getAttackTime ()));
        text = text.replace ("%FILTER_EG_DECAY_TIME%", formatEnvTime (Math.max (0, filterEnvelope.getHoldTime ()) + Math.max (0, filterEnvelope.getDecayTime ())));
        text = text.replace ("%FILTER_EG_RELEASE_TIME%", formatEnvTime (filterEnvelope.getReleaseTime ()));

        text = text.replace ("%FILTER_EG_START_LEVEL%", formatEnvVolume (filterEnvelope.getStartLevel (), 0));
        text = text.replace ("%FILTER_EG_HOLD_LEVEL%", formatEnvVolume (filterEnvelope.getHoldLevel (), 1));
        text = text.replace ("%FILTER_EG_SUSTAIN_LEVEL%", formatEnvVolume (filterEnvelope.getSustainLevel (), 1));
        text = text.replace ("%FILTER_EG_END_LEVEL%", formatEnvVolume (filterEnvelope.getEndLevel (), 0));

        text = text.replace ("%FILTER_EG_ATTACK_SLOPE%", formatDouble (-filterEnvelope.getAttackSlope ()));
        text = text.replace ("%FILTER_EG_DECAY_SLOPE%", formatDouble (-filterEnvelope.getDecaySlope ()));
        text = text.replace ("%FILTER_EG_RELEASE_SLOPE%", formatDouble (-filterEnvelope.getReleaseSlope ()));

        // Filter cutoff velocity modulation
        final double depth = filter.getCutoffVelocityModulator ().getDepth ();
        text = text.replace ("%FILTER_VELOCITY_MOD%", formatDouble (depth));

        // Filter cutoff key modulation
        final double keyModDepth = filter.getCutoffKeyTracking ();
        text = text.replace ("%FILTER_KEY_MOD%", formatDouble (keyModDepth));

        return text;
    }


    private static String addGroups (final List<IGroup> groups, final String multiSamplePartTemplate, final String sampleSubFolder, final Map<String, File> writtenSamples, final boolean selectorRoundRobin) throws IOException
    {
        final Map<ISampleZone, int []> selectorRanges = selectorRoundRobin ? calculateSelectorRanges (groups) : Collections.emptyMap ();

        int zoneCount = 0;
        final StringBuilder result = new StringBuilder ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (zoneCount > 0)
                    result.append ('\n');
                result.append (addZoneData (zone, zoneCount, multiSamplePartTemplate, sampleSubFolder, writtenSamples, selectorRanges.get (zone)));
                zoneCount++;
            }
        return result.toString ();
    }


    /**
     * Calculate the sample-select (selector) range of each alternating (round-robin or random)
     * zone. The cycles are spread evenly across the 0-127 selector axis, therefore only the zones
     * of the first cycle are heard at the default selector position and the other cycles can be
     * reached by modulating the sample selector. Zones which always play keep the full range.
     *
     * @param groups The groups with the zones
     * @return The selector range (low, high) of each alternating zone
     */
    private static Map<ISampleZone, int []> calculateSelectorRanges (final List<IGroup> groups)
    {
        // Collect the distinct cycle positions of all alternating zones
        final TreeSet<Integer> positions = new TreeSet<> ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getPlayLogic () != PlayLogic.ALWAYS)
                    positions.add (Integer.valueOf (Math.max (1, zone.getSequencePosition ())));

        final Map<ISampleZone, int []> selectorRanges = new HashMap<> ();
        if (positions.size () > 1)
        {
            final List<Integer> orderedPositions = new ArrayList<> (positions);
            for (final IGroup group: groups)
                for (final ISampleZone zone: group.getSampleZones ())
                    if (zone.getPlayLogic () != PlayLogic.ALWAYS)
                        selectorRanges.put (zone, calculateSelectorSlice (orderedPositions.indexOf (Integer.valueOf (Math.max (1, zone.getSequencePosition ()))), orderedPositions.size ()));
            return selectorRanges;
        }

        // Without sequence positions each group which contains alternating zones is one cycle
        final List<IGroup> cycleGroups = new ArrayList<> ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getPlayLogic () != PlayLogic.ALWAYS)
                {
                    cycleGroups.add (group);
                    break;
                }
        final int count = cycleGroups.size ();
        if (count > 1)
            for (int i = 0; i < count; i++)
                for (final ISampleZone zone: cycleGroups.get (i).getSampleZones ())
                    if (zone.getPlayLogic () != PlayLogic.ALWAYS)
                        selectorRanges.put (zone, calculateSelectorSlice (i, count));
        return selectorRanges;
    }


    private static int [] calculateSelectorSlice (final int index, final int count)
    {
        return new int []
        {
            index * 128 / count,
            (index + 1) * 128 / count - 1
        };
    }


    private static String addZoneData (final ISampleZone zone, final int zoneCount, final String multiSamplePartTemplate, final String sampleSubFolder, final Map<String, File> writtenSamples, final int [] selectorRange) throws IOException
    {
        // Must use the same sanitized name which was used for writing the sample file
        final String zoneFileName = SafeFileNames.create (zone.getName ()) + ".wav";

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
        final File sampleFile = writtenSamples.get (zoneFileName);
        if (sampleFile == null)
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", zoneFileName));

        String zoneContent = multiSamplePartTemplate.replace ("%SAMPLE_ID%", Integer.toString (zoneCount));
        zoneContent = zoneContent.replace ("%SAMPLE_FOLDER%", sampleSubFolder);
        zoneContent = zoneContent.replace ("%SAMPLE_FILE%", zoneFileName);
        zoneContent = zoneContent.replace ("%SAMPLE_START%", Integer.toString (zone.getStart ()));
        zoneContent = zoneContent.replace ("%SAMPLE_END%", Integer.toString (zone.getStop ()));

        zoneContent = zoneContent.replace ("%SAMPLE_FILE_SIZE%", Long.toString (sampleFile.length ()));
        zoneContent = zoneContent.replace ("%SAMPLE_FILE_TIMESTAMP%", Long.toString (sampleFile.lastModified () / 1000));
        zoneContent = zoneContent.replace ("%SAMPLE_RATE%", Integer.toString (audioMetadata.getSampleRate ()));
        zoneContent = zoneContent.replace ("%SAMPLE_DURATION%", Integer.toString (audioMetadata.getNumberOfSamples ()));

        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        final int keyHigh = limitToDefault (zone.getKeyHigh (), 127);
        final int velLow = limitToDefault (zone.getVelocityLow (), 1);
        final int velHigh = limitToDefault (zone.getVelocityHigh (), 127);
        zoneContent = zoneContent.replace ("%KEY_RANGE_LOW%", Integer.toString (keyLow));
        zoneContent = zoneContent.replace ("%KEY_RANGE_HIGH%", Integer.toString (keyHigh));
        zoneContent = zoneContent.replace ("%VEL_RANGE_LOW%", Integer.toString (velLow));
        zoneContent = zoneContent.replace ("%VEL_RANGE_HIGH%", Integer.toString (velHigh));

        zoneContent = zoneContent.replace ("%KEY_RANGE_LOW_CROSSFADE%", Integer.toString (Math.max (0, keyLow - zone.getNoteCrossfadeLow ())));
        zoneContent = zoneContent.replace ("%KEY_RANGE_HIGH_CROSSFADE%", Integer.toString (Math.min (127, keyHigh + zone.getNoteCrossfadeHigh ())));
        zoneContent = zoneContent.replace ("%VEL_RANGE_LOW_CROSSFADE%", Integer.toString (Math.max (0, velLow - zone.getVelocityCrossfadeLow ())));
        zoneContent = zoneContent.replace ("%VEL_RANGE_HIGH_CROSSFADE%", Integer.toString (Math.min (127, velHigh + zone.getVelocityCrossfadeHigh ())));

        zoneContent = zoneContent.replace ("%SELECTOR_LOW%", Integer.toString (selectorRange == null ? 0 : selectorRange[0]));
        zoneContent = zoneContent.replace ("%SELECTOR_HIGH%", Integer.toString (selectorRange == null ? 127 : selectorRange[1]));

        final double tune = zone.getTuning ();
        int semitones = (int) Math.round (tune);
        int cents = (int) Math.round ((tune - semitones) * 100);
        if (cents > 50)
        {
            semitones += 1;
            cents -= 100;
        }
        else if (cents < -50)
        {
            semitones -= 1;
            cents += 100;
        }

        zoneContent = zoneContent.replace ("%ROOT_KEY%", Integer.toString (Math.clamp (limitToDefault (zone.getKeyRoot (), keyLow) - (long) semitones, 0, 127)));
        zoneContent = zoneContent.replace ("%DETUNE%", Integer.toString (cents));
        zoneContent = zoneContent.replace ("%TUNE_SCALE%", Integer.toString ((int) Math.round (zone.getKeyTracking () * 100)));
        zoneContent = zoneContent.replace ("%PANORAMA%", formatDouble (zone.getPanning ()));
        zoneContent = zoneContent.replace ("%VOLUME%", formatDouble (Math.pow (2, zone.getGain () / 6.0)));

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            zoneContent = zoneContent.replace ("%LOOP_MODE%", "0");
            zoneContent = zoneContent.replace ("%SAMPLE_RELEASE_LOOP%", "3");
            zoneContent = zoneContent.replace ("%LOOP_START%", "0");
            zoneContent = zoneContent.replace ("%LOOP_END%", "0");
            zoneContent = zoneContent.replace ("%LOOP_CROSSFADE%", "0");
            zoneContent = zoneContent.replace ("%LOOP_DETUNE%", "0");
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            zoneContent = zoneContent.replace ("%LOOP_MODE%", loop.getType () == LoopType.ALTERNATING ? "2" : "1");
            zoneContent = zoneContent.replace ("%SAMPLE_RELEASE_LOOP%", loop.isLoopUntilRelease () ? "0" : "3");
            zoneContent = zoneContent.replace ("%LOOP_START%", Integer.toString (loop.getStart ()));
            zoneContent = zoneContent.replace ("%LOOP_END%", Integer.toString (loop.getEnd ()));
            zoneContent = zoneContent.replace ("%LOOP_CROSSFADE%", Integer.toString (loop.getCrossfadeInSamples ()));
            zoneContent = zoneContent.replace ("%LOOP_DETUNE%", Integer.toString ((int) Math.round (loop.getTuning () * 100.0)));
        }

        return zoneContent;
    }


    /**
     * Get the round-robin mode to store. Ableton selects one of the overlapping zones either by
     * cycling through them or randomly. The mode is a setting of the full multi-sample map,
     * therefore the random mode is already applied if one of the zones requests it.
     *
     * @param multisampleSource The multi-sample source from which to read the play logic
     * @return The Ableton round-robin mode
     */
    private static int getRoundRobinMode (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getPlayLogic () == PlayLogic.RANDOM)
                    return AbletonTag.ROUND_ROBIN_MODE_RANDOM;
        return AbletonTag.ROUND_ROBIN_MODE_FORWARD;
    }


    private static String formatEnvTime (final double time)
    {
        return time < 0 ? "0" : formatDouble (time * 1000.0);
    }


    private static String formatEnvVolume (final double volume, final double defaultValue)
    {
        return formatDouble (volume < 0 ? defaultValue : volume);
    }
}
