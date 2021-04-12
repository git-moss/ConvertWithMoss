// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sf2;

import de.mossgrabers.sampleconverter.core.AbstractSampleMetadata;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.file.sf2.Sf2SampleDescriptor;

import java.io.IOException;
import java.io.OutputStream;


/**
 * Metadata for a Sf2 sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2SampleMetadata extends AbstractSampleMetadata
{
    private final Sf2SampleDescriptor sample;


    /**
     * Constructor.
     *
     * @param sample The name of the file where the sample is stored
     */
    public Sf2SampleMetadata (final Sf2SampleDescriptor sample)
    {
        super (sample.getName ());

        this.sample = sample;
    }


    /** {@inheritDoc} */
    @Override
    public void combine (final ISampleMetadata sample) throws CombinationNotPossibleException
    {
        // TODO
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        // TODO
    }
}
