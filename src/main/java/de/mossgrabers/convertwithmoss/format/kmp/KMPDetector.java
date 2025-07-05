// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.ZoneChannels;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Detects recursively KSC or KMP files in folders. Files must end with <i>.KSC</i> or <i>.KMP</i>.
 *
 * @author Jürgen Moßgraber
 */
public class KMPDetector extends AbstractDetector<KMPDetectorUI>
{
    private static final String [] KSC_ENDINGS =
    {
        ".ksc",
        ".KSC"
    };

    private static final String [] KMP_ENDINGS =
    {
        ".kmp",
        ".KMP"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KMPDetector (final INotifier notifier)
    {
        super ("Korg KSC/KMP/KSF", "KMP", notifier, new KMPDetectorUI ("KMP"));
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = this.settingsConfiguration.useKscFiles () ? KSC_ENDINGS : KMP_ENDINGS;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final List<IMultisampleSource> multiSameplSources = new ArrayList<> ();

        if (this.settingsConfiguration.useKscFiles ())
            multiSameplSources.addAll (this.readKSCFile (sourceFile));
        else
        {
            final Optional<IMultisampleSource> kmpFile = this.readKMPFile (sourceFile);
            if (kmpFile.isPresent ())
                multiSameplSources.add (kmpFile.get ());
        }

        return multiSameplSources;
    }


    /**
     * Reads and parses a KSC file. Collects all referenced KMP files and tries to combine them to
     * stereo files by their name-prefix.
     * 
     * @param sourceFile The KSC file
     * @return The parsed multi-sample sources
     */
    private List<IMultisampleSource> readKSCFile (final File sourceFile)
    {
        if (sourceFile.getName ().endsWith ("_UserBank.KSC"))
        {
            this.notifier.log ("IDS_KMP_KSC_V2_IGNORED");
            return Collections.emptyList ();
        }

        final KSCFile kscFile = new KSCFile ();
        try
        {
            kscFile.read (sourceFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }

        final List<String> kmpFiles = kscFile.getKmpFiles ();
        if (kmpFiles.isEmpty ())
        {
            this.notifier.logError ("IDS_KMP_KSC_CONTAINS_NO_KMP");
            return Collections.emptyList ();
        }

        // Find matching left/right files to combine into a stereo multi-sample
        final List<IMultisampleSource> results = new ArrayList<> ();
        File previousFolder = null;

        final int numKmpFiles = kmpFiles.size ();
        int pos = 0;
        while (pos < numKmpFiles)
        {
            // Find the next KMP file
            final String kmpFileName = kmpFiles.get (pos);
            final File kmpFile = findFile (this.notifier, sourceFile.getParentFile (), previousFolder, kmpFileName, 0, "KMP");
            if (!kmpFile.exists ())
            {
                pos++;
                continue;
            }
            previousFolder = kmpFile.getParentFile ();

            // Load the KMP file
            final Optional<IMultisampleSource> multisampleSource = this.readKMPFile (kmpFile);
            if (multisampleSource.isEmpty ())
            {
                pos++;
                continue;
            }

            // Is it a left-channel file? If not, add the mono result
            final IMultisampleSource ms = multisampleSource.get ();
            final String zoneName = ms.getGroups ().get (0).getSampleZones ().get (0).getName ();
            if (!zoneName.endsWith ("-L") || pos + 1 == numKmpFiles)
            {
                results.add (ms);
                pos++;
                continue;
            }

            // Combine with the next KMP to a stereo file
            pos++;
            final String kmpFileNameRightChannel = kmpFiles.get (pos);
            final File kmpFileRightChannel = findFile (this.notifier, sourceFile.getParentFile (), previousFolder, kmpFileNameRightChannel, 0, "KMP");
            try
            {
                this.combineToStereo (kmpFile, kmpFileRightChannel, results);
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ex);
            }
            pos++;
        }

        return results;
    }


    /**
     * Reads a KMP file and creates a multi-sample source from it.
     * 
     * @param sourceFile The KMP file
     * @return The multi-sample source if found
     */
    private Optional<IMultisampleSource> readKMPFile (final File sourceFile)
    {
        final IGroup group = new DefaultGroup ("Group 1");

        final KMPFile kmpFile;
        try (final FileInputStream stream = new FileInputStream (sourceFile))
        {
            kmpFile = new KMPFile (this.notifier, sourceFile, group.getSampleZones ());
            kmpFile.read (stream);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Optional.empty ();
        }

        final String name = kmpFile.getName ();
        final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, name);
        final DefaultMultisampleSource source = new DefaultMultisampleSource (sourceFile, parts, name, sourceFile.getName ());

        // Use guessing on the filename...
        source.getMetadata ().detectMetadata (this.settingsConfiguration, parts);
        source.setGroups (Collections.singletonList (group));
        return Optional.of (source);
    }


