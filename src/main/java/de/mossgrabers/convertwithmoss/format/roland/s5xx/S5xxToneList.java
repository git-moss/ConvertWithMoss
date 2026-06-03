// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Access to an item in the tone list.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxToneList
{
    private final String name;
    private final int    orgSubTone;
    private final int    rootKey;


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public S5xxToneList (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readAscii (input, 8).trim ();

        // Unknown
        input.skipNBytes (1);

        this.orgSubTone = StreamUtils.readUnsigned8 (input);

        // Unknown
        input.skipNBytes (2);

        this.rootKey = StreamUtils.readUnsigned8 (input);

        // Unknown
        input.skipNBytes (3);
    }


    /**
     * Get the name of the tone.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the original sub-tone.
     * 
     * @return The original sub-tone, if any.
     */
    public int getOrgSubTone ()
    {
        return this.orgSubTone;
    }


    /**
     * Get the root key.
     * 
     * @return The root key
     */
    public int getRootKey ()
    {
        return this.rootKey;
    }
}
