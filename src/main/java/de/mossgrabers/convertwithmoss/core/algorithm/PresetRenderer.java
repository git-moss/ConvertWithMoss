// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.algorithm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;


/**
 * Renders one note of a multi-sample into an audio signal, so that a preset can be listened to
 * before it is converted. Everything the model holds is applied: every zone which the note triggers
 * is mixed, the sample is pitched by the distance to its root key, the loop sustains the note, and
 * the amplitude envelope, the filter with its own envelope and key tracking, the pitch and volume
 * LFOs, the velocity modulations, the gain and the panning shape it.
 *
 * This renders what the model holds and not what the source file holds, which is the point: a
 * preset which converts to silence or which lost its envelope sounds exactly as wrong here as it
 * will in the destination.
 *
 * @author Jürgen Moßgraber
 */
public class PresetRenderer
{
    /** The sample rate at which a preview is rendered. */
    public static final int      SAMPLE_RATE           = 44100;
    /** The format of a rendered preview: 16 bit stereo at the preview sample rate. */
    public static final AudioFormat PREVIEW_FORMAT     = new AudioFormat (SAMPLE_RATE, 16, 2, true, false);

    private static final double  DEFAULT_HOLD_SECONDS  = 2.0;
    private static final double  MAXIMUM_SECONDS       = 8.0;
    private static final int     DEFAULT_VELOCITY      = 100;
    /** Below this level the release is over and the rendering stops. */
    private static final double  SILENCE_LEVEL         = 0.0001;
    /** The resonance of a filter at its lowest, which is a Butterworth response. */
    private static final double  MINIMUM_Q             = 0.707;
    /** The resonance of a filter at its highest. */
    private static final double  MAXIMUM_Q             = 12.0;
    /** How far the filter envelope and the velocity may move the cutoff, in octaves. */
    private static final double  CUTOFF_MODULATION_OCTAVES = 6.0;
    /** The reference key of the filter cutoff key tracking. */
    private static final int     CUTOFF_KEY_CENTER     = 60;
    /** A short fade whenever the signal ends, so that a note never stops with a click. */
    private static final double  FADE_OUT_SECONDS      = 0.01;


    /**
     * Render one note of the given multi-sample.
     *
     * @param source The multi-sample to render
     * @return The rendered signal in {@link #PREVIEW_FORMAT}, empty if the note triggers no zone
     * @throws IOException Could not read the audio data of a zone
     */
    public static byte [] render (final IMultisampleSource source) throws IOException
    {
        return render (source, getPreviewKey (source), DEFAULT_VELOCITY);
    }


    /**
     * Render one note of the given multi-sample.
     *
     * @param source The multi-sample to render
     * @param key The key to play
     * @param velocity The velocity to play the key at
     * @return The rendered signal in {@link #PREVIEW_FORMAT}, empty if the note triggers no zone
     * @throws IOException Could not read the audio data of a zone
     */
    public static byte [] render (final IMultisampleSource source, final int key, final int velocity) throws IOException
    {
        final List<ISampleZone> zones = collectZones (source, key, velocity);
        if (zones.isEmpty ())
            return new byte [0];

        final int holdFrames = (int) Math.round (DEFAULT_HOLD_SECONDS * SAMPLE_RATE);
        final int maximumFrames = (int) Math.round (MAXIMUM_SECONDS * SAMPLE_RATE);
        final double [] left = new double [maximumFrames];
        final double [] right = new double [maximumFrames];

        int renderedFrames = 0;
        for (final ISampleZone zone: zones)
            renderedFrames = Math.max (renderedFrames, renderZone (zone, key, velocity, holdFrames, maximumFrames, left, right));
        if (renderedFrames == 0)
            return new byte [0];

        return toPcm (left, right, renderedFrames);
    }


