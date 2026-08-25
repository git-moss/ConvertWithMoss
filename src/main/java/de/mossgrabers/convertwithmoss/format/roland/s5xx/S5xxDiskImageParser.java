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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.tools.ui.Functions;


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
public class S5xxDiskImageParser
{
    /** Minimum file size to read the full header. */
    private static final int HDR_MIN_SIZE         = 512;

    // LAND directory
    private static final int LAND_CD_BLOCK_SIZE   = 512;
    private static final int LAND_STRIDE          = 64;

    // Patch area
    private static final int PATCH_BANK1_START    = 64512;

    // Tone area
    private static final int TONE_OFFSET          = 69120;
    private static final int TONE_SIZE            = 128;
    private static final int TONE_COUNT           = 32;
    private static final int TONE_LIST_OFFSET     = 0x11E00;
    private static final int TONE_LIST_SIZE       = 16;

    // Wave Data area
    private static final int WAVE_DATA_A_DISKETTE = 0x12000;
    private static final int WAVE_DATA_A_CDROM    = 0x2400;
    private static final int WAVE_DATA_SIZE       = 0xA2000;

    // Disk label
    // Derivation: base = 68552 + loop counter 99 = 68651; row 1 = 68651 + 8 = 68659
    private static final int LABEL_ROW1_OFFSET    = 68659;
    private static final int LABEL_ROW_LEN        = 12;
    private static final int LABEL_ROW_COUNT      = 5;

    private final byte []    data;


    /**
     * Construct by reading all bytes from {@code file}.
     *
     * @param file The disk file to read
     * @throws IOException Could not read the file
     */
    public S5xxDiskImageParser (final File file) throws IOException
    {
        this.data = Files.readAllBytes (file.toPath ());
    }


    /**
     * Parse the disk image.
     *
     * @return Fully parsed {@link S5xxDiskImage}
     * @throws IOException On insufficient data, unknown type, or S770 (rejected)
     */
    public List<S5xxDiskImage> parse () throws IOException
    {
        this.requireMinSize (HDR_MIN_SIZE, "header region");

        final InputStream input = new ByteArrayInputStream (this.data);
        final S5xxDiskImageHeader header = new S5xxDiskImageHeader (input);

        final S5xxSamplerType samplerType = header.getSamplerType ();
        if (samplerType == S5xxSamplerType.LAND)
            return this.parseCD (header);

        return Collections.singletonList (new S5xxDiskImage (header, this.parsePatches (samplerType, 0), this.parseTones (0), this.parseDiskLabel (0), this.readWaveData (WAVE_DATA_A_DISKETTE, false)));
    }


    private List<S5xxDiskImage> parseCD (final S5xxDiskImageHeader header) throws IOException
    {
        final List<S5xxDiskImage> result = new ArrayList<> ();
        for (final S5xxSoundDirectoryEntry entry: this.parseSoundDirectory (header))
        {
            // The byte start of the virtual disk
            final int virtualDiskOffset = (int) (entry.getOffset () * LAND_CD_BLOCK_SIZE);
            // The read-methods skip the header of the diskette which is not present with the
            // CD-Format!
            final int missingHeaderOffset = virtualDiskOffset - PATCH_BANK1_START;
            final int waveOffset = virtualDiskOffset + WAVE_DATA_A_CDROM;
            result.add (new S5xxDiskImage (header, this.parsePatches (S5xxSamplerType.LAND, missingHeaderOffset), this.parseTones (missingHeaderOffset), this.parseDiskLabel (missingHeaderOffset), this.readWaveData (waveOffset, true)));
        }
        return result;
    }


    private List<S5xxWaveData> readWaveData (final int slotOffset, final boolean isCdRom) throws IOException
    {
        final InputStream input = new ByteArrayInputStream (this.data, slotOffset, WAVE_DATA_SIZE);
        final List<S5xxWaveData> result = new ArrayList<> ();
        for (int i = 0; i < 36; i++)
            result.add (new S5xxWaveData (input, isCdRom ? 16 : 12));
        return result;
    }


    private List<S5xxPatch> parsePatches (final S5xxSamplerType type, final int slotOffset) throws IOException
    {
        final List<S5xxPatch> patches = new ArrayList<> ();
        this.parsePatchBank (patches, slotOffset + PATCH_BANK1_START, type.patchBlockSize (), type.patchCount (), type);
        return patches;
    }


