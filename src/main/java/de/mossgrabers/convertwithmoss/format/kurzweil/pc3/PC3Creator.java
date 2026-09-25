// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil.pc3;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.ShortNameSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilEnvelope;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilKeymap;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilKeymapEntry;


/**
 * Creator for Kurzweil PC3K files (.p3k), the object files with RAM samples which the PC3K,
 * Forte, Forte SE, PC4 and K2700 load. Each multi-sample becomes a program with one layer per
 * group; a layer plays a keymap with one sample object per zone and carries the amplitude envelope
 * of its group. The velocity ranges of the zones are mapped onto the 8 dynamic levels of the
 * keymap.
 *
 * @author Jürgen Moßgraber
 */
public class PC3Creator extends AbstractCreator<ShortNameSettingsUI>
{
    /** The maximum sample playback rate of the devices. */
    private static final int                    MAX_SAMPLE_RATE    = 96000;

    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, MAX_SAMPLE_RATE, false);

    private static final int                    MAX_NAME_LENGTH    = 16;
    /** The maximum number of layers of a program. */
    private static final int                    MAX_LAYERS         = 32;
    private static final String                 FILE_EXTENSION     = "p3k";

    /** The number of object IDs available per object type (1024-4095). */
    private static final int                    NUM_IDS            = PC3File.LAST_ID - PC3File.FIRST_ID + 1;


    /** The audio data and mapping parameters of one zone prepared for writing. */
    private static class PreparedZone
    {
        ISampleZone zone;
        byte [] []  channelData;
        int         sampleRate;
        int         rootKey;
        int         loopStart;
        boolean     isLooped;
        int         sampleID;
    }


    /** The prepared zones of one layer with the name of their group. */
    private static class PreparedLayer
    {
        final List<PreparedZone> zones = new ArrayList<> ();
        String                   name;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public PC3Creator (final INotifier notifier)
    {
        super ("Kurzweil PC3/Forte", "PC3", notifier, new ShortNameSettingsUI ("PC3"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final PC3File pc3File = new PC3File ();
        this.addMultisample (pc3File, multisampleSource, new HashSet<> ());
        this.writeFile (destinationFolder, multisampleSource.getName (), pc3File);
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<PC3File> parts = new ArrayList<> ();
        PC3File pc3File = new PC3File ();
        parts.add (pc3File);
        Set<String> usedNames = new HashSet<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (this.isCancelled ())
                return;

            // Start a new file when the object IDs of the current one are used up
            if (!pc3File.getPrograms ().isEmpty () && !fitsIntoFile (pc3File, multisampleSource))
            {
                pc3File = new PC3File ();
                parts.add (pc3File);
                usedNames = new HashSet<> ();
            }
            this.addMultisample (pc3File, multisampleSource, usedNames);
        }

        if (parts.size () > 1)
            this.notifier.log ("IDS_KURZWEIL_LIBRARY_SPLIT", Integer.toString (multisampleSources.size ()), Integer.toString (parts.size ()));
        for (int i = 0; i < parts.size (); i++)
            this.writeFile (destinationFolder, parts.size () > 1 ? libraryName + " " + (i + 1) : libraryName, parts.get (i));
    }


    private void writeFile (final File destinationFolder, final String name, final PC3File pc3File) throws IOException
    {
        if (pc3File.getPrograms ().isEmpty ())
            return;

        final File outputFile = this.createUniqueFilename (destinationFolder, SafeFileNames.create (name), FILE_EXTENSION);
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());
        try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (outputFile)))
        {
            pc3File.write (out);
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private static boolean fitsIntoFile (final PC3File pc3File, final IMultisampleSource multisampleSource)
    {
        int numZones = 0;
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        for (final IGroup group: groups)
            numZones += group.getSampleZones ().size ();
        return pc3File.getPrograms ().size () < NUM_IDS && pc3File.getKeymaps ().size () + groups.size () <= NUM_IDS && pc3File.getSamples ().size () + numZones <= NUM_IDS;
    }


    /**
     * Add a multi-sample as a program with one layer per group to the file. Each layer plays a
     * keymap with one sample object per zone.
     *
     * @param pc3File The file to add the objects to
     * @param multisampleSource The multi-sample to add
     * @param usedNames All object names used in the file so far to create unique ones
     * @throws IOException Could not convert the sample data
     */
    private void addMultisample (final PC3File pc3File, final IMultisampleSource multisampleSource, final Set<String> usedNames) throws IOException
    {
        // The object names hold 16 characters, which the device displays
        final String sourceName = multisampleSource.getName ();
        final String name = this.settingsConfiguration.isShortenName () ? createShortName (sourceName) : sourceName;

        // Samples above the maximum playback rate of the devices are down-sampled
        this.recalculateAllSamplePositions (multisampleSource, MAX_SAMPLE_RATE, true);

        // Each group becomes a layer; the groups beyond the maximum number of layers share the
        // last layer
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        final List<PreparedLayer> preparedLayers = new ArrayList<> ();
        boolean hasFilter = false;
        for (final IGroup group: groups)
        {
            final List<PreparedZone> preparedZones = new ArrayList<> ();
            for (final ISampleZone zone: group.getSampleZones ())
            {
                hasFilter |= zone.getFilter ().isPresent ();
                final PreparedZone preparedZone = this.prepareZone (zone);
                if (preparedZone != null)
                    preparedZones.add (preparedZone);
            }
            if (preparedZones.isEmpty ())
                continue;

            if (preparedLayers.size () < MAX_LAYERS)
            {
                final PreparedLayer preparedLayer = new PreparedLayer ();
                preparedLayer.name = group.getName () == null || group.getName ().isBlank () ? name : group.getName ();
                preparedLayers.add (preparedLayer);
            }
            preparedLayers.get (preparedLayers.size () - 1).zones.addAll (preparedZones);
        }
        if (preparedLayers.isEmpty ())
        {
            this.notifier.logError ("IDS_KURZWEIL_NO_ZONES", name);
            return;
        }
        if (groups.size () > MAX_LAYERS)
            this.notifier.log ("IDS_PC3_TOO_MANY_GROUPS", Integer.toString (groups.size ()), name);
        if (hasFilter)
            this.notifier.log ("IDS_PC3_FILTER_DROPPED", name);

        final int programID = PC3File.FIRST_ID + pc3File.getPrograms ().size ();
        final PC3Program program = new PC3Program (programID, shortenName (name));
        int numConflicts = 0;
        int numOutOfRange = 0;
        for (final PreparedLayer preparedLayer: preparedLayers)
        {
            final List<PreparedZone> preparedZones = preparedLayer.zones;

            // A layer is stereo if any of its zones is stereo
            boolean isStereo = false;
            for (final PreparedZone preparedZone: preparedZones)
                isStereo |= preparedZone.channelData.length == 2;

            // Create one sample object per zone
            for (final PreparedZone preparedZone: preparedZones)
            {
                final int sampleID = PC3File.FIRST_ID + pc3File.getSamples ().size ();
                if (sampleID > PC3File.LAST_ID)
                {
                    this.notifier.logError ("IDS_PC3_TOO_MANY_OBJECTS", preparedZone.zone.getName ());
                    break;
                }
                preparedZone.sampleID = sampleID;
                pc3File.getSamples ().put (Integer.valueOf (sampleID), createSample (sampleID, createUniqueName (preparedZone.zone.getName (), usedNames), preparedZone, isStereo));
            }

            // Collect for each of the 8 dynamic levels the zones whose velocity range intersects
            // its band, zones with the largest overlap first
            final List<List<PreparedZone>> zonesOfLevel = new ArrayList<> ();
            for (int level = 0; level < KurzweilKeymap.NUM_LEVELS; level++)
            {
                final int bandLow = level * 16;
                final int bandHigh = bandLow + 15;
                final List<PreparedZone> levelZones = new ArrayList<> ();
                for (final PreparedZone preparedZone: preparedZones)
                {
                    if (preparedZone.sampleID == 0)
                        continue;
                    final int velocityLow = Math.clamp (preparedZone.zone.getVelocityLow (), 0, 127);
                    final int velocityHigh = Math.clamp (preparedZone.zone.getVelocityHigh (), velocityLow, 127);
                    if (velocityLow <= bandHigh && velocityHigh >= bandLow)
                        levelZones.add (preparedZone);
                }
                levelZones.sort ((z1, z2) -> Integer.compare (calcBandOverlap (z2.zone, bandLow, bandHigh), calcBandOverlap (z1.zone, bandLow, bandHigh)));
                zonesOfLevel.add (levelZones);
            }

            // Create the keymap with one entry table per distinct zone set; levels with identical
            // zones share a table
            final int keymapID = PC3File.FIRST_ID + pc3File.getKeymaps ().size ();
            final KurzweilKeymap keymap = new KurzweilKeymap (keymapID, createUniqueName (preparedLayer.name, usedNames));
            final Map<List<PreparedZone>, Integer> tableOfZones = new LinkedHashMap<> ();
            for (int level = 0; level < KurzweilKeymap.NUM_LEVELS; level++)
            {
                final List<PreparedZone> levelZones = zonesOfLevel.get (level);
                Integer tableIndex = tableOfZones.get (levelZones);
                if (tableIndex == null)
                {
                    final KurzweilKeymapEntry [] entries = new KurzweilKeymapEntry [KurzweilKeymap.NUM_ENTRIES];
                    for (int i = 0; i < entries.length; i++)
                        entries[i] = new KurzweilKeymapEntry ();
                    for (final PreparedZone preparedZone: levelZones)
                    {
                        numConflicts += fillEntries (entries, preparedZone);
                        numOutOfRange += Math.max (0, KurzweilKeymap.BASE_NOTE - preparedZone.zone.getKeyLow ());
                    }
                    tableIndex = Integer.valueOf (keymap.addEntryTable (entries));
                    tableOfZones.put (levelZones, tableIndex);
                }
                keymap.setTableIndexOfLevel (level, tableIndex.intValue ());
            }
            pc3File.getKeymaps ().put (Integer.valueOf (keymapID), keymap);

            program.addLayer (createLayer (preparedZones, keymapID, isStereo));
        }

        if (numConflicts > 0)
            this.notifier.logError ("IDS_KURZWEIL_OVERLAPPING_ZONES", Integer.toString (numConflicts), name);
        if (numOutOfRange > 0)
            this.notifier.logError ("IDS_KURZWEIL_KEYS_OUT_OF_RANGE", name);

        pc3File.getPrograms ().add (program);
    }


    /**
     * Create a layer of the program: it plays a keymap on the full key and velocity range and
     * carries the amplitude envelope of its zones if present.
     *
     * @param preparedZones The zones of the layer
     * @param keymapID The ID of the keymap object of the layer
     * @param isStereo True if the keymap references stereo samples
     * @return The layer
     */
    private static PC3Program.Layer createLayer (final List<PreparedZone> preparedZones, final int keymapID, final boolean isStereo)
    {
        final PC3Program.Layer layer = new PC3Program.Layer ();
        layer.setKeymapID (keymapID);
        layer.setStereo (isStereo);

        // The zones of a group share their envelope, take it from the first one
        final IEnvelopeModulator amplitudeModulator = preparedZones.get (0).zone.getAmplitudeEnvelopeModulator ();
        if (amplitudeModulator.getDepth () > 0)
        {
            final IEnvelope source = amplitudeModulator.getSource ();
            if (source.isSet ())
            {
                final KurzweilEnvelope envelope = new KurzweilEnvelope ();
                envelope.fromEnvelope (source);
                layer.setAmplitudeEnvelope (envelope);
            }
        }

        return layer;
    }


    private static int calcBandOverlap (final ISampleZone zone, final int bandLow, final int bandHigh)
    {
        final int velocityLow = Math.clamp (zone.getVelocityLow (), 0, 127);
        final int velocityHigh = Math.clamp (zone.getVelocityHigh (), velocityLow, 127);
        return Math.min (velocityHigh, bandHigh) - Math.max (velocityLow, bandLow);
    }


    /**
     * Fill the keymap entries covered by the key range of the zone.
     *
     * @param entries The entries to fill
     * @param preparedZone The prepared zone
     * @return The number of entries which were already used by another zone
     */
    private static int fillEntries (final KurzweilKeymapEntry [] entries, final PreparedZone preparedZone)
    {
        int numConflicts = 0;
        final ISampleZone zone = preparedZone.zone;
        final double keyTracking = zone.getKeyTracking ();
        final int tuningCents = (int) Math.round (zone.getTuning () * 100);

        final int lowKey = Math.max (zone.getKeyLow (), KurzweilKeymap.BASE_NOTE);
        final int highKey = Math.min (zone.getKeyHigh (), KurzweilKeymap.BASE_NOTE + KurzweilKeymap.NUM_ENTRIES - 1);
        for (int note = lowKey; note <= highKey; note++)
        {
            final KurzweilKeymapEntry entry = entries[note - KurzweilKeymap.BASE_NOTE];
            if (entry.isUsed ())
            {
                numConflicts++;
                continue;
            }
            entry.setSampleID (preparedZone.sampleID);
            entry.setSubSampleNumber (1);
            // The device tracks the keyboard chromatically relative to the root key; the entry
            // tuning holds the offset from that plus partial or disabled key tracking
            entry.setTuning (Math.clamp (Math.round ((keyTracking - 1) * (note - preparedZone.rootKey) * 100) + tuningCents, Short.MIN_VALUE, Short.MAX_VALUE));
        }
        return numConflicts;
    }


    /**
     * Convert the audio data of a zone to 16-bit PCM and apply the start, stop and loop range.
     * Since the device plays the loop until the end of the sample, the data is cut after the loop
     * end.
     *
     * @param zone The zone to prepare
     * @return The prepared zone or null if the audio format is not supported
     * @throws IOException Could not convert the sample data
     */
    private PreparedZone prepareZone (final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }

        this.logResampling (zone, DESTINATION_FORMAT);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return null;
        }

        final byte [] pcmData = waveFile.getDataChunk ().getData ();
        final int numFrames = pcmData.length / (2 * numChannels);
        if (numFrames == 0)
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
            return null;
        }
        final int start = Math.clamp (zone.getStart (), 0, Math.max (0, numFrames - 1));
        int endInclusive = (zone.getStop () > 0 ? Math.min (zone.getStop (), numFrames) : numFrames) - 1;

        final PreparedZone preparedZone = new PreparedZone ();
        preparedZone.zone = zone;
        preparedZone.sampleRate = waveFile.getFormatChunk ().getSampleRate ();
        preparedZone.rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);

        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            final int loopEnd = Math.min (loop.getEnd (), numFrames - 1);
            if (loopEnd > start && loop.getStart () < loopEnd)
            {
                endInclusive = loopEnd;
                preparedZone.isLooped = true;
                preparedZone.loopStart = Math.clamp (loop.getStart () - (long) start, 0, loopEnd - start);
            }
        }
        if (endInclusive < start)
            endInclusive = start;

        preparedZone.channelData = new byte [numChannels] [];
        for (int channel = 0; channel < numChannels; channel++)
            preparedZone.channelData[channel] = extractChannelBigEndian (pcmData, numChannels, channel, start, endInclusive);
        return preparedZone;
    }


    /**
     * Create a sample object for a prepared zone. In a stereo layer all sample objects are stereo;
     * the data of mono zones is duplicated to both channels.
     *
     * @param sampleID The object ID for the sample
     * @param name The name of the sample object
     * @param preparedZone The prepared zone
     * @param isStereo True if the layer is stereo
     * @return The sample object
     */
    private static PC3Sample createSample (final int sampleID, final String name, final PreparedZone preparedZone, final boolean isStereo)
    {
        final PC3Sample sample = new PC3Sample (sampleID, name);
        sample.setStereo (isStereo);
        final int numChannels = isStereo ? 2 : 1;
        for (int channel = 0; channel < numChannels; channel++)
        {
            final PC3SampleHeader header = new PC3SampleHeader ();
            header.setSampleData (preparedZone.channelData[Math.min (channel, preparedZone.channelData.length - 1)]);
            header.setSampleRate (preparedZone.sampleRate);
            header.setRootKey (preparedZone.rootKey);
            header.setVolumeAdjust (preparedZone.zone.getGain ());
            if (preparedZone.isLooped)
                header.setLoopStartFrame (preparedZone.loopStart);
            sample.addHeader (header);
        }
        return sample;
    }


    /**
     * Extract one channel from interleaved little-endian 16-bit PCM data as big-endian data.
     *
     * @param pcmData The interleaved little-endian PCM data
     * @param numChannels The number of channels in the data
     * @param channel The channel to extract
     * @param startFrame The first frame to extract
     * @param endFrameInclusive The last frame to extract
     * @return The big-endian channel data
     */
    private static byte [] extractChannelBigEndian (final byte [] pcmData, final int numChannels, final int channel, final int startFrame, final int endFrameInclusive)
    {
        final int numFrames = endFrameInclusive - startFrame + 1;
        final byte [] channelData = new byte [numFrames * 2];
        for (int i = 0; i < numFrames; i++)
        {
            final int src = ((startFrame + i) * numChannels + channel) * 2;
            // Convert from little-endian to big-endian
            channelData[i * 2] = pcmData[src + 1];
            channelData[i * 2 + 1] = pcmData[src];
        }
        return channelData;
    }


    /**
     * Shorten a name to the maximum object name length and replace all non-ASCII characters.
     *
     * @param name The name
     * @return The shortened name
     */
    private static String shortenName (final String name)
    {
        final StringBuilder shortened = new StringBuilder ();
        for (int i = 0; i < name.length () && shortened.length () < MAX_NAME_LENGTH; i++)
        {
            final char c = name.charAt (i);
            shortened.append (c >= 32 && c <= 126 ? c : '_');
        }
        return shortened.toString ().trim ();
    }


    /**
     * Create a unique object name with the maximum object name length.
     *
     * @param name The name
     * @param usedNames The names used so far in the file
     * @return The unique name
     */
    private static String createUniqueName (final String name, final Set<String> usedNames)
    {
        String base = shortenName (name);
        if (base.isBlank ())
            base = "Unnamed";
        String uniqueName = base;
        int counter = 1;
        while (!usedNames.add (uniqueName))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            uniqueName = base.substring (0, Math.min (base.length (), MAX_NAME_LENGTH - suffix.length ())) + suffix;
        }
        return uniqueName;
    }
}
