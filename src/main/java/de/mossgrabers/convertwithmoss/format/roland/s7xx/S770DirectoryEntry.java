// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Roland S-770 directory entry (32 bytes). Also used as a container for the Diskette format.
 *
 * @author Jürgen Moßgraber
 */
public class S770DirectoryEntry
{
    private final String       name;
    private final int          firstNameByte;
    private final S770FileType fileType;
    private final int          fileAttributes;
    private final int          forwardLinkPtr;
    private final int          backwardLinkPtr;
    private final int          linkId;
    private final int          fatEntry;
    private final int          numClusters;


    /**
     * Constructor for a HD/CD-ROM directory entry.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read the directory entry
     */
    public S770DirectoryEntry (final InputStream input) throws IOException
    {
        // The name needs to be read as raw bytes since the un-mappable free/deleted marker 0xFE
        // must be preserved
        final byte [] nameBytes = input.readNBytes (16);
        if (nameBytes.length != 16)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ASCII_LENGTH_TOO_SHORT", "16", Integer.toString (nameBytes.length)));
        this.firstNameByte = nameBytes[0] & 0xFF;
        this.name = new String (nameBytes, StandardCharsets.US_ASCII);
        this.fileType = S770FileType.fromValue (StreamUtils.readUnsigned8 (input) & 0xFF);
        this.fileAttributes = StreamUtils.readUnsigned8 (input) & 0xFF;
        this.forwardLinkPtr = StreamUtils.readUnsigned16 (input, false);
        this.backwardLinkPtr = StreamUtils.readUnsigned16 (input, false);
        this.linkId = StreamUtils.readUnsigned16 (input, false);
        // Reserved
        StreamUtils.readUnsigned32 (input, false);
        this.fatEntry = StreamUtils.readUnsigned16 (input, false);
        this.numClusters = StreamUtils.readUnsigned16 (input, false);
    }


    /**
     * Constructor for storing a simple diskette directory entry.
     *
     * @param input The input to read the entry name from
     * @param fileType The type of entry
     * @param numClusters So far an unknown value...
     * @throws IOException Could not read the name
     */
    public S770DirectoryEntry (final InputStream input, final S770FileType fileType, final int numClusters) throws IOException
    {
        this.name = StreamUtils.readAscii (input, 16);
        this.firstNameByte = this.name.isEmpty () ? 0 : this.name.charAt (0);
        this.fileType = fileType;
        this.fileAttributes = 0;
        this.forwardLinkPtr = 0;
        this.backwardLinkPtr = 0;
        this.linkId = 0;
        this.fatEntry = 0;
        this.numClusters = numClusters;
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
     * Get the type of the file referenced by this entry.
     *
     * @return The type
     */
    public S770FileType getFileType ()
    {
        return this.fileType;
    }


    /**
     * Get the file attributes.
     *
     * @return The file attributes (specific meaning unknown)
     */
    public int getFileAttributes ()
    {
        return this.fileAttributes;
    }


    /**
     * Pointer to the next entry.
     *
     * @return The pointer
     */
    public int getForwardLinkPtr ()
    {
        return this.forwardLinkPtr;
    }


    /**
     * Pointer to the previous entry.
     *
     * @return The pointer
     */
    public int getBackwardLinkPtr ()
    {
        return this.backwardLinkPtr;
    }


    /**
     * Get the ID of the link.
     *
     * @return The ID
     */
    public int getLinkId ()
    {
        return this.linkId;
    }


    /**
     * Get the number of clusters used by the referenced file.
     *
     * @return The number of clusters
     */
    public int getNumClusters ()
    {
        return this.numClusters;
    }


    /**
     * Get the FAT index of the first data segment of the referenced file.
     *
     * @return The FAT index
     */
    public int getFatEntry ()
    {
        return this.fatEntry;
    }


    /**
     * Check if this entry references a file. A first name character of 0 marks this and all
     * following entries as unused, 0xFE marks a deleted entry.
     *
     * @return True if the entry is free or deleted
     */
    public boolean isFree ()
    {
        return this.firstNameByte == 0x00 || this.firstNameByte == 0xFE;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770DirectoryEntry [" + "name='" + this.name.trim () + "', " + "fileType=" + this.fileType + ", " + "fileAttributes=0x" + Integer.toHexString (this.fileAttributes) + ", " + "forwardLinkPtr=" + this.forwardLinkPtr + ", " + "backwardLinkPtr=" + this.backwardLinkPtr + ", " + "linkId=" + this.linkId + ", " + "fatEntry=" + this.fatEntry + ", " + "numClusters=" + this.numClusters + "]";
    }
}