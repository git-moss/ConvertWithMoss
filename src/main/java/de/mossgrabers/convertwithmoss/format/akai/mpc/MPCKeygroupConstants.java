// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

/**
 * Some constants for the MPC key-group format.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupConstants
{
    /** A zero as a double as a string. */
    public static final String DOUBLE_ZERO          = "0.000000";
    /** XPM file version. */
    public static final String FILE_VERSION         = "2.1";
    /** Application version. */
    public static final String APP_VERSION          = "v2.11.6.6";

    /** Value for -12dB. */
    public static final double MINUS_12_DB          = 0.353000;
    /** Value for +6dB. */
    public static final double PLUS_6_DB            = 1.0;
    /** The value range from -12dB to +6dB. */
    public static final double VALUE_RANGE          = PLUS_6_DB - MINUS_12_DB;

    /** The minimum time for an envelope time value. */
    public static final double MIN_ENV_TIME_SECONDS = 0.001;
    /** The maximum time for an envelope time value. */
    public static final double MAX_ENV_TIME_SECONDS = 100.0;

    // Logarithmic values!
    /** The default attack time. */
    public static final double DEFAULT_ATTACK_TIME  = 0.0;
    /** The default hold time. */
    public static final double DEFAULT_HOLD_TIME    = 0.0;
    /** The default decay time. */
    public static final double DEFAULT_DECAY_TIME   = 0.0022861319686154;
    /** The default release time. */
    public static final double DEFAULT_RELEASE_TIME = 0.0;


    /**
     * Private constructor for utility class.
     */
    private MPCKeygroupConstants ()
    {
        // Intentionally empty
    }
}
