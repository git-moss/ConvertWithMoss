// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * The Decent Sampler preset format consists of several XML tags.
 *
 * @author Jürgen Moßgraber
 */
public class DecentSamplerTag
{
    /** The root tag. */
    public static final String                    DECENTSAMPLER        = "DecentSampler";
    /** The root minimum version attribute. */
    public static final String                    MIN_VERSION          = "minVersion";
    /** The product identifier attribute on the root of a product preset. */
    public static final String                    PRODUCT_ID           = "_dsProductId";
    /** The MIDI tag. */
    public static final String                    MIDI                 = "midi";
    /** The buses tag. */
    public static final String                    BUSES                = "buses";
    /** The arpeggiator tag. */
    public static final String                    ARPEGGIATOR          = "arpeggiator";
    /** The note sequences tag. */
    public static final String                    NOTE_SEQUENCES       = "noteSequences";
    /** The enabled attribute of groups, tags and effects. */
    public static final String                    ENABLED              = "enabled";

    /** The effects tag. */
    public static final String                    EFFECTS              = "effects";
    /** The modulation effects tag. */
    public static final String                    MOD_EFFECT           = "effect";
    /** The effects tag. */
    public static final String                    EFFECTS_EFFECT       = "effect";
    /** The effect type attribute. */
    public static final String                    EFFECT_TYPE          = "type";
    /** The filter frequency attribute of an effect. */
    public static final String                    EFFECT_FREQUENCY     = "frequency";
    /** The filter resonance attribute of an effect. */
    public static final String                    EFFECT_RESONANCE     = "resonance";
    /** The level attribute of the gain effect. */
    public static final String                    EFFECT_LEVEL         = "level";
    /** The unit of the level attribute of the gain effect, decibels if absent. */
    public static final String                    EFFECT_LEVEL_UNIT    = "levelUnit";
    /** The unit value for a level which is a linear factor. */
    public static final String                    LEVEL_UNIT_LINEAR    = "linear";
    /** The dry/wet mix attribute of an effect. */
    public static final String                    EFFECT_MIX           = "mix";
    /** The wet level attribute of an effect. */
    public static final String                    EFFECT_WET_LEVEL     = "wetLevel";
    /** The impulse response attribute of the convolution effect. */
    public static final String                    EFFECT_IR_FILE       = "irFile";
    /** The semitones attribute of the pitch shift effect. */
    public static final String                    EFFECT_PITCH_SHIFT   = "pitchShift";
    /** The type of the pitch shift effect. */
    public static final String                    EFFECT_TYPE_PITCH    = "pitch_shift";
    /** The type of the gain effect. */
    public static final String                    EFFECT_TYPE_GAIN     = "gain";
    /** The type of the reverb effect. */
    public static final String                    EFFECT_TYPE_REVERB   = "reverb";
    /** The modulators tag. */
    public static final String                    MODULATORS           = "modulators";
    /** The envelope tag. */
    public static final String                    ENVELOPE             = "envelope";
    /** The low frequency oscillator tag. */
    public static final String                    LFO                  = "lfo";
    /** The LFO shape attribute. */
    public static final String                    LFO_SHAPE            = "shape";
    /** The LFO frequency attribute. */
    public static final String                    LFO_FREQUENCY        = "frequency";
    /** The LFO frequency format attribute. */
    public static final String                    LFO_FREQUENCY_FORMAT = "frequencyFormat";
    /** The LFO delay time attribute. */
    public static final String                    LFO_DELAY_TIME       = "delayTime";

