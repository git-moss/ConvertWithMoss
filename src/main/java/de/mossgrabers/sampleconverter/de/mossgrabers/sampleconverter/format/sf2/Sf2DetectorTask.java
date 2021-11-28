// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.sf2;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.exception.ParseException;
import de.mossgrabers.sampleconverter.file.FileUtils;
import de.mossgrabers.sampleconverter.file.sf2.Generator;
import de.mossgrabers.sampleconverter.file.sf2.Sf2File;
import de.mossgrabers.sampleconverter.file.sf2.Sf2Instrument;
import de.mossgrabers.sampleconverter.file.sf2.Sf2InstrumentZone;
import de.mossgrabers.sampleconverter.file.sf2.Sf2Preset;
import de.mossgrabers.sampleconverter.file.sf2.Sf2PresetZone;
import de.mossgrabers.sampleconverter.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.sampleconverter.util.Pair;
import de.mossgrabers.sampleconverter.util.TagDetector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;


/**
 * Detects recursively SoundFont 2 files in folders. Files must end with <i>.sf2</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class Sf2DetectorTask extends AbstractDetectorTask
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    public Sf2DetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, null, ".sf2");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File sourceFile)
    {
        try
        {
            final Sf2File sf2File = new Sf2File (sourceFile);
            final String name = FileUtils.getNameWithoutType (sourceFile);
            final String [] parts = createPathParts (sourceFile.getParentFile (), this.sourceFolder, name);
            return this.parseSF2File (sourceFile, sf2File, parts);
        }
        catch (final IOException | ParseException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
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

            source.setVelocityLayers (this.combineToStereo (layers));

            multisamples.add (source);
        }

        return multisamples;
    }


    /**
     * SF2 contains only mono files. Combine them to stereo, if setup as split-stereo or (only)
     * panned left/right. If it is a pure mono file (not panned) leave it as it is.
     *
     * @param layers The layers which contain the samples to combine
     * @return The layers with combined samples for convenience
     */
    private List<IVelocityLayer> combineToStereo (final List<IVelocityLayer> layers)
    {
        for (final IVelocityLayer layer: layers)
        {
            final List<ISampleMetadata> sampleMetadataOfLayer = layer.getSampleMetadata ();

            final int initialCapacity = sampleMetadataOfLayer.size () / 2;
            final List<ISampleMetadata> resultSamples = new ArrayList<> (initialCapacity);
            final List<Sf2SampleMetadata> leftSamples = new ArrayList<> (initialCapacity);
            final List<Sf2SampleMetadata> rightSamples = new ArrayList<> (initialCapacity);
            final List<Sf2SampleMetadata> panLeftSamples = new ArrayList<> (initialCapacity);
            final List<Sf2SampleMetadata> panRightSamples = new ArrayList<> (initialCapacity);

            for (final ISampleMetadata sampleMetadata: sampleMetadataOfLayer)
            {
                final Sf2SampleMetadata sf2SampleMetadata = (Sf2SampleMetadata) sampleMetadata;
                final Sf2SampleDescriptor sample = sf2SampleMetadata.getSample ();

                // Store left and right samples in different lists first
                switch (sample.getSampleType ())
                {
                    case Sf2SampleDescriptor.LEFT:
                        leftSamples.add (sf2SampleMetadata);
                        break;

                    case Sf2SampleDescriptor.RIGHT:
                        rightSamples.add (sf2SampleMetadata);
                        break;

                    default:
                    case Sf2SampleDescriptor.MONO:
                        final int panorama = sf2SampleMetadata.getPanorama ();
                        if (panorama == 0)
                            resultSamples.add (sampleMetadata);
                        else if (panorama < 0)
                            panLeftSamples.add (sf2SampleMetadata);
                        else
                            panRightSamples.add (sf2SampleMetadata);
                        break;
                }
            }

            if (leftSamples.size () != rightSamples.size ())
                this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_NUMBER_LEFT_RIGHT", Integer.toString (leftSamples.size ()), Integer.toString (rightSamples.size ()));

            resultSamples.addAll (this.combineLinkedSamples (leftSamples, rightSamples));
            resultSamples.addAll (this.combinePanoramaSamples (panLeftSamples, panRightSamples));

            layer.setSampleMetadata (resultSamples);
        }

        return layers;
    }


    /**
     * Match the left and right hand side samples. The left hand side is linked to the right hand
     * side via an index.
     *
     * @param leftSamples The left hand side mono samples
     * @param rightSamples The right hand side mono samples
     * @return The stereo combined result samples
     */
    private List<ISampleMetadata> combineLinkedSamples (final List<Sf2SampleMetadata> leftSamples, final List<Sf2SampleMetadata> rightSamples)
    {
        final List<ISampleMetadata> resultSamples = new ArrayList<> ();

        for (final Sf2SampleMetadata leftSample: leftSamples)
        {
            final int rightSampleIndex = leftSample.getSample ().getLinkedSample ();

            Sf2SampleMetadata rightSample;
            boolean found = false;
            for (int i = 0; i < rightSamples.size (); i++)
            {
                rightSample = rightSamples.get (i);
                final Sf2SampleDescriptor sample = rightSample.getSample ();
                // Match via the linked index
                if (sample.getSampleIndex () == rightSampleIndex)
                {
                    if (this.compareSampleFormat (leftSample, rightSample))
                    {
                        // Store the matching right side sample with the left side one
                        leftSample.setRightSample (sample);
                        resultSamples.add (leftSample);
                        rightSamples.remove (i);
                        found = true;
                    }
                    break;
                }
            }
            // No match found, keep the left sample
            if (!found)
                resultSamples.add (leftSample);
        }

        // Add all unmatched right samples
        if (!rightSamples.isEmpty ())
            resultSamples.addAll (rightSamples);

        return resultSamples;
    }


    /**
     * Match the left and right hand side samples. The left and right hand side are identified by
     * their panning, key- and velocity-range.
     *
     * @param panLeftSamples The left hand side mono samples
     * @param panRightSamples The right hand side mono samples
     * @return The stereo combined result samples
     */
    private List<ISampleMetadata> combinePanoramaSamples (final List<Sf2SampleMetadata> panLeftSamples, final List<Sf2SampleMetadata> panRightSamples)
    {
        final List<ISampleMetadata> resultSamples = new ArrayList<> ();

        for (final Sf2SampleMetadata panLeftSample: panLeftSamples)
        {
            final int keyLow = panLeftSample.getKeyLow ();
            final int keyHigh = panLeftSample.getKeyHigh ();
            final int velocityLow = panLeftSample.getVelocityLow ();
            final int velocityHigh = panLeftSample.getVelocityHigh ();

            Sf2SampleMetadata panRightSample;
            boolean found = false;
            for (int i = 0; i < panRightSamples.size (); i++)
            {
                panRightSample = panRightSamples.get (i);
                // Match by the key and velocity range
                if (keyLow == panRightSample.getKeyLow () && keyHigh == panRightSample.getKeyHigh () && velocityLow == panRightSample.getVelocityLow () && velocityHigh == panRightSample.getVelocityHigh ())
                {
                    if (this.compareSampleFormat (panLeftSample, panRightSample))
                    {
                        // Store the matching right side sample with the left side one
                        panLeftSample.setRightSample (panRightSample.getSample ());
                        resultSamples.add (panLeftSample);
                        panRightSamples.remove (i);
                        found = true;
                    }
                    break;
                }
            }
            // No match found, keep the left sample
            if (!found)
                resultSamples.add (panLeftSample);
        }

        // Add all unmatched right samples
        if (!panRightSamples.isEmpty ())
            resultSamples.addAll (panRightSamples);

        return resultSamples;
    }


    private boolean compareSampleFormat (final Sf2SampleMetadata leftSample, final Sf2SampleMetadata rightSample)
    {
        final Sf2SampleDescriptor left = leftSample.getSample ();
        final Sf2SampleDescriptor right = rightSample.getSample ();

        if (left.getOriginalPitch () != right.getOriginalPitch () || left.getPitchCorrection () != right.getPitchCorrection ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_PITCH", left.getName (), right.getName ());
            return false;
        }

        if (left.getSampleRate () != right.getSampleRate ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_RATE", left.getName (), right.getName ());
            return false;
        }

        // Loops must have the same start and length
        final long leftStart = left.getStartloop () - left.getStart ();
        final long rightStart = right.getStartloop () - right.getStart ();
        final long leftLoopLength = left.getEndloop () - left.getStartloop ();
        final long rightLoopLength = right.getEndloop () - right.getStartloop ();
        if (!leftSample.getLoops ().isEmpty () && (leftStart != rightStart || leftLoopLength != rightLoopLength))
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_LOOP_LENGTH", left.getName (), right.getName (), Long.toString (leftStart), Long.toString (leftLoopLength), Long.toString (rightStart), Long.toString (rightLoopLength));

        // Only show the warning if there is no loop
        final long leftLength = left.getEnd () - left.getStart ();
        final long rightLength = right.getEnd () - right.getStart ();
        if (leftLength != rightLength)
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_LENGTH", left.getName (), Long.toString (leftLength), right.getName (), Long.toString (rightLength));

        return true;
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
        final Sf2SampleMetadata sampleMetadata = new Sf2SampleMetadata (sample, generators.getSignedValue (Generator.PANORAMA));

        // Set the pitch
        final int overridingRootKey = generators.getUnsignedValue (Generator.OVERRIDING_ROOT_KEY).intValue ();
        int pitch = overridingRootKey < 0 ? sample.getOriginalPitch () : overridingRootKey;
        pitch += generators.getSignedValue (Generator.COARSE_TUNE).intValue ();
        sampleMetadata.setKeyRoot (pitch);
        final int fineTune = generators.getSignedValue (Generator.FINE_TUNE).intValue ();
        final double tune = Math.min (1, Math.max (-1, (sample.getPitchCorrection () + (double) fineTune) / 100));
        sampleMetadata.setTune (tune);
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
