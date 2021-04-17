// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.wav;

import de.mossgrabers.sampleconverter.core.AbstractSampleMetadata;
import de.mossgrabers.sampleconverter.core.LoopType;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.wav.DataChunk;
import de.mossgrabers.sampleconverter.file.wav.FormatChunk;
import de.mossgrabers.sampleconverter.file.wav.SampleChunk;
import de.mossgrabers.sampleconverter.file.wav.WaveFile;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;


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

        this.waveFile = new WaveFile (file, false);
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
            return;

        this.keyRoot = sampleChunk.getMIDIUnityNote ();

        final int midiPitchFraction = sampleChunk.getMIDIPitchFraction ();
        // 0x0 (= 0 cent) to 0x80000000 (= 50 cent), tune is [-1..1], which is [-100..100] cent
        this.tune = Math.max (0, Math.min (1, midiPitchFraction * 0.5 / 0x80000000));

        sampleChunk.getLoops ().forEach (sampleLoop -> {
            final SampleLoop loop = new SampleLoop ();
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
}
