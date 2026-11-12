// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.AbstractNKIMetadataFileHandler;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.AbstractKontaktFormat;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplication;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplicationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthorizationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.IChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.SoundinfoChunkData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle NKI files in Kontakt 5+ format. WAV and NCW files are supported.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt5Format extends AbstractKontaktFormat
{
    private static final byte [] NKI_TEMPLATE;
    private static final byte [] NKM_TEMPLATE;
    private static final int     MAX_SLOTS             = 64;
    private static final int     MULTI_CONFIG_SIZE     = 24;
    // The save settings of a program in a multi which is not linked to an instrument file (the
    // file index is -1)
    private static final byte [] SLOT_SAVE_SETTINGS    =
    {
        0x10,
        0x00,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        -1,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00
    };
    private static final String  SOUNDINFO_EMPTY       = "-";
    private static final String  SOUNDINFO_TAG_KONTAKT = "Kontakt";

    static
    {
        try
        {
            NKI_TEMPLATE = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/nki/Kontakt680Template.nki");
            NKM_TEMPLATE = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/nki/Kontakt680Template.nkm");
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
    public Kontakt5Format (final INotifier notifier)
    {
        super (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Optional<IPerformanceSource> readNKM (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            return this.readNKM (sourceFolder, sourceFile, inputStream, metadataConfig, null);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKM (final OutputStream out, final String bankName, final List<String> safeSampleFolderNames, final List<IInstrumentSource> instrumentSources, final int sizeOfSamples) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (new ByteArrayInputStream (NKM_TEMPLATE));

        final Optional<NIContainerDataChunk> presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk.isEmpty () || !(presetChunk.get ().getData () instanceof final PresetChunkData presetChunkData))
            throw new IOException (Functions.getMessage ("IDS_NKI5_NO_PRESET_CHUNK"));

        final KontaktPresetAccessor bankAccessor = new KontaktPresetAccessor ();
        bankAccessor.readKontaktPresetChunks (presetChunkData.getPresetData ());
        final KontaktPresetChunk bankChunk = findChunk (bankAccessor.getChunks (), KontaktPresetChunkID.BANK);
        final KontaktPresetChunk slotListChunk = findChunk (bankChunk.getChildren (), KontaktPresetChunkID.SLOT_LIST);
        final KontaktPresetChunk multiConfigChunk = findChunk (bankChunk.getChildren (), KontaktPresetChunkID.MULTI_CONFIGURATION);

        // The slot list starts with an 8 byte mask of the used slots followed by the program
        // containers of the template, which are used as the template for all slots
        final byte [] slotListData = slotListChunk.getPublicData ();
        final byte [] programContainerTemplate = Arrays.copyOfRange (slotListData, 8, slotListData.length);

        final int numInstruments = Math.min (instrumentSources.size (), MAX_SLOTS);
        if (instrumentSources.size () > MAX_SLOTS)
            this.notifier.logError ("IDS_ERR_LIMITED_INSTRUMENTS", Integer.toString (MAX_SLOTS), Integer.toString (instrumentSources.size ()));

        final Map<String, Integer> samplePaths = new LinkedHashMap<> ();
        final BitSet slotMask = new BitSet (MAX_SLOTS);
        final ByteArrayOutputStream slotsOut = new ByteArrayOutputStream ();
        final byte [] multiConfiguration = multiConfigChunk.getPublicData ().clone ();
        int numZones = 0;
        int numGroups = 0;

        for (int i = 0; i < numInstruments; i++)
        {
            final IInstrumentSource instrumentSource = instrumentSources.get (i);
            final IMultisampleSource multisampleSource = instrumentSource.getMultisampleSource ();

            // Create the program from the NKI template
            final KontaktPresetAccessor programAccessor = new KontaktPresetAccessor ();
            programAccessor.readKontaktPresetChunks (readPresetData (NKI_TEMPLATE));
            final KontaktPresetChunk programChunk = findChunk (programAccessor.getChunks (), KontaktPresetChunkID.PROGRAM);
            final Program program = programAccessor.getPrograms ().get (0);
            final List<String> sampleFileNames = program.fillFrom (multisampleSource);
            program.setInstrumentValues (instrumentSource);

            // All programs share one file list, files used by several programs are only added once
            final String folderName = i < safeSampleFolderNames.size () ? safeSampleFolderNames.get (i) : "";
            final int [] filenameMapping = new int [sampleFileNames.size ()];
            for (int f = 0; f < filenameMapping.length; f++)
            {
                final String sampleFileName = sampleFileNames.get (f);
                final String samplePath = folderName.isBlank () ? sampleFileName : folderName + "/" + sampleFileName;
                filenameMapping[f] = samplePaths.computeIfAbsent (samplePath, _ -> Integer.valueOf (samplePaths.size ())).intValue ();
            }
            program.mapFilenameIds (filenameMapping);
            program.write (programChunk);

            numZones += program.getZones ().size ();
            numGroups += program.getGroups ().size ();

            // Wrap the program into a program list (which is an array of programs)
            final KontaktPresetChunk programArray = new KontaktPresetChunk ();
            programArray.setChildren (Collections.singletonList (programChunk));
            final ByteArrayOutputStream programListOut = new ByteArrayOutputStream ();
            programArray.writeArray (programListOut, false);

            // Put the program list into a copy of the program container
            final KontaktPresetChunk programContainerChunk = new KontaktPresetChunk ();
            programContainerChunk.read (new ByteArrayInputStream (programContainerTemplate));
            if (programContainerChunk.getId () != KontaktPresetChunkID.PROGRAM_CONTAINER)
                throw new IOException (Functions.getMessage ("IDS_NKI5_NO_PRESET_CHUNK"));
            findChunk (programContainerChunk.getChildren (), KontaktPresetChunkID.PROGRAM_LIST).setPublicData (programListOut.toByteArray ());
            // The template program is linked to its NKI file, remove the link
            for (final KontaktPresetChunk child: programContainerChunk.getChildren ())
                if (child.getId () == KontaktPresetChunkID.SAVE_SETTINGS)
                    child.setPublicData (SLOT_SAVE_SETTINGS.clone ());
            programContainerChunk.write (slotsOut);
            slotMask.set (i);

            // The MIDI channel is stored as 0 = Omni, 1 = A1 ... 64 = D16
            final int offset = i * MULTI_CONFIG_SIZE;
            if (offset + 1 < multiConfiguration.length)
            {
                final int kontaktMidiChannel = toKontaktMidiChannel (instrumentSource.getMidiChannel ());
                multiConfiguration[offset] = (byte) (kontaktMidiChannel & 0xFF);
                multiConfiguration[offset + 1] = (byte) (kontaktMidiChannel >> 8 & 0xFF);
            }
        }

        // Write the slot list
        final ByteArrayOutputStream slotListOut = new ByteArrayOutputStream ();
        slotListOut.write (Arrays.copyOf (slotMask.toByteArray (), 8));
        slotListOut.write (slotsOut.toByteArray ());
        slotListChunk.setPublicData (slotListOut.toByteArray ());
        multiConfigChunk.setPublicData (multiConfiguration);
        bankChunk.setPublicData (createBankData (bankChunk.getPublicData (), bankName));

        // Replace the files in the file list
        for (final KontaktPresetChunk chunk: bankAccessor.getChunks ())
            if (chunk.getId () == KontaktPresetChunkID.FILENAME_LIST_EX)
                chunk.setPublicData (FileList.createFileListEx (new ArrayList<> (samplePaths.keySet ()), null));

        // Write all chunks without touching the programs
        final ByteArrayOutputStream presetOut = new ByteArrayOutputStream ();
        for (final KontaktPresetChunk chunk: bankAccessor.getChunks ())
            chunk.write (presetOut);
        final byte [] presetData = presetOut.toByteArray ();
        presetChunkData.setPresetData (presetData);

        final Optional<NIContainerDataChunk> soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk.isPresent () && soundInfoChunk.get ().getData () instanceof final SoundinfoChunkData soundinfo)
        {
            soundinfo.setName (bankName);
            soundinfo.setAuthor (SOUNDINFO_EMPTY);
            soundinfo.setVendor (SOUNDINFO_EMPTY);
            soundinfo.setDescription (SOUNDINFO_EMPTY);
            soundinfo.setTags (Collections.singletonList (SOUNDINFO_TAG_KONTAKT));
            soundinfo.setAttributes (Collections.emptyList ());
        }

        final Optional<NIContainerDataChunk> soundHeaderChunk = niContainerItem.find (NIContainerChunkType.BNI_SOUND_HEADER);
        if (soundHeaderChunk.isPresent ())
        {
            final IChunkData soundHeaderData = soundHeaderChunk.get ().getData ();
            final ByteArrayOutputStream headerOut = new ByteArrayOutputStream ();
            soundHeaderData.write (headerOut);
            final byte [] header = headerOut.toByteArray ();
            KontaktSoundHeader.updateMulti (header, numZones, numGroups, numInstruments, sizeOfSamples, presetData);
            soundHeaderData.read (new ByteArrayInputStream (header));
        }

        niContainerItem.write (out);
    }


    /**
     * Get the preset data from a NI container.
     *
     * @param containerData The data of the NI container
     * @return The preset data
     * @throws IOException Could not read the container or it does not contain preset data
     */
    private static byte [] readPresetData (final byte [] containerData) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (new ByteArrayInputStream (containerData));
        final Optional<NIContainerDataChunk> presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk.isEmpty () || !(presetChunk.get ().getData () instanceof final PresetChunkData presetChunkData))
            throw new IOException (Functions.getMessage ("IDS_NKI5_NO_PRESET_CHUNK"));
        return presetChunkData.getPresetData ();
    }


    /**
     * Find the first chunk with the given ID.
     *
     * @param chunks The chunks to search
     * @param chunkID The ID of the chunk to find
     * @return The chunk
     * @throws IOException The chunk was not found
     */
    private static KontaktPresetChunk findChunk (final List<KontaktPresetChunk> chunks, final int chunkID) throws IOException
    {
        for (final KontaktPresetChunk chunk: chunks)
            if (chunk.getId () == chunkID)
                return chunk;
        throw new IOException (Functions.getMessage ("IDS_NKI5_NO_PRESET_CHUNK"));
    }


    /**
     * Create the public data of the bank chunk: master volume, master tune, tempo and name.
     *
     * @param templateData The bank data of the template from which to take the tempo
     * @param bankName The name of the bank
     * @return The data
     * @throws IOException Could not create the data
     */
    private static byte [] createBankData (final byte [] templateData, final String bankName) throws IOException
    {
        final ByteArrayInputStream in = new ByteArrayInputStream (templateData);
        StreamUtils.readFloatLE (in);
        StreamUtils.readFloatLE (in);
        final long tempo = StreamUtils.readUnsigned32 (in, false);

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        StreamUtils.writeFloatLE (out, 1.0f);
        StreamUtils.writeFloatLE (out, 1.0f);
        StreamUtils.writeUnsigned32 (out, tempo, false);
        StreamUtils.writeUtf16WithLength (out, bankName);
        return out.toByteArray ();
    }


    /**
     * Converts a MIDI channel to the Kontakt value.
     *
     * @param midiChannel The MIDI channel
     * @return 0 = Omni, 1-16 = channel 1-16 (port A), 17-32 = port B, ...
     */
    private static int toKontaktMidiChannel (final int midiChannel)
    {
        if (midiChannel == IInstrumentSource.MIDI_CHANNEL_OMNI)
            return 0;
        // B1 since there is no off-state
        if (midiChannel == IInstrumentSource.MIDI_CHANNEL_OFF)
            return 17;
        return Math.clamp (midiChannel + 1L, 1, 64);
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
    public Optional<IPerformanceSource> readNKM (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        this.sourceFolder = sourceFolder;

        final NIContainerItem niContainerItem = new NIContainerItem (inputStream);
        final Optional<KontaktPresetAccessor> presetAccessor = this.readKontaktPreset (niContainerItem, monolithSamples);
        if (presetAccessor.isEmpty ())
            return Optional.empty ();

        final List<Pair<IMultisampleSource, Program>> sources = this.convertPrograms (presetAccessor.get (), niContainerItem, sourceFile, metadataConfig, monolithSamples, true);
        return this.createPerformanceSource (sourceFile, presetAccessor.get (), sources);
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
     * @param sourceFile The source file which contains the Kontakt instrument
     * @param inputStream The input stream to read from
     * @param metadataConfig Default metadata
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the file
     */
    public List<IMultisampleSource> readNKI (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        this.sourceFolder = sourceFolder;
        final NIContainerItem niContainerItem = new NIContainerItem (inputStream);
        final Optional<KontaktPresetAccessor> presetAccessor = this.readKontaktPreset (niContainerItem, monolithSamples);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        if (presetAccessor.isPresent ())
        {
            final List<Pair<IMultisampleSource, Program>> sources = this.convertPrograms (presetAccessor.get (), niContainerItem, sourceFile, metadataConfig, monolithSamples, false);
            for (final Pair<IMultisampleSource, Program> source: sources)
                multisampleSources.add (source.getKey ());
        }
        return multisampleSources;
    }


    /**
     * Reads from an NI container, which hopefully contains a NKI preset.
     *
     * @param niContainerItem The NI container item to read from
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @return Access to the read Kontakt program-/multi-data
     * @throws IOException Could not read the container
     */
    private Optional<KontaktPresetAccessor> readKontaktPreset (final NIContainerItem niContainerItem, final Map<Long, ISampleZone> monolithSamples) throws IOException
    {
        final boolean isMonolith = monolithSamples != null;

        final Optional<NIContainerDataChunk> appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
        if (appChunk.isPresent () && appChunk.get ().getData () instanceof final AuthoringApplicationChunkData appChunkData)
        {
            final AuthoringApplication application = appChunkData.getApplication ();
            if (application != AuthoringApplication.KONTAKT)
                throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_KONTAKT_FILE", application == null ? "Unknown" : application.getName ()));

            this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", "Container", appChunkData.getApplicationVersion (), isMonolith ? " - monolith" : "", "Little-Endian");

            final Optional<NIContainerDataChunk> presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
            if (presetChunk.isPresent () && presetChunk.get ().getData () instanceof final PresetChunkData presetChunkData)
            {
                final KontaktPresetAccessor programAccessor = new KontaktPresetAccessor ();
                programAccessor.readKontaktPresetChunks (presetChunkData.getPresetData ());
                return Optional.of (programAccessor);
            }
        }

        // Check for encrypted sections
        for (final NIContainerDataChunk autorizationChunk: niContainerItem.findAll (NIContainerChunkType.AUTHORIZATION))
            if (autorizationChunk.getData () instanceof final AuthorizationChunkData authorization && !authorization.getSerialNumberPIDs ().isEmpty ())
                this.notifier.logError ("IDS_NI_CONTAINER_CONTAINS_ENCRYPTED_SUB_TREE");

        return Optional.empty ();
    }


    /** {@inheritDoc} */
    @Override
    public void writeNKI (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (new ByteArrayInputStream (NKI_TEMPLATE));

        final Optional<NIContainerDataChunk> presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk.isEmpty () || !(presetChunk.get ().getData () instanceof final PresetChunkData presetChunkData))
            throw new IOException (Functions.getMessage ("IDS_NKI5_NO_PRESET_CHUNK"));

        final KontaktPresetAccessor programAccessor = new KontaktPresetAccessor ();
        programAccessor.readKontaktPresetChunks (presetChunkData.getPresetData ());

        final Program program = programAccessor.getPrograms ().get (0);
        final List<String> sampleFileNames = program.fillFrom (multisampleSource);
        programAccessor.setPrograms (Collections.singletonList (program));

        // Replace the sample files in the file list
        final String instrumentFileName = SafeFileNames.create (multisampleSource.getName ()) + ".nki";
        for (final KontaktPresetChunk chunk: programAccessor.getChunks ())
            if (chunk.getId () == KontaktPresetChunkID.FILENAME_LIST_EX)
                chunk.setPublicData (FileList.createFileListEx (safeSampleFolderName, sampleFileNames, instrumentFileName));

        final byte [] presetData = programAccessor.writeKontaktPresetChunks ();
        presetChunkData.setPresetData (presetData);

        updateSoundinfo (niContainerItem, multisampleSource);
        updateSoundHeader (niContainerItem, program, sizeOfSamples, presetData);

        niContainerItem.write (out);
    }


    /**
     * Update the sound header which contains a checksum of the preset data. Kontakt does not load
     * the file if it does not match.
     *
     * @param niContainerItem The top item to start searching for the sound header chunk
     * @param program The program which is stored in the preset data
     * @param sizeOfSamples The sum of the size of all used samples (only the sample data)
     * @param presetData The preset data
     * @throws IOException Could not update the header
     */
    private static void updateSoundHeader (final NIContainerItem niContainerItem, final Program program, final int sizeOfSamples, final byte [] presetData) throws IOException
    {
        final Optional<NIContainerDataChunk> soundHeaderChunk = niContainerItem.find (NIContainerChunkType.BNI_SOUND_HEADER);
        if (soundHeaderChunk.isEmpty ())
            return;
        final IChunkData soundHeaderData = soundHeaderChunk.get ().getData ();
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        soundHeaderData.write (out);
        final byte [] header = out.toByteArray ();
        KontaktSoundHeader.update (header, program, sizeOfSamples, presetData);
        soundHeaderData.read (new ByteArrayInputStream (header));
    }


    /**
     * Update the SoundInfo chunk with the metadata of the multi-sample.
     *
     * @param niContainerItem The top item to start searching for the SoundInfo chunk
     * @param multisampleSource The multi-sample from which to take the metadata
     */
    private static void updateSoundinfo (final NIContainerItem niContainerItem, final IMultisampleSource multisampleSource)
    {
        final Optional<NIContainerDataChunk> soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk.isEmpty () || !(soundInfoChunk.get ().getData () instanceof final SoundinfoChunkData soundinfo))
            return;

        final IMetadata metadata = multisampleSource.getMetadata ();
        soundinfo.setName (multisampleSource.getName ());
        soundinfo.setAuthor (getValueOrDash (metadata.getCreator ()));
        soundinfo.setVendor (SOUNDINFO_EMPTY);
        soundinfo.setDescription (getValueOrDash (metadata.getDescription ()));
        soundinfo.setTags (Collections.singletonList (SOUNDINFO_TAG_KONTAKT));
        soundinfo.setAttributes (Collections.emptyList ());
    }


    private static boolean isSet (final String value)
    {
        return value != null && !value.isBlank () && !SOUNDINFO_EMPTY.equals (value.trim ());
    }


    private static String getValueOrDash (final String value)
    {
        return value == null || value.isBlank () ? SOUNDINFO_EMPTY : value;
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
        final Optional<NIContainerDataChunk> soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk.isPresent () && soundInfoChunk.get ().getData () instanceof final SoundinfoChunkData soundinfo)
        {
            final List<String> attributes = soundinfo.getAttributes ();
            final IMetadata metadata = source.getMetadata ();
            metadata.setKeywords (attributes.toArray (new String [attributes.size ()]));
            // Kontakt stores a '-' if a value is not set
            final String creator = metadata.getCreator ();
            if (creator == null || creator.isBlank ())
            {
                final String author = soundinfo.getAuthor ();
                if (isSet (author))
                    metadata.setCreator (author);
                else if (isSet (soundinfo.getVendor ()))
                    metadata.setCreator (soundinfo.getVendor ());
            }
            final String description = soundinfo.getDescription ();
            if (isSet (description))
                metadata.setDescription (description);

            final String category = metadata.getCategory ();
            if ((category == null || category.isBlank () || "New".equals (category)) && !attributes.isEmpty ())
                metadata.setCategory (attributes.get (0));
        }
        AbstractNKIMetadataFileHandler.updateMetadata (metadataConfig, parts, source.getMetadata ());
    }


    /**
     * Convert the program object into one or more multi-sample sources.
     *
     * @param presetAccessor Contains the read programs
     * @param niContainerItem The top item to start searching for the SoundInfo chunk.
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @param monolithSamples If the NKI is inside a monolith, these are the sample files
     * @param isMulti True if the programs are read from a multi (NKM)
     * @return The multi-sample sources with their source program
     * @throws IOException Could not convert the program
     */
    private List<Pair<IMultisampleSource, Program>> convertPrograms (final KontaktPresetAccessor presetAccessor, final NIContainerItem niContainerItem, final File sourceFile, final IMetadataConfig metadataConfig, final Map<Long, ISampleZone> monolithSamples, final boolean isMulti) throws IOException
    {
        final String n = metadataConfig.isPreferFolderName () ? this.sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);

        final List<Pair<IMultisampleSource, Program>> results = new ArrayList<> ();
        final List<Program> programs = presetAccessor.getPrograms ();
        final List<String> filePaths = presetAccessor.getFilePaths ();
        for (final Program program: programs)
        {
            final String programName = program.getName ();
            final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, null);
            this.fillInto (multisampleSource, program, programs.size () > 1 ? new String []
            {
                programName
            } : parts, filePaths, monolithSamples != null, isMulti);
            results.add (new Pair<> (multisampleSource, program));

            updateMetadata (niContainerItem, multisampleSource, metadataConfig, parts);

            if (monolithSamples != null)
                replaceSamples (multisampleSource, monolithSamples);
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
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isPresent ())
                sampleFileMap.put (zone.getName (), sampleData.get ());
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
