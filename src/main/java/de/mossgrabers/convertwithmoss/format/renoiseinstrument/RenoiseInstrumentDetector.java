package de.mossgrabers.convertwithmoss.format.renoiseinstrument;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.INotifier;

import java.io.File;
import java.util.function.Consumer;

public class RenoiseInstrumentDetector extends AbstractDetector<RenoiseInstrumentDetectorTask>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public RenoiseInstrumentDetector (final INotifier notifier)
    {
        super ("Renoise Instrument", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void detect (final File folder, final Consumer<IMultisampleSource> consumer)
    {
        this.startDetection (new RenoiseInstrumentDetectorTask (this.notifier, consumer, folder));
    }    
}
