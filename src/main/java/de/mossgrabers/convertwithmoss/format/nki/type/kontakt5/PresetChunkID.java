// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt5;

import java.util.HashMap;
import java.util.Map;


/**
 * All available types of preset chunks.
 *
 * @author Jürgen Moßgraber
 */
public class PresetChunkID
{
    /** Scripting. */
    public static final int                   PAR_SCRIPT         = 0x06;
    /** Send levels. */
    public static final int                   PAR_FX_SEND_LEVELS = 0x17;
    /** FX. */
    public static final int                   PAR_FX             = 0x25;
    /** A Kontakt program. */
    public static final int                   PROGRAM            = 0x28;
    /** The list with all voice groups. */
    public static final int                   VOICE_GROUPS       = 0x32;
    /** The list with all groups. */
    public static final int                   GROUP_LIST         = 0x33;
    /** The list with all zones. */
    public static final int                   ZONE_LIST          = 0x34;
    /** A parameter array with 8 entries. */
    public static final int                   PARAMETER_ARRAY_8  = 0x3A;
    /** A parameter array with 16 entries. */
    public static final int                   PARAMETER_ARRAY_16 = 0x3B;
    /** A parameter array with 32 entries. */
    public static final int                   PARAMETER_ARRAY_32 = 0x3C;
    /** The list with all file names. */
    public static final int                   FILENAME_LIST      = 0x3D;
    /** Insert bus settings */
    public static final int                   INSERT_BUS         = 0x45;
    /** Save settings. */
    public static final int                   SAVE_SETTINGS      = 0x47;
    /** The list with all file names (more recent version). */
    public static final int                   FILENAME_LIST_EX   = 0x4B;
    /** Quick browse info. */
    public static final int                   QUICK_BROWSE_DATA  = 0x4E;

    // 0x00 BParModBase
    // 0x01 BAutomationObject
    // 0x02 OutputPartition
    // 0x03 BBank
    // 0x04 BGroup
    // 0x05 BLoop
    // 0x06 BParScript
    // 0x07 BParEnv
    // 0x08 BParLFO
    // 0x09 BParArp
    // 0x0a BParEnvF
    // 0x0b BParGlide
    // 0x0c BParExternalMod
    // 0x0d BParInternalMod
    // 0x0e BParSrcMode
    // 0x0f BParStartCriteria
    // 0x10 BParFXDelay
    // 0x11 BParFXChorus
    // 0x12 BParFXFlanger
    // 0x13 BParFXGainer
    // 0x14 BParFXPhaser
    // 0x15 BParFXReverb
    // 0x16 BParFXIRC
    // 0x17 BParFXSendLevels
    // 0x18 BParFXFilter
    // 0x19 BParFXCompressor
    // 0x20 BParFXLoFi
    // 0x1a BParFXInverter
    // 0x1b BParFXDYX
    // 0x1c BParFXLimiter
    // 0x1d BParFXSurroundPanner
    // 0x1d BParFXShaper
    // 0x1e BParFXDistortion
    // 0x1f BParFXStereoSpread
    // 0x20 BParFXLofi
    // 0x21 BParFXSkreamer
    // 0x22 BParFXRotator
    // 0x23 BParFXTwang
    // 0x24 BParFXCabinet
    // 0x25 BParFX
    // 0x26 BDyxMorphGroup
    // 0x27 BDyxMorphMap
    // 0x28 BProgram
    // 0x29 BProgramContainer
    // 0x2a BSample
    // 0x2b ? VoiceGroup
    // 0x2c BZone
    // 0x2d BZoneLevelEnv
    // 0x2e BZoneArraySer
    // 0x2f BGroupCompleteSer
    // 0x30 PresetImpl
    // 0x32 ? VoiceGroups
    // 0x33 ? GroupList
    // 0x34 ? ZoneList
    // 0x35 ? PrivateRawObject
    // 0x36 ? ProgramList
    // 0x37 ? SlotList
    // 0x38 ? StarCritList
    // 0x39 ? LoopArray
    // 0x3a BParameterArraySer<BParFX,8>
    // 0x3b BParameterArraySer<BParInternalMod,16>
    // 0x3c BParameterArraySer<BParExternalMod,32>
    // 0x3d FileNameListPreK51
    // 0x3e BOutputConfiguration
    // 0x3d FileNameListPreK1 / FNTablePreK51
    // 0x3f BParEnv_AHDSR
    // 0x40 BParEnv_FM7
    // 0x41 BParEnv_DBD
    // 0x42 BParFXTape
    // 0x43 BParFXTrans
    // 0x44 BParFXSSLGEQ
    // 0x45 BInsertBus
    // 0x46 BParFXSSLGBusComp
    // 0x47 SaveSettings
    // 0x48 ? PrivateRawObject
    // 0x49 ? PrivateRawObject
    // 0x4a BParGroupDynamics
    // 0x4b FNTableImpl | FileNameList
    // 0x4c BParFXFBComp
    // 0x4d BParFXJump
    // 0x4e QuickBrowseData
    // 0x4f BSnapshot
    // 0x50 BGroupSnapshot
    // 0x51 BSnapshotMetaData
    // 0x52 BParFXVan51
    // 0x53 BParFXACBox
    // 0x54 BParFXHotSolo
    // 0x54 BParFXBassInvader
    // 0x55 BParFXCat
    // 0x56 BParFXDStortion
    // 0x57 BParFXPlateReverb
    // 0x58 BParFXCryWah
    // 0x5a BParFXReplikaDelay
    // 0x5b BParFXPhasis
    // 0x5c BParFXFlair
    // 0x5d BParFXChoral
    // 0x5e BParFXCoreCell
    // 0x5f BParFXHilbertLimiter
    // 0x59 BParFXGaloisReverb
    // 0x60 BParFXSupercharger
    // 0x61 BParFXBassPro
    // 0x63 BParFXPsycheDelay
    // 0x64 BParFXRingModulator

