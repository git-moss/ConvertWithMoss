// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.dls;

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
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffChunkId;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.InfoChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;


/**
 * Read/write a DLS file.
 *
 * @author Jürgen Moßgraber
 */
public class DlsFile extends AbstractRIFFFile
{
    private static final Collection<Class<? extends RiffChunkId>> DLS_RIFF_CHUNK_IDS = new ArrayList<> ();
    static
    {
        Collections.addAll (DLS_RIFF_CHUNK_IDS, CommonRiffChunkId.class, InfoRiffChunkId.class, DlsRiffChunkId.class);
    }

    private final Set<String>       ignoredChunks    = new HashSet<> ();
    private List<DlsInstrument>     instruments      = null;
    private List<Long>              cueOffsets       = new ArrayList<> ();

    // Wave data
    private final List<FormatChunk> waveFormatChunks = new ArrayList<> ();
    private final List<DataChunk>   waveDataChunks   = new ArrayList<> ();
    private final List<String>      waveInfoChunks   = new ArrayList<> ();

    // Used only for reading
    private DlsInstrument           currentInstrument;
    private DlsRegion               currentRegion;
    private boolean                 isInstrumentChunk;
    private boolean                 isWaveChunk;


    /**
     * Constructor.
     *
     * @param dlsFile The DLS file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public DlsFile (final File dlsFile) throws IOException, ParseException
    {
        super (DlsRiffChunkId.DLS_ID, true);

        try (final FileInputStream stream = new FileInputStream (dlsFile))
        {
            this.read (stream);
        }
    }


    /**
     * Default constructor.
     */
    public DlsFile ()
    {
        super (DlsRiffChunkId.DLS_ID, true);
    }


    /**
     * Get all ignored (not handled) chunks.
     *
     * @return The ignored chunks
     */
    public Set<String> getIgnoredChunks ()
    {
        return this.ignoredChunks;
    }


    /**
     * Get the DLS instruments.
     * 
     * @return The instruments
     */
    public List<DlsInstrument> getInstruments ()
    {
        return this.instruments;
    }


    /**
     * Create wave sample data from the format and data chunks.
     * 
     * @return The wave samples
     * @throws IOException Could not read audio metadata
     */
    public List<WavFileSampleData> createWaveSampleData () throws IOException
    {
        final List<WavFileSampleData> result = new ArrayList<> ();
        for (int i = 0; i < this.waveFormatChunks.size (); i++)
        {
            final WaveFile waveFile = new WaveFile (this.waveFormatChunks.get (i), this.waveDataChunks.get (i));
            final WavFileSampleData sampleData = new WavFileSampleData (waveFile);
            result.add (sampleData);
        }
        return result;
    }


    /**
     * Get the wave info file names.
     * 
     * @return The wave info names
     */
    public List<String> getWaveInfoFileNames ()
    {
        return this.waveInfoChunks;
    }


    /**
     * Read and parse a DLS file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    private void read (final InputStream inputStream) throws IOException, ParseException
    {
        final RIFFParser riffParser = new RIFFParser (DLS_RIFF_CHUNK_IDS);
        riffParser.declareGroupChunk (DlsRiffChunkId.DLS_ID.getFourCC (), CommonRiffChunkId.RIFF_ID);
        riffParser.declareGroupChunk (InfoRiffChunkId.INFO_ID.getFourCC (), CommonRiffChunkId.LIST_ID);

        riffParser.declareGroupChunk (DlsRiffChunkId.LINS_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.declareGroupChunk (DlsRiffChunkId.INS_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.declareGroupChunk (DlsRiffChunkId.LRGN_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.declareGroupChunk (DlsRiffChunkId.RGN_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.declareGroupChunk (DlsRiffChunkId.RGN2_ID.getFourCC (), CommonRiffChunkId.LIST_ID);

        riffParser.declareGroupChunk (DlsRiffChunkId.WVPL_ID.getFourCC (), CommonRiffChunkId.LIST_ID);
        riffParser.declareGroupChunk (DlsRiffChunkId.WAVE_ID.getFourCC (), CommonRiffChunkId.LIST_ID);

        riffParser.parse (inputStream, this);

        this.validate ();
    }


    /**
     * Ensure that the cue offsets are in increasing order. If that is the case, the indices match
     * the order of the format/data lists.
     * 
     * @throws ParseException If they are not in order
     */
    private void validate () throws ParseException
    {
        // Ensure that the offsets are in order
        long offset = 0;
        final int numCueOffsets = this.cueOffsets.size ();
        for (int i = 0; i < numCueOffsets; i++)
        {
            final long off = this.cueOffsets.get (i).longValue ();
            if (off < offset)
                throw new ParseException ("Wave Sample References not in order.");
            offset = off;
        }

        if (numCueOffsets != this.waveDataChunks.size () || numCueOffsets != this.waveFormatChunks.size ())
            throw new ParseException ("Wave Sample Format/Data/Cues do not have the same size!");
    }


