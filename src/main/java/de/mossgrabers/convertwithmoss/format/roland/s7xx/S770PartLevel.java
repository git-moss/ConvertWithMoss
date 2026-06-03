// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 part level (1 byte, bit-endian: le). bits 0-6 → level (7 bits) bit 7 → is_on (1 bit)
 *
 * @author Jürgen Moßgraber
 */
public class S770PartLevel
{
    private final int     level;
    private final boolean isOn;


    public S770PartLevel (final InputStream in) throws IOException
    {
        final int b = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.level = b & 0x7F;
        this.isOn = b >> 7 != 0;
    }


    public int getLevel ()
    {
        return this.level;
    }


    public boolean isOn ()
    {
        return this.isOn;
    }


    @Override
    public String toString ()
    {
        return "S770PartLevel [level=" + this.level + ", isOn=" + this.isOn + "]";
    }
}