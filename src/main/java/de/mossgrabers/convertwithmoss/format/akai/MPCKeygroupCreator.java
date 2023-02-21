// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


/**
 * Creator for Akai MPC keygroup files. Keygroups have a description file and related samples in one
 * folder. A keygroup program offers up to 128 keygroups, and each keygroup can hold up to four
 * samples (Layers 1–4). This is a total of 512 samples.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MPCKeygroupCreator extends AbstractCreator
{
	private enum SamplePlay {ONE_SHOT, NOTE_OFF, NOTE_ON};
	
    private static final String DOUBLE_ZERO  = "0.000000";
    private static final String FILE_VERSION = "2.1";
    private static final String APP_VERSION  = "v2.11.6.6";

    private static final double MINUS_12_DB  = 0.353000;
    private static final double PLUS_6_DB    = 1.0;
    private static final double VALUE_RANGE  = PLUS_6_DB - MINUS_12_DB;
    
    private static final double MIN_ENV_TIME_S = 0.001;   // 0.00147392290249433d; 
    private static final double MAX_ENV_TIME_S = 30.0d;
    private static final double DEFAULT_RELEASE_TIME = 0.0d;
    private static final double DEFAULT_DECAY_TIME = 0.0022861319686154d;
    private static final double DEFAULT_ATTACK_TIME = 0.0d;
    private static final double DEFAULT_HOLD_TIME = 0.0d;
; 
        
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupCreator (final INotifier notifier)
    {
        super ("MPC Keygroup", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Store all samples and metadata file in one folder
        final File sampleFolder = new File (destinationFolder, sampleName);
        if (sampleFolder.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", sampleFolder.getAbsolutePath ());
            return;
        }
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

        // Store all samples
        int outputCount = 0;
        for (final IVelocityLayer layer: multisampleSource.getLayers ())
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                String fn = filename.get ();
                // Ensure upper case WAV ending, which seems to be required
                if (fn.endsWith (".wav"))
                    fn = fn.substring (0, fn.length () - 4) + ".WAV";
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, fn)))
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
            }
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @param sampleName The name of the multi sample
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
        XMLUtils.addTextElement (document, versionElement, MPCKeygroupTag.VERSION_FILE_VERSION, FILE_VERSION);

        final Element programElement = createProgramElement (document, multisampleSource);
        multisampleElement.appendChild (programElement);
        final Element instrumentsElement = document.createElement (MPCKeygroupTag.PROGRAM_INSTRUMENTS);
        programElement.appendChild (instrumentsElement);

        final Map<String, List<Keygroup>> keygroupsMap = new HashMap<> ();

        // Need to stack the parts of velocity layers in key ranges
        for (final IVelocityLayer velocityLayer: multisampleSource.getLayers ())
        {
            final TriggerType trigger = velocityLayer.getTrigger ();

            for (final ISampleMetadata sampleMetadata: velocityLayer.getSampleMetadata ())
            {
                final Optional<Keygroup> keygroupOpt = getKeygroup (keygroupsMap, sampleMetadata, document, instrumentsElement, trigger, multisampleSource.getGlobalFilter ());
                if (keygroupOpt.isEmpty ())
                {
                    this.notifier.logError ("IDS_MPC_MORE_THAN_4_LAYERS", Integer.toString (sampleMetadata.getKeyLow ()), Integer.toString (sampleMetadata.getKeyHigh ()), Integer.toString (sampleMetadata.getVelocityLow ()), Integer.toString (sampleMetadata.getVelocityHigh ()));
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

        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    /**
     * Creates a program element.
     *
     * @param document The XML document
     * @param multisampleSource The multi sample source
     * @return The created element
     */
    private static Element createProgramElement (final Document document, final IMultisampleSource multisampleSource)
    {
        final Element programElement = document.createElement (MPCKeygroupTag.ROOT_PROGRAM);
        programElement.setAttribute (MPCKeygroupTag.PROGRAM_TYPE, MPCKeygroupTag.TYPE_KEYGROUP);
        XMLUtils.addTextElement (document, programElement, MPCKeygroupTag.PROGRAM_NAME, multisampleSource.getName ());
        programElement.appendChild (document.createElement (MPCKeygroupTag.PROGRAM_PADS + APP_VERSION));

        // Pitchbend 2 semitones up/down
        final List<IVelocityLayer> layers = getNonEmptyLayers (multisampleSource.getLayers (), false);
        if (!layers.isEmpty ())
        {
            final int bendUp = Math.abs (layers.get (0).getSampleMetadata ().get (0).getBendUp ());
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
     * @param sampleMetadata The sample metadata
     * @param sampleName The name of the sample
     * @return The created layer
     */
    private static Element createLayerElement (final Document document, final int layerIndex, final ISampleMetadata sampleMetadata, final String sampleName)
    {
        final Element layerElement = document.createElement ("Layer");
        layerElement.setAttribute ("number", Integer.toString (layerIndex + 1));

        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_ACTIVE, MPCKeygroupTag.TRUE);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VOLUME, Double.toString (convertGain (sampleMetadata.getGain ())));
        
        double pan = sampleMetadata.getPanorama();
        pan = clamp(pan, -1.0d, 1.0d);
        pan += 1.0d;
        pan /= 2.0;
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PAN, String.format (Locale.US, "%.6f", Double.valueOf (pan)));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PITCH, Double.toString (sampleMetadata.getTune ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VEL_START, Integer.toString (sampleMetadata.getVelocityLow ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VEL_END, Integer.toString (sampleMetadata.getVelocityHigh ()));

        final Optional<String> filename = sampleMetadata.getUpdatedFilename ();
        if (filename.isEmpty ())
            return layerElement;

        // Add the name of the multisample to the wave file to make it 'more unique' if
        // necessary
        String fn = filename.get ();
        if (!fn.startsWith (sampleName))
        {
            fn = sampleName + "_" + fn;
            sampleMetadata.setCombinedName (fn);
        }
        if (fn.toLowerCase (Locale.US).endsWith (".wav"))
            fn = fn.substring (0, fn.length () - 4);

        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_START, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_END, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_START, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_END, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_CROSSFADE, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_LOOP_TUNE, "0");
        // The root note is strangely one more then the lower upper keys!
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_ROOT_NOTE, Integer.toString (sampleMetadata.getKeyRoot () + 1));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_KEY_TRACK, MPCKeygroupTag.TRUE);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_NAME, fn);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PITCH_RANDOM, DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_VOLUME_RANDOM, DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_PAN_RANDOM, DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_OFFSET_RANDOM, DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SAMPLE_FILE, "");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_INDEX, "129");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_DIRECTION, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_OFFSET, "0");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_START, Integer.toString (sampleMetadata.getStart ()));

        final List<ISampleLoop> loops = sampleMetadata.getLoops ();
        if (loops.isEmpty ())
        {
            XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_END, Integer.toString (sampleMetadata.getStop ()));
            XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, "0");
            return layerElement;
        }

        // Format can store only 1 loop
        final ISampleLoop sampleLoop = loops.get (0);
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_START, Integer.toString (sampleLoop.getStart ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_END, Integer.toString (sampleLoop.getEnd ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP, sampleMetadata.isReversed () ? "3" : "1");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_LOOP_CROSSFADE, Double.toString (sampleLoop.getCrossfade ()));
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_TAIL_POSITION, "0.500000");
        XMLUtils.addTextElement (document, layerElement, MPCKeygroupTag.LAYER_SLICE_TAIL_LENGTH, DOUBLE_ZERO);

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


    private static Optional<Keygroup> getKeygroup (final Map<String, List<Keygroup>> keygroupsMap, final ISampleMetadata sampleMetadata, final Document document, final Element instrumentsElement, final TriggerType trigger, final Optional<IFilter> optFilter)
    {
        final int keyLow = sampleMetadata.getKeyLow ();
        final int keyHigh = sampleMetadata.getKeyHigh ();
        final String rangeKey = keyLow + "-" + keyHigh;
        final boolean isSequence = sampleMetadata.getPlayLogic () == PlayLogic.ROUND_ROBIN;
        final List<Keygroup> keygroups = keygroupsMap.computeIfAbsent (rangeKey, key -> new ArrayList<> ());

        // Check if a keygroup exists to which the layer can be added
        for (final Keygroup keygroup: keygroups)
        {
            // Look for velocity or sequence keygroups (type must match)
            if (keygroup.isSequence () == isSequence)
            {
                // Velocity range must match as well for sequences
                if (keygroup.isSequence () && isSequence && (sampleMetadata.getVelocityLow () != keygroup.getVelocityLow () || sampleMetadata.getVelocityHigh () != keygroup.getVelocityHigh ()))
                    continue;

                // Matching keygroup with free layer found
                if (keygroup.getLayerCount () < 4)
                    return Optional.of (keygroup);

                // Can only have 1 sequence keygroup since round robin is per keygroup
                if (isSequence)
                    return Optional.empty ();
            }
        }

        // No existing keygroup found, create a new one (Instrument is a keygroup)

        final Element instrumentElement = document.createElement ("Instrument");
        instrumentElement.setAttribute ("number", Integer.toString (calcInstrumentNumber (keygroupsMap)));
        instrumentsElement.appendChild (instrumentElement);

        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_TYPE, Integer.toString (MPCFilter.getFilterIndex (filter)));
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_CUTOFF, formatDouble (normalizeFrequency (filter.getCutoff ()), 2));
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RESONANCE, formatDouble (Math.min (40, filter.getResonance ()) / 40.0, 2));

            final int envelopeDepth = filter.getEnvelopeDepth ();
            // Only positive modulation values are supported with MPC
            if (envelopeDepth > 0)
            {
                XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ENV_AMOUNT, formatDouble (envelopeDepth / (double) IFilter.MAX_ENVELOPE_DEPTH, 2));

                final IEnvelope filterEnvelope = filter.getEnvelope ();
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_ATTACK, filterEnvelope.getAttack (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_ATTACK_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_HOLD, filterEnvelope.getHold (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_HOLD_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_DECAY, filterEnvelope.getDecay (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_DECAY_TIME, true);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_SUSTAIN, filterEnvelope.getSustain (), 0, 1, 1);
                setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_FILTER_RELEASE, filterEnvelope.getRelease (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_RELEASE_TIME, true);
            }
        }

        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_LOW_NOTE, Integer.toString (keyLow));
        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_HIGH_NOTE, Integer.toString (keyHigh));
        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_IGNORE_BASE_NOTE, sampleMetadata.getKeyTracking () == 0 ? "True" : "False");

        final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeEnvelope ();
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_ATTACK, amplitudeEnvelope.getAttack (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_ATTACK_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_HOLD, amplitudeEnvelope.getHold (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_HOLD_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_DECAY, amplitudeEnvelope.getDecay (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_DECAY_TIME, true);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_SUSTAIN, amplitudeEnvelope.getSustain (), 0, 1, 1);
        setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_VOLUME_RELEASE, amplitudeEnvelope.getRelease (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_RELEASE_TIME, true);

        final int pitchDepth = sampleMetadata.getPitchEnvelopeDepth ();
        // Only positive modulation values are supported with MPC
        if (pitchDepth > 0)
        {
            final double mpcPitchDepth = clamp (pitchDepth, -3600, 3600) / 3600.0 / 2.0 + 0.5;
            XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ENV_AMOUNT, formatDouble (mpcPitchDepth, 2));

            final IEnvelope pitchEnvelope = sampleMetadata.getPitchEnvelope ();
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_ATTACK, pitchEnvelope.getAttack (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_ATTACK_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_HOLD, pitchEnvelope.getHold (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_HOLD_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_DECAY, pitchEnvelope.getDecay (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_DECAY_TIME, true);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_SUSTAIN, pitchEnvelope.getSustain (), 0, 1, 1);
            setEnvelopeAttribute (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_PITCH_RELEASE, pitchEnvelope.getRelease (), MIN_ENV_TIME_S, MAX_ENV_TIME_S, DEFAULT_RELEASE_TIME);
        }

        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_ZONE_PLAY, ZonePlay.from (sampleMetadata.getPlayLogic ()).getID ());

        SamplePlay triggerMode = SamplePlay.NOTE_ON;
        
        if (trigger == TriggerType.RELEASE)
            triggerMode = SamplePlay.NOTE_OFF;
        else if(sampleMetadata.getAmplitudeEnvelope().getSustain() <= 0) {
        	if(sampleMetadata.getKeyLow() == sampleMetadata.getKeyHigh()) 
        		triggerMode = SamplePlay.ONE_SHOT; 
        }
        
        XMLUtils.addTextElement (document, instrumentElement, MPCKeygroupTag.INSTRUMENT_TRIGGER_MODE, Integer.toString (triggerMode.ordinal()));

        instrumentElement.appendChild (createLfoElement (document));
        final Element layersElement = document.createElement ("Layers");
        instrumentElement.appendChild (layersElement);

        final Keygroup keygroup;
        if (isSequence)
            keygroup = new Keygroup (layersElement, sampleMetadata.getVelocityLow (), sampleMetadata.getVelocityHigh ());
        else
            keygroup = new Keygroup (layersElement);
        keygroups.add (keygroup);
        return Optional.of (keygroup);
    }


    private static double normalizeFrequency (final double cutoff)
    {
        final double val = log2 (cutoff) / log2 (IFilter.MAX_FREQUENCY);
        return Math.min (1, Math.max (0, val));
    }


    private static double log2 (final double value)
    {
        return Math.log (value) / Math.log (2);
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
        final double result = VALUE_RANGE * v / 18.0;
        return MINUS_12_DB + result;
    }


    /**
     * Computes a normalized logarigmic value between 0 and 1 from a value and a given range.
     * 
     * The envelope time function of the MPC is approached by an exponential function
     * duration = a * e^(b*control_value) where the control value corresponds to the
     * value needed by the MPC to produce the duration
     * 
     * @param value the value (e.g. duration)
     * @param minimum the minimum value (must be greater than zero)
     * @param maximum the maximum value
     * @return the normalized logarithmic value
     */
    private static double normalizedLogarithmicEnvTimeValue(double value, double minimum, double maximum) {
    	value = clamp(value, minimum, maximum);
    	
        final double a = minimum;
        final double b = Math.log(maximum / minimum);
        
        return Math.log(value/a)/b; 
    }
    
    
    private static void setEnvelopeAttribute (final Document document, final Element element, final String attribute, final double value, final double minimum, final double maximum, final double defaultValue, boolean logarithmic)
    {
        final double v = value < 0 ? defaultValue : value;
        final double normalizedValue = logarithmic 
        		? normalizedLogarithmicEnvTimeValue(v, minimum, maximum) 
        		: normalizeValue (v, minimum, maximum);
        XMLUtils.addTextElement (document, element, attribute, String.format (Locale.US, "%.6f", Double.valueOf (normalizedValue)));
    }
    
    private static void setEnvelopeAttribute (final Document document, final Element element, final String attribute, final double value, final double minimum, final double maximum, final double defaultValue) {
    	setEnvelopeAttribute (document, element, attribute, value, minimum, maximum, defaultValue, false);
    }
}