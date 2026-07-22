// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * Logic to apply how to play layers.
 *
 * @author Jürgen Moßgraber
 */
public enum PlayLogic
{
    /** The layer does always play. */
    ALWAYS,
    /** Layers play one after the other in sequential order. */
    ROUND_ROBIN,
    /**
     * One of the layers is chosen randomly. Formats which cannot express a random selection should
     * fall back to {@link #ROUND_ROBIN} and not to {@link #ALWAYS}, since playing all layers at
     * once is musically much further away from the intended result than cycling through them.
     */
    RANDOM
}
