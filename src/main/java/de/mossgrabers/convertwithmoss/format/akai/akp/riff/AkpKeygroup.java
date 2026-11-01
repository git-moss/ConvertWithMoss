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

import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
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

    /** The length of the Key-group structure with version 1 zones. */
    private static final int          LENGTH_KGRP    = 336;

    private final AkpKeygroupLocation location       = new AkpKeygroupLocation ();
    private final AkpEnvelope         ampEnvelope    = new AkpEnvelope ();
    private final AkpEnvelope         filterEnvelope = new AkpEnvelope ();
    private final AkpAuxEnvelope      auxEnvelope    = new AkpAuxEnvelope ();
    private final AkpFilter           filter         = new AkpFilter ();
    private final List<AkpZone>       zones          = new ArrayList<> ();


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
     *
     * @return 1-4 sample zones depending on the number of active key-group zones
     */
    public List<ISampleZone> createSampleZones (final AkpModulations modsChunk, final AkpTuning tuningChunk, final AkpOutput outChunk)
    {
        final List<ISampleZone> sampleZones = new ArrayList<> ();

        final int lowNote = this.location.getLowNote ();
        final int highNote = this.location.getHighNote ();

        final double globalTuning = tuningChunk.getSemitoneTune () + tuningChunk.getFineTune () / 100.0;
        final double keygroupTuning = globalTuning + this.location.getSemitoneTune () + this.location.getFineTune () / 100.0;

        final IEnvelope amplitudeEnvelope = new DefaultEnvelope ();
        amplitudeEnvelope.setAttackTime (AkaiEnvelopeTime.toSecondsS5000 (this.ampEnvelope.getAttack ()));
        amplitudeEnvelope.setDecayTime (AkaiEnvelopeTime.toSecondsS5000 (this.ampEnvelope.getDecay ()));
        amplitudeEnvelope.setSustainLevel (this.ampEnvelope.getSustain () / 100.0);
        amplitudeEnvelope.setReleaseTime (AkaiEnvelopeTime.toSecondsS5000 (this.ampEnvelope.getRelease ()));
        setTimeTracking (amplitudeEnvelope, this.ampEnvelope.getVelocityToAttack (), this.ampEnvelope.getKeyscale ());

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
        final int pitchEnvelopeDepth = this.getPitchEnvelopeDepth (modsChunk);

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
            // A fully opened low-pass without resonance, key-tracking and a closing envelope has
            // no audible effect, therefore no filter is created in that case
            final boolean isOpenLowPass = filterType == FilterType.LOW_PASS && zoneCutoff >= 100 && this.filter.getResonance () == 0 && this.filter.getKeyboardTrack () == 0 && this.filterEnvelope.getDepth () >= 0;
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
                cutoffEnvelopeModulator.setDepth (this.filterEnvelope.getDepth () / 100.0);
                final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
                cutoffEnvelope.setAttackTime (AkaiEnvelopeTime.toSecondsS5000 (this.filterEnvelope.getAttack ()));
                cutoffEnvelope.setDecayTime (AkaiEnvelopeTime.toSecondsS5000 (this.filterEnvelope.getDecay ()));
                cutoffEnvelope.setSustainLevel (this.filterEnvelope.getSustain () / 100.0);
                cutoffEnvelope.setReleaseTime (AkaiEnvelopeTime.toSecondsS5000 (this.filterEnvelope.getRelease ()));
                setTimeTracking (cutoffEnvelope, this.filterEnvelope.getVelocityToAttack (), this.filterEnvelope.getKeyscale ());
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

            if (pitchEnvelopeDepth != 0)
            {
                final IEnvelopeModulator pitchModulator = sampleZone.getPitchEnvelopeModulator ();
                pitchModulator.setDepth (pitchEnvelopeDepth / 100.0);
                pitchModulator.setSource (auxiliaryEnvelope);
            }
            sampleZone.setBendUp (tuningChunk.getPitchbendUp () * 100);
            sampleZone.setBendDown (-tuningChunk.getPitchbendDown () * 100);
        }

        return sampleZones;
    }


    /**
     * Get the depth of the pitch modulation by the auxiliary envelope.
     *
     * @param modsChunk The modulations chunk which contains the modulation sources
     * @return The depth in the range of [-100..100], 0 if the auxiliary envelope does not modulate
     *         the pitch
     */
    private int getPitchEnvelopeDepth (final AkpModulations modsChunk)
    {
        if (modsChunk.getSource (Slot.PITCH_MOD_1) == AkpModulations.SOURCE_AUX_ENV)
            return this.location.getPitchMod1 ();
        if (modsChunk.getSource (Slot.PITCH_MOD_2) == AkpModulations.SOURCE_AUX_ENV)
            return this.location.getPitchMod2 ();
        return 0;
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
}
