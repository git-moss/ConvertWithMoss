// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.type.IKontaktFormat;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt1.Kontakt1Type;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt2.Kontakt2Type;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Kontakt5MonolithType;
import de.mossgrabers.convertwithmoss.format.nki.type.kontakt5.Kontakt5Type;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * Detector for Native Instruments Kontakt Instrument (NKI) files.
 *
 * @author Jürgen Moßgraber
 */
public class NkiDetectorTask extends AbstractDetectorTask
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    public NkiDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ".nki", ".nkm");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            final IKontaktFormat format = this.detectFormat (fileAccess);
            final List<IMultisampleSource> result = format.readNKI (this.sourceFolder, sourceFile, fileAccess, this.metadataConfig);
            if (result.isEmpty ())
                this.notifier.logError ("IDS_NKI_COULD_NOT_DETECT_GROUPS");
            return result;
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
        }
        return Collections.emptyList ();
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
        // Is this Kontakt 5+ container format?
        fileAccess.seek (12);
        final String id = StreamUtils.readASCII (fileAccess, 4);
        fileAccess.seek (0);
        if ("hsin".equals (id))
            return new Kontakt5Type (this.notifier);

        final int typeID = fileAccess.readInt ();
        switch (typeID)
        {
            case Magic.KONTAKT5_MONOLITH:
                fileAccess.seek (0);
                return new Kontakt5MonolithType (this.notifier);

            case Magic.KONTAKT1_INSTRUMENT, Magic.KONTAKT1_MULTI:
                return new Kontakt1Type (this.notifier);

            case Magic.KONTAKT2_LITTLE_ENDIAN:
                return new Kontakt2Type (this.notifier, false);

            case Magic.KONTAKT2_BIG_ENDIAN:
                return new Kontakt2Type (this.notifier, true);

            default:
                throw new IOException (Functions.getMessage ("IDS_NKI_UNKNOWN_FILE_ID", Integer.toHexString (typeID).toUpperCase ()));
        }
    }
}
