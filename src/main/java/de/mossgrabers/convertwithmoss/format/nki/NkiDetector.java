package de.mossgrabers.convertwithmoss.format.nki;

import java.io.File;
import java.util.function.Consumer;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorWithMetadataPane;

public class NkiDetector extends AbstractDetectorWithMetadataPane<NkiDetectorTask> {

    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public NkiDetector (final INotifier notifier)
    {
        super ("NKI", notifier, "Nki");
    }	
	
	@Override
	public void detect(File folder, Consumer<IMultisampleSource> consumer) {
        this.startDetection (new NkiDetectorTask (this.notifier, consumer, folder, this.metadataPane));
	}

}
