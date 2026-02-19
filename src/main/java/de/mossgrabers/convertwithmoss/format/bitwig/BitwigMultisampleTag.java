// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * The Bitwig multisample format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class BitwigMultisampleTag
{
    /** The root tag. */
    public static final String                    MULTISAMPLE    = "multisample";
    /** The sound category tag. */
    public static final String                    GENERATOR      = "generator";
    /** The file generator tag. */
    public static final String                    CATEGORY       = "category";
    /** The creator of the multisample tag. */
    public static final String                    CREATOR        = "creator";
    /** The description tag. */
    public static final String                    DESCRIPTION    = "description";
    /** The keywords tag. */
    public static final String                    KEYWORDS       = "keywords";
    /** The keyword tag. */
    public static final String                    KEYWORD        = "keyword";
    /** The group tag. */
    public static final String                    GROUP          = "group";
    /** The layer tag. */
    public static final String                    LAYER          = "layer";
    /** The sample tag. */
    public static final String                    SAMPLE         = "sample";
    /** The sample-key tag. */
    public static final String                    KEY            = "key";
    /** The sample-velocity tag. */
    public static final String                    VELOCITY       = "velocity";
    /** The sample-select tag. */
    public static final String                    SELECT         = "select";
    /** The sample-loop tag. */
    public static final String                    LOOP           = "loop";

    /** The supported top level tags. */
    public static final Set<String>               TOP_LEVEL_TAGS = Set.of (GENERATOR, CATEGORY, CREATOR, DESCRIPTION, KEYWORDS, GROUP, LAYER, SAMPLE);
    /** The supported layer tags. */
    public static final Set<String>               LAYER_TAGS     = Set.of (SAMPLE);
    /** The supported sample tags. */
    public static final Set<String>               SAMPLE_TAGS    = Set.of (KEY, VELOCITY, SELECT, LOOP);

    /** The group attribute. */
    public static final String                    ATTR_GROUP     = "group";

    /** Supported attributes of all tags. */
    private static final Map<String, Set<String>> ATTRIBUTES     = new HashMap<> ();
    static
    {
        ATTRIBUTES.put (MULTISAMPLE, Set.of ("name"));
        ATTRIBUTES.put (GENERATOR, Collections.emptySet ());
        ATTRIBUTES.put (CATEGORY, Collections.emptySet ());
        ATTRIBUTES.put (CREATOR, Collections.emptySet ());
        ATTRIBUTES.put (DESCRIPTION, Collections.emptySet ());
        ATTRIBUTES.put (KEYWORDS, Collections.emptySet ());
        ATTRIBUTES.put (KEYWORD, Collections.emptySet ());
        ATTRIBUTES.put (GROUP, Set.of ("name", "color"));
        ATTRIBUTES.put (LAYER, Set.of ("name"));
        // parameter-1 to 3 are not used but should not trigger an error log entry
        ATTRIBUTES.put (SAMPLE, Set.of ("file", ATTR_GROUP, "sample-start", "sample-stop", "gain", "tune", "reverse", "zone-logic", "parameter-1", "parameter-2", "parameter-3"));
        ATTRIBUTES.put (KEY, Set.of ("root", "low", "high", "low-fade", "high-fade", "tune", "track"));
        ATTRIBUTES.put (VELOCITY, Set.of ("low", "high", "low-fade", "high-fade"));
        ATTRIBUTES.put (SELECT, Collections.emptySet ());
        ATTRIBUTES.put (LOOP, Set.of ("mode", "start", "stop", "fade"));
    }


    /**
     * Private constructor for utility class.
     */
    private BitwigMultisampleTag ()
    {
        // Intentionally empty
    }


    /**
     * Get the supported attributes of a tag.
     *
     * @param tagName The name of the tag
     * @return The tags
     */
    public static Set<String> getAttributes (final String tagName)
    {
        return ATTRIBUTES.get (tagName);
    }
}
