// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.convertwithmoss.core.NoteParser;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.MultisampleException;
import de.mossgrabers.convertwithmoss.exception.NoteNotDetectedException;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects MIDI notes from filenames and creates a key mapping for the multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class KeyMapping
{
    /** The names of notes. */
    private static final String []                  NOTE_NAMES_FLAT         =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "B"
    };
    private static final String []                  NOTE_NAMES_SHARP        =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };
    /** The names of notes. */
    private static final String []                  NOTE_NAMES_FLAT_GERMAN  =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "H"
    };
    private static final String []                  NOTE_NAMES_SHARP_GERMAN =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "H"
    };

    private static final List<Map<String, Integer>> KEY_MAP                 = new ArrayList<> (10);

    static
    {
        for (int i = 0; i < 10; i++)
            KEY_MAP.add (new HashMap<> ());

        // Create note map
        for (int note = 0; note < 128; note++)
        {
            final int n = Math.abs (note % 12);
            final String octave = Integer.toString (note / 12 - 2);
            final Integer ni = Integer.valueOf (note);
            KEY_MAP.get (0).put (NOTE_NAMES_FLAT[n] + octave, ni);
            KEY_MAP.get (1).put (NOTE_NAMES_SHARP[n] + octave, ni);
            KEY_MAP.get (2).put (NOTE_NAMES_FLAT_GERMAN[n] + octave, ni);
            KEY_MAP.get (3).put (NOTE_NAMES_SHARP_GERMAN[n] + octave, ni);
            KEY_MAP.get (4).put (NOTE_NAMES_FLAT[n] + "_" + octave, ni);
            KEY_MAP.get (5).put (NOTE_NAMES_SHARP[n] + "_" + octave, ni);
            KEY_MAP.get (6).put (NOTE_NAMES_FLAT_GERMAN[n] + "_" + octave, ni);
            KEY_MAP.get (7).put (NOTE_NAMES_SHARP_GERMAN[n] + "_" + octave, ni);
            KEY_MAP.get (8).put (String.format ("%03d", ni), ni);
            KEY_MAP.get (9).put (String.format ("%02d", ni), ni);
        }
    }

    private final List<IGroup> orderedSampleMetadata;
    private final Set<String>  extractedNames = new HashSet<> ();
    private final String       name;


    /**
     * Constructor.
     *
     * @param sampleData The sample data from which to get the filenames and set the key ranges.
     * @param isAscending Sort ascending otherwise descending
     * @param crossfadeNotes The number of notes to cross-fade ranges
     * @param crossfadeVelocities The number of velocity steps to cross-fade ranges
     * @param groupPatterns The group patterns
     * @param leftChannelPatterns The left channel detection patterns
     * @throws MultisampleException Found duplicated MIDI notes
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    public KeyMapping (final List<IFileBasedSampleData> sampleData, final boolean isAscending, final int crossfadeNotes, final int crossfadeVelocities, final String [] groupPatterns, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        this.orderedSampleMetadata = this.createGroups (sampleData, isAscending, crossfadeNotes, groupPatterns, leftChannelPatterns);
        this.name = findCommonPrefix (new ArrayList<> (this.extractedNames));

        // Calculate velocity cross-fades
        final int range = 127 / this.orderedSampleMetadata.size ();
        int low = 0;
        int high = range;
        final int crossfadeVel = Math.min (range, crossfadeVelocities);
        for (final IGroup group: this.orderedSampleMetadata)
        {
            int velHigh = Math.min (high + crossfadeVel, 127);
            final int next = high + range;
            // Make sure that the last group always reaches 127
            if (next > 127)
                velHigh = 127;
            final int crossfadeHigh = velHigh == 127 ? 0 : Math.min (velHigh - low, crossfadeVel);

            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setVelocityLow (low);
                zone.setVelocityCrossfadeLow (0);
                zone.setVelocityHigh (velHigh);
                zone.setVelocityCrossfadeHigh (crossfadeHigh);
            }

            low = high + 1;
            high = Math.min (next, 127);
        }
    }


    /**
     * Get the detected name of the multi-sample.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the sample metadata ordered by their root notes.
     *
     * @return The sample metadata list by group
     */
    public List<IGroup> getSampleMetadata ()
    {
        return this.orderedSampleMetadata;
    }


    /**
     * Detect and create a group order.
     *
     * @param sampleData The sample data from which to get the filenames and set the key ranges.
     * @param isAscending Sort ascending otherwise descending
     * @param crossfadeNotes The number of notes to cross-fade ranges
     * @param groupPatterns The group patterns
     * @param leftChannelPatterns The left channel detection patterns
     * @return The created groups
     * @throws MultisampleException Found duplicated MIDI notes
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    private List<IGroup> createGroups (final List<IFileBasedSampleData> sampleData, final boolean isAscending, final int crossfadeNotes, final String [] groupPatterns, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        final Map<Integer, List<ISampleZone>> sampleMetadata = new TreeMap<> ();
        final Map<Integer, List<ISampleZone>> groups = detectGroups (sampleData, groupPatterns);

        for (final Entry<Integer, List<ISampleZone>> entry: groups.entrySet ())
        {
            final List<ISampleZone> group = entry.getValue ();
            for (final ISampleZone zone: group)
                this.extractedNames.add (zone.getName ());

            final Map<Integer, List<ISampleZone>> noteMap = detectNotes (group);
            final Map<Integer, ISampleZone> groupNoteMap = convertSplitStereo (noteMap, leftChannelPatterns);
            sampleMetadata.put (entry.getKey (), createKeyMaps (groupNoteMap));

            if (crossfadeNotes > 0 && crossfadeNotes < 128)
                createCrossfades (groupNoteMap, crossfadeNotes);
        }

        return orderGroups (sampleMetadata, isAscending);
    }


    /**
     * Get the sample metadata ordered by their root notes.
     *
     * @param groupMapping The groups to sort
     * @param isAscending Sort ascending otherwise descending
     * @return The sample metadata list by group
     */
    private static List<IGroup> orderGroups (final Map<Integer, List<ISampleZone>> groupMapping, final boolean isAscending)
    {
        final Collection<List<ISampleZone>> groups = groupMapping.values ();
        final List<IGroup> orderedGroups = new ArrayList<> (groups.size ());

        // Order descending
        groups.forEach (groupSampleMetadata -> {

            final IGroup group = new DefaultGroup (new ArrayList<> (groupSampleMetadata));
            if (isAscending)
                orderedGroups.add (group);
            else
                orderedGroups.add (0, group);

        });

        for (int i = 0; i < orderedGroups.size (); i++)
            orderedGroups.get (i).setName ("Group " + (i + 1));

        return orderedGroups;
    }


    /**
     * Check how many samples are assigned to a note. If there is only one, return that result. If
     * there are two, try to combine the mono files into a stereo file. If there are more than 2
     * throw an exception.
     *
     * @param noteMap The assigned sample zones
     * @param leftChannelPatterns The left channel detection patterns
     * @return The result
     * @throws MultisampleException If there are more than 2 samples assigned to a note
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    private static Map<Integer, ISampleZone> convertSplitStereo (final Map<Integer, List<ISampleZone>> noteMap, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        // Check if each note has assigned 1 or 2 files all other cases give an exception
        int noOfAssignedSamples = -1;
        for (final Entry<Integer, List<ISampleZone>> entry: noteMap.entrySet ())
        {
            final List<ISampleZone> zones = entry.getValue ();

            final int size = zones.size ();
            if (noOfAssignedSamples == -1)
            {
                if (size > 2)
                    throw new MultisampleException (Functions.getMessage ("IDS_WAV_MORE_THAN_2_FILES"), entry);
                if (size == 2)
                    for (final ISampleZone zone: zones)
                        try
                        {
                            if (!zone.getSampleData ().getAudioMetadata ().isMono ())
                                throw new MultisampleException (Functions.getMessage ("IDS_WAV_FILES_MUST_BE_MONO"), entry);
                        }
                        catch (final IOException ex)
                        {
                            throw new MultisampleException (ex.getMessage (), entry);
                        }
                noOfAssignedSamples = size;
            }
            else if (noOfAssignedSamples != size)
                throw new MultisampleException (Functions.getMessage ("IDS_WAV_DIFFERENT_NUMBER_OF_FILES"), entry);
        }

        if (noOfAssignedSamples == 1)
        {
            final Map<Integer, ISampleZone> result = new TreeMap<> ();
            for (final Entry<Integer, List<ISampleZone>> entry: noteMap.entrySet ())
                result.put (entry.getKey (), entry.getValue ().get (0));
            return result;
        }
        return combineAllMonoToStereo (noteMap, leftChannelPatterns);
    }


    /**
     * Combines mono left/right channels into stereo files for all notes.
     *
     * @param noteMap The mono files assigned to notes
     * @param leftChannelPatterns The left channel detection patterns
     * @return The combined samples assigned to notes
     * @throws CombinationNotPossibleException Could not combine the samples
     */
    private static Map<Integer, ISampleZone> combineAllMonoToStereo (final Map<Integer, List<ISampleZone>> noteMap, final String [] leftChannelPatterns) throws CombinationNotPossibleException
    {
        final Map<Integer, ISampleZone> result = new TreeMap<> ();
        for (final Entry<Integer, List<ISampleZone>> entry: noteMap.entrySet ())
        {
            final List<ISampleZone> samples = entry.getValue ();
            result.put (entry.getKey (), combineMonoToStereo (samples.get (0), samples.get (1), leftChannelPatterns));
        }
        return result;
    }


    /**
     * Combines a left/right channel into a stereo file.
     *
     * @param first The first sample zone
     * @param second The second sample zone
     * @param leftChannelPatterns Detection patterns for the left channel, e.g. "_L"
     * @return First item is the left channel, second is the right channel
     * @throws CombinationNotPossibleException Could not detect the left channel
     */
    private static ISampleZone combineMonoToStereo (final ISampleZone first, final ISampleZone second, final String [] leftChannelPatterns) throws CombinationNotPossibleException
    {
        final String firstFilename = first.getName ();
        final String secondFilename = second.getName ();

        // First try a safer detection at the end of the name
        for (final String pattern: leftChannelPatterns)
        {
            if (firstFilename.endsWith (pattern))
                return combineLeftRight (first, second, pattern);
            if (secondFilename.endsWith (pattern))
                return combineLeftRight (second, first, pattern);
        }

        // Now, if not found, try the full name
        for (final String pattern: leftChannelPatterns)
        {
            if (firstFilename.contains (pattern))
                return combineLeftRight (first, second, pattern);
            if (secondFilename.contains (pattern))
                return combineLeftRight (second, first, pattern);
        }

        throw new CombinationNotPossibleException (Functions.getMessage ("IDS_WAV_NO_LEFT_CHANNEL"));
    }


    /**
     * Combines the left and right channel zones into one sample zone with a stereo wave file.
     *
     * @param leftChannelZone The left channel zone
     * @param rightChannelZone The right channel zone
     * @param pattern The matched pattern for the left channel
     * @return The combined sample zone
     * @throws CombinationNotPossibleException The files are not mono files or have different sample
     *             or loop lengths
     */
    private static ISampleZone combineLeftRight (final ISampleZone leftChannelZone, final ISampleZone rightChannelZone, final String pattern) throws CombinationNotPossibleException
    {
        // Always true
        if (leftChannelZone.getSampleData () instanceof final WavFileSampleData leftChannel && rightChannelZone.getSampleData () instanceof final WavFileSampleData rightChannel)
        {
            leftChannel.combine (rightChannel);
            leftChannelZone.setName (leftChannel.getFilename ().replace (pattern, ""));
            return leftChannelZone;
        }
        throw new CombinationNotPossibleException (Functions.getMessage ("IDS_WAV_COMBINATION_NOT_POSSIBLE"));
    }


    /**
     * Detect groups.
     *
     * @param sampleData Info about all available samples
     * @param groupPatterns The patterns to match for groups
     * @return The detected groups
     * @throws MultisampleException There was a pattern detected but (or more) of the samples could
     *             not be matched
     */
    private static Map<Integer, List<ISampleZone>> detectGroups (final List<IFileBasedSampleData> sampleData, final String [] groupPatterns) throws MultisampleException
    {
        final Map<Integer, List<ISampleZone>> groups = new TreeMap<> ();

        // If no groups are detected create one group which contains all samples
        final Optional<Pattern> patternResult = getGroupPattern (sampleData, groupPatterns);
        if (patternResult.isEmpty ())
        {
            final List<ISampleZone> zones = new ArrayList<> (sampleData.size ());
            for (final IFileBasedSampleData si: sampleData)
                zones.add (new DefaultSampleZone (FileUtils.getNameWithoutType (new File (si.getFilename ())), si));
            groups.put (Integer.valueOf (0), zones);
            return groups;
        }

        // Now match all sample names with the detected group pattern
        final Pattern pattern = patternResult.get ();
        for (final IFileBasedSampleData si: sampleData)
        {
            final String filename = si.getFilename ();
            final Matcher matcher = pattern.matcher (filename);
            if (!matcher.matches ())
                throw new MultisampleException (Functions.getMessage ("IDS_WAV_NO_VEL_GROUP_DETECTED", filename));
            try
            {
                final String number = matcher.group ("value");
                final Integer id = Integer.valueOf (number);
                // Not used: matcher.group ("prefix");
                // Not used: matcher.group ("postfix");
                final ISampleZone zone = new DefaultSampleZone (FileUtils.getNameWithoutType (new File (filename)), si);
                groups.computeIfAbsent (id, key -> new ArrayList<> ()).add (zone);
            }
            catch (final NumberFormatException ex)
            {
                throw new MultisampleException (Functions.getMessage ("IDS_WAV_NO_VEL_GROUP_DETECTED", filename));
            }
        }

        return groups;
    }


    /**
     * Check if one of the group patterns matches.
     *
     * @param sampleData The sample data
     * @param groupPatterns The patterns to detect groups
     * @return The matching pattern or null
     * @throws MultisampleException If a pattern could not be parsed
     */
    private static Optional<Pattern> getGroupPattern (final List<IFileBasedSampleData> sampleData, final String [] groupPatterns) throws MultisampleException
    {
        if (groupPatterns.length == 0 || sampleData.isEmpty ())
            return Optional.empty ();
        final String filename = sampleData.get (0).getFilename ();
        for (final String groupPattern: groupPatterns)
        {
            final String [] parts = groupPattern.split ("\\*");
            String query;

            if (parts.length == 1)
            {
                // * is at the end or the beginning
                if (groupPattern.endsWith ("*"))
                    query = "(?<prefix>.*)" + Pattern.quote (parts[0]) + "(?<value>\\d+)(?<postfix>.*)";
                else
                    query = "(?<prefix>.*)(?<value>\\d+)" + Pattern.quote (parts[0]) + "(?<postfix>.*)";
            }
            else if (parts.length == 2)
                // * is in the middle
                query = "(?<prefix>.*)" + Pattern.quote (parts[0]) + "(?<value>\\d+)" + Pattern.quote (parts[1]) + "(?<postfix>.*)";
            else
                throw new MultisampleException (Functions.getMessage ("IDS_WAV_ERR_IN_GROUP_PATTERN", groupPattern));

            final Pattern p = Pattern.compile (query);
            if (p.matcher (filename).matches ())
                return Optional.of (p);
        }
        return Optional.empty ();
    }


    /**
     * Try to read the MIDI note from the SMPL chunk and order the filenames by ascending MIDI
     * notes.
     *
     * @param zones The sample zones to process
     * @return The map with note and sample metadata pairs ordered by the note
     * @throws NoteNotDetectedException NOte could not be detected from filename
     */
    private static Map<Integer, List<ISampleZone>> createNoteMap (final List<ISampleZone> zones) throws NoteNotDetectedException
    {
        final Map<Integer, List<ISampleZone>> orderedNotes = new TreeMap<> ();
        for (final ISampleZone zone: zones)
        {
            final String filename = zone.getName ();
            final int midiNote = zone.getKeyRoot ();
            if (midiNote <= 0)
                throw new NoteNotDetectedException (filename);
            orderedNotes.computeIfAbsent (Integer.valueOf (midiNote), key -> new ArrayList<> ()).add (zone);
        }

        // All samples are mapped to the same note, seems the metadata does not contain meaningful
        // information...
        if (orderedNotes.size () == 1 && zones.size () > 1)
            throw new NoteNotDetectedException (Functions.getMessage ("IDS_WAV_ONLY_ONE_NOTE"));

        return orderedNotes;
    }


    /**
     * Parse the MIDI note from each filename and order the filenames by ascending MIDI notes.
     *
     * @param zones The sample zones to process
     * @return The map with note and sample metadata pairs ordered by the note
     * @throws NoteNotDetectedException NOte could not be detected from filename
     */
    private static Map<Integer, List<ISampleZone>> createNoteMapFromNames (final List<ISampleZone> zones) throws NoteNotDetectedException
    {
        if (zones.isEmpty ())
            return new TreeMap<> ();

        // Collect all potential key maps which match all sample names
        final Set<Integer> potentialKeyMaps = new TreeSet<> ();
        String filename = "";
        for (int keyMapIndex = 0; keyMapIndex < KEY_MAP.size (); keyMapIndex++)
        {
            final Map<String, Integer> keyMap = KEY_MAP.get (keyMapIndex);
            int midiNote = -1;
            for (final ISampleZone zone: zones)
            {
                filename = zone.getName ();
                midiNote = lookupMidiNote (keyMap, filename);
                if (midiNote < 0)
                    break;

            }

            // Did all sample names match?
            if (midiNote >= 0)
                potentialKeyMaps.add (Integer.valueOf (keyMapIndex));
        }

        if (potentialKeyMaps.isEmpty ())
            throw new NoteNotDetectedException (filename);

        // Use the matching key maps to parse the notes, the one with the most results is
        // the winner
        Map<Integer, List<ISampleZone>> result = null;
        for (final Integer keyMapIndex: potentialKeyMaps)
        {
            final Map<Integer, List<ISampleZone>> orderedByNote = new TreeMap<> ();
            final Map<String, Integer> keyMap = KEY_MAP.get (keyMapIndex.intValue ());
            for (final ISampleZone zone: zones)
            {
                final int midiNote = lookupMidiNote (keyMap, zone.getName ());
                orderedByNote.computeIfAbsent (Integer.valueOf (midiNote), key -> new ArrayList<> ()).add (zone);
            }

            result = decidePreferred (result, orderedByNote);
        }

        // Can never happen
        if (result == null)
            throw new NoteNotDetectedException (filename);

        // Finally, set the root keys
        for (final Map.Entry<Integer, List<ISampleZone>> e: result.entrySet ())
            for (final ISampleZone zone: e.getValue ())
                zone.setKeyRoot (e.getKey ().intValue ());

        return result;
    }


    private static Map<Integer, List<ISampleZone>> decidePreferred (final Map<Integer, List<ISampleZone>> current, final Map<Integer, List<ISampleZone>> alternative)
    {
        if (current == null || alternative.size () > current.size ())
            return alternative;

        if (alternative.size () < current.size ())
            return current;

        // First check for number of channels consistency
        if (!checkChannelConsistency (current) && checkChannelConsistency (alternative))
            return alternative;

        // Both are consistent, try note length texts
        return calcNoteTextLenghts (current) > calcNoteTextLenghts (alternative) ? current : alternative;
    }


    private static int calcNoteTextLenghts (final Map<Integer, List<ISampleZone>> noteZoneMap)
    {
        int length = 0;
        for (final Map.Entry<Integer, List<ISampleZone>> e: noteZoneMap.entrySet ())
            length += NoteParser.formatNoteSharps (e.getKey ().intValue ()).length ();
        return length;
    }


    private static boolean checkChannelConsistency (final Map<Integer, List<ISampleZone>> noteZoneMap)
    {
        int channels = -1;
        for (final Map.Entry<Integer, List<ISampleZone>> e: noteZoneMap.entrySet ())
            if (channels == -1)
                channels = e.getValue ().size ();
            else if (channels != e.getValue ().size ())
                return false;
        return true;
    }


    /**
     * First try to read the notes from the sample chunk. If that fails try different key detections
     * from the filename.
     *
     * @param zones The samples to process
     * @return The key map
     * @throws MultisampleException Could not detect a note map
     */
    private static Map<Integer, List<ISampleZone>> detectNotes (final List<ISampleZone> zones) throws MultisampleException
    {
        // First try to detect the notes from the sample chunk
        try
        {
            return createNoteMap (zones);
        }
        catch (final NoteNotDetectedException ex)
        {
            // Second try to parse the note from the filename in different variations
            try
            {
                return createNoteMapFromNames (zones);
            }
            catch (final NoteNotDetectedException ex2)
            {
                throw new MultisampleException (Functions.getMessage ("IDS_WAV_NO_MIDI_NOTE_DETECTED", ex2.getMessage ()));
            }
        }
    }


    /**
     * Create the key ranges and store them in the sample metadata.
     *
     * @param orderedByNote The sample zones ordered by their MIDI root note
     * @return The ordered sample zones
     */
    private static List<ISampleZone> createKeyMaps (final Map<Integer, ISampleZone> orderedByNote)
    {
        final List<ISampleZone> ordered = new ArrayList<> ();

        ISampleZone previous = null;
        for (final Map.Entry<Integer, ISampleZone> e: orderedByNote.entrySet ())
        {
            final ISampleZone current = e.getValue ();
            if (previous == null)
                current.setKeyLow (0);
            else
            {
                final int middle = (current.getKeyRoot () + previous.getKeyRoot ()) / 2;
                previous.setKeyHigh (middle);
                current.setKeyLow (middle + 1);
            }
            ordered.add (current);
            previous = current;
        }
        if (previous != null)
            previous.setKeyHigh (127);

        return ordered;
    }


    /**
     * Parse the MIDI note from (a part of) the filename.
     *
     * @param keyMap The key map to use
     * @param filename The filename
     * @return The MIDI note value (0-127) or -1 if not found
     */
    private static int lookupMidiNote (final Map<String, Integer> keyMap, final String filename)
    {
        final String fn = FileUtils.getNameWithoutType (new File (filename));
        final String noteArea = fn.toUpperCase (Locale.US);

        int pos = -1;
        String str = "";
        int note = 0;

        // Test if one of the notes is a part of the text
        for (final Map.Entry<String, Integer> e: keyMap.entrySet ())
        {
            final String n = e.getKey ().toUpperCase (Locale.US);
            final int p = noteArea.lastIndexOf (n);
            if (p == -1)
                continue;
            // There might be parts of the name which are not the note info, therefore run through
            // all of them and use the most right info but only if the result has at least the same
            // length
            final int keyLength = n.length ();
            final int strLength = str.length ();
            if (pos == -1 || keyLength > strLength || keyLength == strLength && p + keyLength > pos + strLength)
            {
                pos = p;
                str = n;
                note = e.getValue ().intValue ();
            }
        }

        return pos == -1 ? -1 : note;
    }


    /**
     * Create cross-fades between the sample ranges.
     *
     * @param noteMap The note map ordered by notes ascending
     * @param crossfadeNotes The number of notes to cross-fade
     */
    private static void createCrossfades (final Map<Integer, ISampleZone> noteMap, final int crossfadeNotes)
    {
        ISampleZone previousZone = null;
        for (final ISampleZone zone: noteMap.values ())
        {
            if (previousZone != null)
            {
                final int diff = zone.getKeyRoot () - previousZone.getKeyRoot () - 1;
                final int range = Math.min (diff, crossfadeNotes);
                final int crossfadeLow = range / 2;
                final int crossfadeHigh = crossfadeLow + range % 2;

                previousZone.setNoteCrossfadeHigh (range);
                zone.setNoteCrossfadeLow (range);

                zone.setKeyLow (AbstractCreator.limitToDefault (zone.getKeyLow (), 0) - crossfadeLow - 1);
                previousZone.setKeyHigh (AbstractCreator.limitToDefault (previousZone.getKeyHigh (), 127) + crossfadeHigh - 1);
            }
            previousZone = zone;
        }
    }


    /**
     * Find the common prefix among all given names.
     *
     * @param names The names
     * @return The common prefix
     */
    public static String findCommonPrefix (final List<String> names)
    {
        if (names.isEmpty ())
            return "";
        String prefix = names.get (0);
        for (int i = 1; i < names.size (); i++)
            while (!names.get (i).startsWith (prefix))
                prefix = prefix.substring (0, prefix.length () - 1);
        return prefix;
    }
}