    /** The user interface tag. */
    public static final String                    UI                   = "ui";
    /** The tabulator tag. */
    public static final String                    TAB                  = "tab";
    /** The labeled knob tag. */
    public static final String                    LABELED_KNOB         = "labeled-knob";
    /** The binding tag. */
    public static final String                    BINDING              = "binding";
    /** The parameter attribute of a binding. */
    public static final String                    BINDING_PARAMETER    = "parameter";
    /** The tags tag. */
    public static final String                    TAGS                 = "tags";
    /** The tag tag. */
    public static final String                    TAG                  = "tag";
    /** The name attribute of a tag. */
    public static final String                    TAG_NAME             = "name";
    /** The polyphony attribute of a tag. */
    public static final String                    TAG_POLYPHONY        = "polyphony";
    /** The attribute which assigns tags to all groups or to a single group. */
    public static final String                    TAGS_ATTRIBUTE       = "tags";
    /** The name of the tag which is used to limit an instrument to one voice. */
    public static final String                    TAG_MONOPHONIC       = "monophonic";
    /** The start of the name of the tag of an exclusive group, followed by its number. */
    public static final String                    TAG_EXCLUSIVE_GROUP  = "exclusiveGroup";

    /** The groups tag. */
    public static final String                    GROUPS               = "groups";
    /** The groups tag. */
    public static final String                    GLOBAL_TUNING        = "globalTuning";
    /** The alias of the volume attribute on the groups level, which wins if both are set. */
    public static final String                    GLOBAL_VOLUME        = "globalVolume";
    /** The pan offset of the whole instrument, which is added to the pan. */
    public static final String                    GLOBAL_PAN           = "globalPan";
    /** The group tag. */
    public static final String                    GROUP                = "group";
    /** The sample tag. */
    public static final String                    SAMPLE               = "sample";
    /** The sequence mode tag. */
    public static final String                    SEQ_MODE             = "seqMode";
    /** The sequence length tag. */
    public static final String                    SEQ_LENGTH           = "seqLength";

    /** The sequence mode value for playing all samples. */
    public static final String                    SEQ_ALWAYS           = "always";
    /** The sequence mode value for cycling through all samples. */
    public static final String                    SEQ_ROUND_ROBIN      = "round_robin";
    /** The sequence mode value for randomly selecting a sample but never twice in a row. */
    public static final String                    SEQ_RANDOM           = "random";
    /** The sequence mode value for randomly selecting a sample, repetitions are possible. */
    public static final String                    SEQ_TRUE_RANDOM      = "true_random";

    /** The global tuning attribute. */
    public static final String                    GROUP_TUNING         = "groupTuning";
    /** The group name tag. */
    public static final String                    GROUP_NAME           = "name";
    /** The group enabled tag. */
    public static final String                    GROUP_ENABLED        = "enabled";
    /** The alias of the volume attribute on the group level, which wins if both are set. */
    public static final String                    GROUP_VOLUME         = "groupVolume";
    /** The pan offset of a group, which is added to the pan. */
    public static final String                    GROUP_PAN            = "groupPan";
    /**
     * The attribute which turns the amplitude envelope off, which plays the samples as one-shots.
     */
    public static final String                    AMP_ENV_ENABLED      = "ampEnvEnabled";
    /** The tags which stop a sample when a sample with one of them is triggered. */
    public static final String                    SILENCED_BY_TAGS     = "silencedByTags";
    /** How a silenced sample is stopped. */
    public static final String                    SILENCING_MODE       = "silencingMode";
    /** The silencing mode value which stops a sample immediately. */
    public static final String                    SILENCING_MODE_FAST  = "fast";
    /** A fade-out time of a silenced sample which overrides the silencing mode. */
    public static final String                    SILENCING_DECAY      = "silencingDecay";
    /** The start of the names of the output routing attributes, followed by the output number. */
    public static final String                    OUTPUT               = "output";
    /** The end of the name of the attribute which selects the target of an output. */
    public static final String                    OUTPUT_TARGET        = "Target";
    /** The end of the name of the attribute which sets the volume of an output. */
    public static final String                    OUTPUT_VOLUME        = "Volume";
    /** The output target of the main output. */
    public static final String                    OUTPUT_MAIN          = "MAIN_OUTPUT";
    /** The output target which does not output anything. */
    public static final String                    OUTPUT_NONE          = "NO_OUTPUT";
    /** The number of outputs of a sample. */
    public static final int                       NUMBER_OF_OUTPUTS    = 8;

