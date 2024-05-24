// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.MultisampleException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.KeyMapping;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;


/**
 * Base class to detect recursively sample files in folders (e.g. WAV or AIFF), which can be the
 * source for a multi-sample. All sample files in a folder which have the same format are considered
 * to belong to one multi-sample.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractSampleFileDetectorTask extends AbstractDetectorTask
{
    protected final int       crossfadeNotes;
    protected final int       crossfadeVelocities;
    protected final String [] groupPatterns;
    protected final boolean   isAscending;
    protected final String [] monoSplitPatterns;
    protected final String [] postfixTexts;


    /**
     * Configure the detector.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multi-sample sources
     * @param sourceFolder The top source folder for the detection
     * @param groupPatterns Detection patterns for groups
     * @param isAscending Are groups ordered ascending?
     * @param monoSplitPatterns Detection pattern for mono splits (to be combined to stereo files)
     * @param postfixTexts Post-fix text to remove
     * @param crossfadeNotes Number of notes to cross-fade
     * @param crossfadeVelocities The number of velocity steps to cross-fade ranges
     * @param metadata Additional metadata configuration parameters
     * @param fileEndings The file ending(s) identifying the files
     */
    protected AbstractSampleFileDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String [] groupPatterns, final boolean isAscending, final String [] monoSplitPatterns, final String [] postfixTexts, final int crossfadeNotes, final int crossfadeVelocities, final IMetadataConfig metadata, final String... fileEndings)
    {
        super (notifier, consumer, sourceFolder, metadata, fileEndings);

        this.groupPatterns = groupPatterns;
        this.isAscending = isAscending;
        this.monoSplitPatterns = monoSplitPatterns;
        this.postfixTexts = postfixTexts;
        this.crossfadeNotes = crossfadeNotes;
        this.crossfadeVelocities = crossfadeVelocities;
    }


    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        this.notifier.log ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        final List<IMultisampleSource> multisample = this.readFile (folder);
        if (multisample.isEmpty ())
            return;

        // Check for task cancellation
        if (!this.isCancelled ())
            this.consumer.accept (multisample.get (0));
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File folder)
    {
        final File [] files = this.listFiles (folder, this.fileEndings);
        if (files.length == 0)
            return Collections.emptyList ();

        // Analyze all files
        final List<IFileBasedSampleData> sampleData = new ArrayList<> (files.length);
        for (final File file: files)
        {
            // Check for task cancellation
            if (this.isCancelled ())
                return Collections.emptyList ();

            try
            {
                sampleData.add (this.createSampleData (file));
            }
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_SKIPPED", folder.getAbsolutePath (), file.getAbsolutePath (), ex.getMessage ());
                return Collections.emptyList ();
            }
        }

        return this.createMultisample (folder, sampleData);
    }


    /**
     * Detect metadata, order samples and finally create the multi-sample.
     *
     * @param folder The folder which contains the sample files
     * @param sampleData The detected sample files
     * @return The multi-sample
     */
    private List<IMultisampleSource> createMultisample (final File folder, final List<IFileBasedSampleData> sampleData)
    {
        if (sampleData.isEmpty ())
            return Collections.emptyList ();

        try
        {
            // If there are instrument chunks, use them otherwise create the multi-sample from the
            // file names
            final List<IGroup> groups;
            String name;
            if (this.hasInstrumentData (sampleData))
            {
                final IGroup group = new DefaultGroup ("Group");
                groups = Collections.singletonList (group);
                final List<String> filenames = new ArrayList<> (sampleData.size ());
                for (final IFileBasedSampleData fileSampleData: sampleData)
                {
                    final DefaultSampleZone sampleZone = new DefaultSampleZone (FileUtils.getNameWithoutType (new File (fileSampleData.getFilename ())), fileSampleData);
                    group.addSampleZone (sampleZone);
                    this.fillInstrumentData (sampleZone, fileSampleData);
                    filenames.add (new File (fileSampleData.getFilename ()).getName ());
                }
                name = KeyMapping.findCommonPrefix (filenames);
                if (name.isBlank ())
                    name = folder.getName ();
            }
            else
            {
                final KeyMapping keyMapping = new KeyMapping (new ArrayList<> (sampleData), this.isAscending, this.crossfadeNotes, this.crossfadeVelocities, this.groupPatterns, this.monoSplitPatterns);
                if (this.metadataConfig.isPreferFolderName ())
                {
                    name = cleanupName (folder.getName (), this.postfixTexts);
                    if (name.isBlank ())
                        name = keyMapping.getName ();
                }
                else
                    name = keyMapping.getName ();
                groups = keyMapping.getSampleMetadata ();
            }

            if (name.isBlank ())
            {
                this.notifier.logError ("IDS_NOTIFY_NO_NAME");
                return Collections.emptyList ();
            }

            name = cleanupName (name, this.postfixTexts);

            final String [] parts = this.createParts (folder, name);
            final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (folder, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, folder));
            final IMetadata metadata = multisampleSource.getMetadata ();
            this.createMetadata (metadata, sampleData, parts);
            this.updateCreationDateTime (metadata, new File (sampleData.get (0).getFilename ()));
            multisampleSource.setGroups (groups);

            for (final IGroup group: groups)
                for (final ISampleZone zone: group.getSampleZones ())
                    zone.getSampleData ().addZoneData (zone, true, true);

            this.notifier.log ("IDS_NOTIFY_DETECTED_GROUPS", Integer.toString (groups.size ()));
            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return Collections.singletonList (multisampleSource);
        }
        catch (final IOException | MultisampleException | CombinationNotPossibleException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex.getMessage ());
        }

        return Collections.emptyList ();
    }


    /**
     * Tests if the name is not empty and removes configured post-fix texts.
     *
     * @param name The name to check
     * @param postfixTexts The post-fix texts to remove
     * @return The cleaned up name or null if it is empty
     */
    private static String cleanupName (final String name, final String... postfixTexts)
    {
        String n = name;
        for (final String pt: postfixTexts)
            if (name.endsWith (pt))
            {
                n = n.substring (0, n.length () - pt.length ());
                break;
            }
        n = n.trim ();
        if (n.endsWith ("-") || n.endsWith ("_"))
            n = n.substring (0, n.length () - 1);
        return n;
    }


    private String [] createParts (final File folder, final String name)
    {
        final String [] parts = AudioFileUtils.createPathParts (folder, this.sourceFolder, name);
        if (parts.length <= 1)
            return parts;

        // Remove the samples folder
        final List<String> subpaths = new ArrayList<> (Arrays.asList (parts));
        subpaths.remove (1);
        return subpaths.toArray (new String [subpaths.size ()]);
    }


    /**
     * Checks if the file has data about an instrument zone (e.g. key/velocity range and loops).
     *
     * @param sampleData The sample files to check
     * @return True if instrument data is available
     */
    protected abstract boolean hasInstrumentData (final List<IFileBasedSampleData> sampleData);


    /**
     * Set the zone data (e.g. key/velocity range and loops) from the given file based sample data.
     *
     * @param zone The zone to fill
     * @param sampleData The source data
     */
    protected abstract void fillInstrumentData (final ISampleZone zone, final IFileBasedSampleData sampleData);
}
