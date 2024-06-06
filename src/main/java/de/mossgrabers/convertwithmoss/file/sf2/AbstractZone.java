// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.MathUtils;


/**
 * Base class for SF2 zones.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractZone
{
    /** Index to the first generator of the zone in the PGEN list. */
    protected int                   firstGenerator;
    protected int                   numberOfGenerators;

    /** Index to the first modulator of the zone in the PMOD list. */
    protected int                   firstModulator;
    protected int                   numberOfModulators;

    /** If true, this is a global zone which applies to the whole preset. */
    protected boolean               isGlobal       = false;

    /** The generators assigned to the zone. */
    protected Map<Integer, Integer> generators     = new HashMap<> ();
    protected List<Sf2Modulator>    modulators     = new ArrayList<> ();

    /** The order of generators in a SF2 file has some restrictions and therefore is important! */
    protected List<Integer>         generatorOrder = new ArrayList<> ();


    /**
     * Default Constructor.
     */
    protected AbstractZone ()
    {
        // Intentionally empty
    }


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
     * Get all generators of the zone.
     *
     * @return The generators
     */
    public Map<Integer, Integer> getGenerators ()
    {
        return this.generators;
    }


    /**
     * Get the order of all generators of the zone.
     *
     * @return The generator order
     */
    public List<Integer> getGeneratorOrder ()
    {
        return this.generatorOrder;
    }


    /**
     * Add a signed generator to the zone. Converts the integer to the generator signed integer
     * format.
     *
     * @param generator The ID of the generator
     * @param value The value
     */
    public void addSignedGenerator (final int generator, final int value)
    {
        this.addGenerator (generator, MathUtils.toSignedComplement (value));
    }


    /**
     * Add a generator to the zone.
     *
     * @param generator The ID of the generator
     * @param value The value
     */
    public void addGenerator (final int generator, final int value)
    {
        final Integer generatorID = Integer.valueOf (generator);
        if (this.generators.containsKey (generatorID))
            return;
        this.generators.put (generatorID, Integer.valueOf (value));
        this.generatorOrder.add (generatorID);
    }


    /**
     * Add a generator with a combined value to the zone.
     *
     * @param generator The ID of the generator
     * @param lowValue The low byte of the value
     * @param highValue The high byte of the value
     */
    public void addGenerator (final int generator, final int lowValue, final int highValue)
    {
        this.addGenerator (generator, highValue << 8 | lowValue & 0xFF);
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
     * @return The result
     */
    public List<Sf2Modulator> getModulators (final Integer modulatorID)
    {
        final List<Sf2Modulator> result = new ArrayList<> ();
        for (final Sf2Modulator modulator: this.modulators)
            if (modulator.getControllerSource () == modulatorID.intValue ())
                result.add (modulator);
        return result;
    }


    /**
     * Get all modulators.
     *
     * @return The modulators
     */
    public List<Sf2Modulator> getModulators ()
    {
        return this.modulators;
    }


    /**
     * Update the counts of generators and modulators.
     *
     * @param firstGenerator The index of the first generator of this zone
     * @param firstModulator The index of the first modulator of this zone
     */
    public void updateCounts (final int firstGenerator, final int firstModulator)
    {
        this.firstGenerator = firstGenerator;
        this.firstModulator = firstModulator;
        this.numberOfGenerators = this.generators.size ();
        this.numberOfModulators = this.modulators.size ();
    }
}
