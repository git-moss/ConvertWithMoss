// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator2;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;


/**
 * The settings of a voice of the Emulator II - filter, envelopes, LFO, level, velocity and tuning
 * - and how they are decoded from the bytes 0x1A to 0xB9 and 0xF0 to 0xFF of the voice record.
 * <p>
 * The record is the runtime structure of the voice: the operating system keeps most settings in
 * the form the playback engine uses - tables with one entry per velocity zone (level, cutoff, Q,
 * VCA attack and VCF attack), the counters of the envelope stages, and pointers into the tables of
 * the LFO depths. The meaning of the bytes and the laws were established by aligning 9,563 voice
 * records of the factory and the OMI libraries with the SoundFont conversions which EMXP made of
 * the same banks; see documentation/design/EMULATOR2_FORMAT.md. The envelope counters follow from
 * the 10 ms tick of the envelopes exactly, everything else is a lookup which EMXP resolved.
 *
 * @author Jürgen Moßgraber
 */
class Emulator2VoiceSettings
{
    /** Fine tuning, a signed byte in 1/64 semi-tone. */
    private static final int      FINE_TUNE           = 0x1A;
    /** Velocity to VCA attack in the high nibble. */
    private static final int      VELOCITY_TO_ATTACK  = 0x1B;
    /** LFO delay in 10 ms, less one. */
    private static final int      LFO_DELAY           = 0x1C;
    /** LFO rate. */
    private static final int      LFO_RATE            = 0x1D;
    /** Filter keyboard tracking in the high nibble, 8 = one octave per octave. */
    private static final int      KEY_TRACKING        = 0x1F;
    /** The amount of the filter envelope. */
    private static final int      ENVELOPE_AMOUNT     = 0x25;
    /** The pointers into the LFO depth tables: LFO to pitch, to filter and to level. */
    private static final int      LFO_TO_PITCH        = 0x28;
    private static final int      LFO_TO_FILTER       = 0x2A;
    private static final int      LFO_TO_LEVEL        = 0x2C;
    /** The sustain level of the filter envelope. */
    private static final int      VCF_SUSTAIN         = 0x2E;
    /** Flags; the top bit inverts the filter envelope. */
    private static final int      FLAGS               = 0x2F;
    private static final int      FLAG_NEGATIVE_ENVELOPE = 0x80;
    /** The 16 entry velocity tables, entry 0 for the highest velocity. */
    private static final int      CUTOFF_TABLE        = 0x30;
    private static final int      LEVEL_TABLE         = 0x40;
    private static final int      VCF_ATTACK_HIGH     = 0x50;
    private static final int      VCF_ATTACK_LOW      = 0x60;
    private static final int      VCA_ATTACK_HIGH     = 0x70;
    private static final int      VCA_ATTACK_LOW      = 0x80;
    private static final int      Q_TABLE             = 0xF0;
    private static final int      TABLE_LAST_ENTRY    = 15;
    /** The counters of the decay and release stages: the number of passes and the negative increment. */
    private static final int      VCF_DECAY_HIGH      = 0x90;
    private static final int      VCF_DECAY_LOW       = 0x92;
    private static final int      VCF_RELEASE_HIGH    = 0x9E;
    private static final int      VCF_RELEASE_LOW     = 0xA0;
    private static final int      VCA_DECAY_HIGH      = 0xA5;
    private static final int      VCA_DECAY_LOW       = 0xA7;
    /** The sustain level of the VCA envelope. */
    private static final int      VCA_SUSTAIN         = 0xA9;
    private static final int      VCA_RELEASE_HIGH    = 0xB3;
    private static final int      VCA_RELEASE_LOW     = 0xB5;

