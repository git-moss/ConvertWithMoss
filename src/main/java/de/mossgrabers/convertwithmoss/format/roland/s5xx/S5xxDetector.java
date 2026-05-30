// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s5xx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;


/**
 * Detects recursively Roland S5xx sampler disk files in folders. Files must end with <i>.out</i>,
 * <i>.img</i> or <i>sdk</i>.
 *
 * @author Jürgen Moßgraber
 */
public class S5xxDetector extends AbstractDetector<MetadataSettingsUI>
{
    // The highest key
    private static final int    TOP_KEY     = 127;

    /** Empirical scaling constant. Increase for globally slower envelopes. */
    private static final double K           = 0.5;

    /** Controls curve steepness. Smaller = more dramatic exponential behavior. */
    private static final double CURVE       = 18.0;

    /** Minimum possible time in milliseconds. */
    private static final double MIN_TIME_MS = 1.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public S5xxDetector (final INotifier notifier)
    {
        super ("Roland S5xx", "S5xx", notifier, new MetadataSettingsUI ("S5xx"), ".out", ".img", ".sdk");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final DiskImage image = new DiskImageParser (sourceFile).parse ();
            final DiskImageHeader hdr = image.getHeader ();
            this.notifier.log ("IDS_S5XX_VERSION", hdr.getSamplerType ().getDescription (), hdr.getOsVersionString ());
            return this.readPatches (sourceFile, image);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    private List<IMultisampleSource> readPatches (final File sourceFile, final DiskImage image)
    {
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        final String metadataDescription = createMetadataDescription (image.getDiskLabel ());

        for (final Patch patch: image.getPatches ())
        {
            final String patchName = patch.getName ();
            if (patchName.isBlank ())
                continue;
            this.notifier.log ("IDS_S5XX_CONVERTING_PATCH", String.format ("%-3s %s", patch.getPatchId (), patchName));

            final IMultisampleSource multisampleSource = this.readPatch (sourceFile, patch, patchName, metadataDescription, image);
            multisampleSources.add (multisampleSource);
        }

        return multisampleSources;
    }


    private IMultisampleSource readPatch (final File sourceFile, final Patch patch, final String patchName, final String metadataDescription, final DiskImage image)
    {
        final File parentFile = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, patchName);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, patchName);

        final List<Tone> tones = image.getTones ();
        final List<WaveData> waveData = image.getWaveData ();

        final IGroup groupLayer1 = new DefaultGroup ("Layer 1");
        final IGroup groupLayer2 = new DefaultGroup ("Layer 2");

        final int bendRange = patch.getBendRange () * 100;

        // There are two layers, each key(!) of each layer can play a different tone
        for (int layer = 0; layer < 2; layer++)
        {
            int lowKey = 0;
            int highKey = 0;
            int toneId = -1;
            // Create one sample zone for each tone range
            for (int key = 0; key <= TOP_KEY; key++)
            {
                final int toneToKey = patch.getToneToKey (layer, key);
                if (toneId == -1)
                    toneId = toneToKey;
                else if (toneId != toneToKey || key == TOP_KEY)
                {
                    highKey = key == TOP_KEY ? TOP_KEY : key - 1;

                    final Tone tone = tones.get (toneToKey);
                    final String toneName = tone.getName ();
                    final ISampleZone sampleZone = new DefaultSampleZone (toneName, lowKey, highKey);
                    if (layer == 0)
                        groupLayer1.addSampleZone (sampleZone);
                    else
                    {
                        groupLayer2.addSampleZone (sampleZone);
                        // Possible stereo setup
                        if (patch.getKeyMode () == 4 && tone.getOutputAssign () == 1)
                            sampleZone.setPanning (1);
                    }

                    sampleZone.setBendUp (bendRange);
                    sampleZone.setBendDown (-bendRange);

                    applyParameters (sampleZone, tone, waveData, tones, image.getHeader ().getSamplerType ());

                    lowKey = key;
                    toneId = toneToKey;
                }
            }
        }

        final List<IGroup> groups = applyLayerSetup (patch, groupLayer1, groupLayer2);
        multisampleSource.setGroups (groups);
        final int octaveShift = patch.getOctaveShift () * 12;
        for (final IGroup group: groups)
            for (final ISampleZone sampleZone: group.getSampleZones ())
                sampleZone.setTuning (sampleZone.getTuning () + octaveShift);

        final IMetadata metadata = multisampleSource.getMetadata ();
        metadata.setDescription (metadataDescription);
        this.createMetadata (metadata, (IFileBasedSampleData) null, parts);
        this.updateCreationDateTime (metadata, sourceFile);

