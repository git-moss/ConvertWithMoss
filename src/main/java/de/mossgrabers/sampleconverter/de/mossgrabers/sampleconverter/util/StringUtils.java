// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2022
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.util;

/**
 * Some helper functions to deal with strings.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class StringUtils
{
    private static final String COMMA_SPLIT = ",";


    /**
     * Splits a string by all commas.
     *
     * @param text The text to split
     * @return The split parts
     */
    public static String [] splitByComma (final String text)
    {
        return split (text, COMMA_SPLIT);
    }


    /**
     * Splits a string with the String split function but returns an empty list in case of null as
     * well as the given text is an empty text or contains only whitespace.
     *
     * @param text The text to split
     * @param regex The regular expression for splitting
     * @return The split parts
     */
    public static String [] split (final String text, final String regex)
    {
        if (text == null || text.isBlank ())
            return new String [0];
        return text.split (regex);
    }
}