    /** {@inheritDoc} */
    @Override
    public void enterGroup (final RawRIFFChunk group) throws ParseException
    {
        final int type = group.getType ();
        if (type == DlsRiffChunkId.LINS_ID.getFourCC ())
            this.isInstrumentChunk = true;
        else if (type == DlsRiffChunkId.WVPL_ID.getFourCC ())
            this.isWaveChunk = true;
    }


    /** {@inheritDoc} */
    @Override
    public void leaveGroup (final RawRIFFChunk group) throws ParseException
    {
        final int type = group.getType ();

        if (type == DlsRiffChunkId.LINS_ID.getFourCC ())
            this.isInstrumentChunk = false;
        else if (type == DlsRiffChunkId.WVPL_ID.getFourCC ())
            this.isWaveChunk = false;
    }


    /** {@inheritDoc} */
    @Override
    public void visitChunk (final RawRIFFChunk group, final RawRIFFChunk chunk) throws ParseException
    {
        final int id = chunk.getId ().getFourCC ();

        //
        // Pool Table Chunk

        if (id == DlsRiffChunkId.PTBL_ID.getFourCC ())
        {
            final int numberOfCues = (int) chunk.getFourBytesAsUnsignedLong (4);
            // Byte offsets to each LIST:wave chunk
            for (int i = 0; i < numberOfCues; i++)
                this.cueOffsets.add (Long.valueOf (chunk.getFourBytesAsUnsignedLong (8 + 4 * i)));

            // TODO order by their offset to get the index to the FMT/DATA/INFO lists -> there might
            // not be a full coverage! Therefore, we need to keep track of the chunk positions

            return;
        }

        //
        // Instrument chunks

        if (id == DlsRiffChunkId.COLH_ID.getFourCC ())
        {
            final long numberOfInstruments = chunk.getFourBytesAsUnsignedLong (0);
            this.instruments = new ArrayList<> ((int) numberOfInstruments);
            return;
        }

        if (id == DlsRiffChunkId.INSH_ID.getFourCC ())
        {
            this.currentInstrument = new DlsInstrument (chunk);
            if (this.instruments == null)
                throw new ParseException ("Unsound instrument list.");
            this.instruments.add (this.currentInstrument);
            return;
        }

        //
        // Region chunks

        if (id == DlsRiffChunkId.RGNH_ID.getFourCC ())
        {
            this.currentRegion = new DlsRegion (chunk);
            if (this.currentInstrument == null)
                throw new ParseException ("Unsound region list.");
            this.currentInstrument.addRegion (this.currentRegion);
            return;
        }

        if (id == DlsRiffChunkId.WSMP_ID.getFourCC ())
        {
            if (!this.isWaveChunk)
            {
                if (this.currentRegion == null)
                    throw new ParseException ("Unsound region list.");
                this.currentRegion.parseWaveSampleChunk (chunk);
            }
            return;
        }

        if (id == DlsRiffChunkId.WLNK_ID.getFourCC ())
        {
            if (this.currentRegion == null)
                throw new ParseException ("Unsound region list.");
            this.currentRegion.parseWaveSampleLinkChunk (chunk);
            return;
        }

        //
        // Wave chunks

        if (id == DlsRiffChunkId.FMT_ID.getFourCC ())
        {
            final FormatChunk formatChunk = new FormatChunk (chunk);
            this.waveFormatChunks.add (formatChunk);
            return;
        }

        if (id == DlsRiffChunkId.DATA_ID.getFourCC ())
        {
            final DataChunk dataChunk = new DataChunk (chunk);
            this.waveDataChunks.add (dataChunk);
            return;
        }

        //
        // Info chunk sub-chunks

        if (chunk.getType () == InfoRiffChunkId.INFO_ID.getFourCC ())
        {
            if (this.isInstrumentChunk)
            {
                if (id == InfoRiffChunkId.INFO_INAM.getFourCC ())
                {
                    if (this.currentInstrument == null)
                        throw new ParseException ("Unsound Info chunk.");
                    this.currentInstrument.setName (InfoChunk.chunkDataToAsciiString (chunk));
                }
                return;
            }

            if (this.isWaveChunk)
            {
                if (id == InfoRiffChunkId.INFO_INAM.getFourCC ())
                    this.waveInfoChunks.add (InfoChunk.chunkDataToAsciiString (chunk));
                return;
            }

            this.infoChunk.add (chunk);
            return;
        }

        // Ignore other chunks
        this.ignoredChunks.add (RiffChunkId.toASCII (id));
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        if (this.infoChunk != null)
            sb.append (this.infoChunk.infoText ()).append ('\n');

        sb.append ("Instruments:\n");
        for (final DlsInstrument instrument: this.instruments)
            sb.append (instrument);

        sb.append ("\nSamples:\n");
        final int size = this.waveFormatChunks.size ();
        if (this.waveDataChunks.size () != size || this.waveInfoChunks.size () != size)
            sb.append ("Unsound sample wave data.");
        else
        {
            for (int i = 0; i < size; i++)
            {
                sb.append ("--------------------- ").append (this.waveInfoChunks.get (i)).append ('\n');
                final FormatChunk format = this.waveFormatChunks.get (i);
                sb.append (format.infoText ()).append ('\n');
            }
        }

        if (!this.ignoredChunks.isEmpty ())
        {
            sb.append ("Ignored chunks: \n");
            for (final String chunk: this.ignoredChunks)
                sb.append ("  * ").append (chunk).append ('\n');
        }

        return sb.toString ();
    }


