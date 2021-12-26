// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.sf2;

import java.util.List;
import java.util.Map.Entry;


/**
 * A SF2 preset zone.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2PresetZone extends AbstractZone
{
    /** The instrument referenced by the zone. */
    private Sf2Instrument instrument;


    /**
     * Constructor.
     *
     * @param firstGenerator Index to the first generator of the zone in the PGEN list
     * @param numberOfGenerators The number of generators of this zone
     * @param firstModulator Index to the first modulator of the zone in the PMOD list
     * @param numberOfModulators The number of modulators of this zone
     */
    public Sf2PresetZone (final int firstGenerator, final int numberOfGenerators, final int firstModulator, final int numberOfModulators)
    {
        super (firstGenerator, numberOfGenerators, firstModulator, numberOfModulators);
    }


    /**
     * Get the instrument which is assigned to this zone.
     *
     * @return The instrument
     */
    public Sf2Instrument getInstrument ()
    {
        return this.instrument;
    }


    /**
     * Assign the instrument to this zone which is referenced in the generators list.
     *
     * @param instruments The available instruments
     */
    public void applyInstrument (final List<Sf2Instrument> instruments)
    {
        final Integer instrumentIndex = this.generators.get (Integer.valueOf (Generator.INSTRUMENT));
        if (instrumentIndex != null)
            this.instrument = instruments.get (instrumentIndex.intValue ());
    }


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();

        if (this.isGlobal ())
            sb.append (" - Global");
        sb.append ('\n');

        for (final Entry<Integer, Integer> gen: this.getGenerators ().entrySet ())
        {
            final int generator = gen.getKey ().intValue ();
            sb.append ("      - Preset. Generator: " + Generator.getGeneratorName (generator) + " : " + gen.getValue () + "\n");
        }

        if (this.instrument != null)
        {
            sb.append ("      - Instrument: " + this.instrument + "\n");

            for (int instrumentZoneIndex = 0; instrumentZoneIndex < this.instrument.getZoneCount (); instrumentZoneIndex++)
            {
                final Sf2InstrumentZone instrumentZone = this.instrument.getZone (instrumentZoneIndex);
                sb.append ("         * Instr. Zone ").append (instrumentZoneIndex + 1).append (instrumentZone.printInfo ());
            }
        }

        return sb.toString ();
    }
}
