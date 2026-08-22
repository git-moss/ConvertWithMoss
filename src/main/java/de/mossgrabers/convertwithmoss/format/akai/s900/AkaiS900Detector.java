// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.akai.s900.AkaiS900Keygroup.KeygroupLayer;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects Akai S900/S950 image files. Files must end with <i>.img</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Detector extends AbstractDetector<MetadataSettingsUI>
{
    /**
     * The cut-off frequency in Hertz which each of the 100 filter settings produces, one entry per
     * setting. The sampler does not filter digitally: each of its 8 voices runs through an analog
     * switched-capacitor low-pass whose corner sits at a fiftieth of the clock it is fed with, and
     * that clock is a timer which the operating system divides down from 4 MHz. The setting is
     * scaled by 1.25, offset by 70 and clipped to the range of that divider table, which is why the
     * filter is fully closed below a setting of 24 and fully open from 76 upwards, and why the
     * curve in between is anything but a straight line.
     */
    private static final int [] FILTER_CUTOFF      =
    {
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        125,
        141,
        150,
        158,
        168,
        188,
        199,
        211,
        224,
        250,
        267,
        281,
        299,
        333,
        356,
        376,
        400,
        444,
        471,
        500,
        533,
        593,
        627,
        667,
        711,
        800,
        842,
        889,
        941,
        1067,
        1143,
        1185,
        1280,
        1391,
        1524,
        1600,
        1684,
        1882,
        2000,
        2133,
        2286,
        2462,
        2667,
        2909,
        2909,
        3556,
        3556,
        4000,
        4000,
        4571,
        4571,
        5333,
        5333,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400,
        6400
    };

    // Guessed LFO mappings until there is a device available for measuring the values.
    private static final double LFO_MIN_HERTZ      = 0.001953125;
    private static final double LFO_MAX_HERTZ      = 20.0;
    private static final double LFO_FREQUENCY_BASE = 4.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiS900Detector (final INotifier notifier)
    {
        super ("Akai S900/S950", "S900", notifier, new MetadataSettingsUI ("S900"), ".img", ".akai");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final AkaiS900DiskImage image = new AkaiS900DiskImage (sourceFile);
            final List<AkaiS900Program> programs = image.getPrograms ();
            final Map<String, AkaiS900Sample> samples = image.getSamples ();

            AkaiS900DirectoryEntry continuationEntry = image.doesRequireContinuationDisk ();
            try
            {
                if (continuationEntry != null)
                {
                    this.notifier.log ("IDS_S900_CONTINUATION_DISK", continuationEntry.getName ());
                    File continuationFile = sourceFile;
                    while (continuationEntry != null)
                    {
                        continuationFile = getContinuationFile (continuationFile);
                        this.notifier.log ("IDS_S900_CONTINUATION_DISK_FOUND", continuationFile.getName ());

                        final AkaiS900DiskImage continuationImage = new AkaiS900DiskImage (continuationFile);
                        continuationEntry = continuationImage.doesRequireContinuationDisk ();
                        programs.addAll (continuationImage.getPrograms ());
                        samples.putAll (continuationImage.getSamples ());
                    }
                }
            }
            catch (final IOException ex)
            {
                // Only log as normal info since a missing continuation disk might not be an error
                // when the sample is not used
                this.notifier.logText (ex.getMessage ());
            }

            if (programs.isEmpty ())
            {
                if (samples.isEmpty ())
                    this.notifier.logError ("IDS_S900_EMPTY_DISK");
                else
                    this.notifier.log ("IDS_S900_NO_PROGRAMS_ON_DISK");
                return Collections.emptyList ();
            }

            final List<IMultisampleSource> multiSampleSources = new ArrayList<> ();
            final String fileName = FileUtils.getNameWithoutType (sourceFile.getName ());
            for (final AkaiS900Program program: programs)
            {
                final String programName = program.getName ();
                this.notifier.log ("IDS_S900_READING_PROGRAM", programName);

                final IGroup group = new DefaultGroup ();
                this.createSampleZones (group, program, samples);
                if (group.getSampleZones ().isEmpty ())
                {
                    this.notifier.logError ("IDS_S900_SKIPPING_EMPTY_PROGRAM");
                    continue;
                }

                final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, programName, Collections.singletonList (group));
                multisampleSource.extendSubPath (fileName);
                multiSampleSources.add (multisampleSource);
            }
            return multiSampleSources;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private void createSampleZones (final IGroup group, final AkaiS900Program program, final Map<String, AkaiS900Sample> samples) throws IOException
    {
        for (final AkaiS900Keygroup keygroup: program.getKeygroups ())
        {
            // Each key-group has 1 or 2 velocity layers (soft and loud) depending on the velocity
            // split value

            final int velocitySwitchValue = keygroup.getVelocitySwitchValue ();
            final KeygroupLayer [] velocityLayers = keygroup.getVelocityLayers ();
            // Soft velocity layer
            if (velocitySwitchValue > 0)
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (keygroup, velocityLayers[0], samples);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setVelocityLow (0);
                    sampleZone.setVelocityHigh (Math.min (velocitySwitchValue, 127));
                    if ((keygroup.getFlags () & AkaiS900Keygroup.FLAG_CROSSFADE) == 0)
                        sampleZone.setVelocityCrossfadeHigh (127 - velocitySwitchValue);
                    group.addSampleZone (sampleZone);
                }
            }
            // Loud velocity layer
            if (velocitySwitchValue < 128)
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (keygroup, velocityLayers[1], samples);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setVelocityLow (Math.min (velocitySwitchValue + 1, 127));
                    sampleZone.setVelocityHigh (127);
                    if ((keygroup.getFlags () & AkaiS900Keygroup.FLAG_CROSSFADE) == 0)
                        sampleZone.setVelocityCrossfadeLow (velocitySwitchValue);
                    group.addSampleZone (sampleZone);
                }
            }
        }

        final List<ISampleZone> sampleZones = group.getSampleZones ();

        final double amplitudeKeyTracking = program.getKeyboardTilt () / 50.0;
        for (final ISampleZone sampleZone: sampleZones)
            sampleZone.setAmplitudeKeyTracking (amplitudeKeyTracking);

        if (program.isKeygroupCrossfadeEnable ())
            calculateCrossfades (sampleZones);
    }


    private Optional<ISampleZone> createSampleZone (final AkaiS900Keygroup keygroup, final KeygroupLayer velocityLayer, final Map<String, AkaiS900Sample> samples) throws IOException
    {
        final String sampleName = velocityLayer.getSample ();
        final AkaiS900Sample sample = samples.get (sampleName);
        if (sample == null)
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final Optional<WaveFile> waveFile = AkaiS900DiskImage.writeSample (sample);
        if (waveFile.isEmpty ())
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final int lowKey = keygroup.getKeyLow ();
        final int highKey = keygroup.getKeyHigh ();
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, lowKey, highKey);
        sampleZone.setSampleData (new WavFileSampleData (waveFile.get ()));

        sampleZone.setReversed (sample.getDirection () == 'R');
        // The one-shot trigger mode ignores a note-off and plays the sample up to its end
        final int flags = keygroup.getFlags ();
        sampleZone.setOneShot ((flags & AkaiS900Keygroup.FLAG_ONE_SHOT) > 0);

        // Pitch
        final double nominalPitch = sample.getNominalPitch () / 16.0;
        final int rootNote = (int) Math.round (nominalPitch);
        sampleZone.setKeyRoot (rootNote);
        // The nominal pitch is the recorded pitch of the sample. A sample which was recorded
        // e.g. a quarter tone sharp must be played back a quarter tone lower to be in tune
        sampleZone.setTuning (rootNote - nominalPitch);
        sampleZone.setKeyTracking ((flags & AkaiS900Keygroup.FLAG_TRANSPOSE) > 0 ? 0 : 1.0);

        // Pitch LFO
        // Indirect modulation wheel modulation is currently not support, therefore, prevent
        // unintended wobbly sounds
        final int lfoVibratoDepth = keygroup.getLfoVibratoDepth ();
        if (lfoVibratoDepth > 0 && keygroup.getModulationWheelLfoDepthModulation () == 0)
        {
            final ILfoModulator pitchLfoModulator = sampleZone.getPitchLfoModulator ();
            // Should be +-3 semi-tones
            pitchLfoModulator.setDepth (lfoVibratoDepth / 99.0 * 0.25);
            final ILfo lfo = pitchLfoModulator.getSource ();
            lfo.setWaveform (LfoWaveform.SINE);
            lfo.setRate (lfoRateToHertz (keygroup.getLfoVibratoRate () / 99.0));
            lfo.setDelay (toSeconds (keygroup.getLfoBuildUpTime (), false));
            lfo.setKeySync ((flags & AkaiS900Keygroup.FLAG_VIBRATO_DESYNC) == 0);
        }

        // Pitch Envelope
        final int pitchWarpInitialOffset = keygroup.getPitchWarpInitialOffset ();
        if (pitchWarpInitialOffset > 0)
        {
            final IEnvelopeModulator pitchEnvelopeModulator = sampleZone.getPitchEnvelopeModulator ();
            pitchEnvelopeModulator.setDepth (1.0 / 40.0);
            final IEnvelope pitchEnvelope = pitchEnvelopeModulator.getSource ();
            pitchEnvelope.setStartLevel (keygroup.getPitchWarpInitialOffset () / 50.0 * 0.25);
            pitchEnvelope.setAttackTime (0);
            pitchEnvelope.setDecayTime (toSeconds (keygroup.getPitchWarpRecoveryTime (), false));
            pitchEnvelope.setSustainLevel (0);
        }

        // Mixing

        // There is no panning only a hard assignment to left/right channel otherwise it is mono
        final int outputChannel = keygroup.getOutputAssign ();
        if (outputChannel == 0x08)
            sampleZone.setPanning (-1.0);
        else if (outputChannel == 0x09)
            sampleZone.setPanning (1.0);

        sampleZone.setGain (Math.min (24, (velocityLayer.getLoudnessOffset () + sample.getLoudnessOffset ()) * 0.375));
        sampleZone.getAmplitudeEnvelopeModulator ().setSource (convertEnvelope (keygroup.getAttack (), keygroup.getDecay (), keygroup.getSustain (), keygroup.getRelease ()));
        sampleZone.getAmplitudeVelocityModulator ().setDepth (keygroup.getLoudnessVelocityInteraction () / 99.0);

        // Play-back
        sampleZone.setStart ((int) sample.getStart ());
        sampleZone.setStop ((int) sample.getEnd ());

        // Loop
        if (sample.getLoopLength () > 0)
        {
            final char loopMode = sample.getPlaybackMode ();
            if (loopMode == 'L' || loopMode == 'A')
            {
                final long marker = sample.getEnd ();
                final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                sampleLoop.setStart ((int) (marker - sample.getLoopLength ()));
                sampleLoop.setEnd ((int) marker);
                sampleZone.getLoops ().add (sampleLoop);
            }
        }

        // Filter
        final int filterCutoff = velocityLayer.getFilter ();
        IFilter filter = null;
        if (filterCutoff < 99)
        {
            final double cutoff = FILTER_CUTOFF[Math.clamp (filterCutoff, 0, 99)];
            filter = new DefaultFilter (FilterType.LOW_PASS, 3, cutoff, 0);

            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            cutoffEnvelopeModulator.setDepth (keygroup.getEnvelopeFilterFrequencyModulation () / 50.0);
            cutoffEnvelopeModulator.setSource (convertEnvelope (velocityLayer.getFilterAttack (), velocityLayer.getFilterDecay (), velocityLayer.getFilterSustain (), velocityLayer.getFilterRelease ()));

            filter.setCutoffKeyTracking ((keygroup.getFilterkeyTracking () - 50) / 50.0);
            filter.getCutoffVelocityModulator ().setDepth (keygroup.getFilterVelocityInteraction () / 99.0);

            sampleZone.setFilter (filter);
        }

        return Optional.of (sampleZone);
    }


    private static IEnvelope convertEnvelope (final int attack, final int decay, final int sustain, final int release)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (toSeconds (attack, false));
        envelope.setDecayTime (toSeconds (decay, true));
        envelope.setSustainLevel (sustain / 99.0);
        envelope.setReleaseTime (toSeconds (release, false));
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 99.0 * (isLong ? 6.0 : 2.0);
    }


    /**
     * Calculate the overlap between key-groups and set them as cross-fades.
     *
     * @param zones The zones to update to cross-fades
     */
    private static void calculateCrossfades (final List<ISampleZone> zones)
    {
        for (int i = 0; i < zones.size (); i++)
        {
            final ISampleZone currentZone = zones.get (i);

            // Calculate lower cross-fade (overlap with previous DIFFERENT zone)
            int lowerCrossfade = 0;
            for (int j = i - 1; j >= 0; j--)
            {
                final ISampleZone prevZone = zones.get (j);
                if (prevZone.getKeyLow () != currentZone.getKeyLow () || prevZone.getKeyHigh () != currentZone.getKeyHigh ())
                {
                    // Found a different zone - calculate overlap
                    if (prevZone.getKeyHigh () >= currentZone.getKeyLow ())
                        lowerCrossfade = Math.min (prevZone.getKeyHigh (), currentZone.getKeyHigh ()) - currentZone.getKeyLow () + 1;
                    break;
                }
            }
            currentZone.setNoteCrossfadeLow (lowerCrossfade);

            // Calculate upper cross-fade (overlap with next DIFFERENT zone)
            int upperCrossfade = 0;
            for (int j = i + 1; j < zones.size (); j++)
            {
                final ISampleZone nextZone = zones.get (j);
                if (nextZone.getKeyLow () != currentZone.getKeyLow () || nextZone.getKeyHigh () != currentZone.getKeyHigh ())
                {
                    // Found a different zone - calculate overlap
                    if (currentZone.getKeyHigh () >= nextZone.getKeyLow ())
                        upperCrossfade = currentZone.getKeyHigh () - Math.max (nextZone.getKeyLow (), currentZone.getKeyLow ()) + 1;
                    break;
                }
            }
            currentZone.setNoteCrossfadeHigh (upperCrossfade);
        }
    }


    private static File getContinuationFile (final File sourceFile) throws IOException
    {
        final String nameWithoutType = FileUtils.getNameWithoutType (sourceFile);
        final String [] split = nameWithoutType.split ("_");
        if (split.length == 2)
            try
            {
                final int index = Integer.parseInt (split[1]);
                final File file = new File (sourceFile.getParent (), split[0] + "_" + (index + 1) + sourceFile.getName ().substring (nameWithoutType.length ()));
                if (file.exists ())
                    return file;
            }
            catch (final NumberFormatException _)
            {
                // Thrown below
            }
        throw new IOException (Functions.getMessage ("IDS_S900_COULD_NOT_FIND_CONTINUATION_DISK"));
    }


    /**
     * Convert a normalized LFO frequency value to a rate in Hertz.
     *
     * @param value The normalized value [0..1]
     * @return The rate in Hertz
     */
    public static double lfoRateToHertz (final double value)
    {
        final double normalized = Math.clamp (value, 0, 1);
        return LFO_MIN_HERTZ + LFO_MAX_HERTZ * (Math.pow (LFO_FREQUENCY_BASE, normalized) - 1.0) / (LFO_FREQUENCY_BASE - 1.0);
    }
}
