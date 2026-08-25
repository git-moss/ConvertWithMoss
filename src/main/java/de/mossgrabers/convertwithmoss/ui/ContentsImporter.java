// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.mossgrabers.convertwithmoss.core.ContentsEntry;
import de.mossgrabers.convertwithmoss.ui.ContentsExporter.Format;


/**
 * Reads a list which was written by the {@link ContentsExporter} back in and turns it into the
 * selection of the contents dialog. This closes the loop for building a conversion list in another
 * application: export the contents, pick the presets to convert in e.g. a spreadsheet and import
 * the result to have exactly those presets ticked.
 *
 * A row of the list selects its preset when its 'Selected' field says so; if the list has no such
 * field at all - e.g. because it was written by hand or the rows which should not be converted were
 * simply deleted - every row of it selects its preset. Presets which the list does not mention are
 * not selected, so a shortened list narrows the selection down to what it contains.
 *
 * @author Jürgen Moßgraber
 */
public final class ContentsImporter
{
    /** The columns which the importer understands, lower case. */
    private static final String COLUMN_NAME     = "name";
    private static final String COLUMN_FILE     = "file";
    private static final String COLUMN_PATH     = "path";
    private static final String COLUMN_INDEX    = "index";
    private static final String COLUMN_SELECTED = "selected";

    private static final String SEPARATOR       = "\u0000";


    /**
     * The outcome of reading a list.
     *
     * @param selectedEntries The sources which the list selects
     * @param numberOfRows The number of rows which the list contains
     * @param numberOfUnmatchedRows The number of rows which do not match any of the found sources
     */
    public record ImportResult (Set<ContentsEntry> selectedEntries, int numberOfRows, int numberOfUnmatchedRows)
    {
        // Intentionally empty
    }


    /**
     * One row of a list. All fields are optional, since a list might be written by hand or
     * shortened in another application.
     *
     * @param name The name of the preset
     * @param file The name of the file which contains the preset
     * @param path The full path of that file
     * @param index The index of the preset inside of its file, -1 if the row has none
     * @param isSelected True if the row selects its preset
     */
    private record ListRow (String name, String file, String path, int index, boolean isSelected)
    {
        // Intentionally empty
    }


    /**
     * Private due to helper class.
     */
    private ContentsImporter ()
    {
        // Intentionally empty
    }


    /**
     * Read a list and match its rows against the sources which were found.
     *
     * @param file The file to read
     * @param format The format of the file
     * @param entries All found sources
     * @return The result of the matching
     * @throws IOException Could not read the file or it is not a list at all
     */
    public static ImportResult importList (final File file, final Format format, final List<ContentsEntry> entries) throws IOException
    {
        final String content = Files.readString (file.toPath (), StandardCharsets.UTF_8);
        final List<ListRow> rows = format == Format.JSON ? parseJSON (content) : parseCSV (content);
        return matchRows (rows, entries);
    }


    /**
     * Match the rows of a list against the found sources. A row is matched by its file and the
     * index of the preset inside of it, which identifies a preset even if several of them are named
     * alike; the full path is tried first, so that two files of the same name in different folders
     * do not get mixed up. A row which has no index - e.g. one from a hand-written list - matches
     * every source of that name instead.
     *
     * @param rows The rows of the list
     * @param entries All found sources
     * @return The result of the matching
     */
    private static ImportResult matchRows (final List<ListRow> rows, final List<ContentsEntry> entries)
    {
        final Map<String, ContentsEntry> entriesByPath = new HashMap<> ();
        final Map<String, ContentsEntry> entriesByFile = new HashMap<> ();
        final Map<String, List<ContentsEntry>> entriesByName = new HashMap<> ();
        for (final ContentsEntry entry: entries)
        {
            final File sourceFile = entry.getSourceFile ();
            if (sourceFile != null)
            {
                entriesByPath.put (sourceFile.getAbsolutePath () + SEPARATOR + entry.getIndexInFile (), entry);
                entriesByFile.put (sourceFile.getName ().toLowerCase (Locale.US) + SEPARATOR + entry.getIndexInFile (), entry);
            }
            entriesByName.computeIfAbsent (entry.getName ().toLowerCase (Locale.US), _ -> new ArrayList<> ()).add (entry);
        }

        final Set<ContentsEntry> selectedEntries = new HashSet<> ();
        int numberOfUnmatchedRows = 0;
        for (final ListRow row: rows)
        {
            final List<ContentsEntry> matches = findMatches (row, entriesByPath, entriesByFile, entriesByName);
            if (matches.isEmpty ())
            {
                numberOfUnmatchedRows++;
                continue;
            }
            if (row.isSelected ())
                selectedEntries.addAll (matches);
        }

        return new ImportResult (selectedEntries, rows.size (), numberOfUnmatchedRows);
    }


