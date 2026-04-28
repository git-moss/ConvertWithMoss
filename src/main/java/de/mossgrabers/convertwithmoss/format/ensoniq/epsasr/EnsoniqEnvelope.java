// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ensoniq.epsasr;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * An envelope in a Ensoniq EPS / EPS-16+ / ASR instrument wave-sample.
 *
 * @author Jürgen Moßgraber
 */
public class EnsoniqEnvelope
{
    private final int envelopeType;
    private final int softLevel0;
    private final int hardLevel0;
    private final int time1;
    private final int softLevel1;
    private final int hardLevel1;
    private final int time2;
    private final int softLevel2;
    private final int hardLevel2;
    private final int time3;
    private final int softLevel3;
    private final int hardLevel3;
    private final int time4;
    private final int softLevel4;
    private final int hardLevel4;
    private final int time5;
    private final int velocitySwitch;
    private final int level5;
    private final int time6;
    private final int time1VelSens;
    private final int kbTimeScaling;
    private final int mode;


    /**
     * Constructor.
     * 
     * @param input The stream from which to read the envelope data
     * @throws IOException Could not read the envelope
     */
    public EnsoniqEnvelope (final InputStream input) throws IOException
    {
        this.envelopeType = StreamUtils.readUnsigned8FromWord (input);
        this.softLevel0 = StreamUtils.readUnsigned8FromWord (input);
        this.hardLevel0 = StreamUtils.readUnsigned8FromWord (input);
        this.time1 = StreamUtils.readUnsigned8FromWord (input);
        this.softLevel1 = StreamUtils.readUnsigned8FromWord (input);
        this.hardLevel1 = StreamUtils.readUnsigned8FromWord (input);
        this.time2 = StreamUtils.readUnsigned8FromWord (input);
        this.softLevel2 = StreamUtils.readUnsigned8FromWord (input);
        this.hardLevel2 = StreamUtils.readUnsigned8FromWord (input);
        this.time3 = StreamUtils.readUnsigned8FromWord (input);
        this.softLevel3 = StreamUtils.readUnsigned8FromWord (input);
        this.hardLevel3 = StreamUtils.readUnsigned8FromWord (input);
        this.time4 = StreamUtils.readUnsigned8FromWord (input);
        this.softLevel4 = StreamUtils.readUnsigned8FromWord (input);
        this.hardLevel4 = StreamUtils.readUnsigned8FromWord (input);
        this.time5 = StreamUtils.readUnsigned8FromWord (input);
        this.velocitySwitch = StreamUtils.readUnsigned8FromWord (input);
        this.level5 = StreamUtils.readSigned8FromWord (input);
        this.time6 = StreamUtils.readUnsigned8FromWord (input);
        this.time1VelSens = StreamUtils.readUnsigned8FromWord (input);
        this.kbTimeScaling = StreamUtils.readUnsigned8FromWord (input);
        this.mode = StreamUtils.readUnsigned8FromWord (input);
    }


    /**
     * Get the envelope type.
     * 
     * @return The envelope type, default envelopes: 0-15
     */
    public int getEnvelopeType ()
    {
        return this.envelopeType;
    }


    /**
     * Get the soft level 0
     * 
     * @return Initial level: 0-127
     */
    public int getSoftLevel0 ()
    {
        return this.softLevel0;
    }


    /**
     * Get the hard level 0
     * 
     * @return Initial level: 0-127
     */
    public int getHardLevel0 ()
    {
        return this.hardLevel0;
    }


    /**
     * Get time 1.
     * 
     * @return Attack time; time from initial level to level: 0-127
     */
    public int getTime1 ()
    {
        return this.time1;
    }


    /**
     * Get the soft level 1.
     * 
     * @return Peak level: 0-127
     */
    public int getSoftLevel1 ()
    {
        return this.softLevel1;
    }


    /**
     * Get the hard level 1.
     * 
     * @return Peak level: 0-127
     */
    public int getHardLevel1 ()
    {
        return this.hardLevel1;
    }


    /**
     * Get the time 2.
     * 
     * @return First decay time; time from level 1 to level 2: 0-127
     */
    public int getTime2 ()
    {
        return this.time2;
    }


    /**
     * Get the soft level 2.
     * 
     * @return 0-127
     */
    public int getSoftLevel2 ()
    {
        return this.softLevel2;
    }


    /**
     * Get the hard level 2.
     * 
     * @return 0-127
     */
    public int getHardLevel2 ()
    {
        return this.hardLevel2;
    }


    /**
     * Get the time 3.
     * 
     * @return Second decay; time from level 2 to level 3: 0-127
     */
    public int getTime3 ()
    {
        return this.time3;
    }


    /**
     * Get the soft level 3.
     * 
     * @return 0-127
     */
    public int getSoftLevel3 ()
    {
        return this.softLevel3;
    }


    /**
     * Get the hard level 3.
     * 
     * @return 0-127
     */
    public int getHardLevel3 ()
    {
        return this.hardLevel3;
    }


    /**
     * Get the time 4.
     * 
     * @return Third decay; time from level 3 to level 4: 0-127
     */
    public int getTime4 ()
    {
        return this.time4;
    }


    /**
     * Get the soft level 4 (sustain).
     * 
     * @return Sustain: 0-127
     */
    public int getSoftLevel4 ()
    {
        return this.softLevel4;
    }


    /**
     * Get the hard level 4 (sustain).
     * 
     * @return Sustain: 0-127
     */
    public int getHardLevel4 ()
    {
        return this.hardLevel4;
    }


    /**
     * Get the time 5 (release time).
     * 
     * @return Time from level 4 to level 5: 0-127
     */
    public int getTime5 ()
    {
        return this.time5;
    }


    /**
     * Soft level on/off.
     * 
     * @return 0/1
     */
    public int getVelocitySwitch ()
    {
        return this.velocitySwitch;
    }


    /**
     * Get the level 5.
     * 
     * @return Release breakpoint relative to sustain level (+/-): -127..127
     */
    public int getLevel5 ()
    {
        return this.level5;
    }


    /**
     * Get time 6.
     * 
     * @return Second release time; time from level 5 to 6
     */
    public int getTime6 ()
    {
        return this.time6;
    }


    /**
     * Get the time 1 velocity sensitivity.
     * 
     * @return Time 1 velocity sensitivity: 0-127
     */
    public int getTime1VelSens ()
    {
        return this.time1VelSens;
    }


    /**
     * Get the keyboard time scaling.
     * 
     * @return Keyboard Time Scaling: 0-127
     */
    public int getKbTimeScaling ()
    {
        return this.kbTimeScaling;
    }


    /**
     * Get the play mode.
     * 
     * @return 0=normal, 1=cycle, 2=repeat
     */
    public int getMode ()
    {
        return this.mode;
    }
}
