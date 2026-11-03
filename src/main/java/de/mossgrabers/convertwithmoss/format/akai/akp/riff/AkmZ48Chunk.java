// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.IRiffChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * The 'z48 ' chunk of a multi which is only written by the Z4/Z8. It contains the multi in the
 * Z4/Z8 format as sub-chunks: 'mult', 'qlnk', 'mflt', 'fx  ' and one 'part' per part. Only the
 * parts are decoded, the other sub-chunks are kept as they are. The format is described in
 * documentation/design/AKAI_AKP_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class AkmZ48Chunk extends AbstractSpecificRIFFChunk
{
    /** The number of parts of a multi. */
    public static final int        NUMBER_OF_PARTS      = 32;

    /** The number of Q-Links. */
    private static final int       NUMBER_OF_QLINKS     = 8;
    /** The length of the MIDI filter sub-chunk. */
    private static final int       LENGTH_MIDI_FILTER   = 409;
    /** The effects sub-chunk of a multi created on the Z8 without any effects settings. */
    private static final String    DEFAULT_EFFECTS_FILE = "AkmZ48Effects.bin";

    private final List<IRiffChunk> subChunks            = new ArrayList<> ();
    private final List<AkmZ48Part> parts                = new ArrayList<> ();


    /**
     * Default constructor. Creates the chunk with the default settings and unused parts.
     */
    public AkmZ48Chunk ()
    {
        super (AkmRiffChunkId.Z48_ID, 0);

        this.subChunks.add (createSubChunk (AkmRiffChunkId.Z48_MULTI_ID, new byte []
        {
            1,
            0,
            NUMBER_OF_PARTS
        }));

        // Each Q-Link has a scale minimum of -100 and maximum of 100
        final byte [] qlinkData = new byte [1 + NUMBER_OF_QLINKS * 8];
        qlinkData[0] = 1;
        for (int i = 0; i < NUMBER_OF_QLINKS; i++)
        {
            qlinkData[1 + i * 8 + 5] = 1;
            qlinkData[1 + i * 8 + 6] = -100;
            qlinkData[1 + i * 8 + 7] = 100;
        }
        this.subChunks.add (createSubChunk (AkmRiffChunkId.Z48_QLINK_ID, qlinkData));

        final byte [] midiFilterData = new byte [LENGTH_MIDI_FILTER];
        midiFilterData[0] = 1;
        this.subChunks.add (createSubChunk (AkmRiffChunkId.Z48_MIDI_FILTER_ID, midiFilterData));
        this.subChunks.add (createSubChunk (AkmRiffChunkId.FX_ID, loadTemplate (DEFAULT_EFFECTS_FILE)));

        for (int i = 0; i < NUMBER_OF_PARTS; i++)
        {
            final AkmZ48Part part = new AkmZ48Part ();
            part.setMidiChannel (i);
            this.parts.add (part);
            this.subChunks.add (part);
        }
    }


    /**
     * Get all parts.
     *
     * @return The parts, normally 32
     */
    public List<AkmZ48Part> getParts ()
    {
        return this.parts;
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        final byte [] data = chunk.getData ();

        // The sub-chunks are kept in the order of the file for writing
        this.parts.clear ();
        this.subChunks.clear ();

        int position = 0;
        while (position + 8 <= data.length)
        {
            final int fourCC = Byte.toUnsignedInt (data[position]) << 24 | Byte.toUnsignedInt (data[position + 1]) << 16 | Byte.toUnsignedInt (data[position + 2]) << 8 | Byte.toUnsignedInt (data[position + 3]);
            final int size = Byte.toUnsignedInt (data[position + 4]) | Byte.toUnsignedInt (data[position + 5]) << 8 | Byte.toUnsignedInt (data[position + 6]) << 16 | Byte.toUnsignedInt (data[position + 7]) << 24;
            final int start = position + 8;
            if (size < 0 || start + size > data.length)
                throw new ParseException ("Sub-chunk of Z4/Z8 multi is larger than the multi.");

            final Optional<AkmRiffChunkId> id = AkmRiffChunkId.fromFourCC (fourCC);
            final RiffChunkId chunkId = id.isPresent () ? id.get () : new UnknownChunkId (fourCC);
            final RawRIFFChunk subChunk = createSubChunk (chunkId, Arrays.copyOfRange (data, start, start + size));
            if (id.isPresent () && id.get () == AkmRiffChunkId.PART_ID)
            {
                final AkmZ48Part part = new AkmZ48Part ();
                part.read (subChunk);
                this.parts.add (part);
                this.subChunks.add (part);
            }
            else
                this.subChunks.add (subChunk);

            position = start + size + size % 2;
        }

        this.rawRiffChunk.setData (data);
    }


    /**
     * Updates the data of the chunk from all sub-chunks. Needs to be called before the chunk is
     * written.
     */
    public void updateData ()
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        try
        {
            for (final IRiffChunk subChunk: this.subChunks)
            {
                if (subChunk instanceof final AkmZ48Part part)
                    part.updateData (Optional.empty ());
                subChunk.write (out);
            }
        }
        catch (final IOException ex)
        {
            // Cannot happen since it is written to memory
            throw new UncheckedIOException (ex);
        }
        this.rawRiffChunk.setData (out.toByteArray ());
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }


    /**
     * Create a sub-chunk.
     *
     * @param chunkId The ID of the sub-chunk
     * @param data The data of the sub-chunk
     * @return The sub-chunk
     */
    private static RawRIFFChunk createSubChunk (final RiffChunkId chunkId, final byte [] data)
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (0, chunkId, data.length);
        chunk.setData (data);
        return chunk;
    }


    /**
     * Load a template from the resources.
     *
     * @param resourceName The name of the resource
     * @return The content of the resource
     */
    private static byte [] loadTemplate (final String resourceName)
    {
        try (final InputStream in = AkmZ48Chunk.class.getResourceAsStream ("/de/mossgrabers/convertwithmoss/format/akai/akp/" + resourceName))
        {
            if (in == null)
                throw new IllegalStateException ("Missing AKM template resource: " + resourceName);
            return in.readAllBytes ();
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException ("Could not read AKM template resource: " + resourceName, ex);
        }
    }


    /**
     * The ID of a sub-chunk which is not known.
     *
     * @param fourCC The FourCC of the ID
     */
    private record UnknownChunkId (int fourCC) implements RiffChunkId
    {
        /** {@inheritDoc} */
        @Override
        public int getFourCC ()
        {
            return this.fourCC;
        }


        /** {@inheritDoc} */
        @Override
        public String getDescription ()
        {
            return "Unknown";
        }
    }
}
