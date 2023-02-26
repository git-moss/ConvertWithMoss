// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleMetadata;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;


/**
 * Accessor to a Korg Sample (KSF) file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KSFFile extends DefaultSampleMetadata
{
    /** ID for KSF Sample parameter chunk. */
    private static final String KSF_SAMPLE_PARAM_ID         = "SMP1";
    /** ID for KSF Sample data chunk. */
    private static final String KSF_SAMPLE_DATA_ID          = "SMD1";
    /** ID for KSF Sample number chunk. */
    private static final String KSF_SAMPLE_NUMBER_ID        = "SNO1";
    /** ID for KSF Sample name chunk. */
    private static final String KSF_SAMPLE_NAME_ID          = "NAME";
    /** ID for KSF Sample file name chunk. */
    private static final String KSF_SAMPLE_FILENAME_ID      = "SMF1";
    /** ID for KSF divided sample parameter chunk. */
    private static final String KSF_SAMPLE_DIVIDED_PARAM_ID = "SPD1";
    /** ID for KSF divided sample data chunk. */
    private static final String KSF_SAMPLE_DIVIDED_DATA_ID  = "SDD1";

    private static final int    KSF_SAMPLE_PARAM_SIZE       = 32;
    private static final int    KSF_SAMPLE_DATA_SIZE        = 12;
    private static final int    KSF_SAMPLE_NUMBER_SIZE      = 4;
    private static final int    KSF_SAMPLE_NAME_SIZE        = 24;
    private static final int    KSF_SAMPLE_FILENAME_SIZE    = 12;

    private int                 sampleNumber;

    private int                 channels;
    private int                 sampleResolution;
    private int                 numberOfSamples;
    private byte []             sampleData;


    /**
     * Constructor.
     */
    public KSFFile ()
    {
        // Intentionally empty
    }


    /**
     * Read and parse a KSF file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    public void read (final InputStream inputStream) throws IOException, ParseException
    {
        final DataInputStream in = new DataInputStream (inputStream);

        while (true)
        {
            final String id = new String (in.readNBytes (4));
            final int dataSize = in.readInt ();

            switch (id)
            {
                case KSF_SAMPLE_PARAM_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_PARAM_SIZE);

                    this.setCombinedName (new String (in.readNBytes (16)).trim () + ".wav");
                    final int s = in.readInt ();
                    // Originally in the Triton the start is only 3 bytes! The first byte is the
                    // 'default bank' (0–3) but documented as 4 byte start in the later workstations
                    this.setStart (s & 0xFFF);

                    // No idea what 'second start' is, seems to be identical to loop start
                    in.readInt ();

                    final DefaultSampleLoop loop = new DefaultSampleLoop ();
                    loop.setStart (in.readInt ());
                    loop.setEnd (in.readInt ());
                    this.getLoops ().add (loop);
                    break;

                case KSF_SAMPLE_DATA_ID:
                    if (dataSize < KSF_SAMPLE_DATA_SIZE)
                        throw new ParseException (Functions.getMessage ("IDS_KMP_WRONG_CHUNK_LENGTH", id, Integer.toString (dataSize), Integer.toString (KSF_SAMPLE_DATA_SIZE)));

                    final int sampleDataLength = dataSize - KSF_SAMPLE_DATA_SIZE;

                    this.sampleRate = in.readInt ();

                    // Attributes byte combines several settings
                    final int attributes = in.read ();
                    if ((attributes & 0x10) > 0)
                        throw new ParseException (Functions.getMessage ("IDS_KMP_COMPRESSED_DATA_NOT_SUPPORTED"));
                    // Not used: attributes & 0x20 = 1: Not Use 2nd Start 0: Use It
                    this.setReversed ((attributes & 0x40) == 1);
                    if ((attributes & 0x80) > 0)
                        this.getLoops ().clear ();

                    // loopTune (–99…+99 cents) not supported
                    in.readByte ();

                    this.channels = in.read ();
                    // 8/16
                    this.sampleResolution = in.read ();
                    this.numberOfSamples = in.readInt ();
                    this.sampleData = in.readNBytes (sampleDataLength);
                    break;

                case KSF_SAMPLE_NUMBER_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_NUMBER_SIZE);
                    this.sampleNumber = in.readInt ();
                    break;

                case KSF_SAMPLE_NAME_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_NAME_SIZE);
                    this.setCombinedName (new String (in.readNBytes (24)).trim () + ".wav");
                    break;

                case KSF_SAMPLE_FILENAME_ID:
                    assertSize (id, dataSize, KSF_SAMPLE_FILENAME_SIZE);
                    throw new ParseException (Functions.getMessage ("IDS_KMP_ERR_REFERENCED_KSF_NOT_SUPPORTED"));

                case KSF_SAMPLE_DIVIDED_PARAM_ID:
                case KSF_SAMPLE_DIVIDED_DATA_ID:
                    throw new ParseException (Functions.getMessage ("IDS_KMP_ERR_DISTRIBUTED_KSF_NOT_SUPPORTED"));

                default:
                    throw new ParseException (Functions.getMessage ("IDS_KMP_UNKNOWN_CHUNK", id));
            }

            if (in.available () == 0)
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        final WaveFile wavFile = new WaveFile (this.channels, this.sampleRate, this.sampleResolution, this.numberOfSamples);
        final DataChunk dataChunk = wavFile.getDataChunk ();

        // Flip MSB / LSB
        final byte [] destination = dataChunk.getData ();
        for (int i = 0; i < destination.length; i += 2)
        {
            destination[i] = this.sampleData[i + 1];
            destination[i + 1] = this.sampleData[i];
        }

        wavFile.write (outputStream);
    }


    /**
     * Write a KSF file.
     *
     * @param sample The sample to store in a KSF file
     * @param sampleIndex The index of the sample
     * @param outputStream Where to write the file to
     * @throws IOException Could not write the file
     * @throws ParseException If source wave files are broken
     * @throws CompressionNotSupportedException If source wave files are compressed
     */
    public static void write (final ISampleMetadata sample, final int sampleIndex, final OutputStream outputStream) throws IOException, ParseException, CompressionNotSupportedException
    {
        final DataOutputStream out = new DataOutputStream (outputStream);

        out.write (KSF_SAMPLE_PARAM_ID.getBytes ());
        out.writeInt (KSF_SAMPLE_PARAM_SIZE);

        final Optional<String> filename = sample.getUpdatedFilename ();
        if (filename.isEmpty ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_FILENAME", "null"));
        final String name = FileUtils.getNameWithoutType (new File (filename.get ()));
        out.write (pad (name, 16).getBytes ());

        out.writeInt (sample.getStart ());

        final List<ISampleLoop> loops = sample.getLoops ();
        if (loops.isEmpty ())
        {
            out.writeInt (sample.getStart ());
            out.writeInt (0);
            out.writeInt (sample.getStop ());
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
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

        final ByteArrayOutputStream dataOut = new ByteArrayOutputStream ();
        sample.writeSample (dataOut);
        final ByteArrayInputStream dataIn = new ByteArrayInputStream (dataOut.toByteArray ());
        final WaveFile waveFile = new WaveFile ();
        waveFile.read (dataIn, true);

        final FormatChunk formatChunk = waveFile.getFormatChunk ();
        final DataChunk dataChunk = waveFile.getDataChunk ();
        final byte [] data = dataChunk.getData ();
        final int numSamples = dataChunk.calculateLength (formatChunk);
        out.writeInt (KSF_SAMPLE_DATA_SIZE + data.length);

        out.writeInt (formatChunk.getSampleRate ());

        // Attributes byte combines several settings
        int attributes = 0;
        // Not used: attributes & 0x20 = 1: Not Use 2nd Start 0: Use It
        attributes |= 0x20;
        if (sample.isReversed ())
            attributes |= 0x40;
        if (loops.isEmpty ())
            attributes |= 0x80;
        out.write (attributes);

        // loopTune (–99…+99 cents) not supported
        out.writeByte (0);

        out.write (formatChunk.getNumberOfChannels ());
        // 8/16
        final int bits = formatChunk.getSignicantBitsPerSample ();
        if (bits != 8 && bits != 16)
            throw new IOException (Functions.getMessage ("IDS_KMP_BIT_SIZE_NOT_SUPPORTED", Integer.toString (bits)));
        out.write (bits);

        out.writeInt (numSamples);

        if (bits == 8)
            out.write (data);
        else
        {
            // Flip bytes
            for (int i = 0; i < data.length; i += 2)
            {
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


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append (" - Sample ").append (this.sampleNumber).append (":\n");
        sb.append (" - Samplename: ").append (this.getUpdatedFilename ()).append ("\n");
        sb.append (" - Frequency: ").append (this.sampleRate).append ("\n");
        sb.append (" - Channels: ").append (this.channels).append ("\n");
        sb.append (" - Resolution: ").append (this.sampleResolution).append (" Bit\n");
        sb.append (" - Samples: ").append (this.numberOfSamples).append ("\n");
        sb.append (" - Reversed: ").append (this.isReversed () ? "Yes" : "No").append ("\n");
        sb.append (" - Start: ").append (this.getStart ()).append ("\n");
        sb.append (" - Key Range (low/root/high): ").append (this.getKeyLow ()).append ("/").append (this.getKeyRoot ()).append ("/").append (this.getKeyHigh ()).append ("\n");

        sb.append (" - Loop: ");
        if (this.loops.isEmpty ())
            sb.append ("Off\n");
        else
            sb.append (this.loops.get (0).getStart ()).append (" - ").append (this.loops.get (0).getEnd ()).append ("\n");

        return sb.toString ();
    }
}
