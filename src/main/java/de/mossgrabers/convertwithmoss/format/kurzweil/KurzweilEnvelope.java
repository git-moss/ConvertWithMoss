// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kurzweil;

import de.mossgrabers.convertwithmoss.core.model.IEnvelope;


/**
 * An envelope of a Kurzweil program layer. It consists of 7 stages, each with a duration and a
 * target level: 3 attack stages, 1 decay stage and 3 release stages. In a program segment the
 * stages are stored as 7 (time, level) byte pairs from byte 0; byte 14 holds a loop mode which is
 * not supported. A time byte holds the number of steps on the non-linear editor time grid of the
 * device plus 3, a level byte the target level as a signed percentage.
 *
 * @author Jürgen Moßgraber
 */
public class KurzweilEnvelope
{
    private static final int          NUM_STAGES  = 7;
    private static final int          STAGE_DECAY = 3;

    /**
     * The non-linear grid on which the device edits envelope times. Each row is one section: up to
     * the given number of seconds one editor step lengthens the time by the step size.
     */
    private static final double [] [] TIME_GRID   =
    {
        {
            2,
            0.02
        },
        {
            5,
            0.04
        },
        {
            10,
            0.10
        },
        {
            15,
            0.50
        },
        {
            25,
            1.0
        },
        {
            60,
            5.0
        }
    };

    private final double []           times       = new double [NUM_STAGES];
    private final int []              levels      = new int [NUM_STAGES];


    /**
     * Constructor for an empty envelope.
     */
    public KurzweilEnvelope ()
    {
        // Intentionally empty
    }


    /**
     * Constructor. Decodes the envelope from the data of a program segment.
     *
     * @param data The segment data with the 7 (time, level) pairs from byte 0
     */
    public KurzweilEnvelope (final byte [] data)
    {
        for (int stage = 0; stage < NUM_STAGES; stage++)
        {
            this.times[stage] = decodeTime (data[stage * 2] & 0xFF);
            this.levels[stage] = data[stage * 2 + 1];
        }
    }


    /**
     * Encode the envelope into the data of a program segment.
     *
     * @param data The segment data to fill with the 7 (time, level) pairs from byte 0
     */
    public void write (final byte [] data)
    {
        for (int stage = 0; stage < NUM_STAGES; stage++)
        {
            data[stage * 2] = (byte) encodeTime (this.times[stage]);
            data[stage * 2 + 1] = (byte) Math.clamp (this.levels[stage], -100, 100);
        }
    }


    /**
     * Set the stages from a model envelope: an optional delay stage which stays at level 0, the
     * attack ramp to the hold level, a hold stage, the decay to the sustain level and one release
     * stage back to level 0.
     *
     * @param envelope The source envelope
     */
    public void fromEnvelope (final IEnvelope envelope)
    {
        final double holdLevel = envelope.getHoldLevel ();
        final int peak = (int) Math.round ((holdLevel < 0 ? 1 : holdLevel) * 100);
        final double sustainLevel = envelope.getSustainLevel ();

        final double delay = Math.max (0, envelope.getDelayTime ());
        int stage = 0;
        if (delay > 0)
        {
            this.times[stage] = delay;
            this.levels[stage] = 0;
            stage++;
        }
        this.times[stage] = Math.max (0, envelope.getAttackTime ());
        this.levels[stage] = peak;
        stage++;
        this.times[stage] = Math.max (0, envelope.getHoldTime ());
        this.levels[stage] = peak;
        stage++;
        if (stage < STAGE_DECAY)
        {
            this.times[stage] = 0;
            this.levels[stage] = peak;
        }

        this.times[STAGE_DECAY] = Math.max (0, envelope.getDecayTime ());
        this.levels[STAGE_DECAY] = (int) Math.round ((sustainLevel < 0 ? 1 : sustainLevel) * 100);

        this.times[STAGE_DECAY + 1] = Math.max (0, envelope.getReleaseTime ());
        this.levels[STAGE_DECAY + 1] = 0;
        // The remaining release stages stay at zero
    }


    /**
     * Fill a model envelope from the stages: leading attack stages which stay at level 0 form the
     * delay, the following stages up to the peak level the attack and the remaining attack stages
     * the hold. The decay stage sets the decay time and sustain level, the release stages up to the
     * first which reaches level 0 sum to the release time.
     *
     * @param envelope The envelope to fill
     */
    public void toEnvelope (final IEnvelope envelope)
    {
        int peak = 0;
        for (int stage = 0; stage < STAGE_DECAY; stage++)
            peak = Math.max (peak, this.levels[stage]);

        if (peak > 0)
        {
            int stage = 0;
            double delay = 0;
            while (stage < STAGE_DECAY && this.levels[stage] <= 0)
            {
                delay += this.times[stage];
                stage++;
            }
            double attack = 0;
            while (stage < STAGE_DECAY)
            {
                attack += this.times[stage];
                final boolean reachedPeak = this.levels[stage] >= peak;
                stage++;
                if (reachedPeak)
                    break;
            }
            double hold = 0;
            while (stage < STAGE_DECAY)
            {
                hold += this.times[stage];
                stage++;
            }
            envelope.setDelayTime (delay);
            envelope.setAttackTime (attack);
            envelope.setHoldTime (hold);
            envelope.setHoldLevel (peak / 100.0);
        }

        envelope.setDecayTime (this.times[STAGE_DECAY]);
        envelope.setSustainLevel (Math.clamp (this.levels[STAGE_DECAY], 0, 100) / 100.0);

        double release = 0;
        for (int stage = STAGE_DECAY + 1; stage < NUM_STAGES; stage++)
        {
            release += this.times[stage];
            if (this.levels[stage] <= 0)
                break;
        }
        envelope.setReleaseTime (release);
        envelope.setEndLevel (0);
    }


    /**
     * Decode a time byte into seconds by walking the time grid.
     *
     * @param timeByte The time byte: the number of grid steps plus 3
     * @return The time in seconds
     */
    public static double decodeTime (final int timeByte)
    {
        double steps = Math.max (0, timeByte - 3);
        double seconds = 0;
        for (final double [] section: TIME_GRID)
        {
            final double sectionSteps = (section[0] - seconds) / section[1];
            if (steps <= sectionSteps)
                return seconds + steps * section[1];
            steps -= sectionSteps;
            seconds = section[0];
        }
        return seconds;
    }


    /**
     * Encode a time in seconds into a time byte by walking the time grid.
     *
     * @param seconds The time in seconds
     * @return The time byte in the range of 3..255
     */
    public static int encodeTime (final double seconds)
    {
        final double limited = Math.clamp (seconds, 0, 60);
        double steps = 0;
        double sectionStart = 0;
        for (final double [] section: TIME_GRID)
        {
            steps += (Math.min (limited, section[0]) - sectionStart) / section[1];
            if (limited <= section[0])
                break;
            sectionStart = section[0];
        }
        return Math.clamp ((int) Math.round (steps) + 3L, 3, 255);
    }
}
