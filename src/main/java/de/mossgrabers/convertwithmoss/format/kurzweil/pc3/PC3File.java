// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil.pc3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.kurzweil.KurzweilKeymap;


/**
 * An object file of the Kurzweil PC3 series (PC3, PC3K, PC3A, PC3LE), Forte, Forte SE, PC4 and K2700
 * (.pc3, .p3k, .p3a, .ple, .for, .fse, .pc4, .p4s, .k27). The file starts with a 36 byte header
 * ('COOL' magic) followed by the objects and the raw sample data region. Each object starts with a
 * 20 byte header (type, ID, size and the offset of its data) and its name; a zero word terminates
 * the object list. All values are big-endian.
 *
 * See documentation/design/KURZWEIL_PC3_FORMAT.md for the details.
 *
 * @author Jürgen Moßgraber
 */
public class PC3File
{
    /** The object type of a keymap. */
    public static final int                    TYPE_KEYMAP          = 0x85;
    /** The object type of a program. */
    public static final int                    TYPE_PROGRAM         = 0x8A;
    /** The object type of a sample. */
    public static final int                    TYPE_SAMPLE          = 0x9E;

    /** The first object ID of the user area of the devices. */
    public static final int                    FIRST_ID             = 1024;
    /** The highest object ID of the user area of the devices. */
    public static final int                    LAST_ID              = 4095;

    private static final byte []               MAGIC                = "COOL".getBytes (StandardCharsets.US_ASCII);
    private static final int                   HEADER_LENGTH        = 36;
    private static final int                   OBJECT_HEADER_LENGTH = 20;

    private final Map<Integer, PC3Sample>      samples              = new LinkedHashMap<> ();
    private final Map<Integer, KurzweilKeymap> keymaps              = new LinkedHashMap<> ();
    private final List<PC3Program>             programs             = new ArrayList<> ();


    /**
     * Constructor for a new empty file.
     */
    public PC3File ()
    {
        // Intentionally empty
    }


    /**
     * Constructor. Parses the file from the given data.
     *
     * @param fileData The content of the file
     * @throws IOException Could not parse the file
     */
    public PC3File (final byte [] fileData) throws IOException
    {
        if (fileData.length < HEADER_LENGTH || fileData[0] != MAGIC[0] || fileData[1] != MAGIC[1] || fileData[2] != MAGIC[2] || fileData[3] != MAGIC[3])
            throw new IOException ("Not a Kurzweil PC3/Forte file.");

        // The size of the object region is the offset of the sample data region
        final long objectRegionSize = readUnsigned32 (fileData, 8);
        if (objectRegionSize < HEADER_LENGTH || objectRegionSize > fileData.length)
            throw new IOException ("Broken header in Kurzweil PC3/Forte file.");
        final int sampleDataOffset = (int) objectRegionSize;

        int position = HEADER_LENGTH;
        while (position + OBJECT_HEADER_LENGTH <= sampleDataOffset)
        {
            final int type = readSigned32 (fileData, position);
            final int size = readSigned32 (fileData, position + 12);
            // A zero word terminates the object list
            if (type == 0 || size < OBJECT_HEADER_LENGTH)
                break;
            if (position + size > sampleDataOffset)
                throw new IOException ("Broken object in Kurzweil PC3/Forte file.");

            final int id = readSigned32 (fileData, position + 4);
            final int nameLength = readUnsigned16 (fileData, position + 18) - 2;
            if (nameLength < 0 || OBJECT_HEADER_LENGTH + nameLength > size)
                throw new IOException ("Broken object header in Kurzweil PC3/Forte file.");
            final StringBuilder name = new StringBuilder ();
            for (int i = position + OBJECT_HEADER_LENGTH; i < position + OBJECT_HEADER_LENGTH + nameLength && fileData[i] != 0; i++)
                name.append ((char) (fileData[i] & 0xFF));

            final int dataStart = position + OBJECT_HEADER_LENGTH + nameLength;
            this.parseObject (type, id, name.toString ().strip (), fileData, dataStart, position + size - dataStart);

            position += size;
        }

        for (final PC3Sample sample: this.samples.values ())
            for (final PC3SampleHeader header: sample.getHeaders ())
                header.extractSampleData (fileData, sampleDataOffset);
    }


