// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.soundbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.FlacFileSampleData;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively Audiomodern Soundbox sound packs in folders. Files must end with
 * <i>.sbpack</i>. A pack is a ZIP file which contains several presets; each preset becomes one
 * multi-sample source.
 *
 * @author Jürgen Moßgraber
 */
public class SoundboxDetector extends AbstractDetector<EmptySettingsUI>
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SoundboxDetector (final INotifier notifier)
    {
        super ("Audiomodern Soundbox", "Soundbox", notifier, EmptySettingsUI.INSTANCE, ".sbpack");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        try (final ZipFile zipFile = new ZipFile (file))
        {
            final ZipEntry packEntry = zipFile.getEntry (SoundboxTag.PACK_FILE);
            if (packEntry == null)
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_NO_METADATA_FILE");
                return Collections.emptyList ();
            }

            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return this.parsePack (file, zipFile, packEntry);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parses the pack description file and all presets referenced from it.
     *
     * @param packFile The .sbpack file
     * @param zipFile The opened ZIP file
     * @param packEntry The ZIP entry of the pack description file
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the files
     */
    private List<IMultisampleSource> parsePack (final File packFile, final ZipFile zipFile, final ZipEntry packEntry) throws IOException
    {
        final Document document;
        try (final InputStream in = zipFile.getInputStream (packEntry))
        {
            document = XMLUtils.parseDocument (new InputSource (in));
        }
        catch (final SAXException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Collections.emptyList ();
        }

        final Element top = document.getDocumentElement ();
        if (!SoundboxTag.PACK.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }

        final String author = top.getAttribute (SoundboxTag.ATTR_AUTHOR);
        final String description = top.getAttribute (SoundboxTag.ATTR_DESCRIPTION);

        // The ordered preset names - index N refers to the file '_presets/pN.sbset'
        final List<String> presetNames = new ArrayList<> ();
        final Element presetsElement = XMLUtils.getChildElementByName (top, SoundboxTag.PRESETS);
        if (presetsElement != null)
            for (final Element presetElement: XMLUtils.getChildElementsByName (presetsElement, SoundboxTag.PRESET_NAME, false))
                presetNames.add (presetElement.getAttribute (SoundboxTag.ATTR_NAME));

        // All groups mapped by their name
        final Map<String, Element> groupElementMap = new HashMap<> ();
        final Element groupsElement = XMLUtils.getChildElementByName (top, SoundboxTag.GROUPS);
        if (groupsElement != null)
            for (final Element groupElement: XMLUtils.getChildElementsByName (groupsElement, SoundboxTag.GROUP, false))
                groupElementMap.put (groupElement.getAttribute (SoundboxTag.ATTR_NAME), groupElement);

        // The ordered original sample file names - index N refers to the file '_samples/sN'
        final List<String> soundNames = new ArrayList<> ();
        final Element soundsElement = XMLUtils.getChildElementByName (top, SoundboxTag.SOUNDS);
        if (soundsElement != null)
            for (final Element soundElement: XMLUtils.getChildElementsByName (soundsElement, SoundboxTag.SOUND, false))
                soundNames.add (XMLUtils.readTextContent (soundElement));

        final List<IMultisampleSource> results = new ArrayList<> ();
        final Map<Integer, ISampleData> sampleDataCache = new HashMap<> ();
        for (int presetIndex = 0; presetIndex < presetNames.size (); presetIndex++)
        {
            final ZipEntry presetEntry = zipFile.getEntry (SoundboxTag.PRESETS_FOLDER + "p" + presetIndex + ".sbset");
            if (presetEntry == null)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing preset file p" + presetIndex + ".sbset");
                continue;
            }

            try (final InputStream in = zipFile.getInputStream (presetEntry))
            {
                final Document presetDocument = XMLUtils.parseDocument (new InputSource (in));
                final IMultisampleSource source = this.parsePreset (packFile, zipFile, presetDocument.getDocumentElement (), presetNames.get (presetIndex), groupElementMap, soundNames, sampleDataCache, author, description);
                if (source != null)
                    results.add (source);
            }
            catch (final SAXException | IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            }
        }
        return results;
    }


    /**
     * Parses one preset into a multi-sample source.
     *
     * @param packFile The .sbpack file
     * @param zipFile The opened ZIP file
     * @param presetElement The root element of the preset document
     * @param presetName The name of the preset
     * @param groupElementMap All groups of the pack mapped by their name
     * @param soundNames The original sample file names
     * @param sampleDataCache Caches the already created sample data objects
     * @param author The pack author
     * @param description The pack description
     * @return The multi-sample source or null if the preset does not contain any samples
     * @throws IOException Error reading the sample data
     */
    private IMultisampleSource parsePreset (final File packFile, final ZipFile zipFile, final Element presetElement, final String presetName, final Map<String, Element> groupElementMap, final List<String> soundNames, final Map<Integer, ISampleData> sampleDataCache, final String author, final String description) throws IOException
    {
        if (!SoundboxTag.PRESET.equals (presetElement.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown Root");
            return null;
        }

        final Element layersElement = XMLUtils.getChildElementByName (presetElement, SoundboxTag.LAYERS);
        if (layersElement == null)
            return null;
        final List<Element> layerElements = XMLUtils.getChildElementsByName (layersElement, SoundboxTag.LAYER, false);

        final List<IGroup> groups = new ArrayList<> ();
        SoundboxEngineSettings firstEngineSettings = null;
        for (int layerIndex = 0; layerIndex < layerElements.size (); layerIndex++)
        {
            final String groupName = presetElement.getAttribute ("g" + layerIndex);
            if (groupName.isBlank ())
                continue;
            final Element groupElement = groupElementMap.get (groupName);
            if (groupElement == null)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing group " + groupName);
                continue;
            }

            final Element layerElement = layerElements.get (layerIndex);
            final SoundboxLayerState layerState = SoundboxLayerState.parse (SoundboxJuceBase64.decode (layerElement.getAttribute (SoundboxTag.ATTR_STATE)));
            if (!layerState.active)
                continue;
            final SoundboxEngineSettings engineSettings = SoundboxEngineSettings.parse (SoundboxJuceBase64.decode (layerElement.getAttribute (SoundboxTag.ATTR_SETTINGS)));
            if (firstEngineSettings == null)
                firstEngineSettings = engineSettings;

            // A filter effect of the layer (or of the master effects) becomes the filter of its
            // zones
            IFilter filter = SoundboxFilterEffect.readFilterEffect (XMLUtils.getChildElementByName (layerElement, SoundboxTag.EFFECTS));
            if (filter == null)
            {
                final Element masterElement = XMLUtils.getChildElementByName (presetElement, SoundboxTag.MASTER);
                if (masterElement != null)
                    filter = SoundboxFilterEffect.readFilterEffect (XMLUtils.getChildElementByName (masterElement, SoundboxTag.EFFECTS));
            }

            for (final IGroup group: this.parseGroup (zipFile, groupElement, groupName, layerState, engineSettings, filter, soundNames, sampleDataCache))
                if (!group.getSampleZones ().isEmpty ())
                    groups.add (group);
        }

        if (groups.isEmpty ())
        {
            this.notifier.log ("IDS_NOTIFY_SOUNDBOX_EMPTY_PRESET", presetName);
            return null;
        }

        final IMultisampleSource multisampleSource = this.createMultisampleSource (packFile, presetName, groups, description);
        final IMetadata metadata = multisampleSource.getMetadata ();
        if (!author.isBlank ())
            metadata.setCreator (author);

        // The voice mode and glide are per layer in Soundbox but per instrument in the model,
        // therefore the values of the first active layer are applied
        if (firstEngineSettings != null)
        {
            if (firstEngineSettings.voiceMode != SoundboxEngineSettings.VOICE_MODE_POLY)
            {
                multisampleSource.setPolyphony (1);
                multisampleSource.setMonophonicLegato (firstEngineSettings.voiceMode == SoundboxEngineSettings.VOICE_MODE_LEGATO);
            }
            // The time law of the glide knob is not calibrated, pass the fraction through
            multisampleSource.setPortamentoTime (firstEngineSettings.glide);
        }

        return multisampleSource;
    }


    /**
     * Parses all sounds of a group into sample zones. Newer plug-in versions support round robins:
     * the 'rrLayer' attribute of a sound assigns it to one of the round robin layers of the group,
     * which are returned as separate groups with round robin play logic.
     *
     * @param zipFile The opened ZIP file
     * @param groupElement The XML element of the group
     * @param groupName The name of the group
     * @param layerState The state of the layer which plays the group
     * @param engineSettings The engine settings of the layer which plays the group
     * @param filter The filter of the layer or null if there is none
     * @param soundNames The original sample file names
     * @param sampleDataCache Caches the already created sample data objects
     * @return The groups, one for each round robin layer
     * @throws IOException Error reading the sample data
     */
    private List<IGroup> parseGroup (final ZipFile zipFile, final Element groupElement, final String groupName, final SoundboxLayerState layerState, final SoundboxEngineSettings engineSettings, final IFilter filter, final List<String> soundNames, final Map<Integer, ISampleData> sampleDataCache) throws IOException
    {
        // The 'G' prefix marks a group name, remove it for display
        final String displayName = groupName.startsWith ("G") ? groupName.substring (1) : groupName;
        final Map<Integer, DefaultGroup> roundRobinGroups = new TreeMap<> ();

        for (final Element soundElement: XMLUtils.getChildElementsByName (groupElement, SoundboxTag.SOUND, false))
        {
            // Old format: 'f', newer round robin format: 'f0', 'f1', ...
            String fileIndexText = soundElement.getAttribute ("f");
            if (fileIndexText.isBlank ())
                fileIndexText = soundElement.getAttribute ("f0");
            final int fileIndex;
            try
            {
                fileIndex = Integer.parseInt (fileIndexText);
            }
            catch (final NumberFormatException _)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, "Malformed sample reference: " + fileIndexText);
                continue;
            }

            final SoundboxSound sound = SoundboxSound.parse (SoundboxJuceBase64.decode (XMLUtils.readTextContent (soundElement)));

            final ISampleData sampleData = this.getSampleData (zipFile, fileIndex, sampleDataCache);
            if (sampleData == null)
                continue;

            final int roundRobinLayer = XMLUtils.getIntegerAttribute (soundElement, "rrLayer", 0);
            final DefaultGroup group = roundRobinGroups.computeIfAbsent (Integer.valueOf (roundRobinLayer), layer -> new DefaultGroup (layer.intValue () == 0 ? displayName : displayName + " RR" + (layer.intValue () + 1)));
            final String soundName = fileIndex < soundNames.size () ? removeFileEnding (soundNames.get (fileIndex)) : "s" + fileIndex;
            final ISampleZone zone = new DefaultSampleZone (soundName, sampleData);

            zone.setKeyRoot (sound.keyRoot);
            zone.setKeyLow (sound.keyLow);
            zone.setKeyHigh (Math.clamp (sound.keyHighExcl - 1L, sound.keyLow, 127));
            zone.setVelocityLow (sound.velocityLow);
            zone.setVelocityHigh (sound.velocityHigh);
            zone.setReversed (sound.reverse);

            sampleData.addZoneData (zone, false, false);
            final int numFrames = zone.getStop ();
            zone.setStart ((int) Math.round (sound.sampleStart * numFrames));
            zone.setStop ((int) Math.round (sound.sampleEnd * numFrames));

            if (sound.loopActive && sound.loopEnd > sound.loopStart)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (sound.pingPong ? LoopType.ALTERNATING : LoopType.FORWARDS);
                loop.setStart ((int) Math.round (sound.loopStart * numFrames));
                loop.setEnd ((int) Math.round (sound.loopEnd * numFrames));
                loop.setCrossfade (sound.loopCrossfade);
                zone.addLoop (loop);
            }

            // Fold the layer volume (0.75 = unity gain) and the sound volume percent into the
            // zone gain
            final double volumeRatio = sound.volumePercent / 100.0 * (layerState.volume / SoundboxLayerState.DEFAULT_VOLUME);
            if (volumeRatio > 0 && volumeRatio != 1)
                zone.setGain (20.0 * Math.log10 (volumeRatio));
            else if (volumeRatio <= 0)
                zone.setGain (-96);

            zone.setPanning (Math.clamp (sound.panning + layerState.panning, -1, 1));
            zone.setTuning (sound.tuneSemitones + engineSettings.transposeSemitones + (engineSettings.octaveIndex - SoundboxEngineSettings.OCTAVE_CENTER) * 12.0 + engineSettings.fineTuneCents / 100.0);

            if (filter != null)
                zone.setFilter (new DefaultFilter (filter.getType (), filter.getPoles (), filter.getCutoff (), filter.getResonance ()));

            final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
            envelope.setAttackTime (engineSettings.attack * SoundboxEngineSettings.MAX_ATTACK_SECONDS);
            envelope.setDecayTime (engineSettings.decay * SoundboxEngineSettings.MAX_RELEASE_SECONDS);
            envelope.setSustainLevel (engineSettings.sustain);
            envelope.setReleaseTime (engineSettings.release * SoundboxEngineSettings.MAX_RELEASE_SECONDS);

            group.addSampleZone (zone);
        }

        final List<IGroup> groups = new ArrayList<> (roundRobinGroups.values ());

        // Only zones of an actual round robin cycle get the round robin play logic
        if (groups.size () > 1)
        {
            int sequencePosition = 1;
            for (final IGroup group: groups)
            {
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    zone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                    zone.setSequencePosition (sequencePosition);
                }
                sequencePosition++;
            }
        }

        return groups;
    }


    /**
     * Creates the sample data for a sample file in the ZIP. The files have no file ending,
     * therefore the type is detected from the magic bytes (WAV in all known packs, newer plug-in
     * versions also use FLAC).
     *
     * @param zipFile The opened ZIP file
     * @param fileIndex The index of the sample file
     * @param sampleDataCache Caches the already created sample data objects
     * @return The sample data or null if the entry is missing or has an unknown format
     * @throws IOException Error reading the sample data
     */
    private ISampleData getSampleData (final ZipFile zipFile, final int fileIndex, final Map<Integer, ISampleData> sampleDataCache) throws IOException
    {
        final Integer key = Integer.valueOf (fileIndex);
        if (sampleDataCache.containsKey (key))
            return sampleDataCache.get (key);

        ISampleData sampleData = null;
        final String entryName = SoundboxTag.SAMPLES_FOLDER + "s" + fileIndex;
        final ZipEntry sampleEntry = zipFile.getEntry (entryName);
        if (sampleEntry == null)
            this.notifier.logError (ERR_BAD_METADATA_FILE, "Missing sample file " + entryName);
        else
        {
            final byte [] magic = new byte [4];
            try (final InputStream in = zipFile.getInputStream (sampleEntry))
            {
                final int read = in.readNBytes (magic, 0, 4);
                if (read == 4 && magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F')
                    sampleData = new WavFileSampleData (new File (zipFile.getName ()), new File (entryName));
                else if (read == 4 && magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C')
                    sampleData = new FlacFileSampleData (new File (zipFile.getName ()), new File (entryName));
                else
                    this.notifier.logError (ERR_BAD_METADATA_FILE, "Unknown sample format of " + entryName);
            }
        }

        sampleDataCache.put (key, sampleData);
        return sampleData;
    }


    private static String removeFileEnding (final String fileName)
    {
        final int dotPosition = fileName.lastIndexOf ('.');
        return dotPosition > 0 ? fileName.substring (0, dotPosition) : fileName;
    }
}
