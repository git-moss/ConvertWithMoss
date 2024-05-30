// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format;

import java.util.Arrays;
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
    private static final Map<String, String>    KEYWORD_LOOKUP                = new TreeMap<> (new StringLengthComparator ());

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
            CATEGORY_BASS,
            "Fretless",
            "Slap",
            "Fingered"
        });
        CATEGORIES.put (CATEGORY_BELL, new String []
        {
            CATEGORY_BELL,
            "Musical Box",
            "Music Box",
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
            "Drum-Set",
            "Drumset",
            "Cymbal",
            "Gong",
            "Ride",
            "Kit",
            "Tom",
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
            "SciFi",
            "Sci-Fi",
            "Wind",
            "Rain",
            "Thunder",
            "Telephone",
            "Metal",
            "Noise",
            "Cricket",
            "Experiment",
            "Gun",
            "Heart",
            "Stadium",
            "Applause",
            "Surround"
        });
        CATEGORIES.put (CATEGORY_GUITAR, new String []
        {
            CATEGORY_GUITAR,
            "Rajao",
            "Banjo"
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
            "Harpsi"
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
            "Orchestra",
        });
        CATEGORIES.put (CATEGORY_ORGAN, new String []
        {
            CATEGORY_ORGAN,
            "Tonewheel",
            "Accordion",
            "Hammond",
            "Farfisa",
            "Gospel",
            "B3",
            "C3"
        });
        CATEGORIES.put (CATEGORY_PAD, new String []
        {
            CATEGORY_PAD,
            "Ambient",
            "Atmo"
        });
        CATEGORIES.put (CATEGORY_PERCUSSION, new String []
        {
            CATEGORY_PERCUSSION,
            "Tambourine",
            "Woodblock",
            "Triangle",
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
            "Tabla"
        });
        CATEGORIES.put (CATEGORY_PIANO, new String []
        {
            CATEGORY_PIANO,
            "Grand",
            "Electric Piano",
            "E-Piano",
            "Upright",
            "Digital Piano",
            "Klavier",
            "Clav",
            "Suitcase",
            "Whirly",
            "Wurlitz",
            "Mark I",
            "Rhodes",
            "EP"
        });
        CATEGORIES.put (CATEGORY_PIPE, new String []
        {
            CATEGORY_PIPE,
            "Flute",
            "Didgeridoo",
            "Whistle",
            "Piccolo",
            "Recorder"
        });
        CATEGORIES.put (CATEGORY_PLUCK, new String []
        {
            CATEGORY_PLUCK,
            "Balalaika",
            "Dulcimer",
            "Mandolin",
            "Sitar",
            "Harp",
            "Koto",
            "Lyre",
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
            "Trance",
            "Analog",
            "Sweep",
            "Swell",
            "Mini",
            "Moog",
            "Syn",
            "SAW",
            "DJ"
        });
        CATEGORIES.put (CATEGORY_WINDS, new String []
        {
            "Sax",
            "Oboe",
            "Clarinet",
            "Bassoon",
            "Harmonica",
            "Bag Pipe",
            "Klarinette",
            "Musette",
            "Woodwind"
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

        Arrays.asList (KEYWORDS).forEach (value -> KEYWORD_LOOKUP.put (value.toUpperCase (Locale.US), value));
    }


    /**
     * Constructor.
     */
    private TagDetector ()
    {
        // Intentionally empty
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @return The detected tag
     */
    public static String detectCategory (final String [] texts)
    {
        return detect (texts, CATEGORY_LOOKUP, "Unknown");
    }


    /**
     * Detect a category in the given strings.
     *
     * @param texts The texts
     * @return The detected tag
     */
    public static String detectCategory (final List<String> texts)
    {
        return detect (texts, CATEGORY_LOOKUP, "Unknown");
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
    public static String [] detectKeywords (final List<String> texts)
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
    public static String detect (final List<String> texts, final Map<String, String> lookupMap, final String defaultTag)
    {
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
}
