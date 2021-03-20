// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.util;

import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.MultisampleException;
import de.mossgrabers.sampleconverter.exception.NoteNotDetectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Detects MIDI notes and creates a key mapping for the multisample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KeyMapping
{
    /** The names of notes. */
    private static final String []            NOTE_NAMES_FLAT         =
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
    private static final String []            NOTE_NAMES_SHARP        =
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
    private static final String []            NOTE_NAMES_FLAT_GERMAN  =
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
    private static final String []            NOTE_NAMES_SHARP_GERMAN =
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

    // Order with longest strings first
    private static final Map<String, Integer> KEY_MAP                 = new TreeMap<> ( (o1, o2) -> {
                                                                          final int diff = o2.length () - o1.length ();
                                                                          if (diff != 0)
                                                                              return diff;
                                                                          return o2.compareTo (o1);
                                                                      });

    static
    {
        // Create note map
        for (int note = 0; note < 128; note++)
        {
            final int n = Math.abs (note % 12);
            final String octave = Integer.toString (note / 12 - 2);
            final Integer ni = Integer.valueOf (note);
            KEY_MAP.put (NOTE_NAMES_FLAT[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_FLAT_GERMAN[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP_GERMAN[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_FLAT[n] + "_" + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP[n] + "_" + octave, ni);
            KEY_MAP.put (NOTE_NAMES_FLAT_GERMAN[n] + "_" + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP_GERMAN[n] + "_" + octave, ni);
            KEY_MAP.put (String.format ("%03d", ni), ni);
            KEY_MAP.put (ni.toString (), ni);
        }
    }

    private final List<List<ISampleMetadata>> orderedSampleMetadata;
    private final Set<String>                 extractedNames = new HashSet<> ();
    private final String                      name;


    /**
     * Constructor.
     *
     * @param sampleInfos The sample infos from which to get the filenames and set the key ranges.
     * @param isAscending Sort ascending otherwise descending
     * @param crossfadeNotes The number of notes to crossfade ranges
     * @param crossfadeVelocities The number of velocity steps to crossfade ranges
     * @param layerPatterns The layer patterns
     * @param leftChannelPatterns The left channel detection patterns
     * @throws MultisampleException Found duplicated MIDI notes
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    public KeyMapping (final ISampleMetadata [] sampleInfos, final boolean isAscending, final int crossfadeNotes, final int crossfadeVelocities, final String [] layerPatterns, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        this.name = this.calculateCommonName (this.findShortestFilename ());

        this.orderedSampleMetadata = this.createLayers (sampleInfos, isAscending, crossfadeNotes, layerPatterns, leftChannelPatterns);

        // Calculate velocity crossfades
        final int range = 127 / this.orderedSampleMetadata.size ();
        int low = 0;
        int high = range;
        final int crossfadeVel = Math.min (range, crossfadeVelocities);
        for (final List<ISampleMetadata> layer: this.orderedSampleMetadata)
        {
            for (final ISampleMetadata info: layer)
            {
                info.setVelocityLow (low);
                info.setVelocityCrossfadeLow (0);
                final int velHigh = Math.min (high + crossfadeVel, 127);
                info.setVelocityHigh (velHigh);
                info.setVelocityCrossfadeHigh (velHigh == 127 ? 0 : Math.min (velHigh - low, crossfadeVel));
            }
            low = high + 1;
            high = Math.min (high + range, 127);
        }
    }


    /**
     * Get the detected name of the multisample.
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
     * @return The sample metadata list by layer
     */
    public List<List<ISampleMetadata>> getSampleMetadata ()
    {
        return this.orderedSampleMetadata;
    }


    /**
     * Detect and create a layer order.
     *
     * @param sampleInfos The sample infos from which to get the filenames and set the key ranges.
     * @param isAscending Sort ascending otherwise descending
     * @param crossfadeNotes The number of notes to crossfade ranges
     * @param layerPatterns The layer patterns
     * @param leftChannelPatterns The left channel detection patterns
     * @return The create layers
     * @throws MultisampleException Found duplicated MIDI notes
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    private List<List<ISampleMetadata>> createLayers (final ISampleMetadata [] sampleInfos, final boolean isAscending, final int crossfadeNotes, final String [] layerPatterns, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        final Map<Integer, List<ISampleMetadata>> sampleMetadata = new TreeMap<> ();
        final Map<Integer, List<ISampleMetadata>> layers = detectLayers (sampleInfos, layerPatterns);

        for (final Entry<Integer, List<ISampleMetadata>> entry: layers.entrySet ())
        {
            final List<ISampleMetadata> layer = entry.getValue ();
            final Map<Integer, List<ISampleMetadata>> noteMap = this.detectNotes (layer);
            final Map<Integer, ISampleMetadata> layerNoteMap = convertSplitStereo (noteMap, leftChannelPatterns);
            sampleMetadata.put (entry.getKey (), createKeyMaps (layerNoteMap));

            if (crossfadeNotes > 0 && crossfadeNotes < 128)
                createCrossfades (layerNoteMap, crossfadeNotes);
        }

        return orderLayers (sampleMetadata, isAscending);
    }


    /**
     * Get the sample metadata ordered by their root notes.
     *
     * @param sampleMetadata The layers to sort
     * @param isAscending Sort ascending otherwise descending
     * @return The sample metadata list by layer
     */
    private static List<List<ISampleMetadata>> orderLayers (final Map<Integer, List<ISampleMetadata>> sampleMetadata, final boolean isAscending)
    {
        final Collection<List<ISampleMetadata>> layers = sampleMetadata.values ();
        final List<List<ISampleMetadata>> reorderedSampleMetadata = new ArrayList<> (layers.size ());

        if (isAscending)
            reorderedSampleMetadata.addAll (layers);
        else
        {
            // Reorder descending
            layers.forEach (layer -> reorderedSampleMetadata.add (0, layer));
        }

        return reorderedSampleMetadata;
    }


    /**
     * Check how many samples are assigned to a note. If there is only one, return that result. If
     * there are two, try to combine the mono files into a stereo file. If there are more than 2
     * throw an exception.
     *
     * @param noteMap The assigned samples
     * @param leftChannelPatterns The left channel detection patterns
     * @return The result
     * @throws MultisampleException If there are more than 2 samples assigned to a note
     * @throws CombinationNotPossibleException Could not create stereo files
     */
    private static Map<Integer, ISampleMetadata> convertSplitStereo (final Map<Integer, List<ISampleMetadata>> noteMap, final String [] leftChannelPatterns) throws MultisampleException, CombinationNotPossibleException
    {
        // Check if each note has assigned 1 or 2 files all other cases give an exception
        int noOfAssignedSamples = -1;
        for (final Entry<Integer, List<ISampleMetadata>> entry: noteMap.entrySet ())
        {
            final List<ISampleMetadata> samples = entry.getValue ();

            final int size = samples.size ();
            if (noOfAssignedSamples == -1)
            {
                if (size > 2)
                    throw new MultisampleException ("Attempt to combine Mono splits into Stereo files but there are more than two files per note.", entry);
                if (size == 2)
                {
                    for (final ISampleMetadata sm: samples)
                    {
                        if (!sm.isMono ())
                            throw new MultisampleException ("Files to combine must be Mono.", entry);
                    }

                }
                noOfAssignedSamples = size;
            }
            else if (noOfAssignedSamples != size)
                throw new MultisampleException ("Attempt to combine Mono splits into Stereo files but not all notes have the same number of files.", entry);
        }

        if (noOfAssignedSamples == 1)
        {
            final Map<Integer, ISampleMetadata> result = new TreeMap<> ();
            for (final Entry<Integer, List<ISampleMetadata>> entry: noteMap.entrySet ())
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
    private static Map<Integer, ISampleMetadata> combineAllMonoToStereo (final Map<Integer, List<ISampleMetadata>> noteMap, final String [] leftChannelPatterns) throws CombinationNotPossibleException
    {
        final Map<Integer, ISampleMetadata> result = new TreeMap<> ();
        for (final Entry<Integer, List<ISampleMetadata>> entry: noteMap.entrySet ())
        {
            final List<ISampleMetadata> samples = entry.getValue ();
            result.put (entry.getKey (), combineMonoToStereo (samples.get (0), samples.get (1), leftChannelPatterns));
        }
        return result;
    }


    /**
     * Combines a left/right channel into a stereo file.
     *
     * @param first The first sample
     * @param second The second sample
     * @param leftChannelPatterns Detection patterns for the left channel, e.g. "_L"
     * @return First item is the left channel, second is the right channel
     * @throws CombinationNotPossibleException Could not detect the left channel
     */
    private static ISampleMetadata combineMonoToStereo (final ISampleMetadata first, final ISampleMetadata second, final String [] leftChannelPatterns) throws CombinationNotPossibleException
    {
        for (final String pattern: leftChannelPatterns)
        {
            if (first.getFilename ().contains (pattern))
                return combineLeftRight (first, second, pattern);
            if (second.getFilename ().contains (pattern))
                return combineLeftRight (second, first, pattern);
        }
        throw new CombinationNotPossibleException ("Could not detect left channel.");
    }


    /**
     * Combines the left and right channel into one wave file.
     *
     * @param leftChannel The left channel
     * @param rightChannel The right channel
     * @param pattern The matched pattern for the left channel
     * @return The combined sample
     * @throws CombinationNotPossibleException The files are not mono files or have different sample
     *             or loop lengths
     */
    private static ISampleMetadata combineLeftRight (final ISampleMetadata leftChannel, final ISampleMetadata rightChannel, final String pattern) throws CombinationNotPossibleException
    {
        leftChannel.combine (rightChannel);
        leftChannel.setCombinedName (leftChannel.getFilename ().replace (pattern, ""));
        return leftChannel;
    }


    /**
     * Detect velocity layers.
     *
     * @param sampleInfos Info about all available samples
     * @param layerPatterns The patterns to match for velocity layers
     * @return The detected layers
     * @throws MultisampleException There was a pattern detected but (or more) of the samples could
     *             not be matched
     */
    private static Map<Integer, List<ISampleMetadata>> detectLayers (final ISampleMetadata [] sampleInfos, final String [] layerPatterns) throws MultisampleException
    {
        final Map<Integer, List<ISampleMetadata>> layers = new TreeMap<> ();

        // If no layers are detected create one layer which contains all samples
        final Optional<Pattern> patternResult = getLayerPattern (sampleInfos, layerPatterns);
        if (patternResult.isEmpty ())
        {
            layers.put (Integer.valueOf (0), new ArrayList<> (Arrays.asList (sampleInfos)));
            return layers;
        }

        // Now match all sample names with the detected layer pattern
        final Pattern pattern = patternResult.get ();
        for (final ISampleMetadata si: sampleInfos)
        {
            final String filename = si.getFilename ();
            final Matcher matcher = pattern.matcher (filename);
            if (!matcher.matches ())
                throw new MultisampleException ("Could not detected velocity layer information in: " + filename);
            try
            {
                final String number = matcher.group ("value");
                final Integer id = Integer.valueOf (number);
                final String prefix = matcher.group ("prefix");
                final String postfix = matcher.group ("postfix");
                si.setNameWithoutLayer (prefix + postfix);
                layers.computeIfAbsent (id, key -> new ArrayList<> ()).add (si);
            }
            catch (final NumberFormatException ex)
            {
                throw new MultisampleException ("Could not detected velocity layer information in: " + filename);
            }
        }

        return layers;
    }


    /**
     * Check if one of the layer patterns matches.
     *
     * @param sampleInfos
     * @param layerPatterns
     * @return The matching pattern or null
     * @throws MultisampleException If a pattern could not be parsed
     */
    private static Optional<Pattern> getLayerPattern (final ISampleMetadata [] sampleInfos, final String [] layerPatterns) throws MultisampleException
    {
        if (layerPatterns.length == 0)
            return Optional.empty ();
        final String filename = sampleInfos[0].getFilename ();
        for (final String layerPattern: layerPatterns)
        {
            final String [] parts = layerPattern.split ("\\*");
            String query;

            if (parts.length == 1)
            {
                // * is at the end or the beginning
                if (layerPattern.endsWith ("*"))
                    query = "(?<prefix>.*)" + Pattern.quote (parts[0]) + "(?<value>\\d+)(?<postfix>.*)";
                else
                    query = "(?<prefix>.*)(?<value>\\d+)" + Pattern.quote (parts[0]) + "(?<postfix>.*)";
            }
            else if (parts.length == 2)
            {
                // * is in the middle
                query = "(?<prefix>.*)" + Pattern.quote (parts[0]) + "(?<value>\\d+)" + Pattern.quote (parts[1]) + "(?<postfix>.*)";
            }
            else
                throw new MultisampleException ("Could not parse layer pattern: " + layerPattern);

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
     * @param samples The samples to process
     * @return The map with note and sample metadata pairs ordered by the note
     * @throws NoteNotDetectedException NOte could not be detected from filename
     */
    private Map<Integer, List<ISampleMetadata>> createNoteMap (final List<ISampleMetadata> samples) throws NoteNotDetectedException
    {
        final Map<Integer, List<ISampleMetadata>> orderedNotes = new TreeMap<> ();
        for (final ISampleMetadata sample: samples)
        {
            final Optional<String> filename = sample.getUpdatedFilename ();
            if (filename.isEmpty ())
                continue;
            this.extractedNames.add (filename.get ());
            final int midiNote = sample.getKeyRoot ();
            if (midiNote <= 0)
                throw new NoteNotDetectedException (filename.get ());
            orderedNotes.computeIfAbsent (Integer.valueOf (midiNote), key -> new ArrayList<> ()).add (sample);
        }

        // All samples are mapped to the same note, seems the metadata does not contain meaningful
        // information...
        if (orderedNotes.size () == 1 && samples.size () > 1)
            throw new NoteNotDetectedException ("All files have the same midi note");

        return orderedNotes;
    }


    /**
     * Parse the MIDI note from each filename and order the filenames by ascending MIDI notes.
     *
     * @param samples The samples to process
     * @param keyMap The key map to use
     * @return The map with note and sample metadata pairs ordered by the note
     * @throws NoteNotDetectedException NOte could not be detected from filename
     */
    private Map<Integer, List<ISampleMetadata>> createNoteMap (final List<ISampleMetadata> samples, final Map<String, Integer> keyMap) throws NoteNotDetectedException
    {
        this.extractedNames.clear ();

        final Map<Integer, List<ISampleMetadata>> orderedNotes = new TreeMap<> ();
        for (final ISampleMetadata sample: samples)
        {
            final String filename = sample.getNameWithoutLayer ();
            final int midiNote = this.lookupMidiNote (keyMap, filename);
            if (midiNote == -1)
                throw new NoteNotDetectedException (filename);
            sample.setKeyRoot (midiNote);
            orderedNotes.computeIfAbsent (Integer.valueOf (midiNote), key -> new ArrayList<> ()).add (sample);
        }
        return orderedNotes;
    }


    /**
     * First try to read the notes from the sample chunk. If that fails try different key detections
     * from the filename.
     *
     * @param samples The samples to process
     * @return The key map
     * @throws MultisampleException Could not detect a note map
     */
    private Map<Integer, List<ISampleMetadata>> detectNotes (final List<ISampleMetadata> samples) throws MultisampleException
    {
        // First try to detect the notes from the sample chunk
        try
        {
            return this.createNoteMap (samples);
        }
        catch (final NoteNotDetectedException ex)
        {
            // Second try to parse the note from the filename in different variations
            try
            {
                return this.createNoteMap (samples, KEY_MAP);
            }
            catch (final NoteNotDetectedException ex2)
            {
                throw new MultisampleException ("Could not detect MIDI note in file name: " + ex2.getMessage ());
            }
        }

    }


    /**
     * Create the key ranges and store them in the sample metadata.
     *
     * @param orderedNotes The sample metadata ordered by their MIDI root note
     * @return The ordered samples
     */
    private static List<ISampleMetadata> createKeyMaps (final Map<Integer, ISampleMetadata> orderedNotes)
    {
        final List<ISampleMetadata> ordered = new ArrayList<> ();

        ISampleMetadata previous = null;
        for (final Map.Entry<Integer, ISampleMetadata> e: orderedNotes.entrySet ())
        {
            final ISampleMetadata current = e.getValue ();
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
    private int lookupMidiNote (final Map<String, Integer> keyMap, final String filename)
    {
        // Remove .wav at the end
        String fn = filename.substring (0, filename.length () - 4).trim ();
        final String noteArea = fn.toUpperCase (Locale.US);

        int pos = -1;
        String str = "";
        Integer note = Integer.valueOf (0);

        // Test if one of the notes is a part of the text
        for (final Map.Entry<String, Integer> e: keyMap.entrySet ())
        {
            final String n = e.getKey ();
            final int p = noteArea.lastIndexOf (n);
            if (p == -1)
                continue;
            // There might be parts of the name which is not the note info, therefore run through
            // all of them and use the most right info but only if the result has at least the same
            // length
            final int keyLength = n.length ();
            final int strLength = str.length ();
            if (p + keyLength > pos + strLength && keyLength >= strLength)
            {
                pos = p;
                str = n;
                note = e.getValue ();
            }
        }

        if (pos == -1)
            return -1;
        fn = fn.substring (0, pos) + fn.substring (pos + str.length (), fn.length ());
        this.extractedNames.add (fn.trim ());
        return note.intValue ();
    }


    /**
     * Create crossfades between the sample ranges.
     *
     * @param noteMap The note map ordered by notes ascending
     * @param crossfadeNotes The number of notes to crossfade
     */
    private static void createCrossfades (final Map<Integer, ISampleMetadata> noteMap, final int crossfadeNotes)
    {
        ISampleMetadata previousSampleMetadata = null;
        for (final ISampleMetadata sampleMetadata: noteMap.values ())
        {
            if (previousSampleMetadata != null)
            {
                final int diff = sampleMetadata.getKeyRoot () - previousSampleMetadata.getKeyRoot () - 1;
                final int range = Math.min (diff, crossfadeNotes);
                final int crossfadeLow = range / 2;
                final int crossfadeHigh = crossfadeLow + range % 2;

                previousSampleMetadata.setNoteCrossfadeHigh (range);
                sampleMetadata.setNoteCrossfadeLow (range);

                sampleMetadata.setKeyLow (sampleMetadata.getKeyLow () - crossfadeLow - 1);
                previousSampleMetadata.setKeyHigh (previousSampleMetadata.getKeyHigh () + crossfadeHigh - 1);
            }
            previousSampleMetadata = sampleMetadata;
        }
    }


    /**
     * Get the length of the shortest extracted name.
     *
     * @return The number of characters
     */
    private int findShortestFilename ()
    {
        int minimum = -1;
        for (final String en: this.extractedNames)
        {
            final int l = en.length ();
            if (l < minimum || minimum == -1)
                minimum = l;
        }
        return minimum;
    }


    /**
     * Calculate the common characters of all extracted names starting from the beginning.
     *
     * @param length The maximum number of characters to look at
     * @return The identical characters among all names
     */
    private String calculateCommonName (final int length)
    {
        if (this.extractedNames.isEmpty ())
            return "";

        final List<String> names = new ArrayList<> (this.extractedNames);
        final String firstFilename = names.get (0);
        for (int pos = 0; pos < length; pos++)
        {
            final char c = firstFilename.charAt (pos);
            for (int i = 1; i < names.size (); i++)
            {
                if (c != names.get (i).charAt (pos))
                    return firstFilename.substring (0, pos).trim ();
            }
        }
        return firstFilename.trim ();
    }
}
