// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.ContentsEntry;


/**
 * Writes the list of the sources which are shown in the contents dialog to a text file, so that the
 * contents of a bank, a disk image or a whole preset folder can be kept as an inventory or be
 * processed by another application, e.g. a spreadsheet or a script. Only the list is written, the
 * presets themselves are not converted.
 *
 * The field names are intentionally not translated, since they are read by other applications and
 * must therefore not depend on the language of the user interface.
 *
 * @author Jürgen Moßgraber
 */
public final class ContentsExporter
{
    /** The file formats into which the contents can be written. */
    public enum Format
    {
        /** Comma separated values with one line per source. */
        CSV,
        /** An array with one object per source. */
        JSON
    }


    private static final String [] COLUMNS =
    {
        "Name",
        "Category",
        "Zones",
        "Key Low",
        "Key High",
        "MIDI Low",
        "MIDI High",
        "Folder",
        "File",
        "Container",
        "Index",
        "Selected",
        "Path"
    };


    /**
     * Private due to helper class.
     */
    private ContentsExporter ()
    {
        // Intentionally empty
    }


    /**
     * Write the given sources to a file.
     *
     * @param file The file to write to, it is overwritten if it already exists
     * @param format The format to write
     * @param entries The sources to write, in the order in which they are displayed
     * @param selectedEntries The sources which are ticked, they are marked as selected
     * @throws IOException Could not write the file
     */
    public static void export (final File file, final Format format, final List<ContentsEntry> entries, final Set<ContentsEntry> selectedEntries) throws IOException
    {
        final String content = format == Format.JSON ? createJSON (entries, selectedEntries) : createCSV (entries, selectedEntries);
        Files.writeString (file.toPath (), content, StandardCharsets.UTF_8);
    }


