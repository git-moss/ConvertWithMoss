// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import de.mossgrabers.convertwithmoss.format.ni.maschine.maschine2.MaschinePresetParameterArray;


/**
 * TODO Delete this file
 *
 * @author Jürgen Moßgraber
 */
public class RewriteMxsnd
{
    /**
     * The main method.
     * 
     * @param args The source file and then the output file
     */
    // TODO Rewrite test - remove
    public static void main (final String [] args)
    {
        // try (final InputStream inputStream = new FileInputStream (new File (args[0])); final
        // OutputStream outputStream = new FileOutputStream (new File (args[1])))
        try
        {
            // final NIContainerItem niContainerItem = new NIContainerItem ();
            // niContainerItem.read (inputStream);
            // niContainerItem.write (outputStream);

            final byte [] data = Files.readAllBytes (new File (args[0]).toPath ());
            final MaschinePresetParameterArray array = new MaschinePresetParameterArray (data);

            final byte [] data2 = array.serialize ();
            Files.write (new File ("C:\\Users\\mos\\Desktop\\TEST\\RewriteArray-REWRITE.bin").toPath (), data2);

            if (Arrays.compare (data, data2) == 0)
                System.out.println ("OK!");
            else
                System.out.println ("Fail :-(");

        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
