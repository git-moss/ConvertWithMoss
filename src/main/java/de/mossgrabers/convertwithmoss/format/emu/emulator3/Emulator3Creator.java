// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.emu.emulator4.Emu3DiskImage;
import de.mossgrabers.tools.FileUtils;


/**
 * Creator for E-mu EIII bank files (*.e3x, *.esi). Every group of a multi-sample source becomes one
 * preset which uses the primary layer of its note zones; the presets of a source are chained with
 * the preset link, which is how the samplers stack more than one velocity layer. A library collects
 * all sources into a single bank, or - when a CD-ROM image is written - one bank per source into a
 * single image, which is how a converted library reaches these samplers. Samples are stored as
 * 16-bit mono or stereo PCM and are de-duplicated by their content. The format was
 * reverse-engineered by the emu3bm project, see documentation/design/EIII_FORMAT.md. Written banks
 * have not been verified on hardware yet but round-trip through {@link Emulator3Detector} and match
 * the structure of the E-mu library CD-ROMs.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3Creator extends AbstractCreator<Emulator3CreatorUI>
{
    /** The samplers play back at most at this rate. */
    private static final int                    MAX_SAMPLE_RATE     = Emulator3Constants.MAX_SAMPLE_RATE;
    /** The sample memory of the samplers is limited to 128 MB. */
    private static final long                   MAX_BANK_SIZE       = 128L * 1024 * 1024;
    /** The samplers refuse to edit a loop which is shorter than this. */
    private static final int                    MINIMUM_LOOP_LENGTH = 10;
    /** The number of frames at the start and the end of a sample which have to be silent. */
    private static final int                    NUM_SILENT_FRAMES   = 2;
    /** The lowest key of the E-mu keyboard. */
    private static final int                    LOWEST_KEY          = Emulator3Constants.KEY_OFFSET;
    /** The highest key of the E-mu keyboard. */
    private static final int                    HIGHEST_KEY         = Emulator3Constants.KEY_OFFSET + Emulator3Constants.NUM_KEYS - 1;

    private static final DestinationAudioFormat DESTINATION_FORMAT  = new DestinationAudioFormat (new int []
    {
        16
    }, MAX_SAMPLE_RATE, false);


    /** Holds one de-duplicated sample to be written into the bank. */
    private static class Sample
    {
        String  name;
        byte [] left;
        byte [] right;
        int     sampleRate;
        boolean hasLoop;
        boolean loopInRelease;
        int     loopStart;
        int     loopEnd;


        int getNumFrames ()
        {
            return this.left.length / 2;
        }


        boolean isStereo ()
        {
            return this.right != null;
        }


        int getSize ()
        {
            return Emulator3Constants.SAMPLE_HEADER_SIZE + this.left.length + (this.right == null ? 0 : this.right.length);
        }
    }


    /** Holds one preset to be written into the bank; it carries the zones of one group. */
    private static class Preset
    {
        String  name;
        byte [] data;
        /** The 1-based number of the preset which is layered on top of this one, 0 for none. */
        int     link;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator3Creator (final INotifier notifier)
    {
        super ("E-mu Emulator III", "EIII", notifier, new Emulator3CreatorUI ("EIII"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.writeBank (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPresetLibraries ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public void createPresetLibrary (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String libraryName) throws IOException
    {
        if (!multisampleSources.isEmpty ())
            this.writeBank (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Write the given sources, either as one bank file or as a CD-ROM image which holds one bank
     * per source.
     *
     * @param destinationFolder Where to create the file
     * @param multisampleSources The sources to convert
     * @param name The name of the bank or of the image
     * @throws IOException Could not write the bank or the image
     */
    private void writeBank (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final Emulator3BankFormat bankFormat = this.settingsConfiguration.getTargetFormat ();
        final boolean writeCdImage = this.settingsConfiguration.writeCdImage ();
        final String safeName = FileUtils.createSafeFilename (name);
        final File outputFile = this.createUniqueFilename (destinationFolder, safeName, writeCdImage ? "iso" : bankFormat.getFileEnding ().substring (1));
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        if (writeCdImage)
        {
            this.writeImage (outputFile, multisampleSources, bankFormat, safeName);
            return;
        }

        final Optional<byte []> bank = this.createBankData (multisampleSources, bankFormat, safeName);
        if (bank.isEmpty ())
            return;
        try (final OutputStream out = new BufferedOutputStream (Files.newOutputStream (outputFile.toPath ())))
        {
            out.write (bank.get ());
        }
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Write a CD-ROM image which holds one bank for each of the given sources. The sources are not
     * merged into one bank: a bank is what the sampler loads into its memory, which is far smaller
     * than a converted library - the image is the library and each of its banks is loaded on its
     * own, exactly like on the library CD-ROMs of the samplers.
     *
     * @param outputFile The image file to write
     * @param multisampleSources The sources to convert
     * @param bankFormat The format of the banks
     * @param imageName The name of the image, which also names its folder
     * @throws IOException Could not write the image
     */
    private void writeImage (final File outputFile, final List<IMultisampleSource> multisampleSources, final Emulator3BankFormat bankFormat, final String imageName) throws IOException
    {
        final int maximumBanks = Emu3DiskImage.ImageLayout.EMULATOR_3.getMaximumFiles ();
        final List<Emu3DiskImage.ImageFile> imageFiles = new ArrayList<> ();
        final Set<String> usedBankNames = new HashSet<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (imageFiles.size () >= maximumBanks)
            {
                this.notifier.logError ("IDS_EIII_TOO_MANY_BANKS", Integer.toString (maximumBanks), multisampleSource.getName ());
                break;
            }

            final String bankName = createUniqueName (multisampleSource.getName (), usedBankNames);
            final Optional<byte []> bank = this.createBankData (List.of (multisampleSource), bankFormat, bankName);
            if (bank.isPresent ())
                imageFiles.add (new Emu3DiskImage.ImageFile (bankName, bank.get ()));
        }

        if (imageFiles.isEmpty ())
            return;
        Emu3DiskImage.writeImage (outputFile, imageFiles, Emu3DiskImage.ImageLayout.EMULATOR_3, imageName);
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create one bank which contains all presets and samples of the given sources.
     *
     * @param multisampleSources The sources to convert
     * @param bankFormat The format of the bank
     * @param name The name of the bank
     * @return The bank or empty if it has no presets or got too large
     * @throws IOException Could not convert the sample data
     */
    private Optional<byte []> createBankData (final List<IMultisampleSource> multisampleSources, final Emulator3BankFormat bankFormat, final String name) throws IOException
    {
        final List<Sample> samples = new ArrayList<> ();
        final Map<Object, Integer> sampleIndicesByContent = new HashMap<> ();
        final Set<String> usedSampleNames = new HashSet<> ();
        final List<Preset> presets = new ArrayList<> ();

        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            // Samples above the maximum rate are down-sampled - move the positions with them
            recalculateAllSamplePositions (multisampleSource, MAX_SAMPLE_RATE, true);

            final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
            final int firstPresetOfSource = presets.size ();
            for (final IGroup group: groups)
            {
                if (presets.size () >= bankFormat.getMaxPresets ())
                {
                    this.notifier.logError ("IDS_EIII_TOO_MANY_PRESETS", multisampleSource.getName ());
                    break;
                }
                final Preset preset = this.createPreset (multisampleSource, group, groups.size () > 1 ? presets.size () - firstPresetOfSource + 1 : 0, bankFormat, samples, sampleIndicesByContent, usedSampleNames);
                if (preset != null)
                    presets.add (preset);
            }

            // Chain the presets of one source so that all of its groups play together
            for (int i = firstPresetOfSource; i < presets.size () - 1; i++)
                presets.get (i).link = i + 2;

            if (presets.size () == firstPresetOfSource)
                this.notifier.logError ("IDS_EIII_NO_ZONES", multisampleSource.getName ());
        }

        if (presets.isEmpty ())
            return Optional.empty ();
        return this.createBank (bankFormat, name, presets, samples);
    }


    /**
     * Create one preset from a group of a multi-sample source.
     *
     * @param multisampleSource The source of the group
     * @param group The group to convert
     * @param layerNumber The 1-based number of the layer, 0 if the source has only one group
     * @param bankFormat The format of the bank
     * @param samples The samples collected so far
     * @param sampleIndicesByContent The 1-based indices of the collected samples by their content
     * @param usedSampleNames All sample names used so far
     * @return The preset or null if the group has no usable zones
     * @throws IOException Could not convert the sample data
     */
    private Preset createPreset (final IMultisampleSource multisampleSource, final IGroup group, final int layerNumber, final Emulator3BankFormat bankFormat, final List<Sample> samples, final Map<Object, Integer> sampleIndicesByContent, final Set<String> usedSampleNames) throws IOException
    {
        // The key mappings are assigned in the order of the zones, so a later zone wins the keys
        // which it shares with an earlier one - the samplers can only map a key to one zone
        final List<ISampleZone> zones = new ArrayList<> (group.getSampleZones ());
        zones.sort (Comparator.comparingInt (ISampleZone::getKeyLow));

        final int [] keyMappings = new int [Emulator3Constants.NUM_KEYS];
        java.util.Arrays.fill (keyMappings, Emulator3Constants.UNUSED);
        final List<ISampleZone> mappedZones = new ArrayList<> ();
        final List<Integer> sampleIndices = new ArrayList<> ();

        for (final ISampleZone zone: zones)
        {
            final int keyLow = Math.max (LOWEST_KEY, zone.getKeyLow ());
            final int keyHigh = Math.min (HIGHEST_KEY, zone.getKeyHigh ());
            if (keyLow > keyHigh)
            {
                this.notifier.logError ("IDS_EIII_ZONE_OUT_OF_RANGE", zone.getName (), multisampleSource.getName ());
                continue;
            }
            final int sampleIndex = this.addSample (zone, bankFormat, samples, sampleIndicesByContent, usedSampleNames);
            if (sampleIndex == 0)
                continue;

            final int noteZoneIndex = mappedZones.size ();
            for (int key = keyLow; key <= keyHigh; key++)
                keyMappings[key - Emulator3Constants.KEY_OFFSET] = noteZoneIndex;
            mappedZones.add (zone);
            sampleIndices.add (Integer.valueOf (sampleIndex));
        }

        // Drop the zones which lost all of their keys to a later zone and renumber the rest
        final List<Integer> survivors = new ArrayList<> ();
        final int [] renumbered = new int [mappedZones.size ()];
        java.util.Arrays.fill (renumbered, Emulator3Constants.UNUSED);
        for (int key = 0; key < keyMappings.length; key++)
        {
            final int noteZoneIndex = keyMappings[key];
            if (noteZoneIndex == Emulator3Constants.UNUSED)
                continue;
            if (renumbered[noteZoneIndex] == Emulator3Constants.UNUSED)
            {
                renumbered[noteZoneIndex] = survivors.size ();
                survivors.add (Integer.valueOf (noteZoneIndex));
            }
            keyMappings[key] = renumbered[noteZoneIndex];
        }
        if (survivors.isEmpty ())
            return null;

        final int numNoteZones = survivors.size ();
        final Preset preset = new Preset ();
        preset.name = createPresetName (multisampleSource, layerNumber);
        preset.data = new byte [Emulator3Constants.PRESET_SIZE + numNoteZones * Emulator3Constants.NOTE_ZONE_SIZE + numNoteZones * Emulator3Constants.ZONE_SIZE];

        Emulator3Constants.encodeName (preset.data, 0, preset.name);
        System.arraycopy (Emulator3Constants.DEFAULT_REALTIME_CONTROLS, 0, preset.data, Emulator3Constants.NAME_LENGTH, Emulator3Constants.DEFAULT_REALTIME_CONTROLS.length);
        preset.data[Emulator3Constants.PRESET_PITCH_BEND_RANGE] = (byte) getPitchBendRange (mappedZones);
        preset.data[Emulator3Constants.PRESET_NUM_NOTE_ZONES] = (byte) numNoteZones;
        for (int key = 0; key < keyMappings.length; key++)
            preset.data[Emulator3Constants.PRESET_KEY_MAPPINGS + key] = (byte) keyMappings[key];

        // The velocity range of a layer belongs to the preset which holds it
        int velocityLow = 127;
        int velocityHigh = 1;
        for (final Integer survivor: survivors)
        {
            final ISampleZone zone = mappedZones.get (survivor.intValue ());
            velocityLow = Math.min (velocityLow, Math.clamp (zone.getVelocityLow (), 1, 127));
            velocityHigh = Math.max (velocityHigh, Math.clamp (zone.getVelocityHigh (), 1, 127));
        }
        if (velocityLow > 1 || velocityHigh < 127)
        {
            preset.data[Emulator3Constants.PRESET_VELOCITY_PRIMARY_LOW] = (byte) velocityLow;
            preset.data[Emulator3Constants.PRESET_VELOCITY_PRIMARY_HIGH] = (byte) velocityHigh;
        }

        final int noteZoneOffset = Emulator3Constants.PRESET_SIZE;
        final int zoneOffset = noteZoneOffset + numNoteZones * Emulator3Constants.NOTE_ZONE_SIZE;
        for (int i = 0; i < numNoteZones; i++)
        {
            final int index = survivors.get (i).intValue ();
            // Only the primary layer is used; the secondary one would have to share the key
            // boundaries of the primary layer, which the groups of a source do not have to
            preset.data[noteZoneOffset + i * Emulator3Constants.NOTE_ZONE_SIZE + Emulator3Constants.NOTE_ZONE_PRIMARY] = (byte) i;
            preset.data[noteZoneOffset + i * Emulator3Constants.NOTE_ZONE_SIZE + Emulator3Constants.NOTE_ZONE_SECONDARY] = (byte) Emulator3Constants.UNUSED;
            createZone (preset.data, zoneOffset + i * Emulator3Constants.ZONE_SIZE, mappedZones.get (index), sampleIndices.get (index).intValue (), bankFormat);
        }
        return preset;
    }


    /**
     * Get the name of a preset. A source with more than one group needs one preset per group, which
     * are numbered like the linked presets of the samplers. The detector puts the bank of a source
     * in front of its name, so that a preset which is only named after its articulation stays
     * identifiable. The written file is that bank, so its preset names do not need to repeat it -
     * and could not: they hold 16 characters, which the bank alone would fill.
     *
     * @param multisampleSource The multi-sample source
     * @param layerNumber The 1-based number of the layer, 0 if the source has only one group
     * @return The name
     */
    private static String createPresetName (final IMultisampleSource multisampleSource, final int layerNumber)
    {
        String name = multisampleSource.getName ();
        final String bank = multisampleSource.getMetadata ().getDescription ();
        if (bank != null && !bank.isBlank ())
        {
            final String prefix = bank + " - ";
            if (name.startsWith (prefix) && name.length () > prefix.length ())
                name = name.substring (prefix.length ());
        }
        if (layerNumber == 0)
            return name;
        final String suffix = " L" + (layerNumber < 10 ? "0" : "") + layerNumber;
        final int maxLength = Emulator3Constants.NAME_LENGTH - suffix.length ();
        return (name.length () > maxLength ? name.substring (0, maxLength) : name) + suffix;
    }


    /**
     * Get the pitch bend range in semi-tones which the zones of a preset use.
     *
     * @param zones The zones
     * @return The range in semi-tones
     */
    private static int getPitchBendRange (final List<ISampleZone> zones)
    {
        for (final ISampleZone zone: zones)
            if (zone.getBendUp () > 0)
                return Math.clamp (zone.getBendUp () / 100, 0, 36);
        return 2;
    }


    /**
     * Fill one zone of a preset.
     *
     * @param data The content of the preset
     * @param offset The offset of the zone
     * @param zone The zone to convert
     * @param sampleIndex The 1-based index of the sample of the zone
     * @param bankFormat The format of the bank
     */
    private static void createZone (final byte [] data, final int offset, final ISampleZone zone, final int sampleIndex, final Emulator3BankFormat bankFormat)
    {
        final boolean isEsi = bankFormat == Emulator3BankFormat.ESI_32_V3;

        data[offset + Emulator3Constants.ZONE_ORIGINAL_KEY] = (byte) (Math.clamp (zone.getKeyRoot () < 0 ? zone.getKeyLow () : zone.getKeyRoot (), LOWEST_KEY, HIGHEST_KEY) - Emulator3Constants.KEY_OFFSET);
        Emulator3Constants.putU16 (data, offset + Emulator3Constants.ZONE_SAMPLE_INDEX, sampleIndex);
        data[offset + Emulator3Constants.ZONE_PARAMETER_A] = (byte) (isEsi ? 0 : Emulator3Constants.PARAMETER_A_EMULATOR_3X);

        writeEnvelope (data, offset + Emulator3Constants.ZONE_VCA_ENVELOPE, zone.getAmplitudeEnvelopeModulator ().getSource ());

        // The tuning of a zone only covers one semitone up and down
        data[offset + Emulator3Constants.ZONE_NOTE_TUNING] = (byte) Math.clamp (Math.round (zone.getTuning () * 100.0 / 1.5625), -64, 64);
        final double gain = zone.getGain ();
        final int level = gain <= Double.NEGATIVE_INFINITY ? 0 : (int) Math.clamp (Math.round (Emulator3Constants.FULL_LEVEL * Math.pow (10, gain / 20.0)), 0, Emulator3Constants.FULL_LEVEL);
        data[offset + Emulator3Constants.ZONE_VCA_LEVEL] = (byte) level;
        data[offset + Emulator3Constants.ZONE_VCA_PAN] = (byte) Emulator3Constants.getPanningValue (zone.getPanning ());
        data[offset + Emulator3Constants.ZONE_VELOCITY_TO_VCA_LEVEL] = (byte) Math.clamp (Math.round (zone.getAmplitudeVelocityModulator ().getDepth () * 127), -127, 127);

        // The pitch envelope of the model is written as the auxiliary envelope
        final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
        final double pitchDepth = pitchModulator.getDepth ();
        if (pitchDepth != 0)
        {
            writeEnvelope (data, offset + Emulator3Constants.ZONE_AUX_ENVELOPE, pitchModulator.getSource ());
            data[offset + Emulator3Constants.ZONE_AUX_ENVELOPE_AMOUNT] = (byte) Math.clamp (Math.round (pitchDepth * 127), -127, 127);
            data[offset + Emulator3Constants.ZONE_AUX_ENVELOPE_DESTINATION] = 1;
        }
        else
            writeEnvelope (data, offset + Emulator3Constants.ZONE_AUX_ENVELOPE, null);

        final Optional<IFilter> optionalFilter = zone.getFilter ();
        if (optionalFilter.isPresent ())
        {
            final IFilter filter = optionalFilter.get ();
            data[offset + Emulator3Constants.ZONE_VCF_CUTOFF] = (byte) Emulator3Constants.getCutoffValue (filter.getCutoff ());
            final int resonance = Math.clamp (Math.round (filter.getResonance () * 127), 0, 127);
            data[offset + Emulator3Constants.ZONE_VCF_Q] = (byte) (resonance | (isEsi ? Emulator3Constants.Q_REALTIME_ENABLE : 0));
            data[offset + Emulator3Constants.ZONE_VCF_TYPE_LFO_SHAPE] = (byte) (Emulator3Constants.getFilterTypeValue (filter.getType (), filter.getPoles (), bankFormat) << 3);
            data[offset + Emulator3Constants.ZONE_VCF_TRACKING] = (byte) Math.clamp (Math.round (filter.getCutoffKeyTracking () * 127 / 2.0), -127, 127);
            data[offset + Emulator3Constants.ZONE_VELOCITY_TO_VCF_CUTOFF] = (byte) Math.clamp (Math.round (filter.getCutoffVelocityModulator ().getDepth () * 127), -127, 127);

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            final double cutoffDepth = cutoffModulator.getDepth ();
            if (cutoffDepth != 0)
            {
                writeEnvelope (data, offset + Emulator3Constants.ZONE_VCF_ENVELOPE, cutoffModulator.getSource ());
                data[offset + Emulator3Constants.ZONE_VCF_ENVELOPE_AMOUNT] = (byte) Math.clamp (Math.round (cutoffDepth * 127), -127, 127);
            }
            else
                writeEnvelope (data, offset + Emulator3Constants.ZONE_VCF_ENVELOPE, null);
        }
        else
        {
            // Without a filter write the bypass state: fully open, no resonance, no key tracking,
            // which is what the detector reads back as 'no filter'
            data[offset + Emulator3Constants.ZONE_VCF_CUTOFF] = (byte) Emulator3Constants.DEFAULT_CUTOFF;
            data[offset + Emulator3Constants.ZONE_VCF_Q] = (byte) (isEsi ? Emulator3Constants.Q_REALTIME_ENABLE : 0);
            data[offset + Emulator3Constants.ZONE_VCF_TRACKING] = Emulator3Constants.NO_VCF_TRACKING;
            writeEnvelope (data, offset + Emulator3Constants.ZONE_VCF_ENVELOPE, null);
        }

        data[offset + Emulator3Constants.ZONE_REALTIME_ENABLE] = (byte) Emulator3Constants.REALTIME_ENABLE_ALL;
        // Bit 0 is always set by the samplers, its meaning is unknown
        int flags = 0x01;
        if (zone.getKeyTracking () == 0)
            flags |= Emulator3Constants.ZONE_FLAG_NON_TRANSPOSE;
        if (zone.getLoops ().isEmpty ())
            flags |= Emulator3Constants.ZONE_FLAG_DISABLE_LOOP;
        data[offset + Emulator3Constants.ZONE_FLAGS] = (byte) flags;
    }


    /**
     * Write one of the three envelopes of a zone.
     *
     * @param data The content of the preset
     * @param offset The offset of the envelope
     * @param envelope The envelope to write, null writes a neutral envelope
     */
    private static void writeEnvelope (final byte [] data, final int offset, final IEnvelope envelope)
    {
        if (envelope == null)
        {
            data[offset + Emulator3Constants.ENVELOPE_SUSTAIN] = (byte) Emulator3Constants.FULL_LEVEL;
            return;
        }

        data[offset + Emulator3Constants.ENVELOPE_ATTACK] = (byte) getEnvelopeTimeValue (envelope.getAttackTime ());
        data[offset + Emulator3Constants.ENVELOPE_HOLD] = (byte) getEnvelopeTimeValue (envelope.getHoldTime ());
        data[offset + Emulator3Constants.ENVELOPE_DECAY] = (byte) getEnvelopeTimeValue (envelope.getDecayTime ());
        final double sustainLevel = envelope.getSustainLevel ();
        data[offset + Emulator3Constants.ENVELOPE_SUSTAIN] = (byte) (sustainLevel < 0 ? Emulator3Constants.FULL_LEVEL : (int) Math.clamp (Math.round (sustainLevel * Emulator3Constants.FULL_LEVEL), 0, Emulator3Constants.FULL_LEVEL));
        data[offset + Emulator3Constants.ENVELOPE_RELEASE] = (byte) getEnvelopeTimeValue (envelope.getReleaseTime ());
    }


    /**
     * Convert a time of an envelope stage which might not be set.
     *
     * @param seconds The time in seconds, negative if it is not set
     * @return The value of the envelope stage
     */
    private static int getEnvelopeTimeValue (final double seconds)
    {
        return seconds < 0 ? 0 : Emulator3Constants.getEnvelopeTimeValue (seconds);
    }


    /**
     * Convert the sample of a zone and add it to the samples of the bank, re-using an already added
     * sample with identical content.
     *
     * @param zone The zone
     * @param bankFormat The format of the bank
     * @param samples The samples collected so far
     * @param sampleIndicesByContent The 1-based indices of the collected samples by their content
     * @param usedSampleNames All sample names used so far
     * @return The 1-based index of the sample; 0 if the zone must be skipped
     * @throws IOException Could not convert the sample data
     */
    private int addSample (final ISampleZone zone, final Emulator3BankFormat bankFormat, final List<Sample> samples, final Map<Object, Integer> sampleIndicesByContent, final Set<String> usedSampleNames) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName ());
            return 0;
        }

        this.logResampling (zone, DESTINATION_FORMAT);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), DESTINATION_FORMAT);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        if (numChannels > 2)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MONO", Integer.toString (numChannels), zone.getName ());
            return 0;
        }
        final int sampleRate = waveFile.getFormatChunk ().getSampleRate ();
        final byte [] wavData = waveFile.getDataChunk ().getData ();
        final int numFrames = wavData.length / (2 * numChannels);
        if (numFrames <= 0)
            return 0;

        final Sample sample = new Sample ();
        sample.sampleRate = sampleRate;
        if (numChannels == 2)
        {
            // The samplers store the two channels one after the other instead of interleaved
            sample.left = new byte [numFrames * 2];
            sample.right = new byte [numFrames * 2];
            for (int i = 0; i < numFrames; i++)
            {
                sample.left[i * 2] = wavData[i * 4];
                sample.left[i * 2 + 1] = wavData[i * 4 + 1];
                sample.right[i * 2] = wavData[i * 4 + 2];
                sample.right[i * 2 + 1] = wavData[i * 4 + 3];
            }
        }
        else
            sample.left = wavData.clone ();

        // The samplers report a 'Mono Start Zero!!!' error if the first and the last two frames of
        // a sample are not silent
        silenceEdges (sample.left);
        if (sample.right != null)
            silenceEdges (sample.right);

        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                // The loop positions have to keep a distance to both ends of the sample
                int loopStart = Math.clamp (loop.getStart (), 6, Math.max (6, numFrames - 7));
                final int loopEnd = Math.clamp (loop.getEnd (), 6, Math.max (6, numFrames - 7));
                if (loopEnd - loopStart < MINIMUM_LOOP_LENGTH)
                    loopStart = Math.max (0, loopEnd - MINIMUM_LOOP_LENGTH);
                if (loopEnd > loopStart)
                {
                    sample.hasLoop = true;
                    sample.loopStart = loopStart;
                    sample.loopEnd = loopEnd;
                    sample.loopInRelease = !loop.isLoopUntilRelease ();
                }
                break;
            }

        // Re-use an already written sample with identical content and parameters, e.g. when the
        // same sample is mapped to several key ranges or velocity layers
        final Object contentKey = List.of (ByteBuffer.wrap (sample.left), ByteBuffer.wrap (sample.right == null ? new byte [0] : sample.right), Integer.valueOf (sampleRate), Boolean.valueOf (sample.hasLoop), Boolean.valueOf (sample.loopInRelease), Integer.valueOf (sample.loopStart), Integer.valueOf (sample.loopEnd));
        final Integer existingIndex = sampleIndicesByContent.get (contentKey);
        if (existingIndex != null)
            return existingIndex.intValue ();

        if (samples.size () >= bankFormat.getMaxSamples ())
        {
            this.notifier.logError ("IDS_EIII_TOO_MANY_SAMPLES", zone.getName ());
            return 0;
        }

        sample.name = createUniqueName (zone.getName (), usedSampleNames);
        samples.add (sample);
        final int index = samples.size ();
        sampleIndicesByContent.put (contentKey, Integer.valueOf (index));
        return index;
    }


    /**
     * Silence the first and the last frames of a channel.
     *
     * @param channel The 16-bit data of the channel
     */
    private static void silenceEdges (final byte [] channel)
    {
        final int numBytes = Math.min (NUM_SILENT_FRAMES * 2, channel.length);
        for (int i = 0; i < numBytes; i++)
        {
            channel[i] = 0;
            channel[channel.length - 1 - i] = 0;
        }
    }


    /**
     * Assemble the bank.
     *
     * @param bankFormat The format of the bank
     * @param name The name of the bank
     * @param presets The presets
     * @param samples The samples
     * @return The content of the bank or null if it does not fit into the sample memory
     */
    private Optional<byte []> createBank (final Emulator3BankFormat bankFormat, final String name, final List<Preset> presets, final List<Sample> samples)
    {
        int presetAreaSize = 0;
        for (final Preset preset: presets)
            presetAreaSize += preset.data.length;
        long size = Emulator3Constants.EMPTY_BANK_SIZE + (long) presetAreaSize;
        for (final Sample sample: samples)
            size += sample.getSize ();
        if (size > MAX_BANK_SIZE)
        {
            this.notifier.logError ("IDS_EIII_BANK_TOO_LARGE", Long.toString (size / (1024 * 1024)));
            return Optional.empty ();
        }

        final byte [] data = new byte [(int) size];
        System.arraycopy (Emulator3Constants.createEmptyBank (bankFormat, name), 0, data, 0, Emulator3Constants.EMPTY_BANK_SIZE);

        // The presets follow the address tables; the last entry of the table marks their end
        final int presetTable = bankFormat.getPresetTableOffset ();
        final int presetAreaOffset = bankFormat.getPresetAreaOffset ();
        int offset = presetAreaOffset;
        for (int i = 0; i < presets.size (); i++)
        {
            final Preset preset = presets.get (i);
            Emulator3Constants.putU32 (data, presetTable + i * 4, offset - (long) presetAreaOffset);
            System.arraycopy (preset.data, 0, data, offset, preset.data.length);
            if (preset.link > 0)
                Emulator3Constants.putU16 (data, offset + Emulator3Constants.PRESET_LINK, preset.link);
            offset += preset.data.length;
        }
        for (int i = presets.size (); i <= bankFormat.getMaxPresets (); i++)
            Emulator3Constants.putU32 (data, presetTable + i * 4, presetAreaSize);

        // One filler byte separates the presets from the samples
        data[offset] = (byte) bankFormat.getSampleAreaMarker ();
        offset++;

        final int sampleTable = bankFormat.getSampleTableOffset ();
        final int sampleAreaOffset = offset;
        for (int i = 0; i < samples.size (); i++)
        {
            final Sample sample = samples.get (i);
            Emulator3Constants.putU32 (data, sampleTable + i * 4, offset - sampleAreaOffset + (long) Emulator3Constants.SAMPLE_ADDRESS_OFFSET);
            writeSample (data, offset, sample, offset - sampleAreaOffset);
            offset += sample.getSize ();
        }
        // The last entry of the table points behind the last sample
        Emulator3Constants.putU32 (data, sampleTable + bankFormat.getMaxSamples () * 4, offset - sampleAreaOffset + (long) Emulator3Constants.SAMPLE_ADDRESS_OFFSET);

        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_OBJECTS, presets.size () + (long) samples.size ());
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_NEXT_PRESET, Emulator3Constants.getU32 (data, Emulator3Constants.BANK_NEXT_PRESET) + presetAreaSize);
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_NEXT_SAMPLE, offset - (long) sampleAreaOffset);
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_SELECTED_PRESET, 0);

        // The block counts of the header split the bank at the filler byte
        final int presetBlocks = (sampleAreaOffset - 1 + Emulator3Constants.BLOCK_SIZE - 1) / Emulator3Constants.BLOCK_SIZE;
        final int totalBlocks = (int) ((size + Emulator3Constants.BLOCK_SIZE - 1) / Emulator3Constants.BLOCK_SIZE);
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_PRESET_BLOCKS, presetBlocks);
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_SAMPLE_BLOCKS, totalBlocks - (long) presetBlocks);
        Emulator3Constants.putU32 (data, Emulator3Constants.BANK_TOTAL_BLOCKS, totalBlocks);
        return Optional.of (data);
    }


    /**
     * Write one sample with its header. All positions of the header are byte offsets which are
     * relative to the start of the header.
     *
     * @param data The content of the bank
     * @param offset The offset of the sample
     * @param sample The sample
     * @param memoryOffset The offset of the sample in the sample memory of the sampler
     */
    private static void writeSample (final byte [] data, final int offset, final Sample sample, final int memoryOffset)
    {
        final int numFrames = sample.getNumFrames ();
        final int channelSize = numFrames * 2;
        final int monoSize = Emulator3Constants.SAMPLE_HEADER_SIZE + channelSize;
        final boolean isStereo = sample.isStereo ();

        Emulator3Constants.encodeName (data, offset, sample.name);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_START_LEFT, Emulator3Constants.SAMPLE_HEADER_SIZE);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_START_RIGHT, isStereo ? monoSize : 0);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_END_LEFT, monoSize - 2L);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_END_RIGHT, isStereo ? monoSize + channelSize - 2L : 0);

        final long loopStart = sample.hasLoop ? Emulator3Constants.SAMPLE_HEADER_SIZE + sample.loopStart * 2L : Emulator3Constants.SAMPLE_HEADER_SIZE;
        final long loopEnd = sample.hasLoop ? Emulator3Constants.SAMPLE_HEADER_SIZE + sample.loopEnd * 2L : monoSize - 2L;
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_LOOP_START_LEFT, loopStart);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_LOOP_START_RIGHT, isStereo ? loopStart + channelSize : 0);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_LOOP_END_LEFT, loopEnd);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_LOOP_END_RIGHT, isStereo ? loopEnd + channelSize : 0);

        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_RATE, sample.sampleRate);
        Emulator3Constants.putU16 (data, offset + Emulator3Constants.SAMPLE_PLAYBACK_RATE, Emulator3Constants.encodePlaybackRate (sample.sampleRate));

        int options = isStereo ? Emulator3Constants.OPTION_STEREO : Emulator3Constants.OPTION_CHANNEL_LEFT;
        if (sample.hasLoop)
        {
            // The written formats are the EIIIX and ESI ones, which only know forward loops
            options |= Emulator3Constants.OPTION_LOOP_FORWARD;
            if (sample.loopInRelease)
                options |= Emulator3Constants.OPTION_LOOP_IN_RELEASE;
        }
        Emulator3Constants.putU16 (data, offset + Emulator3Constants.SAMPLE_OPTIONS, options);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_DATA_OFFSET_LEFT, memoryOffset + (long) Emulator3Constants.SAMPLE_HEADER_SIZE);
        Emulator3Constants.putU32 (data, offset + Emulator3Constants.SAMPLE_DATA_OFFSET_RIGHT, isStereo ? memoryOffset + (long) monoSize : 0);

        System.arraycopy (sample.left, 0, data, offset + Emulator3Constants.SAMPLE_HEADER_SIZE, channelSize);
        if (isStereo)
            System.arraycopy (sample.right, 0, data, offset + monoSize, channelSize);
    }


    /**
     * Create a unique name for a bank or a sample within the 16 characters of the format.
     *
     * @param sourceName The name to shorten
     * @param usedNames All names used so far
     * @return The name
     */
    private static String createUniqueName (final String sourceName, final Set<String> usedNames)
    {
        final int maxLength = Emulator3Constants.NAME_LENGTH;
        String name = sourceName.trim ();
        if (name.length () > maxLength)
            name = name.substring (0, maxLength);
        int counter = 1;
        while (!usedNames.add (name))
        {
            counter++;
            final String suffix = Integer.toString (counter);
            final String base = name.substring (0, Math.min (name.length (), maxLength - suffix.length ()));
            name = base + suffix;
        }
        return name;
    }
}
