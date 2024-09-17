// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

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
    public static String                      NO_ASSIGN                    = "No Assign";

    private static final String []            WAVE_FORM_CATEGORIES         = new String []
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
        NO_ASSIGN
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

        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (0), "Acoustic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (1), "Layer");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (2), "Modern");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (3), "Vintage");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (4), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (5), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (6), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (7), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (8), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (16), "Electric Piano");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (17), "FM Piano");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (18), "Clavi");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (19), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (20), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (21), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (22), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (23), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (24), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (32), "Tone Wheel");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (33), "Combo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (34), "Pipe");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (35), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (36), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (37), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (38), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (39), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (40), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (48), "Acoustic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (49), "Electric Clean");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (50), "Distortion");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (51), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (52), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (53), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (54), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (55), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (56), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (64), "Acoustic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (65), "Electric");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (66), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (67), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (68), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (69), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (70), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (71), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (80), "Solo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (81), "Ensemble");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (82), "Pizzicato");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (83), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (84), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (85), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (86), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (87), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (88), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (96), "Solo");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (97), "Ensemble");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (98), "Orchestra");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (99), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (100), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (101), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (102), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (103), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (104), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (112), "Saxophone");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (113), "Flute");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (114), "Woodwind");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (115), "Reed / Pipe");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (116), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (117), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (118), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (119), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (120), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (128), "Analog");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (129), "Digital");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (130), "Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (131), "Dance");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (132), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (133), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (134), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (135), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (136), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (144), "Analog");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (145), "Warm");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (146), "Bright");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (147), "Choir");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (148), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (149), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (150), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (151), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (152), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (160), "Analog");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (161), "Digital");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (162), "Decay");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (163), "Hook");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (164), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (165), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (166), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (167), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (168), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (176), "Mallet");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (177), "Bell");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (178), "Synth Bell");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (179), "Pitched Drum");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (180), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (181), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (182), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (183), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (184), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (192), "Drums");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (193), "Percussion");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (194), "Synth");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (195), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (196), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (197), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (198), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (199), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (208), "Moving");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (209), "Ambient");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (210), "Nature");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (211), "Sci-Fi");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (212), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (213), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (214), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (215), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (216), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (224), "Moving");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (225), "Ambient");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (226), "Sweep");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (227), "Hit");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (228), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (229), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (230), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (231), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (232), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (240), "Bowed");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (241), "Plucked");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (242), "Struck");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (243), "Blown");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (244), "Rock / Pop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (245), "R&B / Hip Hop");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (246), "Electronic");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (247), "Jazz / World");
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (248), NO_ASSIGN);
        PERFORMANCE_SUB_CATEGORY_MAP.put (Integer.valueOf (256), NO_ASSIGN);

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
     * Get the waveform category name.
     *
     * @param categoryValue The category value
     * @return The category name
     */
    public static String getWaveformCategory (final int categoryValue)
    {
        return WAVE_FORM_CATEGORIES[Math.clamp (categoryValue, 0, 255) / 16];
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
        return NO_ASSIGN;
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
        return name == null ? NO_ASSIGN : name;
    }
}
