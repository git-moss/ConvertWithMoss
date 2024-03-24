// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.exception.FormatException;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;


/**
 * Detects recursively Yamaha YSFC files in folders. Files must end with <i>.x7u</i>, <i>.x7l</i>,
 * <i>.x8l</i> or <i>.x8l</i>.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcDetectorTask extends AbstractDetectorTask
{
    private static final String [] ENDINGS     =
    {
        ".x3w",
        ".x7u",
        ".x7l",
        ".x8u",
        ".x8l"
    };
    private static final String    YAMAHA_YSFC = "YAMAHA-YSFC";
    private static final int       HEADER_SIZE = 64;

    private int                    version;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected YamahaYsfcDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDINGS);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final InputStream in = new BufferedInputStream (new FileInputStream (file)))
        {
            return this.processFile (in, file);
        }
        catch (final IOException | FormatException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Process one YSFC file.
     *
     * @param in The input stream to read from
     * @param file The YSFC source file
     * @return The parsed multi samples
     * @throws IOException Could not process the file
     * @throws FormatException Found unexpected format of the file
     */
    private List<IMultisampleSource> processFile (final InputStream in, final File file) throws IOException, FormatException
    {
        final String headerTag = StreamUtils.readASCII (in, 16);
        StreamUtils.checkTag (YAMAHA_YSFC, headerTag.trim ());

        // The version in the form of 'A.B.C', e.g. '1.0.2'
        this.version = parseVersion (StreamUtils.readASCII (in, 16).trim ());

        // The size of the chunk catalog block
        final int catalogueSize = (int) StreamUtils.readUnsigned32 (in, true);

        // Padding
        in.skipNBytes (12);

        // The size of the library block
        long librarySize = StreamUtils.readUnsigned32 (in, true);
        // Library data present?
        if (librarySize >= 0xFFFFFFFFL)
            librarySize = 0;

        // Padding
        in.skipNBytes (12);

        readCatalog (in, HEADER_SIZE + catalogueSize + librarySize, catalogueSize);

        // Library data currently not used
        in.skipNBytes (librarySize);

        return this.createMultisample (readChunks (in));
    }


    /**
     * Create a multisample from the chunk data.
     *
     * @param chunks The YSFC chunks
     * @return The multisample(s)
     * @throws IOException COuld not read the multisample
     */
    private List<IMultisampleSource> createMultisample (final Map<String, YamahaYsfcChunk> chunks) throws IOException
    {
        final YamahaYsfcChunk ewfmChunk = chunks.get ("EWFM");
        final YamahaYsfcChunk ewimChunk = chunks.get ("EWIM");
        final YamahaYsfcChunk dwfmChunk = chunks.get ("DWFM");
        final YamahaYsfcChunk dwimChunk = chunks.get ("DWIM");
        if (ewfmChunk != null && ewimChunk != null && dwfmChunk != null && dwimChunk != null)
        {
            final YamahaYsfcEntry ewfmListChunk = ewfmChunk.getEntryListChunk ();
            final YamahaYsfcEntry ewimListChunk = ewimChunk.getEntryListChunk ();
            if (ewfmListChunk != null && ewimListChunk != null)
            {
                final String multiSampleName = ewfmListChunk.getItemName ();
                // TODO split into categoryID and name for versions >= 4?!
                this.notifier.logText ("Multisample: " + multiSampleName);

                final byte [] dwfmDataArrays = dwfmChunk.getDataArray ();
                Files.write (new File ("C:/Users/mos/Desktop/DWFM.bin").toPath (), dwfmDataArrays);

                final byte [] dwimDataArrays = dwimChunk.getDataArray ();
                Files.write (new File ("C:/Users/mos/Desktop/DWIM.bin").toPath (), dwimDataArrays);

                return Collections.emptyList ();
            }
        }

        this.notifier.logError ("IDS_YSFC_NO_MULTISAMPLE_DATA");
        return Collections.emptyList ();
    }


    /**
     * Read all chunks.
     *
     * @param in The input stream to read from
     * @return The chunks
     * @throws IOException Could not read the chunks
     */
    private static Map<String, YamahaYsfcChunk> readChunks (final InputStream in) throws IOException
    {
        final Map<String, YamahaYsfcChunk> chunks = new HashMap<> ();
        while (in.available () > 0)
        {
            final YamahaYsfcChunk chunk = new YamahaYsfcChunk ();
            chunk.read (in);
            chunks.put (chunk.getChunkID (), chunk);
        }
        return chunks;
    }


    /**
     * Read the catalog block which consists of tuples of chunk IDs and their offset into the file.
     *
     * @param in The input stream to read from
     * @param startOfChunks The start of the chunks in the file counted from the start of the file
     * @param catalogueSize The size of the chunk catalog
     * @return A map where the key is the offset and the value if the chunk ordered by the offset
     * @throws IOException Could not process the block
     */
    private static Map<Long, String> readCatalog (final InputStream in, final long startOfChunks, final int catalogueSize) throws IOException
    {
        final int count = catalogueSize / 8;
        final Map<Long, String> chunkOffsets = new TreeMap<> ();
        for (int i = 0; i < count; i++)
        {
            final String chunkID = StreamUtils.readASCII (in, 4);
            final long offset = StreamUtils.readUnsigned32 (in, true);
            chunkOffsets.put (Long.valueOf (offset - startOfChunks), chunkID);
        }
        return chunkOffsets;
    }


    /**
     * Parse the version in the form of 'A.B.C' (e.g. '1.0.2') into an integer ABC (e.g. 102).
     *
     * @param version The version to parse
     * @return The version as an integer
     */
    private static int parseVersion (final String version)
    {
        try
        {
            return Integer.parseInt ("" + version.charAt (0) + version.charAt (2) + version.charAt (4));
        }
        catch (final NumberFormatException | IndexOutOfBoundsException ex)
        {
            return 100;
        }
    }
}
