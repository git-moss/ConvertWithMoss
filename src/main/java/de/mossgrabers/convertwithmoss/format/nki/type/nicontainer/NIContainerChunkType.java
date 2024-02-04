// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer;

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
    /** BNI Sound Preset (only used in NIK4 domain). */
    BNI_SOUND_PRESET(3, "BNI Sound Preset"),
    /** BNI Sound Header (only used in NIK4 domain). */
    BNI_SOUND_HEADER(4, "BNI Sound Header"),
    /** Bank. */
    BANK(100, "Bank"),
    /** Information about the application that created the preset. */
    AUTHORING_APPLICATION(101, "Authoring Application"),
    /** Bank Container. */
    BANK_CONTAINER(102, "Bank Container"),
    /** Preset Container. */
    PRESET_CONTAINER(103, "Preset Container"),
    /** Binary Chunk Item. */
    BINARY_CHUNK_ITEM(104, "Binary Chunk Item"),
    /** Authorization. */
    AUTHORIZATION(106, "Authorization"),
    /** Some metadata information. */
    SOUNDINFO_ITEM(108, "Soundinfo Item"),
    /** The actual data of a preset. */
    PRESET_CHUNK_ITEM(109, "Preset Chunk Item"),
    /** External File Reference. */
    EXTERNAL_FILE_REFERENCE(110, "External File Reference"),
    /** Resources. */
    RESOURCES(111, "Resources"),
    /** Audio Sample Item. */
    AUDIO_SAMPLE_ITEM(112, "Audio Sample Item"),
    /** Internal Resource Reference Item. */
    INTERNAL_RESOURCE_REFERENCE_ITEM(113, "Internal Resource Reference Item"),
    /** A picture. */
    PICTURE_ITEM(114, "Picture Item"),
    /** A Subtree Item stores another (compressed) Container Item. */
    SUB_TREE_ITEM(115, "Subtree Item"),
    /** Encryption Item. */
    ENCRYPTION_ITEM(116, "Encryption Item"),
    /** Application Specific. */
    APP_SPECIFIC(117, "App Specific"),
    /** The root chunk in a container. */
    CONTAINER_ROOT(118, "Container Root"),
    /** Automation Parameters. */
    AUTOMATION_PARAMETERS(120, "Automation Parameters"),
    /** Controller Assignments. */
    CONTROLLER_ASSIGNMENTS(121, "Controller Assignments"),
    /** Module. */
    MODULE(122, "Module"),
    /** Module Bank. */
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
