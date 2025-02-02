// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.TagDetector;


/**
 * The static icon list of Kontakt.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktIcon
{
    private static final Map<Integer, String> ID_TO_ICON_NAME_MAP = new HashMap<> ();
    private static final Map<String, Integer> ICON_NAME_TO_ID_MAP = new HashMap<> ();
    static
    {
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x00), "Organ");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x01), "Cello");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x02), "Drum Kit");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x03), "Bell");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x04), "Trumpet");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x05), "Guitar");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x06), "Piano");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x07), "Marimba");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x08), "Record Player");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x09), "E-Piano");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0A), "Drum Pads");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0B), "Bass Guitar");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0C), "Electric Guitar");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0D), "Wave");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0E), "Asian Symbol");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x0F), "Flute");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x10), "Speaker");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x11), "Score");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x12), "Conga");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x13), "Pipe Organ");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x14), "FX");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x15), "Computer");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x16), "Violin");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x17), "Surround");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x18), "Synthesizer");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x19), "Microphone");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x1A), "Oboe");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x1B), "Saxophone");
        ID_TO_ICON_NAME_MAP.put (Integer.valueOf (0x1C), "New");

        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, Integer.valueOf (0x02));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_BASS, Integer.valueOf (0x0B));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_BELL, Integer.valueOf (0x03));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_BRASS, Integer.valueOf (0x04));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_CHIP, Integer.valueOf (0x15));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_VOCAL, Integer.valueOf (0x19));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_CHROMATIC_PERCUSSION, Integer.valueOf (0x07));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_CLAP, Integer.valueOf (0x12));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_DESTRUCTION, Integer.valueOf (0x14));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_DRONE, Integer.valueOf (0x14));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_DRUM, Integer.valueOf (0x0A));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_ENSEMBLE, Integer.valueOf (0x11));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_FX, Integer.valueOf (0x14));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_GUITAR, Integer.valueOf (0x05));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_HI_HAT, Integer.valueOf (0x02));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_KEYBOARD, Integer.valueOf (0x09));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_KICK, Integer.valueOf (0x02));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_LEAD, Integer.valueOf (0x18));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_MONOSYNTH, Integer.valueOf (0x18));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_ORCHESTRAL, Integer.valueOf (0x11));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_ORGAN, Integer.valueOf (0x00));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_PAD, Integer.valueOf (0x18));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_PERCUSSION, Integer.valueOf (0x12));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_PIANO, Integer.valueOf (0x06));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_PIPE, Integer.valueOf (0x13));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_PLUCK, Integer.valueOf (0x05));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_SNARE, Integer.valueOf (0x02));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_STRINGS, Integer.valueOf (0x16));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_SYNTH, Integer.valueOf (0x18));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_WINDS, Integer.valueOf (0x1A));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_LOOPS, Integer.valueOf (0x08));
        ICON_NAME_TO_ID_MAP.put (TagDetector.CATEGORY_WORLD, Integer.valueOf (0x0E));

        ICON_NAME_TO_ID_MAP.put ("Organ", Integer.valueOf (0x00));
        ICON_NAME_TO_ID_MAP.put ("Cello", Integer.valueOf (0x01));
        ICON_NAME_TO_ID_MAP.put ("Drum Kit", Integer.valueOf (0x02));
        ICON_NAME_TO_ID_MAP.put ("Bell", Integer.valueOf (0x03));
        ICON_NAME_TO_ID_MAP.put ("Trumpet", Integer.valueOf (0x04));
        ICON_NAME_TO_ID_MAP.put ("Guitar", Integer.valueOf (0x05));
        ICON_NAME_TO_ID_MAP.put ("Piano", Integer.valueOf (0x06));
        ICON_NAME_TO_ID_MAP.put ("Marimba", Integer.valueOf (0x07));
        ICON_NAME_TO_ID_MAP.put ("Record Player", Integer.valueOf (0x08));
        ICON_NAME_TO_ID_MAP.put ("E-Piano", Integer.valueOf (0x09));
        ICON_NAME_TO_ID_MAP.put ("Drum Pads", Integer.valueOf (0x0A));
        ICON_NAME_TO_ID_MAP.put ("Bass Guitar", Integer.valueOf (0x0B));
        ICON_NAME_TO_ID_MAP.put ("Electric Guitar", Integer.valueOf (0x0C));
        ICON_NAME_TO_ID_MAP.put ("Wave", Integer.valueOf (0x0D));
        ICON_NAME_TO_ID_MAP.put ("Asian Symbol", Integer.valueOf (0x0E));
        ICON_NAME_TO_ID_MAP.put ("Flute", Integer.valueOf (0x0F));
        ICON_NAME_TO_ID_MAP.put ("Speaker", Integer.valueOf (0x10));
        ICON_NAME_TO_ID_MAP.put ("Score", Integer.valueOf (0x11));
        ICON_NAME_TO_ID_MAP.put ("Conga", Integer.valueOf (0x12));
        ICON_NAME_TO_ID_MAP.put ("Pipe Organ", Integer.valueOf (0x13));
        ICON_NAME_TO_ID_MAP.put ("FX", Integer.valueOf (0x14));
        ICON_NAME_TO_ID_MAP.put ("Computer", Integer.valueOf (0x15));
        ICON_NAME_TO_ID_MAP.put ("Violin", Integer.valueOf (0x16));
        ICON_NAME_TO_ID_MAP.put ("Surround", Integer.valueOf (0x17));
        ICON_NAME_TO_ID_MAP.put ("Synthesizer", Integer.valueOf (0x18));
        ICON_NAME_TO_ID_MAP.put ("Microphone", Integer.valueOf (0x19));
        ICON_NAME_TO_ID_MAP.put ("Oboe", Integer.valueOf (0x1A));
        ICON_NAME_TO_ID_MAP.put ("Saxophone", Integer.valueOf (0x1B));
        ICON_NAME_TO_ID_MAP.put ("New", Integer.valueOf (0x1C));
    }


    /**
     * Get the name of an icon.
     *
     * @param iconID The index of the icon
     * @return The name
     */
    public static String getName (final int iconID)
    {
        return ID_TO_ICON_NAME_MAP.get (Integer.valueOf (iconID));
    }


    /**
     * Get the ID of an icon.
     *
     * @param categoryName The name of a TagDetector category
     * @return The ID of the icon
     */
    public static int getID (final String categoryName)
    {
        final Integer iconID = ICON_NAME_TO_ID_MAP.get (categoryName);
        return iconID == null ? 0x1C : iconID.intValue ();
    }


    /**
     * Hide due to utility class.
     */
    private KontaktIcon ()
    {
        // Intentionally empty
    }
}
