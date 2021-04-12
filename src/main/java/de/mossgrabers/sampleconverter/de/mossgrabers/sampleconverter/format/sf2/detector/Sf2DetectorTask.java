// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sf2.detector;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.sf2.Generator;
import de.mossgrabers.sampleconverter.file.sf2.Sf2File;
import de.mossgrabers.sampleconverter.file.sf2.Sf2Instrument;
import de.mossgrabers.sampleconverter.file.sf2.Sf2InstrumentZone;
import de.mossgrabers.sampleconverter.file.sf2.Sf2Preset;
import de.mossgrabers.sampleconverter.file.sf2.Sf2PresetZone;
import de.mossgrabers.sampleconverter.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.sampleconverter.format.sf2.Sf2SampleMetadata;
import de.mossgrabers.sampleconverter.util.Pair;
import de.mossgrabers.sampleconverter.util.TagDetector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * Detects recursively SoundFont 2 files in folders. Files must end with <i>.sf2</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2DetectorTask extends AbstractDetectorTask
{
    /** {@inheritDoc} */
    @Override
    protected void detect (final File folder)
    {
        if (this.consumer.isEmpty ())
            return;

        // Detect all sf2 files in the folder
        this.log ("IDS_NOTIFY_ANALYZING", folder.getAbsolutePath ());
        if (this.waitForDelivery ())
            return;

        final File [] sf2Files = folder.listFiles ( (parent, name) -> {

            if (this.isCancelled ())
                return false;

            final File f = new File (parent, name);
            if (f.isDirectory ())
            {
                this.detect (f);
                return false;
            }
            return name.toLowerCase (Locale.US).endsWith (".sf2");
        });
        if (sf2Files.length == 0)
            return;

        for (final File sf2File: sf2Files)
        {
            this.log ("IDS_NOTIFY_ANALYZING", sf2File.getAbsolutePath ());

            if (this.waitForDelivery ())
                break;

            for (final IMultisampleSource multisample: this.readFile (sf2File))
            {
                if (this.waitForDelivery ())
                    break;

                this.consumer.get ().accept (multisample);

                if (this.isCancelled ())
                    return;
            }
        }
    }


    /**
     * Read and parse the given SoundFont 2 file.
     *
     * @param sourceFile The file to process
     * @return The parsed multisample information
     */
    private List<IMultisampleSource> readFile (final File sourceFile)
    {
        try
        {
            final Sf2File sf2File = new Sf2File (sourceFile);

            // TODO remove
            // this.notifier.get ().notify (sf2File.printInfo ());

            final String name = getNameWithoutType (sourceFile);
            final String [] parts = createPathParts (sourceFile.getParentFile (), this.sourceFolder.get (), name);
            return this.parseSF2File (sourceFile, sf2File, parts);
        }
        catch (final IOException | ParseException ex)
        {
            this.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Create multisample sources for all presets found in the SF2 file.
     *
     * @param sourceFile The SF2 source file
     * @param sf2File The parsed SF2 file
     * @param parts The path parts
     * @return The multisample sources
     */
    private List<IMultisampleSource> parseSF2File (final File sourceFile, final Sf2File sf2File, final String [] parts)
    {
        final List<IMultisampleSource> multisamples = new ArrayList<> ();

        final List<Sf2Preset> presets = sf2File.getPresets ();
        // -1 since the last one only signals the end of the presets list
        for (int i = 0; i < presets.size () - 1; i++)
        {
            final Sf2Preset preset = presets.get (i);

            final String mappingName = this.subtractPaths (this.sourceFolder, sourceFile) + " : " + preset.getName ();
            final MultisampleSource source = new MultisampleSource (sourceFile, parts, preset.getName (), mappingName);
            source.setCreator (sf2File.getSoundDesigner ());
            source.setCategory (TagDetector.detectCategory (parts));
            source.setKeywords (TagDetector.detectKeywords (parts));
            source.setDescription (createDescription (sf2File));

            final GeneratorHierarchy generators = new GeneratorHierarchy ();

            // Create the layers
            final List<IVelocityLayer> layers = new ArrayList<> ();
            for (int presetZoneIndex = 0; presetZoneIndex < preset.getZoneCount (); presetZoneIndex++)
            {
                final Sf2PresetZone zone = preset.getZone (presetZoneIndex);
                if (zone.isGlobal ())
                {
                    generators.setPresetZoneGlobalGenerators (zone.getGenerators ());
                    continue;
                }
                generators.setPresetZoneGenerators (zone.getGenerators ());

                final Sf2Instrument instrument = zone.getInstrument ();
                final VelocityLayer layer = new VelocityLayer (instrument.getName ());

                // TODO combine mono to stereo depending on sample type and/or panning (check for
                // same sample and loop length as well as sample rate!)

                for (int instrumentZoneIndex = 0; instrumentZoneIndex < instrument.getZoneCount (); instrumentZoneIndex++)
                {
                    final Sf2InstrumentZone instrZone = instrument.getZone (instrumentZoneIndex);
                    if (instrZone.isGlobal ())
                    {
                        generators.setInstrumentZoneGlobalGenerators (instrZone.getGenerators ());
                        continue;
                    }
                    generators.setInstrumentZoneGenerators (instrZone.getGenerators ());
                    layer.addSampleMetadata (createSampleMetadata (instrZone.getSample (), generators));
                }

                layers.add (layer);
            }

            source.setVelocityLayers (layers);

            multisamples.add (source);
        }

        return multisamples;
    }


    /**
     * Create a sample metadata description.
     *
     * @param sample The source sample
     * @param generators All hierarchical generator values
     * @return The sample metadata
     */
    private static Sf2SampleMetadata createSampleMetadata (final Sf2SampleDescriptor sample, final GeneratorHierarchy generators)
    {
        final Sf2SampleMetadata sampleMetadata = new Sf2SampleMetadata (sample);

        // Set the pitch
        final int overridingRootKey = generators.getUnsignedValue (Generator.OVERRIDING_ROOT_KEY).intValue ();
        int pitch = overridingRootKey < 0 ? sample.getOriginalPitch () : overridingRootKey;
        pitch += generators.getSignedValue (Generator.COARSE_TUNE).intValue ();
        sampleMetadata.setKeyRoot (pitch);
        final int fineTune = generators.getSignedValue (Generator.FINE_TUNE).intValue ();
        sampleMetadata.setTune (sample.getPitchCorrection () + (double) fineTune);
        final int scaleTuning = generators.getSignedValue (Generator.SCALE_TUNE).intValue ();
        sampleMetadata.setKeyTracking (Math.min (100, Math.max (0, scaleTuning)) / 100.0);

        // Set the key range
        final Pair<Integer, Integer> keyRangeValue = generators.getRangeValue (Generator.KEY_RANGE);
        sampleMetadata.setKeyLow (keyRangeValue.getKey ().intValue ());
        sampleMetadata.setKeyHigh (keyRangeValue.getValue ().intValue ());

        // Set the velocity range
        final Pair<Integer, Integer> velRangeValue = generators.getRangeValue (Generator.VELOCITY_RANGE);
        sampleMetadata.setVelocityLow (velRangeValue.getKey ().intValue ());
        sampleMetadata.setVelocityHigh (velRangeValue.getValue ().intValue ());

        // Set play range
        sampleMetadata.setStart (0);
        final long sampleStart = sample.getStart ();
        sampleMetadata.setStop ((int) (sample.getEnd () - sampleStart));

        // Set loop, if any
        if ((generators.getUnsignedValue (Generator.SAMPLE_MODES).intValue () & 1) > 0)
        {
            final SampleLoop sampleLoop = new SampleLoop ();
            sampleLoop.setStart ((int) (sample.getStartloop () - sampleStart));
            sampleLoop.setEnd ((int) (sample.getEndloop () - sampleStart));
            sampleMetadata.addLoop (sampleLoop);
        }

        return sampleMetadata;
    }


    /**
     * Create the description field from several single fields which do not translate to other
     * formats.
     *
     * @param sf2File The source file
     * @return The description text
     */
    private static String createDescription (final Sf2File sf2File)
    {
        final StringBuilder sb = new StringBuilder ();
        final String creationDate = sf2File.getCreationDate ();
        if (creationDate != null)
            sb.append ("Creation Date: ").append (creationDate).append ('\n');
        final String copyright = sf2File.getCopyright ();
        if (copyright != null)
            sb.append ("Copyright: ").append (copyright).append ('\n');
        final String comment = sf2File.getComment ();
        if (comment != null)
            sb.append (comment).append ('\n');
        final String creationTool = sf2File.getCreationTool ();
        if (creationTool != null)
            sb.append ("Creation Tool: ").append (creationTool).append ('\n');
        return sb.toString ();
    }
}
