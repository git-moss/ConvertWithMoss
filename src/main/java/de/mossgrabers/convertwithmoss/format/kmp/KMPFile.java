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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
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
    private static final String     KMP_MSP_ID      = "MSP1";
    /** ID for KMP name chunk. */
    private static final String     KMP_NAME_ID     = "NAME";
    /** ID for KMP relative parameter chunk 1. */
    private static final String     KMP_REL1_ID     = "RLP1";
    /** ID for KMP relative parameter chunk 2. */
    private static final String     KMP_REL2_ID     = "RLP2";
    /** ID for KMP relative parameter chunk 3. */
    private static final String     KMP_REL3_ID     = "RLP3";
    /** ID for KMP multi-sample number chunk. */
    private static final String     KMP_NUMBER_ID   = "MNO1";

    private static final int        KMP_MSP_SIZE    = 18;
    private static final int        KMP_NAME_SIZE   = 24;
    private static final int        KMP_REL1_SIZE   = 18;
    private static final int        KMP_REL2_SIZE   = 4;
    private static final int        KMP_REL3_SIZE   = 6;
    private static final int        KMP_NUMBER_SIZE = 4;

    private static final String     SAMPLE_SKIPPED  = "SKIPPEDSAMPL";
    private static final String     SAMPLE_INTERNAL = "INTERNAL";

    private final INotifier         notifier;
    private final File              sampleFolder1;
    private final File              sampleFolder2;

    private String                  name;
    private int                     numSamples;
    private String                  nameLong;

    private final List<ISampleZone> zones;
    private boolean                 gain12dB        = false;
    private boolean                 maxVolume       = false;


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param dosFilename Classic 8.3 file name
     * @param groupName The name of the group
     * @param zones The sample zones
     * @param gain12dB Enables the +12dB option, if true
     * @param maxVolume Sets all sample volumes to +99, if true
     */
    public KMPFile (final INotifier notifier, final String dosFilename, final String groupName, final List<ISampleZone> zones, final boolean gain12dB, final boolean maxVolume)
    {
        this.notifier = notifier;
        this.sampleFolder1 = null;
        this.sampleFolder2 = null;

        // Korg M3 crashes if samples are not in ascending order!
        this.zones = sortByKeyHigh (zones);

        this.numSamples = this.zones.size ();
        this.gain12dB = gain12dB;
        this.maxVolume = maxVolume;

        this.name = dosFilename;
        this.nameLong = groupName;
    }


    /**
     * Constructor.
     *
     * @param notifier For logging errors
     * @param kmpFile The KMP file
     * @param zones Where to add the sample zones
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public KMPFile (final INotifier notifier, final File kmpFile, final List<ISampleZone> zones) throws IOException, ParseException
    {
        this.notifier = notifier;

        this.sampleFolder1 = kmpFile.getParentFile ();
        this.sampleFolder2 = new File (this.sampleFolder1, FileUtils.getNameWithoutType (kmpFile));

        this.zones = zones;
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

        // Remove all 'skipped sample' zones
        final List<ISampleZone> cleanedZones = new ArrayList<> ();
        for (final ISampleZone zone: this.zones)
        {
            if (!SAMPLE_SKIPPED.equals (zone.getName ()))
                cleanedZones.add (zone);
        }
        this.zones.clear ();
        this.zones.addAll (cleanedZones);
    }


    private void readMultisampleChunk (final DataInputStream in) throws IOException
    {
        this.name = new String (in.readNBytes (16)).trim ();
        this.numSamples = in.read ();

        // useSecondStart, not sure what to do with it
        in.read ();

        for (int i = 0; i < this.numSamples; i++)
            this.zones.add (new DefaultSampleZone ());
    }


    private void readParameterChunk1 (final DataInputStream in) throws IOException, ParseException
    {
        int lowerKey = 0;
        for (final ISampleZone zone: this.zones)
        {
            final int originalKey = in.read ();

            zone.setKeyTracking ((originalKey & 0x80) > 0 ? 0 : 1);
            zone.setKeyRoot (originalKey & 0x7F);
            zone.setKeyLow (lowerKey);
            zone.setKeyHigh (in.read ());
            lowerKey = AbstractCreator.limitToDefault (zone.getKeyHigh (), 127) + 1;
            zone.setTune (in.readByte () / 100.0);

            // Range is [-99..99] but totally unclear to what that relates in dB.
            // Let's keep it between [0..6]dB
            zone.setGain ((Math.clamp (in.readByte (), -99, 99) / 99.0 + 1) / 3.0);

            // Panorama - unused in KMP itself, 64 is center
            in.read ();

            // Filter Cutoff - unused in KMP itself
            in.readByte ();

            String sampleFilename = new String (in.readNBytes (12));

            while (sampleFilename != null)
            {
                if (SAMPLE_SKIPPED.equals (sampleFilename))
                {
                    zone.setName (SAMPLE_SKIPPED);
                    this.notifier.log ("IDS_KMP_SKIPPED_SAMPLE");
                    break;
                }

                if (sampleFilename.startsWith (SAMPLE_INTERNAL))
                    try
                    {
                        final int internalIndex = Integer.parseInt (sampleFilename.substring (SAMPLE_INTERNAL.length ()));
                        throw new IOException (Functions.getMessage ("IDS_KMP_ERR_INTERNAL_SAMPLE", Integer.toString (internalIndex)));
                    }
                    catch (final NumberFormatException ex)
                    {
                        // All good, not a reference to internal sample memory
                    }

                sampleFilename = this.readKSFZone (zone, sampleFilename);
            }
        }
    }


    private void readParameterChunk2 (final DataInputStream in) throws IOException
    {
        for (int i = 0; i < this.zones.size (); i++)
        {
            // Transpose
            in.readByte ();
            // Ignore Resonance, Attack and Decay
            in.readByte ();
            in.readByte ();
            in.readByte ();
        }
    }


    private String readKSFZone (final ISampleZone zone, final String filename) throws IOException, ParseException
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
            return KSFFile.read (stream, zone);
        }
    }


    private static void assertSize (final String chunk, final int dataSize, final int expectedSize) throws ParseException
    {
        if (dataSize != expectedSize)
            throw new ParseException (Functions.getMessage ("IDS_KMP_WRONG_CHUNK_LENGTH", chunk, Integer.toString (dataSize), Integer.toString (expectedSize)));
    }


    /**
     * Write a KMP file. Names will get -L/-R appended for LEFT/RIGHT. KMP Index needs to be 0 for
     * left and 1 for right (index is also on the 3rd number position of KSF name: MS001000.KSF).
     *
     * @param folder The folder of the KMP file
     * @param outputStream Where to write the file to
     * @param kmpIndex The index of the KMP
     * @param kmpChannel The channel to write
     * @throws IOException Could not read the file
     */
    public void write (final File folder, final OutputStream outputStream, final int kmpIndex, final KMPChannel kmpChannel) throws IOException
    {
        final DataOutputStream out = new DataOutputStream (outputStream);

        this.writeMultisampleChunk (out, kmpChannel);
        writeNumberChunk (out, kmpIndex);
        this.writeParameterChunk1 (out, kmpIndex);
        this.writeParameterChunk2 (out);
        this.writeNameChunk (out, kmpChannel);
        this.writeParameterChunk3 (out);

        this.writeKSFZones (folder, kmpIndex, kmpChannel);
    }


    private void writeMultisampleChunk (final DataOutputStream out, final KMPChannel kmpChannel) throws IOException
    {
        out.write (KMP_MSP_ID.getBytes ());
        out.writeInt (KMP_MSP_SIZE);

        out.write (this.createName (16, kmpChannel).getBytes ());
        out.write (this.numSamples);

        // useSecondStart (1 = not use) not sure what that is about
        out.write (1);
    }


    private void writeNameChunk (final DataOutputStream out, final KMPChannel kmpChannel) throws IOException
    {
        out.write (KMP_NAME_ID.getBytes ());
        out.writeInt (KMP_NAME_SIZE);
        out.write (this.createName (24, kmpChannel).getBytes ());
    }


    private static void writeNumberChunk (final DataOutputStream out, final int kmpIndex) throws IOException
    {
        out.write (KMP_NUMBER_ID.getBytes ());
        out.writeInt (KMP_NUMBER_SIZE);

        // The sample number in the bank
        out.writeInt (kmpIndex);
    }


    private void writeParameterChunk1 (final DataOutputStream out, final int kmpIndex) throws IOException
    {
        out.write (KMP_REL1_ID.getBytes ());
        out.writeInt (this.numSamples * KMP_REL1_SIZE);

        for (int i = 0; i < this.zones.size (); i++)
        {
            final ISampleZone zone = this.zones.get (i);

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

            // Range is [-99..99] but totally unclear to what that relates in dB.
            // Let's keep it between [0..6]dB
            out.writeByte (this.maxVolume ? 99 : (byte) Math.clamp (Math.round (Math.clamp (zone.getGain (), 0, 6) / 3.0 - 1.0) * 99.0, 0, 99));

            // Panorama - unused in KMP itself, 64 is center
            out.write (64);

            // Filter Cutoff - unused in KMP itself
            out.writeByte (0);

            out.write (String.format ("MS%03d%03d.KSF", Integer.valueOf (kmpIndex), Integer.valueOf (i)).getBytes ());
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


    private void writeKSFZones (final File folder, final int kmpIndex, final KMPChannel kmpChannel) throws IOException
    {
        for (int i = 0; i < this.zones.size (); i++)
        {
            final ISampleZone zone = this.zones.get (i);
            final String filename = String.format ("MS%03d%03d.KSF", Integer.valueOf (kmpIndex), Integer.valueOf (i));
            try (final OutputStream out = new FileOutputStream (new File (folder, filename)))
            {
                KSFFile.write (zone, i, out, this.gain12dB, kmpChannel);
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


    private String createName (final int maxLength, final KMPChannel kmpChannel)
    {
        final String paddedName = StringUtils.rightPadSpaces (StringUtils.fixASCII (this.nameLong), maxLength);
        switch (kmpChannel)
        {
            case LEFT:
                return paddedName.substring (0, maxLength - 2) + "-L";

            case RIGHT:
                return paddedName.substring (0, maxLength - 2) + "-R";

            default:
                return paddedName;
        }
    }


    private static List<ISampleZone> sortByKeyHigh (final List<ISampleZone> sampleZones)
    {
        Collections.sort (sampleZones, (o1, o2) -> Integer.compare (o1.getKeyHigh (), o2.getKeyHigh ()));
        return sampleZones;
    }
}
