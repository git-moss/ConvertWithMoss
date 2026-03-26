// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A S900/S950 program.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Program
{
    /** The name of the program. */
    private String                       name;

    /**
     * Key-group (positional) cross-fade enable: Samples may be assigned so that the high range of
     * one sample overlaps the low range of another sample. With positional cross-fade on, the lower
     * sample will fade out as the higher sample fades in over the range of the overlap.
     */
    private int                          keygroupCrossfadeEnable;

    private final List<AkaiS900Keygroup> keygroups = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiS900Program (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readASCII (input, 10).trim ();

        // Padding
        input.skip (6);

        // Always 00 00
        input.skip (2);

        // Address of first key-group
        StreamUtils.readUnsigned16 (input, false);

        // 00
        input.skip (1);

        this.keygroupCrossfadeEnable = input.read ();

        // FF
        input.skip (1);

        final int numberOfKeygroups = input.read ();

        // Nothing meaningful in there (1 increasing number and FF)
        input.skip (14);

        for (int i = 0; i < numberOfKeygroups; i++)
            this.keygroups.add (new AkaiS900Keygroup (input));
    }


    /**
     * Get the name of the entry.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Check if key-groups should be cross-faded in the overlapping key-range.
     * 
     * @return True if cross-fade should be applied
     */
    public boolean isKeygroupCrossfadeEnable ()
    {
        return this.keygroupCrossfadeEnable > 0;
    }


    /**
     * Get all key-groups.
     * 
     * @return The key-groups
     */
    public List<AkaiS900Keygroup> getKeygroups ()
    {
        return this.keygroups;
    }
}
