// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;


/**
 * Reads and writes the section container which is shared by the Roland FANTOM <i>.SVD</i> (magic
 * <i>SVD5</i>) and <i>.SVZ</i> (magic <i>SVZa</i>) files. The file starts with a header and a
 * table-of-contents of 16 byte entries; each entry points to a section. A section starts with a 16
 * byte header ({@code count, unitSize, dataOffset=16, reserved=0}) followed by its records. All
 * integers inside the container are little-endian.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreContainer
{
    /** Platform tag used inside a SVD table-of-contents. */
    public static final String   PLATFORM_SVD = "KY19";
    /** Platform tag used inside a SVZ table-of-contents. */
    public static final String   PLATFORM_SVZ = "ZCOR";

    private static final byte [] MAGIC_SVD    = new byte []
    {
        'S',
        'V',
        'D',
        '5'
    };
    private static final byte [] MAGIC_SVZ    = new byte []
    {
        'S',
        'V',
        'Z',
        'a'
    };


    /** A parsed section. */
    public static final class Section
    {
        final String  tag;
        final int     fileOffset;
        final int     count;
        final int     unitSize;
        final int     dataStart;
        final byte [] file;


        Section (final String tag, final int fileOffset, final byte [] file)
        {
            this.tag = tag;
            this.fileOffset = fileOffset;
            this.file = file;
            this.count = (int) ZenCoreUtil.readUnsigned32 (file, fileOffset, false);
            this.unitSize = (int) ZenCoreUtil.readUnsigned32 (file, fileOffset + 4, false);
            final int declaredDataOffset = (int) ZenCoreUtil.readUnsigned32 (file, fileOffset + 8, false);
            this.dataStart = fileOffset + (declaredDataOffset > 0 ? declaredDataOffset : 16);
        }


        /**
         * @return The number of records
         */
        public int getCount ()
        {
            return this.count;
        }


        /**
         * @return The size of one record in bytes
         */
        public int getUnitSize ()
        {
            return this.unitSize;
        }


        /**
         * @return The absolute file offset where the records start
         */
        public int getDataStart ()
        {
            return this.dataStart;
        }


        /**
         * @return The absolute file offset of the section start (its 16 byte header); directory
         *         offsets inside a <i>USDa</i> section are relative to this
         */
        public int getFileOffset ()
        {
            return this.fileOffset;
        }


        /**
         * @return The raw file bytes
         */
        public byte [] getFile ()
        {
            return this.file;
        }
    }


    private final boolean              svz;
    private final Map<String, Section> sections = new LinkedHashMap<> ();


    /**
     * Parses a container from the full file content.
     *
     * @param file The file content
     * @throws IOException The content is not a valid FANTOM container
     */
    public ZenCoreContainer (final byte [] file) throws IOException
    {
        final int tocStart;
        final int tocEnd;
        if (matches (file, 0, MAGIC_SVZ))
        {
            this.svz = true;
            tocStart = 0x10;
            // The TOC ends where the first section begins (sections are in file order)
            tocEnd = (int) ZenCoreUtil.readUnsigned32 (file, tocStart + 8, false);
        }
        else if (file.length > 7 && matches (file, 2, MAGIC_SVD))
        {
            this.svz = false;
            tocStart = 0x10;
            tocEnd = (ZenCoreUtil.readUnsigned16 (file, 0, false) + 1);
        }
        else
            throw new IOException ("Not a FANTOM SVD/SVZ container.");

        for (int off = tocStart; off + 16 <= tocEnd && off + 16 <= file.length; off += 16)
        {
            final String tag = ZenCoreUtil.readName (file, off, 4);
            if (tag.isEmpty () || file[off] == 0)
                break;
            final int sectionOffset = (int) ZenCoreUtil.readUnsigned32 (file, off + 8, false);
            if (sectionOffset <= 0 || sectionOffset + 16 > file.length)
                continue;
            this.sections.put (tag, new Section (tag, sectionOffset, file));
        }
    }


    /**
     * @return True if this is a SVZ, false if it is a SVD
     */
    public boolean isSvz ()
    {
        return this.svz;
    }


    /**
     * Get a section by its tag.
     *
     * @param tag The 4 character section tag
     * @return The section or null if not present
     */
    public Section getSection (final String tag)
    {
        return this.sections.get (tag);
    }


    /**
     * @return The tags of all sections in file order
     */
    public List<String> getSectionTags ()
    {
        return new ArrayList<> (this.sections.keySet ());
    }


    private static boolean matches (final byte [] data, final int offset, final byte [] magic)
    {
        if (offset + magic.length > data.length)
            return false;
        for (int i = 0; i < magic.length; i++)
            if (data[offset + i] != magic[i])
                return false;
        return true;
    }


    /**
     * Assemble a SVZ file from a list of sections. Each section body must already contain its
     * section header (built with {@link #buildBlock} or {@link #buildUsda}).
     *
     * @param header16 The 16 byte file header (magic + version + model tag), verified from a device
     *            export - see {@code svz_header.bin}
     * @param sectionTags The section tags in the order they should appear
     * @param sectionBodies The matching section bodies
     * @return The full SVZ file content
     * @throws IOException Could not assemble the file
     */
    public static byte [] buildSvz (final byte [] header16, final List<String> sectionTags, final List<byte []> sectionBodies) throws IOException
    {
        final int numSections = sectionTags.size ();
        final int headerSize = 0x10 + numSections * 16;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        out.write (header16, 0, 16);

        int sectionOffset = headerSize;
        for (int i = 0; i < numSections; i++)
        {
            writeTag (out, sectionTags.get (i));
            writeTag (out, PLATFORM_SVZ);
            writeU32LE (out, sectionOffset);
            writeU32LE (out, sectionBodies.get (i).length);
            sectionOffset += sectionBodies.get (i).length;
        }
        for (final byte [] body: sectionBodies)
            out.write (body);
        return out.toByteArray ();
    }


    /**
     * Build a fixed-record section body: the 16 byte header ({@code count, unitSize,
     * dataOffset=16+4*count, 0}) followed by a per-record CRC32 table (each entry is
     * {@code zlib.crc32(record)} little-endian) followed by the records. This integrity layout is
     * verified byte-exact against FANTOM device exports.
     *
     * @param records The fixed-size records (all the same length)
     * @return The section body
     * @throws IOException Could not build the section
     */
    public static byte [] buildBlock (final List<byte []> records) throws IOException
    {
        final int count = records.size ();
        final int unitSize = count == 0 ? 0 : records.get (0).length;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeU32LE (out, count);
        writeU32LE (out, unitSize);
        writeU32LE (out, 16L + 4 * count);
        writeU32LE (out, 0);
        for (final byte [] aRecord: records)
            writeU32LE (out, crc32 (aRecord));
        for (final byte [] aRecord: records)
            out.write (aRecord);
        return out.toByteArray ();
    }


    /**
     * Build the variable-size <i>USDa</i> section: the 16 byte header ({@code count, 0,
     * dataOffset=16+16*count, 0}) followed by a directory of {@code count} 16 byte entries
     * ({@code index, offset, size, crc32}, offsets relative to the section start) followed by the
     * concatenated sample chunks.
     *
     * @param chunks The per-sample <i>SMPd</i> chunks
     * @return The section body
     * @throws IOException Could not build the section
     */
    public static byte [] buildUsda (final List<byte []> chunks) throws IOException
    {
        final int count = chunks.size ();
        final int dataOffset = 16 + 16 * count;

        final ByteArrayOutputStream directory = new ByteArrayOutputStream ();
        final ByteArrayOutputStream body = new ByteArrayOutputStream ();
        int offset = dataOffset;
        for (int i = 0; i < count; i++)
        {
            final byte [] chunk = chunks.get (i);
            writeU32LE (directory, i);
            writeU32LE (directory, offset);
            writeU32LE (directory, chunk.length);
            writeU32LE (directory, crc32 (chunk));
            body.write (chunk);
            offset += chunk.length;
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeU32LE (out, count);
        writeU32LE (out, 0);
        writeU32LE (out, dataOffset);
        writeU32LE (out, 0);
        out.write (directory.toByteArray ());
        out.write (body.toByteArray ());
        return out.toByteArray ();
    }


    private static long crc32 (final byte [] data)
    {
        final CRC32 crc = new CRC32 ();
        crc.update (data);
        return crc.getValue ();
    }


    private static void writeTag (final OutputStream out, final String tag) throws IOException
    {
        for (int i = 0; i < 4; i++)
            out.write (i < tag.length () ? tag.charAt (i) : ' ');
    }


    private static void writeU32LE (final OutputStream out, final long value) throws IOException
    {
        out.write ((int) (value & 0xFF));
        out.write ((int) (value >> 8 & 0xFF));
        out.write ((int) (value >> 16 & 0xFF));
        out.write ((int) (value >> 24 & 0xFF));
    }
}
