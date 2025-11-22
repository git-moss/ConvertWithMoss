// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.chunkdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A chunk which contains the data of a preset.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkData extends AbstractChunkData
{
    private long    dictionaryType;
    private long    unknown = 0;
    private long    padding = 0;
    private long    magic   = 0x8565620D;
    private byte [] presetData;


    /**
     * Get the preset data.
     *
     * @return The preset data
     */
    public byte [] getPresetData ()
    {
        return this.presetData;
    }


    /**
     * Set the preset data.
     *
     * @param presetData The preset data
     */
    public void setPresetData (final byte [] presetData)
    {
        this.presetData = presetData;
    }


    /** {@inheritDoc} */
    @Override
    public void read (final InputStream in) throws IOException
    {
        this.readVersion (in);

        // Dictionary type / Authorization checksum?
        this.dictionaryType = StreamUtils.readUnsigned32 (in, false);

        // Number of items in the Dictionary
        final int numberOfItems = (int) StreamUtils.readUnsigned32 (in, false);
        if (numberOfItems != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI_FOUND_MORE_THAN_ONE_ENTRY"));

        final int sizeOfItem = (int) StreamUtils.readUnsigned32 (in, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        this.unknown = StreamUtils.readUnsigned32 (in, false);

        this.presetData = in.readNBytes (sizeOfItem);

        // Padding
        this.padding = StreamUtils.readUnsigned32 (in, false);

        // Seems to be always 0x8565620D, even for encrypted files
        this.magic = StreamUtils.readUnsigned32 (in, false);
    }


    /** {@inheritDoc} */
    @Override
    public void write (final OutputStream out) throws IOException
    {
        this.writeVersion (out);

        // Dictionary type / Authorization checksum?
        StreamUtils.writeUnsigned32 (out, this.dictionaryType, false);

        // Number of items in the Dictionary - must be 1
        StreamUtils.writeUnsigned32 (out, 1, false);

        StreamUtils.writeUnsigned32 (out, this.presetData.length, false);

        // Reference for multiple items in the dictionary or does this belong to the size?!
        StreamUtils.writeUnsigned32 (out, this.unknown, false);

        out.write (this.presetData);

        // Padding & checksum
        StreamUtils.writeUnsigned32 (out, this.padding, false);
        StreamUtils.writeUnsigned32 (out, this.magic, false);
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode (this.presetData);
        result = prime * result + Objects.hash (Long.valueOf (this.dictionaryType), Long.valueOf (this.magic), Long.valueOf (this.padding), Long.valueOf (this.unknown));
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final PresetChunkData other = (PresetChunkData) obj;
        return this.dictionaryType == other.dictionaryType && this.magic == other.magic && this.padding == other.padding && Arrays.equals (this.presetData, other.presetData) && this.unknown == other.unknown;
    }


    /** {@inheritDoc} */
    @Override
    public String dump (final int level)
    {
        return new StringBuilder ().append (StringUtils.formatHexStr (this.presetData)).append ("\n").toString ();
    }
}
