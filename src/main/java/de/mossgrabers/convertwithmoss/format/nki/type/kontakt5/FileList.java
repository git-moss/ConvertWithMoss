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

        if (chunkID == PresetChunkID.FILENAME_LIST_EX)
        {
            final int version = StreamUtils.readUnsigned16 (in, false);
            if (version != 2)
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_EX_VERSION", Integer.toString (version)));

            // The file category: 1 = File or Folder, 2 = NKR file
            StreamUtils.readUnsigned32 (in, false);

            while (true)
            {
                in.mark (4);
                final int type = StreamUtils.readUnsigned32 (in, false);
                if (type == 0)
                    break;
                in.reset ();
                readFile (in);
            }
        }
        else
        {
            final int version = StreamUtils.readUnsigned32 (in, false);
            if (version != 0)
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_VERSION", Integer.toString (version)));
        }

        this.readFiles (in);

        if (in.available () > 2)
        {
            // Last changed date of files
            final int numFiles = this.filePaths.size ();
            for (int i = 0; i < numFiles; i++)
            {
                // The last update date of the file
                StreamUtils.readTimestamp (in, false);
                // Padding - always zero
                StreamUtils.readUnsigned32 (in, false);
            }

            // Padding
            StreamUtils.readUnsigned32 (in, false);

            if (in.available () > 2)
            {
                // Unknown but there is 1 integer for each file
                for (int i = 0; i < numFiles; i++)
                {
                    // The file at the last index is set to 0, 1, 2 or 3. All others seem to be
                    // always 0.
                    StreamUtils.readUnsigned32 (in, false);
                }

                if (in.available () > 2)
                {
                    // The full path of the NKI file
                    readFile (in);
                }
            }

            // There is more data for reverb samples (and maybe wallpaper)
            final int rest = in.available ();
            if (rest == 0)
                return;

            in.readNBytes (rest - 2);
        }

        // Always 1?
        StreamUtils.readUnsigned16 (in, false);
    }


    /**
     * Read all file names with their paths.
     *
     * @param in The data to parse
     * @throws IOException Could not read the data
     */
    private void readFiles (final ByteArrayInputStream in) throws IOException
    {
        final int fileCount = StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < fileCount; i++)
            this.filePaths.add (readFile (in));
    }


    /**
     * Read one file name with it's path.
     *
     * @param in The data to parse
     * @return The read file
     * @throws IOException Could not read the data
     */
    private static String readFile (final ByteArrayInputStream in) throws IOException
    {
        final StringBuilder sb = new StringBuilder ();
        final int segmentCount = StreamUtils.readUnsigned32 (in, false);
        for (int s = 0; s < segmentCount; s++)
            readSegment (in, sb);
        return sb.toString ();
    }


    /**
     * Reads a segment of a file path.
     *
     * @param in Where to read the segment from
     * @param sb Where to append the read path segment
     * @throws IOException Could not read
     */
    private static void readSegment (final ByteArrayInputStream in, final StringBuilder sb) throws IOException
    {
        final int segmentType = in.read ();
        switch (segmentType)
        {
            // Drive letter
            case 1:
                final String drive = StreamUtils.readWithLengthUTF16 (in);
                sb.append (drive).append (":/");
                break;

            // Path segment
            case 2:
                final String pathSegment = StreamUtils.readWithLengthUTF16 (in);
                sb.append (pathSegment).append ('/');
                break;

            // File name
            case 4:
                final String filenameSegment = StreamUtils.readWithLengthUTF16 (in);
                sb.append (filenameSegment);
                break;

            default:
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_SEGMENT", Integer.toString (segmentType)));
        }
    }


    /**
     * Get the file paths.
     *
     * @return The file paths
     */
    public List<String> getFilePaths ()
    {
        return this.filePaths;
    }
}
