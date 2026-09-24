// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * The parameters of a program of the Groove Synthesis 3rd Wave. A program has 4 parts, each with 3
 * oscillators, 181 integer and 136 float parameters, followed by 181 integer parameters of the
 * whole program. Written programs are created from the init program of the instrument, of which
 * only the oscillators of the parts with their levels and tuning, the volume and panning of the
 * parts and the split of the keyboard are changed. See
 * <i>documentation/design/THIRD_WAVE_FORMAT.md</i> for the positions of these parameters.
 *
 * @author Jürgen Moßgraber
 */
final class ThirdWaveProgram
{
    /** The number of parts of a program. */
    static final int            NUM_PARTS                 = 4;
    /** The number of oscillators of a part. */
    static final int            NUM_OSCILLATORS           = 3;
    /** The number of integer parameters of a part. */
    static final int            NUM_PART_INTEGERS         = 181;
    /** The number of float parameters of a part. */
    static final int            NUM_PART_FLOATS           = 136;
    /** The number of integer parameters of the program, older versions have fewer. */
    static final int            NUM_PROGRAM_INTEGERS      = 181;
    /** The number of the first integer parameters of the program which are used. */
    static final int            NUM_USED_PROGRAM_INTEGERS = 28;

    /** Part integers: the wavetable, analog waveform or sample slot of oscillator 1 to 3. */
    private static final int    PART_OSCILLATOR_WAVE      = 0;
    /** Part integers: 1 if oscillator 1 to 3 follows the keyboard, 0 if its pitch is fixed. */
    private static final int    PART_OSCILLATOR_PITCH     = 167;
    /** Part floats: the coarse tuning of oscillator 1 to 3 in semi-tones. */
    private static final int    PART_OSCILLATOR_COARSE    = 0;
    /** Part floats: the fine tuning of oscillator 1 to 3 in semi-tones (-0.5 to 0.5). */
    private static final int    PART_OSCILLATOR_FINE      = 3;
    /** Part floats: the level (0 to 1) of oscillator 1 to 3. */
    private static final int    PART_OSCILLATOR_LEVEL     = 6;
    /** Part floats: the transposition of the keyboard in semi-tones (-24 to 24). */
    private static final int    PART_TRANSPOSE            = 9;
    /** Part floats: the volume of the part as a factor (0 to 1.7). */
    private static final int    PART_VOLUME               = 112;
    /** The maximum volume of a part. */
    static final float          MAX_PART_VOLUME           = 1.7f;
    /** Part floats: the panning of the part (0 = left, 0.5 = center, 1 = right). */
    private static final int    PART_PAN                  = 124;
    /** Part floats: the fine tuning of the part in semi-tones (-0.5 to 0.5). */
    private static final int    PART_FINE                 = 125;
    /** Program integers: the number of split points of the keyboard (0 to 3). */
    private static final int    PROGRAM_SPLIT_COUNT       = 18;
    /** Program integers: the notes of the split points 1 to 3. */
    private static final int    PROGRAM_SPLIT_NOTE        = 21;
    /** Program integers: the parts (bit mask) which the keyboard sections 1 to 4 play. */
    private static final int    PROGRAM_SECTION_PARTS     = 24;

    private static final String TEMPLATE                  = "InitProgram.pro";
    private static final String TEMPLATE_MAGIC            = "W3_PROG_5";

    private final int [] []     partIntegers              = new int [NUM_PARTS] [NUM_PART_INTEGERS];
    private final float [] []   partFloats                = new float [NUM_PARTS] [NUM_PART_FLOATS];
    private final int []        programIntegers           = new int [NUM_PROGRAM_INTEGERS];


    /**
     * Private since created by the factory methods.
     */
    private ThirdWaveProgram ()
    {
        // Intentionally empty
    }


