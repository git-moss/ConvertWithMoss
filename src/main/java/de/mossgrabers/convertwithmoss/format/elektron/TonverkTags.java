// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.elektron;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * The category and the tags of a Tonverk preset. The device knows two main categories, DRUMS and
 * KEYS, by which its LOAD PRESET menu filters, and a fixed set of upper-case tags. The category and
 * the keywords of the model are mapped onto them; whatever has no counterpart on the device is left
 * out.
 *
 * @author Jürgen Moßgraber
 */
public final class TonverkTags
{
    /** The main category of all presets which are not drums. */
    public static final String               CATEGORY_KEYS   = "KEYS";
    /** The main category of drum presets. */
    public static final String               CATEGORY_DRUMS  = "DRUMS";

    /** The tags of the device, as the presets of the factory library use them. */
    private static final String []           DEVICE_TAGS     =
    {
        "ACOUSTIC",
        "ARPEGGIO",
        "ATMOSPHERIC",
        "BASS",
        "BRASS",
        "BRIGHT",
        "CHORD",
        "DARK",
        "DEEP",
        "EPIC",
        "EVOLVING",
        "EXPRESSIVE",
        "GLITCH",
        "HARD",
        "HI-HAT",
        "KICK",
        "LEAD",
        "LOOP",
        "METALLIC",
        "NOISY",
        "PAD",
        "PERCUSSION",
        "SNARE",
        "SOFT",
        "SOUND FX",
        "STRINGS",
        "TEXTURE",
        "VINTAGE"
    };
    private static final Set<String>         TAGS            = Set.of (DEVICE_TAGS);

    /** The categories of the model which are drums. */
    private static final Set<String>         DRUM_CATEGORIES = Set.of (TagDetector.CATEGORY_DRUM, TagDetector.CATEGORY_ACOUSTIC_DRUM, TagDetector.CATEGORY_KICK, TagDetector.CATEGORY_SNARE, TagDetector.CATEGORY_HI_HAT, TagDetector.CATEGORY_CLAP, TagDetector.CATEGORY_PERCUSSION);

    /**
     * The categories and keywords of the model whose tag has another name; all others are matched
     * to a tag of the same name, ignoring the case.
     */
    private static final Map<String, String> TAG_SYNONYMS    = Map.of (TagDetector.CATEGORY_FX, "SOUND FX", TagDetector.CATEGORY_LOOPS, "LOOP", "arp", "ARPEGGIO", "arpeggiated", "ARPEGGIO", "atmo", "ATMOSPHERIC");


    /**
     * Constructor.
     */
    private TonverkTags ()
    {
        // Intentionally empty
    }


    /**
     * Get the main category of a preset.
     *
     * @param category The category of the model, may be null
     * @param isDrumKit True if the preset is written with the Drum machine
     * @return {@link #CATEGORY_DRUMS} for a drum kit or a drum category, otherwise
     *         {@link #CATEGORY_KEYS}
     */
    public static String getCategory (final String category, final boolean isDrumKit)
    {
        if (isDrumKit || CATEGORY_DRUMS.equalsIgnoreCase (category) || DRUM_CATEGORIES.contains (category))
            return CATEGORY_DRUMS;
        return CATEGORY_KEYS;
    }


    /**
     * Get the tags of a preset from the category and the keywords of the model.
     *
     * @param category The category of the model, may be null
     * @param keywords The keywords of the model, may be null
     * @return The tags of the device which match, sorted alphabetically like the device stores them
     */
    public static List<String> getTags (final String category, final String [] keywords)
    {
        final Set<String> tags = new TreeSet<> ();
        addTag (tags, category);
        if (keywords != null)
            for (final String keyword: keywords)
                addTag (tags, keyword);
        return new ArrayList<> (tags);
    }


    private static void addTag (final Set<String> tags, final String text)
    {
        if (text == null || text.isBlank ())
            return;
        final String tag = TAG_SYNONYMS.getOrDefault (text, text.trim ().toUpperCase (Locale.US));
        if (TAGS.contains (tag))
            tags.add (tag);
    }
}