    /** The group name tag. */
    public static final String                    SAMPLE_NAME          = "name";
    /** The sample path attribute. */
    public static final String                    PATH                 = "path";
    /** The volume tag on different levels. */
    public static final String                    VOLUME               = "volume";
    /** The volume velocity tag on the group level. */
    public static final String                    AMP_VELOCITY_TRACK   = "ampVelTrack";
    /** The panning tag on different levels. */
    public static final String                    PANNING              = "pan";
    /** The start tag sample attribute. */
    public static final String                    START                = "start";
    /** The end tag sample attribute. */
    public static final String                    END                  = "end";
    /** The tuning tag sample attribute. */
    public static final String                    TUNING               = "tuning";
    /** The sequence position tag sample attribute. */
    public static final String                    SEQ_POSITION         = "seqPosition";
    /** The root note tag sample attribute. */
    public static final String                    ROOT_NOTE            = "rootNote";
    /** The pitch key tracking tag sample attribute. */
    public static final String                    PITCH_KEY_TRACK      = "pitchKeyTrack";
    /** The trigger group / sample attribute. */
    public static final String                    TRIGGER              = "trigger";
    /** The low note tag sample attribute. */
    public static final String                    LO_NOTE              = "loNote";
    /** The high note tag sample attribute. */
    public static final String                    HI_NOTE              = "hiNote";
    /** The low velocity tag sample attribute. */
    public static final String                    LO_VEL               = "loVel";
    /** The high velocity tag sample attribute. */
    public static final String                    HI_VEL               = "hiVel";

    /** The loop enabled tag sample attribute. */
    public static final String                    LOOP_ENABLED         = "loopEnabled";
    /** The loop start tag sample attribute. */
    public static final String                    LOOP_START           = "loopStart";
    /** The loop end tag sample attribute. */
    public static final String                    LOOP_END             = "loopEnd";
    /** The loop cross-fade tag sample attribute. */
    public static final String                    LOOP_CROSSFADE       = "loopCrossfade";

    /** The envelope modulation amount attribute. */
    public static final String                    MOD_AMOUNT           = "modAmount";

    /** The envelope attack attribute. */
    public static final String                    ENV_ATTACK           = "attack";
    /** The envelope decay attribute. */
    public static final String                    ENV_DECAY            = "decay";
    /** The envelope sustain attribute. */
    public static final String                    ENV_SUSTAIN          = "sustain";
    /** The envelope release attribute. */
    public static final String                    ENV_RELEASE          = "release";

    /** The envelope attack curve attribute. */
    public static final String                    ENV_ATTACK_CURVE     = "attackCurve";
    /** The envelope decay curve attribute. */
    public static final String                    ENV_DECAY_CURVE      = "decayCurve";
    /** The envelope release curve attribute. */
    public static final String                    ENV_RELEASE_CURVE    = "releaseCurve";

    /** The supported top level tags. */
    public static final Set<String>               TOP_LEVEL_TAGS       = Set.of (EFFECTS, UI, GROUPS, MODULATORS, TAGS);
    /** The supported group tags. */
    public static final Set<String>               GROUP_TAGS           = Set.of (SAMPLE);
    /** The supported sample tags. */
    public static final Set<String>               SAMPLE_TAGS          = Set.of (PATH);

    /** Supported attributes of all tags. */
    private static final Map<String, Set<String>> ATTRIBUTES           = new HashMap<> ();

