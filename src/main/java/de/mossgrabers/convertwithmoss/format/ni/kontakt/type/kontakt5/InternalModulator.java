// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfoModulator;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A Kontakt 5+ internal modulator.
 *
 * @author Jürgen Moßgraber
 */
public class InternalModulator
{
    private static final String            PARAMETER_NAME_NONE                = "<none>";
    private static final String            PARAMETER_NAME_VOLUME              = "volume";
    private static final String            PARAMETER_NAME_CUTOFF              = "filterCutoff";
    private static final String            PARAMETER_NAME_PITCH               = "pitch";
    private static final String            MODULATOR_DESCRIPTION_AHDSR_VOLUME = "ENV_AHDSR_VOLUME";
    private static final String            MODULATOR_SOURCE_NAME_AHDSR        = "ENV_AHDSR";
    private static final String            MODULATOR_SOURCE_NAME_AHD          = "ENV_AHD";
    private static final String            MODULATOR_SOURCE_NAME_DBD          = "ENV_DBD";

    // The index of the modulation source type
    private static final int               SOURCE_INDEX_ENVELOPE              = 0;
    private static final int               SOURCE_INDEX_LFO                   = 1;
    private static final int               SOURCE_INDEX_ENVELOPE2             = 2;

    // Flag of a modulated parameter which inverts the modulation
    private static final int               FLAG_INVERT                        = 0x02;
    // Flag of a modulated parameter which has no description
    private static final int               FLAG_NO_DESCRIPTION                = 0x08;

    // There are 0-2 unknown bytes after the modulator description (e.g. the filter cutoff
    // parameter has additional bytes) and 0-1 unknown bytes after the last parameter
    private static final int               MAX_UNKNOWN_BYTES                  = 2;

    // The LFO data contains the waveform (32 bit) at offset 9, followed by the delay (in ms), the
    // frequency (in Hertz), the pulse width and the start phase (all floats)
    private static final int               LFO_WAVEFORM_OFFSET                = 9;
    private static final int               LFO_DELAY_OFFSET                   = 13;
    private static final int               LFO_FREQUENCY_OFFSET               = 17;
    private static final int               LFO_START_PHASE_OFFSET             = 25;
    private static final int               LFO_WAVEFORM_SINE                  = 0;
    private static final int               LFO_WAVEFORM_RECTANGLE             = 1;
    private static final int               LFO_WAVEFORM_TRIANGLE              = 2;
    private static final int               LFO_WAVEFORM_SAWTOOTH              = 3;
    private static final int               LFO_WAVEFORM_RANDOM                = 4;

    // The offsets of the decay 1, break and decay 2 values of a decay-break-decay envelope
    private static final int               DBD_DECAY1_OFFSET                  = 34;
    private static final int               DBD_BREAK_OFFSET                   = 38;
    private static final int               DBD_DECAY2_OFFSET                  = 42;

    private int                            version                            = 0x81;
    private final List<ModulatedParameter> modulatedParameters                = new ArrayList<> ();

    @SuppressWarnings("unused")
    private boolean                        isModSectionOpen                   = false;
    private boolean                        isBypassed                         = false;
    private boolean                        isRetrigger                        = true;
    private String                         modulatorSourceName                = MODULATOR_SOURCE_NAME_AHDSR;
    private int                            modulationSourceIndex;
    private float                          curve                              = -0.332f;
    private float                          attack                             = 0;
    private float                          hold                               = 0;
    private float                          decay                              = 500;
    private float                          release                            = 300;
    private float                          sustain                            = 1;
    private boolean                        ahdOnly                            = false;
    private boolean                        isDecayBreakDecay                  = false;
    private LfoWaveform                    lfoWaveform                        = null;
    private float                          lfoDelay                           = 0;
    private float                          lfoFrequency                       = 0;
    private float                          lfoStartPhase                      = 0;


    /** All attributes of a modulated parameter. */
    private static class ModulatedParameter
    {
        private String parameterName        = PARAMETER_NAME_VOLUME;
        private float  intensity            = 1.0f;
        private int    flags                = 0x10;
        @SuppressWarnings("unused")
        private int    lag                  = 0;
        private String modulatorDescription = MODULATOR_DESCRIPTION_AHDSR_VOLUME;


        /**
         * Get the intensity of the modulation.
         *
         * @return The intensity, negative if the modulation is inverted
         */
        float getIntensity ()
        {
            return (this.flags & FLAG_INVERT) > 0 ? -this.intensity : this.intensity;
        }
    }


    /**
     * Constructor.
     */
    public InternalModulator ()
    {
        // Intentionally empty
    }


    /**
     * Get the volume envelope if present.
     *
     * @return The volume envelope
     */
    public Optional<IEnvelopeModulator> getVolumeEnvelope ()
    {
        return this.createEnvelopeModulator (PARAMETER_NAME_VOLUME);
    }


