// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.monolith;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * An item in a Kontakt 2 Monolith dictionary.
 *
 * @author Jürgen Moßgraber
 */
public class DictionaryItem
{
    private final RandomAccessFile            fileAccess;
    private final boolean                     isBigEndian;
    private final int                         length;
    private final int                         pointer;
    private final DictionaryItemReferenceType referenceType;
    private final byte []                     content;
    private Dictionary                        dictionary = null;


    /**
     * Constructor.
     *
     * @param fileAccess The file to read from
     * @param isBigEndian True if bytes are stored big-endian
     * @throws IOException An error occurred
     */
    public DictionaryItem (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        this.fileAccess = fileAccess;
        this.isBigEndian = isBigEndian;

        this.length = StreamUtils.readWord (fileAccess, isBigEndian);
        this.pointer = StreamUtils.readDoubleWord (fileAccess, isBigEndian);

        final int type = StreamUtils.readWord (fileAccess, isBigEndian);
        if (type < 0 || type > 4)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_DICT_ITEM_REF_TYPE", Integer.toString (type)));
        this.referenceType = DictionaryItemReferenceType.values ()[type];

        this.content = StreamUtils.readNBytes (fileAccess, this.length - 8);
    }


    /**
     * If this item references a sub-directory. The sub-directory is read.
     *
     * @throws IOException Error during reading
     */
    public void readReferencedDictionary () throws IOException
    {
        if (this.referenceType == DictionaryItemReferenceType.DICTIONARY)
        {
            this.fileAccess.seek (this.pointer);
            this.dictionary = new Dictionary (this.fileAccess, this.isBigEndian);
        }
    }


    /**
     * Interpret the content of the item as a UTF-16 string.
     *
     * @return The text
     */
    public String asWideString ()
    {
        final StringBuilder sb = new StringBuilder ();

        for (int i = 0; i < this.content.length; i += 2)
        {
            if (this.content[i] == 0)
                break;
            if (this.content[i + 1] == 0)
                sb.append ((char) this.content[i]);
            else
                sb.append ((char) ((this.content[i] & 0xFF) << 8 | this.content[1] & 0xFF));
        }

        return sb.toString ();
    }


    /**
     * Get the pointer.
     *
     * @return The pointer
     */
    public int getPointer ()
    {
        return this.pointer;
    }


    /**
     * Get the type of the reference.
     *
     * @return The reference type
     */
    public DictionaryItemReferenceType getReferenceType ()
    {
        return this.referenceType;
    }


    /**
     * @return the dictionary
     */
    public Dictionary getDictionary ()
    {
        return this.dictionary;
    }
}
