// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * Accessor to a Korg Sample (KSF) file.
 *
 * @author Jürgen Moßgraber
 */
public class KSFFile
{
    private static final DestinationAudioFormat DESTINATION_FORMAT          = new DestinationAudioFormat (new int []
    {
        8,
        16
    }, 48000, false);

    /** ID for KSF Sample parameter chunk. */
    private static final String                 KSF_SAMPLE_PARAM_ID         = "SMP1";
    /** ID for KSF Sample parameter 2 chunk. */
    private static final String                 KSF_SAMPLE_PARAM_2_ID       = "SMP2";
    /** ID for KSF Sample data chunk. */
    private static final String                 KSF_SAMPLE_DATA_ID          = "SMD1";
    /** ID for KSF Sample number chunk. */
    private static final String                 KSF_SAMPLE_NUMBER_ID        = "SNO1";
    /** ID for KSF Sample name chunk. */
    private static final String                 KSF_SAMPLE_NAME_ID          = "NAME";
    /** ID for KSF Sample file name chunk. */
    private static final String                 KSF_SAMPLE_FILENAME_ID      = "SMF1";
    /** ID for KSF divided sample parameter chunk. */
    private static final String                 KSF_SAMPLE_DIVIDED_PARAM_ID = "SPD1";
    /** ID for KSF divided sample data chunk. */
    private static final String                 KSF_SAMPLE_DIVIDED_DATA_ID  = "SDD1";

    private static final int                    KSF_SAMPLE_PARAM_SIZE       = 32;
    private static final int                    KSF_SAMPLE_DATA_SIZE        = 12;
    private static final int                    KSF_SAMPLE_NUMBER_SIZE      = 4;
    private static final int                    KSF_SAMPLE_NAME_SIZE        = 24;
    private static final int                    KSF_SAMPLE_FILENAME_SIZE    = 12;


    /**
     * Constructor.
     */
    private KSFFile ()
    {
        // Intentionally empty
    }


