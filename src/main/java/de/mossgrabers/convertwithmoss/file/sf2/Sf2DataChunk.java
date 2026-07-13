// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.riff.AbstractListChunk;
import de.mossgrabers.convertwithmoss.file.riff.IRiffChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A Sf2 data list chunk.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2DataChunk extends AbstractListChunk
{
    /**
     * Constructor.
     */
    public Sf2DataChunk ()
    {
        super (Sf2RiffChunkId.DATA_ID.getFourCC ());
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
        final Optional<IRiffChunk> chunk = this.findSubChunk (Sf2RiffChunkId.SMPL_ID);
        if (chunk.isEmpty ())
            return null;
        return chunk.get () instanceof final RawRIFFChunk rawChunk ? rawChunk.getData () : null;
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
        final Optional<IRiffChunk> chunk = this.findSubChunk (Sf2RiffChunkId.SM24_ID);
        if (chunk.isEmpty ())
            return null;
        return chunk.get () instanceof final RawRIFFChunk rawChunk ? rawChunk.getData () : null;
    }
}
