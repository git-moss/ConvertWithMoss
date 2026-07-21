// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil K2000/K2500/K2600 object file (.krz, .k25, .k26). The file starts with a 32 byte
 * header ('PRAM' magic) followed by the object blocks and the raw sample data region. Each object
 * block is prefixed with its negative length; a non-negative value terminates the object list.
 * All values are big-endian.
 *
 * The format was derived from the source code of the KurzFiler tool by Marc Halbruegge, see
 * documentation/design/KURZWEIL_FORMAT.md for the details.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilFile
{
    private static final byte []             MAGIC      = "PRAM".getBytes ();

    /** The OS version to write into the header (3.53, the value KurzFiler writes). */
    private static final int                 OS_VERSION = 353;

    private final Map<Integer, KurzweilSample> samples  = new LinkedHashMap<> ();
    private final Map<Integer, KurzweilKeymap> keymaps  = new LinkedHashMap<> ();
    private final List<KurzweilProgram>      programs   = new ArrayList<> ();


    /**
     * Constructor for a new empty file.
     */
    public KurzweilFile ()
    {
        // Intentionally empty
    }


    /**
     * Constructor. Parses the file from the given data.
     *
     * @param fileData The content of the file
     * @throws IOException Could not parse the file
     */
    public KurzweilFile (final byte [] fileData) throws IOException
    {
        if (fileData.length < 32 || fileData[0] != MAGIC[0] || fileData[1] != MAGIC[1] || fileData[2] != MAGIC[2] || fileData[3] != MAGIC[3])
            throw new IOException ("Not a Kurzweil K2000/K2500/K2600 file.");

        final int sampleDataOffset = readSigned32 (fileData, 4);

        int position = 32;
        while (position + 4 <= fileData.length)
        {
            final int blockSize = readSigned32 (fileData, position);
            if (blockSize >= 0)
                break;
            final int objectLength = -blockSize - 4;
            final int objectStart = position + 4;
            if (objectLength < 6 || objectStart + objectLength > fileData.length)
                throw new IOException ("Broken object block in Kurzweil file.");

            this.parseObject (fileData, objectStart, objectLength);

            position = objectStart + objectLength;
        }

        for (final KurzweilSample sample: this.samples.values ())
            for (final KurzweilSampleHeader header: sample.getHeaders ())
                header.extractSampleData (fileData, sampleDataOffset);
    }


    private void parseObject (final byte [] fileData, final int objectStart, final int objectLength) throws IOException
    {
        final int hash = (fileData[objectStart] & 0xFF) << 8 | fileData[objectStart + 1] & 0xFF;
        final int nameOffset = (fileData[objectStart + 4] & 0xFF) << 8 | fileData[objectStart + 5] & 0xFF;
        final int dataStart = 4 + nameOffset;
        if (dataStart > objectLength)
            throw new IOException ("Broken object header in Kurzweil file.");

        final StringBuilder name = new StringBuilder ();
        for (int i = objectStart + 6; i < objectStart + dataStart && fileData[i] != 0; i++)
            name.append ((char) (fileData[i] & 0xFF));

        final int id = KurzweilObjectID.getID (hash);
        final ByteArrayInputStream in = new ByteArrayInputStream (fileData, objectStart + dataStart, objectLength - dataStart);
        switch (KurzweilObjectID.getType (hash))
        {
            case KurzweilObjectID.TYPE_SAMPLE:
                this.samples.put (Integer.valueOf (id), new KurzweilSample (id, name.toString (), in));
                break;
            case KurzweilObjectID.TYPE_KEYMAP:
                this.keymaps.put (Integer.valueOf (id), new KurzweilKeymap (id, name.toString (), in));
                break;
            case KurzweilObjectID.TYPE_PROGRAM:
                this.programs.add (new KurzweilProgram (id, name.toString (), in));
                break;
            default:
                // All other object types (songs, effects, quick-access banks, ...) are not
                // relevant for the conversion
                break;
        }
    }


    private static int readSigned32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16 | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }


    /**
     * Write the file to the stream. The objects are written in hash order (programs, keymaps,
     * samples) followed by the sample data of all sample objects in the same order.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream out) throws IOException
    {
        // The sample data of the objects is placed consecutively into the sample data region
        final Map<KurzweilSample, Integer> wordOffsets = new LinkedHashMap<> ();
        int wordOffset = 0;
        for (final KurzweilSample sample: this.samples.values ())
        {
            wordOffsets.put (sample, Integer.valueOf (wordOffset));
            wordOffset += sample.getNumberOfWords ();
        }

        final ByteArrayOutputStream objectRegion = new ByteArrayOutputStream ();
        for (final KurzweilProgram program: this.programs)
            writeObjectBlock (objectRegion, KurzweilObjectID.createHash (KurzweilObjectID.TYPE_PROGRAM, program.getId ()), program.getName (), program.createObjectData ());
        for (final KurzweilKeymap keymap: this.keymaps.values ())
            writeObjectBlock (objectRegion, KurzweilObjectID.createHash (KurzweilObjectID.TYPE_KEYMAP, keymap.getId ()), keymap.getName (), keymap.createObjectData ());
        for (final KurzweilSample sample: this.samples.values ())
            writeObjectBlock (objectRegion, KurzweilObjectID.createHash (KurzweilObjectID.TYPE_SAMPLE, sample.getId ()), sample.getName (), sample.createObjectData (wordOffsets.get (sample).intValue ()));

        // The header with the offset of the sample data region (header + objects + terminator)
        out.write (MAGIC);
        StreamUtils.writeSigned32 (out, 32 + objectRegion.size () + 4, true);
        for (int i = 0; i < 6; i++)
            StreamUtils.writeSigned32 (out, i == 2 ? OS_VERSION : 0, true);

        objectRegion.writeTo (out);

        // The terminating block size
        StreamUtils.writeSigned32 (out, 0, true);

        for (final KurzweilSample sample: this.samples.values ())
            for (final KurzweilSampleHeader header: sample.getHeaders ())
                if (header.hasSampleData ())
                    out.write (header.getSampleData ());
    }


    private static void writeObjectBlock (final ByteArrayOutputStream out, final int hash, final String name, final byte [] objectData) throws IOException
    {
        final ByteArrayOutputStream object = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned16 (object, hash, true);
        // Placeholder for the object size
        StreamUtils.writeUnsigned16 (object, 0, true);
        final int nameLength = name.length ();
        StreamUtils.writeUnsigned16 (object, nameLength % 2 == 0 ? nameLength + 4 : nameLength + 3, true);
        for (int i = 0; i < nameLength; i++)
            object.write (name.charAt (i));
        // 0-terminate the name and pad it to an even length
        object.write (0);
        if (nameLength % 2 == 0)
            object.write (0);
        object.write (objectData);
        // Pad the object to an even length
        if (object.size () % 2 == 1)
            object.write (0);

        final byte [] objectBytes = object.toByteArray ();
        objectBytes[2] = (byte) (objectBytes.length >>> 8 & 0xFF);
        objectBytes[3] = (byte) (objectBytes.length & 0xFF);

        // The block: the negative length prefix, the object and padding to a 4-byte boundary
        final int paddedLength = objectBytes.length + 3 & ~3;
        StreamUtils.writeSigned32 (out, -(4 + paddedLength), true);
        out.write (objectBytes);
        for (int i = objectBytes.length; i < paddedLength; i++)
            out.write (0);
    }


    /**
     * Get all sample objects indexed by their ID.
     *
     * @return The sample objects
     */
    public Map<Integer, KurzweilSample> getSamples ()
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
    public List<KurzweilProgram> getPrograms ()
    {
        return this.programs;
    }
}
