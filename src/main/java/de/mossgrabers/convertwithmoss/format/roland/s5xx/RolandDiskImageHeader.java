// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Parsed representation of the first 266 bytes of a Roland disk image.
 *
 * @author Jürgen Moßgraber
 */
public class RolandDiskImageHeader
{
    private final SamplerType samplerType;
    private final String      osVersionString;
    private final String      mediaType;


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read the header
     */
    public RolandDiskImageHeader (final InputStream input) throws IOException
    {
        input.skipNBytes (4);

        final String typeRaw = StreamUtils.readAscii (input, 4);
        this.samplerType = SamplerType.fromId (typeRaw);
        if (this.samplerType == SamplerType.UNKNOWN)
            throw new IOException ("Unrecognised sampler type ID: \"" + typeRaw + "\"");
        if (this.samplerType == SamplerType.S770)
            throw new IOException ("Sampler type S770 (SP-700 / S-750 / S-760 / S-770 / DJ-70) is not supported");

        input.skipNBytes (24);

        this.osVersionString = StreamUtils.readAscii (input, 30);

        input.skipNBytes (194);

        this.mediaType = StreamUtils.readAscii (input, 10);
    }


    /**
     * Resolved sampler model.
     * 
     * @return The type of the sampler
     */
    public SamplerType getSamplerType ()
    {
        return this.samplerType;
    }


    /**
     * Get the OS/version string from bytes 32–61, trimmed of trailing NUL/spaces.
     * 
     * @return The OS version
     */
    public String getOsVersionString ()
    {
        return cleanText (this.osVersionString);
    }


    /**
     * Get the Media-type flag from bytes 256–265, trimmed.
     * 
     * @return The media flag
     */
    public String getMediaTypeFlag ()
    {
        return cleanText (this.mediaType);
    }


    /**
     * Returns {@code true} when the media-type flag equals {@code "Instrument"}, indicating this is
     * a CD-ROM container (LAND type).
     * 
     * @return True if it is a CD-ROM
     */
    public boolean isCdRom ()
    {
        return "Instrument".equals (this.getMediaTypeFlag ());
    }


    private static String cleanText (final String s)
    {
        return s == null ? "(none)" : s.trim ().replaceAll ("\\s+", " ");
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return String.format ("DiskImageHeader{type=\"%s\", os=\"%s\", media=\"%s\", cdRom=%b}", this.samplerType, this.getOsVersionString (), this.getMediaTypeFlag (), Boolean.valueOf (this.isCdRom ()));
    }
}