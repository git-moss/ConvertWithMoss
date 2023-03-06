// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.nki.K2MetadataFileParser;
import de.mossgrabers.convertwithmoss.format.nki.SoundinfoDocument;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;

import org.xml.sax.SAXException;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;


/**
 * Can handle NKI files in Kontakt 2 format.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Kontakt2Type extends AbstractKontaktType
{
    private static final String               NULL_ENTRY      = "(null)";

    private static final Set<String>          KNOWN_BLOCK_IDS = new HashSet<> ();
    private static final Map<Integer, String> ICON_MAP        = new HashMap<> ();
    static
    {
        KNOWN_BLOCK_IDS.add ("2noK"); // Kontakt 2
        KNOWN_BLOCK_IDS.add ("Kon3"); // Kontakt 3
        KNOWN_BLOCK_IDS.add ("3noK"); // Kontakt 3
        KNOWN_BLOCK_IDS.add ("4noK"); // Kontakt 4
        KNOWN_BLOCK_IDS.add ("iPkA"); // Akustik Piano from Kontakt 3 Library

        ICON_MAP.put (Integer.valueOf (0x00), "Organ");
        ICON_MAP.put (Integer.valueOf (0x01), "Cello");
        ICON_MAP.put (Integer.valueOf (0x02), "Drum Kit");
        ICON_MAP.put (Integer.valueOf (0x03), "Bell");
        ICON_MAP.put (Integer.valueOf (0x04), "Trumpet");
        ICON_MAP.put (Integer.valueOf (0x05), "Guitar");
        ICON_MAP.put (Integer.valueOf (0x06), "Piano");
        ICON_MAP.put (Integer.valueOf (0x07), "Marimba");
        ICON_MAP.put (Integer.valueOf (0x08), "Record Player");
        ICON_MAP.put (Integer.valueOf (0x09), "E-Piano");
        ICON_MAP.put (Integer.valueOf (0x0A), "Drum Pads");
        ICON_MAP.put (Integer.valueOf (0x0B), "Bass Guitar");
        ICON_MAP.put (Integer.valueOf (0x0C), "Electric Guitar");
        ICON_MAP.put (Integer.valueOf (0x0D), "Wave");
        ICON_MAP.put (Integer.valueOf (0x0E), "Asian Symbol");
        ICON_MAP.put (Integer.valueOf (0x0F), "Flute");
        ICON_MAP.put (Integer.valueOf (0x10), "Speaker");
        ICON_MAP.put (Integer.valueOf (0x11), "Score");
        ICON_MAP.put (Integer.valueOf (0x12), "Conga");
        ICON_MAP.put (Integer.valueOf (0x13), "Pipe Organ");
        ICON_MAP.put (Integer.valueOf (0x14), "FX");
        ICON_MAP.put (Integer.valueOf (0x15), "Computer");
        ICON_MAP.put (Integer.valueOf (0x16), "Violin");
        ICON_MAP.put (Integer.valueOf (0x17), "Surround");
        ICON_MAP.put (Integer.valueOf (0x18), "Synthesizer");
        ICON_MAP.put (Integer.valueOf (0x19), "Microphone");
        ICON_MAP.put (Integer.valueOf (0x1A), "Oboe");
        ICON_MAP.put (Integer.valueOf (0x1B), "Saxophone");
        ICON_MAP.put (Integer.valueOf (0x1C), "New");
    }

    private final K2MetadataFileParser parser;


    /**
     * Constructor.
     * 
     * @param notifier Where to report errors
     */
    public Kontakt2Type (final INotifier notifier)
    {
        super (notifier);

        this.parser = new K2MetadataFileParser (notifier);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> parse (final File sourceFolder, final File sourceFile, final RandomAccessFile fileAccess, final IMetadataConfig metadata) throws IOException
    {
        // No idea yet about these 12 bytes...
        StreamUtils.skipNBytes (fileAccess, 12);

        String version = readVersion (fileAccess);

        final String blockID = StreamUtils.readASCII (fileAccess, 4);
        if (!KNOWN_BLOCK_IDS.contains (blockID))
            this.notifier.log ("IDS_NKI_UNKNOWN_BLOCK_ID", blockID);

        final Date creation = StreamUtils.readTimestampLSB (fileAccess);
        final SimpleDateFormat sdf = new SimpleDateFormat ("dd.MM.yyyy HH:mm:ss", Locale.GERMAN);
        sdf.setTimeZone (TimeZone.getTimeZone ("UTC+1"));
        final String formattedCreation = sdf.format (creation);

        // No idea yet about these 26 bytes...
        StreamUtils.skipNBytes (fileAccess, 26);

        final String iconName = ICON_MAP.get (Integer.valueOf (StreamUtils.readIntLSB (fileAccess)));
        final String author = StreamUtils.readASCII (fileAccess, 8, StandardCharsets.ISO_8859_1).trim ();

        // No idea yet about these 3 bytes...
        StreamUtils.skipNBytes (fileAccess, 3);

        String website = StreamUtils.readASCII (fileAccess, 86).trim ();
        if (website.isBlank () || NULL_ENTRY.equals (website))
            website = null;

        // No idea yet about these 7 bytes...
        StreamUtils.skipNBytes (fileAccess, 7);

        if (version.startsWith ("4") && !version.startsWith ("4.0") && !version.startsWith ("4.1"))
        {
            // 12 new bytes introduced in 4.2
            StreamUtils.skipNBytes (fileAccess, 12);
        }

        // No idea yet about these 4 bytes... could be the ZLIB length or a checksum...
        StreamUtils.skipNBytes (fileAccess, 4);

        final int patchLevel = StreamUtils.readIntLSB (fileAccess);
        if (version.endsWith ("?"))
            version = version.substring (0, version.length () - 1) + Integer.toString (patchLevel);

        // Is it a monolith?
        final boolean isMonolith = fileAccess.read () != 0x78;
        fileAccess.seek (fileAccess.getFilePointer () - 1);
        this.notifier.log ("IDS_NKI_FOUND_KONTAKT_TYPE", "2", version, isMonolith ? " - monolith" : "");

        if (isMonolith)
        {
            // TODO
            return readMonolith (fileAccess);
        }

        final String xmlCode = readZLIB (fileAccess);

        final int numOfPendingbytes = (int) (sourceFile.length () - fileAccess.getFilePointer ());
        final SoundinfoDocument soundinfo = this.readSoundinfo (fileAccess, numOfPendingbytes, author, iconName);

        try
        {
            final List<IMultisampleSource> multiSamples = this.parser.parse (sourceFolder, sourceFile, xmlCode, metadata);
            updateMetadata (multiSamples, formattedCreation, website, soundinfo);
            return multiSamples;
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


    private SoundinfoDocument readSoundinfo (final RandomAccessFile fileAccess, final int numOfPendingbytes, final String author, final String iconName) throws IOException
    {
        if (numOfPendingbytes > 0)
        {
            // Unknown so far, checksum of ZLIB?
            StreamUtils.skipNBytes (fileAccess, 12);

            final byte [] rest = new byte [numOfPendingbytes - 12];
            fileAccess.readFully (rest);

            final String soundinfoXML = new String (rest, StandardCharsets.UTF_8);
            try
            {
                final SoundinfoDocument soundinfo = new SoundinfoDocument (soundinfoXML);
                final Set<String> categories = soundinfo.getCategories ();
                if (categories.isEmpty ())
                    categories.add (iconName);
                return soundinfo;
            }
            catch (final SAXException ex)
            {
                this.notifier.logError ("IDS_NKI_UNSOUND_SOUNDINFO", ex);
            }
        }
        return new SoundinfoDocument (author, iconName);
    }


    private List<IMultisampleSource> readMonolith (final RandomAccessFile fileAccess) throws IOException
    {
        // Skip the monolith header, no idea yet about these bytes
        StreamUtils.skipNBytes (fileAccess, 30);

        // TODO Implement

        return Collections.emptyList ();
    }


    /**
     * Reads and formats the Kontakt version number with which the file was created.
     *
     * @param in The input stream
     * @return The formatted version number, ends with a '?' if the patch level needs to be read
     *         separately.
     * @throws IOException
     */
    private static String readVersion (final DataInput in) throws IOException
    {
        final byte [] buffer = new byte [4];
        in.readFully (buffer);

        final StringBuilder sb = new StringBuilder ();
        for (int i = 3; i > 0; i--)
            sb.append (Integer.toString (buffer[i])).append ('.');
        if (buffer[0] == -1)
            sb.append ('?');
        else
            sb.append (String.format ("%03d", Integer.valueOf (buffer[0])));
        return sb.toString ();
    }


    /**
     * Update the metadata info on all multi samples.
     *
     * @param multiSamples The multi samples to update
     * @param creation The formatted creation date
     * @param website The web site link
     * @param soundinfo The sound info
     */
    private static void updateMetadata (final List<IMultisampleSource> multiSamples, final String creation, final String website, final SoundinfoDocument soundinfo)
    {
        String additionalInfo = "Creation: " + creation;
        if (website != null)
            additionalInfo += "\nWebsite : " + website;

        for (final IMultisampleSource multiSample: multiSamples)
        {
            // Update the author
            final String soundAuthor = soundinfo.getAuthor ();
            if (soundAuthor != null && !soundAuthor.isBlank ())
                multiSample.setCreator (soundAuthor);

            // Update the category and keywords
            final Set<String> soundCategories = soundinfo.getCategories ();
            if (!soundCategories.isEmpty ())
                multiSample.setCategory (soundCategories.iterator ().next ());
            Collections.addAll (soundCategories, multiSample.getKeywords ());
            multiSample.setKeywords (TagDetector.detectKeywords (soundCategories.toArray (new String [soundCategories.size ()])));

            // Update the description
            String description = multiSample.getDescription ();
            description = description == null ? "" : "\n" + description;
            multiSample.setDescription (additionalInfo + description);
        }
    }
}
