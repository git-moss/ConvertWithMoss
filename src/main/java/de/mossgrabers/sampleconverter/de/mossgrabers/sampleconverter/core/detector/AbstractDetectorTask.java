// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import javafx.concurrent.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * Base class for detector tasks.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractDetectorTask extends Task<Boolean>
{
    protected Optional<INotifier>                    notifier     = Optional.empty ();
    protected Optional<Consumer<IMultisampleSource>> consumer     = Optional.empty ();
    protected Optional<File>                         sourceFolder = Optional.empty ();


    /**
     * Configure the detector.
     *
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    public void configure (final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        this.consumer = Optional.ofNullable (consumer);
        this.sourceFolder = Optional.ofNullable (sourceFolder);
    }


    /**
     * Set the notifier for information and error logging.
     *
     * @param notifier The notifier
     */
    protected void configure (final INotifier notifier)
    {
        this.notifier = Optional.of (notifier);
    }


    /** {@inheritDoc} */
    @Override
    protected Boolean call () throws Exception
    {
        if (this.sourceFolder.isEmpty ())
            return Boolean.FALSE;

        try
        {
            this.detect (this.sourceFolder.get ());
        }
        catch (final RuntimeException ex)
        {
            this.logError (ex);
        }
        final boolean cancelled = this.isCancelled ();
        this.log (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED");
        return Boolean.valueOf (cancelled);
    }


    /**
     * Detect recursively all potential multi-sample files in the given folder.
     *
     * @param folder The folder to start detection.
     */
    protected abstract void detect (final File folder);


    /**
     * Wait a bit.
     *
     * @return The thread was cancelled if true
     */
    protected boolean waitForDelivery ()
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
     * Split the parts of the path offset between the selected source folder and the currently
     * processed sub-folder.
     *
     * @param msSourceFolder The currently processed sub-folder
     * @param sourceFolder The source folder
     * @param name The name of the multisample
     * @return The array with all parts and the name in reverse order
     */
    protected static String [] createPathParts (final File msSourceFolder, final File sourceFolder, final String name)
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


    /**
     * Gets the name of the file without the ending. E.g. the filename 'aFile.jpeg' will return
     * 'aFile'.
     *
     * @param file The file from which to get the name
     * @return The name of the file without the ending
     */
    protected static String getNameWithoutType (final File file)
    {
        final String filename = file.getName ();
        final int pos = filename.lastIndexOf ('.');
        return pos == -1 ? filename : filename.substring (0, pos);
    }


    /**
     * Log the message to the notifier.
     *
     * @param messageID The ID of the message to get
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     */
    public void log (final String messageID, final String... replaceStrings)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notify (Functions.getMessage (messageID, replaceStrings));
    }


    /**
     * Log the message to the notifier.
     *
     * @param messageID The ID of the message to get
     * @param replaceStrings Replaces the %1..%n in the message with the strings
     */
    protected void logError (final String messageID, final String... replaceStrings)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notifyError (Functions.getMessage (messageID, replaceStrings));
    }


    /**
     * Log the message to the notifier.
     *
     * @param messageID The ID of the message to get
     * @param throwable A throwable
     */
    public void logError (final String messageID, final Throwable throwable)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notifyError (Functions.getMessage (messageID), throwable);
    }


    /**
     * Log the message to the notifier.
     *
     * @param throwable A throwable
     */
    public void logError (final Throwable throwable)
    {
        if (this.notifier.isPresent ())
            this.notifier.get ().notifyError (throwable.getMessage (), throwable);
    }


    protected String subtractPaths (final Optional<File> sourceFolder, final File folder)
    {
        final String analysePath = folder.getAbsolutePath ();

        if (sourceFolder.isEmpty ())
            return analysePath;

        final String sourcePath = sourceFolder.get ().getAbsolutePath ();
        if (analysePath.startsWith (sourcePath))
            return analysePath.substring (sourcePath.length ());

        return analysePath;
    }
}
