// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.dls;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;


/**
 * A DLS instrument.
 *
 * @author Jürgen Moßgraber
 */
public class DlsInstrument
{
    /** The length of the INSH structure. */
    private static final int      LENGTH_INSH   = 12;

    private final long            numRegions;
    private String                name          = "<undefined>";
    private final long            program;
    private final int             bankCc32;
    private final int             bankCc0;
    private final boolean         isDrumInstrument;
    private List<DlsArticulation> articulations = new ArrayList<> ();
    private final List<DlsRegion> regions;


    /**
     * Constructor.
     *
     * @param instrumentHeaderChunk The instrument header chunk from which to initialize the
     *            instrument
     * @throws ParseException Could not read the data
     */
    public DlsInstrument (final RawRIFFChunk instrumentHeaderChunk) throws ParseException
    {
        if (instrumentHeaderChunk.getId ().getFourCC () != DlsRiffChunkId.INSH_ID.getFourCC ())
            throw new ParseException ("Given chunk is not a INSH chunk.");

        if (instrumentHeaderChunk.getDataSize () != LENGTH_INSH)
            throw new ParseException ("Unsound INSH chunk.");

        this.numRegions = instrumentHeaderChunk.getFourBytesAsUnsignedLong (0);
        this.regions = new ArrayList<> ((int) this.numRegions);

        // Bits 0-6 are defined as MIDI CC32 and bits 8-14 are defined as MIDI CC0. Bits 7 and
        // 15-30 are reserved and should be written to zero. If the F_INSTRUMENT_DRUMS flag (Bit
        // 31) is equal to 1 then the instrument is a drum instrument; if equal to 0 then the
        // instrument is a melodic instrument
        final long bank = instrumentHeaderChunk.getFourBytesAsUnsignedLong (4);
        this.bankCc32 = (int) (bank & 0x7F); // bits 0-6
        this.bankCc0 = (int) (bank >> 8 & 0x7F); // bits 8-14
        this.isDrumInstrument = (bank >> 31 & 0x1) != 0; // bit 31

        this.program = instrumentHeaderChunk.getFourBytesAsUnsignedLong (8);
    }


    /**
     * Set the name of the instrument.
     *
     * @param name The name to set
     */
    public void setName (final String name)
    {
        this.name = name.trim ();
        final int pos = this.name.indexOf (0);
        if (pos >= 0)
            this.name = this.name.substring (0, pos);
    }


    /**
     * Get the name of the instrument.
     * 
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }


    /**
     * Get the regions.
     * 
     * @return The regions
     */
    public List<DlsRegion> getRegions ()
    {
        return this.regions;
    }


    /**
     * Add a region to the instrument.
     *
     * @param region The region to add
     */
    public void addRegion (final DlsRegion region)
    {
        this.regions.add (region);
    }


    /**
     * Check if this is a drum instrument.
     *
     * @return True if it is a drum instrument otherwise the instrument is a melodic instrument
     */
    public boolean isDrumInstrument ()
    {
        return this.isDrumInstrument;
    }


    /**
     * Add an articulation to the region.
     * 
     * @param articulation The articulation
     */
    public void addArticulation (final DlsArticulation articulation)
    {
        this.articulations.add (articulation);
    }


    /**
     * Get the articulations of the instrument.
     * 
     * @return The articulations
     */
    public List<DlsArticulation> getArticulations ()
    {
        return this.articulations;
    }


    /** {@inheritDoc} */
    @Override
    public String toString ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("* Instrument: ").append (this.name).append ('\n');
        sb.append ("  * Is Drum Instrument: ").append (this.isDrumInstrument ? "Yes" : "No").append ('\n');
        sb.append ("  * Bank: CC32 ").append (this.bankCc32).append (" / CC0 ").append (this.bankCc0).append ('\n');
        sb.append ("  * Program: ").append (this.program).append ('\n');
        for (final DlsRegion region: this.regions)
            sb.append (region);
        return sb.toString ();
    }
}
