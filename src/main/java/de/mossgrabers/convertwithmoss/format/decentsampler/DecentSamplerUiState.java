// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.decentsampler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Applies the values of the controls of the user interface of a DecentSampler preset to the
 * parameters which they are bound to. DecentSampler sends the value of a control through its
 * bindings when a preset is loaded (unless a binding is disabled for loading), therefore the stored
 * control values decide how a preset sounds and not only the attributes of its groups, samples,
 * tags, effects and modulators. The resolved values are written into these attributes of the preset
 * document, from which the detector reads them.
 *
 * @author Jürgen Moßgraber
 */
final class DecentSamplerUiState
{
    private static final String              TAG_ENABLED            = "ENABLED";

    private static final String              FEATURE_CONTROL        = "IDS_DS_FEATURE_CONTROL";

    private static final String              CONTROL                = "control";
    private static final String              MENU                   = "menu";
    private static final String              MENU_OPTION            = "option";
    private static final String              BUTTON                 = "button";
    private static final String              BUTTON_STATE           = "state";
    private static final Set<String>         CONTROLS               = Set.of (CONTROL, DecentSamplerTag.LABELED_KNOB, MENU, BUTTON);
    private static final String              VALUE                  = "value";
    private static final String              MIN_VALUE              = "minValue";
    private static final String              MAX_VALUE              = "maxValue";
    private static final String []           CONTROL_NAMES          =
    {
        "label",
        "parameterName",
        "tooltip",
        "parameter"
    };

    private static final String              BINDING_TYPE           = "type";
    private static final String              BINDING_LEVEL          = "level";
    private static final String              BINDING_POSITION       = "position";
    private static final String              BINDING_IDENTIFIER     = "identifier";
    private static final String              BINDING_TAGS           = "tags";
    private static final String              GROUP_INDEX            = "groupIndex";
    private static final String              GROUP_TAGS             = "groupTags";
    private static final String              EFFECT_INDEX           = "effectIndex";
    private static final String              EFFECT_TAGS            = "effectTags";
    private static final String              MODULATOR_TAGS         = "modulatorTags";
    private static final String              FACTOR                 = "factor";
    private static final String              TRIGGER_ON_LOAD        = "triggerOnLoad";
    private static final String              TRANSLATION            = "translation";
    private static final String              TRANSLATION_LINEAR     = "linear";
    private static final String              TRANSLATION_TABLE      = "table";
    private static final String              TRANSLATION_FIXED      = "fixed_value";
    private static final String              TRANSLATION_VALUE      = "translationValue";
    private static final String              TRANSLATION_ENTRIES    = "translationTable";
    private static final String              TRANSLATION_OUTPUT_MIN = "translationOutputMin";
    private static final String              TRANSLATION_OUTPUT_MAX = "translationOutputMax";
    private static final String              TRANSLATION_REVERSED   = "translationReversed";

    private static final String              TYPE_AMP               = "amp";
    private static final String              TYPE_GENERAL           = "general";
    private static final String              TYPE_EFFECT            = "effect";
    private static final String              TYPE_MODULATOR         = "modulator";
    private static final String              LEVEL_INSTRUMENT       = "instrument";
    private static final String              LEVEL_GROUP            = "group";
    private static final String              LEVEL_TAG              = "tag";
    private static final String              PARAMETER_VOLUME       = "AMP_VOLUME";
    private static final String              PARAMETER_PAN          = "PAN";
    private static final String              PARAMETER_LEVEL        = "LEVEL";

    /** The parameters of the outputs of a group, e.g. OUTPUT_1_VOLUME. */
    private static final Pattern             OUTPUT_PARAMETER       = Pattern.compile ("OUTPUT_(\\d+)_(VOLUME|TARGET)");

