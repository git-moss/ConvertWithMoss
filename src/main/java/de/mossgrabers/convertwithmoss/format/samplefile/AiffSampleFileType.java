// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.samplefile;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.aiff.AiffFileSampleData;
import de.mossgrabers.convertwithmoss.file.aiff.AiffInstrumentChunk;


/**
 * Detects recursively AIFF files in folders, which can be the source for a multi-sample. Files must
 * end with <i>.aiff</i> or <i>.aif</i>. All sample files in a folder are considered to belong to
 * one multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class AiffSampleFileType implements SampleFileType
{
    private static final String [] ENDINGS = new String []
    {
        ".aif",
        ".aiff"
    };


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return "AIFF (*.aif, *.aiff)";
    }


    /** {@inheritDoc} */
    @Override
    public String [] getFileEndings ()
    {
        return ENDINGS;
    }


    /** {@inheritDoc} */
    @Override
    public void fillInstrumentData (final ISampleZone zone, final IFileBasedSampleData sampleData) throws IOException
    {
        if (sampleData instanceof final AiffFileSampleData sd)
        {
            final AiffInstrumentChunk instrumentChunk = sd.getAiffFile ().getInstrumentChunk ();
            zone.setKeyRoot (instrumentChunk.getBaseNote ());
            zone.setKeyLow (instrumentChunk.getLowNote ());
            zone.setKeyHigh (instrumentChunk.getHighNote ());
            zone.setVelocityLow (instrumentChunk.getLowVelocity ());
            zone.setVelocityHigh (instrumentChunk.getHighVelocity ());
            zone.setGain (instrumentChunk.getGain ());
            zone.setTune (instrumentChunk.getDetune () / 100.0);
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData)
    {
        for (final IFileBasedSampleData sampleFileData: sampleData)
            try
            {
                if (sampleFileData instanceof final AiffFileSampleData sd && sd.getAiffFile ().getInstrumentChunk () == null)
                    return false;
            }
            catch (final IOException ex)
            {
                return false;
            }
        return true;
    }
}
