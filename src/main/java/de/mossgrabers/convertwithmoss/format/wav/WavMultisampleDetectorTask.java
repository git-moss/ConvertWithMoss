// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.wav;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IVelocityLayer;
import de.mossgrabers.convertwithmoss.exception.CombinationNotPossibleException;
import de.mossgrabers.convertwithmoss.exception.MultisampleException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;

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
 * @author Jürgen Moßgraber
 */
public class WavMultisampleDetectorTask extends AbstractDetectorTask
{
    private final int       crossfadeNotes;
    private final int       crossfadeVelocities;
    private final String [] velocityLayerPatterns;
    private final boolean   isAscending;
    private final String [] monoSplitPatterns;
    private final String [] postfixTexts;


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
     * @param crossfadeNotes Number of notes to crossfade
     * @param crossfadeVelocities The number of velocity steps to crossfade ranges
     * @param metadata Additional metadata configuration parameters
     */
    public WavMultisampleDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String [] velocityLayerPatterns, final boolean isAscending, final String [] monoSplitPatterns, final String [] postfixTexts, final int crossfadeNotes, final int crossfadeVelocities, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".wav");

        this.velocityLayerPatterns = velocityLayerPatterns;
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
        final File [] wavFiles = this.listFiles (folder, ".wav");
        if (wavFiles.length == 0)
            return Collections.emptyList ();

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
            catch (final IOException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_SKIPPED", folder.getAbsolutePath (), wavFiles[i].getAbsolutePath (), ex.getMessage ());
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
            final String name = cleanupName (this.metadata.isPreferFolderName () ? folder.getName () : keyMapping.getName (), this.postfixTexts);
            if (name.isEmpty ())
            {
                this.notifier.logError ("IDS_NOTIFY_NO_NAME");
                return Collections.emptyList ();
            }

            String [] parts = AudioFileUtils.createPathParts (folder, this.sourceFolder, name);
            if (parts.length > 1)
            {
                // Remove the samples folder
                final List<String> subpaths = new ArrayList<> (Arrays.asList (parts));
                subpaths.remove (1);
                parts = subpaths.toArray (new String [subpaths.size ()]);
            }

            final MultisampleSource multisampleSource = new MultisampleSource (folder, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, folder));
            multisampleSource.setCreator (TagDetector.detect (parts, this.metadata.getCreatorTags (), this.metadata.getCreatorName ()));
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