    /** One pass of an envelope counter takes 255 ticks of 10 ms. */
    private static final double   COUNTER_PASS        = 2.55;
    /** The time which stands for a stage that holds its level, as the SoundFont conversions use it. */
    private static final double   HOLD_TIME           = 100;
    /** The LFO depth pointers point into tables which start at this page. */
    private static final int      LFO_TABLE_PAGE      = 0x1C;
    /** The cutoff value from which on the filter is open. */
    private static final int      CUTOFF_OPEN         = 0xF9;
    /** A cutoff value which means no cutoff parameter at all. */
    private static final int      ENVELOPE_AMOUNT_NONE = 0x04;
    /** A Q table value below this holds no resonance. */
    private static final int      Q_NONE              = 0x17;
    /** The largest velocity to level attenuation in dB, the reference of the depth of the model. */
    private static final double   MAX_LEVEL_DEPTH_DB  = 96;
    /** The velocity to cutoff range of the model, 2 octaves (see the SoundFont 2 reader). */
    private static final double   VELOCITY_CUTOFF_REFERENCE_CENTS = -2400;
    /** One unit of the cutoff table is this many cents of velocity to cutoff. */
    private static final double   VELOCITY_CUTOFF_CENTS_PER_UNIT = -59.4;
    /** The fine tune unit in cents. */
    private static final double   FINE_TUNE_CENTS     = 100.0 / 64;

