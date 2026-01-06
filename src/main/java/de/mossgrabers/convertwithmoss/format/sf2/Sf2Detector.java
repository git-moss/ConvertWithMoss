// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.RiffID;
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


/**
 * Detects recursively SoundFont 2 files in folders. Files must end with <i>.sf2</i>.
 *
 * @author Jürgen Moßgraber
 */
public class Sf2Detector extends AbstractDetector<Sf2DetectorUI>
{
    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Sf2Detector (final INotifier notifier)
    {
        super ("SoundFont 2", "Sf2", notifier, new Sf2DetectorUI ("Sf2"), ".sf2");
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File sourceFile)
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
     * Create multi-sample sources for all presets found in the SF2 file.
     *
     * @param sourceFile The SF2 source file
     * @param sf2File The parsed SF2 file
     * @param parts The path parts
     * @return The multi-sample sources
     */
    private List<IMultisampleSource> parseSF2File (final File sourceFile, final Sf2File sf2File, final String [] parts)
    {
        final List<IMultisampleSource> multisamples = new ArrayList<> ();

        final List<Sf2Preset> presets = sf2File.getPresets ();
        // -1 since the last one only signals the end of the presets list
        for (int i = 0; i < presets.size () - 1; i++)
        {
            final Sf2Preset preset = presets.get (i);

            String presetName = preset.getName ();

            // Little workaround for not set names...
            if ("NewInstr".equals (presetName))
                presetName = parts[0];
            if (this.settingsConfiguration.addFileName () || this.settingsConfiguration.addProgramNumber ())
                presetName = this.addPrefixes (presetName, preset.getProgramNumber (), FileUtils.getNameWithoutType (sourceFile));

            final String mappingName = AudioFileUtils.subtractPaths (this.sourceFolder, sourceFile) + " : " + presetName;
            final DefaultMultisampleSource source = new DefaultMultisampleSource (sourceFile, parts, presetName, mappingName);
            final IMetadata metadata = source.getMetadata ();
            this.fillMetadata (sf2File, parts, metadata);

            final GeneratorHierarchy generators = new GeneratorHierarchy ();

            // Create the groups
            final List<IGroup> groups = new ArrayList<> ();
            for (int presetZoneIndex = 0; presetZoneIndex < preset.getZoneCount (); presetZoneIndex++)
            {
                final Sf2PresetZone presetZone = preset.getZone (presetZoneIndex);
                if (presetZone.isGlobal ())
                {
                    generators.setPresetZoneGlobalGenerators (presetZone.getGenerators ());
                    continue;
                }
                generators.setPresetZoneGenerators (presetZone.getGenerators ());

                final Sf2Instrument instrument = presetZone.getInstrument ();
                final DefaultGroup group = new DefaultGroup (instrument.getName ());

                for (int instrumentZoneIndex = 0; instrumentZoneIndex < instrument.getZoneCount (); instrumentZoneIndex++)
                {
                    final Sf2InstrumentZone instrZone = instrument.getZone (instrumentZoneIndex);
                    if (instrZone.isGlobal ())
                    {
                        generators.setInstrumentZoneGlobalGenerators (instrZone.getGenerators ());
                        continue;
                    }
                    generators.setInstrumentZoneGenerators (instrZone.getGenerators ());
                    final ISampleZone zone = createSampleZone (instrZone.getSample (), generators);
                    parseModulators (zone, presetZone, instrZone);
                    group.addSampleZone (zone);
                }

                groups.add (group);
            }

            if (this.settingsConfiguration.logUnsupportedAttributes ())
                this.printUnsupportedGenerators (generators.diffGenerators ());

            source.setGroups (this.combineToStereo (groups));

            multisamples.add (source);
        }

        return multisamples;
    }


    private void fillMetadata (final Sf2File sf2File, final String [] parts, final IMetadata metadata)
    {
        String description = sf2File.formatInfoFields (RiffID.INFO_CMNT, RiffID.INFO_ICMT, RiffID.INFO_COMM, RiffID.INFO_ICOP, RiffID.INFO_IMIT, RiffID.INFO_IMIU, RiffID.INFO_TORG, RiffID.INFO_TORG);
        // Remove unnecessary 'Comment' labels. Order is important!
        description = description.replace (RiffID.INFO_COMM.getName () + ": ", "").replace (RiffID.INFO_ICMT.getName () + ": ", "").replace (RiffID.INFO_CMNT.getName () + ": ", "");

        metadata.detectMetadata (this.settingsConfiguration, parts);

        if (TagDetector.CATEGORY_UNKNOWN.equals (metadata.getCategory ()))
            metadata.setCategory (TagDetector.detectCategory (description.split ("\n")));

        metadata.setCreator (sf2File.getSoundDesigner ());
        metadata.setCreationDateTime (sf2File.getParsedCreationDate ());
        metadata.setDescription (description);
    }


