// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.sf2;

import java.util.HashMap;
import java.util.Map;


/**
 * Base class for SF2 zones.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractZone
{
    /** Index to the first generator of the zone in the PGEN list. */
    protected final int             firstGenerator;
    protected final int             numberOfGenerators;

    /** Index to the first modulator of the zone in the PMOD list. */
    protected final int             firstModulator;

    /** If true, this is a global zone which applies to the whole preset. */
    protected boolean               isGlobal   = false;

    /** The generators assigned to the zone. */
    protected Map<Integer, Integer> generators = new HashMap<> ();


    /**
     * Constructor.
     *
     * @param firstGenerator Index to the first generator of the zone in the PGEN list
     * @param numberOfGenerators The number of generators in this zone
     * @param firstModulator Index to the first modulator of the zone in the PMOD list
     */
    protected AbstractZone (final int firstGenerator, final int numberOfGenerators, final int firstModulator)
    {
        this.firstGenerator = firstGenerator;
        this.numberOfGenerators = numberOfGenerators;
        this.firstModulator = firstModulator;
    }


    /**
     * If true, this is a global zone which applies to the whole preset.
     *
     * @return True if global
     */
    public boolean isGlobal ()
    {
        return this.isGlobal;
    }


    /**
     * Set if the zone is a global zone.
     *
     * @param isGlobal If true, this is a global zone which applies to the whole preset
     */
    public void setGlobal (final boolean isGlobal)
    {
        this.isGlobal = isGlobal;
    }


    /**
     * Get the index to the first generator of the zone in the PGEN list.
     *
     * @return The index
     */
    public int getFirstGenerator ()
    {
        return this.firstGenerator;
    }


    /**
     * Get the number of generators in this zone.
     *
     * @return The number of generators in this zone
     */
    public int getNumberOfGenerators ()
    {
        return this.numberOfGenerators;
    }


    /**
     * Get the index to the first modulator of the zone in the PMOD list.
     *
     * @return The index
     */
    public int getFirstModulator ()
    {
        return this.firstModulator;
    }


    /**
     * Get all generators of the zone.
     *
     * @return The generators
     */
    public Map<Integer, Integer> getGenerators ()
    {
        return this.generators;
    }


    /**
     * Add a generator to the zone.
     *
     * @param generator The ID of the generator
     * @param value The value
     */
    public void addGenerator (final int generator, final int value)
    {
        this.generators.put (Integer.valueOf (generator), Integer.valueOf (value));
    }


    /**
     * Get a generator from the zone.
     *
     * @param generator The ID of the generator
     * @return The value of the generator or null if not present
     */
    public Integer getGeneratorValue (final int generator)
    {
        return this.generators.get (Integer.valueOf (generator));
    }


    /**
     * Check if a generator is present.
     *
     * @param generatorID The ID of the generator to check for
     * @return True if present
     */
    public boolean hasGenerator (final int generatorID)
    {
        return this.generators.keySet ().contains (Integer.valueOf (generatorID));
    }
}
