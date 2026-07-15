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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzInstrument;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzPartial;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzSample;


/**
 * Creator for Roland FANTOM / FANTOM-0 (and other ZEN-Core hardware) keyboard instruments. Writes a
 * <i>.svz</i> file: a single tone for one multi-sample (see {@link #createPreset}) or a multi-tone
 * bank sharing one sample pool for several (see {@link #createPresetLibrary}), loadable through the
 * device's <i>UTILITY &rarr; IMPORT &rarr; IMPORT TONE</i> function.
 *
 * <p>
 * User samples are written at the FANTOM's native 48 kHz / 16-bit. The voice engine has no loop
 * smoothing of its own, so click-free playback is baked into the samples: the loop end is re-seated
 * to a period-aligned point ({@link #optimizeLoopEnd}), a loop whose wrap still is not seamless
 * gets its tail cross-faded into the loop-start lead-in ({@link #crossfadeLoop}), and guard frames
 * of the loop-start continuation are stored past the loop end for the engine's interpolation
 * look-ahead.
 * </p>
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreCreator extends AbstractCreator<EmptySettingsUI>
{
    /**
     * The FANTOM's native user-sample rate is 48 kHz (its own sample exports are 48 kHz); a sample
     * left at another rate is re-sampled by the device on playback, which aliases bright content (a
     * near-clipping square is heard as a "corrupt", harsh region). User samples are therefore
     * high-quality re-sampled to 48 kHz / 16-bit here, and the loop end is re-seated to a
     * period-aligned point afterwards ({@link #optimizeLoopEnd}) so re-sampling does not leave the
     * loop off its seamless wrap.
     */
    private static final DestinationAudioFormat ZENCORE_FORMAT         = new DestinationAudioFormat (new int []
    {
        16
    }, 48000, true);
    /** The FANTOM's native user-sample rate; loop and play positions are rescaled to it. */
    private static final int                    SAMPLE_RATE            = 48000;

    /**
     * A loop whose wrap discontinuity (value or slope, of 32768 full-scale) is at or below this is
     * left untouched - a hand-tuned, period-aligned loop (e.g. from an SF2) stays pristine. Above
     * it, the loop end is nudged to a period-aligned point and, if the wrap still is not seamless,
     * the loop tail is cross-faded. The threshold is deliberately small because audibility is
     * relative to the material: bright content (its own samples move further per frame) masks a
     * wrap step of ~50, but in a smooth pad the same step is a clearly audible tick on every loop
     * pass, so only a wrap that is seamless for <i>any</i> material is left alone.
     */
    private static final int                    LOOP_SEAM_TOLERANCE    = 16;
    /** How far (in frames) the loop end may be moved to find a seamless, period-aligned wrap. */
    private static final int                    LOOP_END_SEARCH        = 1024;
    /**
     * Number of guard frames stored after a loop end: copies of the loop-start continuation, so the
     * voice engine has valid look-ahead across the loop wrap. The device's own looped samples carry
     * such frames (their played-sample count runs a handful of frames past the loop end); without
     * them the interpolator reads past the data at each wrap - the second interleaved (right)
     * channel falls off the end first, which is heard as a per-wrap click on the right side.
     */
    private static final int                    LOOP_GUARD_FRAMES      = 16;
    /**
     * Length (in frames) of the loop cross-fade applied only when a period-aligned loop end is
     * still not seamless - an evolving pad whose timbre drifts across the loop has no phase-aligned
     * end. About 21 ms at 48 kHz. Bright, already-seamless loops never reach this, so their audio
     * is left bit-exact.
     */
    private static final int                    LOOP_CROSSFADE         = 1024;
    /**
     * Salted into the content-hashed sample names ({@link #uniqueName}): bump when the written
     * sample layout changes (SMPd header fields, guard scheme, ...), so a re-import loads the new
     * bytes instead of the device re-using a same-named sample written by an older layout.
     */
    private static final int                    SAMPLE_FORMAT_REVISION = 8;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ZenCoreCreator (final INotifier notifier)
    {
        super ("Roland ZEN-Core", "ZenCore", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<SvzSample> pool = new ArrayList<> ();
        final Map<Object, Integer> byContent = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        final SvzInstrument instrument = this.buildInstrument (multisampleSource, pool, byContent, usedNames, assignToneNames (Collections.singletonList (multisampleSource.getName ())).get (0));
        if (instrument == null)
            return;
        ensureLoadableSamplePool (pool, byContent, usedNames);

        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (multisampleSource.getName ()), "svz");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());
        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, List.of (instrument)));
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

        // The library name is free text from the user interface; typed with the ".svz" ending it
        // would otherwise end up as "name_svz.svz". The backend sanitizes the name before it
        // reaches this creator (the dot becomes an underscore), so both spellings are handled.
        String name = libraryName.trim ();
        if (name.length () > 4 && (name.regionMatches (true, name.length () - 4, ".svz", 0, 4) || name.regionMatches (true, name.length () - 4, "_svz", 0, 4)))
            name = name.substring (0, name.length () - 4);

        final List<SvzSample> pool = new ArrayList<> ();
        final Map<Object, Integer> byContent = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        final List<SvzInstrument> instruments = new ArrayList<> ();
        final List<String> sourceNames = new ArrayList<> ();
        for (final IMultisampleSource source: multisampleSources)
            sourceNames.add (source.getName ());
        final List<String> toneNames = assignToneNames (sourceNames);
        // One broken source (e.g. an unreadable sample file) must not lose the whole library -
        // report it and continue with the remaining sources.
        for (int i = 0; i < multisampleSources.size (); i++)
        {
            final IMultisampleSource source = multisampleSources.get (i);
            try
            {
                final SvzInstrument instrument = this.buildInstrument (source, pool, byContent, usedNames, toneNames.get (i));
                if (instrument != null)
                    instruments.add (instrument);
            }
            catch (final IOException | RuntimeException ex)
            {
                this.notifier.logError ("IDS_ZENCORE_SOURCE_FAILED", source.getName ());
                this.notifier.logError (ex);
            }
        }
        if (instruments.isEmpty ())
        {
            this.notifier.logError ("IDS_ZENCORE_EMPTY_SOURCE", name);
            return;
        }
        ensureLoadableSamplePool (pool, byContent, usedNames);

        final File outputFile = this.createUniqueFilename (destinationFolder, createSafeFilename (name), "svz");
        this.notifier.log ("IDS_ZENCORE_WRITING_BANK", name, Integer.toString (instruments.size ()));
        writeFile (outputFile, ZenCoreSvz.buildSvz (pool, instruments));
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private SvzInstrument buildInstrument (final IMultisampleSource multisampleSource, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames, final String toneName) throws IOException
    {
        // The audio is resampled to 48 kHz (see ZENCORE_FORMAT), so scale the loop/start/end
        // frames to it; the loop end is then re-seated to a period-aligned point per sample in
        // addSample.
        recalculateSamplePositions (multisampleSource, SAMPLE_RATE);

        final SvzInstrument instrument = new SvzInstrument ();
        instrument.name = toneName;
        // The whole instrument is stereo if any of its zones is: one oscillator plays all its
        // samples and its stereo mode is per-tone, so a stereo instrument stores every sample
        // interleaved (mono zones duplicated) and a mono instrument stores every sample mono.
        instrument.stereo = hasStereoZone (multisampleSource);

        // Distinct source velocity ranges are each mapped onto their own partial(s) - up to four
        // mono or two stereo layers, the engine's four-partial ceiling - the way the other formats
        // map velocity layers automatically. A single velocity range folds into one mono/stereo tone.
        final List<List<ISampleZone>> layers = collectVelocityLayers (multisampleSource);
        if (layers.size () >= 2)
        {
            this.buildVelocityLayeredInstrument (instrument, layers, pool, byContent, usedNames);
            if (instrument.partials.isEmpty ())
            {
                this.notifier.logError ("IDS_ZENCORE_EMPTY_SOURCE", multisampleSource.getName ());
                return null;
            }
            return instrument;
        }

        final List<ISampleZone> zones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            zones.addAll (group.getSampleZones ());
        final ISampleZone representativeZone = this.fillKeyMaps (zones, instrument.keyToSample, instrument.keyToSampleRight, instrument.stereo, pool, byContent, usedNames);
        // A source without a single convertible sample would import as a silent husk tone - skip
        // it instead (and report it; the file or library keeps only real instruments).
        if (representativeZone == null)
        {
            this.notifier.logError ("IDS_ZENCORE_EMPTY_SOURCE", multisampleSource.getName ());
            return null;
        }
        // The FANTOM tone has one filter + amp envelope per partial; take them from a
        // representative zone so converted tones keep the source's character (offsets validated
        // against the 2048 factory tones).
        applyToneParameters (instrument, representativeZone);

        // Name the multi-sample(s) by content (key map + the content-hashed sample names). A stereo
        // instrument has a second, right-channel multi-sample for its second (hard-right) partial.
        instrument.multisampleName = contentName (instrument.name, contentHash (keyMapContent ("L", instrument.keyToSample, pool)));
        if (instrument.stereo)
            instrument.multisampleNameRight = contentName (instrument.name, contentHash (keyMapContent ("R", instrument.keyToSampleRight, pool)));
        return instrument;
    }


    /**
     * Add each zone's audio to the pool and map its key range onto the resulting sample indices. The
     * zones are processed in order, so re-used samples fold together and a later zone wins a shared
     * key (velocity flattening for the single-layer case; one velocity layer at a time otherwise).
     *
     * @param zones The zones to add, in order
     * @param keyToSample The 128-key left/only sample map to fill (1-based indices)
     * @param keyToSampleRight The 128-key right-channel sample map to fill (stereo)
     * @param stereo Whether the instrument is stereo (split stereo zones into two mono samples)
     * @param pool The shared sample pool
     * @param byContent The content de-duplication map
     * @param usedNames The sample names used so far
     * @return The first successfully added zone (a representative for the tone parameters), or null
     * @throws IOException Could not convert a sample
     */
    private ISampleZone fillKeyMaps (final List<ISampleZone> zones, final int [] keyToSample, final int [] keyToSampleRight, final boolean stereo, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        ISampleZone representativeZone = null;
        for (final ISampleZone zone: zones)
        {
            final int [] sampleIndexes = this.addSample (zone, stereo, pool, byContent, usedNames);
            if (sampleIndexes == null)
                continue;
            if (representativeZone == null)
                representativeZone = zone;
            final int keyLow = Math.max (0, zone.getKeyLow ());
            final int keyHigh = Math.min (127, zone.getKeyHigh ());
            for (int key = keyLow; key <= keyHigh; key++)
            {
                keyToSample[key] = sampleIndexes[0];
                keyToSampleRight[key] = sampleIndexes[1];
            }
        }
        return representativeZone;
    }


    /**
     * Group the source's zones by their velocity range - one bucket per distinct range, ordered from
     * the lowest velocity up. A single bucket (the usual case, every zone spanning the full velocity
     * range) means there is nothing to layer.
     *
     * @param multisampleSource The source
     * @return The zone buckets ordered by ascending velocity (fewer than two if not layered)
     */
    private static List<List<ISampleZone>> collectVelocityLayers (final IMultisampleSource multisampleSource)
    {
        final Map<Integer, List<ISampleZone>> byRange = new LinkedHashMap<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Integer key = Integer.valueOf (zone.getVelocityLow () << 8 | zone.getVelocityHigh () & 0xFF);
                byRange.computeIfAbsent (key, k -> new ArrayList<> ()).add (zone);
            }
        final List<List<ISampleZone>> layers = new ArrayList<> (byRange.values ());
        layers.sort ( (a, b) -> Integer.compare (a.get (0).getVelocityLow (), b.get (0).getVelocityLow ()));
        return layers;
    }


    /**
     * Build a velocity-layered tone: each velocity range becomes one partial (mono, centred) or two
     * partials (stereo, hard-panned left/right). The engine has four partials, so at most four mono
     * or two stereo layers fit; any excess layers are merged into the top one (with a warning) so no
     * velocity is left silent. The outermost velocity bounds are opened to 1 and 127 so the whole
     * range triggers a layer.
     *
     * @param instrument The instrument to populate (its {@code partials} list)
     * @param layers The velocity-ordered zone buckets (at least two)
     * @param pool The shared sample pool
     * @param byContent The content de-duplication map
     * @param usedNames The sample names used so far
     * @throws IOException Could not convert a sample
     */
    private void buildVelocityLayeredInstrument (final SvzInstrument instrument, final List<List<ISampleZone>> layers, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
    {
        final boolean stereo = instrument.stereo;
        final int maxLayers = stereo ? 2 : 4;
        List<List<ISampleZone>> effectiveLayers = layers;
        if (layers.size () > maxLayers)
        {
            this.notifier.logError ("IDS_ZENCORE_VELOCITY_LAYER_CAP", Integer.toString (layers.size ()), Integer.toString (maxLayers));
            effectiveLayers = new ArrayList<> (layers.subList (0, maxLayers - 1));
            final List<ISampleZone> merged = new ArrayList<> ();
            for (int i = maxLayers - 1; i < layers.size (); i++)
                merged.addAll (layers.get (i));
            effectiveLayers.add (merged);
        }

        final List<SvzPartial> partials = new ArrayList<> ();
        ISampleZone representativeZone = null;
        for (int li = 0; li < effectiveLayers.size (); li++)
        {
            final List<ISampleZone> layerZones = effectiveLayers.get (li);
            final int velLow = li == 0 ? 1 : Math.clamp (layerZones.get (0).getVelocityLow (), 1, 127);
            final int velHigh = li == effectiveLayers.size () - 1 ? 127 : Math.clamp (layerZones.get (0).getVelocityHigh (), 1, 127);
            final int [] keyLeft = new int [128];
            final int [] keyRight = new int [128];
            final ISampleZone rep = this.fillKeyMaps (layerZones, keyLeft, keyRight, stereo, pool, byContent, usedNames);
            if (rep == null)
                continue;
            if (representativeZone == null)
                representativeZone = rep;
            if (stereo)
            {
                partials.add (makePartial (keyLeft, -64, velLow, velHigh, instrument.name, "L" + li, pool));
                partials.add (makePartial (keyRight, 63, velLow, velHigh, instrument.name, "R" + li, pool));
            }
            else
                partials.add (makePartial (keyLeft, 0, velLow, velHigh, instrument.name, "P" + li, pool));
        }
        instrument.partials = partials;
        if (representativeZone != null)
            applyToneParameters (instrument, representativeZone);
    }


    /**
     * Create one partial of a velocity-layered tone from a key map, pan and velocity window, naming
     * its multi-sample by content so identical layers fold together and a changed layer re-imports.
     *
     * @param keyToSample The 128-key sample map for this partial (copied)
     * @param pan The pan -64 (hard left) .. 0 (centre) .. +63 (hard right)
     * @param velLow The velocity range lower bound 1-127
     * @param velHigh The velocity range upper bound 1-127
     * @param name The tone name (the multi-sample name's descriptive part)
     * @param channelTag A per-partial tag salting the content hash so partials get distinct names
     * @param pool The shared sample pool (for the content-hashed sample names)
     * @return The partial
     */
    private static SvzPartial makePartial (final int [] keyToSample, final int pan, final int velLow, final int velHigh, final String name, final String channelTag, final List<SvzSample> pool)
    {
        final SvzPartial partial = new SvzPartial ();
        System.arraycopy (keyToSample, 0, partial.keyToSample, 0, 128);
        partial.pan = pan;
        partial.velLow = velLow;
        partial.velHigh = velHigh;
        partial.multisampleName = contentName (name, contentHash (keyMapContent (channelTag, keyToSample, pool)));
        return partial;
    }


    /**
     * Shorten each source name to the 16 characters the PATa name field holds (the name the
     * device displays), keeping the names recognizable: a name whose plain truncation is unique
     * keeps it, and names that would truncate identically - e.g. "082_RTW2_106_BASS_SAW" and
     * "..._SQR", whose distinguishing part is cut off - get part of the shared head elided with a
     * '~' and keep their distinctive tail instead ("082_RTW2_106~SAW" / "082_RTW2_106~SQR").
     * Whatever still collides (identical source names) falls back to a ~2, ~3, ... counter.
     *
     * @param sourceNames The source names in bank order
     * @return One unique tone name (at most 16 characters) per source, in the same order
     */
    private static List<String> assignToneNames (final List<String> sourceNames)
    {
        final List<String> bases = new ArrayList<> ();
        for (final String name: sourceNames)
            bases.add (name == null || name.isBlank () ? "Tone" : name.trim ());

        final List<String> result = new ArrayList<> ();
        for (final String base: bases)
            result.add (base.length () <= 16 ? base : base.substring (0, 16));

        // Give the members of each identically-truncating group the shortest tail of their full
        // name that tells them apart (a member that fits untruncated keeps its exact name)
        final Map<String, List<Integer>> groups = new LinkedHashMap<> ();
        for (int i = 0; i < result.size (); i++)
            groups.computeIfAbsent (result.get (i), k -> new ArrayList<> ()).add (Integer.valueOf (i));
        for (final List<Integer> group: groups.values ())
        {
            if (group.size () < 2)
                continue;
            for (int tailLength = 3; tailLength <= 14; tailLength++)
            {
                final Set<String> tails = new HashSet<> ();
                for (final Integer index: group)
                {
                    final String base = bases.get (index.intValue ());
                    tails.add (base.substring (Math.max (0, base.length () - tailLength)));
                }
                if (tails.size () < group.size ())
                    continue;
                for (final Integer index: group)
                {
                    final String base = bases.get (index.intValue ());
                    if (base.length () <= 16)
                        continue;
                    final String tail = base.substring (base.length () - tailLength);
                    result.set (index.intValue (), base.substring (0, 15 - tailLength) + "~" + tail);
                }
                break;
            }
        }

        // Whatever still collides (identical source names) gets a counter
        final Set<String> used = new HashSet<> ();
        for (int i = 0; i < result.size (); i++)
        {
            final String name = result.get (i);
            String candidate = name;
            int counter = 2;
            while (!used.add (candidate))
            {
                final String suffix = "~" + counter++;
                candidate = name.substring (0, Math.min (name.length (), 16 - suffix.length ())) + suffix;
            }
            result.set (i, candidate);
        }
        return result;
    }


    private static boolean hasStereoZone (final IMultisampleSource multisampleSource)
    {
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
                try
                {
                    if (zone.getSampleData ().getAudioMetadata ().getChannels () > 1)
                        return true;
                }
                catch (final IOException _)
                {
                    // Ignore - the sample is converted (and its channel count re-checked) in
                    // addSample.
                }
        return false;
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
            // Never write an attack time below 8: the voice engine produces a transient burst
            // at note-on, hardware-measured on a FANTOM-0 with a calibration bank as INDEPENDENT
            // of the sample content (samples starting at 100 and at 30000 of full scale record
            // byte-alike onsets - the engine fades the sample data start itself) and shaped only
            // by the attack value: ~91 residual at attack 1 (an audible tick in quiet material),
            // ~25 at attack 8 (below audibility with margin), ~13 at 16, ~5 at 32. Attack 8 is
            // also the value found by ear on the device for punchy basses, so transients stay
            // tight.
            instrument.envAttack = Math.max (8, timeToValue (env.getAttackTime ()));
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

        // Pitch and TVF (filter) modulation envelopes (written only when the source actually
        // modulates - a zero depth keeps the template default). Both scales are HARDWARE-CALIBRATED
        // (DEPTHCAL banks on a FANTOM-0). PITCH (280, +/-63): the model depth is semitones/120 and
        // the device pitch envelope shifts ~0.42 semitone per depth unit, so 120/0.42 ~= 280 units
        // per unit depth, clamped to the PCM partial's +/-63 (audible shift itself saturates ~+22 st).
        // FILTER (75, +/-100): the device cutoff scales 150 units per octave (8 cents/unit) and the
        // filter envelope moves cutoff ~20 units per depth unit = 160 cents/unit; the model depth is
        // cents/12000, so 12000/160 ~= 75 units per unit depth.
        applyModulationEnvelope (zone.getPitchEnvelopeModulator (), 280, 63, true, instrument);
        if (optFilter.isPresent ())
            applyModulationEnvelope (optFilter.get ().getCutoffEnvelopeModulator (), 75, 100, false, instrument);
    }


    /**
     * Carry a pitch or filter modulation envelope (shape + depth) into the tone. The device stores
     * the envelope as a signed depth plus a 4-time / 5-level shape; the shape is the standard
     * attack-to-peak / decay-to-sustain / release-to-centre, scaled by the depth.
     *
     * @param modulator The source envelope modulator (its depth is the modulation amount)
     * @param depthScale The device units written per unit of source depth (before clamping)
     * @param depthMax The device depth clamp (+/- this value)
     * @param pitch True for the pitch envelope, false for the TVF (filter) envelope
     * @param instrument The instrument to update
     */
    private static void applyModulationEnvelope (final IEnvelopeModulator modulator, final int depthScale, final int depthMax, final boolean pitch, final SvzInstrument instrument)
    {
        if (modulator == null)
            return;
        final double depth = modulator.getDepth ();
        final IEnvelope env = modulator.getSource ();
        // No meaningful modulation - leave the template default so nothing is clobbered.
        if (env == null || Double.isNaN (depth) || Math.abs (depth) < 0.005)
            return;

        final double hold = env.getHoldTime ();
        final double release = env.getReleaseTime ();
        final int [] times =
        {
            timeToValue (env.getAttackTime ()),
            hold > 0 ? timeToValue (hold) : 0,
            timeToValue (env.getDecayTime ()),
            release >= 0 ? timeToValue (release) : 150
        };
        final double sustain = env.getSustainLevel ();
        final int sustainLevel = sustain < 0 ? 1023 : Math.clamp ((int) Math.round (sustain * 1023.0), 0, 1023);
        // Shape: start at centre, attack to full, hold, decay to sustain, release back to centre.
        final int [] levels =
        {
            0, 1023, 1023, sustainLevel, 0
        };
        final int depthValue = Math.clamp ((int) Math.round (depth * depthScale), -depthMax, depthMax);
        if (pitch)
        {
            instrument.pitchEnvDepth = depthValue;
            instrument.pitchEnvTimes = times;
            instrument.pitchEnvLevels = levels;
        }
        else
        {
            instrument.filterEnvDepth = depthValue;
            instrument.filterEnvTimes = times;
            instrument.filterEnvLevels = levels;
        }
    }


    /**
     * The FANTOM envelope-time law, hardware-calibrated on a FANTOM-0: a bank of tones with exact,
     * patched TVA release values was recorded on the device and the exponential fades fitted. Each
     * anchor pair is the time value and its measured audible stage span (the time to reach -40 dB;
     * the separately measured attack-rise anchors agree within ~25%). The stage times are
     * interpolated log-linearly between the anchors; the last pair extrapolates the measured curve
     * to full scale. Roland's exact table is not published - the earlier log2 approximation
     * overstated times several-fold below ~value 800 (a 0.5 s release was written as value 129,
     * which really plays ~0.2 s, and everything below ~0.3 s collapsed to value 0).
     */
    private static final int []                 TIME_VALUE_ANCHORS     =
    {
        0,
        8,
        32,
        75,
        129,
        256,
        512,
        800,
        1023
    };
    /** The measured stage time in seconds per anchor of {@link #TIME_VALUE_ANCHORS}. */
    private static final double []              TIME_SECOND_ANCHORS    =
    {
        0.010,
        0.020,
        0.060,
        0.120,
        0.200,
        0.390,
        1.240,
        6.190,
        21.5
    };


    /**
     * Convert an envelope time in seconds to the FANTOM 0-1023 time value with the
     * hardware-calibrated law (see {@link #TIME_VALUE_ANCHORS}).
     *
     * @param seconds The time in seconds
     * @return The 0-1023 time value
     */
    private static int timeToValue (final double seconds)
    {
        if (seconds <= TIME_SECOND_ANCHORS[0])
            return 0;
        final int last = TIME_SECOND_ANCHORS.length - 1;
        if (seconds >= TIME_SECOND_ANCHORS[last])
            return TIME_VALUE_ANCHORS[last];
        int i = 1;
        while (TIME_SECOND_ANCHORS[i] < seconds)
            i++;
        final double s0 = TIME_SECOND_ANCHORS[i - 1];
        final double s1 = TIME_SECOND_ANCHORS[i];
        final int v0 = TIME_VALUE_ANCHORS[i - 1];
        final int v1 = TIME_VALUE_ANCHORS[i];
        return (int) Math.round (v0 + (v1 - v0) * Math.log (seconds / s0) / Math.log (s1 / s0));
    }


    /**
     * Convert a zone's audio to the FANTOM sample pool, re-using already-added identical samples.
     * Everything is stored as mono samples: a mono instrument keeps its zones mono; a stereo
     * instrument splits each stereo zone into two mono samples (left and right) sharing one common
     * loop, played by the tone's two hard-panned partials - each channel is then a mono loop, which
     * avoids the loop-wrap click that the engine gives an interleaved-stereo sample.
     *
     * @param zone The source sample zone
     * @param storeStereo Whether the instrument is stereo (split a stereo zone into two mono
     *            samples)
     * @param pool The pool to which to add the result
     * @param byContent The mapped indices of the already created SVZ samples
     * @param usedNames The names of the already created SVZ samples
     * @return The 1-based left and right pool indexes (equal for a mono sample), or null on failure
     * @throws IOException Could not convert the sample to the target format
     */
    private int [] addSample (final ISampleZone zone, final boolean storeStereo, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames) throws IOException
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

        // Keep the source's loop points and audio untouched - no re-sampling, no zero-crossing snap
        // - so a period-aligned loop stays seamless across the wrap (see ZENCORE_FORMAT). When the
        // wrap is not already seamless the loop end is nudged (never the audio) to the nearest
        // point whose waveform matches the loop start, so the wrap becomes seamless without
        // altering the sample.
        // The loop preparation runs on the interleaved audio so both channels share one loop end.
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
            // A loop starting at the very first frames - a whole-file loop, or a loop start the
            // zero-crossing snap processing option moved there - has no lead-in, which the seam
            // machinery below measures against (the waveform's own step into the loop start at
            // loopStart-1/-2). If such a loop's wrap is not already seamless, advance the loop
            // start into the sample: the skipped frames still play once before the first wrap and
            // the loop end is then re-seated / cross-faded against the advanced start as usual. An
            // already-seamless wrap - e.g. a device export's own whole-file loop - is left
            // untouched, keeping such round-trips byte-identical.
            if (loopStart < 2 && end - loopStart >= 64 && startWrapDiscontinuity (pcm, channels, loopStart, end) > LOOP_SEAM_TOLERANCE)
                loopStart += Math.min (LOOP_CROSSFADE, (end - loopStart) / 4);
            end = optimizeLoopEnd (pcm, channels, loopStart, end, frames);
            // If period-alignment still cannot make the wrap seamless - an evolving pad whose
            // timbre drifts across the loop has no phase-aligned end - cross-fade the loop tail
            // into the loop-start lead-in. Safe here precisely because the end is period-aligned
            // first: the two blended stretches are in phase and reinforce, so the amplitude
            // collapse an unaligned cross-fade caused on bright content does not happen.
            // Already-seamless loops are skipped.
            if (loopStart >= 2 && wrapDiscontinuity (pcm, channels, loopStart, end) > LOOP_SEAM_TOLERANCE)
            {
                // The fade can only be as long as the lead-in, and it closes the wrap mismatch by
                // roughly 1/(fade length) - a loop starting only a few dozen frames in leaves an
                // audible residual step (hardware-heard: a loop from frame 70 capped the fade at
                // 70 frames and its ~4400 mismatch left a residual of ~63 that ticked every
                // pass). When the lead-in is the limiting factor, advance the loop start to give
                // the fade full room - the skipped frames still play once - and re-seat the loop
                // end for the new start.
                if (loopStart < LOOP_CROSSFADE && loopStart < (end - loopStart) / 4)
                {
                    loopStart += Math.min (LOOP_CROSSFADE, (end - loopStart) / 4);
                    end = optimizeLoopEnd (pcm, channels, loopStart, end, frames);
                }
                if (wrapDiscontinuity (pcm, channels, loopStart, end) > LOOP_SEAM_TOLERANCE)
                    pcm = crossfadeLoop (pcm, channels, loopStart, end);
            }
        }

        // Store the played part [0, end) plus, for a loop, LOOP_GUARD_FRAMES of the loop-start
        // continuation as guard frames past the loop end (the device carries these on its own
        // loops).
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
        // A stereo instrument splits the zone into two mono samples (left, right); a mono
        // instrument just keeps the left channel. Both mono samples share the loop end computed
        // above.
        final int left = addPooledSample (extractChannel (pcm, 2, 0), rate, zone.getName () + "_L", rootKey, level, hasLoop, loopStart, end, pool, byContent, usedNames);
        if (!storeStereo)
            return new int []
            {
                left,
                left
            };
        return new int []
        {
            left,
            addPooledSample (extractChannel (pcm, 2, 1), rate, zone.getName () + "_R", rootKey, level, hasLoop, loopStart, end, pool, byContent, usedNames)
        };
    }


    /**
     * Add a mono sample to the pool, re-using an already-added identical one (dedupe by content and
     * parameters - e.g. the same sample mapped to several key ranges folds back into one). All
     * pooled samples are mono: a stereo zone is split into two mono samples before this is called.
     *
     * @param pcm The PCM data
     * @param rate The sample rate
     * @param zoneName The name of the zone
     * @param rootKey The root key of the sample
     * @param level The level of the sample
     * @param hasLoop Is the loop enabled?
     * @param loopStart The loop start frame
     * @param end The end of the sample
     * @param pool The pool with the already created samples
     * @param byContent The content de-duplication map
     * @param usedNames The sample names used so far
     * @return The 1-based index into the pool
     */
    private static int addPooledSample (final byte [] pcm, final int rate, final String zoneName, final int rootKey, final int level, final boolean hasLoop, final int loopStart, final int end, final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames)
    {
        final Object contentKey = List.of (ByteBuffer.wrap (pcm), Integer.valueOf (loopStart), Integer.valueOf (end), Boolean.valueOf (hasLoop), Integer.valueOf (rootKey), Integer.valueOf (level));
        final Integer existing = byContent.get (contentKey);
        if (existing != null)
            return existing.intValue ();

        // The name hash covers everything the device caches under the sample name: the audio, the
        // play-back parameters and the writer's layout revision (see SAMPLE_FORMAT_REVISION).
        final ByteBuffer parameters = ByteBuffer.allocate (32);
        parameters.putInt (SAMPLE_FORMAT_REVISION).putInt (loopStart).putInt (end).putInt (hasLoop ? 1 : 0);
        parameters.putInt (rootKey).putInt (level).putInt (rate);
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
     * The wave data of the LAST sample chunk in the pool does not reliably bind on import: the
     * multisample maps and displays correctly, yet the last sample shows an empty waveform and
     * plays silent on every key (hardware-verified on a FANTOM-0 - a probe bank's six-sample pool
     * imported with its last sample dead on every attempt, and identical content with an inert
     * trailing sample appended played; a pool holding exactly one sample, whose only sample IS the
     * last, was the first sighting of the same rule). An inert spacer sample - a few hundred bytes
     * of silence assigned to no key - is therefore always appended, so no real sample sits in the
     * last slot.
     *
     * @param pool The sample pool
     * @param byContent The content de-duplication map
     * @param usedNames The sample names used so far
     */
    private static void ensureLoadableSamplePool (final List<SvzSample> pool, final Map<Object, Integer> byContent, final Set<String> usedNames)
    {
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
     * cycles and the wrap is seamless. The sample data is left bit-exact - unlike a cross-fade,
     * which writes a distorted, amplitude-cancelled region into the audio whenever the loop-end and
     * loop-start phases differ (blending two out-of-phase copies of a bright waveform partly
     * cancels them). A loop whose source points are already seamless is returned unchanged.
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
        // waveform's own step into the loop start (see wrapDiscontinuity). Ties are broken towards
        // the
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
     * repeats the waveform's own step into the loop start. The click is the value and slope
     * mismatch of the loop tail against the loop-start lead-in - measuring the raw
     * {@code |L[loopEnd-1] -
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
     * The wrap discontinuity of a loop that starts at the very first frames, where the lead-in
     * frames {@link #wrapDiscontinuity} measures against do not exist: the missing lead is
     * extrapolated from the waveform's step out of the loop start instead. Only used to decide
     * whether such a loop needs its start advanced (see addSample) so the regular seam machinery
     * can run; a wrap that already follows the waveform's own progression - a period-aligned
     * whole-file loop, like the device's own exports - stays untouched.
     *
     * @param pcm The interleaved 16-bit little-endian PCM
     * @param channels The number of channels
     * @param loopStart The loop start frame (0 or 1)
     * @param loopEnd The loop end frame (at least loopStart + 3)
     * @return The largest value / slope deviation across the wrap
     */
    private static int startWrapDiscontinuity (final byte [] pcm, final int channels, final int loopStart, final int loopEnd)
    {
        int discontinuity = 0;
        for (int channel = 0; channel < channels; channel++)
        {
            final int startValue = sampleAt (pcm, channels, loopStart, channel);
            final int startSlope = sampleAt (pcm, channels, loopStart + 1, channel) - startValue;
            final int endValue = sampleAt (pcm, channels, loopEnd - 1, channel);
            final int endSlope = endValue - sampleAt (pcm, channels, loopEnd - 2, channel);
            discontinuity = Math.max (discontinuity, Math.max (Math.abs (endValue - (startValue - startSlope)), Math.abs (endSlope - startSlope)));
        }
        return discontinuity;
    }


    /**
     * Build the stored PCM: the played part {@code [0, end)} and, for a loop,
     * {@link #LOOP_GUARD_FRAMES} guard frames past the loop end, each a copy of the matching
     * loop-start-continuation frame.
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
        // continuation - the very frames the loop wraps to. The voice engine's look-ahead across
        // the
        // wrap then sees the loop restart (not audio running on past the loop end), so the
        // interpolated
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
     * frames ending at the loop start, so the loop tail gradually becomes the loop-start lead-in
     * and the wrap is seamless even when no single end point is (an evolving pad). It is only ever
     * called on a period-aligned loop, so the two stretches are in phase and blend without the
     * amplitude cancellation an unaligned cross-fade causes on bright material. A linear
     * (equal-gain) blend is used because in-phase signals add coherently.
     *
     * @param pcm The interleaved 16-bit little-endian PCM (never modified)
     * @param channels The number of channels
     * @param loopStart The loop start frame
     * @param end The period-aligned loop end frame
     * @return A copy of the PCM with the cross-faded loop tail, or the input if the loop is too
     *         short
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
        final int clamped = Math.clamp (value, -32768, 32767);
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
