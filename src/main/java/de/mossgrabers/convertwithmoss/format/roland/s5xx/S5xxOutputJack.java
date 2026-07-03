// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

/**
 * Output jack assignment stored at patch block byte 243. Values 0–7 = physical jacks 1–8; value 8 =
 * "Tone".
 *
 * @author Jürgen Moßgraber
 */
public enum S5xxOutputJack
{
    /** Jack 1. */
    JACK_1(0, "Jack 1"),
    /** Jack 2. */
    JACK_2(1, "Jack 2"),
    /** Jack 3. */
    JACK_3(2, "Jack 3"),
    /** Jack 4. */
    JACK_4(3, "Jack 4"),
    /** Jack 5. */
    JACK_5(4, "Jack 5"),
    /** Jack 6. */
    JACK_6(5, "Jack 6"),
    /** Jack 7. */
    JACK_7(6, "Jack 7"),
    /** Jack 8. */
    JACK_8(7, "Jack 8"),
    /** Tone output. */
    TONE(8, "Tone"),
    /** Unknown jack. */
    UNKNOWN(-1, "Unknown");


    private final int    value;
    private final String label;


    /**
     * Constructor.
     *
     * @param value The value
     * @param label The label
     */
    private S5xxOutputJack (final int value, final String label)
    {
        this.value = value;
        this.label = label;
    }


    /**
     * Get the value of the jack.
     *
     * @return The value
     */
    public int getValue ()
    {
        return this.value;
    }


    /**
     * Get the label of the jack.
     *
     * @return The label
     */
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * Resolves from a raw unsigned byte; returns {@link #UNKNOWN} for unrecognized values.
     *
     * @param b The byte containing the index
     * @return The output jack
     */
    public static S5xxOutputJack fromByte (final int b)
    {
        final int unsigned = b & 0xFF;
        for (final S5xxOutputJack j: values ())
            if (j.value == unsigned)
                return j;
        return UNKNOWN;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return this.label;
    }
}