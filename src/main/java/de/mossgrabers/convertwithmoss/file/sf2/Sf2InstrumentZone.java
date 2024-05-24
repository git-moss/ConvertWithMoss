// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.Map.Entry;


/**
 * A SF2 instrument zone.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2InstrumentZone extends AbstractZone
{
    private Sf2SampleDescriptor sampleDescriptor;


    /**
     * Default Constructor.
     */
    public Sf2InstrumentZone ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     *
     * @param firstGenerator Index to the first generator of the zone in the IGEN list
     * @param numberOfGenerators The number of generators in this zone
     * @param firstModulator Index to the first modulator of the zone in the IMOD list
     * @param numberOfModulators The number of modulators of this zone
     */
    public Sf2InstrumentZone (final int firstGenerator, final int numberOfGenerators, final int firstModulator, final int numberOfModulators)
    {
        super (firstGenerator, numberOfGenerators, firstModulator, numberOfModulators);
    }


    /**
     * Set the sample which is referenced from this zone.
     *
     * @param sampleDescriptor The sample to assign
     */
    public void setSample (final Sf2SampleDescriptor sampleDescriptor)
    {
        this.sampleDescriptor = sampleDescriptor;
    }


    /**
     * Get the sample which is referenced from this zone.
     *
     * @return The assigned sample
     */
    public Sf2SampleDescriptor getSample ()
    {
        return this.sampleDescriptor;
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
            sb.append ("           - Instr. Generator: " + Generator.getGeneratorName (generator) + " : " + gen.getValue () + "\n");
        }

        if (this.sampleDescriptor != null)
        {
            sb.append ("           - Sample: " + this.sampleDescriptor.getName () + "\n");
            sb.append ("             * Range:" + this.sampleDescriptor.getStart () + "-" + this.sampleDescriptor.getEnd () + "\n");
            sb.append ("             * Loop :" + this.sampleDescriptor.getLoopStart () + "-" + this.sampleDescriptor.getLoopEnd () + "\n");
            sb.append ("             * Pitch:" + this.sampleDescriptor.getOriginalPitch () + " : " + this.sampleDescriptor.getPitchCorrection () + "\n");
            sb.append ("             * Type :" + this.sampleDescriptor.getSampleType () + " : " + this.sampleDescriptor.getSampleRate () + " Hz\n");
        }

        return sb.toString ();
    }
}
