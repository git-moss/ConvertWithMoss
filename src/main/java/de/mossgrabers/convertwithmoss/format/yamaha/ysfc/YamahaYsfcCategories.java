// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.util.HashMap;
import java.util.Map;


/**
 * The sound categories of the Montage/MODX.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcCategories
{
    /** The 'No Assign' tag. */
    public static String                      NO_ASSIGN      = "No Assign";

    private static final Map<Integer, String> subCategoryMap = new HashMap<> ();
    private static final Map<Integer, String> categoryMap    = new HashMap<> ();

    static
    {
        subCategoryMap.put (Integer.valueOf (0), "Acoustic");
        subCategoryMap.put (Integer.valueOf (1), "Layer");
        subCategoryMap.put (Integer.valueOf (2), "Modern");
        subCategoryMap.put (Integer.valueOf (3), "Vintage");
        subCategoryMap.put (Integer.valueOf (4), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (5), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (6), "Electronic");
        subCategoryMap.put (Integer.valueOf (7), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (8), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (16), "Electric Piano");
        subCategoryMap.put (Integer.valueOf (17), "FM Piano");
        subCategoryMap.put (Integer.valueOf (18), "Clavi");
        subCategoryMap.put (Integer.valueOf (19), "Synth");
        subCategoryMap.put (Integer.valueOf (20), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (21), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (22), "Electronic");
        subCategoryMap.put (Integer.valueOf (23), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (24), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (32), "Tone Wheel");
        subCategoryMap.put (Integer.valueOf (33), "Combo");
        subCategoryMap.put (Integer.valueOf (34), "Pipe");
        subCategoryMap.put (Integer.valueOf (35), "Synth");
        subCategoryMap.put (Integer.valueOf (36), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (37), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (38), "Electronic");
        subCategoryMap.put (Integer.valueOf (39), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (40), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (48), "Acoustic");
        subCategoryMap.put (Integer.valueOf (49), "Electric Clean");
        subCategoryMap.put (Integer.valueOf (50), "Distortion");
        subCategoryMap.put (Integer.valueOf (51), "Synth");
        subCategoryMap.put (Integer.valueOf (52), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (53), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (54), "Electronic");
        subCategoryMap.put (Integer.valueOf (55), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (56), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (64), "Acoustic");
        subCategoryMap.put (Integer.valueOf (65), "Electric");
        subCategoryMap.put (Integer.valueOf (66), "Synth");
        subCategoryMap.put (Integer.valueOf (67), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (68), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (69), "Electronic");
        subCategoryMap.put (Integer.valueOf (70), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (71), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (80), "Solo");
        subCategoryMap.put (Integer.valueOf (81), "Ensemble");
        subCategoryMap.put (Integer.valueOf (82), "Pizzicato");
        subCategoryMap.put (Integer.valueOf (83), "Synth");
        subCategoryMap.put (Integer.valueOf (84), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (85), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (86), "Electronic");
        subCategoryMap.put (Integer.valueOf (87), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (88), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (96), "Solo");
        subCategoryMap.put (Integer.valueOf (97), "Ensemble");
        subCategoryMap.put (Integer.valueOf (98), "Orchestra");
        subCategoryMap.put (Integer.valueOf (99), "Synth");
        subCategoryMap.put (Integer.valueOf (100), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (101), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (102), "Electronic");
        subCategoryMap.put (Integer.valueOf (103), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (104), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (112), "Saxophone");
        subCategoryMap.put (Integer.valueOf (113), "Flute");
        subCategoryMap.put (Integer.valueOf (114), "Woodwind");
        subCategoryMap.put (Integer.valueOf (115), "Reed / Pipe");
        subCategoryMap.put (Integer.valueOf (116), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (117), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (118), "Electronic");
        subCategoryMap.put (Integer.valueOf (119), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (120), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (128), "Analog");
        subCategoryMap.put (Integer.valueOf (129), "Digital");
        subCategoryMap.put (Integer.valueOf (130), "Hip Hop");
        subCategoryMap.put (Integer.valueOf (131), "Dance");
        subCategoryMap.put (Integer.valueOf (132), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (133), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (134), "Electronic");
        subCategoryMap.put (Integer.valueOf (135), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (136), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (144), "Analog");
        subCategoryMap.put (Integer.valueOf (145), "Warm");
        subCategoryMap.put (Integer.valueOf (146), "Bright");
        subCategoryMap.put (Integer.valueOf (147), "Choir");
        subCategoryMap.put (Integer.valueOf (148), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (149), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (150), "Electronic");
        subCategoryMap.put (Integer.valueOf (151), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (152), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (160), "Analog");
        subCategoryMap.put (Integer.valueOf (161), "Digital");
        subCategoryMap.put (Integer.valueOf (162), "Decay");
        subCategoryMap.put (Integer.valueOf (163), "Hook");
        subCategoryMap.put (Integer.valueOf (164), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (165), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (166), "Electronic");
        subCategoryMap.put (Integer.valueOf (167), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (168), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (176), "Mallet");
        subCategoryMap.put (Integer.valueOf (177), "Bell");
        subCategoryMap.put (Integer.valueOf (178), "Synth Bell");
        subCategoryMap.put (Integer.valueOf (179), "Pitched Drum");
        subCategoryMap.put (Integer.valueOf (180), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (181), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (182), "Electronic");
        subCategoryMap.put (Integer.valueOf (183), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (184), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (192), "Drums");
        subCategoryMap.put (Integer.valueOf (193), "Percussion");
        subCategoryMap.put (Integer.valueOf (194), "Synth");
        subCategoryMap.put (Integer.valueOf (195), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (196), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (197), "Electronic");
        subCategoryMap.put (Integer.valueOf (198), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (199), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (208), "Moving");
        subCategoryMap.put (Integer.valueOf (209), "Ambient");
        subCategoryMap.put (Integer.valueOf (210), "Nature");
        subCategoryMap.put (Integer.valueOf (211), "Sci-Fi");
        subCategoryMap.put (Integer.valueOf (212), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (213), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (214), "Electronic");
        subCategoryMap.put (Integer.valueOf (215), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (216), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (224), "Moving");
        subCategoryMap.put (Integer.valueOf (225), "Ambient");
        subCategoryMap.put (Integer.valueOf (226), "Sweep");
        subCategoryMap.put (Integer.valueOf (227), "Hit");
        subCategoryMap.put (Integer.valueOf (228), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (229), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (230), "Electronic");
        subCategoryMap.put (Integer.valueOf (231), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (232), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (240), "Bowed");
        subCategoryMap.put (Integer.valueOf (241), "Plucked");
        subCategoryMap.put (Integer.valueOf (242), "Struck");
        subCategoryMap.put (Integer.valueOf (243), "Blown");
        subCategoryMap.put (Integer.valueOf (244), "Rock / Pop");
        subCategoryMap.put (Integer.valueOf (245), "R&B / Hip Hop");
        subCategoryMap.put (Integer.valueOf (246), "Electronic");
        subCategoryMap.put (Integer.valueOf (247), "Jazz / World");
        subCategoryMap.put (Integer.valueOf (248), NO_ASSIGN);
        subCategoryMap.put (Integer.valueOf (256), NO_ASSIGN);

        categoryMap.put (Integer.valueOf (0x0001), "Piano");
        categoryMap.put (Integer.valueOf (0x0002), "Keyboard");
        categoryMap.put (Integer.valueOf (0x0004), "Organ");
        categoryMap.put (Integer.valueOf (0x0008), "Guitar");
        categoryMap.put (Integer.valueOf (0x0010), "Bass");
        categoryMap.put (Integer.valueOf (0x0020), "Strings");
        categoryMap.put (Integer.valueOf (0x0040), "Brass");
        categoryMap.put (Integer.valueOf (0x0080), "Woodwind");
        categoryMap.put (Integer.valueOf (0x0100), "Syn Lead");
        categoryMap.put (Integer.valueOf (0x0200), "Pad / Choir");
        categoryMap.put (Integer.valueOf (0x0400), "Syn Comp");
        categoryMap.put (Integer.valueOf (0x0800), "Chromatic Perc");
        categoryMap.put (Integer.valueOf (0x1000), "Drum / Perc");
        categoryMap.put (Integer.valueOf (0x2000), "Sound FX");
        categoryMap.put (Integer.valueOf (0x4000), "Musical FX");
        categoryMap.put (Integer.valueOf (0x8000), "Ethnic");
    }


    /**
     * Get the category name.
     * 
     * @param categoryValue The category value
     * @return The category name
     */
    public static String getCategory (final int categoryValue)
    {
        for (final Map.Entry<Integer, String> entry: categoryMap.entrySet ())
        {
            if ((categoryValue & entry.getKey ().intValue ()) > 0)
                return entry.getValue ();
        }
        return NO_ASSIGN;
    }


    /**
     * Get the sub-category name.
     * 
     * @param categoryValue The category value
     * @return The sub-category name
     */
    public static String getSubCategory (final int categoryValue)
    {
        final String name = subCategoryMap.get (Integer.valueOf (categoryValue));
        return name == null ? NO_ASSIGN : name;
    }
}
