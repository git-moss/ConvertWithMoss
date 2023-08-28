// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.IKontaktType;
import de.mossgrabers.convertwithmoss.format.nki.type.KontaktTypes;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Program;
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
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * Detector for Native Instruments Kontakt Instrument (NKI) files. Currently, only the format of the
 * versions before Kontakt 4.2.2 are supported.
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public class NkiDetectorTask extends AbstractDetectorTask
{
    private final KontaktTypes kontaktTypes;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    public NkiDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".nki", ".nkm");

        this.kontaktTypes = new KontaktTypes (notifier, metadata);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            // Is this Kontakt 5+ container format?
            fileAccess.seek (12);
            if ("hsin".equals (StreamUtils.readASCII (fileAccess, 4)))
            {
                fileAccess.seek (0);
                try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
                {
                    return this.readNIContainer (inputStream, sourceFile);
                }
            }

            // Is this Kontakt 5+ monolith container format?
            fileAccess.seek (0);
            final int typeID = fileAccess.readInt ();
            if (KontaktTypes.ID_KONTAKT5_MONOLITH.intValue () == typeID)
            {
                this.notifier.logError ("IDS_NKI_KONTAKT5_MONOLITH_NOT_SUPPORTED");
                return Collections.emptyList ();
            }

            // Check for Kontakt 1 or 2-4 formats
            final IKontaktType kontaktType = this.kontaktTypes.getType (typeID);
            if (kontaktType == null)
                throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_FILE_ID", Integer.toHexString (typeID).toUpperCase ()));
            final List<IMultisampleSource> result = kontaktType.readNKI (this.sourceFolder, sourceFile, fileAccess);
            if (result.isEmpty ())
                this.notifier.logError ("IDS_NKI_COULD_NOT_DETECT_LAYERS");
            return result;
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Reads an NI container, which hopefully contains a NKI preset.
     *
     * @param inputStream The input stream to read from
     * @param sourceFile The source file to convert
     * @return The parsed multi-samples, if any
     * @throws IOException Could not read the container
     */
    private List<IMultisampleSource> readNIContainer (final InputStream inputStream, final File sourceFile) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (inputStream);

        final NIContainerChunk appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
        if (appChunk != null && appChunk.getData () instanceof final AuthoringApplicationChunkData appChunkData)
        {
            final AuthoringApplication application = appChunkData.getApplication ();
            if (application != AuthoringApplication.KONTAKT)
                throw new IOException (Functions.getMessage ("IDS_NKI5_NOT_A_KONTAKT_FILE", application == null ? "Unknown" : application.getName ()));

            // TODO Detect monolith
            final boolean isMonolith = false;
            this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", "Container", appChunkData.getApplicationVersion (), isMonolith ? " - monolith" : "", "Little-Endian");

            final NIContainerChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
            if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
            {
                final List<IMultisampleSource> sources = this.convertProgram (presetChunkData, sourceFile);
                if (!sources.isEmpty ())
                {
                    updateMetadata (niContainerItem, sources);
                    return sources;
                }
            }
        }

        this.notifier.logError ("IDS_NKI5_NO_PROGRAM_FOUND");
        return Collections.emptyList ();
    }


    /**
     * Update metadata from a SoundInfo chunk.
     *
     * @param niContainerItem The top item to start searching for the SoundInfo chunk.
     * @param sources The sources to update
     */
    private static void updateMetadata (final NIContainerItem niContainerItem, final List<IMultisampleSource> sources)
    {
        final NIContainerChunk soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk != null && soundInfoChunk.getData () instanceof final SoundinfoChunkData soundinfo)
        {
            final List<String> attributes = soundinfo.getAttributes ();
            for (final IMultisampleSource source: sources)
            {
                source.setKeywords (attributes.toArray (new String [attributes.size ()]));
                if (source.getCreator () == null)
                    source.setCreator (soundinfo.getAuthor ());
                if (source.getCategory () == null && !attributes.isEmpty ())
                    source.setCategory (attributes.get (0));
            }
        }
    }


    /**
     * Convert the program object into a multisample source.
     *
     * @param presetChunkData The preset chunk data which contains the preset information
     * @param sourceFile The source file to convert
     * @return The multisample source
     * @throws IOException Could not convert the program
     */
    private List<IMultisampleSource> convertProgram (final PresetChunkData presetChunkData, final File sourceFile) throws IOException
    {
        final String n = this.metadata.isPreferFolderName () ? this.sourceFolder.getName () : FileUtils.getNameWithoutType (sourceFile);
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, n);
        final MultisampleSource multisampleSource = new MultisampleSource (sourceFile, parts, null, AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile));

        final Optional<Program> optionalProgram = presetChunkData.parseProgram ();
        if (optionalProgram.isEmpty ())
            return Collections.emptyList ();

        optionalProgram.get ().fillInto (multisampleSource);
        return Collections.singletonList (multisampleSource);
    }
}
