// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.core.detector;

import de.mossgrabers.sampleconverter.core.AbstractObjectDescriptor;
import de.mossgrabers.sampleconverter.core.IDetectorDescriptor;
import de.mossgrabers.sampleconverter.core.INotifier;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Base class for detector descriptors.
 *
 * @param <T> The type of the descriptor task
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public abstract class AbstractDetectorDescriptor<T extends AbstractDetectorTask> extends AbstractObjectDescriptor implements IDetectorDescriptor
{
    private final ExecutorService executor = Executors.newSingleThreadExecutor ();
    private INotifier             notifier;
    private Optional<T>           detector = Optional.empty ();


    /**
     * Constructor.
     *
     * @param name The name of the object.
     */
    protected AbstractDetectorDescriptor (final String name)
    {
        super (name);
    }


    /** {@inheritDoc} */
    @Override
    public void configure (final INotifier notifier)
    {
        this.notifier = notifier;
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
        if (this.detector.isEmpty ())
            return;
        this.detector.get ().cancel (true);
        this.detector = Optional.empty ();
    }


    /**
     * Start the detection process.
     *
     * @param detector The detector to start
     */
    protected void startDetection (final T detector)
    {
        this.detector = Optional.of (detector);
        detector.configure (this.notifier);
        detector.setOnCancelled (event -> this.updateButtonStates (true));
        detector.setOnFailed (event -> this.updateButtonStates (true));
        detector.setOnSucceeded (event -> this.updateButtonStates (true));
        detector.setOnRunning (event -> this.updateButtonStates (false));
        detector.setOnScheduled (event -> this.updateButtonStates (false));
        this.executor.execute (detector);
    }


    /**
     * Forward a button state update to the notifier.
     *
     * @param canClose True to enable the close button
     */
    protected void updateButtonStates (final boolean canClose)
    {
        if (this.notifier != null)
            this.notifier.updateButtonStates (canClose);
    }
}
