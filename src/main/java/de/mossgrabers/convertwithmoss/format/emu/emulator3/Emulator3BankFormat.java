// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.nio.charset.StandardCharsets;


/**
 * The bank formats of the E-mu EIII sampler family. All of them use the same preset, zone and
 * sample structures and only differ in the position and the size of the two address tables which
 * locate these structures in the bank. The identifier is stored as the first 16 bytes of a bank.
 *
 * @author Jürgen Moßgraber
 */
public enum Emulator3BankFormat
{
    /** The Emulator IIIX / ESI-32 bank format, also written by the ESI-2000 and ESI-4000. */
    EMULATOR_3X("EMULATOR 3X    ", "Emulator IIIX", ".e3x", 0x74),
    /** The bank format of the ESI series, which reports itself as an ESI-32 running version 3. */
    ESI_32_V3("EMU SI-32 v3   ", "ESI-32/2000/4000", ".esi", 0xEE),
    /** The bank format of the original Emulator III, which holds far fewer objects. */
    EMULATOR_THREE("EMULATOR THREE ", "Emulator III", ".e3b", 0x00);


    /** The number of bytes of the identifier at the start of a bank. */
    public static final int     IDENTIFIER_LENGTH = 16;

    private final String        identifier;
    private final String        deviceName;
    private final String        fileEnding;
    private final int           sampleAreaMarker;


    /**
     * Constructor.
     *
     * @param identifier The 15 character identifier at the start of a bank, which is followed by a
     *            zero byte
     * @param deviceName The name of the sampler(s) which use the format
     * @param fileEnding The file ending which the E-mu DOS/FAT tools give to such a bank
     * @param sampleAreaMarker The value of the filler byte which separates the presets from the
     *            samples
     */
    private Emulator3BankFormat (final String identifier, final String deviceName, final String fileEnding, final int sampleAreaMarker)
    {
        this.identifier = identifier;
        this.deviceName = deviceName;
        this.fileEnding = fileEnding;
        this.sampleAreaMarker = sampleAreaMarker;
    }


    /**
     * Get the name of the sampler(s) which use this format.
     *
     * @return The device name
     */
    public String getDeviceName ()
    {
        return this.deviceName;
    }


    /**
     * Get the file ending which the E-mu DOS/FAT tools give to a bank of this format.
     *
     * @return The file ending including the dot
     */
    public String getFileEnding ()
    {
        return this.fileEnding;
    }


    /**
     * Get the value of the filler byte which sits between the last preset and the first sample.
     *
     * @return The value of the marker byte
     */
    public int getSampleAreaMarker ()
    {
        return this.sampleAreaMarker;
    }


    /**
     * Is this the compact format of the original Emulator III? It stores its address tables
     * directly behind the bank header and biases the preset addresses.
     *
     * @return True if it is the Emulator III format
     */
    public boolean isCompact ()
    {
        return this == EMULATOR_THREE;
    }


    /**
     * Get the offset of the preset address table.
     *
     * @return The offset in the bank
     */
    public int getPresetTableOffset ()
    {
        return this.isCompact () ? 0x6C : 0x17CA;
    }


    /**
     * Get the offset of the sample address table.
     *
     * @return The offset in the bank
     */
    public int getSampleTableOffset ()
    {
        return this.isCompact () ? 0x204 : 0x1BD2;
    }


    /**
     * Get the offset at which the first preset starts.
     *
     * @return The offset in the bank
     */
    public int getPresetAreaOffset ()
    {
        return this.isCompact () ? 0x74A : 0x2B72;
    }


    /**
     * Get the value which is added to all entries of the preset address table. The Emulator III
     * stores the addresses of its presets relative to a fixed position in its sample memory.
     *
     * @return The bias of the preset addresses
     */
    public int getPresetAddressBias ()
    {
        return this.isCompact () ? 0x1A6FE : 0;
    }


    /**
     * Get the maximum number of presets a bank of this format can hold.
     *
     * @return The number of presets
     */
    public int getMaxPresets ()
    {
        return this.isCompact () ? 100 : 256;
    }


    /**
     * Get the maximum number of samples a bank of this format can hold.
     *
     * @return The number of samples
     */
    public int getMaxSamples ()
    {
        return this.isCompact () ? 99 : 999;
    }


    /**
     * Identify the format of a bank from its first bytes.
     *
     * @param data The content of the bank, at least {@link #IDENTIFIER_LENGTH} bytes
     * @return The format or null if the data is not an EIII bank
     */
    public static Emulator3BankFormat get (final byte [] data)
    {
        if (data.length < IDENTIFIER_LENGTH)
            return null;
        // The identifier is 15 characters followed by a terminating zero byte
        if (data[IDENTIFIER_LENGTH - 1] != 0)
            return null;
        final String text = new String (data, 0, IDENTIFIER_LENGTH - 1, StandardCharsets.US_ASCII);
        for (final Emulator3BankFormat format: values ())
            if (format.identifier.equals (text))
                return format;
        return null;
    }


    /**
     * Get the identifier of the format.
     *
     * @return The 15 character identifier
     */
    public String getIdentifier ()
    {
        return this.identifier;
    }
}