    /**
     * Get the filter cutoff envelope if present.
     *
     * @return The filter cutoff envelope
     */
    public Optional<IEnvelopeModulator> getFilterCutoffEnvelope ()
    {
        return this.createEnvelopeModulator (PARAMETER_NAME_CUTOFF);
    }


    /**
     * Get the pitch envelope if present.
     *
     * @return The pitch envelope
     */
    public Optional<IEnvelopeModulator> getPitchEnvelope ()
    {
        return this.createEnvelopeModulator (PARAMETER_NAME_PITCH);
    }


    /**
     * Get the volume LFO if present.
     *
     * @return The volume LFO, the depth is the intensity of Kontakt
     */
    public Optional<ILfoModulator> getVolumeLfo ()
    {
        return this.createLfoModulator (PARAMETER_NAME_VOLUME);
    }


    /**
     * Get the filter cutoff LFO if present.
     *
     * @return The filter cutoff LFO, the depth is the intensity of Kontakt
     */
    public Optional<ILfoModulator> getFilterCutoffLfo ()
    {
        return this.createLfoModulator (PARAMETER_NAME_CUTOFF);
    }


    /**
     * Get the pitch LFO if present.
     *
     * @return The pitch LFO, the depth is the intensity of Kontakt (1.0 = 1 octave)
     */
    public Optional<ILfoModulator> getPitchLfo ()
    {
        return this.createLfoModulator (PARAMETER_NAME_PITCH);
    }


    private Optional<IEnvelopeModulator> createEnvelopeModulator (final String parameterName)
    {
        if (this.isBypassed || !this.isEnvelopeModulator ())
            return Optional.empty ();
        final Optional<ModulatedParameter> envelopeParameter = this.getModulatedParameter (parameterName);
        if (envelopeParameter.isEmpty ())
            return Optional.empty ();

        final IEnvelopeModulator envelopeModulator = new DefaultEnvelopeModulator (envelopeParameter.get ().getIntensity ());
        final IEnvelope envelope = envelopeModulator.getSource ();
        if (this.isDecayBreakDecay)
        {
            // The decay-break-decay envelope starts at the maximum level, falls to the break level
            // during the first decay and from there to zero during the second decay, independent
            // of the note being held. Since the model does not support this shape it is
            // approximated by an envelope without attack and sustain which decays during the sum
            // of both decay times
            envelope.setAttackTime (0);
            envelope.setHoldTime (0);
            envelope.setDecayTime (this.decay / 1000.0);
            envelope.setSustainLevel (0);
            envelope.setReleaseTime (0);
            return Optional.of (envelopeModulator);
        }

        // The curve of Kontakt has the opposite direction of the slope
        envelope.setAttackSlope (-this.curve);
        envelope.setAttackTime (this.attack / 1000.0);
        envelope.setHoldTime (this.hold / 1000.0);
        envelope.setDecayTime (this.decay / 1000.0);
        envelope.setSustainLevel (this.ahdOnly ? 0 : this.sustain);
        envelope.setReleaseTime ((this.ahdOnly ? this.decay : this.release) / 1000.0);
        return Optional.of (envelopeModulator);
    }


    private Optional<ILfoModulator> createLfoModulator (final String parameterName)
    {
        if (this.isBypassed || this.modulationSourceIndex != SOURCE_INDEX_LFO || this.lfoWaveform == null || this.lfoFrequency <= 0)
            return Optional.empty ();
        final Optional<ModulatedParameter> lfoParameter = this.getModulatedParameter (parameterName);
        if (lfoParameter.isEmpty ())
            return Optional.empty ();

        final ILfoModulator lfoModulator = new DefaultLfoModulator (lfoParameter.get ().getIntensity ());
        final ILfo lfo = lfoModulator.getSource ();
        lfo.setWaveform (this.lfoWaveform);
        lfo.setRate (this.lfoFrequency);
        lfo.setDelay (Math.max (0, this.lfoDelay) / 1000.0);
        lfo.setStartPhase (Math.clamp (this.lfoStartPhase, 0, 1));
        lfo.setKeySync (this.isRetrigger);
        return Optional.of (lfoModulator);
    }


