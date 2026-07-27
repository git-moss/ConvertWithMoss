// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.Set;


/**
 * All SF2 generators (= a synthesizer parameter).
 *
 * @author Jürgen Moßgraber
 */
public class Generator
{
    /** The ID of the start offset. */
    public static final int     START_ADDRS_OFFSET       = 0;
    /** The ID of the end offset. */
    public static final int     END_ADDRS_OFFSET         = 1;
    /** The ID of the start loop offset. */
    public static final int     START_LOOP_ADDRS_OFFSET  = 2;
    /** The ID of the end loop offset. */
    public static final int     END_LOOP_ADDRS_OFFSET    = 3;

    /** The ID of the vibrato low frequency oscillator to pitch generator. */
    public static final int     VIB_LFO_TO_PITCH         = 6;

    /** The ID of the modulation envelope to pitch generator. */
    public static final int     MOD_ENV_TO_PITCH         = 7;

    /** The ID of the initial filter cutoff generator. */
    public static final int     INITIAL_FILTER_CUTOFF    = 8;
    /** The ID of the initial filter resonance generator. */
    public static final int     INITIAL_FILTER_RESONANCE = 9;

    /** The ID of the modulation envelope to filter cutoff generator. */
    public static final int     MOD_ENV_TO_FILTER_CUTOFF = 11;

    /** The ID of the panning generator. */
    public static final int     PANNING                  = 17;

    /** The ID of the vibrato low frequency oscillator delay generator (in time-cents). */
    public static final int     DELAY_VIB_LFO            = 23;
    /** The ID of the vibrato low frequency oscillator frequency generator (in absolute cents). */
    public static final int     FREQ_VIB_LFO             = 24;

    /** The ID of the modulation envelope delay generator. */
    public static final int     MOD_ENV_DELAY            = 25;
    /** The ID of the modulation envelope attack generator. */
    public static final int     MOD_ENV_ATTACK           = 26;
    /** The ID of the modulation envelope hold generator. */
    public static final int     MOD_ENV_HOLD             = 27;
    /** The ID of the modulation envelope decay generator. */
    public static final int     MOD_ENV_DECAY            = 28;
    /** The ID of the modulation envelope sustain generator. */
    public static final int     MOD_ENV_SUSTAIN          = 29;
    /** The ID of the modulation envelope release generator. */
    public static final int     MOD_ENV_RELEASE          = 30;
    /** The ID of the key number to modulation envelope hold generator. */
    public static final int     KEYNUM_TO_MOD_ENV_HOLD   = 31;
    /** The ID of the key number to modulation envelope decay generator. */
    public static final int     KEYNUM_TO_MOD_ENV_DECAY  = 32;

    /** The ID of the volume envelope delay generator. */
    public static final int     VOL_ENV_DELAY            = 33;
    /** The ID of the volume envelope attack generator. */
    public static final int     VOL_ENV_ATTACK           = 34;
    /** The ID of the volume envelope hold generator. */
    public static final int     VOL_ENV_HOLD             = 35;
    /** The ID of the volume envelope decay generator. */
    public static final int     VOL_ENV_DECAY            = 36;
    /** The ID of the volume envelope sustain generator. */
    public static final int     VOL_ENV_SUSTAIN          = 37;
    /** The ID of the volume envelope release generator. */
    public static final int     VOL_ENV_RELEASE          = 38;
    /** The ID of the key number to volume envelope hold generator. */
    public static final int     KEYNUM_TO_VOL_ENV_HOLD   = 39;
    /** The ID of the key number to volume envelope decay generator. */
    public static final int     KEYNUM_TO_VOL_ENV_DECAY  = 40;

    /**
     * The maximum absolute value of the key number to envelope hold and decay generators. The
     * generators are stored in time-cents per key number and their specified range is
     * [-1200..1200].
     */
    public static final int     MAX_KEYNUM_TO_ENV        = 1200;

    /** The ID of the instrument generator. */
    public static final int     INSTRUMENT               = 41;
    /** The ID of the key range generator. */
    public static final int     KEY_RANGE                = 43;
    /** The ID of the velocity range generator. */
    public static final int     VELOCITY_RANGE           = 44;
    /** The ID of the initial gain attenuation generator. */
    public static final int     INITIAL_ATTENUATION      = 48;
    /** The ID of the coarse tune generator. */
    public static final int     COARSE_TUNE              = 51;
    /** The ID of the fine tune generator. */
    public static final int     FINE_TUNE                = 52;
    /** The ID of the sample ID generator. */
    public static final int     SAMPLE_ID                = 53;
    /** The ID of the sample modes generator. */
    public static final int     SAMPLE_MODES             = 54;
    /** The ID of the scale tuning generator. */
    public static final int     SCALE_TUNE               = 56;
    /** The ID of the exclusive class generator. */
    public static final int     EXCLUSIVE_CLASS          = 57;
    /** The ID of the overriding root key generator. */
    public static final int     OVERRIDING_ROOT_KEY      = 58;

