// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.AbstractNKIMetadataFileHandler;
import de.mossgrabers.tools.ui.Functions;


/**
 * A group in a Kontakt 5+ program.
 *
 * @author Jürgen Moßgraber
 */
public class Group
{
    /** Start criteria mode: always. */
    public static final int                  START_MODE_ALWAYS                = 0;
    /** Start criteria mode: cycle round robin. */
    public static final int                  START_MODE_CYCLE_ROUND_ROBIN     = 3;
    /** Start criteria mode: cycle random. */
    public static final int                  START_MODE_CYCLE_RANDOM          = 4;

    // Size of one start criteria entry and its version
    static final int                         START_CRITERIA_SIZE              = 34;
    private static final int                 START_CRITERIA_VERSION           = 0x70;
    private static final int                 DEFAULT_FX_INDEX_AMP_SPLIT_POINT = 6;

    // The private data of a group starts with the output routing table (136 entries of 12 bytes,
    // each starting with the value 8), followed by 25 unknown bytes and the insert effects
    private static final int                 ROUTING_ENTRY_SIZE               = 12;
    private static final int                 ROUTING_ENTRY_ID                 = 8;
    private static final int                 INSERT_EFFECTS_OFFSET            = 25;
    private static final int                 NUMBER_OF_INSERT_EFFECTS         = 8;
    // Since Kontakt 8 the number of slots is stored after the version of the insert effects
    private static final int                 INSERT_EFFECTS_VERSION_COUNT     = 0x13;
    private static final int                 EFFECT_TYPE_FILTER               = 7;
    private static final int                 EFFECT_PARAMETERS_FILTER         = 0x18;

    // The parameters of the legacy, SV and AR filters start at this offset, Daft and Ladder
    // filters have an additional value before the cutoff
    private static final int                 FILTER_PARAMETERS_OFFSET         = 11;

    // The filter types of Kontakt 5+
    private static final FilterDefinition [] FILTER_TYPES                     = new FilterDefinition [109];
    static
    {
        // The legacy filters use the names and cutoff curves of the Kontakt 1/2 XML format
        addLegacyFilter (2, "lp1pole");
        addLegacyFilter (3, "hp1pole");
        addLegacyFilter (4, "bp2pole");
        addLegacyFilter (6, "lp2pole");
        addLegacyFilter (7, "hp2pole");
        addLegacyFilter (8, "lp4pole");
        addLegacyFilter (9, "hp4pole");
        addLegacyFilter (10, "bp4pole");
        addLegacyFilter (11, "br4pole");
        addLegacyFilter (12, "lp6pole");
        // Pro 53
        addLegacyFilter (16, "pro52");
        addLegacyFilter (17, "ladder");

        // Ladder filters, MIDI note 20 + 115 * value
        addFilter (30, FilterType.LOW_PASS, 1, 20, 115);
        addFilter (31, FilterType.LOW_PASS, 2, 20, 115);
        addFilter (32, FilterType.LOW_PASS, 3, 20, 115);
        addFilter (33, FilterType.LOW_PASS, 4, 20, 115);
        addFilter (34, FilterType.HIGH_PASS, 1, 20, 115);
        addFilter (35, FilterType.HIGH_PASS, 2, 20, 115);
        addFilter (36, FilterType.HIGH_PASS, 3, 20, 115);
        addFilter (37, FilterType.HIGH_PASS, 4, 20, 115);
        addFilter (38, FilterType.BAND_PASS, 2, 20, 115);
        addFilter (39, FilterType.BAND_PASS, 4, 20, 115);
        // 40 is the Ladder Peak filter which has no equivalent
        addFilter (41, FilterType.BAND_REJECTION, 4, 20, 115);

        // SV filters, MIDI note 20 + 115 * value
        addFilter (50, FilterType.LOW_PASS, 1, 20, 115);
        addFilter (51, FilterType.HIGH_PASS, 1, 20, 115);
        addFilter (52, FilterType.LOW_PASS, 2, 20, 115);
        addFilter (53, FilterType.BAND_PASS, 2, 20, 115);
        addFilter (54, FilterType.HIGH_PASS, 2, 20, 115);
        addFilter (55, FilterType.LOW_PASS, 4, 20, 115);
        addFilter (56, FilterType.BAND_PASS, 4, 20, 115);
        addFilter (57, FilterType.HIGH_PASS, 4, 20, 115);
        addFilter (58, FilterType.BAND_REJECTION, 4, 20, 115);
        addFilter (59, FilterType.LOW_PASS, 6, 20, 115);
        addFilter (60, FilterType.BAND_PASS, 6, 20, 115);
        addFilter (61, FilterType.HIGH_PASS, 6, 20, 115);
        addFilter (62, FilterType.BAND_REJECTION, 6, 20, 115);

        // Daft filters, MIDI note 20 + 125 * value
        addFilter (70, FilterType.LOW_PASS, 2, 20, 125);
        addFilter (71, FilterType.HIGH_PASS, 2, 20, 125);

        // AR filters, MIDI note 145 * value; the 2/4 variants blend between 2 and 4 poles
        addFilter (100, FilterType.LOW_PASS, 2, 0, 145);
        addFilter (101, FilterType.BAND_PASS, 2, 0, 145);
        addFilter (102, FilterType.HIGH_PASS, 2, 0, 145);
        addFilter (103, FilterType.LOW_PASS, 4, 0, 145);
        addFilter (104, FilterType.BAND_PASS, 4, 0, 145);
        addFilter (105, FilterType.HIGH_PASS, 4, 0, 145);
        addFilter (106, FilterType.LOW_PASS, 4, 0, 145);
        addFilter (107, FilterType.BAND_PASS, 4, 0, 145);
        addFilter (108, FilterType.HIGH_PASS, 4, 0, 145);
    }


