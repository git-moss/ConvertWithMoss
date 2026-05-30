// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;


/**
 * Parser for Roland S-50 / S-550 / W-30 / S-330 floppy and hard-drive disk images.
 *
 * <li>1 x System Program (0/0: 64512 bytes)
 * <li>16 x Patch Parameter (64512/FFC0: 4096 bytes) - 8 x 512 (S-50) / 16 x 256 (all others)
 * <li>1 x Function Parameter (68608/10C00: 256 bytes)
 * <li>1 x MIDI Parameter (68864/10D00: 256 bytes)
 * <li>32 x Tone Parameter (69120/10E00: 4096 bytes) - 32 x 128
 * <li>32 x Tone List (73216/11E00: 512 bytes) - 32 x 16
 * <li>18 x Wave Data A (73728/12000: 331776) - 18 x 18432
 * <li>18 x Wave Data B (405504/63000: 331776) - 18 x 18432
 * <li>Full size: 737280/B4000
 *
 * @author Jürgen Moßgraber
 */
public class DiskImageParser
{
    /** Minimum file size to read the full header. */
    private static final int HDR_MIN_SIZE      = 512;

    // LAND directory
    private static final int LAND_HD_OFFSET    = 1024;
    private static final int LAND_CD_OFFSET    = 512;
    private static final int LAND_STRIDE       = 64;
    private static final int LAND_HD_MAX       = 64;
    private static final int LAND_CD_MAX       = 309;
    private static final int LAND_NAME_CHARS   = 50;

    // Patch area
    private static final int PATCH_BANK1_START = 64512;
    // 64512 + 8 × 256
    private static final int PATCH_BANK2_START = 66560;

    // Tone area
    private static final int TONE_OFFSET       = 69120;
    private static final int TONE_SIZE         = 128;
    private static final int TONE_COUNT        = 32;
    private static final int TONE_LIST_OFFSET  = 0x11E00;
    private static final int TONE_LIST_SIZE    = 16;

    // Wave Data area
    private static final int WAVE_DATA_A       = 0x12000;
    private static final int WAVE_DATA_SIZE    = 0xA2000;

    // Disk label
    // Derivation: base = 68552 + loop counter 99 = 68651; row 1 = 68651 + 8 = 68659
    private static final int LABEL_ROW1_OFFSET = 68659;
    private static final int LABEL_ROW_LEN     = 12;
    private static final int LABEL_ROW_COUNT   = 5;
    // Rows 2-5 start right after row 1 (68671), stored in 12 groups of 4 interleaved bytes.
    // 68671
    private static final int LABEL_ROWS25_OFF  = LABEL_ROW1_OFFSET + LABEL_ROW_LEN;

    private final byte []    data;


    /**
     * Construct by reading all bytes from {@code file}.
     *
     * @param file The disk file to read
     * @throws IOException Could not read the file
     */
    public DiskImageParser (final File file) throws IOException
    {
        this.data = Files.readAllBytes (file.toPath ());
    }


    /**
     * Parse the disk image.
     *
     * @return Fully parsed {@link DiskImage}
     * @throws IOException on insufficient data, unknown type, or S770 (rejected)
     */
    public DiskImage parse () throws IOException
    {
        this.requireMinSize (HDR_MIN_SIZE, "header region");

        final InputStream input = new ByteArrayInputStream (this.data);

        final RolandDiskImageHeader header = new RolandDiskImageHeader (input);
        final SamplerType samplerType = header.getSamplerType ();
        if (samplerType == SamplerType.LAND)
        {
            @SuppressWarnings("unused")
            final List<DirectoryEntry> landDirectory = this.parseLandDirectory (header);
            throw new IOException ("HD / CD-Roms currently not supported.");
        }

        return new DiskImage (header, this.parsePatches (samplerType), this.parseTones (), this.parseDiskLabel (), this.readWaveData ());
    }


    private List<WaveData> readWaveData () throws IOException
    {
        final InputStream input = new ByteArrayInputStream (this.data, WAVE_DATA_A, WAVE_DATA_SIZE);
        final List<WaveData> result = new ArrayList<> ();
        for (int i = 0; i < 36; i++)
            result.add (new WaveData (input));
        return result;
    }


    private List<Patch> parsePatches (final SamplerType type) throws IOException
    {
        final int blockSize = type.patchBlockSize ();
        final int patchCount = type.patchCount ();

        final List<Patch> patches = new ArrayList<> ();

        // Bank 1 - first 8 patches
        final int bank1Count = type.isS50 () ? patchCount : Math.min (8, patchCount);
        this.parsePatchBank (patches, PATCH_BANK1_START, blockSize, bank1Count, 0, type);

        // Bank 2 — second 8 patches for S-550 / W-30 / S-330 only
        if (!type.isS50 () && patchCount > 8)
            this.parsePatchBank (patches, PATCH_BANK2_START, blockSize, patchCount - 8, 8, type);
        return patches;
    }


    private void parsePatchBank (final List<Patch> out, final int bankStart, final int blockSize, final int count, final int globalIndexOffset, final SamplerType type) throws IOException
    {
        for (int i = 0; i < count; i++)
        {
            final int off = bankStart + i * blockSize;
            if (off + blockSize > this.data.length)
                break;

            final int gi = globalIndexOffset + i;
            final String patchId = buildPatchId (gi, type == SamplerType.S330);
            final InputStream input = new ByteArrayInputStream (this.data, off, blockSize);
            out.add (new Patch (gi, patchId, input, type));
        }
    }


