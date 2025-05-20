// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Bank;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.FileList;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.KontaktPresetChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.KontaktPresetChunkID;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Program;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.SlotList;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk which contains the data of a preset.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkData extends AbstractChunkData
{
    private final List<KontaktPresetChunk> chunks   = new ArrayList<> ();
    private final List<Program>            programs = new ArrayList<> ();
    private long                           dictionaryType;
    private long                           unknown  = 0;
    private long                           padding  = 0;
    private long                           magic    = 0x8565620D;
    private byte []                        programData;


    /**
     * Get the read programs.
     *
     * @return The programs
     */
    public List<Program> getPrograms ()
    {
        return this.programs;
    }


    /**
     * Set the programs.
     *
     * @param programs The programs to set
     */
    public void setPrograms (final List<Program> programs)
    {
        this.programs.clear ();
        this.programs.addAll (programs);
    }


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        // Dictionary type / Authorization checksum?
        this.dictionaryType = StreamUtils.readUnsigned32 (in, false);

        // Number of items in the Dictionary
        final int numberOfItems = (int) StreamUtils.readUnsigned32 (in, false);
        if (numberOfItems != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI_FOUND_MORE_THAN_ONE_ENTRY"));

        final int sizeOfItem = (int) StreamUtils.readUnsigned32 (in, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        this.unknown = StreamUtils.readUnsigned32 (in, false);

        this.programData = in.readNBytes (sizeOfItem);

        // Padding
        this.padding = StreamUtils.readUnsigned32 (in, false);

        // Seems to be always 0x8565620D, even for encrypted files
        this.magic = StreamUtils.readUnsigned32 (in, false);

        this.readKontaktPresetChunks (this.programData);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        // Dictionary type / Authorization checksum?
        StreamUtils.writeUnsigned32 (out, this.dictionaryType, false);

        // Number of items in the Dictionary - must be 1
        StreamUtils.writeUnsigned32 (out, 1, false);

        this.programData = this.writeKontaktPresetChunks ();

        StreamUtils.writeUnsigned32 (out, this.programData.length, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        StreamUtils.writeUnsigned32 (out, this.unknown, false);

        out.write (this.programData);

        // Padding & checksum
        StreamUtils.writeUnsigned32 (out, this.padding, false);
        StreamUtils.writeUnsigned32 (out, this.magic, false);
    }


    /**
     * Parses a Kontakt 4.2.2 / 5+ preset chunk structure and the contained Kontakt presets. The
     * method is public so it can be triggered from the 4.2.2 code.
     *
     * @param data The preset data
     * @throws IOException Could not parse the data
     */
    public void readKontaktPresetChunks (final byte [] data) throws IOException
    {
        this.chunks.clear ();

        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        while (in.available () > 0)
        {
            final KontaktPresetChunk chunk = new KontaktPresetChunk ();
            chunk.read (in);
            this.chunks.add (chunk);
        }

        this.readProgramsFromChunks ();
    }


    /**
     * Reads all programs from the already read Kontakt Preset Chunk structure.
     *
     * @throws IOException Could not parse the programs
     */
    private void readProgramsFromChunks () throws IOException
    {
        this.programs.clear ();

        // Read all top level programs
        for (final KontaktPresetChunk programChunk: findAllChunks (this.chunks, KontaktPresetChunkID.PROGRAM))
        {
            final Program program = new Program ();
            program.read (programChunk);
            this.programs.add (program);
        }

        // NKMs have the programs stored in a Bank
        final Optional<KontaktPresetChunk> bankChunkOpt = this.getTopChunk (KontaktPresetChunkID.BANK);
        if (bankChunkOpt.isEmpty ())
            return;

        final KontaktPresetChunk bankChunk = bankChunkOpt.get ();
        new Bank ().parse (bankChunk);

        for (final KontaktPresetChunk childChunk: bankChunk.getChildren ())
            if (childChunk.getId () == KontaktPresetChunkID.SLOT_LIST)
                this.programs.addAll (new SlotList ().read (childChunk));
    }


    /**
     * Write a Kontakt 5+ preset into a buffer.
     *
     * @return data The preset data
     * @throws IOException Could not write the data
     */
    public byte [] writeKontaktPresetChunks () throws IOException
    {
        this.writeProgramsToChunks ();

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        for (final KontaktPresetChunk chunk: this.chunks)
            chunk.write (out);
        return out.toByteArray ();
    }


    /**
     * Writes all programs to a Kontakt Preset Chunk structure.
     *
     * @throws IOException Could not write the programs
     */
    private void writeProgramsToChunks () throws IOException
    {
        // TODO Finish writing programs
        // this.chunks.clear ();

        if (this.programs.size () > 1)
        {
            // TODO also create NKM Banks (see parsePrograms above)
        }
        else
            this.programs.get (0).write (this.chunks.get (0));
    }


    /**
     * Get all chunks of the Kontakt program.
     *
     * @return The chunks
     */
    public List<KontaktPresetChunk> getChunks ()
    {
        return this.chunks;
    }


    /**
     * Get all file paths either from a FILENAME_LIST or a FILENAME_LIST_EX.
     *
     * @return The paths
     * @throws IOException Could not read the paths
     */
    public List<String> getFilePaths () throws IOException
    {
        Optional<KontaktPresetChunk> filelistChunk = this.getTopChunk (KontaktPresetChunkID.FILENAME_LIST);
        if (filelistChunk.isEmpty ())
        {
            filelistChunk = this.getTopChunk (KontaktPresetChunkID.FILENAME_LIST_EX);
            if (filelistChunk.isEmpty ())
                return Collections.emptyList ();
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
    private Optional<KontaktPresetChunk> getTopChunk (final int presetChunkID)
    {
        for (final KontaktPresetChunk chunk: this.chunks)
            if (chunk.getId () == presetChunkID)
                return Optional.of (chunk);
        return Optional.empty ();
    }


    /**
     * Find all chunks of the given type.
     *
     * @param topChunks The chunks to search in
     * @param presetChunkID One of the IDs in PresetChunkID
     * @return The chunk if available
     */
    private static List<KontaktPresetChunk> findAllChunks (final List<KontaktPresetChunk> topChunks, final int presetChunkID)
    {
        final List<KontaktPresetChunk> results = new ArrayList<> ();
        for (final KontaktPresetChunk chunk: topChunks)
            if (chunk.getId () == presetChunkID)
                results.add (chunk);
            else
                results.addAll (findAllChunks (chunk.getChildren (), presetChunkID));
        return results;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode (this.programData);
        result = prime * result + Objects.hash (Long.valueOf (this.dictionaryType), Long.valueOf (this.magic), Long.valueOf (this.padding), Long.valueOf (this.unknown));
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final PresetChunkData other = (PresetChunkData) obj;
        return this.dictionaryType == other.dictionaryType && this.magic == other.magic && this.padding == other.padding && Arrays.equals (this.programData, other.programData) && this.unknown == other.unknown;
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final StringBuilder sb = new StringBuilder ();
        for (final KontaktPresetChunk chunk: this.chunks)
            sb.append (chunk.dump (level)).append ("\n");
        return sb.toString ();
    }
}