    private void parsePatchBank (final List<S5xxPatch> out, final int bankStart, final int blockSize, final int count, final S5xxSamplerType type) throws IOException
    {
        for (int i = 0; i < count; i++)
        {
            final int off = bankStart + i * blockSize;
            if (off + blockSize > this.data.length)
                break;

            final String patchId = buildPatchId (i, type == S5xxSamplerType.S330);
            final InputStream input = new ByteArrayInputStream (this.data, off, blockSize);
            out.add (new S5xxPatch (i, patchId, input, type));
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


    private List<S5xxTone> parseTones (final int slotOffset) throws IOException
    {
        final List<S5xxTone> tones = new ArrayList<> ();

        for (int i = 0; i < TONE_COUNT; i++)
        {
            final InputStream toneListInput = new ByteArrayInputStream (this.data, slotOffset + TONE_LIST_OFFSET + i * TONE_LIST_SIZE, TONE_LIST_SIZE);
            final S5xxToneList listEntry = new S5xxToneList (toneListInput);
            final InputStream toneInput = new ByteArrayInputStream (this.data, slotOffset + TONE_OFFSET + i * TONE_SIZE, TONE_SIZE);
            tones.add (new S5xxTone (listEntry, toneInput));
        }
        return tones;
    }


    private List<S5xxSoundDirectoryEntry> parseSoundDirectory (final S5xxDiskImageHeader header) throws IOException
    {
        Optional<S5xxCDSectionHeader> soundDirectoryHeader = header.getSectionHeader (S5xxCDSectionHeader.SECTION_SOUND_DIRECTORY);
        if (soundDirectoryHeader.isEmpty ())
            soundDirectoryHeader = header.getSectionHeader (S5xxCDSectionHeader.SECTION_SOUND_DIRECTORY_ALT);
        if (soundDirectoryHeader.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_S5XX_UNSOUND_CD"));

        final S5xxCDSectionHeader sectionHeader = soundDirectoryHeader.get ();
        final long baseOffset = sectionHeader.getOffset () * LAND_CD_BLOCK_SIZE;
        final long maxSlots = (sectionHeader.getSize () * LAND_CD_BLOCK_SIZE) / LAND_STRIDE;

        final List<S5xxSoundDirectoryEntry> entries = new ArrayList<> ();
        for (int slot = 0; slot < maxSlots; slot++)
        {
            final long off = baseOffset + slot * LAND_STRIDE;
            final ByteArrayInputStream entryInputStream = new ByteArrayInputStream (this.data, (int) off, LAND_STRIDE);
            final S5xxSoundDirectoryEntry e = new S5xxSoundDirectoryEntry (entryInputStream);
            if (!e.getName ().isEmpty ())
                entries.add (e);
        }
        return entries;
    }


    private Optional<S5xxDiskLabel> parseDiskLabel (final int slotOffset)
    {
        final int startOffset = slotOffset + LABEL_ROW1_OFFSET;

        // Rows 2-5 start right after row 1 (68671), stored in 12 groups of 4 interleaved bytes.
        // 68671
        final int labelRows25Offset = startOffset + LABEL_ROW_LEN;

        // Last byte needed is index 68718 = LABEL_ROWS25_OFF + 12 groups × 4 bytes − 1
        final int endNeeded = labelRows25Offset + LABEL_ROW_LEN * 4; // 68719
        if (this.data.length < endNeeded)
            return Optional.empty ();

        final String [] rows = new String [LABEL_ROW_COUNT];

        // Row 1: contiguous bytes 68659–68670
        rows[0] = this.readAscii (startOffset, LABEL_ROW_LEN).trim ().replace ((char) 0, ' ');

        // Rows 2–5: interleaved 4-byte groups starting at 68671
        // group N at offset (68671 + N*4): [+0]=row2[N], [+1]=row3[N], [+2]=row4[N], [+3]=row5[N]
        final char [] [] chars = new char [4] [LABEL_ROW_LEN]; // [row-2..5][char]
        for (int n = 0; n < LABEL_ROW_LEN; n++)
        {
            final int g = labelRows25Offset + n * 4;
            chars[0][n] = (char) (this.data[g] & 0xFF);
            chars[1][n] = (char) (this.data[g + 1] & 0xFF);
            chars[2][n] = (char) (this.data[g + 2] & 0xFF);
            chars[3][n] = (char) (this.data[g + 3] & 0xFF);
        }
        for (int r = 0; r < 4; r++)
            rows[r + 1] = new String (chars[r]).trim ().replace ((char) 0, ' ');

        return Optional.of (new S5xxDiskLabel (rows));
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
        final int safeLen = Math.clamp (this.data.length - (long) offset, 0, length);
        return new String (this.data, offset, safeLen, StandardCharsets.US_ASCII);
    }
}