// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.dls;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * The data of an articulator chunk which specifies parameters which modify the play-back of a
 * sample used in DLS instruments.
 *
 * @author Jürgen Moßgraber
 */
public class DlsArticulation
{
    // =========================================================================
    // DLS Level 2 Sources, Controls, Destinations, and Transforms
    // (Tables 9 & 10 — full superset of Level 1 Table 8)
    // =========================================================================

    // -------------------------------------------------------------------------
    // Modulator Sources
    // -------------------------------------------------------------------------

    /** No Source */
    public static final int CONN_SRC_NONE                  = 0x0000;

    /** Low Frequency Oscillator */
    public static final int CONN_SRC_LFO                   = 0x0001;

    /** Note-On Velocity */
    public static final int CONN_SRC_KEYONVELOCITY         = 0x0002;

    /** Note Number */
    public static final int CONN_SRC_KEYNUMBER             = 0x0003;

    /** Envelope Generator 1 */
    public static final int CONN_SRC_EG1                   = 0x0004;

    /** Envelope Generator 2 */
    public static final int CONN_SRC_EG2                   = 0x0005;

    /** Pitch Wheel */
    public static final int CONN_SRC_PITCHWHEEL            = 0x0006;

    /** Polyphonic Pressure - only level 2. */
    public static final int CONN_SRC_POLYPRESSURE          = 0x0007;

    /** Channel Pressure - only level 2. */
    public static final int CONN_SRC_CHANNELPRESSURE       = 0x0008;

    /** Vibrato LFO - only level 2. */
    public static final int CONN_SRC_VIBRATO               = 0x0009;

    /** MIDI Mono Pressure - only level 2. */
    public static final int CONN_SRC_MONOPRESSURE          = 0x000A;

    // -------------------------------------------------------------------------
    // MIDI Controller Sources
    // -------------------------------------------------------------------------

    /** Modulation */
    public static final int CONN_SRC_CC1                   = 0x0081;

    /** Channel Volume */
    public static final int CONN_SRC_CC7                   = 0x0087;

    /** Pan */
    public static final int CONN_SRC_CC10                  = 0x008A;

    /** Expression */
    public static final int CONN_SRC_CC11                  = 0x008B;

    /** Reverb Send - only level 2. */
    public static final int CONN_SRC_CC91                  = 0x00DB;

    /** Chorus Send - only level 2. */
    public static final int CONN_SRC_CC93                  = 0x00DD;

    // -------------------------------------------------------------------------
    // Registered Parameter Numbers
    // -------------------------------------------------------------------------

    /** RPN0 - Pitch Bend Range */
    public static final int CONN_SRC_RPN0                  = 0x0100;

    /** RPN1 - Fine Tune */
    public static final int CONN_SRC_RPN1                  = 0x0101;

    /** RPN2 - Coarse Tune */
    public static final int CONN_SRC_RPN2                  = 0x0102;

    // -------------------------------------------------------------------------
    // Generic Destinations
    // -------------------------------------------------------------------------

    /** No Destination */
    public static final int CONN_DST_NONE                  = 0x0000;

    /** Gain (same as CONN_DST_ATTENUATION in Level 1) */
    public static final int CONN_DST_GAIN                  = 0x0001;

    /** Reserved - DO NOT USE */
    public static final int CONN_DST_RESERVED              = 0x0002;

    /** Pitch */
    public static final int CONN_DST_PITCH                 = 0x0003;

    /** Pan */
    public static final int CONN_DST_PAN                   = 0x0004;

    /** Key Number Generator - only level 2. */
    public static final int CONN_DST_KEYNUMBER             = 0x0005;

    // -------------------------------------------------------------------------
    // Channel Output Destinations - only level 2.
    // -------------------------------------------------------------------------

    /** Left Channel Send - only level 2. */
    public static final int CONN_DST_LEFT                  = 0x0010;

    /** Right Channel Send - only level 2. */
    public static final int CONN_DST_RIGHT                 = 0x0011;

    /** Center Channel Send - only level 2. */
    public static final int CONN_DST_CENTER                = 0x0012;

    /** LFE Channel Send - only level 2. */
    public static final int CONN_DST_LFE_CHANNEL           = 0x0013;

