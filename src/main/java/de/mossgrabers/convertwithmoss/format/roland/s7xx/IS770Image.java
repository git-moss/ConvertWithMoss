// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.util.List;


/**
 * Interface for Roland S-770 images.
 *
 * @author Jürgen Moßgraber
 */
interface IS770Image
{
    /**
     * Get the header.
     *
     * @return The header
     */
    S770Header getHeader ();


    /**
     * Get the performances.
     *
     * @return The performances
     */
    List<S770Performance> getPerformances ();


    /**
     * Get the patches.
     *
     * @return The patches
     */
    List<S770Patch> getPatches ();


    /**
     * Get the partials.
     *
     * @return The patches
     */
    List<S770Partial> getPartials ();


    /**
     * Get the samples.
     *
     * @return The samples
     */
    List<S770Sample> getSamples ();
}
