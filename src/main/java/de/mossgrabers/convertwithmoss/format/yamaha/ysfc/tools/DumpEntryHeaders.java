// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.tools;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YsfcFile;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcChunk;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcPerformance;
import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.file.YamahaYsfcPerformancePart;


/**
 * Dumps all entry headers of a YSFC file. The absolute path of the file needs to be given as the
 * first parameter.
 *
 * @author Jürgen Moßgraber
 */
public class DumpEntryHeaders
{
    /**
     * Dump all entry headers of the given file
     *
     * @param args The absolute path to the file as the first argument
     */
    public static void main (final String [] args)
    {
        if (args.length == 0)
        {
            System.out.println ("The absolute path of the file needs to be given as the first parameter.");
            return;
        }

        try
        {
            final YsfcFile ysfcFile = new YsfcFile (new File (args[0]));
            System.out.println (ysfcFile.dump (0));

            final YamahaYsfcChunk dpfmChunk = ysfcFile.getChunks ().get (YamahaYsfcChunk.DATA_LIST_PERFORMANCE);
            if (dpfmChunk == null)
                return;

            System.out.println ("\nPerformances:");
            final List<byte []> dpfmListChunks = dpfmChunk.getDataArrays ();
            for (final byte [] performanceData: dpfmListChunks)
            {
                final YamahaYsfcPerformance performance = new YamahaYsfcPerformance (new ByteArrayInputStream (performanceData), ysfcFile.getFileFormat (), ysfcFile.getVersion ());
                final String performanceName = performance.getName ();
                final List<YamahaYsfcPerformancePart> parts = performance.getParts ();
                System.out.println ("    " + performanceName);
                for (int j = 0; j < parts.size (); j++)
                    System.out.println ("        Part " + (j + 1) + ": " + parts.get (j).getName ());
            }
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
