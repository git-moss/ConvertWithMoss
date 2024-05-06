// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.RIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Read/write WAV files.
 *
 * @author Jürgen Moßgraber
 */
public class WaveFile extends AbstractRIFFFile
{
    FormatChunk                  formatChunk;
    DataChunk                    dataChunk;
    BroadcastAudioExtensionChunk broadcastAudioExtensionChunk = null;
    InstrumentChunk              instrumentChunk              = null;
    SampleChunk                  sampleChunk                  = null;


    /**
     * Constructor. Creates a new file in memory.
     *
     * @param audioMetadata The format of the audio
     */
    public WaveFile (final IAudioMetadata audioMetadata)
    {
        this (audioMetadata.getChannels (), audioMetadata.getSampleRate (), audioMetadata.getBitResolution (), audioMetadata.getNumberOfSamples ());
    }


    /**
     * Constructor. Creates a new file in memory.
     *
     * @param numberOfChannels The number of channels of the sample
     * @param sampleRate The sample rate (in Hz)
     * @param bitsPerSample The resolution the sample in bits
     * @param lengthInSamples The number of samples of the wave
     */
    public WaveFile (final int numberOfChannels, final int sampleRate, final int bitsPerSample, final int lengthInSamples)
    {
        this ();

        this.formatChunk = new FormatChunk (numberOfChannels, sampleRate, bitsPerSample, true);
        this.dataChunk = new DataChunk (this.formatChunk, lengthInSamples);
    }


    /**
     * Constructor. Reads the given WAV file.
     *
     * @param wavFile The WAV file
     * @param ignoreChunkErrors Ignores unknown or missing chunk errors if true
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public WaveFile (final File wavFile, final boolean ignoreChunkErrors) throws IOException, ParseException
    {
        this ();

        try (final FileInputStream stream = new FileInputStream (wavFile))
        {
            this.read (stream, ignoreChunkErrors);
        }
    }


    /**
     * Constructor. Use in combination with the read-method to read a WAV file from a stream.
     */
    public WaveFile ()
    {
        super (RiffID.WAVE_ID);
    }


    /**
     * Reads a WAV file from a stream.
     *
     * @param inputStream The input stream which provides the WAV file
     * @param ignoreChunkErrors Ignores unknown or missing chunk errors if true
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public void read (final InputStream inputStream, final boolean ignoreChunkErrors) throws IOException, ParseException
    {
        final RIFFParser riffParser = new RIFFParser ();
        riffParser.declareGroupChunk (RiffID.INFO_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.parse (inputStream, this, ignoreChunkErrors);

        if (ignoreChunkErrors)
            return;
        if (this.formatChunk == null)
            throw new ParseException ("No format chunk found in WAV file.");
        if (this.dataChunk == null)
            throw new ParseException ("No data chunk found in WAV file.");
    }


    /**
     * Get the broadcast audio extension chunk if present in the WAV file.
     *
     * @return The broadcast audio extension chunk or null if not present
     */
    public BroadcastAudioExtensionChunk getBroadcastAudioExtensionChunk ()
    {
        return this.broadcastAudioExtensionChunk;
    }


    /**
     * Set the broadcast audio extension chunk.
     *
     * @param broadcastAudioExtensionChunk The instrument chunk
     */
    public void setBroadcastAudioExtensionChunk (final BroadcastAudioExtensionChunk broadcastAudioExtensionChunk)
    {
        if (this.broadcastAudioExtensionChunk != null)
            this.chunkStack.remove (this.broadcastAudioExtensionChunk);
        this.broadcastAudioExtensionChunk = broadcastAudioExtensionChunk;
        if (this.broadcastAudioExtensionChunk != null)
            this.chunkStack.add (this.broadcastAudioExtensionChunk);
    }


    /**
     * Get the instrument chunk if present in the WAV file.
     *
     * @return The instrument chunk or null if not present
     */
    public InstrumentChunk getInstrumentChunk ()
    {
        return this.instrumentChunk;
    }


    /**
     * Set the instrument chunk.
     *
     * @param instrumentChunk The instrument chunk
     */
    public void setInstrumentChunk (final InstrumentChunk instrumentChunk)
    {
        if (this.instrumentChunk != null)
            this.chunkStack.remove (this.instrumentChunk);
        this.instrumentChunk = instrumentChunk;
        if (this.instrumentChunk != null)
            this.chunkStack.add (this.instrumentChunk);
    }


    /**
     * Get the sample chunk if present in the WAV file.
     *
     * @return The sample chunk or null if not present
     */
    public SampleChunk getSampleChunk ()
    {
        return this.sampleChunk;
    }


    /**
     * Set the sample chunk.
     *
     * @param sampleChunk The sample chunk
     */
    public void setSampleChunk (final SampleChunk sampleChunk)
    {
        if (this.sampleChunk != null)
            this.chunkStack.remove (this.sampleChunk);
        this.sampleChunk = sampleChunk;
        if (this.sampleChunk != null)
            this.chunkStack.add (this.sampleChunk);
    }


