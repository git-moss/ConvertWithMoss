// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Structure for a EXS24 sample.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Sample extends EXS24Object
{
    int     waveDataStart;
    int     length;
    int     sampleRate;
    int     bitDepth;
    int     channels;
    int     channels2;
    String  type;
    int     size;
    boolean isCompressed = false;
    String  filePath;
    String  fileName;


    /**
     * Constructor.
     */
    public EXS24Sample ()
    {
        super (EXS24Block.TYPE_SAMPLE);
    }


    /**
     * Constructor.
     *
     * @param block The block to read
     * @throws IOException Could not read the block
     */
    public EXS24Sample (final EXS24Block block) throws IOException
    {
        this ();
        this.read (block);
    }


    /** {@inheritDoc} */
    @Override
    protected void read (final InputStream in, final boolean isBigEndian) throws IOException
    {
        this.waveDataStart = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.length = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.sampleRate = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.bitDepth = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        this.channels = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.channels2 = (int) StreamUtils.readUnsigned32 (in, isBigEndian);

        in.skipNBytes (4);

        this.type = StreamUtils.readASCII (in, 4);
        this.size = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        this.isCompressed = StreamUtils.readUnsigned32 (in, isBigEndian) > 0;

        in.skipNBytes (40);

        this.filePath = StringUtils.removeCharactersAfterZero (StreamUtils.readASCII (in, 256));

        // If not present the name from the header is used!
        this.fileName = in.available () > 0 ? StringUtils.removeCharactersAfterZero (StreamUtils.readASCII (in, 256)) : this.name;
    }


    /** {@inheritDoc} */
    @Override
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.waveDataStart, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.length, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.sampleRate, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.bitDepth, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.channels, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.channels2, isBigEndian);
        StreamUtils.padBytes (out, 4);
        StreamUtils.writeASCII (out, this.type, 4);
        StreamUtils.writeUnsigned32 (out, this.size, isBigEndian);
        StreamUtils.writeUnsigned32 (out, this.isCompressed ? 1 : 0, isBigEndian);
        StreamUtils.padBytes (out, 40);
        StreamUtils.writeASCII (out, this.filePath, 256);
        StreamUtils.writeASCII (out, this.fileName, 256);
    }
}
