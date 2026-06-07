// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 performance.
 *
 * @author Jürgen Moßgraber
 */
public class S770Performance
{
    private final String     performanceName;
    private final int []     partsPatchSelection;
    private final int []     midiChannel;
    private final boolean [] midiEnabled;
    private final int []     partsLevel;
    private final int []     partsZoneLower;
    private final int []     partsZoneUpper;
    private final int []     partsFadeWidthLower;
    private final int []     partsFadeWidthUpper;
    private final int        partsProgramChange;
    private final int        partsPitchBend;
    private final int        partsModulation;
    private final int        partsHoldPedal;
    private final int        partsBendRange;
    private final int        partsMidiVolume;
    private final int        partsAfterTouchSwitch;
    private final int        partsAfterTouchMode;
    private final int []     velocityCurveTypeData;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @param isDiskette True if this is stored on a diskette otherwise HD/CD-ROM
     * @throws IOException Could not read the performance
     */
    public S770Performance (final InputStream input, final boolean isDiskette) throws IOException
    {
        this.performanceName = StreamUtils.readAscii (input, 16);

        this.partsPatchSelection = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsPatchSelection[i] = StreamUtils.readSigned8 (input);

        this.midiChannel = new int [16];
        for (int i = 0; i < 16; i++)
            this.midiChannel[i] = StreamUtils.readUnsigned8 (input);

        this.midiEnabled = new boolean [32];
        this.partsLevel = new int [32];
        for (int i = 0; i < 32; i++)
        {
            final int value = StreamUtils.readSigned8 (input);
            this.midiEnabled[i] = (value & 0x80) > 0;
            this.partsLevel[i] = value & 0x7F;
        }

        this.partsZoneLower = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsZoneLower[i] = StreamUtils.readUnsigned8 (input);

        this.partsZoneUpper = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsZoneUpper[i] = StreamUtils.readUnsigned8 (input);

        this.partsFadeWidthLower = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsFadeWidthLower[i] = StreamUtils.readUnsigned8 (input);

        this.partsFadeWidthUpper = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsFadeWidthUpper[i] = StreamUtils.readUnsigned8 (input);

        this.partsProgramChange = StreamUtils.readSigned16 (input, false);
        this.partsPitchBend = StreamUtils.readSigned16 (input, false);
        this.partsModulation = StreamUtils.readSigned16 (input, false);
        this.partsHoldPedal = StreamUtils.readSigned16 (input, false);
        this.partsBendRange = StreamUtils.readSigned16 (input, false);
        this.partsMidiVolume = StreamUtils.readSigned16 (input, false);
        this.partsAfterTouchSwitch = StreamUtils.readSigned16 (input, false);
        this.partsAfterTouchMode = StreamUtils.readSigned16 (input, false);

        this.velocityCurveTypeData = new int [16];
        for (int i = 0; i < 16; i++)
            this.velocityCurveTypeData[i] = StreamUtils.readUnsigned8 (input);

        if (isDiskette)
            return;

        for (int i = 0; i < 32; i++)
            this.partsPatchSelection[i] = StreamUtils.readSigned16 (input, false);
        input.skipNBytes (0xC0);
    }


    /**
     * Get the name of the performance.
     *
     * @return The performance name
     */
    public String getPerformanceName ()
    {
        return this.performanceName;
    }


    /**
     * Get the indices of the selected parts.
     *
     * @return The part indices, 0 = Off, 1-127
     */
    public int [] getPartsPatchSelection ()
    {
        return this.partsPatchSelection;
    }


    /**
     * Get the assigned MIDI channels.
     *
     * @return The MIDI channels, Off, 1-16
     */
    public int [] getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Is MIDI enabled?
     *
     * @return True if enabled
     */
    public boolean [] isMidiEnabled ()
    {
        return this.midiEnabled;
    }


    /**
     * Get the level of the parts.
     *
     * @return The levels in the range of 0-127
     */
    public int [] getPartsLevel ()
    {
        return this.partsLevel;
    }


