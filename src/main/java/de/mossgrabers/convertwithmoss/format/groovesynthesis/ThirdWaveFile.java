// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.groovesynthesis;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Reads and writes the sample slots (S00, S01, ...) of the Groove Synthesis 3rd Wave: the
 * multi-sample slot files (*.bin) and unified program files (*.pgdata), which contain a program
 * together with the sample slots it uses. The settings of a program are not read. See
 * <i>documentation/design/THIRD_WAVE_FORMAT.md</i> for the layouts.
 *
 * @author Jürgen Moßgraber
 */
final class ThirdWaveFile
{
    /** The maximum number of samples in a slot. */
    static final int            MAX_SAMPLES        = 8;
    /** The size of the sample memory in frames, 35 seconds at 48kHz. */
    static final int            MEMORY_FRAMES      = 1683456;
    /** The lowest sample rate the instrument plays. */
    static final int            MIN_SAMPLE_RATE    = 10000;
    /** The highest sample rate the instrument plays. */
    static final int            MAX_SAMPLE_RATE    = 48000;
    /** The largest distance of the lowest or highest note of a sample from its assigned note. */
    static final int            MAX_TRANSPOSITION  = 24;
    /** The maximum length of a name, which is always written with a terminating zero. */
    static final int            MAX_NAME_LENGTH    = 31;
    /** The maximum volume of a sample in a unified program file (+6dB). */
    static final double         MAX_VOLUME         = 2;

    static final int            LOOP_OFF           = 0;
    static final int            LOOP_WHILE_HELD    = 1;
    static final int            LOOP_ALWAYS        = 2;
    static final int            CROSSFADE_OFF      = 0;
    static final int            CROSSFADE_LINEAR   = 1;
    static final int            BIT_DEPTH_16       = 2;

    private static final String PROGRAM_MAGIC      = "W3_UNIPROG_1";
    private static final String SLOT_MAGIC         = "W3_MSAMPLE_";
    private static final int    MAGIC_SIZE         = 16;
    private static final int    NAME_SIZE          = 32;
    /** The version which is written. */
    private static final int    WRITE_VERSION      = 4;
    /** The version of the samples in a unified program file, which adds the volume. */
    private static final int    PROGRAM_VERSION    = 5;

    private static final int    TYPE_P_WAVETABLE   = 0;
    private static final int    TYPE_U_WAVETABLE   = 1;
    private static final int    TYPE_ANALOG        = 2;
    private static final int    TYPE_SAMPLE_SLOT   = 4;
    /** The analog waveform which the init program uses for its third oscillator. */
    private static final int    ANALOG_WAVEFORM    = 42;
    private static final int    P_WAVETABLE_SIZE   = NAME_SIZE + 65536;
    private static final int    U_WAVETABLE_SIZE   = NAME_SIZE + 524288;
    private static final int    FIRST_SLOT_NUMBER  = 4000;
    private static final int    MAX_RESOURCES      = 12;
    private static final int    MAX_PARAMETERS     = 4096;


    /**
     * A sample of a slot. The audio is always signed 16-bit little-endian mono. The end positions
     * are inclusive.
     *
     * @param name The name of the recording
     * @param sampleRate The sample rate of the recording
     * @param bitDepth The bit depth with which the sample plays: 0 = 8-bit, 1 = 12-bit, 2 = 16-bit
     * @param start The first frame to play
     * @param end The last frame to play
     * @param loopStart The first frame of the loop
     * @param loopEnd The last frame of the loop
     * @param root The assigned note, which plays the recording in its original pitch
     * @param low The lowest note which plays the sample
     * @param high The highest note which plays the sample
     * @param crossfadeType The loop cross-fade: 0 = off, 1 = equal volume, 2 = equal power
     * @param crossfade The length of the loop cross-fade in frames
     * @param loopMode 0 = off, 1 = loop while the note is held, 2 = always loop
     * @param tune The fine tuning in semi-tones, -1 to 1
     * @param secondSampleRate A second sample rate, which is normally the same as the sample rate
     * @param volume The linear volume, 0 to 2
     * @param audio The audio data
     */
    record Sample (String name, float sampleRate, int bitDepth, int start, int end, int loopStart, int loopEnd, int root, int low, int high, int crossfadeType, int crossfade, int loopMode, float tune, float secondSampleRate, float volume, byte [] audio)
    {
        /**
         * Get the number of frames of the recording.
         *
         * @return The number of frames
         */
        int getFrames ()
        {
            return this.audio.length / 2;
        }


