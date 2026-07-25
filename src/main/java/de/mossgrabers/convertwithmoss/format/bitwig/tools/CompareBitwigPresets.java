// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.bitwig.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mossgrabers.convertwithmoss.format.bitwig.tools.BitwigPresetTokenizer.Token;
import de.mossgrabers.convertwithmoss.format.bitwig.tools.BitwigPresetTokenizer.TokenType;


/**
 * Command line utility to compare two Bitwig device preset (<i>.bwpreset</i>) files by their named
 * scalar parameters. Each scalar parameter atom is paired with its preceding parameter id, then
 * the two name-to-value maps are diffed. Saving two presets that differ only in (for example) the
 * amplitude release and comparing them reveals exactly which parameter changed and the two stored
 * values - the data needed to calibrate the seconds-to-internal envelope-time mapping.
 *
 * @author Jürgen Moßgraber
 */
public class CompareBitwigPresets
{
    /**
     * The main function.
     *
     * @param arguments The absolute paths of the two files to compare
     */
    public static void main (final String [] arguments)
    {
        if (arguments == null || arguments.length != 2)
        {
            System.out.println ("Give two .bwpreset files to compare as the first and second parameter.");
            return;
        }

        final Map<String, Double> values1;
        final Map<String, Double> values2;
        final String header1;
        final String header2;
        try
        {
            final List<Token> tokens1 = BitwigPresetTokenizer.tokenize (Path.of (arguments[0]));
            final List<Token> tokens2 = BitwigPresetTokenizer.tokenize (Path.of (arguments[1]));
            values1 = namedValues (tokens1);
            values2 = namedValues (tokens2);
            header1 = header (tokens1);
            header2 = header (tokens2);
        }
        catch (final IOException ex)
        {
            System.out.println ("Could not read: " + ex.getMessage ());
            return;
        }

        System.out.println ("A: " + arguments[0]);
        System.out.println ("B: " + arguments[1]);
        System.out.println ();

        if (!header1.equals (header2))
        {
            System.out.println ("Header differs:");
            System.out.println ("  A: " + header1);
            System.out.println ("  B: " + header2);
            System.out.println ();
        }

        final Set<String> allNames = new LinkedHashSet<> ();
        allNames.addAll (values1.keySet ());
        allNames.addAll (values2.keySet ());

        int changed = 0;
        int onlyA = 0;
        int onlyB = 0;
        for (final String name: allNames)
        {
            final Double a = values1.get (name);
            final Double b = values2.get (name);
            if (a != null && b != null)
            {
                if (a.doubleValue () != b.doubleValue ())
                {
                    System.out.println (String.format ("CHANGED  %-28s  %s  ->  %s", name, a, b));
                    changed++;
                }
            }
            else if (a != null)
            {
                System.out.println (String.format ("ONLY A   %-28s  %s", name, a));
                onlyA++;
            }
            else
            {
                System.out.println (String.format ("ONLY B   %-28s  %s", name, b));
                onlyB++;
            }
        }

        System.out.println ();
        System.out.println (String.format ("%d parameter(s) in A, %d in B - %d changed, %d only in A, %d only in B.", Integer.valueOf (values1.size ()), Integer.valueOf (values2.size ()), Integer.valueOf (changed), Integer.valueOf (onlyA), Integer.valueOf (onlyB)));
    }


    /**
     * Build an ordered map of parameter-name to value. A scalar parameter is named by the string
     * atom that immediately precedes it. Repeated names (e.g. one Sampler per drum pad) are
     * disambiguated with a {@code #n} suffix.
     *
     * @param tokens The tokens of one preset
     * @return The ordered name-to-value map
     */
    private static Map<String, Double> namedValues (final List<Token> tokens)
    {
        final Map<String, Double> values = new LinkedHashMap<> ();
        final Map<String, Integer> counts = new LinkedHashMap<> ();
        String lastName = null;
        for (final Token token: tokens)
        {
            if (token.type == TokenType.STRING || token.type == TokenType.META_KEY)
            {
                lastName = token.text;
                continue;
            }
            if (token.type != TokenType.PARAM)
                continue;

            final String base = lastName == null ? "?" : lastName;
            final int occurrence = counts.merge (base, Integer.valueOf (1), Integer::sum).intValue ();
            final String key = occurrence == 1 ? base : base + "#" + occurrence;
            values.put (key, Double.valueOf (token.value));
        }
        return values;
    }


    /**
     * Get the header text of the first token.
     *
     * @param tokens The tokens of one preset
     * @return The header hex run or an empty string
     */
    private static String header (final List<Token> tokens)
    {
        if (tokens.isEmpty () || tokens.get (0).type != TokenType.HEADER)
            return "";
        return tokens.get (0).text;
    }
}
