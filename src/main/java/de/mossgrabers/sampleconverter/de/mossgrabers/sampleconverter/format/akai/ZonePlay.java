// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.akai;

import de.mossgrabers.sampleconverter.core.model.enumeration.PlayLogic;


/**
 * Mapping of play logic to zone play.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public enum ZonePlay
{
    /** Cycle through keygroup samples. */
    CYCLE,
    /** Use the velocity of the keygroup samples. */
    VELOCITY,
    /** Cycle randomly through the keygroup samples. */
    RANDOM;


    /**
     * Get the ID of the zone play.
     *
     * @return The ID
     */
    public String getID ()
    {
        return Integer.toString (this.ordinal ());
    }


    /**
     * Convert a play logic to a zone play.
     *
     * @param playLogic The play logic to convert
     * @return The zone play
     */
    public static ZonePlay from (final PlayLogic playLogic)
    {
        return playLogic == PlayLogic.ROUND_ROBIN ? CYCLE : VELOCITY;
    }


    /**
     * Convert a zone play to a play logic.
     *
     * @return The play logic
     */
    public PlayLogic to ()
    {
        return this == CYCLE ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS;
    }
}
