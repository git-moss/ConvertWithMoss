// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.chunkdata;

import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerChunkType;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumMap;
import java.util.Map;


/**
 * A factory for creating objects for the additional data of a container chunk.
 *
 * @author Jürgen Moßgraber
 */
public class ChunkDataFactory
{
    private static final Map<NIContainerChunkType, Class<? extends IChunkData>> TEMPLATES = new EnumMap<> (NIContainerChunkType.class);
    static
    {
        TEMPLATES.put (NIContainerChunkType.TERMINATOR, TerminatorChunkData.class);
        TEMPLATES.put (NIContainerChunkType.CONTAINER_ROOT, RootChunkData.class);
        TEMPLATES.put (NIContainerChunkType.SOUNDINFO_ITEM, SoundinfoChunkData.class);
        TEMPLATES.put (NIContainerChunkType.AUTHORING_APPLICATION, AuthoringApplicationChunkData.class);
        TEMPLATES.put (NIContainerChunkType.SUB_TREE_ITEM, SubTreeItemChunkData.class);
        TEMPLATES.put (NIContainerChunkType.PRESET_CHUNK_ITEM, PresetChunkData.class);
        TEMPLATES.put (NIContainerChunkType.AUTHORIZATION, AuthorizationChunkData.class);

        // Not used
        TEMPLATES.put (NIContainerChunkType.BNI_SOUND_PRESET, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.BNI_SOUND_HEADER, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.BANK, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.BANK_CONTAINER, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.PRESET_CONTAINER, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.BINARY_CHUNK_ITEM, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.EXTERNAL_FILE_REFERENCE, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.RESOURCES, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.AUDIO_SAMPLE_ITEM, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.INTERNAL_RESOURCE_REFERENCE_ITEM, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.PICTURE_ITEM, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.ENCRYPTION_ITEM, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.APP_SPECIFIC, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.AUTOMATION_PARAMETERS, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.CONTROLLER_ASSIGNMENTS, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.MODULE, UnusedChunkData.class);
        TEMPLATES.put (NIContainerChunkType.MODULE_BANK, UnusedChunkData.class);
    }


    /**
     * Create a new instance of a chunk data matching the given type.
     *
     * @param type The type to match
     * @return The instance or null if no specific data class is registered for the given type
     */
    public static IChunkData createChunkData (final NIContainerChunkType type)
    {
        final Class<? extends IChunkData> clazz = TEMPLATES.get (type);
        if (clazz == null)
            return null;
        try
        {
            return clazz.getConstructor ().newInstance ();
        }
        catch (final InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex)
        {
            return null;
        }
    }


    /**
     * Helper class.
     */
    private ChunkDataFactory ()
    {
        // Intentionally empty
    }
}
