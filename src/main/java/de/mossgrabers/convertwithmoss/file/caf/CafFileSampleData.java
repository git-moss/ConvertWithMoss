// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.caf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.AbstractFileSampleData;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.ui.Functions;


/**
 * The data of a CAF sample file.
 *
 * @author Jürgen Moßgraber
 */
public class CafFileSampleData extends AbstractFileSampleData
{
    private CafFile cafFile = null;


    /**
     * Constructor.
     *
     * @param file The file where the sample is stored
     * @throws IOException Could not read the file
     */
    public CafFileSampleData (final File file) throws IOException
    {
        super (file);
    }


    /**
     * Constructor for a sample stored in a ZIP file.
     *
     * @param zipFile The ZIP file which contains the CAF files
     * @param zipEntry The relative path in the ZIP where the file is stored
     * @throws IOException Could not read the file
     */
    public CafFileSampleData (final File zipFile, final File zipEntry) throws IOException
    {
        super (zipFile, zipEntry);
    }


    /** {@inheritDoc} */
    @Override
    public void writeSample (final OutputStream outputStream) throws IOException
    {
        final CafFile caf = this.getCafFile ();
        final CafAudioDescriptionChunk descriptionChunk = this.checkCanDecode (caf);

        byte [] data = caf.decodeAudioData ();
        final boolean isFloat = caf.isDecodedFloat ();
        final int bitsPerSample = caf.getDecodedBitsPerSample ();

        // WAV stores 8-bit samples unsigned
        if (!isFloat && bitsPerSample == 8)
            data = convertSigned8BitToUnsigned (data);

        final WaveFile wavFile = new WaveFile (descriptionChunk.getChannelsPerFrame (), (int) Math.round (descriptionChunk.getSampleRate ()), bitsPerSample, (int) caf.getNumberOfFrames ());
        if (isFloat)
            wavFile.getFormatChunk ().setCompressionCode (FormatChunk.WAVE_FORMAT_IEEE_FLOAT);
        wavFile.getDataChunk ().setData (data);
        wavFile.write (outputStream);
    }


    /** {@inheritDoc} */
    @Override
    public void addZoneData (final ISampleZone zone, final boolean addRootKey, final boolean addLoops) throws IOException
    {
        final CafFile caf = this.getCafFile ();
        final CafAudioDescriptionChunk descriptionChunk = caf.getAudioDescriptionChunk ();

        final int numberOfChannels = descriptionChunk.getChannelsPerFrame ();
        if (numberOfChannels > 2)
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_ERR_MONO", Integer.toString (numberOfChannels), this.filename));

        if (zone.getStart () < 0)
            zone.setStart (0);
        if (zone.getStop () <= 0)
            zone.setStop ((int) caf.getNumberOfFrames ());

        final CafInstrumentChunk instrumentChunk = caf.getInstrumentChunk ();
        if (instrumentChunk == null)
            return;

        // Read the root key if not set...
        final int baseNote = Math.round (instrumentChunk.getBaseNote ());
        if (addRootKey && zone.getKeyRoot () == -1)
            zone.setKeyRoot (baseNote);

        if (zone.getTuning () == 0)
            zone.setTuning (Math.clamp (instrumentChunk.getBaseNote () - baseNote, -0.5, 0.5));

        if (addLoops)
            addLoops (caf, zone.getLoops ());
    }


    private static void addLoops (final CafFile caf, final List<ISampleLoop> loops)
    {
        // Check if loops are already present
        if (!loops.isEmpty ())
            return;

        // Get the loop from the sustain region of the instrument chunk...
        final CafInstrumentChunk instrumentChunk = caf.getInstrumentChunk ();
        final CafRegion region = caf.getRegion (instrumentChunk.getSustainRegionID ());
        if (region != null && (region.getFlags () & CafRegion.FLAG_LOOP_ENABLE) > 0)
        {
            final double start = region.getStartPosition ();
            final double end = region.getEndPosition ();
            if (start >= 0 && end > start)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                if ((region.getFlags () & CafRegion.FLAG_PLAY_BACKWARD) > 0)
                    loop.setType ((region.getFlags () & CafRegion.FLAG_PLAY_FORWARD) > 0 ? LoopType.ALTERNATING : LoopType.BACKWARDS);
                else
                    loop.setType (LoopType.FORWARDS);
                loop.setStart ((int) start);
                loop.setEnd ((int) end);
                loops.add (loop);
                return;
            }
        }

