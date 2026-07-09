// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;


/**
 * The data of an OGG sample file.
 *
 * @author Jürgen Moßgraber
 */
public class OggFileSampleData extends AbstractFileSampleData
{
    private static final int MIN_PAGE_HEADER_SIZE = 27;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public OggFileSampleData (final File file) throws IOException
    {
        super (file);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        // Decode with the direct decoder which emits all sample frames. The Java Sound path drops
        // the final block of every file (about 10ms), which breaks sample loops that end at the
        // end of the file. It is kept as a fallback in case a file cannot be parsed directly.
        try
        {
            OggVorbisDecoder.decodeToWav (this.sampleFile, outputStream);
        }
        catch (final IOException ex)
        {
            // Nothing has been written to the output stream in this case (see decodeToWav)
            AudioFileUtils.decompressToWav (this.sampleFile, outputStream);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        super.createAudioMetadata ();

        // The Vorbis audio provider reports neither the number of sample frames nor the bit
        // resolution (both are -1) since Vorbis does not store them in its header. Creators which
        // e.g. normalize loop positions by the number of frames would then write corrupt values.
        final int numberOfSamples = this.audioMetadata.getNumberOfSamples ();
        final int bitResolution = this.audioMetadata.getBitResolution ();
        if (numberOfSamples >= 0 && bitResolution >= 0)
            return;

        int numberOfFrames = numberOfSamples;
        if (numberOfFrames < 0)
            numberOfFrames = readNumberOfSampleFrames (this.sampleFile);
        if (numberOfFrames < 0)
        {
            // Fall back to fully decoding the file, which is exactly the data written later anyway
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream ();
            this.writeSample (outputStream);
            this.audioMetadata = AudioFileUtils.getMetadata (new ByteArrayInputStream (outputStream.toByteArray ()));
            return;
        }

        // The audio data is decoded to 16-bit when it is written (see writeSample)
        this.audioMetadata = new DefaultAudioMetadata (this.audioMetadata.getChannels (), this.audioMetadata.getSampleRate (), bitResolution < 0 ? 16 : bitResolution, numberOfFrames);
    }


    /**
     * Reads the number of sample frames from the granule position of the last Ogg page. For a
     * Vorbis stream the granule position of the last page is the total number of PCM sample frames
     * of the stream.
     *
     * @param file The OGG file
     * @return The number of sample frames or -1 if it could not be determined
     * @throws IOException Could not read the file
     */
    private static int readNumberOfSampleFrames (final File file) throws IOException
    {
        final long fileLength = file.length ();
        // An Ogg page is at most 65307 bytes, therefore the header of the last page starts within
        // the final 64KB of the file
        final int tailLength = (int) Math.min (fileLength, 65536);
        final byte [] tail = new byte [tailLength];
        try (final RandomAccessFile inputFile = new RandomAccessFile (file, "r"))
        {
            inputFile.seek (fileLength - tailLength);
            inputFile.readFully (tail);
        }

        // Search backwards for the header of the last page: capture pattern 'OggS' with version 0.
        // The page must end exactly at the end of the file which rules out matches inside of
        // compressed payload data.
        for (int i = tailLength - MIN_PAGE_HEADER_SIZE; i >= 0; i--)
        {
            if (tail[i] != 'O' || tail[i + 1] != 'g' || tail[i + 2] != 'g' || tail[i + 3] != 'S' || tail[i + 4] != 0)
                continue;
            final int numberOfSegments = tail[i + 26] & 0xFF;
            if (i + MIN_PAGE_HEADER_SIZE + numberOfSegments > tailLength)
                continue;
            int payloadSize = 0;
            for (int segment = 0; segment < numberOfSegments; segment++)
                payloadSize += tail[i + MIN_PAGE_HEADER_SIZE + segment] & 0xFF;
            if (i + MIN_PAGE_HEADER_SIZE + numberOfSegments + payloadSize != tailLength)
                continue;

            // The granule position is a signed 64-bit value; -1 marks a page without a finished
            // packet
            long granulePosition = 0;
            for (int b = 7; b >= 0; b--)
                granulePosition = granulePosition << 8 | tail[i + 6 + b] & 0xFF;
            if (granulePosition >= 0)
                return (int) Math.min (granulePosition, Integer.MAX_VALUE);
        }
        return -1;
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // No info available in OGG
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        // Could be implemented with e.g. JAudioTagger
    }
}
