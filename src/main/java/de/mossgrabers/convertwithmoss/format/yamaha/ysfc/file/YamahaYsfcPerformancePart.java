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
    private String                            name;
    private int                               type;
    private int                               mainCategory;
    private int                               subCategory;
    private int                               partSwitch;
    private int                               keyboardSwitch;
    private int                               velocityLimitLow    = 1;
    private int                               velocityLimitHigh   = 127;
    private int                               noteLimitLow        = 0;
    private int                               noteLimitHigh       = 127;
    private int                               pitchBendRangeUpper;
    private int                               pitchBendRangeLower;
    private int                               velocitySenseDepth  = 64;
    private int                               velocitySenseOffset = 64;
    private int                               volume              = 100;
    private int                               pan                 = 64;
    private int                               detune              = 128;
    private int                               reverbSend          = 0;
    private int                               variationSend       = 0;
    private int                               dryLevel            = 127;
    private int                               noteShift;

    private final List<YamahaYsfcPartElement> elements            = new ArrayList<> ();
    private byte []                           manyParameters;
    private byte []                           scenes;
    private final String []                   assignableKnobs     = new String [8];
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
     * Set the part to off/on.
     *
     * @param partSwitch True to enable
     */
    public void setPartSwitch (final boolean partSwitch)
    {
        this.partSwitch = partSwitch ? 1 : 0;
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
