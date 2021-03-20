// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.detector.sfz;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.util.Locale;
import java.util.Optional;


/**
 * Detects recursivly SFZ multisample files in folders. Files must end with <i>.sfz</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class SfzDetectorTask extends AbstractDetectorTask
{
    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        // Detect all wav files in the folder
        this.notifier.get ().notify (Functions.getMessage ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ()));
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
            return name.toLowerCase (Locale.US).endsWith (".sfz");
        });
        if (wavFiles.length == 0)
            return;

        // TODO parse a SfzMetadata object
        // String Files.readString​(Path path) -> liest UTF-8
        final Optional<IMultisampleSource> multisample = Optional.empty ();
        if (multisample.isEmpty ())
            return;

        // Check for task cancellation
        if (!this.isCancelled ())
            this.consumer.get ().accept (multisample.get ());
    }
}