    /**
     * The definition of a Kontakt 5+ filter type.
     *
     * @param legacyName The name of the filter in the Kontakt 1/2 XML format, null if it is not a
     *            legacy filter
     * @param type The filter type
     * @param poles The number of poles
     * @param noteOffset The MIDI note of the minimum cutoff
     * @param noteRange The range of the cutoff in MIDI notes
     */
    private record FilterDefinition (String legacyName, FilterType type, int poles, double noteOffset, double noteRange)
    {
        /**
         * Create the filter.
         *
         * @param cutoff The normalized cutoff [0..1]
         * @param resonance The resonance [0..1]
         * @return The filter
         */
        Optional<IFilter> createFilter (final double cutoff, final double resonance)
        {
            if (this.legacyName != null)
                return AbstractNKIMetadataFileHandler.createFilter (this.legacyName, cutoff, resonance);
            final double note = this.noteOffset + this.noteRange * Math.clamp (cutoff, 0.0, 1.0);
            final double frequency = 440.0 * Math.pow (2, (note - 69.0) / 12.0);
            return Optional.of (new DefaultFilter (this.type, this.poles, frequency, Math.clamp (resonance, 0.0, 1.0)));
        }
    }


    private static void addLegacyFilter (final int id, final String name)
    {
        FILTER_TYPES[id] = new FilterDefinition (name, null, 0, 0, 0);
    }


    private static void addFilter (final int id, final FilterType type, final int poles, final double noteOffset, final double noteRange)
    {
        FILTER_TYPES[id] = new FilterDefinition (null, type, poles, noteOffset, noteRange);
    }


