// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.directwave;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Parses the mapping information which DirectWave stores in the names of sample files. There are
 * two conventions:
 * <ul>
 * <li>The names written when sampling a plugin ('Create DirectWave instrument' in FL Studio) or
 * when saving a program non-monolithically: <i>Name_C5_127.wav</i> (note and velocity) with an
 * optional round-robin cycle appended: <i>Name 4xCycles_C5_100_2.wav</i>. Key and velocity ranges
 * are not part of the name and are reconstructed by splitting the distance to the neighboring
 * sampled notes/velocities in the middle.
 * <li>The Automap tokens documented in the DirectWave manual: everything after the last underscore
 * is a list of tokens separated by '+'. A note name is the root key, 'K' starts a key range, 'V' a
 * velocity range and 'T' trigger group settings, e.g.
 * <i>samplename_C4+KA3-F#4+V64-95+TG1+TY3+TF100+TO15.wav</i>.
 * </ul>
 * Note names use the Image-Line octave convention: MIDI note = 12 * octave + semitone, therefore C5
 * is the middle C (60) and there are no negative octaves.
 *
 * @author Jürgen Moßgraber
 */
public class DirectWaveFileNameParser
{
    /** The parsed information of one sample file. */
    public static class ParsedZone
    {
        /** The sample file. */
        public File   file;
        /** The name of the zone. */
        public String name;
        /** The root key. */
        public int    rootKey         = -1;
        /** The low key of the key range or -1 if it needs to be calculated. */
        public int    keyLow          = -1;
        /** The high key of the key range or -1 if it needs to be calculated. */
        public int    keyHigh         = -1;
        /** The low velocity or -1 if it needs to be calculated. */
        public int    velocityLow     = -1;
        /** The high velocity or -1 if it needs to be calculated. */
        public int    velocityHigh    = -1;
        /** The velocity at which the sample was recorded or -1 if not present. */
        public int    sampledVelocity = -1;
        /** The 1-based round-robin cycle or -1 if not present. */
        public int    cycle           = -1;
        /** The trigger group (0-99) or -1 if not present. */
        public int    triggerGroup    = -1;
        /** The trigger type (0: normal, 1: cycle, 2: random, 3: avoid previous) or -1. */
        public int    triggerType     = -1;
    }


    private static final Pattern   NOTE_PATTERN     = Pattern.compile ("([A-Ga-g])([#b]?)(10|\\d)");
    private static final Pattern   VELOCITY_PATTERN = Pattern.compile ("V(\\d+)-(\\d+)");
    private static final Pattern   KEY_PATTERN      = Pattern.compile ("K(.+)-(.+)");
    private static final Pattern   TRIGGER_PATTERN  = Pattern.compile ("T([GYFO])(\\d+)");
    private static final int []    SEMITONES        =
    {
        9,
        11,
        0,
        2,
        4,
        5,
        7
    };
    private static final String [] NOTE_NAMES       =
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


    /**
     * Parse a note name in the Image-Line convention into a MIDI note.
     *
     * @param text The text to parse, e.g. 'C#5'
     * @return The MIDI note (C5 = 60) or -1 if the text is not a note name
     */
    public static int parseNote (final String text)
    {
        final Matcher matcher = NOTE_PATTERN.matcher (text);
        if (!matcher.matches ())
            return -1;
        int semitone = SEMITONES[Character.toUpperCase (matcher.group (1).charAt (0)) - 'A'];
        final String accidental = matcher.group (2);
        if ("#".equals (accidental))
            semitone++;
        else if ("b".equals (accidental))
            semitone--;
        final int note = 12 * Integer.parseInt (matcher.group (3)) + (semitone + 12) % 12;
        return note > 127 ? -1 : note;
    }


    /**
     * Format a MIDI note as a note name in the Image-Line convention.
     *
     * @param note The MIDI note
     * @return The note name, e.g. 'C#5' for 61
     */
    public static String formatNote (final int note)
    {
        return NOTE_NAMES[note % 12] + Integer.toString (note / 12);
    }


    /**
     * Parse the mapping information from the name of a sample file.
     *
     * @param sampleFile The sample file
     * @return The parsed information or null if the name does not follow one of the conventions
     */
    public static ParsedZone parseFileName (final File sampleFile)
    {
        final String fileName = sampleFile.getName ();
        final int dotPos = fileName.lastIndexOf ('.');
        final String stem = dotPos < 0 ? fileName : fileName.substring (0, dotPos);

        ParsedZone zone = parseSamplingName (stem);
        if (zone == null)
            zone = parseAutomapName (stem);
        if (zone != null)
        {
            zone.file = sampleFile;
            zone.name = stem;
        }
        return zone;
    }


    /**
     * Try to parse a sampling convention name: <i>..._note_velocity</i> optionally followed by
     * <i>_cycle</i> if the part before the note ends with 'xCycles'.
     *
     * @param stem The file name without the extension
     * @return The parsed zone or null
     */
    private static ParsedZone parseSamplingName (final String stem)
    {
        final String [] parts = stem.split ("_");
        if (parts.length < 3)
            return null;

        // Check for the round-robin variant: ..._note_velocity_cycle
        int noteIndex = parts.length - 2;
        int cycle = -1;
        if (parts.length > 3 && parseNote (parts[parts.length - 3]) >= 0 && isNumber (parts[parts.length - 2]) && isNumber (parts[parts.length - 1]) && parts[parts.length - 4].endsWith ("xCycles"))
        {
            noteIndex = parts.length - 3;
            cycle = Integer.parseInt (parts[parts.length - 1]);
        }

        final int note = parseNote (parts[noteIndex]);
        if (note < 0 || !isNumber (parts[noteIndex + 1]))
            return null;
        final int velocity = Integer.parseInt (parts[noteIndex + 1]);
        if (velocity > 127)
            return null;

        final ParsedZone zone = new ParsedZone ();
        zone.rootKey = note;
        zone.sampledVelocity = velocity;
        zone.cycle = cycle;
        return zone;
    }


