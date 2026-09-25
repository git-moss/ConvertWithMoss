// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * A loop which wraps in a steep part of the waveform, e.g. at the edge of a saw wave or in a burst
 * of high frequencies, steps as far without any click, since the waveform moves that much from one
 * frame to the next there anyway. Therefore the wrap must also break the curve of the waveform: its
 * second difference has to stand out from the audio around it, which is the measure with which the
 * snapping decides whether a loop clicks. A loop which the snapping leaves alone as clean is never
 * reported, and the loop cross-fade of the loops which still click is not set on it.
 * <p>
 * Only reported, nothing is changed: whether to enable the snap-to-zero-crossing or loop cross-fade
 * processing - or to keep the loop as the faithful reproduction of the source - is left to the
 * user. A loop which already has a cross-fade is not reported, since the cross-fade masks the wrap
 * - whatever its length, exactly like the snapping leaves it alone.
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
     * A loop which clicks.
     *
     * @param zone The zone of the loop
     * @param loop The loop
     * @param stepPercent The step at its wrap in percent of the local peak level
     */
    private record Click (ISampleZone zone, ISampleLoop loop, double stepPercent)
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
     * The audio is read once per distinct sample; zones whose audio cannot be read are skipped, the
     * check never fails.
     *
     * @param groups The groups whose zones to check
     * @return The result, empty if no checked loop clicks
     */
    public static Optional<Result> detect (final List<IGroup> groups)
    {
        final List<Click> clicks = new ArrayList<> ();
        final int checkedLoops = scan (groups, clicks);
        if (clicks.isEmpty ())
            return Optional.empty ();

        Click worst = clicks.get (0);
        for (final Click click: clicks)
            if (click.stepPercent () > worst.stepPercent ())
                worst = click;
        return Optional.of (new Result (clicks.size (), checkedLoops, worst.zone ().getName (), worst.stepPercent ()));
    }


    /**
     * Get the forward loops of the given groups which click at their wrap-around point, measured
     * like {@link #detect(List)} does.
     *
     * @param groups The groups whose zones to check
     * @return The loops which click, empty if none does
     */
    public static List<ISampleLoop> findClickingLoops (final List<IGroup> groups)
    {
        final List<Click> clicks = new ArrayList<> ();
        scan (groups, clicks);
        final List<ISampleLoop> loops = new ArrayList<> (clicks.size ());
        for (final Click click: clicks)
            loops.add (click.loop ());
        return loops;
    }


    /**
     * Measure the wrap of all forward loops without a cross-fade.
     *
     * @param groups The groups whose zones to check
     * @param clicks Where to add the loops which click
     * @return The number of loops which were checked
     */
    private static int scan (final List<IGroup> groups, final List<Click> clicks)
    {
        int checkedLoops = 0;
        for (final Map.Entry<ISampleData, List<ISampleZone>> entry: groupZonesBySampleData (groups).entrySet ())
        {
            final List<ISampleZone> zones = entry.getValue ();
            // Reading the audio means decoding the whole sample, therefore only touch it if there
            // is a loop to measure at all
            if (!hasLoopToCheck (zones))
                continue;

            final int [] [] channels;
            final int sampleRate;
            try
            {
                channels = LoopZeroSnapper.readChannels (zones.get (0));
                sampleRate = entry.getKey ().getAudioMetadata ().getSampleRate ();
            }
            catch (final Exception _)
            {
                continue;
            }
            if (channels.length == 0)
                continue;
            final int [] signal = LoopZeroSnapper.mixToMono (channels);

            for (final ISampleZone zone: zones)
                for (final ISampleLoop loop: zone.getLoops ())
                {
                    if (loop.getType () != LoopType.FORWARDS || LoopZeroSnapper.isSmoothedByCrossfade (loop))
                        continue;

                    final double stepPercent = measure (signal, loop.getStart (), loop.getEnd (), sampleRate);
                    if (stepPercent < 0)
                        continue;
                    checkedLoops++;
                    if (stepPercent > 0 && breaksCurve (channels, loop))
                        clicks.add (new Click (zone, loop, stepPercent));
                }
        }
        return checkedLoops;
    }


    private static Map<ISampleData, List<ISampleZone>> groupZonesBySampleData (final List<IGroup> groups)
    {
        final Map<ISampleData, List<ISampleZone>> zonesBySampleData = new LinkedHashMap<> ();
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isPresent ())
                    zonesBySampleData.computeIfAbsent (sampleData.get (), _ -> new ArrayList<> ()).add (zone);
            }
        return zonesBySampleData;
    }


    /**
     * Check if any of the given zones has a loop which needs to be measured.
     *
     * @param zones The zones to check
     * @return True if there is a forward loop without a cross-fade
     */
    private static boolean hasLoopToCheck (final List<ISampleZone> zones)
    {
        for (final ISampleZone zone: zones)
            for (final ISampleLoop loop: zone.getLoops ())
                if (loop.getType () == LoopType.FORWARDS && !LoopZeroSnapper.isSmoothedByCrossfade (loop))
                    return true;
        return false;
    }


    /**
     * Check if the wrap of a loop breaks the curve of the waveform, measured like the snapping does.
     *
     * @param channels The audio of all channels
     * @param loop The loop to check
     * @return True if it does
     */
    private static boolean breaksCurve (final int [] [] channels, final ISampleLoop loop)
    {
        final int length = channels[0].length;
        // A loop end of -1 (or beyond the audio) means "loop to the end of the sample"
        int end = loop.getEnd ();
        if (end < 0 || end >= length)
            end = length - 1;
        return LoopZeroSnapper.clicksAtWrap (channels, loop.getStart (), end);
    }


    /**
     * Measure the step at the wrap of one loop.
     *
     * @param signal The mono mix of the sample audio
     * @param start The loop start frame
     * @param loopEnd The loop end frame (inclusive)
     * @param sampleRate The sample rate of the audio
     * @return The step in percent of the local peak level if the loop clicks, 0 if it does not, -1
     *         if it could not be measured
     */
    static double measure (final int [] signal, final int start, final int loopEnd, final int sampleRate)
    {
        final int length = signal.length;
        // A loop end of -1 (or beyond the audio) means "loop to the end of the sample"
        int end = loopEnd;
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
        // In floating point: the product of the two integers exceeds the integer range for 24 and
        // 32 bit audio, e.g. a median step of 74,000 at 48 kHz
        final double movementPerReferenceFrame = (double) medianStep * sampleRate / REFERENCE_SAMPLE_RATE;
        if (step > MINIMUM_STEP_RATIO * (movementPerReferenceFrame + 1.0) && step > MINIMUM_RELATIVE_STEP * peak)
            return 100.0 * step / peak;
        return 0;
    }
}
