// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.nicontainer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.format.ni.nicontainer.NIContainerItem;


/**
 * TODO Delete this file
 *
 * @author Jürgen Moßgraber
 */
public class RewriteChunks
{
    /**
     * The main method.
     *
     * @param args The source file and then the output file
     */
    // TODO Rewrite test - remove
    public static void main (final String [] args)
    {
        try (final InputStream inputStream = new FileInputStream (new File (args[0])); final OutputStream outputStream = new FileOutputStream (new File (args[1])))
        {
            final NIContainerItem niContainerItem = new NIContainerItem ();
            niContainerItem.read (inputStream);
            niContainerItem.write (outputStream);
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