    static
    {
        ATTRIBUTES.put (DECENTSAMPLER, Set.of (MIN_VERSION, PRODUCT_ID));
        ATTRIBUTES.put (GROUPS, withOutputs (GLOBAL_TUNING, VOLUME, GLOBAL_VOLUME, GLOBAL_PAN, PANNING, TUNING, PITCH_KEY_TRACK, AMP_VELOCITY_TRACK, AMP_ENV_ENABLED, SILENCED_BY_TAGS, SILENCING_MODE, SILENCING_DECAY, ROOT_NOTE, LO_NOTE, HI_NOTE, LO_VEL, HI_VEL, START, END, LOOP_START, LOOP_END, LOOP_CROSSFADE, LOOP_ENABLED, ENV_ATTACK, ENV_ATTACK_CURVE, ENV_DECAY, ENV_DECAY_CURVE, ENV_SUSTAIN, ENV_RELEASE, ENV_RELEASE_CURVE, TRIGGER, SEQ_MODE, SEQ_POSITION, TAGS_ATTRIBUTE));
        ATTRIBUTES.put (GROUP, withOutputs (GROUP_NAME, GROUP_ENABLED, GROUP_TUNING, TUNING, VOLUME, GROUP_VOLUME, GROUP_PAN, AMP_VELOCITY_TRACK, PANNING, PITCH_KEY_TRACK, AMP_ENV_ENABLED, SILENCED_BY_TAGS, SILENCING_MODE, SILENCING_DECAY, ROOT_NOTE, LO_NOTE, HI_NOTE, LO_VEL, HI_VEL, START, END, LOOP_START, LOOP_END, LOOP_CROSSFADE, LOOP_ENABLED, ENV_ATTACK, ENV_ATTACK_CURVE, ENV_DECAY, ENV_DECAY_CURVE, ENV_SUSTAIN, ENV_RELEASE, ENV_RELEASE_CURVE, TRIGGER, SEQ_MODE, SEQ_POSITION, TAGS_ATTRIBUTE));
        ATTRIBUTES.put (SAMPLE, withOutputs (SAMPLE_NAME, PATH, ROOT_NOTE, LO_NOTE, HI_NOTE, LO_VEL, HI_VEL, START, END, TUNING, VOLUME, PANNING, PITCH_KEY_TRACK, AMP_VELOCITY_TRACK, AMP_ENV_ENABLED, SILENCED_BY_TAGS, SILENCING_MODE, SILENCING_DECAY, TAGS_ATTRIBUTE, TRIGGER, LOOP_START, LOOP_END, LOOP_CROSSFADE, LOOP_ENABLED, ENV_ATTACK, ENV_ATTACK_CURVE, ENV_DECAY, ENV_DECAY_CURVE, ENV_SUSTAIN, ENV_RELEASE, ENV_RELEASE_CURVE, SEQ_MODE, SEQ_POSITION));
        ATTRIBUTES.put (TAG, Set.of (TAG_NAME, TAG_POLYPHONY, VOLUME, ENABLED));
        ATTRIBUTES.put (EFFECTS_EFFECT, Set.of (EFFECT_TYPE, EFFECT_FREQUENCY, EFFECT_RESONANCE, ENABLED, EFFECT_LEVEL, EFFECT_LEVEL_UNIT));
        ATTRIBUTES.put (ENVELOPE, Set.of (MOD_AMOUNT, ENV_ATTACK, ENV_ATTACK_CURVE, ENV_DECAY, ENV_DECAY_CURVE, ENV_SUSTAIN, ENV_RELEASE, ENV_RELEASE_CURVE));
        ATTRIBUTES.put (LFO, Set.of (MOD_AMOUNT, LFO_SHAPE, LFO_FREQUENCY, LFO_FREQUENCY_FORMAT, LFO_DELAY_TIME));
        ATTRIBUTES.put (BINDING, Set.of (BINDING_PARAMETER));
    }


    /**
     * Private constructor for utility class.
     */
    private DecentSamplerTag ()
    {
        // Intentionally empty
    }


    /**
     * Get the supported attributes of a tag.
     *
     * @param tagName The name of the tag
     * @return The tags
     */
    public static Set<String> getAttributes (final String tagName)
    {
        return ATTRIBUTES.get (tagName);
    }


    /**
     * Add the attributes of the output routing, which can be set on all levels, to the attributes
     * of a tag.
     *
     * @param attributes The other attributes of the tag
     * @return All attributes
     */
    private static Set<String> withOutputs (final String... attributes)
    {
        final Set<String> result = new HashSet<> (Arrays.asList (attributes));
        for (int output = 1; output <= NUMBER_OF_OUTPUTS; output++)
        {
            result.add (OUTPUT + output + OUTPUT_TARGET);
            result.add (OUTPUT + output + OUTPUT_VOLUME);
        }
        return result;
    }
}
