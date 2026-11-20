// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiDiskImage;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.AkaiStreamWhence;
import de.mossgrabers.convertwithmoss.format.akai.diskformat.IAkaiImage;


/**
 * An Akai program.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS1000Program
{
    /** Modulation source: modulation wheel. */
    public static final int      MOD_SOURCE_MODWHEEL = 1;
    /** Modulation source: LFO 1. */
    public static final int      MOD_SOURCE_LFO1     = 7;
    /** Modulation source: LFO 2. */
    public static final int      MOD_SOURCE_LFO2     = 8;

    /** ID for a program structure. */
    private static final int     AKAI_PROGRAM_ID     = 1;
    /** The number of bytes of the S3000 program header which are read. */
    private static final int     S3000_LENGTH        = 103;

    // AKAI character set
    private String               name;
    // 0..127
    private byte                 midiProgramNumber;
    // 0..15, 255=OMNI
    private byte                 midiChannel;
    // 1..16
    private byte                 polyphony;
    // 0=LOW 1=NORM 2=HIGH 3=HOLD
    @SuppressWarnings("unused")
    private byte                 priority;
    // 24..127
    private byte                 lowKey;
    // 24..127
    private byte                 highKey;
    // -2..2
    private byte                 octaveShift;
    // 0..7, 255=OFF
    @SuppressWarnings("unused")
    private byte                 auxOutputSelect;
    // 0..99, the level of the program in the stereo mix (STEREO in the Akai documentation)
    private byte                 stereoLevel;
    // -50..50
    private byte                 mixPan;
    // 0..99
    private byte                 volume;
    // -50..50
    private byte                 velocityToVolume;
    // -50..50
    private byte                 keyToVolume;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 pressureToVolume;
    // 0..99
    private byte                 panLFORate;
    // 0..99
    private byte                 panLFODepth;
    // 0..99
    private byte                 panLFODelay;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 keyToPan;
    // 0..99
    private byte                 lfoRate;
    // 0..99
    private byte                 lfoDepth;
    // 0..99
    private byte                 lfoDelay;
    // 0..99
    private byte                 modulationToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte                 pressureToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte                 velocityToLFODepth;
    // 0..12 semi-tones
    private byte                 bendToPitch;
    // -12..12 semi-tones
    @SuppressWarnings("unused")
    private byte                 pressureToPitch;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean              keygroupCrossfade;
    // 1..99
    private byte                 numberOfKeygroups;
    // -25..25 cents
    private final byte []        keyTemperament      = new byte [12];
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean              fxOutput;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 modulationToPan;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean              stereoCoherence;
    // 0=OFF 1=ON
    private boolean              lfoDesync;
    // 0=LINEAR
    @SuppressWarnings("unused")
    private byte                 pitchLaw;
    // 0=OLDEST 1=QUIETEST
    @SuppressWarnings("unused")
    private byte                 voiceReassign;
    // 0..99
    @SuppressWarnings("unused")
    private byte                 softpedToVolume;
    // 0..99
    @SuppressWarnings("unused")
    private byte                 softpedToAttack;
    // 0..99
    @SuppressWarnings("unused")
    private byte                 softpedToFilter;
    // -128..127 (-50..50 cents)
    @SuppressWarnings("unused")
    private byte                 softpedToTuneCents;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 softpedToTuneSemitones;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 keyToLFORate;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 keyToLFODepth;
    // -50..50
    @SuppressWarnings("unused")
    private byte                 keyToLFODelay;
    // 0=-6dB 1=0dB 2=+12dB
    @SuppressWarnings("unused")
    private byte                 voiceOutputScale;
    // 0=0dB 1=+6dB
    @SuppressWarnings("unused")
    private byte                 stereoOutputScale;

    // S3000 only: the sources of the assignable modulations (see the MOD_SOURCE constants) of
    // the loudness 1-3, the LFO 1 depth, the filter frequency 1-3 and the pitch
    private final byte []        loudnessModSources  = new byte [3];
    private byte                 lfo1DepthModSource;
    private final byte []        filterModSources    = new byte [3];
    private byte                 pitchModSource;
    // S3000 only: -50..50, the amounts of the loudness modulation 1 and 2 (the one of 3 is part of
    // the key-group) and of the LFO 1 depth modulation
    private final byte []        loudnessModAmounts  = new byte [2];
    private byte                 lfo1DepthModAmount;
    // S3000 only: 0=TRIANGLE 1=SAWTOOTH 2=SQUARE 3=RANDOM
    private byte                 lfo1Waveform;
    private byte                 lfo2Waveform;
    // S3000 only: LFO 2 re-trigger, 0=OFF 1=ON
    private boolean              lfo2Retrigger;

    private AkaiS1000Keygroup [] keygroups;


    /**
     * Default constructor.
     *
     * @param image The image to read from
     * @throws IOException Could not read the program
     */
    public AkaiS1000Program (final IAkaiImage image) throws IOException
    {
        // The S3000 header is longer than the one of the S1000
        final boolean isS3000 = image.available () >= S3000_LENGTH;
        this.readProgram (image);
        if (isS3000)
            this.readS3000Parameters (image);
    }


    /**
     * Constructor.
     *
     * @param disk The disk to read from
     * @param dataPosition The position where the data starts
     * @param isS3000 If it is an extended S3000 program (otherwise shorter S1000)
     * @throws IOException Could not read the program
     */
    public AkaiS1000Program (final AkaiDiskImage disk, final int dataPosition, final boolean isS3000) throws IOException
    {
        disk.setPosition (dataPosition, AkaiStreamWhence.START);

        this.readProgram (disk);
        if (isS3000)
            this.readS3000Parameters (disk);

        // Read key-groups
        final int headerSize = isS3000 ? 192 : 150;
        final int numKeygroups = this.numberOfKeygroups & 0xFF;
        this.keygroups = new AkaiS1000Keygroup [numKeygroups];
        for (int i = 0; i < numKeygroups; i++)
        {
            disk.setPosition (dataPosition + headerSize * (i + 1), AkaiStreamWhence.START);
            this.keygroups[i] = new AkaiS1000Keygroup (disk, isS3000);
        }
    }


    /**
     * Set all key-groups.
     *
     * @param keygroups The key-groups to set
     */
    public void setKeygroups (final List<AkaiS1000Keygroup> keygroups)
    {
        this.keygroups = keygroups.toArray (new AkaiS1000Keygroup [keygroups.size ()]);
    }


    private void readProgram (final IAkaiImage image) throws IOException
    {
        final byte progID = image.readInt8 ();
        if (progID != AKAI_PROGRAM_ID)
            throw new IOException ("Not an Akai Program.");

        // key-group address
        image.readInt16 ();

        this.name = image.readText ();

        this.midiProgramNumber = image.readInt8 ();
        this.midiChannel = image.readInt8 ();
        this.polyphony = image.readInt8 ();
        this.priority = image.readInt8 ();
        this.lowKey = image.readInt8 ();
        this.highKey = image.readInt8 ();
        this.octaveShift = image.readInt8 ();
        this.auxOutputSelect = image.readInt8 ();
        this.stereoLevel = image.readInt8 ();
        this.mixPan = image.readInt8 ();
        this.volume = image.readInt8 ();
        this.velocityToVolume = image.readInt8 ();
        this.keyToVolume = image.readInt8 ();
        this.pressureToVolume = image.readInt8 ();
        this.panLFORate = image.readInt8 ();
        this.panLFODepth = image.readInt8 ();
        this.panLFODelay = image.readInt8 ();
        this.keyToPan = image.readInt8 ();
        this.lfoRate = image.readInt8 ();
        this.lfoDepth = image.readInt8 ();
        this.lfoDelay = image.readInt8 ();
        this.modulationToLFODepth = image.readInt8 ();
        this.pressureToLFODepth = image.readInt8 ();
        this.velocityToLFODepth = image.readInt8 ();
        this.bendToPitch = image.readInt8 ();
        this.pressureToPitch = image.readInt8 ();
        this.keygroupCrossfade = image.readInt8 () != 0;
        this.numberOfKeygroups = image.readInt8 ();
        // program number
        image.readInt8 ();

        for (int i = 0; i < 12; i++)
            this.keyTemperament[i] = image.readInt8 ();

        this.fxOutput = image.readInt8 () != 0;
        this.modulationToPan = image.readInt8 ();
        this.stereoCoherence = image.readInt8 () != 0;
        this.lfoDesync = image.readInt8 () != 0;
        this.pitchLaw = image.readInt8 ();
        this.voiceReassign = image.readInt8 ();
        this.softpedToVolume = image.readInt8 ();
        this.softpedToAttack = image.readInt8 ();
        this.softpedToFilter = image.readInt8 ();
        this.softpedToTuneCents = image.readInt8 ();
        this.softpedToTuneSemitones = image.readInt8 ();
        this.keyToLFORate = image.readInt8 ();
        this.keyToLFODepth = image.readInt8 ();
        this.keyToLFODelay = image.readInt8 ();
        this.voiceOutputScale = image.readInt8 ();
        this.stereoOutputScale = image.readInt8 ();

        // The S1000 does not use the remaining bytes, key-groups start at 150/192
    }


    /**
     * Read the parameters which the S3000 added to the program header (offset 72 to 102).
     *
     * @param image The image to read from, positioned at offset 72
     * @throws IOException Could not read the parameters
     */
    private void readS3000Parameters (final IAkaiImage image) throws IOException
    {
        // Legato, pitch bend down, bend mode and transpose
        for (int i = 0; i < 4; i++)
            image.readInt8 ();

        // Pan modulation sources 1-3
        for (int i = 0; i < 3; i++)
            image.readInt8 ();
        this.loudnessModSources[0] = image.readInt8 ();
        this.loudnessModSources[1] = image.readInt8 ();
        // LFO 1 speed modulation source
        image.readInt8 ();
        this.lfo1DepthModSource = image.readInt8 ();
        // LFO 1 delay modulation source
        image.readInt8 ();
        for (int i = 0; i < 3; i++)
            this.filterModSources[i] = image.readInt8 ();
        this.pitchModSource = image.readInt8 ();
        this.loudnessModSources[2] = image.readInt8 ();

        // Pan modulation amounts 1-3
        for (int i = 0; i < 3; i++)
            image.readInt8 ();
        this.loudnessModAmounts[0] = image.readInt8 ();
        this.loudnessModAmounts[1] = image.readInt8 ();
        // LFO 1 speed modulation amount
        image.readInt8 ();
        this.lfo1DepthModAmount = image.readInt8 ();
        // LFO 1 delay modulation amount
        image.readInt8 ();

        this.lfo1Waveform = image.readInt8 ();
        this.lfo2Waveform = image.readInt8 ();
        // Filter 2 modulation sources (S3200 only)
        for (int i = 0; i < 3; i++)
            image.readInt8 ();
        this.lfo2Retrigger = image.readInt8 () != 0;
    }


    /**
     * Get the name of the program.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the MIDI program number of the program. Programs of a volume which share the same program
     * number are all selected together by that number and therefore always play layered.
     *
     * @return The MIDI program number, 0..127
     */
    public int getMidiProgramNumber ()
    {
        return this.midiProgramNumber & 0xFF;
    }


    /**
     * Get the MIDI channel of the program.
     *
     * @return The MIDI channel, 0..15, 255=OMNI
     */
    public byte getMidiChannel ()
    {
        return this.midiChannel;
    }


    /**
     * Get the polyphony of the program, which is the maximum number of voices the program may
     * allocate.
     *
     * @return The polyphony in the range of [1..16]
     */
    public int getPolyphony ()
    {
        return this.polyphony & 0xFF;
    }


    /**
     * Get the low key range for the whole program.
     *
     * @return The low key range
     */
    public byte getLowKey ()
    {
        return this.lowKey;
    }


    /**
     * Get the high key range for the whole program.
     *
     * @return The high key range
     */
    public byte getHighKey ()
    {
        return this.highKey;
    }


    /**
     * Get the octave shift for the whole program.
     *
     * @return The octave shift
     */
    public byte getOctaveShift ()
    {
        return this.octaveShift;
    }


    /**
     * Get the mix panning for the whole program.
     *
     * @return The mix panning
     */
    public byte getMixPan ()
    {
        return this.mixPan;
    }


    /**
     * Get the level of the program in the stereo mix output, 0-99 with 99 being the full level.
     *
     * @return The stereo level
     */
    public byte getStereoLevel ()
    {
        return this.stereoLevel;
    }


    /**
     * Get the basic volume for the whole program.
     *
     * @return The volume in the range of [0..99]
     */
    public byte getVolume ()
    {
        return this.volume;
    }


    /**
     * Get the keyboard velocity intensity to modulate the volume.
     *
     * @return The intensity in the range of [-50..50]
     */
    public byte getVelocityToVolume ()
    {
        return this.velocityToVolume;
    }


    /**
     * Get the intensity of the played key to modulate the volume.
     *
     * @return The intensity in the range of [-50..50]
     */
    public byte getKeyToVolume ()
    {
        return this.keyToVolume;
    }


    /**
     * Get the pitch-bend intensity.
     *
     * @return 0..12 semi-tones
     */
    public byte getBendToPitch ()
    {
        return this.bendToPitch;
    }


    /**
     * Get the rate of LFO 1.
     *
     * @return The rate in the range of [0..99]
     */
    public int getLfo1Rate ()
    {
        return this.lfoRate;
    }


    /**
     * Get the depth of LFO 1.
     *
     * @return The depth in the range of [0..99]
     */
    public int getLfo1Depth ()
    {
        return this.lfoDepth;
    }


    /**
     * Get the delay of LFO 1.
     *
     * @return The delay in the range of [0..99]
     */
    public int getLfo1Delay ()
    {
        return this.lfoDelay;
    }


    /**
     * Get the depth which the modulation wheel adds to LFO 1.
     *
     * @return The depth in the range of [0..99]
     */
    public int getModulationToLfo1Depth ()
    {
        return this.modulationToLFODepth;
    }


    /**
     * Get the waveform of LFO 1 (S3000 only, the S1000 has only a triangle).
     *
     * @return 0=TRIANGLE 1=SAWTOOTH 2=SQUARE 3=RANDOM
     */
    public int getLfo1Waveform ()
    {
        return this.lfo1Waveform;
    }


    /**
     * Are the LFOs of the notes de-synchronized?
     *
     * @return True if each note has its own LFO phase
     */
    public boolean isLfoDesync ()
    {
        return this.lfoDesync;
    }


    /**
     * Get the rate of LFO 2 (the pan LFO of the S1000).
     *
     * @return The rate in the range of [0..99]
     */
    public int getLfo2Rate ()
    {
        return this.panLFORate;
    }


    /**
     * Get the depth of LFO 2 (the pan LFO of the S1000).
     *
     * @return The depth in the range of [0..99]
     */
    public int getLfo2Depth ()
    {
        return this.panLFODepth;
    }


    /**
     * Get the delay of LFO 2 (the pan LFO of the S1000).
     *
     * @return The delay in the range of [0..99]
     */
    public int getLfo2Delay ()
    {
        return this.panLFODelay;
    }


    /**
     * Get the waveform of LFO 2 (S3000 only).
     *
     * @return 0=TRIANGLE 1=SAWTOOTH 2=SQUARE 3=RANDOM
     */
    public int getLfo2Waveform ()
    {
        return this.lfo2Waveform;
    }


    /**
     * Is LFO 2 re-triggered by each note (S3000 only)?
     *
     * @return True if re-triggered
     */
    public boolean isLfo2Retrigger ()
    {
        return this.lfo2Retrigger;
    }


    /**
     * Get the source of an assignable loudness modulation (S3000 only).
     *
     * @param index The index of the modulation in the range of [0..2]
     * @return The source, 0 if not used
     */
    public int getLoudnessModSource (final int index)
    {
        return this.loudnessModSources[index];
    }


    /**
     * Get the amount of the assignable loudness modulation 1 or 2 (S3000 only). The amount of the
     * 3rd one is part of the key-group.
     *
     * @param index The index of the modulation in the range of [0..1]
     * @return The amount in the range of [-50..50]
     */
    public int getLoudnessModAmount (final int index)
    {
        return this.loudnessModAmounts[index];
    }


    /**
     * Get the source of the assignable LFO 1 depth modulation (S3000 only).
     *
     * @return The source, 0 if not used
     */
    public int getLfo1DepthModSource ()
    {
        return this.lfo1DepthModSource;
    }


    /**
     * Get the amount of the assignable LFO 1 depth modulation (S3000 only).
     *
     * @return The amount in the range of [-50..50]
     */
    public int getLfo1DepthModAmount ()
    {
        return this.lfo1DepthModAmount;
    }


    /**
     * Get the source of an assignable filter frequency modulation (S3000 only). The amounts are
     * part of the key-group.
     *
     * @param index The index of the modulation in the range of [0..2]
     * @return The source, 0 if not used
     */
    public int getFilterModSource (final int index)
    {
        return this.filterModSources[index];
    }


    /**
     * Get the source of the assignable pitch modulation (S3000 only). The amount is part of the
     * key-group.
     *
     * @return The source, 0 if not used
     */
    public int getPitchModSource ()
    {
        return this.pitchModSource;
    }


    /**
     * Get all key-groups of the program.
     *
     * @return The key-groups
     */
    public AkaiS1000Keygroup [] getKeygroups ()
    {
        return this.keygroups;
    }
}