    /** The attributes of the groups and group elements which the amplitude parameters set. */
    private static final Map<String, String> AMP_PARAMETERS         = new HashMap<> ();
    /** The attributes of an effect which the effect parameters set. */
    private static final Map<String, String> EFFECT_PARAMETERS      = new HashMap<> ();
    /** The attributes of a tag which the tag parameters set. */
    private static final Map<String, String> TAG_PARAMETERS         = new HashMap<> ();
    /** The attributes of a modulator which the modulator parameters set. */
    private static final Map<String, String> MODULATOR_PARAMETERS   = new HashMap<> ();
    /** The attributes which have a value of true or false. */
    private static final Set<String>         BOOLEAN_ATTRIBUTES     = Set.of (DecentSamplerTag.ENABLED, DecentSamplerTag.AMP_ENV_ENABLED);
    /** The attributes which have a text value. */
    private static final Set<String>         TEXT_ATTRIBUTES        = Set.of (DecentSamplerTag.EFFECT_IR_FILE, DecentSamplerTag.SILENCING_MODE);
    static
    {
        AMP_PARAMETERS.put ("ENV_ATTACK", DecentSamplerTag.ENV_ATTACK);
        AMP_PARAMETERS.put ("ENV_DECAY", DecentSamplerTag.ENV_DECAY);
        AMP_PARAMETERS.put ("ENV_SUSTAIN", DecentSamplerTag.ENV_SUSTAIN);
        AMP_PARAMETERS.put ("ENV_RELEASE", DecentSamplerTag.ENV_RELEASE);
        AMP_PARAMETERS.put ("ENV_ATTACK_CURVE", DecentSamplerTag.ENV_ATTACK_CURVE);
        AMP_PARAMETERS.put ("ENV_DECAY_CURVE", DecentSamplerTag.ENV_DECAY_CURVE);
        AMP_PARAMETERS.put ("ENV_RELEASE_CURVE", DecentSamplerTag.ENV_RELEASE_CURVE);
        AMP_PARAMETERS.put ("GLOBAL_TUNING", DecentSamplerTag.GLOBAL_TUNING);
        AMP_PARAMETERS.put ("GROUP_TUNING", DecentSamplerTag.GROUP_TUNING);
        AMP_PARAMETERS.put ("PITCH_KEY_TRACK", DecentSamplerTag.PITCH_KEY_TRACK);
        AMP_PARAMETERS.put ("AMP_VEL_TRACK", DecentSamplerTag.AMP_VELOCITY_TRACK);
        AMP_PARAMETERS.put (TAG_ENABLED, DecentSamplerTag.ENABLED);
        AMP_PARAMETERS.put ("AMP_ENV_ENABLED", DecentSamplerTag.AMP_ENV_ENABLED);
        AMP_PARAMETERS.put ("SILENCING_MODE", DecentSamplerTag.SILENCING_MODE);
        AMP_PARAMETERS.put ("SILENCING_DECAY", DecentSamplerTag.SILENCING_DECAY);

        EFFECT_PARAMETERS.put (TAG_ENABLED, DecentSamplerTag.ENABLED);
        EFFECT_PARAMETERS.put ("FX_FILTER_FREQUENCY", DecentSamplerTag.EFFECT_FREQUENCY);
        EFFECT_PARAMETERS.put ("FX_FILTER_RESONANCE", DecentSamplerTag.EFFECT_RESONANCE);
        EFFECT_PARAMETERS.put ("FX_MIX", DecentSamplerTag.EFFECT_MIX);
        EFFECT_PARAMETERS.put ("FX_WET_LEVEL", DecentSamplerTag.EFFECT_WET_LEVEL);
        EFFECT_PARAMETERS.put ("FX_REVERB_WET_LEVEL", DecentSamplerTag.EFFECT_WET_LEVEL);
        EFFECT_PARAMETERS.put ("FX_IR_FILE", DecentSamplerTag.EFFECT_IR_FILE);
        EFFECT_PARAMETERS.put ("FX_PITCH_SHIFT", DecentSamplerTag.EFFECT_PITCH_SHIFT);
        EFFECT_PARAMETERS.put (PARAMETER_LEVEL, DecentSamplerTag.EFFECT_LEVEL);

        TAG_PARAMETERS.put (PARAMETER_VOLUME, DecentSamplerTag.VOLUME);
        TAG_PARAMETERS.put ("TAG_VOLUME", DecentSamplerTag.VOLUME);
        TAG_PARAMETERS.put (TAG_ENABLED, DecentSamplerTag.ENABLED);
        TAG_PARAMETERS.put ("TAG_ENABLED", DecentSamplerTag.ENABLED);
        TAG_PARAMETERS.put ("TAG_POLYPHONY", DecentSamplerTag.TAG_POLYPHONY);

        MODULATOR_PARAMETERS.put ("MOD_AMOUNT", DecentSamplerTag.MOD_AMOUNT);
        MODULATOR_PARAMETERS.put ("FREQUENCY", DecentSamplerTag.LFO_FREQUENCY);
    }