    private String                        name;
    private float                         volume;
    private float                         pan;
    private float                         tune;
    private boolean                       keyTracking;
    private boolean                       reverse;
    private boolean                       releaseTrigger;
    private boolean                       releaseTriggerNoteMonophonic;
    private boolean                       muted;
    private boolean                       soloed;
    private int                           releaseTriggerCounter;
    private int                           voiceGroupIdx;
    private int                           fxIdxAmpSplitPoint;
    private int                           interpQuality;
    private int                           midiChannel;
    private final List<InternalModulator> internalModulators = new ArrayList<> ();
    private final List<ExternalModulator> externalModulators = new ArrayList<> ();
    private int                           startCriteriaMode  = START_MODE_ALWAYS;
    private int                           cycleClass         = -1;
    private final List<IFilter>           filters            = new ArrayList<> ();


    /**
     * Constructor.
     */
    public Group ()
    {
        // Intentionally empty
    }


    /**
     * Constructor which creates a group for writing.
     *
     * @param name The name of the group
     * @param keyTracking True if the pitch follows the played key
     * @param reverse True to play the samples reversed
     * @param releaseTrigger True if the group is triggered on note release
     * @param voiceGroupIdx The index of the voice group, -1 if none
     */
    public Group (final String name, final boolean keyTracking, final boolean reverse, final boolean releaseTrigger, final int voiceGroupIdx)
    {
        this.name = name;
        this.volume = 1.0f;
        this.pan = 0;
        this.tune = 1.0f;
        this.keyTracking = keyTracking;
        this.reverse = reverse;
        this.releaseTrigger = releaseTrigger;
        this.releaseTriggerNoteMonophonic = true;
        this.releaseTriggerCounter = 0;
        this.midiChannel = -1;
        this.voiceGroupIdx = voiceGroupIdx;
        this.fxIdxAmpSplitPoint = DEFAULT_FX_INDEX_AMP_SPLIT_POINT;
        this.muted = false;
        this.soloed = false;
        this.interpQuality = 0;
    }


    /**
     * Parse the group data.
     *
     * @param groupChunk The data to parse
     * @throws IOException Error parsing the data
     */
    public void parse (final KontaktPresetChunk groupChunk) throws IOException
    {
        final int version = groupChunk.getVersion ();
        if (version > 0x9C)
            throw new IOException (Functions.getMessage ("IDS_NKI5_UNSUPPORTED_GROUP_VERSION", Integer.toHexString (version).toUpperCase ()));

        final ByteArrayInputStream in = new ByteArrayInputStream (groupChunk.getPublicData ());

        this.name = StreamUtils.readUtf16WithLength (in);
        this.volume = StreamUtils.readFloatLE (in);
        this.pan = StreamUtils.readFloatLE (in);
        this.tune = StreamUtils.readFloatLE (in);
        this.keyTracking = in.read () > 0;
        this.reverse = in.read () > 0;
        this.releaseTrigger = in.read () > 0;
        this.releaseTriggerNoteMonophonic = in.read () > 0;
        this.releaseTriggerCounter = StreamUtils.readSigned32 (in, false);
        this.midiChannel = StreamUtils.readSigned16 (in, false);
        this.voiceGroupIdx = StreamUtils.readSigned32 (in, false);
        this.fxIdxAmpSplitPoint = StreamUtils.readSigned32 (in, false);
        this.muted = in.read () > 0;
        this.soloed = in.read () > 0;
        this.interpQuality = StreamUtils.readSigned32 (in, false);

        this.parseModulators (groupChunk.getChildren ());
        this.parseInsertEffects (groupChunk.getPrivateData ());
    }


