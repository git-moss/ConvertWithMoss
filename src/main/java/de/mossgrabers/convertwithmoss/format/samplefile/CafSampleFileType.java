// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.samplefile;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.caf.CafFileSampleData;
import de.mossgrabers.convertwithmoss.file.caf.CafInstrumentChunk;


/**
 * Detects recursively CAF files in folders, which can be the source for a multi-sample. Files must
 * end with <i>.caf</i>. All sample files in a folder are considered to belong to one multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class CafSampleFileType implements SampleFileType
{
    private static final String [] ENDINGS = new String []
    {
        ".caf"
    };


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return "CAF (*.caf)";
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
        if (sampleData instanceof final CafFileSampleData sd)
        {
            final CafInstrumentChunk instrumentChunk = sd.getCafFile ().getInstrumentChunk ();
            final int baseNote = Math.round (instrumentChunk.getBaseNote ());
            zone.setKeyRoot (baseNote);
            zone.setKeyLow (instrumentChunk.getLowNote ());
            zone.setKeyHigh (instrumentChunk.getHighNote ());
            zone.setVelocityLow (instrumentChunk.getLowVelocity ());
            zone.setVelocityHigh (instrumentChunk.getHighVelocity ());
            zone.setGain (instrumentChunk.getGain ());
            zone.setTuning (Math.clamp (instrumentChunk.getBaseNote () - baseNote, -0.5, 0.5));
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData)
    {
        for (final IFileBasedSampleData sampleFileData: sampleData)
            try
            {
                if (sampleFileData instanceof final CafFileSampleData sd && sd.getCafFile ().getInstrumentChunk () == null)
                    return false;
            }
            catch (final IOException _)
            {
                return false;
            }
        return true;
    }
}
