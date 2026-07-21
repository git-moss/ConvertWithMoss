// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * The data of an AIFF sample file.
 *
 * @author Jürgen Moßgraber
 */
public class AiffFileSampleData extends AbstractFileSampleData
{
    private File     sourceFile = null;
    private AiffFile aiffFile   = null;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public AiffFileSampleData (final File file) throws IOException
    {
        super (file);

        this.fixFileEnding ();
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     * @throws IOException Could not read the file
     */
    public AiffFileSampleData (final File zipFile, final File zipEntry) throws IOException
    {
        super (zipFile, zipEntry);
    }


    private void fixFileEnding () throws IOException
    {
        if (!this.sampleFile.getName ().toLowerCase ().endsWith ("aiff"))
            return;

        // Ugly workaround for the SPI not accepting AIFF files with the ending 'aiff'
        final File tempFile = File.createTempFile ("temp", ".aif");
        tempFile.deleteOnExit ();
        Files.copy (this.sampleFile.toPath (), tempFile.toPath (), StandardCopyOption.REPLACE_EXISTING);
        this.sourceFile = this.sampleFile;
        this.sampleFile = tempFile;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.zipFile == null)
        {
            try (final InputStream inputStream = new FileInputStream (this.sampleFile))
            {
                this.readConvertWrite (inputStream, outputStream);
            }
            return;
        }

        try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream inputStream = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
        {
            this.readConvertWrite (inputStream, outputStream);
        }
    }


    private void readConvertWrite (final InputStream inputStream, final OutputStream outputStream) throws IOException
    {
        try
        {
            // The javax.sound SPI cannot read AIFC files; convert their PCM sound data directly
            // from the parsed chunks
            AiffCommonChunk commonChunk = null;
            try
            {
                commonChunk = this.getAiffFile ().getCommonChunk ();
            }
            catch (final IOException _)
            {
                // Cannot be parsed - let the SPI below try
            }
            if (commonChunk != null && commonChunk.getCompressionType () != null)
            {
                this.writeFromChunks (outputStream);
                return;
            }

            // Read the input AIFF file
            try (final InputStream in = new BufferedInputStream (inputStream); final AudioInputStream audioIn = AudioSystem.getAudioInputStream (in))
            {
                // Obtains the file types that the system can write from the audio input stream
                // specified. Check if WAV can be written
                final AudioFileFormat.Type [] supportedTypes = AudioSystem.getAudioFileTypes (audioIn);
                if (!Arrays.asList (supportedTypes).contains (AudioFileFormat.Type.AIFF))
                    throw new IOException (Functions.getMessage ("IDS_ERR_AIFF_TO_WAV_NOT_SUPPORTED"));

                // Write the output WAV file
                AudioSystem.write (audioIn, AudioFileFormat.Type.WAVE, outputStream);
            }
            catch (final UnsupportedAudioFileException ex)
            {
                final String fileEnding = this.sampleFile.getName ().toLowerCase ();
                if (fileEnding.endsWith (".aiff") || fileEnding.endsWith (".aif"))
                {
                    this.writeFromChunks (outputStream);
                    return;
                }

                throw new IOException (ex);
            }
        }
        finally
        {
            // Remove the temporary file after usage and restore the original file
            if (this.sourceFile != null)
            {
                Files.delete (this.sampleFile.toPath ());
                this.sampleFile = this.sourceFile;
                this.sourceFile = null;
            }
        }
    }


    /**
     * Write the sample as a WAV file converted directly from the parsed AIFF chunks. Used for
     * files which the javax.sound SPI cannot read, e.g. AIFC files with plain PCM sound data
     * ('sowt' marks it as little-endian, 'NONE'/'twos' as big-endian).
     *
     * @param outputStream Where to write the WAV file to
     * @throws IOException Could not convert the sound data
     */
    private void writeFromChunks (final OutputStream outputStream) throws IOException
    {
        final AiffFile aifFile = this.getAiffFile ();
        final AiffCommonChunk commonChunk = aifFile.getCommonChunk ();
        final AiffSoundDataChunk soundDataChunk = aifFile.getSoundDataChunk ();
        if (commonChunk == null || soundDataChunk == null)
            throw new IOException (Functions.getMessage ("IDS_ERR_SOURCE_FORMAT_NOT_SUPPORTED", this.filename));
        if (!commonChunk.isPCM ())
            throw new IOException (Functions.getMessage ("IDS_ERR_COMPRESSED_AIFF_FILE", this.filename, commonChunk.getCompressionName (), commonChunk.getCompressionType ()));

        // WAV stores multi-byte samples in little-endian order and 8-bit samples unsigned
        byte [] data = soundDataChunk.getSoundData ();
        final int bytesPerSample = Math.ceilDiv (commonChunk.getSampleSize (), 8);
        if (bytesPerSample == 1)
            data = convertSigned8BitToUnsigned (data);
        else if (!commonChunk.isLittleEndian ())
            data = swapToLittleEndian (data, bytesPerSample);

        final WaveFile wavFile = new WaveFile (commonChunk.getNumChannels (), commonChunk.getSampleRate (), commonChunk.getSampleSize (), (int) commonChunk.getNumSampleFrames ());
        final DataChunk dataChunk = wavFile.getDataChunk ();
        dataChunk.setData (data);
        wavFile.write (outputStream);
    }