    /**
     * Private constructor for utility class.
     */
    private DecentSamplerUiState ()
    {
        // Intentionally empty
    }


    /**
     * Apply the stored values of all controls of the user interface to the parameters which they
     * are bound to.
     *
     * @param root The root element of the preset
     * @param unsupported Where to add the descriptions of the controls whose value could not be
     *            applied
     */
    static void apply (final Element root, final Set<String> unsupported)
    {
        final Element ui = XMLUtils.getChildElementByName (root, DecentSamplerTag.UI);
        if (ui == null)
            return;

        final NodeList elements = ui.getElementsByTagName ("*");
        for (int i = 0; i < elements.getLength (); i++)
        {
            final Element control = (Element) elements.item (i);
            final String kind = control.getTagName ();
            if (!CONTROLS.contains (kind))
                continue;

            try
            {
                applyControl (root, control, kind, unsupported);
            }
            catch (final IllegalArgumentException _)
            {
                unsupported.add (Functions.getMessage (FEATURE_CONTROL, getControlName (control, null)));
            }
        }
    }


    /**
     * Apply the value of one control to all parameters which it is bound to. The value of a menu
     * selects one of its options (numbered from 1, 0 selects none), the value of a button one of
     * its states (numbered from 0), whose bindings are then applied.
     *
     * @param root The root element of the preset
     * @param control The control
     * @param kind The type of the control
     * @param unsupported Where to add the descriptions of the controls whose value could not be
     *            applied
     */
    private static void applyControl (final Element root, final Element control, final String kind, final Set<String> unsupported)
    {
        final double min = number (control, MIN_VALUE, 0);
        final double max = number (control, MAX_VALUE, 1);
        double value = number (control, VALUE, 0);

        Element bindingParent = control;
        final boolean isMenu = MENU.equals (kind);
        if (isMenu || BUTTON.equals (kind))
        {
            final List<Element> choices = XMLUtils.getChildElementsByName (control, isMenu ? MENU_OPTION : BUTTON_STATE, false);
            final int index = (int) value - (isMenu ? 1 : 0);
            if (index < 0 || index >= choices.size ())
                return;
            bindingParent = choices.get (index);
        }
        else if (max >= min)
            value = Math.clamp (value, min, max);

        for (final Element binding: XMLUtils.getChildElementsByName (bindingParent, DecentSamplerTag.BINDING, false))
        {
            if (isFalse (binding.getAttribute (DecentSamplerTag.ENABLED)) || isFalse (binding.getAttribute (TRIGGER_ON_LOAD)))
                continue;
            try
            {
                if (!applyBinding (root, binding, translate (binding, value, min, max)))
                    unsupported.add (Functions.getMessage (FEATURE_CONTROL, getControlName (control, binding)));
            }
            catch (final IllegalArgumentException _)
            {
                unsupported.add (Functions.getMessage (FEATURE_CONTROL, getControlName (control, binding)));
            }
        }
    }


    /**
     * Translate the value of a control into the value which a binding sends to its parameter.
     *
     * @param binding The binding
     * @param value The value of the control
     * @param min The minimum value of the control
     * @param max The maximum value of the control
     * @return The value to send to the parameter
     * @throws IllegalArgumentException The translation is invalid
     */
    private static String translate (final Element binding, final double value, final double min, final double max)
    {
        final String mode = binding.getAttribute (TRANSLATION);
        if (TRANSLATION_FIXED.equals (mode))
            return binding.getAttribute (TRANSLATION_VALUE);

        final double input = value * number (binding, FACTOR, 1);
        if (TRANSLATION_TABLE.equals (mode))
            return Double.toString (translateTable (binding.getAttribute (TRANSLATION_ENTRIES), input));
        if (!mode.isEmpty () && !TRANSLATION_LINEAR.equals (mode))
            throw new IllegalArgumentException (mode);

        // Without an output range the value is passed on unchanged
        final boolean isReversed = Boolean.parseBoolean (binding.getAttribute (TRANSLATION_REVERSED));
        if (!binding.hasAttribute (TRANSLATION_OUTPUT_MIN) && !binding.hasAttribute (TRANSLATION_OUTPUT_MAX) && !isReversed)
            return Double.toString (input);
        if (max <= min)
            throw new IllegalArgumentException (MAX_VALUE);
        double position = Math.clamp ((input - min) / (max - min), 0, 1);
        if (isReversed)
            position = 1 - position;
        final double outputMin = number (binding, TRANSLATION_OUTPUT_MIN, min);
        return Double.toString (outputMin + position * (number (binding, TRANSLATION_OUTPUT_MAX, max) - outputMin));
    }


