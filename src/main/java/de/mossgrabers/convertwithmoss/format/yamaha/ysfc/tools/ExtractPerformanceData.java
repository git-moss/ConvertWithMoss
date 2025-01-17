// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcChunk;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YamahaYsfcEntry;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YsfcFile;


/**
 * Writes all Performance data arrays into individual files.
 *
 * @author Jürgen Moßgraber
 */
public class ExtractPerformanceData
{
    /**
     * Writes all Performance data arrays into individual files.
     * 
     * @param args First argument is the absolute path to the YSFC file. Second argument is the
     *            output folder, which needs to exist.
     */
    public static void main (final String [] args)
    {
        if (args.length < 2)
        {
            System.out.println ("First argument is the absolute path to the YSFC file. Second argument is the output folder, which needs to exist.");
            return;
        }

        final String outputFolder = args[1];

        YsfcFile ysfcFile;
        try
        {
            ysfcFile = new YsfcFile (new File (args[0]));
            final Map<String, YamahaYsfcChunk> chunks = ysfcFile.getChunks ();
            final YamahaYsfcChunk epfmChunk = chunks.get (YamahaYsfcChunk.ENTRY_LIST_PERFORMANCE);
            final YamahaYsfcChunk dpfmChunk = chunks.get (YamahaYsfcChunk.DATA_LIST_PERFORMANCE);
            if (epfmChunk == null || dpfmChunk == null)
            {
                System.out.println ("The file does not contain any Performance data.");
                return;
            }

            final List<YamahaYsfcEntry> epfmListChunks = epfmChunk.getEntryListChunks ();
            final List<byte []> dpfmListChunks = dpfmChunk.getDataArrays ();
            if (epfmListChunks.size () != dpfmListChunks.size ())
            {
                System.out.println ("Broken file with different number of EPFM and DPFM entries.");
                return;
            }

            for (int i = 0; i < epfmListChunks.size (); i++)
            {
                final YamahaYsfcEntry entry = epfmListChunks.get (i);
                final byte [] performanceData = dpfmListChunks.get (i);
                Files.write (new File (outputFolder, entry.getItemTitle () + "DPFM.bin").toPath (), performanceData);
            }

        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
