// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.LfoIntensityConverter;


/**
 * Creates the modulator chunks of a Kontakt 5+ group from the settings of a sample zone. The chunks
 * are created from templates (taken from files saved by Kontakt) in which the values are replaced.
 *
 * @author Jürgen Moßgraber
 */
final class GroupModulators
{
    /** The version of the parameter arrays which contain the modulators. */
    static final int             PARAMETER_ARRAY_VERSION = 0x12;

    // Flag of a modulated parameter which inverts the modulation
    private static final int     FLAG_INVERT             = 0x02;
    // The modulation is added to the parameter instead of scaling it, e.g. 'Intensity (add)'
    private static final int     FLAG_ADD                = 0x04;
    // The pitch intensity of Kontakt (1.0) covers 1 octave
    private static final double  PITCH_INTENSITY_CENTS   = 1200.0;
    // The pitch intensity of Kontakt (1.0) covers 1 octave, the depth of the model is relative to
    // MAX_ENVELOPE_DEPTH cents
    private static final LfoIntensityConverter PITCH_LFO_CONVERTER  = LfoIntensityConverter.linear (PITCH_INTENSITY_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH);
    private static final LfoIntensityConverter CUTOFF_LFO_CONVERTER = LfoIntensityConverter.linear (1.0);

    //@formatter:off
    // An AHDSR envelope which modulates the pitch, taken from a file saved by Kontakt 7
    private static final byte [] PITCH_ENVELOPE          =
    {
        1, -127, 0, 68, 0, 0, 0, 1, 0, 0, 0, 5, 0, 0, 0, 112, 105, 116, 99, 104, 0, 0, 0, 63, -1, -1, 16, 0,
        0, 15, 0, 0, 0, 69, 78, 86, 95, 65, 72, 68, 83, 82, 95, 80, 73, 84, 67, 72, 0, 0, 0, 0, 1, 0, 8, 0,
        0, 0, 9, 0, 0, 0, 69, 78, 86, 95, 65, 72, 68, 83, 82, 2, 0, 0, 0, 0, 0, 0, 0, 111, 0, 0, 0, 7, 0,
        105, 0, 0, 0, 1, -112, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 86, 0, 0, 0, 63, 0, 80, 0, 0, 0, 0, 17,
        0, -102, -103, -103, -66, 0, 0, -96, 64, 0, 0, -32, 64, 0, 0, -64, 64, 0, 0, 16, 65, -51, -52, 76,
        63, 0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63,
        0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0
    };

    // An SV filter insert effect, taken from a file saved by Kontakt 6.7
    private static final byte [] FILTER_EFFECT           =
    {
        1, 80, 0, 22, 0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, -128, 63, 0, 0, -128, 63, -1, -1, -1, -1,
        0, 0, 0, 0, 25, 0, 0, 0, 24, 0, 19, 0, 0, 0, 0, -110, 0, 50, 0, 0, 0, 50, 0, 0, 0, 0, 0, -128, 63, 0,
        0, 0, 0
    };

    // The second block of a LFO (the source of an internal modulator), taken from a file saved by
    // Kontakt 7
    private static final byte [] LFO_BLOCK               =
    {
        8, 0, 50, 0, 0, 0, 0, 114, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -128, 63, 0, 0, 0, 63, 0, 0, 0, 0, 1, 0,
        0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0, 0, 0, -128, -65, 0, 0, 0, 0, 0, 0, -128, 63, 0
    };
    //@formatter:on

    // The parameter names of the modulation targets
    private static final String  TARGET_VOLUME           = "volume";
    private static final String  TARGET_PITCH            = "pitch";
    private static final String  TARGET_CUTOFF           = "filterCutoff";
    private static final String  TARGET_INTENSITY        = "intensity";
    private static final int     FLAGS_DEFAULT           = 0x10;

    // The index of the modulation source type of internal modulators
    private static final int     SOURCE_INDEX_ENVELOPE   = 2;
    private static final int     SOURCE_INDEX_LFO        = 1;
    // Unknown values found in the internal modulators of envelopes and LFOs saved by Kontakt 7
    private static final int     UNKNOWN_ENVELOPE        = 0;
    private static final int     UNKNOWN_LFO             = 3;
    private static final String  SOURCE_AHDSR            = "ENV_AHDSR";

    // The source types of external modulators
    private static final int     SOURCE_MIDI_CC          = 4;
    private static final int     SOURCE_KEY_POSITION     = 5;
    private static final int     MOD_WHEEL_CC            = 1;
    private static final int     MAX_SOURCE_NAME_LENGTH  = 64;
    private static final int     MAX_PARAMETER_TRAILER   = 4;
    private static final int     SOURCE_VELOCITY         = 6;

    // The offsets in the LFO block
    private static final int     LFO_WAVEFORM            = 9;
    private static final int     LFO_DELAY               = 13;
    private static final int     LFO_FREQUENCY           = 17;
    private static final int     LFO_START_PHASE         = 25;

