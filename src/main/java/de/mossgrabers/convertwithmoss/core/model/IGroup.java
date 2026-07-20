// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model;

import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;


/**
 * A group in a multi-sample which groups several sample zones.
 *
 * @author Jürgen Moßgraber
 */
public interface IGroup
{
    /**
     * Get the name of the group.
     *
     * @return The name
     */
    String getName ();


    /**
     * Set the name of the group.
     *
     * @param name The name
     */
    void setName (String name);


    /**
     * Get the event that triggers the play-back of the sample.
     *
     * @return The trigger type
     */
    TriggerType getTrigger ();


    /**
     * Set the event that triggers the play-back of the sample.
     *
     * @param trigger The trigger type
     */
    void setTrigger (TriggerType trigger);


    /**
     * Get the gain of the group. This is an <i>offset</i> which needs to be applied on top of the
     * gain of each sample zone of the group. It does not replace the gain of a sample zone.
     * <p>
     * <b>Important:</b> all detectors flatten the group values into each of their sample zones and
     * must continue to do so. This attribute is only an additional carrier of the information for
     * formats which do have a real group layer. Therefore, a creator must apply either the group
     * value or the (already flattened) zone value but never both, otherwise the gain is applied
     * twice.
     * <p>
     * Known encodings of this attribute are: Kontakt group <code>volume</code>, Logic EXS24 group
     * offset block, Waldorf Quantum/Iridium <code>Osc&lt;N&gt;Vol</code>, DecentSampler group
     * <code>volume</code>, TX16Wx <code>tx:soundshape</code> and the volume of a drum of a
     * Synthstrom Deluge kit.
     *
     * @return The gain offset in dB, 0 if there is no offset
     */
    double getGain ();


    /**
     * Set the gain of the group. This is an offset which needs to be applied on top of the gain of
     * each sample zone of the group, it does not replace it.
     *
     * @param gain The gain offset in dB, 0 for no offset
     */
    void setGain (double gain);


    /**
     * Get the panning of the group. This is an <i>offset</i> which needs to be applied on top of
     * the panning of each sample zone of the group. It does not replace the panning of a sample
     * zone.
     * <p>
     * <b>Important:</b> all detectors flatten the group values into each of their sample zones and
     * must continue to do so. This attribute is only an additional carrier of the information for
     * formats which do have a real group layer. Therefore, a creator must apply either the group
     * value or the (already flattened) zone value but never both, otherwise the panning is applied
     * twice.
     * <p>
     * Known encodings of this attribute are: Kontakt group <code>pan</code>, Logic EXS24 group
     * offset block, Waldorf Quantum/Iridium <code>Osc&lt;N&gt;Pan</code>, DecentSampler group
     * <code>pan</code>, TX16Wx <code>tx:soundshape</code> and the panning of a drum of a Synthstrom
     * Deluge kit.
     *
     * @return The panning offset in the range of [-1..1], -1 is full left, 0 is no offset and 1 is
     *         full right
     */
    double getPanning ();


    /**
     * Set the panning of the group. This is an offset which needs to be applied on top of the
     * panning of each sample zone of the group, it does not replace it.
     *
     * @param panning The panning offset in the range of [-1..1], -1 is full left, 0 is no offset
     *            and 1 is full right
     */
    void setPanning (double panning);


    /**
     * Get the tuning of the group. This is an <i>offset</i> which needs to be applied on top of the
     * tuning of each sample zone of the group. It does not replace the tuning of a sample zone.
     * <p>
     * <b>Important:</b> all detectors flatten the group values into each of their sample zones and
     * must continue to do so. This attribute is only an additional carrier of the information for
     * formats which do have a real group layer. Therefore, a creator must apply either the group
     * value or the (already flattened) zone value but never both, otherwise the tuning is applied
     * twice.
     * <p>
     * Known encodings of this attribute are: Kontakt group <code>tune</code>, Logic EXS24 group
     * offset block, DecentSampler group <code>tuning</code> and TX16Wx <code>tx:soundshape</code>.
     *
     * @return The tuning offset in positive or negative semi-tones, which means that 0.01
     *         represents 1 cent (1 semi-tone is 100 cent), 0 if there is no offset
     */
    double getTuning ();


    /**
     * Set the tuning of the group. This is an offset which needs to be applied on top of the tuning
     * of each sample zone of the group, it does not replace it.
     *
     * @param tuning The tuning offset in positive or negative semi-tones, which means that 0.01
     *            represents 1 cent (1 semi-tone is 100 cent), 0 for no offset
     */
    void setTuning (double tuning);


    /**
     * Get the description of the samples zones which belong to the group.
     *
     * @return The zones in an ordered map
     */
    List<ISampleZone> getSampleZones ();


    /**
     * Set the description of the sample zones which belong to the group.
     *
     * @param sampleZones The sample zones
     */
    void setSampleZones (List<ISampleZone> sampleZones);


    /**
     * Add a sample zone.
     *
     * @param sampleZone The sample zone description
     */
    void addSampleZone (ISampleZone sampleZone);


    /**
     * If all zones in this group are set to play round-robin and contain the same one and only
     * sequence position, it is considered that round-robin happens on a group-level. This means
     * e.g. 3 groups are played round-robin.
     *
     * @return True if round-robin happens on a group-level otherwise round-robin happens inside the
     *         group
     */
    default boolean isGroupRoundRobin ()
    {
        final List<ISampleZone> sampleZones = this.getSampleZones ();
        if (sampleZones.isEmpty ())
            return false;

        int sequencePosition = -1;
        for (final ISampleZone zone: sampleZones)
        {
            if (zone.getPlayLogic () != PlayLogic.ROUND_ROBIN)
                return false;
            if (sequencePosition == -1)
                sequencePosition = zone.getSequencePosition ();
            else if (sequencePosition != zone.getSequencePosition ())
                return false;
        }
        return true;
    }


    /**
     * If all zones in this group are set to play round-robin and have different sequence positions,
     * it is considered that round-robin happens inside the group (and not on a group-level).
     *
     * @return True if round-robin happens on a zone-level and all zones are set to play round-robin
     */
    default boolean isZoneRoundRobin ()
    {
        final List<ISampleZone> sampleZones = this.getSampleZones ();
        if (sampleZones.isEmpty ())
            return false;

        int sequencePosition = -1;
        for (final ISampleZone zone: sampleZones)
        {
            if (zone.getPlayLogic () != PlayLogic.ROUND_ROBIN)
                return false;
            if (sequencePosition == -1)
                sequencePosition = zone.getSequencePosition ();
            else if (sequencePosition == zone.getSequencePosition ())
                return false;
        }
        return true;
    }


    /**
     * If all zones in this group are set to play round-robin.
     *
     * @return True if all sample zones have round-robin enabled
     */
    default boolean isFullRoundRobin ()
    {
        final List<ISampleZone> sampleZones = this.getSampleZones ();
        if (sampleZones.isEmpty ())
            return false;

        for (final ISampleZone zone: sampleZones)
            if (zone.getPlayLogic () != PlayLogic.ROUND_ROBIN)
                return false;
        return true;
    }


    /**
     * If at least one zones in this group is set to play round-robin.
     *
     * @return True if at least one sample zone has round-robin enabled
     */
    default boolean hasRoundRobin ()
    {
        final List<ISampleZone> sampleZones = this.getSampleZones ();
        if (sampleZones.isEmpty ())
            return false;

        for (final ISampleZone zone: sampleZones)
            if (zone.getPlayLogic () == PlayLogic.ROUND_ROBIN)
                return true;
        return false;
    }
}
