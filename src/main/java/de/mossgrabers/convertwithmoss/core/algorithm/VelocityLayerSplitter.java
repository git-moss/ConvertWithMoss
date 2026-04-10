// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Helper class for splitting sample zones by their velocity values.
 *
 * @author Jürgen Moßgraber
 */
public class VelocityLayerSplitter
{
    /**
     * Splits the given sample zones by their velocity values.
     * 
     * @param sampleZones The sample zones to split
     * @return The key contains the lower velocity range. The lists are ordered by the lower
     *         velocity and the sample zones by their lower key.
     */
    public static Map<String, List<ISampleZone>> splitVelocityLayers (final List<ISampleZone> sampleZones)
    {
        final Map<String, List<ISampleZone>> velocityLayers = new TreeMap<> ();
        for (final ISampleZone sampleZone: sampleZones)
            velocityLayers.computeIfAbsent (createKey (sampleZone), _ -> new ArrayList<> ()).add (sampleZone);
        for (final List<ISampleZone> velocityLayer: velocityLayers.values ())
        {
            Collections.sort (velocityLayer, (ISampleZone sz1, ISampleZone sz2) -> {
                return Integer.compare (sz1.getKeyLow (), sz2.getKeyLow ());
            });
        }
        return velocityLayers;
    }


    private static String createKey (final ISampleZone sampleZone)
    {
        final int keyLow = AbstractCreator.limitToDefault (sampleZone.getVelocityLow (), 0);
        final int keyHigh = AbstractCreator.limitToDefault (sampleZone.getVelocityHigh (), 127);
        return keyLow + "-" + keyHigh;
    }
}
