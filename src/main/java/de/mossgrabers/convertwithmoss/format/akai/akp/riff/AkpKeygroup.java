// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
import de.mossgrabers.convertwithmoss.file.riff.IRiffChunk;
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
    /** The modulator depth of the maximum LFO depth and amount of the pitch. */
    private static final double       PITCH_LFO_FULL_DEPTH        = LFO_PITCH_FULL_DEPTH_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** The modulator depth of the maximum LFO depth and amount of the filter cutoff. */
    private static final double       CUTOFF_LFO_FULL_DEPTH       = LFO_CUTOFF_FULL_DEPTH_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
    /** The modulator depth of the maximum LFO depth and amount of the amplitude. */
    private static final double       AMPLITUDE_LFO_FULL_DEPTH    = LFO_AMPLITUDE_FULL_DEPTH_DB / ILfoModulator.MAX_VOLUME_DEPTH;
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
    /** The number of velocity zones of a key-group. */
    private static final int          NUMBER_OF_ZONES             = 4;

    private final AkpKeygroupLocation location                    = new AkpKeygroupLocation ();
    private final AkpEnvelope         ampEnvelope                 = new AkpEnvelope ();
    private final AkpEnvelope         filterEnvelope              = new AkpEnvelope ();
    private final AkpAuxEnvelope      auxEnvelope                 = new AkpAuxEnvelope ();
    private final AkpFilter           filter                      = new AkpFilter ();
    private final List<AkpZone>       zones                       = new ArrayList<> ();
    private final List<IRiffChunk>    subChunks                   = new ArrayList<> ();


    /**
     * Default constructor. Creates a key-group with the default values and 4 unused zones.
     */
    public AkpKeygroup ()
    {
        super (AkpRiffChunkId.KGRP_ID, LENGTH_KGRP);

        for (int i = 0; i < NUMBER_OF_ZONES; i++)
            this.zones.add (new AkpZone ());
        this.subChunks.add (this.location);
        this.subChunks.add (this.ampEnvelope);
        this.subChunks.add (this.filterEnvelope);
        this.subChunks.add (this.auxEnvelope);
        this.subChunks.add (this.filter);
        this.subChunks.addAll (this.zones);
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

        // The sub-chunks are kept in the order of the file for writing
        this.zones.clear ();
        this.subChunks.clear ();

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
                        this.subChunks.add (this.location);
                        break;

                    case KGRP_ENV_ID:
                        // The envelopes are always stored in the order amplitude, filter,
                        // auxiliary
                        final AbstractAkpChunk envelope = switch (envelopeCounter)
                        {
                            case 0 -> this.ampEnvelope;
                            case 1 -> this.filterEnvelope;
                            default -> this.auxEnvelope;
                        };
                        envelope.read (subChunk);
                        this.subChunks.add (envelope);
                        envelopeCounter++;
                        break;

                    case KGRP_FILTER_ID:
                        this.filter.read (subChunk);
                        this.subChunks.add (this.filter);
                        break;

                    case KGRP_ZONE_ID:
                        final AkpZone zone = new AkpZone ();
                        zone.read (subChunk);
                        this.zones.add (zone);
                        this.subChunks.add (zone);
                        break;

                    default:
                        // Other chunks are not expected but kept for writing
                        this.subChunks.add (subChunk);
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
            final int zoneCutoff = Math.clamp (this.filter.getCutoff () + (long) zone.getFilter (), 0, 100);
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
     * Set the program level parameters from a sample zone. This is the inverse of the program level
     * part of createSampleZones: the velocity sensitivity, the pitch-bend range and the LFOs. LFO 1
     * is used for the pitch LFO (it is the default source of Pitch Mod 1), LFO 2 for the cutoff LFO
     * (default source of Filter Mod 2). The amplitude LFO uses LFO 2 as well via Amp Mod 2 if there
     * is no cutoff LFO or if both use the same LFO settings, otherwise it is dropped. The depth of
     * a modulation is the product of the LFO depth and the amount of the destination, the LFO depth
     * is set as low as possible to keep the precision of the amounts.
     *
     * @param sampleZones All sample zones of the program, the velocity sensitivity and pitch-bend
     *            range are taken from the first one
     * @param modsChunk The modulations chunk
     * @param tuningChunk The tuning chunk
     * @param outChunk The output chunk
     * @param lfo1Chunk The chunk of LFO 1
     * @param lfo2Chunk The chunk of LFO 2
     */
    public static void setProgramParameters (final List<ISampleZone> sampleZones, final AkpModulations modsChunk, final AkpTuning tuningChunk, final AkpOutput outChunk, final AkpLfo lfo1Chunk, final AkpLfo lfo2Chunk)
    {
        final ISampleZone zone = sampleZones.get (0);
        outChunk.setVelocitySensitivity (toAmount (zone.getAmplitudeVelocityModulator ().getDepth ()));
        tuningChunk.setPitchbendUp (Math.clamp (Math.round (Math.abs (zone.getBendUp ()) / 100.0), 0, 24));
        tuningChunk.setPitchbendDown (Math.clamp (Math.round (Math.abs (zone.getBendDown ()) / 100.0), 0, 24));

        // The LFOs are program parameters, they are taken from the first zone which uses them
        final Optional<ILfoModulator> pitchLfoModulator = findLfoModulator (sampleZones, ISampleZone::getPitchLfoModulator);
        if (pitchLfoModulator.isPresent ())
            setLfo (lfo1Chunk, pitchLfoModulator.get ().getSource (), toLfoDepth (pitchLfoModulator.get ().getDepth (), PITCH_LFO_FULL_DEPTH));

        final Optional<ILfoModulator> cutoffLfoModulator = findLfoModulator (sampleZones, sampleZone -> sampleZone.getFilter ().isPresent () ? sampleZone.getFilter ().get ().getCutoffLfoModulator () : null);
        final Optional<ILfoModulator> amplitudeLfoModulator = findLfoModulator (sampleZones, ISampleZone::getAmplitudeLfoModulator);
        final boolean hasAmplitudeLfo = amplitudeLfoModulator.isPresent () && (cutoffLfoModulator.isEmpty () || isSameLfo (cutoffLfoModulator.get ().getSource (), amplitudeLfoModulator.get ().getSource ()));

        // Both modulations share the depth of LFO 2, therefore use the one which needs the larger
        // depth
        final int cutoffLfoDepth = cutoffLfoModulator.isPresent () ? toLfoDepth (cutoffLfoModulator.get ().getDepth (), CUTOFF_LFO_FULL_DEPTH) : 0;
        final int amplitudeLfoDepth = hasAmplitudeLfo ? toLfoDepth (amplitudeLfoModulator.get ().getDepth (), AMPLITUDE_LFO_FULL_DEPTH) : 0;
        if (cutoffLfoModulator.isPresent ())
            setLfo (lfo2Chunk, cutoffLfoModulator.get ().getSource (), Math.max (cutoffLfoDepth, amplitudeLfoDepth));
        else if (hasAmplitudeLfo)
            setLfo (lfo2Chunk, amplitudeLfoModulator.get ().getSource (), amplitudeLfoDepth);
        if (hasAmplitudeLfo)
        {
            modsChunk.setSource (Slot.AMP_MOD_2, AkpModulations.SOURCE_LFO2);
            outChunk.setAmpMod2 (toLfoAmount (amplitudeLfoModulator.get ().getDepth (), AMPLITUDE_LFO_FULL_DEPTH, lfo2Chunk.getDepth ()));
        }
    }


    /**
     * Set the parameters of the key-group from sample zones. This is the inverse of the key-group
     * and zone part of createSampleZones. The key range, envelopes, filter and modulation amounts
     * are taken from the first zone since they are set per key-group.
     *
     * @param sampleZones The sample zones of the key-group, 1 to 4 zones with the same key range
     * @param lfo1Chunk The chunk of LFO 1 which already contains the program parameters
     * @param lfo2Chunk The chunk of LFO 2 which already contains the program parameters
     */
    public void setSampleZones (final List<ISampleZone> sampleZones, final AkpLfo lfo1Chunk, final AkpLfo lfo2Chunk)
    {
        final ISampleZone firstZone = sampleZones.get (0);

        this.location.setLowNote (Math.clamp (firstZone.getKeyLow (), AkpKeygroupLocation.LOWEST_NOTE, 127));
        this.location.setHighNote (Math.clamp (firstZone.getKeyHigh (), AkpKeygroupLocation.LOWEST_NOTE, 127));
        // The range of the mute groups is not documented
        this.location.setMuteGroup (Math.clamp (firstZone.getExclusiveGroup (), 0, 127));

        // Pitch Mod 1 uses LFO 1. If the program has no pitch LFO, the default amount is kept since
        // the LFO depth is 0 and the mod-wheel then adds a vibrato as usual. If only other
        // key-groups have a pitch LFO, the amount needs to be 0
        final ILfoModulator pitchLfoModulator = firstZone.getPitchLfoModulator ();
        if (pitchLfoModulator.getDepth () != 0 && lfo1Chunk.getDepth () > 0)
            this.location.setPitchMod1 (toLfoAmount (pitchLfoModulator.getDepth (), PITCH_LFO_FULL_DEPTH, lfo1Chunk.getDepth ()));
        else if (lfo1Chunk.getDepth () > 0)
            this.location.setPitchMod1 (0);

        // Pitch Mod 2 uses the auxiliary envelope
        final IEnvelopeModulator pitchEnvelopeModulator = firstZone.getPitchEnvelopeModulator ();
        if (pitchEnvelopeModulator.getDepth () != 0)
        {
            this.location.setPitchMod2 (toAmount (pitchEnvelopeModulator.getDepth ()));
            setAuxEnvelope (this.auxEnvelope, pitchEnvelopeModulator.getSource ());
        }

        setEnvelope (this.ampEnvelope, firstZone.getAmplitudeEnvelopeModulator ().getSource ());

        final Optional<IFilter> optionalFilter = firstZone.getFilter ();
        final int keygroupCutoff;
        if (optionalFilter.isPresent ())
        {
            final IFilter sampleFilter = optionalFilter.get ();
            this.filter.setMode (getFilterMode (sampleFilter));
            keygroupCutoff = getCutoff (sampleFilter);
            this.filter.setCutoff (keygroupCutoff);
            // Inverse of the exponential conversion of createSampleZones
            final double resonance = Math.clamp (sampleFilter.getResonance () * 4.0, 0, 1);
            this.filter.setResonance (Math.clamp (Math.round (12.0 * resonance * resonance * resonance), 0, 12));
            this.filter.setKeyboardTrack (Math.clamp (Math.round (sampleFilter.getCutoffKeyTracking () * 12.0), -36, 36));

            // The depth of the filter envelope is stored in the filter envelope itself
            final IEnvelopeModulator cutoffEnvelopeModulator = sampleFilter.getCutoffEnvelopeModulator ();
            setEnvelope (this.filterEnvelope, cutoffEnvelopeModulator.getSource ());
            this.filterEnvelope.setDepth (toAmount (cutoffEnvelopeModulator.getDepth ()));

            // Filter Mod 2 uses LFO 2
            final ILfoModulator cutoffLfoModulator = sampleFilter.getCutoffLfoModulator ();
            if (cutoffLfoModulator.getDepth () != 0 && lfo2Chunk.getDepth () > 0)
                this.filter.setModInput (1, toLfoAmount (cutoffLfoModulator.getDepth (), CUTOFF_LFO_FULL_DEPTH, lfo2Chunk.getDepth ()));
        }
        else
            keygroupCutoff = this.filter.getCutoff ();

        for (int i = 0; i < sampleZones.size () && i < NUMBER_OF_ZONES; i++)
            setZone (this.zones.get (i), sampleZones.get (i), keygroupCutoff);
    }


    /**
     * Check if another key-group has the same key-group level parameters: the location (key range,
     * tuning, modulation amounts, mute group), the envelopes and the filter. The velocity zones are
     * not compared.
     *
     * @param other The other key-group
     * @return True if all key-group level parameters are identical
     */
    public boolean hasSameParameters (final AkpKeygroup other)
    {
        final AbstractAkpChunk [] chunks =
        {
            this.location,
            this.ampEnvelope,
            this.filterEnvelope,
            this.auxEnvelope,
            this.filter
        };
        final AbstractAkpChunk [] otherChunks =
        {
            other.location,
            other.ampEnvelope,
            other.filterEnvelope,
            other.auxEnvelope,
            other.filter
        };
        final Optional<AkpGeneration> generation = Optional.of (AbstractAkpChunk.DEFAULT_GENERATION);
        for (int i = 0; i < chunks.length; i++)
        {
            chunks[i].updateData (generation);
            otherChunks[i].updateData (generation);
            if (!Arrays.equals (chunks[i].getData (), otherChunks[i].getData ()))
                return false;
        }
        return true;
    }


    /**
     * Set the parameters of a velocity zone from a sample zone.
     *
     * @param zone The velocity zone to fill
     * @param sampleZone The sample zone
     * @param keygroupCutoff The cutoff of the key-group in the range of [0..100]
     */
    private static void setZone (final AkpZone zone, final ISampleZone sampleZone, final int keygroupCutoff)
    {
        zone.setSampleName (sampleZone.getName ());
        zone.setLowVelocity (Math.clamp (sampleZone.getVelocityLow (), 0, 127));
        zone.setHighVelocity (Math.clamp (sampleZone.getVelocityHigh (), 0, 127));

        final double tuning = sampleZone.getTuning ();
        final int semitones = Math.clamp (Math.round (tuning), -36, 36);
        zone.setSemitoneTune (semitones);
        zone.setFineTune (Math.clamp (Math.round ((tuning - semitones) * 100.0), -50, 50));

        zone.setPanning (Math.clamp (Math.round (sampleZone.getPanning () * 50.0), -50, 50));
        // The level is an offset, -100..100 is assumed to be -6..+6dB
        zone.setLevel (Math.clamp (Math.round (sampleZone.getGain () / 6.0 * 100.0), -100, 100));
        zone.setKeyboardTrack (sampleZone.getKeyTracking () > 0);

        // The filter value of the zone is an offset to the cutoff of the key-group
        final Optional<IFilter> sampleFilter = sampleZone.getFilter ();
        final int zoneCutoff = sampleFilter.isPresent () ? getCutoff (sampleFilter.get ()) : 100;
        zone.setFilter (Math.clamp (zoneCutoff - (long) keygroupCutoff, -100, 100));

        // The loop itself is written to the sample chunk of the WAV file
        final List<ISampleLoop> loops = sampleZone.getLoops ();
        if (sampleZone.isOneShot ())
            zone.setPlayback (AkpZone.PLAYBACK_ONE_SHOT);
        else if (loops.isEmpty ())
            zone.setPlayback (AkpZone.PLAYBACK_NO_LOOPING);
        else
            zone.setPlayback (loops.get (0).isLoopUntilRelease () ? AkpZone.PLAYBACK_LOOP_UNTIL_RELEASE : AkpZone.PLAYBACK_LOOP_IN_RELEASE);
    }


    /**
     * Set the parameters of an amplitude or filter envelope chunk from an envelope. Values which
     * are not set keep the default of the chunk.
     *
     * @param envelopeChunk The envelope chunk to fill
     * @param envelope The envelope
     */
    private static void setEnvelope (final AkpEnvelope envelopeChunk, final IEnvelope envelope)
    {
        if (envelope.getAttackTime () >= 0)
            envelopeChunk.setAttack (AkaiEnvelopeTime.fromSecondsS5000 (envelope.getAttackTime ()));
        if (envelope.getDecayTime () >= 0)
            envelopeChunk.setDecay (AkaiEnvelopeTime.fromSecondsS5000 (envelope.getDecayTime ()));
        if (envelope.getSustainLevel () >= 0)
            envelopeChunk.setSustain (toLevel (envelope.getSustainLevel ()));
        if (envelope.getReleaseTime () >= 0)
            envelopeChunk.setRelease (AkaiEnvelopeTime.fromSecondsS5000 (envelope.getReleaseTime ()));
        envelopeChunk.setVelocityToAttack (toAmount (-envelope.getTimeVelocityTracking ()));
        envelopeChunk.setKeyscale (toAmount (-envelope.getTimeKeyTracking ()));
    }


    /**
     * Set the parameters of the auxiliary envelope chunk from an envelope. The inverse of
     * createSampleZones: rate 1 is the attack to the hold level, rate 2 the decay to the sustain
     * level, rate 3 is not needed and therefore the fastest one, rate 4 is the release to the end
     * level. A decay which is longer than the longest stage is split onto rate 2 and 3.
     *
     * @param envelopeChunk The auxiliary envelope chunk to fill
     * @param envelope The envelope
     */
    private static void setAuxEnvelope (final AkpAuxEnvelope envelopeChunk, final IEnvelope envelope)
    {
        if (envelope.getAttackTime () >= 0)
            envelopeChunk.setRate (0, AkaiEnvelopeTime.fromSecondsS5000 (envelope.getAttackTime ()));
        final int holdLevel = envelope.getHoldLevel () >= 0 ? toLevel (envelope.getHoldLevel ()) : 100;
        envelopeChunk.setLevel (0, holdLevel);
        final int sustain = envelope.getSustainLevel () >= 0 ? toLevel (envelope.getSustainLevel ()) : 100;
        final double decay = envelope.getDecayTime ();
        if (decay > AkaiEnvelopeTime.toSecondsS5000 (100))
        {
            final int rate = AkaiEnvelopeTime.fromSecondsS5000 (decay / 2.0);
            envelopeChunk.setRate (1, rate);
            envelopeChunk.setLevel (1, (holdLevel + sustain) / 2);
            envelopeChunk.setRate (2, rate);
        }
        else
        {
            if (decay >= 0)
                envelopeChunk.setRate (1, AkaiEnvelopeTime.fromSecondsS5000 (decay));
            envelopeChunk.setLevel (1, sustain);
            envelopeChunk.setRate (2, 0);
        }
        envelopeChunk.setLevel (2, sustain);
        if (envelope.getReleaseTime () >= 0)
            envelopeChunk.setRate (3, AkaiEnvelopeTime.fromSecondsS5000 (envelope.getReleaseTime ()));
        envelopeChunk.setLevel (3, envelope.getEndLevel () >= 0 ? toLevel (envelope.getEndLevel ()) : 0);
        envelopeChunk.setVelocityToRate1 (toAmount (-envelope.getTimeVelocityTracking ()));
        envelopeChunk.setKeyboardToRate2And4 (toAmount (-envelope.getTimeKeyTracking ()));
    }


    /**
     * Set the parameters of an LFO chunk from an LFO.
     *
     * @param lfoChunk The LFO chunk to fill
     * @param lfo The LFO
     * @param depth The depth of the LFO in the range of [1..100]
     */
    private static void setLfo (final AkpLfo lfoChunk, final ILfo lfo, final int depth)
    {
        lfoChunk.setWaveform (switch (lfo.getWaveform ())
        {
            case SINE -> AkpLfo.SINE;
            case SQUARE -> AkpLfo.SQUARE;
            case SAWTOOTH_UP -> AkpLfo.SAW_UP;
            case SAWTOOTH_DOWN -> AkpLfo.SAW_DOWN;
            case RANDOM -> AkpLfo.RANDOM;
            default -> AkpLfo.TRIANGLE;
        });
        // Inverse of the power curve of createLfo
        lfoChunk.setRate (Math.clamp (Math.round (100.0 * Math.pow (Math.max (lfo.getRate (), 0) / LFO_RATE_MAX_HZ, 1.0 / LFO_RATE_EXPONENT)), 0, 100));
        lfoChunk.setDelay (lfo.getDelay () > 0 ? AkaiEnvelopeTime.fromSecondsS5000 (lfo.getDelay ()) : 0);
        lfoChunk.setDepth (depth);
        if (!lfoChunk.isFirst ())
            lfoChunk.setRetrigger (lfo.isKeySync ());
    }


    /**
     * Find the first LFO modulator with a depth.
     *
     * @param sampleZones The sample zones to search
     * @param getter Gets the LFO modulator from a zone, may return null
     * @return The modulator if found
     */
    private static Optional<ILfoModulator> findLfoModulator (final List<ISampleZone> sampleZones, final Function<ISampleZone, ILfoModulator> getter)
    {
        for (final ISampleZone sampleZone: sampleZones)
        {
            final ILfoModulator modulator = getter.apply (sampleZone);
            if (modulator != null && modulator.getDepth () != 0)
                return Optional.of (modulator);
        }
        return Optional.empty ();
    }


    /**
     * Check if two LFOs have the same settings.
     *
     * @param lfo1 The first LFO
     * @param lfo2 The second LFO
     * @return True if they are identical or have the same waveform, rate, delay and key sync
     */
    private static boolean isSameLfo (final ILfo lfo1, final ILfo lfo2)
    {
        return lfo1 == lfo2 || lfo1.getWaveform () == lfo2.getWaveform () && lfo1.getRate () == lfo2.getRate () && lfo1.getDelay () == lfo2.getDelay () && lfo1.isKeySync () == lfo2.isKeySync ();
    }


    /**
     * Get the filter mode which matches the type and poles of a filter.
     *
     * @param sampleFilter The filter
     * @return The filter mode
     */
    private static int getFilterMode (final IFilter sampleFilter)
    {
        final int poles = sampleFilter.getPoles ();
        return switch (sampleFilter.getType ())
        {
            case HIGH_PASS -> poles <= 1 ? 6 : 7;
            case BAND_PASS -> poles >= 3 ? 4 : 3;
            case BAND_REJECTION -> 12;
            default -> poles >= 3 ? 1 : 0;
        };
    }


    /**
     * Get the cutoff of a filter.
     *
     * @param sampleFilter The filter
     * @return The cutoff in the range of [0..100]
     */
    private static int getCutoff (final IFilter sampleFilter)
    {
        return Math.clamp (Math.round (MathUtils.normalizeFrequency (sampleFilter.getCutoff (), IFilter.MAX_FREQUENCY) * 100.0), 0, 100);
    }


    /**
     * Convert a normalized modulation depth to a modulation amount.
     *
     * @param depth The depth in the range of [-1..1]
     * @return The amount in the range of [-100..100]
     */
    private static int toAmount (final double depth)
    {
        return Math.clamp (Math.round (depth * 100.0), -100, 100);
    }


    /**
     * Convert a normalized level to a level of the envelope chunks.
     *
     * @param level The level in the range of [0..1]
     * @return The level in the range of [0..100]
     */
    private static int toLevel (final double level)
    {
        return Math.clamp (Math.round (level * 100.0), 0, 100);
    }


    /**
     * Get the lowest LFO depth which allows to set the depth of an LFO modulator with an amount of
     * up to 100. Inverse of setLfoModulation.
     *
     * @param depth The depth of the modulator in the range of [-1..1]
     * @param fullDepth The modulator depth of the maximum LFO depth and amount
     * @return The LFO depth in the range of [1..100]
     */
    private static int toLfoDepth (final double depth, final double fullDepth)
    {
        final double product = Math.abs (depth) / fullDepth * 10000.0;
        return (int) Math.clamp (Math.ceil (product / 100.0 - 1e-9), 1, 100);
    }


    /**
     * Convert the depth of an LFO modulator to the amount of the modulation destination. Inverse of
     * setLfoModulation.
     *
     * @param depth The depth of the modulator in the range of [-1..1]
     * @param fullDepth The modulator depth of the maximum LFO depth and amount
     * @param lfoDepth The depth of the LFO in the range of [1..100]
     * @return The amount in the range of [-100..100]
     */
    private static int toLfoAmount (final double depth, final double fullDepth, final int lfoDepth)
    {
        return Math.clamp (Math.round (depth / fullDepth * 10000.0 / lfoDepth), -100, 100);
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
     * lengthen the times towards higher velocities and keys, the model uses the opposite direction.
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
     * Updates the data of the key-group chunk from all sub-chunks. Needs to be called before the
     * chunk is written.
     *
     * @param generation The generation in which to write the sub-chunks, if empty read sub-chunks
     *            keep their version
     */
    public void updateData (final Optional<AkpGeneration> generation)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        try
        {
            for (final IRiffChunk subChunk: this.subChunks)
            {
                if (subChunk instanceof final AbstractAkpChunk akpChunk)
                    akpChunk.updateData (generation);
                subChunk.write (out);
            }
        }
        catch (final IOException ex)
        {
            // Cannot happen since it is written to memory
            throw new UncheckedIOException (ex);
        }
        this.rawRiffChunk.setData (out.toByteArray ());
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
