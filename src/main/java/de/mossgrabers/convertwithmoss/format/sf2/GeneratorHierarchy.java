// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.file.sf2.Generator;
import de.mossgrabers.tools.Pair;


/**
 * Get generator values in inverse hierarchical order: instrument zone -> instrument zone global ->
 * preset zone -> preset zone global.
 *
 * @author Jürgen Moßgraber
 */
public class GeneratorHierarchy
{
    private static final Pair<Integer, Integer> DEFAULT_RANGE        = new Pair<> (Integer.valueOf (0), Integer.valueOf (127));

    private final Map<Integer, Integer>         instrumentZone       = new HashMap<> ();
    private final Map<Integer, Integer>         instrumentZoneGlobal = new HashMap<> ();
    private final Map<Integer, Integer>         presetZone           = new HashMap<> ();
    private final Map<Integer, Integer>         presetZoneGlobal     = new HashMap<> ();
    private final Set<Integer>                  processedGenerators  = new HashSet<> ();
    private final Set<Integer>                  allGenerators        = new HashSet<> ();


    /**
     * Constructor.
     */
    public GeneratorHierarchy ()
    {
        this.processedGenerators.add (Integer.valueOf (Generator.INSTRUMENT));
        this.processedGenerators.add (Integer.valueOf (Generator.SAMPLE_ID));
    }


    /**
     * Set the instrument zone generators.
     *
     * @param instrumentZone The generators
     */
    public void setInstrumentZoneGenerators (final Map<Integer, Integer> instrumentZone)
    {
        this.allGenerators.addAll (instrumentZone.keySet ());
        this.instrumentZone.clear ();
        this.instrumentZone.putAll (instrumentZone);
    }


    /**
     * Set the instrument zone global generators.
     *
     * @param instrumentZoneGlobal The generators
     */
    public void setInstrumentZoneGlobalGenerators (final Map<Integer, Integer> instrumentZoneGlobal)
    {
        this.allGenerators.addAll (instrumentZoneGlobal.keySet ());
        this.instrumentZoneGlobal.clear ();
        this.instrumentZoneGlobal.putAll (instrumentZoneGlobal);
    }


    /**
     * Set the preset zone generators.
     *
     * @param presetZone The generators
     */
    public void setPresetZoneGenerators (final Map<Integer, Integer> presetZone)
    {
        this.allGenerators.addAll (presetZone.keySet ());
        this.presetZone.clear ();
        this.presetZone.putAll (presetZone);
    }


    /**
     * Set the preset zone global generators.
     *
     * @param presetZoneGlobal The generators
     */
    public void setPresetZoneGlobalGenerators (final Map<Integer, Integer> presetZoneGlobal)
    {
        this.allGenerators.addAll (presetZoneGlobal.keySet ());
        this.presetZoneGlobal.clear ();
        this.presetZoneGlobal.putAll (presetZoneGlobal);
    }


    /**
     * Get a generator in inverse hierarchical order: instrument zone -> instrument zone global ->
     * preset zone -> preset zone global.
     *
     * @param generator The ID of the generator
     * @return The value of the generator or null if not present
     */
    public Integer getUnsignedValue (final int generator)
    {
        final Integer key = this.getKey (generator);

        Integer value = this.instrumentZone.get (key);
        if (value == null)
        {
            value = this.instrumentZoneGlobal.get (key);
            if (value == null)
                return Generator.getDefaultValue (key);
        }

        if (!Generator.isOnlyInstrument (generator))
        {
            Integer offset = this.presetZone.get (key);
            if (offset == null)
                offset = this.presetZoneGlobal.get (key);
            if (offset != null)
                return Integer.valueOf (value.intValue () + offset.intValue ());
        }

        return value;
    }


    /**
     * Get a generator in inverse hierarchical order: instrument zone -> instrument zone global ->
     * preset zone -> preset zone global.
     *
     * @param generator The ID of the generator
     * @return The value of the generator or null if not present
     */
    public Integer getSignedValue (final int generator)
    {
        final Integer key = this.getKey (generator);

        Integer value = this.instrumentZone.get (key);
        if (value == null)
        {
            value = this.instrumentZoneGlobal.get (key);
            if (value == null)
                return Generator.getDefaultValue (key);
        }

        int v = MathUtils.fromSignedComplement (value.intValue ());

        if (!Generator.isOnlyInstrument (generator))
        {
            Integer offset = this.presetZone.get (key);
            if (offset == null)
                offset = this.presetZoneGlobal.get (key);
            if (offset != null)
                v = v + MathUtils.fromSignedComplement (offset.intValue ());
        }

        return Integer.valueOf (v);
    }


    /**
     * Get a key or velocity range. All other generators throw an IllegalArgumentException.
     *
     * @param generator The generator value to get
     * @return The range value
     */
    public Pair<Integer, Integer> getRangeValue (final int generator)
    {
        if (generator != Generator.KEY_RANGE && generator != Generator.VELOCITY_RANGE)
            throw new IllegalArgumentException ();

        final Integer key = this.getKey (generator);

        // Get ranges on preset and hierarchy level
        Integer presetRangeValue = this.presetZone.get (key);
        if (presetRangeValue == null)
            presetRangeValue = this.presetZoneGlobal.get (key);
        Integer instrumentRangeValue = this.instrumentZone.get (key);
        if (instrumentRangeValue == null)
            instrumentRangeValue = this.instrumentZoneGlobal.get (key);

        final Pair<Integer, Integer> presetRange = getRange (presetRangeValue);
        final Pair<Integer, Integer> instrumentRange = getRange (instrumentRangeValue);

        final Integer low = Integer.valueOf (Math.max (presetRange.getKey ().intValue (), instrumentRange.getKey ().intValue ()));
        final Integer high = Integer.valueOf (Math.min (presetRange.getValue ().intValue (), instrumentRange.getValue ().intValue ()));
        return new Pair<> (low, high);
    }


    /**
     * Calculate the difference between the supported and present generators.
     *
     * @return The names of the unsupported generators which are present in the SF2 file
     */
    public Set<String> diffGenerators ()
    {
        final Set<String> unsupported = new TreeSet<> ();
        for (final Integer generator: this.allGenerators)
            if (!this.processedGenerators.contains (generator))
                unsupported.add (Generator.GENERATORS[generator.intValue ()]);
        return unsupported;
    }


    private static Pair<Integer, Integer> getRange (final Integer rangeValue)
    {
        if (rangeValue == null)
            return DEFAULT_RANGE;
        final int r = rangeValue.intValue ();
        return new Pair<> (Integer.valueOf (r & 0xFF), Integer.valueOf (r >> 8));
    }


    private Integer getKey (final int generator)
    {
        final Integer key = Integer.valueOf (generator);
        this.processedGenerators.add (key);
        return key;
    }
}
