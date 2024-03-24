// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Accessor for a sample chunk ("smpl") in a WAV file.
 *
 * @author Jürgen Moßgraber
 */
public class SampleChunk extends WavChunk
{
    /** Play the loop forwards. */
    public static final int             LOOP_FORWARD     = 0;
    /** Play the loop alternating forwards/backwards. */
    public static final int             LOOP_ALTERNATING = 1;
    /** Play the loop backwards. */
    public static final int             LOOP_BACKWARDS   = 2;

    private static final int            CHUNK_SIZE       = 36;
    private static final int            LOOP_SIZE        = 24;

    private final List<SampleChunkLoop> loops;


    /**
     * Constructor. Creates an empty sample chunk.
     *
     * @param numSampleLoops The number of loops for which to reserve data
     */
    public SampleChunk (final int numSampleLoops)
    {
        super (RiffID.SMPL_ID, new RIFFChunk (0, RiffID.SMPL_ID.getId (), CHUNK_SIZE));

        final int sizeOfLoops = numSampleLoops * LOOP_SIZE;
        this.chunk.setData (new byte [CHUNK_SIZE + sizeOfLoops]);
        this.setNumSampleLoops (numSampleLoops);
        this.setSamplerData (sizeOfLoops);

        this.loops = new ArrayList<> (numSampleLoops);
        for (int i = 0; i < numSampleLoops; i++)
            this.loops.add (new SampleChunkLoop (CHUNK_SIZE + i * LOOP_SIZE));
    }


    /**
     * Constructor.
     *
     * @param chunk The RIFF chunk which contains the data
     * @throws ParseException Length of data does not match the expected chunk size
     */
    public SampleChunk (final RIFFChunk chunk) throws ParseException
    {
        super (RiffID.SMPL_ID, chunk, CHUNK_SIZE);

        final byte [] data = chunk.getData ();

        // Check loop data
        final int additionalDataSize = data.length - CHUNK_SIZE;
        final int numSampleLoops = this.getNumSampleLoops ();
        if (additionalDataSize < numSampleLoops * LOOP_SIZE)
            throw new ParseException ("Loop section of Sample chunk too short. Corrupted file?!");

        this.loops = new ArrayList<> (numSampleLoops);
        for (int i = 0; i < numSampleLoops; i++)
            this.loops.add (new SampleChunkLoop (CHUNK_SIZE + i * LOOP_SIZE));
    }


    /**
     * The manufacturer field specifies the MIDI Manufacturer's Association (MMA) Manufacturer code
     * for the sampler intended to receive this file's waveform. Each manufacturer of a MIDI product
     * is assigned a unique ID which identifies the company. If no particular manufacturer is to be
     * specified, a value of 0 should be used.
     *
     * @return The four bytes converted to an integer
     */
    public int getManufacturer ()
    {
        return this.chunk.getFourBytesAsInt (0x00);
    }


    /**
     * The product field specifies the MIDI model ID defined by the manufacturer corresponding to
     * the Manufacturer field. Contact the manufacturer of the sampler to get the model ID. If no
     * particular manufacturer's product is to be specified, a value of 0 should be used.
     *
     * @return The four bytes converted to an integer
     */
    public int getProduct ()
    {
        return this.chunk.getFourBytesAsInt (0x04);
    }


    /**
     * The sample period specifies the duration of time that passes during the playback of one
     * sample in nanoseconds (normally equal to 1 / Samples Per Second, where Samples Per Second is
     * the value found in the format chunk).
     *
     * @return The four bytes converted to an integer
     */
    public int getSamplePeriod ()
    {
        return this.chunk.getFourBytesAsInt (0x08);
    }


    /**
     * Set the sample period. This is 1000000000 / sample frequency (in Hertz).
     *
     * @param samplePeriod The sample period
     */
    public void setSamplePeriod (final int samplePeriod)
    {
        this.chunk.setIntAsFourBytes (0x08, samplePeriod);
    }


    /**
     * The MIDI unity note value has the same meaning as the instrument chunk's MIDI Unshifted Note
     * field which specifies the musical note at which the sample will be played at it's original
     * sample rate (the sample rate specified in the format chunk).
     *
     * @return The four bytes converted to an integer
     */
    public int getMIDIUnityNote ()
    {
        return this.chunk.getFourBytesAsInt (0x0C);
    }


    /**
     * The MIDI unity note value has the same meaning as the instrument chunk's MIDI Unshifted Note
     * field which specifies the musical note at which the sample will be played at it's original
     * sample rate (the sample rate specified in the format chunk).
     *
     * @param note The MIDI note
     */
    public void setMIDIUnityNote (final int note)
    {
        this.chunk.setIntAsFourBytes (0x0C, note);
    }