        /**
         * Create a copy of the sample with another lowest note.
         *
         * @param newLow The lowest note which plays the sample
         * @return The copy
         */
        Sample withLow (final int newLow)
        {
            return new Sample (this.name, this.sampleRate, this.bitDepth, this.start, this.end, this.loopStart, this.loopEnd, this.root, newLow, this.high, this.crossfadeType, this.crossfade, this.loopMode, this.tune, this.secondSampleRate, this.volume, this.audio);
        }


        /**
         * Create a copy of the sample with another volume.
         *
         * @param newVolume The volume as a factor
         * @return The copy
         */
        Sample withVolume (final float newVolume)
        {
            return new Sample (this.name, this.sampleRate, this.bitDepth, this.start, this.end, this.loopStart, this.loopEnd, this.root, this.low, this.high, this.crossfadeType, this.crossfade, this.loopMode, this.tune, this.secondSampleRate, newVolume, this.audio);
        }
    }


    /**
     * A sample slot.
     *
     * @param name The name of the slot
     * @param index The index of the slot (0 for S00), -1 if unknown
     * @param resource The ID of the slot in the resource directory of a unified program file, -1
     *            for a multi-sample file
     * @param samples The samples of the slot, ordered by their assigned note
     */
    record Slot (String name, int index, int resource, List<Sample> samples)
    {
        /**
         * Get the number of frames of all recordings of the slot.
         *
         * @return The number of frames
         */
        int getFrames ()
        {
            int frames = 0;
            for (final Sample sample: this.samples)
                frames += sample.getFrames ();
            return frames;
        }
    }


    /**
     * The content of a file.
     *
     * @param name The name of the program or slot
     * @param isProgram True if the file is a unified program file
     * @param slots The sample slots
     * @param resourceTypes The types of the resources of a unified program file by their ID
     * @param program The parameters of the program of a unified program file, null if there is
     *            none or its layout is unknown
     */
    record Content (String name, boolean isProgram, List<Slot> slots, int [] resourceTypes, ThirdWaveProgram program)
    {
        /**
         * Test if a resource of a unified program file is a sample slot.
         *
         * @param resource The ID of the resource
         * @return True if it is a sample slot
         */
        boolean isSampleSlot (final int resource)
        {
            return resource >= 0 && resource < this.resourceTypes.length && this.resourceTypes[resource] == TYPE_SAMPLE_SLOT;
        }


        /**
         * Get the sample slot with a resource ID.
         *
         * @param resource The ID of the resource
         * @return The slot, null if it is none or its samples are not included in the file
         */
        Slot getSlot (final int resource)
        {
            for (final Slot slot: this.slots)
                if (slot.resource () == resource)
                    return slot;
            return null;
        }
    }


    /**
     * Private since only static functions.
     */
    private ThirdWaveFile ()
    {
        // Intentionally empty
    }


    /**
     * Read a multi-sample slot or a unified program file.
     *
     * @param file The file to read
     * @return The content, null if the file is not a file of the 3rd Wave
     * @throws IOException The file is broken or has an unknown version
     */
    static Content read (final File file) throws IOException
    {
        try (final RandomAccessFile input = new RandomAccessFile (file, "r"))
        {
            final byte [] header = new byte [MAGIC_SIZE];
            final int length = input.read (header);
            final String magic = new String (header, 0, Math.max (0, length), StandardCharsets.US_ASCII).replace ("\0", "");
            final boolean isProgram = magic.startsWith ("W3_UNIPROG_");
            // Other *.bin files are no error, they belong to other devices
            if (!isProgram && !magic.startsWith (SLOT_MAGIC))
                return null;
            check (length == MAGIC_SIZE, "Broken file header.");

            if (isProgram)
            {
                check (Arrays.equals (header, createMagic (PROGRAM_MAGIC)), "Unsupported unified program version: " + magic);
                return readProgram (input);
            }

            final Slot slot = readSlot (input, getSlotVersion (header, magic), -1, -1);
            check (input.getFilePointer () == input.length (), "Unexpected data behind the last sample.");
            return new Content (slot.name (), false, List.of (slot), new int [0], null);
        }
    }


