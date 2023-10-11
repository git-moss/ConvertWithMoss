// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A Kontakt 5+ multi.
 *
 * @author Jürgen Moßgraber
 */
public class Multi
{
    private String              name;
    private final List<String>  filePaths;
    private final List<Program> programs = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param filePaths The list of file paths for external audio samples referenced from the
     *            program.
     */
    public Multi (final List<String> filePaths)
    {
        this.filePaths = filePaths;
    }


    /**
     * Parse the program data from a Program preset chunk.
     *
     * @param chunk The chunk from which to read the program data
     * @param filePaths The list with all referenced files
     * @throws IOException Could not read the program
     */
    public void parse (final PresetChunk chunk, final List<String> filePaths) throws IOException
    {
        if (chunk.getId () != PresetChunkID.BANK)
            throw new IOException ("Not a bank chunk!");

        this.readBankData (chunk.getPublicData ());
    }


    /**
     * Parses the Bank data.
     *
     * @param data The data to parse
     * @throws IOException Could not read the data
     */
    private void readBankData (final byte [] data) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        final int magic = (int) StreamUtils.readUnsigned32 (in, false);
        if (magic != 0xA3007101)
            throw new IOException ("Not a bank chunk!");

        final int numberOfInstruments = (int) StreamUtils.readUnsigned32 (in, false);

        // Unknown
        StreamUtils.readUnsigned16 (in, false);

        // Always 64
        final long maxInstruments = StreamUtils.readUnsigned32 (in, false);
        // Skip MIDI channel info
        in.skipNBytes (maxInstruments * 2L);
        // Skip unknown, maybe solo/mute
        in.skipNBytes (maxInstruments * 2L);
        // Skip unknown
        in.skipNBytes (maxInstruments * 20L);
        // Skip unknown
        in.skipNBytes (4L * (8 + 4 * 16));
        // Skip unknown
        final byte [] readNBytes = in.readNBytes ((int) (maxInstruments * 2));

        // Unknown, size info about the following block?!
        in.skipNBytes (12);

        this.name = StreamUtils.readWithLengthUTF16 (in);

        // TODO
        // try (FileOutputStream out = new FileOutputStream
        // ("C:\\Users\\mos\\Desktop\\BankChunk-rest2.bin"))
        // {
        // out.write (in.readAllBytes ());
        // }

    }
}