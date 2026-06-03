// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx.loader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DirectoryEntry;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DiskFormat;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Diskette;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DisketteDirectoryArea;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DisketteParameterArea;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Hd;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770HdDirectoryArea;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Header;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770ParameterArea;


/**
 * Entry point: auto-detects Roland S-770 disk format and loads the image.
 *
 * Usage: java RolandS770Loader /path/to/image.bin
 *
 * @author Jürgen Moßgraber
 */
public class S770Loader
{
    public static void main (final String [] args)
    {
        if (args.length < 1)
        {
            System.err.println ("Usage: RolandS770Loader <path-to-image>");
            System.exit (1);
        }

        final File imageFile = new File (args[0]);
        if (!imageFile.exists () || !imageFile.isFile ())
        {
            System.err.println ("File not found: " + imageFile.getAbsolutePath ());
            System.exit (2);
        }

        try
        {
            load (imageFile);
        }
        catch (final IOException ex)
        {
            System.err.println ("Failed to parse image: " + ex.getMessage ());
            ex.printStackTrace ();
            System.exit (3);
        }
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────


    private static void load (final File imageFile) throws IOException
    {
        System.out.println ("=================================================");
        System.out.println ("Roland S-770 Loader");
        System.out.printf ("File : %s%n", imageFile.getAbsolutePath ());
        System.out.printf ("Size : %,d bytes%n", imageFile.length ());
        System.out.println ("=================================================");

        try (final InputStream in = new BufferedInputStream (new FileInputStream (imageFile)))
        {
            final S770Header header = new S770Header (in);
            final S770DiskFormat format = header.getDiskFormat ();
            System.out.println ("Detected format : " + format);
            System.out.println ();

            switch (format)
            {
                case CD_ROM -> loadCdRom (in, header);
                case DISKETTE -> loadDiskette (in, header);
                default -> throw new IOException ("Unknown S-770 disk format: " + format);
            }
        }
    }


    private static void loadCdRom (final InputStream in, final S770Header header) throws IOException
    {
        System.out.println ("--- Parsing as CD-ROM / Hard-Disk image ---");
        System.out.println ();

        final S770Hd disk = new S770Hd (in, header);
        final S770Header id = disk.getIdArea ();
        System.out.println (id);
        System.out.println ();

        printCdRomDirectorySummary (disk.getDirectoryArea (), id);
        System.out.println (disk.getParameterArea ());
        printActiveCdRomEntries (disk.getDirectoryArea (), disk.getParameterArea (), id);
    }


    private static void printCdRomDirectorySummary (final S770HdDirectoryArea dir, final S770Header id)
    {
        System.out.println ("=== Directory Area Summary ===");
        System.out.printf ("  Volumes      : %d active / %d slots%n", id.getNumVolumes (), dir.getVolumeDirectories ().size ());
        System.out.printf ("  Performances : %d active / %d slots%n", id.getNumPerformances (), dir.getPerformanceDirectories ().size ());
        System.out.printf ("  Patches      : %d active / %d slots%n", id.getNumPatches (), dir.getPatchDirectories ().size ());
        System.out.printf ("  Partials     : %d active / %d slots%n", id.getNumPartials (), dir.getPartialDirectories ().size ());
        System.out.printf ("  Samples      : %d active / %d slots%n", id.getNumSamples (), dir.getSampleDirectories ().size ());
        System.out.println ();
    }


    private static void printActiveCdRomEntries (final S770HdDirectoryArea dir, final S770ParameterArea params, final S770Header id)
    {
        printSection ("Volumes", dir.getVolumeDirectories (), params.getVolumes (), id.getNumVolumes ());
        printSection ("Performances", dir.getPerformanceDirectories (), params.getPerformances (), id.getNumPerformances ());
        printSection ("Patches", dir.getPatchDirectories (), params.getPatches (), id.getNumPatches ());
        printSection ("Partials", dir.getPartialDirectories (), params.getPartials (), id.getNumPartials ());
        printSection ("Samples", dir.getSampleDirectories (), params.getSamples (), id.getNumSamples ());
    }

    // ── Diskette loader ───────────────────────────────────────────────────────


    private static void loadDiskette (final InputStream in, final S770Header header) throws IOException
    {
        System.out.println ("--- Parsing as 3.5\" HD Floppy-Diskette image ---");
        System.out.println ();

        final S770Diskette disk = new S770Diskette (in, header);
        System.out.println (header);
        System.out.println ();

        // TODO

        // ── Region offsets ────────────────────────────────────────────────────
        System.out.printf ("=== Calculated Region Offsets ===%n");
        System.out.printf (" ID Area : 0x%08X (512 bytes)%n", 0);
        System.out.println ();

        // ── Directory summary ─────────────────────────────────────────────────
        final S770DisketteDirectoryArea dir = disk.getDirectoryArea ();
        System.out.println ("=== Directory Summary ===");
        System.out.printf (" Performances : %d%n", dir.getPerformanceDirectories ().size ());
        System.out.printf (" Patches : %d%n", dir.getPatchDirectories ().size ());
        System.out.printf (" Partials : %d%n", dir.getPartialDirectories ().size ());
        System.out.printf (" Samples : %d%n", dir.getSampleDirectories ().size ());
        System.out.println ();

        // ── Detailed dump ─────────────────────────────────────────────────────
        final S770DisketteParameterArea params = disk.getParameterArea ();

        printSection ("Performances", dir.getPerformanceDirectories (), params.getPerformanceEntries ());
        printSection ("Patches", dir.getPatchDirectories (), params.getPatches ());
        printSection ("Partials", dir.getPartialDirectories (), params.getPartials ());
        printSection ("Samples", dir.getSampleDirectories (), params.getSamples ());
    }

    // ── Generic section printer ───────────────────────────────────────────────


    /** Prints directory + parameter pairs for a CD-ROM section (bounded by activeCount). */
    private static <T> void printSection (final String title, final List<S770DirectoryEntry> dirs, final List<T> entries, final int activeCount)
    {
        System.out.println ("=== " + title + " ===");
        for (int i = 0; i < activeCount && i < dirs.size (); i++)
        {
            System.out.println ("  [" + i + "] dir    : " + dirs.get (i));
            System.out.println ("  [" + i + "] params : " + entries.get (i));
        }
        System.out.println ();
    }


    /** Prints directory + parameter pairs for a diskette section (all entries are active). */
    private static <T> void printSection (final String title, final List<S770DirectoryEntry> dirs, final List<T> entries)
    {
        System.out.println ("=== " + title + " ===");
        for (int i = 0; i < dirs.size (); i++)
        {
            System.out.println ("  [" + i + "] dir    : " + dirs.get (i));
            System.out.println ("  [" + i + "] params : " + entries.get (i));
        }
        System.out.println ();
    }
}