    /**
     * Parses the insert effects of the group from the private data. Only active (not bypassed)
     * filters are read.
     *
     * @param data The private data of the group
     */
    private void parseInsertEffects (final byte [] data)
    {
        int offset = 0;
        while (offset + ROUTING_ENTRY_SIZE <= data.length && readUnsigned32 (data, offset) == ROUTING_ENTRY_ID)
            offset += ROUTING_ENTRY_SIZE;
        // Older versions have a different structure
        if (offset == 0)
            return;

        offset += INSERT_EFFECTS_OFFSET;
        if (offset + 2 > data.length)
            return;
        final int version = readUnsigned16 (data, offset);
        offset += 2;
        int numberOfSlots = NUMBER_OF_INSERT_EFFECTS;
        if (version >= INSERT_EFFECTS_VERSION_COUNT)
        {
            if (offset + 4 > data.length)
                return;
            numberOfSlots = (int) readUnsigned32 (data, offset);
            offset += 4;
        }

        for (int i = 0; i < numberOfSlots && offset < data.length; i++)
        {
            // Empty slot?
            if (data[offset++] == 0)
                continue;
            if (offset + 6 > data.length || readUnsigned16 (data, offset) != KontaktPresetChunkID.PAR_FX)
                return;
            final int size = (int) readUnsigned32 (data, offset + 2);
            offset += 6;
            if (size < 0 || offset + size > data.length)
                return;
            this.parseFilterEffect (data, offset, size);
            offset += size;
        }
    }


    /**
     * Parses the data of an insert effect. Adds a filter if the effect is an active filter.
     *
     * @param data The data which contains the effect
     * @param offset The offset of the effect data
     * @param size The size of the effect data
     */
    private void parseFilterEffect (final byte [] data, final int offset, final int size)
    {
        // Byte 7: the effect type, 16: the bypass flag, 33: the size of the parameters block (32
        // bit), 37: the ID of the parameters (16 bit), 39: the size of the parameters (32 bit),
        // 43: the parameters
        if (size < 47 || readUnsigned32 (data, offset + 7) != EFFECT_TYPE_FILTER || data[offset + 16] != 0 || readUnsigned16 (data, offset + 37) != EFFECT_PARAMETERS_FILTER)
            return;
        final int parametersOffset = offset + 43;
        final long parametersSize = Math.min (readUnsigned32 (data, offset + 39), (long) size - 43);

        // Filter parameters: 3 bytes: ?, 4 bytes: filter type, the filter type is repeated in the
        // next 4 bytes (Ladder filters have an additional 0 byte before), followed by the
        // parameter values (floats). Daft and Ladder filters have an additional value before the
        // cutoff. The last value is the resonance.
        if (parametersSize < FILTER_PARAMETERS_OFFSET + 8)
            return;
        final long type = readUnsigned32 (data, parametersOffset + 3);
        if (type < 0 || type >= FILTER_TYPES.length || FILTER_TYPES[(int) type] == null)
            return;
        int valuesOffset = FILTER_PARAMETERS_OFFSET;
        if (readUnsigned32 (data, parametersOffset + 7) != type)
        {
            if (parametersSize < FILTER_PARAMETERS_OFFSET + 9 || readUnsigned32 (data, parametersOffset + 8) != type)
                return;
            valuesOffset++;
        }
        final int numberOfValues = (int) ((parametersSize - valuesOffset) / 4);
        final int cutoffOffset = parametersOffset + valuesOffset + (numberOfValues >= 3 ? 4 : 0);
        final float cutoff = Float.intBitsToFloat ((int) readUnsigned32 (data, cutoffOffset));
        final float resonance = Float.intBitsToFloat ((int) readUnsigned32 (data, cutoffOffset + 4));
        if (Float.isNaN (cutoff))
            return;
        final Optional<IFilter> filter = FILTER_TYPES[(int) type].createFilter (cutoff, Float.isNaN (resonance) ? 0 : resonance);
        if (filter.isPresent ())
            this.filters.add (filter.get ());
    }


    private void parseModulators (final List<KontaktPresetChunk> children) throws IOException
    {
        for (final KontaktPresetChunk childChunk: children)
            switch (childChunk.getId ())
            {
                case KontaktPresetChunkID.PARAMETER_ARRAY_16:
                    this.parseEnvelopes (childChunk.getChildren ());
                    break;

                case KontaktPresetChunkID.PARAMETER_ARRAY_32:
                    for (final KontaktPresetChunk kpc: childChunk.getChildren ())
                    {
                        if (kpc.getId () != KontaktPresetChunkID.PAR_EXTERNAL_MOD)
                            continue;
                        final ExternalModulator externalModulator = new ExternalModulator ();
                        externalModulator.read (kpc);
                        this.externalModulators.add (externalModulator);
                    }
                    break;

                case KontaktPresetChunkID.STAR_CRIT_LIST:
                    this.parseStartCriteria (childChunk.getPublicData ());
                    break;

                default:
                    // Not used
                    break;
            }
    }


