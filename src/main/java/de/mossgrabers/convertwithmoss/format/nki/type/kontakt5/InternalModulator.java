// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A Kontakt 5+ internal modulator.
 *
 * @author Jürgen Moßgraber
 */
public class InternalModulator
{
    private static final String                   PARAMETER_NAME_NONE                = "<none>";
    private static final String                   PARAMETER_NAME_VOLUME              = "volume";
    private static final String                   PARAMETER_NAME_PAN                 = "pan";
    private static final String                   PARAMETER_NAME_CUTOFF              = "filterCutoff";
    private static final String                   PARAMETER_NAME_PITCH               = "pitch";
    private static final String                   MODULATOR_DESCRIPTION_AHDSR_VOLUME = "ENV_AHDSR_VOLUME";
    private static final String                   MODULATOR_SOURCE_NAME_AHDSR        = "ENV_AHDSR";

    private static final Map<String, Set<String>> NEEDS_PADDING                      = new HashMap<> ();
    private static final Map<String, Set<String>> NO_PADDING                         = new HashMap<> ();
    static
    {
        // Needs padding

        final Set<String> eqGain1 = new HashSet<> ();
        eqGain1.add ("LFO_SINE_EQ_GAIN_1");

        final Set<String> filterCutoff = new HashSet<> ();
        filterCutoff.add ("");
        filterCutoff.add (PARAMETER_NAME_NONE);
        filterCutoff.add ("LFO_SINE_CUTOFF");
        filterCutoff.add ("STEP_CUTOFF");
        filterCutoff.add ("ENV_AHDSR_CUTOFF");
        filterCutoff.add ("LFO_SAW_CUTOFF");
        filterCutoff.add ("LFO_RECT_CUTOFF");
        filterCutoff.add ("LFO_MULTI_CUTOFF");
        filterCutoff.add ("ENV_DBD_CUTOFF");
        filterCutoff.add ("ENV FLW_CUTOFF");

        final Set<String> filterQ = new HashSet<> ();
        filterQ.add ("LFO_SINE_RESONANCE");
        filterQ.add ("STEP_RESONANCE");

        final Set<String> frequency = new HashSet<> ();
        frequency.add ("ENV_DBD_FREQUENCY");
        frequency.add ("ENV_AHDSR_FREQUENCY");

        final Set<String> vfType = new HashSet<> ();
        vfType.add ("LFO_MULTI_3X2_TYPE");

        final Set<String> formantTalk = new HashSet<> ();
        formantTalk.add ("LFO_SINE_TALK");

        final Set<String> intensity = new HashSet<> ();
        intensity.add ("LFO_SINE_FREQUENCY");

        final Set<String> loopStart = new HashSet<> ();
        loopStart.add ("ENV_AHDSR_LOOP_START");

        final Set<String> pitch = new HashSet<> ();
        pitch.add ("LFO_RECT_PITCH");

        final Set<String> volume = new HashSet<> ();
        volume.add ("GLIDE_VOLUME");
        volume.add ("LFO_SAW_VOLUME");

        final Set<String> dyxMorph = new HashSet<> ();
        dyxMorph.add ("ENV_AHDSR_MORPH");
        dyxMorph.add ("LFO_MULTI_MORPH");
        dyxMorph.add ("LFO_TRI_MORPH");

        NEEDS_PADDING.put ("eqGain1", eqGain1);
        NEEDS_PADDING.put (PARAMETER_NAME_CUTOFF, filterCutoff);
        NEEDS_PADDING.put ("filterQ", filterQ);
        NEEDS_PADDING.put ("frequency", frequency);
        NEEDS_PADDING.put ("formantTalk", formantTalk);
        NEEDS_PADDING.put ("intensity", intensity);
        NEEDS_PADDING.put ("loopStart", loopStart);
        NEEDS_PADDING.put (PARAMETER_NAME_PITCH, pitch);
        NEEDS_PADDING.put ("vfType", vfType);
        NEEDS_PADDING.put (PARAMETER_NAME_VOLUME, volume);
        NEEDS_PADDING.put ("bitdepth", Collections.singleton ("ENV_AHDSR_BITDEPTH"));
        NEEDS_PADDING.put ("dyx_morph", dyxMorph);
        NEEDS_PADDING.put ("dyx_dry", Collections.singleton ("ENV_AHDSR_MORPH"));
        NEEDS_PADDING.put ("downsample", Collections.singleton ("LFO_SINE_SAMPLE_RATE"));

        // No padding

        final Set<String> noFilterCutoff = new HashSet<> ();
        noFilterCutoff.add ("ENV FLW_CUTOFF");
        noFilterCutoff.add ("ENV_FILTER");

        final Set<String> noFormantShift = new HashSet<> ();
        noFormantShift.add (PARAMETER_NAME_NONE);
        noFormantShift.add ("ENV_AHDSR_FORMANT");

        final Set<String> noGrainSpeed = new HashSet<> ();
        noGrainSpeed.add ("LFO_MULTI_CUTOFF");
        noGrainSpeed.add ("LFO_MULTI_SPEED");
        noGrainSpeed.add ("LFO_SAW_SPEED");
        noGrainSpeed.add ("ENV_AHDSR_SPEED");

        final Set<String> noPitch = new HashSet<> ();
        noPitch.add ("");
        noPitch.add (PARAMETER_NAME_NONE);
        noPitch.add ("GLIDE_PITCH");
        noPitch.add ("LFO_SINE_PITCH");
        noPitch.add ("LFO_TRI_PITCH");
        noPitch.add ("LFO_RAND_PITCH");
        noPitch.add ("STEP_PITCH");
        noPitch.add ("ENV_AHDSR_PITCH");
        noPitch.add ("ENV_DBD_PITCH");
        noPitch.add ("ENV_PITCH_MIX");

        final Set<String> noVolume = new HashSet<> ();
        noVolume.add (PARAMETER_NAME_NONE);
        noVolume.add (MODULATOR_DESCRIPTION_AHDSR_VOLUME);
        noVolume.add ("GLIDE_VOLUME");
        noVolume.add ("LFO_SINE_VOLUME");
        noVolume.add ("LFO_RECT_VOLUME");
        noVolume.add ("STEP_VOLUME");
        noVolume.add ("VOL_GATE");

        final Set<String> noPan = new HashSet<> ();
        noPan.add (PARAMETER_NAME_NONE);
        noPan.add ("LFO_SINE_PAN");
        noPan.add ("LFO_TRI_PAN");
        noPan.add ("LFO_RECT_PAN");
        noPan.add ("LFO_TRI_VOLUME");
        noPan.add ("ENV_DBD_PAN");

        NO_PADDING.put (PARAMETER_NAME_CUTOFF, noFilterCutoff);
        NO_PADDING.put ("formantShift", noFormantShift);
        NO_PADDING.put ("grainSpeed", noGrainSpeed);
        NO_PADDING.put (PARAMETER_NAME_PAN, noPan);
        NO_PADDING.put (PARAMETER_NAME_PITCH, noPitch);
        NO_PADDING.put (PARAMETER_NAME_VOLUME, noVolume);
    }

