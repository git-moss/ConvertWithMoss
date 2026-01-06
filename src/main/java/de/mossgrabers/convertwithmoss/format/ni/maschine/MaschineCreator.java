// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine1.Maschine1Format;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.Maschine2Format;


/**
 * Creator for Native Instruments Maschine files.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineCreator extends AbstractWavCreator<MaschineCreatorUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MaschineCreator (final INotifier notifier)
    {
        super ("Maschine Sound", "Maschine", notifier, new MaschineCreatorUI ("Maschine"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        if (multisampleSource.getNonEmptyGroups (false).isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return;
        }

        final int maschineVersion = this.settingsConfiguration.getDestinationVersion ();
        final IMaschineFormat maschineType = maschineVersion == 1 ? new Maschine1Format (this.notifier) : new Maschine2Format (this.notifier);
        final String multisampleName = createSafeFilename (multisampleSource.getName ());
        final File multiFile = this.createUniqueFilename (destinationFolder, multisampleName, maschineType.getFileEnding ());
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        // First, store all samples
        final String safeSampleFolderName = multisampleName + FOLDER_POSTFIX;
        final File sampleFolder = new File (destinationFolder, safeSampleFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            maschineType.writeSound (out, safeSampleFolderName, multisampleSource, maschineVersion);
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }
}