    private String addPrefixes (final String presetName, final int programNumber, final String sf2FileName)
    {
        final StringBuilder sb = new StringBuilder ();
        if (this.settingsConfiguration.addFileName ())
            sb.append (sf2FileName).append (" - ");
        if (this.settingsConfiguration.addProgramNumber ())
            sb.append (String.format ("%03d", Integer.valueOf (programNumber))).append (" - ");
        return sb.append (presetName).toString ();
    }


    private static void parseModulators (final ISampleZone zone, final Sf2PresetZone sf2Zone, final Sf2InstrumentZone instrZone)
    {
        for (final Sf2Modulator sf2Modulator: getModulators (sf2Zone, instrZone, Sf2Modulator.MODULATOR_PITCH_BEND))
            if (sf2Modulator.getDestinationGenerator () == Generator.FINE_TUNE)
            {
                final int amount = sf2Modulator.getModulationAmount ();
                zone.setBendUp (amount);
                zone.setBendDown (-amount);
            }

        for (final Sf2Modulator sf2Modulator: getModulators (sf2Zone, instrZone, Sf2Modulator.MODULATOR_VELOCITY))
        {
            final int destinationGenerator = sf2Modulator.getDestinationGenerator ();
            if (destinationGenerator == Generator.INITIAL_ATTENUATION)
            {
                final int amount = sf2Modulator.getModulationAmount ();
                zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (amount / 960, 0, 1));
            }
            else if (destinationGenerator == Generator.INITIAL_FILTER_CUTOFF)
            {
                final int amount = sf2Modulator.getModulationAmount ();
                zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (amount / -2400, 0, 1));
            }
        }
    }


    private static List<Sf2Modulator> getModulators (final Sf2PresetZone zone, final Sf2InstrumentZone instrZone, final Integer modulatorID)
    {
        final List<Sf2Modulator> modulators = instrZone.getModulators (modulatorID);
        return modulators.isEmpty () ? zone.getModulators (modulatorID) : modulators;
    }


    /**
     * SF2 contains only mono files. Combine them to stereo, if setup as split-stereo or (only)
     * panned left/right. If it is a pure mono file (not panned) leave it as it is.
     *
     * @param groups The groups which contain the samples to combine
     * @return The groups with combined samples for convenience
     */
    private List<IGroup> combineToStereo (final List<IGroup> groups)
    {
        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = group.getSampleZones ();

            final int initialCapacity = zones.size () / 2;
            final List<ISampleZone> resultSamples = new ArrayList<> (initialCapacity);
            final List<ISampleZone> leftSamples = new ArrayList<> (initialCapacity);
            final List<ISampleZone> rightSamples = new ArrayList<> (initialCapacity);
            final List<ISampleZone> panLeftSamples = new ArrayList<> (initialCapacity);
            final List<ISampleZone> panRightSamples = new ArrayList<> (initialCapacity);

            for (final ISampleZone zone: zones)
            {
                final Sf2SampleData sf2SampleData = (Sf2SampleData) zone.getSampleData ();
                final Sf2SampleDescriptor sample = sf2SampleData.getSample ();

                // Store left and right samples in different lists first
                switch (sample.getSampleType ())
                {
                    case Sf2SampleDescriptor.LEFT:
                        leftSamples.add (zone);
                        break;

                    case Sf2SampleDescriptor.RIGHT:
                        rightSamples.add (zone);
                        break;

                    default:
                    case Sf2SampleDescriptor.MONO:
                        final double panning = zone.getTuning ();
                        if (panning == 0)
                            resultSamples.add (zone);
                        else if (panning < 0)
                            panLeftSamples.add (zone);
                        else
                            panRightSamples.add (zone);
                        break;
                }
            }

            if (leftSamples.size () != rightSamples.size ())
                this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_NUMBER_LEFT_RIGHT", Integer.toString (leftSamples.size ()), Integer.toString (rightSamples.size ()));

            resultSamples.addAll (this.combineLinkedSamples (leftSamples, rightSamples));
            resultSamples.addAll (this.combinePanningSamples (panLeftSamples, panRightSamples));

            group.setSampleZones (resultSamples);
        }

        return groups;
    }


    /**
     * Match the left and right hand side samples. The left hand side is linked to the right hand
     * side via an index.
     *
     * @param leftSampleZones The left hand side mono samples
     * @param rightSampleZones The right hand side mono samples
     * @return The stereo combined result samples
     */
    private List<ISampleZone> combineLinkedSamples (final List<ISampleZone> leftSampleZones, final List<ISampleZone> rightSampleZones)
    {
        final List<ISampleZone> resultSamples = new ArrayList<> ();

        for (final ISampleZone leftSampleZone: leftSampleZones)
        {
            final Sf2SampleData leftSampleData = (Sf2SampleData) leftSampleZone.getSampleData ();
            final int rightSampleIndex = leftSampleData.getSample ().getLinkedSample ();

            ISampleZone rightSampleZone;
            boolean found = false;
            for (int i = 0; i < rightSampleZones.size (); i++)
            {
                rightSampleZone = rightSampleZones.get (i);
                final Sf2SampleData rightSampleData = (Sf2SampleData) rightSampleZone.getSampleData ();
                final Sf2SampleDescriptor sample = rightSampleData.getSample ();
                // Match via the linked index
                if (sample.getSampleIndex () == rightSampleIndex)
                {
                    if (this.compareSampleFormat (leftSampleZone, rightSampleZone))
                    {
                        // Store the matching right side sample with the left side one
                        leftSampleData.setRightSample (sample);
                        updateFilename (leftSampleZone, rightSampleZone);
                        leftSampleZone.setPanning (Math.clamp (leftSampleZone.getTuning () + rightSampleZone.getTuning (), -1.0, 1.0));
                        resultSamples.add (leftSampleZone);
                        rightSampleZones.remove (i);
                        found = true;
                    }
                    break;
                }
            }

            // No match found, keep the left sample
            if (!found)
                resultSamples.add (leftSampleZone);
        }

        // Add all unmatched right samples
        if (!rightSampleZones.isEmpty ())
            resultSamples.addAll (rightSampleZones);

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
    private List<ISampleZone> combinePanningSamples (final List<ISampleZone> panLeftSamples, final List<ISampleZone> panRightSamples)
    {
        final List<ISampleZone> resultSamples = new ArrayList<> ();

        for (final ISampleZone panLeftSampleZone: panLeftSamples)
        {
            final int keyLow = AbstractCreator.limitToDefault (panLeftSampleZone.getKeyLow (), 0);
            final int keyHigh = AbstractCreator.limitToDefault (panLeftSampleZone.getKeyHigh (), 127);
            final int velocityLow = AbstractCreator.limitToDefault (panLeftSampleZone.getVelocityLow (), 1);
            final int velocityHigh = AbstractCreator.limitToDefault (panLeftSampleZone.getVelocityHigh (), 127);

            ISampleZone panRightSampleZone;
            boolean found = false;
            for (int i = 0; i < panRightSamples.size (); i++)
            {
                panRightSampleZone = panRightSamples.get (i);
                // Match by the key and velocity range
                if (keyLow == AbstractCreator.limitToDefault (panRightSampleZone.getKeyLow (), 0) && keyHigh == AbstractCreator.limitToDefault (panRightSampleZone.getKeyHigh (), 127) && velocityLow == AbstractCreator.limitToDefault (panRightSampleZone.getVelocityLow (), 1) && velocityHigh == AbstractCreator.limitToDefault (panRightSampleZone.getVelocityHigh (), 127))
                {
                    if (this.compareSampleFormat (panLeftSampleZone, panRightSampleZone))
                    {
                        // Store the matching right side sample with the left side one
                        final Sf2SampleData leftSampleData = (Sf2SampleData) panLeftSampleZone.getSampleData ();
                        updateFilename (panLeftSampleZone, panRightSampleZone);
                        leftSampleData.setRightSample (((Sf2SampleData) panRightSampleZone.getSampleData ()).getSample ());
                        panLeftSampleZone.setPanning (Math.clamp (panLeftSampleZone.getTuning () + panRightSampleZone.getTuning (), -1.0, 1.0));
                        resultSamples.add (panLeftSampleZone);
                        panRightSamples.remove (i);
                        found = true;
                    }
                    break;
                }
            }
            // No match found, keep the left sample
            if (!found)
                resultSamples.add (panLeftSampleZone);
        }

        // Add all unmatched right samples
        if (!panRightSamples.isEmpty ())
            resultSamples.addAll (panRightSamples);

        return resultSamples;
    }


    private boolean compareSampleFormat (final ISampleZone leftSampleZone, final ISampleZone rightSampleZone)
    {
        final Sf2SampleDescriptor left = ((Sf2SampleData) leftSampleZone.getSampleData ()).getSample ();
        final Sf2SampleDescriptor right = ((Sf2SampleData) rightSampleZone.getSampleData ()).getSample ();

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
        final long leftStart = left.getLoopStart () - left.getStart ();
        final long rightStart = right.getLoopStart () - right.getStart ();
        final long leftLoopLength = left.getLoopEnd () - left.getLoopStart ();
        final long rightLoopLength = right.getLoopEnd () - right.getLoopStart ();
        if (!leftSampleZone.getLoops ().isEmpty () && (leftStart != rightStart || leftLoopLength != rightLoopLength))
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_LOOP_LENGTH", left.getName (), right.getName (), Long.toString (leftStart), Long.toString (leftLoopLength), Long.toString (rightStart), Long.toString (rightLoopLength));

        // Only show the warning if there is no loop
        final long leftLength = left.getEnd () - left.getStart ();
        final long rightLength = right.getEnd () - right.getStart ();
        if (leftLength != rightLength)
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_LENGTH", left.getName (), Long.toString (leftLength), right.getName (), Long.toString (rightLength));

        return true;
    }


    /**
     * Create a sample zone.
     *
     * @param sample The source sample
     * @param generators All hierarchical generator values
     * @return The sample zone
     */
    private static ISampleZone createSampleZone (final Sf2SampleDescriptor sample, final GeneratorHierarchy generators)
    {
        try
        {
            final Sf2SampleData sampleData = new Sf2SampleData (sample);
            final DefaultSampleZone zone = new DefaultSampleZone (sample.getName (), sampleData);

            final Integer panning = generators.getSignedValue (Generator.PANNING);
            if (panning != null)
                zone.setPanning (panning.intValue () / 500.0);

            // Set the pitch
            final int overridingRootKey = generators.getUnsignedValue (Generator.OVERRIDING_ROOT_KEY).intValue ();
            final int originalPitch = sample.getOriginalPitch ();
            int pitch = overridingRootKey < 0 ? originalPitch : overridingRootKey;
            pitch += generators.getSignedValue (Generator.COARSE_TUNE).intValue ();
            zone.setKeyRoot (pitch);
            final int fineTune = generators.getSignedValue (Generator.FINE_TUNE).intValue ();
            final int pitchCorrection = sample.getPitchCorrection ();
            final double tune = Math.clamp ((pitchCorrection + (double) fineTune) / 100, -1, 1);
            zone.setTuning (tune);
            final int scaleTuning = generators.getSignedValue (Generator.SCALE_TUNE).intValue ();
            zone.setKeyTracking (Math.clamp (scaleTuning / 100.0, 0, 100));

            // Set the key range
            final Pair<Integer, Integer> keyRangeValue = generators.getRangeValue (Generator.KEY_RANGE);
            zone.setKeyLow (keyRangeValue.getKey ().intValue ());
            zone.setKeyHigh (keyRangeValue.getValue ().intValue ());

            // Set the velocity range
            final Pair<Integer, Integer> velRangeValue = generators.getRangeValue (Generator.VELOCITY_RANGE);
            zone.setVelocityLow (velRangeValue.getKey ().intValue ());
            zone.setVelocityHigh (velRangeValue.getValue ().intValue ());

            // Set play range
            zone.setStart (0);
            final Integer sampleStartOffset = generators.getSignedValue (Generator.START_ADDRS_OFFSET);
            final int sampleStartOffsetInt = sampleStartOffset == null ? 0 : sampleStartOffset.intValue ();
            final long sampleStart = sample.getStart () + sampleStartOffsetInt;
            final Integer sampleEndOffset = generators.getSignedValue (Generator.END_ADDRS_OFFSET);
            final int sampleEndOffsetInt = sampleEndOffset == null ? 0 : sampleEndOffset.intValue ();
            zone.setStop ((int) (sample.getEnd () - sampleStart + sampleEndOffsetInt));

            // Set loop, if any
            if ((generators.getUnsignedValue (Generator.SAMPLE_MODES).intValue () & 1) > 0)
            {
                final DefaultSampleLoop sampleLoop = new DefaultSampleLoop ();
                final Integer startOffset = generators.getSignedValue (Generator.START_LOOP_ADDRS_OFFSET);
                final int startOffsetInt = startOffset == null ? 0 : startOffset.intValue ();
                sampleLoop.setStart ((int) (sample.getLoopStart () - sampleStart + startOffsetInt));
                final Integer endOffset = generators.getSignedValue (Generator.END_LOOP_ADDRS_OFFSET);
                final int endOffsetInt = endOffset == null ? 0 : endOffset.intValue ();
                sampleLoop.setEnd ((int) (sample.getLoopEnd () - sampleStart + endOffsetInt));
                zone.addLoop (sampleLoop);
            }

            // Gain
            final int initialAttenuation = generators.getSignedValue (Generator.INITIAL_ATTENUATION).intValue ();
            if (initialAttenuation > 0)
                zone.setGain (-initialAttenuation / 10.0);

            // Volume envelope
            final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
            amplitudeEnvelope.setDelayTime (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_DELAY)));
            amplitudeEnvelope.setAttackTime (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_ATTACK)));
            amplitudeEnvelope.setHoldTime (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_HOLD)));
            amplitudeEnvelope.setDecayTime (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_DECAY)));
            amplitudeEnvelope.setReleaseTime (convertEnvelopeTime (generators.getSignedValue (Generator.VOL_ENV_RELEASE)));
            amplitudeEnvelope.setSustainLevel (convertEnvelopeVolume (generators.getSignedValue (Generator.VOL_ENV_SUSTAIN)));

            // Filter settings
            final Integer initialCutoffValue = generators.getSignedValue (Generator.INITIAL_FILTER_CUTOFF);
            if (initialCutoffValue != null)
            {
                final int initialCutoff = initialCutoffValue.intValue ();
                if (initialCutoff >= 1500 && initialCutoff < 13500)
                {
                    // Convert cents to Hertz: f2 is the minimum supported frequency, cents is
                    // always a relation of two frequencies, 1200 cents are one octave:
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

                    final IFilter filter = new DefaultFilter (FilterType.LOW_PASS, 2, frequency, resonance / IFilter.MAX_RESONANCE);
                    final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                    final int cutoffModDepth = generators.getSignedValue (Generator.MOD_ENV_TO_FILTER_CUTOFF).intValue ();
                    cutoffModulator.setDepth (cutoffModDepth / (double) IEnvelope.MAX_ENVELOPE_DEPTH);
                    if (cutoffModDepth != 0)
                    {
                        final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                        filterEnvelope.setDelayTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DELAY)));
                        filterEnvelope.setAttackTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_ATTACK)));
                        filterEnvelope.setHoldTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_HOLD)));
                        filterEnvelope.setDecayTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DECAY)));
                        filterEnvelope.setReleaseTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_RELEASE)));
                        filterEnvelope.setSustainLevel (convertEnvelopeVolume (generators.getSignedValue (Generator.MOD_ENV_SUSTAIN)));
                    }

                    zone.setFilter (filter);
                }
            }

            final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
            final int pitchModDepth = generators.getSignedValue (Generator.MOD_ENV_TO_PITCH).intValue ();
            pitchModulator.setDepth (pitchModDepth / (double) IEnvelope.MAX_ENVELOPE_DEPTH);
            if (pitchModDepth != 0)
            {
                final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                pitchEnvelope.setDelayTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DELAY)));
                pitchEnvelope.setAttackTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_ATTACK)));
                pitchEnvelope.setHoldTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_HOLD)));
                pitchEnvelope.setDecayTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_DECAY)));
                pitchEnvelope.setReleaseTime (convertEnvelopeTime (generators.getSignedValue (Generator.MOD_ENV_RELEASE)));
                pitchEnvelope.setSustainLevel (convertEnvelopeVolume (generators.getSignedValue (Generator.MOD_ENV_SUSTAIN)));
            }

            return zone;
        }
        catch (final IOException ex)
        {
            // Can never happen
            return null;
        }
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

        // Attenuation is in centi-bel (dB / 10), so 0 is maximum volume, about 1000 is off
        final int v = Math.min (1000, value.intValue ());
        if (v <= 0)
            return -1;

        // This is likely not correct but since there is also no documentation what the percentage
        // volume values mean in dB it is the best we can do...
        return Math.clamp (1.0 - v / 1000.0, 0, 1);
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


    private static void updateFilename (final ISampleZone leftSampleZone, final ISampleZone rightSampleZone)
    {
        String commonPrefix = commonPrefix (leftSampleZone.getName (), rightSampleZone.getName ()).trim ();
        if (commonPrefix.endsWith ("_") || commonPrefix.endsWith ("("))
            commonPrefix = commonPrefix.substring (0, commonPrefix.length () - 1);
        leftSampleZone.setName (commonPrefix);
    }


    private static String commonPrefix (final String a, final String b)
    {
        final int minLength = Math.min (a.length (), b.length ());
        for (int i = 0; i < minLength; i++)
            if (a.charAt (i) != b.charAt (i))
                return a.substring (0, i);
        return a.substring (0, minLength);
    }
}
