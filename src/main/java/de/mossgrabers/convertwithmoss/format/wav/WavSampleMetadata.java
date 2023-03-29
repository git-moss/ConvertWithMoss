// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Metadata for a WAV sample.
 *
 * @author Jürgen Moßgraber
 */
public class WavSampleMetadata extends DefaultSampleMetadata
{
    private final WaveFile waveFile;


    /**
     * Constructor.
     *
     * @param waveFile An already read wave file
     * @throws IOException Could not read the file
     */
    public WavSampleMetadata (final WaveFile waveFile) throws IOException
    {
        this.waveFile = waveFile;
        this.readFromChunks ();
    }


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public WavSampleMetadata (final File file) throws IOException
    {
        super (file);

        try
        {
            this.waveFile = new WaveFile (file, false);
        }
        catch (final IOException | ParseException ex)
        {
            throw new IOException (ex);
        }
        this.readFromChunks ();
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     * @throws IOException Could not read the file
     */
    public WavSampleMetadata (final File zipFile, final File zipEntry) throws IOException
    {
        super (zipFile, zipEntry);

        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final String path = this.zipEntry.getPath ().replace ('\\', '/');
            final ZipEntry entry = zf.getEntry (path);
            if (entry == null)
                throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));
            try (final InputStream in = zf.getInputStream (entry))
            {
                this.waveFile = new WaveFile ();
                this.waveFile.read (in, true);
            }
            catch (final ParseException ex)
            {
                throw new IOException (ex);
            }
        }

        this.readFromChunks ();
    }


    private void readFromChunks () throws IOException
    {
        if (this.waveFile == null)
            return;

        final FormatChunk formatChunk = this.waveFile.getFormatChunk ();
        final DataChunk dataChunk = this.waveFile.getDataChunk ();

        if (formatChunk != null && dataChunk != null)
        {
            final int numberOfChannels = formatChunk.getNumberOfChannels ();
            if (numberOfChannels > 2)
                throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), this.sampleFile.getAbsolutePath ()));
            this.isMonoFile = numberOfChannels == 1;

            this.start = 0;
            try
            {
                this.stop = dataChunk.calculateLength (formatChunk);
            }
            catch (final CompressionNotSupportedException ex)
            {
                throw new IOException (ex);
            }
        }

        final SampleChunk sampleChunk = this.waveFile.getSampleChunk ();
        if (sampleChunk == null)
            return;

        this.keyRoot = sampleChunk.getMIDIUnityNote ();

        final int midiPitchFraction = sampleChunk.getMIDIPitchFraction ();
        // 0x0 (= 0 cent) to 0x80000000 (= 50 cent), tune is [-1..1], which is [-100..100] cent
        this.tune = Math.max (0, Math.min (1, midiPitchFraction * 0.5 / 0x80000000));

        sampleChunk.getLoops ().forEach (sampleLoop -> {
            final DefaultSampleLoop loop = new DefaultSampleLoop ();
            switch (sampleLoop.getType ())
            {
                default:
                case 0:
                    loop.setType (LoopType.FORWARD);
                    break;
                case 1:
                    loop.setType (LoopType.ALTERNATING);
                    break;
                case 2:
                    loop.setType (LoopType.BACKWARDS);
                    break;
            }
            loop.setStart (sampleLoop.getStart ());
            loop.setEnd (sampleLoop.getEnd ());
            this.loops.add (loop);
        });
    }


    /**
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param sample The other sample to include
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public void combine (final WavSampleMetadata sample) throws CombinationNotPossibleException
    {
        this.waveFile.combine (sample.waveFile);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.getCombinedName ().isEmpty ())
            Files.copy (this.getFile ().toPath (), outputStream);
        else
            this.waveFile.write (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addMissingInfoFromWaveFile (final boolean addRootKey, final boolean addLoops) throws IOException
    {
        super.addMissingInfoFromWaveFile (new WavSampleMetadata (this.waveFile), addRootKey, addLoops);
    }
}
