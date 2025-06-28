// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ZoneChannels;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.Functions;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;


/**
 * Creator for Korg Multisample (KMP) files.
 *
 * @author Jürgen Moßgraber
 */
public class KMPCreator extends AbstractCreator
{
    private static final String KMP_WRITE_GROUP_KMPS = "KMPWriteGroupKmps";
    private static final String KMP_GAIN_PLUS_12     = "KMPGainPlus12";
    private static final String KMP_MAXIMIZE_VOLUME  = "KMPMaximizeVolume";

    private CheckBox            writeGroupKmps;
    private CheckBox            gainPlus12;
    private CheckBox            maximizeVolume;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPCreator (final INotifier notifier)
    {
        super ("Korg KSC/KMP/KSF", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        panel.createSeparator ("@IDS_KMP_OPTIONS");
        this.writeGroupKmps = panel.createCheckBox ("@IDS_KMP_WRITE_KMP_FOR_EACH_GROUP", "@IDS_KMP_WRITE_KMP_FOR_EACH_GROUP_TOOLTIP");
        this.gainPlus12 = panel.createCheckBox ("@IDS_KMP_GAIN_12DB");
        this.maximizeVolume = panel.createCheckBox ("@IDS_KMP_MAXIMIZE_VOLUME", "@IDS_KMP_MAXIMIZE_VOLUME_TOOLTIP");
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.writeGroupKmps.setSelected (config.getBoolean (KMP_WRITE_GROUP_KMPS, false));
        this.gainPlus12.setSelected (config.getBoolean (KMP_GAIN_PLUS_12, false));
        this.maximizeVolume.setSelected (config.getBoolean (KMP_MAXIMIZE_VOLUME, false));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (KMP_WRITE_GROUP_KMPS, this.writeGroupKmps.isSelected ());
        config.setBoolean (KMP_GAIN_PLUS_12, this.gainPlus12.isSelected ());
        config.setBoolean (KMP_MAXIMIZE_VOLUME, this.maximizeVolume.isSelected ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.createLibrary (destinationFolder, Collections.singletonList (multisampleSource), AbstractCreator.createSafeFilename (multisampleSource.getName ()));
    }


    /** {@inheritDoc} */
    @Override
    public void createLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        final List<String> createdKMPNames = new ArrayList<> ();
        int kmpIndex = 0;

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            AbstractCreator.recalculateSamplePositions (multisampleSource, 48000, true);

            final String multiSampleName = createSafeFilename (multisampleSource.getName ());

            // Create a sub-folder for the KMP file(s) and all samples
            final File subFolder = new File (destinationFolder, createUniqueDOSFileName (destinationFolder, multiSampleName, "", new HashSet<> (), true));
            if (!subFolder.exists () && !subFolder.mkdirs ())
            {
                this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", subFolder.getAbsolutePath ());
                return;
            }

            // KMP format supports only 1 group. Therefore, either create 1 KMP file for each group
            // or combine them into 1
            final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
            final ZoneChannels zoneChannels = ZoneChannels.detectChannelConfiguration (groups);
            this.notifier.log ("IDS_KMP_SOURCE_SAMPLES_FORMAT", zoneChannels.toString ());

            if (!this.writeGroupKmps.isSelected () || zoneChannels == ZoneChannels.SPLIT_STEREO)
            {
                final IGroup combinedGroup = new DefaultGroup ();
                final List<ISampleZone> sampleZones = combinedGroup.getSampleZones ();
                for (final IGroup group: groups)
                    sampleZones.addAll (group.getSampleZones ());
                kmpIndex = this.storeKMP (subFolder, multiSampleName, combinedGroup, zoneChannels, kmpIndex, createdKMPNames);
            }
            else
                for (final IGroup group: groups)
                    kmpIndex = this.storeKMP (subFolder, multiSampleName, group, zoneChannels, kmpIndex, createdKMPNames);
        }

        // Write a KSC file with all created KMP files
        final String dosLibraryName = createUniqueDOSFileName (destinationFolder, libraryName, ".KSC", new HashSet<> (), false);
        final File outputFile = new File (destinationFolder, dosLibraryName + ".KSC");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());
        new KSCFile (createdKMPNames).write (outputFile);
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a KMP file.
     *
     * @param subFolder The sub-folder to store to
     * @param sampleName The KMP file to create
     * @param group The group
     * @param zoneChannels The channel configuration of the zone
     * @param kmpIndex The index to use for the KMP
     * @param createdKMPNames The index of the KMP file
     * @return The increased KMP index to use for the next one
     * @throws IOException Could not store the file
     */
    private int storeKMP (final File subFolder, final String sampleName, final IGroup group, final ZoneChannels zoneChannels, final int kmpIndex, final Collection<String> createdKMPNames) throws IOException
    {
        final boolean gain12dB = this.gainPlus12.isSelected ();
        final boolean maxVolume = this.maximizeVolume.isSelected ();
        final String filename = StringUtils.rightPadSpaces (sampleName, 6);

        switch (zoneChannels)
        {
            default:
            case MONO:
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.MONO, group, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 1;

            case STEREO, MIXED:
                // Write 2 KMP files for left/right
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.LEFT, group, gain12dB, maxVolume, createdKMPNames);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex + 1, KMPChannel.RIGHT, group, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 2;

            case SPLIT_STEREO:
                // First split into 2 groups for left and right
                final IGroup leftGroup = new DefaultGroup ();
                final IGroup rightGroup = new DefaultGroup ();
                for (final ISampleZone zone: group.getSampleZones ())
                    if (zone.getPanning () <= -1)
                        leftGroup.addSampleZone (zone);
                    else
                        rightGroup.addSampleZone (zone);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.LEFT, leftGroup, gain12dB, maxVolume, createdKMPNames);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex + 1, KMPChannel.RIGHT, rightGroup, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 2;
        }
    }


    private void storeKMPChannel (final File subFolder, final String filename, final String sampleWithGroupName, final int kmpIndex, final KMPChannel kmpChannel, final IGroup group, final boolean gain12dB, final boolean maxVolume, final Collection<String> createdKMPNames) throws IOException
    {
        final String kmpFileName = createUniqueFilename (subFolder, filename, kmpIndex, createdKMPNames);
        final File kmpFilePath = this.createFile (subFolder, kmpFileName);
        final KMPFile kmpFile = new KMPFile (this.notifier, kmpFileName, sampleWithGroupName, group.getSampleZones (), gain12dB, maxVolume);

        try (final OutputStream out = new FileOutputStream (kmpFilePath))
        {
            kmpFile.write (kmpFilePath.getParentFile (), out, kmpIndex, kmpChannel);
        }
        this.notifier.logText ("\n");
    }


    private static String createUniqueFilename (final File subFolder, final String filename, final int kmpIndex, final Collection<String> createdKMPNames)
    {
        return createUniqueDOSFileName (subFolder, String.format ("%s%02d", filename, Integer.valueOf (kmpIndex)), ".KMP", createdKMPNames, false) + ".KMP";
    }


    private File createFile (final File subFolder, final String kmpFileName) throws IOException
    {
        final File file = new File (subFolder, kmpFileName);
        if (file.exists ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ALREADY_EXISTS", file.getAbsolutePath ()));
        this.notifier.log ("IDS_NOTIFY_STORING", file.getAbsolutePath ());
        return file;
    }
}