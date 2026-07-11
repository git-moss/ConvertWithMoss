// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Synthstrom Deluge instrument files in folders. Files must end with
 * <i>.xml</i> and contain a synth (<i>sound</i>) or drum kit (<i>kit</i>) at the top level. Synths
 * which use sample oscillators are read as a multi-sample (one zone per sample range). Kits are
 * read as a multi-sample with one zone per drum, mapped to ascending notes.
 * <p>
 * The Deluge format exists in two flavors which are both supported: the older format stores values
 * as XML child elements while newer firmware stores them as XML attributes.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] OSC_TAGS              = new String []
    {
        DelugeTag.OSC1,
        DelugeTag.OSC2
    };

    private static final String    XML_HEADER            = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String    ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";
    private static final String    ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String    ERR_SAMPLE_MISSING    = "IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST";

    private static final String    ENDING_DELUGE         = ".xml";
    private static final int       KIT_BASE_NOTE         = 36;
    private static final int       SEARCH_LEVELS         = 2;

    private File                   previousSampleFolder;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DelugeDetector (final INotifier notifier)
    {
        super ("Synthstrom Deluge", "Deluge", notifier, new MetadataSettingsUI ("Deluge"), ENDING_DELUGE);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        this.previousSampleFolder = null;

        try
        {
            final String content = fixBrokenXml (this.loadTextFile (file));
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseXmlDocument (file, document);
        }
        catch (final SAXParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_COULD_NOT_PARSE_XML", Integer.toString (ex.getLineNumber ()), Integer.toString (ex.getColumnNumber ()), ex.getLocalizedMessage ());
            return Collections.emptyList ();
        }
        catch (final SAXException | IOException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Removes tags outside of the root tag which prevent parsing the XML file. 'firmwareVersion'
     * and 'earliestCompatibleFirmware'.
     * <p>
     * The root tag (<i>kit</i> or <i>sound</i>) might carry attributes (e.g.
     * <code>&lt;sound firmwareVersion="3.0.5" ...&gt;</code>) which is why the tag start is matched
     * rather than the exact <code>&lt;kit&gt;</code>/<code>&lt;sound&gt;</code> literal.
     *
     * @param xmlCode The XML document code
     * @return The cleaned document code
     * @throws IOException If the root tag could not be found
     */
    private static String fixBrokenXml (final String xmlCode) throws IOException
    {
        int pos = findRootTag (xmlCode, DelugeTag.KIT);
        if (pos < 0)
            pos = findRootTag (xmlCode, DelugeTag.SOUND);
        if (pos < 0)
            throw new IOException (Functions.getMessage ("IDS_DELUGE_NOT_A_DELUGE_FILE"));
        return XML_HEADER + xmlCode.substring (pos);
    }


    /**
     * Find the start position of the opening root tag with the given name. The tag is matched
     * whether it has attributes (e.g. <code>&lt;sound firmwareVersion="..."&gt;</code>) or not
     * (e.g. <code>&lt;sound&gt;</code>). Tags which merely start with the given name (e.g.
     * <code>soundSources</code>) are not matched.
     *
     * @param xmlCode The XML document code
     * @param tagName The name of the tag to find
     * @return The index of the opening '&lt;' or -1 if not found
     */
    private static int findRootTag (final String xmlCode, final String tagName)
    {
        final String tagStart = "<" + tagName;
        int pos = xmlCode.indexOf (tagStart);
        while (pos >= 0)
        {
            final int after = pos + tagStart.length ();
            if (after < xmlCode.length ())
            {
                // The tag name must be followed by whitespace, the tag end or a self-closing slash
                final char c = xmlCode.charAt (after);
                if (c == '>' || c == '/' || Character.isWhitespace (c))
                    return pos;
            }
            pos = xmlCode.indexOf (tagStart, after);
        }
        return -1;
    }


    /**
     * Parses the XML document of a Deluge instrument file.
     *
     * @param file The source file
     * @param document The parsed XML document
     * @return The parsed multi-sample source as a singleton list or an empty list
     */
    private List<IMultisampleSource> parseXmlDocument (final File file, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (top == null)
            return Collections.emptyList ();

        final String rootName = top.getNodeName ();
        // Root tag is either 'kit' or 'sound', already checked in fixBrokenXml
        final boolean isKit = DelugeTag.KIT.equals (rootName);
        final IGroup group = isKit ? this.parseKit (file, top) : this.parseSound (file, top);
        if (group == null)
        {
            this.notifier.logError ("IDS_DELUGE_NO_SAMPLE_REFS");
            return Collections.emptyList ();
        }

        return Collections.singletonList (this.createMultisampleSource (file, FileUtils.getNameWithoutType (file), Collections.singletonList (group)));
    }


    /**
     * Parse a synth (sound) preset.
     *
     * @param file The source file
     * @param soundElement The sound element
     * @return One group (containing all zones) or null
     */
    private IGroup parseSound (final File file, final Element soundElement)
    {
        final Element oscElement = findSampleOscillatorElement (soundElement);
        if (oscElement == null)
            return null;

        final List<ISampleZone> zones = this.parseSampleOscillator (file, oscElement);
        if (zones.isEmpty ())
            return null;

        applySoundParameters (soundElement, zones);

        final IGroup group = new DefaultGroup ();
        for (final ISampleZone zone: zones)
            group.addSampleZone (zone);

        return group;
    }


    /**
     * Parse a drum kit preset. Each drum which uses a sample is mapped to one zone with an
     * ascending note starting at {@link #KIT_BASE_NOTE}.
     *
     * @param file The source file
     * @param kitElement The kit element
     * @return One group (containing all drum zones) or null
     */
    private IGroup parseKit (final File file, final Element kitElement)
    {
        final Element soundSources = getDirectChild (kitElement, DelugeTag.SOUND_SOURCES);
        if (soundSources == null)
            return null;

        final IGroup group = new DefaultGroup ();
        int note = KIT_BASE_NOTE;
        for (final Element drumSound: getDirectChildren (soundSources, DelugeTag.SOUND))
        {
            if (this.waitForDelivery ())
                return null;

            final Element oscElement = findSampleOscillatorElement (drumSound);
            if (oscElement != null)
            {
                final ISampleZone zone = this.createDrumZone (file, oscElement, note);
                if (zone != null)
                {
                    applySoundParameters (drumSound, Collections.singletonList (zone));
                    group.addSampleZone (zone);
                    note++;
                }
            }

            if (note > 127)
                break;
        }

        return group.getSampleZones ().isEmpty () ? null : group;
    }


    /**
     * Find the first oscillator which uses a sample.
     *
     * @param soundElement The sound element to search
     * @return The oscillator element or null if there is no sample based oscillator
     */
    private static Element findSampleOscillatorElement (final Element soundElement)
    {
        for (final String oscTag: OSC_TAGS)
        {
            final Element osc = getDirectChild (soundElement, oscTag);
            if (osc != null && DelugeTag.TYPE_SAMPLE.equals (getValue (osc, DelugeTag.TYPE)) && (getDirectChild (osc, DelugeTag.SAMPLE_RANGES) != null || !getValue (osc, DelugeTag.FILE_NAME).isBlank ()))
                return osc;
        }
        return null;
    }


    /**
     * Parse all sample zones of a sample oscillator.
     *
     * @param file The source file
     * @param oscElement The oscillator element
     * @return The parsed zones
     */
    private List<ISampleZone> parseSampleOscillator (final File file, final Element oscElement)
    {
        final int loopMode = getIntValue (oscElement, DelugeTag.LOOP_MODE, DelugeTag.LOOP_MODE_CUT);
        final boolean reversed = getIntValue (oscElement, DelugeTag.REVERSED, 0) != 0;

        final List<ISampleZone> zones = new ArrayList<> ();
        final Element sampleRanges = getDirectChild (oscElement, DelugeTag.SAMPLE_RANGES);
        if (sampleRanges != null)
        {
            final List<Element> rangeElements = getDirectChildren (sampleRanges, DelugeTag.SAMPLE_RANGE);
            int previousTopNote = -1;
            for (final Element rangeElement: rangeElements)
            {
                final int topNote = getIntValue (rangeElement, DelugeTag.RANGE_TOP_NOTE, -1);
                final int keyLow = previousTopNote + 1;
                final int keyHigh = topNote >= 0 ? topNote : 127;
                final ISampleZone zone = this.createZone (file, rangeElement, rangeElement, keyLow, keyHigh, -1, loopMode, reversed);
                if (zone != null)
                    zones.add (zone);
                previousTopNote = keyHigh;
            }
        }
        else
        {
            // Single sample - file name and zone are stored directly on the oscillator element
            final ISampleZone zone = this.createZone (file, oscElement, oscElement, 0, 127, -1, loopMode, reversed);
            if (zone != null)
                zones.add (zone);
        }
        return zones;
    }


    /**
     * Create a single zone for one drum of a kit.
     *
     * @param file The source file
     * @param oscElement The oscillator element of the drum
     * @param note The note to which the drum is mapped
     * @return The zone or null if the drum has no usable sample
     */
    private ISampleZone createDrumZone (final File file, final Element oscElement, final int note)
    {
        final int loopMode = getIntValue (oscElement, DelugeTag.LOOP_MODE, DelugeTag.LOOP_MODE_CUT);
        final boolean reversed = getIntValue (oscElement, DelugeTag.REVERSED, 0) != 0;

        Element attributeElement = oscElement;
        Element zoneParent = oscElement;
        final Element sampleRanges = getDirectChild (oscElement, DelugeTag.SAMPLE_RANGES);
        if (sampleRanges != null)
        {
            final List<Element> rangeElements = getDirectChildren (sampleRanges, DelugeTag.SAMPLE_RANGE);
            if (rangeElements.isEmpty ())
                return null;
            attributeElement = rangeElements.get (0);
            zoneParent = rangeElements.get (0);
        }
        return this.createZone (file, attributeElement, zoneParent, note, note, note, loopMode, reversed);
    }


    /**
     * Create one sample zone.
     *
     * @param file The source file
     * @param attributeElement The element which contains the file name, transpose and cents
     * @param zoneParent The element which contains the zone child element with the sample positions
     * @param keyLow The lowest note of the zone
     * @param keyHigh The highest note of the zone
     * @param fixedRootNote If &gt;= 0, this root note is used instead of the one calculated from
     *            transpose/cents
     * @param loopMode The Deluge loop mode of the oscillator
     * @param reversed True if the sample is reversed
     * @return The created zone or null if the sample could not be loaded
     */
    private ISampleZone createZone (final File file, final Element attributeElement, final Element zoneParent, final int keyLow, final int keyHigh, final double fixedRootNote, final int loopMode, final boolean reversed)
    {
        final String fileName = getValue (attributeElement, DelugeTag.FILE_NAME);
        if (fileName.isBlank ())
            return null;

        // Expect the sample 1 folder upwards from KIT or SYNTHS in a SAMPLES folder (which is part
        // of the relative path in fileName)
        final File parentFile = file.getParentFile ();
        final String parentFolderName = parentFile.getName ();
        File sampleFile = null;
        if (parentFolderName.equalsIgnoreCase ("KITS") || parentFolderName.equalsIgnoreCase ("SYNTHS"))
            sampleFile = new File (parentFile.getParentFile (), fileName);
        // Search from 2 folders upwards if not found...
        if (sampleFile == null || !sampleFile.exists ())
            sampleFile = findSampleFile (this.notifier, parentFile, this.previousSampleFolder, fileName, SEARCH_LEVELS);
        if (!sampleFile.exists ())
        {
            this.notifier.logError (ERR_SAMPLE_MISSING, sampleFile.getAbsolutePath ());
            return null;
        }
        this.previousSampleFolder = sampleFile.getParentFile ();

        final ISampleData sampleData;
        try
        {
            sampleData = createSampleData (sampleFile, this.notifier);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return null;
        }

        final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
        zone.setKeyLow (keyLow);
        zone.setKeyHigh (keyHigh);
        zone.setReversed (reversed);

        final int transpose = getIntValue (attributeElement, DelugeTag.TRANSPOSE_OSC, 0);
        final int cents = getIntValue (attributeElement, DelugeTag.CENTS, 0);
        final double rootNote = fixedRootNote >= 0 ? fixedRootNote : DelugeValues.rootNoteFromTranspose (transpose, cents);
        final int keyRoot = (int) Math.round (rootNote);
        zone.setKeyRoot (keyRoot);
        zone.setTuning (rootNote - keyRoot);

        // A Deluge oscillator in STRETCH (time-stretch) mode sustains by looping its sample through
        // the granular time-stretch engine. ConvertWithMoss cannot reproduce the time-stretch, but
        // such a sample still carries a loop start and is meant to sustain, so it is treated as a
        // normal forward loop here. Without this a held STRETCH patch (e.g. a sustained pad) would
        // play its sample once and then drop out, because no loop was created at all.
        final boolean isLoop = loopMode == DelugeTag.LOOP_MODE_LOOP || loopMode == DelugeTag.LOOP_MODE_STRETCH;
        final ISampleLoop loop = applyZonePositions (getDirectChild (zoneParent, DelugeTag.ZONE), zone, sampleData, isLoop);

        try
        {
            sampleData.addZoneData (zone, false, loop == null && isLoop);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }

        return zone;
    }


    /**
     * Read the sample start/end and loop positions from a zone element and apply them to the zone.
     * Handles both the sample-frame based positions and the older milliseconds/seconds based
     * positions.
     *
     * @param zoneElement The zone element (may be null)
     * @param zone The zone to fill
     * @param sampleData The sample data (used for the sample rate when converting milliseconds)
     * @param isLoop True if the oscillator loop mode is set to loop
     * @return The created loop or null if there is none
     */
    private static ISampleLoop applyZonePositions (final Element zoneElement, final ISampleZone zone, final ISampleData sampleData, final boolean isLoop)
    {
        if (zoneElement == null)
            return null;

        int sampleRate = 0;
        int numberOfSamples = Integer.MAX_VALUE;
        try
        {
            final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
            sampleRate = audioMetadata.getSampleRate ();
            numberOfSamples = audioMetadata.getNumberOfSamples ();
        }
        catch (final IOException _)
        {
            // Ignore - fall back to no millisecond conversion
        }

        int start = (int) Math.round (getDoubleValue (zoneElement, DelugeTag.START_SAMPLE_POS, -1));
        int end = (int) Math.round (getDoubleValue (zoneElement, DelugeTag.END_SAMPLE_POS, -1));
        if (start < 0)
            start = millisecondsToSamples (getDoubleValue (zoneElement, DelugeTag.START_SECONDS, 0) * 1000.0 + getDoubleValue (zoneElement, DelugeTag.START_MILLISECONDS, 0), sampleRate);
        if (end < 0)
        {
            final int endFromMilliseconds = millisecondsToSamples (getDoubleValue (zoneElement, DelugeTag.END_SECONDS, 0) * 1000.0 + getDoubleValue (zoneElement, DelugeTag.END_MILLISECONDS, 0), sampleRate);
            // A very large end value is used as a 'play to the end' marker in old files
            end = endFromMilliseconds < numberOfSamples ? endFromMilliseconds : -1;
        }

        if (start > 0)
            zone.setStart (start);
        if (end > 0)
            zone.setStop (end);

        if (!isLoop)
            return null;

        final int loopStart = (int) Math.round (getDoubleValue (zoneElement, DelugeTag.START_LOOP_POS, -1));
        final int loopEnd = (int) Math.round (getDoubleValue (zoneElement, DelugeTag.END_LOOP_POS, -1));
        if (loopStart <= 0 && loopEnd <= 0)
            return null;

        final ISampleLoop loop = new DefaultSampleLoop ();
        loop.setType (LoopType.FORWARDS);
        if (loopStart >= 0)
            loop.setStart (loopStart);
        // The Deluge omits the loop end when the loop runs to the end of the (played) sample.
        // Default it to the sample/zone end so a valid loop is created; otherwise the unset end
        // (-1) would be written as a negative, degenerate loop and the sound would lose its sustain
        // on export (e.g. a pad ending abruptly instead of looping until the release).
        if (loopEnd > 0)
            loop.setEnd (loopEnd);
        else
        {
            final int loopEndDefault = end > 0 ? end : numberOfSamples;
            if (loopEndDefault > 0 && loopEndDefault != Integer.MAX_VALUE)
                loop.setEnd (loopEndDefault);
        }
        zone.addLoop (loop);
        return loop;
    }


    /**
     * Read the amplitude envelope and filter from the default parameters of a sound and apply them
     * to all given zones.
     *
     * @param soundElement The sound element
     * @param zones The zones to fill
     */
    private static void applySoundParameters (final Element soundElement, final List<ISampleZone> zones)
    {
        final Element defaultParams = getDirectChild (soundElement, DelugeTag.DEFAULT_PARAMS);
        if (defaultParams == null)
            return;

        final Element envelope1 = getDirectChild (defaultParams, DelugeTag.ENVELOPE1);
        // The Deluge expresses velocity sensitivity of the amplitude as a "velocity" patch cable
        // routed to the post-effects volume.
        final double amplitudeVelocityDepth = readPatchCableDepth (defaultParams, DelugeTag.SOURCE_VELOCITY, DelugeTag.DESTINATION_VOLUME);

        for (final ISampleZone zone: zones)
        {
            final IEnvelopeModulator amplitudeModulator = zone.getAmplitudeEnvelopeModulator ();
            if (envelope1 != null)
                readEnvelope (envelope1, amplitudeModulator.getSource ());
            amplitudeModulator.setDepth (amplitudeVelocityDepth);

            // A fresh filter is read per zone so the zones do not share mutable modulators.
            final IFilter filter = readFilter (soundElement, defaultParams);
            if (filter != null)
                zone.setFilter (filter);
        }
    }


    /**
     * Read an envelope from a Deluge envelope element.
     *
     * @param envelopeElement The envelope element
     * @param envelope The envelope to fill
     */
    private static void readEnvelope (final Element envelopeElement, final IEnvelope envelope)
    {
        final String attack = getValue (envelopeElement, DelugeTag.ATTACK);
        final String decay = getValue (envelopeElement, DelugeTag.DECAY);
        final String sustain = getValue (envelopeElement, DelugeTag.SUSTAIN);
        final String release = getValue (envelopeElement, DelugeTag.RELEASE);

        if (!attack.isBlank ())
            envelope.setAttackTime (DelugeValues.paramToAttackTime (DelugeValues.parseValue (attack, DelugeValues.PARAM_MIN)));
        if (!decay.isBlank ())
            envelope.setDecayTime (DelugeValues.paramToReleaseTime (DelugeValues.parseValue (decay, DelugeValues.PARAM_MIN)));
        if (!sustain.isBlank ())
            envelope.setSustainLevel (DelugeValues.paramToLevel (DelugeValues.parseValue (sustain, DelugeValues.PARAM_MAX)));
        if (!release.isBlank ())
            envelope.setReleaseTime (DelugeValues.paramToReleaseTime (DelugeValues.parseValue (release, DelugeValues.PARAM_MIN)));
    }


    /**
     * Read the filter from the default parameters. The low-pass filter is preferred. A filter is
     * only returned if it is actually engaged (i.e. not fully open). Both the newer flat attributes
     * (lpfFrequency) and the older nested elements (lpf/frequency) are supported. The filter slot
     * mode (which might be an official or a community firmware feature) is mapped to the matching
     * filter type.
     *
     * @param soundElement The sound element which contains the filter mode
     * @param defaultParams The default parameters element which contains the frequency/resonance
     * @return The filter or null
     */
    private static IFilter readFilter (final Element soundElement, final Element defaultParams)
    {
        final Element nestedLpf = getDirectChild (defaultParams, DelugeTag.LPF);
        final String lpfFrequency = nestedLpf != null ? getValue (nestedLpf, DelugeTag.FREQUENCY) : getValue (defaultParams, DelugeTag.LPF_FREQUENCY);
        if (!lpfFrequency.isBlank ())
        {
            final int frequencyParam = DelugeValues.parseValue (lpfFrequency, DelugeValues.PARAM_MAX);
            if (frequencyParam < DelugeValues.PARAM_MAX)
            {
                final String lpfResonance = nestedLpf != null ? getValue (nestedLpf, DelugeTag.RESONANCE) : getValue (defaultParams, DelugeTag.LPF_RESONANCE);
                final double resonance = DelugeValues.paramToLevel (DelugeValues.parseValue (lpfResonance, DelugeValues.PARAM_MIN));
                final IFilter filter = createFilter (getValue (soundElement, DelugeTag.LPF_MODE), FilterType.LOW_PASS, 4, DelugeValues.paramToCutoff (frequencyParam), resonance);
                applyFilterModulation (filter, defaultParams, DelugeTag.LPF_FREQUENCY);
                return filter;
            }
        }

        final Element nestedHpf = getDirectChild (defaultParams, DelugeTag.HPF);
        final String hpfFrequency = nestedHpf != null ? getValue (nestedHpf, DelugeTag.FREQUENCY) : getValue (defaultParams, DelugeTag.HPF_FREQUENCY);
        if (!hpfFrequency.isBlank ())
        {
            final int frequencyParam = DelugeValues.parseValue (hpfFrequency, DelugeValues.PARAM_MIN);
            if (frequencyParam > DelugeValues.PARAM_MIN)
            {
                final String hpfResonance = nestedHpf != null ? getValue (nestedHpf, DelugeTag.RESONANCE) : getValue (defaultParams, DelugeTag.HPF_RESONANCE);
                final double resonance = DelugeValues.paramToLevel (DelugeValues.parseValue (hpfResonance, DelugeValues.PARAM_MIN));
                final IFilter filter = createFilter (getValue (soundElement, DelugeTag.HPF_MODE), FilterType.HIGH_PASS, 2, DelugeValues.paramToCutoff (frequencyParam), resonance);
                applyFilterModulation (filter, defaultParams, DelugeTag.HPF_FREQUENCY);
                return filter;
            }
        }

        return null;
    }


    /**
     * Apply the filter cutoff modulations which the Deluge stores as patch cables routed to the
     * filter frequency: keyboard tracking (<i>note</i> source), the modulation envelope
     * (<i>envelope2</i>) and velocity sensitivity (<i>velocity</i> source).
     *
     * @param filter The filter to fill
     * @param defaultParams The default parameters element which contains the patch cables and the
     *            modulation envelope
     * @param frequencyDestination The filter frequency modulation destination (lpfFrequency or
     *            hpfFrequency)
     */
    private static void applyFilterModulation (final IFilter filter, final Element defaultParams, final String frequencyDestination)
    {
        // Keyboard tracking: the "note" source routed to the filter frequency.
        filter.setCutoffKeyTracking (readPatchCableDepth (defaultParams, DelugeTag.SOURCE_NOTE, frequencyDestination));

        // Filter envelope: the modulation envelope (envelope2) routed to the filter frequency.
        final Element envelope2 = getDirectChild (defaultParams, DelugeTag.ENVELOPE2);
        final IEnvelopeModulator envelopeModulator = filter.getCutoffEnvelopeModulator ();
        if (envelope2 != null)
            readEnvelope (envelope2, envelopeModulator.getSource ());
        envelopeModulator.setDepth (readPatchCableDepth (defaultParams, DelugeTag.ENVELOPE2, frequencyDestination));

        // Filter velocity: the "velocity" source routed to the filter frequency.
        filter.getCutoffVelocityModulator ().setDepth (readPatchCableDepth (defaultParams, DelugeTag.SOURCE_VELOCITY, frequencyDestination));
    }


    /**
     * Read the normalized depth of the first patch cable with the given source and destination.
     *
     * @param defaultParams The default parameters element which contains the patch cables
     * @param source The modulation source to match
     * @param destination The modulation destination to match
     * @return The modulation depth in the range of [-1..1], 0 if there is no such patch cable
     */
    private static double readPatchCableDepth (final Element defaultParams, final String source, final String destination)
    {
        final String amount = findPatchCableAmount (defaultParams, source, destination);
        if (amount.isBlank ())
            return 0;
        return DelugeValues.patchAmountToModulationDepth (DelugeValues.parseValue (amount, 0));
    }


    /**
     * Find the amount of the first patch cable with the given source and destination.
     *
     * @param defaultParams The default parameters element which contains the patch cables
     * @param source The modulation source to match
     * @param destination The modulation destination to match
     * @return The amount value or an empty string if there is no matching patch cable
     */
    private static String findPatchCableAmount (final Element defaultParams, final String source, final String destination)
    {
        final Element patchCables = getDirectChild (defaultParams, DelugeTag.PATCH_CABLES);
        if (patchCables == null)
            return "";
        for (final Element cable: getDirectChildren (patchCables, DelugeTag.PATCH_CABLE))
            if (source.equals (getValue (cable, DelugeTag.SOURCE)) && destination.equals (getValue (cable, DelugeTag.DESTINATION)))
                return getValue (cable, DelugeTag.AMOUNT);
        return "";
    }


    /**
     * Create a filter, mapping the Deluge filter mode to the type and number of poles.
     *
     * @param mode The Deluge filter mode string (might be empty)
     * @param defaultType The type to use if the mode is not set
     * @param defaultPoles The number of poles to use if the mode is not set
     * @param cutoff The cut-off frequency in Hertz
     * @param resonance The resonance in the range of [0..1]
     * @return The filter
     */
    private static IFilter createFilter (final String mode, final FilterType defaultType, final int defaultPoles, final double cutoff, final double resonance)
    {
        FilterType type = defaultType;
        int poles = defaultPoles;
        if (mode != null && !mode.isBlank ())
            switch (mode)
            {
                case "12dB":
                    type = FilterType.LOW_PASS;
                    poles = 2;
                    break;
                case "24dB", "24dBDrive":
                    type = FilterType.LOW_PASS;
                    poles = 4;
                    break;
                case "HPLadder":
                    type = FilterType.HIGH_PASS;
                    poles = 4;
                    break;
                case "SVF_Band":
                    type = FilterType.BAND_PASS;
                    poles = 2;
                    break;
                case "SVF_Notch":
                    type = FilterType.BAND_REJECTION;
                    poles = 2;
                    break;
                default:
                    break;
            }
        return new DefaultFilter (type, poles, cutoff, resonance);
    }


    /**
     * Convert a number of milliseconds to samples.
     *
     * @param milliseconds The milliseconds
     * @param sampleRate The sample rate, 0 if unknown
     * @return The number of samples or -1 if it could not be calculated or the milliseconds are 0
     */
    private static int millisecondsToSamples (final double milliseconds, final int sampleRate)
    {
        if (milliseconds <= 0 || sampleRate <= 0)
            return -1;
        return (int) Math.round (milliseconds * sampleRate / 1000.0);
    }


    /**
     * Get a value which is stored either as an XML attribute or as the text content of a direct
     * child element.
     *
     * @param parent The parent element
     * @param name The name of the attribute or child element
     * @return The value or an empty string if not present
     */
    private static String getValue (final Element parent, final String name)
    {
        final String attribute = parent.getAttribute (name);
        if (!attribute.isEmpty ())
            return attribute;
        final Element child = getDirectChild (parent, name);
        return child == null ? "" : child.getTextContent ().trim ();
    }


    private static int getIntValue (final Element parent, final String name, final int defaultValue)
    {
        final String value = getValue (parent, name);
        if (value.isBlank ())
            return defaultValue;
        try
        {
            return Integer.parseInt (value.trim ());
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    private static double getDoubleValue (final Element parent, final String name, final double defaultValue)
    {
        final String value = getValue (parent, name);
        if (value.isBlank ())
            return defaultValue;
        try
        {
            return Double.parseDouble (value.trim ());
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    /**
     * Get the first direct child element with the given name.
     *
     * @param parent The parent element
     * @param name The name of the child element
     * @return The child element or null
     */
    private static Element getDirectChild (final Element parent, final String name)
    {
        for (final Element child: XMLUtils.getChildElements (parent))
            if (name.equals (child.getNodeName ()))
                return child;
        return null;
    }


    /**
     * Get all direct child elements with the given name.
     *
     * @param parent The parent element
     * @param name The name of the child elements
     * @return The child elements (might be empty)
     */
    private static List<Element> getDirectChildren (final Element parent, final String name)
    {
        final List<Element> result = new ArrayList<> ();
        for (final Element child: XMLUtils.getChildElements (parent))
            if (name.equals (child.getNodeName ()))
                result.add (child);
        return result;
    }
}