    /**
     * The MIDI pitch fraction specifies the fraction of a semitone up from the specified MIDI unity
     * note field. A value of 0x80000000 means 1/2 semitone (50 cents) and a value of 0x00000000
     * means no fine tuning between semitones. Only positive values can be stored in this field, so
     * the note can only be tuned sharp. If you want to represent a negative fine tuning, you must
     * add 1 to the root key (which lowers the sample playback pitch by 1 semitone) and add 100 to
     * the negative tuning value to achieve the correct positive value. This conversion can be
     * reversed on WAV import for any fine tuning value larger than 50, effectively providing a fine
     * tune range of -49 to 50 cents for each sample.
     *
     * @return The four bytes converted to an integer
     */
    public int getMIDIPitchFraction ()
    {
        return this.chunk.getFourBytesAsInt (0x10);
    }


    /**
     * Returns the MIDI pitch fraction in positive cents.
     *
     * @return The cents
     */
    public int getMIDIPitchFractionAsCents ()
    {
        // 2147483648.0 = 0x80000000
        return (int) Math.round (this.getMIDIPitchFraction () * 50.0 / 2147483648.0);
    }


    /**
     * Set the MIDI pitch fraction.
     *
     * @param pitchFraction The pitch fraction
     */
    public void setMIDIPitchFraction (final int pitchFraction)
    {
        this.chunk.setIntAsFourBytes (0x10, pitchFraction);
    }


    /**
     * Set the MIDI pitch fraction in cents.
     *
     * @param cents The pitch fraction in cents
     */
    public void setMIDIPitchFractionAsCents (final int cents)
    {
        this.setMIDIPitchFraction ((int) Math.round (cents * 2147483648.0 / 50.0));
    }


    /**
     * The SMPTE format specifies the Society of Motion Pictures and Television E time format used
     * in the following SMPTE Offset field. If a value of 0 is set, SMPTE Offset should also be set
     * to 0.
     *
     * @return The four bytes converted to an integer
     */
    public int getSMPTEFormat ()
    {
        return this.chunk.getFourBytesAsInt (0x14);
    }


    /**
     * The SMPTE Offset value specifies the time offset to be used for the synchronization /
     * calibration to the first sample in the waveform.
     *
     * @return The four bytes converted to an integer
     */
    public int getSMPTEOffset ()
    {
        return this.chunk.getFourBytesAsInt (0x18);
    }


    /**
     * The sample loops field specifies the number Sample Loop definitions in the following list.
     * This value may be set to 0 meaning that no sample loops follow.
     *
     * @return The four bytes converted to an integer
     */
    public int getNumSampleLoops ()
    {
        return this.chunk.getFourBytesAsInt (0x1C);
    }


    /**
     * The sample loops field specifies the number Sample Loop definitions in the following list.
     * This value may be set to 0 meaning that no sample loops follow.
     *
     * @param numLoops The number of loops
     */
    private void setNumSampleLoops (final int numLoops)
    {
        this.chunk.setIntAsFourBytes (0x1C, numLoops);
    }


    /**
     * The sampler data value specifies the number of bytes that will follow this chunk (including
     * the entire sample loop list). This value is greater than 0 when an application needs to save
     * additional information. This value is reflected in this chunks data size value.
     *
     * @return The four bytes converted to an integer
     */
    public int getSamplerData ()
    {
        return this.chunk.getFourBytesAsInt (0x20);
    }


    /**
     * Set the sampler data value.
     *
     * @param sampleDataSize The size
     */
    private void setSamplerData (final int sampleDataSize)
    {
        this.chunk.setIntAsFourBytes (0x20, sampleDataSize);
    }


    /**
     * Get the loops, if any.
     *
     * @return The loops, never null
     */
    public List<SampleChunkLoop> getLoops ()
    {
        return this.loops;
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Manufacturer: ").append (String.format ("0x%X", Integer.valueOf (this.getManufacturer ()))).append ('\n');
        sb.append ("Product: ").append (this.getProduct ()).append ('\n');
        sb.append ("Sample Period: ").append (String.format ("0x%X", Integer.valueOf (this.getSamplePeriod ()))).append ('\n');
        sb.append ("MIDI Unity Note: ").append (this.getMIDIUnityNote ()).append ('\n');
        sb.append ("MIDI Pitch Fraction: ").append (this.getMIDIPitchFraction ()).append (" (= ").append (this.getMIDIPitchFractionAsCents ()).append (" cents)\n");
        sb.append ("SMPTE Format: ").append (this.getSMPTEFormat ()).append ('\n');
        sb.append ("SMPTE Offset: ").append (this.getSMPTEOffset ()).append ('\n');
        sb.append ("Num Sample Loops: ").append (this.getNumSampleLoops ()).append ('\n');
        sb.append ("Sampler Data: ").append (this.getSamplerData ()).append ('\n');
        for (int i = 0; i < this.loops.size (); i++)
            sb.append ("Loop ").append (i + 1).append (":\n").append (this.loops.get (i).infoText ());
        return sb.toString ();
    }


