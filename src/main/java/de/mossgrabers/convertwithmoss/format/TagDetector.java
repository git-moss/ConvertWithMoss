// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * Detects category tags.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public class TagDetector
{
    private static final Map<String, String []> CATEGORIES                    = new HashMap<> ();
    private static final Map<String, String>    CATEGORY_LOOKUP               = new TreeMap<> (new StringLengthComparator ());
    private static final Map<String, String>    CATEGORY_PREFIX_LOOKUP        = new HashMap<> ();
    private static final Map<String, String>    KEYWORD_LOOKUP                = new TreeMap<> (new StringLengthComparator ());
    private static final List<String>           WORD_DICT                     = new ArrayList<> ();

    public static final String                  CATEGORY_UNKNOWN              = "Unknown";
    public static final String                  CATEGORY_ACOUSTIC_DRUM        = "Acoustic Drum";
    public static final String                  CATEGORY_BASS                 = "Bass";
    public static final String                  CATEGORY_BELL                 = "Bell";
    public static final String                  CATEGORY_BRASS                = "Brass";
    public static final String                  CATEGORY_CHIP                 = "Chip";
    public static final String                  CATEGORY_VOCAL                = "Vocal";
    public static final String                  CATEGORY_CHROMATIC_PERCUSSION = "Chromatic Percussion";
    public static final String                  CATEGORY_CLAP                 = "Clap";
    public static final String                  CATEGORY_DESTRUCTION          = "Destruction";
    public static final String                  CATEGORY_DRONE                = "Drone";
    public static final String                  CATEGORY_DRUM                 = "Drum";
    public static final String                  CATEGORY_ENSEMBLE             = "Ensemble";
    public static final String                  CATEGORY_FX                   = "FX";
    public static final String                  CATEGORY_GUITAR               = "Guitar";
    public static final String                  CATEGORY_HI_HAT               = "Hi-Hat";
    public static final String                  CATEGORY_KEYBOARD             = "Keyboard";
    public static final String                  CATEGORY_KICK                 = "Kick";
    public static final String                  CATEGORY_LEAD                 = "Lead";
    public static final String                  CATEGORY_MONOSYNTH            = "Monosynth";
    public static final String                  CATEGORY_ORCHESTRAL           = "Orchestral";
    public static final String                  CATEGORY_ORGAN                = "Organ";
    public static final String                  CATEGORY_PAD                  = "Pad";
    public static final String                  CATEGORY_PERCUSSION           = "Percussion";
    public static final String                  CATEGORY_PIANO                = "Piano";
    public static final String                  CATEGORY_PIPE                 = "Pipe";
    public static final String                  CATEGORY_PLUCK                = "Pluck";
    public static final String                  CATEGORY_SNARE                = "Snare";
    public static final String                  CATEGORY_STRINGS              = "Strings";
    public static final String                  CATEGORY_SYNTH                = "Synth";
    public static final String                  CATEGORY_WINDS                = "Winds";
    public static final String                  CATEGORY_LOOPS                = "Loops";
    public static final String                  CATEGORY_WORLD                = "World";

    private static final String []              KEYWORDS                      =
    {
        "acoustic",
        "aggressive",
        "analog",
        "arp",
        "arpeggiated",
        "atmo",
        "bright",
        "channel",
        "chord",
        "clean",
        "constant",
        "crossmod",
        "dark",
        "deep",
        "deephouse",
        "defined",
        "detuned",
        "digital",
        "drumandbass",
        "dry",
        "duo",
        "dynamic",
        "ebm",
        "edm",
        "effects",
        "electric",
        "evolving",
        "expressive",
        "fast",
        "fx",
        "glide",
        "hard",
        "harmonic",
        "host_synced",
        "house",
        "inharmonic",
        "jazz",
        "layered",
        "linnstrument",
        "major",
        "metallic",
        "mod",
        "modern",
        "modulated",
        "mono",
        "mpe",
        "narrow",
        "natural",
        "neurofunk",
        "noisy",
        "organic",
        "osc_sync",
        "percussive",
        "phat",
        "pop",
        "poly",
        "rock",
        "rich",
        "seq",
        "sequenced",
        "slow",
        "slow_release",
        "soft",
        "soft_attack",
        "spacey",
        "sparse",
        "static",
        "sustained",
        "synthetic",
        "techstep",
        "trance",
        "unison",
        "vintage",
        "wet",
        "wide"
    };

    static
    {
        CATEGORIES.put (CATEGORY_ACOUSTIC_DRUM, new String []
        {
            CATEGORY_ACOUSTIC_DRUM
        });
        CATEGORIES.put (CATEGORY_BASS, new String []
        {
            "Acoustic Bs",
            "Bass Guitar",
            "Picked Bs",
            CATEGORY_BASS,
            "Fretless",
            "Reese",
            "Slap",
            "Fingered"
        });
        CATEGORIES.put (CATEGORY_BELL, new String []
        {
            CATEGORY_BELL,
            "Musical Box",
            "Music Box",
            "Fantasia",
            "Tubular",
            "Carillon"
        });
        CATEGORIES.put (CATEGORY_BRASS, new String []
        {
            CATEGORY_BRASS,
            "Trombone",
            "Trumpet",
            "Cornet",
            "Bugle",
            "Horn",
            "Tuba"
        });
        CATEGORIES.put (CATEGORY_CHIP, new String []
        {
            CATEGORY_CHIP,
            "Computer",
            "CPU",
            "Wave"
        });
        CATEGORIES.put (CATEGORY_VOCAL, new String []
        {
            CATEGORY_VOCAL,
            "Microphone",
            "Gregorian",
            "Whisper",
            "Vocode",
            "Choral",
            "Speech",
            "Speack",
            "Choir",
            "Voice",
            "Shout",
            "Sing",
            "Vox",
            "Ahh"
        });
        CATEGORIES.put (CATEGORY_CHROMATIC_PERCUSSION, new String []
        {
            CATEGORY_CHROMATIC_PERCUSSION,
            "Marimba",
            "Xylophone",
            "Vibraphone",
            "Glockenspiel",
            "Celesta",
            "Mallet",
            "Kalimba"
        });
        CATEGORIES.put (CATEGORY_CLAP, new String []
        {
            CATEGORY_CLAP
        });
        CATEGORIES.put (CATEGORY_DESTRUCTION, new String []
        {
            CATEGORY_DESTRUCTION
        });
        CATEGORIES.put (CATEGORY_DRONE, new String []
        {
            CATEGORY_DRONE
        });
        CATEGORIES.put (CATEGORY_DRUM, new String []
        {
            CATEGORY_DRUM,
            "Reverse Cym",
            "Drum Kit",
            "Drum-Set",
            "Drumset",
            "Cymbal",
            "Gong",
            "Ride",
            "Kit",
            "Tom",
            "707",
            "808",
            "909"
        });
        CATEGORIES.put (CATEGORY_ENSEMBLE, new String []
        {
            CATEGORY_ENSEMBLE
        });
        CATEGORIES.put (CATEGORY_FX, new String []
        {
            CATEGORY_FX,
            "Helicopter",
            "Telephone",
            "Experiment",
            "Explosion",
            "Seashore",
            "Applause",
            "Surround",
            "Starship",
            "Scratch",
            "Stadium",
            "Cricket",
            "Thunder",
            "Bubble",
            "Stream",
            "Scream",
            "Sci-Fi",
            "SciFi",
            "Laugh",
            "Punch",
            "Heart",
            "Alarm",
            "Metal",
            "Noise",
            "Horse",
            "Siren",
            "Wind",
            "Rain",
            "Bird",
            "Door",
            "Gun",
            "Car",
            "Dog",
            "Jet"
        });
        CATEGORIES.put (CATEGORY_GUITAR, new String []
        {
            "Electric Guitar",
            "Mandolin",
            CATEGORY_GUITAR,
            "Ukulele",
            "Hawaiian",
            "Nylon",
            "Rajao",
            "Banjo",
            "Chorus Gt",
            "Clean Gt",
            "Jazz Gt",
            "Muted Gt",
            "Funk Gt",
            "Overdrive",
            "Distortion",
            "Feedback",
            "Charango",
            "GTR"
        });
        CATEGORIES.put (CATEGORY_HI_HAT, new String []
        {
            CATEGORY_HI_HAT,
            "HiHat",
            "HH",
            "Hats"
        });
        CATEGORIES.put (CATEGORY_KEYBOARD, new String []
        {
            CATEGORY_KEYBOARD,
            "Clavinet",
            "Harpsi",
            "Keys",
            "KEY"
        });
        CATEGORIES.put (CATEGORY_KICK, new String []
        {
            CATEGORY_KICK,
            "bass-drum",
            "bassdrum",
            "BD"
        });
        CATEGORIES.put (CATEGORY_LEAD, new String []
        {
            CATEGORY_LEAD,
            "Solo",
            "Unisono",
            "Sync",
            "Supersaw"
        });
        CATEGORIES.put (CATEGORY_MONOSYNTH, new String []
        {
            CATEGORY_MONOSYNTH
        });
        CATEGORIES.put (CATEGORY_ORCHESTRAL, new String []
        {
            CATEGORY_ORCHESTRAL,
            "Soundtrack",
            "Orchestra",
            "Score",
            "Cinema",
        });
        CATEGORIES.put (CATEGORY_ORGAN, new String []
        {
            "Pipe Organ",
            CATEGORY_ORGAN,
            "Tonewheel",
            "Accordion",
            "Bandoneon",
            "Hammond",
            "Farfisa",
            "Gospel",
            "Church",
            "B3",
            "C3"
        });
        CATEGORIES.put (CATEGORY_PAD, new String []
        {
            CATEGORY_PAD,
            "Mellotron",
            "Ambient",
            "Atmo"
        });
        CATEGORIES.put (CATEGORY_PERCUSSION, new String []
        {
            CATEGORY_PERCUSSION,
            "Tambourine",
            "Woodblock",
            "Triangle",
            "Castanets",
            "Cowbell",
            "Timbale",
            "Timpani",
            "Maracas",
            "Djembe",
            "Shaker",
            "Agogo",
            "Bongo",
            "Chimes",
            "Conga",
            "Cuica",
            "Tabla",
            "Taiko",
            "Perc"
        });
        CATEGORIES.put (CATEGORY_PIANO, new String []
        {
            CATEGORY_PIANO,
            "Grand",
            "Electric Piano",
            "Honky-Tonk",
            "E-Piano",
            "Upright",
            "Digital Piano",
            "Klavier",
            "Harpsichord",
            "Spinet",
            "Clav",
            "Suitcase",
            "SCase",
            "Whirly",
            "Wurlitz",
            "Mark I",
            "Rhodes",
            "EP"
        });
        CATEGORIES.put (CATEGORY_PIPE, new String []
        {
            "Didgeridoo",
            "Shakuhachi",
            "Shakuhashi",
            "Recorder",
            "Whistle",
            "Piccolo",
            CATEGORY_PIPE,
            "Flute"
        });
        CATEGORIES.put (CATEGORY_PLUCK, new String []
        {
            CATEGORY_PLUCK,
            "Balalaika",
            "Hackbrett",
            "Dulcimer",
            "Mandolin",
            "Shamisen",
            "Zither",
            "Santur",
            "Sitar",
            "Harp",
            "Koto",
            "Lyre",
            "Erhu",
            "Oud"
        });
        CATEGORIES.put (CATEGORY_SNARE, new String []
        {
            CATEGORY_SNARE
        });
        CATEGORIES.put (CATEGORY_STRINGS, new String []
        {
            "String",
            "Viola",
            "Violin",
            "Cello",
            "Double Bass",
            "Tremolo St",
            "Pizzicato",
            "Arco",
            "Str.",
            "Fiddle",
            "Bowed",
            "Score",
            "Solina"
        });
        CATEGORIES.put (CATEGORY_SYNTH, new String []
        {
            "Electronic-Music",
            "Sequence",
            "Prophet",
            "Trance",
            "Analog",
            "Sweep",
            "Swell",
            "Virus",
            "Juno",
            "Mini",
            "Moog",
            "2600",
            "Syn",
            "SAW",
            "OB6",
            "DJ"
        });
        CATEGORIES.put (CATEGORY_WINDS, new String []
        {
            "Klarinette",
            "Harmonica",
            "Clarinet",
            "Bag Pipe",
            "Melodica",
            "Woodwind",
            "Bassoon",
            "Musette",
            "Ocarina",
            "Bottle",
            "Oboe",
            "Sax"
        });
        CATEGORIES.put (CATEGORY_LOOPS, new String []
        {
            "Loop",
            "Lps",
            "Record",
            "Player",
            "Speaker"
        });
        CATEGORIES.put (CATEGORY_WORLD, new String []
        {
            "Asian"
        });

        // Create inverse map and order by longest names first, to find the most relevant categories
        for (final Map.Entry<String, String []> e: CATEGORIES.entrySet ())
        {
            final String category = e.getKey ();
            for (final String v: e.getValue ())
                CATEGORY_LOOKUP.put (v.toUpperCase (Locale.US), category);
        }

        // A category tag at the very start of a name declares the intended category explicitly
        // (a wide-spread naming convention in commercial libraries, e.g. 'PAD Solina' or
        // 'BASS Growler'). All category keywords can be used as such a prefix. Additionally,
        // the following common abbreviations are recognized - only as a name prefix, since they
        // are too ambiguous for the contains-matching of the general detection (e.g. 'GRAN' is
        // contained in 'Grand' and 'ORG' in 'Forge').
        CATEGORY_PREFIX_LOOKUP.putAll (CATEGORY_LOOKUP);
        CATEGORY_PREFIX_LOOKUP.put ("BRAS", CATEGORY_BRASS);
        CATEGORY_PREFIX_LOOKUP.put ("DRM", CATEGORY_DRUM);
        CATEGORY_PREFIX_LOOKUP.put ("FLUT", CATEGORY_PIPE);
        CATEGORY_PREFIX_LOOKUP.put ("GRAN", CATEGORY_SYNTH);
        CATEGORY_PREFIX_LOOKUP.put ("ORG", CATEGORY_ORGAN);
        CATEGORY_PREFIX_LOOKUP.put ("PERC", CATEGORY_PERCUSSION);
        CATEGORY_PREFIX_LOOKUP.put ("PHYS", CATEGORY_SYNTH);
        CATEGORY_PREFIX_LOOKUP.put ("PLUK", CATEGORY_PLUCK);
        CATEGORY_PREFIX_LOOKUP.put ("POLY", CATEGORY_SYNTH);
        CATEGORY_PREFIX_LOOKUP.put ("REES", CATEGORY_BASS);
        CATEGORY_PREFIX_LOOKUP.put ("STRG", CATEGORY_STRINGS);
        CATEGORY_PREFIX_LOOKUP.put ("SWEP", CATEGORY_SYNTH);
        CATEGORY_PREFIX_LOOKUP.put ("VOC", CATEGORY_VOCAL);

        Arrays.asList (KEYWORDS).forEach (value -> KEYWORD_LOOKUP.put (value.toUpperCase (Locale.US), value));

        // Normalize and sort words by descending length for camel case method
        final List<String> words = new ArrayList<> ();
        words.addAll (CATEGORY_LOOKUP.values ());
        Collections.addAll (words, KEYWORDS);
        for (final String w: words)
            WORD_DICT.add (w.toUpperCase (Locale.ROOT));
        WORD_DICT.sort ((a, b) -> Integer.compare (b.length (), a.length ()));
    }


    /**
     * Constructor.
     */
    private TagDetector ()
    {
        // Intentionally empty
    }


    /**
     * Checks if the given label is one of the CATEGORY_* constants. If not detectCategory is called
     * on the label to find one.
     *
     * @param categoryLabel The label for which to get one of the default categories
     * @return The normalized category
     */
    public static String normalizeCategory (final String categoryLabel)
    {
        if (CATEGORIES.keySet ().contains (categoryLabel))
            return categoryLabel;
        return detectCategory (Collections.singletonList (categoryLabel));
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @return The detected tag
     */
    public static String detectCategory (final String [] texts)
    {
        return detectCategory (Arrays.asList (texts), false);
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @param categoryFromNamePrefix If true, a category tag at the very start of one of the texts
     *            declares the category and takes precedence over keyword matches anywhere in the
     *            texts
     * @return The detected tag
     */
    public static String detectCategory (final String [] texts, final boolean categoryFromNamePrefix)
    {
        return detectCategory (Arrays.asList (texts), categoryFromNamePrefix);
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @return The detected tag
     */
    public static String detectCategory (final Collection<String> texts)
    {
        return detectCategory (texts, false);
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @param categoryFromNamePrefix If true, a category tag at the very start of one of the texts
     *            declares the category and takes precedence over keyword matches anywhere in the
     *            texts
     * @return The detected tag
     */
    public static String detectCategory (final Collection<String> texts, final boolean categoryFromNamePrefix)
    {
        if (categoryFromNamePrefix)
        {
            final String category = detectCategoryByNamePrefix (texts);
            if (category != null)
                return category;
        }
        return detect (texts, CATEGORY_LOOKUP, CATEGORY_UNKNOWN);
    }


    /**
     * Detect a category from a category tag at the very start of a text (e.g. 'PAD Solina' or
     * 'BASS Growler'). Such a prefix declares the intended category explicitly and therefore takes
     * precedence over keyword matches anywhere in the texts, which can otherwise win accidentally
     * (e.g. 'BELL Vibrato Strings' would be detected as Strings instead of Bell).
     *
     * @param texts The texts, the most specific (e.g. the preset name) first
     * @return The category or null if the first word of none of the texts is a category tag
     */
    private static String detectCategoryByNamePrefix (final Collection<String> texts)
    {
        for (final String text: texts)
        {
            final String [] tokens = text.trim ().toUpperCase (Locale.US).split ("[ _-]+");
            if (tokens.length > 1)
            {
                final String category = CATEGORY_PREFIX_LOOKUP.get (tokens[0]);
                if (category != null)
                    return category;
            }
        }
        return null;
    }


    /**
     * Detect keywords in the given strings.
     *
     * @param texts The texts
     * @return The detected keywords
     */
    public static String [] detectKeywords (final String [] texts)
    {
        return detectKeywords (Arrays.asList (texts));
    }


    /**
     * Detect keywords in the given strings.
     *
     * @param texts The texts
     * @return The detected keywords
     */
    public static String [] detectKeywords (final Collection<String> texts)
    {
        final Set<String> keywords = new HashSet<> ();
        for (final String text: texts)
        {
            final String t = text.toUpperCase (Locale.US);
            for (final Map.Entry<String, String> e: KEYWORD_LOOKUP.entrySet ())
                if (t.contains (e.getKey ()))
                    keywords.add (e.getValue ());
        }
        return keywords.toArray (new String [keywords.size ()]);
    }


    /**
     * Detect a tag in the given strings.
     *
     * @param texts The texts
     * @param lookupTags The tag lookups
     * @param defaultTag The tag to return if none matched
     * @return The detected tag
     */
    public static String detect (final String [] texts, final String [] lookupTags, final String defaultTag)
    {
        if (lookupTags == null)
            return defaultTag;

        final Map<String, String> lookupMap = new TreeMap<> (new StringLengthComparator ());
        Arrays.asList (lookupTags).forEach (value -> lookupMap.put (value.toUpperCase (Locale.US), value));

        for (final String text: texts)
        {
            final String t = text.toUpperCase (Locale.US);
            for (final Map.Entry<String, String> e: lookupMap.entrySet ())
                if (t.contains (e.getKey ()))
                    return e.getValue ();
        }
        return defaultTag;
    }


    /**
     * Detect a tag in the given strings.
     *
     * @param texts The texts
     * @param lookupMap The map with the tag lookups
     * @param defaultTag The tag to return if none matched
     * @return The detected tag
     */
    public static String detect (final String [] texts, final Map<String, String> lookupMap, final String defaultTag)
    {
        if (texts == null)
            return defaultTag;
        return detect (Arrays.asList (texts), lookupMap, defaultTag);
    }


    /**
     * Detect a tag in the given strings.
     *
     * @param texts The texts
     * @param lookupMap The map with the tag lookups
     * @param defaultTag The tag to return if none matched
     * @return The detected tag
     */
    public static String detect (final Collection<String> texts, final Map<String, String> lookupMap, final String defaultTag)
    {
        final Map<String, String> results = new HashMap<> ();
        for (final String text: texts)
        {
            final String t = text.toUpperCase (Locale.US);
            for (final Map.Entry<String, String> e: lookupMap.entrySet ())
            {
                final String key = e.getKey ();
                if (t.contains (key))
                    results.put (key, e.getValue ());
            }
        }
        if (results.isEmpty ())
            return defaultTag;
        return Collections.max (results.entrySet (), (entry1, entry2) -> entry1.getKey ().length () - entry2.getKey ().length ()).getValue ();
    }


    /**
     * Comparator to sort by the longest text first.
     */
    static class StringLengthComparator implements Comparator<String>
    {
        /** {@inheritDoc}} */
        @Override
        public int compare (final String s1, final String s2)
        {
            final int diff = s2.length () - s1.length ();
            return diff == 0 ? s1.compareTo (s2) : diff;
        }
    }


    /**
     * Creates camel case from the given input string. All categories and keywords are considered
     * for upper-case.
     *
     * @param input The input string
     * @return The output string in camel case notation
     */
    public static String toCamelCase (final String input)
    {
        if (input == null || input.isEmpty ())
            return input;

        final StringBuilder out = new StringBuilder ();
        final String s = input.toUpperCase (Locale.ROOT);

        int i = 0;
        while (i < s.length ())
        {
            boolean matched = false;

            for (final String w: WORD_DICT)
                if (s.startsWith (w, i))
                {
                    out.append (capitalize (w));
                    i += w.length ();
                    matched = true;
                    break;
                }

            if (!matched)
            {
                // Fallback: single character
                out.append (Character.toLowerCase (s.charAt (i)));
                i++;
            }
        }

        // Ensure first character is upper case
        out.setCharAt (0, Character.toUpperCase (out.charAt (0)));
        return out.toString ();
    }


    private static String capitalize (final String w)
    {
        return w.substring (0, 1) + w.substring (1).toLowerCase (Locale.ROOT);
    }
}