    /** The cutoff table value to Hertz. */
    private static final double [][] CUTOFF_HZ        =
    {
        { 0x0F, 20 }, { 0x1D, 27 }, { 0x1F, 30 }, { 0x23, 35 }, { 0x27, 40 }, { 0x29, 45 }, { 0x2B, 50 }, { 0x33, 88 }, { 0x37, 112 }, { 0x3B, 136 }, { 0x3D, 150 }, { 0x3F, 175 }, { 0x41, 190 }, { 0x43, 200 }, { 0x45, 210 }, { 0x4B, 240 }, { 0x4D, 250 }, { 0x51, 270 }, { 0x53, 280 }, { 0x55, 290 }, { 0x57, 300 }, { 0x59, 310 }, { 0x5B, 320 }, { 0x5D, 333 }, { 0x5F, 350 }, { 0x61, 363 }, { 0x63, 376 }, { 0x65, 400 }, { 0x67, 415 }, { 0x69, 430 }, { 0x6B, 450 }, { 0x6D, 475 }, { 0x6F, 500 }, { 0x71, 530 }, { 0x73, 570 }, { 0x75, 600 }, { 0x77, 630 }, { 0x79, 665 }, { 0x7B, 700 }, { 0x7D, 750 }, { 0x7F, 820 }, { 0x81, 900 }, { 0x83, 980 }, { 0x85, 1080 }, { 0x87, 1200 }, { 0x89, 1300 }, { 0x8B, 1400 }, { 0x8D, 1520 }, { 0x8F, 1670 }, { 0x91, 1800 }, { 0x93, 1900 }, { 0x95, 2050 }, { 0x97, 2200 }, { 0x99, 2350 }, { 0x9B, 2500 }, { 0x9D, 2630 }, { 0x9F, 2800 }, { 0xA1, 2930 }, { 0xA3, 3100 }, { 0xA5, 3300 }, { 0xA7, 3500 }, { 0xA9, 3750 }, { 0xAD, 4200 }, { 0xAF, 4450 }, { 0xB1, 4700 }, { 0xB3, 5000 }, { 0xB5, 5200 }, { 0xB7, 5500 }, { 0xB9, 5750 }, { 0xBB, 6000 }, { 0xBD, 6300 }, { 0xC1, 6850 }, { 0xC3, 7100 }, { 0xC7, 7700 }, { 0xC9, 8000 }, { 0xCB, 8300 }, { 0xCD, 8700 }, { 0xCF, 9000 }, { 0xD1, 9350 }, { 0xD9, 10700 }, { 0xDB, 11050 }, { 0xE5, 13000 }, { 0xE7, 13450 }, { 0xEB, 14500 }, { 0xF1, 16000 }, { 0xF3, 16600 }, { 0xFB, 19200 }
    };
    /** The Q table value to resonance in dB. */
    private static final double [][] RESONANCE_DB     =
    {
        { 0x17, 1 }, { 0x1B, 2 }, { 0x1F, 3 }, { 0x23, 4 }, { 0x2D, 5 }, { 0x31, 6 }, { 0x39, 7 }, { 0x3F, 8 }, { 0x45, 9 }, { 0x4B, 10 }, { 0x51, 11 }, { 0x55, 12 }, { 0x5D, 14 }, { 0x5F, 15 }, { 0x65, 16 }, { 0x6B, 18 }, { 0x73, 20 }, { 0x7D, 24 }, { 0x85, 27 }, { 0x8B, 29 }, { 0x93, 32 }, { 0xAD, 48 }, { 0xB9, 67 }, { 0xD5, 94 }
    };
    /** The level table value to attenuation in dB. */
    private static final double [][] ATTENUATION_DB   =
    {
        { 0xAC, 19 }, { 0xD5, 11 }, { 0xDD, 9 }, { 0xE2, 8 }, { 0xE5, 7 }, { 0xE8, 6 }, { 0xF1, 4 }, { 0xF4, 3 }, { 0xF9, 1 }, { 0xFF, 0 }
    };
    /** The difference between the first and the last level table entry to the velocity range in dB. */
    private static final double [][] VELOCITY_LEVEL_DB =
    {
        { 0, 0 }, { 0x0F, 2 }, { 0x1E, 4 }, { 0x2D, 7 }, { 0x3C, 8 }, { 0x4B, 11 }, { 0x5A, 15 }, { 0x69, 18 }, { 0x78, 22 }, { 0x87, 28 }, { 0x96, 32 }, { 0xA5, 40 }
    };
    /** The envelope amount value to cents. */
    private static final double [][] ENVELOPE_AMOUNT_CENTS =
    {
        { 0x08, 360 }, { 0x0C, 720 }, { 0x14, 1440 }, { 0x20, 2520 }, { 0x24, 2880 }, { 0x28, 3240 }, { 0x2C, 3600 }, { 0x30, 3840 }, { 0x34, 4080 }, { 0x38, 4320 }, { 0x40, 4800 }, { 0x44, 5040 }, { 0x48, 5280 }, { 0x4C, 5520 }, { 0x50, 5760 }, { 0x54, 6000 }, { 0x58, 6240 }, { 0x5C, 6480 }, { 0x60, 6720 }, { 0x64, 6960 }, { 0x68, 7200 }, { 0x6C, 7440 }, { 0x74, 7920 }, { 0x7C, 8400 }, { 0x80, 8880 }, { 0x88, 9120 }, { 0x90, 9600 }, { 0x98, 10080 }, { 0xA0, 10560 }, { 0xA8, 10920 }, { 0xAC, 11160 }, { 0xB4, 11280 }, { 0xB8, 11400 }, { 0xBC, 11520 }, { 0xC6, 11760 }, { 0xCC, 12000 }
    };
    /** The VCF sustain value to the sustain level. */
    private static final double [][] VCF_SUSTAIN_LEVEL =
    {
        { 0x00, 0 }, { 0x05, 0.008 }, { 0x0A, 0.021 }, { 0x0C, 0.031 }, { 0x0E, 0.043 }, { 0x10, 0.066 }, { 0x11, 0.079 }, { 0x12, 0.092 }, { 0x13, 0.11 }, { 0x14, 0.132 }, { 0x15, 0.157 }, { 0x17, 0.235 }, { 0x19, 0.334 }, { 0x1A, 0.396 }, { 0x1C, 0.595 }, { 0x1E, 0.84 }, { 0x1F, 1 }
    };
    /** The VCA sustain value to the attenuation of the sustain in dB. */
    private static final double [][] VCA_SUSTAIN_DB   =
    {
        { 0x47, 138 }, { 0x5F, 132 }, { 0x67, 126 }, { 0x77, 120 }, { 0x7F, 114 }, { 0x8F, 102 }, { 0x97, 90 }, { 0x9F, 81 }, { 0xA7, 74 }, { 0xAF, 67 }, { 0xB7, 53 }, { 0xBF, 45 }, { 0xC7, 38 }, { 0xCF, 33 }, { 0xD7, 28 }, { 0xDF, 20 }, { 0xE7, 15 }, { 0xEF, 10 }, { 0xF7, 5 }, { 0xFF, 0 }
    };
    /** The LFO to pitch table index to cents. */
    private static final double [][] LFO_PITCH_CENTS  =
    {
        { 0, 0 }, { 1, 13 }, { 2, 20 }, { 3, 26 }, { 4, 29 }, { 5, 35 }, { 6, 39 }, { 7, 41 }, { 8, 45 }, { 15, 63 }
    };
    /** The LFO to filter table index to cents. */
    private static final double [][] LFO_FILTER_CENTS =
    {
        { 0, 0 }, { 1, 340 }, { 2, 544 }, { 3, 680 }, { 4, 782 }, { 6, 1020 }, { 9, 1292 }, { 11, 1428 }, { 12, 1496 }, { 15, 1700 }
    };
    /** The LFO to level table index to dB. */
    private static final double [][] LFO_LEVEL_DB     =
    {
        { 0, 0 }, { 1, 1.6 }, { 2, 2.5 }, { 3, 3.2 }, { 4, 3.6 }, { 5, 4.3 }, { 6, 4.8 }, { 7, 5.1 }, { 9, 6.0 }, { 15, 8.0 }
    };
    /**
     * The time of a VCF envelope stage for the time its counter takes on the VCA: the filter
     * envelope drives the cutoff through the exponential response of the filter, which EMXP
     * accounts for with this curve.
     */
    private static final double [][] VCF_TIME         =
    {
        { 0.01, 0.01 }, { 0.02, 0.02 }, { 0.03, 0.05 }, { 0.04, 0.09 }, { 0.05, 0.17 }, { 0.061, 0.25 }, { 0.082, 0.32 }, { 0.102, 0.45 }, { 0.121, 0.55 }, { 0.15, 0.65 }, { 0.182, 0.84 }, { 0.212, 0.93 }, { 0.283, 1.05 }, { 0.319, 1.15 }, { 0.425, 1.28 }, { 0.51, 1.45 }, { 0.637, 1.65 }, { 0.85, 1.8 }, { 1.02, 2.0 }, { 1.27, 2.2 }, { 1.7, 2.5 }, { 1.91, 2.8 }, { 2.55, 3.2 }, { 3.4, 3.6 }, { 3.83, 4.2 }, { 5.1, 5.1 }, { 6.38, 6.4 }, { 7.65, 8.2 }, { 10.2, 10.2 }, { 15.3, 15.3 }
    };


