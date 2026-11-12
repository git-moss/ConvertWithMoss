// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.CRC32;

import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.KontaktIcon;


/**
 * Updates the sound header (BNI_SOUND_HEADER chunk) of a Kontakt 5+ container. The header has the
 * same structure as the header of the Kontakt 4.2 format. It contains a CRC32 of the preset data
 * and a MD5 checksum of the header itself, therefore it needs to be updated if the preset data
 * changes.
 *
 * @author Jürgen Moßgraber
 */
public final class KontaktSoundHeader
{
    /** The size of the header. */
    public static final int      HEADER_SIZE        = 222;

    private static final int     OFFSET_TIMESTAMP   = 0x18;
    private static final int     OFFSET_ZONES       = 0x20;
    private static final int     OFFSET_GROUPS      = 0x22;
    private static final int     OFFSET_INSTRUMENTS = 0x24;
    private static final int     OFFSET_SAMPLE_SIZE = 0x26;
    private static final int     OFFSET_ICON        = 0x36;
    private static final int     OFFSET_AUTHOR      = 0x3A;
    private static final int     SIZE_AUTHOR        = 8;
    private static final int     OFFSET_CATEGORIES  = 0x42;
    private static final int     OFFSET_CHECKSUM    = 0xA2;
    private static final int     SIZE_CHECKSUM      = 16;
    private static final int     OFFSET_CRC         = 0xB6;
    private static final String  NULL_ENTRY         = "(null)";

    // The MD5 checksum is calculated from these 2 bytes followed by the header (with the checksum
    // set to 0)
    private static final byte [] CHECKSUM_PREFIX    =
    {
        0x09,
        (byte) 0xE2
    };


    /**
     * Private due to utility class.
     */
    private KontaktSoundHeader ()
    {
        // Intentionally empty
    }


    /**
     * Updates the values of the header which depend on the program and the preset data and
     * recalculates the checksums.
     *
     * @param header The header to update, must have a length of 222 bytes
     * @param program The program which is stored in the preset data
     * @param sizeOfSamples The sum of the size of all used samples (only the sample data)
     * @param presetData The (uncompressed) preset data
     * @throws IOException The header has the wrong size or the checksum could not be calculated
     */
    public static void update (final byte [] header, final Program program, final long sizeOfSamples, final byte [] presetData) throws IOException
    {
        if (header.length != HEADER_SIZE)
            throw new IOException ("Unsupported sound header size: " + header.length);

        writeUnsigned32 (header, OFFSET_TIMESTAMP, System.currentTimeMillis () / 1000);
        writeUnsigned16 (header, OFFSET_ZONES, program.getZones ().size ());
        writeUnsigned16 (header, OFFSET_GROUPS, program.getGroups ().size ());
        writeUnsigned16 (header, OFFSET_INSTRUMENTS, 1);
        writeUnsigned32 (header, OFFSET_SAMPLE_SIZE, sizeOfSamples);
        writeUnsigned32 (header, OFFSET_ICON, KontaktIcon.getID (program.getInstrumentIconName ()));

        // The author is limited to 7 characters followed by a zero byte
        Arrays.fill (header, OFFSET_AUTHOR, OFFSET_AUTHOR + SIZE_AUTHOR, (byte) 0);
        final String author = program.getInstrumentAuthor ();
        if (author != null && !author.isBlank () && !NULL_ENTRY.equals (author))
        {
            final byte [] authorBytes = author.getBytes (StandardCharsets.ISO_8859_1);
            System.arraycopy (authorBytes, 0, header, OFFSET_AUTHOR, Math.min (authorBytes.length, SIZE_AUTHOR - 1));
        }

        final int [] categories = program.getInstrumentCategories ();
        for (int i = 0; i < categories.length; i++)
            header[OFFSET_CATEGORIES + i] = (byte) categories[i];

        updateChecksums (header, presetData);
    }


    /**
     * Updates the values of the header of a multi which depend on the preset data and recalculates
     * the checksums.
     *
     * @param header The header to update, must have a length of 222 bytes
     * @param numZones The number of zones of all instruments
     * @param numGroups The number of groups of all instruments
     * @param numInstruments The number of instruments
     * @param sizeOfSamples The sum of the size of all used samples (only the sample data)
     * @param presetData The (uncompressed) preset data
     * @throws IOException The header has the wrong size or the checksum could not be calculated
     */
    public static void updateMulti (final byte [] header, final int numZones, final int numGroups, final int numInstruments, final long sizeOfSamples, final byte [] presetData) throws IOException
    {
        if (header.length != HEADER_SIZE)
            throw new IOException ("Unsupported sound header size: " + header.length);

        writeUnsigned32 (header, OFFSET_TIMESTAMP, System.currentTimeMillis () / 1000);
        writeUnsigned16 (header, OFFSET_ZONES, numZones);
        writeUnsigned16 (header, OFFSET_GROUPS, numGroups);
        writeUnsigned16 (header, OFFSET_INSTRUMENTS, numInstruments);
        writeUnsigned32 (header, OFFSET_SAMPLE_SIZE, sizeOfSamples);
        updateChecksums (header, presetData);
    }


    /**
     * Calculates the CRC32 of the preset data and the MD5 checksum of the header (which contains
     * the CRC32).
     *
     * @param header The header
     * @param presetData The (uncompressed) preset data
     * @throws IOException The checksum could not be calculated
     */
    private static void updateChecksums (final byte [] header, final byte [] presetData) throws IOException
    {
        // CRC32 of the uncompressed preset data
        final CRC32 crc = new CRC32 ();
        crc.update (presetData);
        writeUnsigned32 (header, OFFSET_CRC, crc.getValue ());

        // MD5 of the header with the checksum set to 0
        Arrays.fill (header, OFFSET_CHECKSUM, OFFSET_CHECKSUM + SIZE_CHECKSUM, (byte) 0);
        try
        {
            final MessageDigest md5 = MessageDigest.getInstance ("MD5");
            md5.update (CHECKSUM_PREFIX);
            md5.update (header);
            System.arraycopy (md5.digest (), 0, header, OFFSET_CHECKSUM, SIZE_CHECKSUM);
        }
        catch (final NoSuchAlgorithmException ex)
        {
            throw new IOException (ex);
        }
    }


    private static void writeUnsigned16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    private static void writeUnsigned32 (final byte [] data, final int offset, final long value)
    {
        writeUnsigned16 (data, offset, (int) (value & 0xFFFF));
        writeUnsigned16 (data, offset + 2, (int) (value >> 16 & 0xFFFF));
    }
}
