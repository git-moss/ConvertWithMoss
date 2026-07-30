// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.mpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.ui.Functions;


/**
 * Builds the JSON document of a MPC 3 track file (*.xty) from a template which carries the full
 * 'SerialisableTrackData' structure of a real key-group track written by MPC firmware 3.7. The
 * firmware serializes its complete C++ object tree - the track, the program with its mixer and
 * macro assignments, the key-group performance controls and all 128 instrument slots with their
 * eight layers - and its reader is strict about the shape, so the writer keeps every field of the
 * template and only patches the ones which carry the multi-sample: the names, the key-group count
 * and pitch bend ranges, the used instrument slots (key range, envelopes, filter, layers) and the
 * sample list. Unused slots are clones of the template's neutral instrument.
 * <p>
 * The encodings mirror the real files: envelope parameters are wrapped in 'value0' objects with
 * times normalized logarithmically between 1 millisecond and 100 seconds, curves and the panorama
 * are stored in the range of [0..1], layer volumes are objects holding the gain coefficient, the
 * key-group pitch bend range is stored both as a fraction of an octave and as semitone integers,
 * and the root note of a sample lives in the metadata of its sample list entry.
 *
 * @author Jürgen Moßgraber
 */
public class MPC3TrackFile
{
    /** The format identifier in the first line of the file header. */
    public static final String  HEADER_MAGIC      = "ACVS";
    /** The data type identifier in the third line of the file header. */
    public static final String  HEADER_DATA_TYPE  = "SerialisableTrackData";
    /** The encoding identifier in the fourth line of the file header. */
    public static final String  HEADER_ENCODING   = "json";
    /** The suffix of the folder which holds the samples of a track. */
    public static final String  TRACK_DATA_SUFFIX = "_[TrackData]";

    private static final String TEMPLATE          = "de/mossgrabers/convertwithmoss/templates/mpc/keygroup-track-template.json";
    private static final String VALUE0            = "value0";
    /** A track file always carries 128 instrument slots. */
    private static final int    NUM_INSTRUMENTS   = 128;

    private final ObjectMapper  mapper            = new ObjectMapper ();
    private final int           layerLimit;


    /**
     * Constructor.
     *
     * @param layerLimit The maximum number of layers of one key-group (4, or 8 from firmware 3.4)
     */
    public MPC3TrackFile (final int layerLimit)
    {
        this.layerLimit = layerLimit;
    }


    /**
     * Create the JSON document of the track.
     *
     * @param multisampleSource The multi-sample to store
     * @param sampleName The name of the multi-sample, which prefixes the sample files
     * @return The JSON document and the number of created key-groups
     * @throws IOException Could not load or parse the template
     */
    public TrackDocument create (final IMultisampleSource multisampleSource, final String sampleName) throws IOException
    {
        final ObjectNode root = (ObjectNode) this.mapper.readTree (Functions.textFileFor (TEMPLATE));
        final ObjectNode dataNode = (ObjectNode) root.get ("data");
        final String name = multisampleSource.getName ();
        dataNode.put ("name", name);
        final ObjectNode programNode = (ObjectNode) dataNode.get ("program");
        programNode.put ("name", name);

        // The prototypes for the instrument slots and the sample list entries
        final ObjectNode drumNode = (ObjectNode) programNode.get ("drum");
        final ArrayNode instrumentsNode = (ArrayNode) drumNode.get ("instruments");
        final ObjectNode instrumentPrototype = instrumentsNode.get (0).deepCopy ();
        instrumentsNode.removeAll ();
        final ArrayNode samplesNode = (ArrayNode) dataNode.get ("samples");
        final ObjectNode samplePrototype = samplesNode.get (0).deepCopy ();
        samplesNode.removeAll ();

        // Stack the zones of all groups into key-groups by their key range
        final Map<String, List<List<ISampleZone>>> keygroupsMap = new LinkedHashMap<> ();
        final List<List<ISampleZone>> keygroups = new ArrayList<> ();
        final List<ISampleZone> allZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (false))
            for (final ISampleZone zone: group.getSampleZones ())
            {
                // Add the name of the multi-sample to the wave file to make it 'more unique' if
                // necessary
                String zoneName = zone.getName ();
                if (!zoneName.startsWith (sampleName))
                {
                    zoneName = sampleName + "_" + zoneName;
                    zone.setName (zoneName);
                }
                allZones.add (zone);

                final String rangeKey = limitToDefault (zone.getKeyLow (), 0) + "-" + limitToDefault (zone.getKeyHigh (), 127);
                final List<List<ISampleZone>> stacks = keygroupsMap.computeIfAbsent (rangeKey, _ -> new ArrayList<> ());
                List<ISampleZone> stack = stacks.isEmpty () ? null : stacks.get (stacks.size () - 1);
                if (stack == null || stack.size () >= this.layerLimit)
                {
                    stack = new ArrayList<> ();
                    stacks.add (stack);
                    keygroups.add (stack);
                }
                stack.add (zone);
            }
        final int numKeygroups = Math.min (keygroups.size (), NUM_INSTRUMENTS);

