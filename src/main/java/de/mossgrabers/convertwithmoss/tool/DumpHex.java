package de.mossgrabers.convertwithmoss.tool;

import java.io.FileInputStream;


public class DumpHex
{

    public static void main (final String [] args)
    {
        final int s = 0;
        final int n = 200;

        try (FileInputStream in = new FileInputStream ("C:\\Users\\mos\\Desktop\\Tests\\TestEmpty.nki"))
        {
            in.skipNBytes (s);

            for (int i = 0; i < n; i++)
            {
                final int value = in.read ();
                if (value == -1 || value == 0x78)
                    break;

                System.out.print (String.format ("%02X", value));
            }
        }
        catch (final Exception ex)
        {
            ex.printStackTrace ();
        }
    }

}
