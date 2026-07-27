// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.sf2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.riff.InfoRiffChunkId;
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
     * The maximum absolute 16 bit value which still counts as silence (about -60dBFS), which covers
     * dithered 'digital silence'.
     */
    private static final int SILENCE_THRESHOLD = 32;
    /** The range of the coarse tuning generator in semi-tones as defined by the SoundFont 2 spec. */
    private static final int MAX_COARSE_TUNE   = 120;


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
     * @throws IOException Could not parse the SF2 file
     */
    private List<IMultisampleSource> parseSF2File (final File sourceFile, final Sf2File sf2File, final String [] parts) throws IOException
    {
        final List<IMultisampleSource> multisamples = new ArrayList<> ();

        final List<Sf2Preset> presets = sf2File.getPresets ();
        // -1 since the last one only signals the end of the presets list
        for (int i = 0; i < presets.size () - 1; i++)
        {
            final GeneratorHierarchy generators = new GeneratorHierarchy ();

            // Create the groups
            final List<IGroup> groups = new ArrayList<> ();
            final Sf2Preset preset = presets.get (i);
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
                final IGroup group = new DefaultGroup (instrument.getName ());

                for (int instrumentZoneIndex = 0; instrumentZoneIndex < instrument.getZoneCount (); instrumentZoneIndex++)
                {
                    final Sf2InstrumentZone instrZone = instrument.getZone (instrumentZoneIndex);
                    if (instrZone.isGlobal ())
                    {
                        generators.setInstrumentZoneGlobalGenerators (instrZone.getGenerators ());
                        continue;
                    }
                    generators.setInstrumentZoneGenerators (instrZone.getGenerators ());
                    final Optional<ISampleZone> zoneOpt = createSampleZone (instrZone.getSample (), generators);
                    if (zoneOpt.isPresent ())
                    {
                        final ISampleZone zone = zoneOpt.get ();
                        parseModulators (zone, presetZone, instrZone);
                        group.addSampleZone (zone);
                    }
                }

                groups.add (group);
            }

            if (this.settingsConfiguration.logUnsupportedAttributes ())
                this.printUnsupportedGenerators (generators.diffGenerators ());

            String presetName = preset.getName ();
            if ("NewInstr".equals (presetName))
                presetName = parts[0];
            if (this.settingsConfiguration.addFileName () || this.settingsConfiguration.addProgramNumber ())
                presetName = this.addPrefixes (presetName, preset.getProgramNumber (), FileUtils.getNameWithoutType (sourceFile));
            final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, parts, presetName, this.combineToStereo (groups));

            // Skip "empty" presets which reference no samples. Commercial SoundFonts often carry
            // marker presets named after the vendor or the copyright (e.g. "E-mu Systems 2007")
            // whose instrument has no zones; without this guard they would be written as empty
            // instruments.
            if (multisampleSource.getNonEmptyGroups (false).isEmpty ())
            {
                this.notifier.log ("IDS_NOTIFY_SF2_SKIP_EMPTY_PRESET", presetName);
                continue;
            }

            // Also skip marker presets whose samples contain only digital silence - e.g. the
            // vendor and copyright presets of the E-mu E4 Producer Series banks reference half a
            // second of dithered silence instead of no sample at all.
            if (containsOnlySilence (multisampleSource.getNonEmptyGroups (false)))
            {
                this.notifier.log ("IDS_NOTIFY_SF2_SKIP_SILENT_PRESET", presetName);
                continue;
            }
            // Purely informational: when the analyzed audio of the samples consistently
            // contradicts the mapped root keys by whole octaves, the preset sounds that many
            // octaves off. Seen in commercial banks, e.g. several E-mu E4 Producer Series presets
            // carry root keys one octave above the samples' true pitch and therefore sound one
            // octave lower than played. The Transpose processing option can correct the playback
            // pitch without changing the key mapping.
            final int octaveOffset = detectOctaveOffset (multisampleSource.getNonEmptyGroups (false));
            if (octaveOffset != 0)
                this.notifier.log (octaveOffset > 0 ? "IDS_NOTIFY_SF2_SOUNDS_LOWER" : "IDS_NOTIFY_SF2_SOUNDS_HIGHER", presetName, Integer.toString (Math.abs (octaveOffset) / 12));

            this.fillMetadata (sf2File, parts, multisampleSource.getMetadata ());
            multisamples.add (multisampleSource);
        }

        return multisamples;
    }


    private void fillMetadata (final Sf2File sf2File, final String [] parts, final IMetadata metadata)
    {
        String description = sf2File.formatInfoFields (InfoRiffChunkId.INFO_CMNT, InfoRiffChunkId.INFO_ICMT, InfoRiffChunkId.INFO_COMM, InfoRiffChunkId.INFO_ICOP, InfoRiffChunkId.INFO_IMIT, InfoRiffChunkId.INFO_IMIU, InfoRiffChunkId.INFO_TORG, InfoRiffChunkId.INFO_TORG);
        // Remove unnecessary 'Comment' labels. Order is important!
        description = description.replace (InfoRiffChunkId.INFO_COMM.getDescription () + ": ", "").replace (InfoRiffChunkId.INFO_ICMT.getDescription () + ": ", "").replace (InfoRiffChunkId.INFO_CMNT.getDescription () + ": ", "");

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
                zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (amount / 960.0, 0, 1));
            }
            else if (destinationGenerator == Generator.INITIAL_FILTER_CUTOFF)
            {
                final Optional<IFilter> filterOpt = zone.getFilter ();
                if (filterOpt.isPresent ())
                {
                    final int amount = sf2Modulator.getModulationAmount ();
                    filterOpt.get ().getCutoffVelocityModulator ().setDepth (Math.clamp (amount / -2400.0, 0, 1));
                }
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
     * @throws IOException Could not combine to stereo
     */
    private List<IGroup> combineToStereo (final List<IGroup> groups) throws IOException
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
                final Optional<ISampleData> sampleDataOpt = zone.getSampleData ();
                if (sampleDataOpt.isEmpty ())
                    throw new IOException ("Empty sample data in zone: " + zone.getName ());
                if (sampleDataOpt.get () instanceof final Sf2SampleData sf2SampleData)
                {
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
                            final double panning = zone.getPanning ();
                            if (panning == 0)
                                resultSamples.add (zone);
                            else if (panning < 0)
                                panLeftSamples.add (zone);
                            else
                                panRightSamples.add (zone);
                            break;
                    }
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
            final Optional<ISampleData> leftSampleDataOpt = leftSampleZone.getSampleData ();
            if (leftSampleDataOpt.isEmpty ())
                continue;

            boolean found = false;
            if (leftSampleDataOpt.get () instanceof final Sf2SampleData leftSampleData)
            {
                final int rightSampleIndex = leftSampleData.getSample ().getLinkedSample ();

                ISampleZone rightSampleZone;
                for (int i = 0; i < rightSampleZones.size (); i++)
                {
                    rightSampleZone = rightSampleZones.get (i);
                    final Optional<ISampleData> sampleData = rightSampleZone.getSampleData ();
                    if (sampleData.isPresent () && sampleData.get () instanceof final Sf2SampleData rightSampleData)
                    {
                        final Sf2SampleDescriptor sample = rightSampleData.getSample ();
                        // Match via the linked index
                        if (sample.getSampleIndex () == rightSampleIndex)
                        {
                            if (this.compareSampleFormat (leftSampleZone, rightSampleZone))
                            {
                                // Store the matching right side sample with the left side one
                                leftSampleData.setRightSample (sample);
                                updateFilename (leftSampleZone, rightSampleZone);
                                leftSampleZone.setPanning (Math.clamp (leftSampleZone.getPanning () + rightSampleZone.getPanning (), -1.0, 1.0));
                                resultSamples.add (leftSampleZone);
                                rightSampleZones.remove (i);
                                found = true;
                            }
                            break;
                        }
                    }
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
                        final Optional<ISampleData> leftSampleDataOpt = panLeftSampleZone.getSampleData ();
                        if (leftSampleDataOpt.isPresent () && leftSampleDataOpt.get () instanceof final Sf2SampleData leftSampleData)
                        {
                            updateFilename (panLeftSampleZone, panRightSampleZone);
                            final Optional<ISampleData> rightSampleDataOpt = panRightSampleZone.getSampleData ();
                            if (rightSampleDataOpt.isPresent () && rightSampleDataOpt.get () instanceof Sf2SampleData rightSampleData)
                            {
                                leftSampleData.setRightSample (rightSampleData.getSample ());
                                panLeftSampleZone.setPanning (Math.clamp (panLeftSampleZone.getPanning () + panRightSampleZone.getPanning (), -1.0, 1.0));
                                resultSamples.add (panLeftSampleZone);
                                panRightSamples.remove (i);
                                found = true;
                            }
                        }
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
        final Optional<ISampleData> leftSampleDataOpt = leftSampleZone.getSampleData ();
        if (leftSampleDataOpt.isEmpty () || !(leftSampleDataOpt.get () instanceof final Sf2SampleData leftSampleData))
            return false;
        final Optional<ISampleData> rightSampleDataOpt = rightSampleZone.getSampleData ();
        if (rightSampleDataOpt.isEmpty () || !(rightSampleDataOpt.get () instanceof final Sf2SampleData rightSampleData))
            return false;

        final Sf2SampleDescriptor left = leftSampleData.getSample ();
        final Sf2SampleDescriptor right = rightSampleData.getSample ();

        // A genuine stereo pair must match in pitch and sample rate. If either differs the two
        // samples are not really a pair - some SoundFonts (e.g. commercial E-mu banks) carry
        // unreliable stereo links or flag mono samples as left/right. In that case keep them as
        // the separate mono samples they actually are instead of welding two unrelated samples
        // into one stereo sample. A differing length is handled the same way but only when the
        // 'keep mismatched stereo as mono' option is enabled (off by default), because some banks
        // contain genuine stereo pairs whose channels differ slightly in length. These source
        // quirks are only reported when the 'log unsupported attributes' option is enabled.
        if (left.getOriginalPitch () != right.getOriginalPitch () || left.getPitchCorrection () != right.getPitchCorrection ())
        {
            if (this.settingsConfiguration.logUnsupportedAttributes ())
                this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_PITCH", left.getName (), right.getName ());
            return false;
        }

        if (left.getSampleRate () != right.getSampleRate ())
        {
            if (this.settingsConfiguration.logUnsupportedAttributes ())
                this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_RATE", left.getName (), right.getName ());
            return false;
        }

        // If one channel of a genuine stereo pair simply carries extra frames at its start (the
        // loop offset matches the length offset), the channels are re-aligned when they are
        // combined (see Sf2SampleData.setRightSample). Such pairs are compared as if already
        // aligned, so they are neither warned about nor kept as mono. E.g. in the
        // DigitalSoundFactory E-mu E4 banks every right channel sample is 1 frame longer and
        // loops 1 frame later.
        final int alignmentOffset = Sf2SampleData.computeAlignmentOffset (left, right);
        if (alignmentOffset != 0 && this.settingsConfiguration.logUnsupportedAttributes ())
            this.notifier.log ("IDS_NOTIFY_SF2_ALIGNED_STEREO", left.getName (), right.getName (), Integer.toString (alignmentOffset));

        // Loops must have the same start and length
        final long leftStart = left.getLoopStart () - left.getStart ();
        final long rightStart = right.getLoopStart () - right.getStart () - alignmentOffset;
        final long leftLoopLength = left.getLoopEnd () - left.getLoopStart ();
        final long rightLoopLength = right.getLoopEnd () - right.getLoopStart ();
        if (!leftSampleZone.getLoops ().isEmpty () && (leftStart != rightStart || leftLoopLength != rightLoopLength))
            this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_LOOP_LENGTH", left.getName (), right.getName (), Long.toString (leftStart), Long.toString (leftLoopLength), Long.toString (rightStart), Long.toString (rightLoopLength));

        final long leftLength = left.getEnd () - left.getStart ();
        final long rightLength = right.getEnd () - right.getStart () - alignmentOffset;
        if (leftLength != rightLength)
        {
            if (this.settingsConfiguration.logUnsupportedAttributes ())
                this.notifier.logError ("IDS_NOTIFY_ERR_DIFFERENT_SAMPLE_LENGTH", left.getName (), Long.toString (leftLength), right.getName (), Long.toString (rightLength));
            // Off by default the two samples are still combined into a stereo sample (some banks
            // contain genuine stereo pairs whose channels differ slightly in length). Only when
            // the user opts in is a length mismatch treated as "not a real pair" and the samples
            // are kept as the separate mono samples they actually are.
            if (this.settingsConfiguration.keepMismatchedStereoAsMono ())
                return false;
        }

        return true;
    }


    /**
     * Checks if all samples referenced by the given groups contain only digital silence.
     *
     * @param groups The groups which contain the samples to check
     * @return True if none of the samples contains anything audible
     */
    private static boolean containsOnlySilence (final List<IGroup> groups)
    {
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final Optional<ISampleData> sampleDataOpt = zone.getSampleData ();
                if (sampleDataOpt.isPresent () && sampleDataOpt.get () instanceof Sf2SampleData sampleData)
                {
                    final Sf2SampleDescriptor sample = sampleData.getSample ();
                    final Sf2SampleDescriptor rightSample = sampleData.getRightSample ();
                    if (!isSilence (sample) || rightSample != sample && !isSilence (rightSample))
                        return false;
                }
            }
        return true;
    }


    /**
     * Checks if the sample contains only digital silence.
     *
     * @param sample The sample to check
     * @return True if the sample does not contain anything audible
     */
    private static boolean isSilence (final Sf2SampleDescriptor sample)
    {
        final byte [] data = sample.getSampleData ();
        final int end = (int) Math.min (sample.getEnd (), data.length / 2);
        for (int i = (int) sample.getStart (); i < end; i++)
        {
            final short value = (short) (data[2 * i] & 0xFF | data[2 * i + 1] << 8);
            if (Math.abs (value) > SILENCE_THRESHOLD)
                return false;
        }
        return true;
    }


    /**
     * Checks if the pitch of the sample audio consistently contradicts the mapped root keys by a
     * whole number of octaves. Only a strong consensus is reported: at least 3 zones must be
     * measurable and at least 3/4 of them must agree on the same non-zero multiple of 12 semitones
     * (up to 2 octaves).
     *
     * @param groups The groups which contain the sample zones to check
     * @return The offset of the root keys in semitones (a positive multiple of 12 means the preset
     *         sounds that many octaves lower than played), 0 if the roots match the audio or no
     *         reliable consensus was found
     */
    private static int detectOctaveOffset (final List<IGroup> groups)
    {
        int consensus = 0;
        int consensusCount = 0;
        int measured = 0;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int keyRoot = zone.getKeyRoot ();
                if (keyRoot < 0)
                    continue;
                final Optional<ISampleData> sampleDataOpt = zone.getSampleData ();
                if (sampleDataOpt.isPresent () && sampleDataOpt.get () instanceof final Sf2SampleData sampleData)
                {
                    final Sf2SampleDescriptor sample = sampleData.getSample ();
                    final double pitch = detectPitch (sample);
                    if (pitch < 0)
                        continue;
                    measured++;
                    final double offset = keyRoot - pitch;
                    final int nearestOctave = (int) Math.round (offset / 12.0) * 12;
                    if (Math.abs (offset - nearestOctave) > 0.6 || Math.abs (nearestOctave) > 24)
                        continue;
                    if (nearestOctave == 0)
                    {
                        // The first zones already confirm that the roots match the audio
                        if (measured >= 2 && consensusCount == 0)
                            return 0;
                        continue;
                    }
                    if (consensus == 0)
                        consensus = nearestOctave;
                    if (consensus == nearestOctave)
                        consensusCount++;
                }
            }
        return measured >= 3 && consensusCount >= 3 && consensusCount * 4 >= measured * 3 ? consensus : 0;
    }


    /**
     * Detects the pitch of the sample with an auto-correlation over the loop area (or the middle of
     * the sample if it has no loop), which contains the sustained and most periodic part.
     *
     * @param sample The sample to analyze
     * @return The detected pitch as a fractional MIDI note number, -1 if the audio is not periodic
     *         enough for a reliable detection
     */
    private static double detectPitch (final Sf2SampleDescriptor sample)
    {
        final byte [] data = sample.getSampleData ();
        final long start = sample.getStart ();
        final long end = Math.min (sample.getEnd (), data.length / 2);
        long from = start + (end - start) / 3;
        final long loopStart = sample.getLoopStart ();
        if (sample.getLoopEnd () - loopStart >= 32 && loopStart >= start && loopStart < end)
            from = loopStart;
        final int length = (int) Math.min (4096, end - from);
        if (length < 512)
            return -1;

        final double [] frames = new double [length];
        double sum = 0;
        for (int i = 0; i < length; i++)
        {
            final int index = (int) (2 * (from + i));
            final short value = (short) (data[index] & 0xFF | data[index + 1] << 8);
            frames[i] = value;
            sum += value;
        }
        final double mean = sum / length;
        double energy = 0;
        for (int i = 0; i < length; i++)
        {
            frames[i] -= mean;
            energy += frames[i] * frames[i];
        }
        if (energy == 0)
            return -1;

        final long sampleRate = sample.getSampleRate ();
        final int minimumLag = Math.max (8, (int) (sampleRate / 2000));
        final int maximumLag = Math.min (length / 2, (int) (sampleRate / 25));
        if (maximumLag <= minimumLag)
            return -1;

        final double [] correlations = new double [maximumLag + 1];
        int bestLag = -1;
        double bestCorrelation = 0;
        for (int lag = minimumLag; lag <= maximumLag; lag++)
        {
            double correlation = 0;
            for (int i = 0; i + lag < length; i++)
                correlation += frames[i] * frames[i + lag];
            correlations[lag] = correlation;
            if (correlation > bestCorrelation)
            {
                bestCorrelation = correlation;
                bestLag = lag;
            }
        }
        // Reject non-periodic content like percussion or noise
        if (bestLag < 0 || bestCorrelation / energy < 0.4)
            return -1;

        // The correlation of a harmonic sound peaks at every multiple of the true period - move
        // to the shortest lag which is nearly as strong to not lock onto a sub-octave
        int lag = bestLag;
        while (lag / 2 >= minimumLag)
        {
            int candidate = -1;
            double candidateCorrelation = 0;
            for (int i = Math.max (minimumLag, lag / 2 - 2); i <= Math.min (lag / 2 + 2, maximumLag); i++)
                if (correlations[i] > candidateCorrelation)
                {
                    candidateCorrelation = correlations[i];
                    candidate = i;
                }
            if (candidate < 0 || candidateCorrelation < 0.9 * correlations[lag])
                break;
            lag = candidate;
        }

        final double frequency = (double) sampleRate / lag;
        return 69 + 12 * Math.log (frequency / 440.0) / Math.log (2);
    }


    /**
     * Create a sample zone.
     *
     * @param sample The source sample
     * @param generators All hierarchical generator values
     * @return The sample zone
     */
    private static Optional<ISampleZone> createSampleZone (final Sf2SampleDescriptor sample, final GeneratorHierarchy generators)
    {
        try
        {
            final Sf2SampleData sampleData = new Sf2SampleData (sample);
            final ISampleZone zone = new DefaultSampleZone (sample.getName (), sampleData);

            final Integer panning = generators.getSignedValue (Generator.PANNING);
            if (panning != null)
                zone.setPanning (panning.intValue () / 500.0);

            // Set the pitch
            final int overridingRootKey = generators.getUnsignedValue (Generator.OVERRIDING_ROOT_KEY).intValue ();
            final int originalPitch = sample.getOriginalPitch ();
            zone.setKeyRoot (overridingRootKey < 0 ? originalPitch : overridingRootKey);
            // The coarse tuning is a pitch offset in semi-tones and belongs to the tuning, not to
            // the root key: a positive value plays the sound higher, while a higher root key would
            // play it lower. It is added to the fine tuning exactly like the reference
            // implementation does it (see the GEN_COARSETUNE case in FluidSynth's fluid_voice.c,
            // which adds 100 times the coarse tuning and the fine tuning to the pitch)
            final int coarseTune = generators.getSignedValue (Generator.COARSE_TUNE).intValue ();
            final int fineTune = generators.getSignedValue (Generator.FINE_TUNE).intValue ();
            final int pitchCorrection = sample.getPitchCorrection ();
            final double tune = coarseTune + (pitchCorrection + (double) fineTune) / 100;
            zone.setTuning (Math.clamp (tune, -MAX_COARSE_TUNE, MAX_COARSE_TUNE));
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

            // Set the exclusive group, 0 means that the zone is not assigned to any group
            zone.setExclusiveGroup (Math.clamp (generators.getUnsignedValue (Generator.EXCLUSIVE_CLASS).intValue (), 0, 127));

            // Set play range
            zone.setStart (0);
            final Integer sampleStartOffset = generators.getSignedValue (Generator.START_ADDRS_OFFSET);
            final int sampleStartOffsetInt = sampleStartOffset == null ? 0 : sampleStartOffset.intValue ();
            final long sampleStart = sample.getStart () + sampleStartOffsetInt;
            final Integer sampleEndOffset = generators.getSignedValue (Generator.END_ADDRS_OFFSET);
            final int sampleEndOffsetInt = sampleEndOffset == null ? 0 : sampleEndOffset.intValue ();
            zone.setStop ((int) (sample.getEnd () - sampleStart + sampleEndOffsetInt));

            // Set loop, if any
            final int sampleModes = generators.getUnsignedValue (Generator.SAMPLE_MODES).intValue ();
            if ((sampleModes & 1) > 0)
            {
                final ISampleLoop sampleLoop = new DefaultSampleLoop ();
                // Sample mode 3 keeps looping while the key is held and then plays the remainder of
                // the sample on release (sustain loop); mode 1 loops continuously
                sampleLoop.setLoopUntilRelease ((sampleModes & 2) > 0);
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
            amplitudeEnvelope.setTimeKeyTracking (convertEnvelopeKeyTracking (generators.getSignedValue (Generator.KEYNUM_TO_VOL_ENV_DECAY), generators.getSignedValue (Generator.KEYNUM_TO_VOL_ENV_HOLD)));

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
                        filterEnvelope.setTimeKeyTracking (convertEnvelopeKeyTracking (generators.getSignedValue (Generator.KEYNUM_TO_MOD_ENV_DECAY), generators.getSignedValue (Generator.KEYNUM_TO_MOD_ENV_HOLD)));
                    }

                    zone.setFilter (filter);
                }
            }

            final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
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
                pitchEnvelope.setTimeKeyTracking (convertEnvelopeKeyTracking (generators.getSignedValue (Generator.KEYNUM_TO_MOD_ENV_DECAY), generators.getSignedValue (Generator.KEYNUM_TO_MOD_ENV_HOLD)));
            }

            // The vibrato low frequency oscillator maps to the pitch modulation. Its waveform is
            // always a triangle and the depth is given in cent, like the pitch envelope.
            final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
            final int vibLfoDepth = generators.getSignedValue (Generator.VIB_LFO_TO_PITCH).intValue ();
            pitchLfoModulator.setDepth (vibLfoDepth / (double) IEnvelope.MAX_ENVELOPE_DEPTH);
            if (vibLfoDepth != 0)
            {
                final ILfo pitchLfo = pitchLfoModulator.getSource ();
                // The frequency is stored in absolute cents, see the filter cutoff above
                pitchLfo.setRate (8.176 * Math.pow (2, generators.getSignedValue (Generator.FREQ_VIB_LFO).doubleValue () / 1200.0));
                pitchLfo.setDelay (convertEnvelopeTime (generators.getSignedValue (Generator.DELAY_VIB_LFO)));
            }

            return Optional.of (zone);
        }
        catch (final IOException _)
        {
            // Can never happen
            return Optional.empty ();
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


    /**
     * Convert the key number to envelope hold and decay generators into the key tracking of the
     * envelope times. The generators are given in time-cents per key number and are specified in
     * the range of [-1200..1200], therefore they are normalized by 1200. The value is the amount by
     * which the time is decreased for each key above the center key 60. A positive value shortens
     * the times towards higher keys, which is the same direction as the one of the model.
     * <p>
     * The model can only store one value for all times of an envelope. The decay generator is
     * therefore preferred and the hold generator is only used if the decay one is not set.
     *
     * @param decayValue The value of the key number to envelope decay generator, might be null
     * @param holdValue The value of the key number to envelope hold generator, might be null
     * @return The key tracking in the range of [-1..1]
     */
    private static double convertEnvelopeKeyTracking (final Integer decayValue, final Integer holdValue)
    {
        int value = decayValue == null ? 0 : decayValue.intValue ();
        if (value == 0 && holdValue != null)
            value = holdValue.intValue ();
        return Math.clamp (value / (double) Generator.MAX_KEYNUM_TO_ENV, -1.0, 1.0);
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