    /**
     * Write a slot as a multi-sample slot file (version 4). The format has no volume, apply it to
     * the audio before.
     *
     * @param output Where to write the file
     * @param slot The slot to write, the samples need to be ordered by their assigned note
     * @throws IOException Could not write the file or the slot exceeds the limits of the instrument
     */
    static void write (final OutputStream output, final Slot slot) throws IOException
    {
        output.write (createMagic (SLOT_MAGIC + WRITE_VERSION));
        writeSlot (output, slot, WRITE_VERSION);
    }


    /**
     * Write a unified program file: the program and the sample slots it plays. The silent
     * oscillators of the program play an analog waveform, which is part of every instrument and
     * therefore not included in the file.
     *
     * @param output Where to write the file
     * @param name The name of the program
     * @param slots The sample slots, their index is their ID in the resource directory; the ID of
     *            the analog waveform is the number of slots
     * @param program The program, which uses these IDs
     * @throws IOException Could not write the file or the slots exceed the limits of the instrument
     */
    static void writeProgram (final OutputStream output, final String name, final List<Slot> slots, final ThirdWaveProgram program) throws IOException
    {
        final int count = slots.size () + 1;
        check (count <= MAX_RESOURCES, "Too many sample slots for a program.");

        output.write (createMagic (PROGRAM_MAGIC));
        output.write (count);
        final ByteBuffer directory = ByteBuffer.allocate (4 * count).order (ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < slots.size (); i++)
            directory.put ((byte) i).put ((byte) TYPE_SAMPLE_SLOT).putShort ((short) (FIRST_SLOT_NUMBER + i));
        directory.put ((byte) slots.size ()).put ((byte) TYPE_ANALOG).putShort ((short) ANALOG_WAVEFORM);
        output.write (directory.array ());

        for (int i = 0; i < slots.size (); i++)
        {
            output.write (TYPE_SAMPLE_SLOT);
            output.write (i);
            output.write (1);
            writeSlot (output, slots.get (i), PROGRAM_VERSION);
        }
        output.write (TYPE_ANALOG);
        output.write (slots.size ());
        output.write (0);

        output.write (createName (name));
        program.write (output);
    }


    /**
     * Write a slot: the number of samples, the name of the slot and the samples with their audio.
     *
     * @param output Where to write the slot
     * @param slot The slot, the samples need to be ordered by their assigned note
     * @param version The version of the samples: 4 or 5, which adds the volume
     * @throws IOException Could not write the slot or it exceeds the limits of the instrument
     */
    private static void writeSlot (final OutputStream output, final Slot slot, final int version) throws IOException
    {
        final List<Sample> samples = slot.samples ();
        check (!samples.isEmpty () && samples.size () <= MAX_SAMPLES && slot.getFrames () <= MEMORY_FRAMES, "The slot exceeds the limits of the instrument.");

        output.write (samples.size ());
        output.write (createName (slot.name ()));
        for (final Sample sample: samples)
        {
            final ByteBuffer buffer = ByteBuffer.allocate (version >= PROGRAM_VERSION ? 78 : 74).order (ByteOrder.LITTLE_ENDIAN);
            buffer.putInt (sample.getFrames ());
            buffer.putFloat (sample.sampleRate ());
            buffer.put ((byte) sample.bitDepth ());
            buffer.put (createName (sample.name ()));
            buffer.putInt (sample.start ());
            buffer.putInt (sample.end ());
            buffer.putInt (sample.loopStart ());
            buffer.putInt (sample.loopEnd ());
            buffer.put ((byte) sample.root ());
            buffer.put ((byte) sample.low ());
            buffer.put ((byte) sample.high ());
            buffer.put ((byte) sample.crossfadeType ());
            buffer.putInt (sample.crossfade ());
            buffer.put ((byte) sample.loopMode ());
            buffer.putFloat (sample.tune ());
            buffer.putFloat (sample.secondSampleRate ());
            if (version >= PROGRAM_VERSION)
                buffer.putFloat (sample.volume ());
            output.write (buffer.array ());
            output.write (sample.audio ());
        }
    }


    /**
     * Get the version of a multi-sample slot file. Version 1 has no sample rate, bit depth and
     * tuning, version 4 adds them. The samples of version 5 are read like the ones of a unified
     * program file, which add the volume.
     *
     * @param header The header of the file
     * @param magic The header as text
     * @return The version
     * @throws IOException Unknown version
     */
    private static int getSlotVersion (final byte [] header, final String magic) throws IOException
    {
        for (final int version: new int []
        {
            1,
            WRITE_VERSION,
            PROGRAM_VERSION
        })
            if (Arrays.equals (header, createMagic (SLOT_MAGIC + version)))
                return version;
        throw new IOException ("Unsupported multi-sample slot version: " + magic);
    }


