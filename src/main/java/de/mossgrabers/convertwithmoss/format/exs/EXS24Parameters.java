// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * EXS24 parameter constants.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public class EXS24Parameters extends EXS24Object
{
    private static final Map<Integer, String>  PARAM_NAMES    = new HashMap<> ();
    private static final Map<Integer, Integer> DEFAULT_PARAMS = new HashMap<> ();

    static
    {
        DEFAULT_PARAMS.put (Integer.valueOf (7), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (8), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (3), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (4), Integer.valueOf (-1));
        DEFAULT_PARAMS.put (Integer.valueOf (5), Integer.valueOf (16));
        DEFAULT_PARAMS.put (Integer.valueOf (20), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (73), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (243), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (30), Integer.valueOf (1000));
        DEFAULT_PARAMS.put (Integer.valueOf (29), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (75), Integer.valueOf (100));
        DEFAULT_PARAMS.put (Integer.valueOf (46), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (90), Integer.valueOf (-60));
        DEFAULT_PARAMS.put (Integer.valueOf (89), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (60), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (61), Integer.valueOf (98));
        DEFAULT_PARAMS.put (Integer.valueOf (62), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (64), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (63), Integer.valueOf (98));
        DEFAULT_PARAMS.put (Integer.valueOf (76), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (77), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (78), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (79), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (80), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (92), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (82), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (83), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (84), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (81), Integer.valueOf (127));
        DEFAULT_PARAMS.put (Integer.valueOf (85), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (97), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (165), Integer.valueOf (1));
        DEFAULT_PARAMS.put (Integer.valueOf (167), Integer.valueOf (98));
        DEFAULT_PARAMS.put (Integer.valueOf (166), Integer.valueOf (-1));
        DEFAULT_PARAMS.put (Integer.valueOf (172), Integer.valueOf (64));
        DEFAULT_PARAMS.put (Integer.valueOf (173), Integer.valueOf (8));
        DEFAULT_PARAMS.put (Integer.valueOf (174), Integer.valueOf (-14));
        DEFAULT_PARAMS.put (Integer.valueOf (175), Integer.valueOf (-1));
        DEFAULT_PARAMS.put (Integer.valueOf (176), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (177), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (179), Integer.valueOf (6));
        DEFAULT_PARAMS.put (Integer.valueOf (180), Integer.valueOf (-12));
        DEFAULT_PARAMS.put (Integer.valueOf (181), Integer.valueOf (1));
        DEFAULT_PARAMS.put (Integer.valueOf (182), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (183), Integer.valueOf (343));
        DEFAULT_PARAMS.put (Integer.valueOf (254), Integer.valueOf (1000));
        DEFAULT_PARAMS.put (Integer.valueOf (378), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (375), Integer.valueOf (1000));
        DEFAULT_PARAMS.put (Integer.valueOf (376), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (377), Integer.valueOf (100));
        DEFAULT_PARAMS.put (Integer.valueOf (389), Integer.valueOf (3));
        DEFAULT_PARAMS.put (Integer.valueOf (390), Integer.valueOf (3));
        DEFAULT_PARAMS.put (Integer.valueOf (363), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (500), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (362), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (501), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (353), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (354), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (355), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (357), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (358), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (359), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (502), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (503), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (505), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (506), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (509), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (511), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (512), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (515), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (516), Integer.valueOf (2));
        DEFAULT_PARAMS.put (Integer.valueOf (518), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (519), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (522), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (524), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (525), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (528), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (335), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (334), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (336), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (337), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (341), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (340), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (342), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (343), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (344), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (347), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (349), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (492), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (491), Integer.valueOf (98));
        DEFAULT_PARAMS.put (Integer.valueOf (493), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (496), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (498), Integer.valueOf (0));
        DEFAULT_PARAMS.put (Integer.valueOf (535), Integer.valueOf (1));
        DEFAULT_PARAMS.put (Integer.valueOf (282), Integer.valueOf (48));
        DEFAULT_PARAMS.put (Integer.valueOf (387), Integer.valueOf (-10));
        DEFAULT_PARAMS.put (Integer.valueOf (388), Integer.valueOf (1));

        PARAM_NAMES.put (Integer.valueOf (0x07), "MASTER_VOLUME");
        PARAM_NAMES.put (Integer.valueOf (0x160), "MASTER_PAN");
        PARAM_NAMES.put (Integer.valueOf (0x08), "VOLUME_KEYSCALE");
        PARAM_NAMES.put (Integer.valueOf (0x03), "PITCH_BEND_UP");
        PARAM_NAMES.put (Integer.valueOf (0x04), "PITCH_BEND_DOWN");
        PARAM_NAMES.put (Integer.valueOf (0x0a), "MONO_LEGATO");
        PARAM_NAMES.put (Integer.valueOf (0x116), "MIDI_MONO_MODE");
        PARAM_NAMES.put (Integer.valueOf (0x11a), "MIDI_MONO_MODE_PITCH_RANGE");
        PARAM_NAMES.put (Integer.valueOf (0x05), "POLYPHONY_VOICES");
        PARAM_NAMES.put (Integer.valueOf (0x2d), "TRANSPOSE");
        PARAM_NAMES.put (Integer.valueOf (0x0e), "COARSE_TUNE");
        PARAM_NAMES.put (Integer.valueOf (0x0f), "FINE_TUNE");
        PARAM_NAMES.put (Integer.valueOf (0x14), "GLIDE");
        PARAM_NAMES.put (Integer.valueOf (0x48), "PORTA_DOWN");
        PARAM_NAMES.put (Integer.valueOf (0x49), "PORTA_UP");
        PARAM_NAMES.put (Integer.valueOf (0x2c), "FILTER1_TOGGLE");
        PARAM_NAMES.put (Integer.valueOf (0xf3), "FILTER1_TYPE");
        PARAM_NAMES.put (Integer.valueOf (0xaa), "FILTER1_FAT");
        PARAM_NAMES.put (Integer.valueOf (0x1e), "FILTER1_CUTOFF");
        PARAM_NAMES.put (Integer.valueOf (0x1d), "FILTER1_RESO");
        PARAM_NAMES.put (Integer.valueOf (0x4b), "FILTER1_DRIVE");
        PARAM_NAMES.put (Integer.valueOf (0x2e), "FILTER1_KEYTRACK");
        PARAM_NAMES.put (Integer.valueOf (0x174), "FILTER2_TOGGLE");
        PARAM_NAMES.put (Integer.valueOf (0x186), "FILTER2_TYPE");
        PARAM_NAMES.put (Integer.valueOf (0x177), "FILTER2_CUTOFF");
        PARAM_NAMES.put (Integer.valueOf (0x178), "FILTER2_RESO");
        PARAM_NAMES.put (Integer.valueOf (0x179), "FILTER2_DRIVE");
        PARAM_NAMES.put (Integer.valueOf (0x173), "FILTERS_SERIAL_PARALLEL");
        PARAM_NAMES.put (Integer.valueOf (0x17a), "FILTERS_BLEND");
        PARAM_NAMES.put (Integer.valueOf (0x3c), "LFO_1_FADE");
        PARAM_NAMES.put (Integer.valueOf (0x3d), "LFO_1_RATE");
        PARAM_NAMES.put (Integer.valueOf (0x3e), "LFO_1_WAVE_SHAPE");
        PARAM_NAMES.put (Integer.valueOf (0x14c), "LFO_1_KEY_TRIGGER");
        PARAM_NAMES.put (Integer.valueOf (0x14d), "LFO_1_MONO_POLY");
        PARAM_NAMES.put (Integer.valueOf (0x14e), "LFO_1_PHASE");
        PARAM_NAMES.put (Integer.valueOf (0x14f), "LFO_1_POSITIVE_OR_MIDPOINT");
        PARAM_NAMES.put (Integer.valueOf (0x150), "LFO_1_TEMPO_SYNC");
        PARAM_NAMES.put (Integer.valueOf (0x187), "LFO_1_FADE_IN_OR_OUT");
        PARAM_NAMES.put (Integer.valueOf (0x3f), "LFO_2_RATE");
        PARAM_NAMES.put (Integer.valueOf (0x40), "LFO_2_WAVE_SHAPE");
        PARAM_NAMES.put (Integer.valueOf (0x151), "LFO_2_FADE");
        PARAM_NAMES.put (Integer.valueOf (0x152), "LFO_2_KEY_TRIGGER");
        PARAM_NAMES.put (Integer.valueOf (0x153), "LFO_2_MONO_POLY");
        PARAM_NAMES.put (Integer.valueOf (0x154), "LFO_2_PHASE");
        PARAM_NAMES.put (Integer.valueOf (0x155), "LFO_2_POSITIVE_OR_MIDPOINT");
        PARAM_NAMES.put (Integer.valueOf (0x156), "LFO_2_TEMPO_SYNC");
        PARAM_NAMES.put (Integer.valueOf (0x188), "LFO_2_FADE_IN_OR_OUT");
        PARAM_NAMES.put (Integer.valueOf (0xa7), "LFO_3_RATE");
        PARAM_NAMES.put (Integer.valueOf (0x158), "LFO_3_WAVE_SHAPE");
        PARAM_NAMES.put (Integer.valueOf (0x157), "LFO_3_FADE");
        PARAM_NAMES.put (Integer.valueOf (0x159), "LFO_3_KEY_TRIGGER");
        PARAM_NAMES.put (Integer.valueOf (0x15a), "LFO_3_MONO_POLY");
        PARAM_NAMES.put (Integer.valueOf (0x15b), "LFO_3_PHASE");
        PARAM_NAMES.put (Integer.valueOf (0x15c), "LFO_3_POSITIVE_OR_MIDPOINT");
        PARAM_NAMES.put (Integer.valueOf (0x15d), "LFO_3_TEMPO_SYNC");
        PARAM_NAMES.put (Integer.valueOf (0x189), "LFO_3_FADE_IN_OR_OUT");
        PARAM_NAMES.put (Integer.valueOf (0x16a), "ENV2_TYPE");
        PARAM_NAMES.put (Integer.valueOf (0x17d), "ENV2_VEL_SENS");
        PARAM_NAMES.put (Integer.valueOf (0x16c), "ENV2_DELAY_START");
        PARAM_NAMES.put (Integer.valueOf (0x192), "ENV2_ATK_CURVE");
        PARAM_NAMES.put (Integer.valueOf (0x4c), "ENV2_ATK_HI_VEL");
        PARAM_NAMES.put (Integer.valueOf (0x4d), "ENV2_ATK_LO_VEL");
        PARAM_NAMES.put (Integer.valueOf (0x38), "ENV2_HOLD");
        PARAM_NAMES.put (Integer.valueOf (0x4e), "ENV2_DECAY");
        PARAM_NAMES.put (Integer.valueOf (0x4f), "ENV2_SUSTAIN");
        PARAM_NAMES.put (Integer.valueOf (0x50), "ENV2_RELEASE");
        PARAM_NAMES.put (Integer.valueOf (0x5b), "ENV2_TIME_CURVE");
        PARAM_NAMES.put (Integer.valueOf (0x16b), "ENV1_TYPE");
        PARAM_NAMES.put (Integer.valueOf (0x5a), "ENV1_VEL_SENS");
        PARAM_NAMES.put (Integer.valueOf (0x59), "ENV1_VOLUME_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0x16d), "ENV1_DELAY_START");
        PARAM_NAMES.put (Integer.valueOf (0x195), "ENV1_ATK_CURVE");
        PARAM_NAMES.put (Integer.valueOf (0x52), "ENV1_ATK_HI_VEL");
        PARAM_NAMES.put (Integer.valueOf (0x53), "ENV1_ATK_LO_VEL");
        PARAM_NAMES.put (Integer.valueOf (0x58), "ENV1_HOLD");
        PARAM_NAMES.put (Integer.valueOf (0x54), "ENV1_DECAY");
        PARAM_NAMES.put (Integer.valueOf (0x51), "ENV1_SUSTAIN");
        PARAM_NAMES.put (Integer.valueOf (0x55), "ENV1_RELEASE");
        PARAM_NAMES.put (Integer.valueOf (-1), "ENV1_TIME_CURVE");
        PARAM_NAMES.put (Integer.valueOf (0x183), "AMP_VELOCITY_CURVE");
        PARAM_NAMES.put (Integer.valueOf (0x5f), "VELOCITY_OFFSET");
        PARAM_NAMES.put (Integer.valueOf (0xa4), "RANDOM_VELOCITY");
        PARAM_NAMES.put (Integer.valueOf (0xa3), "RANDOM_SAMPLE_SEL");
        PARAM_NAMES.put (Integer.valueOf (0x62), "RANDOM_PITCH");
        PARAM_NAMES.put (Integer.valueOf (0x61), "XFADE_AMOUNT");
        PARAM_NAMES.put (Integer.valueOf (0xa5), "XFADE_TYPE");
        PARAM_NAMES.put (Integer.valueOf (0xab), "UNISON_TOGGLE");
        PARAM_NAMES.put (Integer.valueOf (0xa6), "COARSE_TUNE_REMOTE");
        PARAM_NAMES.put (Integer.valueOf (0xac), "HOLD_VIA_CONTROL");
        PARAM_NAMES.put (Integer.valueOf (0xad), "MOD1_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xae), "MOD1_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xaf), "MOD1_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xb0), "MOD1_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xb1), "MOD1_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xe9), "MOD1_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xb2), "MOD1_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf4), "MOD1_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xb3), "MOD2_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xb4), "MOD2_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xb5), "MOD2_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xb6), "MOD2_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xb7), "MOD2_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xea), "MOD2_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xb8), "MOD2_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf5), "MOD2_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xb9), "MOD3_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xba), "MOD3_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xbb), "MOD3_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xbc), "MOD3_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xbd), "MOD3_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xeb), "MOD3_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xbe), "MOD3_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf6), "MOD3_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xbf), "MOD4_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xc0), "MOD4_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xc1), "MOD4_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xc2), "MOD4_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xc3), "MOD4_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xec), "MOD4_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xc4), "MOD4_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf7), "MOD4_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xc5), "MOD5_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xc6), "MOD5_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xc7), "MOD5_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xc8), "MOD5_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xc9), "MOD5_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xed), "MOD5_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xca), "MOD5_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf8), "MOD5_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xcb), "MOD6_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xcc), "MOD6_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xcd), "MOD6_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xce), "MOD6_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xcf), "MOD6_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xee), "MOD6_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xd0), "MOD6_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xf9), "MOD6_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xd1), "MOD7_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xd2), "MOD7_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xd3), "MOD7_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xd4), "MOD7_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xd5), "MOD7_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xef), "MOD7_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xd6), "MOD7_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xfa), "MOD7_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xd7), "MOD8_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xd8), "MOD8_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xd9), "MOD8_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xda), "MOD8_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xdb), "MOD8_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xf0), "MOD8_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xdc), "MOD8_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xfb), "MOD8_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xdd), "MOD9_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xde), "MOD9_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xdf), "MOD9_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xe0), "MOD9_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xe1), "MOD9_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xf1), "MOD9_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xe2), "MOD9_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xfc), "MOD9_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0xe3), "MOD10_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0xe4), "MOD10_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0xe5), "MOD10_VIA");
        PARAM_NAMES.put (Integer.valueOf (0xe6), "MOD10_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0xe7), "MOD10_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0xf2), "MOD10_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xe8), "MOD10_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0xfd), "MOD10_BYPASS");
        PARAM_NAMES.put (Integer.valueOf (0x19b), "MOD11_DESTINATION");
        PARAM_NAMES.put (Integer.valueOf (0x19c), "MOD11_SOURCE");
        PARAM_NAMES.put (Integer.valueOf (0x19d), "MOD11_VIA");
        PARAM_NAMES.put (Integer.valueOf (0x19e), "MOD11_AMOUNT_LOW");
        PARAM_NAMES.put (Integer.valueOf (0x19f), "MOD11_AMOUNT_HIGH");
        PARAM_NAMES.put (Integer.valueOf (0x1a0), "MOD11_SRC_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0x1a1), "MOD11_VIA_INVERT");
        PARAM_NAMES.put (Integer.valueOf (0x1a2), "MOD11_BYPASS");
    }

    public static final int MASTER_VOLUME              = 0x07;
    public static final int MASTER_PAN                 = 0x160;

    public static final int VOLUME_KEYSCALE            = 0x08;
    public static final int PITCH_BEND_UP              = 0x03;
    public static final int PITCH_BEND_DOWN            = 0x04;
    public static final int MONO_LEGATO                = 0x0a;
    public static final int MIDI_MONO_MODE             = 0x116;
    public static final int MIDI_MONO_MODE_PITCH_RANGE = 0x11a;

    public static final int POLYPHONY_VOICES           = 0x05;
    public static final int TRANSPOSE                  = 0x2d;
    public static final int COARSE_TUNE                = 0x0e;
    public static final int FINE_TUNE                  = 0x0f;
    public static final int GLIDE                      = 0x14;
    public static final int PORTA_DOWN                 = 0x48;
    public static final int PORTA_UP                   = 0x49;

    public static final int FILTER1_TOGGLE             = 0x2c;
    public static final int FILTER1_TYPE               = 0xf3;
    public static final int FILTER1_FAT                = 0xaa;
    public static final int FILTER1_CUTOFF             = 0x1e;
    public static final int FILTER1_RESO               = 0x1d;
    public static final int FILTER1_DRIVE              = 0x4b;
    public static final int FILTER1_KEYTRACK           = 0x2e;

    public static final int FILTER2_TOGGLE             = 0x174;
    public static final int FILTER2_TYPE               = 0x186;
    public static final int FILTER2_CUTOFF             = 0x177;
    public static final int FILTER2_RESO               = 0x178;
    public static final int FILTER2_DRIVE              = 0x179;

    public static final int FILTERS_SERIAL_PARALLEL    = 0x173;
    public static final int FILTERS_BLEND              = 0x17a;

    public static final int LFO_1_FADE                 = 0x3c;
    public static final int LFO_1_RATE                 = 0x3d;
    public static final int LFO_1_WAVE_SHAPE           = 0x3e;
    public static final int LFO_1_KEY_TRIGGER          = 0x14c;
    public static final int LFO_1_MONO_POLY            = 0x14d;
    public static final int LFO_1_PHASE                = 0x14e;
    public static final int LFO_1_POSITIVE_OR_MIDPOINT = 0x14f;
    public static final int LFO_1_TEMPO_SYNC           = 0x150;
    public static final int LFO_1_FADE_IN_OR_OUT       = 0x187;

    public static final int LFO_2_RATE                 = 0x3f;
    public static final int LFO_2_WAVE_SHAPE           = 0x40;
    public static final int LFO_2_FADE                 = 0x151;
    public static final int LFO_2_KEY_TRIGGER          = 0x152;
    public static final int LFO_2_MONO_POLY            = 0x153;
    public static final int LFO_2_PHASE                = 0x154;
    public static final int LFO_2_POSITIVE_OR_MIDPOINT = 0x155;
    public static final int LFO_2_TEMPO_SYNC           = 0x156;
    public static final int LFO_2_FADE_IN_OR_OUT       = 0x188;

    public static final int LFO_3_RATE                 = 0xa7;
    public static final int LFO_3_WAVE_SHAPE           = 0x158;
    public static final int LFO_3_FADE                 = 0x157;
    public static final int LFO_3_KEY_TRIGGER          = 0x159;
    public static final int LFO_3_MONO_POLY            = 0x15a;
    public static final int LFO_3_PHASE                = 0x15b;
    public static final int LFO_3_POSITIVE_OR_MIDPOINT = 0x15c;
    public static final int LFO_3_TEMPO_SYNC           = 0x15d;
    public static final int LFO_3_FADE_IN_OR_OUT       = 0x189;

    /** 0=AD, 1=AR, 2=ADSR, 3=AHDSR, 4=DADSR, 5=DAHDSR */
    public static final int ENV2_TYPE                  = 0x16a;
    public static final int ENV2_VEL_SENS              = 0x17d;

    public static final int ENV2_DELAY_START           = 0x16c;
    public static final int ENV2_ATK_CURVE             = 0x192;
    public static final int ENV2_ATK_HI_VEL            = 0x4c;
    public static final int ENV2_ATK_LO_VEL            = 0x4d;
    public static final int ENV2_HOLD                  = 0x38;
    public static final int ENV2_DECAY                 = 0x4e;
    public static final int ENV2_SUSTAIN               = 0x4f;

    public static final int ENV2_RELEASE               = 0x50;
    public static final int ENV2_TIME_CURVE            = 0x5b;

    /** 0=AD, 1=AR, 2=ADSR, 3=AHDSR, 4=DADSR, 5=DAHDSR */
    public static final int ENV1_TYPE                  = 0x16b;
    public static final int ENV1_VEL_SENS              = 0x5a;
    public static final int ENV1_VOLUME_HIGH           = 0x59;
    public static final int ENV1_DELAY_START           = 0x16d;
    public static final int ENV1_ATK_CURVE             = 0x195;
    public static final int ENV1_ATK_HI_VEL            = 0x52;
    public static final int ENV1_ATK_LO_VEL            = 0x53;
    public static final int ENV1_HOLD                  = 0x58;
    public static final int ENV1_DECAY                 = 0x54;
    public static final int ENV1_SUSTAIN               = 0x51;
    public static final int ENV1_RELEASE               = 0x55;
    /** No ID ?! */
    public static final int ENV1_TIME_CURVE            = -1;

    public static final int AMP_VELOCITY_CURVE         = 0x183;
    public static final int VELOCITY_OFFSET            = 0x5f;
    public static final int RANDOM_VELOCITY            = 0xa4;
    public static final int RANDOM_SAMPLE_SEL          = 0xa3;
    public static final int RANDOM_PITCH               = 0x62;
    public static final int XFADE_AMOUNT               = 0x61;
    public static final int XFADE_TYPE                 = 0xa5;
    public static final int UNISON_TOGGLE              = 0xab;
    public static final int COARSE_TUNE_REMOTE         = 0xa6;
    public static final int HOLD_VIA_CONTROL           = 0xac;

    public static final int MOD1_DESTINATION           = 0xad;
    public static final int MOD1_SOURCE                = 0xae;
    public static final int MOD1_VIA                   = 0xaf;
    public static final int MOD1_AMOUNT_LOW            = 0xb0;
    public static final int MOD1_AMOUNT_HIGH           = 0xb1;
    public static final int MOD1_SRC_INVERT            = 0xe9;
    public static final int MOD1_VIA_INVERT            = 0xb2;
    public static final int MOD1_BYPASS                = 0xf4;

    public static final int MOD2_DESTINATION           = 0xb3;
    public static final int MOD2_SOURCE                = 0xb4;
    public static final int MOD2_VIA                   = 0xb5;
    public static final int MOD2_AMOUNT_LOW            = 0xb6;
    public static final int MOD2_AMOUNT_HIGH           = 0xb7;
    public static final int MOD2_SRC_INVERT            = 0xea;
    public static final int MOD2_VIA_INVERT            = 0xb8;
    public static final int MOD2_BYPASS                = 0xf5;

    public static final int MOD3_DESTINATION           = 0xb9;
    public static final int MOD3_SOURCE                = 0xba;
    public static final int MOD3_VIA                   = 0xbb;
    public static final int MOD3_AMOUNT_LOW            = 0xbc;
    public static final int MOD3_AMOUNT_HIGH           = 0xbd;
    public static final int MOD3_SRC_INVERT            = 0xeb;
    public static final int MOD3_VIA_INVERT            = 0xbe;
    public static final int MOD3_BYPASS                = 0xf6;

    public static final int MOD4_DESTINATION           = 0xbf;
    public static final int MOD4_SOURCE                = 0xc0;
    public static final int MOD4_VIA                   = 0xc1;
    public static final int MOD4_AMOUNT_LOW            = 0xc2;
    public static final int MOD4_AMOUNT_HIGH           = 0xc3;
    public static final int MOD4_SRC_INVERT            = 0xec;
    public static final int MOD4_VIA_INVERT            = 0xc4;
    public static final int MOD4_BYPASS                = 0xf7;

    public static final int MOD5_DESTINATION           = 0xc5;
    public static final int MOD5_SOURCE                = 0xc6;
    public static final int MOD5_VIA                   = 0xc7;
    public static final int MOD5_AMOUNT_LOW            = 0xc8;
    public static final int MOD5_AMOUNT_HIGH           = 0xc9;
    public static final int MOD5_SRC_INVERT            = 0xed;
    public static final int MOD5_VIA_INVERT            = 0xca;
    public static final int MOD5_BYPASS                = 0xf8;

    public static final int MOD6_DESTINATION           = 0xcb;
    public static final int MOD6_SOURCE                = 0xcc;
    public static final int MOD6_VIA                   = 0xcd;
    public static final int MOD6_AMOUNT_LOW            = 0xce;
    public static final int MOD6_AMOUNT_HIGH           = 0xcf;
    public static final int MOD6_SRC_INVERT            = 0xee;
    public static final int MOD6_VIA_INVERT            = 0xd0;
    public static final int MOD6_BYPASS                = 0xf9;

    public static final int MOD7_DESTINATION           = 0xd1;
    public static final int MOD7_SOURCE                = 0xd2;
    public static final int MOD7_VIA                   = 0xd3;
    public static final int MOD7_AMOUNT_LOW            = 0xd4;
    public static final int MOD7_AMOUNT_HIGH           = 0xd5;
    public static final int MOD7_SRC_INVERT            = 0xef;
    public static final int MOD7_VIA_INVERT            = 0xd6;
    public static final int MOD7_BYPASS                = 0xfa;

    public static final int MOD8_DESTINATION           = 0xd7;
    public static final int MOD8_SOURCE                = 0xd8;
    public static final int MOD8_VIA                   = 0xd9;
    public static final int MOD8_AMOUNT_LOW            = 0xda;
    public static final int MOD8_AMOUNT_HIGH           = 0xdb;
    public static final int MOD8_SRC_INVERT            = 0xf0;
    public static final int MOD8_VIA_INVERT            = 0xdc;
    public static final int MOD8_BYPASS                = 0xfb;

    public static final int MOD9_DESTINATION           = 0xdd;
    public static final int MOD9_SOURCE                = 0xde;
    public static final int MOD9_VIA                   = 0xdf;
    public static final int MOD9_AMOUNT_LOW            = 0xe0;
    public static final int MOD9_AMOUNT_HIGH           = 0xe1;
    public static final int MOD9_SRC_INVERT            = 0xf1;
    public static final int MOD9_VIA_INVERT            = 0xe2;
    public static final int MOD9_BYPASS                = 0xfc;

    public static final int MOD10_DESTINATION          = 0xe3;
    public static final int MOD10_SOURCE               = 0xe4;
    public static final int MOD10_VIA                  = 0xe5;
    public static final int MOD10_AMOUNT_LOW           = 0xe6;
    public static final int MOD10_AMOUNT_HIGH          = 0xe7;
    public static final int MOD10_SRC_INVERT           = 0xf2;
    public static final int MOD10_VIA_INVERT           = 0xe8;
    public static final int MOD10_BYPASS               = 0xfd;

    public static final int MOD11_DESTINATION          = 0x19b;
    public static final int MOD11_SOURCE               = 0x19c;
    public static final int MOD11_VIA                  = 0x19d;
    public static final int MOD11_AMOUNT_LOW           = 0x19e;
    public static final int MOD11_AMOUNT_HIGH          = 0x19f;
    public static final int MOD11_SRC_INVERT           = 0x1a0;
    public static final int MOD11_VIA_INVERT           = 0x1a1;
    public static final int MOD11_BYPASS               = 0x1a2;

    Map<Integer, Integer>   params                     = new TreeMap<> ();


    /**
     * Get the name of the parameter.
     *
     * @param id The parameter ID for which to get the name
     * @return The name or null if it does not exist
     */
    public static String getName (final int id)
    {
        final String name = PARAM_NAMES.get (Integer.valueOf (id));
        if (name == null)
            return String.format ("%s (%02x)", "Unknown", Integer.valueOf (id));
        return name;
    }


    /**
     * Constructor.
     */
    public EXS24Parameters ()
    {
        super (EXS24Block.TYPE_PARAMS);
    }


    /**
     * Constructor.
     *
     * @param block The block to read
     * @throws IOException Could not read the block
     */
    public EXS24Parameters (final EXS24Block block) throws IOException
    {
        this ();
        this.read (block);
    }


    /** {@inheritDoc} */
    @Override
    protected void read (final InputStream in, final boolean isBigEndian) throws IOException
    {
        int paramCount = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
        int paramBlockLength = paramCount * 3;
        byte [] parameterData = in.readNBytes (paramBlockLength);

        for (int i = 0; i < paramCount; i++)
        {
            final int paramID = parameterData[i] & 0xFF;
            if (paramID != 0)
            {
                final int valueOffset = paramCount + 2 * i;
                final int value = StreamUtils.readSigned16 (parameterData, valueOffset, isBigEndian);
                this.params.put (Integer.valueOf (paramID), Integer.valueOf (value));
            }
        }

        final int available = in.available ();
        if (available > 0)
        {
            paramCount = (int) StreamUtils.readUnsigned32 (in, isBigEndian);
            if (paramCount <= 0 || paramCount * 2 > available)
                return;

            paramBlockLength = paramCount * 2;
            if (paramBlockLength <= 0)
                return;
            parameterData = in.readNBytes (paramBlockLength);
            for (int i = 0; i < paramBlockLength; i += 2)
            {
                final int paramID = parameterData[i] & 0xFF;
                if (paramID != 0)
                    this.params.put (Integer.valueOf (paramID), Integer.valueOf (parameterData[i + 1]));
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        final int paramCount = this.params.size ();
        StreamUtils.writeUnsigned32 (out, paramCount, isBigEndian);
        for (final Map.Entry<Integer, Integer> entry: this.params.entrySet ())
            out.write (entry.getKey ().intValue ());
        for (final Map.Entry<Integer, Integer> entry: this.params.entrySet ())
            StreamUtils.writeUnsigned16 (out, entry.getValue ().intValue (), isBigEndian);
    }


    /**
     * Get a parameter value.
     * 
     * @param id The ID of the parameter
     * @return The parameter value
     */
    public Integer get (final int id)
    {
        return this.params.get (Integer.valueOf (id));
    }


    /**
     * Set a parameter value.
     * 
     * @param id The ID of the parameter
     * @param value The parameter value
     */
    public void put (final int id, final int value)
    {
        this.params.put (Integer.valueOf (id), Integer.valueOf (value));
    }
}
