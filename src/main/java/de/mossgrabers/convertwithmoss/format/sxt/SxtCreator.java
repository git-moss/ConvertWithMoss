// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sxt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * Creator for SXT files.
 *
 * @author Jürgen Moßgraber
 */
public class SxtCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public SxtCreator (final INotifier notifier)
    {
        super ("Propellerhead Reason NN-XT", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "SXT");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "SXT");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        final File outputFolder = new File (destinationFolder, sampleName);
        safeCreateDirectory (outputFolder);

        final File multiFile = new File (outputFolder, sampleName + ".sxt");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        storeMultisample (multisampleSource, multiFile);

        // Store all samples
        this.writeSamples (outputFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile) throws IOException
    {
        final ByteArrayOutputStream groupsOutputStream = new ByteArrayOutputStream ();
        final ByteArrayOutputStream zoneOutputStream = new ByteArrayOutputStream ();
        final ByteArrayOutputStream zoneSampleReferenceOutputStream = new ByteArrayOutputStream ();
        final List<ByteArrayOutputStream> sampleReferenceOutputStreams = new ArrayList<> ();

        final String multiSampleName = createSafeFilename (multisampleSource.getName ());

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        writeVersion (groupsOutputStream, SxtChunkConstants.VERSION_2_0_0);
        StreamUtils.writeUnsigned32 (groupsOutputStream, groups.size (), true);

        int overallZoneCount = 0;
        for (int groupIndex = 0; groupIndex < groups.size (); groupIndex++)
        {
            final IGroup group = groups.get (groupIndex);
            final SxtGroup sxtGroup = new SxtGroup ();
            sxtGroup.write (groupsOutputStream, SxtChunkConstants.VERSION_2_0_0);

            final List<ISampleZone> zones = group.getSampleZones ();
            overallZoneCount += zones.size ();

            for (int zoneIndex = 0; zoneIndex < zones.size (); zoneIndex++)
            {
                final ISampleZone zone = zones.get (zoneIndex);

                final ByteArrayOutputStream sampleReferenceOutputStream = new ByteArrayOutputStream ();
                sampleReferenceOutputStreams.add (sampleReferenceOutputStream);
                writeSampleReference (sampleReferenceOutputStream, zone.getName () + ".wav");
                writeZoneSampleReference (zoneIndex, zoneSampleReferenceOutputStream);

                // Fill zone
                final SxtZone sxtZone = new SxtZone ();
                sxtZone.groupIndex = groupIndex;
                sxtZone.fillFrom (zone);
                sxtZone.write (zoneOutputStream);
            }
        }

        // Combine all sample references
        final ByteArrayOutputStream referencesOutputStream = new ByteArrayOutputStream ();
        for (final ByteArrayOutputStream out: sampleReferenceOutputStreams)
            writeChunk (referencesOutputStream, SxtChunkConstants.REFERENCE, out.toByteArray (), false, false);

        // Combine all parts of the BODY chunk
        final ByteArrayOutputStream bodyOutputStream = new ByteArrayOutputStream ();
        writeVersion (bodyOutputStream, SxtChunkConstants.VERSION_1_0_0);
        bodyOutputStream.write (groupsOutputStream.toByteArray ());

        writeVersion (bodyOutputStream, SxtChunkConstants.VERSION_2_2_0);
        StreamUtils.writeUnsigned32 (bodyOutputStream, overallZoneCount, true);

        bodyOutputStream.write (zoneOutputStream.toByteArray ());
        bodyOutputStream.write (zoneSampleReferenceOutputStream.toByteArray ());
        // Set Last Patch Reference + Last Sample Reference to ignore
        for (int i = 0; i < 2; i++)
        {
            writeVersion (bodyOutputStream, SxtChunkConstants.VERSION_1_0_0);
            writeVersion (bodyOutputStream, SxtChunkConstants.VERSION_1_2_0);
            bodyOutputStream.write (0);
            writeVersion (bodyOutputStream, SxtChunkConstants.VERSION_1_4_0);
            bodyOutputStream.write (0);
        }

        // Combine all chunks
        final ByteArrayOutputStream childChunksOutputStream = new ByteArrayOutputStream ();
        writeChunk (childChunksOutputStream, SxtChunkConstants.REFERENCES, referencesOutputStream.toByteArray (), false, true);
        writeDescriptionChunk (childChunksOutputStream, multiSampleName);
        writeAuthorChunk (childChunksOutputStream, multisampleSource.getMetadata ());
        writeParameterChunk (childChunksOutputStream);
        writeChunk (childChunksOutputStream, SxtChunkConstants.BODY, bodyOutputStream.toByteArray (), false, false);

        // Finally wrap into a FORM chunk and write the file
        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            writeChunk (out, SxtChunkConstants.PATCH, childChunksOutputStream.toByteArray (), true, false);
        }
    }


    /**
     * Write a description chunk.
     *
     * @param out The output stream
     * @param multiSampleName The patch name from
     * @throws IOException Could not read the data or data is invalid
     */
    private static void writeDescriptionChunk (final ByteArrayOutputStream out, final String multiSampleName) throws IOException
    {
        final ByteArrayOutputStream contentOutStream = new ByteArrayOutputStream ();
        writeVersion (contentOutStream, SxtChunkConstants.VERSION_1_3_0);
        // Reserved
        contentOutStream.write (18);
        writeString (contentOutStream, multiSampleName);
        writeString (contentOutStream, "NNXT Digital Sampler");

        writeChunk (out, SxtChunkConstants.DESC, contentOutStream.toByteArray (), false, false);
    }


    /**
     * Write an author chunk.
     *
     * @param out The output stream
     * @param metadata The patch name from
     * @throws IOException Could not read the data or data is invalid
     */
    private static void writeAuthorChunk (final ByteArrayOutputStream out, final IMetadata metadata) throws IOException
    {
        final ByteArrayOutputStream contentOutStream = new ByteArrayOutputStream ();
        writeVersion (contentOutStream, SxtChunkConstants.VERSION_1_3_0);
        writeString (contentOutStream, metadata.getCreator ());
        writeString (contentOutStream, metadata.getDescription ());

        writeChunk (out, SxtChunkConstants.AUTHOR, contentOutStream.toByteArray (), false, false);
    }


    /**
     * Write an empty parameter chunk.
     *
     * @param out The output stream
     * @throws IOException Could not read the data or data is invalid
     */
    private static void writeParameterChunk (final ByteArrayOutputStream out) throws IOException
    {
        final ByteArrayOutputStream contentOutStream = new ByteArrayOutputStream ();
        writeVersion (contentOutStream, SxtChunkConstants.VERSION_1_0_0);
        // Reserved
        contentOutStream.write (9);
        contentOutStream.write (5);
        // External controller source
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (6);
        // High Quality Interpolation
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (7);
        // Filter Frequency
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (8);
        // Filter Resonance
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (9);
        // Volume Attack
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (10);
        // Volume Decay
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (11);
        // Volume Release
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (12);
        // Modulation Envelope Decay
        contentOutStream.write (0);
        // Reserved
        contentOutStream.write (13);
        // Master Volume
        contentOutStream.write (100);
        // Reserved
        contentOutStream.write (0);

        writeChunk (out, SxtChunkConstants.PARAMETERS, contentOutStream.toByteArray (), false, false);
    }


    /**
     * Write a reference chunk.
     *
     * @param out The output stream to write to
     * @param sampleFileName The name of the sample file
     * @throws IOException Could not write the data
     */
    private static void writeSampleReference (final ByteArrayOutputStream out, final String sampleFileName) throws IOException
    {
        writeVersion (out, SxtChunkConstants.VERSION_1_1_0);
        writePaths (out, sampleFileName);
        // userInterfaceSampleName
        writeString (out, FileUtils.getNameWithoutType (new File (sampleFileName)));

        // refillName
        writeString (out, "");
        // sampleURL
        writeString (out, "");

        // Reserved
        out.write (13);

        // 'Package Name' not used
        writeString (out, "");
    }


    /**
     * Handles reading the relative, absolute and database paths.
     *
     * @param out The output stream to write to
     * @param sampleFileName The name of the sample file
     * @throws IOException Could not read the paths
     */
    private static void writePaths (final ByteArrayOutputStream out, final String sampleFileName) throws IOException
    {
        // Read the relative path
        writeVersion (out, SxtChunkConstants.VERSION_1_1_0);
        out.write (1);
        // 'Step up count' is 0
        StreamUtils.writeUnsigned32 (out, 0, true);
        // Sample file is in a sub-folder
        StreamUtils.writeUnsigned32 (out, 1, true);
        writeString (out, sampleFileName);

        // out.write (0);

        // Write the database path as invalid
        writeVersion (out, SxtChunkConstants.VERSION_1_2_0);
        out.write (0);

        // Write the absolute path as invalid
        writeVersion (out, SxtChunkConstants.VERSION_1_5_0);
        out.write (0);
    }


    /**
     * Create the sample reference block.
     *
     * @param zoneIndex The zone index to use as the sample index
     * @param out The output stream to write the block to
     * @throws IOException Could not write the data
     */
    private static void writeZoneSampleReference (final int zoneIndex, final OutputStream out) throws IOException
    {
        out.write (1);
        writeVersion (out, SxtChunkConstants.VERSION_4_1_0);
        out.write (1);
        StreamUtils.writeUnsigned32 (out, zoneIndex, true);
    }


    /**
     * Writes the version information to the given output stream.
     *
     * @param out The output stream to write to
     * @param version The version to write
     * @throws IOException Could not read the data
     */
    private static void writeVersion (final OutputStream out, final int version) throws IOException
    {
        int v = version;

        out.write (0xBC);
        final int major = v / 1000000;
        v -= major * 1000000;
        final int minor = v / 1000;
        v -= minor * 1000;

        out.write (major);
        out.write (minor);
        out.write (v);

        out.write (0);
    }


    /**
     * Writes a string.
     *
     * @param out The output stream
     * @param text The text to write
     * @throws IOException Could not read the text
     */
    private static void writeString (final OutputStream out, final String text) throws IOException
    {
        final byte [] data = text.getBytes (StandardCharsets.UTF_8);
        StreamUtils.writeUnsigned32 (out, data.length, true);
        out.write (data);
    }


    /**
     * Reads a chunk header and returns the data of the chunk.
     *
     * @param out The output stream to write to
     * @param chunkData The data of the chunk
     * @throws IOException Could not write
     */
    private static void writeChunk (final OutputStream out, final String chunkID, final byte [] chunkData, final boolean isFORM, final boolean isCAT) throws IOException
    {
        String id = chunkID;
        int length = chunkData.length;
        if (isFORM)
        {
            id = SxtChunkConstants.FORM;
            length += 4;
        }
        else if (isCAT)
        {
            id = SxtChunkConstants.CAT;
            length += 4;
        }

        StreamUtils.writeASCII (out, id, 4);
        StreamUtils.writeUnsigned32 (out, length, true);
        if (isFORM || isCAT)
            StreamUtils.writeASCII (out, chunkID, 4);

        out.write (chunkData);
        // Chunk length is always 2 aligned!
        if (chunkData.length % 2 == 1)
            out.write (0);
    }
}