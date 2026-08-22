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
    private final String                 name;
    private final int                    keyboardTilt;
    private final int                    keygroupCrossfadeEnable;
    private final List<AkaiS900Keygroup> keygroups = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiS900Program (final InputStream input) throws IOException
    {
        this.name = StreamUtils.readAscii (input, 10).trim ();

        // Undefined
        input.skipNBytes (4);
        // Undefined
        input.skipNBytes (2);

        this.keyboardTilt = StreamUtils.readSigned8 (input);

        // Undefined
        input.skipNBytes (1);

        // Address of first key-group
        StreamUtils.readUnsigned16 (input, false);

        // Undefined
        input.skipNBytes (1);

        this.keygroupCrossfadeEnable = StreamUtils.readUnsigned8 (input);

        // Reserved, set to 0xFF
        input.skipNBytes (1);

        final int numberOfKeygroups = StreamUtils.readUnsigned8 (input);

        // Nothing meaningful in there (1 increasing number and FF)
        input.skipNBytes (14);

        for (int i = 0; i < numberOfKeygroups; i++)
            this.keygroups.add (new AkaiS900Keygroup (input));
    }


    /**
     * Get the name of the program.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the keyboard tilt (loudness). Key versus loudness. A value of +00 will have no effect on
     * the level of the sample across the keyboard range whilst a value of -50 will reduce the level
     * in the upper octaves and increase the level in the lower octaves. Conversely, a value of +50
     * will increase the level of the upper octaves and decrease the level in the lower octaves.
     *
     * @return The keyboard tilt in the range of [-50..50]
     */
    public int getKeyboardTilt ()
    {
        return this.keyboardTilt;
    }


    /**
     * Check if key-groups should be cross-faded in the overlapping key-range. Key-group
     * (positional) cross-fade enable: Samples may be assigned so that the high range of one sample
     * overlaps the low range of another sample. With positional cross-fade on, the lower sample
     * will fade out as the higher sample fades in over the range of the overlap.
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
