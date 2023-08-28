// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.MultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleMetadata;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.sf2.Generator;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2File;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Instrument;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2InstrumentZone;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Modulator;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2Preset;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2PresetZone;
import de.mossgrabers.convertwithmoss.file.sf2.Sf2SampleDescriptor;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;


/**
 * Detects recursively SoundFont 2 files in folders. Files must end with <i>.sf2</i>.
 *
 * @author Jürgen Moßgraber
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
            final String [] parts = AudioFileUtils.createPathParts (sourceFile.getParentFile (), this.sourceFolder, name);
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

            final String mappingName = AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile) + " : " + preset.getName ();
            final MultisampleSource source = new MultisampleSource (sourceFile, parts, preset.getName (), mappingName);
            source.setCreator (sf2File.getSoundDesigner ());
            source.setCategory (TagDetector.detectCategory (parts));
            source.setKeywords (TagDetector.detectKeywords (parts));
            source.setDescription (createDescription (sf2File));

            final GeneratorHierarchy generators = new GeneratorHierarchy ();

            // Create the layers
            final List<IGroup> layers = new ArrayList<> ();
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
                final DefaultGroup layer = new DefaultGroup (instrument.getName ());

                for (int instrumentZoneIndex = 0; instrumentZoneIndex < instrument.getZoneCount (); instrumentZoneIndex++)
                {
                    final Sf2InstrumentZone instrZone = instrument.getZone (instrumentZoneIndex);
                    if (instrZone.isGlobal ())
                    {
                        generators.setInstrumentZoneGlobalGenerators (instrZone.getGenerators ());
                        continue;
                    }
                    generators.setInstrumentZoneGenerators (instrZone.getGenerators ());
                    final Sf2SampleMetadata sampleMetadata = createSampleMetadata (instrZone.getSample (), generators);
                    parseModulators (sampleMetadata, zone, instrZone);
                    layer.addSampleMetadata (sampleMetadata);
                }

                layers.add (layer);
            }

            this.printUnsupportedGenerators (generators.diffGenerators ());

            source.setGroups (this.combineToStereo (layers));

            multisamples.add (source);
        }

        return multisamples;
    }


    private static void parseModulators (final Sf2SampleMetadata sampleMetadata, final Sf2PresetZone zone, final Sf2InstrumentZone instrZone)
    {
        Optional<Sf2Modulator> modulator = instrZone.getModulator (Sf2Modulator.MODULATOR_PITCH_BEND);
        if (modulator.isEmpty ())
            modulator = zone.getModulator (Sf2Modulator.MODULATOR_PITCH_BEND);
        if (!modulator.isEmpty ())
        {
            final Sf2Modulator sf2Modulator = modulator.get ();
            if (sf2Modulator.getDestinationGenerator () == Generator.FINE_TUNE)
            {
                final int amount = sf2Modulator.getModulationAmount ();
                sampleMetadata.setBendUp (amount);
                sampleMetadata.setBendDown (-amount);
            }
        }
    }


    /**
     * SF2 contains only mono files. Combine them to stereo, if setup as split-stereo or (only)
     * panned left/right. If it is a pure mono file (not panned) leave it as it is.
     *
     * @param layers The layers which contain the samples to combine
     * @return The layers with combined samples for convenience
     */
    private List<IGroup> combineToStereo (final List<IGroup> layers)
    {
        for (final IGroup layer: layers)
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
                        final double panorama = sf2SampleMetadata.getPanorama ();
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
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_PITCH", left.getName (), right.getName ());
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
        final Sf2SampleMetadata sampleMetadata = new Sf2SampleMetadata (sample);

        final Integer panorama = generators.getSignedValue (Generator.PANORAMA);
        if (panorama != null)
            sampleMetadata.setPanorama (panorama.intValue () / 500.0);

        // Set the pitch
        final int overridingRootKey = generators.getUnsignedValue (Generator.OVERRIDING_ROOT_KEY).intValue ();
        final int originalPitch = sample.getOriginalPitch ();
        int pitch = overridingRootKey < 0 ? originalPitch : overridingRootKey;
        pitch += generators.getSignedValue (Generator.COARSE_TUNE).intValue ();
        sampleMetadata.setKeyRoot (pitch);
        final int fineTune = generators.getSignedValue (Generator.FINE_TUNE).intValue ();
        final int pitchCorrection = sample.getPitchCorrection ();
        final double tune = Math.min (1, Math.max (-1, (pitchCorrection + (double) fineTune) / 100));
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
            final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
            sampleLoop.setStart ((int) (sample.getStartloop () - sampleStart));
            sampleLoop.setEnd ((int) (sample.getEndloop () - sampleStart));
            sampleMetadata.addLoop (sampleLoop);
        }

        // Gain
        final int initialAttenuation = generators.getSignedValue (Generator.INITIAL_ATTENUATION).intValue ();
        if (initialAttenuation > 0)
            sampleMetadata.setGain (-initialAttenuation / 10.0);

        // Volume envelope
        final IEnvelope amplitudeEnvelope = sampleMetadata.getAmplitudeModulator ().getSource ();
        amplitudeEnvelope.setDelay (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_DELAY)));
        amplitudeEnvelope.setAttack (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_ATTACK)));
        amplitudeEnvelope.setHold (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_HOLD)));
        amplitudeEnvelope.setDecay (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_DECAY)));
        amplitudeEnvelope.setRelease (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_RELEASE)));
        amplitudeEnvelope.setSustain (convertEnvelopeVolume (generators.getSignedValue (Generator.VOL_ENV_SUSTAIN)));

        // Filter settings
        final Integer initialCutoffValue = generators.getSignedValue (Generator.INITIAL_FILTER_CUTOFF);
        if (initialCutoffValue != null)
        {
            final int initialCutoff = initialCutoffValue.intValue ();
            if (initialCutoff >= 1500 && initialCutoff < 13500)
            {
                // Convert cents to Hertz: f2 is the minimum supported frequency, cents is always a
                // relation of two frequencies, 1200 cents are one octave:
                // cents = 1200 * log2 (f1 / f2), f2 = 8.176 => f1 = f2 * 2^(cents / 1200)
                final double frequency = 8.176 * Math.pow (2, initialCutoff / 1200.0);

                double resonance = 0;
                final Integer initialResonanceValue = generators.getSignedValue (Generator.INITIAL_FILTER_RESONANCE);
                if (initialResonanceValue != null)
                {
                    final int initialResonance = initialResonanceValue.intValue ();
                    if (initialResonance > 0 && initialResonance < 960)
                        resonance = initialResonance / 100.0;
                }

                final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 2, frequency, resonance);
                final IModulator cutoffModulator = filter.getCutoffModulator ();
                final int cutoffModDepth = generators.getSignedValue (Generator.MOD_ENV_TO_FILTER_CUTOFF).intValue ();
                cutoffModulator.setDepth (cutoffModDepth);
                if (cutoffModDepth != 0)
                {
                    final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                    filterEnvelope.setDelay (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DELAY)));
                    filterEnvelope.setAttack (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_ATTACK)));
                    filterEnvelope.setHold (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_HOLD)));
                    filterEnvelope.setDecay (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DECAY)));
                    filterEnvelope.setRelease (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_RELEASE)));
                    filterEnvelope.setSustain (convertEnvelopeVolume (generators.getSignedValue (Generator.MOD_ENV_SUSTAIN)));
                }

                sampleMetadata.setFilter (filter);

                final IModulator pitchModulator = sampleMetadata.getPitchModulator ();
                final int pitchModDepth = generators.getSignedValue (Generator.MOD_ENV_TO_PITCH).intValue ();
                pitchModulator.setDepth (pitchModDepth);
                if (pitchModDepth != 0)
                {
                    final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                    pitchEnvelope.setDelay (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DELAY)));
                    pitchEnvelope.setAttack (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_ATTACK)));
                    pitchEnvelope.setHold (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_HOLD)));
                    pitchEnvelope.setDecay (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DECAY)));
                    pitchEnvelope.setRelease (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_RELEASE)));
                    pitchEnvelope.setSustain (convertEnvelopeVolume (generators.getSignedValue (Generator.MOD_ENV_SUSTAIN)));
                }
            }
        }

        return sampleMetadata;
    }


    private static double convertEnvelopeTime (final Integer value)
    {
        if (value == null)
            return -1;

        final double v = Math.pow (2, value.doubleValue () / 1200.0);
        // Ignore times less than 1 millisecond
        return v < 0.001 ? -1 : v;
    }


    private static double convertEnvelopeVolume (final Integer value)
    {
        if (value == null)
            return -1;

        // Attenuation is in centibel (dB / 10), so 0 is maximum volume, about 1000 is off
        final int v = Math.min (1000, value.intValue ());
        if (v <= 0)
            return -1;

        // This is likely not correct but since there is also no documentation what the percentage
        // volume values mean in dB it is the best we can do...
        return Math.max (0, Math.min (1.0, 1.0 - v / 1000.0));
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


    /**
     * Formats and reports all unsupported generators.
     *
     * @param unsupportedGenerators The unsupported generators
     */
    private void printUnsupportedGenerators (final Set<String> unsupportedGenerators)
    {
        final StringBuilder sb = new StringBuilder ();

        unsupportedGenerators.forEach (attribute -> {
            if (!sb.isEmpty ())
                sb.append (", ");
            sb.append (attribute);
        });

        if (!sb.isEmpty ())
            this.notifier.logError ("IDS_NOTIFY_UNSUPPORTED_GENERATORS", sb.toString ());
    }
}
