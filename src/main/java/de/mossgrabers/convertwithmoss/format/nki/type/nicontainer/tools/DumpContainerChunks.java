// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mossgrabers.convertwithmoss.format.nki.type.nicontainer.NIContainerItem;


/**
 * Dumps all chunks of a NI Container file. The absolute path of the file needs to be given as the
 * first parameter.
 *
 * @author Jürgen Moßgraber
 */
public class DumpContainerChunks
{
    /**
     * Dumps all chunks of a NI Container file to the console.
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

        try (final InputStream inputStream = new FileInputStream (new File (args[0])))
        {
            final NIContainerItem niContainerItem = new NIContainerItem ();
            try
            {
                niContainerItem.read (inputStream);
            }
            catch (final Exception ex)
            {
                ex.printStackTrace ();
            }
            System.out.println (niContainerItem.dump (0));
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
