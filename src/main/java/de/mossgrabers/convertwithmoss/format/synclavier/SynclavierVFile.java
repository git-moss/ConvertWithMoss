// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * Reads and writes Arturia Synclavier V preset files. A preset is a Boost text archive: one long
 * line of white-space separated tokens where strings are stored as <i>&lt;length&gt;
 * &lt;bytes&gt;</i> (exactly one separating blank, the bytes may contain any character). The
 * archive contains a metadata header, a string map, a parameter map (name to a normalized float)
 * and a blob map (name to raw bytes) which holds the per-partial sound file references
 * (<i>AudioSampleObjectN</i>), the resynthesis frames and the MIDI mappings.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierVFile
{
    private static final String       SIGNATURE            = "serialization::archive";
    private static final String       ARCHIVE_VERSION      = "10";
    private static final String       SCHEMA_VERSION       = "15";

    /** The audio sample object blob header: 0x01, length 22, the signature, 0x01, version 10. */
    private static final byte []      SAMPLE_BLOB_HEADER   = new byte []
    {
        0x01,
        0x16,
        's',
        'e',
        'r',
        'i',
        'a',
        'l',
        'i',
        'z',
        'a',
        't',
        'i',
        'o',
        'n',
        ':',
        ':',
        'a',
        'r',
        'c',
        'h',
        'i',
        'v',
        'e',
        0x01,
        0x0A,
        0,
        0,
        0
    };
    /** The default trailer of an audio sample object: the Boost varints 0, -1, -1. */
    private static final byte []      SAMPLE_BLOB_TRAILER  = new byte []
    {
        0,
        (byte) 0xFF,
        1,
        (byte) 0xFF,
        1
    };
    private static final int          SAMPLE_PATH_LENGTH   = 256;

    /**
     * The Synclavier time table ('Sync30_000', 1251 positions): a normalized time parameter is an
     * interpolated index into this piece-wise linear list. Each row is the first index of the
     * segment, the seconds at that index and the seconds per index step.
     */
    private static final double [] [] TIME_SEGMENTS        = new double [] []
    {
        {
            0,
            0,
            0.0002
        },
        {
            250,
            0.05,
            0.0004
        },
        {
            375,
            0.1,
            0.001
        },
        {
            475,
            0.2,
            0.002
        },
        {
            625,
            0.5,
            0.004
        },
        {
            750,
            1,
            0.01
        },
        {
            850,
            2,
            0.02
        },
        {
            950,
            4,
            0.04
        },
        {
            1100,
            10,
            0.1
        },
        {
            1200,
            20,
            0.2
        }
    };
    private static final int          TIME_TABLE_POSITIONS = 1250;
    private static final double       TIME_TABLE_MAXIMUM   = 30.0;

    String                            name                 = "";
    String                            library              = "";
    String                            author               = "";
    String                            type                 = "";
    final List<String>                tags                 = new ArrayList<> ();
    String                            description          = "";
    long                              timestamp            = 0;
    String                            version              = "";
    final Map<String, String>         metadata             = new TreeMap<> ();
    final Map<String, String>         parameters           = new TreeMap<> ();
    final Map<String, byte []>        blobs                = new TreeMap<> ();
    /** The embedded sound files of an export: the referenced path mapped to the audio file. */
    final Map<String, byte []>        samples              = new LinkedHashMap<> ();


    /**
     * Checks if the given data starts like a preset archive. Exports wrap other payloads (the pack
     * image, the embedded sound files) in archives as well but those continue with <i>10 0 0</i>
     * where a preset has the class version tokens <i>10 0 7</i>.
     *
     * @param data The data to check
     * @return True if it is a preset archive
     */
    public static boolean isArchive (final byte [] data)
    {
        final byte [] start = "22 serialization::archive 10 0 7 ".getBytes (StandardCharsets.US_ASCII);
        if (data.length < start.length)
            return false;
        for (int i = 0; i < start.length; i++)
            if (data[i] != start[i])
                return false;
        return true;
    }


    /**
     * Parses a preset file.
     *
     * @param data The file content
     * @return The parsed preset
     * @throws IOException If the data is not a valid preset archive
     */
    public static SynclavierVFile parse (final byte [] data) throws IOException
    {
        final SynclavierVFile preset = new SynclavierVFile ();
        final Reader reader = new Reader (data);

        if (!SIGNATURE.equals (reader.readString ()))
            throw new IOException ("Not a Boost serialization archive.");
        // The archive version and 4 class info tokens
        reader.readTokens (5);
        preset.name = reader.readString ();
        preset.library = reader.readString ();
        // The schema version token (15 for Synclavier V)
        reader.readToken ();
        preset.author = reader.readString ();
        preset.type = reader.readString ();
        reader.readTokens (2);
        final int numTags = reader.readInt ();
        reader.readToken ();
        for (int i = 0; i < numTags; i++)
            preset.tags.add (reader.readString ());
        reader.readTokens (2);
        preset.description = reader.readString ();
        preset.timestamp = reader.readLong ();
        preset.version = reader.readString ();
        reader.readTokens (7);
        reader.readString ();
        reader.readTokens (2);
        final int numMetadata = reader.readInt ();
        reader.readTokens (3);
        for (int i = 0; i < numMetadata; i++)
        {
            final String key = reader.readString ();
            preset.metadata.put (key, reader.readString ());
        }
        reader.readTokens (10);
        final int numParameters = reader.readInt ();
        reader.readTokens (3);
        for (int i = 0; i < numParameters; i++)
        {
            final String key = reader.readString ();
            preset.parameters.put (key, reader.readToken ());
        }
        final int numBlobs = reader.readInt ();
        reader.readToken ();
        for (int i = 0; i < numBlobs; i++)
        {
            final String key = reader.readString ();
            preset.blobs.put (key, reader.readBytes ());
        }

        // An export ('Export Preset'/'Export Bank') appends a second archive which embeds the
        // referenced sound files: <count> then pairs of the referenced path and the audio file
        if (reader.hasMoreTokens () && SIGNATURE.equals (reader.readString ()))
        {
            reader.readTokens (3);
            final int numSamples = reader.readInt ();
            reader.readTokens (2);
            for (int i = 0; i < numSamples; i++)
            {
                if (i == 0)
                    reader.readToken ();
                final String path = reader.readString ();
                preset.samples.put (path, reader.readBytes ());
            }
        }
        return preset;
    }


    /**
     * Writes the preset in the Boost text archive format.
     *
     * @return The file content
     * @throws IOException Could not write the data
     */
    public byte [] write () throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        writeString (out, SIGNATURE);
        writeTokens (out, " " + ARCHIVE_VERSION + " 0 7 0 7");
        writeSeparator (out);
        writeString (out, this.name);
        writeSeparator (out);
        writeString (out, this.library);
        writeTokens (out, " " + SCHEMA_VERSION);
        writeSeparator (out);
        writeString (out, this.author);
        writeSeparator (out);
        writeString (out, this.type);
        writeTokens (out, " 0 0 " + this.tags.size () + " 0");
        for (final String tag: this.tags)
        {
            writeSeparator (out);
            writeString (out, tag);
        }
        writeTokens (out, " 1 0");
        writeSeparator (out);
        writeString (out, this.description);
        writeTokens (out, " " + this.timestamp);
        writeSeparator (out);
        writeString (out, this.version);
        writeTokens (out, " 0 0 0 0 0 0 0");
        writeSeparator (out);
        writeString (out, "");
        writeTokens (out, " 0 0 " + this.metadata.size () + " 0 0 0");
        for (final Map.Entry<String, String> entry: this.metadata.entrySet ())
        {
            writeSeparator (out);
            writeString (out, entry.getKey ());
            writeSeparator (out);
            writeString (out, entry.getValue ());
        }
        writeTokens (out, " 0 0 0 7 0 0 0 0 0 0 " + this.parameters.size () + " 0 0 0");
        for (final Map.Entry<String, String> entry: this.parameters.entrySet ())
        {
            writeSeparator (out);
            writeString (out, entry.getKey ());
            writeTokens (out, " " + entry.getValue ());
        }
        writeTokens (out, " " + this.blobs.size () + " 0");
        for (final Map.Entry<String, byte []> entry: this.blobs.entrySet ())
        {
            writeSeparator (out);
            writeString (out, entry.getKey ());
            writeSeparator (out);
            final byte [] blob = entry.getValue ();
            out.write (Integer.toString (blob.length).getBytes (StandardCharsets.US_ASCII));
            writeSeparator (out);
            out.write (blob);
        }
        out.write ('\n');

        // The embedded sound files of an export as a second archive
        if (!this.samples.isEmpty ())
        {
            writeString (out, SIGNATURE);
            writeTokens (out, " " + ARCHIVE_VERSION + " 0 0 " + this.samples.size () + " 1 0");
            boolean isFirst = true;
            for (final Map.Entry<String, byte []> entry: this.samples.entrySet ())
            {
                if (isFirst)
                {
                    writeTokens (out, " 1");
                    isFirst = false;
                }
                writeSeparator (out);
                writeString (out, entry.getKey ());
                writeSeparator (out);
                final byte [] audio = entry.getValue ();
                out.write (Integer.toString (audio.length).getBytes (StandardCharsets.US_ASCII));
                writeSeparator (out);
                out.write (audio);
            }
            out.write ('\n');
        }
        return out.toByteArray ();
    }


    /**
     * Gets a normalized parameter value.
     *
     * @param key The parameter name
     * @param defaultValue The value to return if the parameter is not present
     * @return The value
     */
    public double getParameter (final String key, final double defaultValue)
    {
        final String value = this.parameters.get (key);
        if (value == null)
            return defaultValue;
        try
        {
            return Double.parseDouble (value);
        }
        catch (final NumberFormatException ex)
        {
            return defaultValue;
        }
    }


    /**
     * Sets a normalized parameter value. The value is only set if the parameter is already present
     * (all presets carry the full parameter dictionary) or force is true.
     *
     * @param key The parameter name
     * @param value The normalized value
     */
    public void setParameter (final String key, final double value)
    {
        this.parameters.put (key, formatValue (value));
    }


    /**
     * Extracts the sound file path from an audio sample object blob.
     *
     * @param blob The blob (may be null)
     * @return The path or an empty string if there is none
     */
    public static String getSamplePath (final byte [] blob)
    {
        if (blob == null || blob.length < SAMPLE_BLOB_HEADER.length + SAMPLE_PATH_LENGTH)
            return "";
        int end = SAMPLE_BLOB_HEADER.length;
        final int limit = SAMPLE_BLOB_HEADER.length + SAMPLE_PATH_LENGTH;
        while (end < limit && blob[end] != 0)
            end++;
        return new String (blob, SAMPLE_BLOB_HEADER.length, end - SAMPLE_BLOB_HEADER.length, StandardCharsets.UTF_8);
    }


    /**
     * Creates an audio sample object blob which references the given sound file path.
     *
     * @param path The path, relative paths are resolved against the folder of the preset file (or
     *            the Arturia sample pool)
     * @return The blob
     * @throws IOException If the path does not fit into the fixed size path field
     */
    public static byte [] createSampleBlob (final String path) throws IOException
    {
        final byte [] pathBytes = path.getBytes (StandardCharsets.UTF_8);
        if (pathBytes.length >= SAMPLE_PATH_LENGTH)
            throw new IOException ("Sample path does not fit into the sample object: " + path);
        final byte [] blob = new byte [SAMPLE_BLOB_HEADER.length + SAMPLE_PATH_LENGTH + SAMPLE_BLOB_TRAILER.length];
        System.arraycopy (SAMPLE_BLOB_HEADER, 0, blob, 0, SAMPLE_BLOB_HEADER.length);
        System.arraycopy (pathBytes, 0, blob, SAMPLE_BLOB_HEADER.length, pathBytes.length);
        System.arraycopy (SAMPLE_BLOB_TRAILER, 0, blob, SAMPLE_BLOB_HEADER.length + SAMPLE_PATH_LENGTH, SAMPLE_BLOB_TRAILER.length);
        return blob;
    }


    /**
     * Converts a normalized time parameter into seconds using the Synclavier time table.
     *
     * @param normalized The normalized value in the range of [0..1]
     * @return The time in seconds (0 to 30)
     */
    public static double normalizedToSeconds (final double normalized)
    {
        final double index = Math.clamp (normalized, 0, 1) * TIME_TABLE_POSITIONS;
        double [] segment = TIME_SEGMENTS[0];
        for (final double [] candidate: TIME_SEGMENTS)
        {
            if (candidate[0] > index)
                break;
            segment = candidate;
        }
        return segment[1] + (index - segment[0]) * segment[2];
    }


    /**
     * Converts seconds into a normalized time parameter using the Synclavier time table.
     *
     * @param seconds The time in seconds
     * @return The normalized value in the range of [0..1]
     */
    public static double secondsToNormalized (final double seconds)
    {
        final double limited = Math.clamp (seconds, 0, TIME_TABLE_MAXIMUM);
        double [] segment = TIME_SEGMENTS[0];
        double segmentEnd = TIME_TABLE_MAXIMUM;
        for (int i = 0; i < TIME_SEGMENTS.length; i++)
        {
            final double [] candidate = TIME_SEGMENTS[i];
            final double endValue = i + 1 < TIME_SEGMENTS.length ? TIME_SEGMENTS[i + 1][1] : TIME_TABLE_MAXIMUM;
            if (limited <= endValue || i == TIME_SEGMENTS.length - 1)
            {
                segment = candidate;
                segmentEnd = endValue;
                break;
            }
        }
        final double index = segment[0] + (Math.min (limited, segmentEnd) - segment[1]) / segment[2];
        return Math.clamp (index / TIME_TABLE_POSITIONS, 0, 1);
    }


    /**
     * Formats a normalized value like the Boost text archive does (a float with up to 8 significant
     * digits, integers without a decimal point).
     *
     * @param value The value
     * @return The formatted value
     */
    public static String formatValue (final double value)
    {
        final float floatValue = (float) value;
        if (floatValue == Math.rint (floatValue) && Math.abs (floatValue) < 1e8)
            return Integer.toString ((int) floatValue);
        String text = String.format (java.util.Locale.US, "%.8g", Float.valueOf (floatValue));
        // Trim trailing zeros but keep at least one digit after the point
        if (text.contains ("."))
        {
            while (text.endsWith ("0"))
                text = text.substring (0, text.length () - 1);
            if (text.endsWith ("."))
                text = text.substring (0, text.length () - 1);
        }
        return text;
    }


    /** Helper to read the token stream of a Boost text archive. */
    private static class Reader
    {
        private final byte [] data;
        private int           position = 0;


        Reader (final byte [] data)
        {
            this.data = data;
        }


        String readToken () throws IOException
        {
            while (this.position < this.data.length && isWhitespace (this.data[this.position]))
                this.position++;
            if (this.position >= this.data.length)
                throw new IOException ("Unexpected end of archive.");
            final int start = this.position;
            while (this.position < this.data.length && !isWhitespace (this.data[this.position]))
                this.position++;
            return new String (this.data, start, this.position - start, StandardCharsets.US_ASCII);
        }


        boolean hasMoreTokens ()
        {
            int index = this.position;
            while (index < this.data.length && isWhitespace (this.data[index]))
                index++;
            return index < this.data.length;
        }


        void readTokens (final int count) throws IOException
        {
            for (int i = 0; i < count; i++)
                this.readToken ();
        }


        int readInt () throws IOException
        {
            final String token = this.readToken ();
            try
            {
                return Integer.parseInt (token);
            }
            catch (final NumberFormatException ex)
            {
                throw new IOException ("Expected an integer but found: " + token);
            }
        }


        long readLong () throws IOException
        {
            final String token = this.readToken ();
            try
            {
                return Long.parseLong (token);
            }
            catch (final NumberFormatException ex)
            {
                throw new IOException ("Expected an integer but found: " + token);
            }
        }


        byte [] readBytes () throws IOException
        {
            final int length = this.readInt ();
            // Exactly one separating blank
            this.position++;
            if (length < 0 || this.position + length > this.data.length)
                throw new IOException ("String length exceeds the archive size.");
            final byte [] result = new byte [length];
            System.arraycopy (this.data, this.position, result, 0, length);
            this.position += length;
            return result;
        }


        String readString () throws IOException
        {
            return new String (this.readBytes (), StandardCharsets.UTF_8);
        }


        private static boolean isWhitespace (final byte value)
        {
            return value == ' ' || value == '\n' || value == '\r' || value == '\t';
        }
    }


    private static void writeSeparator (final ByteArrayOutputStream out)
    {
        out.write (' ');
    }


    private static void writeTokens (final ByteArrayOutputStream out, final String tokens) throws IOException
    {
        out.write (tokens.getBytes (StandardCharsets.US_ASCII));
    }


    private static void writeString (final ByteArrayOutputStream out, final String text) throws IOException
    {
        final byte [] bytes = text.getBytes (StandardCharsets.UTF_8);
        out.write (Integer.toString (bytes.length).getBytes (StandardCharsets.US_ASCII));
        out.write (' ');
        out.write (bytes);
    }
}
