// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
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

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.nki.AbstractNKIMetadataFileHandler;
import de.mossgrabers.convertwithmoss.format.nki.type.AbstractKontaktType;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.AuthoringApplication;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.AuthoringApplicationChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.AuthorizationChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata.SoundinfoChunkData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle NKI files in Kontakt 5+ format. WAV and NCW files are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt5Type extends AbstractKontaktType
{
    private static final byte [] NKI_TEMPLATE;

    static
    {
        try
        {
            NKI_TEMPLATE = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/nki/Kontakt680Template.nki");
        }
        catch (final IOException ex)
        {
            throw new RuntimeException (ex);
        }
    }

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
    public IPerformanceSource readNKM (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            return this.readNKM (sourceFolder, sourceFile, inputStream, metadataConfig, null);
        }
    }


    /**
     * Read and parse a NKM file which uses this format type from the given input stream.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param inputStream The input stream to read from
     * @param metadataConfig Default metadata
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the file
     */
    public IPerformanceSource readNKM (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        this.sourceFolder = sourceFolder;

        final NIContainerItem niContainerItem = readNIContainer (inputStream);
        final List<Pair<IMultisampleSource, Program>> sources = this.readMultisampleSources (niContainerItem, sourceFile, metadataConfig, monolithSamples);
        final DefaultPerformanceSource performanceSource = new DefaultPerformanceSource ();
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile));
        return readMultiConfiguration (niContainerItem, sources, performanceSource) ? performanceSource : null;
    }


    private static boolean readMultiConfiguration (final NIContainerItem niContainerItem, final List<Pair<IMultisampleSource, Program>> sources, final DefaultPerformanceSource performanceSource)
    {
        final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk == null || !(presetChunk.getData () instanceof final PresetChunkData presetChunkData))
            return false;

        final MultiConfiguration multiConfiguration = presetChunkData.getMultiConfiguration ();
        if (multiConfiguration == null)
            return false;

        final List<MultiInstrument> multiInstruments = multiConfiguration.getMultiInstruments ();
        for (int i = 0; i < sources.size (); i++)
        {
            final Pair<IMultisampleSource, Program> source = sources.get (i);
            final IMultisampleSource multisampleSource = source.getKey ();
            final Program program = source.getValue ();
            final int midiChannel = i < multiInstruments.size () ? multiInstruments.get (program.getSlotIndex ()).getMidiChannel () - 1 : 0;
            final DefaultInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, midiChannel);
            instrumentSource.setClipKeyLow (program.getClipKeyLow ());
            instrumentSource.setClipKeyHigh (program.getClipKeyHigh ());
            performanceSource.addInstrument (instrumentSource);
        }
        return true;
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


    /**
     * Read and parse a NKI file which uses this format type from the given input stream.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param inputStream The input stream to read from
     * @param metadataConfig Default metadata
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the file
     */
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        this.sourceFolder = sourceFolder;
        final NIContainerItem niContainerItem = readNIContainer (inputStream);
        final List<Pair<IMultisampleSource, Program>> sources = this.readMultisampleSources (niContainerItem, sourceFile, metadataConfig, monolithSamples);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> (sources.size ());
        for (final Pair<IMultisampleSource, Program> source: sources)
            multisampleSources.add (source.getKey ());
        return multisampleSources;
    }


    /**
     * Reads from an NI container, which hopefully contains a NKI preset.
     *
     * @param niContainerItem The NI container item to read from
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return The parsed multi-samples, if any
     * @throws IOException Could not read the container
     */
    private List<Pair<IMultisampleSource, Program>> readMultisampleSources (final NIContainerItem niContainerItem, final File sourceFile, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        final boolean isMonolith = monolithSamples != null;

        final NIContainerDataChunk appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
        if (appChunk != null && appChunk.getData () instanceof final AuthoringApplicationChunkData appChunkData)
        {
            final AuthoringApplication application = appChunkData.getApplication ();
            if (application != AuthoringApplication.KONTAKT)
                throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_KONTAKT_FILE", application == null ? "Unknown" : application.getName ()));

            this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", "Container", appChunkData.getApplicationVersion (), isMonolith ? " - monolith" : "", "Little-Endian");

            final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
            if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
            {
                final List<Pair<IMultisampleSource, Program>> sources = this.convertPrograms (presetChunkData, sourceFile, metadataConfig, monolithSamples != null);
                final String n = metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
                final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);
                for (final Pair<IMultisampleSource, Program> source: sources)
                    updateMetadata (niContainerItem, source.getKey (), metadataConfig, parts);

                for (final Pair<IMultisampleSource, Program> source: sources)
                    if (monolithSamples != null)
                        replaceSamples (source.getKey (), monolithSamples);

                return sources;
            }
        }

        // Check for encrypted sections
        for (final NIContainerDataChunk autorizationChunk: niContainerItem.findAll (NIContainerChunkType.AUTHORIZATION))
            if (autorizationChunk.getData () instanceof final AuthorizationChunkData authorization && !authorization.getSerialNumberPIDs ().isEmpty ())
                this.notifier.logError ("IDS_NKI5_CONTAINS_ENCRYPTED_SUB_TREE");

        return Collections.emptyList ();
    }


    /**
     * Reads an NI container, which hopefully contains a NKI preset.
     *
     * @param inputStream The input stream to read from
     * @return The parsed multi-samples, if any
     * @throws IOException Could not read the container
     */
    private static NIContainerItem readNIContainer (final InputStream inputStream) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (inputStream);
        return niContainerItem;
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (new ByteArrayInputStream (NKI_TEMPLATE));

        final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
        {
            final Program program = presetChunkData.getPrograms ().get (0);
            program.fillFrom (multisampleSource);
            presetChunkData.setPrograms (Collections.singletonList (program));
        }

        niContainerItem.write (out);
    }


    /**
     * Update metadata from a SoundInfo chunk.
     *
     * @param niContainerItem The top item to start searching for the SoundInfo chunk.
     * @param source The source to update
     * @param parts The path parts
     * @param metadataConfig The metadata configuration
     */
    private static void updateMetadata (final NIContainerItem niContainerItem, final IMultisampleSource source, final IMetadataConfig metadataConfig, final String [] parts)
    {
        final NIContainerDataChunk soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk != null && soundInfoChunk.getData () instanceof final SoundinfoChunkData soundinfo)
        {
            final List<String> attributes = soundinfo.getAttributes ();
            final IMetadata metadata = source.getMetadata ();
            metadata.setKeywords (attributes.toArray (new String [attributes.size ()]));
            if (metadata.getCreator () == null)
            {
                final String author = soundinfo.getAuthor ();
                if (author != null && !author.isBlank ())
                    metadata.setCreator (author);
                else
                    metadata.setCreator (soundinfo.getVendor ());
            }
            metadata.setDescription (soundinfo.getDescription ());

            final String category = metadata.getCategory ();
            if ((category == null || category.isBlank () || "New".equals (category)) && !attributes.isEmpty ())
                metadata.setCategory (attributes.get (0));
        }
        AbstractNKIMetadataFileHandler.updateMetadata (metadataConfig, parts, source.getMetadata ());
    }


    /**
     * Convert the program object into one or more multi-sample sources.
     *
     * @param presetChunkData The preset chunk data which contains the preset information
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @param isMonolith True if all files are stored in a monolith
     * @return The multi-sample sources with their source program
     * @throws IOException Could not convert the program
     */
    private List<Pair<IMultisampleSource, Program>> convertPrograms (final PresetChunkData presetChunkData, final File sourceFile, final IMetadataConfig metadataConfig, final boolean isMonolith) throws IOException
    {
        final String n = metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);

        final List<Pair<IMultisampleSource, Program>> results = new ArrayList<> ();
        final List<Program> programs = presetChunkData.getPrograms ();
        final List<String> filePaths = presetChunkData.getFilePaths ();
        for (final Program program: programs)
        {
            final String programName = program.getName ();
            final String mappingName = AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile) + " : " + programName;
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null, mappingName);
            this.fillInto (multisampleSource, program, programs.size () > 1 ? new String []
            {
                programName
            } : parts, filePaths, isMonolith);
            results.add (new Pair<> (multisampleSource, program));
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
    private static void replaceSamples (final IMultisampleSource multisampleSource, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        final Map<String, ISampleData> sampleFileMap = new HashMap<> ();
        for (final ISampleZone zone: monolithSamples.values ())
        {
            final ISampleData sampleData = zone.getSampleData ();
            sampleFileMap.put (zone.getName (), sampleData);
        }

        for (final IGroup group: multisampleSource.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final String zoneName = zone.getName ();
                ISampleData memoryFile = sampleFileMap.get (zoneName + ".wav");
                if (memoryFile == null)
                {
                    memoryFile = sampleFileMap.get (zoneName + ".ncw");
                    if (memoryFile == null)
                        throw new IOException (Functions.getMessage ("IDS_NKI5_NO_MATCHING_IN_MEMORY_FILE", zoneName));
                }
                zone.setSampleData (memoryFile);
            }
    }
}
