// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2.monolith;

/**
 * The type of a Kontakt 2 Monolith directory entry.
 *
 * @author Jürgen Moßgraber
 */
public enum DirectoryEntryType
{
    /** The end. */
    END,
    /** References another sub-directory. */
    DIRECTORY,
    /** References a sample. */
    SAMPLE,
    /** References the NKI section. */
    NKI,
    /** References a wallpaper. */
    WALLPAPER
}
