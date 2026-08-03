// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

/**
 * The audio file formats which the sample file creator can write.
 *
 * @author Jürgen Moßgraber
 */
public enum SampleFileFormat
{
    /** Write WAV files. */
    WAV ("WAV", ".wav"),
    /** Write AIFF files. */
    AIFF ("AIFF", ".aif"),
    /** Write CAF files with linear PCM audio data. */
    CAF ("CAF", ".caf"),
    /** Write CAF files with Apple Lossless compressed audio data. */
    CAF_ALAC ("CAF-ALAC", ".caf"),
    /** Write FLAC files. */
    FLAC ("FLAC", ".flac");


    private final String name;
    private final String ending;


    /**
     * Constructor.
     *
     * @param name The name to display for the format
     * @param ending The file ending to use for the format
     */
    private SampleFileFormat (final String name, final String ending)
    {
        this.name = name;
        this.ending = ending;
    }


    /**
     * Get the name of the format.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the file ending of the format.
     *
     * @return The ending including the dot
     */
    public String getEnding ()
    {
        return this.ending;
    }


    /**
     * Look up the format by its name.
     *
     * @param name The name of the format, case does not matter
     * @return The format or null if none matches
     */
    public static SampleFileFormat getByName (final String name)
    {
        for (final SampleFileFormat format: values ())
            if (format.name.equalsIgnoreCase (name))
                return format;
        return null;
    }
}