    /**
     * Try to parse an Automap token name: everything after the last underscore (or, if there is
     * none, the last dash) is a list of tokens separated by '+'.
     *
     * @param stem The file name without the extension
     * @return The parsed zone or null
     */
    private static ParsedZone parseAutomapName (final String stem)
    {
        int pos = stem.lastIndexOf ('_');
        if (pos < 0)
            pos = stem.lastIndexOf ('-');
        if (pos < 0 || pos == stem.length () - 1)
            return null;

        final ParsedZone zone = new ParsedZone ();
        for (final String token: stem.substring (pos + 1).split ("\\+"))
            if (!parseAutomapToken (zone, token))
                return null;
        return zone.rootKey < 0 && zone.keyLow < 0 ? null : zone;
    }


    private static boolean parseAutomapToken (final ParsedZone zone, final String token)
    {
        final int note = parseNote (token);
        if (note >= 0)
        {
            zone.rootKey = note;
            return true;
        }

        Matcher matcher = KEY_PATTERN.matcher (token);
        if (matcher.matches ())
        {
            final int low = parseNote (matcher.group (1));
            final int high = parseNote (matcher.group (2));
            if (low < 0 || high < 0)
                return false;
            zone.keyLow = Math.min (low, high);
            zone.keyHigh = Math.max (low, high);
            return true;
        }

        matcher = VELOCITY_PATTERN.matcher (token);
        if (matcher.matches ())
        {
            zone.velocityLow = Math.min (127, Integer.parseInt (matcher.group (1)));
            zone.velocityHigh = Math.min (127, Integer.parseInt (matcher.group (2)));
            return true;
        }

        matcher = TRIGGER_PATTERN.matcher (token);
        if (matcher.matches ())
        {
            final int value = Integer.parseInt (matcher.group (2));
            switch (matcher.group (1).charAt (0))
            {
                case 'G':
                    zone.triggerGroup = value;
                    break;
                case 'Y':
                    zone.triggerType = value;
                    break;
                default:
                    // Trigger frequency and overlap have no counterpart in the model
                    break;
            }
            return true;
        }

        return false;
    }


    /**
     * Calculate the missing key and velocity ranges of the given zones. The key range of a zone
     * extends from the middle between its root key and the root key of the previous zone up to the
     * middle towards the next one; the first/last zones extend to the border. Velocity works the
     * same among the sampled velocities present for one root key.
     *
     * @param zones The zones to update
     */
    public static void calculateMissingRanges (final List<ParsedZone> zones)
    {
        // Calculate the key ranges from the distinct root keys
        final TreeSet<Integer> rootsSet = new TreeSet<> ();
        for (final ParsedZone zone: zones)
            if (zone.keyLow < 0 && zone.rootKey >= 0)
                rootsSet.add (Integer.valueOf (zone.rootKey));
        final List<Integer> roots = new ArrayList<> (rootsSet);
        final Map<Integer, int []> keyRanges = calculateRanges (roots, 0);
        for (final ParsedZone zone: zones)
            if (zone.keyLow < 0 && zone.rootKey >= 0)
            {
                final int [] range = keyRanges.get (Integer.valueOf (zone.rootKey));
                zone.keyLow = range[0];
                zone.keyHigh = range[1];
            }

        // Calculate the velocity ranges from the distinct sampled velocities of each root key
        final Map<Integer, TreeSet<Integer>> velocitiesByRoot = new HashMap<> ();
        for (final ParsedZone zone: zones)
            if (zone.velocityLow < 0 && zone.sampledVelocity >= 0)
                velocitiesByRoot.computeIfAbsent (Integer.valueOf (zone.rootKey), _ -> new TreeSet<> ()).add (Integer.valueOf (zone.sampledVelocity));
        for (final ParsedZone zone: zones)
            if (zone.velocityLow < 0)
            {
                if (zone.sampledVelocity < 0)
                {
                    zone.velocityLow = 1;
                    zone.velocityHigh = 127;
                    continue;
                }
                final Map<Integer, int []> velocityRanges = calculateRanges (new ArrayList<> (velocitiesByRoot.get (Integer.valueOf (zone.rootKey))), 1);
                final int [] range = velocityRanges.get (Integer.valueOf (zone.sampledVelocity));
                zone.velocityLow = range[0];
                zone.velocityHigh = range[1];
            }
    }


    /**
     * Split the space between the given sorted center values in the middle. The first range starts
     * at the minimum, the last one ends at 127.
     *
     * @param centers The sorted distinct center values
     * @param minimum The lowest value of the first range
     * @return A map from center value to [low, high]
     */
    private static Map<Integer, int []> calculateRanges (final List<Integer> centers, final int minimum)
    {
        final Map<Integer, int []> ranges = new HashMap<> ();
        for (int i = 0; i < centers.size (); i++)
        {
            final int center = centers.get (i).intValue ();
            final int low = i == 0 ? minimum : ranges.get (centers.get (i - 1))[1] + 1;
            final int high = i == centers.size () - 1 ? 127 : center + (centers.get (i + 1).intValue () - center) / 2;
            ranges.put (Integer.valueOf (center), new int []
            {
                low,
                high
            });
        }
        return ranges;
    }


    private static boolean isNumber (final String text)
    {
        if (text.isEmpty ())
            return false;
        for (int i = 0; i < text.length (); i++)
            if (!Character.isDigit (text.charAt (i)))
                return false;
        return true;
    }


    /**
     * Private constructor for utility class.
     */
    private DirectWaveFileNameParser ()
    {
        // Intentionally empty
    }
}
