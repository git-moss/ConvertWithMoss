// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk.SampleChunkLoop;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * The data of a WAV sample file.
 *
 * @author Jürgen Moßgraber
 */
public class WavFileSampleData extends AbstractFileSampleData
{
    private final WaveFile waveFile;
    private boolean        hasWavSourceFile = true;


    /**
     * Constructor.
     *
     * @param inputStream The stream from which the file content can be read
     * @throws IOException Could not read the file
     */
    public WavFileSampleData (final InputStream inputStream) throws IOException
    {
        this.waveFile = new WaveFile ();
        try
        {
            this.waveFile.read (inputStream, true);
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }

        this.hasWavSourceFile = false;
    }


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public WavFileSampleData (final File file) throws IOException
    {
        super (file);

        try
        {
            this.waveFile = new WaveFile (file, true);
        }
        catch (final IOException | ParseException ex)
        {
            throw new IOException (ex);
        }
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     * @throws IOException Could not read the file
     */
    public WavFileSampleData (final File zipFile, final File zipEntry) throws IOException
    {
        super (zipFile, zipEntry);

        this.waveFile = new WaveFile ();

        try (final ZipFile zf = new ZipFile (this.zipFile))
        {
            final String path = this.zipEntry.getPath ().replace ('\\', '/');
            final ZipEntry entry = zf.getEntry (path);
            if (entry == null)
                throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));
            try (final InputStream in = zf.getInputStream (entry))
            {
                this.waveFile.read (in, true);
            }
            catch (final ParseException ex)
            {
                throw new IOException (ex);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        // Use the original WAV file if possible
        if (this.hasWavSourceFile)
            super.writeSample (outputStream);
        else
            this.waveFile.write (outputStream);
    }


    /**
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param sample The other sample to include
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public void combine (final WavFileSampleData sample) throws CombinationNotPossibleException
    {
        this.waveFile.combine (sample.waveFile);
    }


    /** {@inheritDoc} */
    @Override
    public void addMetadata (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        final FormatChunk formatChunk = this.waveFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        if (numberOfChannels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), this.sampleFile.getAbsolutePath ()));

        if (zone.getStart () < 0)
            zone.setStart (0);
        try
        {
            if (zone.getStop () <= 0)
                zone.setStop (this.waveFile.getDataChunk ().calculateLength (formatChunk));
        }
        catch (final CompressionNotSupportedException ex)
        {
            throw new IOException (ex);
        }

        final SampleChunk sampleChunk = this.waveFile.getSampleChunk ();
        if (sampleChunk == null)
            return;

        // Read the this.keyRoot if not set...
        if (addRootKey && zone.getKeyRoot () == -1)
            zone.setKeyRoot (sampleChunk.getMIDIUnityNote ());

        final int midiPitchFraction = sampleChunk.getMIDIPitchFraction ();
        if (zone.getTune () == 0)
            zone.setTune (Math.max (0, Math.min (0.5, midiPitchFraction * 0.5 / 0x80000000)));

        if (addLoops)
            addLoops (sampleChunk, zone.getLoops ());
    }


    private static void addLoops (final SampleChunk sampleChunk, final List<ISampleLoop> loops)
    {
        // Check if are already present
        if (!loops.isEmpty ())
            return;

        for (final SampleChunkLoop sampleLoop: sampleChunk.getLoops ())
        {
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
            loops.add (loop);
        }
    }
}
