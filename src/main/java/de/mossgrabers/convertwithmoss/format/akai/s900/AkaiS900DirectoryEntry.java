// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.IOException;
import java.io.RandomAccessFile;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An entry in a S900/S950 directory. Always 24 bytes.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900DirectoryEntry
{
    /** The name of the entry. */
    private String name;
    /** The type of the entry, 'P', 'S', .... */
    private char   type;
    /** The length of the item to it refers. */
    private int    length;
    /** The start of the block on the 'disk'. */
    private int    startBlock;
    /** if S900 compressed file: number of un-compressed floppy blocks else: zero. */
    private int    compression;


    /**
     * Constructor.
     * 
     * @param randomAccessFile The file to read from
     * @throws IOException Could not read
     */
    public AkaiS900DirectoryEntry (final RandomAccessFile randomAccessFile) throws IOException
    {
        if (randomAccessFile.length () - randomAccessFile.getFilePointer () < 24)
            throw new IOException (Functions.getMessage ("IDS_S900_UNSOUND_FILE", "File too short."));

        this.name = StreamUtils.readASCII (randomAccessFile, 10).trim ();

        // Padding
        randomAccessFile.skipBytes (6);

        this.type = (char) randomAccessFile.read ();
        this.length = StreamUtils.readUnsigned24 (randomAccessFile, false);
        this.startBlock = StreamUtils.readUnsigned16 (randomAccessFile, false);
        this.compression = StreamUtils.readUnsigned16 (randomAccessFile, false);
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
     * Get the directory entry type.
     * 
     * @return The type, 'P' = Program, 'S' = Sample
     */
    public char getType ()
    {
        return this.type;
    }


    /**
     * The length of the item to it refers.
     * 
     * @return The length
     */
    public int getLength ()
    {
        return this.length;
    }


    /**
     * The start of the block on the 'disk'.
     * 
     * @return The start
     */
    public int getStartBlock ()
    {
        return this.startBlock;
    }


    /**
     * Get the compression state of the samples.
     * 
     * @return If S900 compressed file: number of un-compressed floppy blocks else: zero.
     */
    public int getCompression ()
    {
        return this.compression;
    }
}
