// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.roland.s7xx.loader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DirectoryEntry;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DiskFormat;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Diskette;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770DisketteDirectoryArea;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Hd;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770HdDirectoryArea;
import de.mossgrabers.convertwithmoss.format.roland.s7xx.S770Header;


/**
 * Auto-detects Roland S-770 disk format and loads the image.
 *
 * Usage: java RolandS770Loader /path/to/image.bin
 *
 * @author Jürgen Moßgraber
 */
public class S770Loader
{
    /**
     * The main method.
     *
     * @param args First argument is the file-path of the image to load
     */
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


    private static void load (final File imageFile) throws IOException
    {
        System.out.println ("=================================================");
        System.out.println ("Roland S-770 Loader");
        System.out.printf ("File : %s%n", imageFile.getAbsolutePath ());
        System.out.printf ("Size : %,d bytes%n", Long.valueOf (imageFile.length ()));
        System.out.println ("=================================================");

        try (final InputStream input = new BufferedInputStream (new FileInputStream (imageFile)))
        {
            final S770Header header = new S770Header (input);
            final S770DiskFormat format = header.getDiskFormat ();
            System.out.println ("Detected format : " + format);
            System.out.println ();

            switch (format)
            {
                case CD_ROM -> loadCdRom (input, header);
                case DISKETTE -> loadDiskette (input, header, imageFile.getParentFile ());
                default -> throw new IOException ("Unknown S-770 disk format: " + format);
            }
        }
    }


    private static void loadCdRom (final InputStream in, final S770Header header) throws IOException
    {
        System.out.println ("--- Parsing as CD-ROM / Hard-Disk image ---");
        System.out.println ();

        final S770Hd disk = new S770Hd (in, header);
        System.out.println (header);
        System.out.println ();

        printCdRomDirectorySummary (disk.getDirectoryArea (), header);
        System.out.println (disk);
        printActiveCdRomEntries (disk);
    }


    private static void printCdRomDirectorySummary (final S770HdDirectoryArea dir, final S770Header id)
    {
        System.out.println ("=== Directory Area Summary ===");
        System.out.printf ("  Volumes      : %d active / %d slots%n", Integer.valueOf (id.getNumVolumes ()), Integer.valueOf (dir.getVolumeDirectories ().size ()));
        System.out.printf ("  Performances : %d active / %d slots%n", Integer.valueOf (id.getNumPerformances ()), Integer.valueOf (dir.getPerformanceDirectories ().size ()));
        System.out.printf ("  Patches      : %d active / %d slots%n", Integer.valueOf (id.getNumPatches ()), Integer.valueOf (dir.getPatchDirectories ().size ()));
        System.out.printf ("  Partials     : %d active / %d slots%n", Integer.valueOf (id.getNumPartials ()), Integer.valueOf (dir.getPartialDirectories ().size ()));
        System.out.printf ("  Samples      : %d active / %d slots%n", Integer.valueOf (id.getNumSamples ()), Integer.valueOf (dir.getSampleDirectories ().size ()));
        System.out.println ();
    }


    private static void printActiveCdRomEntries (final S770Hd image)
    {
        final S770HdDirectoryArea dir = image.getDirectoryArea ();
        final S770Header header = image.getHeader ();
        printCdRomSection ("Volumes", dir.getVolumeDirectories (), image.getVolumes (), header.getNumVolumes ());
        printCdRomSection ("Performances", dir.getPerformanceDirectories (), image.getPerformances (), header.getNumPerformances ());
        printCdRomSection ("Patches", dir.getPatchDirectories (), image.getPatches (), header.getNumPatches ());
        printCdRomSection ("Partials", dir.getPartialDirectories (), image.getPartials (), header.getNumPartials ());
        printCdRomSection ("Samples", dir.getSampleDirectories (), image.getSamples (), header.getNumSamples ());
    }


    private static void loadDiskette (final InputStream in, final S770Header header, final File parentPath) throws IOException
    {
        System.out.println ("--- Parsing as 3.5\" HD Floppy-Diskette image ---");
        System.out.println ();

        final int indexDiskette = header.getIndexDiskette ();
        final int numDiskettes = header.getNumDiskettes ();
        if (indexDiskette != 0)
        {
            System.out.println ("This is a continuation disk (disk " + (indexDiskette + 1) + " of " + (numDiskettes + 1) + "). Cancelled.");
            return;
        }

        final String diskName = header.getDiskName ();
        final List<byte []> continuationData = new ArrayList<> ();
        for (int i = 1; i <= numDiskettes; i++)
        {
            final byte [] continuationDisk = S770Diskette.findContinuationDisk (diskName, i, numDiskettes, parentPath);
            if (continuationDisk == null)
            {
                System.out.println ("Could not find continuation disk " + (i + 1) + " of " + (numDiskettes + 1) + ". Cancelled.");
                return;
            }
            System.out.println ("Found continuation disk " + (i + 1) + " of " + (numDiskettes + 1) + ".");
            continuationData.add (continuationDisk);
        }

        final S770Diskette disk = new S770Diskette (in, header, continuationData);
        System.out.println (header);
        System.out.println ();

        // Directory summary
        final S770DisketteDirectoryArea dir = disk.getDirectoryArea ();
        System.out.println ("=== Directory Summary ===");
        System.out.printf (" Performances : %d%n", Integer.valueOf (dir.getPerformanceDirectories ().size ()));
        System.out.printf (" Patches : %d%n", Integer.valueOf (dir.getPatchDirectories ().size ()));
        System.out.printf (" Partials : %d%n", Integer.valueOf (dir.getPartialDirectories ().size ()));
        System.out.printf (" Samples : %d%n", Integer.valueOf (dir.getSampleDirectories ().size ()));
        System.out.println ();

        // Detailed dump
        printDisketteSection ("Performances", dir.getPerformanceDirectories (), disk.getPerformances ());
        printDisketteSection ("Patches", dir.getPatchDirectories (), disk.getPatches ());
        printDisketteSection ("Partials", dir.getPartialDirectories (), disk.getPartials ());
        printDisketteSection ("Samples", dir.getSampleDirectories (), disk.getSamples ());
    }


    private static <T> void printCdRomSection (final String title, final List<S770DirectoryEntry> dirs, final List<T> entries, final int activeCount)
    {
        System.out.println ("=== " + title + " ===");
        for (int i = 0; i < activeCount && i < dirs.size (); i++)
        {
            System.out.println ("  [" + i + "] dir    : " + dirs.get (i));
            System.out.println ("  [" + i + "] params : " + entries.get (i));
        }
        System.out.println ();
    }


    private static <T> void printDisketteSection (final String title, final List<S770DirectoryEntry> dirs, final List<T> entries)
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