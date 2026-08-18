// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.aac.AacEncoder;
import de.mossgrabers.convertwithmoss.file.alac.AlacEncoder;
import de.mossgrabers.convertwithmoss.file.caf.CafAudioDescriptionChunk;
import de.mossgrabers.convertwithmoss.file.caf.CafFile;
import de.mossgrabers.convertwithmoss.file.caf.CafInstrumentChunk;
import de.mossgrabers.convertwithmoss.file.caf.CafMarker;
import de.mossgrabers.convertwithmoss.file.caf.CafRegion;
import de.mossgrabers.convertwithmoss.file.iff.IffFile;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Only stores the sample files of the multi-sample in one of several audio file formats (WAV, AIFF,
 * CAF or FLAC). There is no preset file and all related samples are stored in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class WavCreator extends AbstractWavCreator<WavCreatorUI>
{
    /** The number of sample frames of a full Apple Lossless packet. */
    private static final int                    ALAC_FRAME_LENGTH      = 4096;

    /** Apple Lossless does not support 8-bit samples. */
    private static final DestinationAudioFormat ALAC_COMPATIBLE_FORMAT = new DestinationAudioFormat (new int []
    {
        16,
        24,
        32
    }, -1, false);

    /** The AAC encoder works on 16-bit samples. */
    private static final DestinationAudioFormat AAC_COMPATIBLE_FORMAT  = new DestinationAudioFormat (new int []
    {
        16
    }, -1, false);


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WavCreator (final INotifier notifier)
    {
        super ("Sample Files", "Wav", notifier, new WavCreatorUI ("Wav", true, true, true, true));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = FileUtils.createSafeFilename (multisampleSource.getName ());
        final String safeSampleFolderName = sampleName + FOLDER_POSTFIX;

        this.notifier.log ("IDS_NOTIFY_STORING", safeSampleFolderName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource, this.settingsConfiguration.getOutputFormat ().getEnding (), DESTINATION_FORMAT, false);

        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    protected String createFileName (final int zoneIndex, final ISampleZone zone)
    {
        return zone.getName () + this.settingsConfiguration.getOutputFormat ().getEnding ();
    }


    /** {@inheritDoc} */
    @Override
    protected void rewriteFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final OutputStream outputStream, final DestinationAudioFormat destinationFormat, final boolean trim) throws IOException
    {
        final SampleFileFormat outputFormat = this.settingsConfiguration.getOutputFormat ();
        if (outputFormat == SampleFileFormat.WAV)
        {
            super.rewriteFile (multisampleSource, zone, outputStream, destinationFormat, trim);
            return;
        }

        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
            return;

        if (outputFormat == SampleFileFormat.FLAC)
        {
            outputStream.write (AudioFileUtils.compressToFLAC (sampleData.get ()));
            return;
        }

        // Convert resolution - Apple Lossless does not support 8-bit samples, AAC works on
        // 16-bit samples
        final DestinationAudioFormat cafFormat = switch (outputFormat)
        {
            case SampleFileFormat.CAF_ALAC -> ALAC_COMPATIBLE_FORMAT;
            case SampleFileFormat.CAF_AAC -> AAC_COMPATIBLE_FORMAT;
            default -> destinationFormat;
        };
        this.logResampling (zone, cafFormat);
        final WaveFile wavFile = AudioFileUtils.convertToWav (sampleData.get (), cafFormat);
        if (wavFile.getDataChunk () == null)
            throw new IOException (Functions.getMessage ("IDS_WAV_CONVERSION_FAILED", zone.getName ()));

        // Trim sample from zone start to end
        if (trim)
            trimStartToEnd (wavFile, zone);

        if (outputFormat == SampleFileFormat.AIFF)
            this.writeAiffFile (multisampleSource, zone, wavFile, outputStream);
        else
            this.writeCafFile (multisampleSource, zone, wavFile, outputFormat, outputStream);
    }


    /**
     * Write the sample of the given zone as an AIFF file. The instrument info and loops of the zone
     * are stored in Instrument and Marker chunks.
     *
     * @param multisampleSource The multi-sample source
     * @param zone The zone which contains the sample
     * @param wavFile The already converted WAV file with the sample data
     * @param outputStream Where to write the AIFF file
     * @throws IOException Could not write the file
     */
    private void writeAiffFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final WaveFile wavFile, final OutputStream outputStream) throws IOException
    {
        final FormatChunk formatChunk = wavFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        final int bitsPerSample = formatChunk.getSignificantBitsPerSample ();
        final int bytesPerSample = Math.ceilDiv (bitsPerSample, 8);

        // AIFF stores multi-byte samples in big-endian order and 8-bit samples signed
        byte [] data = wavFile.getDataChunk ().getData ();
        if (bytesPerSample == 1)
            data = convertUnsigned8BitToSigned (data);
        else
            data = swapEndianness (data, bytesPerSample);
        final long numberOfFrames = data.length / ((long) numberOfChannels * bytesPerSample);

        final ByteArrayOutputStream formBody = new ByteArrayOutputStream ();

        // The Common chunk
        final ByteArrayOutputStream commonOut = new ByteArrayOutputStream ();
        StreamUtils.writeSigned16 (commonOut, numberOfChannels, true);
        StreamUtils.writeUnsigned32 (commonOut, numberOfFrames, true);
        StreamUtils.writeSigned16 (commonOut, bitsPerSample, true);
        commonOut.write (convertDoubleTo80BitFloat (formatChunk.getSampleRate ()));
        IffFile.writeLocalChunk (formBody, "COMM", commonOut.toByteArray ());

        // The metadata text chunks
        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
        {
            final IMetadata metadata = multisampleSource.getMetadata ();
            final String creator = metadata.getCreator ();
            if (creator != null && !creator.isBlank ())
                IffFile.writeLocalChunk (formBody, "AUTH", creator.getBytes (StandardCharsets.US_ASCII));
            final String description = metadata.getDescription ();
            if (description != null && !description.isBlank ())
                IffFile.writeLocalChunk (formBody, "ANNO", description.getBytes (StandardCharsets.US_ASCII));
        }

        // The Marker and Instrument chunks
        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean writeLoop = this.settingsConfiguration.isUpdateSampleChunk () && !loops.isEmpty ();
        if (this.settingsConfiguration.isUpdateInstrumentChunk () || writeLoop)
        {
            if (writeLoop)
            {
                final ISampleLoop loop = loops.get (0);
                final ByteArrayOutputStream markerOut = new ByteArrayOutputStream ();
                StreamUtils.writeUnsigned16 (markerOut, 2, true);
                writeAiffMarker (markerOut, 1, loop.getStart (), "beg loop");
                writeAiffMarker (markerOut, 2, loop.getEnd (), "end loop");
                IffFile.writeLocalChunk (formBody, "MARK", markerOut.toByteArray ());
            }

            final ByteArrayOutputStream instrumentOut = new ByteArrayOutputStream ();
            instrumentOut.write (Math.clamp (zone.getKeyRoot (), 0, 127));
            instrumentOut.write (Math.clamp ((int) Math.round (zone.getTuning () * 100), -50, 50));
            instrumentOut.write (Math.clamp (zone.getKeyLow (), 0, 127));
            instrumentOut.write (Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127));
            instrumentOut.write (Math.clamp (zone.getVelocityLow (), 0, 127));
            instrumentOut.write (Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 0, 127));
            StreamUtils.writeSigned16 (instrumentOut, Math.clamp ((int) zone.getGain (), -127, 127), true);
            // The sustain loop: play mode and the IDs of the start/end markers
            if (writeLoop)
            {
                final ISampleLoop loop = loops.get (0);
                StreamUtils.writeSigned16 (instrumentOut, loop.getType () == LoopType.ALTERNATING ? 2 : 1, true);
                StreamUtils.writeSigned16 (instrumentOut, 1, true);
                StreamUtils.writeSigned16 (instrumentOut, 2, true);
            }
            else
            {
                StreamUtils.writeSigned16 (instrumentOut, 0, true);
                StreamUtils.writeSigned16 (instrumentOut, 0, true);
                StreamUtils.writeSigned16 (instrumentOut, 0, true);
            }
            // No release loop
            StreamUtils.writeSigned16 (instrumentOut, 0, true);
            StreamUtils.writeSigned16 (instrumentOut, 0, true);
            StreamUtils.writeSigned16 (instrumentOut, 0, true);
            IffFile.writeLocalChunk (formBody, "INST", instrumentOut.toByteArray ());
        }

        // The Sound Data chunk
        final ByteArrayOutputStream soundDataOut = new ByteArrayOutputStream ();
        StreamUtils.writeUnsigned32 (soundDataOut, 0, true);
        StreamUtils.writeUnsigned32 (soundDataOut, 0, true);
        soundDataOut.write (data);
        IffFile.writeLocalChunk (formBody, "SSND", soundDataOut.toByteArray ());

        IffFile.writeGroupChunk (outputStream, "FORM", "AIFF", formBody.toByteArray ());
    }


    /**
     * Write the sample of the given zone as a CAF file. The instrument info and loops of the zone
     * are stored in an Instrument and a Region chunk, the metadata in an Information chunk.
     *
     * @param multisampleSource The multi-sample source
     * @param zone The zone which contains the sample
     * @param wavFile The already converted WAV file with the sample data
     * @param outputFormat The format which decides the audio data compression
     * @param outputStream Where to write the CAF file
     * @throws IOException Could not write the file
     */
    private void writeCafFile (final IMultisampleSource multisampleSource, final ISampleZone zone, final WaveFile wavFile, final SampleFileFormat outputFormat, final OutputStream outputStream) throws IOException
    {
        final FormatChunk formatChunk = wavFile.getFormatChunk ();
        final int numberOfChannels = formatChunk.getNumberOfChannels ();
        final int bitsPerSample = formatChunk.getSignificantBitsPerSample ();
        final int bytesPerSample = Math.ceilDiv (bitsPerSample, 8);
        final boolean isFloat = formatChunk.getCompressionCode () == FormatChunk.WAVE_FORMAT_IEEE_FLOAT;

        // CAF stores 8-bit samples signed, multi-byte samples are kept in little-endian order
        // which is marked with a format flag
        byte [] data = wavFile.getDataChunk ().getData ();
        if (!isFloat && bytesPerSample == 1)
            data = convertUnsigned8BitToSigned (data);

        final CafFile cafFile = new CafFile ();

        final CafAudioDescriptionChunk descriptionChunk = new CafAudioDescriptionChunk ();
        descriptionChunk.setSampleRate (formatChunk.getSampleRate ());
        descriptionChunk.setChannelsPerFrame (numberOfChannels);
        cafFile.setAudioDescriptionChunk (descriptionChunk);

        switch (outputFormat)
        {
            case SampleFileFormat.CAF_AAC:
            {
                // Compress the audio data with MPEG-4 AAC
                final AacEncoder aacEncoder = new AacEncoder (formatChunk.getSampleRate (), numberOfChannels);
                final int bytesPerFrame = numberOfChannels * bytesPerSample;
                final int totalFrames = data.length / bytesPerFrame;

                final ByteArrayOutputStream packetsOut = new ByteArrayOutputStream ();
                final java.util.List<byte []> packets = aacEncoder.encode (data, 0, totalFrames);
                final int [] packetSizes = new int [packets.size ()];
                for (int packet = 0; packet < packets.size (); packet++)
                {
                    packetsOut.write (packets.get (packet));
                    packetSizes[packet] = packets.get (packet).length;
                }

                descriptionChunk.setFormatID (CafAudioDescriptionChunk.FORMAT_MPEG4_AAC);
                // The format flags of AAC contain the MPEG-4 audio object type (2 = low complexity)
                descriptionChunk.setFormatFlags (2);
                descriptionChunk.setBytesPerPacket (0);
                descriptionChunk.setFramesPerPacket (AacEncoder.FRAME_LENGTH);
                descriptionChunk.setBitsPerChannel (0);
                cafFile.setMagicCookie (aacEncoder.getMagicCookie ());
                cafFile.setPacketTable (packetSizes, totalFrames, AacEncoder.PRIMING_FRAMES);
                cafFile.setAudioData (packetsOut.toByteArray ());
                break;
            }

            case SampleFileFormat.CAF_ALAC:
            {
                // Compress the audio data with Apple Lossless
                final AlacEncoder alacEncoder = new AlacEncoder (bitsPerSample, numberOfChannels, formatChunk.getSampleRate (), ALAC_FRAME_LENGTH);
                final int bytesPerFrame = numberOfChannels * bytesPerSample;
                final int totalFrames = data.length / bytesPerFrame;

                final ByteArrayOutputStream packetsOut = new ByteArrayOutputStream ();
                final int numberOfPackets = Math.max (1, Math.ceilDiv (totalFrames, ALAC_FRAME_LENGTH));
                final int [] packetSizes = new int [numberOfPackets];
                for (int packet = 0; packet < numberOfPackets; packet++)
                {
                    final int frameOffset = packet * ALAC_FRAME_LENGTH;
                    final byte [] packetData = alacEncoder.encodePacket (data, frameOffset * bytesPerFrame, Math.min (ALAC_FRAME_LENGTH, totalFrames - frameOffset));
                    packetsOut.write (packetData);
                    packetSizes[packet] = packetData.length;
                }

                descriptionChunk.setFormatID (CafAudioDescriptionChunk.FORMAT_APPLE_LOSSLESS);
                // The format flags of Apple loss-less encode the resolution of the source data
                descriptionChunk.setFormatFlags (switch (bitsPerSample)
                {
                    case 16 -> 1;
                    case 24 -> 3;
                    default -> 4;
                });
                descriptionChunk.setBytesPerPacket (0);
                descriptionChunk.setFramesPerPacket (ALAC_FRAME_LENGTH);
                descriptionChunk.setBitsPerChannel (0);
                cafFile.setMagicCookie (alacEncoder.getMagicCookie ());
                cafFile.setPacketTable (packetSizes, totalFrames, 0);
                cafFile.setAudioData (packetsOut.toByteArray ());
                break;
            }

            default:
                descriptionChunk.setFormatID (CafAudioDescriptionChunk.FORMAT_LINEAR_PCM);
                descriptionChunk.setFormatFlags (CafAudioDescriptionChunk.FLAG_IS_LITTLE_ENDIAN | (isFloat ? CafAudioDescriptionChunk.FLAG_IS_FLOAT : 0));
                descriptionChunk.setBytesPerPacket (numberOfChannels * bytesPerSample);
                descriptionChunk.setFramesPerPacket (1);
                descriptionChunk.setBitsPerChannel (bitsPerSample);
                cafFile.setAudioData (data);
                break;
        }

        // The instrument chunk with a region for the loop
        final List<ISampleLoop> loops = zone.getLoops ();
        final boolean writeLoop = this.settingsConfiguration.isUpdateSampleChunk () && !loops.isEmpty ();
        if (this.settingsConfiguration.isUpdateInstrumentChunk () || writeLoop)
        {
            final CafInstrumentChunk instrumentChunk = new CafInstrumentChunk ();
            instrumentChunk.setBaseNote (Math.clamp (zone.getKeyRoot (), 0, 127) + (float) Math.clamp (zone.getTuning (), -0.5, 0.5));
            instrumentChunk.setLowNote (Math.clamp (zone.getKeyLow (), 0, 127));
            instrumentChunk.setHighNote (Math.clamp (limitToDefault (zone.getKeyHigh (), 127), 0, 127));
            instrumentChunk.setLowVelocity (Math.clamp (zone.getVelocityLow (), 0, 127));
            instrumentChunk.setHighVelocity (Math.clamp (limitToDefault (zone.getVelocityHigh (), 127), 0, 127));
            instrumentChunk.setGain ((float) zone.getGain ());

            if (writeLoop)
            {
                final ISampleLoop loop = loops.get (0);
                final CafRegion region = new CafRegion ();
                region.setRegionID (1);
                long flags = CafRegion.FLAG_LOOP_ENABLE;
                switch (loop.getType ())
                {
                    default:
                    case FORWARDS:
                        flags |= CafRegion.FLAG_PLAY_FORWARD;
                        break;
                    case ALTERNATING:
                        flags |= CafRegion.FLAG_PLAY_FORWARD | CafRegion.FLAG_PLAY_BACKWARD;
                        break;
                    case BACKWARDS:
                        flags |= CafRegion.FLAG_PLAY_BACKWARD;
                        break;
                }
                region.setFlags (flags);
                region.getMarkers ().add (new CafMarker (CafMarker.TYPE_REGION_START, loop.getStart (), 1));
                region.getMarkers ().add (new CafMarker (CafMarker.TYPE_REGION_END, loop.getEnd (), 2));
                cafFile.getRegions ().add (region);
                instrumentChunk.setSustainRegionID (1);
            }

            cafFile.setInstrumentChunk (instrumentChunk);
        }

        // The metadata
        if (this.settingsConfiguration.isUpdateBroadcastAudioChunk ())
        {
            final IMetadata metadata = multisampleSource.getMetadata ();
            final Map<String, String> information = cafFile.getInformation ();
            final String creator = metadata.getCreator ();
            if (creator != null && !creator.isBlank ())
                information.put (CafFile.INFORMATION_ARTIST, creator);
            final String description = metadata.getDescription ();
            if (description != null && !description.isBlank ())
                information.put (CafFile.INFORMATION_COMMENTS, description);
        }

        cafFile.write (outputStream);
    }


    private static void writeAiffMarker (final OutputStream out, final int markerID, final long position, final String name) throws IOException
    {
        StreamUtils.writeUnsigned16 (out, markerID, true);
        StreamUtils.writeUnsigned32 (out, position, true);
        // The name is a Pascal string which is padded to an even total length
        final byte [] nameBytes = name.getBytes (StandardCharsets.US_ASCII);
        out.write (nameBytes.length);
        out.write (nameBytes);
        if ((nameBytes.length + 1) % 2 == 1)
            out.write (0);
    }


    /**
     * Convert a Java double to an 80 bit IEEE Standard 754 floating point number as used for the
     * sample rate in the AIFF Common chunk.
     *
     * @param value The value to convert
     * @return The 10 bytes (= 80 bit)
     */
    private static byte [] convertDoubleTo80BitFloat (final double value)
    {
        final byte [] result = new byte [10];
        if (value == 0)
            return result;

        final long bits = Double.doubleToLongBits (value);
        final int sign = (int) (bits >>> 63);
        final int exponent = (int) (bits >>> 52 & 0x7FF);
        final long mantissa = bits & 0xFFFFFFFFFFFFFL;

        // Re-bias the exponent from double (1023) to extended (16383) and make the leading 1 bit
        // of the mantissa explicit
        final int extendedExponent = exponent - 1023 + 16383;
        final long extendedMantissa = 1L << 63 | mantissa << 11;

        result[0] = (byte) (sign << 7 | extendedExponent >> 8);
        result[1] = (byte) extendedExponent;
        for (int i = 0; i < 8; i++)
            result[2 + i] = (byte) (extendedMantissa >>> (7 - i) * 8);
        return result;
    }


    /**
     * Swap the byte order of all samples.
     *
     * @param data The sample data
     * @param bytesPerSample The number of bytes of one sample
     * @return The swapped data in a new array
     */
    private static byte [] swapEndianness (final byte [] data, final int bytesPerSample)
    {
        final byte [] result = new byte [data.length];
        final int limit = data.length - bytesPerSample;
        for (int offset = 0; offset <= limit; offset += bytesPerSample)
            for (int i = 0; i < bytesPerSample; i++)
                result[offset + i] = data[offset + bytesPerSample - 1 - i];
        return result;
    }


    /**
     * Convert unsigned 8-bit samples (WAV) to signed ones (AIFF, CAF).
     *
     * @param data The sample data
     * @return The converted data in a new array
     */
    private static byte [] convertUnsigned8BitToSigned (final byte [] data)
    {
        final byte [] result = new byte [data.length];
        for (int i = 0; i < data.length; i++)
            result[i] = (byte) (data[i] - 128);
        return result;
    }
}
