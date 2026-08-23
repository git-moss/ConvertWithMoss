// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s1xx;

import de.mossgrabers.tools.ui.Functions;


/**
 * S-10 performance parameter block. Applies to all samples.
 *
 * @author Jürgen Moßgraber
 */
public final class PerformanceParameters
{
    /** Total size in bytes of one performance parameter block. */
    public static final int PERFORMANCE_PARAMETER_BYTES = 40;

    /** Vib Rate. Range 0-127. */
    public int              vibratoRate;
    /** Manual Vibrato Depth (the modulation bender is used). Range 0-127. */
    public int              manualVibratoDepth;
    /** Delay Vibrato Depth (kicks in after a certain time). Range 0-127. */
    public int              delayVibratoDepth;
    /** Delay Time of the Vibrato Delay. Range 0-127. */
    public int              delayTimeVibratoDelay;
    /** Bend Mode. 0 (Continuous), 1 (Chromatic). */
    public int              benderMode;

    // Bend range ?

    /** Arpeggio Rate. Range 0-127. */
    public int              arpeggioRate;
    /** Arpeggio Mode. 0 (Up), 1 (Down), 2 (Up/Down), 3 (Random). */
    public int              arpeggioMode;
    /** Arpeggio Range. 0 (1 octave), 1 (2 octave), 2 (3 octave). */
    public int              arpeggioRange;
    /** Arpeggio Repeat. Range 1-16. */
    public int              arpeggioRepeat;
    /** Arpeggio Decay. Range 1-10. */
    public int              arpeggioDecay;

    /** Arpeggio Sync. 0 (Internal Clock), 1 (External trigger). */
    public int              arpeggioSync;
    /** Trigger Gate-Time. Range 0-127. */
    public int              triggerGateTime;
    /** External Gate Play, first gate. Range 23 (Off), 24 (c1) to 103 (g7). */
    public int              externalGatePlay1;
    /** External Gate Play, second gate. Range 23 (Off), 24 (c1) to 103 (g7). */
    public int              externalGatePlay2;
    /** External Gate Play, third gate. Range 23 (Off), 24 (c1) to 103 (g7). */
    public int              externalGatePlay3;
    /** External Gate Play, fourth gate. Range 23 (Off), 24 (c1) to 103 (g7). */
    public int              externalGatePlay4;

    /** Detune Mode. 0 (Fixed), 1 (Velocity). */
    public int              rangeVelocitySNS;
    /** Detune Range. Range 0-127. */
    public int              detuneRangeOfDetuneMode;

    /** Velocity Mix Threshold. Range 0-127. */
    public int              velocityMixThreshold;
    /** Velocity Switch Threshold. Range 0-127. */
    public int              velocitySwitchThreshold;

    /** Auto Bend Destination. 0 (Both), 1 (Half). */
    public int              autoBendDestinationOfDetuneMode;
    /** Pitch Bend Destination. 0 (Both), 1 (Half). */
    public int              bendDestinationOfDetuneMode;
    /** Delay Time. Range 0-127. */
    public int              delayTime;
    /** Delay Level. Range 0-127. */
    public int              delayLevel;
    /** Key Offset. Range -12 to +12. */
    public int              keyOffset;


    /**
     * Creates a performance parameter block from a full 40-byte transport array.
     *
     * @param bytes The 40 raw performance parameter bytes
     */
    public PerformanceParameters (final int [] bytes)
    {
        if (bytes.length != PERFORMANCE_PARAMETER_BYTES)
            throw new IllegalArgumentException (Functions.getMessage ("IDS_S1X_SYSEX_PERFORMANCE_WRONG_LENGTH"));
        this.decodeFields (bytes);
    }


    private void decodeFields (final int [] bytes)
    {
        this.externalGatePlay1 = SysExMessage.decodeTwoNibbles (bytes, 0);
        this.externalGatePlay2 = SysExMessage.decodeTwoNibbles (bytes, 2);
        this.externalGatePlay3 = SysExMessage.decodeTwoNibbles (bytes, 4);
        this.externalGatePlay4 = SysExMessage.decodeTwoNibbles (bytes, 6);
        this.triggerGateTime = SysExMessage.decodeTwoNibbles (bytes, 8);

        this.arpeggioRate = SysExMessage.decodeTwoNibbles (bytes, 10);
        this.arpeggioSync = (bytes[12] >> 2) & 3;
        this.arpeggioMode = (bytes[13] >> 2) & 3;
        this.arpeggioRange = bytes[13] & 3;
        this.arpeggioRepeat = SysExMessage.decodeTwoNibbles (bytes, 14);
        this.arpeggioDecay = SysExMessage.decodeTwoNibbles (bytes, 16);

        this.vibratoRate = SysExMessage.decodeTwoNibbles (bytes, 18);
        this.manualVibratoDepth = SysExMessage.decodeTwoNibbles (bytes, 20);
        this.delayVibratoDepth = SysExMessage.decodeTwoNibbles (bytes, 22);
        this.delayTimeVibratoDelay = SysExMessage.decodeTwoNibbles (bytes, 24);
        this.delayTime = SysExMessage.decodeTwoNibbles (bytes, 26);
        this.delayLevel = SysExMessage.decodeTwoNibbles (bytes, 28);
        // Signed
        this.keyOffset = (byte) SysExMessage.decodeTwoNibbles (bytes, 30);

        this.detuneRangeOfDetuneMode = SysExMessage.decodeTwoNibbles (bytes, 32);
        this.velocityMixThreshold = SysExMessage.decodeTwoNibbles (bytes, 34);
        this.velocitySwitchThreshold = SysExMessage.decodeTwoNibbles (bytes, 36);

        this.autoBendDestinationOfDetuneMode = (bytes[38] >> 3) & 1;
        this.bendDestinationOfDetuneMode = (bytes[38] >> 2) & 1;
        this.benderMode = (bytes[38] >> 1) & 1;
        this.rangeVelocitySNS = bytes[38] & 1;

        // More data available on S-220...
    }
}