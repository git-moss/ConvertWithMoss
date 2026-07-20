// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.renoise;

/**
 * Converts values between the ConvertWithMoss model units and the Renoise instrument (XRNI) units.
 * <p>
 * Renoise note values are identical to MIDI note numbers but the playable range is 0..119 (10
 * octaves) instead of 0..127. Volume is a linear gain factor (1.0 = 0 dB). Panning is 0=left,
 * 0.5=center, 1=right. Tuning is split into an integer {@code Transpose} (semitones) and a
 * {@code Finetune} byte where the full +/-127 range corresponds to +/-1 semitone.
 * <p>
 * The envelope time and filter cutoff curves are Renoise-internal and not part of the published
 * schema; the mappings used here were calibrated against samples saved by Renoise 3.5 and are
 * documented inline.
 *
 * @author Jürgen Moßgraber
 */
public final class RenoiseValueConverter
{
    /** The highest playable Renoise note (B-9); Renoise uses a 0..119 keyboard. */
    public static final int     MAX_NOTE              = 119;

    /** The MuteGroupIndex which marks a sample as not being part of any mute group. */
    public static final int     MUTE_GROUP_NONE       = -1;
    /** The number of mute groups a Renoise sample can be assigned to. */
    public static final int     MUTE_GROUP_COUNT      = 15;

    /** Maximum sample volume in Renoise: +12 dB which equals a linear gain factor of 4.0. */
    private static final double MAX_VOLUME_LINEAR     = 4.0;

    /** A Finetune value of +/-127 equals +/-1 semitone. */
    private static final double FINETUNE_PER_SEMITONE = 127.0;

    // Envelope time mapping. Renoise stores attack/hold/decay/release as a normalized 0..1 control
    // value that maps exponentially to a time. The maximum (value = 1.0) is roughly 60 seconds.
    private static final double ENV_MAX_TIME_SECONDS  = 60.0;
    private static final double ENV_TIME_EXPONENT     = 3.0;

    /** The maximum value of a mixer modulation parameter (cutoff and resonance are 0..127). */
    public static final double  MIXER_PARAM_MAX       = 127.0;

    // Filter cutoff mapping. The 0..127 cutoff maps exponentially over the audible range.
    private static final double FILTER_MIN_HERTZ      = 20.0;
    private static final double FILTER_MAX_HERTZ      = 20000.0;


    /**
     * Private constructor for utility class.
     */
    private RenoiseValueConverter ()
    {
        // Intentionally empty
    }


    /**
     * Clamp a MIDI note to the Renoise playable range (0..119).
     *
     * @param note The MIDI note
     * @return The clamped note
     */
    public static int clampNote (final int note)
    {
        return Math.clamp (note, 0, MAX_NOTE);
    }


    /**
     * Convert a Renoise mute group index into the exclusive group of the model. All sounding notes
     * of a mute group are stopped when a new note of that mute group starts.
     *
     * @param muteGroupIndex The zero-based Renoise mute group index, -1 for no mute group
     * @return The exclusive group (1..15), 0 if the sample is not part of a mute group
     */
    public static int muteGroupToExclusiveGroup (final int muteGroupIndex)
    {
        if (muteGroupIndex < 0)
            return 0;
        return Math.clamp (muteGroupIndex + 1, 1, MUTE_GROUP_COUNT);
    }


    /**
     * Convert the exclusive group of the model into a Renoise mute group index.
     *
     * @param exclusiveGroup The exclusive group, 0 if the zone is not part of an exclusive group
     * @return The zero-based Renoise mute group index, -1 if the zone is not part of a mute group
     */
    public static int exclusiveGroupToMuteGroup (final int exclusiveGroup)
    {
        if (exclusiveGroup <= 0)
            return MUTE_GROUP_NONE;
        return Math.clamp (exclusiveGroup, 1, MUTE_GROUP_COUNT) - 1;
    }


    /**
     * Convert a gain in decibel to the Renoise linear volume factor.
     *
     * @param gainDB The gain in decibel
     * @return The linear volume factor (0 = silence, 1 = 0 dB), clamped to the Renoise maximum
     */
    public static double gainToVolume (final double gainDB)
    {
        if (gainDB == 0)
            return 1.0;
        return Math.clamp (Math.pow (10.0, gainDB / 20.0), 0, MAX_VOLUME_LINEAR);
    }


    /**
     * Convert a Renoise linear volume factor to a gain in decibel.
     *
     * @param volume The linear volume factor (1 = 0 dB)
     * @return The gain in decibel (0 if the volume is not positive)
     */
    public static double volumeToGain (final double volume)
    {
        if (volume <= 0)
            return 0;
        return 20.0 * Math.log10 (volume);
    }


