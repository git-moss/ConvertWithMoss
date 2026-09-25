// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.algorithm.ZoneSplitter;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
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
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Ableton Preset/Rack-Preset files, Live Sets and Live Packs in folders. Files
 * must end with <i>.adv</i>, <i>.adg</i>, <i>.als</i> or <i>.alp</i>. The Sampler and Simpler
 * devices of a Live Set are read from all of its tracks, those of a Live Pack from all presets and
 * Live Sets which it contains.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String                  ERR_MISSING_TAG     = "IDS_NOTIFY_ERR_MISSING_TAG";

    private static final String                  ENDING_PRESET       = ".adv";
    private static final String                  ENDING_RACK         = ".adg";
    private static final String                  ENDING_SET          = ".als";
    private static final String                  ENDING_PACK         = ".alp";

    /** The name of the folder which is present in every Ableton project folder. */
    private static final String                  PROJECT_INFO_FOLDER = "Ableton Project Info";
    /** The number of folders to move upwards to start a search for a sample file. */
    private static final int                     SEARCH_LEVELS       = 1;

    private static final Map<String, FilterType> FILTER_TYPES        = new HashMap<> ();
    static
    {
        FILTER_TYPES.put ("0", FilterType.LOW_PASS);
        FILTER_TYPES.put ("1", FilterType.HIGH_PASS);
        FILTER_TYPES.put ("2", FilterType.BAND_PASS);
        FILTER_TYPES.put ("3", FilterType.BAND_REJECTION);
    }

    private File                                 previousSampleFolder;
    /** The Live Packs which were opened by the current detection, by their files. */
    private final Map<String, AbletonLivePack>   livePacks           = new HashMap<> ();


    /**
     * Loads the sample of a file reference of a device, from the file system or from a Live Pack.
     */
    @FunctionalInterface
    private interface ISampleLoader
    {
        /**
         * Load the sample of a file reference.
         *
         * @param fileRefElement The file reference element
         * @return The sample data or null if the sample was not found, which is already reported
         * @throws IOException Could not access the sample
         */
        ISampleData loadSample (Element fileRefElement) throws IOException;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AbletonDetector (final INotifier notifier)
    {
        super ("Ableton Sampler", "Ableton", notifier, new MetadataSettingsUI ("Ableton"), ENDING_PRESET, ENDING_RACK, ENDING_SET, ENDING_PACK);
    }


    /** {@inheritDoc} */
    @Override
    protected void startDetection ()
    {
        // The samples of the packs of the previous detection are not read anymore
        this.disposeLivePacks ();
        super.startDetection ();
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.disposeLivePacks ();
        super.shutdown ();
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        this.previousSampleFolder = null;
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            if (hasEnding (file.getName (), ENDING_PACK))
                return this.readLivePack (file);

            final Optional<Element> top = this.readDocument (Files.readAllBytes (file.toPath ()));
            if (top.isEmpty ())
                return Collections.emptyList ();

            final String name = FileUtils.getNameWithoutType (file);
            final Function<String, IMultisampleSource> sourceFactory = sourceName -> this.createMultisampleSource (file, sourceName);
            if (hasEnding (file.getName (), ENDING_SET))
            {
                // The samples of a Live Set are stored relative to its project folder, which
                // contains the Live Set
                final File projectFolder = file.getParentFile ();
                final List<IMultisampleSource> multisampleSources = this.parseLiveSet (top.get (), name, sourceFactory, fileRef -> this.getSampleData (file, fileRef, projectFolder));
                if (multisampleSources.isEmpty ())
                    this.notifier.logError ("IDS_ADV_NO_SAMPLER_IN_SET");
                return multisampleSources;
            }

            final Optional<Pair<Element, List<Element>>> devices = getPresetDevices (top.get ());
            if (devices.isEmpty ())
            {
                this.notifier.logError ("IDS_ADV_NOT_A_SAMPLER_PRESET");
                return Collections.emptyList ();
            }
            final File rootPath = getRootPath (file, devices.get ().getKey ());
            return this.parsePresetDevices (top.get (), devices.get ().getValue (), name, sourceFactory, fileRef -> this.getSampleData (file, fileRef, rootPath));
        }
        catch (final SAXException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE", ex);
            return Collections.emptyList ();
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Read the Sampler and Simpler devices of all presets and Live Sets of a Live Pack. Their
     * samples are read from the pack as well.
     *
     * @param packFile The pack file
     * @return The multi-samples
     * @throws IOException Could not read the pack
     */
    private List<IMultisampleSource> readLivePack (final File packFile) throws IOException
    {
        final AbletonLivePack pack = this.openLivePack (packFile);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        int numberWithoutDevices = 0;
        int numberOfBinaryPresets = 0;
        for (final AbletonLivePack.Entry entry: pack.getFiles ())
        {
            final String entryName = entry.getName ();
            final boolean isSet = hasEnding (entryName, ENDING_SET);
            if (!isSet && !hasEnding (entryName, ENDING_PRESET) && !hasEnding (entryName, ENDING_RACK))
                continue;
            if (this.waitForDelivery ())
                break;

            this.notifier.log ("IDS_NOTIFY_ANALYZING", packFile.getAbsolutePath () + File.separator + entry.path ().replace ('/', File.separatorChar));
            try
            {
                final byte [] content = decompress (pack.readContent (entry));
                if (isBinaryFormat (content))
                {
                    numberOfBinaryPresets++;
                    continue;
                }
                final Element top = parseXml (content);
                if (!AbletonTag.TAG_ROOT.equals (top.getNodeName ()))
                    throw new IOException (Functions.getMessage (ERR_MISSING_TAG, AbletonTag.TAG_ROOT));

                final String name = FileUtils.getNameWithoutType (new File (entryName));
                final String folder = entry.getFolder ();
                final Function<String, IMultisampleSource> sourceFactory = sourceName -> this.createPackMultisampleSource (packFile, folder, sourceName);
                final ISampleLoader sampleLoader = fileRef -> this.getPackSampleData (pack, folder, fileRef);
                final List<IMultisampleSource> sources;
                if (isSet)
                    sources = this.parseLiveSet (top, name, sourceFactory, sampleLoader);
                else
                {
                    final Optional<Pair<Element, List<Element>>> devices = getPresetDevices (top);
                    sources = devices.isEmpty () ? Collections.emptyList () : this.parsePresetDevices (top, devices.get ().getValue (), name, sourceFactory, sampleLoader);
                }
                if (sources.isEmpty ())
                    numberWithoutDevices++;
                multisampleSources.addAll (sources);
            }
            catch (final IOException | SAXException ex)
            {
                this.notifier.logError ("IDS_ADV_PACK_ENTRY_FAILED", entry.path (), ex.getMessage ());
            }
        }

        if (numberOfBinaryPresets > 0)
            this.notifier.logError ("IDS_ADV_PACK_BINARY_PRESETS", Integer.toString (numberOfBinaryPresets));
        if (numberWithoutDevices > 0)
            this.notifier.log ("IDS_ADV_PACK_WITHOUT_SAMPLER", Integer.toString (numberWithoutDevices));
        if (multisampleSources.isEmpty ())
            this.notifier.logError ("IDS_ADV_PACK_NO_SAMPLER");
        return multisampleSources;
    }


    /**
     * Open a Live Pack. A pack stays open until the next detection starts, since its samples are
     * read when the multi-samples are written, and a pack is read again when only one of its
     * presets is needed, e.g. to play it in the contents dialog - the de-compression of a large
     * pack takes some seconds.
     *
     * @param packFile The pack file
     * @return The pack
     * @throws IOException Could not open the pack
     */
    private AbletonLivePack openLivePack (final File packFile) throws IOException
    {
        final String key = packFile.getCanonicalPath () + "|" + packFile.length () + "|" + packFile.lastModified ();
        synchronized (this.livePacks)
        {
            AbletonLivePack pack = this.livePacks.get (key);
            if (pack == null)
            {
                pack = AbletonLivePack.open (packFile, this.notifier);
                this.livePacks.put (key, pack);
            }
            return pack;
        }
    }


    /**
     * Delete the temporary files of all opened Live Packs.
     */
    private void disposeLivePacks ()
    {
        synchronized (this.livePacks)
        {
            for (final AbletonLivePack pack: this.livePacks.values ())
                pack.dispose ();
            this.livePacks.clear ();
        }
    }


    /**
     * Read the XML document of a preset or Live Set.
     *
     * @param content The content of the file
     * @return The top element of the document, empty if the file is not supported, which is
     *         already reported
     * @throws IOException Could not de-compress the file
     * @throws SAXException Could not parse the XML document
     */
    private Optional<Element> readDocument (final byte [] content) throws IOException, SAXException
    {
        final byte [] data = decompress (content);
        if (isBinaryFormat (data))
        {
            this.notifier.logError ("IDS_ADV_BINARY_FORMAT");
            return Optional.empty ();
        }

        final Element top = parseXml (data);
        if (!AbletonTag.TAG_ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_MISSING_TAG, AbletonTag.TAG_ROOT);
            return Optional.empty ();
        }
        return Optional.of (top);
    }


    /**
     * Create the multi-samples of the Sampler and Simpler devices of a preset.
     *
     * @param top The top element of the preset
     * @param devices The Sampler and Simpler devices of the preset
     * @param name The name of the preset
     * @param sourceFactory Creates a multi-sample source with the given name
     * @param sampleLoader Loads the samples of the devices
     * @return The multi-samples
     * @throws IOException Could not parse a device
     */
    private List<IMultisampleSource> parsePresetDevices (final Element top, final List<Element> devices, final String name, final Function<String, IMultisampleSource> sourceFactory, final ISampleLoader sampleLoader) throws IOException
    {
        final String creator = top.getAttribute (AbletonTag.ATTR_CREATOR);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        final boolean multiple = devices.size () > 1;
        for (int i = 0; i < devices.size (); i++)
        {
            final IMultisampleSource multiSample = sourceFactory.apply (name);
            this.parseSampler (multiSample, devices.get (i), sampleLoader, creator);
            multisampleSources.add (multiSample);
            // Create unique names if there are multiple ones
            if (multiple)
                multiSample.setName (FileUtils.getNameWithoutType (new File (multiSample.getName ())) + (i + 1));
        }
        return multisampleSources;
    }


    /**
     * Create the multi-samples of all Sampler and Simpler devices of a Live Set, from all of its
     * tracks and the racks on them. They are named after the Live Set and their track. A device
     * without any sample, e.g. an empty Simpler on a track, is left out.
     *
     * @param top The top element of the Live Set
     * @param setName The name of the Live Set
     * @param sourceFactory Creates a multi-sample source with the given name
     * @param sampleLoader Loads the samples of the devices
     * @return The multi-samples
     * @throws IOException Could not parse a device
     */
    private List<IMultisampleSource> parseLiveSet (final Element top, final String setName, final Function<String, IMultisampleSource> sourceFactory, final ISampleLoader sampleLoader) throws IOException
    {
        final Element liveSetElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_LIVE_SET);
        final Element tracksElement = liveSetElement == null ? null : XMLUtils.getChildElementByName (liveSetElement, AbletonTag.TAG_TRACKS);
        if (tracksElement == null)
            return Collections.emptyList ();

        final List<Pair<String, List<Element>>> tracks = new ArrayList<> ();
        int numberOfDevices = 0;
        for (final Element trackElement: XMLUtils.getChildElements (tracksElement))
        {
            final List<Element> devices = new ArrayList<> ();
            collectDevices (trackElement, devices);
            if (!devices.isEmpty ())
            {
                tracks.add (new Pair<> (getTrackName (trackElement), devices));
                numberOfDevices += devices.size ();
            }
        }

        final String creator = top.getAttribute (AbletonTag.ATTR_CREATOR);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final Pair<String, List<Element>> track: tracks)
        {
            final List<Element> devices = track.getValue ();
            for (int i = 0; i < devices.size (); i++)
            {
                final StringBuilder name = new StringBuilder (setName);
                if (numberOfDevices > 1)
                    name.append (" - ").append (track.getKey ());
                if (devices.size () > 1)
                    name.append (' ').append (i + 1);
                final IMultisampleSource multiSample = sourceFactory.apply (name.toString ());
                this.parseSampler (multiSample, devices.get (i), sampleLoader, creator);
                multisampleSources.add (multiSample);
            }
        }
        return multisampleSources;
    }


    /**
     * Create a multi-sample source for a preset or Live Set of a Live Pack. The folders of the
     * pack are used like the folders of the file system, as if the pack was unpacked into a folder
     * which is named like the pack file, which is what Live does.
     *
     * @param packFile The pack file
     * @param folder The path of the folder in the pack which contains the preset or Live Set
     * @param name The name of the multi-sample
     * @return The multi-sample source
     */
    private IMultisampleSource createPackMultisampleSource (final File packFile, final String folder, final String name)
    {
        final String n = this.settingsConfiguration.isPreferFolderName () ? this.sourceFolder.getName () : name;
        final List<String> parts = new ArrayList<> ();
        parts.add (n);
        if (!folder.isEmpty ())
        {
            final String [] folders = folder.split ("/");
            for (int i = folders.length - 1; i >= 0; i--)
                parts.add (SafeFileNames.create (folders[i]));
        }
        parts.add (FileUtils.getNameWithoutType (packFile));
        final String [] outerParts = AudioFileUtils.createPathParts (packFile.getParentFile (), this.sourceFolder, n);
        parts.addAll (Arrays.asList (outerParts).subList (1, outerParts.length));
        return this.createMultisampleSource (packFile, parts.toArray (new String [parts.size ()]), name, Collections.emptyList ());
    }


    /**
     * Get the sample of a file reference of a preset or Live Set of a Live Pack. The path of a
     * sample is relative to a folder which contains the preset - its project folder or the folder
     * of its library - from where it continues with e.g. 'Samples/Imported'. It is therefore
     * looked up from the folder of the preset upwards to the root of the pack. If it is not found
     * there, the sample with the name of the file which is closest to the preset is taken.
     *
     * @param pack The pack
     * @param presetFolder The path of the folder in the pack which contains the preset
     * @param fileRefElement The file reference element
     * @return The sample data or null if the sample was not found
     * @throws IOException Could not read the sample
     */
    private ISampleData getPackSampleData (final AbletonLivePack pack, final String presetFolder, final Element fileRefElement) throws IOException
    {
        final String relativePath = getRelativePath (fileRefElement);
        Optional<AbletonLivePack.Entry> entry = Optional.empty ();
        if (!relativePath.isBlank ())
        {
            String folder = presetFolder;
            while (true)
            {
                entry = pack.findFile (folder.isEmpty () ? relativePath : folder + "/" + relativePath);
                if (entry.isPresent () || folder.isEmpty ())
                    break;
                final int pos = folder.lastIndexOf ('/');
                folder = pos < 0 ? "" : folder.substring (0, pos);
            }
        }

        final String sampleFileName = getSampleFileName (fileRefElement);
        if (entry.isEmpty () && !sampleFileName.isBlank ())
            entry = pack.findFileByName (sampleFileName, presetFolder);

        if (entry.isEmpty ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", new File (pack.getFile (), relativePath.isBlank () ? sampleFileName : relativePath).getPath ());
            return null;
        }
        return pack.createSampleData (entry.get (), this.notifier);
    }


    /**
     * Parse a Simpler or Sampler device and store it in the multi-sample source.
     *
     * @param multisampleSource The multi-sample source to fill
     * @param deviceElement The device element
     * @param sampleLoader Loads the samples of the device
     * @param creator The creator value
     * @throws IOException Could not parse the device
     */
    private void parseSampler (final IMultisampleSource multisampleSource, final Element deviceElement, final ISampleLoader sampleLoader, final String creator) throws IOException
    {
        parseMetadata (deviceElement, multisampleSource.getMetadata (), creator);
        this.parseMultiSample (multisampleSource, deviceElement, sampleLoader);
    }


    /**
     * Find the root path which contains the preset as well as its samples.
     *
     * @param multiSampleFile The ADV file in case it is needed to search upwards
     * @param deviceElement The device element which contains the sample info to search
     * @return The sample file
     * @throws IOException Could not find the info
     */
    private static File getRootPath (final File multiSampleFile, final Element deviceElement) throws IOException
    {
        Element presetRefElement = XMLUtils.getChildElementByName (deviceElement, AbletonTag.TAG_PRESET_REF);
        final Element valueElement;
        if (presetRefElement == null)
        {
            presetRefElement = getRequiredElement (deviceElement, AbletonTag.TAG_LAST_PRESET_REF);
            valueElement = getRequiredElement (presetRefElement, AbletonTag.TAG_VALUE);
        }
        else
            valueElement = presetRefElement;

        Element filePresetRefElement = XMLUtils.getChildElementByName (valueElement, AbletonTag.TAG_FILE_PRESET_REF);
        if (filePresetRefElement == null)
            filePresetRefElement = XMLUtils.getChildElementByName (valueElement, AbletonTag.TAG_FILE_PRESET_REF2);

        if (filePresetRefElement == null)
            return findSampleFolder (multiSampleFile);

        final Element fileRefElement = getRequiredElement (filePresetRefElement, AbletonTag.TAG_FILE_REF);

        final String filePath;
        final int type;
        try
        {
            final String relativePathType = getValueAttribute (fileRefElement, AbletonTag.TAG_RELATIVE_PATH_TYPE);
            type = Integer.parseInt (relativePathType);
        }
        catch (final NumberFormatException _)
        {
            throw new IOException (Functions.getMessage (ERR_MISSING_TAG, AbletonTag.TAG_RELATIVE_PATH_TYPE));
        }

        switch (type)
        {
            case 1:
                // no idea what to make out of this relative data. Therefore, search upwards till
                // the Sample folder is found...
                return findSampleFolder (multiSampleFile);

            case 5:
            case 6:
            default:
                final String relativePresetPath = getRelativePath (fileRefElement);
                filePath = createUpwardsPath (relativePresetPath);
                return new File (multiSampleFile.getParent (), filePath).getCanonicalFile ();
        }
    }


    /**
     * Parse all zone information from the XML code and store it in the multi-sample.
     *
     * @param multisampleSource Where to store the data
     * @param deviceElement The device element which contains the zone data
     * @param sampleLoader Loads the samples of the zones
     * @throws IOException Could not access the sample
     */
    private void parseMultiSample (final IMultisampleSource multisampleSource, final Element deviceElement, final ISampleLoader sampleLoader) throws IOException
    {
        final Element playerElement = getRequiredElement (deviceElement, AbletonTag.TAG_PLAYER);
        final Element mapElement = getRequiredElement (playerElement, AbletonTag.TAG_MULTI_SAMPLE_MAP);
        final Element samplePartsElement = getRequiredElement (mapElement, AbletonTag.TAG_SAMPLE_PARTS);

        final IGroup group = new DefaultGroup ("Group #1");
        final Map<ISampleZone, int []> selectorRanges = new HashMap<> ();

        for (final Element multiSamplePartElement: XMLUtils.getChildElementsByName (samplePartsElement, AbletonTag.TAG_MULTI_SAMPLE_PART, false))
        {
            final String zoneName = getValueAttribute (multiSamplePartElement, AbletonTag.TAG_NAME);

            final Element sampleRefElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_SAMPLE_REF);
            final Element fileRefElement = getRequiredElement (sampleRefElement, AbletonTag.TAG_FILE_REF);

            final ISampleData sampleData = sampleLoader.loadSample (fileRefElement);
            if (sampleData != null)
            {
                final String name = FileUtils.getNameWithoutType (new File (zoneName));
                final ISampleZone zone = new DefaultSampleZone (name, sampleData);
                readZone (zone, multiSamplePartElement);
                selectorRanges.put (zone, readSelectorRange (multiSamplePartElement));
                group.addSampleZone (zone);
            }
        }

        // Round-robin support
        final List<IGroup> groups;
        if (getBooleanValueAttribute (mapElement, AbletonTag.TAG_ROUND_ROBIN_ENABLE))
            groups = applyRoundRobin (mapElement, group);
        else
            groups = applySelectorRoundRobin (group, selectorRanges);
        multisampleSource.setGroups (groups);

        final Optional<IFilter> filter = readFilter (deviceElement);
        if (filter.isPresent ())
            multisampleSource.setGlobalFilter (filter.get ());

        applyGlobalEnvelopes (deviceElement, multisampleSource);
        applyOneShot (deviceElement, multisampleSource);
    }


    /**
     * Read the one-shot play-back mode and apply it to all zones. It is a global setting of the
     * device and only available for a Simpler: the mode 'One-Shot' plays the sample to its end and
     * ignores a note-off as long as the sustain mode is set to 'Trigger' and not to 'Gate'.
     *
     * @param deviceElement The device element
     * @param multisampleSource The multi-sample source which contains the zones to update
     */
    private static void applyOneShot (final Element deviceElement, final IMultisampleSource multisampleSource)
    {
        final Element globalsElement = XMLUtils.getChildElementByName (deviceElement, AbletonTag.TAG_GLOBALS);
        if (globalsElement == null || getIntegerValueAttribute (globalsElement, AbletonTag.TAG_PLAYBACK_MODE, 0) != AbletonTag.PLAYBACK_MODE_ONE_SHOT)
            return;

        // 'Gate' stops the play-back on a note-off, therefore only 'Trigger' is a real one-shot
        final Element volumeAndPanElement = XMLUtils.getChildElementByName (deviceElement, AbletonTag.TAG_VOLUME_AND_PAN);
        final Element oneShotEnvelopeElement = volumeAndPanElement == null ? null : XMLUtils.getChildElementByName (volumeAndPanElement, AbletonTag.TAG_ONE_SHOT_ENVELOPE);
        final Element sustainModeElement = oneShotEnvelopeElement == null ? null : XMLUtils.getChildElementByName (oneShotEnvelopeElement, AbletonTag.TAG_SUSTAIN_MODE);
        if (sustainModeElement != null && getIntegerValueAttribute (sustainModeElement, AbletonTag.TAG_MANUAL, AbletonTag.SUSTAIN_MODE_TRIGGER) != AbletonTag.SUSTAIN_MODE_TRIGGER)
            return;

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                zone.setOneShot (true);
    }


    /**
     * Read all zone data.
     *
     * @param zone The zone to fill
     * @param multiSamplePartElement The XML element with the zone info
     * @throws IOException A required tag is missing
     */
    private static void readZone (final ISampleZone zone, final Element multiSamplePartElement) throws IOException
    {
        final Element keyRangeElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_KEY_RANGE);
        final int lowKey = getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_MINIMUM, 0);
        final int highKey = getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_MAXIMUM, 127);
        final int lowKeyFade = getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_CROSSFADE_MINIMUM, lowKey);
        final int highKeyFade = getIntegerValueAttribute (keyRangeElement, AbletonTag.TAG_CROSSFADE_MAXIMUM, highKey);
        zone.setKeyLow (lowKey);
        zone.setKeyHigh (highKey);
        zone.setNoteCrossfadeLow (Math.abs (lowKeyFade - lowKey));
        zone.setNoteCrossfadeHigh (Math.abs (highKeyFade - highKey));

        final Element velocityRangeElement = getRequiredElement (multiSamplePartElement, AbletonTag.TAG_VELOCITY_RANGE);
        final int velLow = getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_MINIMUM, 1);
        final int velHigh = getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_MAXIMUM, 127);
        final int veFadelLow = getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_CROSSFADE_MINIMUM, 1);
        final int velFadehigh = getIntegerValueAttribute (velocityRangeElement, AbletonTag.TAG_CROSSFADE_MAXIMUM, 127);
        zone.setVelocityLow (velLow);
        zone.setVelocityHigh (velHigh);
        zone.setVelocityCrossfadeLow (Math.abs (veFadelLow - velLow));
        zone.setVelocityCrossfadeHigh (Math.abs (velFadehigh - velHigh));

        zone.setKeyRoot (getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_ROOT_KEY, 60));
        zone.setTuning (getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_DETUNE, 0) / 100.0);
        zone.setKeyTracking (Math.clamp (getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_TUNE_SCALE, 0) / 100.0, 0, 1));
        zone.setPanning (getDoubleValueAttribute (multiSamplePartElement, AbletonTag.TAG_PANORAMA, 0));

        final double volumeVal = getDoubleValueAttribute (multiSamplePartElement, AbletonTag.TAG_VOLUME, 1);
        zone.setGain (Math.log (volumeVal) / Math.log (2) * 6.0);

        zone.setStart (getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_SAMPLE_START, 0));
        zone.setStop (getIntegerValueAttribute (multiSamplePartElement, AbletonTag.TAG_SAMPLE_END, -1));

        final Element reverseElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_REVERSE);
        zone.setReversed (reverseElement != null && "true".equals (getValueAttribute (reverseElement, AbletonTag.TAG_MANUAL)));

        final Element sustainLoopElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_SUSTAIN_LOOP);
        if (sustainLoopElement == null)
            return;
        final int loopMode = getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_MODE, 0);
        if (loopMode <= 0)
            return;
        final ISampleLoop loop = new DefaultSampleLoop ();
        loop.setStart (getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_START, 0));
        loop.setEnd (getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_END, zone.getStop ()));
        loop.setCrossfadeInSamples (getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_LOOP_CROSSFADE, 0));
        loop.setType (loopMode == 1 ? LoopType.FORWARDS : LoopType.ALTERNATING);
        // The loop detune is a child of the sustain loop element; the detune of the multi-sample
        // part is the zone tuning which was already applied above
        loop.setTuning (getIntegerValueAttribute (sustainLoopElement, AbletonTag.TAG_DETUNE, 0) / 100.0);
        zone.getLoops ().add (loop);

        final Element releaseLoopElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_RELEASE_LOOP);
        // 3 = Off
        loop.setLoopUntilRelease (releaseLoopElement != null && getIntegerValueAttribute (releaseLoopElement, AbletonTag.TAG_LOOP_MODE, 0) < 3);
    }


    /**
     * Try to locate the sample and create a sample data object from it. The sample is first looked
     * up relative to the root path. Since the root path is calculated with heuristics, which can
     * fail depending on where the preset and its samples are located, the absolute path stored in
     * the preset is tried next and finally a search by the name of the sample file is started in
     * the folder of the preset file.
     *
     * @param multiSampleFile The multi-sample source file
     * @param fileRefElement The file reference element
     * @param rootPath The root path where the samples are located
     * @return The sample data or null if not found
     * @throws IOException Could not access the sample
     */
    private ISampleData getSampleData (final File multiSampleFile, final Element fileRefElement, final File rootPath) throws IOException
    {
        final String relativePath = getRelativePath (fileRefElement);
        File sampleFile = new File (rootPath, relativePath);
        if (!sampleFile.isFile ())
        {
            final String absolutePath = getAbsolutePath (fileRefElement);
            if (!absolutePath.isBlank () && new File (absolutePath).isFile ())
                sampleFile = new File (absolutePath);
            else
            {
                final String sampleFileName = getSampleFileName (fileRefElement);
                if (!sampleFileName.isBlank ())
                    sampleFile = findSampleFile (this.notifier, multiSampleFile.getParentFile (), this.previousSampleFolder, sampleFileName, SEARCH_LEVELS);
            }

            if (!sampleFile.isFile ())
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_SAMPLE_DOES_NOT_EXIST", new File (rootPath, relativePath).getAbsolutePath ());
                return null;
            }
        }

        this.previousSampleFolder = sampleFile.getParentFile ();
        return createSampleData (sampleFile, this.notifier);
    }


    /**
     * Parse the metadata information.
     *
     * @param top The top XML element
     * @param metadata Where to store the parsed information
     * @param creator The creator value
     */
    private static void parseMetadata (final Element top, final IMetadata metadata, final String creator)
    {
        final Element descriptionTag = XMLUtils.getChildElementByName (top, AbletonTag.TAG_ANNOTATION);
        if (descriptionTag != null)
            metadata.setDescription (XMLUtils.readTextContent (descriptionTag));

        if (creator != null && !creator.isBlank ())
            metadata.setCreator (creator);
    }


    private static Optional<IFilter> readFilter (final Element samplePartsElement)
    {
        try
        {
            final Element filterElement = getRequiredElement (samplePartsElement, AbletonTag.TAG_FILTER);
            final Element filterIsOnElement = getRequiredElement (filterElement, AbletonTag.TAG_IS_ON);
            final boolean isFilterOn = getBooleanValueAttribute (filterIsOnElement, AbletonTag.TAG_MANUAL);
            if (!isFilterOn)
                return Optional.empty ();

            final Element slotElement = getRequiredElement (filterElement, AbletonTag.TAG_SLOT);
            final Element valueElement = getRequiredElement (slotElement, AbletonTag.TAG_VALUE);
            final Element simplerFilterElement = getRequiredElement (valueElement, AbletonTag.TAG_SIMPLER_FILTER);
            final Element typeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_TYPE);
            FilterType type = FILTER_TYPES.get (getValueAttribute (typeElement, AbletonTag.TAG_MANUAL));
            if (type == null)
                type = FilterType.LOW_PASS;

            final Element slopeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_SLOPE);
            final int poles = getBooleanValueAttribute (slopeElement, AbletonTag.TAG_MANUAL) ? 4 : 2;

            final Element freqElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_FREQUENCY);
            final double cutoff = getDoubleValueAttribute (freqElement, AbletonTag.TAG_MANUAL, IFilter.MAX_FREQUENCY);

            final Element resElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_FILTER_RESONANCE);
            final double resonance = getDoubleValueAttribute (resElement, AbletonTag.TAG_MANUAL, 0) / 1.25;

            final IFilter filter = new DefaultFilter (type, poles, cutoff, resonance);

            // Read the envelope
            final Element envelopeElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_ENVELOPE);
            final Element isOnElement = getRequiredElement (envelopeElement, AbletonTag.TAG_IS_ON);
            if (getBooleanValueAttribute (isOnElement, AbletonTag.TAG_MANUAL))
            {
                final Element amountElement = getRequiredElement (envelopeElement, AbletonTag.TAG_AMOUNT);

                final Element attackTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_TIME);
                final Element decayTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_TIME);
                final Element releaseTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_TIME);

                final Element attackLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_LEVEL);
                final Element sustainLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_SUSTAIN_LEVEL);
                final Element releaseLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_LEVEL);

                final Element attackSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_SLOPE);
                final Element decaySlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_SLOPE);
                final Element releaseSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_SLOPE);

                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                // The amount is in semitones (+-72), the depth of the model covers
                // MAX_ENVELOPE_DEPTH cent
                cutoffModulator.setDepth (getDoubleValueAttribute (amountElement, AbletonTag.TAG_MANUAL, 0) * 100.0 / IEnvelope.MAX_ENVELOPE_DEPTH);

                final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                filterEnvelope.setAttackTime (getDoubleValueAttribute (attackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                filterEnvelope.setDecayTime (getDoubleValueAttribute (decayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                filterEnvelope.setReleaseTime (getDoubleValueAttribute (releaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

                filterEnvelope.setStartLevel (getDoubleValueAttribute (attackLevelElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setSustainLevel (getDoubleValueAttribute (sustainLevelElement, AbletonTag.TAG_MANUAL, 1));
                filterEnvelope.setEndLevel (getDoubleValueAttribute (releaseLevelElement, AbletonTag.TAG_MANUAL, 0));

                filterEnvelope.setAttackSlope (-getDoubleValueAttribute (attackSlopeElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setDecaySlope (-getDoubleValueAttribute (decaySlopeElement, AbletonTag.TAG_MANUAL, 0));
                filterEnvelope.setReleaseSlope (-getDoubleValueAttribute (releaseSlopeElement, AbletonTag.TAG_MANUAL, 0));
            }

            // Read the velocity modulation
            final Element modByVelocityElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_MOD_BY_VELOCITY);
            final double modDepth = getDoubleValueAttribute (modByVelocityElement, AbletonTag.TAG_MANUAL, 0);
            filter.getCutoffVelocityModulator ().setDepth (modDepth);

            // Read the pitch (key) modulation
            final Element modByPitchElement = getRequiredElement (simplerFilterElement, AbletonTag.TAG_MOD_BY_PITCH);
            final double modPitchDepth = getDoubleValueAttribute (modByPitchElement, AbletonTag.TAG_MANUAL, 1);
            filter.setCutoffKeyTracking (modPitchDepth);

            return Optional.of (filter);
        }
        catch (final IOException _)
        {
            // No filter configured
            return Optional.empty ();
        }
    }


    private static void applyGlobalEnvelopes (final Element deviceElement, final IMultisampleSource multisampleSource)
    {
        try
        {
            final Element volAndPanElement = getRequiredElement (deviceElement, AbletonTag.TAG_VOLUME_AND_PAN);

            // Read the velocity depth
            final Element velocityDepthElement = getRequiredElement (volAndPanElement, AbletonTag.TAG_VOLUME_VEL_SCALE);
            final double velocityDepth = getDoubleValueAttribute (velocityDepthElement, AbletonTag.TAG_MANUAL, 0);

            // Read the amplitude envelope
            final Element envelopeElement = getRequiredElement (volAndPanElement, AbletonTag.TAG_ENVELOPE);

            final Element attackTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_TIME);
            final Element decayTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_TIME);
            final Element releaseTimeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_TIME);

            final Element attackLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_LEVEL);
            final Element sustainLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_SUSTAIN_LEVEL);
            final Element releaseLevelElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_LEVEL);

            final Element attackSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_ATTACK_SLOPE);
            final Element decaySlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_DECAY_SLOPE);
            final Element releaseSlopeElement = getRequiredElement (envelopeElement, AbletonTag.TAG_RELEASE_SLOPE);

            final IEnvelope ampEnvelope = new DefaultEnvelope ();
            ampEnvelope.setAttackTime (getDoubleValueAttribute (attackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
            ampEnvelope.setDecayTime (getDoubleValueAttribute (decayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
            ampEnvelope.setReleaseTime (getDoubleValueAttribute (releaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

            ampEnvelope.setStartLevel (getDoubleValueAttribute (attackLevelElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setSustainLevel (getDoubleValueAttribute (sustainLevelElement, AbletonTag.TAG_MANUAL, 1));
            ampEnvelope.setEndLevel (getDoubleValueAttribute (releaseLevelElement, AbletonTag.TAG_MANUAL, 0));

            ampEnvelope.setAttackSlope (-getDoubleValueAttribute (attackSlopeElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setDecaySlope (-getDoubleValueAttribute (decaySlopeElement, AbletonTag.TAG_MANUAL, 0));
            ampEnvelope.setReleaseSlope (-getDoubleValueAttribute (releaseSlopeElement, AbletonTag.TAG_MANUAL, 0));

            // Read the pitch envelope
            final Element auxEnvelopeElement = getRequiredElement (deviceElement, AbletonTag.TAG_AUX_ENVELOPE);
            final Element isAuxEnvOnElement = getRequiredElement (auxEnvelopeElement, AbletonTag.TAG_IS_ON);
            IEnvelope auxEnvelope = null;
            double auxDepth = 0;
            if (getBooleanValueAttribute (isAuxEnvOnElement, AbletonTag.TAG_MANUAL))
            {
                final Element slotElement = getRequiredElement (auxEnvelopeElement, AbletonTag.TAG_SLOT);
                final Element valueElement = getRequiredElement (slotElement, AbletonTag.TAG_VALUE);
                final Element auxEnvElement = getRequiredElement (valueElement, AbletonTag.TAG_SIMPLER_AUX_ENVELOPE);

                final Element auxAttackTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_TIME);
                final Element auxDecayTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_DECAY_TIME);
                final Element auxReleaseTimeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_TIME);

                final Element auxAttackLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_LEVEL);
                final Element auxSustainLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_SUSTAIN_LEVEL);
                final Element auxReleaseLevelElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_LEVEL);

                final Element auxAttackSlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_ATTACK_SLOPE);
                final Element auxDecaySlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_DECAY_SLOPE);
                final Element auxReleaseSlopeElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_RELEASE_SLOPE);

                final Element auxModDestElement = getRequiredElement (auxEnvElement, AbletonTag.TAG_MODULATION_DESTINATION);
                Element auxConnectionElement = getRequiredElement (auxModDestElement, AbletonTag.TAG_MODULATION_CONNECTION_0);
                int destination = getIntegerValueAttribute (auxConnectionElement, AbletonTag.TAG_MODULATION_CONNECTION, 0);
                // 6 = Pitch Modulation
                if (destination != 6)
                {
                    auxConnectionElement = getRequiredElement (auxModDestElement, AbletonTag.TAG_MODULATION_CONNECTION_1);
                    destination = getIntegerValueAttribute (auxConnectionElement, AbletonTag.TAG_MODULATION_CONNECTION, 0);
                }
                if (destination == 6)
                {
                    auxEnvelope = new DefaultEnvelope ();
                    auxDepth = getDoubleValueAttribute (auxConnectionElement, AbletonTag.TAG_AMOUNT, 0) / 100.0;

                    auxEnvelope.setAttackTime (getDoubleValueAttribute (auxAttackTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                    auxEnvelope.setDecayTime (getDoubleValueAttribute (auxDecayTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);
                    auxEnvelope.setReleaseTime (getDoubleValueAttribute (auxReleaseTimeElement, AbletonTag.TAG_MANUAL, 0) / 1000.0);

                    auxEnvelope.setStartLevel (getDoubleValueAttribute (auxAttackLevelElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setSustainLevel (getDoubleValueAttribute (auxSustainLevelElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setEndLevel (getDoubleValueAttribute (auxReleaseLevelElement, AbletonTag.TAG_MANUAL, 0));

                    auxEnvelope.setAttackSlope (-getDoubleValueAttribute (auxAttackSlopeElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setDecaySlope (-getDoubleValueAttribute (auxDecaySlopeElement, AbletonTag.TAG_MANUAL, 0));
                    auxEnvelope.setReleaseSlope (-getDoubleValueAttribute (auxReleaseSlopeElement, AbletonTag.TAG_MANUAL, 0));
                }
            }

            for (final IGroup group: multisampleSource.getGroups ())
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    zone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);
                    zone.getAmplitudeVelocityModulator ().setDepth (velocityDepth);

                    if (auxEnvelope != null)
                    {
                        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                        pitchModulator.setDepth (auxDepth);
                        pitchModulator.setSource (auxEnvelope);
                    }
                }
        }
        catch (final IOException _)
        {
            // Ignore missing elements
        }
    }


    /**
     * Read the sample-select (selector) range of a multi-sample part.
     *
     * @param multiSamplePartElement The multi-sample part element
     * @return Low, high, cross-fade low and cross-fade high of the selector range
     */
    private static int [] readSelectorRange (final Element multiSamplePartElement)
    {
        final Element selectorRangeElement = XMLUtils.getChildElementByName (multiSamplePartElement, AbletonTag.TAG_SELECTOR_RANGE);
        if (selectorRangeElement == null)
            return new int []
            {
                0,
                127,
                0,
                127
            };
        final int low = getIntegerValueAttribute (selectorRangeElement, AbletonTag.TAG_MINIMUM, 0);
        final int high = getIntegerValueAttribute (selectorRangeElement, AbletonTag.TAG_MAXIMUM, 127);
        return new int []
        {
            low,
            high,
            getIntegerValueAttribute (selectorRangeElement, AbletonTag.TAG_CROSSFADE_MINIMUM, low),
            getIntegerValueAttribute (selectorRangeElement, AbletonTag.TAG_CROSSFADE_MAXIMUM, high)
        };
    }


    /**
     * Detect round-robin cycles which are stored on the sample-select (selector) axis, the only way
     * to store them before the round-robin flag was added in Live 12: zones which occupy the same
     * key and velocity range but disjoint selector ranges play one at a time depending on the
     * selector position. Such zones are split into one group per selector slice in ascending
     * selector order. Zones with overlapping selector ranges or with selector cross-fades (a
     * morphing setup rather than round-robin) are left as they are.
     *
     * @param group The group with all read zones
     * @param selectorRanges The selector range of each zone
     * @return The resulting groups
     */
    private static List<IGroup> applySelectorRoundRobin (final IGroup group, final Map<ISampleZone, int []> selectorRanges)
    {
        final Map<String, List<ISampleZone>> clusters = new LinkedHashMap<> ();
        for (final ISampleZone zone: group.getSampleZones ())
            clusters.computeIfAbsent (zone.getKeyLow () + "," + zone.getKeyHigh () + "," + zone.getVelocityLow () + "," + zone.getVelocityHigh (), _ -> new ArrayList<> ()).add (zone);

        final Set<List<ISampleZone>> roundRobinClusters = new HashSet<> ();
        for (final List<ISampleZone> cluster: clusters.values ())
            if (isSelectorRoundRobin (cluster, selectorRanges))
                roundRobinClusters.add (cluster);
        if (roundRobinClusters.isEmpty ())
            return Collections.singletonList (group);

        final List<IGroup> groups = new ArrayList<> ();
        for (final List<ISampleZone> cluster: clusters.values ())
        {
            if (roundRobinClusters.contains (cluster))
                cluster.sort ((zone1, zone2) -> Integer.compare (selectorRanges.get (zone1)[0], selectorRanges.get (zone2)[0]));
            for (int i = 0; i < cluster.size (); i++)
            {
                final ISampleZone zone = cluster.get (i);
                final int groupIndex;
                if (roundRobinClusters.contains (cluster))
                {
                    zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                    zone.setSequencePosition (i + 1);
                    groupIndex = i;
                }
                else
                    groupIndex = 0;
                while (groups.size () <= groupIndex)
                    groups.add (new DefaultGroup ("Group #" + (groups.size () + 1)));
                groups.get (groupIndex).addSampleZone (zone);
            }
        }
        return groups;
    }


    /**
     * Check if the zones of the cluster form a round-robin setup on the selector axis: at least two
     * zones which all have no selector cross-fade and do not overlap each other.
     *
     * @param cluster The zones which occupy the same key and velocity range
     * @param selectorRanges The selector range of each zone
     * @return True if the cluster is a round-robin setup
     */
    private static boolean isSelectorRoundRobin (final List<ISampleZone> cluster, final Map<ISampleZone, int []> selectorRanges)
    {
        if (cluster.size () < 2)
            return false;

        final List<int []> ranges = new ArrayList<> ();
        for (final ISampleZone zone: cluster)
        {
            final int [] range = selectorRanges.get (zone);
            if (range[2] != range[0] || range[3] != range[1])
                return false;
            ranges.add (range);
        }
        ranges.sort ((range1, range2) -> Integer.compare (range1[0], range2[0]));
        for (int i = 1; i < ranges.size (); i++)
            if (ranges.get (i)[0] <= ranges.get (i - 1)[1])
                return false;
        return true;
    }


    private static List<IGroup> applyRoundRobin (final Element mapElement, final IGroup group)
    {
        // "0" = forward, "1" = backwards, "2" = other, "3" = random
        final int roundRobinDirection = getIntegerValueAttribute (mapElement, AbletonTag.TAG_ROUND_ROBIN_MODE, AbletonTag.ROUND_ROBIN_MODE_FORWARD);
        // Only the random mode picks a zone randomly, all other modes cycle through the zones
        final PlayLogic playLogic = roundRobinDirection == AbletonTag.ROUND_ROBIN_MODE_RANDOM ? PlayLogic.RANDOM : PlayLogic.ROUND_ROBIN;
        final List<List<ISampleZone>> splitZones = ZoneSplitter.splitZonesStableOrder (group.getSampleZones ());
        final List<IGroup> groups = new ArrayList<> ();
        for (int i = 0; i < splitZones.size (); i++)
        {
            final List<ISampleZone> sampleZones = splitZones.get (i);
            for (final ISampleZone sampleZone: sampleZones)
                sampleZone.setPlayLogic (playLogic);

            // A random selection has no play-back order, therefore no sequence is assigned
            if (roundRobinDirection <= AbletonTag.ROUND_ROBIN_MODE_BACKWARDS)
                setRoundRobinSequence (sampleZones, roundRobinDirection == 0);
            final IGroup layerGroup = new DefaultGroup ("Group #" + (i + 1));
            layerGroup.setSampleZones (sampleZones);
            groups.add (layerGroup);
        }
        return groups;
    }


    private static void setRoundRobinSequence (final List<ISampleZone> sampleZones, final boolean forwards)
    {
        int pos = forwards ? 1 : sampleZones.size ();
        for (final ISampleZone sampleZone: sampleZones)
        {
            sampleZone.setSequencePosition (pos);
            pos += forwards ? 1 : -1;
        }
    }


    private static String createUpwardsPath (final String relativePath)
    {
        int numberOfParentDirectories = Paths.get (relativePath).getNameCount ();
        final String lowerCase = relativePath.toLowerCase ();
        if (lowerCase.endsWith (".adv") || lowerCase.endsWith (".adg"))
            numberOfParentDirectories -= 1;
        return "../".repeat (numberOfParentDirectories);
    }


    /**
     * Get the Sampler and Simpler devices of a preset: the device of a device preset or all
     * devices in a rack preset.
     *
     * @param top The top element of the preset
     * @return The element of the device or rack, which references the preset file, and the Sampler
     *         and Simpler devices; empty if the preset contains none
     */
    private static Optional<Pair<Element, List<Element>>> getPresetDevices (final Element top)
    {
        final List<Element> samplerElements = new ArrayList<> ();

        Element deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_SIMPLER);
        if (deviceElement == null)
            deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_SAMPLER);
        if (deviceElement != null)
            samplerElements.add (deviceElement);
        else
        {
            deviceElement = XMLUtils.getChildElementByName (top, AbletonTag.TAG_DEVICE_RACK);
            if (deviceElement == null)
                return Optional.empty ();
            samplerElements.addAll (XMLUtils.getChildElementsByName (deviceElement, AbletonTag.TAG_DEVICE_SAMPLER, true));
            samplerElements.addAll (XMLUtils.getChildElementsByName (deviceElement, AbletonTag.TAG_DEVICE_SIMPLER, true));
        }

        return samplerElements.isEmpty () ? Optional.empty () : Optional.of (new Pair<> (deviceElement, samplerElements));
    }


    /**
     * Collect all Sampler and Simpler devices which contain samples in the given element and in
     * its children, e.g. in the racks of a track, in the order in which they are stored.
     *
     * @param element The element to search
     * @param devices Where to add the found devices
     */
    private static void collectDevices (final Element element, final List<Element> devices)
    {
        for (final Element child: XMLUtils.getChildElements (element))
        {
            final String tag = child.getNodeName ();
            if (AbletonTag.TAG_DEVICE_SAMPLER.equals (tag) || AbletonTag.TAG_DEVICE_SIMPLER.equals (tag))
            {
                if (!XMLUtils.getChildElementsByName (child, AbletonTag.TAG_MULTI_SAMPLE_PART, true).isEmpty ())
                    devices.add (child);
            }
            else
                collectDevices (child, devices);
        }
    }


    /**
     * Get the name of a track of a Live Set.
     *
     * @param trackElement The track element
     * @return The name which Live displays, the type of the track if it has none
     */
    private static String getTrackName (final Element trackElement)
    {
        final Element nameElement = XMLUtils.getChildElementByName (trackElement, AbletonTag.TAG_NAME);
        if (nameElement != null)
        {
            final String effectiveName = getValueAttribute (nameElement, AbletonTag.TAG_EFFECTIVE_NAME);
            if (!effectiveName.isBlank ())
                return effectiveName;
            final String userName = getValueAttribute (nameElement, AbletonTag.TAG_USER_NAME);
            if (!userName.isBlank ())
                return userName;
        }
        return trackElement.getNodeName ();
    }


    /**
     * Get the path of a file reference, relative to the folder which the type of the reference
     * refers to. Live 11 and later store it as one text. Earlier versions store each folder as an
     * element - an empty one moves one folder up - and the name of the file separately.
     *
     * @param fileRefElement The file reference element
     * @return The relative path, empty if there is none
     */
    private static String getRelativePath (final Element fileRefElement)
    {
        final Element relativePathElement = XMLUtils.getChildElementByName (fileRefElement, AbletonTag.TAG_RELATIVE_PATH);
        if (relativePathElement == null)
            return "";
        final String relativePath = relativePathElement.getAttribute (AbletonTag.ATTR_VALUE);
        if (!relativePath.isEmpty ())
            return relativePath;

        final String fileName = getValueAttribute (fileRefElement, AbletonTag.TAG_NAME);
        if (fileName.isBlank ())
            return "";
        final StringBuilder path = new StringBuilder ();
        for (final Element folderElement: XMLUtils.getChildElementsByName (relativePathElement, AbletonTag.TAG_RELATIVE_PATH_ELEMENT, false))
        {
            final String folder = folderElement.getAttribute (AbletonTag.ATTR_DIR);
            path.append (folder.isEmpty () ? ".." : folder).append ('/');
        }
        return path.append (fileName).toString ();
    }


    /**
     * Get the absolute path of a file reference. Live 11 and later store it as one text. Earlier
     * versions store the folders as elements of a search hint and the name of the file separately.
     *
     * @param fileRefElement The file reference element
     * @return The absolute path, empty if there is none
     */
    private static String getAbsolutePath (final Element fileRefElement)
    {
        final String absolutePath = getValueAttribute (fileRefElement, AbletonTag.TAG_PATH);
        if (!absolutePath.isBlank ())
            return absolutePath;

        final String fileName = getValueAttribute (fileRefElement, AbletonTag.TAG_NAME);
        final Element searchHintElement = XMLUtils.getChildElementByName (fileRefElement, AbletonTag.TAG_SEARCH_HINT);
        final Element pathHintElement = searchHintElement == null ? null : XMLUtils.getChildElementByName (searchHintElement, AbletonTag.TAG_PATH_HINT);
        if (fileName.isBlank () || pathHintElement == null)
            return "";
        final List<String> parts = new ArrayList<> ();
        for (final Element folderElement: XMLUtils.getChildElementsByName (pathHintElement, AbletonTag.TAG_RELATIVE_PATH_ELEMENT, false))
            parts.add (folderElement.getAttribute (AbletonTag.ATTR_DIR));
        if (parts.isEmpty ())
            return "";
        parts.add (fileName);
        // A path on Windows starts with the drive instead of the root folder
        final String path = String.join (File.separator, parts);
        return parts.get (0).endsWith (":") ? path : File.separator + path;
    }


    /**
     * Get the name of the file of a file reference.
     *
     * @param fileRefElement The file reference element
     * @return The name, empty if there is none
     */
    private static String getSampleFileName (final Element fileRefElement)
    {
        String path = getRelativePath (fileRefElement);
        if (path.isBlank ())
            path = getAbsolutePath (fileRefElement);
        return path.substring (Math.max (path.lastIndexOf ('/'), path.lastIndexOf ('\\')) + 1);
    }


    /**
     * De-compress the content of a preset or Live Set. Their XML documents are compressed with
     * GZIP, except in Live Packs, which may store them uncompressed.
     *
     * @param content The content of the file
     * @return The de-compressed content, the given content if it is not compressed
     * @throws IOException Could not de-compress the content
     */
    private static byte [] decompress (final byte [] content) throws IOException
    {
        if (content.length < 2 || (content[0] & 0xFF) != 0x1F || (content[1] & 0xFF) != 0x8B)
            return content;
        try (final InputStream in = new GZIPInputStream (new ByteArrayInputStream (content)))
        {
            return in.readAllBytes ();
        }
    }


    /**
     * Test if the content of a preset is stored in the binary format of older versions of Live
     * instead of XML.
     *
     * @param content The de-compressed content of the file
     * @return True if it is stored in the binary format
     */
    private static boolean isBinaryFormat (final byte [] content)
    {
        return content.length >= 4 && ByteBuffer.wrap (content, 0, 4).order (ByteOrder.LITTLE_ENDIAN).getInt () == AbletonBinaryDecoder.RECORD_ID;
    }


    /**
     * Parse an XML document.
     *
     * @param content The de-compressed content of the file, UTF-8 encoded
     * @return The top element of the document
     * @throws SAXException Could not parse the document
     */
    private static Element parseXml (final byte [] content) throws SAXException
    {
        final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (StreamUtils.readUtf8 (ByteBuffer.wrap (content)))));
        return document.getDocumentElement ();
    }


    /**
     * Test if a file name has the given ending, ignoring the case of the letters.
     *
     * @param fileName The name of the file
     * @param ending The ending, e.g. '.alp'
     * @return True if the name ends with the ending
     */
    private static boolean hasEnding (final String fileName, final String ending)
    {
        return fileName.toLowerCase (Locale.US).endsWith (ending);
    }


    private static double getDoubleValueAttribute (final Element parentElement, final String elementTag, final double defaultValue)
    {
        final String value = getValueAttribute (parentElement, elementTag);
        try
        {
            return Double.parseDouble (value);
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    private static int getIntegerValueAttribute (final Element parentElement, final String elementTag, final int defaultValue)
    {
        final String value = getValueAttribute (parentElement, elementTag);
        try
        {
            return Integer.parseInt (value);
        }
        catch (final NumberFormatException _)
        {
            return defaultValue;
        }
    }


    private static boolean getBooleanValueAttribute (final Element parentElement, final String elementTag)
    {
        final String value = getValueAttribute (parentElement, elementTag);
        return Boolean.parseBoolean (value);
    }


    private static String getValueAttribute (final Element parentElement, final String elementTag)
    {
        final Element element = XMLUtils.getChildElementByName (parentElement, elementTag);
        return element == null ? "" : element.getAttribute (AbletonTag.ATTR_VALUE);
    }


    private static Element getRequiredElement (final Element parentElement, final String tagName) throws IOException
    {
        final Element element = XMLUtils.getChildElementByName (parentElement, tagName);
        if (element == null)
            throw new IOException (Functions.getMessage (ERR_MISSING_TAG, tagName));
        return element;
    }


    private static File findSampleFolder (final File multiSampleFile)
    {
        File folder = multiSampleFile;
        while ((folder = folder.getParentFile ()) != null)
        {
            final String [] childrenNames = folder.list ();
            if (childrenNames == null)
                continue;
            final Set<String> children = new HashSet<> ();
            Collections.addAll (children, childrenNames);
            // Sample paths are relative to either the location of a 'Samples' folder or the root
            // folder of an Ableton project which always contains an info folder
            if (children.contains ("Samples") || children.contains (PROJECT_INFO_FOLDER))
                return folder;
        }
        return new File ("");
    }
}
