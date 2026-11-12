// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.LfoIntensityConverter;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2.Kontakt2Header;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Bank;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.ExternalModulator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Group;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.InternalModulator;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.KontaktPresetAccessor;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.MultiConfiguration;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.MultiInstrument;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Program;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.Zone;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt5.ZoneLoop;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.ui.Functions;


/**
 * Abstract base class for handling NKI files in a specific format.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractKontaktFormat implements IKontaktFormat
{
    // The pitch intensity of Kontakt (1.0) covers 1 octave
    private static final double PITCH_INTENSITY_CENTS = 1200.0;
    // The pitch intensity of Kontakt (1.0) covers 1 octave, the depth of the model is relative to
    // MAX_ENVELOPE_DEPTH cents
    private static final LfoIntensityConverter PITCH_LFO_CONVERTER  = LfoIntensityConverter.linear (PITCH_INTENSITY_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH);
    private static final LfoIntensityConverter CUTOFF_LFO_CONVERTER = LfoIntensityConverter.linear (1.0);
    // The volume values of Kontakt double every 6 dB
    private static final double DB_PER_DOUBLING       = 6.0;
    private static final double MINIMUM_DB            = -150.0;
    private static final String NULL_ENTRY            = "(null)";

    protected final INotifier   notifier;
    protected boolean           isBigEndian;


    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    protected AbstractKontaktFormat (final INotifier notifier)
    {
        this.notifier = notifier;
    }


    /**
     * Fill the given multi-sample instance with data from the program.
     *
     * @param multiSample The multi-sample to fill
     * @param program The program from which to get the data
     * @param parts Parts of the name for category detection
     * @param filePaths All file paths
     * @param isMonolith True if all files are stored in a monolith
     * @throws IOException Error finding samples
     */
    public void fillInto (final IMultisampleSource multiSample, final Program program, final String [] parts, final List<String> filePaths, final boolean isMonolith) throws IOException
    {
        this.fillInto (multiSample, program, parts, filePaths, isMonolith, false);
    }


    /**
     * Fill the given multi-sample instance with data from the program.
     *
     * @param multiSample The multi-sample to fill
     * @param program The program from which to get the data
     * @param parts Parts of the name for category detection
     * @param filePaths All file paths
     * @param isMonolith True if all files are stored in a monolith
     * @param isMultiPart True if the program is part of a multi (NKM). In that case the volume and
     *            panning of the program are not applied to the zones but to the instrument of the
     *            performance
     * @throws IOException Error finding samples
     */
    public void fillInto (final IMultisampleSource multiSample, final Program program, final String [] parts, final List<String> filePaths, final boolean isMonolith, final boolean isMultiPart) throws IOException
    {
        final float instrumentVolume = isMultiPart ? 1.0f : program.getInstrumentVolume ();
        final float instrumentPan = isMultiPart ? 0.0f : program.getInstrumentPan ();

        setMetadata (multiSample, program, parts);
        if (program.getMaxVoices () > 0)
            multiSample.setPolyphony (program.getMaxVoices ());

        final List<File> files = this.lookupFiles (filePaths, multiSample.getSourceFile ().getParent (), isMonolith);
        final Map<Integer, Pair<IGroup, Group>> indexedGroups = createGroups (program);
        // The soloed state of the groups is only in effect if the program has group solo enabled;
        // otherwise the flags of the groups are left-overs of a previously active solo
        final boolean hasSoloedGroup = program.isGroupSolo () && program.getGroups ().stream ().anyMatch (Group::isSoloed);
        for (final Zone kontaktZone: program.getZones ())
        {
            // Zones without a sample file might be present
            if (kontaktZone.getFilenameId () < 0)
                continue;

            final Integer groupIndex = Integer.valueOf (kontaktZone.getGroupIndex ());
            final Pair<IGroup, Group> groupPair = indexedGroups.get (groupIndex);
            if (groupPair == null)
                throw new IOException (Functions.getMessage ("IDS_NKI5_MISSING_GROUP", groupIndex.toString ()));

            // A muted group does not sound; if any group is soloed only the soloed groups sound.
            // Without this, alternate articulations stored in muted groups stack onto the active
            // ones
            final Group zoneKontaktGroup = groupPair.getValue ();
            if (hasSoloedGroup ? !zoneKontaktGroup.isSoloed () : zoneKontaktGroup.isMuted ())
                continue;

            final IGroup group = groupPair.getKey ();
            final Group kontaktGroup = groupPair.getValue ();

            final ISampleZone zone = this.createZone (kontaktZone, files);
            group.addSampleZone (zone);

            zone.setStart (kontaktZone.getSampleStart ());
            zone.setStop (kontaktZone.getNumFrames () - kontaktZone.getSampleEnd ());

            zone.setKeyLow (kontaktZone.getLowKey ());
            zone.setKeyHigh (kontaktZone.getHighKey ());
            final int rootKey = kontaktZone.getRootKey ();
            zone.setKeyRoot (rootKey);

            final float volume = instrumentVolume * kontaktGroup.getVolume () * kontaktZone.getZoneVolume ();
            zone.setGain (valueToDb (volume));
            zone.setPanning (Math.clamp (instrumentPan + kontaktGroup.getPan () + kontaktZone.getZonePan (), -1, 1));

            zone.setTuning (calculateTune (kontaktZone.getZoneTune (), kontaktGroup.getTune (), program.getInstrumentTune ()));
            zone.setKeyTracking (kontaktGroup.isKeyTracking () ? 1 : 0);

            zone.setVelocityLow (kontaktZone.getLowVelocity ());
            zone.setVelocityHigh (kontaktZone.getHighVelocity ());

            zone.setNoteCrossfadeLow (kontaktZone.getFadeLowKey ());
            zone.setNoteCrossfadeHigh (kontaktZone.getFadeHighKey ());
            zone.setVelocityCrossfadeLow (kontaktZone.getFadeLowVelocity ());
            zone.setVelocityCrossfadeHigh (kontaktZone.getFadeHighVelocity ());

            // Only on a group level...
            zone.setReversed (kontaktGroup.isReverse ());

            // Voice group 0 is the instrument, the others are used as the exclusive group
            zone.setExclusiveGroup (Math.max (0, kontaktGroup.getVoiceGroupIdx ()));

            // The cycle class is the zero based position in the round-robin cycle, the model
            // starts with 1
            final int startCriteriaMode = kontaktGroup.getStartCriteriaMode ();
            if (startCriteriaMode == Group.START_MODE_CYCLE_ROUND_ROBIN || startCriteriaMode == Group.START_MODE_CYCLE_RANDOM)
            {
                zone.setPlayLogic (startCriteriaMode == Group.START_MODE_CYCLE_RANDOM ? PlayLogic.RANDOM : PlayLogic.ROUND_ROBIN);
                final int cycleClass = kontaktGroup.getCycleClass ();
                if (cycleClass >= 0)
                    zone.setSequencePosition (cycleClass + 1);
            }

            // The filter is shared by all zones of the group
            final IFilter filter = kontaktGroup.getFilter ().orElse (null);
            if (filter != null)
                zone.setFilter (filter);

            // Set pitch by pitch-bend, amplitude by velocity and the filter cutoff modulation
            boolean foundAmpVelocity = false;
            for (final ExternalModulator modulator: kontaktGroup.getExternalModulators ())
            {
                final long sourceType = modulator.getSourceType ();
                final boolean isVelocity = sourceType == ExternalModulator.SOURCE_VELOCITY || sourceType == ExternalModulator.SOURCE_VELOCITY2;
                switch (modulator.getTargetName ())
                {
                    case ExternalModulator.TARGET_PITCH:
                        if (sourceType == ExternalModulator.SOURCE_PITCHBEND)
                        {
                            final int cents = Math.round (modulator.getIntensity () * 12) * 100;
                            zone.setBendUp (cents);
                            zone.setBendDown (-cents);
                        }
                        break;

                    case ExternalModulator.TARGET_VOLUME:
                        if (isVelocity)
                        {
                            zone.getAmplitudeVelocityModulator ().setDepth (modulator.getIntensity ());
                            // The volume of Kontakt maps its normalized value to decibels with
                            // 60*log10(x). The velocity modulation therefore scales the amplitude
                            // with x^3, which is a convex curve (+1)
                            zone.getAmplitudeVelocityModulator ().setCurve (1);
                            foundAmpVelocity = true;
                        }
                        break;

                    case ExternalModulator.TARGET_FILTER_CUTOFF:
                        if (filter != null)
                        {
                            final double depth = Math.clamp (modulator.getIntensity (), -1.0, 1.0);
                            if (isVelocity)
                                filter.getCutoffVelocityModulator ().setDepth (depth);
                            else if (sourceType == ExternalModulator.SOURCE_KEY_POSITION)
                                filter.setCutoffKeyTracking (depth);
                            else if (sourceType == ExternalModulator.SOURCE_MIDI_CC && modulator.getCCNumber () == 1)
                                filter.getCutoffModWheelModulator ().setDepth (depth);
                        }
                        break;

                    default:
                        // Not supported
                        break;
                }
            }
            if (!foundAmpVelocity)
                zone.getAmplitudeVelocityModulator ().setDepth (0);

            // Set amplitude and pitch modulator
            for (final InternalModulator modulator: kontaktGroup.getInternalModulators ())
            {
                final Optional<IEnvelopeModulator> volumeEnvelope = modulator.getVolumeEnvelope ();
                if (volumeEnvelope.isPresent ())
                {
                    final IEnvelopeModulator amplitudeEnvelopeModulator = zone.getAmplitudeEnvelopeModulator ();
                    final IEnvelopeModulator kontaktEnvelopeModulator = volumeEnvelope.get ();
                    amplitudeEnvelopeModulator.setDepth (kontaktEnvelopeModulator.getDepth ());
                    amplitudeEnvelopeModulator.setSource (kontaktEnvelopeModulator.getSource ());
                }
                final Optional<IEnvelopeModulator> pitchEnvelope = modulator.getPitchEnvelope ();
                if (pitchEnvelope.isPresent ())
                {
                    final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                    final IEnvelopeModulator kontaktPitchModulator = pitchEnvelope.get ();
                    // The pitch intensity of Kontakt (1.0) covers 1 octave, the depth of the model
                    // is relative to MAX_ENVELOPE_DEPTH cents
                    pitchModulator.setDepth (Math.clamp (kontaktPitchModulator.getDepth () * PITCH_INTENSITY_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH, -1.0, 1.0));
                    pitchModulator.setSource (kontaktPitchModulator.getSource ());
                }
                if (filter != null)
                {
                    final Optional<IEnvelopeModulator> cutoffEnvelope = modulator.getFilterCutoffEnvelope ();
                    if (cutoffEnvelope.isPresent ())
                    {
                        final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                        final IEnvelopeModulator kontaktCutoffModulator = cutoffEnvelope.get ();
                        cutoffModulator.setDepth (kontaktCutoffModulator.getDepth ());
                        cutoffModulator.setSource (kontaktCutoffModulator.getSource ());
                    }
                    applyLfoModulator (modulator.getFilterCutoffLfo (), filter.getCutoffLfoModulator (), CUTOFF_LFO_CONVERTER);
                }

                applyLfoModulator (modulator.getVolumeLfo (), zone.getAmplitudeLfoModulator (), LfoIntensityConverter.VOLUME);
                applyLfoModulator (modulator.getPitchLfo (), zone.getPitchLfoModulator (), PITCH_LFO_CONVERTER);
            }
            applyModWheelLfoModulation (kontaktGroup, zone, filter);

            for (final ZoneLoop zoneLoop: kontaktZone.getLoops ())
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                final int loopMode = zoneLoop.getMode ();
                // A one-shot ignores note-off events and has no loop
                if (loopMode == ZoneLoop.MODE_ONESHOT)
                    zone.setOneShot (true);
                if (loopMode == ZoneLoop.MODE_UNTIL_END || loopMode == ZoneLoop.MODE_UNTIL_RELEASE)
                {
                    loop.setType (zoneLoop.isAlternating () ? LoopType.ALTERNATING : LoopType.FORWARDS);
                    loop.setLoopUntilRelease (loopMode == ZoneLoop.MODE_UNTIL_RELEASE);
                    loop.setStart (zoneLoop.getLoopStart ());
                    // The length is the number of frames of the loop, the end of the model is
                    // inclusive
                    loop.setEnd (zoneLoop.getLoopStart () + zoneLoop.getLoopLength () - 1);
                    loop.setTuning (12.0 * (Math.log (zoneLoop.getLoopTuning ()) / Math.log (2.0)));
                    loop.setCrossfadeInSamples (zoneLoop.getCrossfadeLength ());
                    zone.addLoop (loop);
                }
            }
        }

        final List<IGroup> sampleGroups = new ArrayList<> ();
        for (final Pair<IGroup, Group> pair: indexedGroups.values ())
            sampleGroups.add (pair.getKey ());
        multiSample.setGroups (sampleGroups);
    }


    /**
     * Converts a Kontakt volume value to decibels. Kontakt doubles the value every 6 dB, e.g. 0.5
     * is -6 dB and 0.015625 is -36 dB.
     *
     * @param value The volume value
     * @return The value in decibels
     */
    public static double valueToDb (final double value)
    {
        if (value <= 0)
            return MINIMUM_DB;
        return Math.max (MINIMUM_DB, DB_PER_DOUBLING * Math.log (value) / Math.log (2));
    }


    /**
     * Converts decibels to a Kontakt volume value. Kontakt doubles the value every 6 dB.
     *
     * @param db The value in decibels
     * @return The volume value
     */
    public static double dbToValue (final double db)
    {
        return Math.pow (2, db / DB_PER_DOUBLING);
    }


    /**
     * Applies the modulation of the intensity of the oscillators by the modulation wheel. The
     * intensity of the external modulator is added to the intensity of the oscillator.
     *
     * @param kontaktGroup The group which contains the modulators
     * @param zone The zone to which to apply the modulation
     * @param filter The filter of the zone, might be null (shared by all zones of the group)
     */
    private static void applyModWheelLfoModulation (final Group kontaktGroup, final ISampleZone zone, final IFilter filter)
    {
        double volumeIntensity = 0;
        double pitchIntensity = 0;
        double cutoffIntensity = 0;
        for (final ExternalModulator extModulator: kontaktGroup.getExternalModulators ())
        {
            if (!ExternalModulator.TARGET_INTENSITY.equals (extModulator.getTargetName ()) || extModulator.getSourceType () != ExternalModulator.SOURCE_MIDI_CC || extModulator.getCCNumber () != 1)
                continue;
            for (final InternalModulator modulator: kontaktGroup.getInternalModulators ())
            {
                if (modulator.getSlotIndex () != extModulator.getTargetIndex ())
                    continue;
                final double intensity = extModulator.getIntensity ();
                if (modulator.getVolumeLfo ().isPresent ())
                    volumeIntensity += intensity;
                else if (modulator.getPitchLfo ().isPresent ())
                    pitchIntensity += intensity;
                else if (modulator.getFilterCutoffLfo ().isPresent ())
                    cutoffIntensity += intensity;
            }
        }

        applyModWheelIntensity (zone.getAmplitudeLfoModulator (), volumeIntensity, LfoIntensityConverter.VOLUME);
        applyModWheelIntensity (zone.getPitchLfoModulator (), pitchIntensity, PITCH_LFO_CONVERTER);
        if (filter != null)
            applyModWheelIntensity (filter.getCutoffLfoModulator (), cutoffIntensity, CUTOFF_LFO_CONVERTER);
    }


    /**
     * Set the depth of the modulation wheel modulator of an oscillator modulator.
     *
     * @param lfoModulator The oscillator modulator
     * @param modWheelIntensity The intensity which the modulation wheel adds in Kontakt
     * @param converter Converts the intensity to the depth
     */
    private static void applyModWheelIntensity (final ILfoModulator lfoModulator, final double modWheelIntensity, final LfoIntensityConverter converter)
    {
        if (modWheelIntensity != 0)
            lfoModulator.getDepthModWheelModulator ().setDepth (converter.toModWheelDepth (converter.toIntensity (lfoModulator.getDepth ()), modWheelIntensity));
    }


    /**
     * Copy the settings of a low frequency oscillator modulator.
     *
     * @param source The modulator to copy
     * @param destination The modulator to set
     * @param converter Converts the intensity of Kontakt to the depth
     */
    private static void applyLfoModulator (final Optional<ILfoModulator> source, final ILfoModulator destination, final LfoIntensityConverter converter)
    {
        if (source.isEmpty ())
            return;
        final ILfoModulator lfoModulator = source.get ();
        destination.setDepth (converter.toDepth (lfoModulator.getDepth ()));
        destination.setSource (lfoModulator.getSource ());
    }


    /**
     * Create a performance from the programs of a multi (NKM). The programs must have been
     * converted with isMultiPart set to true, since the volume and panning of the programs are
     * applied to the instruments. The master volume of the multi is added to the gain of all
     * instruments and the master tuning to the tuning of all sample zones. Muted instruments are
     * not added; if any instrument is soloed, only the soloed instruments are added.
     *
     * @param sourceFile The source file of the multi
     * @param presetAccessor Contains the read multi-configuration and bank
     * @param sources The converted programs with their source program
     * @return The performance or empty if the multi contains no multi-configuration
     */
    protected Optional<IPerformanceSource> createPerformanceSource (final File sourceFile, final KontaktPresetAccessor presetAccessor, final List<Pair<IMultisampleSource, Program>> sources)
    {
        final MultiConfiguration multiConfiguration = presetAccessor.getMultiConfiguration ();
        if (multiConfiguration == null)
            return Optional.empty ();

        double masterGain = 0;
        double masterTuning = 0;
        final Optional<Bank> bank = presetAccessor.getBank ();
        if (bank.isPresent ())
        {
            masterGain = valueToDb (bank.get ().getMasterVolume ());
            // The tuning is stored as a frequency ratio
            masterTuning = 12.0 * Math.log (bank.get ().getMasterTune ()) / Math.log (2);
        }

        final List<MultiInstrument> multiInstruments = multiConfiguration.getMultiInstruments ();
        final boolean hasSoloedInstrument = multiInstruments.stream ().anyMatch (MultiInstrument::isSoloed);

        final IPerformanceSource performanceSource = new DefaultPerformanceSource ();
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile));
        for (final Pair<IMultisampleSource, Program> source: sources)
        {
            final IMultisampleSource multisampleSource = source.getKey ();
            final Program program = source.getValue ();

            // The slot indices are not continuous
            final int slotIndex = program.getSlotIndex ();
            final MultiInstrument multiInstrument = slotIndex >= 0 && slotIndex < multiInstruments.size () ? multiInstruments.get (slotIndex) : null;
            if (multiInstrument != null && (hasSoloedInstrument ? !multiInstrument.isSoloed () : multiInstrument.isMuted ()))
            {
                this.notifier.log ("IDS_NKM_INSTRUMENT_MUTED", program.getName ());
                continue;
            }

            // The MIDI channel is stored as 0 = Omni, 1 = A1 ... 64 = D16
            final int kontaktMidiChannel = multiInstrument == null ? 0 : multiInstrument.getMidiChannel ();
            final int midiChannel = kontaktMidiChannel <= 0 ? IInstrumentSource.MIDI_CHANNEL_OMNI : kontaktMidiChannel - 1;
            final IInstrumentSource instrumentSource = new DefaultInstrumentSource (multisampleSource, midiChannel);
            instrumentSource.setClipKeyLow (program.getClipKeyLow ());
            instrumentSource.setClipKeyHigh (program.getClipKeyHigh ());
            instrumentSource.setTranspose (program.getMidiTranspose ());
            instrumentSource.setGain (valueToDb (program.getInstrumentVolume ()) + masterGain);
            instrumentSource.setPanning (Math.clamp (program.getInstrumentPan (), -1.0, 1.0));

            if (masterTuning != 0)
                for (final IGroup group: multisampleSource.getGroups ())
                    for (final ISampleZone zone: group.getSampleZones ())
                        zone.setTuning (zone.getTuning () + masterTuning);

            performanceSource.addInstrument (instrumentSource);
        }
        return Optional.of (performanceSource);
    }


    private static void setMetadata (final IMultisampleSource source, final Program program, final String [] parts)
    {
        source.setName (program.getName ());

        final IMetadata metadata = source.getMetadata ();

        final String instrumentAuthor = program.getInstrumentAuthor ();
        if (instrumentAuthor != null && !instrumentAuthor.isBlank () && !NULL_ENTRY.equals (instrumentAuthor))
            metadata.setCreator (instrumentAuthor);
        final StringBuilder description = new StringBuilder ();
        final String instrumentCredits = program.getInstrumentCredits ();
        if (instrumentCredits != null && !instrumentCredits.isBlank () && !NULL_ENTRY.equals (instrumentCredits))
            description.append (instrumentCredits.trim ());
        final String instrumentURL = program.getInstrumentURL ();
        if (instrumentURL != null && !instrumentURL.isBlank ())
        {
            if (!description.isEmpty ())
                description.append ('\n');
            description.append ("Website : ").append (instrumentURL);
        }
        if (!description.isEmpty ())
            metadata.setDescription (description.toString ());

        // The 3 instrument categories use the same lists as the Kontakt 2 header: the 1st is the
        // instrument type, the 2nd and 3rd are taken as keywords
        final int [] categories = program.getInstrumentCategories ();
        final Set<String> keywords = new LinkedHashSet<> ();
        for (int i = 1; i < 3; i++)
        {
            final String keyword = Kontakt2Header.getCategoryName (i + 1, categories[i]);
            if (keyword != null)
                keywords.add (keyword);
        }
        if (!keywords.isEmpty ())
            metadata.setKeywords (keywords.toArray (new String [keywords.size ()]));
        final String instrumentType = Kontakt2Header.getCategoryName (1, categories[0]);

        // The icons 'New' (the default) and 'Wave' (a waveform, the generic icon of a sampled
        // sound)
        // say nothing about the category, which is then detected from the names instead
        final String instrumentIconName = program.getInstrumentIconName ();
        if (instrumentIconName == null || instrumentIconName.isBlank () || "New".equals (instrumentIconName) || "Wave".equals (instrumentIconName))
        {
            if (instrumentType != null)
                metadata.setCategory (TagDetector.detectCategory (instrumentType.split ("[ /]")));
            else
                metadata.setCategory (TagDetector.detectCategory (Collections.singletonList (parts[0]), Arrays.asList (AudioFileUtils.getFolderNames (parts)), false));
        }
        else
            metadata.setCategory (TagDetector.detectCategory (instrumentIconName.split (" ")));
    }


    private static Map<Integer, Pair<IGroup, Group>> createGroups (final Program program)
    {
        final Map<Integer, Pair<IGroup, Group>> map = new TreeMap<> ();
        final List<Group> groups = program.getGroups ();
        for (int i = 0; i < groups.size (); i++)
        {
            final Group kontaktGroup = groups.get (i);
            final IGroup group = new DefaultGroup ();
            group.setName (kontaktGroup.getName ());
            if (kontaktGroup.isReleaseTrigger ())
                group.setTrigger (TriggerType.RELEASE);

            // Additionally record the group values on the group. Note that they are (and must stay)
            // multiplied into each of the sample zones as well, see fillInto. A creator must
            // therefore apply either the group or the zone value but never both.
            final double groupVolume = kontaktGroup.getVolume ();
            if (groupVolume != 1)
                group.setGain (valueToDb (groupVolume));
            // The panning is already stored in the range of [-1..1]
            final double groupPan = kontaktGroup.getPan ();
            if (groupPan != 0)
                group.setPanning (Math.clamp (groupPan, -1, 1));
            // The tuning is stored logarithmically as a frequency ratio, 1 is the neutral value
            final double groupTune = kontaktGroup.getTune ();
            if (groupTune > 0 && groupTune != 1)
                group.setTuning (Math.round (12.0 * Math.log (groupTune) / Math.log (2) * 100000) / 100000.0);

            map.put (Integer.valueOf (i), new Pair<> (group, kontaktGroup));
        }
        return map;
    }


    private List<File> lookupFiles (final List<String> filePaths, final String sourceFilePath, final boolean isMonolith)
    {
        int missingFiles = 0;

        final List<File> files = new ArrayList<> ();
        for (final String filename: filePaths)
        {
            File sampleFile = new File (filename);
            if ((sampleFile.isAbsolute () || filename.startsWith ("/Volumes")) && !sampleFile.exists ())
                sampleFile = new File (sourceFilePath, sampleFile.getName ());
            else
            {
                sampleFile = new File (sourceFilePath, filename);
                if (!sampleFile.exists ())
                    sampleFile = new File (sourceFilePath, sampleFile.getName ());
            }

            if (!sampleFile.exists ())
                missingFiles++;

            files.add (sampleFile);
        }

        // Only search for missing files, if all of them are missing!
        if (isMonolith || missingFiles != files.size ())
            return files;

        final List<File> lookedupFiles = new ArrayList<> ();
        File previousFolder = null;

        for (final File sampleFile: files)
        {
            // Find the sample file starting 2 folders up
            final int height = 2;
            final File file = AbstractDetector.findSampleFile (this.notifier, sampleFile.getParentFile (), previousFolder, sampleFile.getName (), height);
            if (file != null && file.exists ())
            {
                lookedupFiles.add (file);
                previousFolder = file.getParentFile ();
            }
            else
                lookedupFiles.add (sampleFile);
        }
        return lookedupFiles;
    }


    private ISampleZone createZone (final Zone zone, final List<File> files) throws IOException
    {
        final int filenameId = zone.getFilenameId ();
        if (filenameId < 0 || filenameId >= files.size ())
            throw new IOException (Functions.getMessage ("IDS_NKI5_WRONG_FILE_INDEX", Integer.toString (filenameId)));

        // Check if it is an absolute path, try to find the sample file...
        final File sampleFile = files.get (filenameId);
        // Ignore non-existing files since it might be in a monolith
        final ISampleData sampleData = sampleFile.exists () ? AbstractDetector.createSampleData (sampleFile, this.notifier) : null;
        return new DefaultSampleZone (FileUtils.getNameWithoutType (sampleFile), sampleData);
    }


    private static double calculateTune (final double zoneTune, final double groupTune, final double progTune)
    {
        // All three tune values are stored logarithmically
        final double value = 12.0 * Math.log (zoneTune * groupTune * progTune) / Math.log (2);
        return Math.round (value * 100000) / 100000.0;
    }
}