    /**
     * Get the format chunk if present in the WAV file.
     *
     * @return The format chunk or null if not present
     */
    public FormatChunk getFormatChunk ()
    {
        return this.formatChunk;
    }


    /**
     * Get the data chunk if present in the WAV file.
     *
     * @return The data chunk or null if not present
     */
    public DataChunk getDataChunk ()
    {
        return this.dataChunk;
    }


    /**
     * Combines two mono files into a stereo file. Format and sample chunks must be identical.
     *
     * @param otherWave The other sample to include
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public void combine (final WaveFile otherWave) throws CombinationNotPossibleException
    {
        final FormatChunk otherFormat = otherWave.getFormatChunk ();
        if (!Arrays.equals (this.formatChunk.getData (), otherFormat.getData ()))
            throw new CombinationNotPossibleException ("Format chunks are not identical.");

        final SampleChunk otherSample = otherWave.getSampleChunk ();
        if (!this.compareSampleChunk (otherSample))
            throw new CombinationNotPossibleException ("Sample chunks are not identical.");

        final int numberOfChannels = this.formatChunk.getNumberOfChannels ();
        if (numberOfChannels != 1)
            throw new CombinationNotPossibleException ("Can only combine mono files.");

        this.formatChunk.setNumberOfChannels (2);

        // Interleave left and right channel
        final byte [] leftData = this.dataChunk.getData ();
        final byte [] rightData = otherWave.dataChunk.getData ();

        final int length = Math.max (leftData.length, rightData.length);
        final byte [] combinedData = new byte [length * 2];
        final int blockSize = this.formatChunk.getSignicantBitsPerSample () / 8;
        for (int count = 0; count < length; count += blockSize)
        {
            if (count < leftData.length)
                System.arraycopy (leftData, count, combinedData, count * 2, Math.min (blockSize, leftData.length - count));
            if (count < rightData.length)
                System.arraycopy (rightData, count, combinedData, count * 2 + blockSize, Math.min (blockSize, rightData.length - count));
        }
        this.dataChunk.setData (combinedData);
    }


    private boolean compareSampleChunk (final SampleChunk otherSample)
    {
        if (this.sampleChunk == null && otherSample == null)
            return true;
        if (this.sampleChunk == null || otherSample == null)
            return false;
        return this.sampleChunk.getMIDIUnityNote () == otherSample.getMIDIUnityNote () && this.sampleChunk.getMIDIPitchFraction () == otherSample.getMIDIPitchFraction ();
    }


    /** {@inheritDoc} */
    @Override
    public void visitChunk (final RIFFChunk group, final RIFFChunk chunk) throws ParseException
    {
        if (group.getType () == RiffID.INFO_ID.getId () && group.getId () == RiffID.LIST_ID.getId ())
        {
            if (chunk.getType () == RiffID.INFO_ID.getId ())
            {
                if (this.infoChunk == null)
                {
                    this.infoChunk = new InfoChunk ();
                    this.chunkStack.add (this.infoChunk);
                }
                this.infoChunk.add (chunk);
            }
            return;
        }

        final RiffID riffID = RiffID.fromId (chunk.getId ());
        switch (riffID)
        {
            case BEXT_ID:
                this.broadcastAudioExtensionChunk = new BroadcastAudioExtensionChunk (chunk);
                this.chunkStack.add (this.broadcastAudioExtensionChunk);
                break;

            case INST_ID:
                this.instrumentChunk = new InstrumentChunk (chunk);
                this.chunkStack.add (this.instrumentChunk);
                break;

            case FMT_ID:
                this.formatChunk = new FormatChunk (chunk);
                this.chunkStack.add (this.formatChunk);
                break;

            case DATA_ID:
                this.dataChunk = new DataChunk (chunk);
                this.chunkStack.add (this.dataChunk);
                break;

            case SMPL_ID:
                this.sampleChunk = new SampleChunk (chunk);
                this.chunkStack.add (this.sampleChunk);
                break;

            // Can safely be ignored
            case FILLER_ID:
            default:
                this.chunkStack.add (new UnknownChunk (riffID, chunk));
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void fillChunkStack ()
    {
        if (!this.chunkStack.isEmpty ())
            return;

        this.chunkStack.add (this.formatChunk);
        if (this.broadcastAudioExtensionChunk != null)
            this.chunkStack.add (this.broadcastAudioExtensionChunk);
        if (this.infoChunk != null)
            this.chunkStack.add (this.infoChunk);
        this.chunkStack.add (this.dataChunk);
        if (this.instrumentChunk != null)
            this.chunkStack.add (this.instrumentChunk);
        if (this.sampleChunk != null)
            this.chunkStack.add (this.sampleChunk);
    }
}
