// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Can read and write one or more Kontakt programs to a Preset Chunk.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktPresetAccessor
{
    private final List<KontaktPresetChunk> presetChunks       = new ArrayList<> ();
    private final List<Program>            programs           = new ArrayList<> ();
    private final MultiConfiguration       multiConfiguration = new MultiConfiguration ();


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


    /**
     * Get all chunks of the Kontakt program.
     *
     * @return The chunks
     */
    public List<KontaktPresetChunk> getChunks ()
    {
        return this.presetChunks;
    }


    /**
     * Get one of the top preset chunks.
     *
     * @param presetChunkID One of the IDs in PresetChunkID
     * @return The chunk if available
     */
    private Optional<KontaktPresetChunk> getTopChunk (final int presetChunkID)
    {
        for (final KontaktPresetChunk chunk: this.presetChunks)
            if (chunk.getId () == presetChunkID)
                return Optional.of (chunk);
        return Optional.empty ();
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
     * Get the multi-configuration.
     *
     * @return The multi-configuration, might be null
     */
    public MultiConfiguration getMultiConfiguration ()
    {
        return this.multiConfiguration;
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


    /**
     * Parses a Kontakt 4.2.2 / 5+ preset chunk structure and the contained Kontakt presets. The
     * method is public so it can be triggered from the 4.2.2 code.
     *
     * @param data The preset data
     * @throws IOException Could not parse the data
     */
    public void readKontaktPresetChunks (final byte [] data) throws IOException
    {
        this.presetChunks.clear ();

        final ByteArrayInputStream in = new ByteArrayInputStream (data);
        while (in.available () > 0)
        {
            final KontaktPresetChunk chunk = new KontaktPresetChunk ();
            chunk.read (in);
            this.presetChunks.add (chunk);
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
        for (final KontaktPresetChunk programChunk: findAllChunks (this.presetChunks, KontaktPresetChunkID.PROGRAM))
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
        final Bank bank = new Bank ();
        bank.parse (bankChunk);

        boolean hasMultiConfiguration = false;

        for (final KontaktPresetChunk childChunk: bankChunk.getChildren ())
            switch (childChunk.getId ())
            {
                case KontaktPresetChunkID.SLOT_LIST:
                    this.programs.addAll (new SlotList ().read (childChunk));
                    break;

                case KontaktPresetChunkID.MULTI_CONFIGURATION:
                    this.multiConfiguration.parse (childChunk);
                    hasMultiConfiguration = true;
                    break;

                default:
                    // Not used
                    break;
            }

        if (!hasMultiConfiguration)
        {
            final List<MultiInstrument> multiInstruments = this.multiConfiguration.getMultiInstruments ();
            for (final int midiChannel: bank.getMidiChannels ())
                multiInstruments.add (new MultiInstrument (midiChannel));
        }
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
        for (final KontaktPresetChunk chunk: this.presetChunks)
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
            this.programs.get (0).write (this.presetChunks.get (0));
    }
}
