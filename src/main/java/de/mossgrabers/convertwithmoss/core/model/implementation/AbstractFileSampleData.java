// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.core.model.implementation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.model.IFileBasedSampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Sample data contained in a file.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractFileSampleData extends AbstractSampleData implements IFileBasedSampleData
{
    protected String           filename;
    protected File             sampleFile;
    protected final File       zipFile;
    protected final File       zipEntryFile;
    protected Optional<String> filenameWithoutLayer = Optional.empty ();


    /**
     * Constructor for a sample stored in the file system.
     *
     * @param sampleFile The file where the sample is stored
     */
    protected AbstractFileSampleData (final File sampleFile)
    {
        this (sampleFile.getName (), sampleFile, null, null);
    }


    /**
     * Constructor for a sample stored in the file system.
     *
     * @param filename The name of the file where the sample is stored (must not contain any paths!)
     * @param sampleFile The file where the sample is stored
     */
    protected AbstractFileSampleData (final String filename, final File sampleFile)
    {
        this (filename, sampleFile, null, null);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    protected AbstractFileSampleData (final File zipFile, final File zipEntry)
    {
        this (zipEntry.getName (), null, zipFile, zipEntry);
    }


    /**
     * Constructor.
     */
    protected AbstractFileSampleData ()
    {
        this (null, null, null, null);
    }


    /**
     * Constructor.
     *
     * @param filename The name of the file where the sample is stored (must not contain any paths!)
     * @param sampleFile The file where the sample is stored
     * @param zipFile The ZIP file which contains the WAV files
     * @param zipEntry The relative path in the ZIP where the file is stored
     */
    protected AbstractFileSampleData (final String filename, final File sampleFile, final File zipFile, final File zipEntry)
    {
        this.filename = filename;
        this.sampleFile = sampleFile;
        this.zipFile = zipFile;
        this.zipEntryFile = zipEntry;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        if (this.sampleFile != null)
        {
            Files.copy (this.sampleFile.toPath (), outputStream);
            return;
        }

        if (this.zipFile == null)
            return;

        try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
        {
            in.transferTo (outputStream);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        if (this.sampleFile != null)
        {
            this.audioMetadata = AudioFileUtils.getMetadata (this.sampleFile);
            return;
        }

        try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
        {
            this.audioMetadata = AudioFileUtils.getMetadata (in);
        }
    }


    /** {@inheritDoc} */
    @Override
    public String getFilename ()
    {
        return this.filename;
    }


    /**
     * Get the entry in the ZIP file.
     *
     * @param zf The ZIP file object
     * @return The entry
     * @throws FileNotFoundException If the entry could not be found in the ZIP
     */
    protected ZipEntry getHarmonizedZipEntry (final ZipFile zf) throws FileNotFoundException
    {
        String path = this.zipEntryFile.getPath ().replace ('\\', '/');
        // Folders in the ZIP are always relative!
        if (path.startsWith ("/"))
            path = path.substring (1);
        final ZipEntry zipEntry = zf.getEntry (path);
        if (zipEntry == null)
            throw new FileNotFoundException (Functions.getMessage ("IDS_NOTIFY_ERR_FILE_NOT_FOUND_IN_ZIP", path));
        return zipEntry;
    }
}