    // The private data of a group starts with the output routing table (136 entries of 12 bytes)
    // followed by 25 unknown bytes, the version of the insert effects (16 bit) and the 8 slots
    private static final int     INSERT_EFFECTS_SLOTS    = 136 * 12 + 25 + 2;
    private static final int     PAR_FX                  = 0x25;
    /** The insert effect slot in which the filter is stored. */
    static final int             FILTER_SLOT             = 0;
    // The offsets in the filter effect data
    private static final int     FILTER_BYPASS           = 16;
    private static final int     FILTER_TYPE             = 43 + 3;
    private static final int     FILTER_TYPE2            = 43 + 7;
    private static final int     FILTER_CUTOFF           = 43 + 11;
    private static final int     FILTER_RESONANCE        = 43 + 15;
    // The cutoff of the SV filters is MIDI note 20 + 115 * value
    private static final double  SV_NOTE_OFFSET          = 20;
    private static final double  SV_NOTE_RANGE           = 115;

    // The internal modulator data starts with 1 byte (always 1), the version (16 bit) and the
    // size of the first block (32 bit)
    private static final int     BLOCK1_OFFSET           = 7;
    // The values of an AHDSR envelope in the second block of an internal modulator
    private static final int     ENVELOPE_CURVE          = 34;
    private static final int     ENVELOPE_ATTACK         = 38;
    private static final int     ENVELOPE_DECAY          = 42;
    private static final int     ENVELOPE_HOLD           = 46;
    private static final int     ENVELOPE_RELEASE        = 50;
    private static final int     ENVELOPE_SUSTAIN        = 54;
    private static final int     ENVELOPE_AHD_ONLY       = 58;

    // The external modulator data starts with 3 bytes and the size of the block (32 bit)
    private static final int     EXTERNAL_BLOCK_OFFSET   = 7;

    // The start criteria entry, the values are the defaults of Kontakt
    private static final int     START_CRITERIA_VERSION  = 0x70;
    private static final int     START_CRITERIA_KEY      = 24;
    private static final int     START_CRITERIA_CC       = 1;
    private static final int     START_CRITERIA_CC_VALUE = 64;


    /**
     * Private due to utility class.
     */
    private GroupModulators ()
    {
        // Intentionally empty
    }


    /**
     * Creates the internal modulators of a group: the amplitude envelope and optionally a pitch
     * envelope.
     *
     * @param ampEnvelopeTemplate The template for the amplitude envelope (an AHDSR envelope which
     *            modulates the volume)
     * @param zone The zone from which to take the envelopes
     * @return The parameter array chunk which contains the modulators
     */
    static KontaktPresetChunk createInternalModulators (final KontaktPresetChunk ampEnvelopeTemplate, final ISampleZone zone)
    {
        final List<KontaktPresetChunk> modulators = new ArrayList<> ();

        final IEnvelopeModulator ampModulator = zone.getAmplitudeEnvelopeModulator ();
        final IEnvelope ampEnvelope = ampModulator.getSource ();
        final byte [] ampData = ampEnvelopeTemplate.getPublicData ().clone ();
        setIntensity (ampData, BLOCK1_OFFSET, Math.clamp (ampModulator.getDepth (), 0, 1));
        setEnvelope (ampData, ampEnvelope, -ampEnvelope.getAttackSlope (), limitToDefault (ampEnvelope.getAttackTime (), 0), limitToDefault (ampEnvelope.getHoldTime (), 0), limitToDefault (ampEnvelope.getDecayTime (), 0), limitToDefault (ampEnvelope.getSustainLevel (), 1), limitToDefault (ampEnvelope.getReleaseTime (), 1));
        modulators.add (createChunk (ampEnvelopeTemplate.getId (), ampData));

        // The depth of the model is relative to MAX_ENVELOPE_DEPTH cents
        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double pitchIntensity = getPitchEnvelopeIntensity (zone);
        if (pitchIntensity != 0)
        {
            final IEnvelope pitchEnvelope = pitchModulator.getSource ();
            final byte [] pitchData = PITCH_ENVELOPE.clone ();
            setIntensity (pitchData, BLOCK1_OFFSET, pitchIntensity);
            // If the start level is set and not 0 (which is normally the case for a pitch
            // envelope), it is assumed to be the attack level and only the attack time is used as
            // the decay, since this is the most common application for pitch envelopes
            if (pitchEnvelope.getStartLevel () > 0)
                setEnvelope (pitchData, pitchEnvelope, -pitchEnvelope.getAttackSlope (), 0, 0, limitToDefault (pitchEnvelope.getAttackTime (), 0), 0, 0);
            else
                setEnvelope (pitchData, pitchEnvelope, -pitchEnvelope.getAttackSlope (), limitToDefault (pitchEnvelope.getAttackTime (), 0), limitToDefault (pitchEnvelope.getHoldTime (), 0), limitToDefault (pitchEnvelope.getDecayTime (), 0), limitToDefault (pitchEnvelope.getSustainLevel (), 0), limitToDefault (pitchEnvelope.getReleaseTime (), 0));
            modulators.add (createChunk (KontaktPresetChunkID.PAR_INTERNAL_MOD, pitchData));
        }

        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IEnvelopeModulator cutoffModulator = filterOpt.get ().getCutoffEnvelopeModulator ();
            final double cutoffIntensity = getCutoffEnvelopeIntensity (zone);
            if (cutoffIntensity != 0)
            {
                final IEnvelope cutoffEnvelope = cutoffModulator.getSource ();
                final byte [] cutoffData = createInternalModulator (TARGET_CUTOFF, cutoffIntensity, "ENV_AHDSR_CUTOFF", true, UNKNOWN_ENVELOPE, SOURCE_AHDSR, SOURCE_INDEX_ENVELOPE, getEnvelopeBlock ());
                setEnvelope (cutoffData, cutoffEnvelope, -cutoffEnvelope.getAttackSlope (), limitToDefault (cutoffEnvelope.getAttackTime (), 0), limitToDefault (cutoffEnvelope.getHoldTime (), 0), limitToDefault (cutoffEnvelope.getDecayTime (), 0), limitToDefault (cutoffEnvelope.getSustainLevel (), 1), limitToDefault (cutoffEnvelope.getReleaseTime (), 1));
                modulators.add (createChunk (KontaktPresetChunkID.PAR_INTERNAL_MOD, cutoffData));
            }
        }