    /**
     * Read and parse a KSF file.
     *
     * @param inputStream Where to read the file from
     * @param zone The zone to which to add the KSF data
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    public static void read (final InputStream inputStream, final ISampleZone zone) throws IOException, ParseException
    {
        final DataInputStream in = new DataInputStream (inputStream);

        int channels = -1;
        int sampleRate = -1;
        int sampleResolution = -1;
        int numberOfSamples = -1;
        byte [] data = null;
        String combinedName = "";

        while (true)
        {
            final String id = new String (in.readNBytes (4));
            final int dataSize = in.readInt ();

            switch (id)
            {
                case KSF_SAMPLE_PARAM_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_PARAM_SIZE);

                    combinedName = new String (in.readNBytes (16));
                    final int s = in.readInt ();
                    // Originally in the Triton the start is only 3 bytes! The first byte is the
                    // 'default bank' (0–3) but documented as 4 byte start in the later workstations
                    zone.setStart (s & 0xFFF);

                    // No idea what 'second start' is, seems to be identical to loop start
                    in.readInt ();

                    final DefaultSampleLoop loop = new DefaultSampleLoop ();
                    loop.setStart (in.readInt ());
                    loop.setEnd (in.readInt ());
                    zone.getLoops ().add (loop);
                    break;

                case KSF_SAMPLE_PARAM_2_ID:
                    // M3 manual says that this is used instead of SMP1 but seems to be identical...
                    in.readNBytes (dataSize);
                    break;

                case KSF_SAMPLE_DATA_ID:
                    if (dataSize < KSF_SAMPLE_DATA_SIZE)
                        throw new ParseException (Functions.getMessage ("IDS_KMP_WRONG_CHUNK_LENGTH", id, Integer.toString (dataSize), Integer.toString (KSF_SAMPLE_DATA_SIZE)));

                    final int sampleDataLength = dataSize - KSF_SAMPLE_DATA_SIZE;

                    sampleRate = in.readInt ();

                    // Attributes byte combines several settings:
                    // Bit 1 (LSB): 1 = +12 dB play-back; 0 = 0 dB play-back (if un-compressed)
                    // Bit 1-4: Compression ID, if compressed
                    // Bit 5 (0x10): 1 = compressed, 0 = not compressed
                    // Bit 6 (0x20): 1 = not use 2nd start, 0 = do use 2nd start
                    // Bit 7 (0x40): 1 = reverse, 0 = forward
                    // Bit 8 (0x80): 1 = loop off, 0 = loop on
                    final int attributes = in.read ();
                    if ((attributes & 1) > 0)
                        System.out.println ("Boosted!");

                    if ((attributes & 0x10) > 0)
                        throw new ParseException (Functions.getMessage ("IDS_KMP_COMPRESSED_DATA_NOT_SUPPORTED"));
                    // Not used: attributes & 0x20 = 1: Not Use 2nd Start 0: Use It
                    zone.setReversed ((attributes & 0x40) > 0);
                    if ((attributes & 0x80) > 0)
                        zone.getLoops ().clear ();

                    // loopTune (–99…+99 cents) not supported
                    in.readByte ();

                    channels = in.read ();
                    // 8/16
                    sampleResolution = in.read ();
                    numberOfSamples = in.readInt ();
                    data = in.readNBytes (sampleDataLength);
                    if (sampleResolution == 16)
                        flipBytes (data);
                    break;

                case KSF_SAMPLE_NUMBER_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_NUMBER_SIZE);
                    // The sample number, not used
                    in.readInt ();
                    break;

                case KSF_SAMPLE_NAME_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_NAME_SIZE);
                    combinedName = new String (in.readNBytes (24));
                    break;

                case KSF_SAMPLE_FILENAME_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_FILENAME_SIZE);
                    throw new ParseException (Functions.getMessage ("IDS_KMP_ERR_REFERENCED_KSF_NOT_SUPPORTED"));

                case KSF_SAMPLE_DIVIDED_PARAM_ID, KSF_SAMPLE_DIVIDED_DATA_ID:
                    throw new ParseException (Functions.getMessage ("IDS_KMP_ERR_DISTRIBUTED_KSF_NOT_SUPPORTED"));

                default:
                    throw new ParseException (Functions.getMessage ("IDS_KMP_UNKNOWN_CHUNK", id));
            }

            if (in.available () == 0)
                break;
        }

        final IAudioMetadata audioMetadata = new DefaultAudioMetadata (channels, sampleRate, sampleResolution, numberOfSamples);
        final InMemorySampleData sampleData = new InMemorySampleData (audioMetadata, data);
        zone.setName (combinedName.trim ());
        zone.setSampleData (sampleData);
    }


    /**
     * Write a KSF file.
     *
     * @param sampleZone The zone which contains the sample to store in a KSF file
     * @param sampleIndex The index of the sample
     * @param outputStream Where to write the file to
     * @param gain12dB Enables the +12dB option, if true
     * @param kmpChannel The KMP channel to write
     * @throws IOException Could not write the file
     * @throws ParseException If source wave files are broken
     * @throws CompressionNotSupportedException If source wave files are compressed
     */
    public static void write (final ISampleZone sampleZone, final int sampleIndex, final OutputStream outputStream, final boolean gain12dB, final KMPChannel kmpChannel) throws IOException, ParseException, CompressionNotSupportedException
    {
        final DataOutputStream out = new DataOutputStream (outputStream);

        out.write (KSF_SAMPLE_PARAM_ID.getBytes ());
        out.writeInt (KSF_SAMPLE_PARAM_SIZE);

        final String name = createSafeFilename (sampleZone.getName ());
        out.write (pad (name, 16).getBytes ());

        out.writeInt (sampleZone.getStart ());

        final List<ISampleLoop> loops = sampleZone.getLoops ();
        if (loops.isEmpty ())
        {
            // 2nd start
            out.writeInt (0);
            out.writeInt (0);
            out.writeInt (sampleZone.getStop ());
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            // 2nd start - no idea, but identical to loop start
            out.writeInt (loop.getStart ());
            out.writeInt (loop.getStart ());
            out.writeInt (loop.getEnd ());
        }

        //////////////////////////////////////
        // KSF_SAMPLE_NUMBER_ID

        out.write (KSF_SAMPLE_NUMBER_ID.getBytes ());
        out.writeInt (KSF_SAMPLE_NUMBER_SIZE);
        out.writeInt (sampleIndex);

        //////////////////////////////////////
        // KSF_SAMPLE_DATA_ID

        out.write (KSF_SAMPLE_DATA_ID.getBytes ());

        // Convert the file to be a 8 or 16 bit WAV file with a maximum of 48kHz
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleZone.getSampleData (), DESTINATION_FORMAT);
        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final DataChunk dataChunk = waveFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();
        final int numSamples = dataChunk.calculateLength (formatChunk);

