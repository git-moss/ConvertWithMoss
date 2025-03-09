// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.BroadcastAudioExtensionChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk;
import de.mossgrabers.convertwithmoss.file.wav.SampleChunk.SampleChunkLoop;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * The data of a WAV sample file.
 *
 * @author Jürgen Moßgraber
 */
public class WavFileSampleData extends AbstractFileSampleData
{
    private WaveFile waveFile         = null;
    private boolean  hasWavSourceFile = true;


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
            final FormatChunk formatChunk = this.waveFile.getFormatChunk ();
            this.audioMetadata = new DefaultAudioMetadata (formatChunk.getNumberOfChannels (), formatChunk.getSampleRate (), formatChunk.getSignificantBitsPerSample (), this.waveFile.getDataChunk ().calculateLength (formatChunk));
        }
        catch (final ParseException | CompressionNotSupportedException ex)
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
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        // Use the original WAV file if possible
        final WaveFile sourceFile = this.getWaveFile ();
        if (this.hasWavSourceFile && !sourceFile.doesRequireRewrite ())
            super.writeSample (outputStream);
        else
            sourceFile.write (outputStream);
    }


    /**
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param sample The other sample to include
     * @return The stereo sample data
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public ISampleData combine (final WavFileSampleData sample) throws CombinationNotPossibleException
    {
        try
        {
            final WaveFile wf = this.getWaveFile ();
            final byte [] stereoData = wf.combine (sample.getWaveFile ());
            final FormatChunk formatChunk = wf.getFormatChunk ();
            final int frames = formatChunk.calculateLength (wf.getDataChunk ().getData ());
            final IAudioMetadata am = new DefaultAudioMetadata (2, formatChunk.getSampleRate (), formatChunk.getSignificantBitsPerSample (), frames);
            return new InMemorySampleData (am, stereoData);
        }
        catch (final IOException ex)
        {
            throw new CombinationNotPossibleException (this.filename);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        final WaveFile wavFile = this.getWaveFile ();
        final FormatChunk formatChunk = wavFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        if (numberOfChannels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), this.sampleFile.getAbsolutePath ()));

        if (zone.getStart () < 0)
            zone.setStart (0);
        try
        {
            if (zone.getStop () <= 0)
                zone.setStop (wavFile.getDataChunk ().calculateLength (formatChunk));
        }
        catch (final CompressionNotSupportedException ex)
        {
            throw new IOException (ex);
        }

        final SampleChunk sampleChunk = wavFile.getSampleChunk ();
        if (sampleChunk == null)
            return;

        // Read the this.keyRoot if not set...
        if (addRootKey && zone.getKeyRoot () == -1)
            zone.setKeyRoot (sampleChunk.getMIDIUnityNote ());

        if (zone.getTune () == 0)
        {
            final double tune = Math.clamp (sampleChunk.getMIDIPitchFractionAsCents () / 100.0, -0.5, 0.5);
            zone.setTune (tune);
            // Root note needs to be updated as well!
            if (tune < 0)
                zone.setKeyRoot (sampleChunk.getMIDIUnityNote ());
        }

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
                case SampleChunk.LOOP_FORWARD:
                    loop.setType (LoopType.FORWARDS);
                    break;
                case SampleChunk.LOOP_ALTERNATING:
                    loop.setType (LoopType.ALTERNATING);
                    break;
                case SampleChunk.LOOP_BACKWARDS:
                    loop.setType (LoopType.BACKWARDS);
                    break;
            }
            loop.setStart (sampleLoop.getStart ());
            loop.setEnd (sampleLoop.getEnd ());
            loops.add (loop);
        }
    }


    /**
     * Get the underlying WAV file.
     *
     * @return The wave file
     * @throws IOException Could not read the file
     */
    public WaveFile getWaveFile () throws IOException
    {
        if (this.waveFile == null)
            if (this.zipFile == null)
                try
                {
                    this.waveFile = new WaveFile (this.sampleFile, true);
                }
                catch (final ParseException ex)
                {
                    throw new IOException (ex);
                }
            else
            {
                this.waveFile = new WaveFile ();

                try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
                {
                    this.waveFile.read (in, true);
                }
                catch (final ParseException | RuntimeException ex)
                {
                    throw new IOException (ex);
                }
            }

        return this.waveFile;
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        final WaveFile wavFile;
        try
        {
            wavFile = this.getWaveFile ();
        }
        catch (final IOException ex)
        {
            return;
        }

        final StringBuilder sb = new StringBuilder (wavFile.formatInfoFields ());

        final BroadcastAudioExtensionChunk broadcastAudioExtensionChunk = wavFile.getBroadcastAudioExtensionChunk ();
        if (broadcastAudioExtensionChunk != null)
        {
            final String originator = broadcastAudioExtensionChunk.getOriginator ();
            if (!originator.isBlank ())
                metadata.setCreator (originator);
            final String description = broadcastAudioExtensionChunk.getDescription ().trim ();
            if (!description.isBlank ())
            {
                if (sb.length () > 0)
                    sb.append ('\n');
                sb.append (description);
            }
            metadata.setCreationDateTime (broadcastAudioExtensionChunk.getOriginationDateTime ());
        }

        if (sb.length () > 0)
            metadata.setDescription (sb.toString ());
    }
}
