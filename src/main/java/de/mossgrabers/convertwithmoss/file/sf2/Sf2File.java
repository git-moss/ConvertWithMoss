// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.file.sf2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.IChunk;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.file.riff.AbstractRIFFFile;
import de.mossgrabers.convertwithmoss.file.riff.RIFFParser;
import de.mossgrabers.convertwithmoss.file.riff.RawRIFFChunk;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;
import de.mossgrabers.convertwithmoss.file.wav.InfoChunk;
import de.mossgrabers.tools.ui.Functions;


/**
 * Read/write a SoundFont 2 file. A SoundFont consists of a couple of presets. A preset consists of
 * one or more instrument layers (groups). An instrument references one or more samples. Synthesizer
 * and effect parameters can be applied on instrument and preset level.
 * <p>
 * Preset ->1:N Zones ->1:1 Instrument ->1:N Zone ->1:1 Sample.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2File extends AbstractRIFFFile
{
    /** The length of the PBAG structure. */
    private static final int          LENGTH_PBAG   = 4;
    /** The length of the PMOD structure. */
    private static final int          LENGTH_PMOD   = 10;
    /** The length of the PGEN structure. */
    private static final int          LENGTH_PGEN   = 4;
    /** The length of the INST structure. */
    private static final int          LENGTH_INST   = 22;
    /** The length of the IBAG structure. */
    private static final int          LENGTH_IBAG   = 4;
    /** The length of the IMOD structure. */
    private static final int          LENGTH_IMOD   = 10;
    /** The length of the IGEN structure. */
    private static final int          LENGTH_IGEN   = 4;
    /** The length of the SHDR structure. */
    private static final int          LENGTH_SHDR   = 46;

    private final List<Sf2Preset>     presets       = new ArrayList<> ();
    private final List<Sf2Instrument> instruments   = new ArrayList<> ();
    private final Set<String>         ignoredChunks = new HashSet<> ();

    private Sf2DataChunk              dataChunk;
    private Sf2PresetDataChunk        presetDataChunk;


    /**
     * Constructor.
     *
     * @param sf2File The SF2 file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public Sf2File (final File sf2File) throws IOException, ParseException
    {
        super (RiffID.SF_SFBK_ID);

        try (final FileInputStream stream = new FileInputStream (sf2File))
        {
            this.read (stream);
        }
    }


    /**
     * Default constructor.
     */
    public Sf2File ()
    {
        super (RiffID.SF_SFBK_ID);

        this.infoChunk = new InfoChunk ();
        this.dataChunk = new Sf2DataChunk ();
        this.presetDataChunk = new Sf2PresetDataChunk ();
    }


    /**
     * Get the creation date as a date object.
     *
     * @return The date object
     */
    public Date getParsedCreationDate ()
    {
        if (this.infoChunk != null)
            return this.infoChunk.getCreationDate ();
        return new Date ();
    }


    /**
     * Get the names of any sound designers or engineers responsible for the SoundFont compatible
     * bank (optional).
     *
     * @return The names, empty string if not present
     */
    public String getSoundDesigner ()
    {
        if (this.infoChunk == null)
            return "";
        final String result = this.infoChunk.getInfoField (RiffID.INFO_IENG, RiffID.INFO_IART, RiffID.INFO_ITCH, RiffID.INFO_ISTR, RiffID.INFO_STAR);
        return result == null ? "" : result.trim ();
    }


    /**
     * Get keywords.
     *
     * @return The keywords, empty string if not present
     */
    public String getKeywords ()
    {
        if (this.infoChunk == null)
            return "";
        final String result = this.infoChunk.getInfoField (RiffID.INFO_IKEY);
        return result == null ? "" : result.trim ();
    }


    /**
     * Get the presets. The last preset needs to be ignored since it only signals the end of the
     * preset list.
     *
     * @return The presets
     */
    public List<Sf2Preset> getPresets ()
    {
        return this.presets;
    }


    /**
     * Get the data chunk.
     *
     * @return The data chunk
     */
    public Sf2DataChunk getDataChunk ()
    {
        return this.dataChunk;
    }


    /**
     * Get the preset data chunk.
     *
     * @return The preset data chunk
     */
    public Sf2PresetDataChunk getPresetDataChunk ()
    {
        return this.presetDataChunk;
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
     * Read and parse a SF2 file.
     *
     * @param inputStream Where to read the file from
     * @throws IOException Could not read the file
     * @throws ParseException Error during parsing
     */
    private void read (final InputStream inputStream) throws IOException, ParseException
    {
        final RIFFParser riffParser = new RIFFParser ();
        riffParser.declareGroupChunk (RiffID.SF_SFBK_ID.getId (), RiffID.RIFF_ID.getId ());
        riffParser.declareGroupChunk (RiffID.INFO_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.declareGroupChunk (RiffID.SF_DATA_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.declareGroupChunk (RiffID.SF_PDTA_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.parse (inputStream, this, true, true);
    }


    /** {@inheritDoc} */
    @Override
    public void enterGroup (final RawRIFFChunk group) throws ParseException
    {
        switch (RiffID.fromId (group.getType ()))
        {
            case INFO_ID:
                this.infoChunk = new InfoChunk ();
                break;

            case SF_DATA_ID:
                this.dataChunk = new Sf2DataChunk ();
                break;

            case SF_PDTA_ID:
                this.presetDataChunk = new Sf2PresetDataChunk ();
                break;

            default:
                // Not used
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void visitChunk (final RawRIFFChunk group, final RawRIFFChunk chunk) throws ParseException
    {
        final RiffID fromId = RiffID.fromId (chunk.getId ());
        switch (fromId)
        {
            ////////////////////////////////////////////
            // Data chunk sub-chunks

            case SMPL_ID:
            case SF_SM24_ID:
                this.dataChunk.add (chunk);
                break;

            ////////////////////////////////////////////////////
            // Preset, Instrument, and Sample Header chunks

            case SF_PHDR_ID:
                this.presetDataChunk.add (chunk);
                this.parsePresets (chunk);
                break;
            case SF_PBAG_ID:
                this.presetDataChunk.add (chunk);
                this.parsePresetZones (chunk);
                break;
            case SF_PGEN_ID:
                this.presetDataChunk.add (chunk);
                this.parsePresetGenerators (chunk);
                break;
            case SF_PMOD_ID:
                this.presetDataChunk.add (chunk);
                this.parsePresetModulators (chunk);
                break;
            case INST_ID:
                this.presetDataChunk.add (chunk);
                this.parseInstruments (chunk);
                break;
            case SF_IBAG_ID:
                this.presetDataChunk.add (chunk);
                this.parseInstrumentZones (chunk);
                break;
            case SF_IMOD_ID:
                this.presetDataChunk.add (chunk);
                this.parseInstrumentModulators (chunk);
                break;
            case SF_IGEN_ID:
                this.presetDataChunk.add (chunk);
                this.parseInstrumentGenerators (chunk);
                break;
            case SF_SHDR_ID:
                this.presetDataChunk.add (chunk);
                this.parseSampleHeader (chunk);
                break;

            default:
                // Info chunk sub-chunks
                if (chunk.getType () == RiffID.INFO_ID.getId ())
                    this.infoChunk.add (chunk);
                else
                    // Ignore other chunks
                    this.ignoredChunks.add (RiffID.toASCII (chunk.getId ()));
                break;
        }
    }


    /**
     * Parse all presets from the preset header chunk (PHDR).
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parsePresets (final RawRIFFChunk chunk) throws ParseException
    {
        final long length = chunk.getSize ();
        if (length % Sf2Preset.LENGTH_PRESET_HEADER > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET"));
        final long presetCount = length / Sf2Preset.LENGTH_PRESET_HEADER;
        for (int i = 0; i < presetCount; i++)
        {
            final Sf2Preset preset = new Sf2Preset ();
            preset.readHeader (i * Sf2Preset.LENGTH_PRESET_HEADER, chunk);
            this.presets.add (preset);
        }
    }


    /**
     * Parse the preset zones chunk (PBAG) and assign the parsed zones to their preset.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parsePresetZones (final RawRIFFChunk chunk) throws ParseException
    {
        final long len = chunk.getSize ();
        if (len % LENGTH_PBAG > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_ZONE"));

        try
        {
            for (int i = 0; i < this.presets.size () - 1; i++)
            {
                final Sf2Preset preset = this.presets.get (i);
                final Sf2Preset nextPreset = this.presets.get (i + 1);

                // Get the pointer into the PGEN structure
                final int firstZoneIndex = preset.getFirstZoneIndex ();
                final int numberOfZones = nextPreset.getFirstZoneIndex () - firstZoneIndex;

                for (int zoneCounter = 0; zoneCounter < numberOfZones; zoneCounter++)
                {
                    final int offset = (firstZoneIndex + zoneCounter) * LENGTH_PBAG;
                    final int generatorIndex = chunk.getTwoBytesAsInt (offset);
                    final int modulatorIndex = chunk.getTwoBytesAsInt (offset + 2);
                    final int nextGeneratorIndex = chunk.getTwoBytesAsInt (offset + LENGTH_PBAG);
                    final int nextModulatorIndex = chunk.getTwoBytesAsInt (offset + LENGTH_PBAG + 2);
                    preset.addZone (new Sf2PresetZone (generatorIndex, nextGeneratorIndex - generatorIndex, modulatorIndex, nextModulatorIndex - modulatorIndex));
                }
            }
        }
        catch (final RuntimeException ex)
        {
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_ZONE"), ex);
        }
    }


    /**
     * Parse the preset modulators chunk (PMOD) and assign the parsed modulators to their preset.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parsePresetModulators (final RawRIFFChunk chunk) throws ParseException
    {
        // Check for sound PMOD structure
        final long size = chunk.getSize ();
        if (size % LENGTH_PMOD > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_MODULATORS"));

        for (int i = 0; i < this.presets.size () - 1; i++)
        {
            final Sf2Preset preset = this.presets.get (i);
            for (int zoneIndex = 0; zoneIndex < preset.getZoneCount (); zoneIndex++)
                parsePresetZoneModulators (chunk, preset.getZone (zoneIndex));
        }
    }


    /**
     * Parse all modulators of a preset zone.
     *
     * @param chunk The chunk to parse
     * @param zone The zone
     */
    private static void parsePresetZoneModulators (final RawRIFFChunk chunk, final Sf2PresetZone zone)
    {
        final int firstModulator = zone.getFirstModulator ();
        final int numberOfModulators = zone.getNumberOfModulators ();

        for (int index = 0; index < numberOfModulators; index++)
        {
            final int offset = (firstModulator + index) * LENGTH_PMOD;
            final int sourceModulator = chunk.getTwoBytesAsInt (offset);
            final int destinationGenerator = chunk.getTwoBytesAsInt (offset + 2);
            final int modAmount = chunk.getTwoBytesAsInt (offset + 4);
            final int amountSourceOperand = chunk.getTwoBytesAsInt (offset + 6);
            final int transformOperand = chunk.getTwoBytesAsInt (offset + 8);
            zone.addModulator (sourceModulator, destinationGenerator, modAmount, amountSourceOperand, transformOperand);
        }
    }


    /**
     * Parse the preset generators chunk (PGEN) and assign the parsed generators to their preset.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parsePresetGenerators (final RawRIFFChunk chunk) throws ParseException
    {
        // Check for sound PGEN structure
        final long size = chunk.getSize ();
        if (size % LENGTH_PGEN > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_GENERATORS"));

        for (int i = 0; i < this.presets.size () - 1; i++)
        {
            final Sf2Preset preset = this.presets.get (i);
            for (int zoneIndex = 0; zoneIndex < preset.getZoneCount (); zoneIndex++)
                parsePresetZoneGenerators (chunk, preset.getZone (zoneIndex));
        }
    }


    /**
     * Parse all generators of a preset zone.
     *
     * @param chunk The chunk to parse
     * @param zone The zone
     */
    private static void parsePresetZoneGenerators (final RawRIFFChunk chunk, final Sf2PresetZone zone)
    {
        final int firstGenerator = zone.getFirstGenerator ();
        final int numberOfGenerators = zone.getNumberOfGenerators ();

        for (int index = 0; index < numberOfGenerators; index++)
        {
            final int offset = (firstGenerator + index) * LENGTH_PGEN;
            final int generator = chunk.getTwoBytesAsInt (offset);
            final int value = chunk.getTwoBytesAsInt (offset + 2);
            zone.addGenerator (generator, value);
        }

        // The first zone might be global. In that case it does not end with an instrument
        // generator
        if (!zone.hasGenerator (Generator.INSTRUMENT))
            zone.setGlobal (true);
    }


    /**
     * Parse the instrument chunk (INST) and assign the parsed instruments to their preset.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parseInstruments (final RawRIFFChunk chunk) throws ParseException
    {
        final long leng = chunk.getSize ();
        if (leng % LENGTH_INST > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INST"));

        // Parse all instruments
        final long instrumentCount = leng / LENGTH_INST;
        for (int i = 0; i < instrumentCount; i++)
        {
            final int offset = LENGTH_INST * i;
            final Sf2Instrument instrument = new Sf2Instrument ();
            instrument.readHeader (offset, chunk);
            this.instruments.add (instrument);
        }

        // Assign the instruments to the preset zones
        for (int i = 0; i < this.presets.size () - 1; i++)
        {
            final Sf2Preset preset = this.presets.get (i);
            for (int zoneIndex = 0; zoneIndex < preset.getZoneCount (); zoneIndex++)
            {
                final Sf2PresetZone zone = preset.getZone (zoneIndex);
                zone.applyInstrument (this.instruments);
            }
        }
    }


    /**
     * Parse the instrument zones chunk (IBAG) and assign the parsed zones to their instrument.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parseInstrumentZones (final RawRIFFChunk chunk) throws ParseException
    {
        final long length = chunk.getSize ();
        if (length % LENGTH_IBAG > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_ZONE"));

        try
        {
            for (int i = 0; i < this.instruments.size () - 1; i++)
            {
                final Sf2Instrument instrument = this.instruments.get (i);
                final Sf2Instrument nextInstrument = this.instruments.get (i + 1);

                // Get the pointer into the IGEN structure
                final int firstZoneIndex = instrument.getFirstZoneIndex ();
                final int numberOfZones = nextInstrument.getFirstZoneIndex () - firstZoneIndex;

                for (int zoneCounter = 0; zoneCounter < numberOfZones; zoneCounter++)
                {
                    final int offset = (firstZoneIndex + zoneCounter) * LENGTH_IBAG;
                    final int generatorIndex = chunk.getTwoBytesAsInt (offset);
                    final int modulatorIndex = chunk.getTwoBytesAsInt (offset + 2);
                    final int nextGeneratorIndex = chunk.getTwoBytesAsInt (offset + LENGTH_IBAG);
                    final int nextModulatorIndex = chunk.getTwoBytesAsInt (offset + LENGTH_IBAG + 2);
                    instrument.addZone (new Sf2InstrumentZone (generatorIndex, nextGeneratorIndex - generatorIndex, modulatorIndex, nextModulatorIndex - modulatorIndex));
                }
            }
        }
        catch (final RuntimeException ex)
        {
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_ZONE"), ex);
        }
    }


    /**
     * Parse the instrument modulators chunk (IMOD) and assign the parsed modulators to their
     * instrument.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parseInstrumentModulators (final RawRIFFChunk chunk) throws ParseException
    {
        // Check for sound PMOD structure
        final long size = chunk.getSize ();
        if (size % LENGTH_IMOD > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_MODULATORS"));

        for (int i = 0; i < this.instruments.size () - 1; i++)
        {
            final Sf2Instrument instrument = this.instruments.get (i);
            for (int zoneIndex = 0; zoneIndex < instrument.getZoneCount (); zoneIndex++)
                parseInstrumentZoneModulators (chunk, instrument.getZone (zoneIndex));
        }
    }


    /**
     * Parse all modulators of a instrument zone.
     *
     * @param chunk The chunk to parse
     * @param zone The zone
     */
    private static void parseInstrumentZoneModulators (final RawRIFFChunk chunk, final Sf2InstrumentZone zone)
    {
        final int firstModulator = zone.getFirstModulator ();
        final int numberOfModulators = zone.getNumberOfModulators ();

        for (int index = 0; index < numberOfModulators; index++)
        {
            final int offset = (firstModulator + index) * LENGTH_IMOD;
            final int sourceModulator = chunk.getTwoBytesAsInt (offset);
            final int destinationGenerator = chunk.getTwoBytesAsInt (offset + 2);
            final int modAmount = chunk.getTwoBytesAsInt (offset + 4);
            final int amountSourceOperand = chunk.getTwoBytesAsInt (offset + 6);
            final int transformOperand = chunk.getTwoBytesAsInt (offset + 8);
            zone.addModulator (sourceModulator, destinationGenerator, modAmount, amountSourceOperand, transformOperand);
        }
    }


    /**
     * Parse the instrument generators chunk (IGEN) and assign the parsed generators to their
     * instrument.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Error if the chunk is unsound
     */
    private void parseInstrumentGenerators (final RawRIFFChunk chunk) throws ParseException
    {
        // Check for sound IGEN structure
        final long size = chunk.getSize ();
        if (size % LENGTH_IGEN > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_GENERATORS"));

        for (int i = 0; i < this.instruments.size () - 1; i++)
        {
            final Sf2Instrument instrument = this.instruments.get (i);
            for (int zoneIndex = 0; zoneIndex < instrument.getZoneCount (); zoneIndex++)
                parseInstrumentZoneGenerators (chunk, instrument.getZone (zoneIndex));
        }
    }


    /**
     * Parse all generators of an instrument zone.
     *
     * @param chunk The chunk to parse
     * @param zone The zone
     */
    private static void parseInstrumentZoneGenerators (final RawRIFFChunk chunk, final Sf2InstrumentZone zone)
    {
        final int firstGenerator = zone.getFirstGenerator ();
        final int numberOfGenerators = zone.getNumberOfGenerators ();

        for (int index = 0; index < numberOfGenerators; index++)
        {
            final int offset = (firstGenerator + index) * LENGTH_IGEN;
            final int generator = chunk.getTwoBytesAsInt (offset);
            final int value = chunk.getTwoBytesAsInt (offset + 2);
            zone.addGenerator (generator, value);
        }

        // The first zone might be global. In that case it does not end with a sample ID
        // generator
        if (!zone.hasGenerator (Generator.SAMPLE_ID))
            zone.setGlobal (true);
    }


    /**
     * Parse all sample descriptors.
     *
     * @param chunk The chunk to parse
     * @throws ParseException Could not parse the sample headers chunk
     */
    private void parseSampleHeader (final RawRIFFChunk chunk) throws ParseException
    {
        // Check for sound IGEN structure
        final long size = chunk.getSize ();
        if (size % LENGTH_SHDR > 0)
            throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_SAMPLE_HEADER"));

        // Read all sample headers
        final List<Sf2SampleDescriptor> samples = new ArrayList<> ();
        for (int i = 0; i < size / LENGTH_SHDR; i++)
        {
            final byte [] sampleData = this.dataChunk.getSampleData ();
            if (sampleData == null)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA_CHUNK"));
            final Sf2SampleDescriptor sampleDescriptor = new Sf2SampleDescriptor (i, sampleData, this.dataChunk.getSample24Data ());
            sampleDescriptor.readHeader (i * LENGTH_SHDR, chunk);
            samples.add (sampleDescriptor);
        }

        // Assign samples to instrument zones
        for (int i = 0; i < this.instruments.size () - 1; i++)
        {
            final Sf2Instrument instrument = this.instruments.get (i);
            for (int zoneIndex = 0; zoneIndex < instrument.getZoneCount (); zoneIndex++)
            {
                final Sf2InstrumentZone zone = instrument.getZone (zoneIndex);
                final Integer value = zone.getGeneratorValue (Generator.SAMPLE_ID);
                if (value == null)
                {
                    if (zone.isGlobal ())
                        continue;
                    throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_MISSING_SAMPLE_GENERATOR"));
                }
                final int sampleIndex = value.intValue ();
                if (sampleIndex >= samples.size ())
                    throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_SAMPLE_HEADER"));
                final Sf2SampleDescriptor sampleDescriptor = samples.get (sampleIndex);
                zone.setSample (sampleDescriptor);
            }
        }
    }


    /**
     * Creates all preset data sub-chunks from the preset list.
     *
     * @throws IOException Could not write the preset chunks
     */
    public void createPresetDataChunks () throws IOException
    {
        final List<IChunk> subChunks = this.presetDataChunk.getSubChunks ();
        subChunks.clear ();

        // Update the zone offsets
        int zoneCount = 0;
        for (final Sf2Preset sf2Preset: this.presets)
        {
            sf2Preset.setFirstZoneIndex (zoneCount);
            zoneCount += sf2Preset.getZoneCount ();
        }

        // Create data chunk

        // Work around the GB limit of Java arrays
        final File tempSampleDataFile = File.createTempFile ("temp-sd", ".bin");
        tempSampleDataFile.deleteOnExit ();
        final File tempSampleData24File = File.createTempFile ("temp-sd24", ".bin");
        tempSampleData24File.deleteOnExit ();

        try (final FileOutputStream sampleOut = new FileOutputStream (tempSampleDataFile); final FileOutputStream sample24Out = new FileOutputStream (tempSampleData24File))
        {
            // Collect all sample data from the sample zones
            for (final Sf2Preset sf2Preset: this.presets)
            {
                final int numZones = sf2Preset.getZoneCount ();
                for (int presetZoneIndex = 0; presetZoneIndex < numZones; presetZoneIndex++)
                {
                    final Sf2PresetZone zone = sf2Preset.getZone (presetZoneIndex);
                    final Sf2Instrument instrument = zone.getInstrument ();
                    for (int instZoneIndex = 0; instZoneIndex < instrument.getZoneCount (); instZoneIndex++)
                    {
                        final Sf2SampleDescriptor sample = instrument.getZone (instZoneIndex).getSample ();
                        sampleOut.write (sample.getSampleData ());
                        sample24Out.write (sample.getSample24Data ());
                    }
                }
            }
        }

        // Fill data chunk
        final RawRIFFChunk sampleChunk = new RawRIFFChunk (0, RiffID.SMPL_ID.getId (), tempSampleDataFile.length ());
        sampleChunk.setData (tempSampleDataFile);
        this.dataChunk.add (sampleChunk);
        final long length24 = tempSampleData24File.length ();
        if (length24 > 0)
        {
            final RawRIFFChunk sample24Chunk = new RawRIFFChunk (0, RiffID.SF_SM24_ID.getId (), length24);
            sample24Chunk.setData (tempSampleData24File);
            this.dataChunk.add (sample24Chunk);
        }

        // Create PHDR chunk
        try (final ByteArrayOutputStream out = new ByteArrayOutputStream ())
        {
            for (final Sf2Preset sf2Preset: this.presets)
                sf2Preset.writeHeader (out);
            final byte [] data = out.toByteArray ();
            final RawRIFFChunk chunk = new RawRIFFChunk (0, RiffID.SF_PHDR_ID.getId (), data.length);
            chunk.setData (data);
            subChunks.add (chunk);
        }

        this.createPresetZonesChunk (subChunks);
    }


    /**
     * Create the preset zones chunk (PBAG) from the preset zones.
     *
     * @param subChunks Where to add the chunk
     * @throws IOException Could not write the chunk
     */
    private void createPresetZonesChunk (final List<IChunk> subChunks) throws IOException
    {
        try (final ByteArrayOutputStream pbagOut = new ByteArrayOutputStream (); final ByteArrayOutputStream pmodOut = new ByteArrayOutputStream (); final ByteArrayOutputStream pgenOut = new ByteArrayOutputStream (); final ByteArrayOutputStream instOut = new ByteArrayOutputStream (); final ByteArrayOutputStream ibagOut = new ByteArrayOutputStream (); final ByteArrayOutputStream imodOut = new ByteArrayOutputStream (); final ByteArrayOutputStream igenOut = new ByteArrayOutputStream (); final ByteArrayOutputStream shdrOut = new ByteArrayOutputStream ())
        {
            int presetGeneratorIndex = 0;
            int presetModulatorIndex = 0;
            int instGeneratorIndex = 0;
            int instModulatorIndex = 0;
            int instZoneCount = 0;

            // Ignore last final preset
            for (int presetIndex = 0; presetIndex < this.presets.size () - 1; presetIndex++)
            {
                final Sf2Preset sf2Preset = this.presets.get (presetIndex);

                for (int zoneIndex = 0; zoneIndex < sf2Preset.getZoneCount (); zoneIndex++)
                {
                    //////////////////////////////////////////////////////////////
                    // Preset data

                    final Sf2PresetZone presetZone = sf2Preset.getZone (zoneIndex);
                    StreamUtils.writeUnsigned16 (pbagOut, presetGeneratorIndex, false);
                    StreamUtils.writeUnsigned16 (pbagOut, presetModulatorIndex, false);

                    for (final Sf2Modulator modulator: presetZone.getModulators ())
                    {
                        StreamUtils.writeUnsigned16 (pmodOut, modulator.getControllerSource (), false);
                        StreamUtils.writeUnsigned16 (pmodOut, modulator.getDestinationGenerator (), false);
                        StreamUtils.writeUnsigned16 (pmodOut, modulator.getModulationAmount (), false);
                        StreamUtils.writeUnsigned16 (pmodOut, modulator.getAmountSourceOperand (), false);
                        StreamUtils.writeUnsigned16 (pmodOut, modulator.getTransformOperand (), false);
                    }

                    for (final Integer generatorID: presetZone.getGeneratorOrder ())
                    {
                        final Integer generatorValue = presetZone.getGeneratorValue (generatorID.intValue ());
                        StreamUtils.writeUnsigned16 (pgenOut, generatorID.intValue (), false);
                        StreamUtils.writeUnsigned16 (pgenOut, generatorValue.intValue (), false);
                    }

                    presetGeneratorIndex += presetZone.getNumberOfGenerators ();
                    presetModulatorIndex += presetZone.getNumberOfModulators ();

                    //////////////////////////////////////////////////////////////
                    // Instrument data

                    final Sf2Instrument instrument = presetZone.getInstrument ();
                    instrument.setFirstZoneIndex (instZoneCount);
                    instZoneCount += instrument.getZoneCount ();
                    instrument.writeHeader (instOut);

                    for (int instZoneIndex = 0; instZoneIndex < instrument.getZoneCount (); instZoneIndex++)
                    {
                        final Sf2InstrumentZone instZone = instrument.getZone (instZoneIndex);
                        StreamUtils.writeUnsigned16 (ibagOut, instGeneratorIndex, false);
                        StreamUtils.writeUnsigned16 (ibagOut, instModulatorIndex, false);

                        for (final Sf2Modulator modulator: instZone.getModulators ())
                        {
                            StreamUtils.writeUnsigned16 (imodOut, modulator.getControllerSource (), false);
                            StreamUtils.writeUnsigned16 (imodOut, modulator.getDestinationGenerator (), false);
                            StreamUtils.writeUnsigned16 (imodOut, modulator.getModulationAmount (), false);
                            StreamUtils.writeUnsigned16 (imodOut, modulator.getAmountSourceOperand (), false);
                            StreamUtils.writeUnsigned16 (imodOut, modulator.getTransformOperand (), false);
                        }

                        for (final Integer generatorID: instZone.getGeneratorOrder ())
                        {
                            final Integer generatorValue = instZone.getGeneratorValue (generatorID.intValue ());
                            StreamUtils.writeUnsigned16 (igenOut, generatorID.intValue (), false);
                            StreamUtils.writeUnsigned16 (igenOut, generatorValue.intValue (), false);
                        }

                        instGeneratorIndex += instZone.getNumberOfGenerators ();
                        instModulatorIndex += instZone.getNumberOfModulators ();

                        instZone.getSample ().writeHeader (shdrOut);
                    }
                }
            }

            // Final PBAG element
            StreamUtils.writeUnsigned16 (pbagOut, presetGeneratorIndex, false);
            StreamUtils.writeUnsigned16 (pbagOut, presetModulatorIndex, false);

            // Final PMOD element
            for (int i = 0; i < 5; i++)
                StreamUtils.writeUnsigned16 (pmodOut, 0, false);

            // Final PGEN element
            StreamUtils.writeUnsigned16 (pgenOut, 0, false);
            StreamUtils.writeUnsigned16 (pgenOut, 0, false);

            // Final INST element
            Sf2Instrument.writeLastHeader (instOut, instZoneCount);

            // Final IBAG element
            StreamUtils.writeUnsigned16 (ibagOut, instGeneratorIndex, false);
            StreamUtils.writeUnsigned16 (ibagOut, instModulatorIndex, false);

            // Final IMOD element
            for (int i = 0; i < 5; i++)
                StreamUtils.writeUnsigned16 (imodOut, 0, false);

            // Final IGEN element
            StreamUtils.writeUnsigned16 (igenOut, 0, false);
            StreamUtils.writeUnsigned16 (igenOut, 0, false);

            // Final SHDR element
            Sf2SampleDescriptor.writeLastHeader (shdrOut);

            createChunk (RiffID.SF_PBAG_ID, pbagOut, subChunks);
            createChunk (RiffID.SF_PMOD_ID, pmodOut, subChunks);
            createChunk (RiffID.SF_PGEN_ID, pgenOut, subChunks);
            createChunk (RiffID.INST_ID, instOut, subChunks);
            createChunk (RiffID.SF_IBAG_ID, ibagOut, subChunks);
            createChunk (RiffID.SF_IMOD_ID, imodOut, subChunks);
            createChunk (RiffID.SF_IGEN_ID, igenOut, subChunks);
            createChunk (RiffID.SF_SHDR_ID, shdrOut, subChunks);
        }
    }


    private static void createChunk (final RiffID riffID, final ByteArrayOutputStream out, final List<IChunk> subChunks)
    {
        final byte [] data = out.toByteArray ();
        final RawRIFFChunk chunk = new RawRIFFChunk (0, riffID.getId (), data.length);
        chunk.setData (data);
        subChunks.add (chunk);
    }


    /** {@inheritDoc} */
    @Override
    public String infoText ()
    {
        final StringBuilder sb = new StringBuilder ();
        if (this.infoChunk != null)
            sb.append (this.infoChunk.infoText ()).append ('\n');

        sb.append ("Presets:\n");
        // -1 since the last one only signals the end of the presets list
        for (int i = 0; i < this.presets.size () - 1; i++)
        {
            final Sf2Preset preset = this.presets.get (i);
            sb.append (preset.printInfo ());
        }

        if (!this.ignoredChunks.isEmpty ())
        {
            sb.append ("Ignored chunks: \n");
            for (final String chunk: this.ignoredChunks)
                sb.append (" - ").append (chunk).append ('\n');
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
        if (this.dataChunk != null)
            this.chunkStack.add (this.dataChunk);
        if (this.presetDataChunk != null)
            this.chunkStack.add (this.presetDataChunk);
    }
}
