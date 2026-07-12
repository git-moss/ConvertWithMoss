// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * Creator for Roland FANTOM / FANTOM-0 (and other ZEN-Core hardware) keyboard instruments. Writes a
 * <i>.svz</i> file: a single tone for one multi-sample (see {@link #createPreset}) or a multi-tone
 * bank sharing one sample pool for several (see {@link #createPresetLibrary}), loadable through the
 * device's <i>UTILITY &rarr; IMPORT &rarr; IMPORT TONE</i> function.
 *
 * <p>
 * User samples are written at the FANTOM's native 48 kHz / 16-bit; loops are snapped to zero
 * crossings and the post-loop tail is tapered so no playback boundary lands on a non-zero value,
 * which is the sample-preparation needed for click-free hardware playback.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreCreator extends AbstractCreator<ZenCoreCreatorUI>
{
    /**
     * The FANTOM plays user samples at its native rate; resample everything to it and to 16-bit.
     */
    private static final int                    SAMPLE_RATE    = 48000;
    private static final DestinationAudioFormat ZENCORE_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, SAMPLE_RATE, true);

    private static final int                    ZERO_SEARCH    = 1024;


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

        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, List.of (instrument), this.settingsConfiguration.getModelTag ()));
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

        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, instruments, this.settingsConfiguration.getModelTag ()));
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private SvzInstrument buildInstrument (final IMultisampleSource multisampleSource, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        // Scale loop/start/end positions to the destination rate (the audio is resampled below).
        recalculateSamplePositions (multisampleSource, SAMPLE_RATE);

        final SvzInstrument instrument = new SvzInstrument ();
        instrument.name = multisampleSource.getName ();
        ISampleZone representativeZone = null;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int sampleIndex = this.addSample (zone, pool, byContent, usedNames);
                if (sampleIndex <= 0)
                    continue;
                if (representativeZone == null)
                    representativeZone = zone;
                final int keyLow = Math.max (0, zone.getKeyLow ());
                final int keyHigh = Math.min (127, zone.getKeyHigh ());
                for (int key = keyLow; key <= keyHigh; key++)
                    instrument.keyToSample[key] = sampleIndex;
            }
        // The FANTOM tone has one filter + amp envelope per partial; take them from a
        // representative zone so converted tones keep the source's character (offsets validated
        // against the 2048 factory tones).
        if (representativeZone != null)
            applyToneParameters (instrument, representativeZone);
        return instrument;
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
            instrument.envDecay = timeToValue (env.getDecayTime ());
            final double release = env.getReleaseTime ();
            instrument.envRelease = release >= 0 ? timeToValue (release) : 150;
            final double holdLevel = env.getHoldLevel ();
            instrument.envHoldLevel = holdLevel < 0 ? 1023 : Math.clamp ((int) Math.round (holdLevel * 1023.0), 0, 1023);
            final double sustain = env.getSustainLevel ();
            instrument.envSustain = sustain < 0 ? 1023 : Math.clamp ((int) Math.round (sustain * 1023.0), 0, 1023);
        }
    }


    /**
     * Approximate the FANTOM TVA envelope time value (0-1023) from a time in seconds. Roland's
     * exact time table is not published; this log2 curve is calibrated so ~20 s maps to full scale
     * and near-instant times map to 0.
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
     * Convert a zone's audio to the FANTOM sample pool, re-using an already-added identical sample.
     *
     * @param zone The source sample zone
     * @param pool The pool to which to add the result
     * @param byContent The mapped indices of the already created SVZ samples
     * @param usedNames The names of the already created SVZ samples
     * @return The 1-based index into the pool, or 0 if the sample could not be added
     * @throws IOException Could not convert the sample to the target format
     */
    private int addSample (final ISampleZone zone, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final WaveFile waveFile = AudioFileUtils.convertToWav (zone.getSampleData (), ZENCORE_FORMAT);
        int channels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (channels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), zone.getName ());
            return 0;
        }
        byte [] pcm = waveFile.getDataChunk ().getData ();
        if (channels == 1)
        {
            pcm = monoToStereo (pcm);
            channels = 2;
        }
        final int frames = pcm.length / (2 * channels);
        if (frames <= 0)
            return 0;

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
            // Hardware sample-prep: snap both loop points to zero crossings and taper the tail so
            // no play-back boundary lands on a non-zero value (the FANTOM clicks on those).
            final int [] snapped = prepLoopForHardware (pcm, channels, loopStart, end, frames);
            loopStart = snapped[0];
            end = snapped[1];
        }

        final int rootKey = Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), 0, 127);

        // Re-use an identical sample mapped to several key ranges (detect duplicates by content +
        // parameters).
        final List<Object> contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (loopStart), Integer.valueOf (end), Boolean.valueOf (hasLoop), Integer.valueOf (rootKey));
        final Integer existing = byContent.get (contentKey);
        if (existing != null)
            return existing.intValue ();

        final SvzSample sample = new SvzSample ();
        sample.pcm = pcm;
        sample.rate = SAMPLE_RATE;
        sample.channels = channels;
        sample.name = uniqueName (zone.getName (), usedNames);
        sample.originalKey = rootKey;
        sample.level = Math.clamp ((int) Math.round (Math.pow (10, zone.getGain () / 20.0) * 127.0), 0, 127);
        sample.hasLoop = hasLoop;
        sample.loopStart = loopStart;
        sample.end = end;
        pool.add (sample);

        final int index = pool.size (); // 1-based
        byContent.put (contentKey, Integer.valueOf (index));
        return index;
    }


    /**
     * Snap the loop start and end to the nearest rising zero crossing (the sample closest to zero
     * across all channels) and taper the post-loop tail to zero.
     *
     * @param pcm The PCM data to edit
     * @param channels The number of channels
     * @param loopStart The start of the loop
     * @param loopEnd The end of the loop
     * @param frames The number of frames of the PCM data
     * @return {@code {newLoopStart, newLoopEnd}}
     */
    private static int [] prepLoopForHardware (final byte [] pcm, final int channels, final int loopStart, final int loopEnd, final int frames)
    {
        final int newStart = snapToZeroCrossing (pcm, channels, loopStart, frames);
        final int newEnd = snapToZeroCrossing (pcm, channels, loopEnd, frames);

        if (newEnd < frames - 1)
        {
            final int span = frames - newEnd;
            for (int frame = newEnd; frame < frames; frame++)
            {
                final double gain = (double) (frames - 1 - frame) / span;
                for (int channel = 0; channel < channels; channel++)
                {
                    final int idx = (frame * channels + channel) * 2;
                    final int value = (int) Math.round ((short) (pcm[idx] & 0xFF | pcm[idx + 1] << 8) * gain);
                    pcm[idx] = (byte) (value & 0xFF);
                    pcm[idx + 1] = (byte) (value >> 8 & 0xFF);
                }
            }
        }
        return new int []
        {
            newStart,
            newEnd
        };
    }


    private static int snapToZeroCrossing (final byte [] pcm, final int channels, final int position, final int frames)
    {
        final int lo = Math.max (1, position - ZERO_SEARCH);
        final int hi = Math.min (frames - 1, position + ZERO_SEARCH);
        int best = position;
        long bestCost = Long.MAX_VALUE;
        for (int frame = lo; frame < hi; frame++)
        {
            // Left channel crossing up through zero.
            if (!(sampleAt (pcm, channels, frame - 1, 0) < 0 && sampleAt (pcm, channels, frame, 0) >= 0))
                continue;
            long cost = 0;
            for (int channel = 0; channel < channels; channel++)
                cost += Math.abs (sampleAt (pcm, channels, frame, channel));
            if (cost < bestCost)
            {
                bestCost = cost;
                best = frame;
            }
        }
        return best;
    }


    private static int sampleAt (final byte [] pcm, final int channels, final int frame, final int channel)
    {
        final int idx = (frame * channels + channel) * 2;
        return (short) (pcm[idx] & 0xFF | pcm[idx + 1] << 8);
    }


    private static byte [] monoToStereo (final byte [] mono)
    {
        final int frames = mono.length / 2;
        final byte [] stereo = new byte [frames * 4];
        for (int f = 0; f < frames; f++)
        {
            stereo[f * 4] = mono[f * 2];
            stereo[f * 4 + 1] = mono[f * 2 + 1];
            stereo[f * 4 + 2] = mono[f * 2];
            stereo[f * 4 + 3] = mono[f * 2 + 1];
        }
        return stereo;
    }


    private static String uniqueName (final String name, final Set<String> usedNames)
    {
        String base = name == null ? "Sample" : name.trim ();
        if (base.length () > 16)
            base = base.substring (0, 16);
        String candidate = base;
        int counter = 1;
        while (!usedNames.add (candidate))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            candidate = base.substring (0, Math.min (base.length (), 16 - suffix.length ())) + suffix;
        }
        return candidate;
    }


    private static void writeFile (final File outputFile, final byte [] content) throws IOException
    {
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (content);
        }
    }
}
