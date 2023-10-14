// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;


/**
 * A Kontakt 5+ slot list.
 *
 * @author Jürgen Moßgraber
 */
public class SlotList
{
    /**
     * Constructor.
     */
    public SlotList ()
    {
        // Intentionally empty
    }


    /**
     * Parse the slot list data.
     *
     * @param chunk The chunk from which to read the slot list data
     * @param filePaths The paths to the samples
     * @return The parsed programs
     * @throws IOException Could not read the slot list
     */
    public List<Program> parse (final PresetChunk chunk, final List<String> filePaths) throws IOException
    {
        if (chunk.getId () != PresetChunkID.SLOT_LIST)
            throw new IOException ("Not a slot list chunk!");

        final List<Program> programs = new ArrayList<> ();

        try (final ByteArrayInputStream in = new ByteArrayInputStream (chunk.getPublicData ()))
        {
            final BitSet slotFlags = BitSet.valueOf (in.readNBytes (8));
            for (int i = 0; i < 64; i++)
            {
                if (slotFlags.get (i))
                {
                    final PresetChunk programContainerChunk = new PresetChunk ();
                    programContainerChunk.parse (in);
                    if (programContainerChunk.getId () != PresetChunkID.PROGRAM_CONTAINER)
                        continue;

                    for (final PresetChunk child: programContainerChunk.getChildren ())
                    {
                        if (child.getId () == PresetChunkID.PROGRAM_LIST)
                            programs.add (parseProgramList (child, filePaths));
                    }
                }
            }
        }

        return programs;
    }


    private static Program parseProgramList (final PresetChunk programListChunk, final List<String> filePaths) throws IOException
    {
        final byte [] publicData = programListChunk.getPublicData ();
        try (final ByteArrayInputStream bin = new ByteArrayInputStream (publicData))
        {
            final PresetChunk childChunk = new PresetChunk ();
            childChunk.readArray (bin, publicData.length, false);

            final List<PresetChunk> children = childChunk.getChildren ();
            final PresetChunk presetChunk = children.get (0);
            final Program program = new Program (filePaths);
            program.parse (presetChunk);
            return program;
        }
    }
}