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
     * Get the highest note of the key-group.
     *
     * @return The note in the range of [21..127]
     */
    public int getHighNote ()
    {
        return this.highNote;
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
     * Get the amount of the pitch modulation 2. The source is set in the modulations chunk.
     *
     * @return The amount in the range of [-100..100]
     */
    public int getPitchMod2 ()
    {
        return this.pitchMod2;
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
}
