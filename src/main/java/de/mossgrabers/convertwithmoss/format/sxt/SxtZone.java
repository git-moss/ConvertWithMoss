// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Structure for a SXT group.
 *
 * @author Jürgen Moßgraber
 */
class SxtZone
{
    /** The index of the group to which the zone belongs. */
    long groupIndex                   = -1;

    /** MinValue = 0, MaxValue = keyRangeEnd, Default = 36. */
    int  keyRangeStart                = 36;

    /** MinValue = 0, MaxValue = keyRangeEnd, Default = 96. */
    int  keyRangeEnd                  = 96;

    /** MinValue = 1, MaxValue = velocityRangeEnd, Default = 1. */
    int  velocityRangeStart           = 1;

    /** MinValue = 1, MaxValue = 127, Default = 127. */
    int  velocityRangeEnd             = 127;

    /** MinValue = 0, MaxValue = 127, Default = 60. */
    int  rootKey                      = 60;

    /** MinValue = -50, MaxValue = 50, Default = 0. */
    int  sampleTune                   = 0;

    /** MinValue = 0, MaxValue = SampleEnd. */
    long sampleStart                  = 0;

    /** MinValue = 0, MaxValue = SampleSize. */
    long sampleEnd                    = 0;

    /** MinValue = 0, MaxValue = SampleLoopEnd. */
    long sampleLoopStart              = 0;

    /** MinValue = 0, MaxValue = SampleSize. */
    long sampleLoopEnd                = 0;

    /** MinValue = 0. */
    long sampleSize                   = 0;

    /**
     * MinValue = 0, MaxValue = 4, Default = 0. Forward = 0, Forward loop = 1, Fwd-bwd loop = 2,
     * Fwd-sus loop = 3, Backwards = 4.
     */
    int  playMode                     = 0;

    /** MinValue = 0, MaxValue = 7, Default = 0. */
    int  outputPair                   = 0;

    /** MinValue = 0, MaxValue = FadeOut - 1, Default = 0 (Off). */
    int  velocityFadeIn               = 0;

    /** MinValue = 1, MaxValue = 128, Default = 128 (Off). */
    int  velocityFadeOut              = 128;

    /** MinValue = 0, MaxValue = 1, Default = 0. */
    int  alternateMode                = 0;

    /** MinValue = -1000, MaxValue = 1000, Default = 0. */
    int  modulationToFilterFreq       = 0;
    int  modulationToModEnvDecay      = 0;
    int  modulationToLFO1Amt          = 0;
    int  modulationToFilterQ          = 0;
    int  modulationToAmpGain          = 0;
    int  modulationToLFO1Rate         = 0;
    int  velocityToFilterFreq         = 0;
    int  velocityToModEnvDecay        = 0;
    int  velocityToAmpGain            = 0;
    int  velocityToAmpEnvAttack       = 0;
    int  velocityToSampleStart        = 0;

    /** MinValue = 0, MaxValue = 1. */
    int  modWheelToFilterFreqOn       = 1;
    int  modWheelToModEnvDecayOn      = 1;
    int  modWheelToLFO1AmountOn       = 1;
    int  modWheelToFilterResOn        = 0;
    int  modWheelToVolumeOn           = 0;
    int  modWheelToLFO1RateOn         = 0;
    int  extControllerToFilterFreqOn  = 0;
    int  extControllerToModEnvDecayOn = 0;
    int  extControllerToLFO1AmountOn  = 0;
    int  extControllerToFilterResOn   = 1;
    int  extControllerToVolumeOn      = 1;
    int  extControllerToLFO1RateOn    = 1;

    // LFO1 parameters
    int  lfo1KeySync                  = 1;
    int  lfo1Freq                     = -536;
    int  lfo1TempoSyncRate            = -10500;
    int  lfo1Delay                    = 1;
    int  lfo1DelayIsOff               = 1;
    int  lfo1WaveForm                 = 101;
    int  lfo1ToPitch                  = 0;
    int  lfo1ToFilterFreq             = 0;
    int  lfo1ToAmp                    = 0;
    int  lfo1SyncMode                 = 89;

