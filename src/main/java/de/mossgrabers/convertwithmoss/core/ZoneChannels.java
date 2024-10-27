// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Enumeration for different channel configurations of zones.
 *
 * @author Jürgen Moßgraber
 */
public enum ZoneChannels
{
    /** All zones reference mono samples. */
    MONO,
    /** All zones reference stereo samples. */
    STEREO,
    /** All zones reference mono samples which are either hard panned left or right. */
    SPLIT_STEREO,
    /** Mixed mono and stereo samples. */
    MIXED;


    /**
     * Checks for the channel setup across all zones of the given groups.
     *
     * @param groups The groups to check
     * @return The channel configuration
     * @throws IOException Could not detect the number of channels
     */
    public static ZoneChannels detectChannelConfiguration (final List<IGroup> groups) throws IOException
    {
        Boolean stereo = null;
        Boolean splitMono = null;

        for (final IGroup group: groups)
            for (final ISampleZone sampleZone: group.getSampleZones ())
            {
                final boolean isStereo = sampleZone.getSampleData ().getAudioMetadata ().getChannels () == 2;
                if (stereo == null)
                {
                    // First iteration, store if mono or stereo
                    stereo = Boolean.valueOf (isStereo);
                    // If it is already stereo, it cannot be split stereo
                    if (isStereo && splitMono == null)
                        splitMono = Boolean.FALSE;
                    continue;
                }

                // Mixed mono/stereo
                if (stereo.booleanValue () != isStereo)
                    return ZoneChannels.MIXED;

                if (isStereo)
                    continue;

                // Check for split stereo which needs to be hard panned left or right
                if (splitMono == null || splitMono.booleanValue ())
                {
                    final double panorama = sampleZone.getPanorama ();
                    splitMono = Boolean.valueOf (panorama <= -1 || panorama >= 1);
                }
            }

        if (stereo != null && stereo.booleanValue ())
            return ZoneChannels.STEREO;

        return splitMono != null && splitMono.booleanValue () ? ZoneChannels.SPLIT_STEREO : ZoneChannels.MONO;
    }
}
