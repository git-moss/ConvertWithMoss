// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


/**
 * Creator for Korg Multisample (KMP) files.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class KMPCreator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPCreator (final INotifier notifier)
    {
        super ("KMP/KSF", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder for the KMP files (one for each layer) and all samples
        final File subFolder = new File (destinationFolder, createDOSFileName (sampleName));
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        // Korg KMP format supports only 1 multi sample layer. Therefore create 1 output
        // file for each layer
        final List<IVelocityLayer> layers = getNonEmptyLayers (multisampleSource.getLayers ());
        final int size = layers.size ();
        for (int i = 0; i < size; i++)
        {
            final IVelocityLayer layer = layers.get (i);
            final ISampleMetadata sampleMetadata = layer.getSampleMetadata ().get (0);
            final String layerName = size > 1 ? String.format ("%d-%s", Integer.valueOf (sampleMetadata.getVelocityHigh ()), sampleName) : sampleName;
            final String dosFileName = createDOSFileName (layerName) + ".KMP";
            final File multiFile = new File (subFolder, dosFileName);
            if (multiFile.exists ())
            {
                this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
                continue;
            }

            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
            this.storeMultisample (multiFile, dosFileName, layerName, layer);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a KMP file.
     *
     * @param multiFile The file of the korgmultisample
     * @param dosFilename Classic 8.3 file name
     * @param layerName The name to use for the layer
     * @param layer The layer to store
     * @throws IOException Could not store the file
     */
    private void storeMultisample (final File multiFile, final String dosFilename, final String layerName, final IVelocityLayer layer) throws IOException
    {
        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            final KMPFile kmpFile = new KMPFile (this.notifier, dosFilename, layerName, layer);
            kmpFile.write (multiFile.getParentFile (), out);
        }
    }


    private static String createDOSFileName (final String filename)
    {
        String dosFilename = filename.toUpperCase ().replace (' ', '_');
        if (dosFilename.length () > 8)
            dosFilename = dosFilename.substring (0, 8);
        return dosFilename;
    }
}