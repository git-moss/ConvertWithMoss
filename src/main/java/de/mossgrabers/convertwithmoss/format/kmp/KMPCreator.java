// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for Korg Multisample (KMP) files.
 *
 * @author Jürgen Moßgraber
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
        super ("Korg KMP/KSF", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        AbstractCreator.recalculateSamplePositions (multisampleSource, 44100);

        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder for the KMP file(s) and all samples
        final File subFolder = new File (destinationFolder, FileUtils.createDOSFileName (sampleName, new HashSet<> ()));
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        // KMP format supports only 1 group. Therefore, create 1 KMP file for each group
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        final boolean needsMultipleKMPs = groups.size () > 1;
        final Set<String> createdKMPNames = new HashSet<> ();
        for (final IGroup group: groups)
        {
            final ISampleZone zone = group.getSampleZones ().get (0);
            final String groupName = needsMultipleKMPs ? String.format ("%d-%s", Integer.valueOf (zone.getVelocityHigh ()), sampleName) : sampleName;
            final String kmpFileName = FileUtils.createDOSFileName (groupName, createdKMPNames) + ".KMP";
            File groupSubFolder = subFolder;
            if (needsMultipleKMPs)
            {
                groupSubFolder = new File (subFolder, kmpFileName);
                if (!groupSubFolder.exists () && !groupSubFolder.mkdirs ())
                {
                    this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", groupSubFolder.getAbsolutePath ());
                    return;
                }
            }
            final File multiFile = new File (groupSubFolder, kmpFileName);
            if (multiFile.exists ())
            {
                this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
                continue;
            }

            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
            this.storeKMP (multiFile, kmpFileName, groupName, group);

            this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
        }
    }


    /**
     * Create a KMP file.
     *
     * @param multiFile The KMP file to create
     * @param dosFilename Classic 8.3 file name
     * @param groupName The name to use for the group
     * @param group The group to store
     * @throws IOException Could not store the file
     */
    private void storeKMP (final File multiFile, final String dosFilename, final String groupName, final IGroup group) throws IOException
    {
        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            final KMPFile kmpFile = new KMPFile (this.notifier, dosFilename, groupName, group);
            kmpFile.write (multiFile.getParentFile (), out);
        }
    }
}