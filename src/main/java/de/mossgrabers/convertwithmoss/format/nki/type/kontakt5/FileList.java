// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * A chunk which contains a list of sample files with a (relative) path.
 *
 * @author Jürgen Moßgraber
 */
public class FileList
{
    private final List<String> filePaths = new ArrayList<> ();


    /**
     * Parse a file list (or pre 5.1 file list) from a preset chunk.
     *
     * @param chunk The chunk to parse
     * @throws IOException Could not read the chunk
     */
    public void parse (final PresetChunk chunk) throws IOException
    {
        final int chunkID = chunk.getId ();
        if (chunkID != PresetChunkID.FILENAME_LIST && chunkID != PresetChunkID.FILENAME_LIST_EX)
            throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_FILELIST_CHUNK"));

        final byte [] data = chunk.getPublicData ();
        final ByteArrayInputStream in = new ByteArrayInputStream (data);

        final int version = StreamUtils.readUnsigned16 (in, false);
        if (version > 2)
            throw new IOException ("TODO"); // TODO

        if (chunkID == PresetChunkID.FILENAME_LIST_EX)
        {
            // Unknown
            StreamUtils.readUnsigned32 (in, false);
        }

        this.readPublicData (in);
    }


    /**
     * Parse the public data section.
     *
     * @param in The data to parse
     * @throws IOException Could not read the data
     */
    private void readPublicData (final ByteArrayInputStream in) throws IOException
    {
        final int fileCount = StreamUtils.readUnsigned32 (in, false);

        for (int i = 0; i < fileCount; i++)
        {
            final StringBuilder sb = new StringBuilder ();
            final int segmentCount = StreamUtils.readUnsigned32 (in, false);
            for (int s = 0; s < segmentCount; s++)
            {
                final int segmentType = in.read ();
                switch (segmentType)
                {
                    case 0:
                        throw new IOException ("Found segmentType: 0");
                    // Unknown
                    // in.read ();
                    // in.read ();
                    // break;

                    case 2:
                        final String pathSegment = StreamUtils.readWithLengthUTF16 (in);
                        sb.append (pathSegment).append ('/');
                        break;

                    case 4:
                        final String pathSegment2 = StreamUtils.readWithLengthUTF16 (in);
                        sb.append (pathSegment2);
                        break;

                    case 6:
                        throw new IOException ("Found segmentType: 6");
                    // uint8 "?"
                    // set length [uint32 "len"]
                    // utf16 [expr $length * 2] "name"
                    // break;

                    default: // TODO
                        throw new IOException ("unknown segmentType: " + segmentType);
                }
            }

            this.filePaths.add (sb.toString ());
        }

        // TODO there are more bytes...
        int rest = in.available ();
    }


    /**
     * Get the parsed file paths.
     * 
     * @return The file paths
     */
    public List<String> getFilePaths ()
    {
        return this.filePaths;
    }
}
