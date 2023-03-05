// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.nki.NiSSMetadataFileParser;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;


/**
 * Can handle NKI files in Kontakt 1 format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Kontakt1Type extends AbstractKontaktType
{
    private final NiSSMetadataFileParser parser;


    /**
     * Constructor.
     * 
     * @param notifier Where to report errors
     */
    public Kontakt1Type (final INotifier notifier)
    {
        super (notifier);

        this.parser = new NiSSMetadataFileParser (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> parse (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadata) throws IOException
    {
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE_1");

        // Read the offset to the ZLIB part, 8 bytes have already been read
        final int offset = StreamUtils.readIntLSB (fileAccess) - 8;
        if (fileAccess.skipBytes (offset) != offset)
            throw new IOException (Functions.getMessage ("IDS_NKI_FILE_CORRUPTED"));

        try
        {
            final String xmlCode = readZLIB (fileAccess);
            return this.parser.parse (sourceFolder, sourceFile, xmlCode, metadata);
        }
        catch (final UnsupportedEncodingException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_ILLEGAL_CHARACTER", ex);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        return Collections.emptyList ();
    }
}
