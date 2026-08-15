// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.core.utils.NoteParser;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects recursively Roland S-10/MKS-100/S-220 sampler system exclusive files in folders. Files
 * must end with <i>.syx</i>.
 *
 * @author Jürgen Moßgraber
 */
public class S1xxDetector extends AbstractDetector<MetadataSettingsUI>
{
    /** Empirical scaling constant. Increase for globally slower envelopes. */
    private static final double K           = 0.2;

    /** Controls curve steepness. Smaller = more dramatic exponential behavior. */
    private static final double CURVE       = 18.0;

    /** Minimum possible time in milliseconds. */
    private static final double MIN_TIME_MS = 1.0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public S1xxDetector (final INotifier notifier)
    {
        super ("Roland S-10, S-220, MKS-100", "S1xx", notifier, new MetadataSettingsUI ("S1xx"), ".syx");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final RolandS10SysEx sysEx = RolandS10SysEx.read (sourceFile);
            final String name = FileUtils.getNameWithoutType (sourceFile);
            this.notifier.log ("IDS_S1X_SYSEX_READING_PATCH", name, sysEx.waveParameters[0].isS220 ? "S-220" : "S-10/MKS-100");
            return Collections.singletonList (this.convert (sysEx, sourceFile, name));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Creates a multi-sample source and its sample zones from a parsed Roland S-10 SysEx dump.
     *
     * @param sysEx The parsed S-10 dump
     * @param sourceFile The originating .syx file
     * @param name The name of the patch
     * @return The populated multi-sample source
     */
    private IMultisampleSource convert (final RolandS10SysEx sysEx, final File sourceFile, final String name)
    {
        final IGroup group = new DefaultGroup ("Group 1");
        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, name, Collections.singletonList (group));

        final List<ISampleZone> zones = new ArrayList<> ();

        final WaveParameters wave = sysEx.waveParameters[0];
        if (wave.samplingStructure <= 6)
        {
            // Create 1 layer
            zones.add (createSampleZoneFromWave (wave, 0, 127));
        }
        else if (wave.samplingStructure <= 9)
        {
            // Create 2 layers
            zones.add (createSampleZoneFromWave (wave, 0, wave.split2 - 1));
            zones.add (createSampleZoneFromWave (sysEx.waveParameters[1], wave.split2, 127));
        }
        else
        {
            // Create 4 layers
            zones.add (createSampleZoneFromWave (wave, 0, wave.split1 - 1));
            zones.add (createSampleZoneFromWave (sysEx.waveParameters[1], wave.split1, wave.split2 - 1));
            zones.add (createSampleZoneFromWave (sysEx.waveParameters[2], wave.split2, wave.split3 - 1));
            zones.add (createSampleZoneFromWave (sysEx.waveParameters[3], wave.split3, 127));
        }

        group.setSampleZones (zones);
        multisampleSource.setGroups (List.of (group));
        applyPerformanceParameters (multisampleSource, sysEx.performanceParameters);

        return multisampleSource;
    }


    /**
     * Fills one sample zone with the data of one wave parameter block.
     * 
     * @param wave The wave parameters to read from
     * @param keyLow The lowest key to set
     * @param keyHigh The highest key to set
     * @return The created sample zone
     */
    private static ISampleZone createSampleZoneFromWave (final WaveParameters wave, final int keyLow, final int keyHigh)
    {
        // Wave parameters not used: enableBender, auto-looping, second loop from S-220

        final String name = wave.voiceName + " " + NoteParser.formatNoteAndOctave (wave.recordKey, 0) + " " + keyLow + "-" + keyHigh;

        final byte [] data = wave.toWavBytes ();
        final int sampleFrames = data.length / 2;
        final IAudioMetadata audioMetadata = new DefaultAudioMetadata (1, wave.samplingRate == 0 ? 30000 : 15000, 16, sampleFrames);
        final ISampleData sampleData = new InMemorySampleData (audioMetadata, data);
        final ISampleZone zone = new DefaultSampleZone (name, sampleData);

        zone.setKeyLow (keyLow);
        zone.setKeyHigh (keyHigh);
        zone.setVelocityLow (0);
        zone.setVelocityHigh (127);

        zone.setKeyRoot (wave.recordKey);
        zone.setTuning (wave.bankTune / 100.0);
        zone.setKeyTracking (wave.enableKeyFollow ? 1.0 : 0.0);

        zone.setStart (0);
        final int end = sampleFrames - 1;
        zone.setStop (end);

        // Scan Mode 2 = BWD (backwards playback)
        zone.setReversed (wave.scanMode == 2);

        if (wave.loopMode == 0)
            zone.setOneShot (true);
        else
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            switch (wave.scanMode)
            {
                default -> loop.setType (LoopType.FORWARDS);
                case 1 -> loop.setType (LoopType.BACKWARDS);
                case 2 -> loop.setType (LoopType.ALTERNATING);
            }
            loop.setStart (end - wave.manualLoopLength);
            loop.setEnd (end);
            loop.setTuning (wave.loopTune / 100.0);
            zone.addLoop (loop);
        }