    /**
     * Private constructor since this is a utility class.
     */
    private Emulator2VoiceSettings ()
    {
        // Intentionally empty
    }


    /**
     * Apply the settings of a voice record to a zone.
     *
     * @param zone The zone
     * @param record The 256 bytes of the voice record
     */
    static void apply (final ISampleZone zone, final byte [] record)
    {
        // Tuning
        zone.setTuning (record[FINE_TUNE] * FINE_TUNE_CENTS / 100.0);

        // Level and velocity to level: entry 0 of the level table is the level at the highest
        // velocity, the last entry the level at the lowest one
        final int level = record[LEVEL_TABLE] & 0xFF;
        zone.setGain (-lookup (ATTENUATION_DB, level));
        final double velocityToLevel = lookup (VELOCITY_LEVEL_DB, level - (record[LEVEL_TABLE + TABLE_LAST_ENTRY] & 0xFF));
        zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (velocityToLevel / MAX_LEVEL_DEPTH_DB, -1, 1));

        // VCA envelope
        final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
        amplitudeEnvelope.setAttackTime (attackTime (record[VCA_ATTACK_HIGH] & 0xFF, record[VCA_ATTACK_LOW] & 0xFF));
        amplitudeEnvelope.setDecayTime (decayTime (record[VCA_DECAY_HIGH] & 0xFF, record[VCA_DECAY_LOW] & 0xFF));
        amplitudeEnvelope.setSustainLevel (Math.clamp (1 - lookup (VCA_SUSTAIN_DB, record[VCA_SUSTAIN] & 0xFF) / 100.0, 0, 1));
        amplitudeEnvelope.setReleaseTime (decayTime (record[VCA_RELEASE_HIGH] & 0xFF, record[VCA_RELEASE_LOW] & 0xFF));
        amplitudeEnvelope.setTimeVelocityTracking (((record[VELOCITY_TO_ATTACK] & 0xFF) >> 4) / 15.0);

