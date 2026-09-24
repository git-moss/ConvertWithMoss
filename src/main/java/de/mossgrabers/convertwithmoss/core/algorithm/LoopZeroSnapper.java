// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * Moves the start and end of forward loops to where the loop wraps around without a click. Sample
 * libraries sometimes ship loops whose boundaries do not meet, so the loop audibly clicks at the
 * wrap-around point on every repeat. Only the stored loop positions change, the audio is not
 * altered.
 * <p>
 * Most loops are fine, and moving the boundaries of a loop which already wraps cleanly can only
 * break it - e.g. a loop of whole periods of a tone becomes a loop which ends in the middle of one.
 * Therefore the wrap of a loop is measured first and only a loop which clicks is changed: the
 * second difference of the audio at the wrap is compared with the second differences of the audio
 * around it, in every channel. Two positions are tried for such a loop and the one with the smaller
 * click is taken, but only if it is clearly smaller than the click of the loop as it was:
 * <ul>
 * <li>the loop end which continues best into the loop start - the audio in front of the loop end is
 * compared with the audio in front of the loop start - and then the position of the loop with that
 * length which does so,</li>
 * <li>the nearest rising zero-crossings of the loop start and of the loop end.</li>
 * </ul>
 * A loop with a cross-fade is left alone, the cross-fade already smooths the wrap. Very short
 * (single-cycle) loops are left untouched so their pitch is not changed, and a boundary is moved by
 * at most an eighth of the loop length.
 *
 * @author Jürgen Moßgraber
 */
public final class LoopZeroSnapper
{
    /** Loops shorter than this many frames are not touched (single-cycle / pitch critical). */
    private static final int    MINIMUM_LOOP_LENGTH  = 4096;
    /** The maximum number of frames a loop boundary may be moved to find a zero-crossing. */
    private static final int    MAXIMUM_WINDOW       = 512;
    /** The maximum time in seconds a loop boundary may be moved to find a better match. */
    private static final double MATCH_WINDOW         = 0.02;
    /** The number of frames in front of the loop end and the loop start which are compared. */
    private static final int    MATCH_FRAMES         = 16;
    /** The number of frames on each side of the wrap which are the reference for its click. */
    private static final int    CLICK_CONTEXT        = 2048;
    /**
     * A loop whose wrap is at most this many times the median second difference around it does not
     * click, e.g. the loops of commercial SoundFonts measure 2.5 in the median.
     */
    private static final double CLEAN_WRAP           = 10.0;
    /** A new position must reduce the click at the wrap at least to this share. */
    private static final double REQUIRED_IMPROVEMENT = 0.7;


    /**
     * Constructor.
     */
    private LoopZeroSnapper ()
    {
        // Intentionally empty
    }


