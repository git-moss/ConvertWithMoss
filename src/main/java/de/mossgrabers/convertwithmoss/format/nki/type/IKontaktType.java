// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;


/**
 * Interface to a specific Kontakt format type.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public interface IKontaktType
{
    /**
     * Parse a file which uses this format type from the given input stream.
     *
     * @param sourceFolder The top source folder for the detection
     * @param sourceFile The source file which contains the XML document
     * @param fileAccess The random access file to read from
     * @return The parsed multisample sources
     * @throws IOException
     */
    List<IMultisampleSource> parse (File sourceFolder, File sourceFile, RandomAccessFile fileAccess) throws IOException;
}
