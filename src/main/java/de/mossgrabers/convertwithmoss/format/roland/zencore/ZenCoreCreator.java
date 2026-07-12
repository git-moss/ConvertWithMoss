// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.CRC32;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzInstrument;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzSample;


/**
 * Creator for Roland FANTOM / FANTOM-0 (and other ZEN-Core hardware) keyboard instruments. Writes an
 * importable <i>.svz</i>: a single tone for one multi-sample (see {@link #createPreset}) or a
 * multi-tone bank sharing one sample pool for several (see {@link #createPresetLibrary}), loadable
 * through the device's <i>UTILITY &rarr; IMPORT &rarr; IMPORT TONE</i> function.
 *
 * <p>
 * User samples are written at the FANTOM's native 48 kHz / 16-bit. The voice engine has no loop
 * smoothing of its own, so click-free playback is baked into the samples: the loop end is re-seated
 * to a period-aligned point ({@link #optimizeLoopEnd}), a loop whose wrap still is not seamless gets
 * its tail cross-faded into the loop-start lead-in ({@link #crossfadeLoop}), and guard frames of the
 * loop-start continuation are stored past the loop end for the engine's interpolation look-ahead.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreCreator extends AbstractCreator<ZenCoreCreatorUI>
{
    /**
     * The FANTOM's native user-sample rate is 48 kHz (its own sample exports are 48 kHz); a sample
     * left at another rate is re-sampled by the device on playback, which aliases bright content (a
     * near-clipping square is heard as a "corrupt", harsh region). User samples are therefore
     * high-quality re-sampled to 48 kHz / 16-bit here, and the loop end is re-seated to a
     * period-aligned point afterwards ({@link #optimizeLoopEnd}) so re-sampling does not leave the
     * loop off its seamless wrap.
     */
    private static final DestinationAudioFormat ZENCORE_FORMAT      = new DestinationAudioFormat (new int []
    {
        16
    }, 48000, true);
    /** The FANTOM's native user-sample rate; loop and play positions are rescaled to it. */
    private static final int                    SAMPLE_RATE         = 48000;

    /**
     * A loop whose wrap discontinuity (value or slope, of 32768 full-scale) is at or below this is
     * left untouched - a hand-tuned, period-aligned loop (e.g. from an SF2) stays pristine. Above it,
     * the loop end is nudged to a period-aligned point and, if the wrap still is not seamless, the
     * loop tail is cross-faded. The threshold is deliberately small because audibility is relative to
     * the material: bright content (its own samples move further per frame) masks a wrap step of ~50,
     * but in a smooth pad the same step is a clearly audible tick on every loop pass, so only a wrap
     * that is seamless for <i>any</i> material is left alone.
     */
    private static final int                    LOOP_SEAM_TOLERANCE = 16;
    /** How far (in frames) the loop end may be moved to find a seamless, period-aligned wrap. */
    private static final int                    LOOP_END_SEARCH     = 1024;
    /**
     * Number of guard frames stored after a loop end: copies of the loop-start continuation, so the
     * voice engine has valid look-ahead across the loop wrap. The device's own looped samples carry
     * such frames (their played-sample count runs a handful of frames past the loop end); without
     * them the interpolator reads past the data at each wrap - the second interleaved (right) channel
     * falls off the end first, which is heard as a per-wrap click on the right side.
     */
    private static final int                    LOOP_GUARD_FRAMES   = 16;
    /**
     * Length (in frames) of the loop cross-fade applied only when a period-aligned loop end is still
     * not seamless - an evolving pad whose timbre drifts across the loop has no phase-aligned end.
     * About 21 ms at 48 kHz. Bright, already-seamless loops never reach this, so their audio is left
     * bit-exact.
     */
    private static final int                    LOOP_CROSSFADE      = 1024;
    /**
     * Salted into the content-hashed sample names ({@link #uniqueName}): bump when the written
     * sample layout changes (SMPd header fields, guard scheme, ...), so a re-import loads the new
     * bytes instead of the device re-using a same-named sample written by an older layout.
     */
    private static final int                    SAMPLE_FORMAT_REVISION = 6;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ZenCoreCreator (final INotifier notifier)
    {
        super ("Roland ZEN-Core", "ZenCore", notifier, new ZenCoreCreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "svz");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        final List<SvzSample> pool = new ArrayList<> ();
        final Map<Object, Integer> byContent = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        final SvzInstrument instrument = this.buildInstrument (multisampleSource, pool, byContent, usedNames);
        ensureLoadableSamplePool (pool, byContent, usedNames);

        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, List.of (instrument), this.settingsConfiguration.getHeader ()));
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (multisampleSources.isEmpty ())
            return;

        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (libraryName), "svz");
        this.notifier.log ("IDS_ZENCORE_WRITING_BANK", libraryName, Integer.toString (multisampleSources.size ()));

        final List<SvzSample> pool = new ArrayList<> ();
        final Map<Object, Integer> byContent = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        final List<SvzInstrument> instruments = new ArrayList<> ();
        for (final IMultisampleSource source: multisampleSources)
            instruments.add (this.buildInstrument (source, pool, byContent, usedNames));
        ensureLoadableSamplePool (pool, byContent, usedNames);

        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, instruments, this.settingsConfiguration.getHeader ()));
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private SvzInstrument buildInstrument (final IMultisampleSource multisampleSource, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        // The audio is resampled to 48 kHz (see ZENCORE_FORMAT), so rescale the loop/start/end frames
        // to it; the loop end is then re-seated to a period-aligned point per sample in addSample.
        recalculateSamplePositions (multisampleSource, SAMPLE_RATE);

        final SvzInstrument instrument = new SvzInstrument ();
        instrument.name = multisampleSource.getName ();
        ISampleZone representativeZone = null;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int [] sampleIndexes = this.addSample (zone, pool, byContent, usedNames);
                if (sampleIndexes == null)
                    continue;
                if (representativeZone == null)
                    representativeZone = zone;
                final int keyLow = Math.max (0, zone.getKeyLow ());
                final int keyHigh = Math.min (127, zone.getKeyHigh ());
                for (int key = keyLow; key <= keyHigh; key++)
                {
                    instrument.keyToSample[key] = sampleIndexes[0];
                    instrument.keyToSampleRight[key] = sampleIndexes[1];
                }
            }
        // The FANTOM tone has one filter + amp envelope per partial; take them from a
        // representative zone so converted tones keep the source's character (offsets validated
        // against the 2048 factory tones).
        if (representativeZone != null)
            applyToneParameters (instrument, representativeZone);

        // Name the multisample(s) by content (key map + the content-hashed sample names), see
        // SvzInstrument.multisampleName. A stereo instrument gets a second, right-channel
        // multisample - the tone plays them through its two wave slots (Wave L / Wave R).
        instrument.multisampleName = contentName (instrument.name, contentHash (keyMapContent ("L", instrument.keyToSample, pool)));
        if (!Arrays.equals (instrument.keyToSample, instrument.keyToSampleRight))
            instrument.multisampleNameRight = contentName (instrument.name, contentHash (keyMapContent ("R", instrument.keyToSampleRight, pool)));
        return instrument;
    }


    private static byte [] keyMapContent (final String channelTag, final int [] keyToSample, final List<SvzSample> pool)
    {
        final StringBuilder content = new StringBuilder (channelTag).append ('\n');
        for (int key = 0; key < 128; key++)
        {
            final int sampleIndex = keyToSample[key];
            content.append (key).append (':').append (sampleIndex == 0 ? "-" : pool.get (sampleIndex - 1).name).append ('\n');
        }
        return content.toString ().getBytes (StandardCharsets.US_ASCII);
    }


    private static void applyToneParameters (final SvzInstrument instrument, final ISampleZone zone)
    {
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            instrument.filterType = switch (filter.getType ())
            {
                case LOW_PASS -> 1;
                case BAND_PASS -> 2;
                case HIGH_PASS -> 3;
                default -> 1;
            };
            instrument.cutoff = Math.clamp ((int) Math.round (MathUtils.normalizeCutoff (filter.getCutoff ()) * 1023.0), 0, 1023);
            instrument.resonance = Math.clamp ((int) Math.round (filter.getResonance () * 1023.0), 0, 1023);
        }

        final IEnvelope env = zone.getAmplitudeEnvelopeModulator ().getSource ();
        if (env != null)
        {
            instrument.envAttack = timeToValue (env.getAttackTime ());
            final double hold = env.getHoldTime ();
            instrument.envHold = hold > 0 ? timeToValue (hold) : 0;
            // Never write a decay time of 0: a tone whose TVA decay stage is instant imports but
            // plays silent on a FANTOM-0 (an SFZ without ampeg opcodes defaults to an all-instant
            // envelope). The floor is inaudibly fast but keeps the envelope valid - zero attack,
            // hold and release are all fine on the hardware, only the decay stage kills the voice.
            instrument.envDecay = Math.max (8, timeToValue (env.getDecayTime ()));
            final double release = env.getReleaseTime ();
            instrument.envRelease = release >= 0 ? timeToValue (release) : 150;
            final double holdLevel = env.getHoldLevel ();
            instrument.envHoldLevel = holdLevel < 0 ? 1023 : Math.clamp ((int) Math.round (holdLevel * 1023.0), 0, 1023);
            final double sustain = env.getSustainLevel ();
            instrument.envSustain = sustain < 0 ? 1023 : Math.clamp ((int) Math.round (sustain * 1023.0), 0, 1023);
        }
    }


    /**
     * Approximate the FANTOM TVA envelope time value (0-1023) from a time in seconds. Roland's exact
     * time table is not published; this log2 curve is calibrated so ~20 s maps to full scale and
     * near-instant times map to 0.
     *
     * @param seconds The time in seconds
     * @return The 0-1023 time value
     */
    private static int timeToValue (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return Math.clamp ((int) Math.round (1023 + 168 * Math.log (seconds / 20.0) / Math.log (2)), 0, 1023);
    }


    /**
     * Convert a zone's audio to the FANTOM sample pool, re-using already-added identical samples.
     * Samples are always stored <b>mono</b>: the voice engine mis-plays interleaved-stereo storage
     * (the two channels' samples alternate into the output as a strong Nyquist-carrier buzz, plus a
     * tick on every loop pass - both hardware-measured on a FANTOM-0, even with a seamless wrap),
     * and the device's own sampler stores mono as well. A stereo zone is therefore split into two
     * per-channel mono samples which the tone plays through its two wave slots (Wave L / Wave R) -
     * identical channels de-duplicate back into a single mono sample.
     *
     * @return The 1-based left and right pool indexes (equal for mono), or null if the sample could
     *         not be added
     */
    private int [] addSample (final ISampleZone zone, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final WaveFile waveFile = AudioFileUtils.convertToWav (zone.getSampleData (), ZENCORE_FORMAT);
        final int channels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (channels < 1 || channels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), zone.getName ());
            return null;
        }
        byte [] pcm = waveFile.getDataChunk ().getData ();
        final int rate = waveFile.getFormatChunk ().getSampleRate ();
        final int frames = pcm.length / (2 * channels);
        if (frames <= 0)
            return null;

        // Keep the source's loop points and audio untouched - no re-sampling, no zero-crossing snap -
        // so a period-aligned loop stays seamless across the wrap (see ZENCORE_FORMAT). When the wrap
        // is not already seamless the loop end is nudged (never the audio) to the nearest point whose
        // waveform matches the loop start, so the wrap becomes seamless without altering the sample.
        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean hasLoop = !loops.isEmpty ();
        int loopStart = 0;
        int end = zone.getStop () > 0 ? Math.min (zone.getStop (), frames) : frames;
        if (hasLoop)
        {
            final ISampleLoop loop = loops.get (0);
            loopStart = Math.clamp (loop.getStart (), 0, frames - 1);
            if (loop.getEnd () > loopStart)
                end = Math.min (loop.getEnd (), frames);
            end = optimizeLoopEnd (pcm, channels, loopStart, end, frames);
            // If period-alignment still cannot make the wrap seamless - an evolving pad whose timbre
            // drifts across the loop has no phase-aligned end - cross-fade the loop tail into the
            // loop-start lead-in. Safe here precisely because the end is period-aligned first: the two
            // blended stretches are in phase and reinforce, so the amplitude collapse an unaligned
            // cross-fade caused on bright content does not happen. Already-seamless loops are skipped,
            // as are loops starting at the first frames, which have no lead-in to measure or blend to.
            if (loopStart >= 2 && wrapDiscontinuity (pcm, channels, loopStart, end) > LOOP_SEAM_TOLERANCE)
                pcm = crossfadeLoop (pcm, channels, loopStart, end);
        }

        // Store the played part [0, end) plus, for a loop, LOOP_GUARD_FRAMES of the loop-start
        // continuation as guard frames past the loop end (the device carries these on its own loops).
        pcm = storeWithGuard (pcm, channels, end, hasLoop ? loopStart : -1);

        final int rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);
        final int level = Math.clamp ((int) Math.round (Math.pow (10, zone.getGain () / 20.0) * 127.0), 0, 127);

        if (channels == 1)
        {
            final int index = addPooledSample (pcm, rate, zone.getName (), rootKey, level, hasLoop, loopStart, end, pool, byContent, usedNames);
            return new int []
            {
                index,
                index
            };
        }
        return new int []
        {
            addPooledSample (extractChannel (pcm, 2, 0), rate, zone.getName (), rootKey, level, hasLoop, loopStart, end, pool, byContent, usedNames),
            addPooledSample (extractChannel (pcm, 2, 1), rate, zone.getName (), rootKey, level, hasLoop, loopStart, end, pool, byContent, usedNames)
        };
    }


    /**
     * Add a mono sample to the pool, re-using an already-added identical one (dedupe by content and
     * parameters - the two channels of a stereo-duplicated mono source fold back into one sample).
     *
     * @return The 1-based index into the pool
     */
    private static int addPooledSample (final byte [] pcm, final int rate, final String zoneName, final int rootKey, final int level, final boolean hasLoop, final int loopStart, final int end, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames)
    {
        final Object contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (loopStart), Integer.valueOf (end), Boolean.valueOf (hasLoop), Integer.valueOf (rootKey), Integer.valueOf (level));
        final Integer existing = byContent.get (contentKey);
        if (existing != null)
            return existing.intValue ();

        // The name hash covers everything the device caches under the sample name: the audio, the
        // playback parameters and the writer's layout revision (see SAMPLE_FORMAT_REVISION).
        final ByteBuffer parameters = ByteBuffer.allocate (32);
        parameters.putInt (SAMPLE_FORMAT_REVISION).putInt (loopStart).putInt (end).putInt (hasLoop ? 1 : 0);
        parameters.putInt (rootKey).putInt (level).putInt (1).putInt (rate);
        final CRC32 crc = new CRC32 ();
        crc.update (pcm);
        crc.update (parameters.array ());

        final SvzSample sample = new SvzSample ();
        sample.pcm = pcm;
        sample.rate = rate;
        sample.channels = 1;
        sample.name = uniqueName (zoneName, (int) crc.getValue (), usedNames);
        sample.originalKey = rootKey;
        sample.level = level;
        sample.hasLoop = hasLoop;
        sample.loopStart = loopStart;
        sample.end = end;
        pool.add (sample);

        final int index = pool.size (); // 1-based
        byContent.put (contentKey, Integer.valueOf (index));
        return index;
    }


    /**
     * A <i>.svz</i> whose sample pool holds exactly one sample imports without an error, but the
     * device never loads its wave data: the multisample maps and displays correctly, yet shows an
     * empty waveform and plays silent on every key (hardware-verified on a FANTOM-0; pools of two or
     * more samples from the identical writer load fine). An inert spacer sample - a few hundred
     * bytes of silence assigned to no key - keeps every pool loadable.
     *
     * @param pool The sample pool
     * @param byContent The content de-duplication map
     * @param usedNames The sample names used so far
     */
    private static void ensureLoadableSamplePool (final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames)
    {
        if (pool.size () == 1)
            addPooledSample (new byte [128], SAMPLE_RATE, "Spacer", 60, 0, false, 0, 64, pool, byContent, usedNames);
    }


    private static byte [] extractChannel (final byte [] interleaved, final int channels, final int channel)
    {
        final int frames = interleaved.length / (2 * channels);
        final byte [] mono = new byte [frames * 2];
        for (int frame = 0; frame < frames; frame++)
        {
            mono[frame * 2] = interleaved[(frame * channels + channel) * 2];
            mono[frame * 2 + 1] = interleaved[(frame * channels + channel) * 2 + 1];
        }
        return mono;
    }


    /**
     * When a loop's wrap is not already seamless, move the loop <b>end</b> (never the audio) to the
     * nearby point whose wrap is the least discontinuous, so the loop covers a whole number of wave
     * cycles and the wrap is seamless. The sample data is left bit-exact - unlike a cross-fade, which
     * writes a distorted, amplitude-cancelled region into the audio whenever the loop-end and
     * loop-start phases differ (blending two out-of-phase copies of a bright waveform partly cancels
     * them). A loop whose source points are already seamless is returned unchanged.
     *
     * @param pcm The interleaved 16-bit little-endian PCM (never modified)
     * @param channels The number of channels
     * @param loopStart The loop start frame
     * @param loopEnd The loop end frame from the source
     * @param frames The total number of frames
     * @return The (possibly nudged) loop end frame
     */
    private static int optimizeLoopEnd (final byte [] pcm, final int channels, final int loopStart, final int loopEnd, final int frames)
    {
        if (loopEnd - loopStart < 64 || loopStart < 2)
            return loopEnd;
        final int originalDiscontinuity = wrapDiscontinuity (pcm, channels, loopStart, loopEnd);
        if (originalDiscontinuity <= LOOP_SEAM_TOLERANCE)
            return loopEnd;

        // A forward loop's only discontinuity is the wrap L[end-1] -> L[loopStart], so the seamless
        // loop end is simply the candidate near the source end whose wrap deviates least from the
        // waveform's own step into the loop start (see wrapDiscontinuity). Ties are broken towards the
        // source end so the loop length changes as little as possible. The audio is never modified.
        final int lo = Math.max (loopStart + 2, loopEnd - LOOP_END_SEARCH);
        final int hi = Math.min (frames - LOOP_GUARD_FRAMES - 1, loopEnd + LOOP_END_SEARCH);
        int best = loopEnd;
        int bestDiscontinuity = originalDiscontinuity;
        for (int candidate = lo; candidate <= hi; candidate++)
        {
            final int discontinuity = wrapDiscontinuity (pcm, channels, loopStart, candidate);
            if (discontinuity < bestDiscontinuity || (discontinuity == bestDiscontinuity && Math.abs (candidate - loopEnd) < Math.abs (best - loopEnd)))
            {
                bestDiscontinuity = discontinuity;
                best = candidate;
            }
        }
        return best;
    }


    /**
     * The loop-wrap discontinuity in 16-bit full-scale units: how far the wrap deviates from the
     * waveform's own progression. A forward loop plays {@code [loopStart, loopEnd)} and wraps
     * {@code L[loopEnd-1] -> L[loopStart]}; that is seamless when {@code L[loopEnd-1]} equals the
     * natural predecessor of {@code L[loopStart]}, i.e. {@code L[loopStart-1]}, so the wrap just
     * repeats the waveform's own step into the loop start. The click is the value and slope mismatch
     * of the loop tail against the loop-start lead-in - measuring the raw {@code |L[loopEnd-1] -
     * L[loopStart]|} instead would wrongly flag the waveform's own (perfectly audible) slope.
     *
     * @param pcm The interleaved 16-bit little-endian PCM
     * @param channels The number of channels
     * @param loopStart The loop start frame (at least 2)
     * @param loopEnd The loop end frame
     * @return The largest value / slope deviation across the wrap
     */
    private static int wrapDiscontinuity (final byte [] pcm, final int channels, final int loopStart, final int loopEnd)
    {
        int discontinuity = 0;
        for (int channel = 0; channel < channels; channel++)
        {
            final int endValue = sampleAt (pcm, channels, loopEnd - 1, channel);
            final int leadValue = sampleAt (pcm, channels, loopStart - 1, channel);
            final int endSlope = endValue - sampleAt (pcm, channels, loopEnd - 2, channel);
            final int leadSlope = leadValue - sampleAt (pcm, channels, loopStart - 2, channel);
            discontinuity = Math.max (discontinuity, Math.max (Math.abs (endValue - leadValue), Math.abs (endSlope - leadSlope)));
        }
        return discontinuity;
    }


    /**
     * Build the stored PCM: the played part {@code [0, end)} and, for a loop, {@link #LOOP_GUARD_FRAMES}
     * guard frames past the loop end, each a copy of the matching loop-start-continuation frame.
     *
     * @param pcm The interleaved 16-bit little-endian PCM
     * @param channels The number of channels
     * @param end The loop end / play end frame
     * @param loopStart The loop start frame, or a negative value for a one-shot (no guard frames)
     * @return The stored PCM (played part, plus guard frames for a loop)
     */
    private static byte [] storeWithGuard (final byte [] pcm, final int channels, final int end, final int loopStart)
    {
        final int frameBytes = channels * 2;
        final int sourceFrames = pcm.length / frameBytes;
        final int played = Math.min (end, sourceFrames);
        if (loopStart < 0)
        {
            if (played >= sourceFrames)
                return pcm;
            final byte [] oneShot = new byte [played * frameBytes];
            System.arraycopy (pcm, 0, oneShot, 0, played * frameBytes);
            return oneShot;
        }
        // The played part [0, end) plus LOOP_GUARD_FRAMES of guard: copies of the loop-start
        // continuation - the very frames the loop wraps to. The voice engine's look-ahead across the
        // wrap then sees the loop restart (not audio running on past the loop end), so the interpolated
        // transition matches wherever the loop actually resumes.
        final byte [] stored = new byte [(end + LOOP_GUARD_FRAMES) * frameBytes];
        System.arraycopy (pcm, 0, stored, 0, played * frameBytes);
        for (int frame = end; frame < end + LOOP_GUARD_FRAMES; frame++)
        {
            final int sourceFrame = loopStart + (frame - end);
            if (sourceFrame >= 0 && (sourceFrame + 1) * frameBytes <= pcm.length)
                System.arraycopy (pcm, sourceFrame * frameBytes, stored, frame * frameBytes, frameBytes);
        }
        return stored;
    }


    private static int sampleAt (final byte [] pcm, final int channels, final int frame, final int channel)
    {
        final int idx = (frame * channels + channel) * 2;
        return (short) (pcm[idx] & 0xFF | pcm[idx + 1] << 8);
    }


    /**
     * Cross-fade the {@link #LOOP_CROSSFADE} frames ending at the loop end into the same number of
     * frames ending at the loop start, so the loop tail gradually becomes the loop-start lead-in and
     * the wrap is seamless even when no single end point is (an evolving pad). It is only ever called
     * on a period-aligned loop, so the two stretches are in phase and blend without the amplitude
     * cancellation an unaligned cross-fade causes on bright material. A linear (equal-gain) blend is
     * used because in-phase signals add coherently.
     *
     * @param pcm The interleaved 16-bit little-endian PCM (never modified)
     * @param channels The number of channels
     * @param loopStart The loop start frame
     * @param end The period-aligned loop end frame
     * @return A copy of the PCM with the cross-faded loop tail, or the input if the loop is too short
     */
    private static byte [] crossfadeLoop (final byte [] pcm, final int channels, final int loopStart, final int end)
    {
        final int n = Math.min (LOOP_CROSSFADE, Math.min ((end - loopStart) / 4, loopStart));
        if (n < 8)
            return pcm;
        final byte [] out = pcm.clone ();
        for (int i = 0; i < n; i++)
        {
            final double weight = (i + 1.0) / (n + 1.0); // 0 -> 1 as the loop end is approached
            for (int channel = 0; channel < channels; channel++)
            {
                final int tail = sampleAt (pcm, channels, end - n + i, channel);
                final int lead = sampleAt (pcm, channels, loopStart - n + i, channel);
                setSample (out, channels, end - n + i, channel, (int) Math.round (tail * (1.0 - weight) + lead * weight));
            }
        }
        return out;
    }


    private static void setSample (final byte [] pcm, final int channels, final int frame, final int channel, final int value)
    {
        final int clamped = value < -32768 ? -32768 : value > 32767 ? 32767 : value;
        final int idx = (frame * channels + channel) * 2;
        pcm[idx] = (byte) (clamped & 0xFF);
        pcm[idx + 1] = (byte) (clamped >> 8 & 0xFF);
    }


    /**
     * Build a unique sample name that is also stable for identical content: the (truncated) zone
     * name plus a 4-digit hash of the sample data, its playback parameters and the writer's layout
     * revision. The device re-uses an already imported multisample/sample when a newly imported one
     * has the same name - without a content-dependent name, re-importing a changed conversion
     * silently keeps playing the old sample data. With it, equal names imply equal bytes, so such
     * re-use is harmless.
     *
     * @param name The zone name
     * @param hash The content hash (audio + parameters + layout revision)
     * @param usedNames The names used so far (a found collision appends a counter)
     * @return The unique name (at most 16 characters)
     */
    private static String uniqueName (final String name, final int hash, final Set<String> usedNames)
    {
        final String base = name == null ? "Sample" : name.trim ();
        String candidate = contentName (base, hash);
        int counter = 1;
        while (!usedNames.add (candidate))
            candidate = contentName (base, hash + counter++);
        return candidate;
    }


    /**
     * Combine a name and a content hash into a 16 character sample/multisample name. A too long
     * name keeps its head and its tail (sample names typically end in the note, e.g. "..._D#3",
     * which is the part that tells the zones apart).
     *
     * @param base The descriptive name
     * @param hash The content hash
     * @return The name, at most 11 characters of the base plus '_' and 4 hash digits
     */
    private static String contentName (final String base, final int hash)
    {
        final String prefix = base.length () > 11 ? base.substring (0, 7) + base.substring (base.length () - 4) : base;
        return String.format ("%s_%04x", prefix, Integer.valueOf (hash & 0xFFFF));
    }


    private static int contentHash (final byte [] data)
    {
        final CRC32 crc = new CRC32 ();
        crc.update (data);
        return (int) crc.getValue ();
    }


    private static void writeFile (final File outputFile, final byte [] content) throws IOException
    {
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (content);
        }
    }
}
