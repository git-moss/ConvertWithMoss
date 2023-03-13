// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.monolith;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A Kontakt 2 Monolith dictionary.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Dictionary
{
    private static final byte []       DICTIONARY_HEADER_ID =
    {
        (byte) 0x54,
        (byte) 0xAC,
        (byte) 0x70,
        (byte) 0x5E
    };

    private final List<DictionaryItem> items                = new ArrayList<> ();


    /**
     * Constructor.
     *
     * @param fileAccess The file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @throws IOException An error occurred
     */
    public Dictionary (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        if (isBigEndian)
            throw new IOException (Functions.getMessage ("IDS_NKI_ERR_MONOLITH_BIG_ENDIAN_NOT_SUPPORTED"));

        final byte [] dictionaryHeader = StreamUtils.readNBytes (fileAccess, 22);
        if (Arrays.compare (dictionaryHeader, 0, 4, DICTIONARY_HEADER_ID, 0, 4) != 0)
            throw new IOException (Functions.getMessage ("IDS_ERR_FILE_CORRUPTED"));

        // Read all dictionary items
        final int numSubBlocks = dictionaryHeader[14];
        for (int i = 0; i < numSubBlocks; i++)
            this.items.add (new DictionaryItem (fileAccess, isBigEndian));

        // Follow the references (needs to be here since it moves the file pointer)
        for (final DictionaryItem item: this.items)
            item.readReferencedDictionary ();
    }


    /**
     * Get the items of this directory.
     *
     * @return The items
     */
    public List<DictionaryItem> getItems ()
    {
        return this.items;
    }
}
