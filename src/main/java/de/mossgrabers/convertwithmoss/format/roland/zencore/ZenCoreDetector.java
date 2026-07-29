// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.zencore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.roland.zencore.ZenCoreSvz.SvzInstrument;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Roland ZEN-Core <i>.SVZ</i> tone/sample packs: it reads the user samples of the shared
 * <i>USPa</i>/<i>USDa</i> pool and, when present, the <i>MSPa</i> multi-sample key map (turning it
 * into key-ranged zones). The <i>.SVZ</i> container is shared across the whole FANTOM range and the
 * wider ZEN-Core hardware line, so a file written by {@link ZenCoreCreator} round-trips back
 * through this detector.
 *
 * @author Jürgen Moßgraber
 */
public class ZenCoreDetector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public ZenCoreDetector (final INotifier notifier)
    {
        super ("Roland ZEN-Core", "ZenCore", notifier, new MetadataSettingsUI ("ZenCore"), ".svz");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            return this.readSvz (sourceFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readSvz (final File svzFile) throws IOException
    {
        final ZenCoreContainer container = new ZenCoreContainer (Files.readAllBytes (svzFile.toPath ()));
        final List<ZenCoreSample> samples = ZenCoreSvz.readSamples (container);
        if (samples.isEmpty ())
        {
            this.notifier.logError ("IDS_ZENCORE_NO_USER_SAMPLES", svzFile.getName ());
            return Collections.emptyList ();
        }

        // A bank holds one tone per preset, all of them sharing the sample pool of the file. Each
        // tone becomes a multi-sample of its own, built from the multi-samples which its partials
        // play - otherwise all presets of a bank would be flattened into one.
        final List<SvzInstrument> tones = ZenCoreSvz.readTones (container);
        final List<ZenCoreKeyMap> keyMaps = ZenCoreSvz.readKeyMaps (container);
        final String fileName = FileUtils.getNameWithoutType (svzFile);
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();

        if (tones.size () > 1)
        {
            for (final SvzInstrument tone: tones)
            {
                final IGroup toneGroup = new DefaultGroup ("Samples");
                for (final int waveNumber: tone.waveNumbers)
                    if (waveNumber > 0 && waveNumber <= keyMaps.size ())
                        buildZonesFromKeyMap (toneGroup, samples, keyMaps.get (waveNumber - 1));
                if (toneGroup.getSampleZones ().isEmpty ())
                    continue;
                applyToneShaping (toneGroup, tone);
                final String toneName = tone.name == null || tone.name.isBlank () ? fileName : tone.name;
                multisampleSources.add (this.createMultisampleSource (svzFile, toneName, List.of (toneGroup)));
                this.notifier.log ("IDS_ZENCORE_READING_SVZ", toneName, Integer.toString (toneGroup.getSampleZones ().size ()));
            }
            if (!multisampleSources.isEmpty ())
                return multisampleSources;
        }

        // One tone, or a file which only holds user samples: everything of the file becomes one
        // multi-sample which is named after the file
        final IGroup group = new DefaultGroup ("Samples");
        if (keyMaps.isEmpty ())
        {
            for (final ZenCoreSample sample: samples)
                if (sample.getSampleData () != null)
                    group.addSampleZone (createZone (sample, sample.getOriginalKey (), sample.getOriginalKey ()));
        }
        else
            buildZonesFromKeyMap (group, samples, keyMaps.get (0));

        if (group.getSampleZones ().isEmpty ())
        {
            this.notifier.logError ("IDS_ZENCORE_NO_USER_SAMPLES", svzFile.getName ());
            return Collections.emptyList ();
        }

        // Carry the tone's shaping back into the model, so ZEN-Core sources convert with their
        // filter and envelopes instead of pipeline defaults - the times through the same
        // hardware-calibrated law the writer uses.
        if (!tones.isEmpty ())
            applyToneShaping (group, tones.get (0));
        this.notifier.log ("IDS_ZENCORE_READING_SVZ", fileName, Integer.toString (group.getSampleZones ().size ()));
        return Collections.singletonList (this.createMultisampleSource (svzFile, fileName, List.of (group)));
    }


    private static void buildZonesFromKeyMap (final IGroup group, final List<ZenCoreSample> samples, final ZenCoreKeyMap keyMap)
    {
        // Turn the flat 128-key table into contiguous zones (a zone per run of one sample index)
        int runStart = -1;
        int runIndex = -1;
        for (int key = 0; key <= ZenCoreKeyMap.NUM_KEYS; key++)
        {
            final boolean assigned = key < ZenCoreKeyMap.NUM_KEYS && keyMap.isAssigned (key);
            final int index = assigned ? keyMap.getSampleIndex (key) : -1;
            if (index != runIndex)
            {
                // The key map stores a 1-based index into the sample pool (0 = unassigned).
                final int sampleListIndex = runIndex - 1;
                if (sampleListIndex >= 0 && sampleListIndex < samples.size ())
                {
                    final ZenCoreSample sample = samples.get (sampleListIndex);
                    if (sample.getSampleData () != null)
                        group.addSampleZone (createZone (sample, runStart, key - 1));
                }
                runStart = key;
                runIndex = index;
            }
        }
    }


    private static void applyToneShaping (final IGroup group, final SvzInstrument tone)
    {
        final IEnvelope amplitudeEnvelope = new DefaultEnvelope ();
        amplitudeEnvelope.setAttackTime (ZenCoreUtil.valueToTime (tone.envAttack));
        amplitudeEnvelope.setHoldTime (tone.envHold > 0 ? ZenCoreUtil.valueToTime (tone.envHold) : 0);
        amplitudeEnvelope.setDecayTime (ZenCoreUtil.valueToTime (tone.envDecay));
        amplitudeEnvelope.setReleaseTime (ZenCoreUtil.valueToTime (tone.envRelease));
        amplitudeEnvelope.setHoldLevel (tone.envHoldLevel / 1023.0);
        amplitudeEnvelope.setSustainLevel (tone.envSustain / 1023.0);

        for (final ISampleZone zone: group.getSampleZones ())
        {
            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
            if (tone.filterType >= 1 && tone.filterType <= 3)
            {
                final FilterType filterType = switch (tone.filterType)
                {
                    case 2 -> FilterType.BAND_PASS;
                    case 3 -> FilterType.HIGH_PASS;
                    default -> FilterType.LOW_PASS;
                };
                final IFilter filter = new DefaultFilter (filterType, 4, MathUtils.denormalizeCutoff (tone.cutoff / 1023.0), tone.resonance / 1023.0);
                applyModulationEnvelope (filter.getCutoffEnvelopeModulator (), tone.filterEnvDepth, tone.filterEnvTimes, tone.filterEnvLevels, 75.0);
                zone.setFilter (filter);
            }
            applyModulationEnvelope (zone.getPitchEnvelopeModulator (), tone.pitchEnvDepth, tone.pitchEnvTimes, tone.pitchEnvLevels, 280.0);
        }
    }


    /**
     * Turn a raw pitch/TVF modulation envelope back into a model envelope - the inverse of the
     * writer's mapping (device depth = model depth * scale; a zero depth is the template default,
     * i.e. no modulation).
     *
     * @param modulator The modulator to fill
     * @param depth The signed device depth
     * @param times The four raw times, or null
     * @param levels The five raw levels, or null
     * @param depthScale The hardware-calibrated depth scale (pitch 280, filter 75)
     */
    private static void applyModulationEnvelope (final IEnvelopeModulator modulator, final int depth, final int [] times, final int [] levels, final double depthScale)
    {
        if (depth == 0 || times == null || levels == null)
            return;
        modulator.setDepth (depth / depthScale);
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (ZenCoreUtil.valueToTime (times[0]));
        envelope.setHoldTime (times[1] > 0 ? ZenCoreUtil.valueToTime (times[1]) : 0);
        envelope.setDecayTime (ZenCoreUtil.valueToTime (times[2]));
        envelope.setReleaseTime (ZenCoreUtil.valueToTime (times[3]));
        envelope.setStartLevel (levels[0] / 1023.0);
        envelope.setHoldLevel (levels[2] / 1023.0);
        envelope.setSustainLevel (levels[3] / 1023.0);
        envelope.setEndLevel (levels[4] / 1023.0);
        modulator.setSource (envelope);
    }


    private static ISampleZone createZone (final ZenCoreSample sample, final int keyLow, final int keyHigh)
    {
        final ISampleZone zone = new DefaultSampleZone (sample.getName (), keyLow, keyHigh);
        zone.setSampleData (sample.getSampleData ());
        zone.setKeyRoot (sample.getOriginalKey ());
        zone.setStart (sample.getStartPoint ());
        if (sample.getEndPoint () > 0)
            zone.setStop (sample.getEndPoint ());
        zone.setGain (MathUtils.valueToDb (Math.max (sample.getLevel (), 1) / 127.0) + sample.getGain ());
        zone.setTuning (sample.getFineTune () / 100.0);

        // A one-shot ignores a note-off and plays the sample up to its end
        zone.setOneShot (sample.getLoopMode () == ZenCoreSample.LOOP_MODE_ONE_SHOT);
        if (sample.getLoopMode () != ZenCoreSample.LOOP_MODE_ONE_SHOT && sample.getEndPoint () > sample.getLoopStart ())
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (sample.getLoopStart ());
            loop.setEnd (sample.getEndPoint ());
            zone.getLoops ().add (loop);
        }
        return zone;
    }
}
