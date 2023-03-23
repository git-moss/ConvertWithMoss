// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


/**
 * Accessor to a Korg Multisample (KMP) file.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KMPFile
{
    /** ID for KMP chunk. */
    private static final String KMP_MSP_ID      = "MSP1";
    /** ID for KMP name chunk. */
    private static final String KMP_NAME_ID     = "NAME";
    /** ID for KMP relative parameter chunk 1. */
    private static final String KMP_REL1_ID     = "RLP1";
    /** ID for KMP relative parameter chunk 2. */
    private static final String KMP_REL2_ID     = "RLP2";
    /** ID for KMP relative parameter chunk 3. */
    private static final String KMP_REL3_ID     = "RLP3";
    /** ID for KMP multisample number chunk. */
    private static final String KMP_NUMBER_ID   = "MNO1";

    private static final int    KMP_MSP_SIZE    = 18;
    private static final int    KMP_NAME_SIZE   = 24;
    private static final int    KMP_REL1_SIZE   = 18;
    private static final int    KMP_REL2_SIZE   = 4;
    private static final int    KMP_REL3_SIZE   = 6;
    private static final int    KMP_NUMBER_SIZE = 4;

    private static final String SAMPLE_SKIPPED  = "SKIPPEDSAMPL";
    private static final String SAMPLE_INTERNAL = "INTERNAL";

    private final INotifier     notifier;
    private final File          sampleFolder1;
    private final File          sampleFolder2;

    private String              name;
    private int                 numSamples;
    private String              nameLong;

    private final List<KSFFile> ksfFiles        = new ArrayList<> ();
    private IVelocityLayer      layer;


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param dosFilename Classic 8.3 file name
     * @param layerName The name of the layer
     * @param layer The layer
     */
    public KMPFile (final INotifier notifier, final String dosFilename, final String layerName, final IVelocityLayer layer)
    {
        this.notifier = notifier;
        this.sampleFolder1 = null;
        this.sampleFolder2 = null;

        this.layer = layer;
        this.numSamples = this.layer.getSampleMetadata ().size ();

        this.name = dosFilename;
        this.nameLong = layerName;
    }


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param kmpFile The KMP file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public KMPFile (final INotifier notifier, final File kmpFile) throws IOException, ParseException
    {
        this.notifier = notifier;

        this.sampleFolder1 = kmpFile.getParentFile ();
        this.sampleFolder2 = new File (this.sampleFolder1, FileUtils.getNameWithoutType (kmpFile));

        try (final FileInputStream stream = new FileInputStream (kmpFile))
        {
            this.read (stream);
        }
    }


    /**
     * Get the name.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.nameLong == null ? this.name : this.nameLong;
    }


    /**
     * Get the sample files.
     *
     * @return The KSF files
     */
    public List<KSFFile> getKsfFiles ()
    {
        return this.ksfFiles;
    }


    /**
     * Read and parse a KMP file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    private void read (final InputStream inputStream) throws IOException, ParseException
    {
        final DataInputStream in = new DataInputStream (inputStream);

        while (true)
        {
            final String id = new String (in.readNBytes (4));
            final int dataSize = in.readInt ();

            switch (id)
            {
                case KMP_MSP_ID:
                    assertSize (id, dataSize, KMP_MSP_SIZE);
                    this.readMultisampleChunk (in);
                    break;

                case KMP_NAME_ID:
                    assertSize (id, dataSize, KMP_NAME_SIZE);
                    this.nameLong = new String (in.readNBytes (24)).trim ();
                    break;

                case KMP_NUMBER_ID:
                    assertSize (id, dataSize, KMP_NUMBER_SIZE);
                    // The sample number in the bank
                    in.readInt ();
                    break;

                case KMP_REL1_ID:
                    assertSize (id, dataSize, this.numSamples * KMP_REL1_SIZE);
                    this.readParameterChunk1 (in);
                    break;

                case KMP_REL2_ID:
                    assertSize (id, dataSize, this.numSamples * KMP_REL2_SIZE);
                    this.readParameterChunk2 (in);
                    break;

                case KMP_REL3_ID:
                    // Ignore Drive, Boost and EQ settings
                    assertSize (id, dataSize, this.numSamples * KMP_REL3_SIZE);
                    final int readSize2 = (int) in.skip (dataSize);
                    assertSize (id, readSize2, dataSize);
                    break;

                default:
                    throw new ParseException (Functions.getMessage ("IDS_KMP_UNKNOWN_CHUNK", id));
            }

            if (in.available () == 0)
                break;
        }

        this.readKSFFiles ();
    }


    private void readKSFFiles () throws IOException, ParseException
    {
        for (final KSFFile ksf: this.ksfFiles)
        {
            File ksfFile = new File (this.sampleFolder1, ksf.getFilename ());
            if (!ksfFile.exists ())
            {
                ksfFile = new File (this.sampleFolder2, ksf.getFilename ());
                if (!ksfFile.exists ())
                    throw new IOException (Functions.getMessage ("IDS_KMP_ERR_KSF_NOT_FOUND", ksfFile.getAbsolutePath ()));
            }

            try (final FileInputStream stream = new FileInputStream (ksfFile))
            {
                ksf.read (stream);
            }
        }
    }


    private void readMultisampleChunk (final DataInputStream in) throws IOException
    {
        this.name = new String (in.readNBytes (16)).trim ();
        this.numSamples = in.read ();

        // useSecondStart not sure what to do with it
        in.read ();

        for (int i = 0; i < this.numSamples; i++)
            this.ksfFiles.add (new KSFFile ());
    }


    private void readParameterChunk1 (final DataInputStream in) throws IOException
    {
        int lowerKey = 0;
        for (final KSFFile ksfFile: this.ksfFiles)
        {
            final int originalKey = in.read ();

            ksfFile.setKeyTracking ((originalKey & 0x80) > 0 ? 1 : 0);
            ksfFile.setKeyRoot (originalKey & 0x7F);
            ksfFile.setKeyLow (lowerKey);
            ksfFile.setKeyHigh (in.read ());
            lowerKey = ksfFile.getKeyHigh () + 1;
            ksfFile.setTune (in.readByte () / 100.0);
            ksfFile.setGain (in.readByte () / 100.0 * 12.0);

            // Panorama - unused in KMP itself, 64 is center
            in.read ();

            // Filter Cutoff - unused in KMP itself
            in.readByte ();

            final String sampleFilename = new String (in.readNBytes (12));

            if (SAMPLE_SKIPPED.equals (sampleFilename))
                this.notifier.logError ("IDS_KMP_ERR_SKIPPED_SAMPLE");
            else if (sampleFilename.startsWith (SAMPLE_INTERNAL))
            {
                try
                {
                    final int internalIndex = Integer.parseInt (sampleFilename.substring (SAMPLE_INTERNAL.length ()));
                    this.notifier.logError ("IDS_KMP_ERR_INTERNAL_SAMPLE", Integer.toString (internalIndex));
                }
                catch (final NumberFormatException ex)
                {
                    // All good, not a reference to internal sample memory
                }
            }

            ksfFile.setFilename (sampleFilename);
        }
    }


    private void readParameterChunk2 (final DataInputStream in) throws IOException
    {
        for (int i = 0; i < this.ksfFiles.size (); i++)
        {
            // Transpose
            in.readByte ();
            // Ignore Resonance, Attack and Decay
            in.readByte ();
            in.readByte ();
            in.readByte ();
        }
    }


    private static void assertSize (final String chunk, final int dataSize, final int expectedSize) throws ParseException
    {
        if (dataSize != expectedSize)
            throw new ParseException (Functions.getMessage ("IDS_KMP_WRONG_CHUNK_LENGTH", chunk, Integer.toString (dataSize), Integer.toString (expectedSize)));
    }


    /**
     * Write a KMP file.
     *
     * @param folder The folder of the KMP file
     * @param outputStream Where to write the file to
     * @throws IOException Could not read the file
     */
    public void write (final File folder, final OutputStream outputStream) throws IOException
    {
        final DataOutputStream out = new DataOutputStream (outputStream);

        this.writeMultisampleChunk (out);
        writeNumberChunk (out);
        this.writeParameterChunk1 (out);
        this.writeParameterChunk2 (out);
        this.writeNameChunk (out);
        this.writeParameterChunk3 (out);

        this.writeKSFFiles (folder);
    }


    private void writeMultisampleChunk (final DataOutputStream out) throws IOException
    {
        out.write (KMP_MSP_ID.getBytes ());
        out.writeInt (KMP_MSP_SIZE);

        out.write (StringUtils.rightPadSpaces (StringUtils.fixASCII (this.nameLong), 16).getBytes ());
        out.write (this.numSamples);

        // useSecondStart (1 = not use) not sure what that is about
        out.write (1);
    }


    private void writeNameChunk (final DataOutputStream out) throws IOException
    {
        out.write (KMP_NAME_ID.getBytes ());
        out.writeInt (KMP_NAME_SIZE);
        out.write (StringUtils.rightPadSpaces (StringUtils.fixASCII (this.nameLong), 24).getBytes ());
    }


    private static void writeNumberChunk (final DataOutputStream out) throws IOException
    {
        out.write (KMP_NUMBER_ID.getBytes ());
        out.writeInt (KMP_NUMBER_SIZE);

        // The sample number in the bank
        out.writeInt (0);
    }


    private void writeParameterChunk1 (final DataOutputStream out) throws IOException
    {
        out.write (KMP_REL1_ID.getBytes ());
        out.writeInt (this.numSamples * KMP_REL1_SIZE);

        final List<ISampleMetadata> sampleMetadata = this.layer.getSampleMetadata ();
        for (int i = 0; i < sampleMetadata.size (); i++)
        {
            final ISampleMetadata sample = sampleMetadata.get (i);

            int originalKey = sample.getKeyRoot ();
            if (sample.getKeyTracking () == 0)
                originalKey |= 0x80;
            out.write (originalKey);

            out.write (sample.getKeyHigh ());
            out.writeByte ((byte) Math.round (sample.getTune () * 100.0));
            out.writeByte ((byte) Math.round (sample.getGain () * 100.0 / 12.0));

            // Panorama - unused in KMP itself, 64 is center
            out.write (64);

            // Filter Cutoff - unused in KMP itself
            out.writeByte (0);

            out.write (String.format ("MS%06d.KSF", Integer.valueOf (i)).getBytes ());
        }
    }


    private void writeParameterChunk2 (final DataOutputStream out) throws IOException
    {
        out.write (KMP_REL2_ID.getBytes ());
        out.writeInt (this.numSamples * KMP_REL2_SIZE);

        for (int i = 0; i < this.numSamples; i++)
        {
            // Transposing
            out.writeByte (0);
            // Resonance
            out.writeByte (0);
            // Attack
            out.writeByte (0);
            // Decay
            out.writeByte (0);
        }
    }


    private void writeParameterChunk3 (final DataOutputStream out) throws IOException
    {
        out.write (KMP_REL3_ID.getBytes ());
        out.writeInt (this.numSamples * KMP_REL3_SIZE);

        for (int i = 0; i < this.numSamples; i++)
        {
            // Drive
            out.writeByte (0);
            // Boost
            out.writeByte (0);
            // Low EQ Level
            out.writeByte (0);
            // Mid EQ Level
            out.writeByte (0);
            // High EQ Level
            out.writeByte (0);
            // Unused
            out.writeByte (0);
        }
    }


    private void writeKSFFiles (final File folder) throws IOException
    {
        final List<ISampleMetadata> sampleMetadata = this.layer.getSampleMetadata ();
        for (int i = 0; i < sampleMetadata.size (); i++)
        {
            final ISampleMetadata sample = sampleMetadata.get (i);
            final String filename = String.format ("MS%06d.KSF", Integer.valueOf (i));
            try (final OutputStream out = new FileOutputStream (new File (folder, filename)))
            {
                KSFFile.write (sample, i, out);

                this.notifier.log ("IDS_NOTIFY_PROGRESS");
                if (i > 0 && i % 80 == 0)
                    this.notifier.log ("IDS_NOTIFY_LINE_FEED");
            }
            catch (final ParseException | CompressionNotSupportedException ex)
            {
                throw new IOException (ex);
            }
        }
    }
}
