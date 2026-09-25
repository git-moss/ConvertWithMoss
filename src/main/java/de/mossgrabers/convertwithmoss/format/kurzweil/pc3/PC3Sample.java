// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil.pc3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil PC3 series / Forte sample object. Contains one sample header per recording. A sample
 * with several headers is a multi-root sample; for stereo samples the headers form left/right pairs
 * (even index = left channel). Apart from the longer headers the object is laid out like a sample
 * object of the K2000/K2500/K2600. The Forte generation (Forte, PC4, K2700) stores its samples in
 * an object of its own type with a 16 byte preamble and headers with 64-bit positions. Both
 * layouts are read and written.
 *
 * @author Jürgen Moßgraber
 */
public class PC3Sample
{
    private static final int            FLAG_STEREO      = 1;

    /** The default natural envelope written by a PC3K: 2 records of 6 signed 16-bit values. */
    private static final int [] []      DEFAULT_ENVELOPE          =
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

    /** The default natural envelope written by a K2700. */
    private static final int [] []      DEFAULT_ENVELOPE_EXTENDED =
    {
        {
            -1,
            256,
            0,
            0,
            -5416,
            0
        },
        {
            -1,
            256,
            0,
            0,
            -5416,
            0
        }
    };

    private final int                   id;
    private final String                name;
    private int                         baseID           = 1;
    private int                         flags            = 0;
    private int                         copyID           = 0;

    private final List<PC3SampleHeader> headers          = new ArrayList<> ();


    /**
     * Constructor for a new empty sample object.
     *
     * @param id The object ID
     * @param name The name of the sample, maximum 16 characters
     */
    public PC3Sample (final int id, final String name)
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
     * @param isExtended True for a sample object of the Forte generation (Forte, PC4, K2700):
     *            a 16 byte preamble with the number of headers and headers with 64-bit positions
     * @throws IOException Could not read the object
     */
    public PC3Sample (final int id, final String name, final InputStream in, final boolean isExtended) throws IOException
    {
        this.id = id;
        this.name = name;

        final int numHeaders;
        if (isExtended)
        {
            this.baseID = StreamUtils.readSigned32 (in, true);
            // Always 1
            StreamUtils.readSigned16 (in, true);
            numHeaders = StreamUtils.readSigned16 (in, true) + 1;
            // The offset to the first header - always 8
            StreamUtils.readSigned16 (in, true);
            this.flags = in.read ();
            // 5 unused bytes
            in.readNBytes (5);
        }
        else
        {
            this.baseID = StreamUtils.readSigned16 (in, true);
            numHeaders = StreamUtils.readSigned16 (in, true) + 1;
            // The offset to the first header - always 8
            StreamUtils.readSigned16 (in, true);
            this.flags = in.read ();
            // 1 unused byte, the copy ID and 2 unused bytes
            in.read ();
            this.copyID = StreamUtils.readSigned16 (in, true);
            StreamUtils.readSigned16 (in, true);
        }

        final int headerLength = isExtended ? PC3SampleHeader.LENGTH_EXTENDED : PC3SampleHeader.LENGTH;
        if (numHeaders <= 0 || numHeaders * headerLength > in.available ())
            throw new IOException ("Broken sample object in Kurzweil PC3/Forte file.");
        for (int i = 0; i < numHeaders; i++)
            this.headers.add (new PC3SampleHeader (in, isExtended));

        // The rest of the object data holds the natural envelope records which are not
        // interpreted and re-created with default values when writing
    }


    /**
     * Write the object data (the part after the object name) to the stream.
     *
     * @param byteOffset The byte offset in the sample data region at which the sample data of this
     *            object will be placed
     * @param isExtended True to write the sample object of the Forte generation (Forte, PC4,
     *            K2700) with 64-bit positions
     * @return The object data
     * @throws IOException Could not write the object
     */
    public byte [] createObjectData (final int byteOffset, final boolean isExtended) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        if (isExtended)
        {
            StreamUtils.writeSigned32 (out, this.baseID, true);
            StreamUtils.writeSigned16 (out, 1, true);
            StreamUtils.writeSigned16 (out, this.headers.size () - 1, true);
            StreamUtils.writeSigned16 (out, 8, true);
            out.write (this.flags);
            for (int i = 0; i < 5; i++)
                out.write (0);
        }
        else
        {
            StreamUtils.writeSigned16 (out, this.baseID, true);
            StreamUtils.writeSigned16 (out, this.headers.size () - 1, true);
            StreamUtils.writeSigned16 (out, 8, true);
            out.write (this.flags);
            out.write (0);
            StreamUtils.writeSigned16 (out, this.copyID, true);
            StreamUtils.writeSigned16 (out, 0, true);
        }

        // Every header points its envelope offset fields at the two envelope records which start
        // right after the last header (the offsets are relative to the fields themselves, which sit
        // 16 bytes before the end of a header)
        final int headerLength = isExtended ? PC3SampleHeader.LENGTH_EXTENDED : PC3SampleHeader.LENGTH;
        int offset = byteOffset;
        final int numHeaders = this.headers.size ();
        for (int i = 0; i < numHeaders; i++)
        {
            final PC3SampleHeader header = this.headers.get (i);
            final int distanceToEnvelope = (numHeaders - 1 - i) * headerLength;
            if (isExtended)
                header.setEnvelopeOffsets (distanceToEnvelope + 16, distanceToEnvelope + 26);
            else
                header.setEnvelopeOffsets (distanceToEnvelope + 16, distanceToEnvelope + 14);
            header.write (out, offset, isExtended);
            offset += header.getNumberOfDataBytes ();
        }

        for (final int [] envelope: isExtended ? DEFAULT_ENVELOPE_EXTENDED : DEFAULT_ENVELOPE)
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
    public List<PC3SampleHeader> getHeaders ()
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
    public int addHeader (final PC3SampleHeader header)
    {
        this.headers.add (header);
        return this.headers.size ();
    }


    /**
     * Get the number of bytes which the sample data of all headers occupies in the sample data
     * region.
     *
     * @return The number of bytes
     */
    public int getNumberOfDataBytes ()
    {
        int bytes = 0;
        for (final PC3SampleHeader header: this.headers)
            bytes += header.getNumberOfDataBytes ();
        return bytes;
    }
}
