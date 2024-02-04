// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * A preset chunk. Such a chunk might contain a public / private data section and a number of child
 * chunks.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunk
{
    private int                     id;
    private byte []                 publicData;
    private byte []                 privateData;
    private int                     version;
    private final List<PresetChunk> children = new ArrayList<> ();


    /**
     * Parse the structure of a preset.
     *
     * @param in The input stream to read the preset data from
     * @throws IOException Could not read
     */
    public void parse (final InputStream in) throws IOException
    {
        this.id = StreamUtils.readUnsigned16 (in, false);
        final int objectSize = (int) StreamUtils.readUnsigned32 (in, false);
        final byte [] objectData = in.readNBytes (objectSize);
        final ByteArrayInputStream objectInputStream = new ByteArrayInputStream (objectData);

        switch (this.id)
        {
            case PresetChunkID.GROUP_LIST:
                this.readArray (objectInputStream, objectSize, false);
                break;

            case PresetChunkID.ZONE_LIST:
                this.readArray (objectInputStream, objectSize, true);
                break;

            case PresetChunkID.BANK, PresetChunkID.PROGRAM_CONTAINER, PresetChunkID.PROGRAM, PresetChunkID.PAR_SCRIPT, PresetChunkID.PAR_FX_SEND_LEVELS, PresetChunkID.VOICE_GROUPS, PresetChunkID.PARAMETER_ARRAY_8, PresetChunkID.INSERT_BUS, PresetChunkID.SAVE_SETTINGS, PresetChunkID.QUICK_BROWSE_DATA:
                this.readStructure (objectInputStream, objectSize);
                break;

            case PresetChunkID.SLOT_LIST, PresetChunkID.PAR_FX, PresetChunkID.FILENAME_LIST_EX, PresetChunkID.FILENAME_LIST, PresetChunkID.PARAMETER_ARRAY_16:
            default:
                this.publicData = objectInputStream.readNBytes (objectSize);
                return;
        }
    }


    /**
     * Reads an array structure which is a list of preset object structures (which do not have an ID
     * or size as the header).
     *
     * @param in The input stream to read the preset data from
     * @param objectSize The size of the structure to read
     * @param hasReference If true another integer is read before each array object and stored in
     *            the ID of the children
     * @throws IOException Could not read
     */
    public void readArray (final InputStream in, final int objectSize, final boolean hasReference) throws IOException
    {
        final int arrayLength = (int) StreamUtils.readUnsigned32 (in, false);
        for (int index = 0; index < arrayLength; index++)
        {
            final int reference = hasReference ? (int) StreamUtils.readUnsigned32 (in, false) : 0;

            final PresetChunk arrayObject = new PresetChunk ();
            arrayObject.readStructure (in, objectSize);
            this.children.add (arrayObject);

            arrayObject.id = reference;
        }
    }


    /**
     * Read the public, private data sections and children structures.
     *
     * @param in The input stream to read the preset data from
     * @param objectSize The size of the structure to read
     * @throws IOException Could not read
     */
    private void readStructure (final InputStream in, final int objectSize) throws IOException
    {
        final int read = in.read ();
        final boolean isDataStructured = read > 0;
        if (!isDataStructured)
        {
            if (objectSize > 0)
                this.publicData = in.readNBytes (objectSize - 1);
            return;
        }

        this.version = StreamUtils.readUnsigned16 (in, false);

        final int privateDataSize = (int) StreamUtils.readUnsigned32 (in, false);
        this.privateData = in.readNBytes (privateDataSize);

        final int publicDataSize = (int) StreamUtils.readUnsigned32 (in, false);
        this.publicData = in.readNBytes (publicDataSize);

        final int sizeChildren = (int) StreamUtils.readUnsigned32 (in, false);
        final byte [] childrenData = in.readNBytes (sizeChildren);

        final ByteArrayInputStream inChildren = new ByteArrayInputStream (childrenData);
        while (inChildren.available () > 0)
        {
            final PresetChunk object = new PresetChunk ();
            object.parse (inChildren);
            this.children.add (object);
        }
    }


    /**
     * Get the ID of the object.
     *
     * @return the id
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the public data.
     *
     * @return The data
     */
    public byte [] getPublicData ()
    {
        return this.publicData;
    }


    /**
     * Get the private data.
     *
     * @return The data
     */
    public byte [] getPrivateData ()
    {
        return this.privateData;
    }


    /**
     * Get the version.
     *
     * @return The version
     */
    public int getVersion ()
    {
        return this.version;
    }


    /**
     * Get the child structures.
     *
     * @return The children
     */
    public List<PresetChunk> getChildren ()
    {
        return this.children;
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int length = level * 4;

        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("ID: 0x", length)).append (Integer.toHexString (this.id).toUpperCase ()).append (" ").append (PresetChunkID.getName (this.id)).append ("\n");

        final int childLevel = level + 1;

        sb.append (StringUtils.padLeftSpaces ("Private Size: ", length + 4)).append (this.privateData == null ? 0 : this.privateData.length).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Public  Size: ", length + 4)).append (this.publicData == null ? 0 : this.publicData.length).append ("\n");

        if (this.children.isEmpty ())
            sb.append (StringUtils.padLeftSpaces ("Children: None\n", length + 4));
        else
        {
            sb.append (StringUtils.padLeftSpaces ("Children:\n", length + 4));
            for (final PresetChunk child: this.children)
                sb.append (child.dump (childLevel));
        }

        return sb.toString ();
    }
}
