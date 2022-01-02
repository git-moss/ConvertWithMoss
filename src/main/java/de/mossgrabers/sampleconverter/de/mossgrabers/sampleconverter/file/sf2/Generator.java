// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.sf2;

import java.util.Set;


/**
 * All SF2 generators (= a synthesizer parameter).
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Generator
{
    /** The ID of the modulation envelope to pitch generator. */
    public static final int       MOD_ENV_TO_PITCH         = 7;

    /** The ID of the initial filter cutoff generator. */
    public static final int       INITIAL_FILTER_CUTOFF    = 8;
    /** The ID of the initial filter resonance generator. */
    public static final int       INITIAL_FILTER_RESONANCE = 9;

    /** The ID of the modulation envelope to filter cutoff generator. */
    public static final int       MOD_ENV_TO_FILTER_CUTOFF = 11;

    /** The ID of the panorama generator. */
    public static final int       PANORAMA                 = 17;

    /** The ID of the modulation envelope delay generator. */
    public static final int       MOD_ENV_DELAY            = 25;
    /** The ID of the modulation envelope attack generator. */
    public static final int       MOD_ENV_ATTACK           = 26;
    /** The ID of the modulation envelope hold generator. */
    public static final int       MOD_ENV_HOLD             = 27;
    /** The ID of the modulation envelope decay generator. */
    public static final int       MOD_ENV_DECAY            = 28;
    /** The ID of the modulation envelope sustain generator. */
    public static final int       MOD_ENV_SUSTAIN          = 29;
    /** The ID of the modulation envelope release generator. */
    public static final int       MOD_ENV_RELEASE          = 30;

    /** The ID of the volume envelope delay generator. */
    public static final int       VOL_ENV_DELAY            = 33;
    /** The ID of the volume envelope attack generator. */
    public static final int       VOL_ENV_ATTACK           = 34;
    /** The ID of the volume envelope hold generator. */
    public static final int       VOL_ENV_HOLD             = 35;
    /** The ID of the volume envelope decay generator. */
    public static final int       VOL_ENV_DECAY            = 36;
    /** The ID of the volume envelope sustain generator. */
    public static final int       VOL_ENV_SUSTAIN          = 37;
    /** The ID of the volume envelope release generator. */
    public static final int       VOL_ENV_RELEASE          = 38;

    /** The ID of the instrument generator. */
    public static final int       INSTRUMENT               = 41;
    /** The ID of the key range generator. */
    public static final int       KEY_RANGE                = 43;
    /** The ID of the velocity range generator. */
    public static final int       VELOCITY_RANGE           = 44;
    /** The ID of the initial gain attenuation generator. */
    public static final int       INITIAL_ATTENUATION      = 48;
    /** The ID of the coarse tune generator. */
    public static final int       COARSE_TUNE              = 51;
    /** The ID of the fine tune generator. */
    public static final int       FINE_TUNE                = 52;
    /** The ID of the sample ID generator. */
    public static final int       SAMPLE_ID                = 53;
    /** The ID of the sample modes generator. */
    public static final int       SAMPLE_MODES             = 54;
    /** The ID of the scale tuning generator. */
    public static final int       SCALE_TUNE               = 56;
    /** The ID of the overriding root key generator. */
    public static final int       OVERRIDING_ROOT_KEY      = 58;

    /** The generator names. */
    public static final String [] GENERATORS               = new String [61];
    private static final int []   DEFAULT_VALUES           = new int [61];
    static
    {
        GENERATORS[0] = "startAddrsOffset";
        GENERATORS[1] = "endAddrsOffset";
        GENERATORS[2] = "startloopAddrsOffset";
        GENERATORS[3] = "endloopAddrsOffset";
        GENERATORS[4] = "startAddrsCoarseOffset";
        GENERATORS[5] = "modLfoToPitch";
        GENERATORS[6] = "vibLfoToPitch";
        GENERATORS[7] = "modEnvToPitch";
        GENERATORS[8] = "initialFilterFc";
        GENERATORS[9] = "initialFilterQ";
        GENERATORS[10] = "modLfoToFilterFc";
        GENERATORS[11] = "modEnvToFilterFc";
        GENERATORS[12] = "endAddrsCoarseOffset";
        GENERATORS[13] = "modLfoToVolume";
        GENERATORS[14] = "unused1";
        GENERATORS[15] = "chorusEffectsSend";
        GENERATORS[16] = "reverbEffectsSend";
        GENERATORS[17] = "pan";
        GENERATORS[18] = "unused2";
        GENERATORS[19] = "unused3";
        GENERATORS[20] = "unused4";
        GENERATORS[21] = "delayModLFO";
        GENERATORS[22] = "freqModLFO";
        GENERATORS[23] = "delayVibLFO";
        GENERATORS[24] = "freqVibLFO";
        GENERATORS[25] = "delayModEnv";
        GENERATORS[26] = "attackModEnv";
        GENERATORS[27] = "holdModEnv";
        GENERATORS[28] = "decayModEnv";
        GENERATORS[29] = "sustainModEnv";
        GENERATORS[30] = "releaseModEnv";
        GENERATORS[31] = "keynumToModEnvHold";
        GENERATORS[32] = "keynumToModEnvDecay";
        GENERATORS[33] = "delayVolEnv";
        GENERATORS[34] = "attackVolEnv";
        GENERATORS[35] = "holdVolEnv";
        GENERATORS[36] = "decayVolEnv";
        GENERATORS[37] = "sustainVolEnv";
        GENERATORS[38] = "releaseVolEnv";
        GENERATORS[39] = "keynumToVolEnvHold";
        GENERATORS[40] = "keynumToVolEnvDecay";
        GENERATORS[41] = "instrument";
        GENERATORS[42] = "reserved1";
        GENERATORS[43] = "keyRange";
        GENERATORS[44] = "velRange";
        GENERATORS[45] = "startloopAddrsCoarseOffset";
        GENERATORS[46] = "keynum";
        GENERATORS[47] = "velocity";
        GENERATORS[48] = "initialAttenuation";
        GENERATORS[49] = "reserved2";
        GENERATORS[50] = "endloopAddrsCoarseOffset";
        GENERATORS[51] = "coarseTune";
        GENERATORS[52] = "fineTune";
        GENERATORS[53] = "sampleID";
        GENERATORS[54] = "sampleModes";
        GENERATORS[55] = "reserved3";
        GENERATORS[56] = "scaleTuning";
        GENERATORS[57] = "exclusiveClass";
        GENERATORS[58] = "overridingRootKey";
        GENERATORS[59] = "unused5";
        GENERATORS[60] = "endOper";

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
     * Get a description of a generator.
     *
     * @param generatorID The ID of the generator
     * @return The text for the ID
     */
    public static String getGeneratorName (final int generatorID)
    {
        if (generatorID >= GENERATORS.length || GENERATORS[generatorID] == null)
            return "Undefined";
        return GENERATORS[generatorID];
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
