// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.sf2.GeneratorHierarchy;


/**
 * A SF2 modulator.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Modulator
{
    /** The ID for a Velocity modulator. */
    public static final Integer               MODULATOR_VELOCITY        = Integer.valueOf (2);
    /** The ID for a Pitch Bend modulator. */
    public static final Integer               MODULATOR_PITCH_BEND      = Integer.valueOf (14);

    /**
     * Source operand flag: the direction of the source is negative, it runs from its maximum to its
     * minimum - e.g. velocity 127 gives 0 and velocity 0 gives the full amount, as in the default
     * modulators of the SoundFont specification. Without the flag the direction is positive.
     */
    public static final int                   SOURCE_DIRECTION_NEGATIVE = 0x0100;
    /** Source operand flag: the source is bipolar (-1..1) instead of unipolar (0..1). */
    public static final int                   SOURCE_POLARITY_BIPOLAR   = 0x0200;
    /** Source operand type: linear. */
    public static final int                   SOURCE_TYPE_LINEAR        = 0x0000;
    /** Source operand type: concave. */
    public static final int                   SOURCE_TYPE_CONCAVE       = 0x0400;
    /** Source operand type: convex. */
    public static final int                   SOURCE_TYPE_CONVEX        = 0x0800;
    /** Source operand type: switch. */
    public static final int                   SOURCE_TYPE_SWITCH        = 0x0C00;
    /** The mask of the type bits of the source operand. */
    public static final int                   SOURCE_TYPE_MASK          = 0xFC00;
    /** The mask of the controller index of the source operand. */
    public static final int                   SOURCE_INDEX_MASK         = 0x007F;

    private static final Map<Integer, String> MODULATOR_NAMES           = new HashMap<> ();
    static
    {
        /**
         * No controller is to be used. The output of this controller module should be treated as if
         * its value were set to ‘1’. It should not be a means to turn off a modulator.
         */
        MODULATOR_NAMES.put (Integer.valueOf (0), "No Controller");
        /**
         * The controller source to be used is the velocity value which is sent from the MIDI
         * note-on command which generated the given sound.
         */
        MODULATOR_NAMES.put (MODULATOR_VELOCITY, "Note-On Velocity");
        /**
         * The controller source to be used is the key number value which was sent from the MIDI
         * note-on command which generated the given sound.
         */
        MODULATOR_NAMES.put (Integer.valueOf (3), "Note-On Key Number");
        /**
         * The controller source to be used is the poly-pressure amount that is sent from the MIDI
         * poly-pressure command.
         */
        MODULATOR_NAMES.put (Integer.valueOf (10), "Poly Pressure");
        /**
         * The controller source to be used is the channel pressure amount that is sent from the
         * MIDI channel-pressure command.
         */
        MODULATOR_NAMES.put (Integer.valueOf (13), "Channel Pressure");
        /**
         * The controller source to be used is the pitch wheel amount which is sent from the MIDI
         * pitch wheel command.
         */
        MODULATOR_NAMES.put (MODULATOR_PITCH_BEND, "Pitch Wheel");
        /**
         * The controller source to be used is the pitch wheel sensitivity amount which is sent from
         * the MIDI RPN 0 pitch wheel sensitivity command.
         */
        MODULATOR_NAMES.put (Integer.valueOf (16), "Pitch Wheel Sensitivity");
        /**
         * The controller source is the output of another modulator. This is NOT SUPPORTED as an
         * Amount Source.
         */
        MODULATOR_NAMES.put (Integer.valueOf (127), "Link");
    }

    private final int sourceOperand;
    private final int controllerSource;
    private final int destinationGenerator;
    private final int modAmount;
    private final int amountSourceOperand;
    private final int transformOperand;


    /**
     * Constructor.
     *
     * @param sourceModulator The source operand: the ID of the source modulator in the lower 7
     *            bits, combined with the direction, polarity and type flags (see the SOURCE_*
     *            constants)
     * @param destinationGenerator The destination of the modulator
     * @param modAmount A signed value indicating the degree to which the source modulates the
     *            destination
     * @param amountSourceOperand Indicates the degree to which the source modulates the destination
     *            is to be controlled by the specified modulation source
     * @param transformOperand Indicates that a transform of the specified type will be applied to
     *            the modulation source before application to the modulator
     */
    public Sf2Modulator (final int sourceModulator, final int destinationGenerator, final int modAmount, final int amountSourceOperand, final int transformOperand)
    {
        this.sourceOperand = sourceModulator;
        this.controllerSource = sourceModulator & SOURCE_INDEX_MASK;
        this.destinationGenerator = destinationGenerator;
        this.modAmount = modAmount;
        this.amountSourceOperand = amountSourceOperand;
        this.transformOperand = transformOperand;
    }


    /**
     * Get the ID of the controller source.
     *
     * @return The controller source
     */
    public int getControllerSource ()
    {
        return this.controllerSource;
    }


    /**
     * Get the full source operand: the ID of the controller source combined with the direction,
     * polarity and type flags.
     *
     * @return The source operand
     */
    public int getSourceOperand ()
    {
        return this.sourceOperand;
    }


    /**
     * Is the direction of the source negative? The source then runs from its maximum to its
     * minimum, e.g. velocity 127 gives 0 and velocity 0 gives the full amount.
     *
     * @return True if the direction is negative
     */
    public boolean isNegativeDirection ()
    {
        return (this.sourceOperand & SOURCE_DIRECTION_NEGATIVE) != 0;
    }


    /**
     * Is the source bipolar (-1..1) instead of unipolar (0..1)?
     *
     * @return True if bipolar
     */
    public boolean isBipolar ()
    {
        return (this.sourceOperand & SOURCE_POLARITY_BIPOLAR) != 0;
    }


    /**
     * Get the type (the curve) of the source.
     *
     * @return One of the SOURCE_TYPE_* constants
     */
    public int getSourceType ()
    {
        return this.sourceOperand & SOURCE_TYPE_MASK;
    }


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();

        sb.append ("           - Modulator: " + getModulatorName (this.controllerSource) + " (" + getSourceTypeName (this.getSourceType ()) + ", " + (this.isBipolar () ? "bipolar" : "unipolar") + ", " + (this.isNegativeDirection () ? "negative" : "positive") + ")\n");
        sb.append ("               - Destination Generator: " + GeneratorHierarchy.getGeneratorName (this.destinationGenerator) + " : " + this.modAmount + "\n");
        sb.append ("               - Amount Source Operand: " + getModulatorName (this.amountSourceOperand) + "\n");

        return sb.toString ();
    }


    private static String getModulatorName (final int modulatorID)
    {
        return MODULATOR_NAMES.getOrDefault (Integer.valueOf (modulatorID), "Unknown");
    }


    private static String getSourceTypeName (final int sourceType)
    {
        return switch (sourceType)
        {
            case SOURCE_TYPE_LINEAR -> "linear";
            case SOURCE_TYPE_CONCAVE -> "concave";
            case SOURCE_TYPE_CONVEX -> "convex";
            case SOURCE_TYPE_SWITCH -> "switch";
            default -> "unknown type";
        };
    }


    /**
     * Get the destination generator to be modulated.
     *
     * @return The destination generator
     */
    public int getDestinationGenerator ()
    {
        return this.destinationGenerator;
    }


    /**
     * Get the modulation amount.
     *
     * @return The modulation amount
     */
    public int getModulationAmount ()
    {
        return this.modAmount;
    }


    /**
     * Get the amount source operand.
     *
     * @return The value
     */
    public int getAmountSourceOperand ()
    {
        return this.amountSourceOperand;
    }


    /**
     * Get the transformation operand.
     *
     * @return The transform operand
     */
    public int getTransformOperand ()
    {
        return this.transformOperand;
    }
}
