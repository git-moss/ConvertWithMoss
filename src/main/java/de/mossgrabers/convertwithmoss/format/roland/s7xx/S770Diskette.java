// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

import java.io.IOException;
import java.io.InputStream;


/**
 * Parser for a Roland S-770 3.5″ HD floppy-diskette image.
 *
 * @author Jürgen Moßgraber
 */
public class S770Diskette
{
    private final S770Header                idArea;
    private final S770DisketteDirectoryArea directoryArea;
    private final S770DisketteParameterArea parameterArea;


    /**
     * Parses a Roland S-770 diskette image from an already-open {@link InputStream}. The stream
     * must be positioned after the header area (0x200).
     *
     * @param in Stream positioned at 0x200
     * @param header The already read header
     * @throws IOException if the stream cannot be read
     */
    public S770Diskette (final InputStream in, final S770Header header) throws IOException
    {
        this.idArea = header;
        this.directoryArea = this.readDirectoryArea (in);
        this.parameterArea = this.readParameterArea (in);
    }


    /**
     * Reads the compact directory area.
     * 
     * @param input The input stream to read from
     * @return The read directory
     * @throws IOException Could not read the directory
     */
    private S770DisketteDirectoryArea readDirectoryArea (final InputStream input) throws IOException
    {
        return new S770DisketteDirectoryArea (input, this.idArea.getNumPerformances (), this.idArea.getNumPatches (), this.idArea.getNumPartials (), this.idArea.getNumSamples ());
    }


    /**
     * Reads the parameter area.
     * 
     * @param in The parameter area
     * @return The read parameter area
     * @throws IOException Could not read the parameters
     */
    private S770DisketteParameterArea readParameterArea (final InputStream in) throws IOException
    {
        return new S770DisketteParameterArea (in, this.idArea.getNumVolumes (), this.idArea.getNumPerformances (), this.idArea.getNumPatches (), this.idArea.getNumPartials (), this.idArea.getNumSamples ());
    }


    /**
     * Get the diskette directories.
     * 
     * @return The compact directory area
     */
    public S770DisketteDirectoryArea getDirectoryArea ()
    {
        return this.directoryArea;
    }


    /**
     * Get the parameter area.
     * 
     * @return The compact diskette parameter area.
     */
    public S770DisketteParameterArea getParameterArea ()
    {
        return this.parameterArea;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        return "RolandS770Diskette {\n\n" + this.idArea + "\n\n" + this.directoryArea + "\n\n" + this.parameterArea + "\n}";
    }
}