    /**
     * Read the sample slots of a unified program file: a directory of the wavetables and sample
     * slots the oscillators use, followed by their data - if it is included - and by the program.
     *
     * @param input The input positioned behind the header
     * @return The content
     * @throws IOException The file is broken
     */
    private static Content readProgram (final RandomAccessFile input) throws IOException
    {
        final int count = input.readUnsignedByte ();
        check (count <= MAX_RESOURCES, "Too many resources in the program.");

        // Directory entries: ID, type, number
        final int [] types = new int [count];
        final int [] numbers = new int [count];
        final boolean [] isRead = new boolean [count];
        for (int i = 0; i < count; i++)
        {
            final int id = input.readUnsignedByte ();
            check (id < count && !isRead[id], "Invalid resource directory.");
            isRead[id] = true;
            types[id] = input.readUnsignedByte ();
            numbers[id] = Short.toUnsignedInt (Short.reverseBytes (input.readShort ()));
        }

        // Resources: type, ID, flag if the data is included, data
        Arrays.fill (isRead, false);
        final List<Slot> slots = new ArrayList<> ();
        for (int i = 0; i < count; i++)
        {
            final int type = input.readUnsignedByte ();
            final int id = input.readUnsignedByte ();
            final int hasData = input.readUnsignedByte ();
            check (id < count && !isRead[id] && type == types[id] && hasData <= 1, "The resources do not match the resource directory.");
            isRead[id] = true;
            if (hasData == 0)
                continue;

            switch (type)
            {
                case TYPE_P_WAVETABLE:
                    skip (input, P_WAVETABLE_SIZE);
                    break;
                case TYPE_U_WAVETABLE:
                    skip (input, U_WAVETABLE_SIZE);
                    break;
                case TYPE_SAMPLE_SLOT:
                    slots.add (readSlot (input, PROGRAM_VERSION, numbers[id] - FIRST_SLOT_NUMBER, id));
                    break;
                default:
                    throw new IOException ("Unsupported resource type: " + type);
            }
        }

        // The program: name, number of integer and float parameters of each part, number of the
        // parameters of the program and number of sequencer events
        final String name = readName (input);
        final long numPartIntegers = readUnsignedInt (input);
        final long numPartFloats = readUnsignedInt (input);
        final long numProgramIntegers = readUnsignedInt (input);
        final long numEvents = readUnsignedInt (input);
        check (numPartIntegers <= MAX_PARAMETERS && numPartFloats <= MAX_PARAMETERS && numProgramIntegers <= MAX_PARAMETERS, "Invalid number of program parameters.");
        final long programSize = 4 * (4 * (numPartIntegers + numPartFloats) + numProgramIntegers) + 24 * numEvents;
        check (programSize == input.length () - input.getFilePointer (), "Broken program data.");

        ThirdWaveProgram program = null;
        if (numPartIntegers == ThirdWaveProgram.NUM_PART_INTEGERS && numPartFloats == ThirdWaveProgram.NUM_PART_FLOATS && numProgramIntegers >= ThirdWaveProgram.NUM_USED_PROGRAM_INTEGERS)
        {
            final byte [] parameters = new byte [(int) (4 * (4 * (numPartIntegers + numPartFloats) + numProgramIntegers))];
            input.readFully (parameters);
            program = ThirdWaveProgram.read (ByteBuffer.wrap (parameters), (int) numProgramIntegers);
        }
        return new Content (name, true, slots, types, program);
    }


