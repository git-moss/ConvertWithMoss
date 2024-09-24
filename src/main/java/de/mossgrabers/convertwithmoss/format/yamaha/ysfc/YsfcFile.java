// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Read/write a Yamaha YSFC file.
 *
 * @author Jürgen Moßgraber
 */
public class YsfcFile
{
    private static final String                YAMAHA_YSFC = "YAMAHA-YSFC";
    private static final int                   HEADER_SIZE = 64;

    private File                               sourceFile;
    private String                             versionStr;
    private int                                version;
    private int                                maxEntryID  = 0xFFFFFFFF;
    private final Map<String, YamahaYsfcChunk> chunks      = HashMap.newHashMap (4);


    /**
     * Default constructor.
     */
    public YsfcFile ()
    {
        this.createChunks ("EWFM", "DWFM", "EWIM", "DWIM");
    }


    private void createChunks (final String... chunkIDs)
    {
        for (final String chunkID: chunkIDs)
            this.chunks.put (chunkID, new YamahaYsfcChunk (chunkID));
    }


    /**
     * Constructor.
     *
     * @param file The YSFC file to read
     * @throws IOException Could not read the file
     */
    public YsfcFile (final File file) throws IOException
    {
        this.sourceFile = file;

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            this.read (in);
        }
    }


    /**
     * Get the source file.
     *
     * @return The source file
     */
    public File getSourceFile ()
    {
        return this.sourceFile;
    }


    /**
     * Get the version text, e.g. '4.0.4'.
     *
     * @return The text
     */
    public String getVersionStr ()
    {
        return this.versionStr;
    }


    /**
     * Set the version text, e.g. '4.0.4'.
     *
     * @param versionStr The text
     */
    public void setVersionStr (final String versionStr)
    {
        this.versionStr = versionStr;
    }


    /**
     * Get the version number as an integer, e.g. 404.
     *
     * @return The version number
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Get the chunks.
     *
     * @return The chunks, the key is the chunks name
     */
    public Map<String, YamahaYsfcChunk> getChunks ()
    {
        return this.chunks;
    }


    /**
     * Read and parse a YSFC file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     */
    private void read (final InputStream inputStream) throws IOException
    {
        final String headerTag = StreamUtils.readASCII (inputStream, 16);
        try
        {
            StreamUtils.checkTag (YAMAHA_YSFC, headerTag.trim ());
        }
        catch (final FormatException ex)
        {
            throw new IOException (ex);
        }

        // The version in the form of 'A.B.C', e.g. '1.0.2'. Older versions may have appended 0xFF
        // instead of 0x00
        this.versionStr = createAsciiString (inputStream.readNBytes (16));
        this.version = parseVersion (this.versionStr.trim ());

        // The size of the chunk catalog block
        final int catalogSize = (int) StreamUtils.readUnsigned32 (inputStream, true);

        // Padding
        inputStream.skipNBytes (12);

        // The size of the library block
        long librarySize = StreamUtils.readUnsigned32 (inputStream, true);
        // Library data present?
        if (librarySize >= 0xFFFFFFFFL)
            librarySize = 0;

        // Padding
        inputStream.skipNBytes (8);

        this.maxEntryID = (int) StreamUtils.readUnsigned32 (inputStream, true);

        // Skip the catalog since we read the full file anyway and it does not contain any
        // additional information
        inputStream.skipNBytes (catalogSize);

        // Reference to uses library - only self-contained libraries are supported!
        inputStream.skipNBytes (librarySize);

        this.readChunks (inputStream, this.version);
    }


    /**
     * Write a YSFC file.
     *
     * @param outputStream Where to write the file to
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream outputStream) throws IOException
    {
        final List<YamahaYsfcChunk> orderedChunks = this.sortAndUpdateChunks ();
        final int catalogSize = orderedChunks.size () * 8;

        StreamUtils.writeASCII (outputStream, YAMAHA_YSFC, 16);
        StreamUtils.writeASCII (outputStream, this.versionStr, 16);
        StreamUtils.writeUnsigned32 (outputStream, catalogSize, true);
        StreamUtils.padBytes (outputStream, 12, 0xFF);

        // The size of the library block - fixed
        final int librarySize = 81;
        StreamUtils.writeUnsigned32 (outputStream, librarySize, true);

        StreamUtils.padBytes (outputStream, 8, 0xFF);

        StreamUtils.writeUnsigned32 (outputStream, this.maxEntryID, true);

        writeCatalog (outputStream, orderedChunks, HEADER_SIZE + catalogSize + librarySize);

        // Write empty library references
        StreamUtils.padBytes (outputStream, librarySize - 1, 0xFF);
        StreamUtils.padBytes (outputStream, 1, 0x00);

        writeChunks (outputStream, orderedChunks);
    }


    /**
     * Write the catalog block which consists of tuples of chunk IDs and their offset into the file.
     *
     * @param out The input stream to read from
     * @param startOfChunks The start of the chunks in the file counted from the start of the file
     * @throws IOException Could not process the block
     */
    private static void writeCatalog (final OutputStream out, final List<YamahaYsfcChunk> orderedChunks, final int startOfChunks) throws IOException
    {
        int offset = startOfChunks;
        for (final YamahaYsfcChunk chunk: orderedChunks)
        {
            StreamUtils.writeASCII (out, chunk.getChunkID (), 4);
            StreamUtils.writeUnsigned32 (out, offset, true);
            offset += 8 + chunk.getChunkLength ();
        }
    }


    /**
     * Read all chunks.
     *
     * @param in The input stream to read from
     * @param version The format version of the key-bank, e.g. 404 for version 4.0.4
     * @throws IOException Could not read the chunks
     */
    private void readChunks (final InputStream in, final int version) throws IOException
    {
        while (in.available () > 0)
        {
            final YamahaYsfcChunk chunk = new YamahaYsfcChunk ();
            chunk.read (in, version);
            this.chunks.put (chunk.getChunkID (), chunk);
        }
    }


    /**
     * Write all chunks.
     *
     * @param out The output stream to read from
     * @throws IOException Could not write the chunks
     */
    private static void writeChunks (final OutputStream out, final List<YamahaYsfcChunk> orderedChunks) throws IOException
    {
        for (final YamahaYsfcChunk chunk: orderedChunks)
            chunk.write (out);
    }


    /**
     * Parse the version in the form of 'A.B.C' (e.g. '1.0.2') into an integer ABC (e.g. 102).
     *
     * @param versionStr The version to parse
     * @return The version as an integer
     */
    private static int parseVersion (final String versionStr)
    {
        try
        {
            return Integer.parseInt ("" + versionStr.charAt (0) + versionStr.charAt (2) + versionStr.charAt (4));
        }
        catch (final NumberFormatException | IndexOutOfBoundsException ex)
        {
            return 100;
        }
    }


    private static String createAsciiString (final byte [] byteArray)
    {
        int lastAsciiIndex = byteArray.length - 1;
        while (lastAsciiIndex >= 0 && (byteArray[lastAsciiIndex] < 0 || byteArray[lastAsciiIndex] > 127))
            lastAsciiIndex--;
        return new String (byteArray, 0, lastAsciiIndex + 1, StandardCharsets.US_ASCII);
    }


    private List<YamahaYsfcChunk> sortAndUpdateChunks ()
    {
        final YamahaYsfcChunk ewfm = this.chunks.get ("EWFM");
        final YamahaYsfcChunk dwfm = this.chunks.get ("DWFM");
        final YamahaYsfcChunk ewim = this.chunks.get ("EWIM");
        final YamahaYsfcChunk dwim = this.chunks.get ("DWIM");

        updateEntryReferences (ewfm, dwfm, 10001);
        updateEntryReferences (ewim, dwim, 10002);
        this.maxEntryID = 10001 + ewfm.getEntryListChunks ().size () * 2;

        final List<YamahaYsfcChunk> orderedChunks = new ArrayList<> ();
        orderedChunks.add (ewfm);
        orderedChunks.add (dwfm);
        orderedChunks.add (ewim);
        orderedChunks.add (dwim);
        return orderedChunks;
    }


    /**
     * Update the references in the entry list chunk to the referenced data chunk items.
     *
     * @param entryChunk The entry chunk with the entry item list
     * @param dataChunk The data chunk with the referenced data
     * @param entryID The entryID to start with
     */
    private static void updateEntryReferences (final YamahaYsfcChunk entryChunk, final YamahaYsfcChunk dataChunk, final int entryID)
    {
        final List<YamahaYsfcEntry> entryListChunks = entryChunk.getEntryListChunks ();
        final List<byte []> dataArrays = dataChunk.getDataArrays ();
        int offset = 12;
        for (int i = 0; i < entryListChunks.size (); i++)
        {
            final YamahaYsfcEntry ysfcEntry = entryListChunks.get (i);
            final byte [] dataArray = dataArrays.get (i);

            ysfcEntry.setEntryID (entryID + i * 2);
            ysfcEntry.setCorrespondingDataOffset (offset);
            ysfcEntry.setCorrespondingDataSize (dataArray.length);

            offset += 8 + dataArray.length;
        }
    }


    /**
     * Fill the wave data entries and data lists into the respective chunks.
     *
     * @param keyBankEntry The key-bank entry
     * @param keybankList The key-bank data arrays
     * @param waveDataEntry The wave data entry
     * @param waveDataList The wave data items
     * @throws IOException Could not store the data
     */
    public void fillWaveChunks (final YamahaYsfcEntry keyBankEntry, final List<YamahaYsfcKeybank> keybankList, final YamahaYsfcEntry waveDataEntry, final List<YamahaYsfcWaveData> waveDataList) throws IOException
    {
        // Waveform Metadata
        this.chunks.get ("EWFM").addEntry (keyBankEntry);
        final YamahaYsfcChunk dwfm = this.chunks.get ("DWFM");
        final ByteArrayOutputStream dwfmContentOutput = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned16 (dwfmContentOutput, keybankList.size (), false);
        StreamUtils.padBytes (dwfmContentOutput, 2);
        for (int i = 0; i < keybankList.size (); i++)
        {
            final ByteArrayOutputStream dataOutput = new ByteArrayOutputStream ();
            keybankList.get (i).write (dataOutput);
            dwfmContentOutput.write (dataOutput.toByteArray ());
        }
        dwfm.addDataArray (dwfmContentOutput.toByteArray ());

        // Wave Data
        this.chunks.get ("EWIM").addEntry (waveDataEntry);
        final YamahaYsfcChunk dwim = this.chunks.get ("DWIM");
        final ByteArrayOutputStream dwimContentOutput = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (dwimContentOutput, waveDataList.size (), true);
        for (final YamahaYsfcWaveData element: waveDataList)
        {
            final ByteArrayOutputStream dataOutput = new ByteArrayOutputStream ();
            element.write (dataOutput);
            dwimContentOutput.write (dataOutput.toByteArray ());
        }
        dwim.addDataArray (dwimContentOutput.toByteArray ());
    }
}
