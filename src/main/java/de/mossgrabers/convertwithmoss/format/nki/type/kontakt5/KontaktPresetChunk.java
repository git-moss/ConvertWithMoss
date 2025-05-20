// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * A chunk in a preset. Such a chunk might contain a public / private data section and a number of
 * child chunks.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktPresetChunk
{
    private int                            id          = -1;
    private byte []                        publicData  = {};
    private byte []                        privateData = {};
    private int                            version     = -1;
    private final List<KontaktPresetChunk> children    = new ArrayList<> ();


    /**
     * Parse the structure of a preset.
     *
     * @param in The input stream to read the preset data from
     * @throws IOException Could not read
     */
    public void read (final InputStream in) throws IOException
    {
        this.id = StreamUtils.readUnsigned16 (in, false);
        final int objectSize = (int) StreamUtils.readUnsigned32 (in, false);
        final byte [] objectData = in.readNBytes (objectSize);
        final ByteArrayInputStream objectInputStream = new ByteArrayInputStream (objectData);
        switch (this.id)
        {
            case KontaktPresetChunkID.GROUP_LIST:
                this.readArray (objectInputStream, objectSize, false);
                this.children.forEach (child -> child.id = KontaktPresetChunkID.GROUP);
                break;

            case KontaktPresetChunkID.ZONE_LIST:
                this.readArray (objectInputStream, objectSize, true);
                // TODO reference needs to be written again; check what this reference is
                // this.children.forEach (child -> child.id = KontaktPresetChunkID.ZONE);
                break;

            case KontaktPresetChunkID.BANK, KontaktPresetChunkID.PROGRAM_CONTAINER, KontaktPresetChunkID.PROGRAM, KontaktPresetChunkID.PAR_SCRIPT, KontaktPresetChunkID.PAR_FX_SEND_LEVELS, KontaktPresetChunkID.VOICE_GROUPS, KontaktPresetChunkID.PARAMETER_ARRAY_8, KontaktPresetChunkID.INSERT_BUS, KontaktPresetChunkID.SAVE_SETTINGS, KontaktPresetChunkID.QUICK_BROWSE_DATA:
                this.readStructure (objectInputStream, objectSize);
                break;

            case KontaktPresetChunkID.PARAMETER_ARRAY_16:
                this.readFixedArray (objectInputStream, 16);
                break;

            case KontaktPresetChunkID.SLOT_LIST, KontaktPresetChunkID.PAR_FX, KontaktPresetChunkID.FILENAME_LIST_EX, KontaktPresetChunkID.FILENAME_LIST:
            default:
                this.publicData = objectInputStream.readNBytes (objectSize);
                break;
        }
    }


    /**
     * Write the structure of a preset to a buffer.
     *
     * @param out The output stream to write the preset data to
     * @throws IOException Could not write
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream objectOutputStream = new ByteArrayOutputStream ();
        switch (this.id)
        {
            case KontaktPresetChunkID.GROUP_LIST:
                this.writeArray (objectOutputStream, false);
                break;

            case KontaktPresetChunkID.ZONE_LIST:
                this.writeArray (objectOutputStream, true);
                break;

            case KontaktPresetChunkID.BANK, KontaktPresetChunkID.PROGRAM_CONTAINER, KontaktPresetChunkID.PROGRAM, KontaktPresetChunkID.PAR_SCRIPT, KontaktPresetChunkID.PAR_FX_SEND_LEVELS, KontaktPresetChunkID.VOICE_GROUPS, KontaktPresetChunkID.PARAMETER_ARRAY_8, KontaktPresetChunkID.INSERT_BUS, KontaktPresetChunkID.SAVE_SETTINGS, KontaktPresetChunkID.QUICK_BROWSE_DATA:
                this.writeStructure (objectOutputStream);
                break;

            case KontaktPresetChunkID.SLOT_LIST, KontaktPresetChunkID.PAR_FX, KontaktPresetChunkID.FILENAME_LIST_EX, KontaktPresetChunkID.FILENAME_LIST, KontaktPresetChunkID.PARAMETER_ARRAY_16:
            default:
                objectOutputStream.write (this.publicData);
                break;
        }
        final byte [] objectData = objectOutputStream.toByteArray ();

        StreamUtils.writeUnsigned16 (out, this.id, false);
        StreamUtils.writeUnsigned32 (out, objectData.length, false);
        out.write (objectData);
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
            final int reference = hasReference ? (int) StreamUtils.readUnsigned32 (in, false) : -1;

            final KontaktPresetChunk arrayObject = new KontaktPresetChunk ();
            arrayObject.id = reference;
            arrayObject.readStructure (in, objectSize);
            this.children.add (arrayObject);
        }
    }


    /**
     * Reads some preset chunks.
     *
     * @param in The stream to read from
     * @param size The number of preset chunks to read
     * @throws IOException Could not read
     */
    public void readFixedArray (final InputStream in, final int size) throws IOException
    {
        // TODO
        final int isXXXX = in.read ();
        // System.out.println ("isXXXX: " + isXXXX);

        final int version = StreamUtils.readUnsigned16 (in, false);
        // check for 0x10, 0x11, 0x12, 0x13

        for (int i = 0; i < size; i++)
            if (in.read () > 0)
            {
                final KontaktPresetChunk child = new KontaktPresetChunk ();
                child.id = StreamUtils.readUnsigned16 (in, false);

                final int dataSize = (int) StreamUtils.readUnsigned32 (in, false);
                child.publicData = in.readNBytes (dataSize);
                this.children.add (child);
            }
    }


    /**
     * Reads an array structure which is a list of preset object structures (which do not have an ID
     * or size as the header).
     *
     * @param out The output stream to write the data to
     * @param hasReference If true another integer is read before each array object and stored in
     *            the ID of the children
     * @throws IOException Could not read
     */
    public void writeArray (final OutputStream out, final boolean hasReference) throws IOException
    {
        final int arrayLength = this.children.size ();
        StreamUtils.writeUnsigned32 (out, arrayLength, false);
        for (int index = 0; index < arrayLength; index++)
        {
            final KontaktPresetChunk arrayObject = this.children.get (index);
            if (hasReference)
                StreamUtils.writeUnsigned32 (out, arrayObject.id, false);
            arrayObject.writeStructure (out);
        }
    }


    /**
     * Read the public, private data sections and children structures.
     *
     * @param in The input stream to read the data from
     * @param objectSize The size of the structure to read
     * @throws IOException Could not read
     */
    private void readStructure (final InputStream in, final int objectSize) throws IOException
    {
        final boolean isDataStructured = in.read () > 0;
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
            final KontaktPresetChunk object = new KontaktPresetChunk ();
            object.read (inChildren);
            this.children.add (object);
        }
    }


    /**
     * Write the public, private data sections and children structures.
     *
     * @param out The output stream to write the data to
     * @throws IOException Could not write
     */
    private void writeStructure (final OutputStream out) throws IOException
    {
        final boolean isDataStructured = this.version != -1;
        out.write (isDataStructured ? 1 : 0);
        if (!isDataStructured)
        {
            out.write (this.publicData);
            return;
        }

        StreamUtils.writeUnsigned16 (out, this.version, false);
        StreamUtils.writeUnsigned32 (out, this.privateData.length, false);
        out.write (this.privateData);
        StreamUtils.writeUnsigned32 (out, this.publicData.length, false);
        out.write (this.publicData);

        final ByteArrayOutputStream outChildren = new ByteArrayOutputStream ();
        for (final KontaktPresetChunk object: this.children)
            object.write (outChildren);
        final byte [] childrenData = outChildren.toByteArray ();
        StreamUtils.writeUnsigned32 (out, childrenData.length, false);
        out.write (childrenData);
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
     * Set the ID of the object.
     *
     * @param id The id
     */
    public void setId (final int id)
    {
        this.id = id;
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
     * Set the public data.
     *
     * @param publicData The data
     */
    public void setPublicData (final byte [] publicData)
    {
        this.publicData = publicData;
    }


    /**
     * Set the public data.
     *
     * @param privateData The data
     */
    public void setPrivateData (final byte [] privateData)
    {
        this.privateData = privateData;
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
     * Set the version.
     *
     * @param version The version
     */
    public void setVersion (final int version)
    {
        this.version = version;
    }


    /**
     * Get the child structures.
     *
     * @return The children
     */
    public List<KontaktPresetChunk> getChildren ()
    {
        return this.children;
    }


    /**
     * Set the child structures.
     *
     * @param children The children
     */
    public void setChildren (final List<KontaktPresetChunk> children)
    {
        this.children.clear ();
        this.children.addAll (children);
    }


    /**
     * Dumps all info into a text.
     *
     * @param level The indentation level
     * @return The formatted string
     */
    public String dump (final int level)
    {
        final int padding = level * 4;

        final StringBuilder sb = new StringBuilder ();
        if (this.id < 0)
            sb.append (StringUtils.padLeftSpaces ("Unreferenced Array\n", padding));
        else
            sb.append (StringUtils.padLeftSpaces ("ID: 0x", padding)).append (Integer.toHexString (this.id).toUpperCase ()).append (" ").append (KontaktPresetChunkID.getName (this.id)).append ("\n");

        final int childLevel = level + 1;
        final int childPadding = padding + 4;

        sb.append (StringUtils.padLeftSpaces ("Private (", childPadding)).append (this.privateData == null ? 0 : this.privateData.length).append (" Bytes): ").append (this.privateData == null ? "[]" : StringUtils.formatHexStr (this.privateData)).append ("\n");
        sb.append (StringUtils.padLeftSpaces ("Public  (", childPadding)).append (this.publicData == null ? 0 : this.publicData.length).append (" Bytes): ").append (this.publicData == null ? "[]" : StringUtils.formatHexStr (this.publicData)).append ("\n");

        if (!this.children.isEmpty ())
        {
            sb.append (StringUtils.padLeftSpaces ("Children:\n", childPadding));
            for (final KontaktPresetChunk child: this.children)
                sb.append (child.dump (childLevel));
        }

        return sb.toString ();
    }
}
