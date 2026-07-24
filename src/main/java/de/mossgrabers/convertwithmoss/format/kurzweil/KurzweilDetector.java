// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Kurzweil K2000/K2500/K2600 files in folders. Files must end with <i>.krz</i>,
 * <i>.k25</i> or <i>.k26</i>. Each program in a file becomes one multi-sample; keymaps and samples
 * which are not referenced by a program are added as multi-samples of their own.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String IDS_KURZWEIL_READING            = "IDS_KURZWEIL_READING";

    /** A full cutoff velocity modulation is 8 octaves (the SFZ 'fil_veltrack' range). */
    static final int            MAX_VELOCITY_MODULATION_CENTS   = 9600;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KurzweilDetector (final INotifier notifier)
    {
        super ("Kurzweil K2x00", "Kurzweil", notifier, new MetadataSettingsUI ("Kurzweil"), ".krz", ".k25", ".k26");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final KurzweilFile kurzweilFile;
        try
        {
            kurzweilFile = new KurzweilFile (Files.readAllBytes (sourceFile.toPath ()));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> sources = new ArrayList<> ();
        final Map<Long, ISampleData> sampleDataCache = new HashMap<> ();
        final Set<Integer> usedKeymapIDs = new HashSet<> ();
        final Set<Integer> usedSampleIDs = new HashSet<> ();
        final Set<Integer> reportedRomSampleIDs = new HashSet<> ();

        // Each program becomes one multi-sample. A program is a sound the user explicitly wants,
        // so problems (missing keymaps, missing or ROM samples) are reported for it.
        for (final KurzweilProgram program: kurzweilFile.getPrograms ())
        {
            final String name = program.getName ().isBlank () ? FileUtils.getNameWithoutType (sourceFile) : program.getName ();
            this.notifier.log (IDS_KURZWEIL_READING, "program", name);

            final List<IGroup> groups = new ArrayList<> ();
            final Set<Integer> reportedMissingKeymapIDs = new HashSet<> ();
            for (final KurzweilProgram.Layer layer: program.getLayers ())
            {
                final Integer keymapID = Integer.valueOf (layer.getKeymapID ());
                final KurzweilKeymap keymap = kurzweilFile.getKeymaps ().get (keymapID);
                if (keymap == null)
                {
                    if (reportedMissingKeymapIDs.add (keymapID))
                        this.notifier.log ("IDS_KURZWEIL_KEYMAP_MISSING", keymapID.toString (), name);
                    continue;
                }
                usedKeymapIDs.add (keymapID);
                this.createGroupsFromKeymap (kurzweilFile, keymap, layer, groups, sampleDataCache, usedSampleIDs, reportedRomSampleIDs, true);
            }
            this.addSource (sources, sourceFile, name, groups, true);
        }

        // Keymaps which are not referenced by a program are leftover objects of the bank; in a
        // multi-file sound set their samples usually live in a different file. Convert them if
        // their samples happen to be present in this file but stay silent about the (expected)
        // references to samples which are not - otherwise a bank with one usable program spews an
        // error per orphaned keymap.
        for (final KurzweilKeymap keymap: kurzweilFile.getKeymaps ().values ())
        {
            if (usedKeymapIDs.contains (Integer.valueOf (keymap.getId ())))
                continue;
            final List<IGroup> groups = new ArrayList<> ();
            this.createGroupsFromKeymap (kurzweilFile, keymap, new KurzweilProgram.Layer (), groups, sampleDataCache, usedSampleIDs, reportedRomSampleIDs, false);
            final String name = keymap.getName ().isBlank () ? FileUtils.getNameWithoutType (sourceFile) : keymap.getName ();
            if (this.addSource (sources, sourceFile, name, groups, false))
                this.notifier.log (IDS_KURZWEIL_READING, "keymap", name);
        }

        // Samples which are not referenced by a keymap as well. These are the actual content of a
        // sample-only bank, so a deduplicated note is logged for the ones which only reference
        // device ROM.
        for (final KurzweilSample sample: kurzweilFile.getSamples ().values ())
        {
            if (usedSampleIDs.contains (Integer.valueOf (sample.getId ())))
                continue;
            final String name = sample.getName ().isBlank () ? FileUtils.getNameWithoutType (sourceFile) : sample.getName ();
            final List<IGroup> groups = this.createGroupsFromSample (sample, sampleDataCache, reportedRomSampleIDs, true);
            if (this.addSource (sources, sourceFile, name, groups, false))
                this.notifier.log (IDS_KURZWEIL_READING, "sample", name);
        }

        return sources;
    }


    /**
     * Merge adjacent velocity zones, drop empty groups and, if any zones remain, add the resulting
     * multi-sample to the list.
     *
     * @param sources Where to add the created multi-sample
     * @param sourceFile The source file
     * @param name The name of the multi-sample
     * @param groups The groups with the zones
     * @param reportProblems If true, a note is logged when there are no convertible zones
     * @return True if a multi-sample was added
     */
    private boolean addSource (final List<IMultisampleSource> sources, final File sourceFile, final String name, final List<IGroup> groups, final boolean reportProblems)
    {
        mergeAdjacentVelocityZones (groups);

        final List<IGroup> filledGroups = new ArrayList<> ();
        for (final IGroup group: groups)
            if (!group.getSampleZones ().isEmpty ())
                filledGroups.add (group);

        if (filledGroups.isEmpty ())
        {
            if (reportProblems)
                this.notifier.log ("IDS_KURZWEIL_NO_ZONES", name);
            return false;
        }

        sources.add (this.createMultisampleSource (sourceFile, name, filledGroups));
        return true;
    }


    /**
     * A zone which appears identically on several velocity levels (e.g. a full velocity pad
     * combined with velocity split zones on other keys) is read as one zone per level. Merge such
     * zones when their velocity ranges are adjacent.
     *
     * @param groups The groups with the zones to merge
     */
    private static void mergeAdjacentVelocityZones (final List<IGroup> groups)
    {
        boolean merged;
        do
        {
            merged = false;
            for (int i = 0; i < groups.size (); i++)
                for (final ISampleZone zone: groups.get (i).getSampleZones ())
                    for (int j = i + 1; j < groups.size (); j++)
                    {
                        final Optional<ISampleZone> otherOpt = findAdjacentZone (groups.get (j), zone);
                        if (otherOpt.isPresent ())
                        {
                            final ISampleZone other = otherOpt.get ();
                            zone.setVelocityLow (Math.min (zone.getVelocityLow (), other.getVelocityLow ()));
                            zone.setVelocityHigh (Math.max (zone.getVelocityHigh (), other.getVelocityHigh ()));
                            groups.get (j).getSampleZones ().remove (other);
                            merged = true;
                        }
                    }
        } while (merged);
    }


    private static Optional<ISampleZone> findAdjacentZone (final IGroup group, final ISampleZone zone)
    {
        final Optional<ISampleData> sampleDataOpt = zone.getSampleData ();
        if (sampleDataOpt.isEmpty ())
            return Optional.empty ();

        final ISampleData sampleData = sampleDataOpt.get ();
        for (final ISampleZone other: group.getSampleZones ())
        {
            final Optional<ISampleData> otherSampleDataOpt = other.getSampleData ();
            if (otherSampleDataOpt.isEmpty ())
                continue;
            final ISampleData otherSampleData = otherSampleDataOpt.get ();

            if (sampleData.equals (otherSampleData) && other.getKeyLow () == zone.getKeyLow () && other.getKeyHigh () == zone.getKeyHigh () && other.getKeyRoot () == zone.getKeyRoot () && other.getTuning () == zone.getTuning () && (other.getVelocityLow () == zone.getVelocityHigh () + 1 || other.getVelocityHigh () + 1 == zone.getVelocityLow ()))
                return Optional.of (other);
        }
        return Optional.empty ();
    }


    /**
     * Create the groups (one per velocity level) with their zones from a keymap.
     *
     * @param kurzweilFile The file which contains the referenced samples
     * @param keymap The keymap to convert
     * @param layer The program layer which references the keymap - limits the key range and
     *            transposes the zones
     * @param groups Where to add the created groups
     * @param sampleDataCache Cache of the already converted sample data
     * @param usedSampleIDs All sample IDs referenced from keymaps are added to this set
     * @param reportedRomSampleIDs The IDs of the ROM samples which were already reported
     * @param reportProblems If true, missing or ROM sample references are logged
     */
    private void createGroupsFromKeymap (final KurzweilFile kurzweilFile, final KurzweilKeymap keymap, final KurzweilProgram.Layer layer, final List<IGroup> groups, final Map<Long, ISampleData> sampleDataCache, final Set<Integer> usedSampleIDs, final Set<Integer> reportedRomSampleIDs, final boolean reportProblems)
    {
        final List<KurzweilKeymapEntry []> entryTables = keymap.getEntryTables ();
        for (int tableIndex = 0; tableIndex < entryTables.size (); tableIndex++)
        {
            // The velocity range of the table spans all dynamic levels which reference it
            int lowLevel = -1;
            int highLevel = -1;
            for (int level = 0; level < KurzweilKeymap.NUM_LEVELS; level++)
                if (keymap.getTableIndexOfLevel (level) == tableIndex)
                {
                    if (lowLevel < 0)
                        lowLevel = level;
                    highLevel = level;
                }
            if (lowLevel < 0)
                continue;

            // The velocity window of the layer further limits the range of the table
            final int velocityLow = Math.max (Math.max (1, lowLevel * 16), layer.getVelocityLow ());
            final int velocityHigh = Math.min (highLevel * 16 + 15, layer.getVelocityHigh ());
            if (velocityLow > velocityHigh)
                continue;

            String groupName = keymap.getName ();
            if (entryTables.size () > 1)
                groupName += " " + KurzweilKeymap.formatLevels (lowLevel, highLevel);
            final IGroup group = new DefaultGroup (groupName);

            // Combine runs of identical entries into one zone
            final KurzweilKeymapEntry [] entries = entryTables.get (tableIndex);
            int index = 0;
            while (index < entries.length)
            {
                if (!entries[index].isUsed ())
                {
                    index++;
                    continue;
                }
                int lastIndex = index;
                while (lastIndex + 1 < entries.length && entries[lastIndex + 1].isIdentical (entries[index]))
                    lastIndex++;

                final ISampleZone zone = this.createZone (kurzweilFile, keymap, entries[index], index, lastIndex, layer, sampleDataCache, usedSampleIDs, reportedRomSampleIDs, reportProblems);
                if (zone != null)
                {
                    zone.setVelocityLow (velocityLow);
                    zone.setVelocityHigh (velocityHigh);
                    group.addSampleZone (zone);
                }

                index = lastIndex + 1;
            }

            groups.add (group);
        }
    }


    /**
     * Create a zone from a run of identical keymap entries.
     *
     * @param kurzweilFile The file which contains the referenced samples
     * @param keymap The keymap
     * @param entry The (first) entry of the run
     * @param firstIndex The index of the first entry of the run
     * @param lastIndex The index of the last entry of the run
     * @param layer The program layer
     * @param sampleDataCache Cache of the already converted sample data
     * @param usedSampleIDs All sample IDs referenced from keymaps are added to this set
     * @param reportedRomSampleIDs The IDs of the ROM samples which were already reported
     * @param reportProblems If true, missing or ROM sample references are logged
     * @return The zone or null if it cannot be created
     */
    private ISampleZone createZone (final KurzweilFile kurzweilFile, final KurzweilKeymap keymap, final KurzweilKeymapEntry entry, final int firstIndex, final int lastIndex, final KurzweilProgram.Layer layer, final Map<Long, ISampleData> sampleDataCache, final Set<Integer> usedSampleIDs, final Set<Integer> reportedRomSampleIDs, final boolean reportProblems)
    {
        final Integer sampleID = Integer.valueOf (entry.getSampleID ());
        final KurzweilSample sample = kurzweilFile.getSamples ().get (sampleID);
        if (sample == null)
        {
            if (reportProblems)
                this.notifier.log ("IDS_KURZWEIL_SAMPLE_MISSING", sampleID.toString (), keymap.getName ());
            return null;
        }
        usedSampleIDs.add (sampleID);

        final int keyLow = Math.clamp (Math.max (keymap.getNoteOfEntry (firstIndex), layer.getLowKey ()), 0, 127);
        final int keyHigh = Math.min (keymap.getNoteOfEntry (lastIndex), layer.getHighKey ());
        if (keyLow > keyHigh || keyHigh < 0 || keyLow > 127)
            return null;

        // For stereo samples the entry references the left header of a left/right pair
        final List<KurzweilSampleHeader> headers = sample.getHeaders ();
        final int headerIndex = sample.isStereo () ? entry.getSubSampleNumber () - 1 & ~1 : entry.getSubSampleNumber () - 1;
        final KurzweilSampleHeader header = headerIndex >= 0 && headerIndex < headers.size () ? headers.get (headerIndex) : null;
        final KurzweilSampleHeader rightHeader = sample.isStereo () && headerIndex + 1 < headers.size () ? headers.get (headerIndex + 1) : null;
        if (header == null || sample.isStereo () && rightHeader == null)
        {
            if (reportProblems)
                this.notifier.log ("IDS_KURZWEIL_BAD_SUB_SAMPLE", Integer.toString (entry.getSubSampleNumber ()), sample.getName ());
            return null;
        }
        if (!header.hasSampleData () || rightHeader != null && !rightHeader.hasSampleData ())
        {
            if (reportProblems && reportedRomSampleIDs.add (sampleID))
                this.notifier.log ("IDS_KURZWEIL_ROM_SAMPLE", sample.getName ());
            return null;
        }

        final ISampleZone zone = new DefaultSampleZone (sample.getName (), Math.min (keyLow, keyHigh), keyHigh);
        zone.setSampleData (sampleDataCache.computeIfAbsent (Long.valueOf ((long) sample.getId () << 16 | headerIndex), _ -> createSampleData (header, rightHeader)));
        zone.setKeyRoot (header.getRootKey ());
        zone.setTuning ((entry.getTuning () + layer.getTranspose () * 100) / 100.0);
        zone.setGain (header.getVolumeAdjust ());
        zone.setStart (0);
        zone.setStop (header.getNumberOfFrames ());
        addLoop (zone, header);
        applyLayerSettings (zone, layer);
        return zone;
    }


    /**
     * Apply the amplitude envelope, the filter and the filter envelope of the program layer to the
     * zone.
     *
     * @param zone The zone
     * @param layer The program layer
     */
    private static void applyLayerSettings (final ISampleZone zone, final KurzweilProgram.Layer layer)
    {
        final KurzweilEnvelope amplitudeEnvelope = layer.getAmplitudeEnvelope ();
        if (amplitudeEnvelope != null)
            amplitudeEnvelope.toEnvelope (zone.getAmplitudeEnvelopeModulator ().getSource ());

        final FilterType filterType = layer.getFilterType ();
        if (filterType == null)
            return;
        final IFilter filter = new DefaultFilter (filterType, layer.getFilterPoles (), layer.getCutoffFrequency (), layer.getResonance ());
        final KurzweilEnvelope filterEnvelope = layer.getFilterEnvelope ();
        if (filterEnvelope != null)
        {
            final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
            modulator.setDepth (Math.clamp (layer.getFilterEnvelopeDepth () / (double) IEnvelope.MAX_ENVELOPE_DEPTH, -1, 1));
            filterEnvelope.toEnvelope (modulator.getSource ());
        }
        final int cutoffVelocityDepth = layer.getCutoffVelocityDepth ();
        if (cutoffVelocityDepth != 0)
            filter.getCutoffVelocityModulator ().setDepth (Math.clamp (cutoffVelocityDepth / (double) MAX_VELOCITY_MODULATION_CENTS, -1, 1));
        zone.setFilter (filter);
    }


    /**
     * Create one group with one zone per sample header for a sample object which is not referenced
     * by any keymap. The key ranges are spread around the root keys of the headers.
     *
     * @param sample The sample object
     * @param sampleDataCache Cache of the already converted sample data
     * @param reportedRomSampleIDs The IDs of the ROM samples which were already reported
     * @param reportProblems If true, a note is logged when a header only references device ROM
     * @return The created group
     */
    private List<IGroup> createGroupsFromSample (final KurzweilSample sample, final Map<Long, ISampleData> sampleDataCache, final Set<Integer> reportedRomSampleIDs, final boolean reportProblems)
    {
        final int headerStep = sample.isStereo () ? 2 : 1;
        final List<KurzweilSampleHeader> headers = sample.getHeaders ();

        // Collect the headers with sample data sorted by their root key
        final List<Integer> headerIndices = new ArrayList<> ();
        for (int i = 0; i + headerStep - 1 < headers.size (); i += headerStep)
            if (headers.get (i).hasSampleData () && (headerStep == 1 || headers.get (i + 1).hasSampleData ()))
                headerIndices.add (Integer.valueOf (i));
            else if (reportProblems && reportedRomSampleIDs.add (Integer.valueOf (sample.getId ())))
                this.notifier.log ("IDS_KURZWEIL_ROM_SAMPLE", sample.getName ());
        headerIndices.sort ((i1, i2) -> Integer.compare (headers.get (i1.intValue ()).getRootKey (), headers.get (i2.intValue ()).getRootKey ()));

        final IGroup group = new DefaultGroup (sample.getName ());
        for (int i = 0; i < headerIndices.size (); i++)
        {
            final int headerIndex = headerIndices.get (i).intValue ();
            final KurzweilSampleHeader header = headers.get (headerIndex);
            final KurzweilSampleHeader rightHeader = headerStep == 2 ? headers.get (headerIndex + 1) : null;

            // Split the key range in the middle between neighboring root keys
            final int rootKey = Math.clamp (header.getRootKey (), 0, 127);
            final int keyLow = i == 0 ? 0 : Math.min ((headers.get (headerIndices.get (i - 1).intValue ()).getRootKey () + rootKey) / 2 + 1, rootKey);
            final int keyHigh = i == headerIndices.size () - 1 ? 127 : Math.max ((rootKey + headers.get (headerIndices.get (i + 1).intValue ()).getRootKey ()) / 2, rootKey);

            final ISampleZone zone = new DefaultSampleZone (sample.getName (), keyLow, keyHigh);
            zone.setSampleData (sampleDataCache.computeIfAbsent (Long.valueOf ((long) sample.getId () << 16 | headerIndex), _ -> createSampleData (header, rightHeader)));
            zone.setKeyRoot (rootKey);
            zone.setGain (header.getVolumeAdjust ());
            zone.setStart (0);
            zone.setStop (header.getNumberOfFrames ());
            addLoop (zone, header);
            group.addSampleZone (zone);
        }

        return Collections.singletonList (group);
    }


    private static void addLoop (final ISampleZone zone, final KurzweilSampleHeader header)
    {
        if (!header.isLooped ())
            return;
        final ISampleLoop loop = new DefaultSampleLoop ();
        loop.setType (LoopType.FORWARDS);
        loop.setStart (header.getLoopStart ());
        loop.setEnd (header.getSampleEnd ());
        zone.getLoops ().add (loop);
    }


    /**
     * Convert the 16-bit big-endian PCM data of a sample header (or a stereo pair of headers) into
     * sample data for a zone.
     *
     * @param header The (left) sample header
     * @param rightHeader The right channel header of a stereo pair or null for mono
     * @return The sample data
     */
    private static ISampleData createSampleData (final KurzweilSampleHeader header, final KurzweilSampleHeader rightHeader)
    {
        final byte [] leftData = header.getSampleData ();
        final int numFrames = header.getNumberOfFrames ();

        if (rightHeader == null)
        {
            final byte [] pcmData = new byte [numFrames * 2];
            for (int i = 0; i < numFrames; i++)
            {
                // Convert from big-endian to little-endian
                pcmData[i * 2] = leftData[i * 2 + 1];
                pcmData[i * 2 + 1] = leftData[i * 2];
            }
            return new InMemorySampleData (new DefaultAudioMetadata (1, header.getSampleRate (), 16, numFrames), pcmData);
        }

        final byte [] rightData = rightHeader.getSampleData ();
        final int numStereoFrames = Math.min (numFrames, rightHeader.getNumberOfFrames ());
        final byte [] pcmData = new byte [numStereoFrames * 4];
        for (int i = 0; i < numStereoFrames; i++)
        {
            pcmData[i * 4] = leftData[i * 2 + 1];
            pcmData[i * 4 + 1] = leftData[i * 2];
            pcmData[i * 4 + 2] = rightData[i * 2 + 1];
            pcmData[i * 4 + 3] = rightData[i * 2];
        }
        return new InMemorySampleData (new DefaultAudioMetadata (2, header.getSampleRate (), 16, numStereoFrames), pcmData);
    }
}
