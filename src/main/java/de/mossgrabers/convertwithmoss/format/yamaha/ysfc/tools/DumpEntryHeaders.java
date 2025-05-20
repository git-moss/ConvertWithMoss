// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.yamaha.ysfc.tools;

import java.io.File;
import java.io.IOException;

import de.mossgrabers.convertwithmoss.format.yamaha.ysfc.YsfcFile;


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
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