    /**
     * Convert a panning value of the model ([-1..1]) to the Renoise panning ([0..1], 0.5 = center).
     *
     * @param panning The model panning [-1..1]
     * @return The Renoise panning [0..1]
     */
    public static double panningToRenoise (final double panning)
    {
        return Math.clamp (panning / 2.0 + 0.5, 0, 1);
    }


    /**
     * Convert a Renoise panning ([0..1], 0.5 = center) to the model panning ([-1..1]).
     *
     * @param panning The Renoise panning [0..1]
     * @return The model panning [-1..1]
     */
    public static double panningToModel (final double panning)
    {
        return Math.clamp ((panning - 0.5) * 2.0, -1, 1);
    }


    /**
     * Split a tuning given in semitones into the integer Renoise transpose part.
     *
     * @param semitones The tuning in semitones
     * @return The integer transpose (semitones)
     */
    public static int tuningToTranspose (final double semitones)
    {
        return (int) Math.round (semitones);
    }


    /**
     * Calculate the Renoise fine-tune part of a tuning given in semitones.
     *
     * @param semitones The tuning in semitones
     * @return The fine-tune (-127..127, where 127 = 1 semitone)
     */
    public static int tuningToFinetune (final double semitones)
    {
        final int transpose = tuningToTranspose (semitones);
        return Math.clamp ((int) Math.round ((semitones - transpose) * FINETUNE_PER_SEMITONE), -127, 127);
    }


    /**
     * Combine a Renoise transpose and fine-tune into a tuning in semitones.
     *
     * @param transpose The transpose in semitones
     * @param finetune The fine-tune (-127..127, where 127 = 1 semitone)
     * @return The tuning in semitones
     */
    public static double toTuning (final int transpose, final int finetune)
    {
        return transpose + finetune / FINETUNE_PER_SEMITONE;
    }


    /**
     * Convert an envelope time in seconds to the normalized Renoise envelope value.
     *
     * @param seconds The time in seconds (negative is treated as 0)
     * @return The normalized value [0..1]
     */
    public static double timeToRenoise (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        final double normalized = Math.pow (Math.min (seconds, ENV_MAX_TIME_SECONDS) / ENV_MAX_TIME_SECONDS, 1.0 / ENV_TIME_EXPONENT);
        return Math.clamp (normalized, 0, 1);
    }


    /**
     * Convert a normalized Renoise envelope value to a time in seconds.
     *
     * @param value The normalized value [0..1]
     * @return The time in seconds
     */
    public static double timeToSeconds (final double value)
    {
        if (value <= 0)
            return 0;
        return Math.pow (Math.clamp (value, 0, 1), ENV_TIME_EXPONENT) * ENV_MAX_TIME_SECONDS;
    }


    /**
     * Convert a filter cutoff in Hertz to the Renoise mixer cutoff value (0..127).
     *
     * @param hertz The cutoff frequency in Hertz
     * @return The mixer cutoff value [0..127]
     */
    public static double cutoffToMixer (final double hertz)
    {
        final double clamped = Math.clamp (hertz, FILTER_MIN_HERTZ, FILTER_MAX_HERTZ);
        final double normalized = Math.log (clamped / FILTER_MIN_HERTZ) / Math.log (FILTER_MAX_HERTZ / FILTER_MIN_HERTZ);
        return Math.clamp (normalized, 0, 1) * MIXER_PARAM_MAX;
    }


    /**
     * Convert a Renoise mixer cutoff value (0..127) to a frequency in Hertz.
     *
     * @param value The mixer cutoff value [0..127]
     * @return The cutoff frequency in Hertz
     */
    public static double mixerToCutoff (final double value)
    {
        final double normalized = Math.clamp (value / MIXER_PARAM_MAX, 0, 1);
        return FILTER_MIN_HERTZ * Math.pow (FILTER_MAX_HERTZ / FILTER_MIN_HERTZ, normalized);
    }


    /**
     * Convert a model resonance [0..1] to the Renoise mixer resonance value (0..127).
     *
     * @param resonance The model resonance [0..1]
     * @return The mixer resonance value [0..127]
     */
    public static double resonanceToMixer (final double resonance)
    {
        return Math.clamp (resonance, 0, 1) * MIXER_PARAM_MAX;
    }


    /**
     * Convert a Renoise mixer resonance value (0..127) to a model resonance [0..1].
     *
     * @param value The mixer resonance value [0..127]
     * @return The model resonance [0..1]
     */
    public static double mixerToResonance (final double value)
    {
        return Math.clamp (value / MIXER_PARAM_MAX, 0, 1);
    }
}
