// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.wav;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.MultisampleException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.util.TagDetector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * Detects recursively wave files in folders, which can be the source for a multisample. Wave files
 * must end with <i>.wav</i>. All wave files in a folder are considered to belong to one
 * multi-sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WavMultisampleDetectorTask extends AbstractDetectorTask
{
    private int       crossfadeNotes;
    private int       crossfadeVelocities;
    private String [] velocityLayerPatterns;
    private boolean   isAscending;
    private String [] monoSplitPatterns;
    private String [] postfixTexts;
    private boolean   isPreferFolderName;
    private String    creatorName;
    private String [] creatorTags;


    /**
     * Configure the detector.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param velocityLayerPatterns Detection patterns for velocity layers
     * @param isAscending Are layers ordered ascending?
     * @param monoSplitPatterns Detection pattern for mono splits (to be combined to stereo files)
     * @param postfixTexts Post-fix text to remove
     * @param isPreferFolderName Prefer detecting metadata tags from the folder name
     * @param crossfadeNotes Number of notes to crossfade
     * @param crossfadeVelocities The number of velocity steps to crossfade ranges
     * @param creatorTags Potential names for creators to choose from
     * @param creatorName Default name for the creator if none is detected
     */
    public WavMultisampleDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String [] velocityLayerPatterns, final boolean isAscending, final String [] monoSplitPatterns, final String [] postfixTexts, final boolean isPreferFolderName, final int crossfadeNotes, final int crossfadeVelocities, final String [] creatorTags, final String creatorName)
    {
        super (notifier, consumer, sourceFolder, ".wav");

        this.velocityLayerPatterns = velocityLayerPatterns;
        this.isAscending = isAscending;
        this.monoSplitPatterns = monoSplitPatterns;
        this.postfixTexts = postfixTexts;
        this.isPreferFolderName = isPreferFolderName;
        this.crossfadeNotes = crossfadeNotes;
        this.crossfadeVelocities = crossfadeVelocities;
        this.creatorTags = creatorTags;
        this.creatorName = creatorName;
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
        final File [] wavFiles = this.listFiles (folder, ".wav");

        // Analyze all WAV files
        final WavSampleMetadata [] sampleFiles = new WavSampleMetadata [wavFiles.length];
        for (int i = 0; i < wavFiles.length; i++)
        {
            // Check for task cancellation
            if (this.isCancelled ())
                return Collections.emptyList ();

            try
            {
                sampleFiles[i] = new WavSampleMetadata (wavFiles[i]);
            }
            catch (final IOException | ParseException | CompressionNotSupportedException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_SKIPPED", folder.getAbsolutePath (), wavFiles[i].getAbsolutePath ());
                return Collections.emptyList ();
            }
        }

        return this.createMultisample (folder, sampleFiles);
    }


    /**
     * Detect metadata, order samples and finally create the multi-sample.
     *
     * @param folder The folder which contains the sample files
     * @param sampleFileMetadata The detected sample files
     * @return The multi-sample
     */
    private List<IMultisampleSource> createMultisample (final File folder, final WavSampleMetadata [] sampleFileMetadata)
    {
        try
        {
            final WavKeyMapping keyMapping = new WavKeyMapping (sampleFileMetadata, this.isAscending, this.crossfadeNotes, this.crossfadeVelocities, this.velocityLayerPatterns, this.monoSplitPatterns);
            final String name = cleanupName (this.isPreferFolderName ? folder.getName () : keyMapping.getName (), this.postfixTexts);
            if (name.isEmpty ())
            {
                this.notifier.logError ("IDS_NOTIFY_NO_NAME");
                return Collections.emptyList ();
            }

            String [] parts = createPathParts (folder, this.sourceFolder, name);
            if (parts.length > 1)
            {
                // Remove the samples folder
                final List<String> subpaths = new ArrayList<> (Arrays.asList (parts));
                subpaths.remove (1);
                parts = subpaths.toArray (new String [subpaths.size ()]);
            }

            final MultisampleSource multisampleSource = new MultisampleSource (folder, parts, name, this.subtractPaths (this.sourceFolder, folder));
            multisampleSource.setCreator (TagDetector.detect (parts, this.creatorTags, this.creatorName));
            multisampleSource.setCategory (TagDetector.detectCategory (parts));
            multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

            final List<IVelocityLayer> sampleMetadata = keyMapping.getSampleMetadata ();
            multisampleSource.setVelocityLayers (sampleMetadata);

            this.notifier.log ("IDS_NOTIFY_DETECED_LAYERS", Integer.toString (sampleMetadata.size ()));
            if (this.waitForDelivery ())
                return Collections.emptyList ();

            return Collections.singletonList (multisampleSource);
        }
        catch (final MultisampleException | CombinationNotPossibleException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_SAVE_FAILED", ex.getMessage ());
            return Collections.emptyList ();
        }
    }


    /**
     * Tests if the name is not empty and removes configured post-fix texts.
     *
     * @param name The name to check
     * @param postfixTexts The post-fix texts to remove
     * @return The cleaned up name or null if it is empty
     */
    private static String cleanupName (final String name, final String [] postfixTexts)
    {
        String n = name;
        for (final String pt: postfixTexts)
        {
            if (name.endsWith (pt))
            {
                n = n.substring (0, n.length () - pt.length ());
                break;
            }
        }
        return n.trim ();
    }
}
