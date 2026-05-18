// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Parses name/value pairs which are separated by semicolons.
 *
 * @author Jürgen Moßgraber
 */
public class NameValueParser
{
    /**
     * Parses all name/value pairs from the input.
     * 
     * @param input The input like name1=value1;name2=value2
     * @return The pairs, the value of duplicated keys are in the list
     */
    public static Map<String, ArrayList<String>> parse (final String input)
    {
        final Map<String, ArrayList<String>> map = new LinkedHashMap<> ();
        for (final String token: input.split (";"))
        {
            if (token.isBlank ())
                continue;
            final int equalsIndex = token.indexOf ('=');
            if (equalsIndex == -1)
                continue;

            final String key = token.substring (0, equalsIndex).trim ();
            final String value = HTMLUtils.htmlToUnicode (token.substring (equalsIndex + 1).trim ());
            map.computeIfAbsent (key, _ -> new ArrayList<> ()).add (value);
        }
        return map;
    }
}