        // Only write left or right channel
        int dataLength = data.length;
        final boolean isStereo = formatChunk.getNumberOfChannels () == 2;
        if (isStereo)
            dataLength = dataLength / 2;

        out.writeInt (KSF_SAMPLE_DATA_SIZE + dataLength);
        out.writeInt (formatChunk.getSampleRate ());

        // Attributes byte combines several settings; 1 = boost +12dB
        int attributes = gain12dB ? 1 : 0;
        // Not used: attributes & 0x20 = 1: Not Use 2nd Start 0: Use It
        attributes |= 0x20;
        if (sampleZone.isReversed ())
            attributes |= 0x40;
        if (loops.isEmpty ())
            attributes |= 0x80;
        out.write (attributes);

        // loopTune (–99…+99 cents) not supported
        out.writeByte (0);

        // Number of channels is always 1
        out.write (1);
        // 8/16
        final int bits = formatChunk.getSignificantBitsPerSample ();
        if (bits != 8 && bits != 16)
            throw new IOException (Functions.getMessage ("IDS_KMP_BIT_SIZE_NOT_SUPPORTED", Integer.toString (bits)));
        out.write (bits);

        out.writeInt (numSamples);

        if (bits == 8)
        {
            if (kmpChannel == KMPChannel.MONO || !isStereo)
                out.write (data);
            else
            {
                final int start = kmpChannel == KMPChannel.RIGHT && isStereo ? 1 : 0;
                for (int i = start; i < data.length; i += 2)
                    out.write (data[i]);
            }
        }
        else
        {
            final int start = kmpChannel == KMPChannel.RIGHT && isStereo ? 2 : 0;
            final int step = kmpChannel == KMPChannel.MONO || !isStereo ? 2 : 4;
            for (int i = start; i < data.length; i += step)
            {
                // Flip bytes
                out.write (data[i + 1]);
                out.write (data[i]);
            }
        }

        //////////////////////////////////////
        // KSF_SAMPLE_NAME_ID

        out.write (KSF_SAMPLE_NAME_ID.getBytes ());
        out.writeInt (KSF_SAMPLE_NAME_SIZE);

        out.write (pad (name, 24).getBytes ());
    }


    private static void assertSize (final String chunk, final int dataSize, final int expectedSize) throws ParseException
    {
        if (dataSize != expectedSize)
            throw new ParseException (Functions.getMessage ("IDS_KMP_WRONG_CHUNK_LENGTH", chunk, Integer.toString (dataSize), Integer.toString (expectedSize)));
    }


    private static String pad (final String text, final int length)
    {
        return (text + "                        ").substring (0, length);
    }


    // Flip MSB / LSB
    private static void flipBytes (final byte [] data)
    {
        for (int i = 0; i < data.length; i += 2)
        {
            final byte temp = data[i];
            data[i] = data[i + 1];
            data[i + 1] = temp;
        }
    }


    private static String createSafeFilename (final String filename)
    {
        final String name = filename.replaceAll ("[\\\\/:*?\"<>|&\\.#]", "_").trim ();
        return name.length () == 1 ? "NAME" + name : name;
    }
}
