// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.FileList;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Multi;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.PresetChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.PresetChunkID;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Program;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
        final List<Program> programs = new ArrayList<> ();

        Optional<PresetChunk> filelistChunk = this.getTopChunk (PresetChunkID.FILENAME_LIST);
        if (filelistChunk.isEmpty ())
        {
            filelistChunk = this.getTopChunk (PresetChunkID.FILENAME_LIST_EX);
            if (filelistChunk.isEmpty ())
            {
                // No files?
                return programs;
            }
        }
        final FileList fileList = new FileList ();
        fileList.parse (filelistChunk.get ());
        final List<String> filePaths = fileList.getSampleFiles ();

        for (final PresetChunk programChunk: this.getTopChunks (PresetChunkID.PROGRAM))
        {
            final Program program = new Program (filePaths);
            program.parse (programChunk, filePaths);
            programs.add (program);
        }

        final Optional<PresetChunk> multiChunk = this.getTopChunk (PresetChunkID.BANK);
        if (multiChunk.isPresent ())
        {
            final Multi multi = new Multi (filePaths);
            multi.parse (multiChunk.get (), filePaths);

            throw new IOException (Functions.getMessage ("IDS_NKI5_MULTIS_NOT_SUPPORTED"));

            // TODO return programs
        }

        return programs;
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
     * Get one of the top preset chunks.
     *
     * @param presetChunkID One of the IDs in PresetChunkID
     * @return The chunk if available
     */
    private List<PresetChunk> getTopChunks (final int presetChunkID)
    {
        final List<PresetChunk> results = new ArrayList<> ();
        for (final PresetChunk chunk: this.chunks)
        {
            if (chunk.getId () == presetChunkID)
                results.add (chunk);
        }
        return results;
    }
}