    /**
     * Interpolate a value in a translation table.
     *
     * @param table The table, pairs of input and output values separated by semicolons, e.g.
     *            '0,33;0.5,1100;1,22000'
     * @param input The input value
     * @return The interpolated output value
     * @throws IllegalArgumentException The table is invalid
     */
    private static double translateTable (final String table, final double input)
    {
        final String [] entries = table.split (";");
        if (entries.length < 2)
            throw new IllegalArgumentException (TRANSLATION_ENTRIES);
        final double [] x = new double [entries.length];
        final double [] y = new double [entries.length];
        for (int i = 0; i < entries.length; i++)
        {
            final String [] pair = entries[i].split (",");
            if (pair.length != 2)
                throw new IllegalArgumentException (TRANSLATION_ENTRIES);
            x[i] = finite (pair[0]);
            y[i] = finite (pair[1]);
            if (i > 0 && x[i] <= x[i - 1])
                throw new IllegalArgumentException (TRANSLATION_ENTRIES);
        }

        if (input <= x[0])
            return y[0];
        for (int i = 1; i < x.length; i++)
            if (input <= x[i])
                return y[i - 1] + (input - x[i - 1]) / (x[i] - x[i - 1]) * (y[i] - y[i - 1]);
        return y[y.length - 1];
    }


    /**
     * Set the value of a binding to the attribute of all elements which it targets.
     *
     * @param root The root element of the preset
     * @param binding The binding
     * @param value The translated value
     * @return False if the parameter or the level of the binding is not supported, true if the
     *         value was applied or the binding does not change how the preset sounds
     * @throws IllegalArgumentException The value is not valid for the parameter
     */
    private static boolean applyBinding (final Element root, final Element binding, final String value)
    {
        final String type = binding.getAttribute (BINDING_TYPE);
        final String level = binding.getAttribute (BINDING_LEVEL);
        final String parameter = binding.getAttribute (DecentSamplerTag.BINDING_PARAMETER);
        final boolean isInstrumentOrGroup = LEVEL_INSTRUMENT.equals (level) || LEVEL_GROUP.equals (level);

        final String attribute;
        final List<Element> targets;
        if (LEVEL_TAG.equals (level) && (TYPE_AMP.equals (type) || TYPE_GENERAL.equals (type)))
        {
            attribute = TAG_PARAMETERS.get (parameter);
            if (attribute == null)
                return false;
            targets = getTags (root, binding);
        }
        else
            switch (type)
            {
                case TYPE_AMP, TYPE_GENERAL -> {
                    attribute = getAmpAttribute (parameter, level);
                    if (attribute == null || !isInstrumentOrGroup)
                        return false;
                    targets = getGroupTargets (root, binding, level);
                }
                case TYPE_EFFECT -> {
                    // Only filters and the gain effect are converted, the other effects are
                    // reported by
                    // the detector anyway
                    attribute = EFFECT_PARAMETERS.get (parameter);
                    if (attribute == null)
                        return true;
                    if (!isInstrumentOrGroup)
                        return false;
                    targets = getEffectTargets (root, binding, level);
                }
                case TYPE_MODULATOR -> {
                    attribute = MODULATOR_PARAMETERS.get (parameter);
                    if (attribute == null)
                        return false;
                    final Element modulators = XMLUtils.getChildElementByName (root, DecentSamplerTag.MODULATORS);
                    targets = modulators == null ? Collections.emptyList () : select (XMLUtils.getChildElements (modulators), binding, BINDING_POSITION, MODULATOR_TAGS, true);
                }
                case null, default -> {
                    return true;
                }
            }

        // A binding without a target does not change anything in DecentSampler either
        if (targets.isEmpty ())
            return true;

        final String resolved;
        if (BOOLEAN_ATTRIBUTES.contains (attribute))
            resolved = Boolean.toString (!isFalse (value) && ("true".equalsIgnoreCase (value) || finite (value) != 0));
        else if (TEXT_ATTRIBUTES.contains (attribute) || attribute.endsWith (DecentSamplerTag.OUTPUT_TARGET))
            resolved = value;
        else if (DecentSamplerTag.TAG_POLYPHONY.equals (attribute))
            resolved = Long.toString (Math.round (finite (value)));
        else
            resolved = Double.toString (finite (value));

        for (final Element target: targets)
        {
            target.setAttribute (attribute, resolved);
            // The value of the gain parameter is a linear factor
            if (PARAMETER_LEVEL.equals (parameter))
                target.setAttribute (DecentSamplerTag.EFFECT_LEVEL_UNIT, DecentSamplerTag.LEVEL_UNIT_LINEAR);
        }
        return true;
    }


