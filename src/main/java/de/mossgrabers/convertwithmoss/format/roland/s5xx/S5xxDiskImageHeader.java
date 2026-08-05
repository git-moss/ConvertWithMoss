// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Parsed representation of the first 266 bytes of a Roland disk image.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxDiskImageHeader
{
    private final S5xxSamplerType           samplerType;
    private final String                    osVersionString;
    private final List<S5xxCDSectionHeader> sectionHeaders = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the header
     */
    public S5xxDiskImageHeader (final InputStream input) throws IOException
    {
        input.skipNBytes (4);

        final String typeRaw = StreamUtils.readAscii (input, 4);
        this.samplerType = S5xxSamplerType.fromId (typeRaw);
        if (this.samplerType == S5xxSamplerType.UNKNOWN)
            throw new IOException ("Unrecognised sampler type ID: \"" + typeRaw + "\"");
        if (this.samplerType == S5xxSamplerType.S770)
            throw new IOException ("Sampler type S770 (SP-700 / S-750 / S-760 / S-770 / DJ-70) is not supported");

        input.skipNBytes (24);

        this.osVersionString = StringUtils.consolidateWhitespace (StreamUtils.readAscii (input, 30), "none");

        input.skipNBytes (194);

        if (this.samplerType == S5xxSamplerType.LAND)
        {
            for (int i = 0; i < 3; i++)
                this.sectionHeaders.add (new S5xxCDSectionHeader (input));
        }
    }


    /**
     * Looks up the section header with the given ID.
     * 
     * @param headerID SECTION_INSTRUMENT_GROUP, SECTION_SOUND_DIRECTORY or SECTION_INSTRUMENT_MAP
     * @return The section header if present
     */
    public Optional<S5xxCDSectionHeader> getSectionHeader (final String headerID)
    {
        for (final S5xxCDSectionHeader sectionheader: this.sectionHeaders)
            if (headerID.equals (sectionheader.getName ()))
                return Optional.of (sectionheader);
        return Optional.empty ();
    }


    /**
     * Resolved sampler model.
     *
     * @return The type of the sampler
     */
    public S5xxSamplerType getSamplerType ()
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
        return this.osVersionString;
    }
}