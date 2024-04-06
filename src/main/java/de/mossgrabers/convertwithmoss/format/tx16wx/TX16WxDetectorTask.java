// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively TX16Wx txprog files in folders. Files must end with <i>.txprog</i>.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxDetectorTask extends AbstractDetectorTask
{
    private static final String                  ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String                  ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";
    private static final String                  ENDING_TXPROGRAM      = ".txprog";

    private static final Map<String, LoopType>   LOOP_MODES            = new HashMap<> ();
    private static final Map<String, FilterType> FILTER_TYPES          = new HashMap<> ();
    static
    {
        LOOP_MODES.put ("Forward", LoopType.FORWARDS);
        LOOP_MODES.put ("Backward", LoopType.BACKWARDS);
        LOOP_MODES.put ("Bidirectional", LoopType.ALTERNATING);

        FILTER_TYPES.put ("LowPass", FilterType.LOW_PASS);
        FILTER_TYPES.put ("HighPass", FilterType.HIGH_PASS);
        FILTER_TYPES.put ("BandPass", FilterType.BAND_PASS);
        FILTER_TYPES.put ("Notch", FilterType.BAND_REJECTION);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected TX16WxDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDING_TXPROGRAM);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final FileInputStream in = new FileInputStream (file))
        {
            final String content = StreamUtils.readUTF8 (in);
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, file.getParent (), document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The preset or library file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String basePath, final Document document)
    {
        final Element topElement = document.getDocumentElement ();
        if (!TX16WxTag.PROGRAM.equals (topElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Map<String, ISampleZone> sampleMap = this.parseSamples (topElement, basePath);
        final List<IGroup> groups = this.parseGroups (topElement, sampleMap);

        final String name = FileUtils.getNameWithoutType (multiSampleFile);
        final String n = this.metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, n);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (groups), parts);

        multisampleSource.setGroups (groups);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse all sample (wave) tags.
     * 
     * @param basePath The base path of the samples
     * @return All parsed samples
     */
    private Map<String, ISampleZone> parseSamples (final Element topElement, final String basePath)
    {
        final Map<String, ISampleZone> sampleZoneMap = new HashMap<> ();
        for (final Element sampleElement: XMLUtils.getChildElementsByName (topElement, TX16WxTag.SAMPLE, false))
        {
            final String sampleName = sampleElement.getAttribute (TX16WxTag.PATH);
            if (sampleName == null || sampleName.isBlank ())
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                continue;
            }

            final ISampleZone sampleZone;
            try
            {
                sampleZone = this.createSampleZone (new File (basePath, sampleName));
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
                continue;
            }

            final String sampleID = sampleElement.getAttribute (TX16WxTag.SAMPLE_ID);
            sampleZoneMap.put (sampleID, sampleZone);

            // Parse all loops
            readLoops (sampleElement, sampleZone.getLoops ());
        }

        return sampleZoneMap;
    }


    /**
     * Parses all groups.
     *
     * @param programElement The XML element containing all groups
     * @param sampleMap The mapping of all sample element to their ID
     * @return All parsed groups
     */
    private List<IGroup> parseGroups (final Element programElement, final Map<String, ISampleZone> sampleMap)
    {
        final Map<String, Element> soundShapeElementMap = new HashMap<> ();
        for (final Element soundShapeElement: XMLUtils.getChildElementsByName (programElement, TX16WxTag.SOUND_SHAPE, false))
        {
            final String soundShapeID = soundShapeElement.getAttribute (TX16WxTag.SOUND_SHAPE_ID);
            if (soundShapeID != null)
                soundShapeElementMap.put (soundShapeID, soundShapeElement);
        }

        final List<IGroup> groups = new ArrayList<> ();
        int groupCounter = 1;
        for (final Element groupElement: XMLUtils.getChildElementsByName (programElement, TX16WxTag.GROUP, false))
        {
            final String k = groupElement.getAttribute (TX16WxTag.GROUP_NAME);
            final String groupName = k == null || k.isBlank () ? "Group " + groupCounter : k;
            final DefaultGroup group = new DefaultGroup (groupName);

            this.parseGroup (group, groupElement, sampleMap, soundShapeElementMap);
            groups.add (group);
            groupCounter++;
        }
        return groups;
    }


    /**
     * Parse a group.
     *
     * @param group The object to fill in the data
     * @param groupElement The XML group element
     * @param sampleMap The mapping of all sample element to their ID
     * @param soundShapeElementMap The sound shape elements mapped to their ID
     */
    private void parseGroup (final DefaultGroup group, final Element groupElement, final Map<String, ISampleZone> sampleMap, final Map<String, Element> soundShapeElementMap)
    {
        final double groupVolumeOffset = parseVolume (groupElement, TX16WxTag.VOLUME);
        final double groupPanoramaOffset = parsePanorama (groupElement, TX16WxTag.PANORAMA);
        double groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, TX16WxTag.TUNING_COARSE, 0) * 100;
        groupTuningOffset += XMLUtils.getDoubleAttribute (groupElement, TX16WxTag.TUNING_FINE, 0);

        final String playMode = groupElement.getAttribute (TX16WxTag.GROUP_PLAYMODE);
        if ("Release".equals (playMode))
            group.setTrigger (TriggerType.RELEASE);

        // Volume envelope
        IEnvelope ampEnvelope = null;
        final String soundShape = groupElement.getAttribute (TX16WxTag.SOUND_SHAPE);
        if (soundShape != null)
        {
            final Element soundShapeElement = soundShapeElementMap.get (soundShape);
            if (soundShapeElement != null)
            {
                // Amplitude envelope
                final Element aegElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.AMP_ENVELOPE);
                ampEnvelope = new DefaultEnvelope ();
                ampEnvelope.setAttack (parseTime (aegElement, TX16WxTag.AMP_ENV_ATTACK));
                ampEnvelope.setDecay (parseTime (aegElement, TX16WxTag.AMP_ENV_DECAY));
                ampEnvelope.setSustain (MathUtils.dBToDouble (parseVolume (aegElement, TX16WxTag.AMP_ENV_SUSTAIN)));
                ampEnvelope.setRelease (parseTime (aegElement, TX16WxTag.AMP_ENV_RELEASE));

                final Optional<IFilter> filter = parseFilter (soundShapeElement);
                // TODO if (filter.isPresent ())

            }
        }

        for (final Element regionElement: XMLUtils.getChildElementsByName (groupElement, TX16WxTag.REGION, false))
        {
            final ISampleZone sampleZone = sampleMap.get (regionElement.getAttribute (TX16WxTag.SAMPLE));

            // Play back start not available
            // Play back stop not available

            sampleZone.setGain (groupVolumeOffset + parseVolume (regionElement, TX16WxTag.VOLUME));
            sampleZone.setPanorama (groupPanoramaOffset + parsePanorama (regionElement, TX16WxTag.PANORAMA));

            double tuning = XMLUtils.getDoubleAttribute (regionElement, TX16WxTag.TUNING_COARSE, 0) * 100;
            tuning += XMLUtils.getDoubleAttribute (regionElement, TX16WxTag.TUNING_FINE, 0);
            sampleZone.setTune (groupTuningOffset + tuning);

            // TODO seems to be in a sub-group element "switcher"
            // final String zoneLogic = this.currentGroupsElement.getAttribute (TX16WxTag.SEQ_MODE);
            // sampleZone.setPlayLogic (zoneLogic != null && "round_robin".equals (zoneLogic) ?
            // PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

            // Key tracking not available

            sampleZone.setKeyRoot (getNoteAttribute (regionElement, TX16WxTag.ROOT_NOTE));

            final Element boundsElement = XMLUtils.getChildElementByName (regionElement, TX16WxTag.BOUNDS);
            if (boundsElement != null)
            {
                sampleZone.setKeyLow (getNoteAttribute (boundsElement, TX16WxTag.LO_NOTE, TX16WxTag.LO_NOTE_ALT));
                sampleZone.setKeyHigh (getNoteAttribute (boundsElement, TX16WxTag.HI_NOTE, TX16WxTag.HI_NOTE_ALT));
                final int velLow = XMLUtils.getIntegerAttribute (boundsElement, TX16WxTag.LO_VEL, -1);
                final int velHigh = XMLUtils.getIntegerAttribute (boundsElement, TX16WxTag.HI_VEL, -1);
                if (velLow > 0)
                    sampleZone.setVelocityLow (velLow);
                if (velHigh > 0)
                    sampleZone.setVelocityHigh (velHigh);

                final Element fadeBoundsElement = XMLUtils.getChildElementByName (regionElement, TX16WxTag.FADE_BOUNDS);
                if (fadeBoundsElement != null)
                {
                    sampleZone.setNoteCrossfadeLow (getNoteAttribute (fadeBoundsElement, TX16WxTag.LO_NOTE, TX16WxTag.LO_NOTE_ALT));
                    sampleZone.setNoteCrossfadeHigh (getNoteAttribute (fadeBoundsElement, TX16WxTag.HI_NOTE, TX16WxTag.HI_NOTE_ALT));
                    final int fadeVelLow = XMLUtils.getIntegerAttribute (fadeBoundsElement, TX16WxTag.LO_VEL, -1);
                    final int fadeVelHigh = XMLUtils.getIntegerAttribute (fadeBoundsElement, TX16WxTag.HI_VEL, -1);
                    if (fadeVelLow > 0)
                        sampleZone.setVelocityCrossfadeLow (fadeVelLow);
                    if (fadeVelHigh > 0)
                        sampleZone.setVelocityCrossfadeHigh (fadeVelHigh);
                }
            }

            try
            {
                sampleZone.getSampleData ().addMetadata (sampleZone, false, false);
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            }

            sampleZone.getAmplitudeModulator ().setSource (ampEnvelope);

            group.addSampleZone (sampleZone);
        }
    }


    /**
     * Read all loops from the sample element.
     * 
     * @param sampleElement The sample element
     * @param loops The loops list
     */
    private static void readLoops (final Element sampleElement, final List<ISampleLoop> loops)
    {
        for (final Element loopElement: XMLUtils.getChildElementsByName (sampleElement, TX16WxTag.SAMPLE_LOOP, false))
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            final String loopMode = sampleElement.getAttribute (TX16WxTag.LOOP_MODE);
            if (loopMode != null)
            {
                final LoopType loopType = LOOP_MODES.get (loopMode);
                if (loopType != null)
                {
                    loop.setType (loopType);
                    loop.setStart (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_START, 0));
                    loop.setEnd (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_END, 0));
                    loop.setCrossfadeInSamples (XMLUtils.getIntegerAttribute (loopElement, TX16WxTag.LOOP_CROSSFADE, 0));
                    loops.add (loop);
                }
            }
        }
    }


    /**
     * Parse the filter in the sound shape element.
     *
     * @param soundShapeElement The sound shape element
     * @return The read filter
     */
    private static Optional<IFilter> parseFilter (final Element soundShapeElement)
    {
        final Element filterElement = XMLUtils.getChildElementByName (soundShapeElement, TX16WxTag.FILTER);
        if (filterElement == null)
            return Optional.empty ();

        final String filterTypeValue = filterElement.getAttribute (TX16WxTag.FILTER_TYPE);
        if (filterTypeValue == null)
            return Optional.empty ();

        final FilterType filterType = FILTER_TYPES.get (filterTypeValue);
        if (filterType == null)
            return Optional.empty ();

        String frequencyValue = filterElement.getAttribute (TX16WxTag.FILTER_FREQUENCY);
        if (frequencyValue == null)
            frequencyValue = filterElement.getAttribute (TX16WxTag.FILTER_CUTOFF);
        if (frequencyValue == null)
            return Optional.empty ();
        double frequency;
        if (frequencyValue.endsWith ("hz") || frequencyValue.endsWith ("Hz"))
            frequency = Double.parseDouble (frequencyValue.substring (0, frequencyValue.length () - 2).trim ());
        else if (frequencyValue.endsWith ("khz") || frequencyValue.endsWith ("kHz"))
            frequency = Double.parseDouble (frequencyValue.substring (0, frequencyValue.length () - 3).trim ()) * 1000;
        else
            return Optional.empty ();

        final double resonance = XMLUtils.getDoubleAttribute (soundShapeElement, "resonance", 0);
        // TODO parse resonance correctly
        // TODO parse poles

        return Optional.of (new DefaultFilter (filterType, 4, frequency, resonance));
    }


    /**
     * Get the value of a note element. The value can be either an integer MIDI note or a text like
     * C#5.
     *
     * @param element The element
     * @param attributeNames The name(s) of the attribute(s) from which to get the note value
     * @return The value
     */
    private static int getNoteAttribute (final Element element, final String... attributeNames)
    {
        for (final String attributeName: attributeNames)
        {
            final String attribute = element.getAttribute (attributeName);
            if (attribute != null)
                return NoteParser.parseNote (attribute);
        }
        return -1;
    }


    /**
     * Parses a volume value from the given tag.
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The volume in dB
     */
    private static double parseVolume (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in dB?
        if (attribute.endsWith ("dB"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 2).trim ());

        // The value is in the range of [0..1] but it is not specified what 0 and 1 means, lets
        // scale it to [0..6] dB.
        if (attribute.endsWith ("%"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ()) / 100.0 * 6.0;
        return Double.parseDouble (attribute) * 6.0;
    }


    /**
     * Parses a panorama value from the given tag.
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The panorama in the range of [-1..1]
     */
    private static double parsePanorama (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in percent (-100..100)?
        final double value;
        if (attribute.endsWith ("%"))
            value = Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ()) / 100.0;
        else
            // The value is in the range of [-1..1]
            value = Double.parseDouble (attribute);
        return MathUtils.clamp (value, -1.0, 1.0);
    }


    /**
     * Parses a time value from the given tag in milli-seconds.
     *
     * @param element The element which contains the attribute
     * @param tag The tag name of the attribute
     * @return The time in milli-seconds in the range of 0.1ms to 38000ms
     */
    private static double parseTime (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in milli-seconds?
        if (attribute.endsWith ("ms"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 2).trim ());
        return Double.parseDouble (attribute.substring (0, attribute.length () - 1).trim ()) * 1000;
    }
}
