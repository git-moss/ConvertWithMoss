// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil sample object (type 38). Contains one sample header per recording. A sample with
 * several headers is a multi-root sample; for stereo samples the headers form left/right pairs
 * (even index = left channel).
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilSample
{
    private static final int                 FLAG_STEREO      = 1;

    /** The default natural envelope which KurzFiler writes: 2 records of 6 signed 16-bit values. */
    private static final int [] []           DEFAULT_ENVELOPE =
    {
        {
            -1,
            1,
            0,
            0,
            -1600,
            0
        },
        {
            -1,
            1,
            0,
            0,
            -1600,
            0
        }
    };

    private final int                        id;
    private final String                     name;
    private int                              baseID           = 1;
    private int                              flags            = 0;
    private int                              copyID           = 0;

    private final List<KurzweilSampleHeader> headers          = new ArrayList<> ();


    /**
     * Constructor for a new empty sample object.
     *
     * @param id The object ID
     * @param name The name of the sample, maximum 16 characters
     */
    public KurzweilSample (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Constructor. Reads the object data (the part after the object name) from the stream.
     *
     * @param id The object ID
     * @param name The name of the sample
     * @param in The input stream to read from
     * @throws IOException Could not read the object
     */
    public KurzweilSample (final int id, final String name, final InputStream in) throws IOException
    {
        this.id = id;
        this.name = name;

        this.baseID = StreamUtils.readSigned16 (in, true);
        final int numHeaders = StreamUtils.readSigned16 (in, true);
        // The offset to the first header - always 8
        StreamUtils.readSigned16 (in, true);
        this.flags = in.read ();
        // 1 unused byte, the copy ID and 2 unused bytes
        in.read ();
        this.copyID = StreamUtils.readSigned16 (in, true);
        StreamUtils.readSigned16 (in, true);

        for (int i = 0; i <= numHeaders; i++)
            this.headers.add (new KurzweilSampleHeader (in));

        // The rest of the object data holds the natural envelope records which are not
        // interpreted and re-created with default values when writing
    }


    /**
     * Write the object data (the part after the object name) to the stream.
     *
     * @param wordOffset The word offset in the sample data region at which the sample data of this
     *            object will be placed
     * @return The object data
     * @throws IOException Could not write the object
     */
    public byte [] createObjectData (final int wordOffset) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        StreamUtils.writeSigned16 (out, this.baseID, true);
        StreamUtils.writeSigned16 (out, this.headers.size () - 1, true);
        StreamUtils.writeSigned16 (out, 8, true);
        out.write (this.flags);
        out.write (0);
        StreamUtils.writeSigned16 (out, this.copyID, true);
        StreamUtils.writeSigned16 (out, 0, true);

        // Every header points both of its envelope offset fields at the first envelope record
        // which starts right after the last header (offsets are relative to the fields at bytes
        // 24/26 of the header)
        int offset = wordOffset;
        final int numHeaders = this.headers.size ();
        for (int i = 0; i < numHeaders; i++)
        {
            final KurzweilSampleHeader header = this.headers.get (i);
            final int distanceToEnvelope = (numHeaders - 1 - i) * KurzweilSampleHeader.LENGTH;
            header.setEnvelopeOffsets (distanceToEnvelope + 8, distanceToEnvelope + 6);
            header.write (out, offset);
            if (header.hasSampleData ())
                offset += header.getNumberOfFrames ();
        }

        for (final int [] envelope: DEFAULT_ENVELOPE)
            for (final int value: envelope)
                StreamUtils.writeSigned16 (out, value, true);

        return out.toByteArray ();
    }


    /**
     * Get the object ID.
     *
     * @return The ID
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the name of the sample.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Is this a stereo sample? If true the headers form left/right pairs.
     *
     * @return True if stereo
     */
    public boolean isStereo ()
    {
        return (this.flags & FLAG_STEREO) > 0;
    }


    /**
     * Set the stereo flag.
     *
     * @param isStereo True for a stereo sample
     */
    public void setStereo (final boolean isStereo)
    {
        this.flags = isStereo ? FLAG_STEREO : 0;
    }


    /**
     * Get all sample headers.
     *
     * @return The headers
     */
    public List<KurzweilSampleHeader> getHeaders ()
    {
        return this.headers;
    }


    /**
     * Add a sample header.
     *
     * @param header The header to add
     * @return The 1-based index of the added header (= the sub-sample number to reference it from a
     *         keymap entry)
     */
    public int addHeader (final KurzweilSampleHeader header)
    {
        this.headers.add (header);
        return this.headers.size ();
    }


    /**
     * Get the number of words which the sample data of all headers occupies in the sample data
     * region.
     *
     * @return The number of words
     */
    public int getNumberOfWords ()
    {
        int words = 0;
        for (final KurzweilSampleHeader header: this.headers)
            if (header.hasSampleData ())
                words += header.getNumberOfFrames ();
        return words;
    }
}
