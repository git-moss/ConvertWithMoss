// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.akai.akp.riff;

import java.util.Optional;

import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;


/**
 * The generations of the AKP format. The generations differ in the versions (and sizes) of some of
 * the chunks. Since there is no OS version in the file, the generations were identified from the
 * file dates of the analysed files: generation 1 = 1998, generation 2 = 1999, generation 3 = 2000 -
 * 2002 (also written by the Z4/Z8). The chunks 'out ', 'mods' and 'env ' are version 1 in all
 * generations.
 *
 * @author Jürgen Moßgraber
 */
public enum AkpGeneration
{
    /** The first generation: all chunks are version 1. */
    GENERATION_1(1, 1, 1, 1, 1, 1),
    /** The second generation: extended 'lfo ' chunk, 'kloc', 'filt' and 'zone' version 2. */
    GENERATION_2(1, 1, 2, 2, 2, 2),
    /** The third generation: extended 'tune' and 'zone' chunks, 'prg ' version 2. */
    GENERATION_3(2, 2, 2, 2, 2, 3);


    private final int programVersion;
    private final int tuningVersion;
    private final int lfoVersion;
    private final int locationVersion;
    private final int filterVersion;
    private final int zoneVersion;


    /**
     * Constructor.
     *
     * @param programVersion The version of the 'prg ' chunk
     * @param tuningVersion The version of the 'tune' chunk
     * @param lfoVersion The version of the 'lfo ' chunks
     * @param locationVersion The version of the 'kloc' chunk
     * @param filterVersion The version of the 'filt' chunk
     * @param zoneVersion The version of the 'zone' chunks
     */
    AkpGeneration (final int programVersion, final int tuningVersion, final int lfoVersion, final int locationVersion, final int filterVersion, final int zoneVersion)
    {
        this.programVersion = programVersion;
        this.tuningVersion = tuningVersion;
        this.lfoVersion = lfoVersion;
        this.locationVersion = locationVersion;
        this.filterVersion = filterVersion;
        this.zoneVersion = zoneVersion;
    }


    /**
     * Get the version of a chunk in this generation.
     *
     * @param chunkId The ID of the chunk
     * @param defaultVersion The version to return for chunks which are not part of an AKP file
     * @return The version
     */
    public int getVersion (final RiffChunkId chunkId, final int defaultVersion)
    {
        final Optional<AkpRiffChunkId> id = AkpRiffChunkId.fromFourCC (chunkId.getFourCC ());
        if (id.isEmpty ())
            return defaultVersion;

        return switch (id.get ())
        {
            case PRG_ID -> this.programVersion;
            case TUNE_ID -> this.tuningVersion;
            case LFO_ID -> this.lfoVersion;
            case KGRP_LOC_ID -> this.locationVersion;
            case KGRP_FILTER_ID -> this.filterVersion;
            case KGRP_ZONE_ID -> this.zoneVersion;
            case OUT_ID, MODS_ID, KGRP_ENV_ID -> 1;
            default -> defaultVersion;
        };
    }
}
