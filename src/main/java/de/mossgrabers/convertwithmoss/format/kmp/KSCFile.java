// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.kmp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Access to a Korg Sample Collection file (KSC) file.
 *
 * @author Jürgen Moßgraber
 */
public class KSCFile
{
    private static final String KSC_HEADER    = "#KORG Script Version 1.0\r\n";
    private static final String KMP_EXTENSION = ".KMP";

    private final List<String>  kmpFiles      = new ArrayList<> ();


    /**
     * Default constructor.
     */
    public KSCFile ()
    {
        // Intentionally empty
    }


    /**
     * Constructor.
     * 
     * @param kmpFileNames The list with all KMP file names to add to the list
     */
    public KSCFile (final List<String> kmpFileNames)
    {
        for (final String kmpFileName: kmpFileNames)
        {
            if (kmpFileName.endsWith (KMP_EXTENSION))
                this.kmpFiles.add (kmpFileName);
            else
                this.kmpFiles.add (kmpFileName + KMP_EXTENSION);
        }
    }


    /**
     * Get all KMP files.
     * 
     * @return The KMP files
     */
    public List<String> getKmpFiles ()
    {
        return this.kmpFiles;
    }


    /**
     * Read a KSC file.
     * 
     * @param kscFile The file to read from
     * @throws IOException Could not read the KSC file
     */
    public void read (final File kscFile) throws IOException
    {
        final String content = Files.readString (kscFile.toPath ());
        this.kmpFiles.clear ();
        this.kmpFiles.addAll (content.lines ().map (String::trim).filter (line -> !line.isEmpty () && !line.startsWith ("#") && line.endsWith (KMP_EXTENSION)).collect (Collectors.toList ()));
    }


    /**
     * Write a KSC file.
     * 
     * @param kscFile The file to write to
     * @throws IOException Could not write the KSC file
     */
    public void write (final File kscFile) throws IOException
    {
        final StringBuilder sb = new StringBuilder (KSC_HEADER);
        for (final String kmpFilename: this.kmpFiles)
            sb.append (kmpFilename).append ("\r\n");
        Files.writeString (kscFile.toPath (), sb.toString ());
    }
}
