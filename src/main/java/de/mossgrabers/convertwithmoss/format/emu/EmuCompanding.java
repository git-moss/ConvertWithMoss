// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu;

/**
 * The transfer function of the AM6072 companding DAC which the E-mu Emulator, the Emulator II and
 * the Emax feed with their sample bytes. A stored byte is a sign in bit 7, a chord in bits 6 to 4
 * and a step in bits 3 to 0; each chord doubles the size of a step, which is what gives the 8 bits
 * of a sample the dynamic range of about 13 linear bits. The expansion is done by the DAC itself,
 * so the byte on the disk is already the code of the converter.
 *
 * @author Jürgen Moßgraber
 */
public final class EmuCompanding
{
    /** The largest magnitude the DAC produces. */
    public static final int       FULL_SCALE = 8031;
    /** The factor which scales the output of the DAC to the 16 bit range. */
    private static final int      SCALE      = 4;

    /** Expansion of the 256 possible sample bytes into signed 16 bit audio. */
    private static final short [] EXPANSION  = createExpansionTable ();


    /**
     * Private constructor since this is a utility class.
     */
    private EmuCompanding ()
    {
        // Intentionally empty
    }


    /**
     * Expand one stored sample byte into a signed 16 bit sample value.
     *
     * @param sampleByte The byte as stored on the disk
     * @return The sample value
     */
    public static short expand (final int sampleByte)
    {
        return EXPANSION[sampleByte & 0xFF];
    }


    /**
     * Compand a signed 16 bit sample value into the byte which the DAC expands to the closest
     * value.
     *
     * @param value The sample value
     * @return The byte to store
     */
    public static int compand (final int value)
    {
        final boolean isNegative = value < 0;
        final int magnitude = Math.min (isNegative ? -value : value, EXPANSION[0x7F]);
        // The transfer function rises monotonically, so the closest of the 16 steps of the chord
        // which covers the magnitude is the closest of all 128 steps
        int chord = 0;
        while (chord < 7 && magnitude > EXPANSION[chord << 4 | 0x0F])
            chord++;
        int bestStep = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int step = 0; step < 16; step++)
        {
            final int distance = Math.abs (EXPANSION[chord << 4 | step] - magnitude);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestStep = step;
            }
        }
        return (isNegative ? 0x80 : 0x00) | chord << 4 | bestStep;
    }


    /**
     * Build the transfer function of the DAC for all 256 byte values.
     *
     * @return The expansion, scaled to the 16 bit range
     */
    private static short [] createExpansionTable ()
    {
        final short [] table = new short [256];
        for (int value = 0; value < 256; value++)
        {
            final int chord = value >> 4 & 0x07;
            final int step = value & 0x0F;
            final int magnitude = (((step << 1) + 33) << chord) - 33;
            final int scaled = magnitude * SCALE;
            table[value] = (short) ((value & 0x80) != 0 ? -scaled : scaled);
        }
        return table;
    }
}
