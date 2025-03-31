// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tal;

/**
 * The TAL sampler format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class TALSamplerTag
{
    /** The root tag. */
    public static final String ROOT                    = "tal";
    /** The programs tag. */
    public static final String PROGRAMS                = "programs";
    /** The program tag. */
    public static final String PROGRAM                 = "program";

    /** The sample layer tag. */
    public static final String SAMPLE_LAYER            = "samplelayer";
    /** The multi-samples tag. */
    public static final String MULTISAMPLES            = "multisamples";
    /** The sample tag. */
    public static final String MULTISAMPLE             = "multisample";

    /** The current program attribute tag. */
    public static final String ROOT_CUR_PROGRAM        = "curprogram";
    /** The version tag. */
    public static final String ROOT_VERSION            = "version";

    /** The program name attribute tag. */
    public static final String PROGRAM_NAME            = "programname";
    /** The layer enabled attribute tag. */
    public static final String PROGRAM_LAYER_ON        = "sampleenabled";
    /** The number of voices which can be played. */
    public static final String PROGRAM_NUM_VOICES      = "numvoices";
    /** The pitchbend range. */
    public static final String PITCHBEND_RANGE         = "globalpitchbendrange";

    /** The multi-sample URL tag. */
    public static final String MULTISAMPLE_URL         = "url";

    /** The start tag sample attribute. */
    public static final String START_SAMPLE            = "startsample";
    /** The end tag sample attribute. */
    public static final String END_SAMPLE              = "endsample";

    /** The root note tag sample attribute. */
    public static final String ROOT_NOTE               = "rootkey";
    /** The low note tag sample attribute. */
    public static final String LO_NOTE                 = "lowkey";
    /** The high note tag sample attribute. */
    public static final String HI_NOTE                 = "highkey";
    /** The low velocity tag sample attribute. */
    public static final String LO_VEL                  = "velocitystart";
    /** The high velocity tag sample attribute. */
    public static final String HI_VEL                  = "velocityend";
    /** The volume tag on different levels. */
    public static final String VOLUME                  = "volume";
    /** The sample panning attribute. */
    public static final String PANNING                 = "pan";
    /** The layer transpose attribute. */
    public static final String LAYER_TRANSPOSE         = "layertranspose";
    /** The tune sample attribute. */
    public static final String SAMPLE_TUNE             = "sampletune";
    /** The fine tune sample attribute. */
    public static final String SAMPLE_FINE_TUNE        = "samplefinetune";
    /** The sample transpose attribute. */
    public static final String TRANSPOSE               = "transpose";
    /** The transpose tag sample attribute. */
    public static final String DETUNE                  = "detune";

    /** The pitch key tracking tag sample attribute (0: no tracking, 1 tracking enabled). */
    public static final String PITCH_KEY_TRACK         = "track";

    /** The loop enabled tag sample attribute. */
    public static final String LOOP_ENABLED            = "loopenabled";
    /** The loop start tag sample attribute. */
    public static final String LOOP_START              = "loopstartsample";
    /** The loop end tag sample attribute. */
    public static final String LOOP_END                = "loopendsample";
    /** The loop alternate attribute tag. */
    public static final String LOOP_ALTERNATE          = "pingpongloop";

    /** The sample reverse attribute. */
    public static final String REVERSE                 = "reverse";
    /** Fade in samples attribute. */
    public static final String FADE_IN_SAMPLES         = "fadeinsamples";

    /** TAL-Sampler has a few ROM Samples with base waveforms included, always zero. */
    public static final String IS_ROM_SAMPLE           = "isromsample";
    /** The sample slice number attribute, always zero. */
    public static final String SLICE                   = "slice";
    /** Should the phase be inverted? */
    public static final String PHASE_INVERSE           = "phaseinverse";
    /** Invert stereo: 1 -> normal; 0 -> inverted stereo output. */
    public static final String STEREO_INVERSE          = "stereoinverse";
    /** Is the group muted? */
    public static final String MUTE_GROUP              = "mutegroup";

    /** The global amplitude envelope attack attribute. */
    public static final String ADSR_AMP_ATTACK         = "adsrampattack";
    /** The global amplitude envelope hold attribute. */
    public static final String ADSR_AMP_HOLD           = "adsramphold";
    /** The global amplitude envelope decay attribute. */
    public static final String ADSR_AMP_DECAY          = "adsrampdecay";
    /** The global amplitude envelope sustain attribute. */
    public static final String ADSR_AMP_SUSTAIN        = "adsrampsustain";
    /** The global amplitude envelope release attribute. */
    public static final String ADSR_AMP_RELEASE        = "adsramprelease";

    /** The global filter envelope attack attribute. */
    public static final String ADSR_VCF_ATTACK         = "adsrvcfattack";
    /** The global filter envelope hold attribute. */
    public static final String ADSR_VCF_HOLD           = "adsrvcfhold";
    /** The global filter envelope decay attribute. */
    public static final String ADSR_VCF_DECAY          = "adsrvcfdecay";
    /** The global filter envelope sustain attribute. */
    public static final String ADSR_VCF_SUSTAIN        = "adsrvcfsustain";
    /** The global filter envelope release attribute. */
    public static final String ADSR_VCF_RELEASE        = "adsrvcfrelease";

    /** The global modulation (pitch) envelope attack attribute. */
    public static final String ADSR_MOD_ATTACK         = "adsrmodattack";
    /** The global modulation (pitch) envelope hold attribute. */
    public static final String ADSR_MOD_HOLD           = "adsrmodhold";
    /** The global modulation (pitch) envelope decay attribute. */
    public static final String ADSR_MOD_DECAY          = "adsrmoddecay";
    /** The global modulation (pitch) envelope sustain attribute. */
    public static final String ADSR_MOD_SUSTAIN        = "adsrmodsustain";
    /** The global modulation (pitch) envelope release attribute. */
    public static final String ADSR_MOD_RELEASE        = "adsrmodrelease";

    /** The amplitude envelope attack attribute. */
    public static final String AMP_ENV_ATTACK          = "attack";
    /** The amplitude envelope decay attribute. */
    public static final String AMP_ENV_DECAY           = "decay";
    /** The amplitude envelope sustain attribute. */
    public static final String AMP_ENV_SUSTAIN         = "sustain";
    /** The amplitude envelope release attribute. */
    public static final String AMP_ENV_RELEASE         = "release";

    /** The filter cutoff attribute. */
    public static final String FILTER_CUTOFF           = "filtercutoff";
    /** The filter resonance attribute. */
    public static final String FILTER_RESONANCE        = "filterresonance";
    /** The filter mode attribute. */
    public static final String FILTER_MODE             = "filtermode";
    /** The filter keyboard tracking attribute. [0..1] -> -100%..100% */
    public static final String FILTER_KEYBOARD         = "filterkeyboardvalue";
    /** The filter envelope intensity attribute. [0..1] -> -100%..100% */
    public static final String FILTER_ENVELOPE         = "filterenvelope";
    /** The filter layer on/off attribute. */
    public static final String FILTER_LAYER_ON         = "filterlayer";

    /** The modulation matrix tag. */
    public static final String MOD_MATRIX              = "modmatrix";
    /** The modulation matrix entry tag. */
    public static final String MOD_MATRIX_ENTRY        = "entry";

    /** The modulation matrix source ID attribute. */
    public static final String MOD_MATRIX_SOURCE_ID    = "modmatrixsourceid";
    /** The modulation matrix source ID attribute. */
    public static final String MOD_MATRIX_AMOUNT       = "modmatrixamount";
    /** The modulation matrix parameter ID attribute. */
    public static final String MOD_MATRIX_PARAMETER_ID = "parameterid";


    /**
     * Private constructor for utility class.
     */
    private TALSamplerTag ()
    {
        // Intentionally empty
    }
}
