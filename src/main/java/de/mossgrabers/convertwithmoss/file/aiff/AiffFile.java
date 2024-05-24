// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.aiff;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    private static final String      AIFF_CHUNK_APPLICATION     = "APPL";
    private static final String      AIFF_CHUNK_AUDIO_RECORDING = "AESD";
    private static final String      AIFF_CHUNK_AUDIO_MIDI      = "MIDI";
    /** The name metadata chunk. */
    public static final String       AIFF_CHUNK_NAME            = "NAME";
    /** The author metadata chunk. */
    public static final String       AIFF_CHUNK_AUTHOR          = "AUTH";
    /** The copyright metadata chunk. */
    public static final String       AIFF_CHUNK_COPYRIGHT       = "(c) ";
    /** The annotation metadata chunk. */
    public static final String       AIFF_CHUNK_ANNOTATION      = "ANNO";

    // AIFC additions
    private static final String      AIFC_CHUNK_FORMAT_VERSION  = "FVER";

    private static final Set<String> AIFF_TYPES                 = new HashSet<> (2);
    static
    {
        AIFF_TYPES.add (FORM_TYPE_AIFF);
        AIFF_TYPES.add (FORM_TYPE_AIFC);
    }

    private boolean               isCompressed      = false;
    private AiffCommonChunk       commonChunk       = null;
    private AiffSoundDataChunk    soundDataChunk    = null;
    private AiffMarkerChunk       markerChunk       = null;
    private AiffInstrumentChunk   instrumentChunk   = null;
    private final List<IffChunk>        unprocessedChunks = new ArrayList<> ();
    private final List<AiffChunk> chunkStack        = new ArrayList<> ();
    private final Map<String, String>   metadata          = new TreeMap<> ();


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
     * Get the metadata.
     *
     * @return The metadata
     */
    public Map<String, String> getMetadata ()
    {
        return this.metadata;
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
    public AiffCommonChunk getCommonChunk ()
    {
        return this.commonChunk;
    }


    /**
     * Get the marker chunk.
     *
     * @return The marker chunk if present
     */
    public AiffMarkerChunk getMarkerChunk ()
    {
        return this.markerChunk;
    }


    /**
     * Get the instrument chunk.
     *
     * @return The instrument chunk if present
     */
    public AiffInstrumentChunk getInstrumentChunk ()
    {
        return this.instrumentChunk;
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
        final String chunkID = chunk.getId ();
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
            final String chunkID = chunk.getId ();
            switch (chunkID)
            {
                case AIFF_CHUNK_COMMON:
                    this.commonChunk = new AiffCommonChunk (chunk);
                    this.commonChunk.read (chunk);
                    break;

                case AIFF_CHUNK_NAME:
                case AIFF_CHUNK_AUTHOR:
                case AIFF_CHUNK_COPYRIGHT:
                case AIFF_CHUNK_ANNOTATION:
                    final String text = new String (chunk.getData (), StandardCharsets.US_ASCII).trim ();
                    if (!text.isBlank ())
                        this.metadata.put (chunkID, text);
                    break;

                case AIFF_CHUNK_SOUND_DATA:
                    this.soundDataChunk = new AiffSoundDataChunk (chunk);
                    this.soundDataChunk.read (chunk, this.commonChunk == null ? -1 : this.commonChunk.sampleSize);
                    break;

                case AIFF_CHUNK_COMMENT:
                    try (InputStream in = chunk.streamData ())
                    {
                        final int numComments = StreamUtils.readUnsigned16 (in, true);
                        for (int i = 0; i < numComments; i++)
                        {
                            // Ignore: unsigned long timeStamp
                            in.skipNBytes (4);
                            // Ignore: MarkerID marker
                            in.skipNBytes (2);
                            final int length = StreamUtils.readUnsigned16 (in, true);
                            final String comment = StreamUtils.readASCII (in, length).trim ();
                            if (comment.length () % 2 == 1)
                                in.skipNBytes (1);
                            if (!comment.isBlank ())
                                this.metadata.put ("Comment" + (i + 1), comment);
                        }
                    }
                    break;

                case AIFF_CHUNK_MARKER:
                    this.markerChunk = new AiffMarkerChunk (chunk);
                    this.markerChunk.read (chunk);
                    break;

                case AIFF_CHUNK_INSTRUMENT:
                    this.instrumentChunk = new AiffInstrumentChunk (chunk);
                    this.instrumentChunk.read (chunk);
                    break;

                case AIFF_CHUNK_APPLICATION:
                case AIFF_CHUNK_AUDIO_RECORDING:
                case AIFF_CHUNK_AUDIO_MIDI:
                case AIFC_CHUNK_FORMAT_VERSION:
                default:
                    // Ignore unknown (or currently unused) chunks
                    this.unprocessedChunks.add (chunk);
                    break;
            }
        }
    }


    /**
     * Check if the chunk stack is already filled from reading the WAV file. Fill it if empty.
     */
    private void fillChunkStack ()
    {
        if (!this.chunkStack.isEmpty ())
            return;

        if (this.commonChunk != null)
            this.chunkStack.add (this.commonChunk);
        if (this.soundDataChunk != null)
            this.chunkStack.add (this.soundDataChunk);
        if (this.markerChunk != null)
            this.chunkStack.add (this.markerChunk);
        if (this.instrumentChunk != null)
            this.chunkStack.add (this.instrumentChunk);
    }


    /**
     * Format all values as a string for dumping it out.
     *
     * @return The formatted string
     */
    public String infoText ()
    {
        this.fillChunkStack ();

        final StringBuilder sb = new StringBuilder ();
        for (final AiffChunk chunk: this.chunkStack)
        {
            sb.append ("* ").append (chunk.getName ()).append (" ('").append (chunk.getId ()).append ("')\n");
            sb.append ("    " + chunk.infoText ().replace ("\n", "\n    ")).append ('\n');
        }

        if (!this.metadata.isEmpty ())
        {
            sb.append ("* Metadata chunks:\n");
            for (final Map.Entry<String, String> entry: this.metadata.entrySet ())
                sb.append ("    * ").append (entry.getKey ()).append (": ").append (entry.getValue ()).append ("\n");
        }

        if (!this.unprocessedChunks.isEmpty ())
        {
            sb.append ("* Unprocessed chunks:\n");
            for (final IffChunk iffChunk: this.unprocessedChunks)
                sb.append ("    * ").append (iffChunk.getId ()).append ("\n");
        }
        return sb.toString ();
    }
}
