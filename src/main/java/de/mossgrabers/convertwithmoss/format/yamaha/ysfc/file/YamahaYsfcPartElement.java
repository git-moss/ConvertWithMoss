// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * An element of a part in a performance.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcPartElement
{
    private static final double                MAXIMUM_ENVELOPE_TIME = 90.0;
    private static final double                MINIMUM_ENVELOPE_TIME = 0.2;

    private static final Map<Integer, Integer> PITCH_KEY             = new HashMap<> ();
    private static final Map<Integer, Double>  ENVELOPE_TIMES        = new TreeMap<> ();
    static
    {
        PITCH_KEY.put (Integer.valueOf (0), Integer.valueOf (-200));
        PITCH_KEY.put (Integer.valueOf (1), Integer.valueOf (-199));
        PITCH_KEY.put (Integer.valueOf (2), Integer.valueOf (-198));
        PITCH_KEY.put (Integer.valueOf (3), Integer.valueOf (-197));
        PITCH_KEY.put (Integer.valueOf (4), Integer.valueOf (-196));
        PITCH_KEY.put (Integer.valueOf (5), Integer.valueOf (-195));
        PITCH_KEY.put (Integer.valueOf (6), Integer.valueOf (-194));
        PITCH_KEY.put (Integer.valueOf (7), Integer.valueOf (-193));
        PITCH_KEY.put (Integer.valueOf (8), Integer.valueOf (-192));
        PITCH_KEY.put (Integer.valueOf (9), Integer.valueOf (-191));
        PITCH_KEY.put (Integer.valueOf (10), Integer.valueOf (-190));
        PITCH_KEY.put (Integer.valueOf (11), Integer.valueOf (-185));
        PITCH_KEY.put (Integer.valueOf (12), Integer.valueOf (-180));
        PITCH_KEY.put (Integer.valueOf (13), Integer.valueOf (-175));
        PITCH_KEY.put (Integer.valueOf (14), Integer.valueOf (-170));
        PITCH_KEY.put (Integer.valueOf (15), Integer.valueOf (-165));
        PITCH_KEY.put (Integer.valueOf (16), Integer.valueOf (-160));
        PITCH_KEY.put (Integer.valueOf (17), Integer.valueOf (-155));
        PITCH_KEY.put (Integer.valueOf (18), Integer.valueOf (-150));
        PITCH_KEY.put (Integer.valueOf (19), Integer.valueOf (-145));
        PITCH_KEY.put (Integer.valueOf (20), Integer.valueOf (-140));
        PITCH_KEY.put (Integer.valueOf (21), Integer.valueOf (-135));
        PITCH_KEY.put (Integer.valueOf (22), Integer.valueOf (-130));
        PITCH_KEY.put (Integer.valueOf (23), Integer.valueOf (-125));
        PITCH_KEY.put (Integer.valueOf (24), Integer.valueOf (-120));
        PITCH_KEY.put (Integer.valueOf (25), Integer.valueOf (-115));
        PITCH_KEY.put (Integer.valueOf (26), Integer.valueOf (-110));
        PITCH_KEY.put (Integer.valueOf (27), Integer.valueOf (-105));
        PITCH_KEY.put (Integer.valueOf (28), Integer.valueOf (-104));
        PITCH_KEY.put (Integer.valueOf (29), Integer.valueOf (-103));
        PITCH_KEY.put (Integer.valueOf (30), Integer.valueOf (-102));
        PITCH_KEY.put (Integer.valueOf (31), Integer.valueOf (-101));
        PITCH_KEY.put (Integer.valueOf (32), Integer.valueOf (-100));
        PITCH_KEY.put (Integer.valueOf (33), Integer.valueOf (-99));
        PITCH_KEY.put (Integer.valueOf (34), Integer.valueOf (-98));
        PITCH_KEY.put (Integer.valueOf (35), Integer.valueOf (-97));
        PITCH_KEY.put (Integer.valueOf (36), Integer.valueOf (-96));
        PITCH_KEY.put (Integer.valueOf (37), Integer.valueOf (-95));
        PITCH_KEY.put (Integer.valueOf (38), Integer.valueOf (-90));
        PITCH_KEY.put (Integer.valueOf (39), Integer.valueOf (-85));
        PITCH_KEY.put (Integer.valueOf (40), Integer.valueOf (-80));
        PITCH_KEY.put (Integer.valueOf (41), Integer.valueOf (-75));
        PITCH_KEY.put (Integer.valueOf (42), Integer.valueOf (-70));
        PITCH_KEY.put (Integer.valueOf (43), Integer.valueOf (-65));
        PITCH_KEY.put (Integer.valueOf (44), Integer.valueOf (-60));
        PITCH_KEY.put (Integer.valueOf (45), Integer.valueOf (-55));
        PITCH_KEY.put (Integer.valueOf (46), Integer.valueOf (-50));
        PITCH_KEY.put (Integer.valueOf (47), Integer.valueOf (-45));
        PITCH_KEY.put (Integer.valueOf (48), Integer.valueOf (-40));
        PITCH_KEY.put (Integer.valueOf (49), Integer.valueOf (-35));
        PITCH_KEY.put (Integer.valueOf (50), Integer.valueOf (-30));
        PITCH_KEY.put (Integer.valueOf (51), Integer.valueOf (-25));
        PITCH_KEY.put (Integer.valueOf (52), Integer.valueOf (-20));
        PITCH_KEY.put (Integer.valueOf (53), Integer.valueOf (-15));
        PITCH_KEY.put (Integer.valueOf (54), Integer.valueOf (-10));
        PITCH_KEY.put (Integer.valueOf (55), Integer.valueOf (-9));
        PITCH_KEY.put (Integer.valueOf (56), Integer.valueOf (-8));
        PITCH_KEY.put (Integer.valueOf (57), Integer.valueOf (-7));
        PITCH_KEY.put (Integer.valueOf (58), Integer.valueOf (-6));
        PITCH_KEY.put (Integer.valueOf (59), Integer.valueOf (-5));
        PITCH_KEY.put (Integer.valueOf (60), Integer.valueOf (-4));
        PITCH_KEY.put (Integer.valueOf (61), Integer.valueOf (-3));
        PITCH_KEY.put (Integer.valueOf (62), Integer.valueOf (-2));
        PITCH_KEY.put (Integer.valueOf (63), Integer.valueOf (-1));
        PITCH_KEY.put (Integer.valueOf (64), Integer.valueOf (0));
        PITCH_KEY.put (Integer.valueOf (65), Integer.valueOf (1));
        PITCH_KEY.put (Integer.valueOf (66), Integer.valueOf (2));
        PITCH_KEY.put (Integer.valueOf (67), Integer.valueOf (3));
        PITCH_KEY.put (Integer.valueOf (68), Integer.valueOf (4));
        PITCH_KEY.put (Integer.valueOf (69), Integer.valueOf (5));
        PITCH_KEY.put (Integer.valueOf (70), Integer.valueOf (6));
        PITCH_KEY.put (Integer.valueOf (71), Integer.valueOf (7));
        PITCH_KEY.put (Integer.valueOf (72), Integer.valueOf (8));
        PITCH_KEY.put (Integer.valueOf (73), Integer.valueOf (9));
        PITCH_KEY.put (Integer.valueOf (74), Integer.valueOf (10));
        PITCH_KEY.put (Integer.valueOf (75), Integer.valueOf (15));
        PITCH_KEY.put (Integer.valueOf (76), Integer.valueOf (20));
        PITCH_KEY.put (Integer.valueOf (77), Integer.valueOf (25));
        PITCH_KEY.put (Integer.valueOf (78), Integer.valueOf (30));
        PITCH_KEY.put (Integer.valueOf (79), Integer.valueOf (35));
        PITCH_KEY.put (Integer.valueOf (80), Integer.valueOf (40));
        PITCH_KEY.put (Integer.valueOf (81), Integer.valueOf (45));
        PITCH_KEY.put (Integer.valueOf (82), Integer.valueOf (50));
        PITCH_KEY.put (Integer.valueOf (83), Integer.valueOf (55));
        PITCH_KEY.put (Integer.valueOf (84), Integer.valueOf (60));
        PITCH_KEY.put (Integer.valueOf (85), Integer.valueOf (65));
        PITCH_KEY.put (Integer.valueOf (86), Integer.valueOf (70));
        PITCH_KEY.put (Integer.valueOf (87), Integer.valueOf (75));
        PITCH_KEY.put (Integer.valueOf (88), Integer.valueOf (80));
        PITCH_KEY.put (Integer.valueOf (89), Integer.valueOf (85));
        PITCH_KEY.put (Integer.valueOf (90), Integer.valueOf (90));
        PITCH_KEY.put (Integer.valueOf (91), Integer.valueOf (95));
        PITCH_KEY.put (Integer.valueOf (92), Integer.valueOf (96));
        PITCH_KEY.put (Integer.valueOf (93), Integer.valueOf (97));
        PITCH_KEY.put (Integer.valueOf (94), Integer.valueOf (98));
        PITCH_KEY.put (Integer.valueOf (95), Integer.valueOf (99));
        PITCH_KEY.put (Integer.valueOf (96), Integer.valueOf (100));
        PITCH_KEY.put (Integer.valueOf (97), Integer.valueOf (101));
        PITCH_KEY.put (Integer.valueOf (98), Integer.valueOf (102));
        PITCH_KEY.put (Integer.valueOf (99), Integer.valueOf (103));
        PITCH_KEY.put (Integer.valueOf (100), Integer.valueOf (104));
        PITCH_KEY.put (Integer.valueOf (101), Integer.valueOf (105));
        PITCH_KEY.put (Integer.valueOf (102), Integer.valueOf (110));
        PITCH_KEY.put (Integer.valueOf (103), Integer.valueOf (115));
        PITCH_KEY.put (Integer.valueOf (104), Integer.valueOf (120));
        PITCH_KEY.put (Integer.valueOf (105), Integer.valueOf (125));
        PITCH_KEY.put (Integer.valueOf (106), Integer.valueOf (130));
        PITCH_KEY.put (Integer.valueOf (107), Integer.valueOf (135));
        PITCH_KEY.put (Integer.valueOf (108), Integer.valueOf (140));
        PITCH_KEY.put (Integer.valueOf (109), Integer.valueOf (145));
        PITCH_KEY.put (Integer.valueOf (110), Integer.valueOf (150));
        PITCH_KEY.put (Integer.valueOf (111), Integer.valueOf (155));
        PITCH_KEY.put (Integer.valueOf (112), Integer.valueOf (160));
        PITCH_KEY.put (Integer.valueOf (113), Integer.valueOf (165));
        PITCH_KEY.put (Integer.valueOf (114), Integer.valueOf (170));
        PITCH_KEY.put (Integer.valueOf (115), Integer.valueOf (175));
        PITCH_KEY.put (Integer.valueOf (116), Integer.valueOf (180));
        PITCH_KEY.put (Integer.valueOf (117), Integer.valueOf (185));
        PITCH_KEY.put (Integer.valueOf (118), Integer.valueOf (190));
        PITCH_KEY.put (Integer.valueOf (119), Integer.valueOf (192));
        PITCH_KEY.put (Integer.valueOf (120), Integer.valueOf (193));
        PITCH_KEY.put (Integer.valueOf (121), Integer.valueOf (194));
        PITCH_KEY.put (Integer.valueOf (122), Integer.valueOf (195));
        PITCH_KEY.put (Integer.valueOf (123), Integer.valueOf (196));
        PITCH_KEY.put (Integer.valueOf (124), Integer.valueOf (197));
        PITCH_KEY.put (Integer.valueOf (125), Integer.valueOf (198));
        PITCH_KEY.put (Integer.valueOf (126), Integer.valueOf (199));
        PITCH_KEY.put (Integer.valueOf (127), Integer.valueOf (200));

        ENVELOPE_TIMES.put (Integer.valueOf (0), Double.valueOf (MINIMUM_ENVELOPE_TIME));
        ENVELOPE_TIMES.put (Integer.valueOf (1), Double.valueOf (0.21));
        ENVELOPE_TIMES.put (Integer.valueOf (2), Double.valueOf (0.22));
        ENVELOPE_TIMES.put (Integer.valueOf (3), Double.valueOf (0.23));
        ENVELOPE_TIMES.put (Integer.valueOf (4), Double.valueOf (0.24));
        ENVELOPE_TIMES.put (Integer.valueOf (5), Double.valueOf (0.25));
        ENVELOPE_TIMES.put (Integer.valueOf (6), Double.valueOf (0.26));
        ENVELOPE_TIMES.put (Integer.valueOf (7), Double.valueOf (0.27));
        ENVELOPE_TIMES.put (Integer.valueOf (8), Double.valueOf (0.28));
        ENVELOPE_TIMES.put (Integer.valueOf (9), Double.valueOf (0.29));
        ENVELOPE_TIMES.put (Integer.valueOf (10), Double.valueOf (0.3));
        ENVELOPE_TIMES.put (Integer.valueOf (11), Double.valueOf (0.304));
        ENVELOPE_TIMES.put (Integer.valueOf (12), Double.valueOf (0.308));
        ENVELOPE_TIMES.put (Integer.valueOf (13), Double.valueOf (0.312));
        ENVELOPE_TIMES.put (Integer.valueOf (14), Double.valueOf (0.316));
        ENVELOPE_TIMES.put (Integer.valueOf (15), Double.valueOf (0.32));
        ENVELOPE_TIMES.put (Integer.valueOf (16), Double.valueOf (0.324));
        ENVELOPE_TIMES.put (Integer.valueOf (17), Double.valueOf (0.328));
        ENVELOPE_TIMES.put (Integer.valueOf (18), Double.valueOf (0.332));
        ENVELOPE_TIMES.put (Integer.valueOf (19), Double.valueOf (0.336));
        ENVELOPE_TIMES.put (Integer.valueOf (20), Double.valueOf (0.34));
        ENVELOPE_TIMES.put (Integer.valueOf (21), Double.valueOf (0.343));
        ENVELOPE_TIMES.put (Integer.valueOf (22), Double.valueOf (0.346));
        ENVELOPE_TIMES.put (Integer.valueOf (23), Double.valueOf (0.349));
        ENVELOPE_TIMES.put (Integer.valueOf (24), Double.valueOf (0.352));
        ENVELOPE_TIMES.put (Integer.valueOf (25), Double.valueOf (0.355));
        ENVELOPE_TIMES.put (Integer.valueOf (26), Double.valueOf (0.358));
        ENVELOPE_TIMES.put (Integer.valueOf (27), Double.valueOf (0.361));
        ENVELOPE_TIMES.put (Integer.valueOf (28), Double.valueOf (0.364));
        ENVELOPE_TIMES.put (Integer.valueOf (29), Double.valueOf (0.367));
        ENVELOPE_TIMES.put (Integer.valueOf (30), Double.valueOf (0.37));
        ENVELOPE_TIMES.put (Integer.valueOf (31), Double.valueOf (0.373));
        ENVELOPE_TIMES.put (Integer.valueOf (32), Double.valueOf (0.376));
        ENVELOPE_TIMES.put (Integer.valueOf (33), Double.valueOf (0.379));
        ENVELOPE_TIMES.put (Integer.valueOf (34), Double.valueOf (0.382));
        ENVELOPE_TIMES.put (Integer.valueOf (35), Double.valueOf (0.385));
        ENVELOPE_TIMES.put (Integer.valueOf (36), Double.valueOf (0.388));
        ENVELOPE_TIMES.put (Integer.valueOf (37), Double.valueOf (0.391));
        ENVELOPE_TIMES.put (Integer.valueOf (38), Double.valueOf (0.394));
        ENVELOPE_TIMES.put (Integer.valueOf (39), Double.valueOf (0.397));
        ENVELOPE_TIMES.put (Integer.valueOf (40), Double.valueOf (0.4));
        ENVELOPE_TIMES.put (Integer.valueOf (41), Double.valueOf (0.404));
        ENVELOPE_TIMES.put (Integer.valueOf (42), Double.valueOf (0.408));
        ENVELOPE_TIMES.put (Integer.valueOf (43), Double.valueOf (0.412));
        ENVELOPE_TIMES.put (Integer.valueOf (44), Double.valueOf (0.416));
        ENVELOPE_TIMES.put (Integer.valueOf (45), Double.valueOf (0.42));
        ENVELOPE_TIMES.put (Integer.valueOf (46), Double.valueOf (0.424));
        ENVELOPE_TIMES.put (Integer.valueOf (47), Double.valueOf (0.428));
        ENVELOPE_TIMES.put (Integer.valueOf (48), Double.valueOf (0.432));
        ENVELOPE_TIMES.put (Integer.valueOf (49), Double.valueOf (0.436));
        ENVELOPE_TIMES.put (Integer.valueOf (50), Double.valueOf (0.44));
        ENVELOPE_TIMES.put (Integer.valueOf (51), Double.valueOf (0.446));
        ENVELOPE_TIMES.put (Integer.valueOf (52), Double.valueOf (0.452));
        ENVELOPE_TIMES.put (Integer.valueOf (53), Double.valueOf (0.458));
        ENVELOPE_TIMES.put (Integer.valueOf (54), Double.valueOf (0.464));
        ENVELOPE_TIMES.put (Integer.valueOf (55), Double.valueOf (0.47));
        ENVELOPE_TIMES.put (Integer.valueOf (56), Double.valueOf (0.476));
        ENVELOPE_TIMES.put (Integer.valueOf (57), Double.valueOf (0.482));
        ENVELOPE_TIMES.put (Integer.valueOf (58), Double.valueOf (0.488));
        ENVELOPE_TIMES.put (Integer.valueOf (59), Double.valueOf (0.494));
        ENVELOPE_TIMES.put (Integer.valueOf (60), Double.valueOf (0.5));
        ENVELOPE_TIMES.put (Integer.valueOf (61), Double.valueOf (0.54));
        ENVELOPE_TIMES.put (Integer.valueOf (62), Double.valueOf (0.58));
        ENVELOPE_TIMES.put (Integer.valueOf (63), Double.valueOf (0.62));
        ENVELOPE_TIMES.put (Integer.valueOf (64), Double.valueOf (0.66));
        ENVELOPE_TIMES.put (Integer.valueOf (65), Double.valueOf (0.7));
        ENVELOPE_TIMES.put (Integer.valueOf (66), Double.valueOf (0.74));
        ENVELOPE_TIMES.put (Integer.valueOf (67), Double.valueOf (0.78));
        ENVELOPE_TIMES.put (Integer.valueOf (68), Double.valueOf (0.82));
        ENVELOPE_TIMES.put (Integer.valueOf (69), Double.valueOf (0.86));
        ENVELOPE_TIMES.put (Integer.valueOf (70), Double.valueOf (0.9));
        ENVELOPE_TIMES.put (Integer.valueOf (71), Double.valueOf (1.02));
        ENVELOPE_TIMES.put (Integer.valueOf (72), Double.valueOf (1.14));
        ENVELOPE_TIMES.put (Integer.valueOf (73), Double.valueOf (1.26));
        ENVELOPE_TIMES.put (Integer.valueOf (74), Double.valueOf (1.38));
        ENVELOPE_TIMES.put (Integer.valueOf (75), Double.valueOf (1.5));
        ENVELOPE_TIMES.put (Integer.valueOf (76), Double.valueOf (1.62));
        ENVELOPE_TIMES.put (Integer.valueOf (77), Double.valueOf (1.74));
        ENVELOPE_TIMES.put (Integer.valueOf (78), Double.valueOf (1.86));
        ENVELOPE_TIMES.put (Integer.valueOf (79), Double.valueOf (1.98));
        ENVELOPE_TIMES.put (Integer.valueOf (80), Double.valueOf (2.1));
        ENVELOPE_TIMES.put (Integer.valueOf (81), Double.valueOf (2.29));
        ENVELOPE_TIMES.put (Integer.valueOf (82), Double.valueOf (2.48));
        ENVELOPE_TIMES.put (Integer.valueOf (83), Double.valueOf (2.67));
        ENVELOPE_TIMES.put (Integer.valueOf (84), Double.valueOf (2.86));
        ENVELOPE_TIMES.put (Integer.valueOf (85), Double.valueOf (3.05));
        ENVELOPE_TIMES.put (Integer.valueOf (86), Double.valueOf (3.24));
        ENVELOPE_TIMES.put (Integer.valueOf (87), Double.valueOf (3.43));
        ENVELOPE_TIMES.put (Integer.valueOf (88), Double.valueOf (3.62));
        ENVELOPE_TIMES.put (Integer.valueOf (89), Double.valueOf (3.81));
        ENVELOPE_TIMES.put (Integer.valueOf (90), Double.valueOf (4.0));
        ENVELOPE_TIMES.put (Integer.valueOf (91), Double.valueOf (4.8));
        ENVELOPE_TIMES.put (Integer.valueOf (92), Double.valueOf (5.6));
        ENVELOPE_TIMES.put (Integer.valueOf (93), Double.valueOf (6.4));
        ENVELOPE_TIMES.put (Integer.valueOf (94), Double.valueOf (7.2));
        ENVELOPE_TIMES.put (Integer.valueOf (95), Double.valueOf (8.0));
        ENVELOPE_TIMES.put (Integer.valueOf (96), Double.valueOf (8.8));
        ENVELOPE_TIMES.put (Integer.valueOf (97), Double.valueOf (9.6));
        ENVELOPE_TIMES.put (Integer.valueOf (98), Double.valueOf (10.4));
        ENVELOPE_TIMES.put (Integer.valueOf (99), Double.valueOf (11.2));
        ENVELOPE_TIMES.put (Integer.valueOf (100), Double.valueOf (12.0));
        ENVELOPE_TIMES.put (Integer.valueOf (101), Double.valueOf (13.3));
        ENVELOPE_TIMES.put (Integer.valueOf (102), Double.valueOf (14.6));
        ENVELOPE_TIMES.put (Integer.valueOf (103), Double.valueOf (15.9));
        ENVELOPE_TIMES.put (Integer.valueOf (104), Double.valueOf (17.2));
        ENVELOPE_TIMES.put (Integer.valueOf (105), Double.valueOf (18.5));
        ENVELOPE_TIMES.put (Integer.valueOf (106), Double.valueOf (19.8));
        ENVELOPE_TIMES.put (Integer.valueOf (107), Double.valueOf (21.1));
        ENVELOPE_TIMES.put (Integer.valueOf (108), Double.valueOf (22.4));
        ENVELOPE_TIMES.put (Integer.valueOf (109), Double.valueOf (23.7));
        ENVELOPE_TIMES.put (Integer.valueOf (110), Double.valueOf (25.0));
        ENVELOPE_TIMES.put (Integer.valueOf (111), Double.valueOf (28.9));
        ENVELOPE_TIMES.put (Integer.valueOf (112), Double.valueOf (32.8));
        ENVELOPE_TIMES.put (Integer.valueOf (113), Double.valueOf (36.7));
        ENVELOPE_TIMES.put (Integer.valueOf (114), Double.valueOf (40.6));
        ENVELOPE_TIMES.put (Integer.valueOf (115), Double.valueOf (44.5));
        ENVELOPE_TIMES.put (Integer.valueOf (116), Double.valueOf (48.4));
        ENVELOPE_TIMES.put (Integer.valueOf (117), Double.valueOf (52.3));
        ENVELOPE_TIMES.put (Integer.valueOf (118), Double.valueOf (56.2));
        ENVELOPE_TIMES.put (Integer.valueOf (119), Double.valueOf (60.1));
        ENVELOPE_TIMES.put (Integer.valueOf (120), Double.valueOf (64.0));
        ENVELOPE_TIMES.put (Integer.valueOf (121), Double.valueOf (67.714));
        ENVELOPE_TIMES.put (Integer.valueOf (122), Double.valueOf (71.429));
        ENVELOPE_TIMES.put (Integer.valueOf (123), Double.valueOf (75.143));
        ENVELOPE_TIMES.put (Integer.valueOf (124), Double.valueOf (78.857));
        ENVELOPE_TIMES.put (Integer.valueOf (125), Double.valueOf (82.571));
        ENVELOPE_TIMES.put (Integer.valueOf (126), Double.valueOf (86.286));
        ENVELOPE_TIMES.put (Integer.valueOf (127), Double.valueOf (MAXIMUM_ENVELOPE_TIME));
    }

    private int     elementSwitch;
    private int     waveBank;
    private int     elementGroupNumber;
    private int     receiveNoteOff;
    private int     keyAssignMode;
    private int     alternateGroup;
    private int     pan;
    private int     randomPanDepth;
    private int     alternatePanDepth;
    private int     scalingPanDepth;
    private int     xaMode;
    private int     noteLimitLow;
    private int     noteLimitHigh;
    private int     velocityLimitLow;
    private int     velocityLimitHigh;
    private int     velocityCrossFade;
    private int     keyOnDelay;
    private int     keyOnDelayTempoSync;
    private int     reverbSendLevel;
    private int     variationSendLevel;
    private int     insertionEffectSwitch;
    private int     outputSelect;
    private int     controlBox1Sw;
    private int     controlBox2Sw;
    private int     controlBox3Sw;
    private int     controlBox4Sw;
    private int     controlBox5Sw;
    private int     controlBox6Sw;
    private int     controlBox7Sw;
    private int     controlBox8Sw;
    private int     controlBox9Sw;
    private int     controlBox10Sw;
    private int     controlBox11Sw;
    private int     controlBox12Sw;
    private int     controlBox13Sw;
    private int     controlBox14Sw;
    private int     controlBox15Sw;
    private int     controlBox16Sw;
    private int     keyOnDelayTempo;
    private int     halfDamperSwitch;
    private int     elementLevel;
    private int     levelVelocitySensitivity;
    private int     levelVelocityOffset;
    private int     levelSensKeyCurve;
    private int     aegAttackTime;
    private int     aegDecay1Time;
    private int     aegDecay2Time;
    private int     aegSustainTime;
    private int     aegReleaseTime;
    private int     aegInitLevel;
    private int     aegAttackLevel;
    private int     aegDecay1Level;
    private int     aegDecay2Level;
    private int     aegTimeVelocitySegment;
    private int     aegTimeVelocitySensitivity;
    private int     aegTimeKeyFollowSensitivity;
    private int     aegTimeKeyFollowCenterNote;
    private int     aegTimeKeyFollowAdjustment;
    private int     levelScalingBreakPoint1;
    private int     levelScalingBreakPoint2;
    private int     levelScalingBreakPoint3;
    private int     levelScalingBreakPoint4;
    private int     levelScalingOffset1;
    private int     levelScalingOffset2;
    private int     levelScalingOffset3;
    private int     levelScalingOffset4;
    private int     levelKeyFollowSensitivity;
    private int     coarseTune;
    private int     fineTune;
    private int     pitchVelocitySensitivity;
    private int     randomPitchDepth;
    private int     pitchKeyFollowSensitivity;
    private int     pitchKeyFollowCenterNote;
    private int     pitchFineScalingSensitivity;
    private int     pegHoldTime;
    private int     pegAttackTime;
    private int     pegDecay1Time;
    private int     pegDecay2Time;
    private int     pegReleaseTime;
    private int     pegHoldLevel;
    private int     pegAttackLevel;
    private int     pegDecay1Level;
    private int     pegDecay2Level;
    private int     pegReleaseLevel;
    private int     pegDepth;
    private int     pegTimeVelocitySegment;
    private int     pegTimeVelocitySensitivity;
    private int     pegLevelVelocitySensitivity;
    private int     pegLevelSensVelocityCurve;
    private int     pegTimeKeyFollowSensitivity;
    private int     pegTimeKeyFollowCenterNote;
    private int     filterType;
    private int     filterCutoffFrequency;
    private int     filterCutoffVelocitySensitivity;
    private int     filterResonance;
    private int     filterResonanceVelocitySensitivity;
    private int     hpfCutoffFrequency;
    private int     distance;
    private int     filterGain;
    private int     fegHoldTime;
    private int     fegAttackTime;
    private int     fegDecay1Time;
    private int     fegDecay2Time;
    private int     fegReleaseTime;
    private int     fegHoldLevel;
    private int     fegAttackLevel;
    private int     fegDecay1Level;
    private int     fegDecay2Level;
    private int     fegReleaseLevel;
    private int     fegDepth;
    private int     fegTimeVelocitySegment;
    private int     fegTimeVelocitySensitivity;
    private int     fegLevelVelocitySensitivity;
    private int     fegLevelVelocityCurve;
    private int     fegTimeKeyFollowSensitivity;
    private int     fegTimeKeyFollowCenterNote;
    private int     filterCutoffScalingBreakPoint1;
    private int     filterCutoffScalingBreakPoint2;
    private int     filterCutoffScalingBreakPoint3;
    private int     filterCutoffScalingBreakPoint4;
    private int     filterCutoffScalingOffset1;
    private int     filterCutoffScalingOffset2;
    private int     filterCutoffScalingOffset3;
    private int     filterCutoffScalingOffset4;
    private int     filterCutoffKeyFollowSensitivity;
    private int     hpfCutoffKeyFollowSensitivity;
    private int     eqType;
    private int     eqResonance;
    private int     eq1Frequency;
    private int     eq1Gain;
    private int     eq2Frequency;
    private int     eq2Gain;
    private int     lfoWave;
    private int     lfoKeyOnSync;
    private int     lfoKeyOnDelayTime;
    private int     lfoSpeed;
    private int     lfoAmodDepth;
    private int     lfoPmodDepth;
    private int     lfoFmodDepth;
    private int     lfoFadeInTime;
    private int     commonLfoPhaseOffset;
    private int     commonLfoBox1DepthRatio;
    private int     commonLfoBox2DepthRatio;
    private int     commonLfoBox3DepthRatio;
    private byte [] unknownBytes;
    private int     waveformNumber;


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPartElement (final InputStream in, final YamahaYsfcFileFormat version) throws IOException
    {
        this.read (in, version);
    }


    /**
     * Get the element switch.
     *
     * @return 1 if the element is active, 0 if off
     */
    public int getElementSwitch ()
    {
        return this.elementSwitch;
    }


    /**
     * Set the element switch.
     *
     * @param elementSwitch 1 if the element is active, 0 if off
     */
    public void setElementSwitch (final int elementSwitch)
    {
        this.elementSwitch = elementSwitch;
    }


    /**
     * Set the group to which the element belongs.
     *
     * @param elementGroupNumber The number of the group
     */
    public void setElementGroupNumber (final int elementGroupNumber)
    {
        this.elementGroupNumber = elementGroupNumber;
    }


    /**
     * Get the wave bank.
     *
     * @return 0=Preset, 1=User, 2-9=Library1-8
     */
    public int getWaveBank ()
    {
        return this.waveBank;
    }


    /**
     * Set the wave bank.
     *
     * @param waveBank 0=Preset, 1=User, 2-9=Library1-8
     */
    public void setWaveBank (final int waveBank)
    {
        this.waveBank = waveBank;
    }


    /**
     * Get the panning.
     *
     * @return The panning in the range of [1..127] which relates to -63..+63, 64 = Center
     */
    public int getPan ()
    {
        return this.pan;
    }


    /**
     * Set the panning.
     *
     * @param pan The panning in the range of [1..127] which relates to -63..+63, 64 = Center
     */
    public void setPan (final int pan)
    {
        this.pan = pan;
    }


    /**
     * Get the Expanded Articulation (XA) mode.
     *
     * @return 0: Normal, 1: Legato, 2: Key Off, 3: Cycle, 4: Random, 5: A.SW Off, 6: A.SW1 On, 7:
     *         A.SW2 On
     */
    public int getXaMode ()
    {
        return this.xaMode;
    }


    /**
     * Set the Expanded Articulation (XA) mode.
     *
     * @param xaMode 0: Normal, 1: Legato, 2: Key Off, 3: Cycle, 4: Random, 5: A.SW Off, 6: A.SW1
     *            On, 7: A.SW2 On
     */
    public void setXaMode (final int xaMode)
    {
        this.xaMode = Math.clamp (xaMode, 0, 7);
    }


    /**
     * Get the lower note limit.
     *
     * @return The MIDI note
     */
    public int getNoteLimitLow ()
    {
        return this.noteLimitLow;
    }


    /**
     * Set the lower note limit.
     *
     * @param noteLimitLow The MIDI note
     */
    public void setNoteLimitLow (final int noteLimitLow)
    {
        this.noteLimitLow = noteLimitLow;
    }


    /**
     * Get the upper note limit.
     *
     * @return The MIDI note
     */
    public int getNoteLimitHigh ()
    {
        return this.noteLimitHigh;
    }


    /**
     * Set the upper note limit.
     *
     * @param noteLimitHigh The MIDI note
     */
    public void setNoteLimitHigh (final int noteLimitHigh)
    {
        this.noteLimitHigh = noteLimitHigh;
    }


    /**
     * Get the lower velocity limit.
     *
     * @return The MIDI velocity
     */
    public int getVelocityLimitLow ()
    {
        return this.velocityLimitLow;
    }


    /**
     * Set the upper velocity limit.
     *
     * @param velocityLimitLow The MIDI velocity
     */
    public void setVelocityLimitLow (final int velocityLimitLow)
    {
        this.velocityLimitLow = velocityLimitLow;
    }


    /**
     * Get the upper velocity limit.
     *
     * @return The MIDI velocity
     */
    public int getVelocityLimitHigh ()
    {
        return this.velocityLimitHigh;
    }


    /**
     * Set the upper velocity limit.
     *
     * @param velocityLimitHigh The MIDI velocity
     */
    public void setVelocityLimitHigh (final int velocityLimitHigh)
    {
        this.velocityLimitHigh = velocityLimitHigh;
    }


    /**
     * Get the level of the element.
     *
     * @return The level of the element 0-127
     */
    public int getElementLevel ()
    {
        return this.elementLevel;
    }


    /**
     * Set the level of the element.
     *
     * @param elementLevel The level of the element 0-127
     */
    public void setElementLevel (final int elementLevel)
    {
        this.elementLevel = elementLevel;
    }


    /**
     * Get the level velocity sensitivity.
     *
     * @return The value in the range of 0..127 which relates to -64..+63 (0 ~ 64)
     */
    public int getLevelVelocitySensitivity ()
    {
        return this.levelVelocitySensitivity;
    }


    /**
     * Set the level velocity sensitivity.
     *
     * @param levelVelocitySensitivity The value in the range of 0..127
     */
    public void setLevelVelocitySensitivity (final int levelVelocitySensitivity)
    {
        this.levelVelocitySensitivity = levelVelocitySensitivity;
    }


    /**
     * Get the amplitude attack time.
     *
     * @return The value in the range of 0-127
     */
    public int getAegAttackTime ()
    {
        return this.aegAttackTime;
    }


    /**
     * Set the amplitude attack time.
     *
     * @param aegAttackTime The value in the range of 0-127
     */
    public void setAegAttackTime (final int aegAttackTime)
    {
        this.aegAttackTime = aegAttackTime;
    }


    /**
     * Get the amplitude decay 1 time.
     *
     * @return The value in the range of 0-127
     */
    public int getAegDecay1Time ()
    {
        return this.aegDecay1Time;
    }


    /**
     * Set the amplitude decay 1 time.
     *
     * @param aegDecay1Time The value in the range of 0-127
     */
    public void setAegDecay1Time (final int aegDecay1Time)
    {
        this.aegDecay1Time = aegDecay1Time;
    }


    /**
     * Get the amplitude decay 2 time.
     *
     * @return The value in the range of 0-127
     */
    public int getAegDecay2Time ()
    {
        return this.aegDecay2Time;
    }


    /**
     * Set the amplitude decay 2 time.
     *
     * @param aegDecay2Time The value in the range of 0-127
     */
    public void setAegDecay2Time (final int aegDecay2Time)
    {
        this.aegDecay2Time = aegDecay2Time;
    }


    /**
     * Get the amplitude release time.
     *
     * @return The value in the range of 0-127
     */
    public int getAegReleaseTime ()
    {
        return this.aegReleaseTime;
    }


    /**
     * Set the amplitude release time.
     *
     * @param aegReleaseTime The value in the range of 0-127
     */
    public void setAegReleaseTime (final int aegReleaseTime)
    {
        this.aegReleaseTime = aegReleaseTime;
    }


    /**
     * Get the amplitude initial level.
     *
     * @return The value in the range of 0-127
     */
    public int getAegInitLevel ()
    {
        return this.aegInitLevel;
    }


    /**
     * Set the amplitude initial level.
     *
     * @param aegInitLevel The value in the range of 0-127
     */
    public void setAegInitLevel (final int aegInitLevel)
    {
        this.aegInitLevel = aegInitLevel;
    }


    /**
     * Get the amplitude attack level.
     *
     * @return The value in the range of 0-127
     */
    public int getAegAttackLevel ()
    {
        return this.aegAttackLevel;
    }


    /**
     * Set the amplitude attack level.
     *
     * @param aegAttackLevel The value in the range of 0-127
     */
    public void setAegAttackLevel (final int aegAttackLevel)
    {
        this.aegAttackLevel = aegAttackLevel;
    }


    /**
     * Get the amplitude decay 1 level.
     *
     * @return The value in the range of 0-127
     */
    public int getAegDecay1Level ()
    {
        return this.aegDecay1Level;
    }


    /**
     * Set the amplitude decay 1 level.
     *
     * @param aegDecay1Level The value in the range of 0-127
     */
    public void setAegDecay1Level (final int aegDecay1Level)
    {
        this.aegDecay1Level = aegDecay1Level;
    }


    /**
     * Get the amplitude decay 2 level.
     *
     * @return The value in the range of 0-127
     */
    public int getAegDecay2Level ()
    {
        return this.aegDecay2Level;
    }


    /**
     * Set the amplitude decay 2 level.
     *
     * @param aegDecay2Level The value in the range of 0-127
     */
    public void setAegDecay2Level (final int aegDecay2Level)
    {
        this.aegDecay2Level = aegDecay2Level;
    }


    /**
     * Get the coarse tune.
     *
     * @return The value in the range of 16-112 which relates to -48..+48 (64 = center)
     */
    public int getCoarseTune ()
    {
        return this.coarseTune;
    }


    /**
     * Set the coarse tune.
     *
     * @param coarseTune The value in the range of 16-112 which relates to -48..+48 (64 = center)
     */
    public void setCoarseTune (final int coarseTune)
    {
        this.coarseTune = coarseTune;
    }


    /**
     * Get the fine tune.
     *
     * @return The value in the range of 0-127 which relates to -64..+63 (64 = center)
     */
    public int getFineTune ()
    {
        return this.fineTune;
    }


    /**
     * Set the fine tune.
     *
     * @param fineTune The value in the range of 0-127 which relates to -64..+63 (64 = center)
     */
    public void setFineTune (final int fineTune)
    {
        this.fineTune = Math.clamp (fineTune, 0, 127);
    }


    /**
     * Gets the pitch key follow value.
     *
     * @return The value already adjusted to the range of [-200%..200%]
     */
    public int getPitchKeyFollowSensitivity ()
    {
        final Integer value = PITCH_KEY.get (Integer.valueOf (this.pitchKeyFollowSensitivity));
        return value == null ? 0 : value.intValue ();
    }


    /**
     * Sets the pitch key follow value.
     *
     * @param pitchKeyFollowSensitivity The value in the range of [-200%..200%]
     */
    public void setPitchKeyFollowSensitivity (final int pitchKeyFollowSensitivity)
    {
        int diff = -1;
        int pos = -1;
        for (final Entry<Integer, Integer> e: PITCH_KEY.entrySet ())
        {
            final int newDiff = Math.abs (e.getValue ().intValue () - pitchKeyFollowSensitivity);
            if (diff < 0 || newDiff < diff)
            {
                pos = e.getKey ().intValue ();
                diff = newDiff;
            }
        }

        this.pitchKeyFollowSensitivity = pos == -1 ? 96 : pos;
    }


    /**
     * Get the pitch envelope hold time.
     *
     * @return The value in the range of 0-127
     */
    public int getPegHoldTime ()
    {
        return this.pegHoldTime;
    }


    /**
     * Set the pitch envelope hold time.
     *
     * @param pegHoldTime The value in the range of 0-127
     */
    public void setPegHoldTime (final int pegHoldTime)
    {
        this.pegHoldTime = pegHoldTime;
    }


    /**
     * Get the pitch envelope attack time.
     *
     * @return The value in the range of 0-127
     */
    public int getPegAttackTime ()
    {
        return this.pegAttackTime;
    }


    /**
     * Set the pitch envelope attack time.
     *
     * @param pegAttackTime The value in the range of 0-127
     */
    public void setPegAttackTime (final int pegAttackTime)
    {
        this.pegAttackTime = pegAttackTime;
    }


    /**
     * Get the pitch envelope decay 1 time.
     *
     * @return The value in the range of 0-127
     */
    public int getPegDecay1Time ()
    {
        return this.pegDecay1Time;
    }


    /**
     * Set the pitch envelope decay 1 time.
     *
     * @param pegDecay1Time The value in the range of 0-127
     */
    public void setPegDecay1Time (final int pegDecay1Time)
    {
        this.pegDecay1Time = pegDecay1Time;
    }


    /**
     * Get the pitch envelope decay 2 time.
     *
     * @return The value in the range of 0-127
     */
    public int getPegDecay2Time ()
    {
        return this.pegDecay2Time;
    }


    /**
     * Set the pitch envelope decay 2 time.
     *
     * @param pegDecay2Time The value in the range of 0-127
     */
    public void setPegDecay2Time (final int pegDecay2Time)
    {
        this.pegDecay2Time = pegDecay2Time;
    }


    /**
     * Get the pitch release time.
     *
     * @return The value in the range of 0-127
     */
    public int getPegReleaseTime ()
    {
        return this.pegReleaseTime;
    }


    /**
     * Set the pitch envelope release time.
     *
     * @param pegReleaseTime The value in the range of 0-127
     */
    public void setPegReleaseTime (final int pegReleaseTime)
    {
        this.pegReleaseTime = pegReleaseTime;
    }


    /**
     * Get the pitch envelope hold level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getPegHoldLevel ()
    {
        return this.pegHoldLevel;
    }


    /**
     * Set the pitch envelope hold level.
     *
     * @param pegHoldLevel The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setPegHoldLevel (final int pegHoldLevel)
    {
        this.pegHoldLevel = pegHoldLevel;
    }


    /**
     * Get the pitch envelope attack level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getPegAttackLevel ()
    {
        return this.pegAttackLevel;
    }


    /**
     * Set the pitch envelope attack level.
     *
     * @param pegAttackLevel The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setPegAttackLevel (final int pegAttackLevel)
    {
        this.pegAttackLevel = pegAttackLevel;
    }


    /**
     * Get the pitch envelope decay 1 level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getPegDecay1Level ()
    {
        return this.pegDecay1Level;
    }


    /**
     * Set the pitch envelope decay 1 level.
     *
     * @param pegDecay1Level The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setPegDecay1Level (final int pegDecay1Level)
    {
        this.pegDecay1Level = pegDecay1Level;
    }


    /**
     * Get the pitch envelope decay 2 level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getPegDecay2Level ()
    {
        return this.pegDecay2Level;
    }


    /**
     * Set the pitch envelope decay 2 level.
     *
     * @param pegDecay2Level The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setPegDecay2Level (final int pegDecay2Level)
    {
        this.pegDecay2Level = pegDecay2Level;
    }


    /**
     * Get the pitch envelope depth intensity.
     *
     * @return The value in the range of 0..127 which relates to -64..+64 (0 ~ 64)
     */
    public int getPegDepth ()
    {
        return this.pegDepth;
    }


    /**
     * Set the pitch envelope depth intensity.
     *
     * @param pegDepth The value in the range of 0..127 which relates to -64..+64 (0 ~ 64)
     */
    public void setPegDepth (final int pegDepth)
    {
        this.pegDepth = pegDepth;
    }


    /**
     * Get the filter type.
     *
     * @return 0: LPF24D, 1: LPF24A, 2: LPF18, 3: LPF18s, 4: LPF12+HPF12, 5: LPF6+HPF12, 6: HPF24D,
     *         7: HPF12, 8: BPF12D, 9: BPFw, 10: BEF12, 11: BEF6, 12: DualLPF, 13: DualHPF, 14:
     *         DualBPF, 15: DualBEF, 16: LPF12+HPF6, 17: Thru
     */
    public int getFilterType ()
    {
        return this.filterType;
    }


    /**
     * Set the filter type.
     *
     * @param filterType 0: LPF24D, 1: LPF24A, 2: LPF18, 3: LPF18s, 4: LPF12+HPF12, 5: LPF6+HPF12,
     *            6: HPF24D, 7: HPF12, 8: BPF12D, 9: BPFw, 10: BEF12, 11: BEF6, 12: DualLPF, 13:
     *            DualHPF, 14: DualBPF, 15: DualBEF, 16: LPF12+HPF6, 17: Thru
     */
    public void setFilterType (final int filterType)
    {
        this.filterType = filterType;
    }


    /**
     * Get the filter cutoff frequency.
     *
     * @return The value in the range of 0-255
     */
    public int getFilterCutoffFrequency ()
    {
        return this.filterCutoffFrequency;
    }


    /**
     * Set the filter cutoff frequency.
     *
     * @param filterCutoffFrequency The value in the range of 0-255
     */
    public void setFilterCutoffFrequency (final int filterCutoffFrequency)
    {
        this.filterCutoffFrequency = filterCutoffFrequency;
    }


    /**
     * Get the filter resonance.
     *
     * @return The value in the range of 0-127
     */
    public int getFilterResonance ()
    {
        return this.filterResonance;
    }


    /**
     * Set the filter resonance.
     *
     * @param filterResonance The value in the range of 0-127
     */
    public void setFilterResonance (final int filterResonance)
    {
        this.filterResonance = filterResonance;
    }


    /**
     * Get the filter envelope hold time.
     *
     * @return The value in the range of 0-127
     */
    public int getFegHoldTime ()
    {
        return this.fegHoldTime;
    }


    /**
     * Set the filter envelope hold time.
     *
     * @param fegHoldTime The value in the range of 0-127
     */
    public void setFegHoldTime (final int fegHoldTime)
    {
        this.fegHoldTime = fegHoldTime;
    }


    /**
     * Get the filter envelope attack time.
     *
     * @return The value in the range of 0-127
     */
    public int getFegAttackTime ()
    {
        return this.fegAttackTime;
    }


    /**
     * Set the filter envelope attack time.
     *
     * @param fegAttackTime The value in the range of 0-127
     */
    public void setFegAttackTime (final int fegAttackTime)
    {
        this.fegAttackTime = fegAttackTime;
    }


    /**
     * Get the filter envelope decay 1 time.
     *
     * @return The value in the range of 0-127
     */
    public int getFegDecay1Time ()
    {
        return this.fegDecay1Time;
    }


    /**
     * Set the filter envelope decay 1 time.
     *
     * @param fegDecay1Time The value in the range of 0-127
     */
    public void setFegDecay1Time (final int fegDecay1Time)
    {
        this.fegDecay1Time = fegDecay1Time;
    }


    /**
     * Get the filter envelope decay 2 time.
     *
     * @return The value in the range of 0-127
     */
    public int getFegDecay2Time ()
    {
        return this.fegDecay2Time;
    }


    /**
     * Set the filter envelope decay 2 time.
     *
     * @param fegDecay2Time The value in the range of 0-127
     */
    public void setFegDecay2Time (final int fegDecay2Time)
    {
        this.fegDecay2Time = fegDecay2Time;
    }


    /**
     * Get the filter envelope release time.
     *
     * @return The value in the range of 0-127
     */
    public int getFegReleaseTime ()
    {
        return this.fegReleaseTime;
    }


    /**
     * Set the filter envelope release time.
     *
     * @param fegReleaseTime The value in the range of 0-127
     */
    public void setFegReleaseTime (final int fegReleaseTime)
    {
        this.fegReleaseTime = fegReleaseTime;
    }


    /**
     * Get the filter envelope hold level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getFegHoldLevel ()
    {
        return this.fegHoldLevel;
    }


    /**
     * Set the filter envelope hold level.
     *
     * @param fegHoldLevel The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setFegHoldLevel (final int fegHoldLevel)
    {
        this.fegHoldLevel = fegHoldLevel;
    }


    /**
     * Get the filter envelope attack level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getFegAttackLevel ()
    {
        return this.fegAttackLevel;
    }


    /**
     * Set the filter envelope attack level.
     *
     * @param fegAttackLevel The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setFegAttackLevel (final int fegAttackLevel)
    {
        this.fegAttackLevel = fegAttackLevel;
    }


    /**
     * Get the filter envelope decay 1 level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getFegDecay1Level ()
    {
        return this.fegDecay1Level;
    }


    /**
     * Set the filter envelope decay 1 level.
     *
     * @param fegDecay1Level The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setFegDecay1Level (final int fegDecay1Level)
    {
        this.fegDecay1Level = fegDecay1Level;
    }


    /**
     * Get the filter envelope decay 2 level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getFegDecay2Level ()
    {
        return this.fegDecay2Level;
    }


    /**
     * Set the filter envelope decay 2 level.
     *
     * @param fegDecay2Level The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setFegDecay2Level (final int fegDecay2Level)
    {
        this.fegDecay2Level = fegDecay2Level;
    }


    /**
     * Get the filter envelope release level.
     *
     * @return The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public int getFegReleaseLevel ()
    {
        return this.fegReleaseLevel;
    }


    /**
     * Set the filter envelope release level.
     *
     * @param fegReleaseLevel The value in the range of 0..255 which relates to -128..+127 (0 ~ 128)
     */
    public void setFegReleaseLevel (final int fegReleaseLevel)
    {
        this.fegReleaseLevel = fegReleaseLevel;
    }


    /**
     * Get the filter envelope depth intensity.
     *
     * @return The value in the range of 0..127 which relates to -64..+64 (0 ~ 64)
     */
    public int getFegDepth ()
    {
        return this.fegDepth;
    }


    /**
     * Set the filter envelope depth intensity.
     *
     * @param fegDepth The value in the range of 0..127 which relates to -64..+64 (0 ~ 64)
     */
    public void setFegDepth (final int fegDepth)
    {
        this.fegDepth = fegDepth;
    }


    /**
     * Get the filter velocity sensitivity.
     *
     * @return The value in the range of 0..127 which relates to -64..+63 (0 ~ 64)
     */
    public int getFegLevelVelocitySensitivity ()
    {
        return this.fegLevelVelocitySensitivity;
    }


    /**
     * Get the filter velocity sensitivity.
     *
     * @param fegLevelVelocitySensitivity The value in the range of 0..127 which relates to -64..+63
     *            (0 ~ 64)
     */
    public void setFegLevelVelocitySensitivity (final int fegLevelVelocitySensitivity)
    {
        this.fegLevelVelocitySensitivity = fegLevelVelocitySensitivity;
    }


    /**
     * Get the number of the referenced waveform.
     *
     * @return The number in the range of 1 - 6347 （USR, Library 1-2048)
     */
    public int getWaveformNumber ()
    {
        return this.waveformNumber;
    }


    /**
     * Set the number of the referenced waveform.
     *
     * @param waveformNumber The number in the range of 1 - 6347 （USR, Library 1-2048)
     */
    public void setWaveformNumber (final int waveformNumber)
    {
        this.waveformNumber = waveformNumber;
    }


    /**
     * Read a performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final YamahaYsfcFileFormat version) throws IOException
    {
        final byte [] dataBlock = StreamUtils.readDataBlock (in, true);

        final InputStream elementDataIn = new ByteArrayInputStream (dataBlock);
        this.elementSwitch = elementDataIn.read ();
        this.waveBank = elementDataIn.read ();
        this.elementGroupNumber = elementDataIn.read ();
        this.receiveNoteOff = elementDataIn.read ();
        this.keyAssignMode = elementDataIn.read ();
        this.alternateGroup = elementDataIn.read ();
        this.pan = elementDataIn.read ();
        this.randomPanDepth = elementDataIn.read ();
        this.alternatePanDepth = elementDataIn.read ();
        this.scalingPanDepth = elementDataIn.read ();
        this.xaMode = elementDataIn.read ();
        this.noteLimitLow = elementDataIn.read ();
        this.noteLimitHigh = elementDataIn.read ();
        this.velocityLimitLow = elementDataIn.read ();
        this.velocityLimitHigh = elementDataIn.read ();
        this.velocityCrossFade = elementDataIn.read ();
        this.keyOnDelay = elementDataIn.read ();
        this.keyOnDelayTempoSync = elementDataIn.read ();
        this.reverbSendLevel = elementDataIn.read ();
        this.variationSendLevel = elementDataIn.read ();
        this.insertionEffectSwitch = elementDataIn.read ();
        this.outputSelect = elementDataIn.read ();
        this.controlBox1Sw = elementDataIn.read ();
        this.controlBox2Sw = elementDataIn.read ();
        this.controlBox3Sw = elementDataIn.read ();
        this.controlBox4Sw = elementDataIn.read ();
        this.controlBox5Sw = elementDataIn.read ();
        this.controlBox6Sw = elementDataIn.read ();
        this.controlBox7Sw = elementDataIn.read ();
        this.controlBox8Sw = elementDataIn.read ();
        this.controlBox9Sw = elementDataIn.read ();
        this.controlBox10Sw = elementDataIn.read ();
        this.controlBox11Sw = elementDataIn.read ();
        this.controlBox12Sw = elementDataIn.read ();
        this.controlBox13Sw = elementDataIn.read ();
        this.controlBox14Sw = elementDataIn.read ();
        this.controlBox15Sw = elementDataIn.read ();
        this.controlBox16Sw = elementDataIn.read ();
        this.keyOnDelayTempo = elementDataIn.read ();
        this.halfDamperSwitch = elementDataIn.read ();
        this.elementLevel = elementDataIn.read ();
        this.levelVelocitySensitivity = elementDataIn.read ();
        this.levelVelocityOffset = elementDataIn.read ();
        this.levelSensKeyCurve = elementDataIn.read ();
        this.aegAttackTime = elementDataIn.read ();
        this.aegDecay1Time = elementDataIn.read ();
        this.aegDecay2Time = elementDataIn.read ();
        this.aegSustainTime = elementDataIn.read ();
        this.aegReleaseTime = elementDataIn.read ();
        this.aegInitLevel = elementDataIn.read ();
        this.aegAttackLevel = elementDataIn.read ();
        this.aegDecay1Level = elementDataIn.read ();
        this.aegDecay2Level = elementDataIn.read ();
        this.aegTimeVelocitySegment = elementDataIn.read ();
        this.aegTimeVelocitySensitivity = elementDataIn.read ();
        this.aegTimeKeyFollowSensitivity = elementDataIn.read ();
        this.aegTimeKeyFollowCenterNote = elementDataIn.read ();
        this.aegTimeKeyFollowAdjustment = elementDataIn.read ();
        this.levelScalingBreakPoint1 = elementDataIn.read ();
        this.levelScalingBreakPoint2 = elementDataIn.read ();
        this.levelScalingBreakPoint3 = elementDataIn.read ();
        this.levelScalingBreakPoint4 = elementDataIn.read ();
        this.levelScalingOffset1 = elementDataIn.read ();
        this.levelScalingOffset2 = elementDataIn.read ();
        this.levelScalingOffset3 = elementDataIn.read ();
        this.levelScalingOffset4 = elementDataIn.read ();
        this.levelKeyFollowSensitivity = elementDataIn.read ();
        this.coarseTune = elementDataIn.read ();
        this.fineTune = elementDataIn.read ();
        this.pitchVelocitySensitivity = elementDataIn.read ();
        this.randomPitchDepth = elementDataIn.read ();
        this.pitchKeyFollowSensitivity = elementDataIn.read ();
        this.pitchKeyFollowCenterNote = elementDataIn.read ();
        this.pitchFineScalingSensitivity = elementDataIn.read ();
        this.pegHoldTime = elementDataIn.read ();
        this.pegAttackTime = elementDataIn.read ();
        this.pegDecay1Time = elementDataIn.read ();
        this.pegDecay2Time = elementDataIn.read ();
        this.pegReleaseTime = elementDataIn.read ();
        this.pegHoldLevel = elementDataIn.read ();
        this.pegAttackLevel = elementDataIn.read ();
        this.pegDecay1Level = elementDataIn.read ();
        this.pegDecay2Level = elementDataIn.read ();
        this.pegReleaseLevel = elementDataIn.read ();
        this.pegDepth = elementDataIn.read ();
        this.pegTimeVelocitySegment = elementDataIn.read ();
        this.pegTimeVelocitySensitivity = elementDataIn.read ();
        this.pegLevelVelocitySensitivity = elementDataIn.read ();
        this.pegLevelSensVelocityCurve = elementDataIn.read ();
        this.pegTimeKeyFollowSensitivity = elementDataIn.read ();
        this.pegTimeKeyFollowCenterNote = elementDataIn.read ();
        this.filterType = elementDataIn.read ();
        this.filterCutoffFrequency = elementDataIn.read ();
        this.filterCutoffVelocitySensitivity = elementDataIn.read ();
        this.filterResonance = elementDataIn.read ();
        this.filterResonanceVelocitySensitivity = elementDataIn.read ();
        this.hpfCutoffFrequency = elementDataIn.read ();
        this.distance = elementDataIn.read ();
        this.filterGain = elementDataIn.read ();
        this.fegHoldTime = elementDataIn.read ();
        this.fegAttackTime = elementDataIn.read ();
        this.fegDecay1Time = elementDataIn.read ();
        this.fegDecay2Time = elementDataIn.read ();
        this.fegReleaseTime = elementDataIn.read ();
        this.fegHoldLevel = elementDataIn.read ();
        this.fegAttackLevel = elementDataIn.read ();
        this.fegDecay1Level = elementDataIn.read ();
        this.fegDecay2Level = elementDataIn.read ();
        this.fegReleaseLevel = elementDataIn.read ();
        this.fegDepth = elementDataIn.read ();
        this.fegTimeVelocitySegment = elementDataIn.read ();
        this.fegTimeVelocitySensitivity = elementDataIn.read ();
        this.fegLevelVelocitySensitivity = elementDataIn.read ();
        this.fegLevelVelocityCurve = elementDataIn.read ();
        this.fegTimeKeyFollowSensitivity = elementDataIn.read ();
        this.fegTimeKeyFollowCenterNote = elementDataIn.read ();
        this.filterCutoffScalingBreakPoint1 = elementDataIn.read ();
        this.filterCutoffScalingBreakPoint2 = elementDataIn.read ();
        this.filterCutoffScalingBreakPoint3 = elementDataIn.read ();
        this.filterCutoffScalingBreakPoint4 = elementDataIn.read ();
        this.filterCutoffScalingOffset1 = elementDataIn.read ();
        this.filterCutoffScalingOffset2 = elementDataIn.read ();
        this.filterCutoffScalingOffset3 = elementDataIn.read ();
        this.filterCutoffScalingOffset4 = elementDataIn.read ();
        this.filterCutoffKeyFollowSensitivity = elementDataIn.read ();
        this.hpfCutoffKeyFollowSensitivity = elementDataIn.read ();
        this.eqType = elementDataIn.read ();
        this.eqResonance = elementDataIn.read ();
        this.eq1Frequency = elementDataIn.read ();
        this.eq1Gain = elementDataIn.read ();
        this.eq2Frequency = elementDataIn.read ();
        this.eq2Gain = elementDataIn.read ();
        this.lfoWave = elementDataIn.read ();
        this.lfoKeyOnSync = elementDataIn.read ();
        this.lfoKeyOnDelayTime = elementDataIn.read ();
        this.lfoSpeed = elementDataIn.read ();
        this.lfoAmodDepth = elementDataIn.read ();
        this.lfoPmodDepth = elementDataIn.read ();
        this.lfoFmodDepth = elementDataIn.read ();
        this.lfoFadeInTime = elementDataIn.read ();
        this.commonLfoPhaseOffset = elementDataIn.read ();
        this.commonLfoBox1DepthRatio = elementDataIn.read ();
        this.commonLfoBox2DepthRatio = elementDataIn.read ();
        this.commonLfoBox3DepthRatio = elementDataIn.read ();

        final int rest = elementDataIn.available ();
        if (rest != 2 && rest != 5)
            throw new IOException (Functions.getMessage ("IDS_YSFC_UNKNOWN_ELEMENT_STRUCT_SIZE"));

        // No idea about these 3 bytes
        if (rest == 5)
            this.unknownBytes = elementDataIn.readNBytes (3);

        this.waveformNumber = StreamUtils.readUnsigned16 (elementDataIn, false);
    }


    /**
     * Write a performance to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream elementOut = new ByteArrayOutputStream ();
        elementOut.write (this.elementSwitch);
        elementOut.write (this.waveBank);
        elementOut.write (this.elementGroupNumber);
        elementOut.write (this.receiveNoteOff);
        elementOut.write (this.keyAssignMode);
        elementOut.write (this.alternateGroup);
        elementOut.write (this.pan);
        elementOut.write (this.randomPanDepth);
        elementOut.write (this.alternatePanDepth);
        elementOut.write (this.scalingPanDepth);
        elementOut.write (this.xaMode);
        elementOut.write (this.noteLimitLow);
        elementOut.write (this.noteLimitHigh);
        elementOut.write (this.velocityLimitLow);
        elementOut.write (this.velocityLimitHigh);
        elementOut.write (this.velocityCrossFade);
        elementOut.write (this.keyOnDelay);
        elementOut.write (this.keyOnDelayTempoSync);
        elementOut.write (this.reverbSendLevel);
        elementOut.write (this.variationSendLevel);
        elementOut.write (this.insertionEffectSwitch);
        elementOut.write (this.outputSelect);
        elementOut.write (this.controlBox1Sw);
        elementOut.write (this.controlBox2Sw);
        elementOut.write (this.controlBox3Sw);
        elementOut.write (this.controlBox4Sw);
        elementOut.write (this.controlBox5Sw);
        elementOut.write (this.controlBox6Sw);
        elementOut.write (this.controlBox7Sw);
        elementOut.write (this.controlBox8Sw);
        elementOut.write (this.controlBox9Sw);
        elementOut.write (this.controlBox10Sw);
        elementOut.write (this.controlBox11Sw);
        elementOut.write (this.controlBox12Sw);
        elementOut.write (this.controlBox13Sw);
        elementOut.write (this.controlBox14Sw);
        elementOut.write (this.controlBox15Sw);
        elementOut.write (this.controlBox16Sw);
        elementOut.write (this.keyOnDelayTempo);
        elementOut.write (this.halfDamperSwitch);
        elementOut.write (this.elementLevel);
        elementOut.write (this.levelVelocitySensitivity);
        elementOut.write (this.levelVelocityOffset);
        elementOut.write (this.levelSensKeyCurve);
        elementOut.write (this.aegAttackTime);
        elementOut.write (this.aegDecay1Time);
        elementOut.write (this.aegDecay2Time);
        elementOut.write (this.aegSustainTime);
        elementOut.write (this.aegReleaseTime);
        elementOut.write (this.aegInitLevel);
        elementOut.write (this.aegAttackLevel);
        elementOut.write (this.aegDecay1Level);
        elementOut.write (this.aegDecay2Level);
        elementOut.write (this.aegTimeVelocitySegment);
        elementOut.write (this.aegTimeVelocitySensitivity);
        elementOut.write (this.aegTimeKeyFollowSensitivity);
        elementOut.write (this.aegTimeKeyFollowCenterNote);
        elementOut.write (this.aegTimeKeyFollowAdjustment);
        elementOut.write (this.levelScalingBreakPoint1);
        elementOut.write (this.levelScalingBreakPoint2);
        elementOut.write (this.levelScalingBreakPoint3);
        elementOut.write (this.levelScalingBreakPoint4);
        elementOut.write (this.levelScalingOffset1);
        elementOut.write (this.levelScalingOffset2);
        elementOut.write (this.levelScalingOffset3);
        elementOut.write (this.levelScalingOffset4);
        elementOut.write (this.levelKeyFollowSensitivity);
        elementOut.write (this.coarseTune);
        elementOut.write (this.fineTune);
        elementOut.write (this.pitchVelocitySensitivity);
        elementOut.write (this.randomPitchDepth);
        elementOut.write (this.pitchKeyFollowSensitivity);
        elementOut.write (this.pitchKeyFollowCenterNote);
        elementOut.write (this.pitchFineScalingSensitivity);
        elementOut.write (this.pegHoldTime);
        elementOut.write (this.pegAttackTime);
        elementOut.write (this.pegDecay1Time);
        elementOut.write (this.pegDecay2Time);
        elementOut.write (this.pegReleaseTime);
        elementOut.write (this.pegHoldLevel);
        elementOut.write (this.pegAttackLevel);
        elementOut.write (this.pegDecay1Level);
        elementOut.write (this.pegDecay2Level);
        elementOut.write (this.pegReleaseLevel);
        elementOut.write (this.pegDepth);
        elementOut.write (this.pegTimeVelocitySegment);
        elementOut.write (this.pegTimeVelocitySensitivity);
        elementOut.write (this.pegLevelVelocitySensitivity);
        elementOut.write (this.pegLevelSensVelocityCurve);
        elementOut.write (this.pegTimeKeyFollowSensitivity);
        elementOut.write (this.pegTimeKeyFollowCenterNote);
        elementOut.write (this.filterType);
        elementOut.write (this.filterCutoffFrequency);
        elementOut.write (this.filterCutoffVelocitySensitivity);
        elementOut.write (this.filterResonance);
        elementOut.write (this.filterResonanceVelocitySensitivity);
        elementOut.write (this.hpfCutoffFrequency);
        elementOut.write (this.distance);
        elementOut.write (this.filterGain);
        elementOut.write (this.fegHoldTime);
        elementOut.write (this.fegAttackTime);
        elementOut.write (this.fegDecay1Time);
        elementOut.write (this.fegDecay2Time);
        elementOut.write (this.fegReleaseTime);
        elementOut.write (this.fegHoldLevel);
        elementOut.write (this.fegAttackLevel);
        elementOut.write (this.fegDecay1Level);
        elementOut.write (this.fegDecay2Level);
        elementOut.write (this.fegReleaseLevel);
        elementOut.write (this.fegDepth);
        elementOut.write (this.fegTimeVelocitySegment);
        elementOut.write (this.fegTimeVelocitySensitivity);
        elementOut.write (this.fegLevelVelocitySensitivity);
        elementOut.write (this.fegLevelVelocityCurve);
        elementOut.write (this.fegTimeKeyFollowSensitivity);
        elementOut.write (this.fegTimeKeyFollowCenterNote);
        elementOut.write (this.filterCutoffScalingBreakPoint1);
        elementOut.write (this.filterCutoffScalingBreakPoint2);
        elementOut.write (this.filterCutoffScalingBreakPoint3);
        elementOut.write (this.filterCutoffScalingBreakPoint4);
        elementOut.write (this.filterCutoffScalingOffset1);
        elementOut.write (this.filterCutoffScalingOffset2);
        elementOut.write (this.filterCutoffScalingOffset3);
        elementOut.write (this.filterCutoffScalingOffset4);
        elementOut.write (this.filterCutoffKeyFollowSensitivity);
        elementOut.write (this.hpfCutoffKeyFollowSensitivity);
        elementOut.write (this.eqType);
        elementOut.write (this.eqResonance);
        elementOut.write (this.eq1Frequency);
        elementOut.write (this.eq1Gain);
        elementOut.write (this.eq2Frequency);
        elementOut.write (this.eq2Gain);
        elementOut.write (this.lfoWave);
        elementOut.write (this.lfoKeyOnSync);
        elementOut.write (this.lfoKeyOnDelayTime);
        elementOut.write (this.lfoSpeed);
        elementOut.write (this.lfoAmodDepth);
        elementOut.write (this.lfoPmodDepth);
        elementOut.write (this.lfoFmodDepth);
        elementOut.write (this.lfoFadeInTime);
        elementOut.write (this.commonLfoPhaseOffset);
        elementOut.write (this.commonLfoBox1DepthRatio);
        elementOut.write (this.commonLfoBox2DepthRatio);
        elementOut.write (this.commonLfoBox3DepthRatio);
        // This is always filled by the template, therefore never null!
        elementOut.write (this.unknownBytes);
        StreamUtils.writeUnsigned16 (elementOut, this.waveformNumber, false);

        StreamUtils.writeDataBlock (out, elementOut.toByteArray (), true);
    }


    /**
     * Converts an envelope time value to seconds.
     *
     * @param envelopeTime The time in the range of 0-127
     * @return The time in seconds, attack times should be shortened by factor 6
     */
    public static double convertEnvelopeTimeToSeconds (final int envelopeTime)
    {
        return ENVELOPE_TIMES.get (Integer.valueOf (Math.clamp (envelopeTime, 0, 127))).doubleValue ();
    }


    /**
     * Converts an envelope time value to seconds.
     *
     * @param seconds The time in seconds, attack times should be enlarged by factor 6
     * @return The time in the range of 0-127
     */
    public static int convertSecondsToEnvelopeTime (final double seconds)
    {
        if (seconds <= MINIMUM_ENVELOPE_TIME)
            return 0;

        for (int i = 0; i < 127; i++)
            if (seconds >= ENVELOPE_TIMES.get (Integer.valueOf (i)).intValue () && seconds < ENVELOPE_TIMES.get (Integer.valueOf (i + 1)).intValue ())
                return i;

        return 127;
    }
}
