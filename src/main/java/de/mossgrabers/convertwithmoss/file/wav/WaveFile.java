// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RIFFVisitor;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * Read/write WAV files.
 *
 * @author Jürgen Moßgraber
 */
public class WaveFile extends AbstractRIFFFile
{
    private static final Collection<Class<? extends RiffChunkId>> WAVE_RIFF_CHUNK_IDS = new ArrayList<> ();
    static
    {
        Collections.addAll (WAVE_RIFF_CHUNK_IDS, CommonRiffChunkId.class, InfoRiffChunkId.class, WaveRiffChunkId.class);
    }

    /** The time over which the end of the audio of a truncated file is faded out. */
    private static final double  FADE_OUT_SECONDS             = 0.01;

    FormatChunk                  formatChunk;
    DataChunk                    dataChunk;
    BroadcastAudioExtensionChunk broadcastAudioExtensionChunk = null;
    InstrumentChunk              instrumentChunk              = null;
    SampleChunk                  sampleChunk                  = null;

    private boolean              requiresRewrite              = true;
    private int                  fadeOutStart                 = -1;


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
     * Constructor. Creates a new file in memory.
     *
     * @param numberOfChannels The number of channels of the sample
     * @param sampleRate The sample rate (in Hz)
     * @param bitsPerSample The resolution the sample in bits
     * @param wavData The sample byte array in little-endian order
     */
    public WaveFile (final int numberOfChannels, final int sampleRate, final int bitsPerSample, final byte [] wavData)
    {
        this ();

        this.formatChunk = new FormatChunk (numberOfChannels, sampleRate, bitsPerSample, true);
        this.dataChunk = new DataChunk (this.formatChunk, wavData);
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
     * Constructor.
     *
     * @param formatChunk The format chunk
     * @param dataChunk The data chunk
     */
    public WaveFile (final FormatChunk formatChunk, final DataChunk dataChunk)
    {
        this ();

        this.formatChunk = formatChunk;
        this.dataChunk = dataChunk;
        this.fillChunkStack ();
    }


    /**
     * Constructor. Use in combination with the read-method to read a WAV file from a stream.
     */
    public WaveFile ()
    {
        super (WaveRiffChunkId.WAVE_ID, true);
    }


    /**
     * Get the position of the start of the data of the data chunk in the wave file.
     *
     * @param wavFile The wave file
     * @return The position of the data chunk or -1 if no data chunk is present
     * @throws IOException Could not read the file
     */
    public static long getPositionOfDataChunkData (final File wavFile) throws IOException
    {
        final RIFFParser riffParser = new RIFFParser (WAVE_RIFF_CHUNK_IDS);
        riffParser.declareGroupChunk (InfoRiffChunkId.INFO_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        try (final InputStream inputStream = new FileInputStream (wavFile))
        {
            final DataChunkPositionRIFFVisitor callback = new DataChunkPositionRIFFVisitor (riffParser);
            try
            {
                riffParser.parse (inputStream, callback);
            }
            catch (final ParseException ex)
            {
                throw new IOException (ex);
            }

            return callback.dataChunkPosition;
        }
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
        final RIFFParser riffParser = new RIFFParser (WAVE_RIFF_CHUNK_IDS);
        riffParser.setIgnoreChunkErrors (ignoreChunkErrors);
        riffParser.setIgnoreUnknownChunks (ignoreChunkErrors);
        riffParser.declareGroupChunk (InfoRiffChunkId.INFO_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.parse (inputStream, this);

        // Workaround for broken(?) WAV files which have the wave data after(!) the data chunk
        if (this.formatChunk != null && this.dataChunk != null && this.dataChunk.getDataSize () == 0 && inputStream.available () > 0)
            this.dataChunk.setData (inputStream.readAllBytes ());
        // A file which ends before its wave data does is written anew as well, with the audio
        // which is present
        else if (this.isTruncated ())
            this.repairTruncatedData ();
        else
            this.requiresRewrite = false;

        if (ignoreChunkErrors)
            return;
        if (this.formatChunk == null)
            throw new ParseException ("No format chunk found in WAV file.");
        if (this.dataChunk == null)
            throw new ParseException ("No data chunk found in WAV file.");
    }


    /**
     * Returns true if the original source file (if any) needs to be rewritten since changes did
     * happen to it.
     *
     * @return True if rewrite is necessary
     */
    public boolean doesRequireRewrite ()
    {
        return this.requiresRewrite;
    }


    /**
     * Check if the file ends before its wave data does, e.g. after an interrupted copy. The audio
     * which is present is kept, but faded out at its end, see {@link #getFadeOutStart()}.
     *
     * @return True if wave data is missing
     */
    public boolean isTruncated ()
    {
        return this.dataChunk != null && this.dataChunk.getRawChunk ().isTruncated ();
    }


    /**
     * Get the number of sample frames which the data chunk states. A truncated file contains fewer,
     * see {@link #isTruncated()}. Requires the format and the data chunk.
     *
     * @return The number of sample frames
     */
    public int getStatedNumberOfFrames ()
    {
        return (int) (this.dataChunk.getRawChunk ().getStatedSize () / this.formatChunk.calculateBytesPerSample ());
    }


    /**
     * Get the first sample frame which is faded out at the end of the audio of a truncated file.
     * The audio in front of it is the original one.
     *
     * @return The index of the frame or -1 if the file is not truncated
     */
    public int getFadeOutStart ()
    {
        return this.isTruncated () ? this.fadeOutStart : -1;
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
        this.broadcastAudioExtensionChunk = broadcastAudioExtensionChunk;
        this.chunkStack.clear ();
        this.fillChunkStack ();
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
        this.instrumentChunk = instrumentChunk;
        this.chunkStack.clear ();
        this.fillChunkStack ();
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
        this.sampleChunk = sampleChunk;
        this.chunkStack.clear ();
        this.fillChunkStack ();
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
     * Get the data chunk.
     *
     * @param dataChunk The data chunk
     */
    public void setDataChunk (final DataChunk dataChunk)
    {
        this.dataChunk = dataChunk;
        this.chunkStack.clear ();
        this.fillChunkStack ();
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
     * @return The interleaved stereo data
     * @throws CombinationNotPossibleException Could not combine the wave files
     */
    public byte [] combine (final WaveFile otherWave) throws CombinationNotPossibleException
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

        return interleaveChannels (this.dataChunk.getData (), otherWave.dataChunk.getData (), this.formatChunk.getSignificantBitsPerSample ());
    }


    /**
     * Interleave left and right channel.
     *
     * @param leftData The data array of the left channel
     * @param rightData The data array of the right channel
     * @param bitsPerSample The bits per sample, e.g. 8, 16, 24, ...
     * @return The interleaved array (1 sample left, 1 sample right)
     */
    public static byte [] interleaveChannels (final byte [] leftData, final byte [] rightData, final int bitsPerSample)
    {
        final int length = Math.max (leftData.length, rightData.length);
        final byte [] combinedData = new byte [length * 2];
        final int blockSize = bitsPerSample / 8;
        for (int count = 0; count < length; count += blockSize)
        {
            if (count < leftData.length)
                System.arraycopy (leftData, count, combinedData, count * 2, Math.min (blockSize, leftData.length - count));
            if (count < rightData.length)
                System.arraycopy (rightData, count, combinedData, count * 2 + blockSize, Math.min (blockSize, rightData.length - count));
        }
        return combinedData;
    }


    /**
     * Calculates the size of all WAV files.
     *
     * @param sampleFiles The sample files (must be all WAV files).
     * @return The summed size
     * @throws IOException Could not read a file
     */
    public static int calculateSampleSize (final List<File> sampleFiles) throws IOException
    {
        int size = 0;
        try
        {
            for (final File file: sampleFiles)
                size += new WaveFile (file, true).getDataChunk ().getData ().length;
        }
        catch (final ParseException ex)
        {
            throw new IOException (ex);
        }
        return size;
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
    public void visitChunk (final RawRIFFChunk group, final RawRIFFChunk chunk) throws ParseException
    {
        if (group.getType () == InfoRiffChunkId.INFO_ID.getFourCC () && group.getId ().getFourCC () == CommonRiffChunkId.LIST_ID.getFourCC ())
        {
            if (chunk.getType () == InfoRiffChunkId.INFO_ID.getFourCC ())
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

        final RiffChunkId riffId = chunk.getId ();
        final int id = riffId.getFourCC ();

        if (id == WaveRiffChunkId.BEXT_ID.getFourCC ())
        {
            this.broadcastAudioExtensionChunk = new BroadcastAudioExtensionChunk (chunk);
            this.chunkStack.add (this.broadcastAudioExtensionChunk);
            return;
        }

        if (id == WaveRiffChunkId.INST_ID.getFourCC ())
        {
            this.instrumentChunk = new InstrumentChunk (chunk);
            this.chunkStack.add (this.instrumentChunk);
            return;
        }

        if (id == WaveRiffChunkId.FMT_ID.getFourCC ())
        {
            this.formatChunk = new FormatChunk (chunk);
            this.chunkStack.add (this.formatChunk);
            return;
        }

        if (id == WaveRiffChunkId.DATA_ID.getFourCC ())
        {
            this.dataChunk = new DataChunk (chunk);
            this.chunkStack.add (this.dataChunk);
            return;
        }

        if (id == WaveRiffChunkId.SMPL_ID.getFourCC ())
        {
            this.sampleChunk = new SampleChunk (chunk);
            this.chunkStack.add (this.sampleChunk);
            return;
        }

        // FILLER_ID, all others can safely be ignored
        this.chunkStack.add (new UnknownChunk (riffId, chunk));
    }


    /** {@inheritDoc} */
    @Override
    public boolean acceptsTruncatedChunk (final RawRIFFChunk chunk)
    {
        // The audio of a data chunk which is cut off is kept if it can be faded out at the end
        // where it is cut off, since it would click there
        return chunk.getId ().getFourCC () == WaveRiffChunkId.DATA_ID.getFourCC () && this.formatChunk != null && canFadeOut (this.formatChunk);
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


    /** {@inheritDoc} */
    @Override
    public void checkTopChunk (final int type) throws ParseException
    {
        if (WaveRiffChunkId.WAVE_ID.getFourCC () != type)
            throw new ParseException ("Top chunk must be 'WAVE' but is '" + RiffChunkId.toASCII (type) + "'");
    }


    /**
     * Keep the whole sample frames of the audio which is present in the data chunk of a truncated
     * file and fade them out at the end, where they would otherwise stop abruptly and click. If not
     * even one sample frame is present, the data chunk is removed.
     */
    private void repairTruncatedData ()
    {
        final RawRIFFChunk chunk = this.dataChunk.getRawChunk ();
        final byte [] data = chunk.getData ();
        final int bytesPerFrame = this.formatChunk.calculateBytesPerSample ();
        final int numberOfFrames = data.length / bytesPerFrame;
        if (numberOfFrames == 0)
        {
            this.chunkStack.remove (this.dataChunk);
            this.dataChunk = null;
            return;
        }

        final byte [] frames = Arrays.copyOf (data, numberOfFrames * bytesPerFrame);
        this.fadeOutStart = numberOfFrames - Math.clamp (Math.round (FADE_OUT_SECONDS * this.formatChunk.getSampleRate ()), 1, numberOfFrames);
        fadeOut (frames, this.formatChunk, this.fadeOutStart);
        chunk.truncate (frames.length);
        chunk.setData (frames);
    }


    /**
     * Check if the audio of the given format can be faded out: integer PCM with 8 to 32 bits or
     * floating-point audio with 32 or 64 bits, also in the extensible format.
     *
     * @param formatChunk The format of the audio
     * @return True if it can be faded out
     */
    private static boolean canFadeOut (final FormatChunk formatChunk)
    {
        final int channels = formatChunk.getNumberOfChannels ();
        final int bytesPerFrame = formatChunk.calculateBytesPerSample ();
        if (channels < 1 || bytesPerFrame != formatChunk.getBlockAlign ())
            return false;
        final int bytesPerSample = bytesPerFrame / channels;
        return switch (getSampleFormat (formatChunk))
        {
            case FormatChunk.WAVE_FORMAT_PCM -> bytesPerSample >= 1 && bytesPerSample <= 4;
            case FormatChunk.WAVE_FORMAT_IEEE_FLOAT -> bytesPerSample == 4 || bytesPerSample == 8;
            default -> false;
        };
    }


    /**
     * Get the format of the samples, which the extensible format stores in its sub-format.
     *
     * @param formatChunk The format chunk
     * @return The format, see the WAVE_FORMAT_* constants of the format chunk
     */
    private static int getSampleFormat (final FormatChunk formatChunk)
    {
        final int compressionCode = formatChunk.getCompressionCode ();
        if (compressionCode != FormatChunk.WAVE_FORMAT_EXTENSIBLE)
            return compressionCode;
        // The first 2 bytes of the GUID of the sub-format are its format code
        final byte [] data = formatChunk.getData ();
        return data.length < 26 ? FormatChunk.WAVE_FORMAT_UNKNOWN : data[24] & 0xFF | (data[25] & 0xFF) << 8;
    }


    /**
     * Fade out the audio linearly from the given sample frame on, so that its last frame is silent.
     *
     * @param data The audio, whole sample frames
     * @param formatChunk The format of the audio, see {@link #canFadeOut(FormatChunk)}
     * @param fadeOutStart The first sample frame to fade out
     */
    private static void fadeOut (final byte [] data, final FormatChunk formatChunk, final int fadeOutStart)
    {
        final int channels = formatChunk.getNumberOfChannels ();
        final int bytesPerFrame = formatChunk.calculateBytesPerSample ();
        final int bytesPerSample = bytesPerFrame / channels;
        final boolean isFloat = getSampleFormat (formatChunk) == FormatChunk.WAVE_FORMAT_IEEE_FLOAT;
        final ByteBuffer buffer = ByteBuffer.wrap (data).order (ByteOrder.LITTLE_ENDIAN);
        final int numberOfFrames = data.length / bytesPerFrame;
        final double fadeOutFrames = numberOfFrames - fadeOutStart;
        for (int frame = fadeOutStart; frame < numberOfFrames; frame++)
        {
            final double gain = (numberOfFrames - 1 - frame) / fadeOutFrames;
            for (int channel = 0; channel < channels; channel++)
                scaleSample (buffer, frame * bytesPerFrame + channel * bytesPerSample, bytesPerSample, isFloat, gain);
        }
    }


    /**
     * Scale one sample of the audio.
     *
     * @param buffer The audio in little-endian byte order
     * @param offset The position of the sample in the audio
     * @param bytesPerSample The number of bytes of the sample: 1 for unsigned 8 bit, 2 to 4 for
     *            signed integers, 4 or 8 for floating-point
     * @param isFloat True if the sample is a floating-point value
     * @param gain The factor to scale the sample with, in the range of [0..1]
     */
    private static void scaleSample (final ByteBuffer buffer, final int offset, final int bytesPerSample, final boolean isFloat, final double gain)
    {
        if (isFloat)
        {
            if (bytesPerSample == 8)
                buffer.putDouble (offset, buffer.getDouble (offset) * gain);
            else
                buffer.putFloat (offset, (float) (buffer.getFloat (offset) * gain));
            return;
        }

        // 8 bit samples are unsigned with the silence at 128
        if (bytesPerSample == 1)
        {
            buffer.put (offset, (byte) (Math.round (((buffer.get (offset) & 0xFF) - 128) * gain) + 128));
            return;
        }

        // The most significant byte carries the sign
        long value = buffer.get (offset + bytesPerSample - 1);
        for (int i = bytesPerSample - 2; i >= 0; i--)
            value = value << 8 | buffer.get (offset + i) & 0xFF;
        final long scaled = Math.round (value * gain);
        for (int i = 0; i < bytesPerSample; i++)
            buffer.put (offset + i, (byte) (scaled >> 8 * i));
    }


    private static final class DataChunkPositionRIFFVisitor implements RIFFVisitor
    {
        private final RIFFParser parser;
        long                     dataChunkPosition = -1;


        public DataChunkPositionRIFFVisitor (final RIFFParser parser)
        {
            this.parser = parser;
        }


        /** {@inheritDoc} */
        @Override
        public boolean enteringGroup (final RawRIFFChunk group)
        {
            return false;
        }


        /** {@inheritDoc} */
        @Override
        public void enterGroup (final RawRIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void leaveGroup (final RawRIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void visitChunk (final RawRIFFChunk group, final RawRIFFChunk chunk) throws ParseException
        {
            if (chunk.getId ().getFourCC () == WaveRiffChunkId.DATA_ID.getFourCC ())
                this.dataChunkPosition = this.parser.getPosition () - chunk.getSize ();
        }


        /** {@inheritDoc} */
        @Override
        public void checkTopChunk (final int type) throws ParseException
        {
            // Intentionally empty
        }
    }
}
