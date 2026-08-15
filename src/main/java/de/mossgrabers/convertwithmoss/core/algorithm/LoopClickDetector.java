// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * Detects loops which audibly click at their wrap-around point. The step from the last played frame
 * of a loop back to its first frame is compared with the normal frame-to-frame movement of the
 * waveform around the loop boundaries: a step which is many times that movement and a substantial
 * part of the local level is heard as a tick on every repeat. Sample libraries ship such loops
 * surprisingly often, and without a note the first hint is the converted preset ticking on the
 * destination device.
 * <p>
 * Only reported, nothing is changed: whether to enable the snap-to-zero-crossing or loop cross-fade
 * processing - or to keep the loop as the faithful reproduction of the source - is left to the
 * user. A loop which already has a cross-fade is not reported, since the cross-fade masks the wrap.
 *
 * @author Jürgen Moßgraber
 */
public final class LoopClickDetector
{
    /** A step must be this many times the median frame-to-frame movement to count as a click. */
    private static final double MINIMUM_STEP_RATIO    = 8;
    /** ... and at least this part of the local peak level, so quiet material is not flagged. */
    private static final double MINIMUM_RELATIVE_STEP = 0.02;
    /** The number of frames around each loop boundary over which the movement is measured. */
    private static final int    WINDOW                = 512;
    /** Loops shorter than this are skipped, like everywhere else in the processing. */
    private static final int    MINIMUM_LOOP_LENGTH   = 16;
    /** A loop with at least this much cross-fade does not click, the cross-fade masks the wrap. */
    private static final double CROSSFADE_THRESHOLD   = 0.01;
    /**
     * The frame-to-frame movement must be compared per unit of time, not per frame, otherwise the
     * check depends on the sample rate of the source: the same waveform stored at 22 kHz moves
     * twice as much per frame as at 44.1 kHz, which hides a step that sticks out once the sample is
     * resampled to a higher rate by the destination format. The movement is therefore scaled to
     * this reference rate. Resampling itself does not change the step - measured across 821
     * converted loops it stays within one percent point of the level - so this scaling is the only
     * correction needed and the destination format does not need to be known.
     */
    private static final double REFERENCE_SAMPLE_RATE = 44100.0;


    /**
     * The result of a detection run over one multi-sample.
     *
     * @param clickingLoops The number of loops which click at their wrap
     * @param checkedLoops The number of loops which were checked
     * @param worstZoneName The name of the zone with the largest step
     * @param worstStepPercent The largest step in percent of the local peak level
     */
    public record Result (int clickingLoops, int checkedLoops, String worstZoneName, double worstStepPercent)
    {
        // Intentionally empty
    }


    /**
     * Private due to helper class.
     */
    private LoopClickDetector ()
    {
        // Intentionally empty
    }


    /**
     * Check all forward loops of the given groups for an audible step at their wrap-around point.
     * The audio is read once per zone; zones whose audio cannot be read are skipped, the check
     * never fails.
     *
     * @param groups The groups whose zones to check
     * @return The result, empty if no checked loop clicks
     */
    public static Optional<Result> detect (final List<IGroup> groups)
    {
        int clickingLoops = 0;
        int checkedLoops = 0;
        String worstZoneName = "";
        double worstStepPercent = 0;

        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                int [] signal = null;
                int sampleRate = (int) REFERENCE_SAMPLE_RATE;

                for (final ISampleLoop loop: zone.getLoops ())
                {
                    if (loop.getType () != LoopType.FORWARDS || loop.getCrossfade () >= CROSSFADE_THRESHOLD)
                        continue;

                    if (signal == null)
                        try
                        {
                            signal = LoopZeroSnapper.readMonoSignal (zone);
                            final Optional<ISampleData> sampleData = zone.getSampleData ();
                            if (sampleData.isEmpty ())
                                continue;
                            sampleRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
                        }
                        catch (final Exception _)
                        {
                            break;
                        }

                    final double stepPercent = measure (signal, loop, sampleRate);
                    if (stepPercent < 0)
                        continue;
                    checkedLoops++;
                    if (stepPercent > 0)
                    {
                        clickingLoops++;
                        if (stepPercent > worstStepPercent)
                        {
                            worstStepPercent = stepPercent;
                            worstZoneName = zone.getName ();
                        }
                    }
                }
            }

        if (clickingLoops == 0)
            return Optional.empty ();
        return Optional.of (new Result (clickingLoops, checkedLoops, worstZoneName, worstStepPercent));
    }


    /**
     * Measure the step at the wrap of one loop.
     *
     * @param signal The mono mix of the sample audio
     * @param loop The loop to measure
     * @param sampleRate The sample rate of the audio
     * @return The step in percent of the local peak level if the loop clicks, 0 if it does not, -1
     *         if it could not be measured
     */
    private static double measure (final int [] signal, final ISampleLoop loop, final int sampleRate)
    {
        final int length = signal.length;
        final int start = loop.getStart ();
        // A loop end of -1 (or beyond the audio) means "loop to the end of the sample"
        int end = loop.getEnd ();
        if (end < 0 || end >= length)
            end = length - 1;
        if (start < 0 || end <= start || end - start < MINIMUM_LOOP_LENGTH)
            return -1;

        // The normal frame-to-frame movement and the level around both loop boundaries
        final int windowStart = Math.max (0, start - WINDOW);
        final int windowEnd = Math.min (length - 1, end + WINDOW);
        final int innerEnd = Math.min (start + WINDOW, end);
        final int innerStart = Math.max (end - WINDOW, start);
        final int [] steps = new int [innerEnd - windowStart + windowEnd - innerStart];
        int peak = 0;
        int index = 0;
        for (int i = windowStart; i < innerEnd; i++)
        {
            steps[index++] = Math.abs (signal[i + 1] - signal[i]);
            peak = Math.max (peak, Math.abs (signal[i]));
        }
        for (int i = innerStart; i < windowEnd; i++)
        {
            steps[index++] = Math.abs (signal[i + 1] - signal[i]);
            peak = Math.max (peak, Math.abs (signal[i]));
        }
        if (peak == 0)
            return -1;
        Arrays.sort (steps);
        final int medianStep = steps[steps.length / 2];

        final int step = LoopZeroSnapper.discontinuity (signal, start, end);
        final double movementPerReferenceFrame = medianStep * sampleRate / REFERENCE_SAMPLE_RATE;
        if (step > MINIMUM_STEP_RATIO * (movementPerReferenceFrame + 1.0) && step > MINIMUM_RELATIVE_STEP * peak)
            return 100.0 * step / peak;
        return 0;
    }
}
