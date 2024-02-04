// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * A SF2 preset.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Preset
{
    /** The length of the preset header */
    public static final int           LENGTH_PRESET_HEADER = 38;

    private String                    name;
    private int                       number;
    private int                       bankNumber;
    /** Pointer into PBAG list. */
    private int                       firstZoneIndex;

    private final List<Sf2PresetZone> zones                = new ArrayList<> ();


    /**
     * Get the name of the preset.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the program number of the preset in the bank.
     *
     * @return The program number
     */
    public int getNumber ()
    {
        return this.number;
    }


    /**
     * Get the index of the bank to which the preset belongs.
     *
     * @return The bank index
     */
    public int getBankNumber ()
    {
        return this.bankNumber;
    }


    /**
     * Get the index in the PBAG structure (the first zone of the preset).
     *
     * @return The index
     */
    public int getFirstZoneIndex ()
    {
        return this.firstZoneIndex;
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
        this.number = chunk.twoBytesAsInt (offset + 20);
        this.bankNumber = chunk.twoBytesAsInt (offset + 22);
        this.firstZoneIndex = chunk.twoBytesAsInt (offset + 24);

        // The DWORDs dwLibrary, dwGenre and dwMorphology are reserved for future implementation in
        // a preset library management function
    }


    /**
     * Adds a zone to the preset.
     *
     * @param zone The zone to add
     */
    public void addZone (final Sf2PresetZone zone)
    {
        this.zones.add (zone);
    }


    /**
     * Get the zone at the given index.
     *
     * @param zoneIndex The index of the zone
     * @return The zone
     */
    public Sf2PresetZone getZone (final int zoneIndex)
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


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append (" - ").append (this.name).append (" (").append (this.number).append (':').append (this.bankNumber);
        sb.append (", Zone index: ").append (this.firstZoneIndex).append (")\n");
        for (int zoneIndex = 0; zoneIndex < this.getZoneCount (); zoneIndex++)
        {
            final Sf2PresetZone zone = this.getZone (zoneIndex);
            sb.append ("   * Zone ").append (zoneIndex + 1).append (zone.printInfo ());
        }
        return sb.toString ();
    }
}
