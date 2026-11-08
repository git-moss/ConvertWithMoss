// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.monolith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
    private static final String        IR_SAMPLES_DIRECTORY = "IR Samples";

    private final Map<Long, Directory> directories          = new TreeMap<> ();
    private final List<byte []>        sampleData           = new ArrayList<> ();


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

        // The impulse response samples are stored in their own directory and their data is not
        // read, therefore they need to be excluded to keep the order of names and data in sync
        final List<DirectoryEntry> sampleItems = this.findItems (DirectoryEntryType.SAMPLE, this.findIRDirectories ());
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
                    // The size of the waveform overview block which follows the sample data. It is
                    // 0 for impulse response samples, which have no overview
                    final long overviewLength = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
                    // Padding
                    fileAccess.skipBytes (4);
                    // The impulse response samples are not needed
                    if (overviewLength == 0)
                        fileAccess.skipBytes ((int) sampleLength);
                    else
                        this.sampleData.add (StreamUtils.readNBytes (fileAccess, (int) sampleLength));
                    // Skip the waveform overview
                    fileAccess.skipBytes ((int) overviewLength);
                    break;

                case Magic.KONTAKT2_NKR_SAMPLE_RAW_ID:
                    // A waveform overview which is not announced by the preceding sample block (it
                    // is normally skipped there). Skip magic, version and 2 unknown bytes
                    fileAccess.skipBytes (8);
                    final long bitsPerValue = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
                    final long numChannels = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
                    final long numPoints = StreamUtils.readUnsigned32 (fileAccess, isBigEndian);
                    // Skip the sample rate, frames per point, file type and 4 unknown bytes
                    fileAccess.skipBytes (16);
                    // Each point contains 2 values (minimum and maximum) per channel
                    fileAccess.skipBytes ((int) (numPoints * numChannels * 2 * (bitsPerValue / 8)));
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_MAGIC_ID", String.format ("%X", Long.valueOf (magicID))));
            }
        }
    }


    /**
     * Find all entries of a certain type.
     *
     * @param type The type of the entries
     * @param excludedDirectories The positions of directories to ignore
     * @return The found entries in the order of the directories in the file
     */
    private final List<DirectoryEntry> findItems (final DirectoryEntryType type, final Set<Long> excludedDirectories)
    {
        final List<DirectoryEntry> results = new ArrayList<> ();
        for (final Entry<Long, Directory> e: this.directories.entrySet ())
        {
            if (excludedDirectories.contains (e.getKey ()))
                continue;
            final Directory directory = e.getValue ();
            for (final DirectoryEntry item: directory.getEntries ())
                if (item.getReferenceType () == type)
                    results.add (item);
        }
        return results;
    }


    /**
     * Find the directories which contain the impulse response samples. They are referenced by a
     * directory entry with the name 'IR Samples'.
     *
     * @return The positions of the directories
     */
    private Set<Long> findIRDirectories ()
    {
        final Set<Long> irDirectories = new HashSet<> ();
        for (final Directory directory: this.directories.values ())
            for (final DirectoryEntry item: directory.getEntries ())
                if (item.getReferenceType () == DirectoryEntryType.DIRECTORY && IR_SAMPLES_DIRECTORY.equals (item.asWideString ()))
                    irDirectories.add (Long.valueOf (item.getPointer ()));
        return irDirectories;
    }
}
