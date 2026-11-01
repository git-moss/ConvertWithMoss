// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 LFO chunk. There are 2 LFO chunks with a slightly different layout. Version 1 has
 * 12 bytes, version 2 has 14 bytes.
 *
 * @author Jürgen Moßgraber
 */
public class AkpLfo extends AbstractAkpChunk
{
    /** The length of the LFO structure (version 1). */
    private static final int LENGTH_LFO   = 12;
    /** The length of the LFO structure since version 2. */
    private static final int LENGTH_LFO_2 = 14;

    /** Waveform: SINE. */
    public static final int  SINE         = 0;
    /** Waveform: TRIANGLE. */
    public static final int  TRIANGLE     = 1;
    /** Waveform: SQUARE. */
    public static final int  SQUARE       = 2;
    /** Waveform: SQUARE+. */
    public static final int  SQUARE_POS   = 3;
    /** Waveform: SQUARE-. */
    public static final int  SQUARE_NEG   = 4;
    /** Waveform: SAW BI. */
    public static final int  SAW_BI       = 5;
    /** Waveform: SAW UP. */
    public static final int  SAW_UP       = 6;
    /** Waveform: SAW DOWN. */
    public static final int  SAW_DOWN     = 7;
    /** Waveform: RANDOM. */
    public static final int  RANDOM       = 8;

    private final boolean    isFirst;
    private int              waveform;
    private int              rate;
    private int              delay        = 0;
    private int              depth        = 0;
    private boolean          isSync       = false;
    private boolean          isRetrigger  = false;
    private int              modwheel;
    private int              aftertouch   = 0;
    private int              rateMod      = 0;
    private int              delayMod     = 0;
    private int              depthMod     = 0;


    /**
     * Constructor.
     *
     * @param isFirst True for LFO 1, false for LFO 2
     */
    public AkpLfo (final boolean isFirst)
    {
        super (AkpRiffChunkId.LFO_ID, LENGTH_LFO, 1);

        this.isFirst = isFirst;
        this.waveform = isFirst ? TRIANGLE : SINE;
        this.rate = isFirst ? 43 : 0;
        this.modwheel = isFirst ? 15 : 0;
    }


    /**
     * Is this LFO 1 or LFO 2?
     *
     * @return True if it is LFO 1
     */
    public boolean isFirst ()
    {
        return this.isFirst;
    }


    /**
     * Get the waveform.
     *
     * @return The waveform, see the constants SINE to RANDOM
     */
    public int getWaveform ()
    {
        return this.waveform;
    }


    /**
     * Set the waveform, see the waveform constants.
     *
     * @param waveform The waveform, see the waveform constants
     */
    public void setWaveform (final int waveform)
    {
        this.waveform = waveform;
    }


    /**
     * Get the rate.
     *
     * @return The rate in the range of [0..100]
     */
    public int getRate ()
    {
        return this.rate;
    }


    /**
     * Set the rate in the range of [0..100].
     *
     * @param rate The rate in the range of [0..100]
     */
    public void setRate (final int rate)
    {
        this.rate = rate;
    }


    /**
     * Get the delay.
     *
     * @return The delay in the range of [0..100]
     */
    public int getDelay ()
    {
        return this.delay;
    }


    /**
     * Set the delay in the range of [0..100].
     *
     * @param delay The delay in the range of [0..100]
     */
    public void setDelay (final int delay)
    {
        this.delay = delay;
    }


    /**
     * Get the depth.
     *
     * @return The depth in the range of [0..100]
     */
    public int getDepth ()
    {
        return this.depth;
    }


    /**
     * Set the depth in the range of [0..100].
     *
     * @param depth The depth in the range of [0..100]
     */
    public void setDepth (final int depth)
    {
        this.depth = depth;
    }


    /**
     * Is the LFO synchronized? Only available for LFO 1.
     *
     * @return True if synchronized
     */
    public boolean isSync ()
    {
        return this.isSync;
    }


    /**
     * Is the LFO re-triggered? Only available for LFO 2.
     *
     * @return True if re-triggered
     */
    public boolean isRetrigger ()
    {
        return this.isRetrigger;
    }


    /**
     * Set if the LFO is re-triggered by a note (only LFO 2).
     *
     * @param isRetrigger True if the LFO is re-triggered by a note (only LFO 2)
     */
    public void setRetrigger (final boolean isRetrigger)
    {
        this.isRetrigger = isRetrigger;
    }


    /**
     * Get the modulation wheel amount. Only available for LFO 1.
     *
     * @return The amount in the range of [0..100]
     */
    public int getModwheel ()
    {
        return this.modwheel;
    }


    /**
     * Get the aftertouch amount. Only available for LFO 1.
     *
     * @return The amount in the range of [0..100]
     */
    public int getAftertouch ()
    {
        return this.aftertouch;
    }


    /**
     * Get the rate modulation amount.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getRateMod ()
    {
        return this.rateMod;
    }


    /**
     * Get the delay modulation amount.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getDelayMod ()
    {
        return this.delayMod;
    }


    /**
     * Get the depth modulation amount.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getDepthMod ()
    {
        return this.depthMod;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.waveform = getUnsigned (data, 0x01);
        this.rate = getUnsigned (data, 0x02);
        this.delay = getUnsigned (data, 0x03);
        this.depth = getUnsigned (data, 0x04);

        if (this.isFirst)
        {
            this.isSync = getBoolean (data, 0x05);
            this.modwheel = getUnsigned (data, 0x07);
            this.aftertouch = getUnsigned (data, 0x08);
        }
        else
            this.isRetrigger = getBoolean (data, 0x06);

        this.rateMod = getSigned (data, 0x09);
        this.delayMod = getSigned (data, 0x0A);
        this.depthMod = getSigned (data, 0x0B);
    }


    /** {@inheritDoc} */
    @Override
    protected int getSize (final int targetVersion)
    {
        return targetVersion >= 2 ? LENGTH_LFO_2 : LENGTH_LFO;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Undocumented values which are always set
        if (this.isFirst)
            data[0x06] = 1;
        else
        {
            data[0x05] = 1;
            if (targetVersion >= 2)
                data[0x0D] = 5;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 0x01, this.waveform);
        setByte (data, 0x02, this.rate);
        setByte (data, 0x03, this.delay);
        setByte (data, 0x04, this.depth);
        if (this.isFirst)
        {
            setBoolean (data, 0x05, this.isSync);
            setByte (data, 0x07, this.modwheel);
            setByte (data, 0x08, this.aftertouch);
        }
        else
            setBoolean (data, 0x06, this.isRetrigger);
        setByte (data, 0x09, this.rateMod);
        setByte (data, 0x0A, this.delayMod);
        setByte (data, 0x0B, this.depthMod);
    }
}