        // The pitch intensity is relative to 1 octave, volume and cutoff use the depth directly
        addLfo (modulators, TARGET_PITCH, "PITCH", zone.getPitchLfoModulator (), PITCH_LFO_CONVERTER);
        addLfo (modulators, TARGET_VOLUME, "VOLUME", zone.getAmplitudeLfoModulator (), LfoIntensityConverter.VOLUME);
        if (filterOpt.isPresent ())
            addLfo (modulators, TARGET_CUTOFF, "FILTERCUTOFF", filterOpt.get ().getCutoffLfoModulator (), CUTOFF_LFO_CONVERTER);

        // The IDs of the internal modulators are their indices
        setModulatorIds (modulators, true, 0);
        return createParameterArray (KontaktPresetChunkID.PARAMETER_ARRAY_16, modulators);
    }


    /**
     * Creates the external modulators of a group: velocity to volume and pitch-bend.
     *
     * @param velocityTemplate The template for the velocity to volume modulator
     * @param pitchbendTemplate The template for the pitch-bend modulator
     * @param zone The zone from which to take the modulation intensities
     * @return The parameter array chunk which contains the modulators
     */
    static KontaktPresetChunk createExternalModulators (final KontaktPresetChunk velocityTemplate, final KontaktPresetChunk pitchbendTemplate, final ISampleZone zone)
    {
        final List<KontaktPresetChunk> modulators = new ArrayList<> ();

        final byte [] velocityData = velocityTemplate.getPublicData ().clone ();
        setIntensity (velocityData, EXTERNAL_BLOCK_OFFSET, Math.clamp (zone.getAmplitudeVelocityModulator ().getDepth (), -1.0, 1.0));
        modulators.add (createChunk (velocityTemplate.getId (), velocityData));

        // The intensity is relative to 1 octave
        final byte [] pitchbendData = pitchbendTemplate.getPublicData ().clone ();
        setIntensity (pitchbendData, EXTERNAL_BLOCK_OFFSET, Math.clamp (zone.getBendUp () / PITCH_INTENSITY_CENTS, -1.0, 1.0));
        modulators.add (createChunk (pitchbendTemplate.getId (), pitchbendData));

        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isPresent ())
        {
            final IFilter filter = filterOpt.get ();
            final double velocityDepth = Math.clamp (filter.getCutoffVelocityModulator ().getDepth (), -1.0, 1.0);
            if (velocityDepth != 0)
                modulators.add (createExternalModulator (velocityTemplate, TARGET_CUTOFF, velocityDepth, "VEL_CUTOFF", SOURCE_VELOCITY));
            final double keyTracking = Math.clamp (filter.getCutoffKeyTracking (), -1.0, 1.0);
            if (keyTracking != 0)
                modulators.add (createExternalModulator (velocityTemplate, TARGET_CUTOFF, keyTracking, "KP_CUTOFF", SOURCE_KEY_POSITION));
        }

        // The modulation wheel controls the intensity of the oscillators. The internal modulators
        // are referenced by their slot index: the amplitude envelope, the optional pitch and cutoff
        // envelopes followed by the oscillators (see createInternalModulators)
        int slot = 1;
        if (getPitchEnvelopeIntensity (zone) != 0)
            slot++;
        if (getCutoffEnvelopeIntensity (zone) != 0)
            slot++;
        slot = addModWheelModulator (modulators, velocityTemplate, zone.getPitchLfoModulator (), PITCH_LFO_CONVERTER, slot);
        slot = addModWheelModulator (modulators, velocityTemplate, zone.getAmplitudeLfoModulator (), LfoIntensityConverter.VOLUME, slot);
        if (filterOpt.isPresent ())
            slot = addModWheelModulator (modulators, velocityTemplate, filterOpt.get ().getCutoffLfoModulator (), CUTOFF_LFO_CONVERTER, slot);

        // The IDs of the external modulators follow the ones of the internal modulators, which
        // are their slot indices (slot is now the number of internal modulators)
        setModulatorIds (modulators, false, slot);

        return createParameterArray (KontaktPresetChunkID.PARAMETER_ARRAY_32, modulators);
    }


    /**
     * Creates the start criteria of a group. Only round-robin and random are supported.
     *
     * @param zone The zone from which to take the play logic
     * @return The start criteria list chunk
     */
    static KontaktPresetChunk createStartCriteria (final ISampleZone zone)
    {
        final int mode;
        int cycleClass = 0;
        switch (zone.getPlayLogic ())
        {
            case ROUND_ROBIN:
                mode = Group.START_MODE_CYCLE_ROUND_ROBIN;
                // The cycle class is the zero based position in the round-robin cycle, the model
                // starts with 1
                cycleClass = Math.max (0, zone.getSequencePosition () - 1);
                break;
            case RANDOM:
                mode = Group.START_MODE_CYCLE_RANDOM;
                break;
            default:
                return createChunk (KontaktPresetChunkID.STAR_CRIT_LIST, new byte [1]);
        }

        // 1 entry: unknown byte, version, mode, next criteria, key range, controller and value,
        // cycle class, unknown, 2 slice indices, unknown byte
        final byte [] data = new byte [1 + Group.START_CRITERIA_SIZE];
        data[0] = 1;
        int pos = 2;
        pos = writeUnsigned16 (data, pos, START_CRITERIA_VERSION);
        pos = writeUnsigned32 (data, pos, mode);
        pos = writeUnsigned32 (data, pos, 0);
        pos = writeUnsigned16 (data, pos, START_CRITERIA_KEY);
        pos = writeUnsigned16 (data, pos, START_CRITERIA_KEY);
        pos = writeUnsigned32 (data, pos, START_CRITERIA_CC);
        pos = writeUnsigned16 (data, pos, START_CRITERIA_CC_VALUE);
        pos = writeUnsigned16 (data, pos, cycleClass);
        pos = writeUnsigned16 (data, pos, 0);
        pos = writeUnsigned32 (data, pos, 0xFFFFFFFFL);
        writeUnsigned32 (data, pos, 0xFFFFFFFFL);
        return createChunk (KontaktPresetChunkID.STAR_CRIT_LIST, data);
    }


    /**
     * Creates the private data of a group which contains the filter of the zone (if any) as an
     * insert effect. An SV filter is used since it supports all filter types and poles of the model
     * and has a simple cutoff mapping.
     *
     * @param template The private data of a group without any insert effects
     * @param zone The zone from which to take the filter
     * @return The private data
     */
    static byte [] createPrivateData (final byte [] template, final ISampleZone zone)
    {
        final Optional<IFilter> filterOpt = zone.getFilter ();
        if (filterOpt.isEmpty () || template.length < INSERT_EFFECTS_SLOTS + 8)
            return template;
        final IFilter filter = filterOpt.get ();

        final byte [] effect = FILTER_EFFECT.clone ();
        effect[FILTER_BYPASS] = 0;
        final int type = getSVFilterType (filter);
        writeUnsigned32 (effect, FILTER_TYPE, type);
        writeUnsigned32 (effect, FILTER_TYPE2, type);
        final double note = 12.0 * Math.log (Math.max (1, filter.getCutoff ()) / 440.0) / Math.log (2) + 69.0;
        writeFloat (effect, FILTER_CUTOFF, Math.clamp ((note - SV_NOTE_OFFSET) / SV_NOTE_RANGE, 0.0, 1.0));
        writeFloat (effect, FILTER_RESONANCE, Math.clamp (filter.getResonance (), 0.0, 1.0));

        // Replace the first (empty) slot with the filter
        final int slotOffset = INSERT_EFFECTS_SLOTS + FILTER_SLOT;
        final byte [] data = new byte [template.length - 1 + 7 + effect.length];
        System.arraycopy (template, 0, data, 0, slotOffset);
        data[slotOffset] = 1;
        writeUnsigned16 (data, slotOffset + 1, PAR_FX);
        writeUnsigned32 (data, slotOffset + 3, effect.length);
        System.arraycopy (effect, 0, data, slotOffset + 7, effect.length);
        System.arraycopy (template, slotOffset + 1, data, slotOffset + 7 + effect.length, template.length - slotOffset - 1);
        return data;
    }


    /**
     * Get the ID of the SV filter which matches the filter type and number of poles the best.
     *
     * @param filter The filter
     * @return The ID of the SV filter type
     */
    private static int getSVFilterType (final IFilter filter)
    {
        final int poles = filter.getPoles ();
        return switch (filter.getType ())
        {
            // SV HP1, HP2, HP4, HP6
            case HIGH_PASS -> poles <= 1 ? 51 : poles <= 3 ? 54 : poles <= 5 ? 57 : 61;
            // SV BP2, BP4, BP6
            case BAND_PASS -> poles <= 2 ? 53 : poles <= 5 ? 56 : 60;
            // SV Notch 4, Notch 6
            case BAND_REJECTION -> poles <= 5 ? 58 : 62;
            // SV LP1, LP2, LP4, LP6
            default -> poles <= 1 ? 50 : poles <= 3 ? 52 : poles <= 5 ? 55 : 59;
        };
    }


    /**
     * Adds a LFO modulator if the depth is not 0.
     *
     * @param modulators Where to add the modulator
     * @param target The name of the modulated parameter
     * @param targetDescription The target part of the modulator description
     * @param lfoModulator The LFO modulator of the model
     * @param converter Converts the depth to the intensity
     */
    private static void addLfo (final List<KontaktPresetChunk> modulators, final String target, final String targetDescription, final ILfoModulator lfoModulator, final LfoIntensityConverter converter)
    {
        if (!isLfoWritten (lfoModulator))
            return;
        final ILfo lfo = lfoModulator.getSource ();
        final double intensity = getLfoSign (lfo) * converter.toIntensity (lfoModulator.getDepth ());

        final String sourceName;
        final int waveform;
        switch (lfo.getWaveform ())
        {
            case SQUARE:
                sourceName = "LFO_RECTANGLE";
                waveform = 1;
                break;
            case TRIANGLE:
                sourceName = "LFO_TRIANGLE";
                waveform = 2;
                break;
            // The sawtooth of Kontakt starts at the maximum and decreases
            case SAWTOOTH_DOWN, SAWTOOTH_UP:
                sourceName = "LFO_SAWTOOTH";
                waveform = 3;
                break;
            case RANDOM:
                sourceName = "LFO_RANDOM";
                waveform = 4;
                break;
            default:
                sourceName = "LFO_SINE";
                waveform = 0;
                break;
        }

        final byte [] block2 = LFO_BLOCK.clone ();
        writeUnsigned32 (block2, LFO_WAVEFORM, waveform);
        writeFloat (block2, LFO_DELAY, Math.max (0, lfo.getDelay ()) * 1000.0);
        writeFloat (block2, LFO_FREQUENCY, lfo.getRate ());
        writeFloat (block2, LFO_START_PHASE, Math.clamp (lfo.getStartPhase (), 0.0, 1.0));

        final byte [] data = createInternalModulator (target, intensity, sourceName + "_" + targetDescription, lfo.isKeySync (), UNKNOWN_LFO, sourceName, SOURCE_INDEX_LFO, block2);
        modulators.add (createChunk (KontaktPresetChunkID.PAR_INTERNAL_MOD, data));
    }


    /**
     * Creates the data of an internal modulator with one modulated parameter.
     *
     * @param target The name of the modulated parameter
     * @param intensity The intensity of the modulation, negative for inverted
     * @param description The description of the modulation
     * @param retrigger True to retrigger the source on each note
     * @param unknown An unknown value found in the files
     * @param sourceName The name of the modulation source
     * @param sourceIndex The index of the type of the modulation source
     * @param block2 The data of the modulation source
     * @return The data
     */
    private static byte [] createInternalModulator (final String target, final double intensity, final String description, final boolean retrigger, final int unknown, final String sourceName, final int sourceIndex, final byte [] block2)
    {
        try
        {
            final ByteArrayOutputStream block1 = new ByteArrayOutputStream ();
            writeModulatedParameter (block1, target, description, -1);
            // Mod-section open, bypass, retrigger and 5 unknown bytes
            block1.write (1);
            block1.write (0);
            block1.write (retrigger ? 1 : 0);
            block1.write (0);
            StreamUtils.writeUnsigned32 (block1, unknown, false);
            writeAscii (block1, sourceName);
            StreamUtils.writeUnsigned32 (block1, sourceIndex, false);

            final byte [] block1Data = block1.toByteArray ();
            final ByteArrayOutputStream out = new ByteArrayOutputStream ();
            out.write (1);
            StreamUtils.writeUnsigned16 (out, 0x81, false);
            StreamUtils.writeUnsigned32 (out, block1Data.length, false);
            out.write (block1Data);
            StreamUtils.writeUnsigned32 (out, 0, false);
            StreamUtils.writeUnsigned32 (out, block2.length, false);
            out.write (block2);

            final byte [] data = out.toByteArray ();
            setIntensity (data, BLOCK1_OFFSET, intensity);
            return data;
        }
        catch (final IOException ex)
        {
            // Cannot happen with a byte array stream
            throw new IllegalStateException (ex);
        }
    }


    /**
     * Adds an external modulator which controls the intensity of an oscillator with the
     * modulation wheel, if the oscillator is written and has a modulation wheel depth.
     *
     * @param modulators Where to add the modulator
     * @param template The template for an external modulator
     * @param lfoModulator The oscillator
     * @param converter Converts the depth of the model to the intensity of Kontakt
     * @param slot The slot index of the internal modulator of the oscillator, if it is written
     * @return The slot index of the next internal modulator
     */
    private static int addModWheelModulator (final List<KontaktPresetChunk> modulators, final KontaktPresetChunk template, final ILfoModulator lfoModulator, final LfoIntensityConverter converter, final int slot)
    {
        if (!isLfoWritten (lfoModulator))
            return slot;
        final double intensity = getLfoSign (lfoModulator.getSource ()) * converter.toModWheelIntensity (lfoModulator.getDepth (), lfoModulator.getDepthModWheelModulator ().getDepth ());
        if (intensity != 0)
            modulators.add (createExternalModulator (template, TARGET_INTENSITY, slot, intensity, "CC_INTENSITY", SOURCE_MIDI_CC, MOD_WHEEL_CC));
        return slot + 1;
    }


    /**
     * Check if an internal modulator is written for the oscillator.
     *
     * @param lfoModulator The oscillator
     * @return True if it is written
     */
    private static boolean isLfoWritten (final ILfoModulator lfoModulator)
    {
        return lfoModulator.isActive () && lfoModulator.getSource ().getRate () > 0;
    }


    /**
     * The sawtooth of Kontakt starts at the maximum and decreases, an increasing sawtooth is
     * created by inverting the intensity.
     *
     * @param lfo The oscillator
     * @return -1 for an increasing sawtooth, otherwise 1
     */
    private static double getLfoSign (final ILfo lfo)
    {
        return lfo.getWaveform () == LfoWaveform.SAWTOOTH_UP ? -1 : 1;
    }


    /**
     * Get the intensity of the pitch envelope, relative to 1 octave (the depth of the model is
     * relative to MAX_ENVELOPE_DEPTH cents).
     *
     * @param zone The zone
     * @return The intensity, 0 if no pitch envelope is written
     */
    private static double getPitchEnvelopeIntensity (final ISampleZone zone)
    {
        return Math.clamp (zone.getPitchEnvelopeModulator ().getDepth () * IEnvelope.MAX_ENVELOPE_DEPTH / PITCH_INTENSITY_CENTS, -1.0, 1.0);
    }


    /**
     * Get the intensity of the filter cutoff envelope.
     *
     * @param zone The zone
     * @return The intensity, 0 if there is no filter or no cutoff envelope is written
     */
    private static double getCutoffEnvelopeIntensity (final ISampleZone zone)
    {
        final Optional<IFilter> filterOpt = zone.getFilter ();
        return filterOpt.isEmpty () ? 0 : Math.clamp (filterOpt.get ().getCutoffEnvelopeModulator ().getDepth (), -1.0, 1.0);
    }


    /**
     * Creates an external modulator from the velocity to volume template.
     *
     * @param template The template
     * @param target The name of the modulated parameter
     * @param intensity The intensity, negative for inverted
     * @param sourceName The name of the modulation source
     * @param sourceType The type of the modulation source
     * @return The chunk
     */
    private static KontaktPresetChunk createExternalModulator (final KontaktPresetChunk template, final String target, final double intensity, final String sourceName, final int sourceType)
    {
        return createExternalModulator (template, target, -1, intensity, sourceName, sourceType, -1);
    }


    /**
     * Creates an external modulator from a template.
     *
     * @param template The template of an external modulator
     * @param target The name of the modulated parameter
     * @param targetIndex The slot index of the internal modulator if the target is its intensity,
     *            otherwise -1
     * @param intensity The modulation intensity
     * @param sourceName The name of the source
     * @param sourceType The type of the source
     * @param ccNumber The number of the MIDI CC if the source is a MIDI CC, otherwise -1
     * @return The modulator
     */
    private static KontaktPresetChunk createExternalModulator (final KontaktPresetChunk template, final String target, final int targetIndex, final double intensity, final String sourceName, final int sourceType, final int ccNumber)
    {
        final byte [] templateData = template.getPublicData ();
        final int blockSize = (int) readUnsigned32 (templateData, 3);
        final int blockEnd = EXTERNAL_BLOCK_OFFSET + blockSize;

        // Find the end of the source name in the template: number of parameters, name, intensity,
        // 0xFFFF, flags, lag, description, 2 bytes, source name
        int pos = EXTERNAL_BLOCK_OFFSET + 4;
        pos += 4 + (int) readUnsigned32 (templateData, pos) + 9;
        pos += 4 + (int) readUnsigned32 (templateData, pos) + 2;
        pos += 4 + (int) readUnsigned32 (templateData, pos);

        try
        {
            final ByteArrayOutputStream block = new ByteArrayOutputStream ();
            writeModulatedParameter (block, target, "", targetIndex);
            writeAscii (block, sourceName);
            // 1 (32 bit) followed by the source type and the CC number (8 bit) if it is a MIDI CC
            StreamUtils.writeUnsigned32 (block, 1, false);
            StreamUtils.writeUnsigned32 (block, sourceType, false);
            int remaining = pos + 8;
            if (ccNumber >= 0)
            {
                // Kontakt writes 4 zero bytes instead of 0xFFFF and the 2 flags after a MIDI CC
                block.write (ccNumber);
                block.write (new byte [4]);
                remaining += 4;
            }
            // The remaining bytes of the template
            block.write (templateData, remaining, blockEnd - remaining);

            final byte [] blockData = block.toByteArray ();
            final ByteArrayOutputStream out = new ByteArrayOutputStream ();
            out.write (templateData, 0, 3);
            StreamUtils.writeUnsigned32 (out, blockData.length, false);
            out.write (blockData);
            out.write (templateData, blockEnd, templateData.length - blockEnd);

            final byte [] data = out.toByteArray ();
            setIntensity (data, EXTERNAL_BLOCK_OFFSET, intensity);
            return createChunk (template.getId (), data);
        }
        catch (final IOException ex)
        {
            // Cannot happen with a byte array stream
            throw new IllegalStateException (ex);
        }
    }


    /**
     * Writes one modulated parameter: number of parameters (1), name, intensity (set later),
     * 0xFFFF, flags (add-mode for the intensity of an internal modulator), lag, description, the insert effect slot of the filter (only for the filter
     * cutoff) or the slot of the internal modulator (only for its intensity) and 2 zero bytes (no
     * modulation shaper).
     *
     * @param out Where to write to
     * @param target The name of the modulated parameter
     * @param description The description
     * @param targetIndex The slot index of the internal modulator whose intensity is modulated,
     *            -1 if not used
     * @throws IOException Could not write
     */
    private static void writeModulatedParameter (final ByteArrayOutputStream out, final String target, final String description, final int targetIndex) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, 1, false);
        writeAscii (out, target);
        StreamUtils.writeFloatLE (out, 0);
        StreamUtils.writeUnsigned16 (out, 0xFFFF, false);
        // The intensity of an internal modulator is added to (not scaled by) the modulation
        out.write (TARGET_INTENSITY.equals (target) ? FLAGS_DEFAULT | FLAG_ADD : FLAGS_DEFAULT);
        StreamUtils.writeUnsigned16 (out, 0, false);
        writeAscii (out, description);
        // Parameters of other objects are followed by the index of that object: the insert effect
        // slot of the filter and the slot of the internal modulator whose intensity is modulated
        if (TARGET_CUTOFF.equals (target))
            out.write (FILTER_SLOT);
        else if (TARGET_INTENSITY.equals (target))
            out.write (targetIndex);
        out.write (0);
        out.write (0);
    }


    private static void writeAscii (final ByteArrayOutputStream out, final String text) throws IOException
    {
        final byte [] bytes = text.getBytes (StandardCharsets.US_ASCII);
        StreamUtils.writeUnsigned32 (out, bytes.length, false);
        out.write (bytes);
    }


    /**
     * Get the second block (the envelope) of the pitch envelope template.
     *
     * @return The block
     */
    private static byte [] getEnvelopeBlock ()
    {
        final int block1Size = (int) readUnsigned32 (PITCH_ENVELOPE, 3);
        final int block2 = BLOCK1_OFFSET + block1Size + 8;
        final byte [] data = new byte [PITCH_ENVELOPE.length - block2];
        System.arraycopy (PITCH_ENVELOPE, block2, data, 0, data.length);
        return data;
    }


    /**
     * Sets the intensity of the first modulated parameter. It is stored after the number of
     * parameters (32 bit) and the name of the parameter (32 bit length + ASCII). The intensity is
     * always positive, a negative intensity is stored with the invert flag which is part of the
     * flags byte after the intensity and 0xFFFF.
     *
     * @param data The modulator data
     * @param blockOffset The offset of the block which contains the parameters
     * @param intensity The intensity
     */
    private static void setIntensity (final byte [] data, final int blockOffset, final double intensity)
    {
        final int nameLength = (int) readUnsigned32 (data, blockOffset + 4);
        final int intensityOffset = blockOffset + 8 + nameLength;
        writeFloat (data, intensityOffset, Math.abs (intensity));
        final int flagsOffset = intensityOffset + 6;
        if (intensity < 0)
            data[flagsOffset] |= FLAG_INVERT;
        else
            data[flagsOffset] &= (byte) ~FLAG_INVERT;
    }


    /**
     * Sets the values of the AHDSR envelope in the second block of an internal modulator.
     *
     * @param data The modulator data
     * @param envelope The envelope
     * @param curve The curve of the attack in the range of [-1..1]
     * @param attack The attack time in seconds
     * @param hold The hold time in seconds
     * @param decay The decay time in seconds
     * @param sustain The sustain level in the range of [0..1]
     * @param release The release time in seconds
     */
    private static void setEnvelope (final byte [] data, final IEnvelope envelope, final double curve, final double attack, final double hold, final double decay, final double sustain, final double release)
    {
        final int block1Size = (int) readUnsigned32 (data, 3);
        // Skip the separator and the size of the second block
        final int block2 = BLOCK1_OFFSET + block1Size + 8;
        writeFloat (data, block2 + ENVELOPE_CURVE, Math.clamp (curve, -1.0, 1.0));
        writeFloat (data, block2 + ENVELOPE_ATTACK, attack * 1000.0);
        writeFloat (data, block2 + ENVELOPE_DECAY, decay * 1000.0);
        writeFloat (data, block2 + ENVELOPE_HOLD, hold * 1000.0);
        writeFloat (data, block2 + ENVELOPE_RELEASE, release * 1000.0);
        writeFloat (data, block2 + ENVELOPE_SUSTAIN, Math.clamp (sustain, 0.0, 1.0));
        data[block2 + ENVELOPE_AHD_ONLY] = 0;
    }


    /**
     * Find the offset of the ID of a modulator. Each modulator of a group has a unique ID.
     * Internal modulators store it in their first block directly before the name of the
     * modulation source (which is followed by the index of the source). External modulators store
     * it after the type of the source, the optional MIDI CC number (8 bit) and 4 unknown bytes.
     *
     * @param data The data of the modulator
     * @param isInternal True for an internal modulator, false for an external one
     * @return The offset of the ID (32 bit) or -1 if it could not be found
     */
    static int findModulatorIdOffset (final byte [] data, final boolean isInternal)
    {
        if (data.length < BLOCK1_OFFSET + 4)
            return -1;
        final long blockSize = readUnsigned32 (data, 3);
        final int blockEnd = (int) Math.min (data.length, BLOCK1_OFFSET + blockSize);

        if (isInternal)
        {
            // The block ends with the source name and the source index (32 bit)
            final int nameEnd = blockEnd - 4;
            for (int length = 1; length <= MAX_SOURCE_NAME_LENGTH; length++)
            {
                final int lengthOffset = nameEnd - length - 4;
                if (lengthOffset - 4 < BLOCK1_OFFSET)
                    break;
                if (readUnsigned32 (data, lengthOffset) == length && isAscii (data, lengthOffset + 4, length))
                    return lengthOffset - 4;
            }
            return -1;
        }

        // Number of parameters, name of the first parameter, intensity, 0xFFFF, flags, lag and
        // description followed by 2-4 bytes (object index, unknown, modulation shaper) and the
        // name of the source, 1 (32 bit) and the type of the source (32 bit)
        int pos = BLOCK1_OFFSET + 4;
        if (pos + 4 > blockEnd)
            return -1;
        pos += 4 + (int) readUnsigned32 (data, pos) + 9;
        if (pos + 4 > blockEnd)
            return -1;
        pos += 4 + (int) readUnsigned32 (data, pos);
        for (int skip = 0; skip <= MAX_PARAMETER_TRAILER; skip++)
        {
            final int nameOffset = pos + skip;
            if (nameOffset + 4 > blockEnd)
                break;
            final long length = readUnsigned32 (data, nameOffset);
            final int end = nameOffset + 4 + (int) length;
            if (length < 1 || length > MAX_SOURCE_NAME_LENGTH || end + 8 > blockEnd || !isAscii (data, nameOffset + 4, (int) length) || readUnsigned32 (data, end) != 1)
                continue;
            final long sourceType = readUnsigned32 (data, end + 4);
            final int idOffset = end + 8 + (sourceType == SOURCE_MIDI_CC ? 1 : 0) + 4;
            return idOffset + 4 <= blockEnd ? idOffset : -1;
        }
        return -1;
    }


    private static boolean isAscii (final byte [] data, final int offset, final int length)
    {
        for (int i = 0; i < length; i++)
        {
            final int c = data[offset + i] & 0xFF;
            if (c < 0x20 || c >= 0x7F)
                return false;
        }
        return true;
    }


    /**
     * Assigns unique IDs to modulators.
     *
     * @param modulators The modulators
     * @param isInternal True for internal modulators
     * @param firstId The ID of the first modulator
     */
    private static void setModulatorIds (final List<KontaktPresetChunk> modulators, final boolean isInternal, final int firstId)
    {
        for (int i = 0; i < modulators.size (); i++)
        {
            final KontaktPresetChunk modulator = modulators.get (i);
            final byte [] data = modulator.getPublicData ();
            final int offset = findModulatorIdOffset (data, isInternal);
            if (offset >= 0)
                writeUnsigned32 (data, offset, firstId + (long) i);
        }
    }


    private static KontaktPresetChunk createParameterArray (final int id, final List<KontaktPresetChunk> children)
    {
        final KontaktPresetChunk chunk = new KontaktPresetChunk ();
        chunk.setId (id);
        chunk.setVersion (PARAMETER_ARRAY_VERSION);
        chunk.setChildren (children);
        return chunk;
    }


    private static KontaktPresetChunk createChunk (final int id, final byte [] data)
    {
        final KontaktPresetChunk chunk = new KontaktPresetChunk ();
        chunk.setId (id);
        chunk.setPublicData (data);
        return chunk;
    }


    private static double limitToDefault (final double value, final double defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    private static void writeFloat (final byte [] data, final int offset, final double value)
    {
        writeUnsigned32 (data, offset, Float.floatToIntBits ((float) value) & 0xFFFFFFFFL);
    }


    private static int writeUnsigned16 (final byte [] data, final int offset, final int value)
    {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) (value >> 8 & 0xFF);
        return offset + 2;
    }


    private static int writeUnsigned32 (final byte [] data, final int offset, final long value)
    {
        writeUnsigned16 (data, offset, (int) (value & 0xFFFF));
        return writeUnsigned16 (data, offset + 2, (int) (value >> 16 & 0xFFFF));
    }


    private static long readUnsigned32 (final byte [] data, final int offset)
    {
        return (data[offset] & 0xFFL) | (data[offset + 1] & 0xFFL) << 8 | (data[offset + 2] & 0xFFL) << 16 | (data[offset + 3] & 0xFFL) << 24;
    }
}
