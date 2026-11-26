// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * A part in a performance.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcPerformancePart
{
    /** The number of control boxes. */
    public static final int                   NUMBER_OF_CONTROL_BOXES       = 16;
    /** The number of boxes (destinations) of the Part LFO. */
    public static final int                   NUMBER_OF_LFO_BOXES           = 3;
    /** The Part LFO destination Element Level. */
    public static final int                   LFO_DESTINATION_LEVEL         = 64;
    /** The Part LFO destination Element Pitch. */
    public static final int                   LFO_DESTINATION_PITCH         = 65;
    /** The Part LFO destination Element Filter Cutoff. */
    public static final int                   LFO_DESTINATION_CUTOFF        = 66;
    /** The control box source Modulation Wheel. */
    public static final int                   CONTROL_SOURCE_MOD_WHEEL      = 1;
    /** The control box destination P.LFO Depth 1, followed by 2 and 3. */
    public static final int                   CONTROL_DESTINATION_LFO_DEPTH = 184;

    // The offsets of the Mute Switch and the Part LFO parameters in the many parameters block,
    // which are identical for MONTAGE and MODX
    private static final int                  OFFSET_MUTE_SWITCH            = 51;
    private static final int                  OFFSET_LFO_PHASE              = 56;
    private static final int                  OFFSET_LFO_WAVE               = 57;
    private static final int                  OFFSET_LFO_SPEED              = 58;
    private static final int                  OFFSET_LFO_TEMPO_SYNC         = 60;
    private static final int                  OFFSET_LFO_KEY_ON_RESET       = 65;
    private static final int                  OFFSET_LFO_BOX                = 67;
    private static final int                  CONTROL_BOX_SIZE              = 9;

    private String                            name;
    private int                               type;
    private int                               mainCategory;
    private int                               subCategory;
    private int                               partSwitch;
    private int                               keyboardSwitch;
    private int                               velocityLimitLow              = 1;
    private int                               velocityLimitHigh             = 127;
    private int                               noteLimitLow                  = 0;
    private int                               noteLimitHigh                 = 127;
    private int                               pitchBendRangeUpper;
    private int                               pitchBendRangeLower;
    private int                               velocitySenseDepth            = 64;
    private int                               velocitySenseOffset           = 64;
    private int                               volume                        = 100;
    private int                               pan                           = 64;
    private int                               detune                        = 128;
    private int                               reverbSend                    = 0;
    private int                               variationSend                 = 0;
    private int                               dryLevel                      = 127;
    private int                               noteShift;

    private final List<YamahaYsfcPartElement> elements                      = new ArrayList<> ();
    private byte []                           manyParameters;
    private byte []                           scenes;
    private final String []                   assignableKnobs               = new String [8];
    private byte []                           controlBoxes;


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param format The format of the YSFC file
     * @param version The exact file version, e.g. 404 or 501
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPerformancePart (final InputStream in, final YamahaYsfcFileFormat format, final int version) throws IOException
    {
        this.read (in, format, version);
    }


    /**
     * Get the name of the performance.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Set the name of the performance.
     *
     * @param name The name
     */
    public void setName (final String name)
    {
        this.name = name;
    }


    /**
     * Set the category.
     *
     * @param categoryID THe category index in the range of [0..255]
     */
    public void setCategory (final int categoryID)
    {
        this.mainCategory = categoryID / 16;
        this.subCategory = categoryID % 16;
    }


    /**
     * Get the lower note limit.
     *
     * @return The MIDI note
     */
    public int getNoteLimitLow ()
    {
        return this.noteLimitLow;
    }


    /**
     * Set the lower note limit.
     *
     * @param noteLimitLow The MIDI note
     */
    public void setNoteLimitLow (final int noteLimitLow)
    {
        this.noteLimitLow = noteLimitLow;
    }


    /**
     * Get the upper note limit.
     *
     * @return The MIDI note
     */
    public int getNoteLimitHigh ()
    {
        return this.noteLimitHigh;
    }


    /**
     * Set the upper note limit.
     *
     * @param noteLimitHigh The MIDI note
     */
    public void setNoteLimitHigh (final int noteLimitHigh)
    {
        this.noteLimitHigh = noteLimitHigh;
    }


    /**
     * Get the lower velocity limit.
     *
     * @return The MIDI velocity
     */
    public int getVelocityLimitLow ()
    {
        return this.velocityLimitLow;
    }


    /**
     * Set the upper velocity limit.
     *
     * @param velocityLimitLow The MIDI velocity
     */
    public void setVelocityLimitLow (final int velocityLimitLow)
    {
        this.velocityLimitLow = velocityLimitLow;
    }


    /**
     * Get the upper pitch bend value.
     *
     * @return The value in the range of 16..88 which relates to -48..+24 (0 ~ 64)
     */
    public int getPitchBendRangeUpper ()
    {
        return this.pitchBendRangeUpper;
    }


    /**
     * Set the upper pitch bend value.
     *
     * @param pitchBendRangeUpper The value in the range of 16..88 which relates to -48..+24 (0 ~
     *            64)
     */
    public void setPitchBendRangeUpper (final int pitchBendRangeUpper)
    {
        this.pitchBendRangeUpper = pitchBendRangeUpper;
    }


    /**
     * Get the lower pitch bend value.
     *
     * @return The value in the range of 16..88 which relates to -48..+24 (0 ~ 64)
     */
    public int getPitchBendRangeLower ()
    {
        return this.pitchBendRangeLower;
    }


    /**
     * Set the lower pitch bend value.
     *
     * @param pitchBendRangeLower The value in the range of 16..88 which relates to -48..+24 (0 ~
     *            64)
     */
    public void setPitchBendRangeLower (final int pitchBendRangeLower)
    {
        this.pitchBendRangeLower = pitchBendRangeLower;
    }


    /**
     * Enable keyboard control for one of the scenes.
     *
     * @param scene The scene [0..7]
     * @param enable True to enable keyboard control for this scene
     */
    public void setSceneKeyboardControl (final int scene, final boolean enable)
    {
        if (this.scenes.length == 176)
            this.scenes[22 * scene + 21] = (byte) (enable ? 1 : 0);
    }


    /**
     * Is the part switched on?
     *
     * @return True if the part is on
     */
    public boolean isPartSwitch ()
    {
        return this.partSwitch > 0;
    }


    /**
     * Set the part to off/on.
     *
     * @param partSwitch True to enable
     */
    public void setPartSwitch (final boolean partSwitch)
    {
        this.partSwitch = partSwitch ? 1 : 0;
    }


    /**
     * Is the part muted?
     *
     * @return True if the part is muted
     */
    public boolean isMute ()
    {
        return this.manyParameters[OFFSET_MUTE_SWITCH] > 0;
    }


    /**
     * Mute or un-mute the part.
     *
     * @param isMute True to mute the part
     */
    public void setMute (final boolean isMute)
    {
        this.manyParameters[OFFSET_MUTE_SWITCH] = (byte) (isMute ? 1 : 0);
    }


    /**
     * Get the volume of the part.
     *
     * @return The volume in the range of 0-127 (Default = 100)
     */
    public int getVolume ()
    {
        return this.volume;
    }


    /**
     * Set the volume of the part.
     *
     * @param volume The volume in the range of 0-127 (Default = 100)
     */
    public void setVolume (final int volume)
    {
        this.volume = volume;
    }


    /**
     * Get the panning of the part.
     *
     * @return The panning in the range of 1-127, 64 = Center (L63 – C – R63)
     */
    public int getPan ()
    {
        return this.pan;
    }


    /**
     * Set the panning of the part.
     *
     * @param pan The panning in the range of 64 = Center, 1-127
     */
    public void setPan (final int pan)
    {
        this.pan = pan;
    }


    /**
     * Get the de-tuning of the part in 0.1 Hz increments.
     *
     * @return Get the de-tuning in the range of 0-255 (Default 128 = 0 (no de-tuning), -12.8Hz –
     *         +0.0Hz – +12.7Hz)
     */
    public int getDetune ()
    {
        return this.detune;
    }


    /**
     * Get the de-tune value as cents.
     *
     * @return De-tune as cents
     */
    public int getDetuneAsCents ()
    {
        return (int) Math.round (convertToCents (this.detune, 440));
    }


    /**
     * Set the de-tuning of the part in 0.1 Hz increments.
     *
     * @param detune Get the de-tuning in the range of 0-255 (Default 128 = 0 (no de-tuning))
     */
    public void setDetune (final int detune)
    {
        this.detune = detune;
    }


    /**
     * Set the de-tune value as cents.
     *
     * @param cents De-tune as cents
     */
    public void setDetuneAsCents (final int cents)
    {
        this.detune = convertToInputValue (cents, 440);
    }


    /**
     * Get the note shift.
     *
     * @return The note shift in the range of 40-88, 64 = no shift (-24 – 0 – +24 semi-tones)
     */
    public int getNoteShift ()
    {
        return this.noteShift;
    }


    /**
     * Set the note shift.
     *
     * @param noteShift The note shift in the range of 40-88, 64 = no shift (-24 – 0 – +24
     *            semi-tones)
     */
    public void setNoteShift (final int noteShift)
    {
        this.noteShift = Math.clamp (noteShift, 40, 88);
    }


    /**
     * Get the phase of the Part LFO.
     *
     * @return The phase: 0 = 0°, 1 = 90°, 2 = 120°, 3 = 180°, 4 = 240°, 5 = 270°
     */
    public int getLfoPhase ()
    {
        return this.manyParameters[OFFSET_LFO_PHASE];
    }


    /**
     * Set the phase of the Part LFO.
     *
     * @param phase The phase: 0 = 0°, 1 = 90°, 2 = 120°, 3 = 180°, 4 = 240°, 5 = 270°
     */
    public void setLfoPhase (final int phase)
    {
        this.manyParameters[OFFSET_LFO_PHASE] = (byte) phase;
    }


    /**
     * Get the wave of the Part LFO.
     *
     * @return The wave: 0 = Triangle, 1 = Triangle+, 2 = Saw Up, 3 = Saw Down, 4 = Squ1/4, 5 =
     *         Squ1/3, 6 = Square, 7 = Squ2/3, 8 = Squ3/4, 9 = Trapezoid, 10 = S/H1, 11 = S/H2, 12 =
     *         User
     */
    public int getLfoWave ()
    {
        return this.manyParameters[OFFSET_LFO_WAVE];
    }


    /**
     * Set the wave of the Part LFO.
     *
     * @param wave The wave, see {@link #getLfoWave()}
     */
    public void setLfoWave (final int wave)
    {
        this.manyParameters[OFFSET_LFO_WAVE] = (byte) wave;
    }


    /**
     * Get the speed of the Part LFO.
     *
     * @return The speed in the range of 0-63
     */
    public int getLfoSpeed ()
    {
        return this.manyParameters[OFFSET_LFO_SPEED];
    }


    /**
     * Set the speed of the Part LFO.
     *
     * @param speed The speed in the range of 0-63
     */
    public void setLfoSpeed (final int speed)
    {
        this.manyParameters[OFFSET_LFO_SPEED] = (byte) speed;
    }


    /**
     * Set the tempo sync of the Part LFO.
     *
     * @param tempoSync 0 = Off, 1 = On
     */
    public void setLfoTempoSync (final int tempoSync)
    {
        this.manyParameters[OFFSET_LFO_TEMPO_SYNC] = (byte) tempoSync;
    }


    /**
     * Get the key on reset of the Part LFO.
     *
     * @return 0 = Off, 1 = Each-On, 2 = 1st-On
     */
    public int getLfoKeyOnReset ()
    {
        return this.manyParameters[OFFSET_LFO_KEY_ON_RESET];
    }


    /**
     * Set the key on reset of the Part LFO.
     *
     * @param keyOnReset 0 = Off, 1 = Each-On, 2 = 1st-On
     */
    public void setLfoKeyOnReset (final int keyOnReset)
    {
        this.manyParameters[OFFSET_LFO_KEY_ON_RESET] = (byte) keyOnReset;
    }


    /**
     * Get the destination of one of the 3 boxes of the Part LFO.
     *
     * @param box The index of the box [0..2]
     * @return The destination, e.g. 64 = Element Level, 65 = Element Pitch, 66 = Element Filter
     *         Cutoff
     */
    public int getLfoBoxDestination (final int box)
    {
        return this.manyParameters[OFFSET_LFO_BOX + 2 * box] & 0xFF;
    }


    /**
     * Get the depth of one of the 3 boxes of the Part LFO.
     *
     * @param box The index of the box [0..2]
     * @return The depth in the range of 0-127
     */
    public int getLfoBoxDepth (final int box)
    {
        return this.manyParameters[OFFSET_LFO_BOX + 2 * box + 1] & 0xFF;
    }


    /**
     * Set the destination and depth of one of the 3 boxes of the Part LFO.
     *
     * @param box The index of the box [0..2]
     * @param destination The destination, see {@link #getLfoBoxDestination(int)}
     * @param depth The depth in the range of 0-127
     */
    public void setLfoBox (final int box, final int destination, final int depth)
    {
        this.manyParameters[OFFSET_LFO_BOX + 2 * box] = (byte) destination;
        this.manyParameters[OFFSET_LFO_BOX + 2 * box + 1] = (byte) Math.clamp (depth, 0, 127);
    }


    /**
     * Get the source of one of the 16 control boxes.
     *
     * @param box The index of the box [0..15]
     * @return The source, 0 = Off, 1 = Modulation Wheel
     */
    public int getControlBoxSource (final int box)
    {
        return this.controlBoxes[CONTROL_BOX_SIZE * box] & 0xFF;
    }


    /**
     * Get the destination of one of the 16 control boxes.
     *
     * @param box The index of the box [0..15]
     * @return The destination, the part destinations start at 128
     */
    public int getControlBoxDestination (final int box)
    {
        final int offset = CONTROL_BOX_SIZE * box;
        return (this.controlBoxes[offset + 1] & 0x7F) << 7 | this.controlBoxes[offset + 2] & 0x7F;
    }


    /**
     * Get the ratio of one of the 16 control boxes.
     *
     * @param box The index of the box [0..15]
     * @return The ratio in the range of -64..+63
     */
    public int getControlBoxRatio (final int box)
    {
        return (this.controlBoxes[CONTROL_BOX_SIZE * box + 8] & 0xFF) - 64;
    }


    /**
     * Find the first control box which connects a source with a destination.
     *
     * @param source The source, 0 = Off, 1 = Modulation Wheel
     * @param destination The destination, the part destinations start at 128
     * @return The index of the box [0..15] or -1 if there is none
     */
    public int findControlBox (final int source, final int destination)
    {
        for (int box = 0; box < NUMBER_OF_CONTROL_BOXES; box++)
            if (this.getControlBoxSource (box) == source && this.getControlBoxDestination (box) == destination)
                return box;
        return -1;
    }


    /**
     * Set one of the 16 control boxes with the standard curve and unipolar polarity.
     *
     * @param box The index of the box [0..15]
     * @param source The source, 0 = Off, 1 = Modulation Wheel
     * @param destination The destination, the part destinations start at 128
     * @param ratio The ratio in the range of -64..+63
     */
    public void setControlBox (final int box, final int source, final int destination, final int ratio)
    {
        final int offset = CONTROL_BOX_SIZE * box;
        this.controlBoxes[offset] = (byte) source;
        this.controlBoxes[offset + 1] = (byte) (destination >> 7 & 0x7F);
        this.controlBoxes[offset + 2] = (byte) (destination & 0x7F);
        // Curve bank and type: Standard
        this.controlBoxes[offset + 3] = 0;
        this.controlBoxes[offset + 4] = 0;
        // Curve parameters 1 and 2
        this.controlBoxes[offset + 5] = 5;
        this.controlBoxes[offset + 6] = 0;
        // Polarity: Uni
        this.controlBoxes[offset + 7] = 0;
        this.controlBoxes[offset + 8] = (byte) (Math.clamp (ratio, -64, 63) + 64);
    }


    /**
     * Read a performance from the input stream.
     *
     * @param in The input stream
     * @param format The format of the YSFC file
     * @param version The exact file version, e.g. 404 or 501
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final YamahaYsfcFileFormat format, final int version) throws IOException
    {
        this.name = StreamUtils.readAscii (in, 21).trim ();
        final int pos = this.name.indexOf (0);
        if (pos >= 0)
            this.name = this.name.substring (0, pos);

        this.type = in.read ();
        this.mainCategory = in.read ();
        this.subCategory = in.read ();
        this.partSwitch = in.read ();
        this.keyboardSwitch = in.read ();
        this.velocityLimitLow = in.read ();
        this.velocityLimitHigh = in.read ();
        this.noteLimitLow = in.read ();
        this.noteLimitHigh = in.read ();
        this.pitchBendRangeUpper = in.read ();
        this.pitchBendRangeLower = in.read ();
        this.velocitySenseDepth = in.read ();
        this.velocitySenseOffset = in.read ();
        this.volume = in.read ();
        this.pan = in.read ();
        this.detune = in.read ();
        this.reverbSend = in.read ();
        this.variationSend = in.read ();
        this.dryLevel = in.read ();
        this.noteShift = in.read ();

        // Currently not used... MODX has 1 Byte more than Montage!
        this.manyParameters = in.readNBytes (format == YamahaYsfcFileFormat.MONTAGE ? 265 : 266);

        this.scenes = in.readNBytes (8 * (version < 405 ? 21 : 22));

        // Assignable Knob 1-8
        for (int i = 0; i < 8; i++)
            this.assignableKnobs[i] = StreamUtils.readAscii (in, 17).trim ();

        // Control Box 1-16
        this.controlBoxes = in.readNBytes (16 * 9);
    }


    /**
     * Tries to find a common XA mode across all active elements.
     *
     * @return The common XA mode or 0 if they have different ones
     */
    public int getCommonXaMode ()
    {
        int xaMode = -1;
        for (final YamahaYsfcPartElement element: this.elements)
            if (element.getElementSwitch () > 0)
                if (xaMode == -1)
                    xaMode = element.getXaMode ();
                else if (xaMode != element.getXaMode ())
                    return 0;
        return xaMode;
    }


    /**
     * Write a performance to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream arrayOut = new ByteArrayOutputStream ();
        StreamUtils.writeAscii (arrayOut, StringUtils.rightPadSpaces (StringUtils.optimizeName (this.name, 20), 20), 21);

        arrayOut.write (this.getType ());
        arrayOut.write (this.mainCategory);
        arrayOut.write (this.subCategory);
        arrayOut.write (this.partSwitch);
        arrayOut.write (this.keyboardSwitch);
        arrayOut.write (this.velocityLimitLow);
        arrayOut.write (this.velocityLimitHigh);
        arrayOut.write (this.noteLimitLow);
        arrayOut.write (this.noteLimitHigh);
        arrayOut.write (this.pitchBendRangeUpper);
        arrayOut.write (this.pitchBendRangeLower);
        arrayOut.write (this.velocitySenseDepth);
        arrayOut.write (this.velocitySenseOffset);
        arrayOut.write (this.volume);
        arrayOut.write (this.pan);
        arrayOut.write (this.detune);
        arrayOut.write (this.reverbSend);
        arrayOut.write (this.variationSend);
        arrayOut.write (this.dryLevel);
        arrayOut.write (this.noteShift);

        // Currently not used...
        arrayOut.write (this.manyParameters);
        arrayOut.write (this.scenes);

        // Assignable Knob 1-8
        for (int i = 0; i < 8; i++)
            StreamUtils.writeAscii (arrayOut, StringUtils.rightPadSpaces (StringUtils.optimizeName (this.assignableKnobs[i], 16), 16), 17);

        // Control Box 1-16
        arrayOut.write (this.controlBoxes);

        StreamUtils.writeDataBlock (out, arrayOut.toByteArray (), true);
    }


    /**
     * Add an element to the part
     *
     * @param element The element to add
     */
    public void addElement (final YamahaYsfcPartElement element)
    {
        this.elements.add (element);
    }


    /**
     * Get all elements.
     *
     * @return The elements
     */
    public List<YamahaYsfcPartElement> getElements ()
    {
        return this.elements;
    }


    /**
     * Get the type of the part.
     *
     * @return The type
     */
    public int getType ()
    {
        return this.type;
    }


    /**
     * Converts an input value (0-255) representing a frequency deviation to cents.
     *
     * @param inputVal The input value (0-255), where 128 is 0.0 Hz deviation.
     * @param baseFreqHz The base/reference frequency in Hz (e.g., 440.0).
     * @return The frequency deviation in cents.
     */
    private static double convertToCents (final int inputVal, final double baseFreqHz)
    {
        if (inputVal < 0 || inputVal > 255)
            throw new IllegalArgumentException ("Input value must be between 0 and 255.");
        if (baseFreqHz <= 0)
            throw new IllegalArgumentException ("Base frequency must be greater than 0.");

        // Calculate frequency deviation in Hz (128 is the 0-point, each step is 0.1 Hz)
        final double deviationHz = (inputVal - 128) * 0.1;

        // Calculate absolute frequency
        final double absoluteFreq = baseFreqHz + deviationHz;

        // Calculate cents relative to base frequency
        return 1200 * (Math.log (absoluteFreq / baseFreqHz) / Math.log (2));
    }


    /**
     * Inverts the conversion: converts cents back to an integer input value (0-255).
     *
     * @param cents The frequency deviation in cents.
     * @param baseFreqHz The base/reference frequency in Hz (e.g., 440.0).
     * @return The corresponding integer input value (0-255).
     */
    private static int convertToInputValue (final double cents, final double baseFreqHz)
    {
        final double rawInput = 128 + 10 * baseFreqHz * (Math.pow (2, cents / 1200.0) - 1);
        return Math.clamp ((int) Math.round (rawInput), 0, 255);
    }
}
