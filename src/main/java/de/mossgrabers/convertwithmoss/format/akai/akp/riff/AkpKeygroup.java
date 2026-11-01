// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultLfo;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.format.akai.AkaiEnvelopeTime;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpModulations.Slot;


/**
 * A S5000/S6000 key-group chunk. It contains 9 sub-chunks: the location, the amplitude, filter and
 * auxiliary envelopes, the filter and 4 velocity zones.
 *
 * @author Jürgen Moßgraber
 */
public class AkpKeygroup extends AbstractSpecificRIFFChunk
{
    // 0 = 2-POLE LP, 1 = 4-POLE LP, 2 = 2-POLE LP+, 3 = 2-POLE BP, 4 = 4-POLE BP, 5 = 2-POLE
    // BP+, 6 = 1-POLE HP, 7 = 2-POLE HP, 8 = 1-POLE HP+, 9 = LO<>HI, 10 = LO<>BAND, 11 =
    // BAND<>HI, 12 = NOTCH 1, 13 = NOTCH 2, 14 = NOTCH 3, 15 = WIDE NOTCH, 16 = BI-NOTCH, 17 =
    // PEAK 1, 18 = PEAK 2, 19 = PEAK 3, 20 = WIDE PEAK, 21 = BI-PEAK, 22 = PHASER 1, 23 =
    // PHASER 2, 24 = BI-PHASE, 25 = VOWELISER
    private static final Map<Integer, FilterType> FILTER_TYPES = new HashMap<> ();
    private static final Map<Integer, Integer>    FILTER_POLES = new HashMap<> ();
    static
    {
        FILTER_TYPES.put (Integer.valueOf (0), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (1), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (2), FilterType.LOW_PASS);
        FILTER_TYPES.put (Integer.valueOf (3), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (4), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (5), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (6), FilterType.HIGH_PASS);
        FILTER_TYPES.put (Integer.valueOf (7), FilterType.HIGH_PASS);
        FILTER_TYPES.put (Integer.valueOf (8), FilterType.HIGH_PASS);
        FILTER_TYPES.put (Integer.valueOf (12), FilterType.BAND_REJECTION);
        FILTER_TYPES.put (Integer.valueOf (13), FilterType.BAND_REJECTION);
        FILTER_TYPES.put (Integer.valueOf (14), FilterType.BAND_REJECTION);
        FILTER_TYPES.put (Integer.valueOf (15), FilterType.BAND_REJECTION);
        FILTER_TYPES.put (Integer.valueOf (16), FilterType.BAND_REJECTION);
        // The peak filters boost a band around the cutoff, the closest match is a band-pass
        FILTER_TYPES.put (Integer.valueOf (17), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (18), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (19), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (20), FilterType.BAND_PASS);
        FILTER_TYPES.put (Integer.valueOf (21), FilterType.BAND_PASS);

        FILTER_POLES.put (Integer.valueOf (0), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (1), Integer.valueOf (4));
        FILTER_POLES.put (Integer.valueOf (2), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (3), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (4), Integer.valueOf (4));
        FILTER_POLES.put (Integer.valueOf (5), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (6), Integer.valueOf (1));
        FILTER_POLES.put (Integer.valueOf (7), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (8), Integer.valueOf (1));
        // The notch filters differ in their bandwidth, not in the number of poles
        FILTER_POLES.put (Integer.valueOf (12), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (13), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (14), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (15), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (16), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (17), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (18), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (19), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (20), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (21), Integer.valueOf (2));
    }

    // The modulation depths of the maximum LFO depth and modulation amount as well as the range of
    // the LFO rate are not documented, the following values are assumptions

