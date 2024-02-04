// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.enumeration;

/**
 * Different types of triggers.
 *
 * @author Jürgen Moßgraber
 */
public enum TriggerType
{
    /** Layer will play on attack. */
    ATTACK,
    /** Layer will play on release. */
    RELEASE,
    /**
     * Layer will play on note-on, but if there’s no other note going on (commonly used for or first
     * note in a legato phrase).
     */
    FIRST,
    /**
     * Layer will play on note-on, but only if there’s a note going on (notes after first note in a
     * legato phrase).
     */
    LEGATO
}
