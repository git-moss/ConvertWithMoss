// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import java.util.Optional;
import java.util.function.IntUnaryOperator;

import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;


/**
 * The control group of a parameter of a Kurzweil program layer (the pitch, the DSP functions and
 * the amplifier) which the K2000/K2500/K2600 and the PC3 family share: source 1 modulates the
 * parameter with a fixed depth, source 2 with a depth between the minimum and maximum depth which
 * the depth control source selects, e.g. the modulation wheel. The 6 bytes are stored in the order
 * source 1, depth, depth control, minimum depth, maximum depth and source 2. All depths are raw
 * bytes; their unit depends on the parameter.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilControl
{
    /** The control source code for 'always on'. */
    private static final int CONTROL_SOURCE_ON    = 0x7F;
    /** The control source code of the modulation wheel. */
    private static final int CONTROL_SOURCE_WHEEL = 1;
    /** The control source code of the first local LFO. */
    private static final int CONTROL_SOURCE_LFO1  = 114;
    /** The control source code of the second local LFO. */
    private static final int CONTROL_SOURCE_LFO2  = 116;


    /**
     * A modulation of a layer parameter by one of the local LFOs.
     *
     * @param lfo The LFO
     * @param depth The depth with the modulation wheel fully down (cents for the pitch and the
     *            cutoff, dB for the volume)
     * @param modWheelDepth The depth which the modulation wheel adds when fully up
     */
    public record LfoModulation (KurzweilLfo lfo, int depth, int modWheelDepth)
    {
        /**
         * Apply the modulation to a LFO modulator of the internal model. A unipolar LFO shape only
         * swings to one side of the center, which is approximated by half of the depth.
         *
         * @param modulator The LFO modulator to fill
         * @param maxDepth The depth in the unit of the modulation (cents or dB) which is a
         *            modulator depth of 1
         */
        public void toModulator (final ILfoModulator modulator, final double maxDepth)
        {
            final double scale = (this.lfo.isUnipolar () ? 0.5 : 1.0) / maxDepth;
            modulator.setDepth (Math.clamp (this.depth * scale, -1, 1));
            modulator.getDepthModWheelModulator ().setDepth (Math.clamp (this.modWheelDepth * scale, -1, 1));
            this.lfo.toLfo (modulator.getSource ());
        }
    }


    private int source1      = 0;
    private int depth        = 0;
    private int depthControl = 0;
    private int minDepth     = 0;
    private int maxDepth     = 0;
    private int source2      = 0;


    /**
     * Constructor for an empty control group.
     */
    public KurzweilControl ()
    {
        // Intentionally empty
    }


    /**
     * Constructor. Reads the 6 bytes of the control group from the segment data.
     *
     * @param data The segment data
     * @param offset The offset of the control group in the data
     */
    public KurzweilControl (final byte [] data, final int offset)
    {
        this.source1 = data[offset] & 0xFF;
        this.depth = data[offset + 1];
        this.depthControl = data[offset + 2] & 0xFF;
        this.minDepth = data[offset + 3];
        this.maxDepth = data[offset + 4];
        this.source2 = data[offset + 5] & 0xFF;
    }


    /**
     * Write the 6 bytes of the control group to the segment data.
     *
     * @param data The segment data
     * @param offset The offset of the control group in the data
     */
    public void write (final byte [] data, final int offset)
    {
        data[offset] = (byte) this.source1;
        data[offset + 1] = (byte) this.depth;
        data[offset + 2] = (byte) this.depthControl;
        data[offset + 3] = (byte) this.minDepth;
        data[offset + 4] = (byte) this.maxDepth;
        data[offset + 5] = (byte) this.source2;
    }


    /**
     * Check if source 2 is set to one of the local LFOs, which is how a LFO modulation is written.
     *
     * @return True if source 2 is a local LFO
     */
    public boolean hasLfoSource2 ()
    {
        return getLfoIndex (this.source2) >= 0;
    }


    /**
     * Get the modulation of the parameter by one of the local LFOs. Source 1 adds its fixed depth.
     * Source 2 adds the minimum depth if the depth control is off and the maximum depth if it is
     * on; with the modulation wheel as the depth control the minimum depth applies to the wheel
     * fully down and the maximum depth to the wheel fully up. All other depth controls are assumed
     * to be at their minimum. If both sources use different LFOs only the first one is used.
     *
     * @param lfos The 2 local LFOs of the layer, null if a LFO is not present
     * @param decodeDepth Decodes a depth byte into the unit of the parameter
     * @return The modulation, if any
     */
    public Optional<LfoModulation> getLfoModulation (final KurzweilLfo [] lfos, final IntUnaryOperator decodeDepth)
    {
        final int lfoIndex1 = getLfoIndex (this.source1);
        final int lfoIndex2 = getLfoIndex (this.source2);
        final int lfoIndex = lfoIndex1 >= 0 ? lfoIndex1 : lfoIndex2;
        if (lfoIndex < 0)
            return Optional.empty ();
        final KurzweilLfo lfo = lfos[lfoIndex];
        if (lfo == null || lfo.isOff ())
            return Optional.empty ();

        int modulationDepth = lfoIndex1 >= 0 ? decodeDepth.applyAsInt (this.depth) : 0;
        int modWheelDepth = 0;
        if (lfoIndex2 == lfoIndex)
        {
            final int minimumDepth = decodeDepth.applyAsInt (this.minDepth);
            final int maximumDepth = decodeDepth.applyAsInt (this.maxDepth);
            if (this.depthControl == CONTROL_SOURCE_WHEEL)
            {
                modulationDepth += minimumDepth;
                modWheelDepth = maximumDepth - minimumDepth;
            }
            else
                modulationDepth += this.depthControl == CONTROL_SOURCE_ON ? maximumDepth : minimumDepth;
        }

        if (modulationDepth == 0 && modWheelDepth == 0)
            return Optional.empty ();
        return Optional.of (new LfoModulation (lfo, modulationDepth, modWheelDepth));
    }


    /**
     * Modulate the parameter by a LFO. Assigns the LFO to the first free local LFO or to the one
     * with identical settings. Since there are only 2 local LFOs, a third different LFO uses the
     * second one. The LFO is set as source 2 with the modulation wheel as the depth control which
     * covers a fixed depth (minimum = maximum depth) as well. Source 1 is not changed.
     *
     * @param lfos The 2 local LFOs of the layer, null if a LFO is not used yet
     * @param lfo The LFO
     * @param modulationDepth The depth with the modulation wheel fully down
     * @param modWheelDepth The depth which the modulation wheel adds when fully up
     * @param encodeDepth Encodes a depth in the unit of the parameter into a depth byte
     */
    public void setLfoModulation (final KurzweilLfo [] lfos, final KurzweilLfo lfo, final int modulationDepth, final int modWheelDepth, final IntUnaryOperator encodeDepth)
    {
        int lfoIndex = lfos.length - 1;
        for (int i = 0; i < lfos.length; i++)
        {
            if (lfos[i] == null)
                lfos[i] = lfo;
            if (lfos[i].equals (lfo))
            {
                lfoIndex = i;
                break;
            }
        }

        this.source2 = lfoIndex == 0 ? CONTROL_SOURCE_LFO1 : CONTROL_SOURCE_LFO2;
        this.depthControl = CONTROL_SOURCE_WHEEL;
        this.minDepth = encodeDepth.applyAsInt (modulationDepth);
        this.maxDepth = encodeDepth.applyAsInt (modulationDepth + modWheelDepth);
    }


    /**
     * Get the index of the local LFO which a control source references.
     *
     * @param controlSource The control source
     * @return The index (0 = LFO1, 1 = LFO2) or -1 if it is no local LFO
     */
    private static int getLfoIndex (final int controlSource)
    {
        return switch (controlSource)
        {
            case CONTROL_SOURCE_LFO1 -> 0;
            case CONTROL_SOURCE_LFO2 -> 1;
            default -> -1;
        };
    }
}
