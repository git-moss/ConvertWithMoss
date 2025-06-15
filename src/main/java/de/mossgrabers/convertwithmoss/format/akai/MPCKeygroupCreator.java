// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.control.TitledSeparator;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;


/**
 * Creator for Akai MPC keygroup files. Keygroups have a description file and related samples in one
 * folder. A keygroup program offers up to 128 keygroups, and each keygroup can hold up to four
 * samples (Layers 1–4). This is a total of 512 samples.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupCreator extends AbstractCreator
{
    private static final String MPC_LAYER_LIMIT_USE_8 = "MPCLayerLimitUse8";


    private enum SamplePlay
    {
        ONE_SHOT,
        NOTE_OFF,
        NOTE_ON
    }


    private ToggleGroup layerLimitGroup;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupCreator (final INotifier notifier)
    {
        super ("Akai MPC Keygroup", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_MPC_LAYER_LIMIT");

        this.layerLimitGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_MPC_LAYER_LIMIT_4");
        order1.setAccessibleHelp (Functions.getMessage ("IDS_MPC_LAYER_LIMIT"));
        order1.setToggleGroup (this.layerLimitGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_MPC_LAYER_LIMIT_8");
        order2.setAccessibleHelp (Functions.getMessage ("IDS_MPC_LAYER_LIMIT"));
        order2.setToggleGroup (this.layerLimitGroup);

        final TitledSeparator separator = this.addWavChunkOptions (panel);
        separator.getStyleClass ().add ("titled-separator-pane");

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.layerLimitGroup.selectToggle (this.layerLimitGroup.getToggles ().get (config.getBoolean (MPC_LAYER_LIMIT_USE_8, false) ? 1 : 0));

        this.loadWavChunkSettings (config, "MPC");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MPC_LAYER_LIMIT_USE_8, this.getLayerLimit () == 8);

        this.saveWavChunkSettings (config, "MPC");
    }


    /**
     * Get the limit for the number of layers in key-groups.
     *
     * @return 8 or 4
     */
    private int getLayerLimit ()
    {
        return this.layerLimitGroup.getToggles ().get (1).isSelected () ? 8 : 4;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Store all samples and metadata file in one folder
        final File sampleFolder = this.createUniqueFilename (destinationFolder, sampleName, "");
        safeCreateDirectory (sampleFolder);

        // Create the metadata file
        final File multiFile = new File (sampleFolder, sampleName + ".xpm");
        final Optional<String> metadata = this.createMetadata (multisampleSource, sampleName);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata.get ());
        }

        // Store all samples - WAV ending needs to be upper case!
        this.writeSamples (sampleFolder, multisampleSource, ".WAV");

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @param sampleName The name of the multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final IMultisampleSource multisampleSource, final String sampleName)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement (MPCKeygroupTag.ROOT);
        document.appendChild (multisampleElement);
        final Element versionElement = document.createElement (MPCKeygroupTag.ROOT_VERSION);
        XMLUtils.addTextElement (document, versionElement, MPCKeygroupTag.VERSION_FILE_VERSION, MPCKeygroupConstants.FILE_VERSION);

        final Element programElement = createProgramElement (document, multisampleSource);
        multisampleElement.appendChild (programElement);
        final Element instrumentsElement = document.createElement (MPCKeygroupTag.PROGRAM_INSTRUMENTS);
        programElement.appendChild (instrumentsElement);

        final Map<String, List<Keygroup>> keygroupsMap = new HashMap<> ();

        // Need to stack the parts of groups in key ranges
        for (final IGroup group: multisampleSource.getGroups ())
        {
            final TriggerType trigger = group.getTrigger ();

            for (final ISampleZone sampleMetadata: group.getSampleZones ())
            {
                final Optional<Keygroup> keygroupOpt = this.getKeygroup (keygroupsMap, sampleMetadata, document, instrumentsElement, trigger);
                if (keygroupOpt.isEmpty ())
                {
                    this.notifier.logError ("IDS_MPC_MORE_THAN_N_LAYERS", Integer.toString (this.getLayerLimit ()), Integer.toString (sampleMetadata.getKeyLow ()), Integer.toString (sampleMetadata.getKeyHigh ()), Integer.toString (sampleMetadata.getVelocityLow ()), Integer.toString (sampleMetadata.getVelocityHigh ()));
                    continue;
                }

                final Keygroup keygroup = keygroupOpt.get ();
                keygroup.addLayer (createLayerElement (document, keygroup.getLayerCount (), sampleMetadata, sampleName));
            }
        }

        final int size = calcInstrumentNumber (keygroupsMap) - 1;
        if (size > 128)
            this.notifier.logError ("IDS_MPC_MORE_THAN_128_KEYGROUPS", Integer.toString (size));
        XMLUtils.addTextElement (document, programElement, MPCKeygroupTag.PROGRAM_NUM_KEYGROUPS, Integer.toString (size));

        return this.createXMLString (document);
    }


    /**
     * Creates a program element.
     *
     * @param document The XML document
     * @param multisampleSource The multi-sample source
     * @return The created element
     */
    private static Element createProgramElement (final Document document, final IMultisampleSource multisampleSource)
    {
        final Element programElement = document.createElement (MPCKeygroupTag.ROOT_PROGRAM);
        programElement.setAttribute (MPCKeygroupTag.PROGRAM_TYPE, MPCKeygroupTag.TYPE_KEYGROUP);
        XMLUtils.addTextElement (document, programElement, MPCKeygroupTag.PROGRAM_NAME, multisampleSource.getName ());
        programElement.appendChild (document.createElement (MPCKeygroupTag.PROGRAM_PADS + MPCKeygroupConstants.APP_VERSION));

        // Pitchbend 2 semitones up/down
        final List<IGroup> layers = multisampleSource.getNonEmptyGroups (false);
        if (!layers.isEmpty ())
        {
            final int bendUp = Math.abs (layers.get (0).getSampleZones ().get (0).getBendUp ());
            final double bendUpValue = bendUp == 0 ? 0.16 : bendUp / 1200.0;
            XMLUtils.addTextElement (document, programElement, MPCKeygroupTag.PROGRAM_PITCHBEND_RANGE, formatDouble (bendUpValue, 3));
        }

        // Vibrato on Modulation Wheel
        XMLUtils.addTextElement (document, programElement, MPCKeygroupTag.PROGRAM_WHEEL_TO_LFO, "1.000000");
        return programElement;
    }


    /**
     * Creates a layer element.
     *
     * @param document The XML document
     * @param layerIndex The index of the layer
     * @param zone The sample metadata
     * @param sampleName The name of the sample
     * @return The created layer
     */
    private static Element createLayerElement (final Document document, final int layerIndex, final ISampleZone zone, final String sampleName)
    {
        final Element layerElement = document.createElement ("Layer");
        layerElement.setAttribute ("number", Integer.toString (layerIndex + 1));

        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_ACTIVE, MPCKeygroupTag.TRUE);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VOLUME, Double.toString (convertGain (zone.getGain ())));

        final double pan = (Math.clamp (zone.getPanning (), -1.0d, 1.0d) + 1.0d) / 2.0d;
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PAN, String.format (Locale.US, "%.6f", Double.valueOf (pan)));

        final double tuneCent = zone.getTune ();
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PITCH, Double.toString (tuneCent));
        // Values need to be identical to the pitch element!
        final int tuneCentInteger = (int) tuneCent;
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_COARSE_TUNE, Integer.toString (tuneCentInteger));
        // First multiply with 100 to prevent rounding error!
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_FINE_TUNE, Integer.toString ((int) (tuneCent * 100.0 - tuneCentInteger * 100.0)));

        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VEL_START, Integer.toString (limitToDefault (zone.getVelocityLow (), 1)));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VEL_END, Integer.toString (limitToDefault (zone.getVelocityHigh (), 127)));

        // Add the name of the multi-sample to the wave file to make it 'more unique' if
        // necessary
        String zoneName = zone.getName ();
        if (!zoneName.startsWith (sampleName))
        {
            zoneName = sampleName + "_" + zoneName;
            zone.setName (zoneName);
        }

        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_START, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_END, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_START, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_END, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_CROSSFADE, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_TUNE, "0");
        // The root note is strangely one more then the lower upper keys!
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_ROOT_NOTE, Integer.toString (limitToDefault (zone.getKeyRoot (), limitToDefault (zone.getKeyLow (), 0)) + 1));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_KEY_TRACK, MPCKeygroupTag.TRUE);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_NAME, zoneName);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PITCH_RANDOM, MPCKeygroupConstants.DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VOLUME_RANDOM, MPCKeygroupConstants.DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PAN_RANDOM, MPCKeygroupConstants.DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_OFFSET_RANDOM, MPCKeygroupConstants.DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_FILE, "");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_INDEX, "129");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_DIRECTION, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_OFFSET, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_START, Integer.toString (zone.getStart ()));

        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_END, Integer.toString (zone.getStop ()));
            XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, "0");
            return layerElement;
        }

        // Format can store only 1 loop
        final ISampleLoop sampleLoop = loops.get (0);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_START, Integer.toString (sampleLoop.getStart ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_END, Integer.toString (sampleLoop.getEnd ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, zone.isReversed () ? "3" : "1");

        final int loopCrossfade = (int) Math.round (sampleLoop.getCrossfade () * sampleLoop.getLength ());
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_CROSSFADE, Integer.toString (loopCrossfade));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_TAIL_POSITION, "0.500000");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_TAIL_LENGTH, MPCKeygroupConstants.DOUBLE_ZERO);

        return layerElement;
    }


    // LFO for vibrato on Modulation Wheel
    private static Element createLfoElement (final Document document)
    {
        final Element lfoElement = document.createElement ("LFO");
        lfoElement.setAttribute ("LfoNum", "0");
        XMLUtils.addTextElement (document, lfoElement, "Type", "Sine");
        XMLUtils.addTextElement (document, lfoElement, "Rate", "0.700000");
        XMLUtils.addTextElement (document, lfoElement, "LfoPitch", "0.044000");
        return lfoElement;
    }


    private Optional<Keygroup> getKeygroup (final Map<String, List<Keygroup>> keygroupsMap, final ISampleZone zone, final Document document, final Element instrumentsElement, final TriggerType trigger)
    {
        final int keyLow = limitToDefault (zone.getKeyLow (), 0);
        final int keyHigh = limitToDefault (zone.getKeyHigh (), 127);
        final String rangeKey = keyLow + "-" + keyHigh;
        final boolean isSequence = zone.getPlayLogic () == PlayLogic.ROUND_ROBIN;
        final List<Keygroup> keygroups = keygroupsMap.computeIfAbsent (rangeKey, _ -> new ArrayList<> ());

        final int layerLimit = this.getLayerLimit ();

        // Check if a key-group exists to which the layer can be added
        for (final Keygroup keygroup: keygroups)
            // Look for velocity or sequence key-groups (type must match)
            if (keygroup.isSequence () == isSequence)
            {
                // Velocity range must match as well for sequences
                if (keygroup.isSequence () && isSequence && (limitToDefault (zone.getVelocityLow (), 1) != limitToDefault (keygroup.getVelocityLow (), 1) || limitToDefault (zone.getVelocityHigh (), 127) != limitToDefault (keygroup.getVelocityHigh (), 127)))
                    continue;

                // Matching key-group with free layer found
                if (keygroup.getLayerCount () < layerLimit)
                    return Optional.of (keygroup);

                // Can only have 1 sequence key-group since round robin is per key-group
                if (isSequence)
                    return Optional.empty ();
            }

        // No existing key-group found, create a new one (Instrument is a key-group)

        final Element instrumentElement = document.createElement ("Instrument");
        instrumentElement.setAttribute ("number", Integer.toString (calcInstrumentNumber (keygroupsMap)));
        instrumentsElement.appendChild (instrumentElement);

        /////////////////////////////////////////////////////////////
        // Filter

        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_TYPE, Integer.toString (MPCFilter.getFilterIndex (filter)));
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_CUTOFF, formatDouble (MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY), 2));
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RESONANCE, formatDouble (filter.getResonance (), 2));

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            final double envelopeDepth = cutoffModulator.getDepth ();
            // Only positive modulation values are supported with MPC
            if (envelopeDepth > 0)
            {
                XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ENV_AMOUNT, formatDouble (envelopeDepth, 2));

                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK, filterEnvelope.getAttackTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_ATTACK_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_HOLD, filterEnvelope.getHoldTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_HOLD_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY, filterEnvelope.getDecayTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_DECAY_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_SUSTAIN, filterEnvelope.getSustainLevel (), 0, 1, 1);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE, filterEnvelope.getReleaseTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_RELEASE_TIME, true);
                setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK_CURVE, filterEnvelope.getAttackSlope ());
                setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY_CURVE, filterEnvelope.getDecaySlope ());
                setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE_CURVE, filterEnvelope.getReleaseSlope ());
            }

            final double filterCutoffVelocityAmount = filter.getCutoffVelocityModulator ().getDepth ();
            if (filterCutoffVelocityAmount > 0)
                XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VELOCITY_TO_FILTER_AMOUNT, formatDouble (filterCutoffVelocityAmount, 2));
        }

        /////////////////////////////////////////////////////////////
        // Range

        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_LOW_NOTE, Integer.toString (keyLow));
        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_HIGH_NOTE, Integer.toString (keyHigh));
        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_IGNORE_BASE_NOTE, zone.getKeyTracking () == 0 ? "True" : "False");

        /////////////////////////////////////////////////////////////
        // Amplitude

        final double ampVelocityAmount = zone.getAmplitudeVelocityModulator ().getDepth ();
        if (ampVelocityAmount > 0)
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VELOCITY_TO_AMP_AMOUNT, formatDouble (ampVelocityAmount, 2));

        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK, amplitudeEnvelope.getAttackTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_ATTACK_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_HOLD, amplitudeEnvelope.getHoldTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_HOLD_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY, amplitudeEnvelope.getDecayTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_DECAY_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_SUSTAIN, amplitudeEnvelope.getSustainLevel (), 0, 1, 1);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE, amplitudeEnvelope.getReleaseTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_RELEASE_TIME, true);
        setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK_CURVE, amplitudeEnvelope.getAttackSlope ());
        setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY_CURVE, amplitudeEnvelope.getDecaySlope ());
        setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE_CURVE, amplitudeEnvelope.getReleaseSlope ());

        /////////////////////////////////////////////////////////////
        // Pitch

        final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
        final double pitchDepth = pitchModulator.getDepth ();
        // Only positive modulation values are supported with MPC
        if (pitchDepth > 0)
        {
            final double mpcPitchDepth = pitchDepth / 2.0 + 0.5;
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ENV_AMOUNT, formatDouble (mpcPitchDepth, 2));

            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK, pitchEnvelope.getAttackTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_ATTACK_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_HOLD, pitchEnvelope.getHoldTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_HOLD_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY, pitchEnvelope.getDecayTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_DECAY_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_SUSTAIN, pitchEnvelope.getSustainLevel (), 0, 1, 1);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE, pitchEnvelope.getReleaseTime (), MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, MPCKeygroupConstants.DEFAULT_RELEASE_TIME, true);
            setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK_CURVE, pitchEnvelope.getAttackSlope ());
            setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY_CURVE, pitchEnvelope.getDecaySlope ());
            setEnvelopeCurveAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE_CURVE, pitchEnvelope.getReleaseSlope ());
        }

        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_ZONE_PLAY, ZonePlay.from (zone.getPlayLogic ()).getID ());

        SamplePlay triggerMode = SamplePlay.NOTE_ON;

        if (trigger == TriggerType.RELEASE)
            triggerMode = SamplePlay.NOTE_OFF;
        else if (amplitudeEnvelope.getSustainLevel () <= 0 && limitToDefault (zone.getKeyLow (), 0) == limitToDefault (zone.getKeyHigh (), 127))
            triggerMode = SamplePlay.ONE_SHOT;

        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_TRIGGER_MODE, Integer.toString (triggerMode.ordinal ()));

        instrumentElement.appendChild (createLfoElement (document));
        final Element layersElement = document.createElement ("Layers");
        instrumentElement.appendChild (layersElement);

        final Keygroup keygroup;
        if (isSequence)
            keygroup = new Keygroup (layersElement, limitToDefault (zone.getVelocityLow (), 1), limitToDefault (zone.getVelocityHigh (), 127));
        else
            keygroup = new Keygroup (layersElement);
        keygroups.add (keygroup);
        return Optional.of (keygroup);
    }


    private static int calcInstrumentNumber (final Map<String, List<Keygroup>> keygroupsMap)
    {
        int count = 1;
        for (final List<Keygroup> keygroups: keygroupsMap.values ())
            count += keygroups.size ();
        return count;
    }


    /**
     * Convert a volume in the range of [-12dB..12dB] to a range of [0..1] which represent
     * [-Inf..6dB].
     *
     * @param volumeDB The volume to convert
     * @return The converted volume
     */
    private static double convertGain (final double volumeDB)
    {
        final double v = 12 + (volumeDB > 6 ? 6 : volumeDB);
        final double result = MPCKeygroupConstants.VALUE_RANGE * v / 18.0;
        return MPCKeygroupConstants.MINUS_12_DB + result;
    }


    /**
     * Computes a normalized logarithmic value between 0 and 1 from a value and a given range.
     *
     * The envelope time function of the MPC is approached by an exponential function <pre>
     * duration = a * e^(b*control_value)
     * </pre> where the control value corresponds to the value needed by the MPC to produce the
     * duration.
     *
     * @param value The value (e.g. duration)
     * @param minimum The minimum value (must be greater than zero)
     * @param maximum The maximum value
     * @return the normalized logarithmic value
     */
    private static double normalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return Math.log (Math.clamp (value, minimum, maximum) / minimum) / Math.log (maximum / minimum);
    }


    private static void setEnvelopeAttribute (final Document document, final Element element, final String attribute, final double value, final double minimum, final double maximum, final double defaultValue)
    {
        setEnvelopeAttribute (document, element, attribute, value, minimum, maximum, defaultValue, false);
    }


    private static void setEnvelopeAttribute (final Document document, final Element element, final String attribute, final double value, final double minimum, final double maximum, final double defaultValue, final boolean logarithmic)
    {
        final double v = value < 0 ? defaultValue : value;
        final double normalizedValue = logarithmic ? normalizeLogarithmicEnvTimeValue (v, minimum, maximum) : MathUtils.normalize (v, minimum, maximum);
        XMLUtils.addTextElement (document, element, attribute, String.format (Locale.US, "%.6f", Double.valueOf (normalizedValue)));
    }


    private static void setEnvelopeCurveAttribute (final Document document, final Element element, final String curveTag, final double slopeValue)
    {
        final double value = Math.clamp ((slopeValue + 1.0) / 2.0, 0, 1);
        XMLUtils.addTextElement (document, element, curveTag, String.format (Locale.US, "%.6f", Double.valueOf (value)));
    }
}