        // The key-group node holds the zone count and the pitch bend range; the range is stored
        // both as a fraction of an octave and as semitone integers
        final ObjectNode keygroupNode = (ObjectNode) programNode.get ("keygroup");
        keygroupNode.put ("numKeygroups", Math.max (1, numKeygroups));
        int bendUpCents = 200;
        int bendDownCents = 200;
        if (!allZones.isEmpty ())
        {
            final ISampleZone firstZone = allZones.get (0);
            if (firstZone.getBendUp () != 0)
                bendUpCents = Math.abs (firstZone.getBendUp ());
            if (firstZone.getBendDown () != 0)
                bendDownCents = Math.abs (firstZone.getBendDown ());
        }
        final int bendUpSemitones = Math.max (1, Math.round (bendUpCents / 100f));
        final int bendDownSemitones = Math.max (1, Math.round (bendDownCents / 100f));
        keygroupNode.put ("pitchBendRange", bendUpSemitones / 12.0);
        keygroupNode.put ("pitchBendPositiveRange", bendUpSemitones);
        keygroupNode.put ("pitchBendNegativeRange", bendDownSemitones);

        // The used instrument slots carry the key-groups, the rest stays at the neutral prototype
        for (int i = 0; i < NUM_INSTRUMENTS; i++)
        {
            final ObjectNode instrumentNode = instrumentPrototype.deepCopy ();
            if (i < numKeygroups)
                patchInstrument (instrumentNode, keygroups.get (i));
            instrumentsNode.add (instrumentNode);
        }

        // The sample list with the metadata which the MPC keeps for each sample. Zones of
        // different velocity layers may share one sample; the first zone provides the metadata
        final Map<String, ObjectNode> sampleEntries = new LinkedHashMap<> ();
        for (final ISampleZone zone: allZones)
            sampleEntries.computeIfAbsent (zone.getName (), zoneName -> {
                final ObjectNode sampleNode = samplePrototype.deepCopy ();
                sampleNode.put ("name", zoneName);
                sampleNode.put ("path", zoneName + ".WAV");
                final ObjectNode metadataNode = (ObjectNode) sampleNode.get ("metadata");
                metadataNode.put ("rootNote", limitToDefault (zone.getKeyRoot (), limitToDefault (zone.getKeyLow (), 0)));
                metadataNode.put ("tune", 0.0);
                samplesNode.add (sampleNode);
                return sampleNode;
            });

