// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import de.mossgrabers.convertwithmoss.file.riff.AbstractListChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * A Sf2 data list chunk.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2DataChunk extends AbstractListChunk
{
    private RawRIFFChunk sampleDataChunk;
    private RawRIFFChunk sampleData24Chunk;


    /**
     * Constructor.
     */
    public Sf2DataChunk ()
    {
        super (RiffID.SF_DATA_ID.getId ());
    }


    /** {@inheritDoc} */
    @Override
    public void add (final RawRIFFChunk chunk)
    {
        super.add (chunk);

        final RiffID riffID = chunk.getRiffID ();
        if (riffID == RiffID.SMPL_ID)
            this.sampleDataChunk = chunk;
        else if (riffID == RiffID.SF_SM24_ID)
            this.sampleData24Chunk = chunk;
    }


    /**
     * The smpl sub-chunk, if present, contains one or more "samples" of digital audio information
     * in the form of linearly coded sixteen bit, signed, little-endian (least significant byte
     * first) words. Each sample is followed by a minimum of forty-six zero valued sample data
     * points. These zero valued data points are necessary to guarantee that any reasonable upward
     * pitch shift using any reasonable interpolator can loop on zero data at the end of the sound.
     *
     * @return The data
     */
    public byte [] getSampleData ()
    {
        return this.sampleDataChunk == null ? null : this.sampleDataChunk.getData ();
    }


    /**
     * The sm24 sub-chunk, if present, contains the least significant byte counterparts to each
     * sample data point contained in the smpl chunk. Note this means for every two bytes in the
     * [smpl] sub-chunk there is a 1-byte counterpart in [sm24] sub-chunk.
     *
     * @return The data
     */
    public byte [] getSample24Data ()
    {
        return this.sampleData24Chunk == null ? null : this.sampleData24Chunk.getData ();
    }
}