        fillAmplitudeEnvelope (zone, wave);

        // Pitch Envelope
        if (wave.autoBendDepth > 0)
        {
            final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
            pitchEnvelopeModulator.setDepth (1);
            final IEnvelope pitchEnvelope = pitchEnvelopeModulator.getSource ();
            pitchEnvelope.setStartLevel (wave.autoBendDepth / 255.0);
            pitchEnvelope.setAttackTime (calculateTime (wave.autoBendDepth, 0, wave.autoBendRate));
            pitchEnvelope.setHoldLevel (0);
            pitchEnvelope.setSustainLevel (0);
        }

        // LFO: enableVibrato + performance parameters

        return zone;
    }


    /**
     * Maps the S-10 rate/level envelope (4 rates, 3 levels, last stage decays to 0) onto the
     * generic ADSR-style {@link IEnvelope}. Exact S-10 rate-to-time scaling is undocumented.
     * 
     * @param zone The zone to which to add the envelope
     * @param wave The wave parameters which contain the envelope
     */
    private static void fillAmplitudeEnvelope (final ISampleZone zone, final WaveParameters wave)
    {
        // Not used: wave.envelopeVelocitySensitivity

        zone.getAmplitudeVelocityModulator ().setDepth (wave.dynamicSensitivity / 255.0);

        final IEnvelope envelope = zone.getAmplitudeEnvelopeModulator ().getSource ();

        envelope.setStartLevel (0);
        envelope.setAttackTime (calculateTime (0, wave.envelopeLevel1, wave.envelopeRate1));

        if (wave.envelopeLevel1 != wave.envelopeLevel2)
        {
            envelope.setHoldTime (calculateTime (wave.envelopeLevel1, wave.envelopeLevel2, wave.envelopeRate2));
            envelope.setHoldLevel (wave.envelopeLevel2 / 255.0);
        }

        if (wave.envelopeLevel2 != wave.envelopeLevel3)
        {
            envelope.setDecayTime (calculateTime (wave.envelopeLevel2, wave.envelopeLevel3, wave.envelopeRate3));
            envelope.setSustainLevel (wave.envelopeLevel3 / 255.0);

        }

        envelope.setReleaseTime (calculateTime (wave.envelopeLevel3, 0, wave.envelopeRate4));
        envelope.setEndLevel (0);
    }


    /**
     * Applies global performance parameters, currently the key transpose.
     * 
     * @param multisampleSource The multi-sample source to apply the performance parameters to
     * @param performance The performance parameters to apply
     */
    private static void applyPerformanceParameters (final IMultisampleSource multisampleSource, final PerformanceParameters performance)
    {
        if (performance == null)
            return;

        // LFO: vibratoRate, manualVibratoDepth, delayVibratoDepth, delayTimeVibratoDelay
    }


    /**
     * Approximates the envelope segment times.
     *
     * Formula: <pre>time ~= K * deltaLevel * 2^((255 - rate) / CURVE)</pre><br>
     * The constants are empirical and can be tuned to taste.
     *
     * This produces:
     * <ol>
     * <li>low rates -> very long times
     * <li>high rates -> very short times
     * <li>larger level transitions -> longer times
     * </ol>
     *
     * @param startLevel The start level in the range of 0..255
     * @param endLevelValue The end level in the range of 0..255
     * @param rate The rate in the range of 1..255
     * @return The calculated time in seconds
     */
    private static final double calculateTime (final int startLevel, final int endLevelValue, final int rate)
    {
        final int deltaLevel = Math.abs (endLevelValue - startLevel);
        final double rateFactor = Math.pow (2.0, (255.0 - rate) / CURVE);
        final double timeMs = K * deltaLevel * rateFactor;
        return (timeMs < MIN_TIME_MS ? 0 : timeMs) / 1000.0;
    }
}
