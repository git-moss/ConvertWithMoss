// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import de.mossgrabers.convertwithmoss.format.bitwig.tools.BitwigPresetTokenizer.Token;


/**
 * Command line utility to dump the recognized structure of Bitwig device preset
 * (<i>.bwpreset</i>) files. Pass a single file or a folder (scanned recursively). Each line is
 * prefixed with the byte offset; scalar parameter values are annotated with their preceding
 * parameter id, so the amplitude envelope, filter, gain and pan values become directly readable.
 *
 * @author Jürgen Moßgraber
 */
public class DumpBitwigPreset
{
    /**
     * The main function.
     *
     * @param arguments The first argument is a <i>.bwpreset</i> file or a folder to scan
     */
    public static void main (final String [] arguments)
    {
        if (arguments == null || arguments.length == 0)
        {
            System.out.println ("Give a .bwpreset file or a folder as the first parameter.");
            return;
        }

        dump (new File (arguments[0]));
    }


    /**
     * Dump a single preset or all presets in a folder (recursively).
     *
     * @param file The file or folder
     */
    private static void dump (final File file)
    {
        if (file.isDirectory ())
        {
            final File [] children = file.listFiles ();
            if (children != null)
                for (final File child: children)
                    dump (child);
            return;
        }

        if (!file.getName ().toLowerCase (Locale.US).endsWith (".bwpreset"))
            return;

        System.out.println ("\n========================================================================");
        System.out.println (file.getAbsolutePath ());
        System.out.println ("========================================================================");

        final List<Token> tokens;
        try
        {
            tokens = BitwigPresetTokenizer.tokenize (file.toPath ());
        }
        catch (final IOException ex)
        {
            System.out.println ("  Could not read: " + ex.getMessage ());
            return;
        }

        String lastName = null;
        for (final Token token: tokens)
        {
            final String position = String.format ("0x%08X %9d", Integer.valueOf (token.offset), Integer.valueOf (token.offset));
            switch (token.type)
            {
                case HEADER:
                    System.out.println (position + "  HEADER    " + BitwigPresetTokenizer.MAGIC + " " + token.text);
                    System.out.println ("                       version=" + headerField (token.text, 0, 8) + " (raw header bytes follow the magic)");
                    break;

                case META_KEY:
                    lastName = token.text;
                    System.out.println (position + "  META-KEY  " + token.text);
                    break;

                case STRING:
                    lastName = token.text;
                    System.out.println (position + "  STRING    \"" + token.text + "\"");
                    break;

                case PARAM:
                    System.out.println (position + String.format ("  PARAM     classId=0x%04X  value=%s   ; %s", Long.valueOf (token.number), Double.toString (token.value), lastName == null ? "?" : lastName));
                    break;

                case OBJECT_START:
                    System.out.println (position + String.format ("  OBJECT    len1=%d len2=%d", Long.valueOf (token.number), Long.valueOf ((long) token.value)));
                    break;

                case RAW:
                default:
                    System.out.println (position + String.format ("  RAW       %d bytes  %s", Integer.valueOf (token.length), token.text));
                    break;
            }
        }
    }


    /**
     * Extract a sub-field of the ASCII-hex header run.
     *
     * @param header The header hex string (without the magic)
     * @param start The start index
     * @param length The number of characters
     * @return The sub-string or the whole header if it is too short
     */
    private static String headerField (final String header, final int start, final int length)
    {
        if (header.length () < start + length)
            return header;
        return header.substring (start, start + length);
    }
}