    // LFO2 parameters
    int  lfo2Freq                     = -536;
    int  lfo2Delay                    = -10500;
    int  lfo2DelayIsOff               = 1;
    int  lfo2ToPitch                  = 0;
    int  lfo2ToPan                    = 0;

    // Modulation Envelope Parameters
    int  modEnvDelay                  = -10500;
    int  modEnvDelayIsOff             = 1;
    int  modEnvAttack                 = -13159;
    int  modEnvAttackIsOff            = 0;
    int  modEnvHold                   = -14400;
    int  modEnvHoldIsOff              = 1;
    int  modEnvDecay                  = -1200;
    int  modEnvSustain                = 0;
    int  modEnvRelease                = -1200;
    int  modEnvKeyToDecay             = 0;
    int  modEnvToFilterFreq           = 0;
    int  modEnvToPitch                = 0;

    // Pitch Parameters

    /** MinValue = 0, MaxValue = 24, Default = 7. */
    int  pitchWheelRange              = 7;
    /** MinValue = -5, MaxValue = 5, Default = 0. */
    int  octave                       = 0;
    /** MinValue = -12, MaxValue = 12, Default = 0. */
    int  semitone                     = 0;
    /** MinValue = -50, MaxValue = 50, Default = 0. */
    int  cent                         = 0;
    /** MinValue = 0, MaxValue = 1200, Default = 100. */
    int  keyToPitch                   = 100;

    // Filter Parameters

    /** MinValue = 0, MaxValue = 1, Default = 1. */
    int  filterIsOn                   = 1;
    /** MinValue = 0, MaxValue = 14100, Default = 14100. */
    int  filterFreq                   = 14100;
    /** MinValue = 0, MaxValue = 1000, Default = 0. */
    int  filterResonance              = 0;
    /**
     * MinValue = 60, MaxValue = 65, Default = 61. LP24 = 60, LP12 = 61, BP12 = 62, HP12 = 63, Notch
     * = 64, LP6 = 65.
     */
    int  filterType                   = 61;
    /** MinValue = 0, MaxValue = 1200, Default = 0. */
    int  keyToFreq                    = 0;

    // Amplitude Envelope Parameters
    int  ampEnvDelay                  = -10500;
    int  ampEnvDelayIsOff             = 1;
    int  ampEnvAttack                 = -13159;
    int  ampEnvAttackIsOff            = 0;
    int  ampEnvHold                   = -14400;
    int  ampEnvHoldIsOff              = 1;
    int  ampEnvDecay                  = -7271;
    int  ampEnvSustain                = 0;
    int  ampEnvRelease                = -7271;
    int  ampEnvKeyToDecay             = 0;
    int  ampEnvGain                   = 0;

    // Pan Parameters
    int  pan                          = 0;
    int  spreadMode                   = 87;
    int  spread                       = 0;


    /**
     * Default constructor.
     */
    public SxtZone ()
    {
        // Intentionally empty
    }


