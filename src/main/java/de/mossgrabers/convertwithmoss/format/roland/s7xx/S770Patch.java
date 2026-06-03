// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 patch parameter (256 bytes).
 *
 * 16 patch_name u1×15 (program_change_num … analog_feel) 1 (pad) u1×88 keys_partial_selection 8
 * (pad) u1×88 keys_assign_type 8 (pad) 4 bender_section 7 after_touch_section 4 modulation_section
 * 1 (pad) 8 controller_section 8 (pad)
 *
 * @author Jürgen Moßgraber
 */
public class S770Patch
{
    private final String            patchName;
    private final int               programChangeNum;
    private final int               stereoMixLevel;
    private final int               totalPan;
    private final int               patchLevel;
    private final int               outputAssign8;
    private final int               priority;
    private final int               cutoff;
    private final int               velocitySensitivity;
    private final int               octaveShift;
    private final int               coarseTune;
    private final int               fineTune;
    private final int               smtCtrlSelection;
    private final int               smtCtrlSensitivity;
    private final int               outAssign;
    private final int               analogFeel;
    private final int []            keysPartialSelection;
    private final int []            keysAssignType;
    private final BenderSection     bender;
    private final AfterTouchSection afterTouch;
    private final ModulationSection modulation;
    private final ControllerSection controller;

    private final int []            partialList;