    /**
     * Get the attribute of the groups or of a group element which an amplitude or general parameter
     * sets.
     *
     * @param parameter The parameter of the binding
     * @param level The level of the binding
     * @return The attribute or null if the parameter is not supported
     */
    private static String getAmpAttribute (final String parameter, final String level)
    {
        final boolean isInstrument = LEVEL_INSTRUMENT.equals (level);
        if (PARAMETER_VOLUME.equals (parameter))
            return isInstrument ? DecentSamplerTag.GLOBAL_VOLUME : DecentSamplerTag.GROUP_VOLUME;
        if (PARAMETER_PAN.equals (parameter))
            return isInstrument ? DecentSamplerTag.GLOBAL_PAN : DecentSamplerTag.GROUP_PAN;
        final Matcher matcher = OUTPUT_PARAMETER.matcher (parameter);
        if (matcher.matches ())
            return DecentSamplerTag.OUTPUT + matcher.group (1) + ("VOLUME".equals (matcher.group (2)) ? DecentSamplerTag.OUTPUT_VOLUME : DecentSamplerTag.OUTPUT_TARGET);
        return AMP_PARAMETERS.get (parameter);
    }


    /**
     * Get the groups element (the instrument level) or the group elements which a binding targets.
     *
     * @param root The root element of the preset
     * @param binding The binding
     * @param level The level of the binding
     * @return The targeted elements
     */
    private static List<Element> getGroupTargets (final Element root, final Element binding, final String level)
    {
        final Element groups = XMLUtils.getChildElementByName (root, DecentSamplerTag.GROUPS);
        if (groups == null)
            return Collections.emptyList ();
        if (LEVEL_INSTRUMENT.equals (level))
            return List.of (groups);
        if (LEVEL_GROUP.equals (level))
            return select (XMLUtils.getChildElementsByName (groups, DecentSamplerTag.GROUP, false), binding, GROUP_INDEX, GROUP_TAGS, true);
        return Collections.emptyList ();
    }


    /**
     * Get the effect elements which a binding targets. The effects of the instrument are selected
     * by the position or the effect index, the effects of groups by the group and the effect index.
     *
     * @param root The root element of the preset
     * @param binding The binding
     * @param level The level of the binding
     * @return The targeted elements
     */
    private static List<Element> getEffectTargets (final Element root, final Element binding, final String level)
    {
        final List<Element> parents;
        if (LEVEL_INSTRUMENT.equals (level))
            parents = List.of (root);
        else if (LEVEL_GROUP.equals (level))
        {
            final Element groups = XMLUtils.getChildElementByName (root, DecentSamplerTag.GROUPS);
            if (groups == null)
                return Collections.emptyList ();
            parents = select (XMLUtils.getChildElementsByName (groups, DecentSamplerTag.GROUP, false), binding, GROUP_INDEX, GROUP_TAGS, false);
        }
        else
            return Collections.emptyList ();

        final List<Element> result = new ArrayList<> ();
        for (final Element parent: parents)
        {
            final Element effects = XMLUtils.getChildElementByName (parent, DecentSamplerTag.EFFECTS);
            if (effects != null)
                result.addAll (select (XMLUtils.getChildElementsByName (effects, DecentSamplerTag.EFFECTS_EFFECT, false), binding, EFFECT_INDEX, EFFECT_TAGS, true));
        }
        return result;
    }


