// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000.riff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.riff.AbstractSpecificRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A S5000/S6000 output chunk.
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

        FILTER_POLES.put (Integer.valueOf (0), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (1), Integer.valueOf (4));
        FILTER_POLES.put (Integer.valueOf (2), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (3), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (4), Integer.valueOf (4));
        FILTER_POLES.put (Integer.valueOf (5), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (6), Integer.valueOf (1));
        FILTER_POLES.put (Integer.valueOf (7), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (8), Integer.valueOf (1));
        FILTER_POLES.put (Integer.valueOf (12), Integer.valueOf (1));
        FILTER_POLES.put (Integer.valueOf (13), Integer.valueOf (2));
        FILTER_POLES.put (Integer.valueOf (14), Integer.valueOf (3));
        FILTER_POLES.put (Integer.valueOf (15), Integer.valueOf (4));
    }

    // Full key-group data
    private byte [] data;


    /**
     * Default constructor. The length of the Output structure (including the 8 byte RIFF header) is
     * 344 or 336.
     * 
     * @param chunk The raw chunk
     */
    public AkpKeygroup (final RawRIFFChunk chunk)
    {
        super (AkpRiffChunkId.OUT_ID, chunk.getData ().length);

        this.data = chunk.getData ();
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

        final int lowNote = getValue (0xB2);
        final int highNote = getValue (0xB3);

        // -36..36
        final int globalSemiToneTune = tuningChunk.getValue (0x33);
        // -50..50
        final int globalFineTune = tuningChunk.getValue (0x34);
        final double globalTuning = globalSemiToneTune + globalFineTune / 100.0;

        // -36..36
        final int keygroupSemiToneTune = this.getValue (0xB4);
        // -50..50
        final int keygroupFineTune = this.getValue (0xB5);
        final double keygroupTuning = globalTuning + keygroupSemiToneTune + keygroupFineTune / 100.0;

        // All -100..100
        final int pitchMod1 = this.getValue (0xB8);
        final int pitchMod2 = this.getValue (0xB9);

        // All 0..100
        final int ampEnvAttack = this.getUnsignedValue (0xC7);
        final int ampEnvDecay = this.getUnsignedValue (0xC9);
        final int ampEnvRelease = this.getUnsignedValue (0xCA);
        final int ampEnvSustain = this.getUnsignedValue (0xCD);

        final IEnvelope ampEnvelope = new DefaultEnvelope ();
        ampEnvelope.setAttackTime (toSeconds (ampEnvAttack));
        ampEnvelope.setDecayTime (toSeconds (ampEnvDecay));
        ampEnvelope.setSustainLevel (ampEnvSustain / 100.0);
        ampEnvelope.setReleaseTime (toSeconds (ampEnvRelease));

        // All 0..100
        final int filterEnvAttack = this.getUnsignedValue (0xE1);
        final int filterEnvDecay = this.getUnsignedValue (0xE3);
        final int filterEnvRelease = this.getUnsignedValue (0xE4);
        final int filterEnvSustain = this.getUnsignedValue (0xE7);

        // -100..100
        final int filterEnvDepth = this.getValue (0xE9);

        // All 0..100
        final int auxEnvRate1 = this.getUnsignedValue (0xFB);
        final int auxEnvRate2 = this.getUnsignedValue (0xFC);
        final int auxEnvRate3 = this.getUnsignedValue (0xFD);
        final int auxEnvRate4 = this.getUnsignedValue (0xFE);
        final int auxEnvLevel1 = this.getUnsignedValue (0xFF);
        final int auxEnvLevel2 = this.getUnsignedValue (0x100);
        final int auxEnvLevel3 = this.getUnsignedValue (0x101);
        final int auxEnvLevel4 = this.getUnsignedValue (0x102);
        final IEnvelope auxEnvelope = new DefaultEnvelope ();
        auxEnvelope.setAttackTime (toSeconds (auxEnvRate1));
        auxEnvelope.setHoldTime (toSeconds (auxEnvRate2));
        auxEnvelope.setDecayTime (toSeconds (auxEnvRate3));
        auxEnvelope.setReleaseTime (toSeconds (auxEnvRate4));
        auxEnvelope.setStartLevel (auxEnvLevel1 / 100.0);
        auxEnvelope.setHoldLevel (auxEnvLevel2 / 100.0);
        auxEnvelope.setSustainLevel (auxEnvLevel3 / 100.0);
        auxEnvelope.setEndLevel (auxEnvLevel4 / 100.0);

        // 0 = NO SOURCE, 1 = MODWHEEL, 2 = BEND, 3 = AFT'TOUCH, 4 = EXTERNAL, 5 = VELOCITY, 6 =
        // KEYBOARD, 7 = LFO1, 8 = LFO2, 9 = AMP ENV, 10 = FILT ENV, 11 = AUX ENV, 12 = dMODWHEEL,
        // 13 = dBEND, 14 = dEXTERNAL
        final boolean pitchMod1UsesAuxEnv = modsChunk.getUnsignedValue (0x93) == 11;
        final boolean pitchMod2UsesAuxEnv = modsChunk.getUnsignedValue (0x94) == 11;

        final int pitchbendUp = tuningChunk.getUnsignedValue (0x41);
        final int pitchbendDown = tuningChunk.getUnsignedValue (0x42);

        final int filterMode = this.getUnsignedValue (0x115);
        final FilterType filterType = FILTER_TYPES.get (Integer.valueOf (filterMode));
        // 0..100
        final int filterCutoff = this.getUnsignedValue (0x116);
        // 0..12
        final int filterResonance = this.getUnsignedValue (0x117);
        final Integer filterPoles = FILTER_POLES.get (Integer.valueOf (filterMode));
        final double cutoff = MathUtils.denormalizeFrequency (filterCutoff / 100.0, IFilter.MAX_FREQUENCY);

        int zonePosition = 0x11E;
        for (int i = 0; i < 4; i++)
        {
            // Is there a 'zone' tag?
            if (this.getValue (zonePosition) == 0)
                break;

            // Found 0x2E and 0x30
            final int sizeOfZone = this.getUnsignedValue (zonePosition + 4);

            final int nameStart = zonePosition + 9;
            final int sampleNameLength = this.getUnsignedValue (nameStart);
            if (sampleNameLength > 0)
            {
                final StringBuilder sb = new StringBuilder ();
                for (int n = 0; n < sampleNameLength; n++)
                    sb.append ((char) this.getValue (nameStart + 1 + n));
                final String sampleFileName = sb.toString ().trim ();

                final ISampleZone sampleZone = new DefaultSampleZone ();
                sampleZones.add (sampleZone);
                sampleZone.setName (sampleFileName);
                sampleZone.setKeyLow (lowNote);
                sampleZone.setKeyHigh (highNote);
                sampleZone.setVelocityLow (this.getUnsignedValue (zonePosition + 0x2A));
                sampleZone.setVelocityHigh (this.getUnsignedValue (zonePosition + 0x2B));
                // -50..50
                final int fineTune = this.getValue (zonePosition + 0x2C);
                // -36..36
                final int semiToneTune = this.getValue (zonePosition + 0x2D);
                sampleZone.setTuning (keygroupTuning + semiToneTune + fineTune / 100.0 + 12.0);

                if (filterType != null)
                {
                    // Resonance is much too high, therefore apply another factor 2
                    final IFilter filter = new DefaultFilter (filterType, filterPoles.intValue (), cutoff, (filterResonance / 12.0) / 2.0);
                    sampleZone.setFilter (filter);

                    // -100..100
                    // final int filterDepth = this.getValue (zonePosition + 0x2E);
                    final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                    cutoffEnvelopeModulator.setDepth (filterEnvDepth / 100.0);
                    final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
                    cutoffEnvelope.setAttackTime (toSeconds (filterEnvAttack));
                    cutoffEnvelope.setDecayTime (toSeconds (filterEnvDecay));
                    cutoffEnvelope.setSustainLevel (filterEnvSustain / 100.0);
                    cutoffEnvelope.setReleaseTime (toSeconds (filterEnvRelease));
                }

                // -50..50
                sampleZone.setPanning (this.getValue (zonePosition + 0x2F) / 50.0);

                // 0 = NO LOOPING, 1 = ONE SHOT, 2 = LOOP IN REL, 3 = LOOP UNTIL REL, 4 = AS SAMPLE
                final int loopType = this.getUnsignedValue (zonePosition + 0x30);
                if (loopType > 1)
                    sampleZone.getLoops ().add (new DefaultSampleLoop ());

                // -100..100 -> since this is an offset assume -6..+6dB
                sampleZone.setGain (this.getValue (zonePosition + 0x32) / 100.0 * 6.0);

                final IEnvelopeModulator amplitudeEnvelopeModulator = sampleZone.getAmplitudeEnvelopeModulator ();
                amplitudeEnvelopeModulator.setDepth (1);
                amplitudeEnvelopeModulator.setSource (ampEnvelope);
                sampleZone.getAmplitudeVelocityModulator ().setDepth (outChunk.getVelocitySensitivity () / 100.0);

                if (pitchMod1UsesAuxEnv || pitchMod2UsesAuxEnv)
                {
                    final IEnvelopeModulator pitchModulator = sampleZone.getPitchModulator ();
                    pitchModulator.setDepth ((pitchMod1UsesAuxEnv ? pitchMod1 : pitchMod2) / 100.0);
                    pitchModulator.setSource (auxEnvelope);
                }
                sampleZone.setBendDown (pitchbendDown);
                sampleZone.setBendUp (pitchbendUp);
            }

            zonePosition += sizeOfZone + 8;
        }

        return sampleZones;
    }


    private int getValue (final int position)
    {
        return this.data[position - 0xA6];
    }


    private int getUnsignedValue (final int position)
    {
        return Byte.toUnsignedInt (this.data[position - 0xA6]);
    }


    private static double toSeconds (final int value)
    {
        // No real idea, assume 6 seconds max
        return value / 100.0 * 6.0;
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