    /**
     * Find the sources which one row of a list addresses.
     *
     * @param row The row
     * @param entriesByPath The sources by their full path and index
     * @param entriesByFile The sources by their file name and index
     * @param entriesByName The sources by their name
     * @return The matching sources, empty if the row addresses none of them
     */
    private static List<ContentsEntry> findMatches (final ListRow row, final Map<String, ContentsEntry> entriesByPath, final Map<String, ContentsEntry> entriesByFile, final Map<String, List<ContentsEntry>> entriesByName)
    {
        if (row.index () >= 0)
        {
            if (!row.path ().isBlank ())
            {
                final ContentsEntry entry = entriesByPath.get (row.path () + SEPARATOR + row.index ());
                if (entry != null)
                    return List.of (entry);
            }
            if (!row.file ().isBlank ())
            {
                // The library might have been moved since the list was written, then only the name
                // of the file is left to go by
                final ContentsEntry entry = entriesByFile.get (row.file ().toLowerCase (Locale.US) + SEPARATOR + row.index ());
                if (entry != null)
                    return List.of (entry);
            }
        }

        if (row.name ().isBlank ())
            return List.of ();
        final List<ContentsEntry> matches = entriesByName.get (row.name ().toLowerCase (Locale.US));
        return matches == null ? List.of () : matches;
    }


    /**
     * Read the rows of a list which is formatted as comma separated values. The columns are looked
     * up by the names in the header line, so that a list may be reordered or reduced to the columns
     * which matter for picking the presets.
     *
     * @param content The content of the file
     * @return The rows
     * @throws IOException The file has no header line which names at least one known column
     */
    private static List<ListRow> parseCSV (final String content) throws IOException
    {
        final List<List<String>> lines = splitCSV (content);
        if (lines.isEmpty ())
            throw new IOException ("Empty list.");

        final Map<String, Integer> columns = new HashMap<> ();
        final List<String> header = lines.get (0);
        for (int i = 0; i < header.size (); i++)
            columns.put (header.get (i).trim ().toLowerCase (Locale.US), Integer.valueOf (i));
        if (!columns.containsKey (COLUMN_NAME) && !columns.containsKey (COLUMN_FILE))
            throw new IOException ("No 'Name' or 'File' column found in the header line.");
        final boolean hasSelectedColumn = columns.containsKey (COLUMN_SELECTED);

        final List<ListRow> rows = new ArrayList<> ();
        for (int i = 1; i < lines.size (); i++)
        {
            final List<String> line = lines.get (i);
            if (line.size () == 1 && line.get (0).isBlank ())
                continue;
            final String selected = getColumn (line, columns, COLUMN_SELECTED);
            rows.add (new ListRow (getColumn (line, columns, COLUMN_NAME), getColumn (line, columns, COLUMN_FILE), getColumn (line, columns, COLUMN_PATH), parseIndex (getColumn (line, columns, COLUMN_INDEX)), !hasSelectedColumn || isTrue (selected)));
        }
        return rows;
    }


    /**
     * Get the value of one column of a line.
     *
     * @param line The fields of the line
     * @param columns The position of each known column
     * @param column The column to read
     * @return The value, empty if the list does not have that column or the line is too short
     */
    private static String getColumn (final List<String> line, final Map<String, Integer> columns, final String column)
    {
        final Integer position = columns.get (column);
        if (position == null)
            return "";
        final int index = position.intValue ();
        return index < line.size () ? line.get (index).trim () : "";
    }


