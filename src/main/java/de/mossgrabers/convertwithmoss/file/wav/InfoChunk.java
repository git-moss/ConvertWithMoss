// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.wav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.riff.AbstractListChunk;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;


/**
 * Class for a list chunk with info sub-chunks.
 *
 * @author Jürgen Moßgraber
 */
public class InfoChunk extends AbstractListChunk
{
    private final SimpleDateFormat    standardDateFormat  = new SimpleDateFormat ("MMMM d, yyyy", Locale.ENGLISH);

    private final SimpleDateFormat [] creationDateParsers = new SimpleDateFormat []
    {
        // 'Month Day, Year' where Month is initially capitalized and is the conventional full
        // English spelling of the month, Day is the date in decimal followed by a comma, and Year
        // is the full decimal year.
        this.standardDateFormat,
        // Other variations found which do not comply to the specification
        new SimpleDateFormat ("dd'st' MMMM yyyy", Locale.ENGLISH),
        new SimpleDateFormat ("dd'nd' MMMM yyyy", Locale.ENGLISH),
        new SimpleDateFormat ("dd'rd' MMMM yyyy", Locale.ENGLISH),
        new SimpleDateFormat ("dd'th' MMMM yyyy", Locale.ENGLISH),
        new SimpleDateFormat ("dd MMMM yyyy", Locale.ENGLISH),
        new SimpleDateFormat ("MM/dd/yyyy h:mm:ss a"),
        new SimpleDateFormat ("MM/dd/yyyy hh:mm:ss"),
        new SimpleDateFormat ("MM/dd/yyyy"),
        new SimpleDateFormat ("yyyy-MM-dd h:mm:ss a"),
        new SimpleDateFormat ("yyyy-MM-dd hh:mm:ss"),
        new SimpleDateFormat ("yyyy-MM-dd"),
        new SimpleDateFormat ("EEEE d MMMM yyyy, HH:mm:ss", Locale.ENGLISH)
    };


    /**
     * Default constructor.
     */
    public InfoChunk ()
    {
        super (RiffID.INFO_ID.getId ());
    }


    /**
     * Get all info fields from the sub-chunks.
     *
     * @return The sub-chunk content with their descriptive name as the key
     */
    public Map<String, String> getInfoFields ()
    {
        final Map<String, String> fields = new TreeMap<> ();
        for (final IChunk chunk: this.subChunks)
            fields.put (RiffID.fromId (chunk.getId ()).getName (), chunkDataToAsciiString (chunk));
        return fields;
    }


    /**
     * Get the value of 1 info sub-chunks.
     *
     * @param infoChunkID The ID of the info chunk to get
     * @return The sub-chunk content as an ASCII string
     */
    public String getInfoField (final RiffID infoChunkID)
    {
        final int id = infoChunkID.getId ();
        for (final IChunk chunk: this.subChunks)
            if (chunk.getId () == id)
                return chunkDataToAsciiString (chunk);
        return null;
    }


    /**
     * Get the value of an info field.
     *
     * @param riffIDs The IDs of the info fields to check
     * @return The first present value of the given IDs is returned
     */
    public String getInfoField (final RiffID... riffIDs)
    {
        for (final RiffID id: riffIDs)
        {
            final String value = this.getInfoField (id);
            if (value != null)
                return value;
        }
        return null;
    }


    /**
     * Create a new info-sub-chunk and add it to this info chunk.
     *
     * @param infoChunkID The ID of the info sub-chunk to create
     * @param value The sub-chunk content
     * @param maxLength The maximum text length including 2 zero bytes at the end
     */
    public void addInfoTextField (final RiffID infoChunkID, final String value, final int maxLength)
    {
        byte [] content = value.getBytes (StandardCharsets.US_ASCII);

        final int paddingLength = content.length % 2 == 0 ? 2 : 1;
        final int fullLength = content.length + paddingLength;

        final int diff = fullLength - maxLength;
        if (diff > 0)
            content = Arrays.copyOf (content, fullLength - diff);

        content = Arrays.copyOf (content, content.length + paddingLength);
        this.addInfoField (infoChunkID, content);
    }


    /**
     * Create a new info-sub-chunk and add it to this info chunk.
     *
     * @param infoChunkID The ID of the info sub-chunk to create
     * @param content The sub-chunk content
     */
    public void addInfoField (final RiffID infoChunkID, final byte [] content)
    {
        final RawRIFFChunk riffChunk = new RawRIFFChunk (RiffID.INFO_ID.getId (), infoChunkID.getId (), content.length);
        riffChunk.setData (content);
        this.add (riffChunk);
    }


    /**
     * Get the creation date as a date object.
     *
     * @return The date object
     */
    public Date getCreationDate ()
    {
        final String dateTime = this.getInfoField (RiffID.INFO_ICRD, RiffID.INFO_IDIT, RiffID.INFO_YEAR);
        if (dateTime != null)
            for (final SimpleDateFormat parser: this.creationDateParsers)
                try
                {
                    return parser.parse (dateTime);
                }
                catch (final java.text.ParseException _)
                {
                    // Ignore
                }
        return new Date ();
    }


    /**
     * Set the creation date as a date object.
     *
     * @param date The date object
     */
    public void addCreationDate (final Date date)
    {
        this.addInfoTextField (RiffID.INFO_ICRD, this.standardDateFormat.format (date == null ? new Date () : date), 256);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        for (final IChunk chunk: this.subChunks)
        {
            if (!sb.isEmpty ())
                sb.append ('\n');
            sb.append (RiffID.fromId (chunk.getId ()).getName ()).append (": ").append (chunkDataToAsciiString (chunk));
        }
        return sb.toString ();
    }


    private static String chunkDataToAsciiString (final IChunk chunk)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        try
        {
            chunk.writeData (out);
        }
        catch (final IOException _)
        {
            return "";
        }
        return new String (out.toByteArray (), StandardCharsets.US_ASCII);
    }
}
