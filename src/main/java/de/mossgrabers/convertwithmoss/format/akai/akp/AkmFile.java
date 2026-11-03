// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.IRiffChunk;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AbstractAkpChunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmPart;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmRiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmZ48Chunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkmZ48Part;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpGeneration;


/**
 * Read/write a Akai S5000/S6000/Z4/Z8 AKM file. The format is described in
 * documentation/design/AKAI_AKP_FORMAT.md. The effects settings are not decoded, new files get the
 * effects settings of a factory multi.
 *
 * @author Jürgen Moßgraber
 */
public class AkmFile extends AbstractRIFFFile
{
    /** The number of parts of a multi. */
    public static final int                                       NUMBER_OF_PARTS      = AkmZ48Chunk.NUMBER_OF_PARTS;

    private static final Collection<Class<? extends RiffChunkId>> AKM_RIFF_CHUNK_IDS   = new ArrayList<> ();
    static
    {
        Collections.addAll (AKM_RIFF_CHUNK_IDS, CommonRiffChunkId.class, AkmRiffChunkId.class);
    }

    /** The effects chunk of the factory multi GM PERC KIT1 of the S6000. */
    private static final String                                   DEFAULT_EFFECTS_FILE = "AkmEffects.bin";
    /** The content of the multi version chunk, found in all analysed files. */
    private static final byte []                                  DEFAULT_VERSION      =
    {
        1,
        0x20,
        0,
        0
    };

    private final Set<String>                                     ignoredChunks        = new HashSet<> ();
    private final List<AkmPart>                                   parts                = new ArrayList<> ();
    private Optional<AkmZ48Chunk>                                 z48Chunk             = Optional.empty ();
    private RawRIFFChunk                                          effectsChunk;
    private RawRIFFChunk                                          versionChunk;
    private final List<RawRIFFChunk>                              otherChunks          = new ArrayList<> ();
    private final List<IRiffChunk>                                readChunks           = new ArrayList<> ();
    private String                                                version              = "1.32.0.0";
    private boolean                                               isS5000Series        = true;
    private boolean                                               hasEmptyTopSize      = false;
    private Optional<AkpGeneration>                               writeGeneration      = Optional.empty ();