    /**
     * Parses the start criteria list. Only the cycle modes (round-robin and random) are used.
     *
     * @param data The public data of the start criteria list
     */
    private void parseStartCriteria (final byte [] data)
    {
        if (data.length == 0)
            return;
        // 1 byte: number of start criteria followed by the entries (34 bytes each):
        // 1 byte: ?, 2 bytes: version (0x70), 4 bytes: mode (0 = always, 1 = on key, 2 = on
        // controller, 3 = cycle round robin, 4 = cycle random, 5 = slice trigger), 4 bytes: next
        // criteria, 2 bytes: key min, 2 bytes: key max, 4 bytes: controller, 2 bytes: ?, 2 bytes:
        // cycle class (the zero based position in the round robin cycle), ...
        final int count = data[0] & 0xFF;
        for (int i = 0; i < count; i++)
        {
            final int offset = 1 + i * START_CRITERIA_SIZE;
            if (offset + START_CRITERIA_SIZE > data.length || readUnsigned16 (data, offset + 1) != START_CRITERIA_VERSION)
                return;
            final int mode = (int) readUnsigned32 (data, offset + 3);
            if (mode == START_MODE_CYCLE_ROUND_ROBIN || mode == START_MODE_CYCLE_RANDOM)
            {
                this.startCriteriaMode = mode;
                this.cycleClass = readUnsigned16 (data, offset + 21);
                return;
            }
        }
    }


    private static int readUnsigned16 (final byte [] data, final int offset)
    {
        return data[offset] & 0xFF | (data[offset + 1] & 0xFF) << 8;
    }


    private static long readUnsigned32 (final byte [] data, final int offset)
    {
        return readUnsigned16 (data, offset) | (long) readUnsigned16 (data, offset + 2) << 16;
    }


    private void parseEnvelopes (final List<KontaktPresetChunk> children)
    {
        for (final KontaktPresetChunk childChunk: children)
        {
            final int id = childChunk.getId ();
            if (id == KontaktPresetChunkID.PAR_INTERNAL_MOD || id == KontaktPresetChunkID.PAR_MOD_BASE)
            {
                final InternalModulator internalModulator = new InternalModulator ();
                try
                {
                    internalModulator.read (childChunk);
                    this.internalModulators.add (internalModulator);
                }
                catch (final RuntimeException | IOException _)
                {
                    // Ignore unknown formats but don't crash
                }
            }
        }
    }


    /**
     * Create a public data block from the group data.
     *
     * @param version The version of the group structure
     * @return The created data
     * @throws IOException Error creating the data
     */
    public byte [] create (final int version) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();

        StreamUtils.writeUtf16WithLength (out, this.name);
        StreamUtils.writeFloatLE (out, this.volume);
        StreamUtils.writeFloatLE (out, this.pan);
        StreamUtils.writeFloatLE (out, this.tune);
        out.write (this.keyTracking ? 1 : 0);
        out.write (this.reverse ? 1 : 0);
        out.write (this.releaseTrigger ? 1 : 0);
        out.write (this.releaseTriggerNoteMonophonic ? 1 : 0);
        StreamUtils.writeSigned32 (out, this.releaseTriggerCounter, false);
        StreamUtils.writeSigned16 (out, this.midiChannel, false);
        StreamUtils.writeSigned32 (out, this.voiceGroupIdx, false);
        StreamUtils.writeSigned32 (out, this.fxIdxAmpSplitPoint, false);
        out.write (this.muted ? 1 : 0);
        out.write (this.soloed ? 1 : 0);
        StreamUtils.writeSigned32 (out, this.interpQuality, false);

