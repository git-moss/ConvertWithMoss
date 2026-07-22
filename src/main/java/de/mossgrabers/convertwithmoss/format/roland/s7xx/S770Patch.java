// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * Roland S-770 patch parameter.
 *
 * @author Jürgen Moßgraber
 */
public class S770Patch
{
    /** The key assign type which plays the notes of the key monophonic. */
    private static final int           ASSIGN_TYPE_MONO = 1;

    private static final String        LFO_TVF_DEPTH    = ", lfoTvfDepth=";
    private static final String        LFO_TVA_DEPTH    = ", lfoTvaDepth=";
    private static final String        LFO_PITCH_CTRL   = ", lfoPitchCtrl=";
    private static final String        TVF_CTRL         = ", tvfCtrl=";
    private static final String        TVA_CTRL         = ", tvaCtrl=";

    private final String               patchName;
    private final int                  programChangeNumber;
    private final int                  stereoMixLevel;
    private final int                  totalPan;
    private final int                  patchLevel;
    private final int                  outputAssignment8;
    private final int                  patchPriority;
    private final int                  cutoff;
    private final int                  velocitySensitivity;
    private final int                  octaveShift;
    private final int                  coarseTune;
    private final int                  fineTune;
    private final int                  smtCtrlSelection;
    private final int                  smtCtrlSensitivity;
    private final int                  outAssign;
    private final int                  analogFeel;
    private final int []               keysPartialSelection;
    private final int []               keysAssignType;
    private final BenderSection        bender;
    private final AfterTouchSection    afterTouch;
    private final LfoModulationSection modulation;
    private final ControllerSection    controller;


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @param isDiskette True if this is stored on a diskette otherwise HD/CD-ROM
     * @throws IOException The input stream
     */
    public S770Patch (final InputStream input, final boolean isDiskette) throws IOException
    {
        this.patchName = StreamUtils.readAscii (input, 16);
        this.programChangeNumber = StreamUtils.readUnsigned8 (input);
        this.stereoMixLevel = StreamUtils.readUnsigned8 (input);
        this.totalPan = StreamUtils.readSigned8 (input);
        this.patchLevel = StreamUtils.readUnsigned8 (input);
        this.outputAssignment8 = StreamUtils.readUnsigned8 (input);
        this.patchPriority = StreamUtils.readUnsigned8 (input);
        this.cutoff = StreamUtils.readUnsigned8 (input);
        this.velocitySensitivity = StreamUtils.readUnsigned8 (input);
        this.octaveShift = StreamUtils.readSigned8 (input);
        this.coarseTune = StreamUtils.readSigned8 (input);
        this.fineTune = StreamUtils.readSigned8 (input);
        this.smtCtrlSelection = StreamUtils.readUnsigned8 (input);
        this.smtCtrlSensitivity = StreamUtils.readUnsigned8 (input);
        this.outAssign = StreamUtils.readUnsigned8 (input);
        this.analogFeel = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (1);

        this.keysPartialSelection = new int [88];
        for (int i = 0; i < 88; i++)
            this.keysPartialSelection[i] = StreamUtils.readSigned8 (input);
        input.skipNBytes (8);

        this.keysAssignType = new int [88];
        for (int i = 0; i < 88; i++)
            this.keysAssignType[i] = StreamUtils.readUnsigned8 (input);
        input.skipNBytes (8);

        this.bender = new BenderSection (input);
        this.afterTouch = new AfterTouchSection (input);
        this.modulation = new LfoModulationSection (input);
        input.skipNBytes (1);
        this.controller = new ControllerSection (input);
        input.skipNBytes (8);

        if (isDiskette)
            return;

        // Support for partial indices larger than 128
        for (int i = 0; i < 88; i++)
            this.keysPartialSelection[i] = StreamUtils.readSigned16 (input, false);
        input.skipNBytes (0x50);
    }


    /**
     * Get the name of the patch.
     *
     * @return The name of the patch
     */
    public String getName ()
    {
        return this.patchName;
    }


