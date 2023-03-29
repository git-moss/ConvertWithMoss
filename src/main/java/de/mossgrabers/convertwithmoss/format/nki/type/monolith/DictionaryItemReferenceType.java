// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.monolith;

/**
 * A reference type in a Kontakt 2 Monolith dictionary.
 *
 * @author Jürgen Moßgraber
 */
public enum DictionaryItemReferenceType
{
    /** The end. */
    END,
    /** References another sub-directory. */
    DICTIONARY,
    /** References a sample. */
    SAMPLE,
    /** References the NKI section. */
    NKI,
    /** References a wallpaper. */
    WALLPAPER
}
