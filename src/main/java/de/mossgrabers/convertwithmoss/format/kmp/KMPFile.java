// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.CompressionNotSupportedException;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Accessor to a Korg Multisample (KMP) file.
 *
 * @author Jürgen Moßgraber
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
    /** ID for KMP multi-sample number chunk. */
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

    private final IGroup        group;


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param dosFilename Classic 8.3 file name
     * @param groupName The name of the group
     * @param group The group
     */
    public KMPFile (final INotifier notifier, final String dosFilename, final String groupName, final IGroup group)
    {
        this.notifier = notifier;
        this.sampleFolder1 = null;
        this.sampleFolder2 = null;

        this.group = group;
        this.numSamples = this.group.getSampleZones ().size ();

        this.name = dosFilename;
        this.nameLong = groupName;
    }


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param kmpFile The KMP file
     * @param group The group where to add the KSF zones
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public KMPFile (final INotifier notifier, final File kmpFile, final IGroup group) throws IOException, ParseException
    {
        this.notifier = notifier;

        this.sampleFolder1 = kmpFile.getParentFile ();
        this.sampleFolder2 = new File (this.sampleFolder1, FileUtils.getNameWithoutType (kmpFile));

        this.group = group;
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
     * Read and parse a KMP file.
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
                    // The sample number in the bank, not used
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
    }


    private void readMultisampleChunk (final DataInputStream in) throws IOException
    {
        this.name = new String (in.readNBytes (16)).trim ();
        this.numSamples = in.read ();

        // useSecondStart, not sure what to do with it
        in.read ();

        for (int i = 0; i < this.numSamples; i++)
            this.group.addSampleZone (new DefaultSampleZone ());
    }


    private void readParameterChunk1 (final DataInputStream in) throws IOException, ParseException
    {
        int lowerKey = 0;
        for (final ISampleZone zone: this.group.getSampleZones ())
        {
            final int originalKey = in.read ();

            zone.setKeyTracking ((originalKey & 0x80) > 0 ? 0 : 1);
            zone.setKeyRoot (originalKey & 0x7F);
            zone.setKeyLow (lowerKey);
            zone.setKeyHigh (in.read ());
            lowerKey = AbstractCreator.limitToDefault (zone.getKeyHigh (), 127) + 1;
            zone.setTune (in.readByte () / 100.0);
            zone.setGain (in.readByte () / 100.0 * 12.0);

            // Panorama - unused in KMP itself, 64 is center
            in.read ();

            // Filter Cutoff - unused in KMP itself
            in.readByte ();

            final String sampleFilename = new String (in.readNBytes (12));

            if (SAMPLE_SKIPPED.equals (sampleFilename))
                this.notifier.logError ("IDS_KMP_ERR_SKIPPED_SAMPLE");
            else if (sampleFilename.startsWith (SAMPLE_INTERNAL))
                try
                {
                    final int internalIndex = Integer.parseInt (sampleFilename.substring (SAMPLE_INTERNAL.length ()));
                    this.notifier.logError ("IDS_KMP_ERR_INTERNAL_SAMPLE", Integer.toString (internalIndex));
                }
                catch (final NumberFormatException ex)
                {
                    // All good, not a reference to internal sample memory
                }

            this.readKSFZone (zone, sampleFilename);
        }
    }


    private void readParameterChunk2 (final DataInputStream in) throws IOException
    {
        for (int i = 0; i < this.group.getSampleZones ().size (); i++)
        {
            // Transpose
            in.readByte ();
            // Ignore Resonance, Attack and Decay
            in.readByte ();
            in.readByte ();
            in.readByte ();
        }
    }


    private void readKSFZone (final ISampleZone zone, final String filename) throws IOException, ParseException
    {
        File ksfFile = new File (this.sampleFolder1, filename);
        if (!ksfFile.exists ())
        {
            ksfFile = new File (this.sampleFolder2, filename);
            if (!ksfFile.exists ())
                throw new IOException (Functions.getMessage ("IDS_KMP_ERR_KSF_NOT_FOUND", ksfFile.getAbsolutePath ()));
        }

        try (final FileInputStream stream = new FileInputStream (ksfFile))
        {
            KSFFile.read (stream, zone);
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

        this.writeKSFZones (folder);
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

        final List<ISampleZone> zones = this.group.getSampleZones ();
        for (int i = 0; i < zones.size (); i++)
        {
            final ISampleZone zone = zones.get (i);

            final int keyLow = AbstractCreator.limitToDefault (zone.getKeyHigh (), 0);
            final int keyHigh = AbstractCreator.limitToDefault (zone.getKeyHigh (), 127);

            int originalKey = zone.getKeyRoot ();
            if (originalKey < 0)
                originalKey = keyLow;
            if (zone.getKeyTracking () == 0)
                originalKey |= 0x80;
            out.write (originalKey);

            out.write (keyHigh);
            out.writeByte ((byte) Math.round (zone.getTune () * 100.0));
            out.writeByte ((byte) MathUtils.clamp (Math.round (MathUtils.clamp (zone.getGain (), -12, 12) / 12.0 * 100.0), -99, 99));

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


    private void writeKSFZones (final File folder) throws IOException
    {
        final List<ISampleZone> sampleMetadata = this.group.getSampleZones ();
        for (int i = 0; i < sampleMetadata.size (); i++)
        {
            final ISampleZone zone = sampleMetadata.get (i);
            final String filename = String.format ("MS%06d.KSF", Integer.valueOf (i));
            try (final OutputStream out = new FileOutputStream (new File (folder, filename)))
            {
                KSFFile.write (zone, i, out);
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