    /**
     * Get the program change number of the patch.
     *
     * @return The PC number in the range of 1-127
     */
    public int getProgramChangeNumber ()
    {
        return this.programChangeNumber;
    }


    /**
     * Get the output level for the STEREO output of the Patch.
     *
     * @return The level in the range of 0..127
     */
    public int getStereoMixLevel ()
    {
        return this.stereoMixLevel;
    }


    /**
     * Get the panning setting used for the output to the STEREO output jacks.
     *
     * @return The panning (L31 — Center — R31)
     */
    public int getTotalPan ()
    {
        return this.totalPan;
    }


    /**
     * Get the level of the patch.
     *
     * @return The level in the range of 0..127
     */
    public int getPatchLevel ()
    {
        return this.patchLevel;
    }


    /**
     * Get the output assignment in the 8-outs mode.
     *
     * @return The output index, -1=off, 0-7 = 1-8
     */
    public int getOutputAssignment8 ()
    {
        return this.outputAssignment8;
    }


    /**
     * Get the patch priority. If enabled, the patch playing notes of this patch will kept with
     * higher priority if the polyphony has reached its limit
     *
     * @return On/Off
     */
    public int getPatchPriority ()
    {
        return this.patchPriority;
    }


    /**
     * Get the filter cutoff offset. The specified value is added to the Cutoff Frequency of each
     * Partial used in the Patch.
     *
     * @return The cutoff offset in the range of -63..63
     */
    public int getCutoff ()
    {
        return this.cutoff;
    }


    /**
     * Get the velocity sensitivity.
     *
     * @return The sensitivity value
     */
    public int getVelocitySensitivity ()
    {
        return this.velocitySensitivity;
    }


    /**
     * Get the octave shift. This moves the split points as well!
     *
     * @return The octave shift in the range of -2..2, positive values move the range downwards!
     */
    public int getOctaveShift ()
    {
        return this.octaveShift;
    }


    /**
     * Get the coarse tuning.
     *
     * @return The coarse tuning value in the range of -48..48 semi-tones
     */
    public int getCoarseTune ()
    {
        return this.coarseTune;
    }


    /**
     * Get the fine tuning.
     *
     * @return The fine tuning value in the range of -50..+50 cents
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Selects the Sample Mix Control (SMT) source.
     *
     * @return The source
     */
    public int getSmtCtrlSelection ()
    {
        return this.smtCtrlSelection;
    }


    /**
     * Get the sensitivity value of the SMT.
     *
     * @return The sensitivity
     */
    public int getSmtCtrlSensitivity ()
    {
        return this.smtCtrlSensitivity;
    }


    /**
     * Get the output assignment.
     *
     * @return Off, Partial, 1, 2, 3, 4, 5 6
     */
    public int getOutAssign ()
    {
        return this.outAssign;
    }


    /**
     * Get the analog feel.
     *
     * @return The analog feel value in the range of 0..127
     */
    public int getAnalogFeel ()
    {
        return this.analogFeel;
    }


    /**
     * Get the selected partial for each key (from 21 to 108).
     *
     * @return The partial indices, -1 if off
     */
    public int [] getKeysPartialSelection ()
    {
        return this.keysPartialSelection;
    }


    /**
     * Get the assign types.
     *
     * @return 0: Poly, 1: Mono, 2-17: Ext 1-Ext 16
     */
    public int [] getKeysAssignType ()
    {
        return this.keysAssignType;
    }


    /**
     * Test if the patch is played monophonic. The assign type is stored for each key individually,
     * therefore the patch is only considered to be monophonic if all of the keys which do play a
     * partial are set to Mono.
     *
     * @return True if all used keys are set to be played monophonic
     */
    public boolean isMonophonic ()
    {
        boolean hasUsedKey = false;
        for (int i = 0; i < this.keysAssignType.length; i++)
        {
            // Keys without a partial are not played at all
            if (this.keysPartialSelection[i] < 0)
                continue;
            if (this.keysAssignType[i] != ASSIGN_TYPE_MONO)
                return false;
            hasUsedKey = true;
        }
        return hasUsedKey;
    }


