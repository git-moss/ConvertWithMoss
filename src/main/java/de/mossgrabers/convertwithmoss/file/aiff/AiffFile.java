// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.iff.IffChunk;
import de.mossgrabers.convertwithmoss.file.iff.IffFile;


/**
 * Audio Interchange File Format AIFF - A Standard for Sampled Sound Files by Apple Computer, Inc.
 * AIFF (= Audio IFF) is based on the 'EA IFF 85' Standard for Interchange Format files.
 *
 * @author Jürgen Moßgraber
 */
public class AiffFile
{
    private static final String      FORM_TYPE_AIFF             = "AIFF";
    private static final String      FORM_TYPE_AIFC             = "AIFC";

    // Specified AIFF chunk IDs
    private static final String      AIFF_CHUNK_COMMON          = "COMM";
    private static final String      AIFF_CHUNK_INSTRUMENT      = "INST";
    private static final String      AIFF_CHUNK_MARKER          = "MARK";
    private static final String      AIFF_CHUNK_SOUND_DATA      = "SSND";
    private static final String      AIFF_CHUNK_COMMENT         = "COMT";
    private static final String      AIFF_CHUNK_NAME            = "NAME";
    private static final String      AIFF_CHUNK_AUTHOR          = "AUTH";
    private static final String      AIFF_CHUNK_COPYRIGHT       = "(c) ";
    private static final String      AIFF_CHUNK_ANNOTATION      = "ANNO";
    private static final String      AIFF_CHUNK_APPLICATION     = "APPL";
    private static final String      AIFF_CHUNK_AUDIO_RECORDING = "AESD";
    private static final String      AIFF_CHUNK_AUDIO_MIDI      = "MIDI";

    // AIFC additions
    private static final String      AIFC_CHUNK_FORMAT_VERSION  = "FVER";

    private static final Set<String> AIFF_TYPES                 = new HashSet<> (2);
    static
    {
        AIFF_TYPES.add (FORM_TYPE_AIFF);
        AIFF_TYPES.add (FORM_TYPE_AIFC);
    }

    private boolean                   isCompressed = false;
    private Optional<AiffCommonChunk> commonChunk  = Optional.empty ();
    private Map<String, String>       metadata     = new HashMap<> ();


    /**
     * Constructor. Reads the given AIFF file.
     *
     * @param aiffFile The AIFF file
     * @throws IOException Could not read the file
     */
    public AiffFile (final File aiffFile) throws IOException
    {
        try (final FileInputStream stream = new FileInputStream (aiffFile))
        {
            this.read (stream);
        }
    }


    /**
     * Constructor. Use in combination with the read-method to read an AIFF file from a stream.
     */
    public AiffFile ()
    {
        // Intentionally empty
    }


    /**
     * Is the audio data compressed?
     * 
     * @return True if compressed
     */
    public boolean isCompressed ()
    {
        return this.isCompressed;
    }


    /**
     * Get the common chunk.
     * 
     * @return The common chunk if present
     */
    public Optional<AiffCommonChunk> getCommonChunk ()
    {
        return this.commonChunk;
    }


    /**
     * Reads an AIFF file from a stream.
     *
     * @param inputStream The input stream which provides the AIFF file
     * @throws IOException Could not read the file
     */
    public void read (final InputStream inputStream) throws IOException
    {
        final IffChunk chunk = IffFile.readChunk (inputStream);
        final String chunkID = chunk.getID ();
        if (!AIFF_TYPES.contains (chunkID))
            throw new IOException ("Not an AIFF or AIFC file.");
        this.isCompressed = FORM_TYPE_AIFC.equals (chunkID);

        final List<IffChunk> localChunks = new ArrayList<> ();
        try (final InputStream formChunkStream = chunk.streamData ())
        {
            while (formChunkStream.available () > 0)
                localChunks.add (IffFile.readChunk (formChunkStream));
        }

        this.readLocalChunks (localChunks);
    }


    private void readLocalChunks (final List<IffChunk> localChunks) throws IOException
    {
        for (final IffChunk chunk: localChunks)
        {
            final String chunkID = chunk.getID ();
            switch (chunkID)
            {
                case AIFF_CHUNK_COMMON:
                    final AiffCommonChunk commonChunk = new AiffCommonChunk ();
                    commonChunk.read (chunk);
                    this.commonChunk = Optional.of (commonChunk);
                    break;

                case AIFF_CHUNK_NAME:
                case AIFF_CHUNK_AUTHOR:
                case AIFF_CHUNK_COPYRIGHT:
                case AIFF_CHUNK_ANNOTATION:
                    try (final InputStream in = chunk.streamData ())
                    {
                        final long textLength = StreamUtils.readUnsigned32 (in, true);
                        final String text = StreamUtils.readASCII (in, (int) textLength).trim ();
                        if (!text.isBlank ())
                            this.metadata.put (chunkID, text);
                    }
                    break;

                case AIFF_CHUNK_INSTRUMENT:
                case AIFF_CHUNK_MARKER:
                case AIFF_CHUNK_SOUND_DATA:
                case AIFF_CHUNK_COMMENT:
                case AIFF_CHUNK_APPLICATION:
                case AIFF_CHUNK_AUDIO_RECORDING:
                case AIFF_CHUNK_AUDIO_MIDI:
                case AIFC_CHUNK_FORMAT_VERSION:
                    // Currently not used
                    break;

                default:
                    // Ignore unknown (or currently unused) chunks
                    break;
            }
        }
    }

}