    /**
     * Read all zone parameters.
     * 
     * @param in The input stream from which to read
     * @throws IOException Could not read the parameters
     */
    public void read (final InputStream in) throws IOException
    {
        this.groupIndex = StreamUtils.readUnsigned32 (in, true);

        // Mapping parameters
        this.keyRangeStart = in.read ();
        this.keyRangeEnd = in.read ();
        this.velocityRangeStart = in.read ();
        this.velocityRangeEnd = in.read ();
        this.rootKey = in.read ();
        this.sampleTune = StreamUtils.readUnsigned16 (in, true);
        this.sampleStart = StreamUtils.readUnsigned32 (in, true);
        this.sampleEnd = StreamUtils.readUnsigned32 (in, true);
        this.sampleLoopStart = StreamUtils.readUnsigned32 (in, true);
        this.sampleLoopEnd = StreamUtils.readUnsigned32 (in, true);
        this.sampleSize = StreamUtils.readUnsigned32 (in, true);
        this.playMode = in.read ();
        this.outputPair = in.read ();
        this.velocityFadeIn = in.read ();
        this.velocityFadeOut = in.read ();
        this.alternateMode = in.read ();

        // Performance parameters
        this.modulationToFilterFreq = StreamUtils.readSigned32 (in, true);
        this.modulationToModEnvDecay = StreamUtils.readSigned32 (in, true);
        this.modulationToLFO1Amt = StreamUtils.readSigned32 (in, true);
        this.modulationToFilterQ = StreamUtils.readSigned32 (in, true);
        this.modulationToAmpGain = StreamUtils.readSigned32 (in, true);
        this.modulationToLFO1Rate = StreamUtils.readSigned32 (in, true);
        this.velocityToFilterFreq = StreamUtils.readSigned32 (in, true);
        this.velocityToModEnvDecay = StreamUtils.readSigned32 (in, true);
        this.velocityToAmpGain = StreamUtils.readSigned32 (in, true);
        this.velocityToAmpEnvAttack = StreamUtils.readSigned32 (in, true);
        this.velocityToSampleStart = StreamUtils.readSigned32 (in, true);
        this.modWheelToFilterFreqOn = in.read ();
        this.modWheelToModEnvDecayOn = in.read ();
        this.modWheelToLFO1AmountOn = in.read ();
        this.modWheelToFilterResOn = in.read ();
        this.modWheelToVolumeOn = in.read ();
        this.modWheelToLFO1RateOn = in.read ();
        this.extControllerToFilterFreqOn = in.read ();
        this.extControllerToModEnvDecayOn = in.read ();
        this.extControllerToLFO1AmountOn = in.read ();
        this.extControllerToFilterResOn = in.read ();
        this.extControllerToVolumeOn = in.read ();
        this.extControllerToLFO1RateOn = in.read ();

        // LFO1 parameters
        this.lfo1KeySync = in.read ();
        this.lfo1Freq = StreamUtils.readSigned32 (in, true);
        this.lfo1TempoSyncRate = in.read ();
        this.lfo1Delay = StreamUtils.readSigned32 (in, true);
        this.lfo1DelayIsOff = in.read ();
        this.lfo1WaveForm = in.read ();
        this.lfo1ToPitch = StreamUtils.readSigned32 (in, true);
        this.lfo1ToFilterFreq = StreamUtils.readSigned32 (in, true);
        this.lfo1ToAmp = StreamUtils.readSigned32 (in, true);
        this.lfo1SyncMode = in.read ();

        // LFO2 parameters
        this.lfo2Freq = StreamUtils.readSigned32 (in, true);
        this.lfo2Delay = StreamUtils.readSigned32 (in, true);
        this.lfo2DelayIsOff = in.read ();
        this.lfo2ToPitch = StreamUtils.readSigned32 (in, true);
        this.lfo2ToPan = StreamUtils.readSigned32 (in, true);

        // Modulation Envelope Parameters
        this.modEnvDelay = StreamUtils.readSigned32 (in, true);
        this.modEnvDelayIsOff = in.read ();
        this.modEnvAttack = StreamUtils.readSigned32 (in, true);
        this.modEnvAttackIsOff = in.read ();
        this.modEnvHold = StreamUtils.readSigned32 (in, true);
        this.modEnvHoldIsOff = in.read ();
        this.modEnvDecay = StreamUtils.readSigned32 (in, true);
        this.modEnvSustain = StreamUtils.readSigned32 (in, true);
        this.modEnvRelease = StreamUtils.readSigned32 (in, true);
        this.modEnvKeyToDecay = StreamUtils.readSigned32 (in, true);
        this.modEnvToFilterFreq = StreamUtils.readSigned32 (in, true);
        this.modEnvToPitch = StreamUtils.readSigned32 (in, true);

        // Pitch Parameters
        this.pitchWheelRange = in.read ();
        this.octave = in.read ();
        this.semitone = StreamUtils.readSigned16 (in, true);
        this.cent = StreamUtils.readSigned16 (in, true);
        this.keyToPitch = StreamUtils.readSigned32 (in, true);

        // Filter Parameters
        this.filterIsOn = in.read ();
        this.filterFreq = StreamUtils.readSigned32 (in, true);
        this.filterResonance = StreamUtils.readSigned32 (in, true);
        this.filterType = in.read ();
        this.keyToFreq = StreamUtils.readSigned32 (in, true);

        // Modulation Envelope Parameters
        this.ampEnvDelay = StreamUtils.readSigned32 (in, true);
        this.ampEnvDelayIsOff = in.read ();
        this.ampEnvAttack = StreamUtils.readSigned32 (in, true);
        this.ampEnvAttackIsOff = in.read ();
        this.ampEnvHold = StreamUtils.readSigned32 (in, true);
        this.ampEnvHoldIsOff = in.read ();
        this.ampEnvDecay = StreamUtils.readSigned32 (in, true);
        this.ampEnvSustain = StreamUtils.readSigned32 (in, true);
        this.ampEnvRelease = StreamUtils.readSigned32 (in, true);
        this.ampEnvKeyToDecay = StreamUtils.readSigned32 (in, true);
        this.ampEnvGain = StreamUtils.readSigned32 (in, true);

        // Pan Parameters
        this.pan = StreamUtils.readSigned32 (in, true);
        this.spreadMode = in.read ();
        this.spread = StreamUtils.readSigned32 (in, true);
    }


