// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.model.enumeration;

/**
 * Logic to apply how to play layers.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public enum PlayLogic
{
    /** The layer does always play. */
    ALWAYS,
    /** Layers play one after the other in sequential order. */
    ROUND_ROBIN
}
