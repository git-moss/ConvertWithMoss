// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc1000;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects Akai MPC1000 program files. Files must end with <i>.pgm</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiMPC1000Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Map<Integer, FilterType> FILTER_MAP     = new HashMap<> ();

    static
    {
        FILTER_MAP.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_MAP.put (Integer.valueOf (2), FilterType.BAND_PASS);
        FILTER_MAP.put (Integer.valueOf (3), FilterType.HIGH_PASS);
        // Not sure about this one but there is a LOW PASS 2 in the manual
        FILTER_MAP.put (Integer.valueOf (4), FilterType.LOW_PASS);
    }

    private static final LfoWaveform []           LFO_WAVEFORMS  =
    {
        LfoWaveform.TRIANGLE,
        LfoWaveform.SINE,
        LfoWaveform.SQUARE,
        LfoWaveform.SAWTOOTH_UP,
        LfoWaveform.SAWTOOTH_DOWN,
        LfoWaveform.RANDOM
    };

    /** Ticks per beat of the LFO rate and delay. */
    private static final double                   TICKS_PER_BEAT = 96.0;
    /** The tempo for converting the tempo synchronized LFO rate and delay. */
    private static final double                   TEMPO          = 120.0;
    /** The depth of a pitch amount of 100, assumed to be 1 octave. */
    private static final double                   PITCH_SCALE    = 1200.0 / IEnvelope.MAX_ENVELOPE_DEPTH;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiMPC1000Detector (final INotifier notifier)
    {
        super ("Akai MPC 500/1000/2500", "MPC1000", notifier, new MetadataSettingsUI ("MPC1000"), ".pgm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final FileInputStream input = new FileInputStream (sourceFile))
        {
            final AkaiMPC1000Program program = new AkaiMPC1000Program (input);
            final String programName = FileUtils.getNameWithoutType (sourceFile).trim ();
            final IGroup group = new DefaultGroup ();
            this.createSampleZones (group, program, sourceFile.getParentFile ());
            return Collections.singletonList (this.createMultisampleSource (sourceFile, programName, Collections.singletonList (group)));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private void createSampleZones (final IGroup group, final AkaiMPC1000Program program, final File sourceFileParent) throws IOException
    {
        final byte [] midiNotes = program.getMidiNotes ();
        final List<AkaiMPC1000Pad> pads = program.getPads ();
        for (int i = 0; i < pads.size (); i++)
        {
            final AkaiMPC1000Pad pad = pads.get (i);
            for (final AkaiMPC1000Sample sample: pad.getSamples ())
            {
                final Optional<ISampleZone> zoneOpt = this.createSampleZone (pad, sample, sourceFileParent);
                if (zoneOpt.isPresent ())
                {
                    final ISampleZone sampleZone = zoneOpt.get ();
                    sampleZone.setKeyLow (midiNotes[i]);
                    sampleZone.setKeyHigh (midiNotes[i]);
                    group.addSampleZone (sampleZone);
                }
            }
        }
    }


    private Optional<ISampleZone> createSampleZone (final AkaiMPC1000Pad pad, final AkaiMPC1000Sample sample, final File sourceFileParent) throws IOException
    {
        final String sampleName = sample.getName ();
        if (sampleName == null || sampleName.isBlank ())
            return Optional.empty ();

        final File sampleFile = findFileIgnoreCase (sourceFileParent, sampleName + ".wav");
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_ISO_SAMPLE_NOT_FOUND", sampleName);
            return Optional.empty ();
        }

        final WavFileSampleData sampleData = new WavFileSampleData (sampleFile);
        final ISampleZone sampleZone = new DefaultSampleZone (sampleName, sampleData);
        sampleData.addZoneData (sampleZone, true, true);

        sampleZone.setVelocityLow (sample.getVelocityRangeLower ());
        sampleZone.setVelocityHigh (sample.getVelocityRangeUpper ());

        // The mute group already uses 0 for 'Off' like the model
        sampleZone.setExclusiveGroup (Math.clamp (pad.getMuteGroup (), 0, 32));

        // Pitch
        sampleZone.setTuning (sample.getTuning () / 100.0);
        sampleZone.setKeyTracking (0);

        // Mixing
        sampleZone.setGain (MathUtils.valueToDb ((pad.getMixerLevel () + sample.getLevel ()) / 200.0));
        sampleZone.setPanning ((pad.getMixerPan () - 50) / 50.0);

        // Play mode 0 = One Shot: a note-off is ignored and the sample is played up to its end
        final boolean isOneShot = sample.getPlayMode () == 0;

        final IAudioMetadata audioMetadata = sampleData.getAudioMetadata ();
        final int sampleLength = audioMetadata.getNumberOfSamples ();
        final double sampleLengthAsSeconds = sampleLength / (double) audioMetadata.getSampleRate ();
        final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
        amplitudeEnvelopeModulator.setSource (convertEnvelope (pad, isOneShot, !sampleZone.getLoops ().isEmpty (), sampleLengthAsSeconds));
        sampleZone.getAmplitudeVelocityModulator ().setDepth (pad.getVelocityToLevel () / 100.0);

        // Play-back
        sampleZone.setOneShot (isOneShot);
        sampleZone.setStart (0);
        sampleZone.setStop (sampleLength);

        // Filter
        final FilterType filterType = FILTER_MAP.get (Integer.valueOf (pad.getFilter1Type ()));
        if (filterType != null)
        {
            final double cutoff = pad.getFilter1Freq () / 100.0 * IFilter.MAX_FREQUENCY;
            // Poles are unknown, let's go with 4
            final IFilter filter = new DefaultFilter (filterType, 4, cutoff, pad.getFilter1Res () / 100.0);

            // The filter envelope starts at the cutoff plus the amount and goes back to the
            // cutoff within the envelope time
            final int envelopeAmount = pad.getFilter1EnvelopeAmount ();
            if (envelopeAmount != 0)
            {
                final IEnvelope filterEnvelope = new DefaultEnvelope ();
                filterEnvelope.setAttackTime (0);
                filterEnvelope.setDecayTime (toSeconds (pad.getFilter1EnvelopeTime (), false));
                filterEnvelope.setSustainLevel (0);
                filterEnvelope.setReleaseTime (0);
                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                cutoffEnvelopeModulator.setSource (filterEnvelope);
                cutoffEnvelopeModulator.setDepth (Math.clamp (envelopeAmount / 50.0, -1.0, 1.0));
            }

            filter.getCutoffVelocityModulator ().setDepth (pad.getFilter1VelocityToFrequency () / 100.0);
            sampleZone.setFilter (filter);
        }

        convertLfo (pad, sampleZone);

        return Optional.of (sampleZone);
    }


    /**
     * Convert the LFO of a pad. The rate and the delay are synchronized to the tempo, which is
     * assumed to be 120 BPM. The depths of the amounts are unknown: a pitch amount of 100 is
     * assumed to be 1 octave, a level amount of 100 the full volume range and a filter amount of
     * 100 the full cutoff range. The amount for filter 2 is ignored since only filter 1 is
     * converted.
     *
     * @param pad The pad
     * @param sampleZone The sample zone to which to add the LFO modulations
     */
    private static void convertLfo (final AkaiMPC1000Pad pad, final ISampleZone sampleZone)
    {
        final int rate = pad.getLfoRate ();
        if (rate <= 0)
            return;

        final double secondsPerBeat = 60.0 / TEMPO;
        final ILfo lfo = new DefaultLfo ();
        final int waveform = pad.getLfoWaveform ();
        lfo.setWaveform (waveform >= 0 && waveform < LFO_WAVEFORMS.length ? LFO_WAVEFORMS[waveform] : LfoWaveform.TRIANGLE);
        lfo.setRate (1.0 / (rate / TICKS_PER_BEAT * secondsPerBeat));
        lfo.setDelay (pad.getLfoDelay () / TICKS_PER_BEAT * secondsPerBeat);
        // The delay starts with the note, therefore the LFO is expected to restart with each note
        lfo.setKeySync (true);

        setLfoModulator (sampleZone.getPitchLfoModulator (), lfo, pad.getLfoPitch () / 100.0 * PITCH_SCALE);
        setLfoModulator (sampleZone.getAmplitudeLfoModulator (), lfo, pad.getLfoLevel () / 100.0);
        final Optional<IFilter> filter = sampleZone.getFilter ();
        if (filter.isPresent ())
            setLfoModulator (filter.get ().getCutoffLfoModulator (), lfo, pad.getLfoFilter1 () / 100.0);
    }


    /**
     * Set the oscillator and the depth of a modulator, if the depth is not 0.
     *
     * @param modulator The modulator
     * @param lfo The oscillator
     * @param depth The depth
     */
    private static void setLfoModulator (final ILfoModulator modulator, final ILfo lfo, final double depth)
    {
        if (depth <= 0)
            return;
        modulator.setSource (lfo);
        modulator.setDepth (depth);
    }


    /**
     * Convert the amplitude envelope of a pad. The MPC has an attack and a decay. The decay mode
     * 'Start' fades out the sound directly after the attack. The decay mode 'End' fades out the
     * sound so that the fade ends at the end of the sample. In play mode 'Note On' the sound stops
     * when the pad is released, which is converted to a release with the decay time.
     *
     * @param pad The pad
     * @param isOneShot True if the sample is played up to its end regardless of a note-off
     * @param isLooped True if the sample contains a loop
     * @param sampleLength The length of the sample in seconds
     * @return The envelope
     */
    private static IEnvelope convertEnvelope (final AkaiMPC1000Pad pad, final boolean isOneShot, final boolean isLooped, final double sampleLength)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        final double attack = toSeconds (pad.getAttack (), false);
        final double decay = toSeconds (pad.getDecay (), true);
        envelope.setAttackTime (attack);

        if (pad.getDecayMode () == 1)
        {
            // Decay mode 'Start': fade out right after the attack
            envelope.setDecayTime (decay);
            envelope.setSustainLevel (0);
        }
        else if (decay > 0 && !isLooped)
        {
            // Decay mode 'End': hold the level and fade out at the end of the sample
            envelope.setHoldTime (Math.max (0, sampleLength - attack - decay));
            envelope.setDecayTime (decay);
            envelope.setSustainLevel (0);
        }
        else
            envelope.setSustainLevel (1.0);

        envelope.setReleaseTime (isOneShot ? sampleLength : decay);
        return envelope;
    }


    private static double toSeconds (final int value, final boolean isLong)
    {
        // No real idea, assume 2 seconds max
        return value / 100.0 * (isLong ? 6.0 : 2.0);
    }
}
