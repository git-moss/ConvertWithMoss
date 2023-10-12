// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.AuthoringApplication;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.AuthoringApplicationChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.SoundinfoChunkData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Can handle NKI files in Kontakt 5+ format. WAV and NCW files are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt5Type extends AbstractKontaktType
{
    private File sourceFolder;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public Kontakt5Type (final INotifier notifier)
    {
        super (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            return this.readNKI (sourceFolder, sourceFile, inputStream, metadataConfig, null);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        // Not yet supported
    }


    /**
     * Read and parse a file which uses this format type from the given input stream.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param inputStream The input stream to read from
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return The parsed multisample sources
     * @param metadataConfig Default metadata
     * @throws IOException Error reading the file
     */
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig, final Map<Long, ISampleMetadata> monolithSamples) throws IOException
    {
        this.sourceFolder = sourceFolder;

        final List<IMultisampleSource> sources = this.readNIContainer (inputStream, sourceFile, metadataConfig, monolithSamples != null);
        for (final IMultisampleSource multisampleSource: sources)
        {
            if (monolithSamples != null)
                replaceSamples (multisampleSource, monolithSamples);
        }
        return sources;
    }


    /**
     * Reads an NI container, which hopefully contains a NKI preset.
     *
     * @param inputStream The input stream to read from
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @param isMonolith True if the NKI is inside a monolith
     * @return The parsed multi-samples, if any
     * @throws IOException Could not read the container
     */
    private List<IMultisampleSource> readNIContainer (final InputStream inputStream, final File sourceFile, final IMetadataConfig metadataConfig, final boolean isMonolith) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (inputStream);

        final NIContainerChunk appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
        if (appChunk != null && appChunk.getData () instanceof final AuthoringApplicationChunkData appChunkData)
        {
            final AuthoringApplication application = appChunkData.getApplication ();
            if (application != AuthoringApplication.KONTAKT)
                throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_KONTAKT_FILE", application == null ? "Unknown" : application.getName ()));

            this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", "Container", appChunkData.getApplicationVersion (), isMonolith ? " - monolith" : "", "Little-Endian");

            final NIContainerChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
            if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
            {
                final List<IMultisampleSource> sources = this.convertPrograms (presetChunkData, sourceFile, metadataConfig);
                for (final IMultisampleSource multisampleSource: sources)
                    updateMetadata (niContainerItem, multisampleSource);
                return sources;
            }
        }

        this.notifier.logError ("IDS_NKI5_NO_PROGRAM_FOUND");
        return Collections.emptyList ();

    }


    /**
     * Update metadata from a SoundInfo chunk.
     *
     * @param niContainerItem The top item to start searching for the SoundInfo chunk.
     * @param source The source to update
     */
    private static void updateMetadata (final NIContainerItem niContainerItem, final IMultisampleSource source)
    {
        final NIContainerChunk soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk != null && soundInfoChunk.getData () instanceof final SoundinfoChunkData soundinfo)
        {
            final List<String> attributes = soundinfo.getAttributes ();
            final IMetadata metadata = source.getMetadata ();
            metadata.setKeywords (attributes.toArray (new String [attributes.size ()]));
            if (metadata.getCreator () == null)
                metadata.setCreator (soundinfo.getAuthor ());
            if (metadata.getCategory () == null && !attributes.isEmpty ())
                metadata.setCategory (attributes.get (0));
        }
    }


    /**
     * Convert the program object into one or more multisample sources.
     *
     * @param presetChunkData The preset chunk data which contains the preset information
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @return The multisample sources
     * @throws IOException Could not convert the program
     */
    private List<IMultisampleSource> convertPrograms (final PresetChunkData presetChunkData, final File sourceFile, final IMetadataConfig metadataConfig) throws IOException
    {
        final String n = metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final Program program: presetChunkData.parsePrograms ())
        {
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null, AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile));
            program.fillInto (multisampleSource);
            results.add (multisampleSource);
        }
        return results;
    }


    /**
     * Replace the sample source with the in-memory files from the monolith container in all groups.
     *
     * @param multisampleSource The source to update
     * @param monolithSamples The in-memory monolith samples
     * @throws IOException Could not find a file
     */
    private static void replaceSamples (final IMultisampleSource multisampleSource, final Map<Long, ISampleMetadata> monolithSamples) throws IOException
    {
        final Map<String, ISampleMetadata> sampleFileMap = new HashMap<> ();
        for (final ISampleMetadata ms: monolithSamples.values ())
            sampleFileMap.put (ms.getFilename (), ms);

        for (final IGroup group: multisampleSource.getGroups ())
        {
            final List<ISampleMetadata> newGroupSamples = new ArrayList<> ();
            for (final ISampleMetadata sampleMetadata: group.getSampleMetadata ())
            {
                final String filename = sampleMetadata.getFilename ();
                final ISampleMetadata memoryFile = sampleFileMap.get (filename);
                if (memoryFile == null)
                    throw new IOException (Functions.getMessage ("IDS_NKI5_NO_MATCHING_IN_MEMORY_FILE", filename));
                newGroupSamples.add (memoryFile);
            }
            group.setSampleMetadata (newGroupSamples);
        }
    }
}
