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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AbstractAkpChunk;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpGeneration;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpKeygroup;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpLfo;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpModulations;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpOutput;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpProgram;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpRiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.akp.riff.AkpTuning;


/**
 * Read/write a Akai S5000/S6000 AKP file.
 *
 * @author Jürgen Moßgraber
 */
public class AkpFile extends AbstractRIFFFile
{
    /** The maximum number of key-groups of a program. */
    public static final int                                       MAX_KEYGROUPS          = 99;
    /** The number of velocity zones of a key-group. */
    public static final int                                       MAX_ZONES_PER_KEYGROUP = 4;

    private static final Collection<Class<? extends RiffChunkId>> AKP_RIFF_CHUNK_IDS     = new ArrayList<> ();
    static
    {
        Collections.addAll (AKP_RIFF_CHUNK_IDS, CommonRiffChunkId.class, AkpRiffChunkId.class);
    }

    private final Set<String>        ignoredChunks   = new HashSet<> ();

    private final AkpProgram         programChunk    = new AkpProgram ();
    private final AkpOutput          outputChunk     = new AkpOutput ();
    private final AkpModulations     modsChunk       = new AkpModulations ();
    private final AkpTuning          tuningChunk     = new AkpTuning ();
    private final AkpLfo             lfo1Chunk       = new AkpLfo (true);
    private final AkpLfo             lfo2Chunk       = new AkpLfo (false);
    private final List<AkpKeygroup>  keygroups       = new ArrayList<> ();
    private Optional<RawRIFFChunk>   z48Chunk        = Optional.empty ();
    private final List<RawRIFFChunk> otherChunks     = new ArrayList<> ();
    private int                      lfoCounter      = 0;
    private boolean                  isS5000Series   = true;
    private boolean                  hasEmptyTopSize = false;
    private Optional<AkpGeneration>  writeGeneration = Optional.empty ();


    /**
     * Constructor.
     *
     * @param akpFile The AKP file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public AkpFile (final File akpFile) throws IOException, ParseException
    {
        super (AkpRiffChunkId.APRG_ID, true);

        try (final FileInputStream stream = new FileInputStream (akpFile))
        {
            this.read (stream);
        }
    }


    /**
     * Default constructor.
     */
    public AkpFile ()
    {
        super (AkpRiffChunkId.APRG_ID, true);
    }


    /**
     * Read and parse an AKP file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    private void read (final InputStream inputStream) throws IOException, ParseException
    {
        final RIFFParser riffParser = new RIFFParser (AKP_RIFF_CHUNK_IDS, AkpRiffChunkId.APRG_ID);
        riffParser.declareGroupChunk (AkpRiffChunkId.APRG_ID.getFourCC (), CommonRiffChunkId.RIFF_ID);
        riffParser.parse (inputStream, this);
        this.hasEmptyTopSize = riffParser.hasEmptyTopSize ();
    }


    /**
     * Write the AKP file in the given generation.
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
     * Write the AKP file in the given generation. Chunks which were read in a different version
     * are converted, which means that their undocumented bytes are set to the default values of the
     * generation. Use the write methods without the generation parameter to keep the versions of a
     * read file, new files are written in the default generation (3).
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
     * Check if the AKP file was written by a S5000/S6000. Files written by a Z4/Z8 contain an
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
     * Create a group with all samples zones from the read AKP file.
     *
     * @param metadataSettings The metadata settings for detection
     * @return The multi-sample source
     */
    public IGroup createGroup (final IMetadataConfig metadataSettings)
    {
        final IGroup group = new DefaultGroup ();
        for (final AkpKeygroup keygroup: this.keygroups)
            for (final ISampleZone sampleZone: keygroup.createSampleZones (this.modsChunk, this.tuningChunk, this.outputChunk, this.lfo1Chunk, this.lfo2Chunk))
                group.addSampleZone (sampleZone);
        fixPanning (group);
        return group;
    }


    /**
     * Fill the program from sample zones. This is the inverse of createGroup.
     *
     * @param keygroupZones The sample zones of each key-group, 1 to 4 zones with the same key range
     *            per key-group
     */
    public void setSampleZones (final List<List<ISampleZone>> keygroupZones)
    {
        this.keygroups.clear ();
        if (keygroupZones.isEmpty ())
            return;

        final List<ISampleZone> allZones = new ArrayList<> ();
        for (final List<ISampleZone> zones: keygroupZones)
            allZones.addAll (zones);
        AkpKeygroup.setProgramParameters (allZones, this.modsChunk, this.tuningChunk, this.outputChunk, this.lfo1Chunk, this.lfo2Chunk);
        for (final List<ISampleZone> zones: keygroupZones)
        {
            final AkpKeygroup keygroup = new AkpKeygroup ();
            keygroup.setSampleZones (zones, this.lfo1Chunk, this.lfo2Chunk);
            this.keygroups.add (keygroup);
        }
    }


