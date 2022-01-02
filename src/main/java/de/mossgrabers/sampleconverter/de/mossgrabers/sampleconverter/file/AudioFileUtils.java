// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.File;
import java.io.IOException;


/**
 * Audio file utility functions.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public final class AudioFileUtils
{
    /**
     * Private due to helper class.
     */
    private AudioFileUtils ()
    {
        // Intentionally empty
    }


    /**
     * Get the number of samples of an audio file.
     *
     * @param audioFile The audio file from which to get its' length in samples
     * @return The duration or 0 if it could not be retrieved
     * @throws IOException Could not read or access the file
     */
    public static long getLength (final File audioFile) throws IOException
    {
        try
        {
            final AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat (audioFile);
            return getLength (audioFileFormat);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException ("Could not retrieve audio file format.", ex);
        }
    }


    /**
     * Get the length of an audio file from its' audio file format.
     *
     * @param audioFileFormat The audio file format
     * @return The length or 0 if it could not be retrieved
     */
    public static long getLength (final AudioFileFormat audioFileFormat)
    {
        final AudioFormat format = audioFileFormat.getFormat ();
        if (format.getEncoding () != Encoding.PCM_SIGNED)
            return 0;

        final int frameLength = audioFileFormat.getFrameLength ();

        // Make sure there is a frame length
        if (frameLength == AudioSystem.NOT_SPECIFIED)
            return 0;

        return frameLength;
    }
}
