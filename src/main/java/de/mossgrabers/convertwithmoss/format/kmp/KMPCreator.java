// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder for the KMP files (one for each group) and all samples
        final Set<String> createdKMPNames = new HashSet<> ();
        final File subFolder = new File (destinationFolder, FileUtils.createDOSFileName (sampleName, createdKMPNames));
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }

        // Korg KMP format supports only 1 group. Therefore, create 1 output file for each group
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        final int size = groups.size ();
        final boolean needsSubDir = size > 1;
        final Set<String> createdKSFNames = new HashSet<> (size);
        for (int i = 0; i < size; i++)
        {
            final IGroup group = groups.get (i);
            final ISampleZone zone = group.getSampleZones ().get (0);
            final String groupName = size > 1 ? String.format ("%d-%s", Integer.valueOf (zone.getVelocityHigh ()), sampleName) : sampleName;
            final String dosFileName = FileUtils.createDOSFileName (groupName, createdKSFNames) + ".KMP";
            final File groupSubFolder;
            if (needsSubDir)
            {
                groupSubFolder = new File (subFolder, dosFileName);
                if (!groupSubFolder.exists () && !groupSubFolder.mkdirs ())
                {
                    this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", groupSubFolder.getAbsolutePath ());
                    return;
                }
            }
            else
                groupSubFolder = subFolder;
            final File multiFile = new File (groupSubFolder, dosFileName);
            if (multiFile.exists ())
            {
                this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
                continue;
            }

            this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
            this.storeMultisample (multiFile, dosFileName, groupName, group);

            this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
        }
    }


    /**
     * Create a KMP file.
     *
     * @param multiFile The file of the korgmultisample
     * @param dosFilename Classic 8.3 file name
     * @param groupName The name to use for the group
     * @param group The group to store
     * @throws IOException Could not store the file
     */
    private void storeMultisample (final File multiFile, final String dosFilename, final String groupName, final IGroup group) throws IOException
    {
        try (final OutputStream out = new FileOutputStream (multiFile))
        {
            final KMPFile kmpFile = new KMPFile (this.notifier, dosFilename, groupName, group);
            kmpFile.write (multiFile.getParentFile (), out);
        }
    }
}