    /**
     * Builds the patch ID string.
     * <ul>
     * <li>S-330: P11–P18 (bank 1), P21–P28 (bank 2).</li>
     * <li>Others: P1–P8 or P1–P16 (sequential).</li>
     * </ul>
     *
     * @param zeroBasedIndex The index
     * @param isS330 True if it is S330 format
     * @return The formated patch ID string
     */
    private static String buildPatchId (final int zeroBasedIndex, final boolean isS330)
    {
        if (isS330)
        {
            final int group = zeroBasedIndex / 8 + 1; // 1 or 2
            final int within = zeroBasedIndex % 8 + 1; // 1–8
            return "P" + group + within;
        }
        return "P" + (zeroBasedIndex + 1);
    }


    private List<Tone> parseTones () throws IOException
    {
        final List<Tone> tones = new ArrayList<> ();

        for (int i = 0; i < TONE_COUNT; i++)
        {
            final InputStream toneListInput = new ByteArrayInputStream (this.data, TONE_LIST_OFFSET + i * TONE_LIST_SIZE, TONE_LIST_SIZE);
            final InputStream toneInput = new ByteArrayInputStream (this.data, TONE_OFFSET + i * TONE_SIZE, TONE_SIZE);
            tones.add (new Tone (new ToneList (toneListInput), toneInput));
        }
        return tones;
    }


    private List<DirectoryEntry> parseLandDirectory (final RolandDiskImageHeader header)
    {
        final boolean isCdRom = header.isCdRom ();
        final int baseOffset = isCdRom ? LAND_CD_OFFSET : LAND_HD_OFFSET;
        final int maxSlots = isCdRom ? LAND_CD_MAX : LAND_HD_MAX;

        final List<DirectoryEntry> entries = new ArrayList<> ();

        for (int slot = 0; slot < maxSlots; slot++)
        {
            final int off = baseOffset + slot * LAND_STRIDE;
            if (off >= this.data.length)
                break;

            final int available = Math.min (LAND_NAME_CHARS, this.data.length - off);
            final String name = this.readAsciiPrintable (off, available);

            // CD-ROM: the tool stops at the first all-blank slot
            if (isCdRom && name.isEmpty ())
                break;

            entries.add (new DirectoryEntry (slot + 1, name));
        }
        return entries;
    }


    private DiskLabel parseDiskLabel ()
    {
        // Last byte needed is index 68718 = LABEL_ROWS25_OFF + 12 groups × 4 bytes − 1
        final int endNeeded = LABEL_ROWS25_OFF + LABEL_ROW_LEN * 4; // 68719
        if (this.data.length < endNeeded)
            return null;

        final String [] rows = new String [LABEL_ROW_COUNT];

        // Row 1: contiguous bytes 68659–68670
        rows[0] = this.readAscii (LABEL_ROW1_OFFSET, LABEL_ROW_LEN);

        // Rows 2–5: interleaved 4-byte groups starting at 68671
        // group N at offset (68671 + N*4): [+0]=row2[N], [+1]=row3[N], [+2]=row4[N], [+3]=row5[N]
        final char [] [] chars = new char [4] [LABEL_ROW_LEN]; // [row-2..5][char]
        for (int n = 0; n < LABEL_ROW_LEN; n++)
        {
            final int g = LABEL_ROWS25_OFF + n * 4;
            chars[0][n] = (char) (this.data[g] & 0xFF);
            chars[1][n] = (char) (this.data[g + 1] & 0xFF);
            chars[2][n] = (char) (this.data[g + 2] & 0xFF);
            chars[3][n] = (char) (this.data[g + 3] & 0xFF);
        }
        for (int r = 0; r < 4; r++)
            rows[r + 1] = new String (chars[r]);

        return new DiskLabel (rows);
    }


    private void requireMinSize (final int minSize, final String context) throws IOException
    {
        if (this.data.length < minSize)
            throw new IOException ("Data too short for " + context + ": need " + minSize + " bytes, got " + this.data.length);
    }


    /**
     * Reads {@code length} raw bytes at {@code offset} as US-ASCII.
     *
     * @param offset The offset in the data array where the string starts
     * @param length The length of the string
     * @return The ASCII text
     */
    private String readAscii (final int offset, final int length)
    {
        final int safeLen = Math.max (0, Math.min (length, this.data.length - offset));
        return new String (this.data, offset, safeLen, StandardCharsets.US_ASCII);
    }


    /**
     * Reads up to {@code maxLength} <em>printable</em> ASCII bytes (0x20–0x7E), stopping early at
     * the first non-printable byte.
     *
     * @param offset The offset in the data array where the string starts
     * @param maxLength The maximum length
     * @return The ASCII text
     */
    private String readAsciiPrintable (final int offset, final int maxLength)
    {
        final StringBuilder sb = new StringBuilder (maxLength);
        for (int i = 0; i < maxLength && offset + i < this.data.length; i++)
        {
            final char c = (char) (this.data[offset + i] & 0xFF);
            if (c < 0x20 || c > 0x7E)
                break;
            sb.append (c);
        }
        return sb.toString ();
    }
}