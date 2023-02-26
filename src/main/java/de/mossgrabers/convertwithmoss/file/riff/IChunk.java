// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.riff;

/**
 * Interface to a chunk.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IChunk
{
    /**
     * Get the RIFF ID.
     *
     * @return The id
     */
    int getId ();


    /**
     * Gets the data.
     *
     * @return The data array. The array will not be cloned for performance reasons and is expected
     *         to be modified from wrapper classes!
     */
    byte [] getData ();
}