        // LFO: one triangle LFO which the voice routes to the pitch, the filter and the level
        final double lfoRate = lfoRate (record[LFO_RATE] & 0xFF);
        final double lfoDelay = Math.max (0, (record[LFO_DELAY] & 0xFF) - 1) / 100.0;
        final double lfoToPitch = lookup (LFO_PITCH_CENTS, lfoTableIndex (record, LFO_TO_PITCH));
        if (lfoToPitch > 0)
        {
            final ILfoModulator modulator = zone.getPitchLfoModulator ();
            modulator.setDepth (lfoToPitch / IEnvelope.MAX_ENVELOPE_DEPTH);
            setLfo (modulator.getSource (), lfoRate, lfoDelay);
        }
        final double lfoToLevel = lookup (LFO_LEVEL_DB, lfoTableIndex (record, LFO_TO_LEVEL));
        if (lfoToLevel > 0)
        {
            final ILfoModulator modulator = zone.getAmplitudeLfoModulator ();
            modulator.setDepth (lfoToLevel / ILfoModulator.MAX_VOLUME_DEPTH);
            setLfo (modulator.getSource (), lfoRate, lfoDelay);
        }

        // Filter: a 4-pole low-pass, whose cutoff table holds the cutoff at the highest velocity
        // in its first entry and the cutoff at the lowest one in its last
        final int cutoff = record[CUTOFF_TABLE] & 0xFF;
        final double velocityToCutoff = VELOCITY_CUTOFF_CENTS_PER_UNIT * (cutoff - (record[CUTOFF_TABLE + TABLE_LAST_ENTRY] & 0xFF));
        final int amountValue = record[ENVELOPE_AMOUNT] & 0xFF;
        double envelopeAmount = amountValue == ENVELOPE_AMOUNT_NONE ? 0 : lookup (ENVELOPE_AMOUNT_CENTS, amountValue);
        if ((record[FLAGS] & FLAG_NEGATIVE_ENVELOPE) != 0)
            envelopeAmount = -envelopeAmount;
        final double lfoToFilter = lookup (LFO_FILTER_CENTS, lfoTableIndex (record, LFO_TO_FILTER));
        // An open filter stays out of the way, as it does in the SoundFont reader: the envelope,
        // the velocity and the LFO could only close it from there
        if (cutoff >= CUTOFF_OPEN)
            return;