    private void parseObject (final int type, final int id, final String name, final byte [] fileData, final int dataStart, final int dataLength) throws IOException
    {
        switch (type)
        {
            case TYPE_SAMPLE:
                this.samples.put (Integer.valueOf (id), new PC3Sample (id, name, new ByteArrayInputStream (fileData, dataStart, dataLength)));
                break;
            case TYPE_KEYMAP:
                this.keymaps.put (Integer.valueOf (id), new KurzweilKeymap (id, name, new ByteArrayInputStream (fileData, dataStart, dataLength)));
                break;
            case TYPE_PROGRAM:
                this.programs.add (new PC3Program (id, name, Arrays.copyOfRange (fileData, dataStart, dataStart + dataLength)));
                break;
            default:
                // Setups/multis, effect chains, algorithms, songs, patterns, ... are not relevant for
                // the conversion
                break;
        }
    }


    /**
     * Write the file to the stream. The objects are written in the order programs, keymaps, samples
     * followed by the sample data of all sample objects in the same order.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream objectRegion = new ByteArrayOutputStream ();
        for (final PC3Program program: this.programs)
            writeObject (objectRegion, TYPE_PROGRAM, program.getId (), program.getName (), program.createObjectData ());
        for (final KurzweilKeymap keymap: this.keymaps.values ())
            writeObject (objectRegion, TYPE_KEYMAP, keymap.getId (), keymap.getName (), keymap.createObjectData ());

        // The sample data of the objects is placed consecutively into the sample data region,
        // which the 32-bit positions of the sample headers address
        long byteOffset = 0;
        for (final PC3Sample sample: this.samples.values ())
        {
            if (byteOffset + sample.getNumberOfDataBytes () > Integer.MAX_VALUE)
                throw new IOException ("The sample data exceeds the 2 GB which a Kurzweil PC3/Forte file can address.");
            writeObject (objectRegion, TYPE_SAMPLE, sample.getId (), sample.getName (), sample.createObjectData ((int) byteOffset));
            byteOffset += sample.getNumberOfDataBytes ();
        }

        // The header: the size of the object region (header + objects + terminator) is the offset
        // of the sample data region, the last field holds the negative size of the object list
        final int objectRegionSize = HEADER_LENGTH + objectRegion.size () + 4;
        out.write (MAGIC);
        StreamUtils.writeSigned32 (out, 0, true);
        StreamUtils.writeSigned32 (out, objectRegionSize, true);
        for (int i = 0; i < 5; i++)
            StreamUtils.writeSigned32 (out, 0, true);
        StreamUtils.writeSigned32 (out, -(objectRegionSize - HEADER_LENGTH), true);

        objectRegion.writeTo (out);

        // The terminating zero word
        StreamUtils.writeSigned32 (out, 0, true);

        for (final PC3Sample sample: this.samples.values ())
            for (final PC3SampleHeader header: sample.getHeaders ())
                if (header.hasSampleData ())
                    out.write (header.getSampleData ());
    }


    private static void writeObject (final OutputStream out, final int type, final int id, final String name, final byte [] data) throws IOException
    {
        // The 0-terminated name and the data are both padded to a multiple of 4 bytes
        final int nameFieldLength = name.length () + 1 + 3 & ~3;
        final int dataLength = data.length + 3 & ~3;

        StreamUtils.writeSigned32 (out, type, true);
        StreamUtils.writeSigned32 (out, id, true);
        StreamUtils.writeSigned32 (out, 0, true);
        StreamUtils.writeSigned32 (out, OBJECT_HEADER_LENGTH + nameFieldLength + dataLength, true);
        StreamUtils.writeUnsigned16 (out, 0, true);
        StreamUtils.writeUnsigned16 (out, nameFieldLength + 2, true);
        for (int i = 0; i < name.length (); i++)
            out.write (name.charAt (i));
        for (int i = name.length (); i < nameFieldLength; i++)
            out.write (0);
        out.write (data);
        for (int i = data.length; i < dataLength; i++)
            out.write (0);
    }


    private static int readSigned32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }


    private static long readUnsigned32 (final byte [] data, final int offset)
    {
        return readSigned32 (data, offset) & 0xFFFFFFFFL;
    }


    private static int readUnsigned16 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }


    /**
     * Get all sample objects indexed by their ID.
     *
     * @return The sample objects
     */
    public Map<Integer, PC3Sample> getSamples ()
    {
        return this.samples;
    }


    /**
     * Get all keymap objects indexed by their ID.
     *
     * @return The keymap objects
     */
    public Map<Integer, KurzweilKeymap> getKeymaps ()
    {
        return this.keymaps;
    }


    /**
     * Get all program objects.
     *
     * @return The program objects
     */
    public List<PC3Program> getPrograms ()
    {
        return this.programs;
    }
}
