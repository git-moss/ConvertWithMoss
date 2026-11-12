// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.text.Normalizer;

import de.mossgrabers.tools.FileUtils;


/**
 * Creates file names which are safe on the media and in the tools around an embedded sampler, not
 * only on the file system of the computer which writes them.
 * <p>
 * {@link FileUtils#createSafeFilename(String)} already replaces the characters which a file system
 * refuses plus the apostrophe and the ampersand - the last two for the sake of the tools which
 * handle the files rather than the file system. The characters below belong to the same group and
 * are replaced here: a converted library is copied to a card, renamed, backed up and read by
 * scripts, and every one of them is expanded by a shell or by the Windows command processor before
 * the file is ever opened. The back-tick is the worst of them, since it substitutes a command.
 * <p>
 * Characters which only look dangerous are deliberately kept: the number sign is part of almost
 * every note name (C#3), the exclamation mark is only expanded by an interactive shell, and the
 * tilde only where it starts a path.
 *
 * @author Jürgen Moßgraber
 */
public final class SafeFileNames
{
    /** The characters which a shell or the Windows command processor expands. */
    private static final String    EXPANDED_CHARACTERS       = "`$;%";
    /** What they are replaced with - the same replacement which FileUtils uses. */
    private static final char      REPLACEMENT               = '_';
    /** Characters which are written out by {@link #createAscii(String)}. */
    private static final String    TRANSLITERATED_CHARACTERS = "äöüÄÖÜßæÆœŒøØđĐłŁþÞðÐ";
    /** The replacements for the characters above. */
    private static final String [] TRANSLITERATIONS          = { "ae", "oe", "ue", "Ae", "Oe", "Ue", "ss", "ae", "AE", "oe", "OE", "o", "O", "d", "D", "l", "L", "th", "Th", "d", "D" };


    /**
     * Constructor.
     */
    private SafeFileNames ()
    {
        // Intentionally empty
    }


    /**
     * Create a file name which is safe on the media and in the tools around an embedded sampler.
     * Applying this twice gives the same result as applying it once, so a name which was already
     * made safe stays the way it is - which is what keeps a file and the reference to it inside a
     * preset in step.
     *
     * @param name The name to make safe, may be null
     * @return The safe name
     */
    public static String create (final String name)
    {
        String result = FileUtils.createSafeFilename (name);
        for (int i = 0; i < EXPANDED_CHARACTERS.length (); i++)
            result = result.replace (EXPANDED_CHARACTERS.charAt (i), REPLACEMENT);
        return result;
    }


    /**
     * Create a safe file name (see {@link #create(String)}) which contains only ASCII characters.
     * German umlauts and some ligatures are written out (e.g. 'ü' becomes 'ue'), accents are
     * removed (e.g. 'í' becomes 'i') and all other non-ASCII characters are replaced. This is for
     * formats and devices which cannot handle other characters in file names.
     *
     * @param name The name to make safe, may be null
     * @return The safe name
     */
    public static String createAscii (final String name)
    {
        final String safeName = create (name);
        final StringBuilder sb = new StringBuilder (safeName.length ());
        for (int i = 0; i < safeName.length (); i++)
        {
            final char c = safeName.charAt (i);
            if (c >= 0x20 && c < 0x7F)
            {
                sb.append (c);
                continue;
            }

            final int index = TRANSLITERATED_CHARACTERS.indexOf (c);
            if (index >= 0)
            {
                sb.append (TRANSLITERATIONS[index]);
                continue;
            }

            // Remove accents and other diacritical marks
            final String base = Normalizer.normalize (Character.toString (c), Normalizer.Form.NFD).replaceAll ("\\p{M}", "");
            if (base.length () == 1 && base.charAt (0) >= 0x20 && base.charAt (0) < 0x7F)
                sb.append (base);
            else
                sb.append (REPLACEMENT);
        }
        return sb.toString ();
    }
}