    /**
     * Write all zone parameters.
     * 
     * @param out The output stream from which to read
     * @throws IOException Could not read the parameters
     */
    public void write (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.groupIndex, true);

        // Mapping parameters
        out.write (this.keyRangeStart);
        out.write (this.keyRangeEnd);
        out.write (this.velocityRangeStart);
        out.write (this.velocityRangeEnd);
        out.write (this.rootKey);
        StreamUtils.writeUnsigned16 (out, this.sampleTune, true);
        StreamUtils.writeUnsigned32 (out, this.sampleStart, true);
        StreamUtils.writeUnsigned32 (out, this.sampleEnd, true);
        StreamUtils.writeUnsigned32 (out, this.sampleLoopStart, true);
        StreamUtils.writeUnsigned32 (out, this.sampleLoopEnd, true);
        StreamUtils.writeUnsigned32 (out, this.sampleSize, true);
        out.write (this.playMode);
        out.write (this.outputPair);
        out.write (this.velocityFadeIn);
        out.write (this.velocityFadeOut);
        out.write (this.alternateMode);

        // Performance parameters
        StreamUtils.writeSigned32 (out, this.modulationToFilterFreq, true);
        StreamUtils.writeSigned32 (out, this.modulationToModEnvDecay, true);
        StreamUtils.writeSigned32 (out, this.modulationToLFO1Amt, true);
        StreamUtils.writeSigned32 (out, this.modulationToFilterQ, true);
        StreamUtils.writeSigned32 (out, this.modulationToAmpGain, true);
        StreamUtils.writeSigned32 (out, this.modulationToLFO1Rate, true);
        StreamUtils.writeSigned32 (out, this.velocityToFilterFreq, true);
        StreamUtils.writeSigned32 (out, this.velocityToModEnvDecay, true);
        StreamUtils.writeSigned32 (out, this.velocityToAmpGain, true);
        StreamUtils.writeSigned32 (out, this.velocityToAmpEnvAttack, true);
        StreamUtils.writeSigned32 (out, this.velocityToSampleStart, true);
        out.write (this.modWheelToFilterFreqOn);
        out.write (this.modWheelToModEnvDecayOn);
        out.write (this.modWheelToLFO1AmountOn);
        out.write (this.modWheelToFilterResOn);
        out.write (this.modWheelToVolumeOn);
        out.write (this.modWheelToLFO1RateOn);
        out.write (this.extControllerToFilterFreqOn);
        out.write (this.extControllerToModEnvDecayOn);
        out.write (this.extControllerToLFO1AmountOn);
        out.write (this.extControllerToFilterResOn);
        out.write (this.extControllerToVolumeOn);
        out.write (this.extControllerToLFO1RateOn);

        // LFO1 parameters
        out.write (this.lfo1KeySync);
        StreamUtils.writeSigned32 (out, this.lfo1Freq, true);
        out.write (this.lfo1TempoSyncRate);
        StreamUtils.writeSigned32 (out, this.lfo1Delay, true);
        out.write (this.lfo1DelayIsOff);
        out.write (this.lfo1WaveForm);
        StreamUtils.writeSigned32 (out, this.lfo1ToPitch, true);
        StreamUtils.writeSigned32 (out, this.lfo1ToFilterFreq, true);
        StreamUtils.writeSigned32 (out, this.lfo1ToAmp, true);
        out.write (this.lfo1SyncMode);