        return new TrackDocument (root.toPrettyString (), numKeygroups);
    }


    /**
     * Patch one instrument slot with the zones of one key-group.
     *
     * @param instrumentNode The instrument node to patch
     * @param zones The zones which are stacked as the layers of the key-group
     */
    private static void patchInstrument (final ObjectNode instrumentNode, final List<ISampleZone> zones)
    {
        final ISampleZone firstZone = zones.get (0);
        instrumentNode.put ("lowNote", limitToDefault (firstZone.getKeyLow (), 0));
        instrumentNode.put ("highNote", limitToDefault (firstZone.getKeyHigh (), 127));
        instrumentNode.put ("ignoreBaseNote", firstZone.getKeyTracking () == 0);
        // 0 is one-shot, 1 is note-off and 2 is note-on
        instrumentNode.put ("triggerMode", firstZone.isOneShot () ? 0 : 2);

        patchSynthSection ((ObjectNode) instrumentNode.get ("synthSection"), firstZone);

        final ArrayNode layersNode = (ArrayNode) instrumentNode.get ("layersv");
        for (int i = 0; i < zones.size () && i < layersNode.size (); i++)
            patchLayer ((ObjectNode) layersNode.get (i), zones.get (i));
    }


    /**
     * Patch one layer with the values of a sample zone.
     *
     * @param layerNode The layer node to patch
     * @param zone The sample zone
     */
    private static void patchLayer (final ObjectNode layerNode, final ISampleZone zone)
    {
        layerNode.put ("active", true);
        layerNode.put ("sampleName", zone.getName ());
        layerNode.put ("sampleFile", zone.getName () + ".WAV");

        // The volume of a layer is an object; the gain coefficient is the linear amplitude
        final double gainCoefficient = Math.pow (10, Math.min (zone.getGain (), 6) / 20.0);
        final ObjectNode volumeNode = (ObjectNode) layerNode.get ("volume");
        volumeNode.put ("gainCoefficient", gainCoefficient);
        volumeNode.put ("controlValue", gainCoefficient);

        layerNode.put ("pan", (Math.clamp (zone.getPanning (), -1.0d, 1.0d) + 1.0d) / 2.0d);

        final double tuneSemitones = zone.getTuning ();
        final int coarseTune = (int) Math.round (tuneSemitones);
        layerNode.put ("pitch", tuneSemitones);
        layerNode.put ("coarseTune", coarseTune);
        layerNode.put ("fineTune", (int) Math.round ((tuneSemitones - coarseTune) * 100.0));

        layerNode.put ("velocityStart", limitToDefault (zone.getVelocityLow (), 1));
        layerNode.put ("velocityEnd", limitToDefault (zone.getVelocityHigh (), 127));
        layerNode.put ("sampleStart", zone.getStart ());
        layerNode.put ("sampleEnd", zone.getStop ());
        layerNode.put ("direction", zone.isReversed () ? 1 : 0);
        layerNode.put ("keyTrackEnable", zone.getKeyTracking () != 0);

        final List<ISampleLoop> loops = zone.getLoops ();
        final ObjectNode sliceInfoNode = (ObjectNode) layerNode.get ("sliceInfo");
        sliceInfoNode.put ("Start", zone.getStart ());
        sliceInfoNode.put ("End", zone.getStop ());
        if (loops.isEmpty ())
            return;

        // The format can store only 1 loop; the layer settings override the (unused) slice
        final ISampleLoop loop = loops.get (0);
        final int loopCrossfade = (int) Math.floor (loop.getCrossfade () * loop.getLength ());
        layerNode.put ("loop", true);
        layerNode.put ("layerLoopModeOverridesSliceLoopMode", true);
        layerNode.put ("loopMode", 1);
        layerNode.put ("loopStart", loop.getStart ());
        layerNode.put ("loopEnd", loop.getEnd ());
        layerNode.put ("loopCrossfadeLength", loopCrossfade);
        sliceInfoNode.put ("LoopMode", 1);
        sliceInfoNode.put ("LoopStart", loop.getStart ());
        sliceInfoNode.put ("LoopCrossfadeLength", loopCrossfade);
    }


    /**
     * Patch the synthesizer section of an instrument slot with the envelopes and the filter of a
     * zone.
     *
     * @param synthNode The synthesizer section node to patch
     * @param zone The sample zone
     */
    private static void patchSynthSection (final ObjectNode synthNode, final ISampleZone zone)
    {
        patchEnvelope ((ObjectNode) synthNode.get ("ampEnvelope"), zone.getAmplitudeEnvelopeModulator ().getSource ());

        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double pitchDepth = pitchModulator.getDepth ();
        if (pitchDepth != 0)
        {
            patchEnvelope ((ObjectNode) synthNode.get ("pitchEnvelope"), pitchModulator.getSource ());
            synthNode.put ("pitchEnvelopeAmount", Math.clamp (pitchDepth / 2.0 + 0.5, 0, 1));
        }

        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty ())
            return;
        final IFilter filter = optFilter.get ();
        final ObjectNode filterValueNode = (ObjectNode) synthNode.get ("filterData").get (VALUE0);
        filterValueNode.put ("filterType", MPCFilter.getFilterIndex (filter));
        filterValueNode.put ("filterCutoff", MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY));
        filterValueNode.put ("filterResonance", filter.getResonance ());
        filterValueNode.put ("filterKeytrack", filter.getCutoffKeyTracking ());
        final double filterCutoffVelocityAmount = filter.getCutoffVelocityModulator ().getDepth ();
        if (filterCutoffVelocityAmount > 0)
            filterValueNode.put ("filterVelocity", filterCutoffVelocityAmount);

        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
        final double envelopeDepth = cutoffModulator.getDepth ();
        // Only positive modulation values are supported with MPC
        if (envelopeDepth > 0)
        {
            filterValueNode.put ("filterEnvelopeAmount", envelopeDepth);
            patchEnvelope ((ObjectNode) synthNode.get ("filterEnvelope"), cutoffModulator.getSource ());
        }
    }


    /**
     * Patch an envelope node. Only the parameters which the model carries are overwritten, the rest
     * keeps the values of the template. All parameters are wrapped in 'value0' objects, the times
     * are normalized logarithmically.
     *
     * @param envelopeNode The envelope node to patch
     * @param envelope The envelope values to set
     */
    private static void patchEnvelope (final ObjectNode envelopeNode, final IEnvelope envelope)
    {
        setWrappedValue (envelopeNode, "AD", false);
        setWrappedValue (envelopeNode, "OneShot", false);
        setWrappedTime (envelopeNode, "Delay", envelope.getDelayTime (), 0);
        setWrappedTime (envelopeNode, "Attack", envelope.getAttackTime (), MPCKeygroupConstants.DEFAULT_ATTACK_TIME);
        setWrappedTime (envelopeNode, "Hold", envelope.getHoldTime (), MPCKeygroupConstants.DEFAULT_HOLD_TIME);
        setWrappedTime (envelopeNode, "Decay", envelope.getDecayTime (), MPCKeygroupConstants.DEFAULT_DECAY_TIME);
        setWrappedValue (envelopeNode, "Sustain", Math.clamp (envelope.getSustainLevel () < 0 ? 1 : envelope.getSustainLevel (), 0, 1));
        setWrappedTime (envelopeNode, "Release", envelope.getReleaseTime (), MPCKeygroupConstants.DEFAULT_RELEASE_TIME);
        setWrappedValue (envelopeNode, "AttackCurve", Math.clamp ((envelope.getAttackSlope () + 1.0) / 2.0, 0, 1));
        setWrappedValue (envelopeNode, "DecayCurve", Math.clamp ((envelope.getDecaySlope () + 1.0) / 2.0, 0, 1));
        setWrappedValue (envelopeNode, "ReleaseCurve", Math.clamp ((envelope.getReleaseSlope () + 1.0) / 2.0, 0, 1));
    }


    private static void setWrappedTime (final ObjectNode node, final String name, final double time, final double normalizedDefault)
    {
        setWrappedValue (node, name, time < 0 ? normalizedDefault : normalizeLogarithmicEnvTimeValue (time, MPCKeygroupConstants.MIN_ENV_TIME_SECONDS, MPCKeygroupConstants.MAX_ENV_TIME_SECONDS));
    }


    private static void setWrappedValue (final ObjectNode node, final String name, final double value)
    {
        final JsonNode wrapper = node.get (name);
        if (wrapper instanceof final ObjectNode wrapperObject)
            wrapperObject.put (VALUE0, value);
    }


    private static void setWrappedValue (final ObjectNode node, final String name, final boolean value)
    {
        final JsonNode wrapper = node.get (name);
        if (wrapper instanceof final ObjectNode wrapperObject)
            wrapperObject.put (VALUE0, value);
    }


    /**
     * Computes a normalized logarithmic value between 0 and 1 from a value and a given range.
     *
     * @param value The value (e.g. duration)
     * @param minimum The minimum value (must be greater than zero)
     * @param maximum The maximum value
     * @return The normalized logarithmic value
     */
    private static double normalizeLogarithmicEnvTimeValue (final double value, final double minimum, final double maximum)
    {
        return Math.log (Math.clamp (value, minimum, maximum) / minimum) / Math.log (maximum / minimum);
    }


    private static int limitToDefault (final int value, final int defaultValue)
    {
        return value < 0 ? defaultValue : value;
    }


    /**
     * The created JSON document and the number of key-groups it holds.
     * 
     * @param json The JSON code
     * @param numKeygroups The number of key-groups
     */
    public record TrackDocument (String json, int numKeygroups)
    {
        // Intentionally empty
    }
}
