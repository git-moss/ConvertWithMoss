// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.polyend;

/**
 * Converts values between the Polyend Tracker instrument format and the model representation.
 *
 * @author Jürgen Moßgraber
 */
public final class PolyendTrackerValueConverter
{
    /** The raw volume value which represents unity gain (0 dB). */
    private static final double VOLUME_UNITY     = 50.0;
    /** The raw panning value which represents the center position. */
    private static final double PANNING_CENTER   = 50.0;
    /** The maximum resonance value stored in the file (representing the model resonance of 1). */
    private static final double RESONANCE_MAX    = 4.3;
    /** The lowest filter cutoff frequency in Hertz. */
    private static final double FILTER_MIN_HERTZ = 20.0;
    /** The highest filter cutoff frequency in Hertz. */
    private static final double FILTER_MAX_HERTZ = 20000.0;


    /**
     * Constructor.
     */
    private PolyendTrackerValueConverter ()
    {
        // Helper class
    }


    /**
     * Convert a normalized point (0 to 65535, proportional to the sample length) into a frame
     * position.
     *
     * @param point The normalized point
     * @param totalFrames The number of frames of the sample
     * @return The frame position (clamped to [0, totalFrames])
     */
    public static int normalizedToFrame (final int point, final int totalFrames)
    {
        if (totalFrames <= 0)
            return 0;
        final long frame = Math.round (point / (double) PolyendTrackerConstants.NORMALIZED_MAX * totalFrames);
        return (int) Math.clamp (frame, 0, totalFrames);
    }


    /**
     * Convert a frame position into a normalized point (0 to 65535, proportional to the sample
     * length).
     *
     * @param frame The frame position
     * @param totalFrames The number of frames of the sample
     * @return The normalized point (clamped to [0, 65535])
     */
    public static int frameToNormalized (final int frame, final int totalFrames)
    {
        if (totalFrames <= 0)
            return 0;
        final long point = Math.round (frame / (double) totalFrames * PolyendTrackerConstants.NORMALIZED_MAX);
        return (int) Math.clamp (point, 0, PolyendTrackerConstants.NORMALIZED_MAX);
    }


    /**
     * Convert the raw volume (0 to 100, 50 = unity gain) into a gain in decibels.
     *
     * @param rawVolume The raw volume
     * @return The gain in decibels
     */
    public static double rawVolumeToGain (final int rawVolume)
    {
        final double linear = Math.clamp (rawVolume, 0, 100) / VOLUME_UNITY;
        if (linear <= 0)
            return 0;
        return 20.0 * Math.log10 (linear);
    }


    /**
     * Convert a gain in decibels into the raw volume (0 to 100, 50 = unity gain).
     *
     * @param gainDecibels The gain in decibels
     * @return The raw volume
     */
    public static int gainToRawVolume (final double gainDecibels)
    {
        final double linear = Math.pow (10.0, gainDecibels / 20.0);
        return (int) Math.clamp (Math.round (linear * VOLUME_UNITY), 0, 100);
    }


    /**
     * Convert the raw panning (0 to 100, 50 = center) into the model panning (-1 to 1).
     *
     * @param rawPanning The raw panning
     * @return The model panning
     */
    public static double rawPanningToModel (final int rawPanning)
    {
        return Math.clamp ((Math.clamp (rawPanning, 0, 100) - PANNING_CENTER) / VOLUME_UNITY, -1.0, 1.0);
    }


    /**
     * Convert the model panning (-1 to 1) into the raw panning (0 to 100, 50 = center).
     *
     * @param panning The model panning
     * @return The raw panning
     */
    public static int modelPanningToRaw (final double panning)
    {
        return (int) Math.clamp (Math.round (Math.clamp (panning, -1.0, 1.0) * VOLUME_UNITY + PANNING_CENTER), 0, 100);
    }


    /**
     * Convert the normalized filter cutoff (0 to 1) into a frequency in Hertz.
     *
     * @param cutoff The normalized cutoff
     * @return The frequency in Hertz
     */
    public static double normalizedCutoffToHertz (final double cutoff)
    {
        return FILTER_MIN_HERTZ * Math.pow (FILTER_MAX_HERTZ / FILTER_MIN_HERTZ, Math.clamp (cutoff, 0.0, 1.0));
    }


    /**
     * Convert a frequency in Hertz into the normalized filter cutoff (0 to 1).
     *
     * @param hertz The frequency in Hertz
     * @return The normalized cutoff
     */
    public static double hertzToNormalizedCutoff (final double hertz)
    {
        if (hertz <= FILTER_MIN_HERTZ)
            return 0.0;
        return Math.clamp (Math.log (hertz / FILTER_MIN_HERTZ) / Math.log (FILTER_MAX_HERTZ / FILTER_MIN_HERTZ), 0.0, 1.0);
    }


    /**
     * Convert the raw resonance (0 to 4.3) into the model resonance (0 to 1).
     *
     * @param rawResonance The raw resonance
     * @return The model resonance
     */
    public static double rawResonanceToModel (final double rawResonance)
    {
        return Math.clamp (rawResonance / RESONANCE_MAX, 0.0, 1.0);
    }


    /**
     * Convert the model resonance (0 to 1) into the raw resonance (0 to 4.3).
     *
     * @param resonance The model resonance
     * @return The raw resonance
     */
    public static double modelResonanceToRaw (final double resonance)
    {
        return Math.clamp (resonance, 0.0, 1.0) * RESONANCE_MAX;
    }


    /**
     * Combine the coarse tune (semitones) and fine tune (cents) into a tuning value in semitones.
     *
     * @param tune The coarse tune in semitones
     * @param finetune The fine tune in cents
     * @return The tuning in semitones
     */
    public static double toTuning (final int tune, final int finetune)
    {
        return tune + finetune / 100.0;
    }


    /**
     * Get the coarse tune (semitones) part of a tuning value.
     *
     * @param tuning The tuning in semitones
     * @return The coarse tune in semitones (clamped to [-24, 24])
     */
    public static int tuningToTune (final double tuning)
    {
        return (int) Math.clamp (Math.round (tuning), -24, 24);
    }


    /**
     * Get the fine tune (cents) part of a tuning value.
     *
     * @param tuning The tuning in semitones
     * @return The fine tune in cents (clamped to [-100, 100])
     */
    public static int tuningToFinetune (final double tuning)
    {
        final double fraction = tuning - tuningToTune (tuning);
        return (int) Math.clamp (Math.round (fraction * 100.0), -100, 100);
    }


    /**
     * Convert milliseconds into seconds.
     *
     * @param milliseconds The milliseconds
     * @return The seconds
     */
    public static double millisecondsToSeconds (final int milliseconds)
    {
        return milliseconds / 1000.0;
    }


    /**
     * Convert seconds into milliseconds (clamped to the unsigned 16-bit range).
     *
     * @param seconds The seconds (a negative value is treated as 0)
     * @return The milliseconds
     */
    public static int secondsToMilliseconds (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return (int) Math.clamp (Math.round (seconds * 1000.0), 0, PolyendTrackerConstants.NORMALIZED_MAX);
    }
}
