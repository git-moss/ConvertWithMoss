// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.IKontaktFormat;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt1.Kontakt1Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.Kontakt2Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Kontakt5Format;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Kontakt5MonolithFormat;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detector for Native Instruments Kontakt Instrument (NKI/NKM) files.
 *
 * @author Jürgen Moßgraber
 */
public class KontaktDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] ENDINGS_ALL          =
    {
        ".nki",
        ".nkm"
    };

    private static final String [] ENDINGS_PERFORMANCES =
    {
        ".nkm"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public KontaktDetector (final INotifier notifier)
    {
        super ("Kontakt NKI/NKM", "Nki", notifier, new MetadataSettingsUI ("Nki"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = detectPerformances ? ENDINGS_PERFORMANCES : ENDINGS_ALL;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            final IKontaktFormat format = this.detectFormat (fileAccess);
            final List<IMultisampleSource> result = format.readNKI (this.sourceFolder, sourceFile, fileAccess, this.settingsConfiguration);
            if (result.isEmpty ())
                this.notifier.logError ("IDS_NI_COULD_NOT_DETECT_GROUPS");
            return result;
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return Collections.emptyList ();
    }


    /** {@inheritDoc} */
    @Override
    protected List<IPerformanceSource> readPerformanceFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return null;

        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            final IKontaktFormat format = this.detectFormat (fileAccess);
            final IPerformanceSource result = format.readNKM (this.sourceFolder, sourceFile, fileAccess, this.settingsConfiguration);
            if (result == null || result.getInstruments ().isEmpty ())
            {
                this.notifier.logError ("IDS_NI_COULD_NOT_DETECT_GROUPS");
                return Collections.emptyList ();
            }
            return Collections.singletonList (result);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return null;
    }


    /**
     * Detect the format of the NKI file. Supported versions are Kontakt 1, 2-4 and 5-7 including
     * monolith files.
     *
     * @param fileAccess The random access file for which to detect the format
     * @return The detected format
     * @throws IOException Could not detect the format
     */
    private IKontaktFormat detectFormat (final RandomAccessFile fileAccess) throws IOException
    {
        final int typeID = fileAccess.readInt ();

        // Is this Kontakt 5+ container format?
        fileAccess.seek (12);
        final String id = StreamUtils.readASCII (fileAccess, 4);
        if ("hsin".equals (id))
        {
            fileAccess.seek (0);
            return new Kontakt5Format (this.notifier);
        }

        fileAccess.seek (4);

        switch (typeID)
        {
            case Magic.KONTAKT1_INSTRUMENT_LE, Magic.KONTAKT1_MULTI_LE:
                return new Kontakt1Format (this.notifier, false);

            case Magic.KONTAKT1_INSTRUMENT_BE, Magic.KONTAKT1_MULTI_BE:
                return new Kontakt1Format (this.notifier, true);

            // Note: these magic bytes are also used in NKI 1.5.x files
            case Magic.KONTAKT2_INSTRUMENT_LE, Magic.KONTAKT2_INSTRUMENT_BE, Magic.KONTAKT2_MULTI_LE, Magic.KONTAKT2_MULTI_BE:
                final boolean isBigEndian = typeID == Magic.KONTAKT2_INSTRUMENT_BE || typeID == Magic.KONTAKT2_MULTI_BE;
                final boolean version1 = StreamUtils.readUnsigned32 (fileAccess, isBigEndian) == 0x24;
                fileAccess.seek (4);
                return version1 ? new Kontakt1Format (this.notifier, isBigEndian) : new Kontakt2Format (this.notifier, isBigEndian);

            case Magic.KONTAKT5_MONOLITH:
                fileAccess.seek (0);
                return new Kontakt5MonolithFormat (this.notifier);

            default:
                throw new IOException (Functions.getMessage ("IDS_NI_UNKNOWN_FILE_ID", Integer.toHexString (typeID).toUpperCase ()));
        }
    }
}
