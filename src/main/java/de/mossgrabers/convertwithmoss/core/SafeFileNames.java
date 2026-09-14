// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

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
    private static final String EXPANDED_CHARACTERS = "`$;%";
    /** What they are replaced with - the same replacement which FileUtils uses. */
    private static final char   REPLACEMENT         = '_';


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
}
