// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import de.mossgrabers.tools.ui.Functions;


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
     * @return The parsed programs
     * @throws IOException Could not read the slot list
     */
    public List<Program> read (final KontaktPresetChunk chunk) throws IOException
    {
        if (chunk.getId () != KontaktPresetChunkID.SLOT_LIST)
            throw new IOException (Functions.getMessage ("IDS_NKI_NO_SLOT_LIST_CHUNK"));

        final List<Program> programs = new ArrayList<> ();

        try (final ByteArrayInputStream in = new ByteArrayInputStream (chunk.getPublicData ()))
        {
            final BitSet slotFlags = BitSet.valueOf (in.readNBytes (8));
            for (int i = 0; i < 64; i++)
                if (slotFlags.get (i))
                {
                    final KontaktPresetChunk programContainerChunk = new KontaktPresetChunk ();
                    programContainerChunk.read (in);
                    if (programContainerChunk.getId () != KontaktPresetChunkID.PROGRAM_CONTAINER)
                        continue;

                    for (final KontaktPresetChunk child: programContainerChunk.getChildren ())
                        if (child.getId () == KontaktPresetChunkID.PROGRAM_LIST)
                        {
                            // This should always be only one!
                            final List<Program> programList = parseProgramList (child);
                            // Remember the slot index since they are not continuous!
                            for (final Program program: programList)
                                program.setSlotIndex (i);
                            programs.addAll (programList);
                        }
                }
        }

        return programs;
    }


    private static List<Program> parseProgramList (final KontaktPresetChunk programListChunk) throws IOException
    {
        final List<Program> programs = new ArrayList<> ();
        final byte [] publicData = programListChunk.getPublicData ();
        try (final ByteArrayInputStream bin = new ByteArrayInputStream (publicData))
        {
            final KontaktPresetChunk childChunk = new KontaktPresetChunk ();
            childChunk.readArray (bin, publicData.length, false);
            for (final KontaktPresetChunk presetChunk: childChunk.getChildren ())
            {
                presetChunk.setId (KontaktPresetChunkID.PROGRAM);
                final Program program = new Program ();
                program.read (presetChunk);
                programs.add (program);
            }
        }
        return programs;
    }
}