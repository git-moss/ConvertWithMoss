// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container;

import java.util.HashMap;
import java.util.Map;


/**
 * The (known) types of chunks in a Native Instruments Container (used by Kontakt 5+ and other NI
 * plugins).
 *
 * @author Jürgen Moßgraber
 */
public enum NIContainerChunkType
{
    /** Indicates the terminal chunk in a chunk list. */
    TERMINATOR(1, "Terminator"),
    BNI_SOUND_PRESET(3, "BNI Sound Preset"),
    BNI_SOUND_HEADER(4, "BNI Sound Header"),
    BANK(100, "Bank"),
    /** Information about the application that created the preset. */
    PRESET(101, "Preset"),
    BANK_CONTAINER(102, "Bank Container"),
    PRESET_CONTAINER(103, "Preset Container"),
    BINARY_CHUNK_ITEM(104, "Binary Chunk Item"),
    AUTHORIZATION(106, "Authorization"),
    /** Some metadata information. */
    SOUNDINFO_ITEM(108, "Soundinfo Item"),
    PRESET_CHUNK_ITEM(109, "Preset Chunk Item"),
    EXTERNAL_FILE_REFERENCE(110, "External File Reference"),
    RESOURCES(111, "Resources"),
    AUDIO_SAMPLE_ITEM(112, "Audio Sample Item"),
    INTERNAL_RESOURCE_REFERENCE_ITEM(113, "Internal Resource Reference Item"),
    PICTURE_ITEM(114, "Picture Item"),
    SUB_TREE_ITEM(115, "Subtree Item"),
    ENCRYPTION_ITEM(116, "Encryption Item"),
    APP_SPECIFIC(117, "App Specific"),
    /** The root chunk in a container. */
    CONTAINER_ROOT(118, "Container Root"),
    AUTOMATION_PARAMETERS(120, "Automation Parameters"),
    CONTROLLER_ASSIGNMENTS(121, "Controller Assignments"),
    MODULE(122, "Module"),
    MODULE_BANK(123, "Module Bank");


    private static final Map<Integer, NIContainerChunkType> LOOKUP = new HashMap<> ();
    static
    {
        for (final NIContainerChunkType type: NIContainerChunkType.values ())
            LOOKUP.put (Integer.valueOf (type.id), type);
    }


    /**
     * Get a chunk type for the given ID.
     *
     * @param id An ID
     * @return The chunk type or null if none exists with that ID
     */
    public static NIContainerChunkType get (final int id)
    {
        return LOOKUP.get (Integer.valueOf (id));
    }


    private final int    id;
    private final String name;


    /**
     * Constructor.
     *
     * @param id The ID of the chunk type
     * @param name The name of the chunk type
     */
    private NIContainerChunkType (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Get the name of the chunk type.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }
}
