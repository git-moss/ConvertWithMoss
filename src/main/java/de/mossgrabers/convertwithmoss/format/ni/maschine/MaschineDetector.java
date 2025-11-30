// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine1.Maschine1Format;
import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.Maschine2Format;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detector for Native Instruments Maschine Sound (mxsnd) files.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String [] ENDINGS_ALL =
    {
        // TODO ".mxsnd",
        ".msnd"
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MaschineDetector (final INotifier notifier)
    {
        super ("Maschine (mxsnd)", "Maschine", notifier, new MetadataSettingsUI ("Maschine"));
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = ENDINGS_ALL;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try (final RandomAccessFile fileAccess = new RandomAccessFile (sourceFile, "r"))
        {
            final IMaschineFormat format = this.detectFormat (fileAccess);
            final List<IMultisampleSource> result = format.readSound (this.sourceFolder, sourceFile, fileAccess, this.settingsConfiguration);
            if (result.isEmpty ())
                this.notifier.logError ("IDS_NI_COULD_NOT_DETECT_GROUPS");
            return result;
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NI_MASCHINE_READ_ERROR", ex);
        }
        return Collections.emptyList ();
    }


    /**
     * Detect the format of the Maschine file. Supported versions are Maschine 1 and 2/3.
     *
     * @param fileAccess The random access file for which to detect the format
     * @return The detected format
     * @throws IOException Could not detect the format
     */
    private IMaschineFormat detectFormat (final RandomAccessFile fileAccess) throws IOException
    {
        final String startTag = StreamUtils.readASCII (fileAccess, 4);
        fileAccess.seek (0);
        if (Maschine1Format.START_TAG_LITTLE_ENDIAN.equals (startTag) || Maschine1Format.START_TAG_BIG_ENDIAN.equals (startTag))
            return new Maschine1Format (this.notifier);

        final int typeID = fileAccess.readInt ();

        // Is this Maschine 2+ container format?
        fileAccess.seek (12);
        final String id = StreamUtils.readASCII (fileAccess, 4);
        fileAccess.seek (0);
        if ("hsin".equals (id))
            return new Maschine2Format (this.notifier);

        throw new IOException (Functions.getMessage ("IDS_NI_UNKNOWN_FILE_ID", Integer.toHexString (typeID).toUpperCase ()));
    }
}