    /**
     * Distribute sample zones onto key-groups. Zones share a key-group if they have the same key
     * range and the same key-group level parameters (envelopes, filter, modulation amounts), up to
     * 4 zones per key-group. Zones with the same key range but different parameters are put into
     * additional key-groups which overlap.
     *
     * @param sampleZones The sample zones
     * @return The sample zones of each key-group
     */
    public static List<List<ISampleZone>> createKeygroupZones (final List<ISampleZone> sampleZones)
    {
        final List<ISampleZone> sortedZones = new ArrayList<> (sampleZones);
        sortedZones.sort (Comparator.comparingInt (ISampleZone::getKeyLow).thenComparingInt (ISampleZone::getKeyHigh).thenComparingInt (ISampleZone::getVelocityLow));

        final List<List<ISampleZone>> keygroupZones = new ArrayList<> ();
        final List<AkpKeygroup> keygroupParameters = new ArrayList<> ();
        for (final ISampleZone zone: sortedZones)
        {
            final AkpKeygroup parameters = new AkpKeygroup ();
            parameters.setSampleZones (Collections.singletonList (zone), new AkpLfo (true), new AkpLfo (false));

            int index = -1;
            for (int i = 0; i < keygroupZones.size (); i++)
                if (keygroupZones.get (i).size () < MAX_ZONES_PER_KEYGROUP && keygroupParameters.get (i).hasSameParameters (parameters))
                {
                    index = i;
                    break;
                }

            if (index < 0)
            {
                keygroupZones.add (new ArrayList<> ());
                keygroupParameters.add (parameters);
                index = keygroupZones.size () - 1;
            }
            keygroupZones.get (index).add (zone);
        }
        return keygroupZones;
    }


    /**
     * If all sample zones are panned hard left or hard right, it means only a mono assignment to an
     * output channel. In that case we need to remove the panning otherwise the conversion will be
     * only sound left or right.
     *
     * @param group The group which contains all sample zones
     */
    private static void fixPanning (final IGroup group)
    {
        final Set<Double> pannings = new HashSet<> ();
        for (final ISampleZone zone: group.getSampleZones ())
            pannings.add (Double.valueOf (zone.getPanning ()));
        boolean needsFix = false;
        if (pannings.size () == 1)
        {
            final double pan = pannings.iterator ().next ().doubleValue ();
            needsFix = pan == -1.0 || pan == 1.0;
        }
        if (!needsFix)
            return;
        for (final ISampleZone zone: group.getSampleZones ())
            zone.setPanning (0);
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

        if (id == AkpRiffChunkId.Z48_ID.getFourCC ())
        {
            // Only present in files written by a Z4/Z8. It contains the program in the Z4/Z8
            // format which is not decoded since the S5000/S6000 chunks follow as well. It is kept
            // for writing.
            this.isS5000Series = false;
            this.z48Chunk = Optional.of (chunk);
            return;
        }

        if (id == AkpRiffChunkId.PRG_ID.getFourCC ())
        {
            this.programChunk.read (chunk);
            return;
        }

        if (id == AkpRiffChunkId.OUT_ID.getFourCC ())
        {
            this.outputChunk.read (chunk);
            return;
        }

        if (id == AkpRiffChunkId.MODS_ID.getFourCC ())
        {
            this.modsChunk.read (chunk);
            return;
        }

        if (id == AkpRiffChunkId.LFO_ID.getFourCC ())
        {
            // There are always 2 LFO chunks, LFO 1 comes first
            if (this.lfoCounter == 0)
                this.lfo1Chunk.read (chunk);
            else
                this.lfo2Chunk.read (chunk);
            this.lfoCounter++;
            return;
        }

        if (id == AkpRiffChunkId.TUNE_ID.getFourCC ())
        {
            this.tuningChunk.read (chunk);
            return;
        }

        if (id == AkpRiffChunkId.KGRP_ID.getFourCC ())
        {
            final AkpKeygroup keygroup = new AkpKeygroup ();
            keygroup.read (chunk);
            this.keygroups.add (keygroup);
            return;
        }

        // Info chunk sub-chunks
        if (chunk.getType () == InfoRiffChunkId.INFO_ID.getFourCC ())
            this.infoChunk.add (chunk);
        else
        {
            // Ignore other chunks but keep them for writing
            this.ignoredChunks.add (RiffChunkId.toASCII (chunk.getId ().getFourCC ()));
            this.otherChunks.add (chunk);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void fillChunkStack ()
    {
        // The chunk order is fixed, the Z4/Z8 chunk comes first
        this.chunkStack.clear ();
        if (this.z48Chunk.isPresent ())
            this.chunkStack.add (this.z48Chunk.get ());

        this.programChunk.setNumberOfKeygroups (this.keygroups.size ());
        for (final AbstractAkpChunk chunk: List.of (this.programChunk, this.outputChunk, this.tuningChunk, this.lfo1Chunk, this.lfo2Chunk, this.modsChunk))
        {
            chunk.updateData (this.writeGeneration);
            this.chunkStack.add (chunk);
        }

        for (final AkpKeygroup keygroup: this.keygroups)
        {
            keygroup.updateData (this.writeGeneration);
            this.chunkStack.add (keygroup);
        }

        this.chunkStack.addAll (this.otherChunks);
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
        if (AkpRiffChunkId.APRG_ID.getFourCC () != type)
            throw new ParseException ("Top chunk must be 'APRG' but is '" + RiffChunkId.toASCII (type) + "'");
    }
}
