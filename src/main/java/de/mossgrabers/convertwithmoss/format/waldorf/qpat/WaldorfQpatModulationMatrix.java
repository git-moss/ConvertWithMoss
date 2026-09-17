// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

/**
 * The sources and destinations of the modulation matrix of the Waldorf Quantum/Iridium which are
 * read and written, and how the device applies the amount of a slot to them.
 * <p>
 * The sources keep their indices in all format versions, the destinations do not: the list grew
 * with the firmware. The format version 14 added 'DF Tilt' in front of the destinations of the
 * filters and the version 15 the nine destinations of the Seeds oscillators, which moved e.g. the
 * 'VCA' from 107 to 108 and then to 117 - the indices of the version 15 are the ones of the Iridium
 * MK2 firmware 4.0.6, the older ones are taken from the hints of patches which the device wrote.
 * Therefore the device does not rely on the stored index when it loads a patch: for every
 * enumeration whose hint holds the name of one of its options it takes the index of that option and
 * only uses the stored value if the name is not found (Iridium MK2 firmware 4.0.6,
 * PatchLib::migrateIOAreaToStagingArea and Param::DiscreteNameToIndex).
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatModulationMatrix
{
    /** The number of slots of the modulation matrix. */
    public static final int    NUM_SLOTS                  = 40;
    /** The number of free envelopes. */
    public static final int    NUM_FREE_ENVELOPES         = 3;
    /** The number of low frequency oscillators. */
    public static final int    NUM_LFOS                   = 6;

    /** MatrixSrc: [4] "Free Env1" [5] "Free Env2" [6] "Free Env3". */
    public static final int    SOURCE_FIRST_FREE_ENVELOPE = 4;
    /** MatrixSrc: [7] "LFO 1" [8] "LFO 2" [9] "LFO 3" [10] "LFO 4" [11] "LFO 5" [12] "LFO 6". */
    public static final int    SOURCE_FIRST_LFO           = 7;

    /** MatrixDst: the pitch of all three oscillators at once. */
    public static final String DESTINATION_PITCH          = "Pitch";
    /** MatrixDst: the cutoff of the first filter. */
    public static final String DESTINATION_FILTER1_CUTOFF = "Filter1 Cutoff";
    /** MatrixDst: the amplifier of the voice. */
    public static final String DESTINATION_VCA            = "VCA";

    /**
     * The pitch in semi-tones which a pitch destination reaches with the full amount of a slot. The
     * device squares the amount of a pitch destination before it applies it, keeping its sign, and
     * multiplies the sum of all slots with 24 semi-tones (Iridium MK2 firmware 4.0.6: the
     * destinations 1-4 are registered with the squaring flag, ModMatrix::calc squares the amount
     * with fabs, the voice multiplies the sum with 24).
     */
    public static final double PITCH_RANGE                = 24.0;
    /**
     * The range in semi-tones by which the cutoff destination moves the cutoff with the full amount
     * of a slot. The device adds the sum of all slots to the cutoff in the units of Filter1CutOff,
     * whose range of 0 to 1 covers 11.25 octaves (Iridium MK2 firmware 4.0.6, the key tracking of
     * the filter is scaled into the same units with 1/135 per semi-tone).
     */
    public static final double CUTOFF_RANGE               = 135.0;


    /**
     * Private due to utility class.
     */
    private WaldorfQpatModulationMatrix ()
    {
        // Intentionally empty
    }


    /**
     * Get the name of the destination of the pitch of one oscillator.
     *
     * @param oscIndex The index of the oscillator [1..3]
     * @return The name, e.g. 'Osc1 Pitch'
     */
    public static String getOscillatorPitchDestination (final int oscIndex)
    {
        return "Osc" + oscIndex + " Pitch";
    }


    /**
     * Get the index of a destination in the list of a format version.
     *
     * @param name The name of the destination, one of 'Pitch', 'Osc1 Pitch', 'Osc2 Pitch', 'Osc3
     *            Pitch', 'Filter1 Cutoff' and 'VCA'
     * @param version The format version
     * @return The index or -1 if the index of the destination is not known for the version
     */
    public static int getDestinationIndex (final String name, final long version)
    {
        return switch (name)
        {
            case DESTINATION_PITCH -> 1;
            case "Osc1 Pitch" -> 2;
            case "Osc2 Pitch" -> 3;
            case "Osc3 Pitch" -> 4;
            case DESTINATION_FILTER1_CUTOFF -> getFilterCutoffIndex (version);
            case DESTINATION_VCA -> getVcaIndex (version);
            default -> -1;
        };
    }


    /**
     * Test if a destination parameter of a slot names the given destination. Like the device, the
     * name in the hint is used if the patch has one, otherwise the index of the format version.
     *
     * @param parameter The destination parameter of the slot, might be null
     * @param name The name of the destination, see {@link #getDestinationIndex(String, long)}
     * @param version The format version of the patch
     * @return True if the slot modulates the destination
     */
    public static boolean isDestination (final WaldorfQpatParameter parameter, final String name, final long version)
    {
        if (parameter == null)
            return false;
        if (!parameter.hint.isBlank ())
            return name.equals (parameter.hint);
        final int index = getDestinationIndex (name, version);
        return index >= 0 && Math.round (parameter.value) == index;
    }


    /**
     * Get the index of the source of a slot. The indices of the free envelopes and the low
     * frequency oscillators are the same in all format versions; like the device, the name in the
     * hint is used if the patch has one.
     *
     * @param parameter The source parameter of the slot
     * @return The index of the source
     */
    public static int getSourceIndex (final WaldorfQpatParameter parameter)
    {
        final String hint = parameter.hint;
        for (int i = 0; i < NUM_LFOS; i++)
            if (hint.equals ("LFO " + (i + 1)))
                return SOURCE_FIRST_LFO + i;
        for (int i = 0; i < NUM_FREE_ENVELOPES; i++)
            if (hint.equals ("Free Env" + (i + 1)))
                return SOURCE_FIRST_FREE_ENVELOPE + i;
        return Math.round (parameter.value);
    }


    /**
     * Convert the amount of a slot which modulates a pitch destination into semi-tones.
     *
     * @param amount The amount in the range of [-1..1]
     * @return The pitch in semi-tones which the full level of the source reaches
     */
    public static double convertPitchAmountToSemitones (final double amount)
    {
        final double clampedAmount = Math.clamp (amount, -1.0, 1.0);
        return Math.signum (clampedAmount) * clampedAmount * clampedAmount * PITCH_RANGE;
    }


    /**
     * Convert a pitch in semi-tones into the amount of a slot which modulates a pitch destination.
     * A pitch beyond the range of a slot is written at the end of the range.
     *
     * @param semitones The pitch in semi-tones which the full level of the source is to reach
     * @return The amount in the range of [-1..1]
     */
    public static double convertSemitonesToPitchAmount (final double semitones)
    {
        final double ratio = Math.clamp (semitones / PITCH_RANGE, -1.0, 1.0);
        return Math.signum (ratio) * Math.sqrt (Math.abs (ratio));
    }


    /**
     * Get the index of the destination 'Filter1 Cutoff' in a format version.
     *
     * @param version The format version
     * @return The index or -1 if it is not known
     */
    private static int getFilterCutoffIndex (final long version)
    {
        if (version >= 15)
            return 109;
        if (version == 14)
            return 100;
        return version >= 9 ? 99 : -1;
    }


    /**
     * Get the index of the destination 'VCA' in a format version.
     *
     * @param version The format version
     * @return The index or -1 if it is not known
     */
    private static int getVcaIndex (final long version)
    {
        if (version >= 15)
            return 117;
        if (version == 14)
            return 108;
        if (version >= 10)
            return 107;
        return version == 9 ? 105 : -1;
    }
}
