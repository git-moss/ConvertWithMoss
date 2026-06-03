// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 performance parameter (256 bytes).
 *
 * @author Jürgen Moßgraber
 */
public class S770Performance
{
    private final String           performanceName;
    private final int []           partsPatchSelection;
    private final int []           midiChannelData;
    private final S770PartLevel [] partsLevel;
    private final int []           partsZoneLower;
    private final int []           partsZoneUpper;
    private final int []           partsFadeWidthLower;
    private final int []           partsFadeWidthUpper;
    private final int              partsProgramChange;
    private final int              partsPitchBend;
    private final int              partsModulation;
    private final int              partsHoldPedal;
    private final int              partsBendRange;
    private final int              partsMidiVolume;
    private final int              partsAfterTouchSwitch;
    private final int              partsAfterTouchMode;
    private final int []           velocityCurveTypeData;

    private final int []           patchList;


    public S770Performance (final InputStream in) throws IOException
    {
        this.performanceName = StreamUtils.readAscii (in, 16);

        this.partsPatchSelection = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsPatchSelection[i] = StreamUtils.readSigned8 (in);

        this.midiChannelData = new int [16];
        for (int i = 0; i < 16; i++)
            this.midiChannelData[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.partsLevel = new S770PartLevel [32];
        for (int i = 0; i < 32; i++)
            this.partsLevel[i] = new S770PartLevel (in);

        this.partsZoneLower = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsZoneLower[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.partsZoneUpper = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsZoneUpper[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.partsFadeWidthLower = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsFadeWidthLower[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.partsFadeWidthUpper = new int [32];
        for (int i = 0; i < 32; i++)
            this.partsFadeWidthUpper[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.partsProgramChange = StreamUtils.readUnsigned16 (in, false);
        this.partsPitchBend = StreamUtils.readUnsigned16 (in, false);
        this.partsModulation = StreamUtils.readUnsigned16 (in, false);
        this.partsHoldPedal = StreamUtils.readUnsigned16 (in, false);
        this.partsBendRange = StreamUtils.readUnsigned16 (in, false);
        this.partsMidiVolume = StreamUtils.readUnsigned16 (in, false);
        this.partsAfterTouchSwitch = StreamUtils.readUnsigned16 (in, false);
        this.partsAfterTouchMode = StreamUtils.readUnsigned16 (in, false);

        this.velocityCurveTypeData = new int [16];
        for (int i = 0; i < 16; i++)
            this.velocityCurveTypeData[i] = StreamUtils.readUnsigned8 (in) & 0xFF;

        this.patchList = new int [32];
        for (int i = 0; i < 32; i++)
            this.patchList[i] = StreamUtils.readSigned16 (in, false);
        in.skipNBytes (0xC0);
    }


    public String getPerformanceName ()
    {
        return this.performanceName;
    }


    public int [] getPartsPatchSelection ()
    {
        return this.partsPatchSelection;
    }


    public int [] getMidiChannelData ()
    {
        return this.midiChannelData;
    }


    public S770PartLevel [] getPartsLevel ()
    {
        return this.partsLevel;
    }


    public int [] getPartsZoneLower ()
    {
        return this.partsZoneLower;
    }


    public int [] getPartsZoneUpper ()
    {
        return this.partsZoneUpper;
    }


    public int [] getPartsFadeWidthLower ()
    {
        return this.partsFadeWidthLower;
    }


    public int [] getPartsFadeWidthUpper ()
    {
        return this.partsFadeWidthUpper;
    }


    public int getPartsProgramChange ()
    {
        return this.partsProgramChange;
    }


    public int getPartsPitchBend ()
    {
        return this.partsPitchBend;
    }


    public int getPartsModulation ()
    {
        return this.partsModulation;
    }


    public int getPartsHoldPedal ()
    {
        return this.partsHoldPedal;
    }


    public int getPartsBendRange ()
    {
        return this.partsBendRange;
    }


    public int getPartsMidiVolume ()
    {
        return this.partsMidiVolume;
    }


    public int getPartsAfterTouchSwitch ()
    {
        return this.partsAfterTouchSwitch;
    }


    public int getPartsAfterTouchMode ()
    {
        return this.partsAfterTouchMode;
    }


    public int [] getVelocityCurveTypeData ()
    {
        return this.velocityCurveTypeData;
    }


    public int [] getPatchList ()
    {
        return this.patchList;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770PerformanceParameter [\n" + "  performanceName='" + this.performanceName.trim () + "'\n" + "  partsPatchSelection=" + Arrays.toString (this.partsPatchSelection) + "\n" + "  midiChannelData=" + Arrays.toString (this.midiChannelData) + "\n" + "  partsLevel=" + Arrays.toString (this.partsLevel) + "\n" + "  partsZoneLower=" + Arrays.toString (this.partsZoneLower) + "\n" + "  partsZoneUpper=" + Arrays.toString (this.partsZoneUpper) + "\n" + "  partsFadeWidthLower=" + Arrays.toString (this.partsFadeWidthLower) + "\n" + "  partsFadeWidthUpper=" + Arrays.toString (this.partsFadeWidthUpper) + "\n" + "  partsProgramChange=" + this.partsProgramChange + "\n" + "  partsPitchBend=" + this.partsPitchBend + "\n" + "  partsModulation=" + this.partsModulation + "\n" + "  partsHoldPedal=" + this.partsHoldPedal + "\n" + "  partsBendRange=" + this.partsBendRange + "\n" + "  partsMidiVolume=" + this.partsMidiVolume + "\n" + "  partsAfterTouchSwitch=" + this.partsAfterTouchSwitch + "\n" + "  partsAfterTouchMode=" + this.partsAfterTouchMode + "\n" + "  velocityCurveTypeData=" + Arrays.toString (this.velocityCurveTypeData) + "\n]";
    }
}