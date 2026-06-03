// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx;

/**
 * Storage medium type for Roland S-770 images.
 *
 * @author Jürgen Moßgraber
 */
public enum S770DiskFormat
{
    /** CD-ROM or Hard-Disk image – includes directories. */
    CD_ROM,

    /** 3.5″ HD floppy-diskette image. */
    DISKETTE
}