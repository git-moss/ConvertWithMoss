// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    public static final String                  CATEGORY_ACOUSTIC_DRUMS       = "Acoustic Drums";
    public static final String                  CATEGORY_BASS                 = "Bass";
    public static final String                  CATEGORY_BELL                 = "Bell";
    public static final String                  CATEGORY_BRASS                = "Brass";
    public static final String                  CATEGORY_CHIP                 = "Chip";
    public static final String                  CATEGORY_VOCAL                = "Vocal";
    public static final String                  CATEGORY_CHROMATIC_PERCUSSION = "Chromatic Percussion";
    public static final String                  CATEGORY_CLAP                 = "Clap";
    public static final String                  CATEGORY_DESTRUCTION          = "Destruction";
    public static final String                  CATEGORY_DRONE                = "Drone";
    public static final String                  CATEGORY_DRUMS                = "Drums";
    public static final String                  CATEGORY_ENSEMBLE             = "Ensemble";
    public static final String                  CATEGORY_FX                   = "FX";
    public static final String                  CATEGORY_GUITAR               = "Guitar";
    public static final String                  CATEGORY_HI_HAT               = "Hi-Hat";
    public static final String                  CATEGORY_KEYBOARDS            = "Keyboards";
    public static final String                  CATEGORY_KICK                 = "Kick";
    public static final String                  CATEGORY_LEAD                 = "Lead";
    public static final String                  CATEGORY_MONOSYNTH            = "Monosynth";
    public static final String                  CATEGORY_ORCHESTRAL           = "Orchestral";
    public static final String                  CATEGORY_ORGAN                = "Organ";
    public static final String                  CATEGORY_PAD                  = "Pad";
    public static final String                  CATEGORY_PERCUSSION           = "Percussion";
    public static final String                  CATEGORY_PIANO                = "Piano";
    public static final String                  CATEGORY_PIPE                 = "Pipe";
    public static final String                  CATEGORY_PLUCKS               = "Plucks";
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
        CATEGORIES.put (CATEGORY_ACOUSTIC_DRUMS, new String []
        {
            "Acoustic Drum"
        });
        CATEGORIES.put (CATEGORY_BASS, new String []
        {
            "Bass",
            "Fretless",
            "Slap",
            "Fingered"
        });
        CATEGORIES.put (CATEGORY_BELL, new String []
        {
            "Bell",
            "Musical Box",
            "Music Box",
            "Tubular"
        });
        CATEGORIES.put (CATEGORY_BRASS, new String []
        {
            "Brass",
            "Horn",
            "Trumpet",
            "Trombone",
            "Tuba"
        });
        CATEGORIES.put (CATEGORY_CHIP, new String []
        {
            "Chip",
            "Computer",
            "CPU",
            "Wave"
        });
        CATEGORIES.put (CATEGORY_VOCAL, new String []
        {
            "Choir",
            "Vocal",
            "Vox",
            "Voice",
            "Vocode",
            "Choral",
            "Gregorian",
            "Ahh",
            "Whisper",
            "Shout",
            "Sing",
            "Microphone"
        });
        CATEGORIES.put (CATEGORY_CHROMATIC_PERCUSSION, new String []
        {
            "Chromatic Percussion",
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
            "Clap"
        });
        CATEGORIES.put (CATEGORY_DESTRUCTION, new String []
        {
            "Destruction"
        });
        CATEGORIES.put (CATEGORY_DRONE, new String []
        {
            "Drone"
        });
        CATEGORIES.put (CATEGORY_DRUMS, new String []
        {
            "Drum",
            "Kit",
            "Tom",
            "Cymbal",
            "Ride",
            "808",
            "909",
            "Gong",
            "Pads"
        });
        CATEGORIES.put (CATEGORY_ENSEMBLE, new String []
        {
            "Ensemble"
        });
        CATEGORIES.put (CATEGORY_FX, new String []
        {
            "FX",
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
            "Guitar",
            "Rajao",
            "Banjo"
        });
        CATEGORIES.put (CATEGORY_HI_HAT, new String []
        {
            "Hi-Hat",
            "HiHat",
            "HH",
            "Hats"
        });
        CATEGORIES.put (CATEGORY_KEYBOARDS, new String []
        {
            "Keyboard",
            "Clavinet",
            "Harpsi"
        });
        CATEGORIES.put (CATEGORY_KICK, new String []
        {
            "Kick"
        });
        CATEGORIES.put (CATEGORY_LEAD, new String []
        {
            "Lead",
            "Solo",
            "Unisono",
            "Sync",
            "Supersaw"
        });
        CATEGORIES.put (CATEGORY_MONOSYNTH, new String []
        {
            "Monosynth"
        });
        CATEGORIES.put (CATEGORY_ORCHESTRAL, new String []
        {
            "Orchestral",
            "Orchestra",
        });
        CATEGORIES.put (CATEGORY_ORGAN, new String []
        {
            "Organ",
            "Tonewheel",
            "Accordion",
            "Farfisa",
            "Gospel",
            "B3",
            "C3"
        });
        CATEGORIES.put (CATEGORY_PAD, new String []
        {
            "Pad"
        });
        CATEGORIES.put (CATEGORY_PERCUSSION, new String []
        {
            "Percussion",
            "Conga",
            "Bongo",
            "Cowbell",
            "Shaker",
            "Timpani",
            "Agogo",
            "Chimes",
            "Djembe",
            "Tabla",
            "Tambourine",
            "Timbale",
            "Triangle",
            "Woodblock"
        });
        CATEGORIES.put (CATEGORY_PIANO, new String []
        {
            "Piano",
            "Grand",
            "Electric Piano",
            "E-Piano",
            "Upright",
            "Digital Piano",
            "Klavier",
            "Clav",
            "EP",
            "Suitcase",
            "Whirly",
            "Wurlitz",
            "Mark I",
            "Rhodes"
        });
        CATEGORIES.put (CATEGORY_PIPE, new String []
        {
            "Pipe",
            "Flute",
            "Didgeridoo",
            "Whistle",
            "Piccolo",
            "Recorder"
        });
        CATEGORIES.put (CATEGORY_PLUCKS, new String []
        {
            "Pluck",
            "Mandolin",
            "Harp",
            "Koto",
            "Sitar",
            "Dulcimer",
            "Mandolin",
            "Oud"
        });
        CATEGORIES.put (CATEGORY_SNARE, new String []
        {
            "Snare"
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
            "Score"
        });
        CATEGORIES.put (CATEGORY_SYNTH, new String []
        {
            "Syn",
            "Sequence",
            "Sweep",
            "Swell",
            "Mini",
            "Moog"
        });
        CATEGORIES.put (CATEGORY_WINDS, new String []
        {
            "Winds",
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
     * Detect keywords in the given strings.
     *
     * @param texts The texts
     * @return The detected keywords
     */
    public static String [] detectKeywords (final String [] texts)
    {
        final Set<String> keywords = new HashSet<> ();
        for (final String text: texts)
        {
            final String t = text.toUpperCase (Locale.US);
            for (final Map.Entry<String, String> e: KEYWORD_LOOKUP.entrySet ())
            {
                if (t.contains (e.getKey ()))
                    keywords.add (e.getValue ());
            }
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
            {
                if (t.contains (e.getKey ()))
                    return e.getValue ();
            }
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
        for (final String text: texts)
        {
            final String t = text.toUpperCase (Locale.US);
            for (final Map.Entry<String, String> e: lookupMap.entrySet ())
            {
                if (t.contains (e.getKey ()))
                    return e.getValue ();
            }
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
