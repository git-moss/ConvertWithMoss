// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.samplefile;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.wav.InstrumentChunk;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Detects recursively wave files in folders, which can be the source for a multi-sample. Wave files
 * must end with <i>.wav</i>. All wave files in a folder are considered to belong to one
 * multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public class WavSampleFileType implements SampleFileType
{
    private static final String [] ENDINGS = new String []
    {
        ".wav"
    };


    /** {@inheritDoc} */
    @Override
    public String getName ()
    {
        return "WAV (*.wav)";
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
        if (sampleData instanceof final WavFileSampleData sd)
        {
            final InstrumentChunk instrumentChunk = sd.getWaveFile ().getInstrumentChunk ();
            zone.setKeyRoot (instrumentChunk.getUnshiftedNote ());
            zone.setKeyLow (instrumentChunk.getLowNote ());
            zone.setKeyHigh (instrumentChunk.getHighNote ());
            zone.setVelocityLow (instrumentChunk.getLowVelocity ());
            zone.setVelocityHigh (instrumentChunk.getHighVelocity ());
            zone.setGain (instrumentChunk.getGain ());
            zone.setTune (instrumentChunk.getFineTune () / 100.0);
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData)
    {
        for (final IFileBasedSampleData sampleFileData: sampleData)
            try
            {
                if (sampleFileData instanceof final WavFileSampleData sd && sd.getWaveFile ().getInstrumentChunk () == null)
                    return false;
            }
            catch (final IOException ex)
            {
                return false;
            }
        return true;
    }
}
