// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * Helper class to detect note values.
 *
 * @author Jürgen Moßgraber
 */
public class NoteParser
{
    /** The names of notes. */
    private static final String []            NOTE_NAMES_FLAT         =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "B"
    };
    private static final String []            NOTE_NAMES_SHARP        =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };
    /** The names of notes. */
    private static final String []            NOTE_NAMES_FLAT_GERMAN  =
    {
        "C",
        "Db",
        "D",
        "Eb",
        "E",
        "F",
        "Gb",
        "G",
        "Ab",
        "A",
        "Bb",
        "H"
    };
    private static final String []            NOTE_NAMES_SHARP_GERMAN =
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "H"
    };

    private static final Map<String, Integer> KEY_MAP                 = new HashMap<> ();
    private static final Integer              DEFAULT_VALUE           = Integer.valueOf (-1);

    static
    {
        // Create note map
        for (int note = 0; note < 128; note++)
        {
            final int n = Math.abs (note % 12);
            final String octave = Integer.toString (note / 12 - 2);
            final Integer ni = Integer.valueOf (note);
            KEY_MAP.put (NOTE_NAMES_FLAT[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_FLAT_GERMAN[n] + octave, ni);
            KEY_MAP.put (NOTE_NAMES_SHARP_GERMAN[n] + octave, ni);
            KEY_MAP.put (String.format ("%d", ni), ni);
            if (note < 100)
                KEY_MAP.put (String.format ("%03d", ni), ni);
        }
    }


    /**
     * Format a note using only sharps. Expects C3 (= 60) as middle C.
     *
     * @param note The MIDI note
     * @return The formatted text
     */
    public static String formatNoteSharps (final int note)
    {
        final int n = Math.abs (note % 12);
        final String octave = Integer.toString (note / 12 - 2);
        return NOTE_NAMES_SHARP[n] + octave;
    }


    /**
     * Private due to helper class.
     */
    private NoteParser ()
    {
        // Intentionally empty
    }


    /**
     * The lookup map contains all variations of note representations including MIDI numbers.
     *
     * @param noteValue The note as a text
     * @return The parsed note or -1 if it could not be detected or the given value was null
     */
    public static int parseNote (final String noteValue)
    {
        if (noteValue == null)
            return -1;
        return KEY_MAP.getOrDefault (noteValue.toUpperCase (Locale.US), DEFAULT_VALUE).intValue ();
    }
}
