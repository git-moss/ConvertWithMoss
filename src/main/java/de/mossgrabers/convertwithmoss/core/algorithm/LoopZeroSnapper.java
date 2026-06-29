// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * Snaps the start and end of forward loops to a nearby zero-crossing of the sample audio. Sample
 * libraries sometimes ship loops whose boundaries land on non-matching sample values, so the loop
 * audibly clicks at the wrap-around point on every repeat. Moving both boundaries to a rising
 * zero-crossing makes the loop end and the loop start meet near zero, which removes the click
 * without altering the audio - only the stored loop positions change.
 * <p>
 * The adjustment is conservative: it is only applied when it actually reduces the discontinuity at
 * the loop wrap, very short (single-cycle) loops are left untouched so their pitch is not changed,
 * and a boundary is moved by at most an eighth of the loop length.
 *
 * @author Jürgen Moßgraber
 */
public final class LoopZeroSnapper
{
    /** Loops shorter than this many frames are not touched (single-cycle / pitch critical). */
    private static final int MINIMUM_LOOP_LENGTH = 4096;
    /** The maximum number of frames a loop boundary may be moved to find a zero-crossing. */
    private static final int MAXIMUM_WINDOW      = 512;


    /**
     * Constructor.
     */
    private LoopZeroSnapper ()
    {
        // Intentionally empty
    }


    /**
     * Snap the forward loops of all given zones to a nearby zero-crossing.
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

            final int [] signal;
            try
            {
                signal = readMonoSignal (zone);
            }
            catch (final IOException | UnsupportedAudioFileException _)
            {
                // The audio cannot be read - leave the loop unchanged
                continue;
            }
            if (signal.length < MINIMUM_LOOP_LENGTH)
                continue;

            for (final ISampleLoop loop: loops)
                if (snapLoop (loop, signal))
                    adjusted++;
        }
        return adjusted;
    }


    /**
     * Snap a single loop if it is a forward loop and a better (lower discontinuity) boundary pair
     * can be found nearby.
     *
     * @param loop The loop to adjust
     * @param signal The mono mix of the sample audio
     * @return True if the loop was adjusted
     */
    private static boolean snapLoop (final ISampleLoop loop, final int [] signal)
    {
        if (loop.getType () != LoopType.FORWARDS)
            return false;

        final int length = signal.length;
        final int start = loop.getStart ();
        // A loop end of -1 (or beyond the audio) means "loop to the end of the sample"
        int end = loop.getEnd ();
        if (end < 0 || end >= length)
            end = length - 1;
        if (start < 0 || end <= start)
            return false;
        if (end - start < MINIMUM_LOOP_LENGTH)
            return false;

        final int window = Math.min (MAXIMUM_WINDOW, (end - start) / 8);
        if (window < 1)
            return false;
        final int newStart = nearestRisingZeroCrossing (signal, start, window);
        final int newEnd = nearestRisingZeroCrossing (signal, end, window);
        if (newStart < 0 || newEnd < 0 || newEnd <= newStart)
            return false;

        // Only apply the snap when it actually reduces the click at the wrap-around
        if (discontinuity (signal, newStart, newEnd) >= discontinuity (signal, start, end))
            return false;

        loop.setStart (newStart);
        loop.setEnd (newEnd);
        return true;
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
    private static int discontinuity (final int [] signal, final int start, final int end)
    {
        final int last = Math.max (0, Math.min (end, signal.length - 1));
        final int first = Math.max (0, Math.min (start, signal.length - 1));
        return Math.abs (signal[last] - signal[first]);
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
    private static int [] readMonoSignal (final ISampleZone zone) throws IOException, UnsupportedAudioFileException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        zone.getSampleData ().writeSample (out);
        try (final AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (new ByteArrayInputStream (out.toByteArray ())))
        {
            final AudioFormat format = audioInputStream.getFormat ();
            final int channels = Math.max (1, format.getChannels ());
            final int bits = format.getSampleSizeInBits ();
            if (bits != 8 && bits != 16 && bits != 24 && bits != 32)
                return new int [0];

            final boolean bigEndian = format.isBigEndian ();
            final int bytesPerSample = bits / 8;
            final int frameSize = bytesPerSample * channels;
            final byte [] data = audioInputStream.readAllBytes ();
            final int numberOfFrames = frameSize == 0 ? 0 : data.length / frameSize;
            final int [] signal = new int [numberOfFrames];
            for (int frame = 0; frame < numberOfFrames; frame++)
            {
                final int frameOffset = frame * frameSize;
                long sum = 0;
                for (int channel = 0; channel < channels; channel++)
                    sum += readSample (data, frameOffset + channel * bytesPerSample, bits, bigEndian);
                signal[frame] = (int) (sum / channels);
            }
            return signal;
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
                sample = (sample << 8) | (data[offset + b] & 0xFF);
        else
            for (int b = bytes - 1; b >= 0; b--)
                sample = (sample << 8) | (data[offset + b] & 0xFF);

        // Sign-extend to a full integer
        final int shift = 32 - bits;
        return sample << shift >> shift;
    }
}