    private int                            version             = 0x81;
    private final List<ModulatedParameter> modulatedParameters = new ArrayList<> ();

    @SuppressWarnings("unused")
    private boolean                        isModSectionOpen    = false;
    @SuppressWarnings("unused")
    private boolean                        isBypassed          = false;
    @SuppressWarnings("unused")
    private boolean                        isRetrigger         = true;
    private String                         modulatorSourceName = MODULATOR_SOURCE_NAME_AHDSR;
    private int                            modulationSourceIndex;
    private float                          curve               = -0.332f;
    private float                          attack              = 0;
    private float                          hold                = 0;
    private float                          decay               = 500;
    private float                          release             = 300;
    private float                          sustain             = 1;
    private boolean                        ahdOnly             = false;


    /** All attributes of a modulated parameter. */
    private class ModulatedParameter
    {
        private String   parameterName        = PARAMETER_NAME_VOLUME;
        private float    intensity            = 1.0f;
        private int      flags                = 18;
        @SuppressWarnings("unused")
        private int      lag                  = 0;
        private String   modulatorDescription = MODULATOR_DESCRIPTION_AHDSR_VOLUME;
        @SuppressWarnings("unused")
        private boolean  isModShapeActive     = false;
        private float [] curveSteps;
        private float [] curvePoints;
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
        return this.isEnvelopeModulator () ? this.createEnvelopeModulator (this.getEnvelopeParameter (PARAMETER_NAME_VOLUME)) : Optional.empty ();
    }


    /**
     * Get the filter cutoff envelope if present.
     *
     * @return The filter cutoff envelope
     */
    public Optional<IEnvelopeModulator> getFilterCutoffEnvelope ()
    {
        return this.isEnvelopeModulator () ? this.createEnvelopeModulator (this.getEnvelopeParameter (PARAMETER_NAME_CUTOFF)) : Optional.empty ();
    }


