// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
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
        final int objectSize = StreamUtils.readUnsigned32 (in, false);

        switch (this.id)
        {
            case PresetChunkID.PROGRAM, PresetChunkID.PAR_SCRIPT, PresetChunkID.PAR_FX_SEND_LEVELS, PresetChunkID.VOICE_GROUPS, PresetChunkID.PARAMETER_ARRAY_8, PresetChunkID.INSERT_BUS, PresetChunkID.SAVE_SETTINGS, PresetChunkID.QUICK_BROWSE_DATA:
                this.readStructure (in, objectSize);
                break;

            case PresetChunkID.PAR_FX, PresetChunkID.GROUP_LIST, PresetChunkID.ZONE_LIST, PresetChunkID.FILENAME_LIST_EX, PresetChunkID.FILENAME_LIST, PresetChunkID.PARAMETER_ARRAY_16:
            default:
                this.publicData = in.readNBytes (objectSize);
                return;
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

        final int privateDataSize = StreamUtils.readUnsigned32 (in, false);
        this.privateData = in.readNBytes (privateDataSize);

        final int publicDataSize = StreamUtils.readUnsigned32 (in, false);
        this.publicData = in.readNBytes (publicDataSize);

        final int sizeChildren = StreamUtils.readUnsigned32 (in, false);
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