    /**
     * Get the tag elements which a binding on the tag level targets. A tag which is not defined yet
     * is added, since its parameters have default values.
     *
     * @param root The root element of the preset
     * @param binding The binding
     * @return The tag elements
     */
    private static List<Element> getTags (final Element root, final Element binding)
    {
        final String names = binding.getAttribute (binding.hasAttribute (BINDING_IDENTIFIER) ? BINDING_IDENTIFIER : BINDING_TAGS);
        Element tags = XMLUtils.getChildElementByName (root, DecentSamplerTag.TAGS);
        final List<Element> result = new ArrayList<> ();
        for (final String n: names.split (","))
        {
            final String name = n.trim ();
            if (name.isEmpty ())
                continue;
            if (tags == null)
            {
                tags = root.getOwnerDocument ().createElement (DecentSamplerTag.TAGS);
                root.appendChild (tags);
            }
            Element tag = null;
            for (final Element element: XMLUtils.getChildElementsByName (tags, DecentSamplerTag.TAG, false))
                if (name.equals (element.getAttribute (DecentSamplerTag.TAG_NAME).trim ()))
                {
                    tag = element;
                    break;
                }
            if (tag == null)
            {
                tag = root.getOwnerDocument ().createElement (DecentSamplerTag.TAG);
                tag.setAttribute (DecentSamplerTag.TAG_NAME, name);
                tags.appendChild (tag);
            }
            result.add (tag);
        }
        return result;
    }


    /**
     * Select the elements which a binding targets, either by their tags or by their index.
     *
     * @param candidates The elements to select from
     * @param binding The binding
     * @param indexAttribute The attribute of the binding which contains the index
     * @param tagsAttribute The attribute of the binding which contains the tags
     * @param usePosition If true, the position attribute is the index if there is no index
     *            attribute
     * @return The selected elements
     */
    private static List<Element> select (final List<Element> candidates, final Element binding, final String indexAttribute, final String tagsAttribute, final boolean usePosition)
    {
        final String tags = binding.getAttribute (binding.hasAttribute (tagsAttribute) ? tagsAttribute : BINDING_TAGS);
        if (!tags.isBlank ())
        {
            final List<String> names = Arrays.stream (tags.split (",")).map (String::trim).toList ();
            return candidates.stream ().filter (candidate -> Arrays.stream (candidate.getAttribute (DecentSamplerTag.TAGS_ATTRIBUTE).split (",")).map (String::trim).anyMatch (names::contains)).toList ();
        }
        final int index = (int) number (binding, indexAttribute, usePosition ? number (binding, BINDING_POSITION, 0) : 0);
        return index < 0 || index >= candidates.size () ? Collections.emptyList () : List.of (candidates.get (index));
    }


    /**
     * Get a name of a control for the log.
     *
     * @param control The control
     * @param binding The binding of the control, might be null
     * @return The name
     */
    private static String getControlName (final Element control, final Element binding)
    {
        for (final String attribute: CONTROL_NAMES)
        {
            final String name = control.getAttribute (attribute).trim ();
            if (!name.isEmpty ())
                return name;
        }

        // Use the parameter of the (first) binding for a control without a name
        Element namingBinding = binding;
        if (namingBinding == null)
        {
            final NodeList bindings = control.getElementsByTagName (DecentSamplerTag.BINDING);
            if (bindings.getLength () == 0)
                return control.getTagName ();
            namingBinding = (Element) bindings.item (0);
        }
        return namingBinding.getAttribute (DecentSamplerTag.BINDING_PARAMETER);
    }


    /**
     * Test if the value of a boolean attribute is false.
     *
     * @param value The value of the attribute
     * @return True if it is false or 0, false if it is true or not set
     */
    static boolean isFalse (final String value)
    {
        return "false".equalsIgnoreCase (value) || "0".equals (value) || "0.0".equals (value);
    }


    private static double number (final Element element, final String attribute, final double defaultValue)
    {
        return element.hasAttribute (attribute) ? finite (element.getAttribute (attribute)) : defaultValue;
    }


    private static double finite (final String value)
    {
        final double result = Double.parseDouble (value.trim ());
        if (!Double.isFinite (result))
            throw new IllegalArgumentException (value);
        return result;
    }
}