    public S770Patch (final InputStream in) throws IOException
    {
        this.patchName = StreamUtils.readAscii (in, 16);
        this.programChangeNum = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.stereoMixLevel = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.totalPan = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.patchLevel = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.outputAssign8 = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.priority = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.cutoff = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.velocitySensitivity = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.octaveShift = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.coarseTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.fineTune = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.smtCtrlSelection = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.smtCtrlSensitivity = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.outAssign = StreamUtils.readUnsigned8 (in) & 0xFF;
        this.analogFeel = StreamUtils.readUnsigned8 (in) & 0xFF;
        in.skipNBytes (1);

        this.keysPartialSelection = new int [88];
        for (int i = 0; i < 88; i++)
            this.keysPartialSelection[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
        in.skipNBytes (8);

        this.keysAssignType = new int [88];
        for (int i = 0; i < 88; i++)
            this.keysAssignType[i] = StreamUtils.readUnsigned8 (in) & 0xFF;
        in.skipNBytes (8);

        this.bender = new BenderSection (in);
        this.afterTouch = new AfterTouchSection (in);
        this.modulation = new ModulationSection (in);
        in.skipNBytes (1);
        this.controller = new ControllerSection (in);
        in.skipNBytes (8);

        this.partialList = new int [88];
        for (int i = 0; i < 88; i++)
            this.partialList[i] = StreamUtils.readSigned16 (in, false);

        in.skipNBytes (0x50);
    }


    public String getPatchName ()
    {
        return this.patchName;
    }


    public int getProgramChangeNum ()
    {
        return this.programChangeNum;
    }


    public int getStereoMixLevel ()
    {
        return this.stereoMixLevel;
    }


    public int getTotalPan ()
    {
        return this.totalPan;
    }


    public int getPatchLevel ()
    {
        return this.patchLevel;
    }


    public int getOutputAssign8 ()
    {
        return this.outputAssign8;
    }


    public int getPriority ()
    {
        return this.priority;
    }


    public int getCutoff ()
    {
        return this.cutoff;
    }


    public int getVelocitySensitivity ()
    {
        return this.velocitySensitivity;
    }


    public int getOctaveShift ()
    {
        return this.octaveShift;
    }


    public int getCoarseTune ()
    {
        return this.coarseTune;
    }


    public int getFineTune ()
    {
        return this.fineTune;
    }


    public int getSmtCtrlSelection ()
    {
        return this.smtCtrlSelection;
    }


    public int getSmtCtrlSensitivity ()
    {
        return this.smtCtrlSensitivity;
    }


    public int getOutAssign ()
    {
        return this.outAssign;
    }


    public int getAnalogFeel ()
    {
        return this.analogFeel;
    }


    public int [] getKeysPartialSelection ()
    {
        return this.keysPartialSelection;
    }


    public int [] getKeysAssignType ()
    {
        return this.keysAssignType;
    }


    public BenderSection getBender ()
    {
        return this.bender;
    }


    public AfterTouchSection getAfterTouch ()
    {
        return this.afterTouch;
    }


    public ModulationSection getModulation ()
    {
        return this.modulation;
    }


    public ControllerSection getController ()
    {
        return this.controller;
    }


    @Override
    public String toString ()
    {
        return "S770PatchParameter [\n" + "  patchName='" + this.patchName.trim () + "'\n" + "  programChangeNum=" + this.programChangeNum + "\n" + "  stereoMixLevel=" + this.stereoMixLevel + "\n" + "  totalPan=" + this.totalPan + "\n" + "  patchLevel=" + this.patchLevel + "\n" + "  outputAssign8=" + this.outputAssign8 + "\n" + "  priority=" + this.priority + "\n" + "  cutoff=" + this.cutoff + "\n" + "  velocitySensitivity=" + this.velocitySensitivity + "\n" + "  octaveShift=" + this.octaveShift + "\n" + "  coarseTune=" + this.coarseTune + "\n" + "  fineTune=" + this.fineTune + "\n" + "  smtCtrlSelection=" + this.smtCtrlSelection + "\n" + "  smtCtrlSensitivity=" + this.smtCtrlSensitivity + "\n" + "  outAssign=" + this.outAssign + "\n" + "  analogFeel=" + this.analogFeel + "\n" + "  keysPartialSelection=" + Arrays.toString (this.keysPartialSelection) + "\n" + "  keysAssignType=" + Arrays.toString (this.keysAssignType) + "\n" + "  bender=" + this.bender + "\n" + "  afterTouch=" + this.afterTouch + "\n" + "  modulation=" + this.modulation + "\n" + "  controller=" + this.controller + "\n]";
    }

    // -------------------------------------------------------------------------
    // Inner section classes
    // -------------------------------------------------------------------------


    /** Bender section (4 bytes). */
    public static class BenderSection
    {
        private final int pitchCtrlUp;
        private final int pitchCtrlDown;
        private final int tvaCtrl;
        private final int tvfCtrl;


        public BenderSection (final InputStream in) throws IOException
        {
            this.pitchCtrlUp = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.pitchCtrlDown = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvaCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvfCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getPitchCtrlUp ()
        {
            return this.pitchCtrlUp;
        }


        public int getPitchCtrlDown ()
        {
            return this.pitchCtrlDown;
        }


        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        @Override
        public String toString ()
        {
            return "BenderSection [pitchCtrlUp=" + this.pitchCtrlUp + ", pitchCtrlDown=" + this.pitchCtrlDown + ", tvaCtrl=" + this.tvaCtrl + ", tvfCtrl=" + this.tvfCtrl + "]";
        }
    }


    /** After-touch section (7 bytes). */
    public static class AfterTouchSection
    {
        private final int pitchCtrl;
        private final int tvaCtrl;
        private final int tvfCtrl;
        private final int lfoRateCtrl;
        private final int lfoPitchCtrl;
        private final int lfoTvaDepth;
        private final int lfoTvfDepth;


        public AfterTouchSection (final InputStream in) throws IOException
        {
            this.pitchCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvaCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvfCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getPitchCtrl ()
        {
            return this.pitchCtrl;
        }


        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        @Override
        public String toString ()
        {
            return "AfterTouchSection [pitchCtrl=" + this.pitchCtrl + ", tvaCtrl=" + this.tvaCtrl + ", tvfCtrl=" + this.tvfCtrl + ", lfoRateCtrl=" + this.lfoRateCtrl + ", lfoPitchCtrl=" + this.lfoPitchCtrl + ", lfoTvaDepth=" + this.lfoTvaDepth + ", lfoTvfDepth=" + this.lfoTvfDepth + "]";
        }
    }


    /** Modulation section (4 bytes). */
    public static class ModulationSection
    {
        private final int lfoRateCtrl;
        private final int lfoPitchCtrl;
        private final int lfoTvaDepth;
        private final int lfoTvfDepth;


        public ModulationSection (final InputStream in) throws IOException
        {
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        @Override
        public String toString ()
        {
            return "ModulationSection [lfoRateCtrl=" + this.lfoRateCtrl + ", lfoPitchCtrl=" + this.lfoPitchCtrl + ", lfoTvaDepth=" + this.lfoTvaDepth + ", lfoTvfDepth=" + this.lfoTvfDepth + "]";
        }
    }


    /** Controller section (8 bytes). */
    public static class ControllerSection
    {
        private final int ctrlNum;
        private final int pitchCtrl;
        private final int tvaCtrl;
        private final int tvfCtrl;
        private final int lfoRateCtrl;
        private final int lfoPitchCtrl;
        private final int lfoTvaDepth;
        private final int lfoTvfDepth;


        public ControllerSection (final InputStream in) throws IOException
        {
            this.ctrlNum = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.pitchCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvaCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.tvfCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (in) & 0xFF;
        }


        public int getCtrlNum ()
        {
            return this.ctrlNum;
        }


        public int getPitchCtrl ()
        {
            return this.pitchCtrl;
        }


        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "ControllerSection [ctrlNum=" + this.ctrlNum + ", pitchCtrl=" + this.pitchCtrl + ", tvaCtrl=" + this.tvaCtrl + ", tvfCtrl=" + this.tvfCtrl + ", lfoRateCtrl=" + this.lfoRateCtrl + ", lfoPitchCtrl=" + this.lfoPitchCtrl + ", lfoTvaDepth=" + this.lfoTvaDepth + ", lfoTvfDepth=" + this.lfoTvfDepth + "]";
        }
    }
}