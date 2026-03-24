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
import java.nio.file.Files;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.KontaktPresetAccessor;
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
     * @param args Not used
     */
    public static void mainX (final String [] args)
    {
        try
        {
            final byte [] data = Files.readAllBytes (new File ("C:\\Users\\mos\\Desktop\\TEST\\FM8Jam-presetChunks.bin").toPath ());
            final KontaktPresetAccessor presetAccessor = new KontaktPresetAccessor ();
            presetAccessor.readKontaktPresetChunks (data);
            final byte [] dataCopy = presetAccessor.writeKontaktPresetChunks ();

            final int minimum = Math.min (data.length, dataCopy.length);
            for (int i = 0; i < minimum; i++)
                if (data[i] != dataCopy[i])
                    System.out.println (i);

            if (Arrays.compare (data, dataCopy) == 0)
                System.out.println ("Yay!");
            else
                System.out.println ("Nay! :-(");
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }


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