    /** {@inheritDoc} */
    @Override
    protected void fillChunkStack ()
    {
        if (!this.chunkStack.isEmpty ())
            return;

        if (this.infoChunk != null)
            this.chunkStack.add (this.infoChunk);
        // TODO
        // if (this.dataChunk != null)
        // this.chunkStack.add (this.dataChunk);
        // if (this.presetDataChunk != null)
        // this.chunkStack.add (this.presetDataChunk);
    }


    public static void main (final String [] args)
    {
        try
        {
            // final DlsFile dlsFile = new DlsFile (new File
            // ("C:\\Privat\\Programming\\ConvertWithMoss\\Testdateien\\DLS\\Mario_Kart_64_Soundfont.dls"));

            final File parentFile = new File ("C:\\Privat\\Programming\\ConvertWithMoss\\Testdateien\\DLS\\");
            for (final String filename: parentFile.list ())
            {
                if (!filename.endsWith (".dls"))
                    continue;

                // IO.println (dlsFile.infoText ());
                final DlsFile dlsFile = new DlsFile (new File (parentFile, filename));

                List<DlsInstrument> instruments = dlsFile.getInstruments ();
                if (instruments == null)
                {
                    IO.println ("No instrument in : " + filename);
                    continue;
                }
                for (final DlsInstrument instrument: instruments)
                {
                    for (final DlsRegion region: instrument.getRegions ())
                    {
                        // IO.println (region.getChannelPlacement () + " : " + region.getLinkOptions
                        // () + " : " + region.getPhaseGroup ());
                    }

                }

                IO.println (dlsFile.infoText ());
            }
        }
        catch (IOException | ParseException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace ();
        }
    }
}
