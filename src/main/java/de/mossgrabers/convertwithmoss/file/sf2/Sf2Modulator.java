// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.util.HashMap;
import java.util.Map;


/**
 * A SF2 modulator.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Modulator
{
    /** The ID for a Velocity modulator. */
    public static final Integer               MODULATOR_VELOCITY   = Integer.valueOf (2);
    /** The ID for a Pitch Bend modulator. */
    public static final Integer               MODULATOR_PITCH_BEND = Integer.valueOf (14);

    private static final Map<Integer, String> MODULATOR_NAMES      = new HashMap<> ();
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

    private final int controllerSource;
    private final int destinationGenerator;
    private final int modAmount;
    private final int amountSourceOperand;
    private final int transformOperand;


    /**
     * Constructor.
     *
     * @param sourceModulator The ID of the source modulator
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
        this.controllerSource = sourceModulator & 0x7F;
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
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();

        sb.append ("           - Modulator: " + getModulatorName (this.controllerSource));
        sb.append ("               - Destination Generator: " + Generator.getGeneratorName (this.destinationGenerator) + " : " + this.modAmount + "\n");
        sb.append ("               - Amount Source Operand: " + getModulatorName (this.amountSourceOperand) + "\n");

        return sb.toString ();
    }


    private static String getModulatorName (final int modulatorID)
    {
        return MODULATOR_NAMES.getOrDefault (Integer.valueOf (modulatorID), "Unknown");
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
