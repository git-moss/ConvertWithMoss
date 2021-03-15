// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.exception.CombinationNotPossibleException;
import de.mossgrabers.sampleconverter.exception.CompressionNotSupportedException;
import de.mossgrabers.sampleconverter.exception.MultisampleException;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.ui.tools.Functions;
import de.mossgrabers.sampleconverter.util.KeyMapping;
import de.mossgrabers.sampleconverter.util.TagDetector;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;


/**
 * Detects recursivly wave files in folders, which can be the source for a multisample. Wave files
 * must end with <i>.wav</i>. All wave files in a folder are considered to belong to one
 * multi-sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class WaveMultisampleDetectorTask extends Task<Boolean>
{
    private File                         sourceFolder;
    private int                          crossfadeNotes;
    private String []                    velocityLayerPatterns;
    private boolean                      isAscending;
    private String []                    monoSplitPatterns;
    private String []                    postfixTexts;
    private boolean                      isPreferFolderName;
    private String                       creatorName;
    private String []                    creatorTags;
    private Consumer<IMultisampleSource> consumer;
    private INotifier                    notifier;


    /**
     * Configure the detector.
     *
     * @param notifier
     *
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param velocityLayerPatterns Detection patterns for velocity layers
     * @param isAscending Are layers ordered ascending?
     * @param monoSplitPatterns Detection pattern for mono splits (to be combined to stereo files)
     * @param postfixTexts Postfix text to remove
     * @param isPreferFolderName Prefer detecting metadata tags from the folder name
     * @param crossfadeNotes Number of notes to crossfade
     * @param creatorTags Potential names for creators to choose from
     * @param creatorName Default name for the creator if none is detected
     */
    public void configure (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final String [] velocityLayerPatterns, final boolean isAscending, final String [] monoSplitPatterns, final String [] postfixTexts, final boolean isPreferFolderName, final int crossfadeNotes, final String [] creatorTags, final String creatorName)
    {
        this.notifier = notifier;
        this.consumer = consumer;
        this.sourceFolder = sourceFolder;
        this.velocityLayerPatterns = velocityLayerPatterns;
        this.isAscending = isAscending;
        this.monoSplitPatterns = monoSplitPatterns;
        this.postfixTexts = postfixTexts;
        this.isPreferFolderName = isPreferFolderName;
        this.crossfadeNotes = crossfadeNotes;
        this.creatorTags = creatorTags;
        this.creatorName = creatorName;
    }


    /** {@inheritDoc} */
    @Override
    protected Boolean call () throws Exception
    {
        this.detect (this.sourceFolder);
        final boolean cancelled = this.isCancelled ();
        this.notifier.notify (Functions.getMessage (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED"));
        return Boolean.valueOf (cancelled);
    }


    /**
     * Detect recursivly all wave files in the given folder.
     *
     * @param folder The folder to start detection.
     */
    private void detect (final File folder)
    {
        // Detect all wav files in the folder
        this.notifier.notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ()));
        if (this.waitForDelivery ())
            return;

        final File [] wavFiles = folder.listFiles ( (parent, name) -> {

            if (this.isCancelled ())
                return false;

            final File f = new File (parent, name);
            if (f.isDirectory ())
            {
                this.detect (f);
                return false;
            }
            return name.toLowerCase (Locale.US).endsWith (".wav");
        });
        if (wavFiles.length == 0)
            return;

        // Analyze all wav files
        final WavSampleMetadata [] sampleFiles = new WavSampleMetadata [wavFiles.length];
        for (int i = 0; i < wavFiles.length; i++)
        {
            // Check for task cancellation
            if (this.isCancelled ())
                return;

            try
            {
                sampleFiles[i] = new WavSampleMetadata (wavFiles[i]);
            }
            catch (final IOException | ParseException | CompressionNotSupportedException ex)
            {
                this.notifier.notify (String.format (Functions.getMessage ("IDS_NOTIFY_SKIPPED"), folder.getAbsolutePath (), wavFiles[i]));
                return;
            }
        }

        final IMultisampleSource multisample = this.createMultisample (folder, sampleFiles);
        if (multisample == null)
            return;

        // Check for task cancellation
        if (!this.isCancelled ())
            this.consumer.accept (multisample);
    }


    /**
     * Detect metadata, order samples and finally create the multi-sample.
     *
     * @param folder The folder which contains the sample files
     * @param sampleFileMetadata The detected sample files
     * @return The multi-sample or null if an error occured
     */
    private IMultisampleSource createMultisample (final File folder, final WavSampleMetadata [] sampleFileMetadata)
    {
        try
        {
            final KeyMapping keyMapping = new KeyMapping (sampleFileMetadata, this.crossfadeNotes, this.velocityLayerPatterns, this.monoSplitPatterns);
            final String name = cleanupName (this.isPreferFolderName ? folder.getName () : keyMapping.getName (), this.postfixTexts);
            if (name.isEmpty ())
            {
                this.notifier.notify (Functions.getMessage ("IDS_NOTIFY_NO_NAME"));
                return null;
            }

            final String [] parts = createPathParts (folder, this.sourceFolder, name);
            final String creator = TagDetector.detect (parts, this.creatorTags, this.creatorName);
            final String category = TagDetector.detectCategory (parts);
            final String [] keywords = TagDetector.detectKeywords (parts);

            final List<List<ISampleMetadata>> orderedSampleMetadata = keyMapping.getOrderedSampleMetadata (this.isAscending);

            this.notifier.notify (Functions.getMessage ("IDS_NOTIFY_DETECED_LAYERS", Integer.toString (orderedSampleMetadata.size ())));
            if (this.waitForDelivery ())
                return null;

            return new WavMultisampleSource (folder, parts, orderedSampleMetadata, keyMapping, name, creator, category, keywords);
        }
        catch (final MultisampleException | CombinationNotPossibleException ex)
        {
            this.notifier.notify (Functions.getMessage ("IDS_NOTIFY_SAVE_FAILED"));
            return null;
        }
    }


    /**
     * Wait a bit.
     *
     * @return The thread was cancelled if true
     */
    private boolean waitForDelivery ()
    {
        try
        {
            Thread.sleep (10);
        }
        catch (final InterruptedException ex)
        {
            if (this.isCancelled ())
                return true;
            Thread.currentThread ().interrupt ();
        }
        return false;
    }


    /**
     * Tests if the name is not empty and removes configured postfix texts.
     *
     * @param name The name to check
     * @param postfixTexts The postfix texts to remove
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


    /**
     * Split the parts of the path offset between the selected source folder and the currently
     * processed sub-folder.
     *
     * @param msSourceFolder The currently processed sub-folder
     * @param sourceFolder The source folder
     * @param name The name of the multisample
     * @return The array with all parts and the name in reverse order
     */
    private static String [] createPathParts (final File msSourceFolder, final File sourceFolder, final String name)
    {
        File f = msSourceFolder;
        final List<String> pathNames = new ArrayList<> ();
        while (!f.equals (sourceFolder))
        {
            pathNames.add (f.getName ());
            f = f.getParentFile ();
        }
        pathNames.add (sourceFolder.getName ());

        final String [] result = new String [pathNames.size () + 1];
        result[0] = name;
        for (int i = 0; i < pathNames.size (); i++)
            result[i + 1] = pathNames.get (i);
        return result;
    }
}
