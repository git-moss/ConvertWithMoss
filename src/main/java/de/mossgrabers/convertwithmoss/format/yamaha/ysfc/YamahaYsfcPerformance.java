// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * A performance which is the metadata description of a sample in YSFC terms.
 *
 * @author Jürgen Moßgraber
 */
public class YamahaYsfcPerformance
{
    private String                                name;
    private final List<YamahaYsfcPerformancePart> parts           = new ArrayList<> ();
    private final YamahaYsfcVersion               sourceVersion;

    private byte []                               reverbBlock;
    private byte []                               variationBlock;
    private byte []                               masterEqBlock;
    private byte []                               masterEffectBlock;
    private byte []                               commonParameters;
    private byte []                               sceneData;
    private byte []                               controlBoxes;
    private byte []                               adPart;
    private byte []                               digitalInputPart;
    private byte []                               playSettings;
    private final String []                       assignableKnobs = new String [8];
    final List<YamahaYsfcPerformancePart>         allParts        = new ArrayList<> ();


    /**
     * Default constructor.
     *
     * @param version The version to use
     * @throws IOException Could not read the default parameters for the requested version
     */
    public YamahaYsfcPerformance (final YamahaYsfcVersion version) throws IOException
    {
        this.sourceVersion = version;
        if (version != YamahaYsfcVersion.MONTAGE)
            throw new IOException (Functions.getMessage ("IDS_YSFC_VERSION_NOT_SUPPORTED", version.getTitle ()));
        final byte [] data = Functions.rawFileFor ("de/mossgrabers/convertwithmoss/templates/ysfc/MontageDPFM.bin");
        this.read (new ByteArrayInputStream (data));
    }


    /**
     * Constructor which reads the performance from the input stream.
     *
     * @param in The input stream
     * @param version The format version of the YSFC file
     * @throws IOException Could not read the entry item
     */
    public YamahaYsfcPerformance (final InputStream in, final YamahaYsfcVersion version) throws IOException
    {
        this.sourceVersion = version;
        this.read (in);
    }


    /**
     * Get the name of the performance.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get all parts of the performance.
     * 
     * @return The parts
     */
    public List<YamahaYsfcPerformancePart> getParts ()
    {
        return this.parts;
    }


    /**
     * Read a performance from the input stream.
     *
     * @param in The input stream
     * @throws IOException Could not read the entry item
     */
    public void read (final InputStream in) throws IOException
    {
        this.readCommon (new ByteArrayInputStream (StreamUtils.readDataBlock (in, true)));
        this.readEffects (in);
        this.readParts (in);
    }


    private void readCommon (final InputStream in) throws IOException
    {
        this.name = StreamUtils.readASCII (in, 21).trim ();
        final int pos = this.name.indexOf (0);
        if (pos >= 0)
            this.name = this.name.substring (0, pos);

        // Common Parameters
        this.commonParameters = in.readNBytes (43);

        // Scene 1-8
        if (this.sourceVersion == YamahaYsfcVersion.MONTAGE)
            this.sceneData = in.readNBytes (8 * 11);
        else // MODX
            this.sceneData = in.readNBytes (8 * 21);

        // Assignable Knob 1-8
        for (int i = 0; i < 8; i++)
            this.assignableKnobs[i] = StreamUtils.readASCII (in, 17).trim ();

        // Control Box 1-16
        this.controlBoxes = in.readNBytes (16 * 9);
    }


    private void readEffects (final InputStream in) throws IOException
    {
        this.reverbBlock = StreamUtils.readDataBlock (in, true);
        this.variationBlock = StreamUtils.readDataBlock (in, true);
        this.masterEqBlock = StreamUtils.readDataBlock (in, true);
        this.masterEffectBlock = StreamUtils.readDataBlock (in, true);
    }


    private void readParts (final InputStream in) throws IOException
    {
        final int numberOfParts = (int) StreamUtils.readUnsigned32 (in, true);

        for (int i = 0; i < numberOfParts; i++)
            this.allParts.add (new YamahaYsfcPerformancePart (new ByteArrayInputStream (StreamUtils.readDataBlock (in, true)), this.sourceVersion));

        // Skip AD + Digital input parts
        this.adPart = StreamUtils.readDataBlock (in, true);
        this.digitalInputPart = StreamUtils.readDataBlock (in, true);

        // Read all elements

        // Only keep sample based parts (0 = AWM, 1 = AWM Drum, 2 = FM)
        for (int i = 0; i < numberOfParts; i++)
        {
            final int partType = (int) StreamUtils.readUnsigned32 (in, true);
            switch (partType)
            {
                // AWM
                case 0, 1:
                    // 8 for plain AWM or 73 for drums
                    final int numberOfElements = (int) StreamUtils.readUnsigned32 (in, true);
                    final YamahaYsfcPerformancePart part = this.allParts.get (i);
                    this.parts.add (part);
                    for (int el = 0; el < numberOfElements; el++)
                        part.addElement (new YamahaYsfcPartElement (in, this.sourceVersion));
                    break;

                // FM - not used
                case 2:
                    final int numberOfOperators = (int) StreamUtils.readUnsigned32 (in, true);
                    StreamUtils.readDataBlock (in, true);
                    for (int op = 0; op < numberOfOperators; op++)
                        StreamUtils.readDataBlock (in, true);
                    break;

                default:
                    throw new IOException (Functions.getMessage ("IDS_YSFC_UNKNOWN_PART_TYPE", Integer.toString (partType)));
            }
        }

        // Super knob & ARP settings
        this.playSettings = in.readAllBytes ();
    }


