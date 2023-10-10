// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2.monolith;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.Magic;
import de.mossgrabers.tools.ui.Functions;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;


/**
 * A Kontakt 2 Monolith dictionary.
 *
 * @author Jürgen Moßgraber
 */
public class Directory
{
    private final List<DirectoryEntry> entries = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param fileAccess The file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @throws IOException An error occurred
     */
    public Directory (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        final int magic = (int) StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
        if (magic != Magic.KONTAKT2_NKR_HEADER_ID)
            throw new IOException (Functions.getMessage ("IDS_ERR_FILE_CORRUPTED"));

        // Skip header version and 8 more unknown bytes
        StreamUtils.skipNBytes (fileAccess, 10);

        // Read the number if the items in the dictionary
        final int numItems = (int) StreamUtils.readUnsigned32 (fileAccess, isBigEndian);

        // Skip padding
        StreamUtils.skipNBytes (fileAccess, 4);

        // Read all dictionary entries
        for (int i = 0; i < numItems; i++)
            this.entries.add (new DirectoryEntry (fileAccess, isBigEndian));
    }


    /**
     * Get the entries of this directory.
     *
     * @return The entries
     */
    public List<DirectoryEntry> getEntries ()
    {
        return this.entries;
    }
}