    /**
     * Create a program from the init program of the instrument ('init prog' on the home screen),
     * which is stored in the program file format of the instrument (*.pro): header, name, the
     * number of parameters and one parameter per line.
     *
     * @return The program
     * @throws IOException Could not read the template
     */
    static ThirdWaveProgram createInitProgram () throws IOException
    {
        final List<String> lines = new ArrayList<> ();
        try (final InputStream in = ThirdWaveProgram.class.getResourceAsStream (TEMPLATE))
        {
            if (in == null)
                throw new IOException ("Missing template: " + TEMPLATE);
            for (final String line: new String (in.readAllBytes (), StandardCharsets.US_ASCII).split ("\n"))
                lines.add (line.trim ());
        }

        final ThirdWaveProgram program = new ThirdWaveProgram ();
        try
        {
            if (!TEMPLATE_MAGIC.equals (lines.get (0)) || Integer.parseInt (lines.get (2)) != NUM_PART_INTEGERS || Integer.parseInt (lines.get (3)) != NUM_PART_FLOATS || Integer.parseInt (lines.get (4)) != NUM_PROGRAM_INTEGERS)
                throw new IOException ("Unsupported template: " + TEMPLATE);
            int line = 6;
            for (int part = 0; part < NUM_PARTS; part++)
                for (int i = 0; i < NUM_PART_INTEGERS; i++)
                    program.partIntegers[part][i] = Integer.parseInt (lines.get (line++));
            for (int part = 0; part < NUM_PARTS; part++)
                for (int i = 0; i < NUM_PART_FLOATS; i++)
                    program.partFloats[part][i] = Float.parseFloat (lines.get (line++));
            for (int i = 0; i < NUM_PROGRAM_INTEGERS; i++)
                program.programIntegers[i] = Integer.parseInt (lines.get (line++));
        }
        catch (final NumberFormatException | IndexOutOfBoundsException ex)
        {
            throw new IOException ("Broken template: " + TEMPLATE, ex);
        }
        return program;
    }


    /**
     * Read the parameters of a program as they are stored behind the numbers of the parameters in a
     * unified program file.
     *
     * @param data The parameters, little-endian
     * @param numProgramIntegers The number of integer parameters of the program, at least
     *            NUM_USED_PROGRAM_INTEGERS
     * @return The program
     */
    static ThirdWaveProgram read (final ByteBuffer data, final int numProgramIntegers)
    {
        data.order (ByteOrder.LITTLE_ENDIAN);
        final ThirdWaveProgram program = new ThirdWaveProgram ();
        for (int part = 0; part < NUM_PARTS; part++)
            for (int i = 0; i < NUM_PART_INTEGERS; i++)
                program.partIntegers[part][i] = data.getInt ();
        for (int part = 0; part < NUM_PARTS; part++)
            for (int i = 0; i < NUM_PART_FLOATS; i++)
                program.partFloats[part][i] = data.getFloat ();
        for (int i = 0; i < numProgramIntegers; i++)
        {
            final int value = data.getInt ();
            if (i < NUM_PROGRAM_INTEGERS)
                program.programIntegers[i] = value;
        }
        return program;
    }


    /**
     * Get what an oscillator of a part plays.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @return The ID of the resource (sample slot, wavetable or analog waveform) in the resource
     *         directory of the program file
     */
    int getOscillatorResource (final int part, final int oscillator)
    {
        return this.partIntegers[part][PART_OSCILLATOR_WAVE + oscillator];
    }


    /**
     * Get the level of an oscillator of a part.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @return The level, 0 to 1
     */
    float getOscillatorLevel (final int part, final int oscillator)
    {
        return this.partFloats[part][PART_OSCILLATOR_LEVEL + oscillator];
    }


    /**
     * Get the tuning of an oscillator of a part.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @return The coarse and fine tuning in semi-tones
     */
    double getOscillatorTuning (final int part, final int oscillator)
    {
        return (double) this.partFloats[part][PART_OSCILLATOR_COARSE + oscillator] + this.partFloats[part][PART_OSCILLATOR_FINE + oscillator];
    }


    /**
     * Does an oscillator of a part follow the keyboard?
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @return False if its pitch is fixed
     */
    boolean isOscillatorPitchTracked (final int part, final int oscillator)
    {
        return this.partIntegers[part][PART_OSCILLATOR_PITCH + oscillator] != 0;
    }


    /**
     * Get the transposition of the keyboard of a part.
     *
     * @param part The index of the part (0-3)
     * @return The transposition in semi-tones
     */
    int getPartTranspose (final int part)
    {
        return Math.round (this.partFloats[part][PART_TRANSPOSE]);
    }


    /**
     * Get the volume of a part.
     *
     * @param part The index of the part (0-3)
     * @return The volume as a factor, 0 to 1.7
     */
    float getPartVolume (final int part)
    {
        return this.partFloats[part][PART_VOLUME];
    }


    /**
     * Get the panning of a part.
     *
     * @param part The index of the part (0-3)
     * @return The panning, -1 (left) to 1 (right)
     */
    double getPartPanning (final int part)
    {
        return Math.clamp (2.0 * this.partFloats[part][PART_PAN] - 1.0, -1.0, 1.0);
    }


    /**
     * Get the fine tuning of a part.
     *
     * @param part The index of the part (0-3)
     * @return The tuning in semi-tones
     */
    double getPartFineTuning (final int part)
    {
        return this.partFloats[part][PART_FINE];
    }