    /**
     * Write a performance to the output stream.
     *
     * @param out The output stream
     * @throws IOException Could not write the entry item
     */
    public void write (final OutputStream out) throws IOException
    {
        this.writeCommon (out);
        this.writeEffects (out);
        this.writeParts (out);
    }


    private void writeCommon (final OutputStream out) throws IOException
    {
        final ByteArrayOutputStream arrayOut = new ByteArrayOutputStream ();

        StreamUtils.writeASCII (arrayOut, StringUtils.optimizeName (this.name, 20), 21);

        arrayOut.write (this.commonParameters);
        arrayOut.write (this.sceneData);

        // Assignable Knob 1-8
        for (int i = 0; i < 8; i++)
            StreamUtils.writeASCII (arrayOut, StringUtils.rightPadSpaces (StringUtils.optimizeName (this.assignableKnobs[i], 16), 16), 17);

        // Control Box 1-16
        arrayOut.write (this.controlBoxes);

        StreamUtils.writeDataBlock (out, arrayOut.toByteArray (), true);
    }


    private void writeEffects (final OutputStream out) throws IOException
    {
        StreamUtils.writeDataBlock (out, this.reverbBlock, true);
        StreamUtils.writeDataBlock (out, this.variationBlock, true);
        StreamUtils.writeDataBlock (out, this.masterEqBlock, true);
        StreamUtils.writeDataBlock (out, this.masterEffectBlock, true);
    }


    private void writeParts (final OutputStream out) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, this.allParts.size (), true);

        for (final YamahaYsfcPerformancePart part: this.allParts)
            part.write (out);

        StreamUtils.writeDataBlock (out, this.adPart, true);
        StreamUtils.writeDataBlock (out, this.digitalInputPart, true);

        for (final YamahaYsfcPerformancePart part: this.allParts)
        {
            StreamUtils.writeUnsigned32 (out, part.getType(), true);

            // Always 8 AWM elements!
            StreamUtils.writeUnsigned32 (out, 8, true);
            for (final YamahaYsfcPartElement element: part.getElements ())
                element.write (out);
        }

        out.write (this.playSettings);
    }


    // TODO remove
    public static void main (final String [] args)
    {
        try (final FileOutputStream out = new FileOutputStream (new File ("C:\\Users\\mos\\Desktop\\result.bin")))
        {
            final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (YamahaYsfcVersion.MONTAGE);
            performance.write (out);
        }
        catch (final IOException e)
        {
            e.printStackTrace ();
        }
    }


    // TODO remove
    public static void main2 (final String [] args)
    {
        final String filenameX7U = "C:\\Privat\\Programming\\ConvertWithMoss\\Testdateien\\YamahaYSFC\\X7 - Montage\\01W Atmosphere.X7U";
        final String filenameX8L = "C:\\Privat\\Programming\\ConvertWithMoss\\Testdateien\\YamahaYSFC\\X8 - MODX\\Arilyn.X8L";

        YsfcFile ysfcFile;
        try
        {
            ysfcFile = new YsfcFile (new File (filenameX7U));
            final Map<String, YamahaYsfcChunk> chunks = ysfcFile.getChunks ();
            final YamahaYsfcChunk epfmChunk = chunks.get (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE);
            final YamahaYsfcChunk dpfmChunk = chunks.get (YamahaYsfcChunk.DATA_LIST_PERFORMANCE);
            if (epfmChunk == null || dpfmChunk == null)
                // TODO
                // this.notifier.logError ("IDS_YSFC_NO_MULTISAMPLE_DATA");
                // return Collections.emptyList ();
                return;

            final List<YamahaYsfcEntry> epfmListChunks = epfmChunk.getEntryListChunks ();
            final List<byte []> dpfmListChunks = dpfmChunk.getDataArrays ();

            if (epfmListChunks.size () != dpfmListChunks.size ())
                // TODO
                // this.notifier.logError ("IDS_YSFC_DIFFERENT_NUMBER_OF_WAVEFORM_CHUNKS");
                // return Collections.emptyList ();
                return;

            for (int i = 0; i < epfmListChunks.size (); i++)
            {
                final YamahaYsfcEntry yamahaYsfcEntry = epfmListChunks.get (i);
                final byte [] performanceData = dpfmListChunks.get (i);

                final YamahaYsfcPerformance yamahaYsfcPerformance = new YamahaYsfcPerformance (new ByteArrayInputStream (performanceData), ysfcFile.getVersion ());
            }

        }
        catch (final IOException e)
        {
            e.printStackTrace ();
        }
    }


    // TODO remove
    private static void dump (final YamahaYsfcEntry yamahaYsfcEntry, final byte [] performanceData, final int version)
    {
        // TODO Auto-generated method stub
        try
        {
            Files.write (new File ("C:\\Users\\mos\\Desktop\\Data\\" + yamahaYsfcEntry.getItemTitle () + "DPFM.bin").toPath (), performanceData);
        }
        catch (final IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace ();
        }

    }
}