    /**
     * Get the pitch envelope if present.
     *
     * @return The pitch envelope
     */
    public Optional<IEnvelopeModulator> getPitchEnvelope ()
    {
        return this.isEnvelopeModulator () ? this.createEnvelopeModulator (this.getEnvelopeParameter (PARAMETER_NAME_PITCH)) : Optional.empty ();
    }


    private Optional<IEnvelopeModulator> createEnvelopeModulator (final ModulatedParameter envelopeParameter)
    {
        if (envelopeParameter == null)
            return Optional.empty ();
        final IEnvelopeModulator envelopeModulator = new DefaultEnvelopeModulator (envelopeParameter.intensity);
        final IEnvelope envelope = envelopeModulator.getSource ();
        envelope.setAttackSlope (this.curve);
        envelope.setAttackTime (this.attack / 1000.0);
        envelope.setHoldTime (this.hold / 1000.0);
        envelope.setDecayTime (this.decay / 1000.0);
        envelope.setSustainLevel (this.ahdOnly ? 0 : this.sustain);
        envelope.setReleaseTime ((this.ahdOnly ? this.decay : this.release) / 1000.0);
        return Optional.of (envelopeModulator);
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

        // Always 01
        if (in.read () != 1)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_HEADER"));

        // Version: 0x80, 0x81
        this.version = StreamUtils.readUnsigned16 (in, false);
        if (this.version < 0x80 || this.version > 0x81)
            throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_VERSION", Integer.toString (this.version)));

        // First block with parameter/modulation info
        final int sizeBlock1 = (int) StreamUtils.readUnsigned32 (in, false);
        this.readBlock1 (new ByteArrayInputStream (in.readNBytes (sizeBlock1)));

        // Separator - always 0
        if (StreamUtils.readUnsigned32 (in, false) != 0)
            throw new IOException (Functions.getMessage ("IDS_NKI_SEPARATOR_NOT_NULL_IN_INTERNAL_MOD"));

        // Second block with the modulation source
        final int sizeBlock2 = (int) StreamUtils.readUnsigned32 (in, false);
        this.readBlock2 (new ByteArrayInputStream (in.readNBytes (sizeBlock2)));
    }


    private void readBlock1 (final InputStream in) throws IOException
    {
        final int count = (int) StreamUtils.readUnsigned32 (in, false);

        for (int i = 0; i < count; i++)
        {
            final ModulatedParameter modulatedParameter = new ModulatedParameter ();
            modulatedParameter.parameterName = StreamUtils.readWith4ByteLengthAscii (in);
            modulatedParameter.intensity = StreamUtils.readFloatLE (in);

            if (StreamUtils.readUnsigned16 (in, false) != 0xFFFF)
                throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_INTERNAL_MOD_BLOCK1_VALUE"));

            modulatedParameter.flags = in.read ();
            modulatedParameter.lag = StreamUtils.readSigned16 (in, false);

            if ((modulatedParameter.flags & 8) > 0)
                // Unused
                in.readNBytes (5);
            else
            {
                modulatedParameter.modulatorDescription = StreamUtils.readWith4ByteLengthAscii (in);

                // This is a brute force solution but does work in 99.9%
                // It is not a padding but unclear when these 0-2 bytes appear and when not
                final int needsPadding = needsPadding (i, modulatedParameter);
                if (needsPadding > 0)
                    in.skipNBytes (needsPadding);

                final boolean hasModulationShaper = in.read () > 0;
                if (hasModulationShaper)
                {
                    // Modulation Shaper Data/Table are currently not used
                    final int type = in.read ();
                    modulatedParameter.isModShapeActive = in.read () > 0;

                    switch (type)
                    {
                        default:
                        case 0:
                            // This is highly likely to be a parsing error above (wrong padding)
                            return;

                        case 1:
                            // The table consists of 128 steps
                            modulatedParameter.curveSteps = new float [128];
                            for (int p = 0; p < 128; p++)
                                modulatedParameter.curveSteps[p] = StreamUtils.readFloatLE (in);
                            break;

                        case 2:
                            // Each curve point has three values: x-position, y-position and slope
                            final int numPoints = in.read ();
                            modulatedParameter.curvePoints = new float [numPoints * 3];
                            for (int p = 0; p < numPoints * 3; p++)
                                modulatedParameter.curvePoints[p] = StreamUtils.readFloatLE (in);
                            break;
                    }
                }
            }

            this.modulatedParameters.add (modulatedParameter);
        }

        if (this.modulatedParameters.isEmpty ())
            return;

        if (this.version != 0x81 || this.modulatedParameters.get (this.modulatedParameters.size () - 1).curvePoints == null)
            in.read ();

        this.isModSectionOpen = in.read () > 0;
        this.isBypassed = in.read () > 0;
        this.isRetrigger = in.read () > 0;

        try
        {
            // Is one of these the slot of the FX?
            in.read ();
            StreamUtils.readUnsigned32 (in, false);

            this.modulatorSourceName = StreamUtils.readWith4ByteLengthAscii (in);
            this.modulationSourceIndex = (int) StreamUtils.readUnsigned32 (in, false);
        }
        catch (final IOException ex)
        {
            // This is a follow up exception if the padding in the first loop is detected falsely...
        }
    }


