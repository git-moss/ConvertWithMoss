// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.detector;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.AbstractCoreTask;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;


/**
 * Base class for detector descriptors.
 *
 * @param <T> The type of the descriptor task
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractDetector<T extends AbstractDetectorTask> extends AbstractCoreTask implements IDetector
{
    private final ExecutorService executor             = Executors.newSingleThreadExecutor ();
    private Optional<T>           detectorTaskOptional = Optional.empty ();


    /**
     * Constructor.
     *
     * @param name The name of the object.
     * @param notifier The notifier
     */
    protected AbstractDetector (final String name, final INotifier notifier)
    {
        super (name, notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> multisampleSourceConsumer, final Consumer<IPerformanceSource> performanceSourceConsumer, final boolean detectPerformances)
    {
        // Override to implement performance source consumer as well

        this.detect (folder, multisampleSourceConsumer);
    }


    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.executor.shutdown ();
    }


    /** {@inheritDoc} */
    @Override
    public void cancel ()
    {
        if (this.detectorTaskOptional.isEmpty ())
            return;
        this.detectorTaskOptional.get ().cancel (true);
        this.detectorTaskOptional = Optional.empty ();
    }


    /**
     * Start the detection process.
     *
     * @param detectorTask The detector task to start
     */
    protected void startDetection (final T detectorTask)
    {
        this.detectorTaskOptional = Optional.of (detectorTask);
        detectorTask.setOnCancelled (event -> this.notifier.updateButtonStates (true));
        detectorTask.setOnFailed (event -> this.notifier.updateButtonStates (true));
        detectorTask.setOnSucceeded (event -> this.notifier.updateButtonStates (true));
        detectorTask.setOnRunning (event -> this.notifier.updateButtonStates (false));
        detectorTask.setOnScheduled (event -> this.notifier.updateButtonStates (false));
        this.executor.execute (detectorTask);
    }


    /** {@inheritDoc} */
    @Override
    public boolean validateParameters ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return false;
    }
}
