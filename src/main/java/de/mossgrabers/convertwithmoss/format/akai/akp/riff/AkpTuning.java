// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

/**
 * A S5000/S6000 tune chunk. Version 1 has 22 bytes, version 2 has 24 bytes.
 *
 * @author Jürgen Moßgraber
 */
public class AkpTuning extends AbstractAkpChunk
{
    /** The length of the Tuning structure (version 1). */
    private static final int LENGTH_TUNE   = 22;
    /** The length of the Tuning structure since version 2. */
    private static final int LENGTH_TUNE_2 = 24;

    private int              semitoneTune  = 0;
    private int              fineTune      = 0;
    private final int []     detunes       = new int [12];
    private int              pitchbendUp   = 2;
    private int              pitchbendDown = 2;
    private boolean          isBendHeld    = false;
    private int              aftertouch    = 0;


    /**
     * Default constructor.
     */
    public AkpTuning ()
    {
        super (AkpRiffChunkId.TUNE_ID, LENGTH_TUNE, 1);
    }


    /**
     * Get the semitone tuning of the program.
     *
     * @return The tuning in the range of [-36..36]
     */
    public int getSemitoneTune ()
    {
        return this.semitoneTune;
    }


    /**
     * Get the fine tuning of the program.
     *
     * @return The tuning in cents in the range of [-50..50]
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Get the detune of a note of the octave.
     *
     * @param note The note in the range of [0..11] which is C to B
     * @return The detune in cents in the range of [-50..50]
     */
    public int getDetune (final int note)
    {
        return this.detunes[note];
    }


    /**
     * Get the pitch-bend up range.
     *
     * @return The range in semi-tones in the range of [0..24]
     */
    public int getPitchbendUp ()
    {
        return this.pitchbendUp;
    }


    /**
     * Set the pitch-bend up range in semi-tones [0..24].
     *
     * @param pitchbendUp The pitch-bend up range in semi-tones [0..24]
     */
    public void setPitchbendUp (final int pitchbendUp)
    {
        this.pitchbendUp = pitchbendUp;
    }


    /**
     * Get the pitch-bend down range.
     *
     * @return The range in semi-tones in the range of [0..24]
     */
    public int getPitchbendDown ()
    {
        return this.pitchbendDown;
    }


    /**
     * Set the pitch-bend down range in semi-tones [0..24].
     *
     * @param pitchbendDown The pitch-bend down range in semi-tones [0..24]
     */
    public void setPitchbendDown (final int pitchbendDown)
    {
        this.pitchbendDown = pitchbendDown;
    }


    /**
     * Get the bend mode.
     *
     * @return True if HELD otherwise NORMAL
     */
    public boolean isBendHeld ()
    {
        return this.isBendHeld;
    }


    /**
     * Get the aftertouch pitch modulation.
     *
     * @return The modulation in semi-tones in the range of [-12..12]
     */
    public int getAftertouch ()
    {
        return this.aftertouch;
    }


    /** {@inheritDoc} */
    @Override
    protected void readData (final byte [] data)
    {
        this.semitoneTune = getSigned (data, 0x01);
        this.fineTune = getSigned (data, 0x02);
        for (int i = 0; i < this.detunes.length; i++)
            this.detunes[i] = getSigned (data, 0x03 + i);
        this.pitchbendUp = getUnsigned (data, 0x0F);
        this.pitchbendDown = getUnsigned (data, 0x10);
        this.isBendHeld = getBoolean (data, 0x11);
        this.aftertouch = getSigned (data, 0x12);
    }


    /** {@inheritDoc} */
    @Override
    protected int getSize (final int targetVersion)
    {
        return targetVersion >= 2 ? LENGTH_TUNE_2 : LENGTH_TUNE;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeDefaults (final byte [] data, final int targetVersion)
    {
        // Undocumented, mostly 1 since version 2
        if (targetVersion >= 2)
            data[0x14] = 1;
    }


    /** {@inheritDoc} */
    @Override
    protected void writeData (final byte [] data, final int targetVersion)
    {
        setByte (data, 0x01, this.semitoneTune);
        setByte (data, 0x02, this.fineTune);
        for (int i = 0; i < this.detunes.length; i++)
            setByte (data, 0x03 + i, this.detunes[i]);
        setByte (data, 0x0F, this.pitchbendUp);
        setByte (data, 0x10, this.pitchbendDown);
        setBoolean (data, 0x11, this.isBendHeld);
        setByte (data, 0x12, this.aftertouch);
    }
}