    /**
     * Format the sources as comma separated values with a header line.
     *
     * @param entries The sources to write
     * @param selectedEntries The sources which are ticked
     * @return The formatted contents
     */
    private static String createCSV (final List<ContentsEntry> entries, final Set<ContentsEntry> selectedEntries)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < COLUMNS.length; i++)
        {
            if (i > 0)
                sb.append (',');
            sb.append (COLUMNS[i]);
        }
        sb.append ('\n');

        for (final ContentsEntry entry: entries)
        {
            final int lowestKey = entry.getLowestKey ();
            final boolean hasKeyRange = lowestKey >= 0;
            final File sourceFile = entry.getSourceFile ();

            appendCSV (sb, entry.getName (), true);
            appendCSV (sb, entry.getCategory (), false);
            appendCSV (sb, Integer.toString (entry.getNumberOfZones ()), false);
            appendCSV (sb, hasKeyRange ? ContentsEntry.formatNote (lowestKey) : "", false);
            appendCSV (sb, hasKeyRange ? ContentsEntry.formatNote (entry.getHighestKey ()) : "", false);
            appendCSV (sb, hasKeyRange ? Integer.toString (lowestKey) : "", false);
            appendCSV (sb, hasKeyRange ? Integer.toString (entry.getHighestKey ()) : "", false);
            appendCSV (sb, String.join ("/", entry.getFolderPath ()), false);
            appendCSV (sb, sourceFile == null ? "" : sourceFile.getName (), false);
            appendCSV (sb, String.join ("/", entry.getContainerPath ()), false);
            appendCSV (sb, Integer.toString (entry.getIndexInFile ()), false);
            appendCSV (sb, Boolean.toString (selectedEntries.contains (entry)), false);
            appendCSV (sb, sourceFile == null ? "" : sourceFile.getAbsolutePath (), false);
            sb.append ('\n');
        }

        return sb.toString ();
    }


    /**
     * Append one field to a line, quoted if necessary.
     *
     * @param sb Where to append the field
     * @param value The value of the field
     * @param isFirst True if it is the first field of the line, which needs no separator
     */
    private static void appendCSV (final StringBuilder sb, final String value, final boolean isFirst)
    {
        if (!isFirst)
            sb.append (',');
        // Only quote a field which needs it, which keeps the file readable
        if (value.indexOf (',') < 0 && value.indexOf ('"') < 0 && value.indexOf ('\n') < 0 && value.indexOf ('\r') < 0)
        {
            sb.append (value);
            return;
        }
        sb.append ('"').append (value.replace ("\"", "\"\"")).append ('"');
    }


    /**
     * Format the sources as an array of JSON objects. A source which has no zones at all has no key
     * range either, which is written as null.
     *
     * @param entries The sources to write
     * @param selectedEntries The sources which are ticked
     * @return The formatted contents
     */
    private static String createJSON (final List<ContentsEntry> entries, final Set<ContentsEntry> selectedEntries)
    {
        final StringBuilder sb = new StringBuilder ("[\n");

        for (int i = 0; i < entries.size (); i++)
        {
            final ContentsEntry entry = entries.get (i);
            final int lowestKey = entry.getLowestKey ();
            final int highestKey = entry.getHighestKey ();
            final boolean hasKeyRange = lowestKey >= 0;
            final File sourceFile = entry.getSourceFile ();

            sb.append ("    {\n");
            appendJSON (sb, "name", quoteJSON (entry.getName ()));
            appendJSON (sb, "category", quoteJSON (entry.getCategory ()));
            appendJSON (sb, "zones", Integer.toString (entry.getNumberOfZones ()));
            appendJSON (sb, "keyLow", hasKeyRange ? quoteJSON (ContentsEntry.formatNote (lowestKey)) : "null");
            appendJSON (sb, "keyHigh", hasKeyRange ? quoteJSON (ContentsEntry.formatNote (highestKey)) : "null");
            appendJSON (sb, "midiLow", hasKeyRange ? Integer.toString (lowestKey) : "null");
            appendJSON (sb, "midiHigh", hasKeyRange ? Integer.toString (highestKey) : "null");
            appendJSON (sb, "folder", createJSONArray (entry.getFolderPath ()));
            appendJSON (sb, "file", sourceFile == null ? "null" : quoteJSON (sourceFile.getName ()));
            appendJSON (sb, "container", createJSONArray (entry.getContainerPath ()));
            appendJSON (sb, "index", Integer.toString (entry.getIndexInFile ()));
            appendJSON (sb, "selected", Boolean.toString (selectedEntries.contains (entry)));
            // The last attribute must not be followed by a comma
            sb.append ("        \"path\": ").append (sourceFile == null ? "null" : quoteJSON (sourceFile.getAbsolutePath ())).append ('\n');
            sb.append (i == entries.size () - 1 ? "    }\n" : "    },\n");
        }

        return sb.append ("]\n").toString ();
    }


    /**
     * Append one attribute of an object, followed by a comma.
     *
     * @param sb Where to append the attribute
     * @param name The name of the attribute
     * @param value The already formatted value of the attribute
     */
    private static void appendJSON (final StringBuilder sb, final String name, final String value)
    {
        sb.append ("        \"").append (name).append ("\": ").append (value).append (",\n");
    }


    /**
     * Format a list of names as a JSON array.
     *
     * @param values The names
     * @return The formatted array
     */
    private static String createJSONArray (final List<String> values)
    {
        final StringBuilder sb = new StringBuilder ("[");
        for (int i = 0; i < values.size (); i++)
        {
            if (i > 0)
                sb.append (", ");
            sb.append (quoteJSON (values.get (i)));
        }
        return sb.append (']').toString ();
    }


    /**
     * Format a text as a JSON string with all characters escaped which are not allowed in it.
     *
     * @param text The text to format
     * @return The quoted text
     */
    private static String quoteJSON (final String text)
    {
        final StringBuilder sb = new StringBuilder ("\"");
        for (int i = 0; i < text.length (); i++)
        {
            final char c = text.charAt (i);
            switch (c)
            {
                case '"' -> sb.append ("\\\"");
                case '\\' -> sb.append ("\\\\");
                case '\b' -> sb.append ("\\b");
                case '\f' -> sb.append ("\\f");
                case '\n' -> sb.append ("\\n");
                case '\r' -> sb.append ("\\r");
                case '\t' -> sb.append ("\\t");
                default -> {
                    if (c < 0x20)
                        sb.append (String.format ("\\u%04x", Integer.valueOf (c)));
                    else
                        sb.append (c);
                }
            }
        }
        return sb.append ('"').toString ();
    }
}