    /** The generator default values. */
    private static final int [] DEFAULT_VALUES           = new int [61];
    static
    {
        DEFAULT_VALUES[0] = 0;
        DEFAULT_VALUES[1] = 0;
        DEFAULT_VALUES[2] = 0;
        DEFAULT_VALUES[3] = 0;
        DEFAULT_VALUES[4] = 0;
        DEFAULT_VALUES[5] = 0;
        DEFAULT_VALUES[6] = 0;
        DEFAULT_VALUES[7] = 0;
        DEFAULT_VALUES[8] = 13500;
        DEFAULT_VALUES[9] = 0;
        DEFAULT_VALUES[10] = 0;
        DEFAULT_VALUES[11] = 0;
        DEFAULT_VALUES[12] = 0;
        DEFAULT_VALUES[13] = 0;
        DEFAULT_VALUES[14] = 0;
        DEFAULT_VALUES[15] = 0;
        DEFAULT_VALUES[16] = 0;
        DEFAULT_VALUES[17] = 0;
        DEFAULT_VALUES[18] = 0;
        DEFAULT_VALUES[19] = 0;
        DEFAULT_VALUES[20] = 0;
        DEFAULT_VALUES[21] = -12000;
        DEFAULT_VALUES[22] = 0;
        DEFAULT_VALUES[23] = -12000;
        DEFAULT_VALUES[24] = 0;
        DEFAULT_VALUES[25] = -12000;
        DEFAULT_VALUES[26] = -12000;
        DEFAULT_VALUES[27] = -12000;
        DEFAULT_VALUES[28] = -12000;
        DEFAULT_VALUES[29] = 0;
        DEFAULT_VALUES[30] = -12000;
        DEFAULT_VALUES[31] = 0;
        DEFAULT_VALUES[32] = 0;
        DEFAULT_VALUES[33] = -12000;
        DEFAULT_VALUES[34] = -12000;
        DEFAULT_VALUES[35] = -12000;
        DEFAULT_VALUES[36] = -12000;
        DEFAULT_VALUES[37] = 0;
        DEFAULT_VALUES[38] = -12000;
        DEFAULT_VALUES[39] = 0;
        DEFAULT_VALUES[40] = 0;
        DEFAULT_VALUES[41] = 0;
        DEFAULT_VALUES[42] = 0;
        DEFAULT_VALUES[43] = 0;
        DEFAULT_VALUES[44] = 0;
        DEFAULT_VALUES[45] = 0;
        DEFAULT_VALUES[46] = -1;
        DEFAULT_VALUES[47] = -1;
        DEFAULT_VALUES[48] = 0;
        DEFAULT_VALUES[49] = 0;
        DEFAULT_VALUES[50] = 0;
        DEFAULT_VALUES[51] = 0;
        DEFAULT_VALUES[52] = 0;
        DEFAULT_VALUES[53] = 0;
        DEFAULT_VALUES[54] = 0;
        DEFAULT_VALUES[55] = 0;
        DEFAULT_VALUES[56] = 100;
        DEFAULT_VALUES[57] = 0;
        DEFAULT_VALUES[58] = -1;
        DEFAULT_VALUES[59] = 0;
        DEFAULT_VALUES[60] = 0;
    }

    private static final Set<Integer> ONLY_INSTRUMENT = Set.of (Integer.valueOf (0), Integer.valueOf (1), Integer.valueOf (2), Integer.valueOf (3), Integer.valueOf (4), Integer.valueOf (12), Integer.valueOf (45), Integer.valueOf (46), Integer.valueOf (47), Integer.valueOf (50), Integer.valueOf (54), Integer.valueOf (57), Integer.valueOf (58));


    /**
     * Helper class.
     */
    private Generator ()
    {
        // Intentionally empty
    }


    /**
     * Check if the generator can only be applied on the instrument level (not on a preset level).
     *
     * @param generatorID The ID of the generator
     * @return True if can only applied to instruments
     */
    public static boolean isOnlyInstrument (final int generatorID)
    {
        return ONLY_INSTRUMENT.contains (Integer.valueOf (generatorID));
    }


    /**
     * Get the default value.
     *
     * @param generatorID The ID of the generator
     * @return The default value
     */
    public static Integer getDefaultValue (final Integer generatorID)
    {
        return Integer.valueOf (DEFAULT_VALUES[generatorID.intValue ()]);
    }
}
