// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;


/**
 * Helper class for splitting sample zones by different criteria.
 *
 * @author Jürgen Moßgraber
 */
public class LayerSplitter
{
    /**
     * Constructor.
     */
    protected LayerSplitter ()
    {
        // Intentionally empty
    }


    /**
     * Splits the given groups into non-overlapping layers so that no two zones in the same layer
     * cover the same key/velocity range. Original IGroup objects are reused when possible; new
     * DefaultGroup instances are created only when a group must be split across layers.
     *
     * @param groups The groups of the multi-sample
     * @return A list of layers, each being a list of groups with non-overlapping zones
     */
    public static List<List<IGroup>> splitIntoNonOverlappingLayers (final List<IGroup> groups)
    {
        final List<boolean [] []> layerMatrices = new ArrayList<> ();
        final Map<ISampleZone, Integer> zoneLayerMap = new IdentityHashMap<> ();

        // Step 1: Assign every zone to the first layer it fits into
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                int targetLayer = -1;
                for (int l = 0; l < layerMatrices.size (); l++)
                    if (fitsInLayer (zone, layerMatrices.get (l)))
                    {
                        targetLayer = l;
                        break;
                    }

                // No existing layer fits -> open a new one
                if (targetLayer == -1)
                {
                    targetLayer = layerMatrices.size ();
                    layerMatrices.add (new boolean [128] [128]);
                }

                zoneLayerMap.put (zone, Integer.valueOf (targetLayer));
                markLayer (zone, layerMatrices.get (targetLayer));
            }

        // Step 2: Build result structure
        final List<List<IGroup>> result = new ArrayList<> (layerMatrices.size ());
        for (int i = 0; i < layerMatrices.size (); i++)
            result.add (new ArrayList<> ());

        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = group.getSampleZones ();

            // Collect the distinct layers used by this group's zones (insertion-ordered)
            final Set<Integer> usedLayers = new LinkedHashSet<> ();
            for (final ISampleZone zone: zones)
                usedLayers.add (zoneLayerMap.get (zone));

            if (usedLayers.size () == 1)
                // All zones stay together -> reuse the original IGroup unchanged
                result.get (usedLayers.iterator ().next ().intValue ()).add (group);
            else
                // Zones spread across layers -> create one new group per layer
                for (final int layer: usedLayers)
                {
                    final IGroup newGroup = new DefaultGroup (group.getName ());
                    for (final ISampleZone zone: zones)
                        if (zoneLayerMap.get (zone).intValue () == layer)
                            newGroup.addSampleZone (zone);
                    result.get (layer).add (newGroup);
                }
        }

        return result;
    }


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
            Collections.sort (velocityLayer, (final ISampleZone sz1, final ISampleZone sz2) -> Integer.compare (sz1.getKeyLow (), sz2.getKeyLow ()));
        return velocityLayers;
    }


    private static String createKey (final ISampleZone sampleZone)
    {
        final int keyLow = AbstractCreator.limitToDefault (sampleZone.getVelocityLow (), 0);
        final int keyHigh = AbstractCreator.limitToDefault (sampleZone.getVelocityHigh (), 127);
        return keyLow + "-" + keyHigh;
    }


    private static boolean fitsInLayer (final ISampleZone zone, final boolean [] [] matrix)
    {
        for (int k = zone.getKeyLow (); k <= zone.getKeyHigh (); k++)
            for (int v = zone.getVelocityLow (); v <= zone.getVelocityHigh (); v++)
                if (matrix[k][v])
                    return false;
        return true;
    }


    private static void markLayer (final ISampleZone zone, final boolean [] [] matrix)
    {
        for (int k = zone.getKeyLow (); k <= zone.getKeyHigh (); k++)
            for (int v = zone.getVelocityLow (); v <= zone.getVelocityHigh (); v++)
                matrix[k][v] = true;
    }
}
