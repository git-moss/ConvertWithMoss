// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.tools.Pair;


/**
 * Base class for a group of SF2 zones.
 *
 * @param <T> The type of the zones
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractGroupedZones<T extends AbstractZone>
{
    protected String        name;

    /** Pointer into PBAG/IBAG list. */
    protected int           firstZoneIndex = 0;

    protected final List<T> zones          = new ArrayList<> ();


    /**
     * Default Constructor.
     */
    protected AbstractGroupedZones ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param name The name of the grouped zones
     */
    protected AbstractGroupedZones (final String name)
    {
        this.name = name;
    }


    /**
     * Get the name of the grouped zones.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the instrument.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Set the index of the first zone of the grouped zones.
     *
     * @param firstZoneIndex The index
     */
    public void setFirstZoneIndex (final int firstZoneIndex)
    {
        this.firstZoneIndex = firstZoneIndex;
    }


    /**
     * Get the index in the PBAG/IBAG structure (the first zone of the preset/instrument).
     *
     * @return The index
     */
    public int getFirstZoneIndex ()
    {
        return this.firstZoneIndex;
    }


    /**
     * Adds a zone to the grouped zones.
     *
     * @param zone The zone to add
     */
    public void addZone (final T zone)
    {
        this.zones.add (zone);
    }


    /**
     * Get the zone at the given index.
     *
     * @param zoneIndex The index of the zone
     * @return The zone
     */
    public T getZone (final int zoneIndex)
    {
        return this.zones.get (zoneIndex);
    }


    /**
     * Get the number of zones.
     *
     * @return The number of zones
     */
    public int getZoneCount ()
    {
        return this.zones.size ();
    }


    /**
     * Update all generator/modulator indices and counts of the zones.
     *
     * @param counts Counter for the first generator and modulator
     */
    public void updateCounts (final Pair<Integer, Integer> counts)
    {
        int firstGenerator = counts.getKey ().intValue ();
        int firstModulator = counts.getValue ().intValue ();
        for (final T zone: this.zones)
        {
            zone.updateCounts (firstGenerator, firstModulator);
            firstGenerator += zone.getNumberOfGenerators ();
            firstModulator += zone.getNumberOfModulators ();
        }
        counts.set (Integer.valueOf (firstGenerator), Integer.valueOf (firstModulator));
    }
}
