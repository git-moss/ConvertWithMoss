// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.IOException;

import de.mossgrabers.tools.ui.Functions;


/**
 * A Kontakt 5+ external modulator like velocity or pitch-bend.
 *
 * @author Jürgen Moßgraber
 */
public class ExternalModulator
{
    /** Velocity as the source (older versions). */
    public static final int    SOURCE_VELOCITY      = 0x00;
    /** Pitch-bend as the source. */
    public static final int    SOURCE_PITCHBEND     = 0x01;
    /** MIDI CC as the source. */
    public static final int    SOURCE_MIDI_CC       = 0x04;
    /** Key position as the source. */
    public static final int    SOURCE_KEY_POSITION  = 0x05;
    /** Velocity as the source. */
    public static final int    SOURCE_VELOCITY2     = 0x06;
    /** Release trigger counter as the source. */
    public static final int    SOURCE_RELEASE_TRIG  = 0x08;

    /** The volume as the modulation target. */
    public static final String TARGET_VOLUME        = "volume";
    /** The pitch as the modulation target. */
    public static final String TARGET_PITCH         = "pitch";
    /** The cutoff of a filter as the modulation target. */
    public static final String TARGET_FILTER_CUTOFF = "filterCutoff";

    // Flag of a modulated parameter which inverts the modulation
    private static final int   FLAG_INVERT          = 0x02;
    // The number of steps of a modulation shaper table
    private static final int   SHAPER_TABLE_STEPS   = 128;
    private static final int   SHAPER_TYPE_TABLE    = 1;
    private static final int   SHAPER_TYPE_CURVE    = 2;
    // There are 0-2 unknown bytes after the modulator description (e.g. the filter cutoff
    // parameter has additional bytes) and 0-1 unknown bytes after the last parameter
    private static final int   MAX_UNKNOWN_BYTES    = 2;

    private String             targetName           = "";
    private long               sourceType           = -1;
    private String             sourceName           = "";
    private int                ccNumber             = -1;
    private float              intensity            = 1.0f;


    /**
     * Constructor.
     */
    public ExternalModulator ()
    {
        // Intentionally empty
    }


    /**
     * Get the name of the modulated parameter, e.g. 'volume', 'pitch' or 'filterCutoff'. If several
     * parameters are modulated, it is the first one.
     *
     * @return The name of the parameter
     */
    public String getTargetName ()
    {
        return this.targetName;
    }


    /**
     * Get the source type.
     *
     * @return The source type, see the SOURCE_* constants
     */
    public long getSourceType ()
    {
        return this.sourceType;
    }


    /**
     * Get the name of the source, e.g. 'VEL_VOLUME' or 'PB_PITCH'.
     *
     * @return The name
     */
    public String getSourceName ()
    {
        return this.sourceName;
    }


    /**
     * Get the number of the MIDI CC if the source is a MIDI CC.
     *
     * @return The MIDI CC number or -1
     */
    public int getCCNumber ()
    {
        return this.ccNumber;
    }


    /**
     * Get the modulation intensity.
     *
     * @return The intensity [-1..1], negative if inverted, needs to be rounded after the 2nd
     *         fraction!
     */
    public float getIntensity ()
    {
        return this.intensity;
    }


