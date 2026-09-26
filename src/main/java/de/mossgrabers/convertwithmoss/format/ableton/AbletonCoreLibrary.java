// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


/**
 * Locates samples of the Core Library which is bundled with the Live installation. Its file
 * references are relative to the library, even in a Live Set or a preset outside the library.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonCoreLibrary
{
    /** The name of Live's bundled library. */
    public static final String NAME = "Core Library";
    /** The pack identifier used by Core Library file references. */
    public static final String ID   = "www.ableton.com/0";

    private List<File>         installedLibraries;


    /**
     * Find a sample by its path within the Core Library. Prefer the library which contains the
     * source, including an installation in a custom location, before checking installed copies.
     *
     * @param sourceFile The preset, Live Set or Live Pack which references the sample
     * @param relativePath The path relative to the Core Library
     * @return The sample, empty if it could not be found
     */
    public Optional<File> findSample (final File sourceFile, final String relativePath)
    {
        if (relativePath.isBlank ())
            return Optional.empty ();

        final Path samplePath;
        try
        {
            samplePath = Path.of (relativePath.replace ('\\', '/')).normalize ();
        }
        catch (final InvalidPathException _)
        {
            return Optional.empty ();
        }
        if (samplePath.isAbsolute () || samplePath.startsWith (".."))
            return Optional.empty ();

        File folder = sourceFile.getAbsoluteFile ().getParentFile ();
        while (folder != null)
        {
            final File library = NAME.equalsIgnoreCase (folder.getName ()) ? folder : new File (folder, NAME);
            final File sample = library.toPath ().resolve (samplePath).toFile ();
            if (sample.isFile ())
                return Optional.of (sample);
            folder = folder.getParentFile ();
        }

        if (this.installedLibraries == null)
            this.installedLibraries = findInstalledLibraries ();
        for (final File library: this.installedLibraries)
        {
            final File sample = library.toPath ().resolve (samplePath).toFile ();
            if (sample.isFile ())
                return Optional.of (sample);
        }
        return Optional.empty ();
    }


    /**
     * Find the Core Libraries in the standard macOS and Windows installation locations. Only the
     * installation folders themselves are listed; the sample trees are never searched.
     *
     * @return The library folders
     */
    private static List<File> findInstalledLibraries ()
    {
        final List<File> libraries = new ArrayList<> ();
        final String os = System.getProperty ("os.name", "").toLowerCase (Locale.US);
        if (os.contains ("mac"))
        {
            addInstallations (libraries, new File ("/Applications"), "Ableton Live", "Contents/App-Resources/" + NAME);
            addInstallations (libraries, new File (System.getProperty ("user.home"), "Applications"), "Ableton Live", "Contents/App-Resources/" + NAME);
        }
        else if (os.contains ("windows"))
        {
            final String programData = System.getenv ("ProgramData");
            addInstallations (libraries, new File (programData == null ? "C:/ProgramData" : programData, "Ableton"), "Live", "Resources/" + NAME);
        }
        return libraries;
    }


    /**
     * Add the libraries from a folder which contains Live installations. Prefer release builds over
     * betas, then the most recently installed copy. The source's own library is always checked
     * before these fallbacks.
     *
     * @param libraries Where to add the library folders
     * @param parent The parent of the Live installation folders
     * @param prefix The prefix of an installation folder's name
     * @param libraryPath The path of the library within an installation
     */
    private static void addInstallations (final List<File> libraries, final File parent, final String prefix, final String libraryPath)
    {
        final File [] installations = parent.listFiles (file -> file.isDirectory () && file.getName ().startsWith (prefix));
        if (installations == null)
            return;
        Arrays.sort (installations, Comparator.comparing ((final File file) -> Boolean.valueOf (file.getName ().toLowerCase (Locale.US).contains ("beta"))).thenComparing (Comparator.comparingLong (File::lastModified).reversed ()).thenComparing (File::getName));
        for (final File installation: installations)
        {
            final File library = new File (installation, libraryPath);
            if (library.isDirectory ())
                libraries.add (library);
        }
    }
}
