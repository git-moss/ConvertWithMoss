// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

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
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;


/**
 * Creator for Kurzweil K2000/K2500/K2600 files. The written files use only K2000 features and
 * therefore load on all three device families; the selected target device sets the file extension
 * (.krz, .k25 or .k26). Each multi-sample becomes a program with one layer, a keymap and one sample
 * object per zone; the velocity layers are mapped onto the 8 dynamic levels of the keymap. The
 * layer carries the global amplitude envelope, filter and filter envelope of the multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilCreator extends AbstractCreator<KurzweilCreatorUI>
{
    /** The maximum sample playback rate of the devices. */
    private static final int                    MAX_SAMPLE_RATE    = 96000;

    private static final DestinationAudioFormat DESTINATION_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, MAX_SAMPLE_RATE, false);

    private static final int                    MAX_NAME_LENGTH    = 16;

    /** The number of object IDs available per object type (200-999). */
    private static final int                    NUM_IDS            = KurzweilObjectID.LAST_ID - KurzweilObjectID.FIRST_ID + 1;


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


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KurzweilCreator (final INotifier notifier)
    {
        super ("Kurzweil K2x00", "Kurzweil", notifier, new KurzweilCreatorUI ());
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
        final KurzweilFile kurzweilFile = new KurzweilFile ();
        this.addMultisample (kurzweilFile, multisampleSource, new HashSet<> ());
        this.writeFile (destinationFolder, multisampleSource.getName (), kurzweilFile);
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<KurzweilFile> parts = new ArrayList<> ();
        KurzweilFile kurzweilFile = new KurzweilFile ();
        parts.add (kurzweilFile);
        Set<String> usedNames = new HashSet<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (this.isCancelled ())
                return;

            // Start a new file when the object IDs of the current one are used up
            if (!kurzweilFile.getPrograms ().isEmpty () && !fitsIntoFile (kurzweilFile, multisampleSource))
            {
                kurzweilFile = new KurzweilFile ();
                parts.add (kurzweilFile);
                usedNames = new HashSet<> ();
            }
            this.addMultisample (kurzweilFile, multisampleSource, usedNames);
        }

        if (parts.size () > 1)
            this.notifier.log ("IDS_KURZWEIL_LIBRARY_SPLIT", Integer.toString (multisampleSources.size ()), Integer.toString (parts.size ()));
        for (int i = 0; i < parts.size (); i++)
            this.writeFile (destinationFolder, parts.size () > 1 ? libraryName + " " + (i + 1) : libraryName, parts.get (i));
    }


    private void writeFile (final File destinationFolder, final String name, final KurzweilFile kurzweilFile) throws IOException
    {
        if (kurzweilFile.getPrograms ().isEmpty ())
            return;

        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (name), this.settingsConfiguration.getTargetDevice ().getExtension ());
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());
        try (final OutputStream out = new BufferedOutputStream (new FileOutputStream (outputFile)))
        {
            kurzweilFile.write (out);
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private static boolean fitsIntoFile (final KurzweilFile kurzweilFile, final IMultisampleSource multisampleSource)
    {
        int numZones = 0;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            numZones += group.getSampleZones ().size ();
        return kurzweilFile.getPrograms ().size () < NUM_IDS && kurzweilFile.getKeymaps ().size () < NUM_IDS && kurzweilFile.getSamples ().size () + numZones <= NUM_IDS;
    }


    /**
     * Add a multi-sample as a program with a keymap and one sample object per zone to the file.
     *
     * @param kurzweilFile The file to add the objects to
     * @param multisampleSource The multi-sample to add
     * @param usedNames All object names used in the file so far to create unique ones
     * @throws IOException Could not convert the sample data
     */
    private void addMultisample (final KurzweilFile kurzweilFile, final IMultisampleSource multisampleSource, final Set<String> usedNames) throws IOException
    {
        final String name = multisampleSource.getName ();

        // Samples above the maximum playback rate of the devices are down-sampled
        recalculateAllSamplePositions (multisampleSource, MAX_SAMPLE_RATE, true);

        // Convert all zones first to know if the program needs to be stereo
        final List<PreparedZone> preparedZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final PreparedZone preparedZone = this.prepareZone (zone);
                if (preparedZone != null)
                    preparedZones.add (preparedZone);
            }
        if (preparedZones.isEmpty ())
        {
            this.notifier.logError ("IDS_KURZWEIL_NO_ZONES", name);
            return;
        }
        boolean isStereo = false;
        for (final PreparedZone preparedZone: preparedZones)
            isStereo |= preparedZone.channelData.length == 2;

        // Create one sample object per zone
        for (final PreparedZone preparedZone: preparedZones)
        {
            final int sampleID = KurzweilObjectID.FIRST_ID + kurzweilFile.getSamples ().size ();
            if (sampleID > KurzweilObjectID.LAST_ID)
            {
                this.notifier.logError ("IDS_KURZWEIL_TOO_MANY_OBJECTS", preparedZone.zone.getName ());
                break;
            }
            preparedZone.sampleID = sampleID;
            kurzweilFile.getSamples ().put (Integer.valueOf (sampleID), createSample (sampleID, createUniqueName (preparedZone.zone.getName (), usedNames), preparedZone, isStereo));
        }

        // Collect for each of the 8 dynamic levels the zones whose velocity range intersects its
        // band, zones with the largest overlap first
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
        final int keymapID = KurzweilObjectID.FIRST_ID + kurzweilFile.getKeymaps ().size ();
        final KurzweilKeymap keymap = new KurzweilKeymap (keymapID, createUniqueName (name, usedNames));
        final Map<List<PreparedZone>, Integer> tableOfZones = new LinkedHashMap<> ();
        int numConflicts = 0;
        int numOutOfRange = 0;
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
        kurzweilFile.getKeymaps ().put (Integer.valueOf (keymapID), keymap);

        if (numConflicts > 0)
            this.notifier.logError ("IDS_KURZWEIL_OVERLAPPING_ZONES", Integer.toString (numConflicts), name);
        if (numOutOfRange > 0)
            this.notifier.logError ("IDS_KURZWEIL_KEYS_OUT_OF_RANGE", name);

        // Create the program with a single layer which plays the keymap
        final int programID = KurzweilObjectID.FIRST_ID + kurzweilFile.getPrograms ().size ();
        final KurzweilProgram program = new KurzweilProgram (programID, shortenName (name));
        program.addProgramBlock ();
        program.addLayer (createLayer (multisampleSource, keymapID), isStereo);
        kurzweilFile.getPrograms ().add (program);
    }


    /**
     * Create the layer of the program: it plays the keymap on the full key and velocity range and
     * carries the global amplitude envelope, filter and filter envelope of the multi-sample if
     * present.
     *
     * @param multisampleSource The multi-sample source
     * @param keymapID The ID of the keymap object of the layer
     * @return The layer
     */
    private static KurzweilProgram.Layer createLayer (final IMultisampleSource multisampleSource, final int keymapID)
    {
        final KurzweilProgram.Layer layer = new KurzweilProgram.Layer ();
        layer.setKeymapID (keymapID);

        final Optional<IEnvelopeModulator> amplitudeModulator = multisampleSource.getGlobalAmplitudeModulator ();
        if (amplitudeModulator.isPresent () && amplitudeModulator.get ().getDepth () > 0)
        {
            final IEnvelope source = amplitudeModulator.get ().getSource ();
            if (source.isSet ())
            {
                final KurzweilEnvelope envelope = new KurzweilEnvelope ();
                envelope.fromEnvelope (source);
                layer.setAmplitudeEnvelope (envelope);
            }
        }

        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final IFilter filter = globalFilter.get ();
            layer.setFilter (filter.getType (), filter.getPoles (), filter.getCutoff (), filter.getResonance ());

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            final int depth = (int) Math.round (cutoffModulator.getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH);
            if (depth != 0 && cutoffModulator.getSource ().isSet ())
            {
                final KurzweilEnvelope envelope = new KurzweilEnvelope ();
                envelope.fromEnvelope (cutoffModulator.getSource ());
                layer.setFilterEnvelope (envelope, depth);
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
     * Create a sample object for a prepared zone. In a stereo program all sample objects are
     * stereo; the data of mono zones is duplicated to both channels.
     *
     * @param sampleID The object ID for the sample
     * @param name The name of the sample object
     * @param preparedZone The prepared zone
     * @param isStereo True if the program is stereo
     * @return The sample object
     */
    private static KurzweilSample createSample (final int sampleID, final String name, final PreparedZone preparedZone, final boolean isStereo)
    {
        final KurzweilSample sample = new KurzweilSample (sampleID, name);
        sample.setStereo (isStereo);
        final int numChannels = isStereo ? 2 : 1;
        for (int channel = 0; channel < numChannels; channel++)
        {
            final KurzweilSampleHeader header = new KurzweilSampleHeader ();
            header.setSampleData (preparedZone.channelData[Math.min (channel, preparedZone.channelData.length - 1)]);
            header.setSampleRate (preparedZone.sampleRate);
            header.setRootKey (preparedZone.rootKey);
            header.setVolumeAdjust (preparedZone.zone.getGain ());
            if (preparedZone.isLooped)
                header.setLoopStart (preparedZone.loopStart);
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