    /**
     * Read the internal modulator data.
     *
     * @param chunk The chunk from which to read the bank data
     * @throws IOException Could not read the bank
     */
    public void read (final KontaktPresetChunk chunk) throws IOException
    {
        final int id = chunk.getId ();
        if (id != KontaktPresetChunkID.PAR_INTERNAL_MOD && id != KontaktPresetChunkID.PAR_MOD_BASE)
            throw new IOException (Functions.getMessage ("IDS_NKI_NOT_INTERNAL_MODULATION"));

        final ByteArrayInputStream in = new ByteArrayInputStream (chunk.getPublicData ());

        if (id == KontaktPresetChunkID.PAR_MOD_BASE)
            StreamUtils.readUnsigned32 (in, false);

        int mainVersion = in.read ();
        if (mainVersion == 0)
            mainVersion = in.read ();
        // Always 01
        if (mainVersion != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_HEADER"));

        // Version: 0x80, 0x81
        this.version = StreamUtils.readUnsigned16 (in, false);
        if (this.version < 0x80 || this.version > 0x81)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_VERSION", Integer.toString (this.version)));

        // First block with parameter/modulation info
        final int sizeBlock1 = (int) StreamUtils.readUnsigned32 (in, false);
        this.readBlock1 (in.readNBytes (sizeBlock1));

        // Separator - always 0
        if (StreamUtils.readUnsigned32 (in, false) != 0)
            throw new IOException (Functions.getMessage ("IDS_NKI_SEPARATOR_NOT_NULL_IN_INTERNAL_MOD"));

        // Second block with the modulation source
        final int sizeBlock2 = (int) StreamUtils.readUnsigned32 (in, false);
        this.readBlock2 (in.readNBytes (sizeBlock2));
    }


    /**
     * Reads the first block which contains the modulated parameters followed by the settings and
     * the name of the modulation source. There are 0-2 unknown bytes after the description of a
     * modulated parameter and 0-1 unknown byte after the last parameter. Since it is unclear when
     * they are present, all variations are tried until the whole block could be parsed.
     *
     * @param data The data of the block
     * @throws IOException Could not parse the block
     */
    private void readBlock1 (final byte [] data) throws IOException
    {
        if (data.length < 4)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_BLOCK1_VALUE"));
        final int count = (int) readUnsigned32 (data, 0);
        final List<ModulatedParameter> parameters = new ArrayList<> ();
        if (!this.readModulatedParameters (data, 4, count, parameters))
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_BLOCK1_VALUE"));
        this.modulatedParameters.addAll (parameters);
    }


    /**
     * Reads the remaining modulated parameters and the block trailer.
     *
     * @param data The data of the block
     * @param offset The offset where to start reading
     * @param remaining The number of parameters which still need to be read
     * @param parameters Where to add the read parameters
     * @return True if the parameters and the trailer could be read and the block was fully consumed
     */
    private boolean readModulatedParameters (final byte [] data, final int offset, final int remaining, final List<ModulatedParameter> parameters)
    {
        if (remaining == 0)
            return this.readBlock1Trailer (data, offset) || this.readBlock1Trailer (data, offset + 1);

        final ModulatedParameter modulatedParameter = new ModulatedParameter ();
        modulatedParameter.parameterName = readAscii (data, offset);
        if (modulatedParameter.parameterName == null)
            return false;
        int pos = offset + 4 + modulatedParameter.parameterName.length ();
        if (pos + 9 > data.length)
            return false;
        modulatedParameter.intensity = Float.intBitsToFloat ((int) readUnsigned32 (data, pos));
        if (readUnsigned16 (data, pos + 4) != 0xFFFF)
            return false;
        modulatedParameter.flags = data[pos + 6] & 0xFF;
        modulatedParameter.lag = (short) readUnsigned16 (data, pos + 7);
        pos += 9;

        parameters.add (modulatedParameter);
        if ((modulatedParameter.flags & FLAG_NO_DESCRIPTION) > 0)
        {
            // Unused
            if (this.readModulatedParameters (data, pos + 5, remaining - 1, parameters))
                return true;
        }
        else
        {
            modulatedParameter.modulatorDescription = readAscii (data, pos);
            if (modulatedParameter.modulatorDescription != null)
            {
                pos += 4 + modulatedParameter.modulatorDescription.length ();
                for (int unknown = 0; unknown <= MAX_UNKNOWN_BYTES; unknown++)
                {
                    final int next = ExternalModulator.skipModulationShaper (data, pos + unknown);
                    if (next >= 0 && this.readModulatedParameters (data, next, remaining - 1, parameters))
                        return true;
                }
            }
        }
        parameters.remove (parameters.size () - 1);
        return false;
    }


    /**
     * Reads the end of the first block: the mod-section open, bypass and retrigger flags, 5 unknown
     * bytes, the name of the modulation source and its type index.
     *
     * @param data The data of the block
     * @param offset The offset where the trailer starts
     * @return True if the trailer could be read and ends exactly at the end of the block
     */
    private boolean readBlock1Trailer (final byte [] data, final int offset)
    {
        if (offset + 8 > data.length)
            return false;
        for (int i = 0; i < 3; i++)
            if ((data[offset + i] & 0xFF) > 1)
                return false;
        final String sourceName = readAscii (data, offset + 8);
        if (sourceName == null)
            return false;
        final int pos = offset + 12 + sourceName.length ();
        if (pos + 4 != data.length)
            return false;

        this.isModSectionOpen = data[offset] > 0;
        this.isBypassed = data[offset + 1] > 0;
        this.isRetrigger = data[offset + 2] > 0;
        this.modulatorSourceName = sourceName;
        this.modulationSourceIndex = (int) readUnsigned32 (data, pos);
        return true;
    }


    /**
     * Reads the second block which contains the settings of the modulation source.
     *
     * @param data The data of the block
     */
    private void readBlock2 (final byte [] data)
    {
        if (this.getModulatedParameter (PARAMETER_NAME_VOLUME, PARAMETER_NAME_CUTOFF, PARAMETER_NAME_PITCH).isEmpty ())
            return;

        if (this.modulationSourceIndex == SOURCE_INDEX_LFO)
        {
            this.readLfo (data);
            return;
        }

        if (!this.isEnvelopeModulator ())
            return;

        if (MODULATOR_SOURCE_NAME_DBD.equals (this.modulatorSourceName))
        {
            if (data.length < DBD_DECAY2_OFFSET + 4)
                return;
            final float decay1 = readFloat (data, DBD_DECAY1_OFFSET);
            final float breakLevel = readFloat (data, DBD_BREAK_OFFSET);
            final float decay2 = readFloat (data, DBD_DECAY2_OFFSET);
            this.decay = breakLevel == 0 ? decay1 : decay1 + decay2;
            this.isDecayBreakDecay = true;
            return;
        }

        if (MODULATOR_SOURCE_NAME_AHDSR.equals (this.modulatorSourceName) || MODULATOR_SOURCE_NAME_AHD.equals (this.modulatorSourceName) || PARAMETER_NAME_NONE.equals (this.modulatorSourceName) || "".equals (this.modulatorSourceName))
        {
            // Too short but not understood what it contains
            if (data.length < 59)
                return;

            // The first 34 bytes are not used, followed by the curve, attack, decay, hold, release
            // and sustain (all floats) and the AHD-only flag; 52 or 16 more bytes are not used
            this.curve = readFloat (data, 34);
            this.attack = readFloat (data, 38);
            this.decay = readFloat (data, 42);
            this.hold = readFloat (data, 46);
            this.release = readFloat (data, 50);
            this.sustain = readFloat (data, 54);
            this.ahdOnly = data[58] > 0 || MODULATOR_SOURCE_NAME_AHD.equals (this.modulatorSourceName);
        }
    }


    /**
     * Reads the settings of a low frequency oscillator.
     *
     * @param data The data of the second block
     */
    private void readLfo (final byte [] data)
    {
        if (LFO_START_PHASE_OFFSET + 4 > data.length)
            return;

        this.lfoWaveform = switch ((int) readUnsigned32 (data, LFO_WAVEFORM_OFFSET))
        {
            case LFO_WAVEFORM_SINE -> LfoWaveform.SINE;
            case LFO_WAVEFORM_RECTANGLE -> LfoWaveform.SQUARE;
            case LFO_WAVEFORM_TRIANGLE -> LfoWaveform.TRIANGLE;
            // The sawtooth starts at the maximum and decreases
            case LFO_WAVEFORM_SAWTOOTH -> LfoWaveform.SAWTOOTH_DOWN;
            case LFO_WAVEFORM_RANDOM -> LfoWaveform.RANDOM;
            // Multi-waveform is not supported
            default -> null;
        };
        this.lfoDelay = readFloat (data, LFO_DELAY_OFFSET);
        this.lfoFrequency = readFloat (data, LFO_FREQUENCY_OFFSET);
        this.lfoStartPhase = readFloat (data, LFO_START_PHASE_OFFSET);
    }


    private Optional<ModulatedParameter> getModulatedParameter (final String... parameters)
    {
        for (final ModulatedParameter modulatedParameter: this.modulatedParameters)
            for (final String parameter: parameters)
                if (modulatedParameter.parameterName.equals (parameter))
                    return Optional.of (modulatedParameter);
        return Optional.empty ();
    }


    private boolean isEnvelopeModulator ()
    {
        return this.modulationSourceIndex == SOURCE_INDEX_ENVELOPE || this.modulationSourceIndex == SOURCE_INDEX_ENVELOPE2;
    }


    private static String readAscii (final byte [] data, final int offset)
    {
        return ExternalModulator.readAscii (data, offset);
    }


    private static float readFloat (final byte [] data, final int offset)
    {
        return Float.intBitsToFloat ((int) readUnsigned32 (data, offset));
    }


    private static int readUnsigned16 (final byte [] data, final int offset)
    {
        return ExternalModulator.readUnsigned16 (data, offset);
    }


    private static long readUnsigned32 (final byte [] data, final int offset)
    {
        return ExternalModulator.readUnsigned32 (data, offset);
    }
}
