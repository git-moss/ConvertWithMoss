// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.format.ni.maschine.IMaschineFormat;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerChunkType;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerDataChunk;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplication;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthoringApplicationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.AuthorizationChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.PresetChunkData;
import de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata.SoundinfoChunkData;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle MXSND files in Maschine 2+ format.
 *
 * @author Jürgen Moßgraber
 */
public class Maschine2Format implements IMaschineFormat
{
    private static final byte [] MASCHINE_TEMPLATE_V2;
    private static final byte [] MASCHINE_TEMPLATE_V3;

    static
    {
        try
        {
            MASCHINE_TEMPLATE_V2 = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/maschine/MaschineV2Template.mxsnd");
            MASCHINE_TEMPLATE_V3 = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/maschine/MaschineV3Template.mxsnd");
        }
        catch (final IOException ex)
        {
            throw new RuntimeException (ex);
        }
    }

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
            final NIContainerItem niContainerItem = new NIContainerItem (inputStream);
            final NIContainerDataChunk appChunk = niContainerItem.find (NIContainerChunkType.AUTHORING_APPLICATION);
            if (appChunk != null && appChunk.getData () instanceof final AuthoringApplicationChunkData appChunkData)
            {
                final AuthoringApplication application = appChunkData.getApplication ();
                if (application != AuthoringApplication.MASCHINE)
                    throw new IOException (Functions.getMessage ("IDS_NI_MASCHINE_NOT_A_MASCHINE_FILE", application == null ? "Unknown" : application.getName ()));

                this.notifier.log ("IDS_NI_MASCHINE_FOUND_TYPE", "Container", appChunkData.getApplicationVersion ());

                final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
                if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
                {
                    final MaschinePresetAccessor programAccessor = new MaschinePresetAccessor (this.notifier);
                    final Optional<IMultisampleSource> result = programAccessor.readMaschinePreset (sourceFolder, sourceFile, presetChunkData.getPresetData ());
                    return result.isPresent () ? Collections.singletonList (result.get ()) : Collections.emptyList ();
                }
            }

            // Check for encrypted sections
            for (final NIContainerDataChunk autorizationChunk: niContainerItem.findAll (NIContainerChunkType.AUTHORIZATION))
                if (autorizationChunk.getData () instanceof final AuthorizationChunkData authorization && !authorization.getSerialNumberPIDs ().isEmpty ())
                    this.notifier.logError ("IDS_NI_CONTAINER_CONTAINS_ENCRYPTED_SUB_TREE");

            return Collections.emptyList ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeSound (final OutputStream out, final String safeSampleFolderName, final IMultisampleSource multisampleSource, final int sizeOfSamples, final int version) throws IOException
    {
        final NIContainerItem niContainerItem = new NIContainerItem ();
        niContainerItem.read (new ByteArrayInputStream (version == 2 ? MASCHINE_TEMPLATE_V2 : MASCHINE_TEMPLATE_V3));

        final NIContainerDataChunk soundInfoChunk = niContainerItem.find (NIContainerChunkType.SOUNDINFO_ITEM);
        if (soundInfoChunk != null && soundInfoChunk.getData () instanceof final SoundinfoChunkData soundInfoChunkData)
        {
            soundInfoChunkData.setName (multisampleSource.getName ());
            final IMetadata metadata = multisampleSource.getMetadata ();
            soundInfoChunkData.setAuthor (metadata.getCreator ());
            soundInfoChunkData.setVendor (metadata.getCreator ());
            soundInfoChunkData.setDescription (metadata.getDescription ());
            soundInfoChunkData.setTags (Collections.singletonList (metadata.getCategory ()));
            soundInfoChunkData.setAttributes (Collections.singletonList (metadata.getCategory ()));
        }

        final NIContainerDataChunk presetChunk = niContainerItem.find (NIContainerChunkType.PRESET_CHUNK_ITEM);
        if (presetChunk != null && presetChunk.getData () instanceof final PresetChunkData presetChunkData)
        {
            final MaschinePresetAccessor programAccessor = new MaschinePresetAccessor (this.notifier);
            final byte [] presetData = programAccessor.writeMaschinePreset (multisampleSource, presetChunkData.getPresetData (), safeSampleFolderName);
            presetChunkData.setPresetData (presetData);
        }

        niContainerItem.write (out);
    }
}
