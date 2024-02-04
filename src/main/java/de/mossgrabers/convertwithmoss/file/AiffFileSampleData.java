// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.tools.ui.Functions;

import javax.sound.sampled.AudioFileFormat;
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
 * The data of an AIFF sample file.
 *
 * @author Jürgen Moßgraber
 */
public class AiffFileSampleData extends AbstractFileSampleData
{
    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public AiffFileSampleData (final File file) throws IOException
    {
        super (file);
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
    public void addMetadata (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        if (zone.getStart () < 0)
            zone.setStart (0);
        if (zone.getStop () <= 0)
            zone.setStop (this.getAudioMetadata ().getNumberOfSamples ());

        // More info not available in AIFF
    }
}
