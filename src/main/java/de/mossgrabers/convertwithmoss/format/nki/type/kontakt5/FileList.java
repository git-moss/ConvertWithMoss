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
    private final List<String> sampleFilePaths = new ArrayList<> ();
    private String             absoluteSourcePath;
    private String             absoluteMonolithSourcePath;
    private String             resourceFile;
    private String             nkiFilename;
    private String             wallpaperFilename;
    private String             impulseResponseSampleFilename;
    private String             resourceFolder;


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

            this.readSpecialFiles (in);
        }
        else
        {
            final long version = StreamUtils.readUnsigned32 (in, false);
            if (version < 0 || version > 1)
                throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_FILELIST_VERSION", Long.toString (version)));

            if (version == 1)
                this.absoluteMonolithSourcePath = readFile (in);
        }

        this.readFiles (in);
        this.readMetadata (in, chunkID);
    }


    private void readSpecialFiles (final ByteArrayInputStream in) throws IOException
    {
        while (in.available () > 0)
        {
            final int type = StreamUtils.readSigned32 (in, false);
            switch (type)
            {
                case 0:
                    this.absoluteSourcePath = readFile (in);
                    return;

                // Original absolute folder path only set for monolith
                case 1:
                    this.absoluteMonolithSourcePath = readFile (in);
                    return;

                // NKR resource file
                case 2:
                    this.resourceFile = readFile (in);
                    this.resourceFolder = readFile (in);
                    return;

                // NKR resource file
                case 3:
                    this.resourceFile = readFile (in);
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_FILE_TYPE", Integer.toString (type)));
            }
        }
    }


    private void readMetadata (final ByteArrayInputStream in, final int chunkID) throws IOException
    {
        // Last changed date of files
        final int numFiles = this.sampleFilePaths.size ();
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

            // Reverb samples (and maybe wallpaper)
            while (in.available () > 0)
            {
                final int type = StreamUtils.readSigned32 (in, false);
                switch (type)
                {
                    // There are no additional files at all
                    case 0:
                        return;

                    // No more files
                    case 1:
                        return;

                    case 2:
                        this.nkiFilename = readFile (in);
                        break;

                    case 3:
                        this.nkiFilename = readFile (in);
                        this.wallpaperFilename = readFile (in);
                        break;

                    case 7:
                        this.impulseResponseSampleFilename = readFileShort (in);
                        break;

                    default:
                        throw new IOException (Functions.getMessage ("IDS_NKI5_UNKNOWN_FILE_TYPE", Integer.toString (type)));
                }
            }
        }
        else
        {
            // Final padding
            StreamUtils.readUnsigned32 (in, false);
        }
    }


    /**
     * Read all file names with their paths.
     *
     * @param in The data to parse
     * @throws IOException Could not read the data
     */
    private void readFiles (final ByteArrayInputStream in) throws IOException
    {
        final long fileCount = StreamUtils.readUnsigned32 (in, false);
        for (long i = 0; i < fileCount; i++)
            this.sampleFilePaths.add (readFile (in));
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
     * Read one file name with it's path. The segment count is only 1 byte.
     *
     * @param in The data to parse
     * @return The read file
     * @throws IOException Could not read the data
     */
    private static String readFileShort (final ByteArrayInputStream in) throws IOException
    {
        final StringBuilder sb = new StringBuilder ();
        final int segmentCount = in.read ();
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
            // Drive letter ASCII
            case 0:
                final String driveOld = StreamUtils.readASCII (in, 2).trim ();
                if (driveOld.isEmpty ())
                    sb.append ("/");
                else
                    sb.append (driveOld).append (":/");
                break;

            // Drive letter UTF-16
            case 1:
                final String drive = StreamUtils.readWithLengthUTF16 (in);
                if (drive.isEmpty ())
                    sb.append ("/");
                else
                    sb.append (drive).append (":/");
                break;

            // Path segment
            case 2:
                final String pathSegment = StreamUtils.readWithLengthUTF16 (in);
                sb.append (pathSegment).append ('/');
                break;

            // A flag but unknown for what, only seen so far with NCW files but not all of them
            case 3:
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
     * Get the sample file paths.
     *
     * @return The file paths
     */
    public List<String> getSampleFiles ()
    {
        return this.sampleFilePaths;
    }


    /**
     * Get the absolute path of the samples.
     *
     * @return The absolute sample path
     */
    public String getAbsoluteSourcePath ()
    {
        return this.absoluteSourcePath;
    }


    /**
     * Get the absolute monolith source path
     *
     * @return The absolute monolith source path
     */
    public String getAbsoluteMonolithSourcePath ()
    {
        return this.absoluteMonolithSourcePath;
    }


    /**
     * Get the resource file.
     *
     * @return The resource file
     */
    public String getResourceFile ()
    {
        return this.resourceFile;
    }


    /**
     * Get the NKI filename.
     *
     * @return The NKI filename
     */
    public String getNkiFilename ()
    {
        return this.nkiFilename;
    }


    /**
     * Get the wallpaper file name.
     *
     * @return The wallpaper file name
     */
    public String getWallpaperFilename ()
    {
        return this.wallpaperFilename;
    }


    /**
     * Get the file name of the impulse response.
     *
     * @return The file name of the impulse response
     */
    public String getImpulseResponseSampleFilename ()
    {
        return this.impulseResponseSampleFilename;
    }


    /**
     * Get the folder which contain the resources.
     *
     * @return The resource folder
     */
    public String getResourceFolder ()
    {
        return this.resourceFolder;
    }
}