    /** The pitch modulation of the maximum LFO depth and amount in cents (1 octave). */
    private static final double       LFO_PITCH_FULL_DEPTH_CENTS  = 1200;
    /** The cutoff modulation of the maximum LFO depth and amount in cents (4 octaves). */
    private static final double       LFO_CUTOFF_FULL_DEPTH_CENTS = 4800;
    /** The amplitude modulation of the maximum LFO depth and amount in dB. */
    private static final double       LFO_AMPLITUDE_FULL_DEPTH_DB = 12;
    /** The lowest LFO rate in Hertz, used for the value 0. */
    private static final double       LFO_RATE_MIN_HZ             = 0.01;
    /** The LFO rate of the value 100 in Hertz. */
    private static final double       LFO_RATE_MAX_HZ             = 30;
    /**
     * The exponent of the LFO rate curve. With the maximum of 30Hz, the values 38 to 43 which
     * vibrato programs commonly use result in 4.3 to 5.5Hz, slow sweeps (e.g. 16) in 0.8Hz.
     */
    private static final double       LFO_RATE_EXPONENT           = 2;

    /** The length of the Key-group structure with version 1 zones. */
    private static final int          LENGTH_KGRP                 = 336;

    private final AkpKeygroupLocation location                    = new AkpKeygroupLocation ();
    private final AkpEnvelope         ampEnvelope                 = new AkpEnvelope ();
    private final AkpEnvelope         filterEnvelope              = new AkpEnvelope ();
    private final AkpAuxEnvelope      auxEnvelope                 = new AkpAuxEnvelope ();
    private final AkpFilter           filter                      = new AkpFilter ();
    private final List<AkpZone>       zones                       = new ArrayList<> ();


    /**
     * Default constructor.
     */
    public AkpKeygroup ()
    {
        super (AkpRiffChunkId.KGRP_ID, LENGTH_KGRP);
    }


    /**
     * Get the location chunk which contains the note range and key-group settings.
     *
     * @return The location chunk
     */
    public AkpKeygroupLocation getLocation ()
    {
        return this.location;
    }


    /**
     * Get the amplitude envelope.
     *
     * @return The amplitude envelope
     */
    public AkpEnvelope getAmpEnvelope ()
    {
        return this.ampEnvelope;
    }


    /**
     * Get the filter envelope.
     *
     * @return The filter envelope
     */
    public AkpEnvelope getFilterEnvelope ()
    {
        return this.filterEnvelope;
    }


    /**
     * Get the auxiliary envelope.
     *
     * @return The auxiliary envelope
     */
    public AkpAuxEnvelope getAuxEnvelope ()
    {
        return this.auxEnvelope;
    }


    /**
     * Get the filter.
     *
     * @return The filter
     */
    public AkpFilter getFilter ()
    {
        return this.filter;
    }


    /**
     * Get the velocity zones. These include the unused ones.
     *
     * @return The zones, normally 4
     */
    public List<AkpZone> getZones ()
    {
        return this.zones;
    }


    /**
     * Reads the data from a chunk.
     *
     * @param chunk The chunk to read
     * @throws ParseException Could not read the data
     */
    public void read (final RawRIFFChunk chunk) throws ParseException
    {
        final byte [] data = chunk.getData ();

        int envelopeCounter = 0;
        int position = 0;
        while (position + 8 <= data.length)
        {
            final int fourCC = Byte.toUnsignedInt (data[position]) << 24 | Byte.toUnsignedInt (data[position + 1]) << 16 | Byte.toUnsignedInt (data[position + 2]) << 8 | Byte.toUnsignedInt (data[position + 3]);
            final int size = Byte.toUnsignedInt (data[position + 4]) | Byte.toUnsignedInt (data[position + 5]) << 8 | Byte.toUnsignedInt (data[position + 6]) << 16 | Byte.toUnsignedInt (data[position + 7]) << 24;
            final int start = position + 8;
            if (size < 0 || start + size > data.length)
                throw new ParseException ("Sub-chunk of key-group is larger than the key-group.");

            final Optional<AkpRiffChunkId> id = AkpRiffChunkId.fromFourCC (fourCC);
            if (id.isPresent ())
            {
                final RawRIFFChunk subChunk = new RawRIFFChunk (0, id.get (), size);
                subChunk.setData (Arrays.copyOfRange (data, start, start + size));

                switch (id.get ())
                {
                    case KGRP_LOC_ID:
                        this.location.read (subChunk);
                        break;

                    case KGRP_ENV_ID:
                        // The envelopes are always stored in the order amplitude, filter,
                        // auxiliary
                        if (envelopeCounter == 0)
                            this.ampEnvelope.read (subChunk);
                        else if (envelopeCounter == 1)
                            this.filterEnvelope.read (subChunk);
                        else
                            this.auxEnvelope.read (subChunk);
                        envelopeCounter++;
                        break;

                    case KGRP_FILTER_ID:
                        this.filter.read (subChunk);
                        break;

                    case KGRP_ZONE_ID:
                        final AkpZone zone = new AkpZone ();
                        zone.read (subChunk);
                        this.zones.add (zone);
                        break;

                    default:
                        // Ignore other chunks
                        break;
                }
            }

            position = start + size + size % 2;
        }
    }