        // LFO2 parameters
        StreamUtils.writeSigned32 (out, this.lfo2Freq, true);
        StreamUtils.writeSigned32 (out, this.lfo2Delay, true);
        out.write (this.lfo2DelayIsOff);
        StreamUtils.writeSigned32 (out, this.lfo2ToPitch, true);
        StreamUtils.writeSigned32 (out, this.lfo2ToPan, true);

        // Modulation Envelope Parameters
        StreamUtils.writeSigned32 (out, this.modEnvDelay, true);
        out.write (this.modEnvDelayIsOff);
        StreamUtils.writeSigned32 (out, this.modEnvAttack, true);
        out.write (this.modEnvAttackIsOff);
        StreamUtils.writeSigned32 (out, this.modEnvHold, true);
        out.write (this.modEnvHoldIsOff);
        StreamUtils.writeSigned32 (out, this.modEnvDecay, true);
        StreamUtils.writeSigned32 (out, this.modEnvSustain, true);
        StreamUtils.writeSigned32 (out, this.modEnvRelease, true);
        StreamUtils.writeSigned32 (out, this.modEnvKeyToDecay, true);
        StreamUtils.writeSigned32 (out, this.modEnvToFilterFreq, true);
        StreamUtils.writeSigned32 (out, this.modEnvToPitch, true);

        // Pitch Parameters
        out.write (this.pitchWheelRange);
        out.write (this.octave);
        StreamUtils.writeSigned16 (out, this.semitone, true);
        StreamUtils.writeSigned16 (out, this.cent, true);
        StreamUtils.writeSigned32 (out, this.keyToPitch, true);

        // Filter Parameters
        out.write (this.filterIsOn);
        StreamUtils.writeSigned32 (out, this.filterFreq, true);
        StreamUtils.writeSigned32 (out, this.filterResonance, true);
        out.write (this.filterType);
        StreamUtils.writeSigned32 (out, this.keyToFreq, true);

        // Modulation Envelope Parameters
        StreamUtils.writeSigned32 (out, this.ampEnvDelay, true);
        out.write (this.ampEnvDelayIsOff);
        StreamUtils.writeSigned32 (out, this.ampEnvAttack, true);
        out.write (this.ampEnvAttackIsOff);
        StreamUtils.writeSigned32 (out, this.ampEnvHold, true);
        out.write (this.ampEnvHoldIsOff);
        StreamUtils.writeSigned32 (out, this.ampEnvDecay, true);
        StreamUtils.writeSigned32 (out, this.ampEnvSustain, true);
        StreamUtils.writeSigned32 (out, this.ampEnvRelease, true);
        StreamUtils.writeSigned32 (out, this.ampEnvKeyToDecay, true);
        StreamUtils.writeSigned32 (out, this.ampEnvGain, true);

