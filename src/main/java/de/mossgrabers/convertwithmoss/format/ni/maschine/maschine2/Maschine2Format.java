// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.format.ni.maschine.IMaschineFormat;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplication;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplicationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthorizationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle MXSND files in Maschine 2+ format.
 *
 * @author Jürgen Moßgraber
 */
public class Maschine2Format implements IMaschineFormat
{
    private final INotifier notifier;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public Maschine2Format (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readSound (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadataConfig) throws IOException
    {
        try (final InputStream inputStream = Channels.newInputStream (fileAccess.getChannel ()))
        {
            return this.readSound (sourceFolder, sourceFile, inputStream, metadataConfig);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeSound (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples) throws IOException
    {
        // Not supported
    }


    /**
     * Read and parse a Maschine Sound file which uses this format type from the given input stream.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the Maschine sound
     * @param inputStream The input stream to read from
     * @param metadataConfig Default metadata
     * @return The parsed multi-sample sources
     * @throws IOException Error reading the file
     */
    public List<IMultisampleSource> readSound (final File sourceFolder, final File sourceFile, final InputStream inputStream, final IMetadataConfig metadataConfig) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem (inputStream);
        final Optional<MaschinePresetAccessor> presetAccessor = this.readMaschinePreset (niContainerItem, sourceFolder, sourceFile, metadataConfig);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        if (presetAccessor.isPresent ())
        {
            // final List<Pair<IMultisampleSource, Sound>> sources = this.convertPrograms
            // (presetAccessor.get (), niContainerItem, sourceFile, metadataConfig,
            // monolithSamples);
            // for (final Pair<IMultisampleSource, Sound> source: sources)
            // multisampleSources.add (source.getKey ());
        }
        return multisampleSources;
    }


    /**
     * Reads from an NI container, which hopefully contains a Maschine preset.
     *
     * @param niContainerItem The NI container item to read from
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file to convert
     * @param metadataConfig Default metadata
     * @return Access to the read Maschine data
     * @throws IOException Could not read the container
     */
    private Optional<MaschinePresetAccessor> readMaschinePreset (final NIContainerItem niContainerItem, final File sourceFolder, final File sourceFile, final IMetadataConfig metadataConfig) throws IOException
    {
        final NIContainerDataChunk appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
        if (appChunk != null && appChunk.getData () instanceof final AuthoringApplicationChunkData appChunkData)
        {
            final AuthoringApplication application = appChunkData.getApplication ();
            if (application != AuthoringApplication.MASCHINE)
                throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_NOT_A_MASCHINE_FILE", application == null ? "Unknown" : application.getName ()));

            this.notifier.log ("IDS_NI_MASCHINE_FOUND_TYPE", "Container", appChunkData.getApplicationVersion (), "Little-Endian");

            final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
            if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
            {
                final MaschinePresetAccessor programAccessor = new MaschinePresetAccessor ();
                programAccessor.readMaschinePresetChunks (presetChunkData.getPresetData ());
                return Optional.of (programAccessor);
            }
        }

        // Check for encrypted sections
        for (final NIContainerDataChunk autorizationChunk: niContainerItem.findAll (NIContainerChunkType.AUTHORIZATION))
            if (autorizationChunk.getData () instanceof final AuthorizationChunkData authorization && !authorization.getSerialNumberPIDs ().isEmpty ())
                this.notifier.logError ("IDS_NI_CONTAINER_CONTAINS_ENCRYPTED_SUB_TREE");

        return Optional.empty ();
    }
}