    /**
     * A key-group is a note range with up to 4 velocity layers.
     *
     * @param modsChunk The modulations chunk
     * @param tuningChunk The tuning chunk
     * @param outChunk The output chunk
     * @param lfo1Chunk The chunk of LFO 1
     * @param lfo2Chunk The chunk of LFO 2
     *
     * @return 1-4 sample zones depending on the number of active key-group zones
     */
    public List<ISampleZone> createSampleZones (final AkpModulations modsChunk, final AkpTuning tuningChunk, final AkpOutput outChunk, final AkpLfo lfo1Chunk, final AkpLfo lfo2Chunk)
    {
        final List<ISampleZone> sampleZones = new ArrayList<> ();

        final int lowNote = this.location.getLowNote ();
        final int highNote = this.location.getHighNote ();

        final double globalTuning = tuningChunk.getSemitoneTune () + tuningChunk.getFineTune () / 100.0;
        final double keygroupTuning = globalTuning + this.location.getSemitoneTune () + this.location.getFineTune () / 100.0;

        final IEnvelope amplitudeEnvelope = createEnvelope (this.ampEnvelope);
        final IEnvelope filterEnvelopeSource = createEnvelope (this.filterEnvelope);

        // The auxiliary envelope has 4 rates and 4 levels: rate 1 moves from 0 to level 1, rate 2
        // to level 2 and rate 3 to level 3 which is held while the note is pressed. After the
        // note-off rate 4 moves to level 4. Since there is only 1 decay stage, level 2 is skipped
        // and the times of rate 2 and 3 are combined
        final IEnvelope auxiliaryEnvelope = new DefaultEnvelope ();
        auxiliaryEnvelope.setStartLevel (0);
        auxiliaryEnvelope.setAttackTime (AkaiEnvelopeTime.toSecondsS5000 (this.auxEnvelope.getRate (0)));
        auxiliaryEnvelope.setHoldLevel (this.auxEnvelope.getLevel (0) / 100.0);
        auxiliaryEnvelope.setHoldTime (0);
        auxiliaryEnvelope.setDecayTime (AkaiEnvelopeTime.toSecondsS5000 (this.auxEnvelope.getRate (1)) + AkaiEnvelopeTime.toSecondsS5000 (this.auxEnvelope.getRate (2)));
        auxiliaryEnvelope.setSustainLevel (this.auxEnvelope.getLevel (2) / 100.0);
        auxiliaryEnvelope.setReleaseTime (AkaiEnvelopeTime.toSecondsS5000 (this.auxEnvelope.getRate (3)));
        auxiliaryEnvelope.setEndLevel (this.auxEnvelope.getLevel (3) / 100.0);
        setTimeTracking (auxiliaryEnvelope, this.auxEnvelope.getVelocityToRate1 (), this.auxEnvelope.getKeyboardToRate2And4 ());

        // The modulation sources which can be represented by the model
        final Map<Integer, IEnvelope> envelopes = new HashMap<> ();
        envelopes.put (Integer.valueOf (AkpModulations.SOURCE_AMP_ENV), amplitudeEnvelope);
        envelopes.put (Integer.valueOf (AkpModulations.SOURCE_FILTER_ENV), filterEnvelopeSource);
        envelopes.put (Integer.valueOf (AkpModulations.SOURCE_AUX_ENV), auxiliaryEnvelope);
        final Map<Integer, AkpLfo> lfoChunks = new HashMap<> ();
        lfoChunks.put (Integer.valueOf (AkpModulations.SOURCE_LFO1), lfo1Chunk);
        lfoChunks.put (Integer.valueOf (AkpModulations.SOURCE_LFO2), lfo2Chunk);
        final Map<Integer, ILfo> lfos = new HashMap<> ();
        lfos.put (Integer.valueOf (AkpModulations.SOURCE_LFO1), createLfo (lfo1Chunk));
        lfos.put (Integer.valueOf (AkpModulations.SOURCE_LFO2), createLfo (lfo2Chunk));

        // The program level amounts of the amplitude modulation are in the output chunk, the one
        // of the key-group in the location chunk
        final List<Modulation> pitchModulations = getModulations (modsChunk, new Slot []
        {
            Slot.PITCH_MOD_1,
            Slot.PITCH_MOD_2
        }, new int []
        {
            this.location.getPitchMod1 (),
            this.location.getPitchMod2 ()
        });
        final List<Modulation> amplitudeModulations = getModulations (modsChunk, new Slot []
        {
            Slot.AMP_MOD_1,
            Slot.AMP_MOD_2,
            Slot.AMP_MOD
        }, new int []
        {
            outChunk.getAmpMod1 (),
            outChunk.getAmpMod2 (),
            this.location.getAmpMod ()
        });
        final List<Modulation> cutoffModulations = getModulations (modsChunk, new Slot []
        {
            Slot.FILTER_MOD_1,
            Slot.FILTER_MOD_2,
            Slot.FILTER_MOD_3
        }, new int []
        {
            this.filter.getModInput (0),
            this.filter.getModInput (1),
            this.filter.getModInput (2)
        });

        // The model supports only one envelope and one LFO per destination, therefore the first
        // matching modulation is used
        final Optional<Modulation> pitchEnvelopeModulation = findModulation (pitchModulations, envelopes.keySet ());
        final Optional<Modulation> pitchLfoModulation = findLfoModulation (pitchModulations, lfoChunks);
        final Optional<Modulation> amplitudeLfoModulation = findLfoModulation (amplitudeModulations, lfoChunks);
        final Optional<Modulation> cutoffLfoModulation = findLfoModulation (cutoffModulations, lfoChunks);

        // The filter envelope modulates the cutoff with its own depth. A modulation input with the
        // filter envelope as its source is the same parameter: the Z4/Z8 writes the depth of the
        // filter envelope to it as well (e.g. 40 and 40) while the S5000/S6000 leaves it at 0.
        // Therefore, it is ignored. One with another envelope is only used if the filter envelope
        // has no depth since there is only one cutoff envelope
        int cutoffEnvelopeDepth = this.filterEnvelope.getDepth ();
        IEnvelope cutoffEnvelope = filterEnvelopeSource;
        if (cutoffEnvelopeDepth == 0)
        {
            final Optional<Modulation> cutoffEnvelopeModulation = findModulation (cutoffModulations, Set.of (Integer.valueOf (AkpModulations.SOURCE_AMP_ENV), Integer.valueOf (AkpModulations.SOURCE_AUX_ENV)));
            if (cutoffEnvelopeModulation.isPresent ())
            {
                final Modulation modulation = cutoffEnvelopeModulation.get ();
                cutoffEnvelopeDepth = modulation.amount ();
                cutoffEnvelope = envelopes.get (Integer.valueOf (modulation.source ()));
            }
        }

        final int filterMode = this.filter.getMode ();
        final FilterType filterType = FILTER_TYPES.get (Integer.valueOf (filterMode));
        final Integer filterPoles = FILTER_POLES.get (Integer.valueOf (filterMode));
        // +12 is the default and this tracks the filter octave for octave
        final double filterKeyTracking = Math.clamp (this.filter.getKeyboardTrack () / 12.0, -1, 1);

        for (final AkpZone zone: this.zones)
        {
            if (!zone.isUsed ())
                continue;

            final ISampleZone sampleZone = new DefaultSampleZone ();
            sampleZones.add (sampleZone);
            sampleZone.setName (zone.getSampleName ());
            sampleZone.setKeyLow (lowNote);
            sampleZone.setKeyHigh (highNote);
            sampleZone.setVelocityLow (zone.getLowVelocity ());
            sampleZone.setVelocityHigh (zone.getHighVelocity ());
            sampleZone.setTuning (keygroupTuning + zone.getSemitoneTune () + zone.getFineTune () / 100.0);
            sampleZone.setKeyTracking (zone.isKeyboardTrack () ? 1 : 0);
            sampleZone.setExclusiveGroup (this.location.getMuteGroup ());

            // The filter value of the zone is an offset to the cutoff of the key-group
            final int zoneCutoff = Math.clamp (this.filter.getCutoff () + zone.getFilter (), 0, 100);
            // A fully opened low-pass without resonance, key-tracking, a closing envelope and an
            // LFO has no audible effect, therefore no filter is created in that case
            final boolean isOpenLowPass = filterType == FilterType.LOW_PASS && zoneCutoff >= 100 && this.filter.getResonance () == 0 && this.filter.getKeyboardTrack () == 0 && cutoffEnvelopeDepth >= 0 && cutoffLfoModulation.isEmpty ();
            if (filterType != null && !isOpenLowPass)
            {
                final double cutoff = MathUtils.denormalizeFrequency (zoneCutoff / 100.0, IFilter.MAX_FREQUENCY);
                // Exponential resonance conversion to normalized 0..1 range and damped by
                // factor 4
                final double res = Math.pow (Math.clamp (this.filter.getResonance () / 12.0, 0, 1.0), 1.0 / 3.0) / 4.0;
                final IFilter sampleFilter = new DefaultFilter (filterType, filterPoles.intValue (), cutoff, res);
                sampleZone.setFilter (sampleFilter);

                sampleFilter.setCutoffKeyTracking (filterKeyTracking);
                final IEnvelopeModulator cutoffEnvelopeModulator = sampleFilter.getCutoffEnvelopeModulator ();
                cutoffEnvelopeModulator.setDepth (Math.clamp (cutoffEnvelopeDepth / 100.0, -1, 1));
                cutoffEnvelopeModulator.setSource (cutoffEnvelope);

                if (cutoffLfoModulation.isPresent ())
                    setLfoModulation (sampleFilter.getCutoffLfoModulator (), cutoffLfoModulation.get (), lfos, lfoChunks, LFO_CUTOFF_FULL_DEPTH_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH);
            }

            // -50..50, the panning has no effect if the zone is routed to a mono output
            sampleZone.setPanning (zone.isMonoOutput () ? 0 : zone.getPanning () / 50.0);

            // ONE SHOT ignores a note-off and always plays the sample up to its end
            final int playback = zone.getPlayback ();
            sampleZone.setOneShot (playback == AkpZone.PLAYBACK_ONE_SHOT);
            // The loop itself is read from the sample chunk of the WAV file, the added loop only
            // signals that a loop is required and if it is left on the note-off
            final boolean isLoopUntilRelease = playback == AkpZone.PLAYBACK_LOOP_UNTIL_RELEASE;
            if (isLoopUntilRelease || playback == AkpZone.PLAYBACK_LOOP_IN_RELEASE || zone.isPlaybackAsSample ())
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setLoopUntilRelease (isLoopUntilRelease);
                sampleZone.getLoops ().add (loop);
            }

            // -100..100 -> since this is an offset assume -6..+6dB
            sampleZone.setGain (zone.getLevel () / 100.0 * 6.0);

            final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
            amplitudeEnvelopeModulator.setDepth (1);
            amplitudeEnvelopeModulator.setSource (amplitudeEnvelope);
            sampleZone.getAmplitudeVelocityModulator ().setDepth (outChunk.getVelocitySensitivity () / 100.0);
            if (amplitudeLfoModulation.isPresent ())
                setLfoModulation (sampleZone.getAmplitudeLfoModulator (), amplitudeLfoModulation.get (), lfos, lfoChunks, LFO_AMPLITUDE_FULL_DEPTH_DB / ILfoModulator.MAX_VOLUME_DEPTH);

            if (pitchEnvelopeModulation.isPresent ())
            {
                final Modulation modulation = pitchEnvelopeModulation.get ();
                final IEnvelopeModulator pitchModulator = sampleZone.getPitchEnvelopeModulator ();
                pitchModulator.setDepth (modulation.amount () / 100.0);
                pitchModulator.setSource (envelopes.get (Integer.valueOf (modulation.source ())));
            }
            if (pitchLfoModulation.isPresent ())
                setLfoModulation (sampleZone.getPitchLfoModulator (), pitchLfoModulation.get (), lfos, lfoChunks, LFO_PITCH_FULL_DEPTH_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH);

            sampleZone.setBendUp (tuningChunk.getPitchbendUp () * 100);
            sampleZone.setBendDown (-tuningChunk.getPitchbendDown () * 100);
        }

