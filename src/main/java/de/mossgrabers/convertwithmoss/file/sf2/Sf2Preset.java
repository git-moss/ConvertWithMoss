// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.tools.StringUtils;


/**
 * A SF2 preset. A preset is a setup of several instruments with potentially different key-ranges
 * and other settings. Since there is no concept of groups, SF2 presets are treated as multi-sample
 * sources and SF2 instruments are treated as groups.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Preset extends AbstractGroupedZones<Sf2PresetZone>
{
    /** The length of the preset header */
    public static final int LENGTH_PRESET_HEADER = 38;

    private int             programNumber        = 0;
    private int             bankNumber           = 0;


    /**
     * Default constructor.
     */
    public Sf2Preset ()
    {
        super ();
    }


    /**
     * Constructor.
     *
     * @param name The name of the preset
     */
    public Sf2Preset (final String name)
    {
        super (name);
    }


    /**
     * Get the program number of the preset in the bank.
     *
     * @return The program number
     */
    public int getProgramNumber ()
    {
        return this.programNumber;
    }


    /**
     * Set the program number of the preset in the bank.
     *
     * @param programNumber The program number
     */
    public void setProgramNumber (final int programNumber)
    {
        this.programNumber = programNumber;
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
     * Set the index of the bank to which the preset belongs.
     *
     * @param bankNumber The bank index
     */
    public void setBankNumber (final int bankNumber)
    {
        this.bankNumber = bankNumber;
    }


    /**
     * Reads the data from a preset header chunk.
     *
     * @param offset The offset to start reading
     * @param chunk The chunk to read
     */
    public void readHeader (final int offset, final RawRIFFChunk chunk)
    {
        final byte [] data = chunk.getData ();

        int pos = 0;
        while (pos < 20 && data[offset + pos] != 0)
            pos++;
        this.name = new String (data, offset, pos, StandardCharsets.US_ASCII).trim ();
        this.programNumber = chunk.getTwoBytesAsInt (offset + 20);
        this.bankNumber = chunk.getTwoBytesAsInt (offset + 22);
        this.firstZoneIndex = chunk.getTwoBytesAsInt (offset + 24);

        // The DWORDs dwLibrary, dwGenre and dwMorphology are reserved for future implementation in
        // a preset library management function
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

        StreamUtils.writeUnsigned16 (out, this.programNumber, false);
        StreamUtils.writeUnsigned16 (out, this.bankNumber, false);
        StreamUtils.writeUnsigned16 (out, this.firstZoneIndex, false);

        // The DWORDs dwLibrary, dwGenre and dwMorphology are reserved for future implementation in
        // a preset library management function
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
        StreamUtils.writeUnsigned32 (out, 0, false);
    }


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append (" - ").append (this.name).append (" (").append (this.programNumber).append (':').append (this.bankNumber);
        sb.append (", Zone index: ").append (this.firstZoneIndex).append (")\n");
        for (int zoneIndex = 0; zoneIndex < this.getZoneCount (); zoneIndex++)
        {
            final Sf2PresetZone zone = this.getZone (zoneIndex);
            sb.append ("   * Zone ").append (zoneIndex + 1).append (zone.printInfo ());
        }
        return sb.toString ();
    }


    /** {@inheritDoc} */
    @Override
    protected Sf2PresetZone createGlobalZone ()
    {
        return new Sf2PresetZone (0, 0, 0, 0);
    }
}