    /**
     * The sample loop section of a sample chunk.
     */
    public class SampleChunkLoop
    {
        private final int offset;


        /**
         * Constructor.
         *
         * @param offset The offset into the data array
         */
        public SampleChunkLoop (final int offset)
        {
            this.offset = offset;
        }


        /**
         * The Cue Point ID specifies the unique ID that corresponds to one of the defined cue
         * points in the cue point list. Furthermore, this ID corresponds to any labels defined in
         * the associated data list chunk which allows text labels to be assigned to the various
         * sample loops.
         *
         * @return The four bytes converted to an integer
         */
        public int getCuePointID ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x00);
        }


        /**
         * The type field defines how the waveform samples will be looped.
         *
         * @return 0 Loop forward (normal), 1 Alternating loop (forward/backward, also known as Ping
         *         Pong), 2 Loop backward (reverse), 3 - 31 Reserved for future standard types,
         *         above 32: Sampler specific types (defined by manufacturer)
         */
        public int getType ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x04);
        }


        /**
         * Set the loop type.
         *
         * @param type The type
         */
        public void setType (final int type)
        {
            SampleChunk.this.chunk.setIntAsFourBytes (this.offset + 0x04, type);
        }


        /**
         * The start value specifies the byte offset into the waveform data of the first sample to
         * be played in the loop.
         *
         * @return The four bytes converted to an integer
         */
        public int getStart ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x08);
        }


        /**
         * Set the start.
         *
         * @param start The start
         */
        public void setStart (final int start)
        {
            SampleChunk.this.chunk.setIntAsFourBytes (this.offset + 0x08, start);
        }


        /**
         * The end value specifies the byte offset into the waveform data of the last sample to be
         * played in the loop.
         *
         * @return The four bytes converted to an integer
         */
        public int getEnd ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x0C);
        }


        /**
         * Set the end.
         *
         * @param end The end
         */
        public void setEnd (final int end)
        {
            SampleChunk.this.chunk.setIntAsFourBytes (this.offset + 0x0C, end);
        }


        /**
         * The fractional value specifies a fraction of a sample at which to loop. This allows a
         * loop to be fine tuned at a resolution greater than one sample. The value can range from
         * 0x00000000 to 0xFFFFFFFF. A value of 0 means no fraction, a value of 0x80000000 means 1/2
         * of a sample length. 0xFFFFFFFF is the smallest fraction of a sample that can be
         * represented.
         *
         * @return The four bytes converted to an integer
         */
        public int getFraction ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x10);
        }


        /**
         * The play count value determines the number of times to play the loop. A value of 0
         * specifies an infinite sustain loop. An infinite sustain loop will continue looping until
         * some external force interrupts playback, such as the musician releasing the key that
         * triggered the wave's playback. All other values specify an absolute number of times to
         * loop.
         *
         * @return The four bytes converted to an integer
         */
        public int getPlayCount ()
        {
            return SampleChunk.this.chunk.getFourBytesAsInt (this.offset + 0x14);
        }


        /**
         * Set the play count.
         *
         * @param playCount The play count
         */
        public void setPlayCount (final int playCount)
        {
            SampleChunk.this.chunk.setIntAsFourBytes (this.offset + 0x14, playCount);
        }


        /**
         * Format all values as a string for dumping it out.
         *
         * @return The formatted string
         */
        public String infoText ()
        {
            final StringBuilder sb = new StringBuilder ();
            sb.append ("  Cue Point ID: ").append (this.getCuePointID ()).append ('\n');
            String type;
            switch (this.getType ())
            {
                default:
                case LOOP_FORWARD:
                    type = "Forward (0)";
                    break;
                case LOOP_ALTERNATING:
                    type = "Alternating (1)";
                    break;
                case LOOP_BACKWARDS:
                    type = "Backward (2)";
                    break;
            }
            sb.append ("  Type: ").append (type).append ('\n');
            sb.append ("  Start: ").append (this.getStart ()).append ('\n');
            sb.append ("  End: ").append (this.getEnd ()).append ('\n');
            sb.append ("  Fraction: ").append (this.getFraction ()).append ('\n');
            sb.append ("  Play Count: ").append (this.getPlayCount ());
            return sb.toString ();
        }
    }
}