        return sampleZones;
    }


    /**
     * Get the modulations of a destination.
     *
     * @param modsChunk The modulations chunk which contains the modulation sources
     * @param slots The slots of the destination in the modulations chunk
     * @param amounts The modulation amounts of the slots in the range of [-100..100]
     * @return The modulations which have a source and an amount
     */
    private static List<Modulation> getModulations (final AkpModulations modsChunk, final Slot [] slots, final int [] amounts)
    {
        final List<Modulation> modulations = new ArrayList<> ();
        for (int i = 0; i < slots.length; i++)
        {
            final int source = modsChunk.getSource (slots[i]);
            if (source != AkpModulations.SOURCE_NO_SOURCE && amounts[i] != 0)
                modulations.add (new Modulation (source, amounts[i]));
        }
        return modulations;
    }


    /**
     * Find the first modulation which uses one of the given sources.
     *
     * @param modulations The modulations of a destination
     * @param sources The sources to look for
     * @return The modulation if found
     */
    private static Optional<Modulation> findModulation (final List<Modulation> modulations, final Set<Integer> sources)
    {
        for (final Modulation modulation: modulations)
            if (sources.contains (Integer.valueOf (modulation.source ())))
                return Optional.of (modulation);
        return Optional.empty ();
    }


    /**
     * Find the first modulation which uses one of the LFOs as its source. An LFO with a depth of 0
     * does not modulate anything and is therefore ignored.
     *
     * @param modulations The modulations of a destination
     * @param lfoChunks The LFO chunks mapped to their modulation source
     * @return The modulation if found
     */
    private static Optional<Modulation> findLfoModulation (final List<Modulation> modulations, final Map<Integer, AkpLfo> lfoChunks)
    {
        for (final Modulation modulation: modulations)
        {
            final AkpLfo lfoChunk = lfoChunks.get (Integer.valueOf (modulation.source ()));
            if (lfoChunk != null && lfoChunk.getDepth () > 0)
                return Optional.of (modulation);
        }
        return Optional.empty ();
    }


    /**
     * Set the source and depth of an LFO modulator. The depth is the product of the depth of the
     * LFO and the amount of the modulation.
     *
     * @param modulator The modulator to set
     * @param modulation The modulation which has one of the LFOs as its source
     * @param lfos The converted LFOs mapped to their modulation source
     * @param lfoChunks The LFO chunks mapped to their modulation source
     * @param fullDepth The depth of the model which matches the maximum LFO depth and amount
     */
    private static void setLfoModulation (final ILfoModulator modulator, final Modulation modulation, final Map<Integer, ILfo> lfos, final Map<Integer, AkpLfo> lfoChunks, final double fullDepth)
    {
        final Integer source = Integer.valueOf (modulation.source ());
        final double depth = lfoChunks.get (source).getDepth () / 100.0 * modulation.amount () / 100.0;
        modulator.setDepth (Math.clamp (depth * fullDepth, -1, 1));
        modulator.setSource (lfos.get (source));
    }


    /**
     * Create an envelope from the amplitude or filter envelope chunk.
     *
     * @param envelopeChunk The envelope chunk
     * @return The envelope
     */
    private static IEnvelope createEnvelope (final AkpEnvelope envelopeChunk)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (AkaiEnvelopeTime.toSecondsS5000 (envelopeChunk.getAttack ()));
        envelope.setDecayTime (AkaiEnvelopeTime.toSecondsS5000 (envelopeChunk.getDecay ()));
        envelope.setSustainLevel (envelopeChunk.getSustain () / 100.0);
        envelope.setReleaseTime (AkaiEnvelopeTime.toSecondsS5000 (envelopeChunk.getRelease ()));
        setTimeTracking (envelope, envelopeChunk.getVelocityToAttack (), envelopeChunk.getKeyscale ());
        return envelope;
    }


    /**
     * Create an LFO from an LFO chunk.
     *
     * @param lfoChunk The LFO chunk
     * @return The LFO
     */
    private static ILfo createLfo (final AkpLfo lfoChunk)
    {
        final ILfo lfo = new DefaultLfo ();
        // The unipolar square waves and the bipolar saw-tooth have no counterpart in the model
        lfo.setWaveform (switch (lfoChunk.getWaveform ())
        {
            case AkpLfo.SINE -> LfoWaveform.SINE;
            case AkpLfo.SQUARE, AkpLfo.SQUARE_POS, AkpLfo.SQUARE_NEG -> LfoWaveform.SQUARE;
            case AkpLfo.SAW_BI, AkpLfo.SAW_UP -> LfoWaveform.SAWTOOTH_UP;
            case AkpLfo.SAW_DOWN -> LfoWaveform.SAWTOOTH_DOWN;
            case AkpLfo.RANDOM -> LfoWaveform.RANDOM;
            default -> LfoWaveform.TRIANGLE;
        });
        // The rate curve is not documented, a power curve is assumed
        lfo.setRate (Math.max (LFO_RATE_MIN_HZ, LFO_RATE_MAX_HZ * Math.pow (Math.clamp (lfoChunk.getRate (), 0, 100) / 100.0, LFO_RATE_EXPONENT)));
        // The delay curve is not documented either, the one of the envelope stages is assumed
        final int delay = lfoChunk.getDelay ();
        lfo.setDelay (delay == 0 ? 0 : AkaiEnvelopeTime.toSecondsS5000 (delay));
        // Only LFO 2 can be re-triggered by a note, LFO 1 runs freely (or synchronized to the MIDI
        // clock)
        lfo.setKeySync (!lfoChunk.isFirst () && lfoChunk.isRetrigger ());
        return lfo;
    }


    /**
     * Set the velocity and key tracking of the times of an envelope. The S5000/S6000 only scales
     * some of the stages (e.g. Velo->Attack only the attack) while the model scales all times,
     * which is the closest match. The direction is the same as on the S1000/S3000: positive values
     * lengthen the times towards higher velocities and keys, the model uses the opposite
     * direction.
     *
     * @param envelope The envelope to update
     * @param velocityAmount The velocity to time modulation in the range of [-100..100]
     * @param keyAmount The key to time modulation in the range of [-100..100]
     */
    private static void setTimeTracking (final IEnvelope envelope, final int velocityAmount, final int keyAmount)
    {
        envelope.setTimeVelocityTracking (Math.clamp (-velocityAmount / 100.0, -1, 1));
        envelope.setTimeKeyTracking (Math.clamp (-keyAmount / 100.0, -1, 1));
    }


    /**
     * Write the data to a stream.
     *
     * @param out The output stream to write to
     * @throws IOException Could not write the data
     */
    public void write (final ByteArrayOutputStream out) throws IOException
    {
        // Not used
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        // Not used
        return "";
    }


    /**
     * A modulation of a destination.
     *
     * @param source The modulation source, see the SOURCE_* constants of AkpModulations
     * @param amount The modulation amount in the range of [-100..100]
     */
    private record Modulation (int source, int amount)
    {
        // Intentionally empty
    }
}
