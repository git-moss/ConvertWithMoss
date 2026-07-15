// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synclavier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;


/**
 * The data of a Synclavier Regen SFLC sample file. An SFLC file is a FLAC file whose bytes are
 * obfuscated with a key-stream derived from the file's base name (see {@link SynclavierRegenCodec}).
 * The obfuscation is removed and the resulting FLAC stream is then decoded like a normal FLAC file.
 *
 * @author Jürgen Moßgraber
 */
public class SynclavierRegenSampleData extends AbstractFileSampleData
{
    private byte [] flacData = null;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public SynclavierRegenSampleData (final File file) throws IOException
    {
        super (file);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the SFLC files
     * @param zipEntry The relative path in the ZIP where the file is stored
     * @throws IOException Could not read the file
     */
    public SynclavierRegenSampleData (final File zipFile, final File zipEntry) throws IOException
    {
        super (zipFile, zipEntry);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        AudioFileUtils.decompressToWav (new ByteArrayInputStream (this.getDecodedFlac ()), outputStream);
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        this.audioMetadata = AudioFileUtils.getMetadata (new ByteArrayInputStream (this.getDecodedFlac ()));
    }


    /**
     * De-obfuscates the SFLC file into a plain FLAC stream. The result is cached.
     *
     * @return The FLAC data
     * @throws IOException Could not read the file
     */
    private byte [] getDecodedFlac () throws IOException
    {
        if (this.flacData == null)
            this.flacData = SynclavierRegenCodec.transform (this.readRawData (), SynclavierRegenCodec.baseName (this.filename));
        return this.flacData;
    }


    /**
     * Reads the raw (still obfuscated) content of the sample file.
     *
     * @return The raw bytes
     * @throws IOException Could not read the file
     */
    private byte [] readRawData () throws IOException
    {
        if (this.sampleFile != null)
            return Files.readAllBytes (this.sampleFile.toPath ());

        try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
        {
            return in.readAllBytes ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // No info available in FLAC
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        // No meta data available
    }
}