        final int q = record[Q_TABLE] & 0xFF;
        final double resonance = q < Q_NONE ? 0 : lookup (RESONANCE_DB, q);
        final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 4, lookup (CUTOFF_HZ, cutoff), resonance / IFilter.MAX_RESONANCE);
        filter.setCutoffKeyTracking (Math.min (1, ((record[KEY_TRACKING] & 0xFF) >> 4) / 8.0));
        filter.getCutoffVelocityModulator ().setDepth (Math.clamp (velocityToCutoff / VELOCITY_CUTOFF_REFERENCE_CENTS, -1, 1));
        final IEnvelopeModulator envelopeModulator = filter.getCutoffEnvelopeModulator ();
        envelopeModulator.setDepth (envelopeAmount / IEnvelope.MAX_ENVELOPE_DEPTH);
        if (envelopeAmount != 0)
        {
            final IEnvelope filterEnvelope = envelopeModulator.getSource ();
            filterEnvelope.setAttackTime (vcfTime (attackTime (record[VCF_ATTACK_HIGH] & 0xFF, record[VCF_ATTACK_LOW] & 0xFF)));
            filterEnvelope.setDecayTime (vcfTime (decayTime (record[VCF_DECAY_HIGH] & 0xFF, record[VCF_DECAY_LOW] & 0xFF)));
            filterEnvelope.setSustainLevel (lookup (VCF_SUSTAIN_LEVEL, Math.min (record[VCF_SUSTAIN] & 0xFF, 0x1F)));
            filterEnvelope.setReleaseTime (vcfTime (decayTime (record[VCF_RELEASE_HIGH] & 0xFF, record[VCF_RELEASE_LOW] & 0xFF)));
        }
        if (lfoToFilter > 0)
        {
            final ILfoModulator modulator = filter.getCutoffLfoModulator ();
            modulator.setDepth (lfoToFilter / IEnvelope.MAX_ENVELOPE_DEPTH);
            setLfo (modulator.getSource (), lfoRate, lfoDelay);
        }
        zone.setFilter (filter);
    }


    /**
     * The rate of the LFO.
     *
     * @param value The rate byte
     * @return The rate in Hertz
     */
    private static double lfoRate (final int value)
    {
        return 0.0669 * Math.pow (2, value / 15.67);
    }


    /**
     * Set up the LFO of a modulator.
     *
     * @param lfo The LFO
     * @param rate The rate in Hertz
     * @param delay The delay in seconds
     */
    private static void setLfo (final ILfo lfo, final double rate, final double delay)
    {
        lfo.setWaveform (LfoWaveform.TRIANGLE);
        lfo.setRate (rate);
        lfo.setDelay (delay);
    }


    /**
     * The index into a LFO depth table, which the record holds as a pointer: the page above the
     * table page counts 4 entries.
     *
     * @param record The voice record
     * @param offset The position of the pointer
     * @return The index, 0 if the pointer does not point into the tables
     */
    private static int lfoTableIndex (final byte [] record, final int offset)
    {
        final int page = record[offset + 1] & 0xFF;
        if (page < LFO_TABLE_PAGE)
            return 0;
        return (page - LFO_TABLE_PAGE) * 4 + (record[offset] & 0xFF);
    }


    /**
     * The time of an attack stage: the counter increments a 255 step ramp by the low value every
     * 10 ms, the high value times.
     *
     * @param high The number of passes
     * @param low The increment
     * @return The time in seconds, 0 for an instant attack
     */
    private static double attackTime (final int high, final int low)
    {
        return low == 0 ? 0 : COUNTER_PASS * high / low;
    }


    /**
     * The time of a decay or release stage: the counter decrements by 256 less the low value every
     * 10 ms, the high value passes of 255 steps.
     *
     * @param high The number of passes
     * @param low The negative increment
     * @return The time in seconds
     */
    private static double decayTime (final int high, final int low)
    {
        // The counters 255 / 0 hold the level: no decay, or a release which never ends
        if (low == 0)
            return high == 0xFF ? HOLD_TIME : 0;
        return COUNTER_PASS * high / (256 - low);
    }


    /**
     * The time of a filter envelope stage.
     *
     * @param counterTime The time the counter takes on the VCA
     * @return The time in seconds
     */
    private static double vcfTime (final double counterTime)
    {
        if (counterTime <= 0)
            return 0;
        final double [] [] table = VCF_TIME;
        if (counterTime <= table[0][0])
            return table[0][1] * counterTime / table[0][0];
        for (int i = 1; i < table.length; i++)
            if (counterTime <= table[i][0])
            {
                // Interpolate on the logarithmic scales of both axes
                final double t = Math.log (counterTime / table[i - 1][0]) / Math.log (table[i][0] / table[i - 1][0]);
                return table[i - 1][1] * Math.pow (table[i][1] / table[i - 1][1], t);
            }
        return counterTime;
    }


    /**
     * Look up a value in a table of (key, value) pairs which is sorted by the key, interpolating
     * between the keys and clamping to the ends.
     *
     * @param table The table
     * @param key The key
     * @return The value
     */
    private static double lookup (final double [][] table, final double key)
    {
        if (key <= table[0][0])
            return table[0][1];
        for (int i = 1; i < table.length; i++)
            if (key <= table[i][0])
            {
                final double t = (key - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                return table[i - 1][1] + t * (table[i][1] - table[i - 1][1]);
            }
        return table[table.length - 1][1];
    }
}
