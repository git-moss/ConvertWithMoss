// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * The sound categories of the Montage/MODX.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCategories
{
    /** The 'No Assign' tag. */
    public static final String                TAG_NO_ASSIGN                = "No Assign";

    private static final String               TAG_ACOUSTIC                 = "Acoustic";
    private static final String               TAG_ROCK_POP                 = "Rock / Pop";
    private static final String               TAG_SYNTH                    = "Synth";
    private static final String               TAG_R_B_HIP_HOP              = "R&B / Hip Hop";
    private static final String               TAG_JAZZ_WORLD               = "Jazz / World";
    private static final String               TAG_ELECTRONIC               = "Electronic";
    private static final String               TAG_ANALOG                   = "Analog";

    private static final String []            MAIN_CATEGORIES              = new String []
    {
        TagDetector.CATEGORY_PIANO,
        TagDetector.CATEGORY_KEYBOARD,
        TagDetector.CATEGORY_ORGAN,
        TagDetector.CATEGORY_GUITAR,
        TagDetector.CATEGORY_BASS,
        TagDetector.CATEGORY_STRINGS,
        TagDetector.CATEGORY_BRASS,
        TagDetector.CATEGORY_WINDS,
        TagDetector.CATEGORY_LEAD,
        TagDetector.CATEGORY_PAD,
        TagDetector.CATEGORY_SYNTH,
        TagDetector.CATEGORY_CHROMATIC_PERCUSSION,
        TagDetector.CATEGORY_DRUM,
        TagDetector.CATEGORY_FX,
        TagDetector.CATEGORY_FX,
        TagDetector.CATEGORY_WORLD,
        TAG_NO_ASSIGN
    };

    private static final Map<String, Integer> WAVE_FORM_INDICES            = new HashMap<> ();
    private static final Map<Integer, String> PERFORMANCE_SUB_CATEGORY_MAP = new HashMap<> ();
    private static final Map<Integer, String> PERFORMANCE_CATEGORY_MAP     = new HashMap<> ();

    static
    {
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, Integer.valueOf (192));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_BASS, Integer.valueOf (64));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_BELL, Integer.valueOf (177));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_BRASS, Integer.valueOf (96));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_CHIP, Integer.valueOf (162));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_VOCAL, Integer.valueOf (146));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_CHROMATIC_PERCUSSION, Integer.valueOf (176));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_CLAP, Integer.valueOf (192));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_DESTRUCTION, Integer.valueOf (162));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_DRONE, Integer.valueOf (162));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_DRUM, Integer.valueOf (192));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_ENSEMBLE, Integer.valueOf (81));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_FX, Integer.valueOf (208));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_GUITAR, Integer.valueOf (48));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_HI_HAT, Integer.valueOf (194));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_KEYBOARD, Integer.valueOf (16));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_KICK, Integer.valueOf (192));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_LEAD, Integer.valueOf (128));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_MONOSYNTH, Integer.valueOf (128));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_ORCHESTRAL, Integer.valueOf (81));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_ORGAN, Integer.valueOf (32));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_PAD, Integer.valueOf (147));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_PERCUSSION, Integer.valueOf (197));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_PIANO, Integer.valueOf (0));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_PIPE, Integer.valueOf (33));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_PLUCK, Integer.valueOf (241));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_SNARE, Integer.valueOf (193));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_STRINGS, Integer.valueOf (81));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_SYNTH, Integer.valueOf (162));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_WINDS, Integer.valueOf (113));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_LOOPS, Integer.valueOf (209));
        WAVE_FORM_INDICES.put (TagDetector.CATEGORY_WORLD, Integer.valueOf (240));

        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (0), TAG_ACOUSTIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (1), "Layer");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (2), "Modern");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (3), "Vintage");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (4), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (5), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (6), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (7), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (8), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (16), "Electric Piano");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (17), "FM Piano");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (18), "Clavi");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (19), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (20), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (21), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (22), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (23), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (24), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (32), "Tone Wheel");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (33), "Combo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (34), "Pipe");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (35), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (36), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (37), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (38), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (39), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (40), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (48), TAG_ACOUSTIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (49), "Electric Clean");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (50), "Distortion");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (51), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (52), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (53), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (54), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (55), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (56), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (64), TAG_ACOUSTIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (65), "Electric");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (66), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (67), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (68), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (69), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (70), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (71), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (80), "Solo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (81), "Ensemble");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (82), "Pizzicato");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (83), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (84), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (85), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (86), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (87), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (88), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (96), "Solo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (97), "Ensemble");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (98), "Orchestra");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (99), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (100), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (101), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (102), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (103), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (104), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (112), "Saxophone");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (113), "Flute");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (114), "Woodwind");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (115), "Reed / Pipe");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (116), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (117), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (118), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (119), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (120), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (128), TAG_ANALOG);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (129), "Digital");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (130), "Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (131), "Dance");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (132), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (133), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (134), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (135), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (136), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (144), TAG_ANALOG);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (145), "Warm");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (146), "Bright");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (147), "Choir");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (148), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (149), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (150), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (151), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (152), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (160), TAG_ANALOG);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (161), "Digital");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (162), "Decay");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (163), "Hook");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (164), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (165), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (166), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (167), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (168), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (176), "Mallet");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (177), "Bell");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (178), "Synth Bell");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (179), "Pitched Drum");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (180), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (181), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (182), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (183), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (184), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (192), "Drums");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (193), "Percussion");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (194), TAG_SYNTH);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (195), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (196), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (197), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (198), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (199), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (208), "Moving");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (209), "Ambient");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (210), "Nature");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (211), "Sci-Fi");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (212), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (213), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (214), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (215), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (216), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (224), "Moving");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (225), "Ambient");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (226), "Sweep");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (227), "Hit");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (228), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (229), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (230), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (231), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (232), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (240), "Bowed");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (241), "Plucked");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (242), "Struck");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (243), "Blown");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (244), TAG_ROCK_POP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (245), TAG_R_B_HIP_HOP);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (246), TAG_ELECTRONIC);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (247), TAG_JAZZ_WORLD);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (248), TAG_NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (256), TAG_NO_ASSIGN);

        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0001), "Piano");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0002), "Keyboard");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0004), "Organ");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0008), "Guitar");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0010), "Bass");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0020), "Strings");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0040), "Brass");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0080), "Woodwind");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0100), "Syn Lead");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0200), "Pad / Choir");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0400), "Syn Comp");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x0800), "Chromatic Perc");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x1000), "Drum / Perc");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x2000), "Sound FX");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x4000), "Musical FX");
        PERFORMANCE_CATEGORY_MAP.put (Integer.valueOf (0x8000), "Ethnic");
    }


    /**
     * Private due to constants class.
     */
    private YamahaYsfcCategories ()
    {
        // Intentionally empty
    }


    /**
     * Get the name of the tag category which is mapped to the performance/waveform main category
     * name.
     *
     * @param categoryValue The category value in the range of 0..256
     * @return The category name
     */
    public static String getMainCategory (final int categoryValue)
    {
        return MAIN_CATEGORIES[Math.clamp (categoryValue, 0, 256) / 16];
    }


    /**
     * Get the waveform category index.
     *
     * @param tagDetectorCategory The generic TagDetector category
     * @return The category name
     */
    public static Integer getWaveformCategoryIndex (final String tagDetectorCategory)
    {
        return WAVE_FORM_INDICES.get (tagDetectorCategory);
    }


    /**
     * Get the performance category name.
     *
     * @param categoryFlagValue The category flag value
     * @return The category name
     */
    public static String getPerformanceCategory (final int categoryFlagValue)
    {
        for (final Map.Entry<Integer, String> entry: PERFORMANCE_CATEGORY_MAP.entrySet ())
            if ((categoryFlagValue & entry.getKey ().intValue ()) > 0)
                return entry.getValue ();
        return TAG_NO_ASSIGN;
    }


    /**
     * Get the performance sub-category name.
     *
     * @param categoryValue The category value
     * @return The sub-category name
     */
    public static String getPerformanceSubCategory (final int categoryValue)
    {
        final String name = PERFORMANCE_SUB_CATEGORY_MAP.get (Integer.valueOf (categoryValue));
        return name == null ? TAG_NO_ASSIGN : name;
    }
}