    /**
     * Get the the lower zones of the parts.
     *
     * @return The lower zones in the range of 21(A0)..108(C8)
     */
    public int [] getPartsZoneLower ()
    {
        return this.partsZoneLower;
    }


    /**
     * Get the upper zones of the parts.
     *
     * @return The upper zones in the range of 21(A0)..108(C8)
     */
    public int [] getPartsZoneUpper ()
    {
        return this.partsZoneUpper;
    }


    /**
     * Get the fades of the lower zones.
     *
     * @return The fades in the range of 0..Lower
     */
    public int [] getPartsFadeWidthLower ()
    {
        return this.partsFadeWidthLower;
    }


    /**
     * Get the fades of the upper zones.
     *
     * @return The fades in the range of Upper..127
     */
    public int [] getPartsFadeWidthUpper ()
    {
        return this.partsFadeWidthUpper;
    }


    /**
     * Get the activation state of Program Change.
     *
     * @return Off/on
     */
    public int getPartsProgramChange ()
    {
        return this.partsProgramChange;
    }


    /**
     * Get the activation state of pitch bend.
     *
     * @return Off/on
     */
    public int getPartsPitchBend ()
    {
        return this.partsPitchBend;
    }


    /**
     * Get the activation state of the modulation wheel.
     *
     * @return Off/on
     */
    public int getPartsModulation ()
    {
        return this.partsModulation;
    }


    /**
     * Get the activation state of the hold-pedal.
     *
     * @return Off/on
     */
    public int getPartsHoldPedal ()
    {
        return this.partsHoldPedal;
    }


    /**
     * Get the activation state of the bend range.
     *
     * @return Off/on
     */
    public int getPartsBendRange ()
    {
        return this.partsBendRange;
    }


    /**
     * Get the activation state of the MIDI volume.
     *
     * @return Off/on
     */
    public int getPartsMidiVolume ()
    {
        return this.partsMidiVolume;
    }


    /**
     * Get the activation state of the after-touch.
     *
     * @return Off/on
     */
    public int getPartsAfterTouchSwitch ()
    {
        return this.partsAfterTouchSwitch;
    }


    /**
     * Get the after-touch mode.
     *
     * @return The after-touch mode: off, channel, polyphonic
     */
    public int getPartsAfterTouchMode ()
    {
        return this.partsAfterTouchMode;
    }


    /**
     * Get the velocity curve type.
     *
     * @return The velocity curve type in the range of 0..7
     */
    public int [] getVelocityCurveType ()
    {
        return this.velocityCurveTypeData;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770PerformanceParameter [\n  performanceName='" + this.performanceName.trim () + "'\n  partsPatchSelection=" + Arrays.toString (this.partsPatchSelection) + "\n  midiChannel=" + Arrays.toString (this.midiChannel) + "\n  midiEnabled=" + Arrays.toString (this.midiEnabled) + "\n  partsLevel=" + Arrays.toString (this.partsLevel) + "\n  partsZoneLower=" + Arrays.toString (this.partsZoneLower) + "\n  partsZoneUpper=" + Arrays.toString (this.partsZoneUpper) + "\n  partsFadeWidthLower=" + Arrays.toString (this.partsFadeWidthLower) + "\n  partsFadeWidthUpper=" + Arrays.toString (this.partsFadeWidthUpper) + "\n  partsProgramChange=" + this.partsProgramChange + "\n  partsPitchBend=" + this.partsPitchBend + "\n  partsModulation=" + this.partsModulation + "\n  partsHoldPedal=" + this.partsHoldPedal + "\n  partsBendRange=" + this.partsBendRange + "\n  partsMidiVolume=" + this.partsMidiVolume + "\n  partsAfterTouchSwitch=" + this.partsAfterTouchSwitch + "\n  partsAfterTouchMode=" + this.partsAfterTouchMode + "\n  velocityCurveTypeData=" + Arrays.toString (this.velocityCurveTypeData) + "\n]";
    }
}