    /** Left Rear Channel Send - only level 2. */
    public static final int CONN_DST_LEFTREAR              = 0x0014;

    /** Right Rear Channel Send - only level 2. */
    public static final int CONN_DST_RIGHTREAR             = 0x0015;

    /** Chorus Send - only level 2. */
    public static final int CONN_DST_CHORUS                = 0x0080;

    /** Reverb Send - only level 2. */
    public static final int CONN_DST_REVERB                = 0x0081;

    // -------------------------------------------------------------------------
    // Modulator LFO Destinations
    // -------------------------------------------------------------------------

    /** LFO Frequency */
    public static final int CONN_DST_LFO_FREQUENCY         = 0x0104;

    /** LFO Start Delay Time */
    public static final int CONN_DST_LFO_STARTDELAY        = 0x0105;

    // -------------------------------------------------------------------------
    // Vibrato LFO Destinations - only level 2.
    // -------------------------------------------------------------------------

    /** Vibrato Frequency - only level 2. */
    public static final int CONN_DST_VIB_FREQUENCY         = 0x0114;

    /** Vibrato Start Delay - only level 2. */
    public static final int CONN_DST_VIB_STARTDELAY        = 0x0115;

    // -------------------------------------------------------------------------
    // EG1 Destinations
    // -------------------------------------------------------------------------

    /** EG1 Attack Time */
    public static final int CONN_DST_EG1_ATTACKTIME        = 0x0206;

    /** EG1 Decay Time */
    public static final int CONN_DST_EG1_DECAYTIME         = 0x0207;

    /** EG1 Reserved - DO NOT USE */
    public static final int CONN_DST_EG1_RESERVED          = 0x0208;

    /** EG1 Release Time */
    public static final int CONN_DST_EG1_RELEASETIME       = 0x0209;

    /** EG1 Sustain Level */
    public static final int CONN_DST_EG1_SUSTAINLEVEL      = 0x020A;

    /** EG1 Delay Time - only level 2. */
    public static final int CONN_DST_EG1_DELAYTIME         = 0x020B;

    /** EG1 Hold Time - only level 2. */
    public static final int CONN_DST_EG1_HOLDTIME          = 0x020C;

    /** EG1 Shutdown Time - only level 2. */
    public static final int CONN_DST_EG1_SHUTDOWNTIME      = 0x020D;

    // -------------------------------------------------------------------------
    // EG2 Destinations
    // -------------------------------------------------------------------------

    /** EG2 Attack Time */
    public static final int CONN_DST_EG2_ATTACKTIME        = 0x030A;

    /** EG2 Decay Time */
    public static final int CONN_DST_EG2_DECAYTIME         = 0x030B;

    /** EG2 Reserved - DO NOT USE */
    public static final int CONN_DST_EG2_RESERVED          = 0x030C;

    /** EG2 Release Time */
    public static final int CONN_DST_EG2_RELEASETIME       = 0x030D;

    /** EG2 Sustain Level */
    public static final int CONN_DST_EG2_SUSTAINLEVEL      = 0x030E;

    /** EG2 Delay Time - only level 2. */
    public static final int CONN_DST_EG2_DELAYTIME         = 0x030F;

    /** EG2 Hold Time - only level 2. */
    public static final int CONN_DST_EG2_HOLDTIME          = 0x0310;

    // -------------------------------------------------------------------------
    // Filter Destinations - only level 2.
    // -------------------------------------------------------------------------

    /** Filter Cutoff Frequency - only level 2. */
    public static final int CONN_DST_FILTER_CUTOFF         = 0x0500;

    /** Filter Resonance - only level 2. */
    public static final int CONN_DST_FILTER_Q              = 0x0501;

    // -------------------------------------------------------------------------
    // Transforms
    // -------------------------------------------------------------------------

    /** No Transform */
    public static final int CONN_TRN_NONE                  = 0x0000;

    /** Concave Transform */
    public static final int CONN_TRN_CONCAVE               = 0x0001;

    /** Convex Transform - only level 2. */
    public static final int CONN_TRN_CONVEX                = 0x0002;

    /** Switch Transform - only level 2. */
    public static final int CONN_TRN_SWITCH                = 0x0003;

