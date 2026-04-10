// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
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
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ZoneChannels;
import de.mossgrabers.convertwithmoss.core.algorithm.VelocityLayerSplitter;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.StringUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Korg Multisample (KMP) files.
 *
 * @author Jürgen Moßgraber
 */
public class KMPCreator extends AbstractCreator<KMPCreatorUI>
{
    private static final Set<Integer> SUPPORTED_BIT_DEPTHS = new HashSet<> ();
    static
    {
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (8));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (16));
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPCreator (final INotifier notifier)
    {
        super ("Korg KSC/KMP/KSF", "KMP", notifier, new KMPCreatorUI ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.createPresetLibrary (destinationFolder, Collections.singletonList (multisampleSource), AbstractCreator.createSafeFilename (multisampleSource.getName ()));
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
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

            // KMP format supports only 1 velocity layer (actually more but the Workstations cannot
            // load it)
            final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
            final ZoneChannels zoneChannels = ZoneChannels.detectChannelConfiguration (groups);
            this.notifier.log ("IDS_KMP_SOURCE_SAMPLES_FORMAT", zoneChannels.toString ());

            final List<ISampleZone> sampleZones = multisampleSource.getAllSampleZones (false);
            for (final List<ISampleZone> velocityLayer: VelocityLayerSplitter.splitVelocityLayers (sampleZones).values ())
                kmpIndex = this.storeKMP (subFolder, multiSampleName, velocityLayer, zoneChannels, kmpIndex, createdKMPNames);
        }

        // Write a KSC file with all created KMP files
        final String dosLibraryName = createUniqueDOSFileName (destinationFolder, libraryName, ".KSC", new HashSet<> (), false);
        final File outputFile = new File (destinationFolder, dosLibraryName + ".KSC");
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());
        new KSCFile (createdKMPNames).write (outputFile);
        this.progress.notifyDone ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BITE_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "8, 16");
        return false;
    }


    /**
     * Create a KMP file.
     *
     * @param subFolder The sub-folder to store to
     * @param sampleName The KMP file to create
     * @param sampleZones The sample zones to add to the KMP
     * @param zoneChannels The channel configuration of the zone
     * @param kmpIndex The index to use for the KMP
     * @param createdKMPNames The index of the KMP file
     * @return The increased KMP index to use for the next one
     * @throws IOException Could not store the file
     */
    private int storeKMP (final File subFolder, final String sampleName, final List<ISampleZone> sampleZones, final ZoneChannels zoneChannels, final int kmpIndex, final Collection<String> createdKMPNames) throws IOException
    {
        final boolean gain12dB = this.settingsConfiguration.gainPlus12 ();
        final boolean maxVolume = this.settingsConfiguration.maximizeVolume ();
        final String filename = StringUtils.rightPadSpaces (sampleName, 6);

        switch (zoneChannels)
        {
            default:
            case MONO:
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.MONO, sampleZones, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 1;

            case STEREO, MIXED:
                // Write 2 KMP files for left/right
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.LEFT, sampleZones, gain12dB, maxVolume, createdKMPNames);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex + 1, KMPChannel.RIGHT, sampleZones, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 2;

            case SPLIT_STEREO:
                // First split into 2 arrays for left and right
                final List<ISampleZone> leftSampleZones = new ArrayList<> ();
                final List<ISampleZone> rightSampleZones = new ArrayList<> ();
                for (final ISampleZone zone: sampleZones)
                    if (zone.getTuning () <= -1)
                        leftSampleZones.add (zone);
                    else
                        rightSampleZones.add (zone);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex, KMPChannel.LEFT, leftSampleZones, gain12dB, maxVolume, createdKMPNames);
                this.storeKMPChannel (subFolder, filename, sampleName, kmpIndex + 1, KMPChannel.RIGHT, rightSampleZones, gain12dB, maxVolume, createdKMPNames);
                return kmpIndex + 2;
        }
    }


    private void storeKMPChannel (final File subFolder, final String filename, final String sampleWithGroupName, final int kmpIndex, final KMPChannel kmpChannel, final List<ISampleZone> sampleZones, final boolean gain12dB, final boolean maxVolume, final Collection<String> createdKMPNames) throws IOException
    {
        final String kmpFileName = createUniqueFilename (subFolder, filename, kmpIndex, createdKMPNames);
        final File kmpFilePath = this.createFile (subFolder, kmpFileName);
        final KMPFile kmpFile = new KMPFile (this.notifier, kmpFileName, sampleWithGroupName, sampleZones, gain12dB, maxVolume);

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