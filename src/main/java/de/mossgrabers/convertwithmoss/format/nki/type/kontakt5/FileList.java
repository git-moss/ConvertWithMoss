// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk which contains a list of sample files with a (relative) path.
 *
 * @author Jürgen Moßgraber
 */
public class FileList
{
    private List<String> specialFiles;
    private List<String> sampleFiles;
    private List<String> otherFiles;


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

            this.specialFiles = readFiles (in);
        }
        else
        {
            final long version = StreamUtils.readUnsigned32 (in, false);
            if (version < 0 || version > 1)
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_VERSION", Long.toString (version)));

            if (version == 1)
                // absoluteMonolithSourcePath not used currently
                readFile (in);
        }

        this.sampleFiles = readFiles (in);
        this.readMetadata (in, chunkID);
    }


    private void readMetadata (final ByteArrayInputStream in, final int chunkID) throws IOException
    {
        // Last changed date of files
        final int numFiles = this.sampleFiles.size ();
        for (int i = 0; i < numFiles; i++)
        {
            // The last update date of the file
            StreamUtils.readTimestamp (in, false);
            // Padding - always zero
            StreamUtils.readUnsigned32 (in, false);
        }

        if (chunkID == PresetChunkID.FILENAME_LIST_EX)
        {
            // Unknown but there is 1 integer for each file
            for (int i = 0; i < numFiles; i++)
                StreamUtils.readUnsigned32 (in, false);

            this.otherFiles = readFiles (in);
        }
        else
            // Final padding
            StreamUtils.readUnsigned32 (in, false);
    }


    /**
     * Read several file paths.
     *
     * @param in The input stream to read from
     * @return The read file paths
     * @throws IOException Could not read
     */
    private static List<String> readFiles (final ByteArrayInputStream in) throws IOException
    {
        final List<String> files = new ArrayList<> ();
        if (in.available () > 0)
        {
            final int size = StreamUtils.readSigned32 (in, false);
            for (int i = 0; i < size; i++)
                files.add (readFile (in));
        }
        return files;
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
        final long segmentCount = StreamUtils.readUnsigned32 (in, false);
        for (int s = 0; s < segmentCount; s++)
            readSegment (in, sb);
        return sb.toString ();
    }


    /**
     * Reads a segment of a file path.
     *
     * @param in Where to read the segment from
     * @param buffer Where to append the read path segment
     * @throws IOException Could not read
     */
    private static void readSegment (final ByteArrayInputStream in, final StringBuilder buffer) throws IOException
    {
        final int segmentType = in.read ();
        switch (segmentType)
        {
            // Drive letter ASCII
            case 0:
                final String driveOld = StreamUtils.readASCII (in, 2).trim ();
                if (driveOld.isEmpty ())
                    buffer.append ("/");
                else
                    buffer.append (driveOld).append (":/");
                break;

            // Drive letter UTF-16
            case 1:
                final String drive = StreamUtils.readWithLengthUTF16 (in);
                if (drive.isEmpty ())
                    buffer.append ("/");
                else
                    buffer.append (drive).append (":/");
                break;

            // Path segment
            case 2:
                buffer.append (StreamUtils.readWithLengthUTF16 (in)).append ('/');
                break;

            // Parent directory
            case 3:
                buffer.append ("../");
                break;

            // File name
            case 4:
                buffer.append (StreamUtils.readWithLengthUTF16 (in));
                break;

            case 6:
                // Some kind of top element, maybe a '.'?!
                break;

            // NKX Filename
            case 8:
                buffer.append (StreamUtils.readWithLengthUTF16 (in));
                break;

            // NKM Filename
            case 9:
                buffer.append (StreamUtils.readWithLengthUTF16 (in));
                break;

            default:
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_SEGMENT", Integer.toString (segmentType)));
        }
    }


    /**
     * Get the special file paths.
     *
     * @return The file paths
     */
    public List<String> getSpecialFiles ()
    {
        return this.specialFiles;
    }


    /**
     * Get the sample file paths.
     *
     * @return The file paths
     */
    public List<String> getSampleFiles ()
    {
        return this.sampleFiles;
    }


    /**
     * Get the other file paths.
     *
     * @return The file paths
     */
    public List<String> getOtherFiles ()
    {
        return this.otherFiles;
    }
}