    // -------------------------------------------------------------------------
    // Output Transform — bits 0-3
    // -------------------------------------------------------------------------
    /** Mask for the Output Transform field (bits 0-3). */
    public static final int TRANSFORM_OUTPUT_MASK          = 0x000F;
    /** Bit shift for the Output Transform field. */
    public static final int TRANSFORM_OUTPUT_SHIFT         = 0;

    // -------------------------------------------------------------------------
    // usControl Transform — bits 4-7
    // -------------------------------------------------------------------------
    /** Mask for the usControl Transform field (bits 4-7). */
    public static final int TRANSFORM_CTRL_TRANSFORM_MASK  = 0x00F0;
    /** Bit shift for the usControl Transform field. */
    public static final int TRANSFORM_CTRL_TRANSFORM_SHIFT = 4;

    // -------------------------------------------------------------------------
    // usControl flags — bits 8-9
    // -------------------------------------------------------------------------
    /** Mask for the usControl Bipolar flag (bit 8). */
    public static final int TRANSFORM_CTRL_BIPOLAR_MASK    = 0x0100;
    /** Bit shift for the usControl Bipolar flag. */
    public static final int TRANSFORM_CTRL_BIPOLAR_SHIFT   = 8;

    /** Mask for the usControl Invert flag (bit 9). */
    public static final int TRANSFORM_CTRL_INVERT_MASK     = 0x0200;
    /** Bit shift for the usControl Invert flag. */
    public static final int TRANSFORM_CTRL_INVERT_SHIFT    = 9;

    // -------------------------------------------------------------------------
    // usSource Transform — bits 10-13
    // -------------------------------------------------------------------------
    /** Mask for the usSource Transform field (bits 10-13). */
    public static final int TRANSFORM_SRC_TRANSFORM_MASK   = 0x3C00;
    /** Bit shift for the usSource Transform field. */
    public static final int TRANSFORM_SRC_TRANSFORM_SHIFT  = 10;

    // -------------------------------------------------------------------------
    // usSource flags — bits 14-15
    // -------------------------------------------------------------------------
    /** Mask for the usSource Bipolar flag (bit 14). */
    public static final int TRANSFORM_SRC_BIPOLAR_MASK     = 0x4000;
    /** Bit shift for the usSource Bipolar flag. */
    public static final int TRANSFORM_SRC_BIPOLAR_SHIFT    = 14;

    /** Mask for the usSource Invert flag (bit 15). */
    public static final int TRANSFORM_SRC_INVERT_MASK      = 0x8000;
    /** Bit shift for the usSource Invert flag. */
    public static final int TRANSFORM_SRC_INVERT_SHIFT     = 15;

    private final int       source;
    @SuppressWarnings("unused")
    private final int       control;
    private final int       destination;
    private final int       transform;
    private final int       scale;

    // -------------------------------------------------------------------------
    // Fields extracted from usTransform (Figure 13, p.49)
    // -------------------------------------------------------------------------

    /** Output transform type (bits 0-3). Always 0 in valid DLS Level 2 files. */
    public final int        outputTransform;

    /** Transform type applied to the usControl input (bits 4-7). */
    public final int        controlTransform;

    /** Whether the usControl input is bipolar (bit 8). */
    public final boolean    controlBipolar;

    /** Whether the usControl input is inverted (bit 9). */
    public final boolean    controlInvert;

    /** Transform type applied to the usSource input (bits 10-13). */
    public final int        sourceTransform;

    /** Whether the usSource input is bipolar (bit 14). */
    public final boolean    sourceBipolar;

    /** Whether the usSource input is inverted (bit 15). */
    public final boolean    sourceInvert;


