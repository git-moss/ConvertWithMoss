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

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.IMetadataConfig;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.CommonRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpKeygroup;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpModulations;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpOutput;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpProgram;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpRiffChunkId;
import de.mossgrabers.convertwithmoss.format.akai.s5000.riff.AkpTuning;
import de.mossgrabers.tools.FileUtils;


/**
 * Read/write a Akai S5000/S6000 AKP file.
 *
 * @author Jürgen Moßgraber
 */
public class AkpFile extends AbstractRIFFFile
{
    private static final Collection<Class<? extends RiffChunkId>> AKP_RIFF_CHUNK_IDS = new ArrayList<> ();
    static
    {
        Collections.addAll (AKP_RIFF_CHUNK_IDS, CommonRiffChunkId.class, AkpRiffChunkId.class);
    }

    private final Set<String>       ignoredChunks = new HashSet<> ();

    private final AkpProgram        programChunk  = new AkpProgram ();
    private final AkpOutput         outputChunk   = new AkpOutput ();
    private final AkpModulations    modsChunk     = new AkpModulations ();
    private final AkpTuning         tuningChunk   = new AkpTuning ();
    private final List<AkpKeygroup> keygroups     = new ArrayList<> ();
    private final File              akpFile;
    private boolean                 isS5000Series;


    /**
     * Constructor.
     *
     * @param akpFile The AKP file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public AkpFile (final File akpFile) throws IOException, ParseException
    {
        super (AkpRiffChunkId.APRG_ID);

        this.akpFile = akpFile;

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
        super (AkpRiffChunkId.APRG_ID);

        this.akpFile = null;
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
        this.isS5000Series = riffParser.hasEmptyTopSize ();
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
     * Create a multi-sample source from the read AKP file.
     *
     * @param sourceFolder The starting source folder
     * @param metadataSettings The metadata settings for detection
     * @return The multi-sample source
     */
    public IMultisampleSource createMultisampleSource (final File sourceFolder, final IMetadataConfig metadataSettings)
    {
        final File parentFile = this.akpFile.getParentFile ();
        final String name = FileUtils.getNameWithoutType (this.akpFile);
        final String [] parts = AudioFileUtils.createPathParts (parentFile, sourceFolder, name);
        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (this.akpFile, parts, name, AudioFileUtils.subtractPaths (sourceFolder, this.akpFile));

        final IGroup group = new DefaultGroup ();
        multisampleSource.setGroups (Collections.singletonList (group));

        for (final AkpKeygroup keygroup: this.keygroups)
            for (final ISampleZone sampleZone: keygroup.createSampleZones (this.modsChunk, this.tuningChunk, this.outputChunk))
                group.addSampleZone (sampleZone);
        fixPanning (group);

        multisampleSource.getMetadata ().detectMetadata (metadataSettings, parts);

        return multisampleSource;
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
            // Not used
            return;

        if (id == AkpRiffChunkId.TUNE_ID.getFourCC ())
        {
            this.tuningChunk.read (chunk);
            return;
        }

        if (id == AkpRiffChunkId.KGRP_ID.getFourCC ())
        {
            this.keygroups.add (new AkpKeygroup (chunk));
            return;
        }

        // Info chunk sub-chunks
        if (chunk.getType () == InfoRiffChunkId.INFO_ID.getFourCC ())
            this.infoChunk.add (chunk);
        else
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
