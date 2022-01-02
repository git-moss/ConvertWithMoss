// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.sf2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


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
    protected final int             numberOfModulators;

    /** If true, this is a global zone which applies to the whole preset. */
    protected boolean               isGlobal   = false;

    /** The generators assigned to the zone. */
    protected Map<Integer, Integer> generators = new HashMap<> ();
    protected List<Sf2Modulator>    modulators = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param firstGenerator Index to the first generator of the zone in the PGEN list
     * @param numberOfGenerators The number of generators in this zone
     * @param firstModulator Index to the first modulator of the zone in the PMOD list
     * @param numberOfModulators The number of modulators of this zone
     */
    protected AbstractZone (final int firstGenerator, final int numberOfGenerators, final int firstModulator, final int numberOfModulators)
    {
        this.firstGenerator = firstGenerator;
        this.numberOfGenerators = numberOfGenerators;
        this.firstModulator = firstModulator;
        this.numberOfModulators = numberOfModulators;
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
     * Get the number of modulators in this zone.
     *
     * @return The number of modulators in this zone
     */
    public int getNumberOfModulators ()
    {
        return this.numberOfModulators;
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


    /**
     * Add a modulator to the zone.
     *
     * @param sourceModulator The ID of the source modulator
     * @param destinationGenerator The destination of the modulator
     * @param modAmount A signed value indicating the degree to which the source modulates the
     *            destination
     * @param amountSourceOperand Indicates the degree to which the source modulates the destination
     *            is to be controlled by the specified modulation source
     * @param transformOperand Indicates that a transform of the specified type will be applied to
     *            the modulation source before application to the modulator
     */
    public void addModulator (final int sourceModulator, final int destinationGenerator, final int modAmount, final int amountSourceOperand, final int transformOperand)
    {
        this.modulators.add (new Sf2Modulator (sourceModulator, destinationGenerator, modAmount, amountSourceOperand, transformOperand));
    }


    /**
     * Get a specific modulator if present.
     *
     * @param modulatorID The ID of the modulator to get
     * @return The optional result
     */
    public Optional<Sf2Modulator> getModulator (final Integer modulatorID)
    {
        for (final Sf2Modulator modulator: this.modulators)
        {
            if (modulator.getControllerSource () == modulatorID.intValue ())
                return Optional.of (modulator);
        }
        return Optional.empty ();
    }
}
