// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector;

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
     * @param notifier Where to notify about progress and errors
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    public void configure (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        this.notifier = Optional.of (notifier);
        this.consumer = Optional.ofNullable (consumer);
        this.sourceFolder = Optional.ofNullable (sourceFolder);
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
            this.notifier.get ().notifyError (ex.getMessage (), ex);
        }
        final boolean cancelled = this.isCancelled ();
        this.notifier.get ().notify (Functions.getMessage (cancelled ? "IDS_NOTIFY_CANCELLED" : "IDS_NOTIFY_FINISHED"));
        return Boolean.valueOf (cancelled);
    }


    /**
     * Detect recursivly all potential multi-sample files in the given folder.
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
}
