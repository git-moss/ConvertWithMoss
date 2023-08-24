// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.StructuredObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * A chunk which contains the data of a preset.
 *
 * @author Jürgen Moßgraber
 */
public class PresetDataChunkData extends AbstractChunkData
{
    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        // Dictionary type / Authorization checksum?
        StreamUtils.readUnsigned32 (in, false);

        // Number of items in the Dictionary
        final int numberOfItems = StreamUtils.readUnsigned32 (in, false);
        if (numberOfItems != 1)
            throw new IOException ("Found more than one item!");

        final int sizeOfItem = StreamUtils.readUnsigned32 (in, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        StreamUtils.readUnsigned32 (in, false);

        final byte [] data = in.readNBytes (sizeOfItem);

        final int endPadding = StreamUtils.readUnsigned32 (in, false);
        if (endPadding != 0)
            throw new IOException ("No end padding");

        // Checksum?!
        StreamUtils.readUnsigned32 (in, false);

        this.parsePresetChunks (data);
    }


    /**
     * Parses a Kontakt 5+ preset.
     *
     * @param data The preset data
     * @throws IOException Could not parse the data
     */
    public void parsePresetChunks (final byte [] data) throws IOException
    {
        // final ByteArrayInputStream in = new ByteArrayInputStream (data);

        // TODO loop
        final StructuredObject structuredObject = new StructuredObject ();
        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        structuredObject.parse (in);

        // final List<KontaktPresetChunk> presetChunks = new ArrayList<> ();
        // while (in.available () > 0)
        // {
        // final KontaktPresetChunk kontaktPresetChunk = new KontaktPresetChunk ();
        // kontaktPresetChunk.read (in);
        // presetChunks.add (kontaktPresetChunk);
        // }
        //
        // for (final KontaktPresetChunk presetChunk: presetChunks)
        // {
        // final StructuredObject structuredObject = new StructuredObject ();
        // structuredObject.parse (presetChunk.getData ());
        // }

    }
}
