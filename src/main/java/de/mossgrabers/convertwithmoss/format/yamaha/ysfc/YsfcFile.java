// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
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

import de.mossgrabers.convertwithmoss.core.IStreamable;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Read/write a Yamaha YSFC file.
 *
 * @author Jürgen Moßgraber
 */
public class YsfcFile
{
    private static final String                YAMAHA_YSFC              = "YAMAHA-YSFC";
    private static final int                   HEADER_SIZE              = 64;

    private static final String []             CHUNKS_ONLY_WAVEFORMS    = new String []
    {
        YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_METADATA,
        YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_DATA,
        YamahaYsfcChunk.DATA_LIST_WAVEFORM_METADATA,
        YamahaYsfcChunk.DATA_LIST_WAVEFORM_DATA
    };

    private static final String []             CHUNKS_WITH_PERFORMANCES = new String []
    {
        YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE,
        YamahaYsfcChunk.DATA_LIST_PERFORMANCE,
        YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_METADATA,
        YamahaYsfcChunk.DATA_LIST_WAVEFORM_METADATA,
        YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_DATA,
        YamahaYsfcChunk.DATA_LIST_WAVEFORM_DATA
    };

    private File                               sourceFile;
    private String                             versionStr;
    private int                                version;
    private int                                maxEntryID               = 0xFFFFFFFF;
    private final Map<String, YamahaYsfcChunk> chunks                   = HashMap.newHashMap (4);


    /**
     * Default constructor.
     *
     * @param addPerformances Add performance chunks if true
     */
    public YsfcFile (final boolean addPerformances)
    {
        this.createChunks (addPerformances ? CHUNKS_WITH_PERFORMANCES : CHUNKS_ONLY_WAVEFORMS);
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
        this.versionStr = versionStr.trim ();
        this.version = parseVersion (this.versionStr);
    }


    /**
     * Get the version as an integer, e.g. 404 for '4.0.4'.
     *
     * @return The version value
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Get the YSFC version.
     *
     * @return The version
     */
    public YamahaYsfcFileFormat getFileFormat ()
    {
        return YamahaYsfcFileFormat.get (this.version);
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
        this.setVersionStr (createAsciiString (inputStream.readNBytes (16)));

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
     * @param orderedChunks The ordered list of chunks
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
     * @param orderedChunks The ordered list of chunks to write
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
    public static int parseVersion (final String versionStr)
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
        final YamahaYsfcChunk epfm = this.chunks.get (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE);
        final YamahaYsfcChunk dpfm = this.chunks.get (YamahaYsfcChunk.DATA_LIST_PERFORMANCE);
        final YamahaYsfcChunk ewfm = this.chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_METADATA);
        final YamahaYsfcChunk dwfm = this.chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_METADATA);
        final YamahaYsfcChunk ewim = this.chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_DATA);
        final YamahaYsfcChunk dwim = this.chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_DATA);

        if (epfm != null && dpfm != null)
            updateCorrespondingDataOffsets (epfm, dpfm);
        updateCorrespondingDataOffsets (ewfm, dwfm);
        updateCorrespondingDataOffsets (ewim, dwim);

        this.maxEntryID = 10001; // 0x2711

        if (epfm != null && dpfm != null)
        {
            final List<YamahaYsfcEntry> epfmListChunks = epfm.getEntryListChunks ();
            for (final YamahaYsfcEntry epfmListChunk: epfmListChunks)
                epfmListChunk.setEntryID (this.maxEntryID++);
        }
        final List<YamahaYsfcEntry> ewfmListChunks = ewfm.getEntryListChunks ();
        final List<YamahaYsfcEntry> ewimListChunks = ewim.getEntryListChunks ();
        for (int i = 0; i < ewfmListChunks.size (); i++)
        {
            ewfmListChunks.get (i).setEntryID (this.maxEntryID++);
            ewimListChunks.get (i).setEntryID (this.maxEntryID++);
        }

        final List<YamahaYsfcChunk> orderedChunks = new ArrayList<> ();
        if (epfm != null)
            orderedChunks.add (epfm);
        orderedChunks.add (ewfm);
        orderedChunks.add (ewim);
        if (dpfm != null)
            orderedChunks.add (dpfm);
        orderedChunks.add (dwfm);
        orderedChunks.add (dwim);
        return orderedChunks;
    }


    /**
     * Update the offsets in the entry list chunk to the referenced data chunk items.
     *
     * @param entryChunk The entry chunk with the entry item list
     * @param dataChunk The data chunk with the referenced data
     */
    private static void updateCorrespondingDataOffsets (final YamahaYsfcChunk entryChunk, final YamahaYsfcChunk dataChunk)
    {
        final List<YamahaYsfcEntry> entryListChunks = entryChunk.getEntryListChunks ();
        final List<byte []> dataArrays = dataChunk.getDataArrays ();
        int offset = 12;
        for (int i = 0; i < entryListChunks.size (); i++)
        {
            final byte [] dataArray = dataArrays.get (i);

            final YamahaYsfcEntry ysfcEntry = entryListChunks.get (i);
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
    public void addWaveChunks (final YamahaYsfcEntry keyBankEntry, final List<YamahaYsfcKeybank> keybankList, final YamahaYsfcEntry waveDataEntry, final List<YamahaYsfcWaveData> waveDataList) throws IOException
    {
        // Waveform Metadata
        this.chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_METADATA).addEntry (keyBankEntry);
        final YamahaYsfcChunk dwfm = this.chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_METADATA);
        final ByteArrayOutputStream dwfmContentOutput = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned16 (dwfmContentOutput, keybankList.size (), false);
        StreamUtils.padBytes (dwfmContentOutput, 2);
        for (final YamahaYsfcKeybank element: keybankList)
        {
            final ByteArrayOutputStream dataOutput = new ByteArrayOutputStream ();
            element.write (dataOutput);
            dwfmContentOutput.write (dataOutput.toByteArray ());
        }
        dwfm.addDataArray (dwfmContentOutput.toByteArray ());

        // Wave Data
        this.chunks.get (YamahaYsfcChunk.ENTRY_LIST_WAVEFORM_DATA).addEntry (waveDataEntry);
        final YamahaYsfcChunk dwim = this.chunks.get (YamahaYsfcChunk.DATA_LIST_WAVEFORM_DATA);
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


    /**
     * Fill a combination of Exxx and Dxxx.
     *
     * @param entryListID The ID of the entry list
     * @param dataListID The ID of the data list
     * @param entry The entry
     * @param data The data
     * @param <T> The type of the data
     * @throws IOException Could not store the data
     */
    public <T extends IStreamable> void fillChunkPair (final String entryListID, final String dataListID, final YamahaYsfcEntry entry, final T data) throws IOException
    {
        this.chunks.get (entryListID).addEntry (entry);
        final ByteArrayOutputStream dataOutput = new ByteArrayOutputStream ();
        data.write (dataOutput);
        this.chunks.get (dataListID).addDataArray (dataOutput.toByteArray ());
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int indent = level * 4;
        final int indentNext = indent + 4;

        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("Ysfc File: ", indent)).append (this.sourceFile.getAbsolutePath ()).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Version  : ", indentNext)).append (this.versionStr).append (" (").append (this.version).append (")\n");
        sb.append (StringUtils.padLeftSpaces ("Max.Entry: ", indentNext)).append (StringUtils.formatDataValue (this.maxEntryID)).append ("\n");

        for (final YamahaYsfcChunk chunk: this.getChunks ().values ())
            sb.append (chunk.dump (level + 2));

        return sb.toString ();
    }
}
