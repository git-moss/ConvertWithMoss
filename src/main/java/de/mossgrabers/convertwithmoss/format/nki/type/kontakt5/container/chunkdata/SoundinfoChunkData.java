package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.container.chunkdata;

import de.mossgrabers.convertwithmoss.file.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The root chunk of all containers.
 *
 * @author Jürgen Moßgraber
 */
public class SoundinfoChunkData extends AbstractChunkData
{
    private String              soundInfoVersion;
    private String              name;
    private String              author;
    private String              vendor;
    private final List<String>        tags       = new ArrayList<> ();
    private final List<String>        attributes = new ArrayList<> ();
    private final Map<String, String> properties = new HashMap<> ();


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        super.read (in);

        final int versionMajor = StreamUtils.readUnsigned32 (in, false);
        final int versionMinor = StreamUtils.readUnsigned32 (in, false);
        final int versionPatch = StreamUtils.readUnsigned32 (in, false);
        this.soundInfoVersion = versionMajor + "." + versionMinor + "." + versionPatch;

        this.name = StreamUtils.readWithLengthUTF16 (in);
        this.author = StreamUtils.readWithLengthUTF16 (in);
        this.vendor = StreamUtils.readWithLengthUTF16 (in);

        // Always 0
        StreamUtils.readUnsigned64 (in, false);
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

        final int numberOfTags = StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfTags; i++)
            this.tags.add (StreamUtils.readWithLengthUTF16 (in));

        final int numberOfAttributes = StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfAttributes; i++)
            this.attributes.add (StreamUtils.readWithLengthUTF16 (in));

        // Always 0
        StreamUtils.readUnsigned32 (in, false);

        final int numberOfProperties = StreamUtils.readUnsigned32 (in, false);
        for (int i = 0; i < numberOfProperties; i++)
        {
            final String key = StreamUtils.readWithLengthUTF16 (in);
            final String value = StreamUtils.readWithLengthUTF16 (in);
            this.properties.put (key, value);
        }
    }


    /**
     * Get the version of the sound info.
     *
     * @return The sound info version
     */
    public String getSoundInfoVersion ()
    {
        return this.soundInfoVersion;
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
     * Get the author of the sound.
     *
     * @return The author
     */
    public String getAuthor ()
    {
        return this.author;
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
     * Get the tags.
     *
     * @return The tags
     */
    public List<String> getTags ()
    {
        return this.tags;
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
     * Get the properties.
     *
     * @return The properties
     */
    public Map<String, String> getProperties ()
    {
        return this.properties;
    }
}
