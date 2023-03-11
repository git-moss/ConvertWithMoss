// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * A CSV (comma separated values) file which contains two columns separated by either ';' or ','.
 * The first column contains the source name and the second column to new name to replace the source
 * with.
 *
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class CSVRenameFile
{
    private final Map<String, String> renamingTable = new HashMap<> ();


    /**
     * Get a mapped name for the given source name.
     *
     * @param sourceName The name for which to get the mapping
     * @return The mapped name or null if not present
     */
    public String getMapping (final String sourceName)
    {
        return this.renamingTable.get (sourceName);
    }


    /**
     * Clears the mapping table.
     */
    public void clear ()
    {
        this.renamingTable.clear ();
    }


    /**
     * Initializes the file renaming by loading the provided CSV file if renaming is active.
     *
     * @param mappingFile The mapping file
     * @return true if renaming is not active or the provided CSV file could be loaded successfully,
     *         false else.
     */
    public boolean setRenameFile (final File mappingFile)
    {
        this.clear ();

        try
        {
            final String content = FileUtils.readUTF8 (mappingFile);

            final String [] lines = content.split ("\\r?\\n");
            for (int i = 0; i < lines.length; i++)
            {
                final String [] columns = lines[i].split ("[;,]");
                if (columns.length != 2)
                {
                    Functions.message ("@IDS_NOTIFY_RENAMING_CSV_NOT_TWO_COLUMNS", Integer.toString (columns.length), Integer.toString (i));
                    return false;
                }
                this.renamingTable.put (columns[0].trim (), columns[1].trim ());
            }
        }
        catch (final IOException ex)
        {
            Functions.message ("@IDS_NOTIFY_RENAMING_CSV_IO_EXCEPTION", ex.getMessage ());
            return false;
        }

        return true;
    }


    /**
     * Check if there are mappings.
     *
     * @return Returns true if the map is empty
     */
    public boolean isEmpty ()
    {
        return this.renamingTable.isEmpty ();
    }
}
