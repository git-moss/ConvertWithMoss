// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector.wav;

import de.mossgrabers.sampleconverter.core.AbstractSampleMetadata;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.DataChunk;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.SampleChunk;
import de.mossgrabers.sampleconverter.file.wav.SampleChunk.SampleLoop;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


/**
 * Metadata for a WAV sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WavSampleMetadata extends AbstractSampleMetadata
{
    private final WaveFile waveFile;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws ParseException Could not parse the file
     * @throws IOException Could not read the file
     * @throws CompressionNotSupportedException The wave file is compressed, which is not supported
     */
    public WavSampleMetadata (final File file) throws IOException, ParseException, CompressionNotSupportedException
    {
        super (file);

        this.waveFile = new WaveFile (file);
        final FormatChunk formatChunk = this.waveFile.getFormatChunk ();

        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        if (numberOfChannels > 2)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), file.getAbsolutePath ()));
        this.isMonoFile = numberOfChannels == 1;

        final DataChunk dataChunk = this.waveFile.getDataChunk ();

        this.start = 0;
        this.stop = dataChunk.calculateLength (formatChunk);

        final SampleChunk sampleChunk = this.waveFile.getSampleChunk ();
        if (sampleChunk == null)
        {
            this.hasLoop = false;
            this.loopStart = 0;
            this.loopEnd = this.stop;
            return;
        }

        this.keyRoot = sampleChunk.getMIDIUnityNote ();

        this.hasLoop = sampleChunk.getNumSampleLoops () > 0;
        final List<SampleLoop> loops = sampleChunk.getLoops ();
        if (loops.isEmpty ())
        {
            this.loopStart = 0;
            this.loopEnd = this.stop;
            return;
        }

        final SampleLoop sampleLoop = loops.get (0);
        this.loopStart = sampleLoop.getStart ();
        this.loopEnd = sampleLoop.getEnd ();
    }


    /** {@inheritDoc} */
    @Override
    public void combine (final ISampleMetadata sample) throws CombinationNotPossibleException
    {
        if (!(sample instanceof WavSampleMetadata))
            throw new CombinationNotPossibleException (Functions.getMessage ("IDS_NOTIFY_ERR_ONLY_WAV"));

        this.waveFile.combine (((WavSampleMetadata) sample).waveFile);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.getCombinedName ().isEmpty ())
        {
            final byte [] buffer = new byte [10000];
            try (final FileInputStream fis = new FileInputStream (this.getFile ()))
            {
                int length;
                while ((length = fis.read (buffer)) > 0)
                    outputStream.write (buffer, 0, length);
            }
        }
        else
            this.waveFile.write (outputStream);
    }
}
