// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.diskformat;

/**
 * Interface to different content on a generic Akai volume.
 *
 * @author Jürgen Moßgraber
 */
public interface IAkaiVolume
{
    /**
     * Returns true if the volume has content and is not empty.
     * 
     * @return True if not empty
     */
    boolean hasContent ();
}
