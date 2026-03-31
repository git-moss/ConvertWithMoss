// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.IOException;
import java.util.List;


/**
 * An Akai program.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiProgram extends AkaiDiskElement
{
    // AKAI character set
    private String          name;
    // 0..127
    @SuppressWarnings("unused")
    private byte            midiProgramNumber;
    // 0..15, 255=OMNI
    private byte            midiChannel;
    // 1..16
    @SuppressWarnings("unused")
    private byte            polyphony;
    // 0=LOW 1=NORM 2=HIGH 3=HOLD
    @SuppressWarnings("unused")
    private byte            priority;
    // 24..127
    private byte            lowKey;
    // 24..127
    private byte            highKey;
    // -2..2
    private byte            octaveShift;
    // 0..7, 255=OFF
    @SuppressWarnings("unused")
    private byte            auxOutputSelect;
    // 0..99
    @SuppressWarnings("unused")
    private byte            mixOutputSelect;
    // -50..50
    private byte            mixPan;
    // 0..99
    private byte            volume;
    // -50..50
    private byte            velocityToVolume;
    // -50..50
    @SuppressWarnings("unused")
    private byte            keyToVolume;
    // -50..50
    @SuppressWarnings("unused")
    private byte            pressureToVolume;
    // 0..99
    @SuppressWarnings("unused")
    private byte            panLFORate;
    // 0..99
    @SuppressWarnings("unused")
    private byte            panLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte            panLFODelay;
    // -50..50
    @SuppressWarnings("unused")
    private byte            keyToPan;
    // 0..99
    @SuppressWarnings("unused")
    private byte            lfoRate;
    // 0..99
    @SuppressWarnings("unused")
    private byte            lfoDepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte            lfoDelay;
    // 0..99
    @SuppressWarnings("unused")
    private byte            modulationToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte            pressureToLFODepth;
    // 0..99
    @SuppressWarnings("unused")
    private byte            velocityToLFODepth;
    // 0..12 semi-tones
    private byte            bendToPitch;
    // -12..12 semi-tones
    @SuppressWarnings("unused")
    private byte            pressureToPitch;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean         keygroupCrossfade;
    // 1..99
    private byte            numberOfKeygroups;
    // -25..25 cents
    private final byte []   keyTemperament = new byte [11];
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean         fxOutput;
    // -50..50
    @SuppressWarnings("unused")
    private byte            modulationToPan;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean         stereoCoherence;
    // 0=OFF 1=ON
    @SuppressWarnings("unused")
    private boolean         lfoDesync;
    // 0=LINEAR
    @SuppressWarnings("unused")
    private byte            pitchLaw;
    // 0=OLDEST 1=QUIETEST
    @SuppressWarnings("unused")
    private byte            voiceReassign;
    // 0..99
    @SuppressWarnings("unused")
    private byte            softpedToVolume;
    // 0..99
    @SuppressWarnings("unused")
    private byte            softpedToAttack;
    // 0..99
    @SuppressWarnings("unused")
    private byte            softpedToFilter;
    // -128..127 (-50..50 cents)
    @SuppressWarnings("unused")
    private byte            softpedToTuneCents;
    // -50..50
    @SuppressWarnings("unused")
    private byte            softpedToTuneSemitones;
    // -50..50
    @SuppressWarnings("unused")
    private byte            keyToLFORate;
    // -50..50
    @SuppressWarnings("unused")
    private byte            keyToLFODepth;
    // -50..50
    @SuppressWarnings("unused")
    private byte            keyToLFODelay;
    // 0=-6dB 1=0dB 2=+12dB
    @SuppressWarnings("unused")
    private byte            voiceOutputScale;
    // 0=0dB 1=+6dB
    @SuppressWarnings("unused")
    private byte            stereoOutputScale;

    private AkaiKeygroup [] keygroups;


    /**
     * Default constructor.
     * 
     * @param image The image to read from
     * @throws IOException Could not read the program
     */
    public AkaiProgram (final IAkaiImage image) throws IOException
    {
        super (0);

        this.readProgram (image);
    }


    /**
     * Constructor.
     *
     * @param disk The disk to read from
     * @param parent The parent volume
     * @param dirEntry The directory entry of the program
     * @throws IOException Could not read the program
     */
    public AkaiProgram (final AkaiS1000DiskImage disk, final AkaiVolume parent, final AkaiDirEntry dirEntry) throws IOException
    {
        super (disk.getPos ());

        final int tempPos = disk.getPos ();
        disk.setPos (parent.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE, AkaiStreamWhence.START);

        this.readProgram (disk);

        // Read key-groups
        int headerSize = disk.isS3000 () ? 192 : 150;
        final int numKeygroups = this.numberOfKeygroups & 0xFF;
        this.keygroups = new AkaiKeygroup [numKeygroups];
        for (int i = 0; i < numKeygroups; i++)
        {
            disk.setPos (parent.getPartition ().getOffset () + dirEntry.getStart () * AKAI_BLOCK_SIZE + headerSize * (i + 1), AkaiStreamWhence.START);
            this.keygroups[i] = new AkaiKeygroup (disk);
        }

        disk.setPos (tempPos, AkaiStreamWhence.START);
    }


    /**
     * Set all key-groups.
     * 
     * @param keygroups The key-groups to set
     */
    public void setKeygroups (final List<AkaiKeygroup> keygroups)
    {
        this.keygroups = keygroups.toArray (new AkaiKeygroup [keygroups.size ()]);
    }


    private void readProgram (final IAkaiImage image) throws IOException
    {
        final byte progID = image.readInt8 ();
        if (progID != AkaiDiskElement.AKAI_PROGRAM_ID)
            throw new IOException ("Not a Akai Program.");

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
        this.mixOutputSelect = image.readInt8 ();
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

        for (int i = 0; i < 11; i++)
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

        // Bytes 73-150/192 are not used, key-groups start at 150/192
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