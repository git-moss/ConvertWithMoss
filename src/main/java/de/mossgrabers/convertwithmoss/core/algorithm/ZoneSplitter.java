// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * Helper class for splitting a list of ISampleZone objects into multiple lists such that zones
 * sharing identical key/velocity ranges are distributed across different result lists.
 *
 * @author Jürgen Moßgraber
 */
public class ZoneSplitter
{
    /**
     * Helper structure representing the identifying range signature of a zone.
     */
    private static final class ZoneKey
    {
        private final int keyLow;
        private final int keyHigh;
        private final int velocityLow;
        private final int velocityHigh;


        /**
         * Constructor.
         * 
         * @param zone The zone for which to create a key
         */
        ZoneKey (final ISampleZone zone)
        {
            this.keyLow = zone.getKeyLow ();
            this.keyHigh = zone.getKeyHigh ();
            this.velocityLow = zone.getVelocityLow ();
            this.velocityHigh = zone.getVelocityHigh ();
        }


        /** {@inheritDoc} */
        @Override
        public boolean equals (final Object obj)
        {
            if (this == obj)
                return true;

            if (!(obj instanceof ZoneKey))
                return false;

            final ZoneKey other = (ZoneKey) obj;

            return this.keyLow == other.keyLow && this.keyHigh == other.keyHigh && this.velocityLow == other.velocityLow && this.velocityHigh == other.velocityHigh;
        }


        /** {@inheritDoc} */
        @Override
        public int hashCode ()
        {
            return Objects.hash (Integer.valueOf (this.keyLow), Integer.valueOf (this.keyHigh), Integer.valueOf (this.velocityLow), Integer.valueOf (this.velocityHigh));
        }
    }


    /**
     * This implementation preserves the original ordering of the input list globally. Ordering is
     * preserved relative to first appearance in the input list. No identical range zones inside the
     * same output list - Stable global ordering - Minimal number of output lists - Deterministic
     * output.
     *
     * Algorithm: Iterate through the input list once: For each zone: place it into the first result
     * list that does not already contain a zone with the same range signature. If no such list
     * exists: create a new result list.
     *
     * @param sampleZones Input list of zones to distribute
     * @return List of conflict-free zone lists
     */
    public static List<List<ISampleZone>> splitZonesStableOrder (final List<ISampleZone> sampleZones)
    {
        // Tracks which range signatures already exist inside each result list.
        final List<Set<ZoneKey>> usedKeysPerLayer = new ArrayList<> ();

        // Result container: each list represents one conflict-free layer.
        final List<List<ISampleZone>> result = new ArrayList<> ();

        for (final ISampleZone zone: sampleZones)
        {
            final ZoneKey key = new ZoneKey (zone);

            boolean placed = false;

            // Try inserting into an existing layer first.
            for (int i = 0; i < result.size (); i++)
                if (!usedKeysPerLayer.get (i).contains (key))
                {
                    result.get (i).add (zone);
                    usedKeysPerLayer.get (i).add (key);
                    placed = true;
                    break;
                }

            // If no compatible layer exists, create a new one.
            if (placed)
                continue;

            final List<ISampleZone> newLayer = new ArrayList<> ();
            newLayer.add (zone);
            final Set<ZoneKey> newLayerKeys = new HashSet<> ();
            newLayerKeys.add (key);
            result.add (newLayer);
            usedKeysPerLayer.add (newLayerKeys);
        }

        return result;
    }
}
