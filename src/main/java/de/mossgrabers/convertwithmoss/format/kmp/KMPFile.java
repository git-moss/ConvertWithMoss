// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private List<KSFFile>       ksfFiles        = new ArrayList<> ();


    /**
     * Constructor.
     * 
     * @param notifier
     *
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
     * Read and parse a SF2 file.
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
        for (int i = 0; i < this.ksfFiles.size (); i++)
        {
            final KSFFile ksf = this.ksfFiles.get (i);

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


    /**
     * Get the name.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.nameLong == null ? this.name : this.nameLong;
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
        for (int i = 0; i < this.ksfFiles.size (); i++)
        {
            final KSFFile ksfFile = this.ksfFiles.get (i);
            final int originalKey = in.read ();

            // What to do with this? Example files are all 0
            // originalKey & 0x80

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
                    final Integer internalIndex = Integer.valueOf (sampleFilename.substring (SAMPLE_INTERNAL.length ()));
                    this.notifier.logError ("IDS_KMP_ERR_INTERNAL_SAMPLE", internalIndex.toString ());
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
            final KSFFile ksfFile = this.ksfFiles.get (i);

            final int transpose = in.readByte ();
            if (transpose != 0)
                ksfFile.setTune (transpose);

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
     * Get the sample files.
     * 
     * @return The KSF files
     */
    public List<KSFFile> getKsfFiles ()
    {
        return this.ksfFiles;
    }
}
