// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;


/**
 * The description of one source which was found during a contents detection run. It only holds the
 * information to display and to identify the source, not the source itself, since e.g. all samples
 * of a disk image would need to be kept in memory otherwise.
 *
 * @author Jürgen Moßgraber
 */
public class ContentsEntry
{
    private static final String [] NOTE_NAMES = new String []
    {
        "C",
        "C#",
        "D",
        "D#",
        "E",
        "F",
        "F#",
        "G",
        "G#",
        "A",
        "A#",
        "B"
    };

    private final int              indexInFile;
    private final String           name;
    private final File             sourceFile;
    private final List<String>     folderPath;
    private final List<String>     containerPath;
    private final int              numberOfZones;
    private final int              lowestKey;
    private final int              highestKey;
    private final String           category;


    /**
     * Constructor.
     *
     * @param indexInFile The index of the source inside of its file, which identifies it
     * @param source The detected source
     * @param sourceFolder The folder which was searched, the reference for the folder path
     */
    public ContentsEntry (final int indexInFile, final IMultisampleSource source, final File sourceFolder)
    {
        this (indexInFile, source.getName (), source, sourceFolder);
    }


    /**
     * Constructor.
     *
     * @param indexInFile The index of the source inside of its file, which identifies it
     * @param name The name to display, e.g. the name of a performance
     * @param source The detected source from which to read the information to display
     * @param sourceFolder The folder which was searched, the reference for the folder path
     */
    public ContentsEntry (final int indexInFile, final String name, final IMultisampleSource source, final File sourceFolder)
    {
        this.indexInFile = indexInFile;
        this.name = name;
        this.sourceFile = source.getSourceFile ();
        this.category = source.getMetadata ().getCategory ();
        this.folderPath = createFolderPath (this.sourceFile, sourceFolder);
        this.containerPath = createContainerPath (source.getSubPath (), this.folderPath);

        int zoneCount = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (final IGroup group: source.getGroups ())
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zoneCount++;
                lowest = Math.min (lowest, zone.getKeyLow ());
                highest = Math.max (highest, zone.getKeyHigh ());
            }
        this.numberOfZones = zoneCount;
        this.lowestKey = zoneCount == 0 ? -1 : lowest;
        this.highestKey = zoneCount == 0 ? -1 : highest;
    }


    /**
     * Get the index of the source inside of its file. Since the detection of a file is
     * deterministic, the same index addresses the same source in a following conversion run. The
     * index is counted per file and not across the whole detection run, so that files which contain
     * no selected source can be skipped entirely without shifting the indices.
     *
     * @return The index inside of the file
     */
    public int getIndexInFile ()
    {
        return this.indexInFile;
    }


    /**
     * Get the name of the source, e.g. the name of a preset in a bank.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the file which contains the source.
     *
     * @return The file
     */
    public File getSourceFile ()
    {
        return this.sourceFile;
    }


    /**
     * Get the folders between the searched source folder and the file of the source, e.g. a preset
     * in '&lt;source&gt;/Bass/Deep.tvpst' is located in the folder 'Bass'. Ordered from the
     * outermost to the innermost folder.
     *
     * @return The folder names, empty if the file is located directly in the source folder
     */
    public List<String> getFolderPath ()
    {
        return this.folderPath;
    }


    /**
     * Get the path of the containers inside of the file in which the source is located, e.g. the
     * name of the bank of a preset inside of a disk image. Ordered from the outermost to the
     * innermost container.
     *
     * @return The container names, empty if the file itself is the only container
     */
    public List<String> getContainerPath ()
    {
        return this.containerPath;
    }


    /**
     * Get the number of sample zones.
     *
     * @return The number of zones
     */
    public int getNumberOfZones ()
    {
        return this.numberOfZones;
    }


    /**
     * Get the category of the source.
     *
     * @return The category, might be blank
     */
    public String getCategory ()
    {
        return this.category;
    }


    /**
     * Format the information about the source, which is displayed after its name.
     *
     * @return The formatted information
     */
    public String getInfo ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append (this.numberOfZones).append (this.numberOfZones == 1 ? " zone" : " zones");
        if (this.lowestKey >= 0)
            sb.append (", ").append (formatNote (this.lowestKey)).append ('-').append (formatNote (this.highestKey));
        if (!this.category.isBlank ())
            sb.append (", ").append (this.category);
        return sb.toString ();
    }


    /**
     * Format a MIDI note number as a note name, e.g. 60 becomes 'C3'.
     *
     * @param note The MIDI note number
     * @return The formatted note
     */
    private static String formatNote (final int note)
    {
        return NOTE_NAMES[note % 12] + (note / 12 - 2);
    }


    /**
     * Get the folders in which the file of the source is located, relative to the folder which was
     * searched. The names are collected by walking up from the file until the source folder is
     * reached and are therefore reversed.
     *
     * @param sourceFile The file which contains the source
     * @param sourceFolder The folder which was searched
     * @return The folder names, empty if the file is located directly in the source folder or is
     *         not located below it at all
     */
    private static List<String> createFolderPath (final File sourceFile, final File sourceFolder)
    {
        if (sourceFile == null || sourceFolder == null)
            return new ArrayList<> ();

        final File searchedFolder = sourceFolder.getAbsoluteFile ();
        final List<String> folders = new ArrayList<> ();
        File folder = sourceFile.getAbsoluteFile ().getParentFile ();
        while (folder != null && !folder.equals (searchedFolder))
        {
            folders.add (folder.getName ());
            folder = folder.getParentFile ();
        }
        // The searched folder is not part of the path of the file, e.g. because a sample of a
        // library is located somewhere else, therefore there is no folder path to display
        if (folder == null)
            return new ArrayList<> ();
        Collections.reverse (folders);
        return folders;
    }


    /**
     * Get the names of the containers of the source. The sub-path additionally contains the name of
     * the source at the first position and the source folder at the last, which are both removed.
     * The remaining parts are ordered from the innermost to the outermost container and therefore
     * reversed. They start with the folders of the file, which are already known from the folder
     * path and are removed as well, so that only the containers inside of the file are left.
     *
     * @param subPath The sub-path of the source
     * @param folderPath The folders in which the file of the source is located
     * @return The container names
     */
    private static List<String> createContainerPath (final String [] subPath, final List<String> folderPath)
    {
        if (subPath == null || subPath.length < 3)
            return new ArrayList<> ();
        final List<String> containers = new ArrayList<> (Arrays.asList (subPath).subList (1, subPath.length - 1));
        Collections.reverse (containers);
        final int folderCount = folderPath.size ();
        if (folderCount > 0 && containers.size () >= folderCount && containers.subList (0, folderCount).equals (folderPath))
            return new ArrayList<> (containers.subList (folderCount, containers.size ()));
        return containers;
    }
}
