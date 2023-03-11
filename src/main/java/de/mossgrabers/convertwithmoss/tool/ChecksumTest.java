package de.mossgrabers.convertwithmoss.tool;

import java.io.FileInputStream;
import java.util.zip.CRC32;


public class ChecksumTest
{

    public static void main (final String [] args)
    {
        for (int s = 0; s < 32; s += 4)
        {

            try (FileInputStream in = new FileInputStream ("C:\\Users\\mos\\Desktop\\Tests\\TestEmpty.nki"))
            {
                in.skipNBytes (s);

                // final Adler32 checksum = new Adler32 ();
                final CRC32 checksum = new CRC32 ();

                int value;
                while ((value = in.read ()) != 0xB9)
                {
                    checksum.update (value);
                }
                System.out.println (Long.toHexString (checksum.getValue ()));
                System.out.println (Long.toHexString (0xFFFFFF - checksum.getValue ()));

                in.skipNBytes (3);

                System.out.println ("MORE");

                for (int i = 0; i < 4; i++)
                {
                    value = in.read ();
                    checksum.update (value);
                }

                System.out.println (Long.toHexString (checksum.getValue ()).toUpperCase ());
                System.out.println (Long.toHexString (0xFFFFFF - checksum.getValue ()).toUpperCase () + "\n");
            }
            catch (final Exception ex)
            {
                ex.printStackTrace ();
            }

        }
    }

}
