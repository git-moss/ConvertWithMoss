// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Provides useful functions for working with HTML.
 *
 * @author Jürgen Moßgraber
 */
public class HTMLUtils
{
    private static final String    AMPERSAND       = "&amp;";

    private static final String [] HTML_CHARS      =
    {
        // Used by Unicode to HTML
        "&nbsp;",
        "&iexcl;",
        "&cent;",
        "&pound;",
        "&curren;",
        "&yen;",
        "&brvbar;",
        "&sect;",
        "&uml;",
        "&copy;",
        "&ordf;",
        "&laquo;",
        "&not;",
        "&shy;",
        "&reg;",
        "&macr;",
        "&deg;",
        "&plusmn;",
        "&sup2;",
        "&sup3;",
        "&acute;",
        "&micro;",
        "&para;",
        "&middot;",
        "&cedil;",
        "&sup1;",
        "&ordm;",
        "&raquo;",
        "&frac14;",
        "&frac12;",
        "&frac34;",
        "&iquest;",
        // Used by both
        "&Agrave;",
        "&Aacute;",
        "&Acirc;",
        "&Atilde;",
        "&Auml;",
        "&Aring;",
        "&AElig;",
        "&Ccedil;",
        "&Egrave;",
        "&Eacute;",
        "&Ecirc;",
        "&Euml;",
        "&Igrave;",
        "&Iacute;",
        "&Icirc;",
        "&Iuml;",
        "&ETH;",
        "&Ntilde;",
        "&Ograve;",
        "&Oacute;",
        "&Ocirc;",
        "&Otilde;",
        "&Ouml;",
        "&times;",
        "&Oslash;",
        "&Ugrave;",
        "&Uacute;",
        "&Ucirc;",
        "&Uuml;",
        "&Yacute;",
        "&THORN;",
        "&szlig;",
        "&agrave;",
        "&aacute;",
        "&acirc;",
        "&atilde;",
        "&auml;",
        "&aring;",
        "&aelig;",
        "&ccedil;",
        "&egrave;",
        "&eacute;",
        "&ecirc;",
        "&euml;",
        "&igrave;",
        "&iacute;",
        "&icirc;",
        "&iuml;",
        "&eth;",
        "&ntilde;",
        "&ograve;",
        "&oacute;",
        "&ocirc;",
        "&otilde;",
        "&ouml;",
        "&divide;",
        "&oslash;",
        "&ugrave;",
        "&uacute;",
        "&ucirc;",
        "&uuml;",
        "&yacute;",
        "&thorn;",
        "&yuml;",
        // Used by HTML to Unicode
        "&quot;",
        AMPERSAND,
        "&lt;",
        "&gt;",
        "&circ;",
        "&tilde;"
    };

    // Unicode representation of above starting with "&Agrave;"
    private static final char []   TRANSLATED_CHAR =
    {
        '\u00c0',
        '\u00c1',
        '\u00c2',
        '\u00c3',
        '\u00c4',
        '\u00c5',
        '\u00c6',
        '\u00c7',
        '\u00c8',
        '\u00c9',
        '\u00ca',
        '\u00cb',
        '\u00cc',
        '\u00cd',
        '\u00ce',
        '\u00cf',
        '\u00d0',
        '\u00d1',
        '\u00d2',
        '\u00d3',
        '\u00d4',
        '\u00d5',
        '\u00d6',
        '\u00d7',
        '\u00d8',
        '\u00d9',
        '\u00da',
        '\u00db',
        '\u00dc',
        '\u00dd',
        '\u00de',
        '\u00df',
        '\u00e0',
        '\u00e1',
        '\u00e2',
        '\u00e3',
        '\u00e4',
        '\u00e5',
        '\u00e6',
        '\u00e7',
        '\u00e8',
        '\u00e9',
        '\u00ea',
        '\u00eb',
        '\u00ec',
        '\u00ed',
        '\u00ee',
        '\u00ef',
        '\u00f0',
        '\u00f1',
        '\u00f2',
        '\u00f3',
        '\u00f4',
        '\u00f5',
        '\u00f6',
        '\u00f7',
        '\u00f8',
        '\u00f9',
        '\u00fa',
        '\u00fb',
        '\u00fc',
        '\u00fd',
        '\u00fe',
        '\u00ff',
        '\u0022',
        '\u0026',
        '\u003c',
        '\u003e',
        '\u02c6',
        '\u02dc'
    };

