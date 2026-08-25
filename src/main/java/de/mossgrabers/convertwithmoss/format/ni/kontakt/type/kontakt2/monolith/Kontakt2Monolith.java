// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.monolith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.ncw.NcwFileSampleData;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.Magic;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.ui.Functions;


/**
 * Can handle NKI files in Kontakt 2 monolith format.
 *
 * @author Jürgen Moßgraber
 */
public class Kontakt2Monolith
{
    private final Map<Long, Directory> directories = new TreeMap<> ();
    private final List<byte []>        sampleData  = new ArrayList<> ();


    /**
     * Constructor. Reads the monolith.
     *
     * @param fileAccess The file access
     * @param isBigEndian Use little or big endian
     * @throws IOException Could not read the monolith
     */
    public Kontakt2Monolith (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        this.readMonolith (fileAccess, isBigEndian);
    }


    /**
     * Read and check the samples from the monolith.
     *
     * @return The sample data objects
     * @throws IOException Could not read the samples
     */
    public Map<String, ISampleData> mapSamples () throws IOException
    {
        final Map<String, ISampleData> multiSamples = new HashMap<> ();

        final List<DirectoryEntry> sampleItems = this.findItems (DirectoryEntryType.SAMPLE);
        for (int i = 0; i < sampleItems.size (); i++)
        {
            final byte [] data = this.sampleData.get (i);
            try (final ByteArrayInputStream in = new ByteArrayInputStream (data))
            {
                final DirectoryEntry directoryEntry = sampleItems.get (i);
                final String filename = directoryEntry.asWideString ();
                if (filename.toLowerCase ().endsWith (".ncw"))
                    multiSamples.put (filename, new NcwFileSampleData (in));
                else
                    multiSamples.put (filename, new WavFileSampleData (in));
            }
        }

        return multiSamples;
    }


    private void readMonolith (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        while (fileAccess.getFilePointer () < fileAccess.length ())
        {
            final long position = fileAccess.getFilePointer ();
            final int magicID = (int) StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
            fileAccess.seek (position);

            switch (magicID)
            {
                case Magic.KONTAKT2_NKR_HEADER_ID:
                    final Directory directory = new Directory (fileAccess, isBigEndian);
                    this.directories.put (Long.valueOf (position), directory);
                    break;

                case Magic.KONTAKT2_NKR_WALLPAPER_ID:
                    // No need for the wallpaper, skip header and image data
                    fileAccess.skipBytes (14);
                    final long length = StreamUtils.readUnsigned64 (fileAccess, isBigEndian);
                    fileAccess.skipBytes ((int) length);
                    break;

                case Magic.KONTAKT2_NKR_NKI_ID:
                    // Skip header and move to the beginning of the ZLIB block
                    fileAccess.skipBytes (27 + 170);
                    return;

                case Magic.KONTAKT2_NKR_SAMPLE_ID:
                    fileAccess.skipBytes (4);
                    // Version
                    StreamUtils.readUnsigned16 (fileAccess, isBigEndian);
                    fileAccess.skipBytes (13);
                    final long sampleLength = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
                    fileAccess.skipBytes (8);
                    final byte [] data = StreamUtils.readNBytes (fileAccess, (int) sampleLength);
                    this.sampleData.add (data);
                    break;

                case Magic.KONTAKT2_NKR_SAMPLE_RAW_ID:
                    fileAccess.skipBytes (4);
                    // Version
                    StreamUtils.readUnsigned16 (fileAccess, isBigEndian);
                    fileAccess.skipBytes (18);

                    // Brute force method to skip this block, since we do not know where the length
                    // of it is stored...
                    long nextSamplePos = findNextSampleBlock (fileAccess, isBigEndian);
                    if (nextSamplePos < 0)
                    {
                        final List<DirectoryEntry> nkiItems = this.findItems (DirectoryEntryType.NKI);
                        if (nkiItems.isEmpty ())
                            throw new IOException (Functions.getMessage ("IDS_NKI_NO_NKI_FILES"));
                        final DirectoryEntry directoryEntry = nkiItems.get (0);
                        nextSamplePos = directoryEntry.getPointer ();
                    }
                    fileAccess.seek (nextSamplePos);
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_MAGIC_ID", String.format ("%X", Long.valueOf (magicID))));
            }
        }
    }


    private final List<DirectoryEntry> findItems (final DirectoryEntryType type)
    {
        final List<DirectoryEntry> results = new ArrayList<> ();
        for (final Entry<Long, Directory> e: this.directories.entrySet ())
        {
            final Directory directory = e.getValue ();
            for (final DirectoryEntry item: directory.getEntries ())
                if (item.getReferenceType () == type)
                    results.add (item);
        }
        return results;
    }


    private static long findNextSampleBlock (final RandomAccessFile fileAccess, final boolean isBigEndian) throws IOException
    {
        for (long i = fileAccess.getFilePointer (); i < fileAccess.length () - 4; i++)
        {
            fileAccess.seek (i);
            if (StreamUtils.readUnsigned32 (fileAccess, isBigEndian) == Magic.KONTAKT2_NKR_SAMPLE_ID && StreamUtils.readUnsigned16 (fileAccess, isBigEndian) == 0x110)
                return i;
        }
        return -1;
    }
}
