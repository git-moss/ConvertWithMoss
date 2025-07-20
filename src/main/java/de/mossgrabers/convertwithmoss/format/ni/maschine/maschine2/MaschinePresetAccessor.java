// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;


/**
 * Can read and write one Maschine sound to a Preset Chunk.
 *
 * @author Jürgen Moßgraber
 */
public class MaschinePresetAccessor
{
    /**
     * Reads a Maschine 2+ preset chunk structure and the contained Maschine sounds.
     *
     * @param data The preset data
     * @throws IOException Could not parse the data
     */
    public void readMaschinePresetChunks (final byte [] data) throws IOException
    {
        // TODO

        Files.write (new File ("C:/Users/mos/Desktop", "MaschinePreset.bin").toPath (), data);
    }
}