    /**
     * Constructor.
     *
     * @param articulationChunk The articulation chunk from which to initialize the region
     * @param dataOffset The offset from which to start reading
     * @throws ParseException Could not read the data
     */
    public DlsArticulation (final RawRIFFChunk articulationChunk, final int dataOffset) throws ParseException
    {
        final int fourCC = articulationChunk.getId ().getFourCC ();
        if (fourCC != DlsRiffChunkId.ART1_ID.getFourCC () && fourCC != DlsRiffChunkId.ART2_ID.getFourCC ())
            throw new ParseException ("Given chunk is not a ART1/ART2 chunk.");

        this.source = articulationChunk.getTwoBytesAsUnsignedInt (dataOffset);
        this.control = articulationChunk.getTwoBytesAsUnsignedInt (dataOffset + 2);
        this.destination = articulationChunk.getTwoBytesAsUnsignedInt (dataOffset + 4);
        this.transform = articulationChunk.getTwoBytesAsUnsignedInt (dataOffset + 6);
        this.scale = articulationChunk.getFourBytesAsUnsignedInt (dataOffset + 8);

        // Decompose usTransform into individual fields
        this.outputTransform = (this.transform & TRANSFORM_OUTPUT_MASK) >>> TRANSFORM_OUTPUT_SHIFT;
        this.controlTransform = (this.transform & TRANSFORM_CTRL_TRANSFORM_MASK) >>> TRANSFORM_CTRL_TRANSFORM_SHIFT;
        this.controlBipolar = (this.transform & TRANSFORM_CTRL_BIPOLAR_MASK) != 0;
        this.controlInvert = (this.transform & TRANSFORM_CTRL_INVERT_MASK) != 0;
        this.sourceTransform = (this.transform & TRANSFORM_SRC_TRANSFORM_MASK) >>> TRANSFORM_SRC_TRANSFORM_SHIFT;
        this.sourceBipolar = (this.transform & TRANSFORM_SRC_BIPOLAR_MASK) != 0;
        this.sourceInvert = (this.transform & TRANSFORM_SRC_INVERT_MASK) != 0;
    }


    /**
     * Get the source.
     *
     * @return The source, see the CONN_SRC_* constants
     */
    public int getSource ()
    {
        return this.source;
    }


    /**
     * Get the controller which modulates the connection, e.g. the modulation wheel. A value of
     * CONN_SRC_NONE means that the connection is always fully applied.
     *
     * @return The control, see the CONN_SRC_* constants
     */
    public int getControl ()
    {
        return this.control;
    }


    /**
     * Get the destination.
     *
     * @return The source, see the CONN_DST_* constants
     */
    public int getDestination ()
    {
        return this.destination;
    }


    /**
     * Get the scale value.
     *
     * @return The scale value
     */
    public int getScale ()
    {
        return this.scale;
    }


    /**
     * Converts a DLS Absolute Time value to seconds. As per Section 1.14.3: Absolute Time = 1200 *
     * log2(time-secs) * 65536. The special value 0x80000000 represents absolute zero.
     *
     * @param value The raw 32-bit signed DLS time value
     * @return The time in seconds
     */
    public static double absoluteTimeToSeconds (final int value)
    {
        return value == 0x80000000 ? 0.0 : Math.pow (2.0, value / (1200.0 * 65536.0));
    }


    /**
     * Convert a relative pitch connection value to cent. The value is stored as a 32-bit fixed point
     * number with 65536 representing one cent.
     *
     * @param value The raw 32-bit relative pitch value
     * @return The pitch in cent
     */
    public static double relativePitchToCents (final int value)
    {
        return value / 65536.0;
    }


    /**
     * Convert an absolute pitch connection value to a frequency in Hertz. The value is stored as a
     * 32-bit fixed point number with 65536 representing one cent, where 6900 cent equal 440 Hertz
     * (which puts 0 cent at about 8.176 Hertz, the same reference as SoundFont).
     *
     * @param value The raw 32-bit absolute pitch value
     * @return The frequency in Hertz
     */
    public static double absolutePitchToHertz (final int value)
    {
        return 440.0 * Math.pow (2.0, (value / 65536.0 - 6900.0) / 1200.0);
    }


    /**
     * Normalizes a DLS EG Sustain Level lScale value to the range 0.0..1.0.
     *
     * Per Section 1.7.2.7: Sustain is defined as a percentage of the envelope peak in 0.1%
     * increments (1 unit = 0.1%). Range: 0% (lScale = 0) to 100% (lScale = 1000).
     *
     * @param value The raw sustain level value from the ConnectionBlock
     * @return The normalized sustain level in the range [0.0, 1.0]
     */
    public static double normalizeSustainLevel (final int value)
    {
        return Math.clamp (value / 1000.0, 0.0, 1.0);
    }
}
