// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ableton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;


/**
 * The data of a sample which is stored in a Live Pack, compressed with FLAC. It is read from the
 * pack and de-compressed whenever it is needed.
 *
 * @author Jürgen Moßgraber
 */
public class AbletonLivePackSampleData extends AbstractFileSampleData
{
    private final AbletonLivePack       pack;
    private final AbletonLivePack.Entry entry;


    /**
     * Constructor.
     *
     * @param pack The pack which contains the sample
     * @param entry The sample file in the pack
     */
    public AbletonLivePackSampleData (final AbletonLivePack pack, final AbletonLivePack.Entry entry)
    {
        super (entry.getOriginalName (), null);

        this.pack = pack;
        this.entry = entry;
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        try (final InputStream in = this.pack.openStream (this.entry))
        {
            AudioFileUtils.decompressToWav (in, outputStream);
        }
        catch (final RuntimeException ex)
        {
            throw new IOException (ex);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        try (final InputStream in = this.pack.openStream (this.entry))
        {
            this.audioMetadata = AudioFileUtils.getMetadata (in);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        // FLAC stores no root key and no loops, but the length of the audio is the play range of
        // the zone as long as the source format did not set one
        if (zone.getStart () < 0)
            zone.setStart (0);
        if (zone.getStop () <= 0)
            zone.setStop (this.getAudioMetadata ().getNumberOfSamples ());
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        // The samples of a pack carry no metadata
    }
}
