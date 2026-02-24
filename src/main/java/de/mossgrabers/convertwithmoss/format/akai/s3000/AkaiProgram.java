// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s3000;

import java.io.IOException;


/**
 * An Akai program.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiProgram extends AkaiDiskElement
{
    // AKAI character set
    private final String          name;
    // 0..127
    @SuppressWarnings("unused")
    private final byte            midiProgramNumber;
    // 0..15, 255=OMNI
    private final byte            midiChannel;
    // 1..16
    @SuppressWarnings("unused")
    private final byte            polyphony;
    // 0=LOW 1=NORM 2=HIGH 3=HOLD
    @SuppressWarnings("unused")
    private final byte            priority;
    // 24..127
    private final byte            lowKey;
    // 24..127
    private final byte            highKey;
    // -2..2
    private final byte            octaveShift;
    // 0..7, 255=OFF
    @SuppressWarnings("unused")
    private final byte            auxOutputSelect;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            mixOutputSelect;
    // -50..50
    private final byte            mixPan;
    // 0..99
    private final byte            volume;
    // -50..50
    private final byte            velocityToVolume;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            keyToVolume;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            pressureToVolume;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            panLFORate;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            panLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            panLFODelay;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            keyToPan;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            lfoRate;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            lfoDepth;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            lfoDelay;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            modulationToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            pressureToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            velocityToLFODepth;
    // 0..12 semi-tones
    private final byte            bendToPitch;
    // -12..12 semi-tones
    @SuppressWarnings("unused")
    private final byte            pressureToPitch;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private final boolean         keygroupCrossfade;
    // 1..99
    private final byte            numberOfKeygroups;
    // -25..25 cents
    private final byte []         keyTemperament = new byte [11];
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private final boolean         fxOutput;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            modulationToPan;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private final boolean         stereoCoherence;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private final boolean         lfoDesync;
    // 0=LINEAR
    @SuppressWarnings("unused")
    private final byte            pitchLaw;
    // 0=OLDEST 1=QUIETEST
    @SuppressWarnings("unused")
    private final byte            voiceReassign;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            softpedToVolume;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            softpedToAttack;
    // 0..99
    @SuppressWarnings("unused")
    private final byte            softpedToFilter;
    // -128..127 (-50..50 cents)
    @SuppressWarnings("unused")
    private final byte            softpedToTuneCents;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            softpedToTuneSemitones;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            keyToLFORate;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            keyToLFODepth;
    // -50..50
    @SuppressWarnings("unused")
    private final byte            keyToLFODelay;
    // 0=-6dB 1=0dB 2=+12dB
    @SuppressWarnings("unused")
    private final byte            voiceOutputScale;
    // 0=0dB 1=+6dB
    @SuppressWarnings("unused")
    private final byte            stereoOutputScale;

    private final AkaiKeygroup [] keygroups;


    /**
     * Constructor.
     *
     * @param disk The disk to read from
     * @param parent The parent volume
     * @param dirEntry The directory entry of the program
     * @throws IOException Could not read the program
     */
    public AkaiProgram (final AkaiDiskImage disk, final AkaiVolume parent, final AkaiDirEntry dirEntry) throws IOException
    {
        super (disk.getPos ());

        final int tempPos = disk.getPos ();
        disk.setPos (parent.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE, AkaiStreamWhence.START);

        final byte progID = disk.readInt8 ();
        if (progID != AkaiDiskElement.AKAI_PROGRAM_ID)
            throw new IOException ("Not a Akai Program.");

        // key-group address
        disk.readInt16 ();

        this.name = disk.readText ();

        this.midiProgramNumber = disk.readInt8 ();
        this.midiChannel = disk.readInt8 ();
        this.polyphony = disk.readInt8 ();
        this.priority = disk.readInt8 ();
        this.lowKey = disk.readInt8 ();
        this.highKey = disk.readInt8 ();
        this.octaveShift = disk.readInt8 ();
        this.auxOutputSelect = disk.readInt8 ();
        this.mixOutputSelect = disk.readInt8 ();
        this.mixPan = disk.readInt8 ();
        this.volume = disk.readInt8 ();
        this.velocityToVolume = disk.readInt8 ();
        this.keyToVolume = disk.readInt8 ();
        this.pressureToVolume = disk.readInt8 ();
        this.panLFORate = disk.readInt8 ();
        this.panLFODepth = disk.readInt8 ();
        this.panLFODelay = disk.readInt8 ();
        this.keyToPan = disk.readInt8 ();
        this.lfoRate = disk.readInt8 ();
        this.lfoDepth = disk.readInt8 ();
        this.lfoDelay = disk.readInt8 ();
        this.modulationToLFODepth = disk.readInt8 ();
        this.pressureToLFODepth = disk.readInt8 ();
        this.velocityToLFODepth = disk.readInt8 ();
        this.bendToPitch = disk.readInt8 ();
        this.pressureToPitch = disk.readInt8 ();
        this.keygroupCrossfade = disk.readInt8 () != 0;
        this.numberOfKeygroups = disk.readInt8 ();
        // program number
        disk.readInt8 ();

        for (int i = 0; i < 11; i++)
            this.keyTemperament[i] = disk.readInt8 ();

        this.fxOutput = disk.readInt8 () != 0;
        this.modulationToPan = disk.readInt8 ();
        this.stereoCoherence = disk.readInt8 () != 0;
        this.lfoDesync = disk.readInt8 () != 0;
        this.pitchLaw = disk.readInt8 ();
        this.voiceReassign = disk.readInt8 ();
        this.softpedToVolume = disk.readInt8 ();
        this.softpedToAttack = disk.readInt8 ();
        this.softpedToFilter = disk.readInt8 ();
        this.softpedToTuneCents = disk.readInt8 ();
        this.softpedToTuneSemitones = disk.readInt8 ();
        this.keyToLFORate = disk.readInt8 ();
        this.keyToLFODepth = disk.readInt8 ();
        this.keyToLFODelay = disk.readInt8 ();
        this.voiceOutputScale = disk.readInt8 ();
        this.stereoOutputScale = disk.readInt8 ();

        // Read key-groups
        final int numKeygroups = this.numberOfKeygroups & 0xFF;
        this.keygroups = new AkaiKeygroup [numKeygroups];
        for (int i = 0; i < numKeygroups; i++)
        {
            disk.setPos (parent.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE + 150 * (i + 1), AkaiStreamWhence.START);
            this.keygroups[i] = new AkaiKeygroup (disk);
        }

        disk.setPos (tempPos, AkaiStreamWhence.START);
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
     * Get the MIDI channel of the program.
     *
     * @return The MIDI channel, 0..15, 255=OMNI
     */
    public byte getMidiChannel ()
    {
        return this.midiChannel;
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
     * Get the pitch-bend intensity.
     * 
     * @return 0..12 semi-tones
     */
    public byte getBendToPitch ()
    {
        return this.bendToPitch;
    }


    /**
     * Get all key-groups of the program.
     *
     * @return The key-groups
     */
    public AkaiKeygroup [] getKeygroups ()
    {
        return this.keygroups;
    }
}