    /**
     * Split comma separated values into their lines and fields, which needs to be done in one go
     * since a quoted field may contain both separators.
     *
     * @param content The content of the file
     * @return The fields of each line
     */
    private static List<List<String>> splitCSV (final String content)
    {
        final char delimiter = detectDelimiter (content);
        final List<List<String>> lines = new ArrayList<> ();
        List<String> fields = new ArrayList<> ();
        final StringBuilder field = new StringBuilder ();
        boolean isQuoted = false;

        for (int i = 0; i < content.length (); i++)
        {
            final char c = content.charAt (i);
            if (isQuoted)
            {
                // Two quotes inside of a quoted field are one quote
                if (c == '"')
                {
                    if (i + 1 < content.length () && content.charAt (i + 1) == '"')
                    {
                        field.append ('"');
                        i++;
                    }
                    else
                        isQuoted = false;
                }
                else
                    field.append (c);
                continue;
            }

            if (c == delimiter)
            {
                fields.add (field.toString ());
                field.setLength (0);
                continue;
            }

            switch (c)
            {
                case '"' -> isQuoted = true;
                case '\r' -> {
                    // Ignored, the line ends with the following line feed
                }
                case '\n' -> {
                    fields.add (field.toString ());
                    field.setLength (0);
                    lines.add (fields);
                    fields = new ArrayList<> ();
                }
                default -> field.append (c);
            }
        }

        if (!field.isEmpty () || !fields.isEmpty ())
        {
            fields.add (field.toString ());
            lines.add (fields);
        }
        return lines;
    }


    /**
     * Get the character which separates the fields of a line. A spreadsheet writes a semicolon
     * instead of a comma in the countries which use the comma as their decimal separator, and a
     * list which is pasted out of one is separated by tabulators, so the header line decides which
     * of them it is.
     *
     * @param content The content of the file
     * @return The delimiter, a comma if the header line contains none of the others
     */
    private static char detectDelimiter (final String content)
    {
        int commas = 0;
        int semicolons = 0;
        int tabulators = 0;
        boolean isQuoted = false;
        for (int i = 0; i < content.length (); i++)
        {
            final char c = content.charAt (i);
            if (c == '"')
                isQuoted = !isQuoted;
            else if (!isQuoted)
            {
                if (c == '\n')
                    break;
                switch (c)
                {
                    case ',':
                        commas++;
                        break;
                    case ';':
                        semicolons++;
                        break;
                    case '\t':
                        tabulators++;
                        break;
                    default:
                        break;
                }
            }
        }
        if (semicolons > commas && semicolons >= tabulators)
            return ';';
        return tabulators > commas ? '\t' : ',';
    }


    /**
     * Read the rows of a list which is formatted as JSON.
     *
     * @param content The content of the file
     * @return The rows
     * @throws IOException The file is not an array of objects
     */
    private static List<ListRow> parseJSON (final String content) throws IOException
    {
        final JsonNode root = new ObjectMapper ().readTree (content);
        if (root == null || !root.isArray ())
            throw new IOException ("The list is not a JSON array.");

        final List<ListRow> rows = new ArrayList<> ();
        for (final JsonNode node: root)
        {
            if (!node.isObject ())
                continue;
            final JsonNode selectedNode = node.get (COLUMN_SELECTED);
            rows.add (new ListRow (getText (node, COLUMN_NAME), getText (node, COLUMN_FILE), getText (node, COLUMN_PATH), parseIndex (getText (node, COLUMN_INDEX)), selectedNode == null || selectedNode.isNull () || isTrue (selectedNode.asText (""))));
        }
        return rows;
    }


    /**
     * Get the text of one attribute of an object.
     *
     * @param node The object
     * @param name The name of the attribute
     * @return The text, empty if the attribute is missing or null
     */
    private static String getText (final JsonNode node, final String name)
    {
        final JsonNode valueNode = node.get (name);
        return valueNode == null || valueNode.isNull () ? "" : valueNode.asText ("").trim ();
    }


    /**
     * Parse the index of a preset inside of its file.
     *
     * @param value The value to parse
     * @return The index, -1 if the value is not a number
     */
    private static int parseIndex (final String value)
    {
        try
        {
            return value.isBlank () ? -1 : Integer.parseInt (value);
        }
        catch (final NumberFormatException _)
        {
            return -1;
        }
    }


    /**
     * Check if a value means true. Since the list might be edited in another application, a tick
     * ('x'), a '1' and a 'yes' count as well.
     *
     * @param value The value to check
     * @return True if the value means true
     */
    private static boolean isTrue (final String value)
    {
        final String text = value.trim ().toLowerCase (Locale.US);
        return "true".equals (text) || "1".equals (text) || "x".equals (text) || "yes".equals (text);
    }
}
