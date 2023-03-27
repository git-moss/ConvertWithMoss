// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.tools.ui.Functions;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;


/**
 * Metadata for a AIFF sample. Converts the output to a WAV file when writing!
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class AiffSampleMetadata extends DefaultSampleMetadata
{
    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public AiffSampleMetadata (final File file) throws IOException
    {
        super (file);

        // Check for AIFF support
        final AudioFileFormat.Type [] types = AudioSystem.getAudioFileTypes ();
        if (!Arrays.asList (types).contains (AudioFileFormat.Type.AIFF))
            throw new IOException (Functions.getMessage ("IDS_ERR_AIFF_TO_WAV_NOT_SUPPORTED"));

        final AudioInputStream audioInputStream;
        try
        {
            audioInputStream = AudioSystem.getAudioInputStream (this.sampleFile);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }

        final AudioFormat audioFormat = audioInputStream.getFormat ();
        final int channels = audioFormat.getChannels ();
        if (channels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (channels), this.sampleFile.getAbsolutePath ()));
        this.isMonoFile = channels == 1;
        this.start = 0;
        this.stop = (int) audioInputStream.getFrameLength ();

        // Change filename ending from AIFF to WAV
        this.setCombinedName (this.filename.replace (".aiff", ".wav").replace (".aif", ".wav").replace (".AIFF", ".wav").replace (".AIF", ".wav"));
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        // Read the input AIFF file
        try (final AudioInputStream in = AudioSystem.getAudioInputStream (new BufferedInputStream (new FileInputStream (this.sampleFile))))
        {
            // Obtains the file types that the system can write from the audio input stream
            // specified. Check if WAV can be written
            final AudioFileFormat.Type [] supportedTypes = AudioSystem.getAudioFileTypes (in);
            if (!Arrays.asList (supportedTypes).contains (AudioFileFormat.Type.AIFF))
                throw new IOException (Functions.getMessage ("IDS_ERR_AIFF_TO_WAV_NOT_SUPPORTED"));

            // Write the output WAV file
            AudioSystem.write (in, AudioFileFormat.Type.WAVE, outputStream);
        }
        catch (final UnsupportedAudioFileException ex)
        {
            throw new IOException (ex);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addMissingInfoFromWaveFile (final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // Info not available in AIFF
    }
}