        // ... or from a pair of sustain loop markers
        double loopStart = -1;
        double loopEnd = -1;
        for (final CafMarker marker: caf.getMarkers ())
            if (CafMarker.TYPE_SUSTAIN_LOOP_START.equals (marker.getType ()))
                loopStart = marker.getFramePosition ();
            else if (CafMarker.TYPE_SUSTAIN_LOOP_END.equals (marker.getType ()))
                loopEnd = marker.getFramePosition ();

        if (loopStart >= 0 && loopEnd > loopStart)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart ((int) loopStart);
            loop.setEnd ((int) loopEnd);
            loops.add (loop);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void createAudioMetadata () throws IOException
    {
        // The javax.sound SPI cannot read CAF files; provide their metadata from the parsed
        // chunks instead
        final CafFile caf = this.getCafFile ();
        final CafAudioDescriptionChunk descriptionChunk = this.checkCanDecode (caf);
        this.audioMetadata = new DefaultAudioMetadata (descriptionChunk.getChannelsPerFrame (), (int) Math.round (descriptionChunk.getSampleRate ()), caf.getDecodedBitsPerSample (), (int) caf.getNumberOfFrames ());
    }


    /** {@inheritDoc} */
    @Override
    public void updateMetadata (final IMetadata metadata)
    {
        final CafFile caf;
        try
        {
            caf = this.getCafFile ();
        }
        catch (final IOException _)
        {
            return;
        }

        final Map<String, String> information = caf.getInformation ();

        final String artist = information.get (CafFile.INFORMATION_ARTIST);
        if (artist != null)
            metadata.setCreator (artist);

        final StringBuilder sb = new StringBuilder ();
        final String copyright = information.get (CafFile.INFORMATION_COPYRIGHT);
        if (copyright != null)
            sb.append (copyright).append ('\n');
        final String comments = information.get (CafFile.INFORMATION_COMMENTS);
        if (comments != null)
            sb.append (comments).append ('\n');

        final String description = sb.toString ().trim ();
        if (!description.isEmpty ())
            metadata.setDescription (description);
    }


    /**
     * Get the underlying CAF file.
     *
     * @return The file
     * @throws IOException Could not parse the file
     */
    public CafFile getCafFile () throws IOException
    {
        if (this.cafFile != null)
            return this.cafFile;

        if (this.zipFile == null)
            this.cafFile = new CafFile (this.sampleFile);
        else
        {
            this.cafFile = new CafFile ();
            try (final ZipFile zf = new ZipFile (this.zipFile); final InputStream in = zf.getInputStream (this.getHarmonizedZipEntry (zf)))
            {
                this.cafFile.read (in);
            }
        }

        return this.cafFile;
    }


    /**
     * Check if the audio data of the CAF file can be decoded and raise an exception if not.
     *
     * @param caf The CAF file to check
     * @return The audio description chunk of the file
     * @throws IOException The audio data cannot be decoded
     */
    private CafAudioDescriptionChunk checkCanDecode (final CafFile caf) throws IOException
    {
        final CafAudioDescriptionChunk descriptionChunk = caf.getAudioDescriptionChunk ();
        if (!caf.canDecodeAudioData ())
            throw new IOException (Functions.getMessage ("IDS_ERR_UNSUPPORTED_CAF_CODEC", this.filename, descriptionChunk.getFormatName (), descriptionChunk.getFormatID ()));
        return descriptionChunk;
    }


    /**
     * Convert signed 8-bit samples (CAF) to unsigned ones (WAV).
     *
     * @param data The sample data
     * @return The converted data in a new array
     */
    private static byte [] convertSigned8BitToUnsigned (final byte [] data)
    {
        final byte [] result = new byte [data.length];
        for (int i = 0; i < data.length; i++)
            result[i] = (byte) (data[i] + 128);
        return result;
    }
}
