// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.format.ableton.AbletonBinaryDecoder.DecodedObject;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A Live Pack (*.alp) of Ableton Live: a folder with presets, Live Sets and samples which is
 * stored in one file. The file starts with 'pl-a' and the position of its table of contents, which
 * follows the content of all files at the end of the file. The table of contents is a tree of the
 * files and folders with the position and the size of the content of each file. Samples are
 * compressed with FLAC and carry '.flac' after their original name, e.g. 'Kick.wav.flac', all
 * other files are stored as they are. Most packs are additionally compressed as a whole with GZIP;
 * since the table of contents is at the end and the files are read from their positions, such a
 * pack is de-compressed into a temporary file first.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonLivePack
{
    private static final byte []  PACK_ID              =
    {
        'p',
        'l',
        '-',
        'a'
    };
    private static final int      HEADER_SIZE          = 12;
    /** Files and the table of contents are only read into memory up to this size. */
    private static final int      MAX_MEMORY_SIZE      = 256 * 1024 * 1024;
    private static final String   FLAC_ENDING          = ".flac";

    private static final String   TYPE_CONTENT_INFO    = "PackageContentInfo";
    private static final String   FIELD_ROOT_ITEM      = "RootItem";
    private static final String   FIELD_NAME           = "Name";
    private static final String   FIELD_CHILDREN       = "Children";
    private static final String   FIELD_IS_DIR         = "IsDir";
    private static final String   FIELD_OFFSET         = "FileDataOffset";
    private static final String   FIELD_SIZE           = "FileSize";
    private static final String   FIELD_IS_COMPRESSED  = "IsCompressed";

    private final File            packFile;
    private final File            dataFile;
    private final boolean         isTemporary;
    private final List<Entry>     files                = new ArrayList<> ();
    private final Map<String, Entry> entries           = new HashMap<> ();
    private final Map<String, Entry> entriesIgnoreCase = new HashMap<> ();
    private final Map<String, List<Entry>> filesByName = new HashMap<> ();
    private final Map<String, File> extractedFiles     = new HashMap<> ();
    private File                  extractFolder;


    /**
     * A file or a folder of the pack.
     *
     * @param path The path relative to the root of the pack, the folders separated by '/'
     * @param isFolder True if it is a folder
     * @param offset The position of the content of the file in the pack
     * @param size The number of bytes of the content of the file
     * @param isCompressed True if the file was compressed with FLAC when it was added to the pack
     */
    public record Entry (String path, boolean isFolder, long offset, long size, boolean isCompressed)
    {
        /**
         * Get the name of the file or folder.
         *
         * @return The name
         */
        public String getName ()
        {
            return this.path.substring (this.path.lastIndexOf ('/') + 1);
        }


        /**
         * Get the name of the file before it was added to the pack, which is the name without
         * '.flac' for a compressed sample.
         *
         * @return The name
         */
        public String getOriginalName ()
        {
            final String name = this.getName ();
            return this.isCompressed && name.toLowerCase (Locale.US).endsWith (FLAC_ENDING) ? name.substring (0, name.length () - FLAC_ENDING.length ()) : name;
        }


        /**
         * Get the path of the folder which contains the file or folder.
         *
         * @return The path, empty for the root of the pack
         */
        public String getFolder ()
        {
            final int pos = this.path.lastIndexOf ('/');
            return pos < 0 ? "" : this.path.substring (0, pos);
        }
    }


    /**
     * Open a Live Pack and read its table of contents. A pack which is compressed with GZIP is
     * de-compressed into a temporary file, which is deleted with {@link #dispose()}.
     *
     * @param packFile The pack file
     * @param notifier Where to report the progress
     * @return The pack
     * @throws IOException The file could not be read or is not a Live Pack
     */
    public static AbletonLivePack open (final File packFile, final INotifier notifier) throws IOException
    {
        if (!isCompressed (packFile))
            return new AbletonLivePack (packFile, packFile, false);

        notifier.log ("IDS_ADV_PACK_DECOMPRESSING");
        final File dataFile = File.createTempFile ("ConvertWithMoss-", ".alp");
        dataFile.deleteOnExit ();
        try
        {
            try (final InputStream in = new GZIPInputStream (new BufferedInputStream (new FileInputStream (packFile)), 65536); final OutputStream out = new BufferedOutputStream (new FileOutputStream (dataFile), 65536))
            {
                in.transferTo (out);
            }
            return new AbletonLivePack (packFile, dataFile, true);
        }
        catch (final IOException | RuntimeException ex)
        {
            deleteQuietly (dataFile);
            throw ex;
        }
    }


    /**
     * Constructor.
     *
     * @param packFile The pack file
     * @param dataFile The file which contains the uncompressed pack, the pack file itself if it is
     *            not compressed
     * @param isTemporary True if the data file is a temporary file
     * @throws IOException The file could not be read or is not a Live Pack
     */
    private AbletonLivePack (final File packFile, final File dataFile, final boolean isTemporary) throws IOException
    {
        this.packFile = packFile;
        this.dataFile = dataFile;
        this.isTemporary = isTemporary;

        final byte [] tableOfContents;
        try (final RandomAccessFile file = new RandomAccessFile (dataFile, "r"))
        {
            final byte [] header = new byte [HEADER_SIZE];
            if (file.length () < HEADER_SIZE)
                throw new IOException (Functions.getMessage ("IDS_ADV_PACK_NOT_A_PACK"));
            file.readFully (header);
            if (!Arrays.equals (header, 0, PACK_ID.length, PACK_ID, 0, PACK_ID.length))
                throw new IOException (Functions.getMessage ("IDS_ADV_PACK_NOT_A_PACK"));

            // The position of the table of contents follows the identifier
            final long position = ByteBuffer.wrap (header, PACK_ID.length, 8).order (ByteOrder.LITTLE_ENDIAN).getLong ();
            final long size = file.length () - position;
            if (position < HEADER_SIZE || size <= 0 || size > MAX_MEMORY_SIZE)
                throw new IOException (Functions.getMessage ("IDS_ADV_PACK_BROKEN", Long.toString (position)));
            tableOfContents = new byte [(int) size];
            file.seek (position);
            file.readFully (tableOfContents);
        }

        for (final DecodedObject object: new AbletonBinaryDecoder (tableOfContents).readRecords ())
            if (TYPE_CONTENT_INFO.equals (object.type ()))
            {
                final Optional<DecodedObject> rootItem = object.getObject (FIELD_ROOT_ITEM);
                if (rootItem.isPresent ())
                {
                    this.addChildren (rootItem.get (), "");
                    return;
                }
            }
        throw new IOException (Functions.getMessage ("IDS_ADV_PACK_BROKEN", "0"));
    }


    /**
     * Add the files and folders of a folder of the table of contents. The root folder carries the
     * name of the pack; like Live, which unpacks a pack into a folder named after the pack file, it
     * is not part of the paths.
     *
     * @param folder The folder
     * @param path The path of the folder, empty for the root
     */
    private void addChildren (final DecodedObject folder, final String path)
    {
        for (final DecodedObject child: folder.getObjects (FIELD_CHILDREN))
        {
            final String name = child.getString (FIELD_NAME);
            if (name.isEmpty ())
                continue;
            final String childPath = path.isEmpty () ? name : path + "/" + name;
            final boolean isFolder = child.getBoolean (FIELD_IS_DIR);
            final Entry entry = new Entry (childPath, isFolder, child.getLong (FIELD_OFFSET), child.getLong (FIELD_SIZE), child.getBoolean (FIELD_IS_COMPRESSED));
            this.entries.put (childPath, entry);
            this.entriesIgnoreCase.putIfAbsent (childPath.toLowerCase (Locale.US), entry);
            if (isFolder)
                this.addChildren (child, childPath);
            else
            {
                this.files.add (entry);
                this.filesByName.computeIfAbsent (entry.getOriginalName ().toLowerCase (Locale.US), _ -> new ArrayList<> ()).add (entry);
            }
        }
    }


    /**
     * Get the pack file.
     *
     * @return The file
     */
    public File getFile ()
    {
        return this.packFile;
    }


    /**
     * Get all files of the pack in the order of the table of contents.
     *
     * @return The files
     */
    public List<Entry> getFiles ()
    {
        return Collections.unmodifiableList (this.files);
    }


    /**
     * Read the complete content of a file of the pack.
     *
     * @param entry The file
     * @return The content
     * @throws IOException The file could not be read
     */
    public byte [] readContent (final Entry entry) throws IOException
    {
        if (entry.size () > MAX_MEMORY_SIZE)
            throw new IOException (Functions.getMessage ("IDS_ADV_PACK_BROKEN", Long.toString (entry.offset ())));

        try (final InputStream in = this.openStream (entry))
        {
            return in.readAllBytes ();
        }
    }


    /**
     * Open a stream which reads the content of a file of the pack.
     *
     * @param entry The file
     * @return The stream
     * @throws IOException The pack could not be opened
     */
    public InputStream openStream (final Entry entry) throws IOException
    {
        return new BufferedInputStream (new RangeInputStream (this.dataFile, entry.offset (), entry.size ()), 65536);
    }


    /**
     * Get a file by its path. A sample which is compressed with FLAC is found with the path of the
     * original file as well. If no file has exactly the path, the case of the letters is ignored.
     *
     * @param path The path relative to the root of the pack, may contain '.' and '..'
     * @return The file, empty if the path does not lead to a file of the pack
     */
    public Optional<Entry> findFile (final String path)
    {
        final Optional<String> normalizedPath = normalize (path);
        if (normalizedPath.isEmpty () || normalizedPath.get ().isEmpty ())
            return Optional.empty ();

        final String p = normalizedPath.get ();
        for (final String candidate: new String []
        {
            p,
            p + FLAC_ENDING
        })
        {
            final Entry entry = this.entries.get (candidate);
            if (entry != null && !entry.isFolder ())
                return Optional.of (entry);
        }
        for (final String candidate: new String []
        {
            p,
            p + FLAC_ENDING
        })
        {
            final Entry entry = this.entriesIgnoreCase.get (candidate.toLowerCase (Locale.US));
            if (entry != null && !entry.isFolder ())
                return Optional.of (entry);
        }
        return Optional.empty ();
    }


    /**
     * Search a file by its name in all folders of the pack. If several files have the name, the one
     * closest to the given folder is taken.
     *
     * @param fileName The name of the file, the name of the original file for a sample which is
     *            compressed with FLAC
     * @param folder The path of the folder near which to search
     * @return The file, empty if there is none with the name
     */
    public Optional<Entry> findFileByName (final String fileName, final String folder)
    {
        final List<Entry> candidates = this.filesByName.get (fileName.toLowerCase (Locale.US));
        if (candidates == null || candidates.isEmpty ())
            return Optional.empty ();

        Entry best = candidates.get (0);
        int bestLength = -1;
        for (final Entry candidate: candidates)
        {
            final int length = countCommonFolders (candidate.getFolder (), folder);
            if (length > bestLength)
            {
                best = candidate;
                bestLength = length;
            }
        }
        return Optional.of (best);
    }


    /**
     * Create the sample data of a sample file of the pack. A sample which is compressed with FLAC
     * is de-compressed when it is read. Other samples are copied into a temporary file which is
     * read like any sample file.
     *
     * @param entry The sample file
     * @param notifier Where to report errors
     * @return The sample data
     * @throws IOException The sample could not be read
     */
    public synchronized ISampleData createSampleData (final Entry entry, final INotifier notifier) throws IOException
    {
        if (entry.isCompressed () && entry.getName ().toLowerCase (Locale.US).endsWith (FLAC_ENDING))
            return new AbletonLivePackSampleData (this, entry);

        File sampleFile = this.extractedFiles.get (entry.path ());
        if (sampleFile == null)
        {
            if (this.extractFolder == null)
            {
                this.extractFolder = Files.createTempDirectory ("ConvertWithMoss-").toFile ();
                this.extractFolder.deleteOnExit ();
            }
            // One folder for each file keeps the name of the sample file, which can exist in
            // several folders of the pack
            final File folder = new File (this.extractFolder, Integer.toString (this.extractedFiles.size () + 1));
            if (!folder.mkdir ())
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", folder.getAbsolutePath ()));
            folder.deleteOnExit ();
            sampleFile = new File (folder, entry.getName ());
            sampleFile.deleteOnExit ();
            try (final InputStream in = this.openStream (entry); final OutputStream out = new FileOutputStream (sampleFile))
            {
                in.transferTo (out);
            }
            this.extractedFiles.put (entry.path (), sampleFile);
        }
        return AbstractDetector.createSampleData (sampleFile, notifier);
    }


    /**
     * Delete the temporary files of the pack. Its sample data cannot be read anymore afterwards.
     */
    public synchronized void dispose ()
    {
        if (this.isTemporary)
            deleteQuietly (this.dataFile);
        if (this.extractFolder != null)
        {
            for (final File sampleFile: this.extractedFiles.values ())
            {
                deleteQuietly (sampleFile);
                deleteQuietly (sampleFile.getParentFile ());
            }
            deleteQuietly (this.extractFolder);
            this.extractedFiles.clear ();
            this.extractFolder = null;
        }
    }


    /**
     * Test if a file is compressed with GZIP.
     *
     * @param file The file
     * @return True if it starts with the identifier of GZIP
     * @throws IOException The file could not be read
     */
    private static boolean isCompressed (final File file) throws IOException
    {
        try (final InputStream in = new FileInputStream (file))
        {
            return in.read () == 0x1F && in.read () == 0x8B;
        }
    }


    /**
     * Remove '.' and '..' from a path.
     *
     * @param path The path, the folders separated by '/' or '\'
     * @return The path, empty if it leads outside of the pack
     */
    private static Optional<String> normalize (final String path)
    {
        final Deque<String> parts = new ArrayDeque<> ();
        for (final String part: path.replace ('\\', '/').split ("/"))
        {
            if (part.isEmpty () || ".".equals (part))
                continue;
            if ("..".equals (part))
            {
                if (parts.isEmpty ())
                    return Optional.empty ();
                parts.removeLast ();
            }
            else
                parts.addLast (part);
        }
        return Optional.of (String.join ("/", parts));
    }


    /**
     * Count the folders at the start of two paths which are identical.
     *
     * @param path1 The first path
     * @param path2 The second path
     * @return The number of identical folders
     */
    private static int countCommonFolders (final String path1, final String path2)
    {
        final String [] folders1 = path1.split ("/");
        final String [] folders2 = path2.split ("/");
        int count = 0;
        while (count < folders1.length && count < folders2.length && folders1[count].equalsIgnoreCase (folders2[count]))
            count++;
        return count;
    }


    private static void deleteQuietly (final File file)
    {
        try
        {
            Files.deleteIfExists (file.toPath ());
        }
        catch (final IOException _)
        {
            // Deleted on exit
        }
    }


    /**
     * Reads a part of a file.
     */
    private static class RangeInputStream extends InputStream
    {
        private final RandomAccessFile file;
        private long                   remaining;


        /**
         * Constructor.
         *
         * @param file The file to read from
         * @param offset The position of the first byte to read
         * @param size The number of bytes to read
         * @throws IOException The file could not be opened
         */
        RangeInputStream (final File file, final long offset, final long size) throws IOException
        {
            this.file = new RandomAccessFile (file, "r");
            try
            {
                if (offset < 0 || size < 0 || offset + size > this.file.length ())
                    throw new IOException (Functions.getMessage ("IDS_ADV_PACK_BROKEN", Long.toString (offset)));
                this.file.seek (offset);
            }
            catch (final IOException ex)
            {
                this.file.close ();
                throw ex;
            }
            this.remaining = size;
        }


        /** {@inheritDoc} */
        @Override
        public int read () throws IOException
        {
            if (this.remaining <= 0)
                return -1;
            final int value = this.file.read ();
            if (value >= 0)
                this.remaining--;
            return value;
        }


        /** {@inheritDoc} */
        @Override
        public int read (final byte [] buffer, final int offset, final int length) throws IOException
        {
            if (this.remaining <= 0)
                return -1;
            final int count = this.file.read (buffer, offset, (int) Math.min (length, this.remaining));
            if (count > 0)
                this.remaining -= count;
            return count;
        }


        /** {@inheritDoc} */
        @Override
        public void close () throws IOException
        {
            this.file.close ();
        }
    }
}
