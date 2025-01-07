// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai;

/**
 * Some constants for the MPC keygroup format.
 *
 * @author Jürgen Moßgraber
 */
public class MPCKeygroupConstants
{
    static final String DOUBLE_ZERO          = "0.000000";
    static final String FILE_VERSION         = "2.1";
    static final String APP_VERSION          = "v2.11.6.6";

    static final double MINUS_12_DB          = 0.353000;
    static final double PLUS_6_DB            = 1.0;
    static final double VALUE_RANGE          = PLUS_6_DB - MINUS_12_DB;

    static final double MIN_ENV_TIME_SECONDS = 0.001;
    static final double MAX_ENV_TIME_SECONDS = 100.0;

    // Logarithmic values!
    static final double DEFAULT_ATTACK_TIME  = 0.0;
    static final double DEFAULT_HOLD_TIME    = 0.0;
    static final double DEFAULT_DECAY_TIME   = 0.0022861319686154;
    static final double DEFAULT_RELEASE_TIME = 0.0;


    /**
     * Private constructor for utility class.
     */
    private MPCKeygroupConstants ()
    {
        // Intentionally empty
    }
}
