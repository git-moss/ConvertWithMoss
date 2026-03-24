// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

/**
 * Executes a multi-sample format conversion.
 *
 * @author Jürgen Moßgraber
 */
@FunctionalInterface
public interface IExecute
{
    /**
     * Called if the conversion should be executed.
     */
    void execute ();
}