    /**
     * Combines 2 mono KMP files into 1 stereo multi-sample source.
     * 
     * @param kmpFile1 The 1st KMP file
     * @param kmpFile2 The 2nd KMP file
     * @param results Where to add the found multi-sample source
     * @throws IOException Could not combine the samples
     */
    private void combineToStereo (final File kmpFile1, final File kmpFile2, final List<IMultisampleSource> results) throws IOException
    {
        // If it does not exist we still call this method to trigger an error
        final Optional<IMultisampleSource> multisampleSource1 = this.readKMPFile (kmpFile1);
        final Optional<IMultisampleSource> multisampleSource2 = this.readKMPFile (kmpFile2);
        if (multisampleSource1.isEmpty () || multisampleSource2.isEmpty ())
            return;

        // Make some sanity checks
        final IMultisampleSource s1 = multisampleSource1.get ();
        final IMultisampleSource s2 = multisampleSource2.get ();
        final List<ISampleZone> sampleZones1 = s1.getGroups ().get (0).getSampleZones ();
        final List<ISampleZone> sampleZones2 = s2.getGroups ().get (0).getSampleZones ();
        if (sampleZones1.size () != sampleZones2.size ())
        {
            this.notifier.logError ("IDS_KMP_KSC_DIFFERENT_NUMBER_OF_ZONES", kmpFile1.getName (), kmpFile2.getName ());
            return;
        }
        if (sampleZones1.isEmpty ())
        {
            this.notifier.logError ("IDS_KMP_KSC_NO_ZONES", kmpFile1.getName (), kmpFile2.getName ());
            return;
        }

        // Identify which one is the left and right channel via their name
        final String zoneName1 = sampleZones1.get (0).getName ();
        final String zoneName2 = sampleZones2.get (0).getName ();

        final IMultisampleSource left;
        final IMultisampleSource right;
        if (zoneName1.endsWith ("-L") && zoneName2.endsWith ("-R"))
        {
            left = s1;
            right = s2;
        }
        else if (zoneName1.endsWith ("-R") && zoneName2.endsWith ("-L"))
        {
            left = s2;
            right = s1;
        }
        else
        {
            this.notifier.logError ("IDS_KMP_KSC_2_FILES_ARE_NOT_STEREO", kmpFile1.getName (), kmpFile2.getName ());
            return;
        }

        final List<ISampleZone> leftSampleZones = left.getGroups ().get (0).getSampleZones ();
        final List<ISampleZone> rightSampleZones = right.getGroups ().get (0).getSampleZones ();
        final Optional<IGroup> optGroup = ZoneChannels.combineSplitStereo (leftSampleZones, rightSampleZones);

        left.setName (getCommonPrefix (left.getName (), right.getName ()));
        if (optGroup.isPresent ())
        {
            left.getGroups ().set (0, optGroup.get ());
            results.add (left);
        }
    }


    private static String getCommonPrefix (final String str1, final String str2)
    {
        String prefix = StringUtils.getCommonPrefix (str1, str2);
        if (prefix.endsWith ("-"))
            prefix = prefix.substring (0, prefix.length () - 1);
        // IMPROVE: Is this the velocity?
        if (prefix.endsWith ("0"))
            prefix = prefix.substring (0, prefix.length () - 1);
        return prefix.trim ();
    }
}