    /**
     * Move the forward loops of all given zones to where they wrap around without a click.
     *
     * @param sampleZones The zones whose loops to adjust
     * @return The number of loops which were adjusted
     */
    public static int snap (final List<ISampleZone> sampleZones)
    {
        int adjusted = 0;
        for (final ISampleZone zone: sampleZones)
        {
            final List<ISampleLoop> loops = zone.getLoops ();
            if (loops.isEmpty ())
                continue;

            final int [] [] channels;
            final int sampleRate;
            try
            {
                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isEmpty ())
                    continue;
                sampleRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
                channels = readChannels (zone);
            }
            catch (final IOException | UnsupportedAudioFileException _)
            {
                // The audio cannot be read - leave the loop unchanged
                continue;
            }
            if (channels.length == 0 || channels[0].length < MINIMUM_LOOP_LENGTH)
                continue;

            for (final ISampleLoop loop: loops)
                if (snapLoop (loop, channels, sampleRate))
                    adjusted++;
        }
        return adjusted;
    }


    /**
     * Move a single loop if it is a forward loop which clicks at its wrap and a position with a
     * clearly smaller click can be found nearby.
     *
     * @param loop The loop to adjust
     * @param channels The audio of all channels
     * @param sampleRate The sample rate of the audio
     * @return True if the loop was adjusted
     */
    private static boolean snapLoop (final ISampleLoop loop, final int [] [] channels, final int sampleRate)
    {
        if (loop.getType () != LoopType.FORWARDS || isSmoothedByCrossfade (loop))
            return false;

        final int length = channels[0].length;
        final int start = loop.getStart ();
        // A loop end of -1 (or beyond the audio) means "loop to the end of the sample"
        int end = loop.getEnd ();
        if (end < 0 || end >= length)
            end = length - 1;
        if (start < 0 || end - start + 1 < MINIMUM_LOOP_LENGTH)
            return false;

        final double click = measureWrap (channels, start, end);
        if (click <= CLEAN_WRAP)
            return false;

        int [] best = null;
        double bestClick = click * REQUIRED_IMPROVEMENT;

        final int matchWindow = (int) Math.min (Math.round (MATCH_WINDOW * sampleRate), (end - start + 1) / 8L);
        final Optional<int []> matched = findBestMatch (channels, start, end, matchWindow);
        if (matched.isPresent ())
        {
            final double matchedClick = measureWrap (channels, matched.get ()[0], matched.get ()[1]);
            if (matchedClick < bestClick)
            {
                best = matched.get ();
                bestClick = matchedClick;
            }
        }

        final int [] signal = mixToMono (channels);
        final int window = Math.min (MAXIMUM_WINDOW, (end - start) / 8);
        final int newStart = nearestRisingZeroCrossing (signal, start, window);
        final int newEndCrossing = nearestRisingZeroCrossing (signal, end, window);
        // The loop end is inclusive, therefore it is the last frame in front of the crossing
        if (newStart >= 0 && newEndCrossing > newStart + 1 && window > 0)
        {
            final int [] crossings =
            {
                newStart,
                newEndCrossing - 1
            };
            if (crossings[1] - crossings[0] + 1 >= MINIMUM_LOOP_LENGTH)
            {
                final double crossingsClick = measureWrap (channels, crossings[0], crossings[1]);
                if (crossingsClick < bestClick)
                    best = crossings;
            }
        }

        if (best == null || best[0] == start && best[1] == end)
            return false;
        loop.setStart (best[0]);
        loop.setEnd (best[1]);
        return true;
    }


    /**
     * Find the loop end which continues best into the loop start and then the position of a loop
     * with that length which does so. How well a loop end continues into a loop start is how
     * similar the audio in front of the loop end is to the audio in front of the loop start, since
     * that is the audio which the loop start follows in the source.
     *
     * @param channels The audio of all channels
     * @param start The loop start frame
     * @param end The loop end frame (inclusive)
     * @param window The maximum distance to search in both directions
     * @return The start and end of the loop, empty if there is no room to search
     */
    private static Optional<int []> findBestMatch (final int [] [] channels, final int start, final int end, final int window)
    {
        final int length = channels[0].length;
        if (window < 1 || start < MATCH_FRAMES)
            return Optional.empty ();

        int bestEnd = -1;
        double bestError = Double.MAX_VALUE;
        final int lastEnd = Math.min (length - 1, end + window);
        for (int candidate = Math.max (start + MINIMUM_LOOP_LENGTH - 1, end - window); candidate <= lastEnd; candidate++)
        {
            final double error = compareLeadIns (channels, start, candidate);
            if (error < bestError)
            {
                bestError = error;
                bestEnd = candidate;
            }
        }
        if (bestEnd < 0)
            return Optional.empty ();

        int bestShift = 0;
        bestError = Double.MAX_VALUE;
        for (int shift = -window; shift <= window; shift++)
        {
            if (start + shift < MATCH_FRAMES || bestEnd + shift >= length)
                continue;
            final double error = compareLeadIns (channels, start + shift, bestEnd + shift);
            if (error < bestError)
            {
                bestError = error;
                bestShift = shift;
            }
        }
        return Optional.of (new int []
        {
            start + bestShift,
            bestEnd + bestShift
        });
    }


    /**
     * Compare the audio in front of the loop end (including it) with the audio in front of the loop
     * start.
     *
     * @param channels The audio of all channels
     * @param start The loop start frame, at least MATCH_FRAMES
     * @param end The loop end frame (inclusive)
     * @return The squared difference relative to the energy of both, 0 for identical audio
     */
    private static double compareLeadIns (final int [] [] channels, final int start, final int end)
    {
        double difference = 0;
        double energy = 0;
        for (final int [] channel: channels)
            for (int i = 0; i < MATCH_FRAMES; i++)
            {
                final double beforeEnd = channel[end - i];
                final double beforeStart = channel[start - 1 - i];
                final double delta = beforeEnd - beforeStart;
                difference += delta * delta;
                energy += beforeEnd * beforeEnd + beforeStart * beforeStart;
            }
        return energy == 0 ? 0 : difference / energy;
    }


    /**
     * Measure the click at the wrap-around of a loop: the largest second difference of the played
     * audio right at the wrap, relative to the median second difference of the audio around it, in
     * the channel where this is the largest.
     *
     * @param channels The audio of all channels
     * @param start The loop start frame
     * @param end The loop end frame (inclusive)
     * @return The relative size of the click, around 1 to 3 for a loop which wraps seamlessly
     */
    static double measureWrap (final int [] [] channels, final int start, final int end)
    {
        double result = 0;
        for (final int [] channel: channels)
        {
            final int length = channel.length;
            // The played audio: the frames in front of and including the loop end, followed by
            // the frames from the loop start on
            final int from = Math.max (0, end - CLICK_CONTEXT);
            final int beforeWrap = end - from + 1;
            final int afterWrap = Math.min (CLICK_CONTEXT, length - start);
            final int count = beforeWrap + afterWrap;
            if (count < 8)
                continue;
            final double [] played = new double [count];
            for (int i = 0; i < beforeWrap; i++)
                played[i] = channel[from + i];
            for (int i = 0; i < afterWrap; i++)
                played[beforeWrap + i] = channel[start + i];

            final double [] secondDifferences = new double [count - 2];
            for (int i = 0; i < secondDifferences.length; i++)
                secondDifferences[i] = Math.abs (played[i + 2] - 2 * played[i + 1] + played[i]);

            // The loop end is at index beforeWrap - 1, the loop start follows it
            final int wrap = beforeWrap - 1;
            double peak = 0;
            for (int i = Math.max (0, wrap - 3); i < Math.min (secondDifferences.length, wrap + 3); i++)
                peak = Math.max (peak, secondDifferences[i]);

            final double [] sorted = secondDifferences.clone ();
            Arrays.sort (sorted);
            double median = sorted[sorted.length / 2];
            if (median == 0)
                median = 1;
            result = Math.max (result, peak / median);
        }
        return result;
    }


    /**
     * The size of the jump at the loop wrap-around - the absolute difference between the last
     * played frame of the loop and its first frame. The loop end is inclusive, so it is the last
     * frame which is played before the loop jumps back to its start.
     *
     * @param signal The mono mix of the sample audio
     * @param start The loop start frame
     * @param end The loop end frame (inclusive)
     * @return The absolute sample-value difference at the wrap
     */
    static int discontinuity (final int [] signal, final int start, final int end)
    {
        final int last = Math.clamp (end, 0, signal.length - 1);
        final int first = Math.clamp (start, 0, signal.length - 1);
        return Math.abs (signal[last] - signal[first]);
    }


    /**
     * Check if the wrap of a loop is smoothed by a cross-fade. Such a loop is neither moved nor
     * reported as clicking by the {@link LoopClickDetector}, so both always agree about a loop.
     *
     * @param loop The loop to check
     * @return True if the loop has a cross-fade
     */
    static boolean isSmoothedByCrossfade (final ISampleLoop loop)
    {
        return loop.getCrossfade () > 0;
    }


    /**
     * Find the frame of the rising zero-crossing (a non-positive sample followed by a positive one)
     * which is closest to the given position, within the given window.
     *
     * @param signal The mono mix of the sample audio
     * @param position The position to search around
     * @param window The maximum distance to search in both directions
     * @return The frame index of the crossing or -1 if none was found
     */
    private static int nearestRisingZeroCrossing (final int [] signal, final int position, final int window)
    {
        for (int distance = 0; distance <= window; distance++)
        {
            final int after = position + distance;
            if (after > 0 && after < signal.length && signal[after - 1] <= 0 && signal[after] > 0)
                return after;
            final int before = position - distance;
            if (before > 0 && before < signal.length && signal[before - 1] <= 0 && signal[before] > 0)
                return before;
        }
        return -1;
    }


    /**
     * Decode the sample audio of a zone into a mono (channel sum) integer signal.
     *
     * @param zone The zone
     * @return The mono signal (one integer per frame), empty if the bit depth is unsupported
     * @throws IOException Could not read the audio
     * @throws UnsupportedAudioFileException The audio format is not supported
     */
    public static int [] readMonoSignal (final ISampleZone zone) throws IOException, UnsupportedAudioFileException
    {
        final int [] [] channels = readChannels (zone);
        return channels.length == 0 ? new int [0] : mixToMono (channels);
    }


    /**
     * Mix all channels into one.
     *
     * @param channels The audio of all channels
     * @return The mono signal, the average of the channels
     */
    private static int [] mixToMono (final int [] [] channels)
    {
        final int numberOfFrames = channels[0].length;
        final int [] signal = new int [numberOfFrames];
        for (int frame = 0; frame < numberOfFrames; frame++)
        {
            long sum = 0;
            for (final int [] channel: channels)
                sum += channel[frame];
            signal[frame] = (int) (sum / channels.length);
        }
        return signal;
    }


    /**
     * Decode the sample audio of a zone into one integer signal per channel.
     *
     * @param zone The zone
     * @return The signals (one array per channel, one integer per frame), empty if the bit depth is
     *         unsupported
     * @throws IOException Could not read the audio
     * @throws UnsupportedAudioFileException The audio format is not supported
     */
    private static int [] [] readChannels (final ISampleZone zone) throws IOException, UnsupportedAudioFileException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            throw new IOException ("Empty sample data in zone: " + zone.getName ());
        sampleData.get ().writeSample (out);
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (new ByteArrayInputStream (out.toByteArray ())))
        {
            final AudioFormat format = audioInputStream.getFormat ();
            final int numberOfChannels = Math.max (1, format.getChannels ());
            final int bits = format.getSampleSizeInBits ();
            if (bits != 8 && bits != 16 && bits != 24 && bits != 32)
                return new int [0] [];

            final boolean bigEndian = format.isBigEndian ();
            final int bytesPerSample = bits / 8;
            final int frameSize = bytesPerSample * numberOfChannels;
            final byte [] data = audioInputStream.readAllBytes ();
            final int numberOfFrames = frameSize == 0 ? 0 : data.length / frameSize;
            final int [] [] channels = new int [numberOfChannels] [numberOfFrames];
            for (int frame = 0; frame < numberOfFrames; frame++)
            {
                final int frameOffset = frame * frameSize;
                for (int channel = 0; channel < numberOfChannels; channel++)
                    channels[channel][frame] = readSample (data, frameOffset + channel * bytesPerSample, bits, bigEndian);
            }
            return channels;
        }
    }


    /**
     * Read a single signed PCM sample from a byte array.
     *
     * @param data The audio data
     * @param offset The byte offset of the sample
     * @param bits The number of bits per sample (8, 16, 24 or 32)
     * @param bigEndian True if the data is stored big-endian
     * @return The signed sample value
     */
    private static int readSample (final byte [] data, final int offset, final int bits, final boolean bigEndian)
    {
        final int bytes = bits / 8;
        if (bytes <= 0 || offset + bytes > data.length)
            return 0;
        // 8-bit WAV samples are unsigned with a bias of 128
        if (bits == 8)
            return (data[offset] & 0xFF) - 128;

        int sample = 0;
        if (bigEndian)
            for (int b = 0; b < bytes; b++)
                sample = sample << 8 | data[offset + b] & 0xFF;
        else
            for (int b = bytes - 1; b >= 0; b--)
                sample = sample << 8 | data[offset + b] & 0xFF;

        // Sign-extend to a full integer
        final int shift = 32 - bits;
        return sample << shift >> shift;
    }
}
