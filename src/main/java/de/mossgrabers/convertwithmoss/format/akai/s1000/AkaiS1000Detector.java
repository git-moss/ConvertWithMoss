// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s1000;

import java.io.File;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.iso.AbstractIsoDetector;
import de.mossgrabers.convertwithmoss.format.iso.IsoFormat;


/**
 * Detects recursively ISO/IMG files in folders for Akai S1000, S1100 and S3000. Files must end with
 * <i>.ISO</i> or <i>.IMG</i>.
 *
 * @author Jürgen Moßgraber
 */
public class AkaiS1000Detector extends AbstractIsoDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public AkaiS1000Detector (final INotifier notifier)
    {
        super ("Akai S1000/S3000", "S1000", notifier, new MetadataSettingsUI ("S1000"), ".iso", ".img");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final IsoFormat isoFormat = identifyIso (sourceFile);
        switch (isoFormat)
        {
            case AKAI_S1000_S1100:
            case AKAI_S3000:
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return this.processAkaiS1000OrS3000 (sourceFile, isoFormat == IsoFormat.AKAI_S3000);

            default:
                this.notifier.logError ("IDS_ISO_WRONG_FORMAT", IsoFormat.getName (IsoFormat.AKAI_S1000_S1100));
                return Collections.emptyList ();
        }
    }
}
