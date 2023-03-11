package de.mossgrabers.convertwithmoss.tool;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;

import java.io.File;
import java.io.IOException;


public class WriteWAV
{
    public static void main (final String [] args)
    {
        // writeEmptyFile ();

        try
        {
            final WaveFile wavFile = new WaveFile (new File ("C:\\Users\\mos\\Desktop\\EmptyMono.wav"), false);
            System.out.println (wavFile.getFormatChunk ().infoText ());

            final WaveFile wavFile2 = new WaveFile (new File ("C:\\Users\\mos\\Desktop\\EmptyMono2.wav"), false);
            System.out.println ("\n" + wavFile2.getFormatChunk ().infoText ());
        }
        catch (IOException | ParseException ex)
        {
            // TODO Auto-generated catch block
            ex.printStackTrace ();
        }

    }


    private static void writeEmptyFile ()
    {
        final WaveFile wavFile = new WaveFile (1, 44100, 16, 0);
        try
        {
            wavFile.write (new File ("C:\\Users\\mos\\Desktop\\Tests\\EmptyMono.wav"));
        }
        catch (final IOException ex)
        {
            ex.printStackTrace ();
        }
    }
}
