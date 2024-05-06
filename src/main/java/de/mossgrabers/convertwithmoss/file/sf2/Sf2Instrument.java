// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.tools.StringUtils;


/**
 * An SF2 instrument.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Instrument
{
    /** The length of the preset header */
    public static final int               LENGTH_INSTRUMENT = 22;

    private String                        name;
    /** Pointer into IBAG list. */
    private int                           firstZoneIndex;

    private final List<Sf2InstrumentZone> zones             = new ArrayList<> ();


    /**
     * Get the name of the instrument.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the index in the IBAG structure (the first zone of the preset).
     *
     * @return The index
     */
    public int getFirstZoneIndex ()
    {
        return this.firstZoneIndex;
    }


    /**
     * Set the index of the first zone of the preset.
     *
     * @param firstZoneIndex The index
     */
    public void setFirstZoneIndex (final int firstZoneIndex)
    {
        this.firstZoneIndex = firstZoneIndex;
    }


    /**
     * Reads the data from a preset header chunk.
     *
     * @param offset The offset to start reading
     * @param chunk The chunk to read
     */
    public void readHeader (final int offset, final RIFFChunk chunk)
    {
        final byte [] data = chunk.getData ();

        int pos = 0;
        while (pos < 20 && data[offset + pos] != 0)
            pos++;
        this.name = new String (data, offset, pos, StandardCharsets.US_ASCII).trim ();
        this.firstZoneIndex = chunk.getTwoBytesAsInt (offset + 20);
    }


    /**
     * Write the data to a preset header chunk.
     * 
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void writeHeader (final ByteArrayOutputStream out) throws IOException
    {
        StreamUtils.writeASCII (out, StringUtils.fixASCII (this.name), 20);
        StreamUtils.writeUnsigned16 (out, this.firstZoneIndex, false);
    }


    /**
     * Write the data to a preset header chunk.
     * 
     * @param out The output stream to write to
     * @param lastZoneIndex The last (unused) zone index
     * @throws IOException Could not write the data
     */
    public static void writeLastHeader (final ByteArrayOutputStream out, final int lastZoneIndex) throws IOException
    {
        StreamUtils.writeASCII (out, "EOI", 20);
        StreamUtils.writeUnsigned16 (out, lastZoneIndex, false);
    }


    /**
     * Adds a zone to the preset.
     *
     * @param zone The zone to add
     */
    public void addZone (final Sf2InstrumentZone zone)
    {
        this.zones.add (zone);
    }


    /**
     * Get the zone at the given index.
     *
     * @param zoneIndex The index of the zone
     * @return The zone
     */
    public Sf2InstrumentZone getZone (final int zoneIndex)
    {
        return this.zones.get (zoneIndex);
    }


    /**
     * Get the number of zones.
     *
     * @return The number of zones
     */
    public int getZoneCount ()
    {
        return this.zones.size ();
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return this.name + " (Zone index: " + this.firstZoneIndex + ")";
    }
}
