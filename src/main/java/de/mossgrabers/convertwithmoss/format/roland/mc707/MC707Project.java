// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.mc707;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreUtil;


/**
 * A Roland MC-707 / MC-101 project file (<i>.mpj</i>). The container is a fixed-layout variant of
 * the ZEN-Core <i>SVD5</i> framing (see <i>MC707_FORMAT.md</i>): a 7-entry TOC of <i>MC77</i>
 * sections in which only the looper-audio (<i>LPDa</i>) and user-sample (<i>USDa</i>) sections vary
 * in size. The <i>USRa</i> section holds the project's user banks (64 tones, 64 drum kits with 88
 * key records each, 500 sample-parameter slots); the <i>PRJa</i> section additionally holds the
 * per-clip tone/kit banks of the 8 tracks.
 *
 * <p>
 * New projects are created from the device's own init project: the firmware of both grooveboxes
 * embeds the byte-identical image (Okumura-LZSS compressed as <i>init.lzs</i>), which is shipped
 * here as a gzip-ed resource and patched with the converted content.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public class MC707Project
{
    /** The number of user tone slots in the USRa bank. */
    public static final int           NUM_USER_TONES       = 64;
    /** The number of user drum-kit slots in the USRa bank. */
    public static final int           NUM_USER_KITS        = 64;
    /** The number of sample slots in the sample-parameter table. */
    public static final int           NUM_SAMPLE_SLOTS     = 500;
    /** The number of multisample key-map slots. */
    public static final int           NUM_MULTISAMPLE_MAPS = 128;
    /** The number of clip-tone slots in the PRJa bank (6 groups of 16 clips + 1 current). */
    public static final int           NUM_CLIP_TONES       = 102;
    /** The number of clip-kit slots in the PRJa bank (8 groups of 16 clips + 1 current). */
    public static final int           NUM_CLIP_KITS        = 136;

    /** The size of a ZEN-Core tone record (identical to the FANTOM PATa record). */
    public static final int           TONE_SIZE            = 1632;
    /** The size of a drum-kit common record. */
    public static final int           KIT_SIZE             = 3328;
    /** The size of a drum-kit key record. */
    public static final int           KIT_KEY_SIZE         = 216;
    /** The size of a sample-parameter record. */
    public static final int           SAMPLE_PARAM_SIZE    = 84;
    /** The size of a multisample key-map record: the name plus 4 bytes for each of the 128 keys. */
    public static final int           MULTISAMPLE_MAP_SIZE = 16 + 128 * 4;
    /** The number of key records of a kit; record 0 = MIDI key 21 (A0), record 87 = 108 (C8). */
    public static final int           KIT_NUM_KEYS         = 88;
    /** The MIDI note of a kit's first key record. */
    public static final int           KIT_BASE_KEY         = 21;
    /** The length of all name fields. */
    public static final int           NAME_LENGTH          = 16;

    // Offsets of the user banks inside the USRa section (16 byte block header included).
    private static final int          USR_TONES            = 0x10;
    private static final int          USR_KITS             = 0x10 + 0x19800;
    private static final int          USR_KIT_KEYS         = 0x10 + 0x4D800;
    private static final int          USR_SAMPLE_TABLE     = 0x10 + 0x176800;
    private static final int          USR_MULTISAMPLE_MAPS = 0x10 + 0x180C10;

    // Offsets of the clip sound banks inside the PRJa section (see MC707_FORMAT.md §4).
    private static final int          PRJ_NAME             = 0x10;
    private static final int          PRJ_CLIPS            = 0x10F0;
    private static final int          PRJ_TONES            = 0x31EBB0;
    private static final int          PRJ_KITS             = 0x3475F0;
    private static final int          PRJ_KIT_KEYS         = 0x3B5DF0;

    /** The size of a clip record (the clip's sound name and common data plus its sequence). */
    private static final int          CLIP_RECORD_SIZE     = 0x5C40;

    private static final String       MAGIC                = "PRJ5";
    private static final int          TOC_OFFSET           = 0x10;
    private static final int          TOC_ENTRY_SIZE       = 16;
    private static final int          NUM_SECTIONS         = 7;

    private static final byte []      TEMPLATE             = loadTemplate ();

    private final byte []             data;
    private final Map<String, int []> sections             = new HashMap<> ();


    /**
     * Constructor. Parses and validates the TOC.
     *
     * @param data The content of a project file
     * @throws IOException The data is not a MC-707/MC-101 project
     */
    public MC707Project (final byte [] data) throws IOException
    {
        this.data = data;
        if (data.length < 0x80 || !MAGIC.equals (new String (data, 2, 4)))
            throw new IOException ("Not a Roland MC-707/MC-101 project (missing PRJ5 header).");
        for (int i = 0; i < NUM_SECTIONS; i++)
        {
            final int entry = TOC_OFFSET + i * TOC_ENTRY_SIZE;
            final String tag = new String (data, entry, 4);
            final int offset = (int) ZenCoreUtil.readUnsigned32 (data, entry + 8, false);
            final int size = (int) ZenCoreUtil.readUnsigned32 (data, entry + 12, false);
            if (offset < 0x80 || offset + size > data.length)
                throw new IOException ("Corrupt MC-707/MC-101 project: section '" + tag + "' exceeds the file.");
            this.sections.put (tag, new int []
            {
                offset,
                size
            });
        }
        if (!this.sections.containsKey ("USRa") || !this.sections.containsKey ("USDa"))
            throw new IOException ("Corrupt MC-707/MC-101 project: user sections missing.");
    }


    /**
     * Create a fresh project from the device's init-project image.
     *
     * @return The project, ready to be patched
     * @throws IOException Could not create the project
     */
    public static MC707Project createFromTemplate () throws IOException
    {
        return new MC707Project (TEMPLATE.clone ());
    }


    /**
     * Get the raw file data (the live buffer, not a copy).
     *
     * @return The data
     */
    public byte [] getData ()
    {
        return this.data;
    }


    /**
     * Get the file offset of a section.
     *
     * @param tag The section tag, e.g. "USRa"
     * @return The offset
     */
    public int getSectionOffset (final String tag)
    {
        return this.sections.get (tag)[0];
    }


    /**
     * Get the size of a section.
     *
     * @param tag The section tag
     * @return The size in bytes
     */
    public int getSectionSize (final String tag)
    {
        return this.sections.get (tag)[1];
    }


    /**
     * Set the project name (shown in the device's project list).
     *
     * @param name The name, truncated to 16 characters
     */
    public void setProjectName (final String name)
    {
        System.arraycopy (ZenCoreUtil.padName (name, NAME_LENGTH), 0, this.data, this.getSectionOffset ("PRJa") + PRJ_NAME, NAME_LENGTH);
    }


    /**
     * Get the file offset of a user-bank tone record.
     *
     * @param slot The tone slot (0-63)
     * @return The offset
     */
    public int getUserToneOffset (final int slot)
    {
        return this.getSectionOffset ("USRa") + USR_TONES + slot * TONE_SIZE;
    }


    /**
     * Get the file offset of a user-bank drum-kit common record.
     *
     * @param slot The kit slot (0-63)
     * @return The offset
     */
    public int getUserKitOffset (final int slot)
    {
        return this.getSectionOffset ("USRa") + USR_KITS + slot * KIT_SIZE;
    }


    /**
     * Get the file offset of a user-bank drum-kit key record.
     *
     * @param slot The kit slot (0-63)
     * @param keyIndex The key record index (0-87, MIDI key 21 + index)
     * @return The offset
     */
    public int getUserKitKeyOffset (final int slot, final int keyIndex)
    {
        return this.getSectionOffset ("USRa") + USR_KIT_KEYS + (slot * KIT_NUM_KEYS + keyIndex) * KIT_KEY_SIZE;
    }


    /**
     * Get the file offset of a sample-parameter record.
     *
     * @param slot The sample slot (0-499)
     * @return The offset
     */
    public int getSampleParamOffset (final int slot)
    {
        return this.getSectionOffset ("USRa") + USR_SAMPLE_TABLE + slot * SAMPLE_PARAM_SIZE;
    }


    /**
     * Get the file offset of a multisample key-map record.
     *
     * @param slot The map slot (0-127)
     * @return The offset
     */
    public int getMultisampleMapOffset (final int slot)
    {
        return this.getSectionOffset ("USRa") + USR_MULTISAMPLE_MAPS + slot * MULTISAMPLE_MAP_SIZE;
    }


    /**
     * Get the file offset of a clip record in the PRJa section. The record starts with the clip's
     * user-facing sound name (the kit-common names of Roland's preset projects are partly stale,
     * the clip record carries the name the device displays).
     *
     * @param slot The slot (0-135, 8 tracks of 16 clips + 1 current)
     * @return The offset
     */
    public int getClipRecordOffset (final int slot)
    {
        return this.getSectionOffset ("PRJa") + PRJ_CLIPS + slot * CLIP_RECORD_SIZE;
    }


    /**
     * Get the file offset of a clip-bank tone record in the PRJa section.
     *
     * @param slot The slot (0-101)
     * @return The offset
     */
    public int getClipToneOffset (final int slot)
    {
        return this.getSectionOffset ("PRJa") + PRJ_TONES + slot * TONE_SIZE;
    }


    /**
     * Get the file offset of a clip-bank drum-kit common record in the PRJa section.
     *
     * @param slot The slot (0-135)
     * @return The offset
     */
    public int getClipKitOffset (final int slot)
    {
        return this.getSectionOffset ("PRJa") + PRJ_KITS + slot * KIT_SIZE;
    }


    /**
     * Get the file offset of a clip-bank drum-kit key record in the PRJa section.
     *
     * @param slot The kit slot (0-135)
     * @param keyIndex The key record index (0-87)
     * @return The offset
     */
    public int getClipKitKeyOffset (final int slot, final int keyIndex)
    {
        return this.getSectionOffset ("PRJa") + PRJ_KIT_KEYS + (slot * KIT_NUM_KEYS + keyIndex) * KIT_KEY_SIZE;
    }


    /**
     * Build the complete project file with the given user-sample section content. All sections up
     * to the USDa are taken from this project's (patched) data; the USDa section is replaced and
     * its TOC size updated. The USDa payload must start with the <i>SMPh</i> header.
     *
     * @param usdaPayload The new USDa payload (without the 16 byte section block header)
     * @return The project file bytes
     */
    public byte [] buildWithUserSampleData (final byte [] usdaPayload)
    {
        final int usdaOffset = this.getSectionOffset ("USDa");
        final int usdaSize = 16 + usdaPayload.length;

        final byte [] result = new byte [usdaOffset + usdaSize];
        System.arraycopy (this.data, 0, result, 0, usdaOffset);

        // The section block header: a single opaque record spanning the payload.
        ZenCoreUtil.writeUnsigned32 (result, usdaOffset, 1, false);
        ZenCoreUtil.writeUnsigned32 (result, usdaOffset + 4, usdaPayload.length, false);
        ZenCoreUtil.writeUnsigned32 (result, usdaOffset + 8, 16, false);
        ZenCoreUtil.writeUnsigned32 (result, usdaOffset + 12, 0, false);
        System.arraycopy (usdaPayload, 0, result, usdaOffset + 16, usdaPayload.length);

        // Update the USDa size in the TOC (the last of the 7 entries).
        ZenCoreUtil.writeUnsigned32 (result, TOC_OFFSET + (NUM_SECTIONS - 1) * TOC_ENTRY_SIZE + 12, usdaSize, false);
        return result;
    }


    private static byte [] loadTemplate ()
    {
        try (final InputStream in = new GZIPInputStream (MC707Project.class.getResourceAsStream ("mpj_template.bin.gz")))
        {
            final ByteArrayOutputStream out = new ByteArrayOutputStream ();
            in.transferTo (out);
            return out.toByteArray ();
        }
        catch (final IOException | NullPointerException ex)
        {
            throw new IllegalStateException ("Missing MC-707 init-project template resource.", ex);
        }
    }
}