    /**
     * Read a sample slot: the number of samples, the name of the slot and the samples, each
     * followed by its audio.
     *
     * @param input The input
     * @param version The version of the samples
     * @param index The index of the slot, -1 if unknown
     * @param resource The ID of the slot in the resource directory, -1 if none
     * @return The slot
     * @throws IOException The data is broken
     */
    private static Slot readSlot (final RandomAccessFile input, final int version, final int index, final int resource) throws IOException
    {
        final int count = input.readUnsignedByte ();
        check (count <= MAX_SAMPLES, "A slot holds at most 8 samples but found " + count + ".");
        final String slotName = readName (input);

        final List<Sample> samples = new ArrayList<> (count);
        for (int i = 0; i < count; i++)
        {
            // Samples which use the same recording each store a copy of the audio
            final long frames = readUnsignedInt (input);
            check (frames > 0 && frames <= MEMORY_FRAMES, "Invalid length of a sample.");
            final float sampleRate = version >= WRITE_VERSION ? readFloat (input) : MAX_SAMPLE_RATE;
            final int bitDepth = version >= WRITE_VERSION ? input.readUnsignedByte () : BIT_DEPTH_16;
            final String name = readName (input);
            final int start = readPosition (input, frames);
            final int end = readPosition (input, frames);
            final int loopStart = readPosition (input, frames);
            final int loopEnd = readPosition (input, frames);
            final int root = input.readUnsignedByte ();
            final int low = input.readUnsignedByte ();
            final int high = input.readUnsignedByte ();
            final int crossfadeType = input.readUnsignedByte ();
            final long crossfade = readUnsignedInt (input);
            final int loopMode = input.readUnsignedByte ();
            final float tune = version >= WRITE_VERSION ? readFloat (input) : 0;
            final float secondSampleRate = version >= WRITE_VERSION ? readFloat (input) : sampleRate;
            final float volume = version >= PROGRAM_VERSION ? readFloat (input) : 1;

            // Strict checks: a file with a different layout must fail instead of producing noise
            check (sampleRate >= 1000 && sampleRate <= 192000 && secondSampleRate > 0 && bitDepth <= BIT_DEPTH_16, "Invalid audio format of the sample '" + name + "'.");
            check (root <= 127 && low <= high && high <= 127, "Invalid key range of the sample '" + name + "'.");
            check (start <= end && crossfadeType <= 2 && crossfade <= frames && loopMode <= LOOP_ALWAYS, "Invalid play range or loop of the sample '" + name + "'.");
            check (Math.abs (tune) <= 1.001f && volume >= 0 && volume <= 2.001f, "Invalid tuning or volume of the sample '" + name + "'.");
            check (2 * frames <= input.length () - input.getFilePointer (), "The audio of the sample '" + name + "' is incomplete.");

            final byte [] audio = new byte [(int) (2 * frames)];
            input.readFully (audio);
            samples.add (new Sample (name, sampleRate, bitDepth, start, end, loopStart, loopEnd, root, low, high, crossfadeType, (int) crossfade, loopMode, tune, secondSampleRate, volume, audio));
        }
        return new Slot (slotName, index, resource, samples);
    }


    private static byte [] createMagic (final String magic)
    {
        return Arrays.copyOf (magic.getBytes (StandardCharsets.US_ASCII), MAGIC_SIZE);
    }


    /**
     * Creates a name with a terminating zero. Characters other than printable ASCII are replaced.
     *
     * @param name The name
     * @return The 32 bytes of the name
     */
    private static byte [] createName (final String name)
    {
        final byte [] text = (name == null ? "" : name.replaceAll ("[^\\x20-\\x7E]", "_")).getBytes (StandardCharsets.US_ASCII);
        final byte [] bytes = new byte [NAME_SIZE];
        System.arraycopy (text, 0, bytes, 0, Math.min (text.length, MAX_NAME_LENGTH));
        return bytes;
    }


    private static String readName (final RandomAccessFile input) throws IOException
    {
        final byte [] bytes = new byte [NAME_SIZE];
        input.readFully (bytes);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0)
            length++;
        return new String (bytes, 0, length, StandardCharsets.US_ASCII).trim ();
    }


    private static long readUnsignedInt (final RandomAccessFile input) throws IOException
    {
        return Integer.toUnsignedLong (Integer.reverseBytes (input.readInt ()));
    }


    private static float readFloat (final RandomAccessFile input) throws IOException
    {
        final float value = Float.intBitsToFloat (Integer.reverseBytes (input.readInt ()));
        check (Float.isFinite (value), "Invalid number.");
        return value;
    }


    private static int readPosition (final RandomAccessFile input, final long frames) throws IOException
    {
        final long position = readUnsignedInt (input);
        check (position < frames, "A sample position lies behind the end of the sample.");
        return (int) position;
    }


    private static void skip (final RandomAccessFile input, final long size) throws IOException
    {
        check (size <= input.length () - input.getFilePointer (), "The data of a wavetable is incomplete.");
        input.seek (input.getFilePointer () + size);
    }


    private static void check (final boolean condition, final String message) throws IOException
    {
        if (!condition)
            throw new IOException (message);
    }
}