    private static final Map<Integer, String> CHUNK_NAMES        = new HashMap<> ();
    static
    {
        CHUNK_NAMES.put (Integer.valueOf (PAR_SCRIPT), "PAR_SCRIPT");
        CHUNK_NAMES.put (Integer.valueOf (PAR_FX_SEND_LEVELS), "PAR_FX_SEND_LEVELS");
        CHUNK_NAMES.put (Integer.valueOf (PAR_FX), "PAR_FX");
        CHUNK_NAMES.put (Integer.valueOf (PROGRAM), "PROGRAM");
        CHUNK_NAMES.put (Integer.valueOf (VOICE_GROUPS), "VOICE_GROUPS");
        CHUNK_NAMES.put (Integer.valueOf (GROUP_LIST), "GROUP_LIST");
        CHUNK_NAMES.put (Integer.valueOf (ZONE_LIST), "ZONE_LIST");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_8), "PARAMETER_ARRAY_8");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_16), "PARAMETER_ARRAY_16");
        CHUNK_NAMES.put (Integer.valueOf (PARAMETER_ARRAY_32), "PARAMETER_ARRAY_32");
        CHUNK_NAMES.put (Integer.valueOf (FILENAME_LIST), "FILENAME_LIST");
        CHUNK_NAMES.put (Integer.valueOf (INSERT_BUS), "INSERT_BUS");
        CHUNK_NAMES.put (Integer.valueOf (SAVE_SETTINGS), "SAVE_SETTINGS");
        CHUNK_NAMES.put (Integer.valueOf (FILENAME_LIST_EX), "FILENAME_LIST_EX");
        CHUNK_NAMES.put (Integer.valueOf (QUICK_BROWSE_DATA), "QUICK_BROWSE_DATA");
    }


    /**
     * Get the name of an ID.
     *
     * @param id The ID for which to get the name
     * @return The name
     */
    public static String getName (final int id)
    {
        return CHUNK_NAMES.getOrDefault (Integer.valueOf (id), "Unknown");
    }


    /**
     * Hide constructor in helper class.
     */
    private PresetChunkID ()
    {
        // Intentionally empty
    }
}
