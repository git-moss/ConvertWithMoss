// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

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
    private int                               velocityLimitLow;
    private int                               velocityLimitHigh;
    private int                               noteLimitLow;
    private int                               noteLimitHigh;
    private int                               pitchBendRangeUpper;
    private int                               pitchBendRangeLower;

    private byte []                           theRest;

    private final List<YamahaYsfcPartElement> elements = new ArrayList<> ();


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPerformancePart (final InputStream in, final YamahaYsfcFileFormat version) throws IOException
    {
        this.read (in, version);
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
     * Read a performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in, final YamahaYsfcFileFormat version) throws IOException
    {
        this.name = StreamUtils.readASCII (in, 21).trim ();
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

        // Currently not used...
        // MODX has 1 Byte more than Montage! Needs to be considered in case that Scenes are used!
        this.theRest = in.readAllBytes ();
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
        {
            if (element.getElementSwitch () > 0)
                if (xaMode == -1)
                    xaMode = element.getXaMode ();
                else if (xaMode != element.getXaMode ())
                    return 0;
        }
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
        StreamUtils.writeASCII (arrayOut, StringUtils.rightPadSpaces (StringUtils.optimizeName (this.name, 20), 20), 21);

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

        // Currently not used...
        arrayOut.write (this.theRest);

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
}
