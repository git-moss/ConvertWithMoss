// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.iso;

import java.io.File;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.format.akai.mpc2000.AkaiMPC2000Detector;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @author Jürgen Moßgraber
 */
public class IsoDetector extends AbstractIsoDetector<MetadataSettingsUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public IsoDetector (final INotifier notifier)
    {
        super ("ISO/IMG file", "ISO", notifier, new MetadataSettingsUI ("ISO"), ".iso", ".img");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        final IsoFormat isoFormat = IsoFormatIdentifier.identifyIso (sourceFile);
        switch (isoFormat)
        {
            case AKAI_MPC2000:
            case AKAI_MPC2000XL:
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return AkaiMPC2000Detector.processAkaiMPC2000Disk (sourceFile, this.sourceFolder, this.notifier, this.settingsConfiguration);

            case AKAI_S1000_S1100:
            case AKAI_S3000:
                this.notifier.log ("IDS_ISO_PROCESSING_FORMAT", IsoFormat.getName (isoFormat));
                return this.processAkaiS1000Disk (sourceFile);

            case ISO_9660:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_9660");
                return Collections.emptyList ();

            case ROLAND_S550_W30_DJ70:
            case ROLAND_S7XX:
            case UNKNOWN:
            default:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_FORMAT", IsoFormat.getName (isoFormat));
                return Collections.emptyList ();
        }
    }
}