    /**
     * Get the bender section.
     *
     * @return The bender section
     */
    public BenderSection getBender ()
    {
        return this.bender;
    }


    /**
     * Get the after-touch section.
     *
     * @return The after-touch section
     */
    public AfterTouchSection getAfterTouch ()
    {
        return this.afterTouch;
    }


    /**
     * Get the modulation section.
     *
     * @return The modulation section
     */
    public LfoModulationSection getLfoModulation ()
    {
        return this.modulation;
    }


    /**
     * Get the controller section.
     *
     * @return The controller section
     */
    public ControllerSection getController ()
    {
        return this.controller;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "S770PatchParameter [\n" + "  patchName='" + this.patchName.trim () + "'\n" + "  programChangeNum=" + this.programChangeNumber + "\n" + "  stereoMixLevel=" + this.stereoMixLevel + "\n" + "  totalPan=" + this.totalPan + "\n" + "  patchLevel=" + this.patchLevel + "\n" + "  outputAssign8=" + this.outputAssignment8 + "\n" + "  priority=" + this.patchPriority + "\n" + "  cutoff=" + this.cutoff + "\n" + "  velocitySensitivity=" + this.velocitySensitivity + "\n" + "  octaveShift=" + this.octaveShift + "\n" + "  coarseTune=" + this.coarseTune + "\n" + "  fineTune=" + this.fineTune + "\n" + "  smtCtrlSelection=" + this.smtCtrlSelection + "\n" + "  smtCtrlSensitivity=" + this.smtCtrlSensitivity + "\n" + "  outAssign=" + this.outAssign + "\n" + "  analogFeel=" + this.analogFeel + "\n" + "  keysPartialSelection=" + Arrays.toString (this.keysPartialSelection) + "\n" + "  keysAssignType=" + Arrays.toString (this.keysAssignType) + "\n" + "  bender=" + this.bender + "\n" + "  afterTouch=" + this.afterTouch + "\n" + "  modulation=" + this.modulation + "\n" + "  controller=" + this.controller + "\n]";
    }


