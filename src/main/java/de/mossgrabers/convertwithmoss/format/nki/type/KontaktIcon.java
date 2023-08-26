// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import java.util.HashMap;
import java.util.Map;


/**
 * The static icon list of Kontakt.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktIcon
{
    private static final Map<Integer, String> ICON_MAP = new HashMap<> ();
    static
    {
        ICON_MAP.put (Integer.valueOf (0x00), "Organ");
        ICON_MAP.put (Integer.valueOf (0x01), "Cello");
        ICON_MAP.put (Integer.valueOf (0x02), "Drum Kit");
        ICON_MAP.put (Integer.valueOf (0x03), "Bell");
        ICON_MAP.put (Integer.valueOf (0x04), "Trumpet");
        ICON_MAP.put (Integer.valueOf (0x05), "Guitar");
        ICON_MAP.put (Integer.valueOf (0x06), "Piano");
        ICON_MAP.put (Integer.valueOf (0x07), "Marimba");
        ICON_MAP.put (Integer.valueOf (0x08), "Record Player");
        ICON_MAP.put (Integer.valueOf (0x09), "E-Piano");
        ICON_MAP.put (Integer.valueOf (0x0A), "Drum Pads");
        ICON_MAP.put (Integer.valueOf (0x0B), "Bass Guitar");
        ICON_MAP.put (Integer.valueOf (0x0C), "Electric Guitar");
        ICON_MAP.put (Integer.valueOf (0x0D), "Wave");
        ICON_MAP.put (Integer.valueOf (0x0E), "Asian Symbol");
        ICON_MAP.put (Integer.valueOf (0x0F), "Flute");
        ICON_MAP.put (Integer.valueOf (0x10), "Speaker");
        ICON_MAP.put (Integer.valueOf (0x11), "Score");
        ICON_MAP.put (Integer.valueOf (0x12), "Conga");
        ICON_MAP.put (Integer.valueOf (0x13), "Pipe Organ");
        ICON_MAP.put (Integer.valueOf (0x14), "FX");
        ICON_MAP.put (Integer.valueOf (0x15), "Computer");
        ICON_MAP.put (Integer.valueOf (0x16), "Violin");
        ICON_MAP.put (Integer.valueOf (0x17), "Surround");
        ICON_MAP.put (Integer.valueOf (0x18), "Synthesizer");
        ICON_MAP.put (Integer.valueOf (0x19), "Microphone");
        ICON_MAP.put (Integer.valueOf (0x1A), "Oboe");
        ICON_MAP.put (Integer.valueOf (0x1B), "Saxophone");
        ICON_MAP.put (Integer.valueOf (0x1C), "New");
    }


    /**
     * Get the name of an icon.
     *
     * @param iconID The index of the icon
     * @return The name
     */
    public static String getName (final int iconID)
    {
        return ICON_MAP.get (Integer.valueOf (iconID));
    }


    /**
     * Hide due to utility class.
     */
    private KontaktIcon ()
    {
        // Intentionally empty
    }
}