    /**
     * Get the ranges of the keyboard sections, which are split by the split points.
     *
     * @return The lowest and highest note of each section; the note of a split point belongs to the
     *         lower section
     */
    int [] [] getKeyboardSections ()
    {
        final int numSplits = Math.clamp (this.programIntegers[PROGRAM_SPLIT_COUNT], 0, NUM_PARTS - 1);
        final int [] [] sections = new int [numSplits + 1] [2];
        int low = 0;
        for (int i = 0; i <= numSplits; i++)
        {
            final int high = i == numSplits ? 127 : Math.clamp (this.programIntegers[PROGRAM_SPLIT_NOTE + i], low, 127);
            sections[i][0] = low;
            sections[i][1] = high;
            low = Math.min (high + 1, 127);
        }
        return sections;
    }


    /**
     * Get the parts which a keyboard section plays.
     *
     * @param section The index of the section (0-3)
     * @return The parts as a bit mask, bit 0 is part 1
     */
    int getSectionParts (final int section)
    {
        return this.programIntegers[PROGRAM_SECTION_PARTS + section];
    }


    /**
     * Set what an oscillator of a part plays.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @param resource The ID of the resource (sample slot, wavetable or analog waveform) in the
     *            resource directory of the program file
     * @param level The level of the oscillator, 0 to 1
     */
    void setOscillator (final int part, final int oscillator, final int resource, final float level)
    {
        this.partIntegers[part][PART_OSCILLATOR_WAVE + oscillator] = resource;
        this.partFloats[part][PART_OSCILLATOR_LEVEL + oscillator] = level;
    }


    /**
     * Set the resource of an oscillator of a part, keeping its level.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @param resource The ID of the resource in the resource directory of the program file
     */
    void setOscillatorResource (final int part, final int oscillator, final int resource)
    {
        this.partIntegers[part][PART_OSCILLATOR_WAVE + oscillator] = resource;
    }


    /**
     * Set the tuning of an oscillator of a part.
     *
     * @param part The index of the part (0-3)
     * @param oscillator The index of the oscillator (0-2)
     * @param tuning The tuning in semi-tones, -64 to 63
     */
    void setOscillatorTuning (final int part, final int oscillator, final double tuning)
    {
        final long coarse = Math.clamp (Math.round (tuning), -64, 63);
        this.partFloats[part][PART_OSCILLATOR_COARSE + oscillator] = coarse;
        this.partFloats[part][PART_OSCILLATOR_FINE + oscillator] = (float) Math.clamp (tuning - coarse, -0.5, 0.5);
    }


    /**
     * Set the volume of a part.
     *
     * @param part The index of the part (0-3)
     * @param volume The volume as a factor, 0 to 1.7
     */
    void setPartVolume (final int part, final float volume)
    {
        this.partFloats[part][PART_VOLUME] = Math.clamp (volume, 0, MAX_PART_VOLUME);
    }


    /**
     * Set the panning of a part.
     *
     * @param part The index of the part (0-3)
     * @param panning The panning, -1 (left) to 1 (right)
     */
    void setPartPanning (final int part, final double panning)
    {
        this.partFloats[part][PART_PAN] = (float) ((Math.clamp (panning, -1.0, 1.0) + 1.0) / 2.0);
    }


    /**
     * Split the keyboard into sections.
     *
     * @param splitNotes The notes of the split points, at most 3, increasing
     * @param sectionParts The parts (bit mask) which each section plays, one more than there are
     *            split points
     */
    void setKeyboardSplits (final int [] splitNotes, final int [] sectionParts)
    {
        this.programIntegers[PROGRAM_SPLIT_COUNT] = splitNotes.length;
        for (int i = 0; i < splitNotes.length; i++)
            this.programIntegers[PROGRAM_SPLIT_NOTE + i] = splitNotes[i];
        for (int i = 0; i < NUM_PARTS; i++)
            this.programIntegers[PROGRAM_SECTION_PARTS + i] = i < sectionParts.length ? sectionParts[i] : 1 << i;
    }


    /**
     * Write the program as it is stored behind the name in a unified program file: the numbers of
     * the parameters, the parameters and the (empty) sequence.
     *
     * @param output Where to write the program
     * @throws IOException Could not write the program
     */
    void write (final OutputStream output) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.allocate (16 + 4 * (NUM_PARTS * (NUM_PART_INTEGERS + NUM_PART_FLOATS) + NUM_PROGRAM_INTEGERS)).order (ByteOrder.LITTLE_ENDIAN);
        buffer.putInt (NUM_PART_INTEGERS).putInt (NUM_PART_FLOATS).putInt (NUM_PROGRAM_INTEGERS);
        // No sequencer events
        buffer.putInt (0);
        for (final int [] integers: this.partIntegers)
            for (final int value: integers)
                buffer.putInt (value);
        for (final float [] floats: this.partFloats)
            for (final float value: floats)
                buffer.putFloat (value);
        for (final int value: this.programIntegers)
            buffer.putInt (value);
        output.write (buffer.array ());
    }
}