        return multisampleSource;
    }


    private static void applyParameters (final ISampleZone sampleZone, final Tone tone, final List<WaveData> waveData, final List<Tone> tones, final SamplerType samplerType)
    {
        sampleZone.setSampleData (createSampleData (tone.getOrigSubTone () == 1 ? tones.get (tone.getSourceTone ()) : tone, waveData));
        sampleZone.setKeyRoot (tone.getOrigKeyNumber ());

        sampleZone.setStart (tone.getStartPoint ());

        // 0 = Forward, 1 = Alternating, 2 = One-Shot, 3 = Reverse
        final int loopMode = tone.getLoopMode ();
        sampleZone.setReversed (loopMode == 3);
        if (loopMode < 2)
        {
            final ISampleLoop sampleLoop = new DefaultSampleLoop ();
            sampleLoop.setType (loopMode == 0 ? LoopType.FORWARDS : LoopType.ALTERNATING);
            final int loopPoint = tone.getLoopPoint ();
            sampleLoop.setStart (loopPoint);
            sampleLoop.setEnd (loopPoint + tone.getLoopLength ());
            sampleLoop.setTuning (tone.getLoopTune () / 100.0);
            sampleZone.getLoops ().add (sampleLoop);
        }

        // Pitch parameters
        sampleZone.setTuning (tone.getFineTune () / 100.0);
        if (tone.getPitchFollow () == 0)
        {
            sampleZone.setKeyTracking (0);
            sampleZone.setTuning (sampleZone.getTuning () + tone.getTranspose ());
        }

        // Volume parameters
        sampleZone.setGain (MathUtils.valueToDb (tone.getLevel () / 127.0));
        final IEnvelope ampEnvelope = createEnvelope (tone.getTvaEnvelopeLevels (), tone.getTvaEnvelopeRates (), tone.getTvaEnvSustainPoint (), tone.getTvaEnvEndPoint ());
        sampleZone.getAmplitudeEnvelopeModulator ().setSource (ampEnvelope);

        // No filter on the S-50
        if (!samplerType.isS50 ())
            createFilter (sampleZone, tone);
    }


    private static void createFilter (final ISampleZone sampleZone, final Tone tone)
    {
        if (tone.getTvfSwitch () == 0)
            return;

        final double cutoff = MathUtils.denormalizeCutoff (tone.getTvfCutoff () / 127.0);
        final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 2, cutoff, tone.getTvfResonance () / 127.0);

        final IEnvelope envelope = createEnvelope (tone.getTvfEnvLevels (), tone.getTvfEnvRates (), tone.getTvfEnvSustainPoint (), tone.getTvfEnvEndPoint ());
        final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
        cutoffEnvelopeModulator.setSource (envelope);
        final double depth = tone.getTvfEgDepth () / 127.0;
        cutoffEnvelopeModulator.setDepth (tone.getTvfEgPolarity () == 1 ? -depth : depth);

        sampleZone.setFilter (filter);
    }


    private static ISampleData createSampleData (final Tone tone, final List<WaveData> waveData)
    {
        final int startSegment = (tone.getWaveBank () == 1 ? 18 : 0) + tone.getWaveSegmentTop ();
        final int numSegments = tone.getWaveSegmentLength ();
        short [] samples = new short [numSegments * WaveData.SAMPLES_PER_SEGMENT];
        for (int i = 0; i < numSegments; i++)
        {
            final short [] segmentSamples = waveData.get (startSegment + i).getSamples ();
            System.arraycopy (segmentSamples, 0, samples, i * WaveData.SAMPLES_PER_SEGMENT, segmentSamples.length);
        }

        final int endPoint = tone.getEndPoint ();
        if (endPoint + 1 < samples.length)
            samples = Arrays.copyOf (samples, endPoint + 1);

        final int sampleRate = tone.getSamplingFrequency () == 0 ? 30000 : 15000;
        return new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 12, samples.length), samples);
    }


    private static List<IGroup> applyLayerSetup (final Patch patch, final IGroup groupLayer1, final IGroup groupLayer2)
    {
        switch (patch.getKeyMode ())
        {
            // Normal
            default:
            case 0:
                return Collections.singletonList (groupLayer1);

            // Unison
            case 1:
                final double detune = patch.getDetune () / 100.0;
                for (final ISampleZone sampleZone: groupLayer2.getSampleZones ())
                    sampleZone.setTuning (sampleZone.getTuning () + detune);
                break;

            // Velocity Switch, Velocity Cross Fade
            case 2, 3:
                final int velocitySwitchThreshold = patch.getVelocitySwitchThreshold ();
                if (velocitySwitchThreshold == 0)
                    return Collections.singletonList (groupLayer2);
                if (velocitySwitchThreshold == 127)
                    return Collections.singletonList (groupLayer1);
                for (final ISampleZone sampleZone: groupLayer1.getSampleZones ())
                    sampleZone.setVelocityHigh (velocitySwitchThreshold - 1);
                for (final ISampleZone sampleZone: groupLayer2.getSampleZones ())
                    sampleZone.setVelocityLow (velocitySwitchThreshold);
                break;

            // Velocity Mix
            case 4:
                // There might be a stereo setup if all tones of layer 2 are sent to output 2, all
                // sample zones which are sent to output 2 have been hard-panned to the right

                boolean hasPanning = false;
                boolean hasFullPanning = true;
                final List<ISampleZone> sampleZones2 = groupLayer2.getSampleZones ();
                for (final ISampleZone sampleZone: sampleZones2)
                {
                    if (sampleZone.getPanning () == 1)
                        hasPanning = true;
                    else
                        hasFullPanning = false;
                }

                if (hasFullPanning)
                {
                    final List<ISampleZone> sampleZones1 = groupLayer1.getSampleZones ();

                    // Check if all parameters match: number of zones, key-range, root-keys, ...
                    if (checkStereoMatch (sampleZones1, sampleZones2))
                    {
                        for (final ISampleZone sampleZone: sampleZones1)
                            sampleZone.setPanning (-1);
                        groupLayer1.setName ("Left");
                        groupLayer2.setName ("Right");
                        hasPanning = false;
                    }
                }

                // Cleanup panning if it was not combined to stereo
                if (hasPanning)
                {
                    for (final ISampleZone sampleZone: sampleZones2)
                        sampleZone.setPanning (0);
                }
                break;
        }

        final List<IGroup> result = new ArrayList<> ();
        Collections.addAll (result, groupLayer1, groupLayer2);
        return result;
    }


    private static String createMetadataDescription (final DiskLabel diskLabel)
    {
        if (diskLabel == null)
            return "";

        final StringBuilder sb = new StringBuilder ();
        for (final String row: diskLabel.getRows ())
            if (row != null && !row.isBlank ())
                sb.append (row.trim ()).append ("\n");
        return sb.toString ().trim ();
    }


    private static IEnvelope createEnvelope (final int [] levels, final int [] rates, final int sustainPoint, final int endPoint)
    {
        final IEnvelope envelope = new DefaultEnvelope ();

        // First segment is the attack phase
        envelope.setAttackTime (calculateTime (0, levels[0], Math.clamp (rates[0], 1, 127)));
        envelope.setHoldLevel (levels[0] / 127.0);

        // Assume a hold phase when the first 2 levels match
        int decayStartPoint = 1;
        if (sustainPoint > 1 && levels[0] == levels[1])
        {
            envelope.setHoldTime (calculateTime (levels[0], levels[1], Math.clamp (rates[1], 1, 127)));
            decayStartPoint = 2;
        }

        // Add all other segments till the sustain as the decay
        double time = 0;
        for (int i = decayStartPoint; i <= sustainPoint; i++)
            time += calculateTime (levels[i - 1], levels[i], Math.clamp (rates[i], 1, 127));
        if (time > 0)
            envelope.setDecayTime (time);

        envelope.setSustainLevel (levels[sustainPoint] / 127.0);

        // Release phase
        time = 0;
        if (levels[sustainPoint] == 0)
            // If the sustain phase is already 0, use the previous point
            time = calculateTime (levels[sustainPoint - 1], 0, Math.clamp (rates[endPoint], 1, 127));
        else
            // Add the final segments till the end point as the release phase
            for (int i = sustainPoint + 1; i <= endPoint; i++)
                time += calculateTime (levels[i - 1], levels[i], Math.clamp (rates[i], 1, 127));
        if (time > 0)
            envelope.setReleaseTime (time);

        return envelope;
    }


    /**
     * Approximates the envelope segment times.
     *
     * Formula: <pre>time ~= K * deltaLevel * 2^((127 - rate) / CURVE)</pre><br>
     * The constants are empirical and can be tuned to taste.
     *
     * This produces:
     * <ol>
     * <li>low rates -> very long times
     * <li>high rates -> very short times
     * <li>larger level transitions -> longer times
     * </ol>
     *
     * @param startLevel The start level in the range of 0..127
     * @param endLevelValue The end level in the range of 0..127
     * @param rate The rate in the range of 1..127
     * @return The calculated time in seconds
     */
    private static final double calculateTime (final int startLevel, final int endLevelValue, final int rate)
    {
        final int deltaLevel = Math.abs (endLevelValue - startLevel);
        final double rateFactor = Math.pow (2.0, (127.0 - rate) / CURVE);
        final double timeMs = K * deltaLevel * rateFactor;
        return (timeMs < MIN_TIME_MS ? 0 : timeMs) / 1000.0;
    }


    private static boolean checkStereoMatch (final List<ISampleZone> sampleZones1, final List<ISampleZone> sampleZones2)
    {
        if (sampleZones1.size () != sampleZones2.size ())
            return false;

        for (int i = 0; i < sampleZones1.size (); i++)
        {
            final ISampleZone sampleZone1 = sampleZones1.get (i);
            final ISampleZone sampleZone2 = sampleZones1.get (i);
            if (sampleZone1.getKeyLow () != sampleZone2.getKeyLow ())
                return false;
            if (sampleZone1.getKeyHigh () != sampleZone2.getKeyHigh ())
                return false;
            if (sampleZone1.getKeyRoot () != sampleZone2.getKeyRoot ())
                return false;
            if (sampleZone1.getStart () != sampleZone2.getStart ())
                return false;
            try
            {
                if (sampleZone1.getSampleData ().getAudioMetadata ().getNumberOfSamples () != sampleZone2.getSampleData ().getAudioMetadata ().getNumberOfSamples ())
                    return false;
            }
            catch (final IOException ex)
            {
                return false;
            }
        }

        return true;
    }
}
