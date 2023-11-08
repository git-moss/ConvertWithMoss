// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Bank;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.FileList;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.PresetChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.PresetChunkID;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Program;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.SlotList;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * A chunk which contains the data of a preset.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkData extends AbstractChunkData
{
    private final List<PresetChunk> chunks = new ArrayList<> ();


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        // Dictionary type / Authorization checksum?
        StreamUtils.readUnsigned32 (in, false);

        // Number of items in the Dictionary
        final int numberOfItems = (int) StreamUtils.readUnsigned32 (in, false);
        if (numberOfItems != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI_FOUND_MORE_THAN_ONE_ENTRY"));

        final int sizeOfItem = (int) StreamUtils.readUnsigned32 (in, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        StreamUtils.readUnsigned32 (in, false);

        final byte [] data = in.readNBytes (sizeOfItem);

        // Padding
        StreamUtils.readUnsigned32 (in, false);

        // Checksum?!
        StreamUtils.readUnsigned32 (in, false);

        this.parsePresetChunks (data);
    }


    /**
     * Parses a Kontakt 5+ preset. To be used for 4.2.2 data.
     *
     * @param data The preset data
     * @return The program if any
     * @throws IOException Could not parse the data
     */
    public List<Program> parse (final byte [] data) throws IOException
    {
        this.parsePresetChunks (data);
        return this.parsePrograms ();
    }


    /**
     * Parses a Kontakt 5+ preset.
     *
     * @param data The preset data
     * @throws IOException Could not parse the data
     */
    private void parsePresetChunks (final byte [] data) throws IOException
    {
        this.chunks.clear ();

        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        while (in.available () > 0)
        {
            final PresetChunk chunk = new PresetChunk ();
            chunk.parse (in);
            this.chunks.add (chunk);
        }
    }


    /**
     * Parse all programs from the already parsed program chunk structure.
     *
     * @return The programs if any
     * @throws IOException Could not parse the program
     */
    public List<Program> parsePrograms () throws IOException
    {
        final List<String> filePaths = this.getFilePaths ();
        final List<Program> programs = new ArrayList<> ();

        // Read all top level programs
        for (final PresetChunk programChunk: findAllChunks (this.chunks, PresetChunkID.PROGRAM))
        {
            final Program program = new Program (filePaths);
            program.parse (programChunk);
            programs.add (program);
        }

        // NKMs have the programs stored in a Bank
        final Optional<PresetChunk> bankChunkOpt = this.getTopChunk (PresetChunkID.BANK);
        if (bankChunkOpt.isPresent ())
        {
            final PresetChunk bankChunk = bankChunkOpt.get ();
            new Bank ().parse (bankChunk);

            for (final PresetChunk childChunk: bankChunk.getChildren ())
            {
                if (childChunk.getId () == PresetChunkID.SLOT_LIST)
                    programs.addAll (new SlotList ().parse (childChunk, filePaths));
            }
        }

        return programs;
    }


    /**
     * Get all file paths either from a FILENAME_LIST or a FILENAME_LIST_EX.
     *
     * @return The paths
     * @throws IOException Could not read the paths
     */
    private List<String> getFilePaths () throws IOException
    {
        Optional<PresetChunk> filelistChunk = this.getTopChunk (PresetChunkID.FILENAME_LIST);
        if (filelistChunk.isEmpty ())
        {
            filelistChunk = this.getTopChunk (PresetChunkID.FILENAME_LIST_EX);
            if (filelistChunk.isEmpty ())
            {
                // No files?
                return Collections.emptyList ();
            }
        }
        final FileList fileList = new FileList ();
        fileList.parse (filelistChunk.get ());
        return fileList.getSampleFiles ();
    }


    /**
     * Get one of the top preset chunks.
     *
     * @param presetChunkID One of the IDs in PresetChunkID
     * @return The chunk if available
     */
    private Optional<PresetChunk> getTopChunk (final int presetChunkID)
    {
        for (final PresetChunk chunk: this.chunks)
        {
            if (chunk.getId () == presetChunkID)
                return Optional.of (chunk);
        }
        return Optional.empty ();
    }


    /**
     * Find all chunks of the given type.
     *
     * @param topChunks The chunks to search in
     * @param presetChunkID One of the IDs in PresetChunkID
     * @return The chunk if available
     */
    private static List<PresetChunk> findAllChunks (final List<PresetChunk> topChunks, final int presetChunkID)
    {
        final List<PresetChunk> results = new ArrayList<> ();
        for (final PresetChunk chunk: topChunks)
        {
            if (chunk.getId () == presetChunkID)
                results.add (chunk);
            else
                results.addAll (findAllChunks (chunk.getChildren (), presetChunkID));
        }
        return results;
    }


    /**
     * Dumps all info into a text.
     *
     * @return The formatted string
     */
    public String dump ()
    {
        final StringBuilder sb = new StringBuilder ();
        for (final PresetChunk chunk: this.chunks)
            sb.append (chunk.dump (0)).append ("\n");
        return sb.toString ();
    }
}
