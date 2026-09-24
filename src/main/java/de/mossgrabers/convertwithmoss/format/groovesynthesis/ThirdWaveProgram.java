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
 * whole program. A program is created from the init program of the instrument, of which only the
 * oscillators of the parts, their levels and the split of the keyboard are changed. See
 * <i>documentation/design/THIRD_WAVE_FORMAT.md</i> for the positions of these parameters.
 *
 * @author Jürgen Moßgraber
 */
final class ThirdWaveProgram
{
    /** The number of parts of a program. */
    static final int            NUM_PARTS                   = 4;
    /** The number of oscillators of a part. */
    static final int            NUM_OSCILLATORS             = 3;

    private static final int    NUM_PART_INTEGERS           = 181;
    private static final int    NUM_PART_FLOATS             = 136;
    private static final int    NUM_PROGRAM_INTEGERS        = 181;

    /** Part integers: the wavetable, analog waveform or sample slot of oscillator 1 to 3. */
    private static final int    PART_OSCILLATOR_WAVE        = 0;
    /** Part floats: the level (0 to 1) of oscillator 1 to 3. */
    private static final int    PART_OSCILLATOR_LEVEL       = 6;
    /** Program integers: the number of split points of the keyboard (0 to 3). */
    private static final int    PROGRAM_SPLIT_COUNT         = 18;
    /** Program integers: the notes of the split points 1 to 3. */
    private static final int    PROGRAM_SPLIT_NOTE          = 21;
    /** Program integers: the parts (bit mask) which the keyboard sections 1 to 4 play. */
    private static final int    PROGRAM_SECTION_PARTS       = 24;

    private static final String TEMPLATE                    = "InitProgram.pro";
    private static final String TEMPLATE_MAGIC              = "W3_PROG_5";

    private final int [] []     partIntegers                = new int [NUM_PARTS] [NUM_PART_INTEGERS];
    private final float [] []   partFloats                  = new float [NUM_PARTS] [NUM_PART_FLOATS];
    private final int []        programIntegers             = new int [NUM_PROGRAM_INTEGERS];


    /**
     * Private since created by the factory method.
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
     * Split the keyboard into sections, the first section plays part 1, the second part 2, and so
     * on.
     *
     * @param splitNotes The notes of the split points, at most 3, increasing
     */
    void setKeyboardSplits (final int [] splitNotes)
    {
        this.programIntegers[PROGRAM_SPLIT_COUNT] = splitNotes.length;
        for (int i = 0; i < splitNotes.length; i++)
            this.programIntegers[PROGRAM_SPLIT_NOTE + i] = splitNotes[i];
        for (int i = 0; i < NUM_PARTS; i++)
            this.programIntegers[PROGRAM_SECTION_PARTS + i] = 1 << i;
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
