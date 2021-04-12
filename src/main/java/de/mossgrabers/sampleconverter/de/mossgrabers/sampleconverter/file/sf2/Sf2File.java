// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.file.sf2;

import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.riff.RIFFChunk;
import de.mossgrabers.sampleconverter.file.riff.RIFFParser;
import de.mossgrabers.sampleconverter.file.riff.RIFFVisitor;
import de.mossgrabers.sampleconverter.file.riff.RiffID;
import de.mossgrabers.sampleconverter.ui.tools.Functions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Accessor to a SoundFont 2 file. A SoundFont consists of a couple of presets. A preset consists of
 * one or more instrument layers. An instrument references one or more samples. Synthesizer and
 * effect parameters can be applied on instrument and preset level.
 * <p>
 * Preset ->1:N Zones ->1:1 Instrument ->1:N Zone ->1:1 Sample.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2File
{
    /** The length of the PBAG structure. */
    private static final int          LENGTH_PBAG   = 4;
    /** The length of the PGEN structure. */
    private static final int          LENGTH_PGEN   = 4;
    /** The length of the INST structure. */
    private static final int          LENGTH_INST   = 22;
    /** The length of the IBAG structure. */
    private static final int          LENGTH_IBAG   = 4;
    /** The length of the IGEN structure. */
    private static final int          LENGTH_IGEN   = 4;
    /** The length of the SHDR structure. */
    private static final int          LENGTH_SHDR   = 46;

    private double                    version       = -1;
    private String                    soundEngine;
    private String                    compatibleBank;
    private String                    soundDataROM;
    private double                    romRevision   = -1;
    private String                    creationDate;
    private String                    soundDesigner;
    private String                    intendedProduct;
    private String                    copyright;
    private String                    comment;
    private String                    creationTool;
    private byte []                   sampleData;
    private byte []                   sample24Data;

    private final List<Sf2Preset>     presets       = new ArrayList<> ();
    private final List<Sf2Instrument> instruments   = new ArrayList<> ();
    private final Set<String>         ignoredChunks = new HashSet<> ();


    /**
     * Constructor.
     *
     * @param inputStream The input stream which provides the WAV file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public Sf2File (final InputStream inputStream) throws IOException, ParseException
    {
        this.read (inputStream);
    }


    /**
     * Constructor.
     *
     * @param sf2File The SF2 file
     * @throws IOException Could not read the file
     * @throws ParseException Error parsing the chunks
     */
    public Sf2File (final File sf2File) throws IOException, ParseException
    {
        try (final FileInputStream stream = new FileInputStream (sf2File))
        {
            this.read (stream);
        }
    }


    /**
     * Get the SoundFont specification version level to which the file complies (mandatory).
     *
     * @return The version
     */
    public double getVersion ()
    {
        return this.version;
    }


    /**
     * Get the wavetable sound engine for which the file was optimized (mandatory).
     *
     * @return The sound engine name
     */
    public String getSoundEngine ()
    {
        return this.soundEngine;
    }


    /**
     * Get the name of the SoundFont compatible bank (mandatory).
     *
     * @return The name
     */
    public String getCompatibleBank ()
    {
        return this.compatibleBank;
    }


    /**
     * Get the particular wavetable sound data ROM to which any ROM samples refer (optional).
     *
     * @return The ROM name, null if not present
     */
    public String getSoundDataROM ()
    {
        return this.soundDataROM;
    }


    /**
     * Get the particular wavetable sound data ROM revision to which any ROM samples refer
     * (optional).
     *
     * @return The revision number, -1 if not present
     */
    public double getRomRevision ()
    {
        return this.romRevision;
    }


    /**
     * Get the creation date of the SoundFont compatible bank (optional).
     *
     * @return The date, null if not present
     */
    public String getCreationDate ()
    {
        return this.creationDate;
    }


    /**
     * Get the names of any sound designers or engineers responsible for the SoundFont compatible
     * bank (optional).
     *
     * @return The names, null if not present
     */
    public String getSoundDesigner ()
    {
        return this.soundDesigner;
    }


    /**
     * Get the specific product for which the SoundFont compatible bank is intended (optional).
     *
     * @return The product, null if not present
     */
    public String getIntendedProduct ()
    {
        return this.intendedProduct;
    }


    /**
     * Get the copyright assertion string associated with the SoundFont compatible bank (optional).
     *
     * @return The copyright, null if not present
     */
    public String getCopyright ()
    {
        return this.copyright;
    }


    /**
     * Get the SoundFont compatible tools used to create and most recently modify the SoundFont
     * compatible bank (optional).
     *
     * @return The tools, null if not present
     */
    public String getCreationTool ()
    {
        return this.creationTool;
    }


    /**
     * Get the comments associated with the SoundFont compatible bank (optional).
     *
     * @return The comments, null if not present
     */
    public String getComment ()
    {
        return this.comment;
    }


    /**
     * Get the presets.
     *
     * @return The presets
     */
    public List<Sf2Preset> getPresets ()
    {
        return this.presets;
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
        riffParser.declareGroupChunk (RiffID.SF_INFO_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.declareGroupChunk (RiffID.SF_DATA_ID.getId (), RiffID.LIST_ID.getId ());
        riffParser.declareGroupChunk (RiffID.SF_PDTA_ID.getId (), RiffID.LIST_ID.getId ());

        riffParser.parse (inputStream, new Visitor (), true);

        // Check mandatory fields
        if (this.version < 0)
            throw new ParseException ("No version found in SF2 file.");
        if (this.soundEngine == null)
            throw new ParseException ("No sound engine found in SF2 file.");
        if (this.compatibleBank == null)
            throw new ParseException ("No compatible bank found in SF2 file.");
    }


    /** Visitor for traversing all chunks. */
    class Visitor implements RIFFVisitor
    {
        /** {@inheritDoc} */
        @Override
        public boolean enteringGroup (final RIFFChunk group)
        {
            // SoundFont 2 chunks use many groups...
            return true;
        }


        /** {@inheritDoc} */
        @Override
        public void enterGroup (final RIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void leaveGroup (final RIFFChunk group) throws ParseException
        {
            // Intentionally empty
        }


        /** {@inheritDoc} */
        @Override
        public void visitChunk (final RIFFChunk group, final RIFFChunk chunk) throws ParseException
        {
            final RiffID fromId = RiffID.fromId (chunk.getId ());
            switch (fromId)
            {
                ////////////////////////////////////////////
                // Info chunk and its' sub-chunks

                case SF_INFO_ID:
                    // Intentionally empty
                    break;
                case SF_IFIL_ID:
                    Sf2File.this.version = chunk.twoBytesAsInt (0) + chunk.twoBytesAsInt (2) / 100.0;
                    break;
                case SF_ISNG_ID:
                    Sf2File.this.soundEngine = chunk.getNullTerminatedString (0, "EMU8000").trim ();
                    break;
                case SF_INAM_ID:
                    Sf2File.this.compatibleBank = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_IROM_ID:
                    Sf2File.this.soundDataROM = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_IVER_ID:
                    Sf2File.this.romRevision = chunk.twoBytesAsInt (0) + chunk.twoBytesAsInt (2) / 100.0;
                    break;
                case SF_ICRD_ID:
                    Sf2File.this.creationDate = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_IENG_ID:
                    Sf2File.this.soundDesigner = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_IPRD_ID:
                    Sf2File.this.intendedProduct = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_ICOP_ID:
                    Sf2File.this.copyright = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_ICMT_ID:
                    Sf2File.this.comment = chunk.getNullTerminatedString (0, "").trim ();
                    break;
                case SF_ISFT_ID:
                    Sf2File.this.creationTool = chunk.getNullTerminatedString (0, "").trim ();
                    break;

                ////////////////////////////////////////////
                // Data chunk and its' sub-chunks

                case SF_DATA_ID:
                    // Intentionally empty
                    break;
                case SF_SMPL_ID:
                    Sf2File.this.sampleData = chunk.getData ();
                    break;
                case SF_SM24_ID:
                    Sf2File.this.sample24Data = chunk.getData ();
                    break;

                ////////////////////////////////////////////
                // Articulation chunk and its' sub-chunks

                case SF_PDTA_ID:
                    // Intentionally empty
                    break;
                case SF_PHDR_ID:
                    final long length = chunk.getSize ();
                    if (length % Sf2Preset.LENGTH_PRESET_HEADER > 0)
                        throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET"));
                    final long presetCount = length / Sf2Preset.LENGTH_PRESET_HEADER;
                    for (int i = 0; i < presetCount; i++)
                    {
                        final Sf2Preset preset = new Sf2Preset ();
                        preset.readHeader (i * Sf2Preset.LENGTH_PRESET_HEADER, chunk);
                        Sf2File.this.presets.add (preset);
                    }
                    break;
                case SF_PBAG_ID:
                    this.parsePresetZones (chunk);
                    break;
                case SF_PGEN_ID:
                    this.parsePresetGenerators (chunk);
                    break;
                case SF_PMOD_ID:
                    // Modulators are currently not supported
                    break;
                case SF_INST_ID:
                    this.parseInstruments (chunk);
                    break;
                case SF_IBAG_ID:
                    this.parseInstrumentZones (chunk);
                    break;
                case SF_IMOD_ID:
                    // Modulators are currently not supported
                    break;
                case SF_IGEN_ID:
                    this.parseInstrumentGenerators (chunk);
                    break;
                case SF_SHDR_ID:
                    this.parseSampleHeader (chunk);
                    break;

                default:
                    // Ignore other chunks
                    Sf2File.this.ignoredChunks.add (RiffID.toASCII (chunk.getId ()));
                    break;
            }
        }


        /**
         * Parse the preset zones chunk (PBAG) and assign the parsed zones to their preset.
         *
         * @param chunk The chunk to parse
         * @throws ParseException Error if the chunk is unsound
         */
        private void parsePresetZones (final RIFFChunk chunk) throws ParseException
        {
            final long len = chunk.getSize ();
            if (len % LENGTH_PBAG > 0)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_ZONE"));

            try
            {
                for (int i = 0; i < Sf2File.this.presets.size () - 1; i++)
                {
                    final Sf2Preset preset = Sf2File.this.presets.get (i);
                    final Sf2Preset nextPreset = Sf2File.this.presets.get (i + 1);

                    // Get the pointer into the PGEN structure
                    final int firstZoneIndex = preset.getFirstZoneIndex ();
                    final int numberOfZones = nextPreset.getFirstZoneIndex () - firstZoneIndex;

                    for (int zoneCounter = 0; zoneCounter < numberOfZones; zoneCounter++)
                    {
                        final int offset = (firstZoneIndex + zoneCounter) * LENGTH_PBAG;
                        final int generatorIndex = chunk.twoBytesAsInt (offset);
                        final int nextGeneratorIndex = chunk.twoBytesAsInt ((firstZoneIndex + zoneCounter + 1) * LENGTH_PBAG);
                        preset.addZone (new Sf2PresetZone (generatorIndex, nextGeneratorIndex - generatorIndex, chunk.twoBytesAsInt (offset + 2)));
                    }
                }
            }
            catch (final RuntimeException ex)
            {
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_ZONE"), ex);
            }
        }


        /**
         * Parse the preset generators chunk (PGEN) and assign the parsed generators to their
         * preset.
         *
         * @param chunk The chunk to parse
         * @throws ParseException Error if the chunk is unsound
         */
        private void parsePresetGenerators (final RIFFChunk chunk) throws ParseException
        {
            // Check for sound PGEN structure
            final long size = chunk.getSize ();
            if (size % LENGTH_PGEN > 0)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_PRESET_GENERATORS"));

            for (int i = 0; i < Sf2File.this.presets.size () - 1; i++)
            {
                final Sf2Preset preset = Sf2File.this.presets.get (i);
                for (int zoneIndex = 0; zoneIndex < preset.getZoneCount (); zoneIndex++)
                    this.parsePresetZoneGenerators (chunk, preset.getZone (zoneIndex));
            }
        }


        /**
         * Parse all generators of a preset zone.
         *
         * @param chunk The chunk to parse
         * @param zone The zone
         */
        private void parsePresetZoneGenerators (final RIFFChunk chunk, final Sf2PresetZone zone)
        {
            final int firstGenerator = zone.getFirstGenerator ();
            final int numberOfGenerators = zone.getNumberOfGenerators ();

            for (int index = firstGenerator; index < firstGenerator + numberOfGenerators; index++)
            {
                final int generator = chunk.twoBytesAsInt (LENGTH_PGEN * index);
                final int value = chunk.twoBytesAsInt (LENGTH_PGEN * index + 2);
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
        private void parseInstruments (final RIFFChunk chunk) throws ParseException
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
                Sf2File.this.instruments.add (instrument);
            }

            // Assign the instruments to the preset zones
            for (int i = 0; i < Sf2File.this.presets.size () - 1; i++)
            {
                final Sf2Preset preset = Sf2File.this.presets.get (i);
                for (int zoneIndex = 0; zoneIndex < preset.getZoneCount (); zoneIndex++)
                {
                    final Sf2PresetZone zone = preset.getZone (zoneIndex);
                    zone.applyInstrument (Sf2File.this.instruments);
                }
            }
        }


        /**
         * Parse the instrument zones chunk (IBAG) and assign the parsed zones to their instrument.
         *
         * @param chunk The chunk to parse
         * @throws ParseException Error if the chunk is unsound
         */
        private void parseInstrumentZones (final RIFFChunk chunk) throws ParseException
        {
            final long length = chunk.getSize ();
            if (length % LENGTH_IBAG > 0)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_ZONE"));

            try
            {
                for (int i = 0; i < Sf2File.this.instruments.size () - 1; i++)
                {
                    final Sf2Instrument instrument = Sf2File.this.instruments.get (i);
                    final Sf2Instrument nextInstrument = Sf2File.this.instruments.get (i + 1);

                    // Get the pointer into the IGEN structure
                    final int firstZoneIndex = instrument.getFirstZoneIndex ();
                    final int numberOfZones = nextInstrument.getFirstZoneIndex () - firstZoneIndex;

                    for (int zoneCounter = 0; zoneCounter < numberOfZones; zoneCounter++)
                    {
                        final int offset = (firstZoneIndex + zoneCounter) * LENGTH_IBAG;
                        final int generatorIndex = chunk.twoBytesAsInt (offset);
                        final int nextGeneratorIndex = chunk.twoBytesAsInt ((firstZoneIndex + zoneCounter + 1) * LENGTH_IBAG);
                        instrument.addZone (new Sf2InstrumentZone (generatorIndex, nextGeneratorIndex - generatorIndex, chunk.twoBytesAsInt (offset + 2)));
                    }
                }
            }
            catch (final RuntimeException ex)
            {
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_ZONE"), ex);
            }
        }


        /**
         * Parse the instrument generators chunk (IGEN) and assign the parsed generators to their
         * instrument.
         *
         * @param chunk The chunk to parse
         * @throws ParseException Error if the chunk is unsound
         */
        private void parseInstrumentGenerators (final RIFFChunk chunk) throws ParseException
        {
            // Check for sound IGEN structure
            final long size = chunk.getSize ();
            if (size % LENGTH_IGEN > 0)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_INSTRUMENT_GENERATORS"));

            for (int i = 0; i < Sf2File.this.instruments.size () - 1; i++)
            {
                final Sf2Instrument instrument = Sf2File.this.instruments.get (i);
                for (int zoneIndex = 0; zoneIndex < instrument.getZoneCount (); zoneIndex++)
                    this.parseInstrumentZoneGenerators (chunk, instrument.getZone (zoneIndex));
            }
        }


        /**
         * Parse all generators of an instrument zone.
         *
         * @param chunk The chunk to parse
         * @param zone The zone
         */
        private void parseInstrumentZoneGenerators (final RIFFChunk chunk, final Sf2InstrumentZone zone)
        {
            final int firstGenerator = zone.getFirstGenerator ();
            final int numberOfGenerators = zone.getNumberOfGenerators ();

            for (int index = firstGenerator; index < firstGenerator + numberOfGenerators; index++)
            {
                final int generator = chunk.twoBytesAsInt (LENGTH_IGEN * index);
                final int value = chunk.twoBytesAsInt (LENGTH_IGEN * index + 2);
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
        private void parseSampleHeader (final RIFFChunk chunk) throws ParseException
        {
            // Check for sound IGEN structure
            final long size = chunk.getSize ();
            if (size % LENGTH_SHDR > 0)
                throw new ParseException (Functions.getMessage ("IDS_NOTIFY_ERR_BROKEN_SAMPLE_HEADER"));

            // Read all sample headers
            final List<Sf2SampleDescriptor> samples = new ArrayList<> ();
            for (int i = 0; i < size / LENGTH_SHDR; i++)
            {
                final Sf2SampleDescriptor sampleDescriptor = new Sf2SampleDescriptor ();
                sampleDescriptor.readHeader (i * LENGTH_SHDR, chunk);
                samples.add (sampleDescriptor);
            }

            // Assign samples to instrument zones
            for (int i = 0; i < Sf2File.this.instruments.size () - 1; i++)
            {
                final Sf2Instrument instrument = Sf2File.this.instruments.get (i);
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
    }


    /**
     * Format all parameters into a string.
     *
     * @return The formatted string
     */
    public String printInfo ()
    {
        final StringBuilder sb = new StringBuilder ();
        sb.append ("Version: ").append (this.getVersion ()).append ('\n');
        sb.append ("Sound Engine: ").append (this.getSoundEngine ()).append ('\n');
        sb.append ("Compatible Bank: ").append (this.getCompatibleBank ()).append ('\n');
        if (this.soundDataROM != null)
            sb.append ("Sound Data ROM: ").append (this.soundDataROM).append ('\n');
        if (this.romRevision >= 0)
            sb.append ("Sound Data ROM Revision: ").append (this.romRevision).append ('\n');
        if (this.creationDate != null)
            sb.append ("Creation Date: ").append (this.creationDate).append ('\n');
        if (this.soundDesigner != null)
            sb.append ("Sound Designer: ").append (this.soundDesigner).append ('\n');
        if (this.intendedProduct != null)
            sb.append ("Intended Product: ").append (this.intendedProduct).append ('\n');
        if (this.copyright != null)
            sb.append ("Copyright: ").append (this.copyright).append ('\n');
        if (this.comment != null)
            sb.append ("Comment: ").append (this.comment).append ('\n');
        if (this.creationTool != null)
            sb.append ("Creation Tool: ").append (this.creationTool).append ('\n');

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
}
