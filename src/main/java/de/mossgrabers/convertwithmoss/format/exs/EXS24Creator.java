// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Logic EXS24 files.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24Creator extends AbstractCreator
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public EXS24Creator (final INotifier notifier)
    {
        super ("Logic EXS24", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Create a sub-folder to have the EXS file together with the samples
        final File subFolder = new File (destinationFolder, sampleName);
        if (subFolder.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", subFolder.getAbsolutePath ());
            return;
        }
        if (!subFolder.exists () && !subFolder.mkdirs ())
        {
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
            return;
        }
        final File multiFile = new File (subFolder, sampleName + ".exs");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storeMultisample (multisampleSource, multiFile);
        this.writeSamples (subFolder, multisampleSource);

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    private void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile) throws IOException
    {
        final boolean isBigEndian = true;

        final List<EXS24Zone> exsZones = new ArrayList<> ();
        final List<EXS24Sample> exsSamples = new ArrayList<> ();
        final List<EXS24Group> exsGroups = new ArrayList<> ();

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        for (final IGroup group: groups)
        {
            final EXS24Group exsGroup = new EXS24Group ();
            exsGroups.add (exsGroup);
            exsGroup.name = group.getName ();
            exsGroup.releaseTrigger = group.getTrigger () == TriggerType.RELEASE;

            for (final ISampleZone zone: group.getSampleZones ())
            {
                final EXS24Zone exsZone = new EXS24Zone ();
                exsZones.add (exsZone);
                final EXS24Sample exsSample = new EXS24Sample ();
                exsSamples.add (exsSample);
                // TODO Fill zones
                // TODO Fill samples
            }
        }

        final EXS24Instrument exsInstrument = new EXS24Instrument ();
        exsInstrument.name = StringUtils.fixASCII (multisampleSource.getName ());
        exsInstrument.numZoneBlocks = exsZones.size ();
        exsInstrument.numGroupBlocks = exsGroups.size ();
        exsInstrument.numSampleBlocks = exsSamples.size ();
        exsInstrument.numParameterBlocks = 1;

        final EXS24Parameters exsParameters = new EXS24Parameters ();
        // TODO fill parameters

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            final EXS24Block instrumentBlock = exsInstrument.write (isBigEndian);
            instrumentBlock.write (out);

            // Write all group zones
            for (int i = 0; i < exsZones.size (); i++)
            {
                final EXS24Zone exsZone = exsZones.get (i);
                final EXS24Block zoneBlock = exsZone.write (isBigEndian);
                zoneBlock.index = i;
                zoneBlock.write (out);
            }

            // Write all group blocks
            for (int i = 0; i < exsGroups.size (); i++)
            {
                final EXS24Group exsGroup = exsGroups.get (i);
                final EXS24Block groupBlock = exsGroup.write (isBigEndian);
                groupBlock.index = i;
                groupBlock.write (out);
            }

            // Write all sample blocks
            for (int i = 0; i < exsSamples.size (); i++)
            {
                final EXS24Sample exsSample = exsSamples.get (i);
                final EXS24Block sampleBlock = exsSample.write (isBigEndian);
                sampleBlock.index = i;
                sampleBlock.write (out);
            }

            // Write parameters
            final EXS24Block parameterBlock = exsParameters.write (isBigEndian);
            parameterBlock.write (out);
        }
    }
}