    /**
     * Read the external modulator data. The data starts with 3 bytes (version info?) followed by
     * the size of a block (32 bit) which contains: the number of modulated parameters (32 bit), for
     * each parameter: name, intensity (float), 0xFFFF, flags (byte), lag (16 bit), description, 0-2
     * unknown bytes and the modulation shaper. After the parameters: 0-1 unknown byte, the name of
     * the source, 1 (32 bit), the type of the source (32 bit) and the MIDI CC number (32 bit) if
     * the source is a MIDI CC.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void read (final KontaktPresetChunk chunk) throws IOException
    {
        final int id = chunk.getId ();
        if (id != KontaktPresetChunkID.PAR_EXTERNAL_MOD)
            throw new IOException (Functions.getMessage ("IDS_NKI_NOT_EXTERNAL_MODULATION"));

        final byte [] data = chunk.getPublicData ();
        if (data.length < 11)
            return;
        final long size = readUnsigned32 (data, 3);
        if (size < 4 || size > data.length - 7L)
            return;
        final byte [] block = new byte [(int) size];
        System.arraycopy (data, 7, block, 0, block.length);

        final long count = readUnsigned32 (block, 0);
        if (count > 0)
            this.readParameters (block, 4, (int) Math.min (count, Integer.MAX_VALUE), true);
    }


    /**
     * Reads the remaining modulated parameters and the source.
     *
     * @param data The data of the block
     * @param offset The offset where to start reading
     * @param remaining The number of parameters which still need to be read
     * @param isFirst True if it is the first parameter
     * @return True if the parameters and the source could be read
     */
    private boolean readParameters (final byte [] data, final int offset, final int remaining, final boolean isFirst)
    {
        if (remaining == 0)
            return this.readSource (data, offset) || this.readSource (data, offset + 1);

        final String name = readAscii (data, offset);
        if (name == null)
            return false;
        int pos = offset + 4 + name.length ();
        if (pos + 9 > data.length || readUnsigned16 (data, pos + 4) != 0xFFFF)
            return false;
        final float paramIntensity = Float.intBitsToFloat ((int) readUnsigned32 (data, pos));
        final int flags = data[pos + 6] & 0xFF;
        pos += 9;

        final String description = readAscii (data, pos);
        if (description == null)
            return false;
        pos += 4 + description.length ();
        for (int unknown = 0; unknown <= MAX_UNKNOWN_BYTES; unknown++)
        {
            final int next = skipModulationShaper (data, pos + unknown);
            if (next >= 0 && this.readParameters (data, next, remaining - 1, false))
            {
                if (isFirst)
                {
                    this.targetName = name;
                    this.intensity = (flags & FLAG_INVERT) > 0 ? -paramIntensity : paramIntensity;
                }
                return true;
            }
        }
        return false;
    }


    /**
     * Reads the name and type of the source.
     *
     * @param data The data of the block
     * @param offset The offset where the source starts
     * @return True if the source could be read
     */
    private boolean readSource (final byte [] data, final int offset)
    {
        final String name = readAscii (data, offset);
        if (name == null)
            return false;
        final int pos = offset + 4 + name.length ();
        if (pos + 8 > data.length || readUnsigned32 (data, pos) != 1)
            return false;
        this.sourceName = name;
        this.sourceType = readUnsigned32 (data, pos + 4);
        if (this.sourceType == SOURCE_MIDI_CC && pos + 12 <= data.length)
            this.ccNumber = (int) readUnsigned32 (data, pos + 8);
        return true;
    }


    /**
     * Skips the modulation shaper. It starts with the type of the shaper (0: none, 1: table, 2:
     * curve). The table and the curve continue with the active flag. The table consists of 128
     * steps. The curve contains the number of points and each point has three values: x-position,
     * y-position and slope. The shaper is currently not used.
     *
     * @param data The data
     * @param offset The offset where the shaper starts
     * @return The offset after the shaper or -1 if the data does not contain a shaper
     */
    static int skipModulationShaper (final byte [] data, final int offset)
    {
        if (offset >= data.length)
            return -1;
        switch (data[offset])
        {
            case 0:
                return offset + 1;
            case SHAPER_TYPE_TABLE:
                return offset + 2 + SHAPER_TABLE_STEPS * 4;
            case SHAPER_TYPE_CURVE:
                if (offset + 3 > data.length)
                    return -1;
                return offset + 3 + (data[offset + 2] & 0xFF) * 3 * 4;
            default:
                return -1;
        }
    }


    /**
     * Reads an ASCII text which is prefixed with its length (32 bit).
     *
     * @param data The data
     * @param offset The offset of the length
     * @return The text or null if the data does not contain a valid text at the offset
     */
    static String readAscii (final byte [] data, final int offset)
    {
        if (offset < 0 || offset + 4 > data.length)
            return null;
        final long length = readUnsigned32 (data, offset);
        if (length > data.length - offset - 4L)
            return null;
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < length; i++)
        {
            final int c = data[offset + 4 + i] & 0xFF;
            if (c < 0x20 || c > 0x7E)
                return null;
            sb.append ((char) c);
        }
        return sb.toString ();
    }


    static int readUnsigned16 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    static long readUnsigned32 (final byte [] data, final int offset)
    {
        return readUnsigned16 (data, offset) | (long) readUnsigned16 (data, offset + 2) << 16;
    }
}