        return out.toByteArray ();
    }


    /**
     * Get the mode of the first start criteria which is a cycle mode.
     *
     * @return START_MODE_CYCLE_ROUND_ROBIN, START_MODE_CYCLE_RANDOM or START_MODE_ALWAYS if there
     *         is no such start criteria
     */
    public int getStartCriteriaMode ()
    {
        return this.startCriteriaMode;
    }


    /**
     * Get the cycle class of the round-robin start criteria.
     *
     * @return The zero based position in the round-robin cycle, -1 if not set
     */
    public int getCycleClass ()
    {
        return this.cycleClass;
    }


    /**
     * Get the name of the group.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the volume of the group.
     *
     * @return The volume, see Zone
     */
    public float getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the panning of the group.
     *
     * @return The panning, see Zone
     */
    public float getPan ()
    {
        return this.pan;
    }


    /**
     * Get the tuning of the group.
     *
     * @return The tuning, see Zone
     */
    public float getTune ()
    {
        return this.tune;
    }


    /**
     * Is key tracking enabled?
     *
     * @return True if enabled
     */
    public boolean isKeyTracking ()
    {
        return this.keyTracking;
    }


    /**
     * Is reverse playback enabled.
     *
     * @return True if enabled
     */
    public boolean isReverse ()
    {
        return this.reverse;
    }


    /**
     * Is triggered on release?
     *
     * @return True if triggered on release
     */
    public boolean isReleaseTrigger ()
    {
        return this.releaseTrigger;
    }


    /**
     * Is the release trigger monophonic?
     *
     * @return True if monophonic
     */
    public boolean isReleaseTriggerNoteMonophonic ()
    {
        return this.releaseTriggerNoteMonophonic;
    }


    /**
     * Is the group muted?
     *
     * @return True if muted
     */
    public boolean isMuted ()
    {
        return this.muted;
    }


    /**
     * Is the group soloed?
     *
     * @return True if soloed
     */
    public boolean isSoloed ()
    {
        return this.soloed;
    }


    /**
     * Value of the release trigger counter.
     *
     * @return The value
     */
    public int getRlsTrigCounter ()
    {
        return this.releaseTriggerCounter;
    }


    /**
     * The index of the voice group.
     *
     * @return The voice group index, -1 if not set
     */
    public int getVoiceGroupIdx ()
    {
        return this.voiceGroupIdx;
    }


    /**
     * Get the FX index amplitude split point.
     *
     * @return The split point
     */
    public int getFxIdxAmpSplitPoint ()
    {
        return this.fxIdxAmpSplitPoint;
    }


    /**
     * Get the interpolation quality.
     *
     * @return The interpolation quality
     */
    public int getInterpQuality ()
    {
        return this.interpQuality;
    }


    /**
     * Get the MIDI channel.
     *
     * @return The MIDI channel, -1 if not set
     */
    public int getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Get the internal modulators.
     *
     * @return The modulators
     */
    public List<InternalModulator> getInternalModulators ()
    {
        return this.internalModulators;
    }


    /**
     * Get the external modulators.
     *
     * @return The modulators
     */
    public List<ExternalModulator> getExternalModulators ()
    {
        return this.externalModulators;
    }


    /**
     * Get the filter of the group. Several filters can be chained in the insert effects. Since only
     * one filter is supported, a low-pass is preferred since it has the most influence on the
     * sound, otherwise the first one is used.
     *
     * @return The filter if the group contains an active filter
     */
    public Optional<IFilter> getFilter ()
    {
        for (final IFilter filter: this.filters)
            if (filter.getType () == FilterType.LOW_PASS)
                return Optional.of (filter);
        return this.filters.isEmpty () ? Optional.empty () : Optional.of (this.filters.get (0));
    }
}