    private static final int       OFFSET          = 32;


    /**
     * Constructor.
     */
    private HTMLUtils ()
    {
        // Intentionally empty
    }


    /**
     * Replaces special characters in the code range 160 to 255 (decimal) in a string by their
     * equivalent HTML named character representation.
     *
     * @param source The string in which to replace special characters
     * @return The string with replaces special characters
     */
    public static String unicodeToHTML (final String source)
    {
        if (source == null)
            return null;
        final StringBuilder builder = new StringBuilder ();
        for (int i = 0; i < source.length (); i++)
        {
            final char c = source.charAt (i);
            final int ci = c;
            if (ci >= 160 && ci < 256)
                builder.append (HTML_CHARS[ci - 160]);
            else if (c == '\'')
                builder.append ("&#39;");
            else
                builder.append (c);
        }
        return builder.toString ();
    }


    /**
     * Translate HTML special chars to Unicode representation.
     *
     * @param source The source code
     * @return The code with replaced special chars
     */
    public static String htmlToUnicode (final String source)
    {
        if (source == null)
            return null;
        int pos1 = 0;
        int pos2;
        final StringBuilder translated = new StringBuilder ();
        boolean found;
        while ((pos2 = source.indexOf ('&', pos1)) != -1)
        {
            translated.append (source.substring (pos1, pos2));
            found = false;
            for (int i = OFFSET; i < OFFSET + 68; i++)
            {
                final int l = HTML_CHARS[i].length ();
                if (source.regionMatches (pos2, HTML_CHARS[i], 0, l))
                {
                    translated.append (TRANSLATED_CHAR[i - OFFSET]);
                    pos1 = pos2 + l;
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                pos1 = pos2 + 1;
                translated.append ('&');
            }
        }

        // Append the rest, if no chars found append complete source
        return translated.append (source.substring (pos1, source.length ())).toString ();
    }


    /**
     * Replaces a quotation mark with the HTML quotation mark symbol.
     *
     * @param s The string in which to replace quotes
     * @return The replaced string
     */
    public static String htmlQuote (final String s)
    {
        return replaceStrings (s, "\"", "&quot;");
    }


    /**
     * Replaces all '&lt;' and '&gt;' with the HTML codes, so that the HTML tags will not be
     * interpreted.
     *
     * @param text The text in which to replace
     * @return The replaced text
     */
    public static String noHTML (final String text)
    {
        return replaceStrings (replaceStrings (htmlQuote (text), "<", "&lt;"), ">", "&gt;");
    }


    /**
     * Formats a filename in a XHTML safe way. Replaces spaces with %20 and &amp; with &amp;amp;.
     *
     * @param filename The filename to format
     * @return The formatted filename
     */
    public static String formatFilename (final String filename)
    {
        return replaceStrings (replaceStrings (filename, " ", "%20"), "&", AMPERSAND);
    }


    /**
     * Replaces a '&amp;' by '&amp;amp;'.
     *
     * @param source The string in which to replace
     * @return The replaced string
     */
    public static String preserveAmp (final String source)
    {
        if (source == null)
            return null;

        final StringBuilder builder = new StringBuilder ();
        int pos = 0;
        int pos2;
        while ((pos2 = source.indexOf ('&', pos)) != -1)
        {
            builder.append (source.substring (pos, pos2)).append (AMPERSAND);
            pos = pos2 + 1;
        }
        return builder.append (source.substring (pos)).toString ();
    }


    /**
     * Limits the given string to a maximum number of characters. If the string is shorter than the
     * maximum number it is not modified. If the string is null, null is returned.
     *
     * @param str The string to limit
     * @param max The maximum number of characters to return
     * @return The limited string or null
     */
    public static String limit (final String str, final int max)
    {
        return str == null ? null : str.substring (0, Math.min (max, str.length ()));
    }


    /**
     * Replaces the string 'oldString' in the string 'operateString' with the 'newString'.
     *
     * @param operateString The string in which to replace
     * @param oldString The string to replace
     * @param newString The string to replace the old one with
     * @return The replaced string
     */
    public static String replaceStrings (final String operateString, final String oldString, final String newString)
    {
        return operateString == null ? null : operateString.replaceAll (Pattern.quote (oldString), Matcher.quoteReplacement (newString));
    }
}