        // Pan Parameters
        StreamUtils.writeSigned32 (out, this.pan, true);
        out.write (this.spreadMode);
        StreamUtils.writeSigned32 (out, this.spread, true);
    }


    /**
     * Fill the data from the SXT zone into a sample zone.
     * 
     * @param zone The zone into which to fill the data
     */
    public void fillInto (final ISampleZone zone)
    {
        zone.setKeyLow (this.keyRangeStart);
        zone.setKeyHigh (this.keyRangeEnd);
        zone.setVelocityLow (this.velocityRangeStart);
        zone.setVelocityHigh (this.velocityRangeEnd);
        zone.setVelocityCrossfadeLow (this.velocityFadeIn);
        zone.setVelocityCrossfadeHigh (this.velocityFadeOut);
        zone.setKeyRoot (this.rootKey);
        zone.setStart ((int) this.sampleStart);
        zone.setStop ((int) this.sampleEnd);
        zone.setTune (this.octave * 12 + this.semitone + this.cent / 100.0 + this.sampleTune / 100.0);
        zone.setBendUp (this.pitchWheelRange);
        zone.setBendDown (this.pitchWheelRange);

        // Set loop
        boolean hasLoop = true;
        boolean isAlternating = false;
        switch (this.playMode)
        {
            // Forward
            case 0:
                hasLoop = false;
                break;
            // Forward loop
            case 1:
                // Already set-up
                break;
            // Fwd-bwd loop
            case 2:
                isAlternating = true;
                break;
            // Fwd-sus loop
            case 3:
                break;
            // Backwards
            case 4:
                hasLoop = false;
                zone.setReversed (true);
                break;
            // Unknown
            default:
                break;
        }
        if (hasLoop)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setStart ((int) this.sampleLoopStart);
            loop.setEnd ((int) this.sampleLoopEnd);
            if (isAlternating)
                loop.setType (LoopType.ALTERNATING);
        }

        if (this.alternateMode > 0)
            zone.setPlayLogic (PlayLogic.ROUND_ROBIN);

        // Pitch Modulation envelope
        if (this.modEnvToPitch > 0)
        {
            final IModulator pitchModulator = zone.getPitchModulator ();
            pitchModulator.setDepth (this.modEnvToPitch / 1000.0);
            final IEnvelope modEnvelope = pitchModulator.getSource ();
            if (this.modEnvDelayIsOff == 0)
                modEnvelope.setDelay (envelopeTimeCentsToSeconds (this.modEnvDelay));
            if (this.modEnvAttackIsOff == 0)
                modEnvelope.setAttack (envelopeTimeCentsToSeconds (this.modEnvAttack));
            if (this.modEnvHoldIsOff == 0)
                modEnvelope.setHold (envelopeTimeCentsToSeconds (this.modEnvHold));
            modEnvelope.setDecay (envelopeTimeCentsToSeconds (this.modEnvDecay));
            modEnvelope.setSustain (this.modEnvSustain / 1000.0);
            modEnvelope.setRelease (envelopeTimeCentsToSeconds (this.modEnvRelease));
        }

        // Set filter
        if (this.filterIsOn > 0)
        {
            final double freqHz = 440 * Math.pow (2, (this.filterFreq - 6900) / 1200);

            FilterType type;
            int poles = 2;
            switch (this.filterType)
            {
                // LP24
                default:
                case 60:
                    type = FilterType.LOW_PASS;
                    poles = 4;
                    break;

                // LP12
                case 61:
                    type = FilterType.LOW_PASS;
                    break;

                // BP12
                case 62:
                    type = FilterType.BAND_PASS;
                    break;

                // HP12
                case 63:
                    type = FilterType.HIGH_PASS;
                    break;

                // Notch
                case 64:
                    type = FilterType.BAND_REJECTION;
                    break;

                // LP6
                case 65:
                    type = FilterType.LOW_PASS;
                    poles = 1;
                    break;
            }
            final IFilter filter = new DefaultFilter (type, poles, freqHz, this.filterResonance / 1000.0);
            zone.setFilter (filter);
            if (this.modEnvToFilterFreq != 0)
            {
                final IModulator cutoffModulator = filter.getCutoffModulator ();
                cutoffModulator.setDepth (this.modEnvToFilterFreq / 1000.0);
                final IEnvelope modEnvelope = cutoffModulator.getSource ();
                if (this.modEnvDelayIsOff == 0)
                    modEnvelope.setDelay (envelopeTimeCentsToSeconds (this.modEnvDelay));
                if (this.modEnvAttackIsOff == 0)
                    modEnvelope.setAttack (envelopeTimeCentsToSeconds (this.modEnvAttack));
                if (this.modEnvHoldIsOff == 0)
                    modEnvelope.setHold (envelopeTimeCentsToSeconds (this.modEnvHold));
                modEnvelope.setDecay (envelopeTimeCentsToSeconds (this.modEnvDecay));
                modEnvelope.setSustain (this.modEnvSustain / 1000.0);
                modEnvelope.setRelease (envelopeTimeCentsToSeconds (this.modEnvRelease));
            }
        }

        // Set amplitude envelope
        final IModulator amplitudeModulator = zone.getAmplitudeModulator ();
        final IEnvelope ampEnvelope = amplitudeModulator.getSource ();
        if (this.ampEnvDelayIsOff == 0)
            ampEnvelope.setDelay (envelopeTimeCentsToSeconds (this.ampEnvDelay));
        if (this.ampEnvAttackIsOff == 0)
            ampEnvelope.setAttack (envelopeTimeCentsToSeconds (this.ampEnvAttack));
        if (this.ampEnvHoldIsOff == 0)
            ampEnvelope.setHold (envelopeTimeCentsToSeconds (this.ampEnvHold));
        ampEnvelope.setDecay (envelopeTimeCentsToSeconds (this.ampEnvDecay));
        ampEnvelope.setSustain (this.ampEnvSustain / 1000.0);
        ampEnvelope.setRelease (envelopeTimeCentsToSeconds (this.ampEnvRelease));

        // Set gain and panorama
        final double gain = Math.pow ((this.ampEnvGain + 1440) / 1440, 3);
        final double dBValue = 20 * Math.log10 (gain);
        zone.setGain (dBValue);
        zone.setPanorama (this.pan / 1000.0);
    }


    /**
     * Set the data of the SXT zone from a sample zone.
     * 
     * @param zone The zone from which to get the data
     * @throws IOException Audio metadata could not be loaded
     */
    public void fillFrom (final ISampleZone zone) throws IOException
    {
        this.keyRangeStart = zone.getKeyLow ();
        this.keyRangeEnd = zone.getKeyHigh ();
        this.velocityRangeStart = MathUtils.clamp (zone.getVelocityLow (), 1, 127);
        this.velocityRangeEnd = MathUtils.clamp (zone.getVelocityHigh (), 1, 127);
        this.velocityFadeIn = zone.getVelocityCrossfadeLow ();
        final int velocityCrossfadeHigh = zone.getVelocityCrossfadeHigh ();
        this.velocityFadeOut = velocityCrossfadeHigh == 0 ? 0x80 : MathUtils.clamp (127 - velocityCrossfadeHigh, 1, 127);
        this.rootKey = zone.getKeyRoot ();
        this.sampleStart = zone.getStart ();
        this.sampleEnd = zone.getStop ();
        final IAudioMetadata audioMetadata = zone.getSampleData ().getAudioMetadata ();
        this.sampleSize = audioMetadata.getNumberOfSamples ();

        final double tune = zone.getTune ();
        final int semitones = (int) (tune / 100);
        this.octave = semitones / 12;
        this.semitone = semitones % 12;
        this.cent = (int) (tune % 100);

        this.pitchWheelRange = zone.getBendUp ();
        this.pitchWheelRange = zone.getBendDown ();

        // Loop
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            this.playMode = 0;
        else
        {
            if (zone.isReversed ())
                this.playMode = 4;
            else
            {
                final ISampleLoop loop = loops.get (0);
                this.playMode = loop.getType () == LoopType.ALTERNATING ? 2 : 1;
                this.sampleLoopStart = loop.getStart ();
                this.sampleLoopEnd = loop.getEnd ();
            }
        }

        if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
            this.alternateMode = 1;

        // Pitch Modulation envelope
        final IModulator pitchModulator = zone.getPitchModulator ();
        final double depth = pitchModulator.getDepth ();
        if (depth > 0)
        {
            this.modEnvToPitch = (int) (depth * 1000.0);
            final IEnvelope modEnvelope = pitchModulator.getSource ();
            final double delay = modEnvelope.getDelay ();
            if (delay >= 0)
            {
                this.modEnvDelayIsOff = 1;
                this.modEnvDelay = envelopeTimeSecondsToCents (delay);
            }
            final double attack = modEnvelope.getAttack ();
            if (attack >= 0)
            {
                this.modEnvAttackIsOff = 1;
                this.modEnvAttack = envelopeTimeSecondsToCents (attack);
            }
            final double hold = modEnvelope.getHold ();
            if (hold >= 0)
            {
                this.modEnvHoldIsOff = 1;
                this.modEnvHold = envelopeTimeSecondsToCents (hold);
            }
            this.modEnvDecay = envelopeTimeSecondsToCents (modEnvelope.getDecay ());
            final double sustain = modEnvelope.getSustain ();
            this.modEnvSustain = sustain < 0 ? 1000 : (int) (sustain * 1000);
            this.modEnvRelease = envelopeTimeSecondsToCents (modEnvelope.getRelease ());
        }

        // Set filter
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isPresent ())
        {
            final IFilter filter = optFilter.get ();
            this.filterIsOn = 1;

            this.filterFreq = (int) (Math.log (filter.getCutoff () / 440.0) * 1200 + 6900);

            int poles = filter.getPoles ();
            switch (filter.getType ())
            {
                default:
                case LOW_PASS:
                    if (poles == 1)
                        this.filterType = 65;
                    else if (poles == 2)
                        this.filterType = 61;
                    else
                        this.filterType = 60;
                    break;

                case BAND_PASS:
                    this.filterType = 62;
                    break;

                // HP12
                case HIGH_PASS:
                    this.filterType = 63;
                    break;

                // Notch
                case BAND_REJECTION:
                    this.filterType = 64;
                    break;
            }

            final IModulator cutoffModulator = filter.getCutoffModulator ();
            final double modEnvDepth = cutoffModulator.getDepth ();
            if (modEnvDepth > 0)
            {
                this.modEnvToPitch = (int) (modEnvDepth * 1000.0);
                final IEnvelope modEnvelope = cutoffModulator.getSource ();
                final double delay = modEnvelope.getDelay ();
                if (delay >= 0)
                {
                    this.modEnvDelayIsOff = 1;
                    this.modEnvDelay = envelopeTimeSecondsToCents (delay);
                }
                final double attack = modEnvelope.getAttack ();
                if (attack >= 0)
                {
                    this.modEnvAttackIsOff = 1;
                    this.modEnvAttack = envelopeTimeSecondsToCents (attack);
                }
                final double hold = modEnvelope.getHold ();
                if (hold >= 0)
                {
                    this.modEnvHoldIsOff = 1;
                    this.modEnvHold = envelopeTimeSecondsToCents (hold);
                }
                this.modEnvDecay = envelopeTimeSecondsToCents (modEnvelope.getDecay ());
                final double sustain = modEnvelope.getSustain ();
                this.modEnvSustain = sustain < 0 ? 1000 : (int) (sustain * 1000);
                this.modEnvRelease = envelopeTimeSecondsToCents (modEnvelope.getRelease ());
            }
        }

        // Set amplitude envelope
        final IModulator amplitudeModulator = zone.getAmplitudeModulator ();
        final IEnvelope ampEnvelope = amplitudeModulator.getSource ();
        final double delay = ampEnvelope.getDelay ();
        if (delay >= 0)
        {
            this.ampEnvDelayIsOff = 1;
            this.ampEnvDelay = envelopeTimeSecondsToCents (delay);
        }
        final double attack = ampEnvelope.getAttack ();
        if (attack >= 0)
        {
            this.ampEnvAttackIsOff = 1;
            this.ampEnvAttack = envelopeTimeSecondsToCents (attack);
        }
        final double hold = ampEnvelope.getHold ();
        if (hold >= 0)
        {
            this.ampEnvHoldIsOff = 1;
            this.ampEnvHold = envelopeTimeSecondsToCents (hold);
        }
        this.ampEnvDecay = envelopeTimeSecondsToCents (ampEnvelope.getDecay ());
        final double sustain = ampEnvelope.getSustain ();
        this.ampEnvSustain = sustain < 0 ? 1000 : (int) (sustain * 1000);
        this.ampEnvRelease = envelopeTimeSecondsToCents (ampEnvelope.getRelease ());

        // Set gain and panorama
        final double dBValue = zone.getGain ();
        double gainRatio = Math.pow (10, dBValue / 20);
        this.ampEnvGain = (int) (Math.pow (gainRatio, 1 / 3) * 1440 - 1440);

        this.pan = (int) (zone.getPanorama () * 1000.0);
    }


    private static double envelopeTimeCentsToSeconds (final int cents)
    {
        return Math.pow (2, cents / 1200.0);
    }


    private static int envelopeTimeSecondsToCents (final double seconds)
    {
        return (int) (Math.log (seconds < 0 ? 0 : seconds) / Math.log (2) * 1200.0);
    }
}