    /**
     * Swap the byte order of all samples from big-endian to little-endian.
     *
     * @param data The sample data
     * @param bytesPerSample The number of bytes of one sample
     * @return The swapped data in a new array
     */
    private static byte [] swapToLittleEndian (final byte [] data, final int bytesPerSample)
    {
        final byte [] result = new byte [data.length];
        final int limit = data.length - bytesPerSample;
        for (int offset = 0; offset <= limit; offset += bytesPerSample)
            for (int i = 0; i < bytesPerSample; i++)
                result[offset + i] = data[offset + bytesPerSample - 1 - i];
        return result;
    }


    /**
     * Convert signed 8-bit samples (AIFF) to unsigned ones (WAV).
     *
     * @param data The sample data
     * @return The converted data in a new array
     */
    private static byte [] convertSigned8BitToUnsigned (final byte [] data)
    {
        final byte [] result = new byte [data.length];
        for (int i = 0; i < data.length; i++)
            result[i] = (byte) (data[i] + 128);
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        final AiffFile aifFile = this.getAiffFile ();
        final AiffCommonChunk commonChunk = aifFile.getCommonChunk ();
        if (commonChunk == null)
            return;

        final int numberOfChannels = commonChunk.numChannels;
        if (numberOfChannels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), this.sampleFile.getAbsolutePath ()));

        if (zone.getStart () < 0)
            zone.setStart (0);
        if (zone.getStop () <= 0)
            zone.setStop ((int) commonChunk.numSampleFrames);

        final AiffInstrumentChunk instrumentChunk = aifFile.getInstrumentChunk ();
        if (instrumentChunk == null)
            return;

        // Read the this.keyRoot if not set...
        if (addRootKey && zone.getKeyRoot () == -1)
            zone.setKeyRoot (instrumentChunk.baseNote);

        if (zone.getTuning () == 0)
            zone.setTuning (Math.clamp (instrumentChunk.detune / 100.0, -0.5, 0.5));

        if (addLoops)
            addLoops (instrumentChunk, aifFile.getMarkerChunk (), zone.getLoops ());
    }


    private static void addLoops (final AiffInstrumentChunk instrumentChunk, final AiffMarkerChunk markerChunk, final List<ISampleLoop> loops)
    {
        // Check if are already present
        if (!loops.isEmpty ())
            return;

        final AiffLoop sampleLoop = instrumentChunk.sustainLoop;
        if (sampleLoop.playMode == AiffLoop.NO_LOOPING || markerChunk == null)
            return;

        final ISampleLoop loop = new DefaultSampleLoop ();
        switch (sampleLoop.playMode)
        {
            default:
            case AiffLoop.FORWARD_LOOPING:
                loop.setType (LoopType.FORWARDS);
                break;
            case AiffLoop.FORWARD_BACKWARD_LOOPING:
                loop.setType (LoopType.ALTERNATING);
                break;
        }

        final Map<Integer, AiffMarker> markers = markerChunk.getMarkers ();
        final AiffMarker startMarker = markers.get (Integer.valueOf (sampleLoop.beginLoopMarkerID));
        final AiffMarker endMarker = markers.get (Integer.valueOf (sampleLoop.endLoopMarkerID));
        if (startMarker != null && endMarker != null)
        {
            loop.setStart ((int) startMarker.position);
            loop.setEnd ((int) endMarker.position);
            loops.add (loop);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        // The javax.sound SPI cannot read AIFC files; provide their metadata from the parsed
        // chunks instead
        AiffCommonChunk commonChunk = null;
        try
        {
            commonChunk = this.getAiffFile ().getCommonChunk ();
        }
        catch (final IOException _)
        {
            // Cannot be parsed - let the SPI try
        }
        if (commonChunk == null || commonChunk.getCompressionType () == null)
        {
            super.createAudioMetadata ();
            return;
        }

        if (!commonChunk.isPCM ())
            throw new IOException (Functions.getMessage ("IDS_ERR_COMPRESSED_AIFF_FILE", this.filename, commonChunk.getCompressionName (), commonChunk.getCompressionType ()));
        this.audioMetadata = new DefaultAudioMetadata (commonChunk.getNumChannels (), commonChunk.getSampleRate (), commonChunk.getSampleSize (), (int) commonChunk.getNumSampleFrames ());
    }


    /**
     * Get the underlying AIFF file.
     *
     * @return The file
     * @throws IOException Could not parse the file
     */
    public AiffFile getAiffFile () throws IOException
    {
        if (this.aiffFile != null)
            return this.aiffFile;

        if (this.zipFile == null)
            this.aiffFile = new AiffFile (this.sampleFile);
        else
        {
            this.aiffFile = new AiffFile ();
            try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
            {
                this.aiffFile.read (in);
            }
        }

        return this.aiffFile;
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        final AiffFile aifFile;
        try
        {
            aifFile = this.getAiffFile ();
        }
        catch (final IOException _)
        {
            return;
        }

        final Map<String, String> aiffMetadata = aifFile.getMetadata ();

        final String author = aiffMetadata.get (AiffFile.AIFF_CHUNK_AUTHOR);
        if (author != null)
            metadata.setCreator (author);

        final StringBuilder sb = new StringBuilder ();

        final String copyright = aiffMetadata.get (AiffFile.AIFF_CHUNK_COPYRIGHT);
        if (copyright != null)
            sb.append (copyright).append ('\n');
        final String annotation = aiffMetadata.get (AiffFile.AIFF_CHUNK_ANNOTATION);
        if (annotation != null)
            sb.append (annotation).append ('\n');

        int i = 1;
        while (true)
        {
            final String comment = aiffMetadata.get ("Comment" + i);
            if (comment == null)
                break;
            sb.append (comment).append ('\n');
            i++;
        }

        final String description = sb.toString ().trim ();
        if (!description.isEmpty ())
            metadata.setDescription (description);
    }
}
