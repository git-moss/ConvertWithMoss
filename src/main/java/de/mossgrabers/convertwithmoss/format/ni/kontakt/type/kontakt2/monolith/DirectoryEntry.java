// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.monolith;

import java.io.IOException;
import java.io.RandomAccessFile;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An entry in a Kontakt 2 Monolith directory.
 *
 * @author Jürgen Moßgraber
 */
public class DirectoryEntry
{
    private final boolean            isBigEndian;
    private final int                length;
    private final long               pointer;
    private final DirectoryEntryType referenceType;
    private final byte []            content;


    /**
     * Constructor.
     *
     * @param fileAccess The file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @throws IOException An error occurred
     */
    public DirectoryEntry (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        this.isBigEndian = isBigEndian;

        this.length = StreamUtils.readUnsigned16 (fileAccess, isBigEndian);
        this.pointer = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);

        final int type = StreamUtils.readSigned16 (fileAccess, isBigEndian);
        if (type < 0 || type > 4)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_DICT_ITEM_REF_TYPE", Integer.toString (type)));
        this.referenceType = DirectoryEntryType.values ()[type];

        this.content = StreamUtils.readNBytes (fileAccess, this.length - 8);
    }


    /**
     * Interpret the content of the item as a UTF-16 string.
     *
     * @return The text
     */
    public String asWideString ()
    {
        return StreamUtils.readUTF16 (this.content, this.isBigEndian);
    }


    /**
     * Get the pointer.
     *
     * @return The pointer
     */
    public long getPointer ()
    {
        return this.pointer;
    }


    /**
     * Get the type of the reference.
     *
     * @return The reference type
     */
    public DirectoryEntryType getReferenceType ()
    {
        return this.referenceType;
    }
}