    /**
     * Get the key which shows a multi-sample best, which is the middle of the range it covers.
     *
     * @param source The multi-sample
     * @return The key
     */
    public static int getPreviewKey (final IMultisampleSource source)
    {
        // Only keys which a zone actually covers are worth playing - a drum map or a preset with a
        // gap in the middle of its range has none at the middle of its outer bounds
        final boolean [] covered = new boolean [128];
        for (final IGroup group: source.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
                for (int key = Math.max (0, zone.getKeyLow ()); key <= Math.min (127, zone.getKeyHigh ()); key++)
                    covered[key] = true;

        int count = 0;
        for (final boolean isCovered: covered)
            if (isCovered)
                count++;
        if (count == 0)
            return CUTOFF_KEY_CENTER;

        // The middle of the covered keys, which is the most representative note of the preset
        int remaining = count / 2;
        for (int key = 0; key < covered.length; key++)
            if (covered[key] && remaining-- == 0)
                return key;
        return CUTOFF_KEY_CENTER;
    }


    /**
     * Collect the zones which the given key and velocity trigger. A preset can layer several of
     * them on one key, which is why they are all mixed and not only the first one.
     *
     * @param source The multi-sample
     * @param key The key to play
     * @param velocity The velocity to play the key at
     * @return The triggered zones
     */
    private static List<ISampleZone> collectZones (final IMultisampleSource source, final int key, final int velocity)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: source.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (key < zone.getKeyLow () || key > zone.getKeyHigh ())
                    continue;
                final int velocityLow = zone.getVelocityLow ();
                final int velocityHigh = zone.getVelocityHigh ();
                // A zone which declares no velocity window covers the whole range
                if (velocityHigh > 0 && (velocity < velocityLow || velocity > velocityHigh))
                    continue;
                if (zone.getSampleData ().isPresent ())
                    zones.add (zone);
            }
        return zones;
    }


    /**
     * Render one zone and add it to the given signal.
     *
     * @param zone The zone to render
     * @param key The key to play
     * @param velocity The velocity to play the key at
     * @param holdFrames How long the key is held down
     * @param maximumFrames The length of the signal
     * @param left The left channel to add to
     * @param right The right channel to add to
     * @return The number of frames which were written
     * @throws IOException Could not read the audio data of the zone
     */
    private static int renderZone (final ISampleZone zone, final int key, final int velocity, final int holdFrames, final int maximumFrames, final double [] left, final double [] right) throws IOException
    {
        final double [] [] sample = readSample (zone);
        if (sample == null || sample[0].length == 0)
            return 0;
        final double sampleRate = sample[2][0];

        // The distance to the root key, scaled by the key tracking of the zone, plus its own tuning
        final double semitones = (key - zone.getKeyRoot ()) * zone.getKeyTracking () + zone.getTuning ();
        final double step = Math.pow (2, semitones / 12.0) * sampleRate / SAMPLE_RATE;

        final int start = Math.max (0, zone.getStart ());
        final int stop = zone.getStop () > start ? Math.min (zone.getStop (), sample[0].length) : sample[0].length;
        final Loop loop = readLoop (zone, start, stop);

        final EnvelopeRunner amplitude = new EnvelopeRunner (zone.getAmplitudeEnvelopeModulator ().getSource (), 1.0);
        final FilterRunner filter = FilterRunner.create (zone.getFilter (), key, velocity);
        final LfoRunner pitchLfo = LfoRunner.create (zone.getPitchLfoModulator ().getSource (), zone.getPitchLfoModulator ().getDepth ());
        final LfoRunner volumeLfo = LfoRunner.create (zone.getAmplitudeLfoModulator ().getSource (), zone.getAmplitudeLfoModulator ().getDepth ());

        // The gain of the zone plus what the velocity adds to it
        final double velocityAmount = Math.clamp (zone.getAmplitudeVelocityModulator ().getDepth (), 0, 1);
        final double velocityGain = 1.0 - velocityAmount + velocityAmount * velocity / 127.0;
        final double gain = decibelToLinear (zone.getGain ()) * velocityGain;
        final double panning = Math.clamp (zone.getPanning (), -1, 1);
        final double leftGain = gain * Math.min (1, 1 - panning);
        final double rightGain = gain * Math.min (1, 1 + panning);

        double position = start;
        int frame = 0;
        for (; frame < maximumFrames; frame++)
        {
            final boolean isReleased = frame >= holdFrames;
            final double level = amplitude.next (isReleased);
            // Stop once the release has faded out, but never before the key is released
            if (isReleased && level < SILENCE_LEVEL)
                break;

            double value = read (sample, position);
            if (filter != null)
                value = filter.next (value, isReleased);

            double amount = level * (volumeLfo == null ? 1 : volumeLfo.nextVolume ());
            final double fadeFrames = FADE_OUT_SECONDS * SAMPLE_RATE;
            // Fade the very end out so that a note which runs into the limit does not click
            final int remaining = maximumFrames - frame;
            if (remaining < fadeFrames)
                amount *= remaining / fadeFrames;
            // The same where the audio itself runs out: a zone which does not loop - or whose loop
            // has ended with the release - simply stops at its end point, and cutting the wave off
            // in the middle clicks just as much
            if ((loop == null || isReleased && loop.untilRelease) && step > 0)
            {
                final double audioFrames = (stop - position) / step;
                if (audioFrames < fadeFrames)
                    amount *= Math.max (0, audioFrames / fadeFrames);
            }

            left[frame] += value * amount * leftGain;
            right[frame] += value * amount * rightGain;

            position += step * (pitchLfo == null ? 1 : pitchLfo.nextPitchFactor ());
            position = advance (position, loop, stop, isReleased);
            if (position >= stop)
                break;
        }
        return frame;
    }


    /**
     * Move the read position into the loop if it ran past its end.
     *
     * @param position The position after the step
     * @param loop The loop of the zone or null
     * @param stop The last frame of the zone
     * @param isReleased True if the key is already released
     * @return The position to read from next
     */
    private static double advance (final double position, final Loop loop, final int stop, final boolean isReleased)
    {
        if (loop == null || position < loop.end)
            return position;
        // A loop which only sustains the note stops looping as soon as the key is released
        if (isReleased && loop.untilRelease)
            return position;
        final double length = loop.end - loop.start;
        return length <= 0 ? position : loop.start + (position - loop.start) % length;
    }


    /**
     * Read one frame with a linear interpolation between its neighbours.
     *
     * @param sample The channels of the sample
     * @param position The position to read at
     * @return The value, the mix of both channels for a stereo sample
     */
    private static double read (final double [] [] sample, final double position)
    {
        final double [] channel = sample[0];
        final int index = (int) position;
        if (index < 0 || index >= channel.length)
            return 0;
        final int next = Math.min (index + 1, channel.length - 1);
        final double fraction = position - index;
        final double first = channel[index] + (channel[next] - channel[index]) * fraction;
        if (sample[1].length == 0)
            return first;
        final double second = sample[1][index] + (sample[1][next] - sample[1][index]) * fraction;
        return (first + second) / 2.0;
    }


    /**
     * Read the audio data of a zone. Every format can write its samples as a WAV, which makes this
     * the one path which works for all of them.
     *
     * @param zone The zone
     * @return The left channel, the right channel (empty for a mono sample) and the sample rate in
     *         a single element array, null if the data cannot be read
     * @throws IOException Could not read the audio data
     */
    private static double [] [] readSample (final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            return null;

        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        sampleData.get ().writeSample (out);

        try (final AudioInputStream in = AudioSystem.getAudioInputStream (new ByteArrayInputStream (out.toByteArray ())))
        {
            final AudioFormat format = in.getFormat ();
            final AudioFormat target = new AudioFormat (format.getSampleRate (), 16, format.getChannels (), true, false);
            try (final AudioInputStream pcm = AudioSystem.getAudioInputStream (target, in))
            {
                final byte [] bytes = pcm.readAllBytes ();
                final int channels = target.getChannels ();
                final int frames = bytes.length / (2 * channels);
                final double [] left = new double [frames];
                final double [] right = new double [channels > 1 ? frames : 0];
                for (int i = 0; i < frames; i++)
                {
                    left[i] = toSample (bytes, i * 2 * channels);
                    if (channels > 1)
                        right[i] = toSample (bytes, i * 2 * channels + 2);
                }
                return new double [] []
                {
                    left,
                    right,
                    {
                        format.getSampleRate ()
                    }
                };
            }
        }
        catch (final UnsupportedAudioFileException | IllegalArgumentException ex)
        {
            return null;
        }
    }


    private static double toSample (final byte [] bytes, final int offset)
    {
        return (short) (bytes[offset] & 0xFF | bytes[offset + 1] << 8) / 32768.0;
    }


    /**
     * Read the forward loop of a zone.
     *
     * @param zone The zone
     * @param start The first frame of the zone
     * @param stop The last frame of the zone
     * @return The loop or null if the zone has none which can be rendered
     */
    private static Loop readLoop (final ISampleZone zone, final int start, final int stop)
    {
        for (final ISampleLoop sampleLoop: zone.getLoops ())
        {
            if (sampleLoop.getType () != LoopType.FORWARDS)
                continue;
            final int loopStart = Math.max (start, sampleLoop.getStart ());
            final int loopEnd = Math.min (stop, sampleLoop.getEnd ());
            if (loopEnd > loopStart)
                return new Loop (loopStart, loopEnd, sampleLoop.isLoopUntilRelease ());
        }
        return null;
    }


    /**
     * Convert the rendered signal into the preview format.
     *
     * @param left The left channel
     * @param right The right channel
     * @param frames The number of frames to convert
     * @return The interleaved 16 bit signal
     */
    private static byte [] toPcm (final double [] left, final double [] right, final int frames)
    {
        // Normalize only if the mix of several zones went past full scale
        double peak = 0;
        for (int i = 0; i < frames; i++)
            peak = Math.max (peak, Math.max (Math.abs (left[i]), Math.abs (right[i])));
        final double scale = peak > 1 ? 1 / peak : 1;

        final byte [] pcm = new byte [frames * 4];
        for (int i = 0; i < frames; i++)
        {
            writeSample (pcm, i * 4, left[i] * scale);
            writeSample (pcm, i * 4 + 2, right[i] * scale);
        }
        return pcm;
    }


    private static void writeSample (final byte [] pcm, final int offset, final double value)
    {
        final int sample = (int) Math.clamp (Math.round (value * 32767.0), -32768, 32767);
        pcm[offset] = (byte) sample;
        pcm[offset + 1] = (byte) (sample >> 8);
    }


    private static double decibelToLinear (final double decibel)
    {
        if (decibel <= Double.NEGATIVE_INFINITY)
            return 0;
        return decibel == 0 ? 1 : Math.pow (10, Math.clamp (decibel, -96, 24) / 20.0);
    }


    /** The forward loop of a zone. */
    private record Loop (int start, int end, boolean untilRelease)
    {
        // Intentionally empty
    }


    /** Runs an envelope of the model one frame at a time. */
    private static class EnvelopeRunner
    {
        private final double delay;
        private final double attack;
        private final double hold;
        private final double decay;
        private final double sustain;
        private final double release;
        private final double startLevel;
        private final double holdLevel;
        private final double depth;

        private double       time            = 0;
        private double       releaseTime     = -1;
        private double       levelAtRelease  = 0;
        private double       currentLevel    = 0;


        EnvelopeRunner (final IEnvelope envelope, final double depth)
        {
            this.depth = depth;
            this.delay = time (envelope == null ? -1 : envelope.getDelayTime ());
            this.attack = time (envelope == null ? -1 : envelope.getAttackTime ());
            this.hold = time (envelope == null ? -1 : envelope.getHoldTime ());
            this.decay = time (envelope == null ? -1 : envelope.getDecayTime ());
            this.release = time (envelope == null ? -1 : envelope.getReleaseTime ());
            this.sustain = level (envelope == null ? -1 : envelope.getSustainLevel (), 1);
            this.startLevel = level (envelope == null ? -1 : envelope.getStartLevel (), 0);
            this.holdLevel = level (envelope == null ? -1 : envelope.getHoldLevel (), 1);
        }


        private static double time (final double value)
        {
            return value < 0 ? 0 : value;
        }


        private static double level (final double value, final double fallback)
        {
            return value < 0 ? fallback : Math.clamp (value, 0, 1);
        }


        double next (final boolean isReleased)
        {
            if (isReleased && this.releaseTime < 0)
            {
                this.releaseTime = this.time;
                this.levelAtRelease = this.currentLevel;
            }
            this.currentLevel = this.levelAt (isReleased);
            this.time += 1.0 / SAMPLE_RATE;
            return this.currentLevel * this.depth;
        }


        private double levelAt (final boolean isReleased)
        {
            if (isReleased)
            {
                final double since = this.time - this.releaseTime;
                // A release of zero would drop the level to nothing from one frame to the next,
                // which clicks - ramp it down over the fade time instead
                final double releaseSeconds = Math.max (this.release, FADE_OUT_SECONDS);
                return Math.max (0, this.levelAtRelease * (1 - since / releaseSeconds));
            }

            double point = this.time;
            if (point < this.delay)
                return this.startLevel;
            point -= this.delay;
            if (point < this.attack)
                return this.startLevel + (this.holdLevel - this.startLevel) * (point / this.attack);
            point -= this.attack;
            if (point < this.hold)
                return this.holdLevel;
            point -= this.hold;
            if (point < this.decay)
                return this.holdLevel + (this.sustain - this.holdLevel) * (point / this.decay);
            return this.sustain;
        }
    }


    /** Runs a low frequency oscillator of the model one frame at a time. */
    private static class LfoRunner
    {
        private final ILfo   lfo;
        private final double depth;
        private double       time = 0;


        private LfoRunner (final ILfo lfo, final double depth)
        {
            this.lfo = lfo;
            this.depth = depth;
        }


        static LfoRunner create (final ILfo lfo, final double depth)
        {
            return lfo == null || !lfo.isSet () || depth == 0 ? null : new LfoRunner (lfo, depth);
        }


        /**
         * @return The factor to multiply the play-back speed with, which is the vibrato
         */
        double nextPitchFactor ()
        {
            // The depth of the pitch LFO is given in semi-tones of the model's maximum
            return Math.pow (2, this.next () * this.depth * 12.0 / 12.0 / 12.0);
        }


        /**
         * @return The factor to multiply the amplitude with, which is the tremolo
         */
        double nextVolume ()
        {
            return Math.clamp (1 + this.next () * Math.abs (this.depth), 0, 2);
        }


        private double next ()
        {
            final double delay = Math.max (0, this.lfo.getDelay ());
            final double phase = (this.time - delay) * Math.max (0.01, this.lfo.getRate ());
            this.time += 1.0 / SAMPLE_RATE;
            if (this.time < delay)
                return 0;
            final double ramp = phase - Math.floor (phase);
            return switch (this.lfo.getWaveform ())
            {
                case SQUARE -> Math.signum (Math.sin (2 * Math.PI * phase));
                case TRIANGLE -> 4 * Math.abs (ramp - 0.5) - 1;
                case SAWTOOTH_UP -> 2 * ramp - 1;
                case SAWTOOTH_DOWN -> 1 - 2 * ramp;
                case RANDOM -> Math.sin (2 * Math.PI * phase * 1.37) * Math.sin (2 * Math.PI * phase * 0.63);
                default -> Math.sin (2 * Math.PI * phase);
            };
        }
    }


    /**
     * Runs the filter of a zone one frame at a time. A pole pair is one state variable stage, so a
     * four pole filter is two of them in series.
     */
    private static class FilterRunner
    {
        private final FilterType     type;
        private final double         baseCutoff;
        private final double         q;
        private final EnvelopeRunner envelope;
        private final double         envelopeDepth;
        private final double [] []   state;


        private FilterRunner (final IFilter filter, final int key, final int velocity)
        {
            this.type = filter.getType ();
            this.q = MINIMUM_Q + (MAXIMUM_Q - MINIMUM_Q) * Math.clamp (filter.getResonance (), 0, 1);

            // The key tracking moves the cutoff with the note, the velocity lifts it once
            final double tracking = Math.pow (2, filter.getCutoffKeyTracking () * (key - CUTOFF_KEY_CENTER) / 12.0);
            final double velocityDepth = Math.clamp (filter.getCutoffVelocityModulator ().getDepth (), -1, 1);
            final double velocityFactor = Math.pow (2, velocityDepth * CUTOFF_MODULATION_OCTAVES * (velocity / 127.0));
            this.baseCutoff = Math.clamp (filter.getCutoff () * tracking * velocityFactor, 20, SAMPLE_RATE / 2.0 - 100);

            this.envelopeDepth = filter.getCutoffEnvelopeModulator ().getDepth ();
            final IEnvelope source = filter.getCutoffEnvelopeModulator ().getSource ();
            this.envelope = this.envelopeDepth == 0 || source == null ? null : new EnvelopeRunner (source, 1.0);

            final int stages = Math.max (1, (filter.getPoles () + 1) / 2);
            this.state = new double [stages] [2];
        }


        static FilterRunner create (final Optional<IFilter> filter, final int key, final int velocity)
        {
            return filter.isEmpty () ? null : new FilterRunner (filter.get (), key, velocity);
        }


        double next (final double input, final boolean isReleased)
        {
            double cutoff = this.baseCutoff;
            if (this.envelope != null)
                cutoff *= Math.pow (2, this.envelope.next (isReleased) * this.envelopeDepth * CUTOFF_MODULATION_OCTAVES);
            cutoff = Math.clamp (cutoff, 20, SAMPLE_RATE / 2.0 - 100);

            // Topology preserving state variable filter, which stays stable while the cutoff moves
            final double g = Math.tan (Math.PI * cutoff / SAMPLE_RATE);
            final double k = 1.0 / this.q;
            final double a = 1.0 / (1.0 + g * (g + k));

            double value = input;
            for (final double [] stage: this.state)
            {
                final double high = (value - (g + k) * stage[0] - stage[1]) * a;
                final double band = g * high + stage[0];
                final double low = g * band + stage[1];
                stage[0] = g * high + band;
                stage[1] = g * band + low;
                value = switch (this.type)
                {
                    case HIGH_PASS -> high;
                    case BAND_PASS -> band;
                    case BAND_REJECTION -> high + low;
                    default -> low;
                };
            }
            return value;
        }
    }
}
