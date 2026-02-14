// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.s5000;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkmPart;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkmRiffChunkId;


/**
 * Read/write a Akai S5000/S6000 AKM file.
 *
 * @author Jürgen Moßgraber
 */
public class AkmFile extends AbstractRIFFFile
{
    private static final Collection<Class<? extends RiffChunkId>> AKM_RIFF_CHUNK_IDS = new ArrayList<> ();
    static
    {
        Collections.addAll (AKM_RIFF_CHUNK_IDS, CommonRiffChunkId.class, AkmRiffChunkId.class);
    }

    private final Set<String>   ignoredChunks = new HashSet<> ();
    private final List<AkmPart> parts         = new ArrayList<> ();
    private String              version;
    private boolean             isS5000Series;


    /**
     * Constructor.
     *
     * @param akmFile The AKM file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public AkmFile (final File akmFile) throws IOException, ParseException
    {
        super (AkmRiffChunkId.AMUL_ID);

        try (final FileInputStream stream = new FileInputStream (akmFile))
        {
            this.read (stream);
        }
    }


    /**
     * Get the multi-version.
     *
     * @return The multi-version
     */
    public String getVersion ()
    {
        return this.version;
    }


    /**
     * Get the parts.
     *
     * @return The parts
     */
    public List<AkmPart> getParts ()
    {
        return this.parts;
    }


    /**
     * Check if the AKP/AKM format is for the old S-series models.
     *
     * @return True if it is the first format
     */
    public boolean isS5000Series ()
    {
        return this.isS5000Series;
    }


    /**
     * Read and parse an AKM file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    private void read (final InputStream inputStream) throws IOException, ParseException
    {
        final RIFFParser riffParser = new RIFFParser (AKM_RIFF_CHUNK_IDS, AkmRiffChunkId.AMUL_ID);
        riffParser.declareGroupChunk (AkmRiffChunkId.AMUL_ID.getFourCC (), CommonRiffChunkId.RIFF_ID);
        riffParser.parse (inputStream, this);
        this.isS5000Series = riffParser.hasEmptyTopSize ();
    }


    /** {@inheritDoc} */
    @Override
    public void enterGroup (final RawRIFFChunk group) throws ParseException
    {
        // Not used
    }


    /** {@inheritDoc} */
    @Override
    public void visitChunk (final RawRIFFChunk group, final RawRIFFChunk chunk) throws ParseException
    {
        final int id = chunk.getId ().getFourCC ();

        if (id == AkmRiffChunkId.FX_ID.getFourCC ())
            // Not used
            return;

        if (id == AkmRiffChunkId.PART_ID.getFourCC ())
        {
            final AkmPart part = new AkmPart ();
            part.read (chunk);
            if (!part.getPresetName ().isBlank ())
                this.parts.add (part);
            return;
        }

        if (id == AkmRiffChunkId.VERSION_ID.getFourCC ())
        {
            final byte [] data = chunk.getData ();
            this.version = String.format ("%d.%d.%d.%d", Byte.valueOf (data[0]), Byte.valueOf (data[1]), Byte.valueOf (data[2]), Byte.valueOf (data[3]));
            return;
        }

        // Ignore other chunks
        this.ignoredChunks.add (RiffChunkId.toASCII (chunk.getId ().getFourCC ()));
    }


    /** {@inheritDoc} */
    @Override
    protected void fillChunkStack ()
    {
        if (!this.chunkStack.isEmpty ())
            return;

        // Not used since writing is not supported
    }
}