    /** Bender section (4 bytes). */
    public static class BenderSection
    {
        private final int pitchCtrlUp;
        private final int pitchCtrlDown;
        private final int tvaCtrl;
        private final int tvfCtrl;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public BenderSection (final InputStream input) throws IOException
        {
            this.pitchCtrlUp = StreamUtils.readUnsigned8 (input);
            this.pitchCtrlDown = StreamUtils.readUnsigned8 (input);
            this.tvaCtrl = StreamUtils.readUnsigned8 (input);
            this.tvfCtrl = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the pitch upwards.
         *
         * @return The pitch in the range of 0..48 semi-tones
         */
        public int getPitchCtrlUp ()
        {
            return this.pitchCtrlUp;
        }


        /**
         * Get the pitch downwards.
         *
         * @return The pitch in the range of 0..48 semi-tones
         */
        public int getPitchCtrlDown ()
        {
            return this.pitchCtrlDown;
        }


        /**
         * Get the TVA control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        /**
         * Get the TVF control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "BenderSection [pitchCtrlUp=" + this.pitchCtrlUp + ", pitchCtrlDown=" + this.pitchCtrlDown + TVA_CTRL + this.tvaCtrl + TVF_CTRL + this.tvfCtrl + "]";
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


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public AfterTouchSection (final InputStream input) throws IOException
        {
            this.pitchCtrl = StreamUtils.readUnsigned8 (input);
            this.tvaCtrl = StreamUtils.readUnsigned8 (input);
            this.tvfCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (input);
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the pitch control.
         *
         * @return The control in the range of -48..48
         */
        public int getPitchCtrl ()
        {
            return this.pitchCtrl;
        }


        /**
         * Get the TVA control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        /**
         * Get the TVF control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        /**
         * Get the LFO rate control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        /**
         * Get the LFO pitch control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        /**
         * Get the LFO TVA depth control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        /**
         * Get the LFO TVF depth control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "AfterTouchSection [pitchCtrl=" + this.pitchCtrl + TVA_CTRL + this.tvaCtrl + TVF_CTRL + this.tvfCtrl + ", lfoRateCtrl=" + this.lfoRateCtrl + LFO_PITCH_CTRL + this.lfoPitchCtrl + LFO_TVA_DEPTH + this.lfoTvaDepth + LFO_TVF_DEPTH + this.lfoTvfDepth + "]";
        }
    }


    /** Modulation section (4 bytes). */
    public static class LfoModulationSection
    {
        private final int lfoRateCtrl;
        private final int lfoPitchCtrl;
        private final int lfoTvaDepth;
        private final int lfoTvfDepth;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public LfoModulationSection (final InputStream input) throws IOException
        {
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (input);
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the LFO rate control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        /**
         * Get the LFO pitch control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        /**
         * Get the LFO TVA depth control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        /**
         * Get the LFO TVF depth control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "ModulationSection [lfoRateCtrl=" + this.lfoRateCtrl + LFO_PITCH_CTRL + this.lfoPitchCtrl + LFO_TVA_DEPTH + this.lfoTvaDepth + LFO_TVF_DEPTH + this.lfoTvfDepth + "]";
        }
    }


    /** Controller section (8 bytes). */
    public static class ControllerSection
    {
        private final int ctrlSelect;
        private final int pitchCtrl;
        private final int tvaCtrl;
        private final int tvfCtrl;
        private final int lfoRateCtrl;
        private final int lfoPitchCtrl;
        private final int lfoTvaDepth;
        private final int lfoTvfDepth;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public ControllerSection (final InputStream input) throws IOException
        {
            this.ctrlSelect = StreamUtils.readUnsigned8 (input);
            this.pitchCtrl = StreamUtils.readUnsigned8 (input);
            this.tvaCtrl = StreamUtils.readUnsigned8 (input);
            this.tvfCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoRateCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoPitchCtrl = StreamUtils.readUnsigned8 (input);
            this.lfoTvaDepth = StreamUtils.readUnsigned8 (input);
            this.lfoTvfDepth = StreamUtils.readUnsigned8 (input);
        }


        /**
         * Get the control select.
         *
         * @return The controller in the range of 0..95
         */
        public int getCtrlSelect ()
        {
            return this.ctrlSelect;
        }


        /**
         * Get the pitch control.
         *
         * @return The control in the range of -63..63
         */
        public int getPitchCtrl ()
        {
            return this.pitchCtrl;
        }


        /**
         * Get the TVA control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvaCtrl ()
        {
            return this.tvaCtrl;
        }


        /**
         * Get the TVF control.
         *
         * @return The control in the range of -63..63
         */
        public int getTvfCtrl ()
        {
            return this.tvfCtrl;
        }


        /**
         * Get the LFO rate control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoRateCtrl ()
        {
            return this.lfoRateCtrl;
        }


        /**
         * Get the LFO pitch control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoPitchCtrl ()
        {
            return this.lfoPitchCtrl;
        }


        /**
         * Get the LFO TVA control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvaDepth ()
        {
            return this.lfoTvaDepth;
        }


        /**
         * Get the LFO TVF control.
         *
         * @return The control in the range of -63..63
         */
        public int getLfoTvfDepth ()
        {
            return this.lfoTvfDepth;
        }


        /** {@inheritDoc} */
        @Override
        public String toString ()
        {
            return "ControllerSection [ctrlNum=" + this.ctrlSelect + ", pitchCtrl=" + this.pitchCtrl + TVA_CTRL + this.tvaCtrl + TVF_CTRL + this.tvfCtrl + ", lfoRateCtrl=" + this.lfoRateCtrl + LFO_PITCH_CTRL + this.lfoPitchCtrl + LFO_TVA_DEPTH + this.lfoTvaDepth + LFO_TVF_DEPTH + this.lfoTvfDepth + "]";
        }
    }
}