// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.NIContainerChunkType;

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
        TEMPLATES.put (NIContainerChunkType.PRESET_DATA, PresetDataChunkData.class);
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
