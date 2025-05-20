// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultMetadata;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.CompressionUtils;
import de.mossgrabers.convertwithmoss.file.FastLZ;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.nki.Magic;
import de.mossgrabers.convertwithmoss.format.nki.SoundinfoDocument;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktIcon;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt2.monolith.Kontakt2Monolith;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Program;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle NKI files in Kontakt 2 format including monolith. But only WAV files are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt2Type extends AbstractKontaktType
{
    private final K2MetadataFileHandler handler;
    private final PresetChunkData       kontakt5Preset = new PresetChunkData ();
    private final DefaultMetadata       metadata       = new DefaultMetadata ();


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     * @param isBigEndian Larger bytes are first, other wise smaller bytes are first (little-endian)
     */
    public Kontakt2Type (final INotifier notifier, final boolean isBigEndian)
    {
        super (notifier);

        this.isBigEndian = isBigEndian;
        this.handler = new K2MetadataFileHandler (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        final Kontakt2Header header = new Kontakt2Header (this.notifier, this.isBigEndian);
        header.read (fileAccess);
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", header.isFourDotTwo () ? "4.2" : "2", header.getKontaktVersion (), header.isMonolith () ? " - monolith" : "", this.isBigEndian ? "Big-Endian" : "Little-Endian");

        // NKM header does not contain meaningful metadata
        if (!sourceFile.getName ().endsWith (".nkm"))
            this.fillMetadata (header);

        final List<IMultisampleSource> multiSamples;
        if (header.isFourDotTwo ())
        {
            final long crc32Hash = StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);
            final int decompressedLength = (int) StreamUtils.readUnsigned32 (fileAccess, this.isBigEndian);

            // Skip padding
            StreamUtils.skipNBytes (fileAccess, 32);

            multiSamples = this.handleFastLZ (sourceFolder, sourceFile, fileAccess, header.getCompressedLength (), decompressedLength, crc32Hash, metadataConfig);
        }
        else
        {
            final Map<String, ISampleData> monolithSamples = header.isMonolith () ? new Kontakt2Monolith (fileAccess, this.isBigEndian).mapSamples () : Collections.emptyMap ();
            multiSamples = this.handleZLIB (sourceFolder, sourceFile, fileAccess, monolithSamples, metadataConfig);
        }

        this.handleSoundinfo (sourceFile, fileAccess, multiSamples);
        return multiSamples;
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        final Optional<String> result = this.handler.create (safeSampleFolderName, multisampleSource);
        if (result.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_NKI_NO_XML"));
        final ByteArrayOutputStream bout = new ByteArrayOutputStream ();
        CompressionUtils.writeZLIB (bout, result.get (), 1);
        final byte [] zlibContent = bout.toByteArray ();

        StreamUtils.writeUnsigned32 (out, Magic.KONTAKT2_INSTRUMENT_LE, false);
        StreamUtils.writeUnsigned32 (out, zlibContent.length, false);

        // Since we still do not understand how to calculate the checksum, go with a static header
        // with no metadata at all --> this does not work since e.g. the number of zones/groups
        // needs to be set
        out.write (Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/nki/Kontakt2_Static_Header.bin"));

        out.write (zlibContent);

        StreamUtils.writeUnsigned32 (out, Magic.SOUNDINFO_HEADER_LE, false);
        StreamUtils.writeUnsigned32 (out, Magic.SOUNDINFO_HEADER_VERSION_LE, false);

        final IMetadata m = multisampleSource.getMetadata ();
        final SoundinfoDocument soundinfoDocument = new SoundinfoDocument (m.getCreator (), m.getCategory ());
        final byte [] data = soundinfoDocument.createDocument (multisampleSource.getName ()).getBytes (StandardCharsets.UTF_8);
        StreamUtils.writeUnsigned32 (out, data.length, false);
        out.write (data);
    }


    /**
     * Handles the Kontakt 4.2 FastLZ section.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param fileAccess The random access file to read from
     * @param compressedDataSize The size of the compressed data
     * @param uncompressedSize The size of the uncompressed data size
     * @param crc32Hash The CRC32 hash of the compressed data
     * @param metadataConfig Default metadata
     * @return All parsed multi-samples
     * @throws IOException Could decode the multi-samples
     */
    private List<IMultisampleSource> handleFastLZ (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final int compressedDataSize, final int uncompressedSize, final long crc32Hash, final IMetadataConfig metadataConfig) throws IOException
    {
        final byte [] compressedData = new byte [compressedDataSize];
        fileAccess.readFully (compressedData);

        if (MathUtils.calcCRC32 (compressedData) != crc32Hash)
            this.notifier.logError ("IDS_NKI_CRC32_MISMATCH");

        this.kontakt5Preset.readKontaktPresetChunks (FastLZ.uncompress (compressedData, uncompressedSize));

        final String n = metadataConfig.isPreferFolderName () ? sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), sourceFolder, n);

        final List<IMultisampleSource> results = new ArrayList<> ();
        final List<Program> programs = this.kontakt5Preset.getPrograms ();
        final List<String> filePaths = this.kontakt5Preset.getFilePaths ();
        for (final Program program: programs)
        {
            final String programName = program.getName ();
            final String mappingName = AudioFileUtils.subtractPaths (sourceFolder, sourceFile) + " : " + programName;
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null, mappingName);
            this.fillInto (multisampleSource, program, programs.size () > 1 ? new String []
            {
                programName
            } : parts, filePaths);
            results.add (multisampleSource);
        }
        return results;
    }


    /**
     * Handles the ZLIB section with the contained XML document.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param fileAccess The random access file to read from
     * @param monolithSamples The samples that are contained in the NKI monolith otherwise null
     * @param metadataConfig Default metadata
     * @return All parsed multi-samples
     * @throws IOException Could not parse the data
     */
    private List<IMultisampleSource> handleZLIB (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final Map<String, ISampleData> monolithSamples, final IMetadataConfig metadataConfig) throws IOException
    {
        String xmlCode = CompressionUtils.readZLIB (fileAccess);
        try
        {
            xmlCode = xmlCode.trim ();
            return this.handler.parse (sourceFolder, sourceFile, xmlCode, metadataConfig, monolithSamples);
        }
        catch (final UnsupportedEncodingException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Read the sound info block after the ZLIB block.
     *
     * @param sourceFile The source file which contains the XML document
     * @param fileAccess The random access file to read from
     * @param multiSamples The multi-samples to update
     */
    private void handleSoundinfo (final File sourceFile, final RandomAccessFile fileAccess, final List<IMultisampleSource> multiSamples)
    {
        Optional<SoundinfoDocument> soundinfo = Optional.empty ();

        try
        {
            final int numOfPendingbytes = (int) (sourceFile.length () - fileAccess.getFilePointer ());
            if (numOfPendingbytes > 0)
            {
                // Seems to be always little-endian or missing
                final int soundinfoHeader = (int) StreamUtils.readUnsigned32 (fileAccess, false);
                final int soundinfoHeaderVersion = (int) StreamUtils.readUnsigned32 (fileAccess, false);
                if (soundinfoHeader != Magic.SOUNDINFO_HEADER_LE || soundinfoHeaderVersion != Magic.SOUNDINFO_HEADER_VERSION_LE)
                    this.notifier.logError ("IDS_NKI_UNSOUND_SOUNDINFO", "Unsound header");

                final int soundinfoSize = (int) StreamUtils.readUnsigned32 (fileAccess, false);
                final byte [] rest = StreamUtils.readNBytes (fileAccess, soundinfoSize);
                soundinfo = Optional.of (new SoundinfoDocument (new String (rest, StandardCharsets.UTF_8)));
            }
        }
        catch (final SAXException ex)
        {
            this.notifier.logError ("IDS_NKI_UNSOUND_SOUNDINFO", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        updateMetadata (multiSamples, this.metadata, soundinfo);
    }


    private void fillMetadata (final Kontakt2Header header)
    {
        final int iconID = header.getIconID ();
        // 0 is Organ but might simply not be set there use auto-detection instead, 28 is
        // New where we use auto-detection as well
        if (iconID > 0 && iconID < 28)
        {
            final String iconName = KontaktIcon.getName (iconID);
            if (iconName != null)
                this.metadata.setCategory (iconName);
        }

        this.metadata.setCreator (header.getAuthor ());
        this.metadata.setCreationDateTime (header.getCreation ());

        final String category1Name = header.getCategory1Name ();
        if (!Kontakt2Header.CATEGORY_OTHER.equals (category1Name))
            this.metadata.setCategory (category1Name);

        final String category2Name = header.getCategory2Name ();
        final String category3Name = header.getCategory3Name ();
        final List<String> keywords = new ArrayList<> (2);
        if (!Kontakt2Header.CATEGORY_OTHER.equals (category2Name))
            keywords.add (category2Name);
        if (!Kontakt2Header.CATEGORY_OTHER.equals (category3Name))
            keywords.add (category3Name);
        if (!keywords.isEmpty ())
            this.metadata.setKeywords (keywords.toArray (new String [keywords.size ()]));

        String additionalInfo = "";
        final String website = header.getWebsite ();
        if (!website.isBlank ())
            additionalInfo += "\nWebsite: " + website;
        this.metadata.setDescription (additionalInfo);
    }


    /**
     * Update the metadata info on all multi-samples.
     *
     * @param multiSamples The multi-samples to update
     * @param headerMetadata The metadata found in the header
     * @param soundInfo The sound info
     */
    private static void updateMetadata (final List<IMultisampleSource> multiSamples, final IMetadata headerMetadata, final Optional<SoundinfoDocument> soundInfo)
    {
        final Set<String> categories;
        if (soundInfo.isPresent ())
        {
            final SoundinfoDocument soundinfoDocument = soundInfo.get ();

            categories = soundinfoDocument.getCategories ();
            if (!categories.isEmpty ())
                headerMetadata.setCategory (categories.iterator ().next ());

            final String soundAuthor = soundinfoDocument.getAuthor ();
            if (soundAuthor != null && !soundAuthor.isBlank ())
                headerMetadata.setCreator (soundAuthor);
        }
        else
            categories = Collections.emptySet ();

        final String creator = headerMetadata.getCreator ();
        final String category = headerMetadata.getCategory ();

        final boolean isMulti = multiSamples.size () > 1;
        for (final IMultisampleSource multiSample: multiSamples)
        {
            final IMetadata metadata = multiSample.getMetadata ();
            if (!isMulti && (metadata.getCreator () == null || metadata.getCreator ().isBlank ()))
                metadata.setCreator (creator);
            if (!isMulti && (metadata.getCategory () == null || metadata.getCategory ().isBlank ()))
                metadata.setCategory (category);

            final List<String> soundCategories = new ArrayList<> ();
            soundCategories.addAll (categories);
            Collections.addAll (soundCategories, metadata.getKeywords ());
            metadata.setKeywords (TagDetector.detectKeywords (soundCategories.toArray (new String [soundCategories.size ()])));

            // Update the description
            final StringBuilder sb = new StringBuilder (metadata.getDescription ());
            final String description = headerMetadata.getDescription ();
            if (description != null && !description.isBlank ())
            {
                if (!sb.isEmpty ())
                    sb.append ('\n');
                sb.append (description);
            }
            metadata.setDescription (sb.toString ().trim ());
        }
    }
}
