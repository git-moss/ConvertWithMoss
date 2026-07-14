// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.INotifier;


/**
 * An EXS24 file.
 *
 * @author Jürgen Moßgraber
 */
public class EXS24File
{
    private EXS24Instrument                instrument = null;
    private final List<EXS24Zone>          zones      = new ArrayList<> ();
    private final List<EXS24Sample>        samples    = new ArrayList<> ();
    private final Map<Integer, EXS24Group> groups     = new TreeMap<> ();
    private final EXS24Parameters          parameters = new EXS24Parameters ();
    private final List<EXSModulator>       modulators = new ArrayList<> ();
    private final INotifier                notifier;


    /**
     * Constructor.
     *
     * @param notifier The notifier for logging, may be null
     */
    public EXS24File (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Get the instrument object.
     *
     * @return The instrument
     */
    public EXS24Instrument getInstrument ()
    {
        return this.instrument;
    }


    /**
     * Get the zone objects.
     *
     * @return The zones
     */
    public List<EXS24Zone> getZones ()
    {
        return this.zones;
    }


    /**
     * Get the samples objects.
     *
     * @return The samples
     */
    public List<EXS24Sample> getSamples ()
    {
        return this.samples;
    }


    /**
     * Get the groups objects.
     *
     * @return The groups
     */
    public Map<Integer, EXS24Group> getGroups ()
    {
        return this.groups;
    }


    /**
     * Get the parameters objects.
     *
     * @return The parameters
     */
    public EXS24Parameters getParameters ()
    {
        return this.parameters;
    }


    /**
     * Read the EXS24 file.
     *
     * @param in The input stream from which to read the file
     * @throws IOException Could not read the file
     */
    public void read (final InputStream in) throws IOException
    {
        while (in.available () > 0)
        {
            final EXS24Block block = new EXS24Block (in);
            switch (block.type)
            {
                case EXS24Block.TYPE_INSTRUMENT:
                    this.setInstrument (block);
                    break;

                case EXS24Block.TYPE_ZONE:
                    this.addZone (block);
                    break;

                case EXS24Block.TYPE_GROUP:
                    this.addGroup (block);
                    break;

                case EXS24Block.TYPE_SAMPLE:
                    this.addSample (block);
                    break;

                case EXS24Block.TYPE_PARAMS:
                    this.setParameters (block);
                    break;

                case EXS24Block.TYPE_UNKNOWN:
                    // No idea what that is but it is 4 bytes long and they are always 0
                    break;

                case EXS24Block.TYPE_BPLIST_SAMPLER_LAYOUT:
                    // A BPLIST with the layout configuration of Sampler
                    break;

                case EXS24Block.TYPE_BPLIST_MACOS_PLIST:
                    // This contains a MacOS PLIST structure in byte format with file information
                    // about the sample files (could be read with dd.plist library)
                    break;

                default:
                    if (this.notifier != null)
                        this.notifier.logError ("IDS_EXS_UNKNOWN_EXS_BLOCK_TYPE", block.name, Integer.toString (block.type), Integer.toString (block.content.length));
                    break;
            }
        }
    }


    /**
     * Writes the EXS24 file to the given output stream.
     *
     * @param out The stream to write to
     * @param isBigEndian Write big-endian if true
     * @throws IOException Could not write the file
     */
    public void write (final OutputStream out, final boolean isBigEndian) throws IOException
    {
        this.instrument.write (isBigEndian).write (out);

        // Write all group zones
        for (int i = 0; i < this.zones.size (); i++)
        {
            final EXS24Block zoneBlock = this.zones.get (i).write (isBigEndian);
            zoneBlock.index = i;
            zoneBlock.write (out);
        }

        // Write all group blocks
        for (final Map.Entry<Integer, EXS24Group> e: this.groups.entrySet ())
        {
            final EXS24Block groupBlock = e.getValue ().write (isBigEndian);
            groupBlock.index = e.getKey ().intValue ();
            groupBlock.write (out);
        }

        // Write all sample blocks
        for (int i = 0; i < this.samples.size (); i++)
        {
            final EXS24Sample exsSample = this.samples.get (i);
            final EXS24Block sampleBlock = exsSample.write (isBigEndian);
            sampleBlock.index = i;
            sampleBlock.write (out);
        }

        this.applyModulationMatrix ();

        // Write parameters
        this.parameters.write (isBigEndian).write (out);
    }


    /**
     * Add a group.
     *
     * @param group The group to add
     */
    public void addGroup (final EXS24Group group)
    {
        final int groupIndex = this.groups.size ();
        this.groups.put (Integer.valueOf (groupIndex), group);
    }


    /**
     * Creates a zone.
     *
     * @return The created zone
     */
    public EXS24Zone createZone ()
    {
        final EXS24Zone zone = new EXS24Zone ();
        this.zones.add (zone);
        zone.groupIndex = this.groups.size () - 1;
        return zone;
    }


    /**
     * Creates a sample.
     *
     * @param zone The zone to which to add the sample
     * @return The created sample
     */
    public EXS24Sample createSample (final EXS24Zone zone)
    {
        final EXS24Sample sample = new EXS24Sample ();
        this.samples.add (sample);
        zone.sampleIndex = this.samples.size () - 1;
        return sample;
    }


    /**
     * Creates an instrument.
     *
     * @param name The name of the instrument
     */
    public void createInstrument (final String name)
    {
        this.instrument = new EXS24Instrument ();
        this.instrument.name = name;
        this.instrument.numZoneBlocks = this.zones.size ();
        this.instrument.numGroupBlocks = this.groups.size ();
        this.instrument.numSampleBlocks = this.samples.size ();
        this.instrument.numParameterBlocks = 1;
    }


    /**
     * Add a parameter.
     *
     * @param parameterID The ID of the parameter
     * @param value The value of the parameter
     */
    public void addParameter (final int parameterID, final int value)
    {
        this.parameters.put (parameterID, value);
    }


    private void setInstrument (final EXS24Block block) throws IOException
    {
        this.instrument = new EXS24Instrument (block);
    }


    private void addZone (final EXS24Block block) throws IOException
    {
        this.zones.add (new EXS24Zone (block));
    }


    private void addGroup (final EXS24Block block) throws IOException
    {
        this.addGroup (new EXS24Group (block));
    }


    private void addSample (final EXS24Block block) throws IOException
    {
        this.samples.add (new EXS24Sample (block));
    }


    private void setParameters (final EXS24Block block) throws IOException
    {
        this.parameters.read (block);

        this.readModulationMatrix ();
    }


    /**
     * Add a modulator.
     * 
     * @param modulator The modulator to add
     */
    public void addModulator (final EXSModulator modulator)
    {
        this.modulators.add (modulator);
    }


    /**
     * Get a modulator which has the given source and destination.
     * 
     * @param source The ID of the source
     * @param destination The ID of the destination
     * @return The modulator if present
     */
    public Optional<EXSModulator> getModulator (final int source, final int destination)
    {
        for (final EXSModulator modulator: this.modulators)
        {
            if (modulator.source == source && modulator.destination == destination)
                return Optional.of (modulator);
        }
        return Optional.empty ();
    }


    private List<EXSModulator> readModulationMatrix ()
    {
        this.modulators.clear ();
        for (int i = 0; i < 11; i++)
        {
            int offset = i * 6;
            if (i == 10)
                offset += 178;

            final Integer modSource = this.parameters.get (EXS24Parameters.MOD1_SOURCE + offset);
            final Integer modDestination = this.parameters.get (EXS24Parameters.MOD1_DESTINATION + offset);
            if (modSource == null || modDestination == null)
                continue;
            final Integer modBypass = this.parameters.get (EXS24Parameters.MOD1_BYPASS + offset);
            if (modBypass == null || modBypass.intValue () == 0)
            {
                final EXSModulator modulator = new EXSModulator ();
                modulator.source = modSource.intValue ();
                modulator.destination = modDestination.intValue ();
                final Integer modValueLow = this.parameters.get (EXS24Parameters.MOD1_AMOUNT_LOW + offset);
                final Integer modValueHigh = this.parameters.get (EXS24Parameters.MOD1_AMOUNT_HIGH + offset);
                modulator.lowValue = modValueLow == null ? 0 : modValueLow.intValue ();
                modulator.highValue = modValueHigh == null ? 0 : modValueHigh.intValue ();
                this.modulators.add (modulator);
            }
        }

        return this.modulators;
    }


    private void applyModulationMatrix ()
    {
        for (int i = 0; i < Math.min (11, this.modulators.size ()); i++)
        {
            int offset = i * 6;
            if (i == 10)
                offset += 178;

            final EXSModulator modulator = this.modulators.get (i);
            this.parameters.put (EXS24Parameters.MOD1_SOURCE + offset, modulator.source);
            this.parameters.put (EXS24Parameters.MOD1_DESTINATION + offset, modulator.destination);
            this.parameters.put (EXS24Parameters.MOD1_BYPASS + offset, 0);
            this.parameters.put (EXS24Parameters.MOD1_AMOUNT_LOW + offset, modulator.lowValue);
            this.parameters.put (EXS24Parameters.MOD1_AMOUNT_HIGH + offset, modulator.highValue);
        }
    }
}
