// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s900;

import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.file.StreamUtils;


/**
 * A S900/S950 program key-group. A key-group has 2 samples which are split by a velocity value and
 * can be cross-faded.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS900Keygroup
{
    private final int              keyHigh;
    private final int              keyLow;
    private final int              velocitySwitchValue;
    private final int              flags;
    private final int              release;
    private final int              sustain;
    private final int              decay;
    private final int              attack;
    private final int              outputChannel;
    private final int              midiChannelOffset;
    private final KeygroupLayer [] layers = new KeygroupLayer [2];


    /**
     * Constructor.
     *
     * @param input The input stream to read from
     * @throws IOException Could not read
     */
    public AkaiS900Keygroup (final InputStream input) throws IOException
    {
        this.keyHigh = input.read ();
        this.keyLow = input.read ();
        this.velocitySwitchValue = input.read ();
        this.flags = input.read ();

        this.release = input.read ();
        this.sustain = input.read ();
        this.decay = input.read ();
        this.attack = input.read ();

        // Not used
        input.skip (11);

        this.outputChannel = input.read ();
        this.midiChannelOffset = input.read ();

        // Not used
        input.skip (3);

        this.layers[0] = new KeygroupLayer (input);
        this.layers[1] = new KeygroupLayer (input);

        // Address of next key-group (updated by sampler)
        input.skip (2);
    }


    /**
     * Get the 2 velocity layers. Index 0 contains the soft velocity layer and index 1 the loud one.
     *
     * @return The 2 velocity layers
     */
    public KeygroupLayer [] getVelocityLayers ()
    {
        return this.layers;
    }


    /**
     * Get the upper key-range note.
     *
     * @return The note, 0-127
     */
    public int getKeyHigh ()
    {
        return this.keyHigh;
    }


    /**
     * Get the lower key-range note.
     *
     * @return The note, 0-127
     */
    public int getKeyLow ()
    {
        return this.keyLow;
    }


    /**
     * Get the value which switches between the soft and the loud sample.
     *
     * @return The velocity switch value [0..128], 0 = Only the loud sample will play, 128 = Only
     *         the soft sample will play
     */
    public int getVelocitySwitchValue ()
    {
        return this.velocitySwitchValue;
    }


    /**
     * Get the flags.
     *
     * @return 0x01 constant pitch enable, 0x02 velocity cross-fade enable, 0x08 one-shot trigger
     *         mode enable
     */
    public int getFlags ()
    {
        return this.flags;
    }


    /**
     * Get the release value.
     * 
     * @return Release in the range of [0..99]
     */
    public int getRelease ()
    {
        return this.release;
    }


    /**
     * Get the sustain value.
     * 
     * @return Sustain in the range of [0..99]
     */
    public int getSustain ()
    {
        return this.sustain;
    }


    /**
     * Get the decay value.
     * 
     * @return Decay in the range of [0..99]
     */
    public int getDecay ()
    {
        return this.decay;
    }


    /**
     * Get the attack value.
     * 
     * @return Attack in the range of [0..99]
     */
    public int getAttack ()
    {
        return this.attack;
    }


    /**
     * Get the audio output channel.
     *
     * @return channel number-1 or code, 0x08 = LEFT, 0x09 = RIGHT, 0xFF = ANY
     */
    public int getOutputChannel ()
    {
        return this.outputChannel;
    }


    /**
     * Get the MIDI channel offset.
     *
     * @return The MIDI channel offset
     */
    public int getMidiChannelOffset ()
    {
        return this.midiChannelOffset;
    }


    /**
     * Helper class for the 2 velocity layers.
     */
    public class KeygroupLayer
    {
        private final String sample;
        private final int    sampleHeaderAddress;
        private final int    tuning;
        private final int    filter;
        private final int    loud;


        /**
         * Constructor.
         *
         * @param input The input stream to read from
         * @throws IOException Could not read
         */
        public KeygroupLayer (final InputStream input) throws IOException
        {
            this.sample = StreamUtils.readASCII (input, 10).trim ();
            // Padding
            input.skip (6);
            this.sampleHeaderAddress = StreamUtils.readUnsigned16 (input, false);
            this.tuning = StreamUtils.readUnsigned16 (input, false);
            this.filter = input.read ();
            this.loud = input.read ();
        }


        /**
         * Get the sample name.
         *
         * @return The sample name
         */
        public String getSample ()
        {
            return this.sample;
        }


        /**
         * Address of sample header for the sample (updated by sampler). Note: the difference to the
         * next value is 0x46 which is the size of a key-group and therefore this points more likely
         * to the key-group itself or the sample-name in the key-group
         *
         * @return The address
         */
        public int getSampleHeaderAddress ()
        {
            return this.sampleHeaderAddress;
        }


        /**
         * Get the tuning offset (transpose) for the sample.
         *
         * @return The tuning in 1/16 semi-tones (signed) in the range of [-50..50]
         */
        public int getTuning ()
        {
            return this.tuning;
        }


        /**
         * Get the Filter for the sample.
         *
         * @return The cutoff in the range of [0..99]
         */
        public int getFilter ()
        {
            return this.filter;
        }


        /**
         * Loudness offset (signed) for the sample.
         *
         * @return The value in the range of [-50..50]
         */
        public int getLoud ()
        {
            return this.loud;
        }
    }
}
