// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A Kurzweil keymap object (type 37). Maps sample recordings across the key range: entry i responds
 * to the MIDI note 12 + (basePitch + i * centsPerEntry) / 100. The 8 dynamic levels (ppp..fff,
 * mapped linearly onto the MIDI velocity range) each reference one of the entry tables; several
 * levels may share a table.
 *
 * The method bits define which fields are stored per entry: 0x10 tuning as 16-bit (else 0x08 tuning
 * as 8-bit), 0x04 volume adjust, 0x02 sample ID, 0x01 sub-sample number. Without the per-entry
 * sample ID bit all entries use the sample ID of the keymap ('compacted' keymap).
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilKeymap
{
    /** The number of dynamic levels. */
    public static final int                    NUM_LEVELS        = 8;

    /** The names of the 8 dynamic levels. */
    private static final String []             LEVEL_NAMES       =
    {
        "ppp",
        "pp",
        "p",
        "mp",
        "mf",
        "f",
        "ff",
        "fff"
    };

    private static final int                   METHOD_TUNING_16  = 0x10;
    private static final int                   METHOD_TUNING_8   = 0x08;
    private static final int                   METHOD_VOLUME     = 0x04;
    private static final int                   METHOD_SAMPLE_ID  = 0x02;
    private static final int                   METHOD_SUB_SAMPLE = 0x01;

    /** 16-bit tuning, sample ID and sub-sample number per entry (5 bytes). */
    public static final int                    METHOD_DEFAULT    = METHOD_TUNING_16 | METHOD_SAMPLE_ID | METHOD_SUB_SAMPLE;

    /** The lowest MIDI note of a keymap with base pitch 0. */
    public static final int                    BASE_NOTE         = 12;

    /** The number of entries of a standard keymap (1 per semitone). */
    public static final int                    NUM_ENTRIES       = 128;

    private final int                          id;
    private final String                       name;
    private int                                sampleId;
    private int                                method            = METHOD_DEFAULT;
    private int                                basePitch;
    private int                                centsPerEntry     = 100;
    private int                                numEntries        = NUM_ENTRIES;

    private final int []                       levelTableIndices = new int [NUM_LEVELS];
    private final List<KurzweilKeymapEntry []> entryTables       = new ArrayList<> ();


    /**
     * Constructor for a new empty keymap.
     *
     * @param id The object ID
     * @param name The name of the keymap, maximum 16 characters
     */
    public KurzweilKeymap (final int id, final String name)
    {
        this.id = id;
        this.name = name;
    }


    /**
     * Constructor. Reads the object data (the part after the object name) from the stream.
     *
     * @param id The object ID
     * @param name The name of the keymap
     * @param in The input stream to read from
     * @throws IOException Could not read the object
     */
    public KurzweilKeymap (final int id, final String name, final InputStream in) throws IOException
    {
        this.id = id;
        this.name = name;

        this.sampleId = StreamUtils.readSigned16 (in, true);
        this.method = StreamUtils.readSigned16 (in, true);
        this.basePitch = StreamUtils.readSigned16 (in, true);
        this.centsPerEntry = StreamUtils.readSigned16 (in, true);
        this.numEntries = StreamUtils.readSigned16 (in, true) + 1;
        // The entry size - implied by the method bits
        StreamUtils.readSigned16 (in, true);

        // The level offsets point from each level field to the entry table of that dynamic
        // level. Normalizing them by the distance to the end of the level fields leaves for each
        // level the offset of its table into the entry table area
        final int [] levelOffsets = new int [NUM_LEVELS];
        int numTables = 1;
        for (int i = 0; i < NUM_LEVELS; i++)
            levelOffsets[i] = StreamUtils.readSigned16 (in, true) - (NUM_LEVELS - i) * 2;
        for (int i = 1; i < NUM_LEVELS; i++)
            if (levelOffsets[i] != levelOffsets[i - 1])
                numTables++;

        final int entrySize = getEntrySize (this.method);
        if (entrySize == 0 || this.numEntries <= 0)
            throw new IOException ("Invalid keymap method or entry count.");

        final int tableSize = this.numEntries * entrySize;
        if (numTables * tableSize > in.available ())
            throw new IOException ("Broken keymap object in Kurzweil file.");
        for (int table = 0; table < numTables; table++)
        {
            final KurzweilKeymapEntry [] entries = new KurzweilKeymapEntry [this.numEntries];
            for (int i = 0; i < this.numEntries; i++)
                entries[i] = this.readEntry (in);
            this.entryTables.add (entries);

            for (int level = 0; level < NUM_LEVELS; level++)
            {
                if (levelOffsets[level] == 0)
                    this.levelTableIndices[level] = table;
                levelOffsets[level] -= tableSize;
            }
        }
    }


    private KurzweilKeymapEntry readEntry (final InputStream in) throws IOException
    {
        final KurzweilKeymapEntry entry = new KurzweilKeymapEntry ();
        if ((this.method & METHOD_TUNING_16) > 0)
            entry.setTuning (StreamUtils.readSigned16 (in, true));
        else if ((this.method & METHOD_TUNING_8) > 0)
            entry.setTuning ((byte) in.read ());
        if ((this.method & METHOD_VOLUME) > 0)
            entry.setVolumeAdjust ((byte) in.read ());
        if ((this.method & METHOD_SAMPLE_ID) > 0)
            entry.setSampleID (StreamUtils.readSigned16 (in, true));
        else
            entry.setSampleID (this.sampleId);
        if ((this.method & METHOD_SUB_SAMPLE) > 0)
            entry.setSubSampleNumber (in.read ());
        return entry;
    }


    /**
     * Write the object data (the part after the object name) to the stream.
     *
     * @return The object data
     * @throws IOException Could not write the object
     */
    public byte [] createObjectData () throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        final int entrySize = getEntrySize (this.method);
        StreamUtils.writeSigned16 (out, this.sampleId, true);
        StreamUtils.writeSigned16 (out, this.method, true);
        StreamUtils.writeSigned16 (out, this.basePitch, true);
        StreamUtils.writeSigned16 (out, this.centsPerEntry, true);
        StreamUtils.writeSigned16 (out, this.numEntries - 1, true);
        StreamUtils.writeSigned16 (out, entrySize, true);

        final int tableSize = this.numEntries * entrySize;
        for (int level = 0; level < NUM_LEVELS; level++)
            StreamUtils.writeSigned16 (out, (NUM_LEVELS - level) * 2 + this.levelTableIndices[level] * tableSize, true);

        for (final KurzweilKeymapEntry [] entries: this.entryTables)
            for (final KurzweilKeymapEntry entry: entries)
                this.writeEntry (out, entry);

        return out.toByteArray ();
    }


    private void writeEntry (final OutputStream out, final KurzweilKeymapEntry entry) throws IOException
    {
        if ((this.method & METHOD_TUNING_16) > 0)
            StreamUtils.writeSigned16 (out, entry.getTuning (), true);
        else if ((this.method & METHOD_TUNING_8) > 0)
            out.write (entry.getTuning ());
        if ((this.method & METHOD_VOLUME) > 0)
            out.write (entry.getVolumeAdjust ());
        if ((this.method & METHOD_SAMPLE_ID) > 0)
            StreamUtils.writeSigned16 (out, entry.getSampleID (), true);
        if ((this.method & METHOD_SUB_SAMPLE) > 0)
            out.write (entry.getSubSampleNumber ());
    }


    /**
     * Calculate the size of one entry in bytes from the method bits.
     *
     * @param method The method bits
     * @return The entry size
     */
    public static int getEntrySize (final int method)
    {
        int size = 0;
        if ((method & METHOD_TUNING_16) > 0)
            size += 2;
        else if ((method & METHOD_TUNING_8) > 0)
            size += 1;
        if ((method & METHOD_VOLUME) > 0)
            size += 1;
        if ((method & METHOD_SAMPLE_ID) > 0)
            size += 2;
        if ((method & METHOD_SUB_SAMPLE) > 0)
            size += 1;
        return size;
    }


    /**
     * Get the object ID.
     *
     * @return The ID
     */
    public int getId ()
    {
        return this.id;
    }


    /**
     * Get the name of the keymap.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the MIDI note to which an entry responds.
     *
     * @param entryIndex The index of the entry
     * @return The MIDI note
     */
    public int getNoteOfEntry (final int entryIndex)
    {
        return BASE_NOTE + (int) Math.round ((this.basePitch + entryIndex * (double) this.centsPerEntry) / 100.0);
    }


    /**
     * Get the number of entries per table.
     *
     * @return The number of entries
     */
    public int getNumberOfEntries ()
    {
        return this.numEntries;
    }


    /**
     * Get the entry tables.
     *
     * @return The tables
     */
    public List<KurzweilKeymapEntry []> getEntryTables ()
    {
        return this.entryTables;
    }


    /**
     * Add an entry table.
     *
     * @param entries The table with one entry per key position
     * @return The index of the added table
     */
    public int addEntryTable (final KurzweilKeymapEntry [] entries)
    {
        this.entryTables.add (entries);
        return this.entryTables.size () - 1;
    }


    /**
     * Get the index of the entry table which the given dynamic level uses.
     *
     * @param level The dynamic level (0..7)
     * @return The table index
     */
    public int getTableIndexOfLevel (final int level)
    {
        return this.levelTableIndices[level];
    }


    /**
     * Set the index of the entry table which the given dynamic level uses.
     *
     * @param level The dynamic level (0..7)
     * @param tableIndex The table index
     */
    public void setTableIndexOfLevel (final int level, final int tableIndex)
    {
        this.levelTableIndices[level] = tableIndex;
    }


    /**
     * Format the levels as a string.
     *
     * @param lowLevel The low level
     * @param highLevel The high level
     * @return The formatted levels as text
     */
    public static String formatLevels (final int lowLevel, final int highLevel)
    {
        return KurzweilKeymap.LEVEL_NAMES[lowLevel] + (highLevel > lowLevel ? "-" + KurzweilKeymap.LEVEL_NAMES[highLevel] : "");
    }
}
