// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 key-group location chunk (kloc).
 *
 * @author Jürgen Moßgraber
 */
public class AkpKeygroupLocation extends AbstractAkpChunk
{
    /** The length of the Location structure. */
    private static final int LENGTH_KLOC     = 16;
    /** The lowest note of a key-group. */
    public static final int  LOWEST_NOTE     = 21;

    private int              lowNote         = 21;
    private int              highNote        = 127;
    private int              semitoneTune    = 0;
    private int              fineTune        = 0;
    private int              overrideFx      = 0;
    private int              fxSendLevel     = 0;
    private int              pitchMod1       = 100;
    private int              pitchMod2       = 0;
    private int              ampMod          = 0;
    private boolean          isZoneCrossfade = false;
    private int              muteGroup       = 0;


    /**
     * Default constructor.
     */
    public AkpKeygroupLocation ()
    {
        super (AkpRiffChunkId.KGRP_LOC_ID, LENGTH_KLOC, 1);
    }


    /**
     * Get the lowest note of the key-group.
     *
     * @return The note in the range of [21..127]
     */
    public int getLowNote ()
    {
        return this.lowNote;
    }


    /**
     * Set the lowest note of the key-group [21..127].
     *
     * @param lowNote The lowest note of the key-group [21..127]
     */
    public void setLowNote (final int lowNote)
    {
        this.lowNote = lowNote;
    }


    /**
     * Get the highest note of the key-group.
     *
     * @return The note in the range of [21..127]
     */
    public int getHighNote ()
    {
        return this.highNote;
    }


    /**
     * Set the highest note of the key-group [21..127].
     *
     * @param highNote The highest note of the key-group [21..127]
     */
    public void setHighNote (final int highNote)
    {
        this.highNote = highNote;
    }


    /**
     * Get the semitone tuning of the key-group.
     *
     * @return The tuning in the range of [-36..36]
     */
    public int getSemitoneTune ()
    {
        return this.semitoneTune;
    }


    /**
     * Get the fine tuning of the key-group.
     *
     * @return The tuning in cents in the range of [-50..50]
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the effect override.
     *
     * @return 0 = OFF, 1 = FX1, 2 = FX2, 3 = RV3, 4 = RV4
     */
    public int getOverrideFx ()
    {
        return this.overrideFx;
    }


    /**
     * Get the effect send level.
     *
     * @return The level in the range of [0..100]
     */
    public int getFxSendLevel ()
    {
        return this.fxSendLevel;
    }


    /**
     * Get the amount of the pitch modulation 1. The source is set in the modulations chunk.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getPitchMod1 ()
    {
        return this.pitchMod1;
    }


    /**
     * Set the amount of the pitch modulation 1 in the range of [-100..100].
     *
     * @param pitchMod1 The amount of the pitch modulation 1 in the range of [-100..100]
     */
    public void setPitchMod1 (final int pitchMod1)
    {
        this.pitchMod1 = pitchMod1;
    }


    /**
     * Get the amount of the pitch modulation 2. The source is set in the modulations chunk.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getPitchMod2 ()
    {
        return this.pitchMod2;
    }


    /**
     * Set the amount of the pitch modulation 2 in the range of [-100..100].
     *
     * @param pitchMod2 The amount of the pitch modulation 2 in the range of [-100..100]
     */
    public void setPitchMod2 (final int pitchMod2)
    {
        this.pitchMod2 = pitchMod2;
    }


    /**
     * Get the amount of the amplitude modulation. The source is set in the modulations chunk.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getAmpMod ()
    {
        return this.ampMod;
    }


    /**
     * Set the amount of the amplitude modulation in the range of [-100..100].
     *
     * @param ampMod The amount of the amplitude modulation in the range of [-100..100]
     */
    public void setAmpMod (final int ampMod)
    {
        this.ampMod = ampMod;
    }


    /**
     * Is the cross-fade between the velocity zones enabled?
     *
     * @return True if enabled
     */
    public boolean isZoneCrossfade ()
    {
        return this.isZoneCrossfade;
    }


    /**
     * Get the mute group.
     *
     * @return The mute group, 0 = OFF
     */
    public int getMuteGroup ()
    {
        return this.muteGroup;
    }


    /**
     * Set the mute group, 0 = OFF.
     *
     * @param muteGroup The mute group, 0 = OFF
     */
    public void setMuteGroup (final int muteGroup)
    {
        this.muteGroup = muteGroup;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.lowNote = getUnsigned (data, 0x04);
        this.highNote = getUnsigned (data, 0x05);
        this.semitoneTune = getSigned (data, 0x06);
        this.fineTune = getSigned (data, 0x07);
        this.overrideFx = getUnsigned (data, 0x08);
        this.fxSendLevel = getUnsigned (data, 0x09);
        this.pitchMod1 = getSigned (data, 0x0A);
        this.pitchMod2 = getSigned (data, 0x0B);
        this.ampMod = getSigned (data, 0x0C);
        this.isZoneCrossfade = getBoolean (data, 0x0D);
        this.muteGroup = getUnsigned (data, 0x0E);
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Undocumented but always 3, 1, 4
        data[0x01] = 3;
        data[0x02] = 1;
        data[0x03] = 4;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 0x04, this.lowNote);
        setByte (data, 0x05, this.highNote);
        setByte (data, 0x06, this.semitoneTune);
        setByte (data, 0x07, this.fineTune);
        setByte (data, 0x08, this.overrideFx);
        setByte (data, 0x09, this.fxSendLevel);
        setByte (data, 0x0A, this.pitchMod1);
        setByte (data, 0x0B, this.pitchMod2);
        setByte (data, 0x0C, this.ampMod);
        setBoolean (data, 0x0D, this.isZoneCrossfade);
        setByte (data, 0x0E, this.muteGroup);
    }
}