    private static int needsPadding (final int position, final ModulatedParameter modulatedParameter)
    {
        if (position > 0)
        {
            if (PARAMETER_NAME_CUTOFF.equals (modulatedParameter.parameterName) && "ENV_AHDSR_CUTOFF".equals (modulatedParameter.modulatorDescription))
                return 2;
            if (PARAMETER_NAME_VOLUME.equals (modulatedParameter.parameterName) && ("LFO_SINE_VOLUME".equals (modulatedParameter.modulatorDescription) || "STEP_VOLUME".equals (modulatedParameter.modulatorDescription)))
                return 1;

            if ((PARAMETER_NAME_PAN.equals (modulatedParameter.parameterName) && "ENV_DBD_PAN".equals (modulatedParameter.modulatorDescription)) || (PARAMETER_NAME_CUTOFF.equals (modulatedParameter.parameterName) && "LFO_MULTI_CUTOFF".equals (modulatedParameter.modulatorDescription)))
                return 2;
            if ("vfType".equals (modulatedParameter.parameterName) && "LFO_MULTI_3X2_TYPE".equals (modulatedParameter.modulatorDescription))
                return 2;
        }

        final Set<String> set = NEEDS_PADDING.get (modulatedParameter.parameterName);
        if (set != null && set.contains (modulatedParameter.modulatorDescription))
            return 1;

        final Set<String> set2 = NO_PADDING.get (modulatedParameter.parameterName);
        if (set2 != null && set2.contains (modulatedParameter.modulatorDescription))
            return 0;

        final boolean result = (modulatedParameter.parameterName.length () + modulatedParameter.modulatorDescription.length ()) % 2 == 1;
        return result ? 1 : 0;
    }


    private void readBlock2 (final InputStream in) throws IOException
    {
        final ModulatedParameter envelopeParameter = this.getEnvelopeParameter (PARAMETER_NAME_VOLUME, PARAMETER_NAME_CUTOFF, PARAMETER_NAME_PITCH);
        // Too short but not understood what it contains
        if ((envelopeParameter == null) || !this.isEnvelopeModulator () || (in.available () == 0x38))
            return;

        if (MODULATOR_SOURCE_NAME_AHDSR.equals (this.modulatorSourceName) || "<none>".equals (this.modulatorSourceName) || "".equals (this.modulatorSourceName))
        {
            // Not used
            in.readNBytes (34);

            this.curve = StreamUtils.readFloatLE (in);
            this.attack = StreamUtils.readFloatLE (in);
            this.hold = StreamUtils.readFloatLE (in);
            this.decay = StreamUtils.readFloatLE (in);
            this.release = StreamUtils.readFloatLE (in);
            this.sustain = StreamUtils.readFloatLE (in);
            this.ahdOnly = in.read () > 0;

            // 52 or 16 more bytes not used
        }
        else if ("ENV_DBD".equals (this.modulatorSourceName))
        {
            // Decay/Break/Decay envelope type not supported
        }
        else
        {
            // Unknown envelope...
        }
    }


    private ModulatedParameter getEnvelopeParameter (final String... parameters)
    {
        for (final ModulatedParameter modulatedParameter: this.modulatedParameters)
            for (final String parameter: parameters)
                if (modulatedParameter.parameterName.equals (parameter))
                    return modulatedParameter;
        return null;
    }


    private boolean isEnvelopeModulator ()
    {
        return this.modulationSourceIndex == 0 || this.modulationSourceIndex == 2;
    }

}