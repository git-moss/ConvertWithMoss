// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class SoundinfoChunkData extends AbstractChunkData
{
    private String                    soundInfoVersionText;
    private String                    name;
    private String                    author;
    private String                    vendor;
    private String                    description;
    private final List<String>        tags       = new ArrayList<> ();
    private final List<String>        attributes = new ArrayList<> ();
    private final Map<String, String> properties = new TreeMap<> ();
    private int                       versionMajor;
    private int                       versionMinor;
    private int                       versionPatch;


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        this.versionMajor = (int) StreamUtils.readUnsigned32 (in, false);
        this.versionMinor = (int) StreamUtils.readUnsigned32 (in, false);
        this.versionPatch = (int) StreamUtils.readUnsigned32 (in, false);
        this.soundInfoVersionText = this.versionMajor + "." + this.versionMinor + "." + this.versionPatch;

        this.name = StreamUtils.readWithLengthUTF16 (in);
        this.author = StreamUtils.readWithLengthUTF16 (in);
        this.vendor = StreamUtils.readWithLengthUTF16 (in);
        this.description = StreamUtils.readWithLengthUTF16 (in);

        // Always 0
        StreamUtils.readUnsigned32 (in, false);
        // Always FF FF FF FF FF FF FF FF (-1)
        StreamUtils.readUnsigned64 (in, false);
        // Always 0
        StreamUtils.readUnsigned64 (in, false);
        // Always 0
        StreamUtils.readUnsigned64 (in, false);

        // Always 1
        StreamUtils.readUnsigned32 (in, false);
        // Always 1
        StreamUtils.readUnsigned32 (in, false);

        final int numberOfTags = (int) StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfTags; i++)
            this.tags.add (StreamUtils.readWithLengthUTF16 (in));

        final int numberOfAttributes = (int) StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfAttributes; i++)
        {
            final String value = StreamUtils.readWithLengthUTF16 (in);
            if (!"KontaktInstrument".equals (value))
                this.attributes.add (value);
        }

        // Always 0
        StreamUtils.readUnsigned32 (in, false);

        final int numberOfProperties = (int) StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfProperties; i++)
        {
            final String key = StreamUtils.readWithLengthUTF16 (in);
            final String value = StreamUtils.readWithLengthUTF16 (in);
            this.properties.put (key, value);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        StreamUtils.writeUnsigned32 (out, this.versionMajor, false);
        StreamUtils.writeUnsigned32 (out, this.versionMinor, false);
        StreamUtils.writeUnsigned32 (out, this.versionPatch, false);

        StreamUtils.writeWithLengthUTF16 (out, this.name);
        StreamUtils.writeWithLengthUTF16 (out, this.author);
        StreamUtils.writeWithLengthUTF16 (out, this.vendor);
        StreamUtils.writeWithLengthUTF16 (out, this.description);

        // Always 0
        StreamUtils.writeUnsigned32 (out, 0, false);
        // Always FF FF FF FF FF FF FF FF (-1)
        StreamUtils.writeUnsigned64 (out, -1, false);
        // Always 0
        StreamUtils.writeUnsigned64 (out, 0, false);
        // Always 0
        StreamUtils.writeUnsigned64 (out, 0, false);

        // Always 1
        StreamUtils.writeUnsigned32 (out, 1, false);
        // Always 1
        StreamUtils.writeUnsigned32 (out, 1, false);

        StreamUtils.writeUnsigned32 (out, this.tags.size (), false);
        for (final String element: this.tags)
            StreamUtils.writeWithLengthUTF16 (out, element);

        StreamUtils.writeUnsigned32 (out, this.attributes.size (), false);
        for (final String element: this.attributes)
            StreamUtils.writeWithLengthUTF16 (out, element);

        // Always 0
        StreamUtils.writeUnsigned32 (out, 0, false);

        StreamUtils.writeUnsigned32 (out, this.properties.size (), false);
        for (final Map.Entry<String, String> e: this.properties.entrySet ())
        {
            StreamUtils.writeWithLengthUTF16 (out, e.getKey ());
            StreamUtils.writeWithLengthUTF16 (out, e.getValue ());
        }
    }


    /**
     * Get the version of the sound info.
     *
     * @return The sound info version
     */
    public String getSoundInfoVersion ()
    {
        return this.soundInfoVersionText;
    }


    /**
     * Get the name of the sound.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the sound.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Get the author of the sound.
     *
     * @return The author
     */
    public String getAuthor ()
    {
        return this.author;
    }


    /**
     * Set the author of the sound.
     *
     * @param author The author
     */
    public void setAuthor (final String author)
    {
        this.author = author;
    }


    /**
     * Get the vendor of the sound.
     *
     * @return The vendor
     */
    public String getVendor ()
    {
        return this.vendor;
    }


    /**
     * Set the vendor of the sound.
     *
     * @param vendor The vendor
     */
    public void setVendor (final String vendor)
    {
        this.vendor = vendor;
    }


    /**
     * Get the description of the sound.
     *
     * @return The description
     */
    public String getDescription ()
    {
        return this.description;
    }


    /**
     * Set the description of the sound.
     *
     * @param description The description
     */
    public void setDescription (final String description)
    {
        this.description = description;
    }


    /**
     * Get the tags.
     *
     * @return The tags
     */
    public List<String> getTags ()
    {
        return this.tags;
    }


    /**
     * Set the tags.
     * 
     * @param tags The tags to set
     */
    public void setTags (final List<String> tags)
    {
        this.tags.clear ();
        this.tags.addAll (tags);
    }


    /**
     * Get the attributes.
     *
     * @return The attributes
     */
    public List<String> getAttributes ()
    {
        return this.attributes;
    }


    /**
     * Set the attributes.
     * 
     * @param attributes The attributes to set
     */
    public void setAttributes (final List<String> attributes)
    {
        this.attributes.clear ();
        this.attributes.addAll (attributes);
    }


    /**
     * Get the properties.
     *
     * @return The properties
     */
    public Map<String, String> getProperties ()
    {
        return this.properties;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return Objects.hash (this.attributes, this.author, this.description, this.name, this.properties, this.soundInfoVersionText, this.tags, this.vendor, Integer.valueOf (this.versionMajor), Integer.valueOf (this.versionMinor), Integer.valueOf (this.versionPatch));
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final SoundinfoChunkData other = (SoundinfoChunkData) obj;
        return Objects.equals (this.attributes, other.attributes) && Objects.equals (this.author, other.author) && Objects.equals (this.description, other.description) && Objects.equals (this.name, other.name) && Objects.equals (this.properties, other.properties) && Objects.equals (this.soundInfoVersionText, other.soundInfoVersionText) && Objects.equals (this.tags, other.tags) && Objects.equals (this.vendor, other.vendor) && this.versionMajor == other.versionMajor && this.versionMinor == other.versionMinor && this.versionPatch == other.versionPatch;
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        final int padding = level * 4;
        final StringBuilder sb = new StringBuilder ();
        sb.append (StringUtils.padLeftSpaces ("* SoundInfo Version: ", padding)).append (this.soundInfoVersionText).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Name: ", padding)).append (this.name).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Author: ", padding)).append (this.author == null || this.author.isBlank () ? "-" : this.author).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Vendor: ", padding)).append (this.vendor == null || this.vendor.isBlank () ? "-" : this.vendor).append ('\n');
        sb.append (StringUtils.padLeftSpaces ("* Description: ", padding)).append (this.description == null || this.description.isBlank () ? "-" : this.description).append ('\n');

        sb.append (StringUtils.padLeftSpaces ("* Tags: ", padding));
        if (this.tags.isEmpty ())
            sb.append ("None");
        else
            for (final String tag: this.tags)
                sb.append ('\'').append (tag).append ("' ");
        sb.append ('\n');

        sb.append (StringUtils.padLeftSpaces ("* Attributes: ", padding));
        if (this.attributes.isEmpty ())
            sb.append ("None");
        else
            for (final String attribute: this.attributes)
                sb.append ('\'').append (attribute).append ("' ");
        sb.append ('\n');

        sb.append (StringUtils.padLeftSpaces ("* Properties: ", padding));
        if (this.properties.isEmpty ())
            sb.append ("None\n");
        else
        {
            sb.append ('\n');
            for (final Entry<String, String> e: this.properties.entrySet ())
                sb.append (StringUtils.padLeftSpaces (e.getKey (), padding + 4)).append (": ").append (e.getValue ()).append ('\n');
        }

        return sb.toString ();
    }
}
