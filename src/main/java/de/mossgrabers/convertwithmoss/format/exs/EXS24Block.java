// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Structure for a EXS24 block.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Block
{
    /** An instrument block. */
    public static final int          TYPE_INSTRUMENT           = 0x00;
    /** A zone block. */
    public static final int          TYPE_ZONE                 = 0x01;
    /** A group block. */
    public static final int          TYPE_GROUP                = 0x02;
    /** A sample block. */
    public static final int          TYPE_SAMPLE               = 0x03;
    /** A parameters block. */
    public static final int          TYPE_PARAMS               = 0x04;
    /** An unknown block. */
    public static final int          TYPE_UNKNOWN              = 0x08;
    /** Another unknown block. */
    public static final int          TYPE_BPLIST               = 0x0B;

    private static final String      BIG_ENDIAN_MAGIC          = "SOBT";
    private static final String      LITTLE_ENDIAN_MAGIC       = "TBOS";
    private static final Set<String> BIG_ENDIAN_MAGIC_BYTES    = new HashSet<> (2);
    private static final Set<String> LITTLE_ENDIAN_MAGIC_BYTES = new HashSet<> (2);
    static
    {
        Collections.addAll (BIG_ENDIAN_MAGIC_BYTES, "SOBT", "SOBJ");
        Collections.addAll (LITTLE_ENDIAN_MAGIC_BYTES, "TBOS", "JBOS");
    }

    boolean isBigEndian = true;
    int     type;
    int     index       = 0;
    String  name;
    byte [] content;


    /**
     * Constructor.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    public EXS24Block (final InputStream in) throws IOException
    {
        this.read (in);
    }


    /**
     * Constructor.
     *
     * @param type The type of the block
     * @param content The content of the block
     * @param isBigEndian True if it is big-endian
     * @throws IOException Could not read the data
     */
    public EXS24Block (final int type, final byte [] content, final boolean isBigEndian) throws IOException
    {
        this.type = type;
        this.content = content;
        this.isBigEndian = isBigEndian;
    }


    /**
     * Read the block.
     *
     * @param in The input stream to read from
     * @throws IOException Could not read the data
     */
    protected void read (final InputStream in) throws IOException
    {
        this.isBigEndian = in.read () == 0;

        final int version1 = in.read ();
        final int version2 = in.read ();
        if (version1 != 1 && version2 != 0)
            throw new IOException (Functions.getMessage ("IDS_EXS_UNKNOWN_VERSION", Integer.toString (version1), Integer.toString (version2)));

        // There are variants which have a 0x40 added...
        this.type = in.read () & 0x0F;
        final int size = (int) StreamUtils.readUnsigned32 (in, this.isBigEndian);
        this.index = (int) StreamUtils.readUnsigned32 (in, this.isBigEndian);

        // Flags -> Found: 03 (on a group), 64 (instrument), 2, 3, 2147483650 (zone), 2 (sample)
        StreamUtils.readUnsigned32 (in, this.isBigEndian);

        final String magic = StreamUtils.readASCII (in, 4);
        if (!(this.isBigEndian ? BIG_ENDIAN_MAGIC_BYTES : LITTLE_ENDIAN_MAGIC_BYTES).contains (magic))
            throw new IOException (Functions.getMessage ("IDS_EXS_UNKNOWN_MAGIC", magic));

        this.name = StringUtils.removeCharactersAfterZero (StreamUtils.readASCII (in, 64));

        this.content = in.readNBytes (size);
    }


    /**
     * Write the block.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final OutputStream out) throws IOException
    {
        out.write (this.isBigEndian ? 0 : 1);
        out.write (1);
        out.write (0);
        out.write (this.type);

        StreamUtils.writeUnsigned32 (out, this.content.length, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.index, this.isBigEndian);
        StreamUtils.writeUnsigned32 (out, 0, this.isBigEndian);
        StreamUtils.writeASCII (out, this.isBigEndian ? BIG_ENDIAN_MAGIC : LITTLE_ENDIAN_MAGIC, 4);
        StreamUtils.writeASCII (out, StringUtils.fixASCII (this.name), 64);
        out.write (this.content);
    }
}