    /**
     * Constructor.
     *
     * @param akmFile The AKM file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public AkmFile (final File akmFile) throws IOException, ParseException
    {
        super (AkmRiffChunkId.AMUL_ID, false);

        try (final FileInputStream stream = new FileInputStream (akmFile))
        {
            this.read (stream);
        }
    }


    /**
     * Default constructor. Creates a multi with 32 unused parts.
     */
    public AkmFile ()
    {
        super (AkmRiffChunkId.AMUL_ID, false);

        this.effectsChunk = createChunk (AkmRiffChunkId.FX_ID, loadTemplate (DEFAULT_EFFECTS_FILE));
        this.versionChunk = createChunk (AkmRiffChunkId.VERSION_ID, DEFAULT_VERSION.clone ());
        for (int i = 0; i < NUMBER_OF_PARTS; i++)
        {
            final AkmPart part = new AkmPart ();
            part.setMidiChannel (i);
            this.parts.add (part);
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
     * Get all S5000/S6000 parts. Use AkmPart.isUsed to check if a preset is assigned.
     *
     * @return The parts, normally 32
     */
    public List<AkmPart> getParts ()
    {
        return this.parts;
    }


    /**
     * Get the Z4/Z8 chunk, which contains the multi in the Z4/Z8 format.
     *
     * @return The chunk if present, only in files written by a Z4/Z8
     */
    public Optional<AkmZ48Chunk> getZ48Chunk ()
    {
        return this.z48Chunk;
    }


    /**
     * Check if the AKM file was written by a S5000/S6000. Files written by a Z4/Z8 contain an
     * additional 'z48 ' chunk. Note that the size of the RIFF chunk cannot be used for the
     * detection: it is 0 in files written by the S5000/S6000 but also set by some PC software.
     *
     * @return True if written by a S5000/S6000, false if written by a Z4/Z8
     */
    public boolean isS5000Series ()
    {
        return this.isS5000Series;
    }


    /**
     * Write the AKM file in the given generation.
     *
     * @param file The file to write to
     * @param generation The generation of the chunks to write
     * @throws IOException Could not write the file
     */
    public void write (final File file, final AkpGeneration generation) throws IOException
    {
        try (final FileOutputStream out = new FileOutputStream (file))
        {
            this.write (out, generation);
        }
    }


    /**
     * Write the AKM file in the given generation. Since no AKM files of generation 1 and 2 are
     * known, the chunks are written in the same versions in all generations. Generation 3 is
     * written in the format of the Z4/Z8 which includes the 'z48 ' chunk. If the file does not
     * contain a 'z48 ' chunk, it is created from the S5000/S6000 parts. Generations 1 and 2 are
     * written in the format of the S5000/S6000 without the 'z48 ' chunk. Use the write methods
     * without the generation parameter to keep the structure of a read file, new files are written
     * in the default generation (3).
     *
     * @param out The output stream to write to
     * @param generation The generation of the chunks to write
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream out, final AkpGeneration generation) throws IOException
    {
        this.writeGeneration = Optional.of (generation);
        try
        {
            this.write (out);
        }
        finally
        {
            this.writeGeneration = Optional.empty ();
        }
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
        this.hasEmptyTopSize = riffParser.hasEmptyTopSize ();

        if (this.effectsChunk == null)
            this.effectsChunk = createChunk (AkmRiffChunkId.FX_ID, loadTemplate (DEFAULT_EFFECTS_FILE));
        if (this.versionChunk == null)
            this.versionChunk = createChunk (AkmRiffChunkId.VERSION_ID, DEFAULT_VERSION.clone ());
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

        // The chunk order differs between the S5000/S6000 and the Z4/Z8, therefore all chunks are
        // kept in the read order for writing
        if (id == AkmRiffChunkId.Z48_ID.getFourCC ())
        {
            // Only present in files written by a Z4/Z8. The Z4/Z8 writes its parameters into the
            // S5000/S6000 parts as well, except the effects settings and the priority
            this.isS5000Series = false;
            final AkmZ48Chunk z48 = new AkmZ48Chunk ();
            z48.read (chunk);
            this.z48Chunk = Optional.of (z48);
            this.readChunks.add (z48);
            return;
        }

        if (id == AkmRiffChunkId.PART_ID.getFourCC ())
        {
            final AkmPart part = new AkmPart ();
            part.read (chunk);
            this.parts.add (part);
            this.readChunks.add (part);
            return;
        }

        this.readChunks.add (chunk);

        if (id == AkmRiffChunkId.FX_ID.getFourCC ())
        {
            this.effectsChunk = chunk;
            return;
        }

        if (id == AkmRiffChunkId.VERSION_ID.getFourCC ())
        {
            this.versionChunk = chunk;
            final byte [] data = chunk.getData ();
            this.version = String.format ("%d.%d.%d.%d", Byte.valueOf (data[0]), Byte.valueOf (data[1]), Byte.valueOf (data[2]), Byte.valueOf (data[3]));
            return;
        }

        // Ignore other chunks (e.g. 'mpc ') but keep them for writing
        this.ignoredChunks.add (RiffChunkId.toASCII (id));
        this.otherChunks.add (chunk);
    }


    /** {@inheritDoc} */
    @Override
    protected void fillChunkStack ()
    {
        this.chunkStack.clear ();
        for (final AkmPart part: this.parts)
            part.updateData (this.writeGeneration);

        // Keep the structure of a read file
        if (this.writeGeneration.isEmpty () && !this.readChunks.isEmpty ())
        {
            for (final IRiffChunk chunk: this.readChunks)
            {
                if (chunk instanceof final AkmZ48Chunk z48)
                    z48.updateData ();
                this.chunkStack.add (chunk);
            }
            return;
        }

        final AkpGeneration generation = this.writeGeneration.orElse (AbstractAkpChunk.DEFAULT_GENERATION);
        if (generation == AkpGeneration.GENERATION_3)
        {
            // The chunk order of the Z4/Z8
            final AkmZ48Chunk z48 = this.z48Chunk.isPresent () ? this.z48Chunk.get () : this.convertToZ48Chunk ();
            z48.updateData ();
            this.chunkStack.add (z48);
            this.chunkStack.addAll (this.otherChunks);
            this.chunkStack.add (this.versionChunk);
            this.chunkStack.add (this.effectsChunk);
            this.chunkStack.addAll (this.parts);
        }
        else
        {
            // The chunk order of the S5000/S6000
            this.chunkStack.add (this.effectsChunk);
            this.chunkStack.addAll (this.parts);
            this.chunkStack.add (this.versionChunk);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected long calculateFileSize ()
    {
        // The S5000/S6000 writes 0 as the size of the RIFF chunk, keep it for read files
        return this.hasEmptyTopSize ? 0 : super.calculateFileSize ();
    }


    /** {@inheritDoc} */
    @Override
    public void checkTopChunk (final int type) throws ParseException
    {
        if (AkmRiffChunkId.AMUL_ID.getFourCC () != type)
            throw new ParseException ("Top chunk must be 'AMUL' but is '" + RiffChunkId.toASCII (type) + "'");
    }


    /**
     * Create the Z4/Z8 chunk from the current S5000/S6000 parts and add it to the file. Afterwards,
     * the Z4/Z8 parts can be modified, e.g. to set values with a higher resolution. The Z4/Z8 chunk
     * is only written in generation 3. Note that later changes of the S5000/S6000 parts are not
     * applied to the Z4/Z8 parts.
     *
     * @return The created chunk
     */
    public AkmZ48Chunk createZ48Chunk ()
    {
        final AkmZ48Chunk z48 = this.convertToZ48Chunk ();
        this.z48Chunk = Optional.of (z48);
        return z48;
    }


    /**
     * Create the Z4/Z8 chunk from the S5000/S6000 parts. The parameters which are not present in
     * the S5000/S6000 part (e.g. the priority) are set to their default values.
     *
     * @return The created chunk
     */
    private AkmZ48Chunk convertToZ48Chunk ()
    {
        final AkmZ48Chunk z48 = new AkmZ48Chunk ();
        final List<AkmZ48Part> z48Parts = z48.getParts ();
        for (int i = 0; i < Math.min (this.parts.size (), z48Parts.size ()); i++)
        {
            final AkmPart part = this.parts.get (i);
            final AkmZ48Part z48Part = z48Parts.get (i);
            z48Part.setPresetName (part.getPresetName ());
            z48Part.setTune (part.getTranspose () * 100 + part.getFineTune ());
            z48Part.setMidiChannel (part.getMidiChannel ());
            z48Part.setLevel (convertLevel (part.getVolume (), -60));
            z48Part.setPanning (part.getPanning ());
            z48Part.setOutput (convertOutput (part.getOutput ()));
            // FX1, FX2, RV3, RV4 are mapped to A, B, C, D
            z48Part.setEffects (part.getEffectsChannel ());
            // The conversion of the effects send level is unknown, assume the same as for the level
            z48Part.setEffectsSend (convertLevel (part.getEffectsSend (), -40));
            z48Part.setLowKey (part.getLowKey ());
            z48Part.setHighKey (part.getHighKey ());
            z48Part.setMuted (part.isMuted ());
        }
        return z48;
    }


    /**
     * Convert the level of a S5000/S6000 part to dB. The level is roughly a linear gain factor. The
     * Z8 shows a level of 100 as 0dB when loading a S5000/S6000 multi.
     *
     * @param value The level in the range of [0..101]
     * @param minimum The minimum level in dB
     * @return The level in dB with a resolution of 0.1dB
     */
    private static double convertLevel (final int value, final double minimum)
    {
        if (value <= 0)
            return minimum;
        final double dB = Math.clamp (20.0 * Math.log10 (value / 100.0), minimum, 6.0);
        return Math.round (dB * 10.0) / 10.0;
    }


    /**
     * Convert the output of a S5000/S6000 part to the output of a Z4/Z8 part.
     *
     * @param output The output: 0 = L-R, 1-4 = 1-2..7-8, 8 = L, 9 = R, 10-17 = 1..8
     * @return The output: 0 = L-R, 1-4 = 1-2..7-8, 5 = L, 6 = R, 7-14 = 1..8
     */
    private static int convertOutput (final int output)
    {
        if (output <= 4)
            return output;
        if (output >= 8 && output <= 17)
            return output - 3;
        return 0;
    }


    /**
     * Create a chunk.
     *
     * @param chunkId The ID of the chunk
     * @param data The data of the chunk
     * @return The chunk
     */
    private static RawRIFFChunk createChunk (final RiffChunkId chunkId, final byte [] data)
    {
        final RawRIFFChunk chunk = new RawRIFFChunk (0, chunkId, data.length);
        chunk.setData (data);
        return chunk;
    }


    /**
     * Load a template from the resources.
     *
     * @param resourceName The name of the resource
     * @return The content of the resource
     */
    private static byte [] loadTemplate (final String resourceName)
    {
        try (final InputStream in = AkmFile.class.getResourceAsStream (resourceName))
        {
            if (in == null)
                throw new IllegalStateException ("Missing AKM template resource: " + resourceName);
            return in.readAllBytes ();
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException ("Could not read AKM template resource: " + resourceName, ex);
        }
    }
}
