// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.korgmultisample;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Creator for Korgmultisample files.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KorgmultisampleCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KorgmultisampleCreator (final INotifier notifier)
    {
        super ("Korg Wavestate/Modwave", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder for the metadata (might be multiple for each layer) and all samples
        final File subFolder = new File (destinationFolder, sampleName);
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        // Korg multisample format supports only 1 multi sample layer. Therefore create 1 output
        // file for each layer
        final List<IVelocityLayer> layers = multisampleSource.getNonEmptyLayers (true);
        final int size = layers.size ();
        for (int i = 0; i < size; i++)
        {
            final IVelocityLayer layer = layers.get (i);
            final ISampleMetadata sampleMetadata = layer.getSampleMetadata ().get (0);
            final String layerName = size > 1 ? String.format ("%s %03d-%03d", sampleName, Integer.valueOf (sampleMetadata.getVelocityLow ()), Integer.valueOf (sampleMetadata.getVelocityHigh ())) : sampleName;
            final File multiFile = new File (subFolder, layerName + ".korgmultisample");
            if (multiFile.exists ())
            {
                this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
                continue;
            }

            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
            storeMultisample (multisampleSource, multiFile, layerName, layer);
        }

        this.writeSamples (subFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a korgmultisample file.
     *
     * @param multisampleSource The multi sample to store in the library
     * @param multiFile The file of the korgmultisample
     * @param layerName The name to use for the layer
     * @param layer The layer to store
     * @throws IOException Could not store the file
     */
    private static void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final String layerName, final IVelocityLayer layer) throws IOException
    {
        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            writeHeader (out);
            writeTime (out);

            final byte [] byteArray;
            try (final ByteArrayOutputStream multisampleOutput = new ByteArrayOutputStream ())
            {
                writeAscii (multisampleOutput, layerName, true);

                final String creator = multisampleSource.getCreator ();
                if (creator != null && !creator.isBlank ())
                {
                    multisampleOutput.write (KorgmultisampleTag.ID_AUTHOR);
                    writeAscii (multisampleOutput, creator, false);
                }

                final String category = multisampleSource.getCategory ();
                if (category != null && !category.isBlank ())
                {
                    multisampleOutput.write (KorgmultisampleTag.ID_CATEGORY);
                    writeAscii (multisampleOutput, category, false);
                }

                final String description = multisampleSource.getDescription ();
                if (description != null && !description.isBlank ())
                {
                    multisampleOutput.write (KorgmultisampleTag.ID_COMMENT);
                    writeAscii (multisampleOutput, description, false);
                }

                writeSample (multisampleOutput, layer);
                writeUUID (multisampleOutput);

                byteArray = multisampleOutput.toByteArray ();
            }

            out.write (toBytesLSB (byteArray.length, 4));
            out.write (byteArray);
        }
    }


    private static void writeSample (final ByteArrayOutputStream multisampleOutput, final IVelocityLayer layer) throws IOException
    {
        // Create one sample block for each sample
        for (final ISampleMetadata sample: layer.getSampleMetadata ())
        {
            final byte [] sbByteArray;
            try (final ByteArrayOutputStream sampleBlockOutput = new ByteArrayOutputStream ())
            {
                final Optional<String> updatedFilename = sample.getUpdatedFilename ();
                if (updatedFilename.isEmpty ())
                    throw new IOException ("Sample file without a name?!");
                final String filename = updatedFilename.get ();

                final byte [] sampleByteArray;
                final int offset;
                try (final ByteArrayOutputStream sampleOutput = new ByteArrayOutputStream ())
                {
                    writeSampleParameters (sampleOutput, sample);

                    // Offset to key zone data
                    offset = sampleOutput.size () + 2;

                    writeKeyZoneParameters (sampleOutput, sample);

                    sampleByteArray = sampleOutput.toByteArray ();
                }

                sampleBlockOutput.write (0x0A);
                sampleBlockOutput.write (filename.length () + offset);
                writeAscii (sampleBlockOutput, filename, true);
                sampleBlockOutput.write (sampleByteArray);

                sbByteArray = sampleBlockOutput.toByteArray ();
            }

            // Write the sample block
            multisampleOutput.write (KorgmultisampleTag.ID_SAMPLE);
            multisampleOutput.write (sbByteArray.length);
            multisampleOutput.write (sbByteArray);
        }
    }


    private static void writeSampleParameters (final ByteArrayOutputStream sampleOutput, final ISampleMetadata sample) throws IOException
    {
        final int start = sample.getStart ();
        if (start > 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_START);
            write7bitNumberLSB (sampleOutput, start);
        }

        final List<ISampleLoop> loops = sample.getLoops ();
        if (!loops.isEmpty ())
        {
            final int loopStart = loops.get (0).getStart ();
            if (loopStart > 0)
            {
                sampleOutput.write (KorgmultisampleTag.ID_LOOP_START);
                write7bitNumberLSB (sampleOutput, loopStart);
            }
        }

        final int end = sample.getStop ();
        if (end > 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_END);
            write7bitNumberLSB (sampleOutput, end);
        }

        // No loop tune support - ID_LOOP_TUNE

        if (loops.isEmpty ())
        {
            sampleOutput.write (KorgmultisampleTag.ID_ONE_SHOT);
            sampleOutput.write (1);
        }

        // No gain boost supported - ID_BOOST_12DB
    }


    private static void writeKeyZoneParameters (final ByteArrayOutputStream sampleOutput, final ISampleMetadata sample) throws IOException
    {
        final int keyLow = sample.getKeyLow ();
        if (keyLow > 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_KEY_BOTTOM);
            sampleOutput.write (keyLow);
        }

        final int keyHigh = sample.getKeyHigh ();
        if (keyHigh > 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_KEY_TOP);
            sampleOutput.write (keyHigh);
        }

        final int keyRoot = sample.getKeyRoot ();
        if (keyRoot > 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_KEY_ORIGINAL);
            sampleOutput.write (keyRoot);
        }

        final double keyTracking = sample.getKeyTracking ();
        if (keyTracking == 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_FIXED_PITCH);
            sampleOutput.write (1);
        }

        final double tune = sample.getTune ();
        if (tune != 0)
        {
            sampleOutput.write (KorgmultisampleTag.ID_TUNE);
            final float val = (float) Math.min (999, Math.max (-999, tune * 1000));
            writeFloatLittleEndian (sampleOutput, val);
        }

        final double gain = sample.getGain ();
        if (gain != 0)
        {
            final float v = (float) Math.min (1000, Math.max (-1000, gain * 1000));
            sampleOutput.write (KorgmultisampleTag.ID_LEVEL_LEFT);
            writeFloatLittleEndian (sampleOutput, v);
            sampleOutput.write (KorgmultisampleTag.ID_LEVEL_RIGHT);
            writeFloatLittleEndian (sampleOutput, v);
        }

        sampleOutput.write (KorgmultisampleTag.ID_COLOR);
        sampleOutput.write (new byte []
        {
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            (byte) 0xFF,
            0x0F
        });
    }


    private static void writeFloatLittleEndian (final ByteArrayOutputStream out, final float value) throws IOException
    {
        final byte [] buf = new byte [4];

        final int val = Float.floatToRawIntBits (value);
        buf[0] = (byte) val;
        buf[1] = (byte) (val >> 8);
        buf[2] = (byte) (val >> 16);
        buf[3] = (byte) (val >> 24);

        out.write (buf);
    }


    private static void writeUUID (final ByteArrayOutputStream sampleOutput) throws IOException
    {
        sampleOutput.write (KorgmultisampleTag.ID_UUID);
        sampleOutput.write (16);
        final UUID uuid = UUID.randomUUID ();
        final byte [] uuidBytes = new byte [16];
        final ByteBuffer buffer = ByteBuffer.wrap (uuidBytes).order (ByteOrder.BIG_ENDIAN);
        buffer.putLong (uuid.getMostSignificantBits ());
        buffer.putLong (uuid.getLeastSignificantBits ());
        sampleOutput.write (uuidBytes);
    }


    private static void writeHeader (final OutputStream out) throws IOException
    {
        out.write (KorgmultisampleTag.TAG_KORG.getBytes ());
        out.write (new byte []
        {
            0x27,
            0x00,
            0x00,
            0x00,
            0x08,
            0x01,
            0x12,
            0x12
        });

        writeAscii (out, KorgmultisampleTag.TAG_FILE_INFO, true);
        out.write (new byte []
        {
            0x12,
            0x0F
        });

        writeAscii (out, KorgmultisampleTag.TAG_MULTISAMPLE, true);
        out.write (new byte []
        {
            0x18,
            0x01,
            0x25,
            0x00,
            0x00,
            0x00
        });

        writeAscii (out, KorgmultisampleTag.TAG_SINGLE_ITEM, true);
        out.write (0x12);

        out.write (KorgmultisampleTag.TAG_SAMPLE_BUILDER.length ());
        out.write (KorgmultisampleTag.TAG_SAMPLE_BUILDER.getBytes ());
    }


    private static void writeTime (final OutputStream out) throws IOException
    {
        out.write (KorgmultisampleTag.ID_TIME);

        final int time = (int) (System.currentTimeMillis () / 1000);
        out.write (toBytesLSB (time, 8));
    }


    private static void writeAscii (final OutputStream out, final String text, final boolean write0A) throws IOException
    {
        final byte [] bytes = text.getBytes ();
        final int length = Math.min (255, bytes.length);

        if (write0A)
            out.write (0x0A);
        out.write (length);
        out.write (bytes, 0, length);
    }


    /**
     * Write an LSB 7 bit of a flexible number of bytes.
     *
     * @param out The output stream to write to
     * @param value The value to write
     * @throws IOException
     */
    protected static void write7bitNumberLSB (final OutputStream out, final int value) throws IOException
    {
        int number = value;

        while (number > 0)
        {
            int val = number & 0x7F;
            number = number >> 7;

            if (number != 0)
                val |= 0x80;

            out.write (val);
        }
    }
}