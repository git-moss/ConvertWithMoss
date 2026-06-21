// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.renoise;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.FlacFileSampleData;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively Renoise instrument files in folders. Files must end with <i>.xrni</i>. An
 * XRNI file is a ZIP archive containing an <i>Instrument.xml</i> description file plus the samples
 * (WAV or FLAC) in a <i>SampleData</i> folder.
 *
 * @author Jürgen Moßgraber
 */
public class RenoiseDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String SAMPLE_DATA_FOLDER    = "sampledata/";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public RenoiseDetector (final INotifier notifier)
    {
        super ("Renoise", "Renoise", notifier, new MetadataSettingsUI ("Renoise"), ".xrni");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final ZipEntry entry = zipFile.getEntry ("Instrument.xml");
            if (entry == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_NO_METADATA_FILE");
                return Collections.emptyList ();
            }

            return this.parseInstrumentFile (file, zipFile, entry);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param sourceFile The XRNI file
     * @param zipFile The ZIP file which contains the description file
     * @param entry The ZIP entry of the description file
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the file
     */
    private List<IMultisampleSource> parseInstrumentFile (final File sourceFile, final ZipFile zipFile, final ZipEntry entry) throws IOException
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = zipFile.getInputStream (entry))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (in));
            return this.parseDescription (sourceFile, zipFile, document);
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Process the instrument metadata file and the related sample files.
     *
     * @param sourceFile The XRNI file
     * @param zipFile The ZIP file which contains the samples
     * @param document The metadata XML document
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseDescription (final File sourceFile, final ZipFile zipFile, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (top == null || !RenoiseTag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }

        String name = XMLUtils.getChildElementContent (top, RenoiseTag.NAME);
        if (name == null || name.isBlank ())
            name = FileUtils.getNameWithoutType (sourceFile);

        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, name);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, name);
        final IMetadata metadata = multisampleSource.getMetadata ();

        final Element sampleGenerator = XMLUtils.getChildElementByName (top, RenoiseTag.SAMPLE_GENERATOR);
        if (sampleGenerator == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing SampleGenerator");
            return Collections.emptyList ();
        }

        // Parse the indexed modulation sets which carry the envelopes and the sampler filter
        final List<ModulationSet> modulationSets = this.parseModulationSets (sampleGenerator);

        final String overlapMode = XMLUtils.getChildElementContent (sampleGenerator, RenoiseTag.KEYZONE_OVERLAPPING_MODE);
        final boolean roundRobin = RenoiseTag.OVERLAP_CYCLE.equals (overlapMode) || RenoiseTag.OVERLAP_RANDOM.equals (overlapMode);

        // Collect all audio files in the SampleData folder for positional matching
        final List<String> sampleEntries = collectSampleEntries (zipFile);

        final Element samplesElement = XMLUtils.getChildElementByName (sampleGenerator, RenoiseTag.SAMPLES);
        final List<Element> sampleElements = samplesElement == null ? Collections.emptyList () : XMLUtils.getChildElementsByName (samplesElement, RenoiseTag.SAMPLE, false);

        final List<ISampleZone> zones = new ArrayList<> ();
        for (int i = 0; i < sampleElements.size (); i++)
        {
            final ISampleZone zone = this.parseSample (sourceFile, sampleElements.get (i), i, sampleEntries, modulationSets);
            if (zone != null)
                zones.add (zone);
        }

        if (zones.isEmpty ())
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "No samples");
            return Collections.emptyList ();
        }

        multisampleSource.setGroups (groupByVelocity (zones, roundRobin));

        // Detect metadata from the file path and read the comments
        this.createMetadata (metadata, this.getFirstSample (multisampleSource.getGroups ()), parts);
        final String comments = readComments (top);
        if (comments != null && !comments.isBlank ())
            metadata.setDescription (comments);
        this.updateCreationDateTime (metadata, sourceFile);

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parse a single sample element into a sample zone.
     *
     * @param sourceFile The XRNI file (used as the ZIP container for the samples)
     * @param sampleElement The XML sample element
     * @param index The index of the sample (used for positional sample file matching)
     * @param sampleEntries The audio file entries found in the SampleData folder
     * @param modulationSets The parsed modulation sets
     * @return The sample zone or null if the sample could not be loaded
     */
    private ISampleZone parseSample (final File sourceFile, final Element sampleElement, final int index, final List<String> sampleEntries, final List<ModulationSet> modulationSets)
    {
        final String fileName = XMLUtils.getChildElementContent (sampleElement, RenoiseTag.FILE_NAME);

        final ISampleData sampleData = this.loadSampleData (sourceFile, fileName, index, sampleEntries);
        if (sampleData == null)
            return null;

        String name = XMLUtils.getChildElementContent (sampleElement, RenoiseTag.NAME);
        if (name == null || name.isBlank ())
            name = fileName == null || fileName.isBlank () ? "Sample " + (index + 1) : fileName;

        final ISampleZone zone = new DefaultSampleZone (name, sampleData);

        zone.setGain (RenoiseValueConverter.volumeToGain (XMLUtils.getChildElementDoubleContent (sampleElement, RenoiseTag.VOLUME, 1.0)));
        zone.setPanning (RenoiseValueConverter.panningToModel (XMLUtils.getChildElementDoubleContent (sampleElement, RenoiseTag.PANNING, 0.5)));
        final int transpose = XMLUtils.getChildElementIntegerContent (sampleElement, RenoiseTag.TRANSPOSE, 0);
        final int finetune = XMLUtils.getChildElementIntegerContent (sampleElement, RenoiseTag.FINETUNE, 0);
        zone.setTuning (RenoiseValueConverter.toTuning (transpose, finetune));

        zone.setStart (0);
        try
        {
            zone.setStop (sampleData.getAudioMetadata ().getNumberOfSamples ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BROKEN_WAV", ex);
        }

        // Key/velocity mapping
        final Element mapping = XMLUtils.getChildElementByName (sampleElement, RenoiseTag.MAPPING);
        if (mapping != null)
        {
            zone.setKeyRoot (XMLUtils.getChildElementIntegerContent (mapping, RenoiseTag.BASE_NOTE, 48));
            zone.setKeyLow (XMLUtils.getChildElementIntegerContent (mapping, RenoiseTag.NOTE_START, 0));
            zone.setKeyHigh (XMLUtils.getChildElementIntegerContent (mapping, RenoiseTag.NOTE_END, RenoiseValueConverter.MAX_NOTE));
            zone.setVelocityLow (Math.max (1, XMLUtils.getChildElementIntegerContent (mapping, RenoiseTag.VELOCITY_START, 0)));
            zone.setVelocityHigh (XMLUtils.getChildElementIntegerContent (mapping, RenoiseTag.VELOCITY_END, 127));

            if (RenoiseTag.LAYER_NOTE_OFF.equals (XMLUtils.getChildElementContent (mapping, RenoiseTag.LAYER)))
                zone.setTrigger (TriggerType.RELEASE);
        }

        // Loop
        final String loopMode = XMLUtils.getChildElementContent (sampleElement, RenoiseTag.LOOP_MODE);
        if (loopMode != null && !loopMode.isBlank () && !RenoiseTag.LOOP_OFF.equals (loopMode))
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            switch (loopMode)
            {
                case RenoiseTag.LOOP_BACKWARD -> loop.setType (LoopType.BACKWARDS);
                case RenoiseTag.LOOP_PING_PONG -> loop.setType (LoopType.ALTERNATING);
                default -> loop.setType (LoopType.FORWARDS);
            }
            loop.setStart (XMLUtils.getChildElementIntegerContent (sampleElement, RenoiseTag.LOOP_START, 0));
            loop.setEnd (XMLUtils.getChildElementIntegerContent (sampleElement, RenoiseTag.LOOP_END, zone.getStop ()));
            zone.addLoop (loop);
        }

        // Envelopes (amplitude/pitch) and the sampler filter from the referenced modulation set
        final int modulationSetIndex = XMLUtils.getChildElementIntegerContent (sampleElement, RenoiseTag.MODULATION_SET_INDEX, -1);
        final ModulationSet modulationSet = modulationSetIndex >= 0 && modulationSetIndex < modulationSets.size () ? modulationSets.get (modulationSetIndex) : null;
        if (modulationSet != null)
        {
            if (modulationSet.volumeEnvelope != null)
                zone.getAmplitudeEnvelopeModulator ().setSource (modulationSet.volumeEnvelope);
            if (modulationSet.pitchEnvelope != null)
            {
                zone.getPitchEnvelopeModulator ().setSource (modulationSet.pitchEnvelope);
                zone.getPitchEnvelopeModulator ().setDepth (1.0);
            }
            final IFilter filter = modulationSet.createFilter ();
            if (filter != null)
                zone.setFilter (filter);
        }

        return zone;
    }


    /**
     * Load the sample data for a sample. If the sample references a file name it is matched against
     * the entries in the SampleData folder, otherwise (and as a fall-back) the sample is matched
     * positionally by its index.
     *
     * @param sourceFile The XRNI file (the ZIP container)
     * @param fileName The file name reference of the sample (may be null)
     * @param index The index of the sample
     * @param sampleEntries The audio file entries found in the SampleData folder
     * @return The sample data or null if it could not be loaded
     */
    private ISampleData loadSampleData (final File sourceFile, final String fileName, final int index, final List<String> sampleEntries)
    {
        // Renoise resolves samples positionally by index. The FileName element holds the (absolute)
        // path on the original author's machine, so it is only used as a fall-back match.
        String entryName = index < sampleEntries.size () ? sampleEntries.get (index) : null;
        if (entryName == null)
            entryName = findSampleEntry (fileName, sampleEntries);
        if (entryName == null)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", fileName == null ? "?" : fileName);
            return null;
        }

        try
        {
            return createZipSampleData (sourceFile, entryName);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND", ex);
            return null;
        }
    }


    /**
     * Find the SampleData entry that matches the given file name (by base name, ignoring path and
     * extension).
     *
     * @param fileName The file name reference (may be null)
     * @param sampleEntries The audio file entries found in the SampleData folder
     * @return The matching entry name or null if there is no match
     */
    private static String findSampleEntry (final String fileName, final List<String> sampleEntries)
    {
        if (fileName == null || fileName.isBlank ())
            return null;

        final String needle = baseName (fileName).toLowerCase (Locale.US);
        for (final String entry: sampleEntries)
            if (baseName (entry).toLowerCase (Locale.US).equals (needle))
                return entry;
        return null;
    }


    /**
     * Create the sample data for an audio file stored in the ZIP.
     *
     * @param zipFile The ZIP file (the XRNI file)
     * @param entryName The relative path of the audio file in the ZIP
     * @return The sample data
     * @throws IOException Could not read the file or the format is not supported
     */
    private static ISampleData createZipSampleData (final File zipFile, final String entryName) throws IOException
    {
        final File entryFile = new File (entryName);
        final String lower = entryName.toLowerCase (Locale.US);
        if (lower.endsWith (".wav"))
            return new WavFileSampleData (zipFile, entryFile);
        if (lower.endsWith (".flac"))
            return new FlacFileSampleData (zipFile, entryFile);
        if (lower.endsWith (".aif") || lower.endsWith (".aiff"))
            return new AiffFileSampleData (zipFile, entryFile);
        throw new IOException ("Unsupported sample format: " + entryName);
    }


    /**
     * Collect all audio file entries that reside in the SampleData folder, sorted by name (so the
     * default zero-padded Renoise naming yields the correct play order).
     *
     * @param zipFile The ZIP file
     * @return The sorted list of entry names
     */
    private static List<String> collectSampleEntries (final ZipFile zipFile)
    {
        final List<String> entries = new ArrayList<> ();
        final Enumeration<? extends ZipEntry> enumeration = zipFile.entries ();
        while (enumeration.hasMoreElements ())
        {
            final ZipEntry entry = enumeration.nextElement ();
            if (entry.isDirectory ())
                continue;
            final String entryName = entry.getName ();
            final String lower = entryName.toLowerCase (Locale.US);
            if (lower.startsWith (SAMPLE_DATA_FOLDER) && (lower.endsWith (".wav") || lower.endsWith (".flac") || lower.endsWith (".aif") || lower.endsWith (".aiff")))
                entries.add (entryName);
        }
        Collections.sort (entries);
        return entries;
    }


    /**
     * Parse all modulation sets into an indexed list. Each set carries the optional amplitude
     * (Volume), pitch and filter (Cutoff) envelopes from its AHDSR devices.
     *
     * @param sampleGenerator The sample generator element
     * @return The indexed list of modulation sets
     */
    private List<ModulationSet> parseModulationSets (final Element sampleGenerator)
    {
        final List<ModulationSet> result = new ArrayList<> ();
        final Element modulationSetsElement = XMLUtils.getChildElementByName (sampleGenerator, RenoiseTag.MODULATION_SETS);
        if (modulationSetsElement == null)
            return result;

        for (final Element modulationSetElement: XMLUtils.getChildElementsByName (modulationSetsElement, RenoiseTag.MODULATION_SET, false))
        {
            final ModulationSet modulationSet = new ModulationSet ();
            modulationSet.filterTypeIndex = XMLUtils.getChildElementIntegerContent (modulationSetElement, RenoiseTag.FILTER_TYPE, RenoiseFilterType.INDEX_NONE);

            final Element devicesElement = XMLUtils.getChildElementByName (modulationSetElement, RenoiseTag.DEVICES);
            if (devicesElement != null)
            {
                for (final Element deviceElement: XMLUtils.getChildElementsByName (devicesElement, RenoiseTag.AHDSR_DEVICE, false))
                {
                    final IEnvelope envelope = readEnvelope (deviceElement);
                    final String target = XMLUtils.getChildElementContent (deviceElement, RenoiseTag.TARGET);
                    if (RenoiseTag.TARGET_PITCH.equals (target))
                        modulationSet.pitchEnvelope = envelope;
                    else if (RenoiseTag.TARGET_CUTOFF.equals (target))
                        modulationSet.cutoffEnvelope = envelope;
                    else
                        modulationSet.volumeEnvelope = envelope;
                }

                // The base cutoff/resonance of the sampler filter are stored in the mixer device
                final Element mixerElement = XMLUtils.getChildElementByName (devicesElement, RenoiseTag.MIXER_DEVICE);
                if (mixerElement != null)
                {
                    modulationSet.cutoffValue = paramValue (mixerElement, RenoiseTag.CUTOFF, -1);
                    modulationSet.resonanceValue = paramValue (mixerElement, RenoiseTag.RESONANCE, -1);
                }
            }

            result.add (modulationSet);
        }
        return result;
    }


    /**
     * Read an AHDSR envelope from a modulation device.
     *
     * @param deviceElement The AHDSR device element
     * @return The envelope
     */
    private static IEnvelope readEnvelope (final Element deviceElement)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (RenoiseValueConverter.timeToSeconds (paramValue (deviceElement, RenoiseTag.ATTACK, 0)));
        envelope.setHoldTime (RenoiseValueConverter.timeToSeconds (paramValue (deviceElement, RenoiseTag.HOLD, 0)));
        envelope.setDecayTime (RenoiseValueConverter.timeToSeconds (paramValue (deviceElement, RenoiseTag.DECAY, 0)));
        envelope.setSustainLevel (Math.clamp (paramValue (deviceElement, RenoiseTag.SUSTAIN, 1.0), 0, 1));
        envelope.setReleaseTime (RenoiseValueConverter.timeToSeconds (paramValue (deviceElement, RenoiseTag.RELEASE, 0)));
        return envelope;
    }


    /**
     * Read the {@code Value} of a device parameter (a parameter element wrapping a {@code Value}
     * child).
     *
     * @param parent The device element
     * @param parameterName The name of the parameter element
     * @param defaultValue The default value if the parameter is not present
     * @return The parameter value
     */
    private static double paramValue (final Element parent, final String parameterName, final double defaultValue)
    {
        final Element parameter = XMLUtils.getChildElementByName (parent, parameterName);
        if (parameter == null)
            return defaultValue;
        return XMLUtils.getChildElementDoubleContent (parameter, RenoiseTag.VALUE, defaultValue);
    }


    /**
     * Read the joined comment lines of the instrument.
     *
     * @param top The root element
     * @return The comments joined by line-feeds or null if there are none
     */
    private static String readComments (final Element top)
    {
        final Element globalProperties = XMLUtils.getChildElementByName (top, RenoiseTag.GLOBAL_PROPERTIES);
        if (globalProperties == null)
            return null;
        final Element commentsElement = XMLUtils.getChildElementByName (globalProperties, RenoiseTag.COMMENTS);
        if (commentsElement == null)
            return null;

        final StringBuilder sb = new StringBuilder ();
        for (final Element commentElement: XMLUtils.getChildElementsByName (commentsElement, RenoiseTag.COMMENT, false))
        {
            if (!sb.isEmpty ())
                sb.append ('\n');
            sb.append (XMLUtils.readTextContent (commentElement));
        }
        return sb.toString ();
    }


    /**
     * Group the flat list of Renoise samples into groups by their velocity range (each distinct
     * velocity range becomes one group / velocity layer).
     *
     * @param zones The parsed zones
     * @param roundRobin True to additionally flag overlapping same-range zones as round-robin
     * @return The groups
     */
    private static List<IGroup> groupByVelocity (final List<ISampleZone> zones, final boolean roundRobin)
    {
        final Map<String, IGroup> groups = new LinkedHashMap<> ();
        for (final ISampleZone zone: zones)
        {
            final String key = zone.getVelocityLow () + "-" + zone.getVelocityHigh ();
            groups.computeIfAbsent (key, k -> new DefaultGroup ("Velocity " + k)).addSampleZone (zone);
        }

        if (roundRobin)
            for (final IGroup group: groups.values ())
            {
                // Zones that share an identical key and velocity range form a round-robin set
                final Map<String, Integer> counts = new HashMap<> ();
                for (final ISampleZone zone: group.getSampleZones ())
                    counts.merge (rangeKey (zone), Integer.valueOf (1), Integer::sum);

                final Map<String, Integer> positions = new HashMap<> ();
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    final String key = rangeKey (zone);
                    if (counts.get (key).intValue () > 1)
                    {
                        zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                        zone.setSequencePosition (positions.merge (key, Integer.valueOf (1), Integer::sum).intValue ());
                    }
                }
            }

        return new ArrayList<> (groups.values ());
    }


    /**
     * Build a key identifying the key and velocity range of a zone.
     *
     * @param zone The zone
     * @return The range key
     */
    private static String rangeKey (final ISampleZone zone)
    {
        return zone.getKeyLow () + ":" + zone.getKeyHigh () + ":" + zone.getVelocityLow () + ":" + zone.getVelocityHigh ();
    }


    /**
     * Get the base name of a file path (without folder and without extension).
     *
     * @param path The path
     * @return The base name
     */
    private static String baseName (final String path)
    {
        String result = path.replace ('\\', '/');
        final int slash = result.lastIndexOf ('/');
        if (slash >= 0)
            result = result.substring (slash + 1);
        final int dot = result.lastIndexOf ('.');
        if (dot >= 0)
            result = result.substring (0, dot);
        return result;
    }


    /**
     * Holds the envelopes and the sampler filter of one Renoise modulation set.
     */
    private static class ModulationSet
    {
        IEnvelope volumeEnvelope;
        IEnvelope pitchEnvelope;
        IEnvelope cutoffEnvelope;
        int       filterTypeIndex = RenoiseFilterType.INDEX_NONE;
        double    cutoffValue     = -1;
        double    resonanceValue  = -1;


        /**
         * Create the filter described by this modulation set.
         *
         * @return The filter or null if this set does not select a (plain) filter
         */
        IFilter createFilter ()
        {
            final FilterType filterType = RenoiseFilterType.fromFilterTypeIndex (this.filterTypeIndex);
            if (filterType == null)
                return null;

            final double cutoff = this.cutoffValue < 0 ? IFilter.MAX_FREQUENCY : RenoiseValueConverter.mixerToCutoff (this.cutoffValue);
            final double resonance = this.resonanceValue < 0 ? 0 : RenoiseValueConverter.mixerToResonance (this.resonanceValue);
            final IFilter filter = new DefaultFilter (filterType, 2, cutoff, resonance);
            if (this.cutoffEnvelope != null)
            {
                filter.getCutoffEnvelopeModulator ().setSource (this.cutoffEnvelope);
                filter.getCutoffEnvelopeModulator ().setDepth (1.0);
            }
            return filter;
        }
    }
}
