// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelopeModulator;


/**
 * Helper class to read and store all envelopes and filter settings.
 *
 * @author Jürgen Moßgraber
 */
public class MPCEnvelopesAndFilter
{
    private static final String VALUE0                  = "value0";

    private IFilter             filter                  = null;
    private IEnvelopeModulator  ampEnvelopeModulator    = null;
    private IEnvelopeModulator  filterEnvelopeModulator = null;
    private IEnvelopeModulator  pitchEnvelopeModulator  = null;


    /**
     * Constructor.
     *
     * @param synthSectionNode THe synthesizer section node
     * @param isGlobal True if it is a global envelope
     */
    public MPCEnvelopesAndFilter (final JsonNode synthSectionNode, final boolean isGlobal)
    {
        if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "ampEnvelopeGlobal", false))
        {
            final Optional<IEnvelope> ampEnvelopeOpt = parseEnvelope (synthSectionNode, "ampEnvelope");
            if (ampEnvelopeOpt.isPresent ())
            {
                this.ampEnvelopeModulator = new DefaultEnvelopeModulator (1);
                this.ampEnvelopeModulator.setSource (ampEnvelopeOpt.get ());
            }
        }
        if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "pitchEnvelopeGlobal", false))
        {
            final Optional<IEnvelope> pitchEnvelopeOpt = parseEnvelope (synthSectionNode, "pitchEnvelope");
            if (pitchEnvelopeOpt.isPresent ())
            {
                final double pitchEnvelopeAmount = synthSectionNode.get ("pitchEnvelopeAmount").asDouble ();
                this.pitchEnvelopeModulator = new DefaultEnvelopeModulator (pitchEnvelopeAmount * 2.0 - 1.0);
                this.pitchEnvelopeModulator.setSource (pitchEnvelopeOpt.get ());
            }
        }

        if (!isGlobal || getEnvelopeAttributeAsBoolean (synthSectionNode, "filterEnvelopeGlobal", false))
        {
            final JsonNode filterDataNode = synthSectionNode.get ("filterData");
            if (filterDataNode == null)
                return;

            final JsonNode valueNode = filterDataNode.get (VALUE0);
            final int filterID = valueNode.get ("filterType").asInt ();
            if (filterID <= 0)
                return;

            final Optional<IEnvelope> filterEnvelopeOpt = parseEnvelope (synthSectionNode, "filterEnvelope");
            if (filterEnvelopeOpt.isPresent ())
            {
                // Filter envelope depth will be set below...
                this.filterEnvelopeModulator = new DefaultEnvelopeModulator (1);
                this.filterEnvelopeModulator.setSource (filterEnvelopeOpt.get ());
            }

            final double cutoff = valueNode.get ("filterCutoff").asDouble ();
            final double resonance = valueNode.get ("filterResonance").asDouble ();
            this.filter = new MPCFilter (filterID, cutoff, resonance);
            if (this.filter.getType () == null)
                return;

            final double filterAmount = valueNode.get ("filterEnvelopeAmount").asDouble ();
            if (filterAmount > 0 && this.filterEnvelopeModulator != null)
            {
                final IEnvelopeModulator cutoffModulator = this.filter.getCutoffEnvelopeModulator ();
                cutoffModulator.setDepth (filterAmount);
                cutoffModulator.getSource ().set (this.filterEnvelopeModulator.getSource ());
            }

            final double filterCutoffVelocityAmount = valueNode.get ("filterVelocity").asDouble ();
            if (filterCutoffVelocityAmount > 0)
                this.filter.getCutoffVelocityModulator ().setDepth (filterCutoffVelocityAmount);

            this.filter.setCutoffKeyTracking (valueNode.get ("filterKeytrack").asDouble ());
        }
    }


    /**
     * @return the filter
     */
    public IFilter getFilter ()
    {
        return this.filter;
    }


    /**
     * @return the ampEnvelopeModulator
     */
    public IEnvelopeModulator getAmpEnvelopeModulator ()
    {
        return this.ampEnvelopeModulator;
    }


    /**
     * @return the filterEnvelopeModulator
     */
    public IEnvelopeModulator getFilterEnvelopeModulator ()
    {
        return this.filterEnvelopeModulator;
    }


    /**
     * @return the pitchEnvelopeModulator
     */
    public IEnvelopeModulator getPitchEnvelopeModulator ()
    {
        return this.pitchEnvelopeModulator;
    }


    private static Optional<IEnvelope> parseEnvelope (final JsonNode synthSectionElement, final String envelopeName)
    {
        final JsonNode envelopeElement = synthSectionElement.get (envelopeName);
        if (envelopeElement == null || getEnvelopeAttributeAsBoolean (envelopeElement, "OneShot", false))
            return Optional.empty ();

        final IEnvelope envelope = new DefaultEnvelope ();

        if (getEnvelopeAttributeAsBoolean (envelopeElement, "AD", false))
            envelope.setSustainLevel (0);
        else
        {
            envelope.setDelayTime (getEnvelopeAttributeAsDouble (envelopeElement, "Delay", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
            envelope.setHoldTime (getEnvelopeAttributeAsDouble (envelopeElement, "Hold", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
            envelope.setSustainLevel (getEnvelopeAttributeAsDouble (envelopeElement, "Sustain", 0, 1, 1, false));
        }

        envelope.setAttackTime (getEnvelopeAttributeAsDouble (envelopeElement, "Attack", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setDecayTime (getEnvelopeAttributeAsDouble (envelopeElement, "Decay", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0, true));
        envelope.setAttackSlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "AttackCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setDecaySlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "DecayCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        envelope.setReleaseTime (getEnvelopeAttributeAsDouble (envelopeElement, "Release", MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS, 0.63, true));
        envelope.setReleaseSlope (Math.clamp (getEnvelopeAttributeAsDouble (envelopeElement, "ReleaseCurve", 0.5, 0, 1, false) * 2.0 - 1.0, -1.0, 1.0));
        return Optional.of (envelope);
    }


    private static boolean getEnvelopeAttributeAsBoolean (final JsonNode node, final String attribute, final boolean defaultValue)
    {
        final JsonNode attributeNode = node.get (attribute);
        if (attributeNode == null)
            return defaultValue;
        final JsonNode valueNode = attributeNode.get (VALUE0);
        return valueNode == null ? defaultValue : valueNode.asBoolean ();
    }


    private static double getEnvelopeAttributeAsDouble (final JsonNode node, final String attribute, final double minimum, final double maximum, final double defaultValue, final boolean logarithmic)
    {
        final JsonNode attributeNode = node.get (attribute);
        if (attributeNode == null)
            return defaultValue;
        final JsonNode valueNode = attributeNode.get (VALUE0);
        if (valueNode == null)
            return defaultValue;
        final double value = valueNode.asDouble ();
        return logarithmic ? denormalizeLogarithmicEnvTimeValue (value, minimum, maximum) : MathUtils.denormalizeValue (value, minimum, maximum);
    }


    private static double denormalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return minimum * Math.exp (Math.clamp (value, 0, 1) * Math.log (maximum / minimum));
    }
}
