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
import de.mossgrabers.convertwithmoss.format.emu.emulator4.Emulator4Detector;
import de.mossgrabers.convertwithmoss.format.ensoniq.epsasr.EnsoniqEpsAsrDetector;
import de.mossgrabers.convertwithmoss.format.roland.s5xx.S5xxDetector;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Detector;


/**
 * Detects recursively ISO files in folders. Files must end with <i>.ISO</i>.
 *
 * @author Jürgen Moßgraber
 */
public class IsoDetector extends AbstractIsoDetector<MetadataSettingsUI>
{
    private static final String         IDS_ISO_PROCESSING_FORMAT = "IDS_ISO_PROCESSING_FORMAT";

    private final EnsoniqEpsAsrDetector ensoniqDetector;
    private final Emulator4Detector     emulator4Detector;
    private final S5xxDetector          rolandS5xxDetector;
    private final S770Detector          rolandS7xxDetector;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public IsoDetector (final INotifier notifier)
    {
        super ("ISO/IMG file", "ISO", notifier, new MetadataSettingsUI ("ISO"), ".iso", ".img", ".out", ".sdk", ".hda");

        this.emulator4Detector = new Emulator4Detector (notifier);
        this.ensoniqDetector = new EnsoniqEpsAsrDetector (notifier);
        this.rolandS5xxDetector = new S5xxDetector (notifier);
        this.rolandS7xxDetector = new S770Detector (notifier);
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
                this.notifier.log (IDS_ISO_PROCESSING_FORMAT, IsoFormat.getName (isoFormat));
                return AkaiMPC2000Detector.processAkaiMPC2000Disk (sourceFile, this.sourceFolder, this.notifier, this.settingsConfiguration);

            case AKAI_S1000_S1100:
            case AKAI_S3000:
                this.notifier.log (IDS_ISO_PROCESSING_FORMAT, IsoFormat.getName (isoFormat));
                return this.processAkaiS1000Disk (sourceFile);

            case EMU3:
                this.notifier.log (IDS_ISO_PROCESSING_FORMAT, IsoFormat.getName (isoFormat));
                this.emulator4Detector.setSourceFolder (this.sourceFolder);
                this.emulator4Detector.setSettings (this.settingsConfiguration);
                return this.emulator4Detector.readPresetFile (sourceFile);

            case ENSONIQ:
                this.ensoniqDetector.setSourceFolder (this.sourceFolder);
                this.ensoniqDetector.setSettings (this.settingsConfiguration);
                return this.ensoniqDetector.readPresetFile (sourceFile);

            case ISO_9660:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_9660");
                return Collections.emptyList ();

            case ROLAND_S5XX:
                this.notifier.log (IDS_ISO_PROCESSING_FORMAT, IsoFormat.getName (isoFormat));
                this.rolandS5xxDetector.setSourceFolder (this.sourceFolder);
                return this.rolandS5xxDetector.readPresetFile (sourceFile);

            case ROLAND_S7XX:
                this.notifier.log (IDS_ISO_PROCESSING_FORMAT, IsoFormat.getName (isoFormat));
                this.rolandS7xxDetector.setSourceFolder (this.sourceFolder);
                return this.rolandS7xxDetector.readPresetFile (sourceFile);

            case UNKNOWN:
            default:
                this.notifier.logError ("IDS_ISO_UNSUPPORTED_FORMAT", IsoFormat.getName (isoFormat));
                return Collections.emptyList ();
        }
    }
}
