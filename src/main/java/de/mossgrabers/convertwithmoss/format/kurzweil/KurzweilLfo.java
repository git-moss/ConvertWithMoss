// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.util.Objects;

import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;


/**
 * A local LFO (LFO1 or LFO2) of a Kurzweil program layer. The K2000/K2500/K2600 and the PC3 family
 * store the same 5 values: the rate control source, the minimum and maximum rate (the rate control
 * moves the rate between them), the phase (0..3 = 0, 90, 180 and 270 degrees) and the shape. The
 * local LFOs restart with each note. The PC3 family numbers the shapes differently; since the
 * numbering is not known, all its shapes are read as a sine and a sine is written as the shape 0,
 * which is the shape of an LFO which was not edited. The PC3 family adds tempo synchronized minimum
 * rates above 24 Hertz.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilLfo
{
    /** The rate value of 2 Hertz, the default of the device. */
    private static final int DEFAULT_RATE      = 46;
    /** The highest rate value (24 Hertz). */
    private static final int MAX_RATE          = 184;

    /** The control source code for 'always on'. */
    private static final int CONTROL_SOURCE_ON = 0x7F;

    // The shapes, the following even code is the unipolar ('+') variant of each shape
    private static final int SHAPE_NONE        = 0;
    private static final int SHAPE_SINE        = 1;
    private static final int SHAPE_SQUARE      = 3;
    private static final int SHAPE_TRIANGLE    = 5;
    private static final int SHAPE_RISE_SAW    = 7;
    private static final int SHAPE_FALL_SAW    = 9;
    /**
     * The first of the step shapes: 3, 4, 5, 6, 7, 8, 10 and 12 steps on the even codes, each
     * followed by its unipolar ('+') variant on the odd code.
     */
    private static final int SHAPE_FIRST_STEP  = 20;

    private final boolean    isPC3;
    private int              rateControl       = 0;
    private int              minRate           = DEFAULT_RATE;
    private int              maxRate           = 0;
    private int              phase             = 0;
    private int              shape             = SHAPE_SINE;


    /**
     * Constructor for a sine LFO with 2 Hertz.
     *
     * @param isPC3 True for a LFO of the PC3 family, false for the K2000/K2500/K2600
     */
    public KurzweilLfo (final boolean isPC3)
    {
        this.isPC3 = isPC3;
        if (isPC3)
            this.shape = 0;
    }


    /**
     * Constructor. Decodes a LFO of the K2000/K2500/K2600 from the data of a program segment.
     *
     * @param data The data of the LFO segment
     */
    public KurzweilLfo (final byte [] data)
    {
        this (data, 1, false);
    }


    /**
     * Constructor. Decodes the LFO from its 5 values.
     *
     * @param data The data which contains the LFO
     * @param offset The offset of the rate control source, the first value
     * @param isPC3 True for a LFO of the PC3 family, false for the K2000/K2500/K2600
     */
    public KurzweilLfo (final byte [] data, final int offset, final boolean isPC3)
    {
        this.isPC3 = isPC3;
        this.rateControl = data[offset] & 0xFF;
        this.minRate = data[offset + 1] & 0xFF;
        this.maxRate = data[offset + 2] & 0xFF;
        this.phase = data[offset + 3] & 3;
        this.shape = data[offset + 4] & 0xFF;
    }


    /**
     * Encode the LFO into the data of a program segment of the K2000/K2500/K2600.
     *
     * @param data The data of the LFO segment (7 bytes)
     */
    public void write (final byte [] data)
    {
        this.write (data, 1);
    }


    /**
     * Encode the 5 values of the LFO. The rate is not controlled; therefore the minimum and maximum
     * rate are identical.
     *
     * @param data The data which contains the LFO
     * @param offset The offset of the rate control source, the first value
     */
    public void write (final byte [] data, final int offset)
    {
        data[offset] = (byte) this.rateControl;
        data[offset + 1] = (byte) this.minRate;
        data[offset + 2] = (byte) this.maxRate;
        data[offset + 3] = (byte) this.phase;
        data[offset + 4] = (byte) this.shape;
    }


    /**
     * Check if the LFO is switched off, which is the case if its shape is 'None' (K2000/K2500/K2600
     * only) or if it does not run at all (both rates are 0 Hertz).
     *
     * @return True if the LFO does not output anything
     */
    public boolean isOff ()
    {
        if (this.minRate == 0 && this.maxRate == 0)
            return true;
        return !this.isPC3 && this.shape == SHAPE_NONE;
    }


    /**
     * Check if the shape is a unipolar one ('+' shapes), which only swings to one side of the
     * center. Always false for the PC3 family since its shape numbering is not known.
     *
     * @return True if unipolar
     */
    public boolean isUnipolar ()
    {
        if (this.isPC3)
            return false;
        if (this.shape >= SHAPE_FIRST_STEP)
            return this.shape % 2 == 1;
        return this.shape != SHAPE_NONE && this.shape % 2 == 0;
    }


    /**
     * Fill the values of the LFO into a LFO of the internal model.
     *
     * @param lfo The LFO to fill
     */
    public void toLfo (final ILfo lfo)
    {
        lfo.setWaveform (this.getWaveform ());
        lfo.setRate (decodeRate (this.getRateValue ()));
        lfo.setStartPhase (this.phase / 4.0);
        lfo.setKeySync (true);
    }


    /**
     * Set the values of the LFO from a LFO of the internal model. The delay and fade-in are not
     * supported by the LFOs of the device.
     *
     * @param lfo The LFO
     */
    public void fromLfo (final ILfo lfo)
    {
        if (this.isPC3)
            this.shape = 0;
        else
            this.shape = switch (lfo.getWaveform ())
            {
                case TRIANGLE -> SHAPE_TRIANGLE;
                case SQUARE -> SHAPE_SQUARE;
                case SAWTOOTH_UP -> SHAPE_RISE_SAW;
                case SAWTOOTH_DOWN -> SHAPE_FALL_SAW;
                // There is no random shape, use a sine instead
                default -> SHAPE_SINE;
            };

        final double rate = lfo.getRate ();
        this.rateControl = 0;
        this.minRate = rate < 0 ? DEFAULT_RATE : encodeRate (rate);
        this.maxRate = this.minRate;

        final double startPhase = lfo.getStartPhase ();
        this.phase = startPhase < 0 ? 0 : (int) Math.round (startPhase * 4) % 4;
    }


    /**
     * Get the waveform of the shape. The steps shapes are staircases which are approximated by a
     * rising sawtooth. The shapes of the PC3 family are read as a sine.
     *
     * @return The waveform
     */
    private LfoWaveform getWaveform ()
    {
        if (this.isPC3)
            return LfoWaveform.SINE;
        if (this.shape >= SHAPE_FIRST_STEP)
            return LfoWaveform.SAWTOOTH_UP;
        // Map the unipolar '+' variant to its bipolar shape
        final int bipolarShape = this.shape % 2 == 0 ? this.shape - 1 : this.shape;
        return switch (bipolarShape)
        {
            case SHAPE_SQUARE -> LfoWaveform.SQUARE;
            case SHAPE_TRIANGLE -> LfoWaveform.TRIANGLE;
            case SHAPE_RISE_SAW -> LfoWaveform.SAWTOOTH_UP;
            case SHAPE_FALL_SAW -> LfoWaveform.SAWTOOTH_DOWN;
            default -> LfoWaveform.SINE;
        };
    }


    /**
     * Get the rate value which is active without any controller movement. Without a rate control
     * source the minimum rate applies, with the control source 'on' the maximum rate. For all other
     * control sources the minimum rate is used, unless it is 0 which would stop the LFO; in this
     * case the control source is expected to raise the rate up to the maximum rate.
     *
     * @return The rate value
     */
    private int getRateValue ()
    {
        if (this.rateControl == CONTROL_SOURCE_ON || this.rateControl != 0 && this.minRate == 0)
            return this.maxRate;
        return this.minRate;
    }


    /**
     * Decode a rate value. The values follow the editor steps of the device: 0.01 Hertz steps up to
     * 0.2 Hertz, 0.05 up to 1 Hertz, 0.1 up to 10 Hertz, 0.2 up to 20 Hertz and 0.5 up to 24 Hertz.
     * The values 185 to 188 are the tempo synchronized rates of the PC3 family.
     *
     * @param value The rate value in the range of [0..188]
     * @return The rate in Hertz
     */
    public static double decodeRate (final int value)
    {
        if (value > MAX_RATE)
        {
            // The tempo synchronized rates (1/16, 1/8 triplet, 1/8 and 1/4 note; the triplet is
            // assumed) converted assuming 120 BPM
            final double [] tempoRates =
            {
                8.0,
                6.0,
                4.0,
                2.0
            };
            return tempoRates[Math.min (value - MAX_RATE - 1, tempoRates.length - 1)];
        }

        final int v = Math.max (value, 0);
        if (v < 20)
            return v * 0.01;
        if (v <= 36)
            return 0.2 + (v - 20) * 0.05;
        if (v <= 126)
            return 1.0 + (v - 36) * 0.1;
        if (v <= 176)
            return 10.2 + (v - 127) * 0.2;
        return 20.5 + (v - 177) * 0.5;
    }


    /**
     * Encode a rate into the nearest rate value.
     *
     * @param rate The rate in Hertz
     * @return The rate value in the range of [0..184]
     */
    public static int encodeRate (final double rate)
    {
        int bestValue = 0;
        double bestDistance = Double.MAX_VALUE;
        // Only the rates in Hertz are written
        for (int value = 0; value <= MAX_RATE; value++)
        {
            final double distance = Math.abs (decodeRate (value) - rate);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestValue = value;
            }
        }
        return bestValue;
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return Objects.hash (Integer.valueOf (this.rateControl), Integer.valueOf (this.minRate), Integer.valueOf (this.maxRate), Integer.valueOf (this.phase), Integer.valueOf (this.shape));
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null || this.getClass () != obj.getClass ())
            return false;
        final KurzweilLfo other = (KurzweilLfo) obj;
        return this.rateControl == other.rateControl && this.minRate == other.minRate && this.maxRate == other.maxRate && this.phase == other.phase && this.shape == other.shape;
    }
}
