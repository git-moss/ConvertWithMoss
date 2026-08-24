// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.hfe.EmuFmDisk;
import de.mossgrabers.convertwithmoss.file.hfe.EmuFmEncoder;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.convertwithmoss.format.emu.EmuCompanding;
import de.mossgrabers.convertwithmoss.format.emu.EmuDiskCreatorUI;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creates a sound disk of the E-mu Emulator, the first Emulator of 1981. A disk holds two banks,
 * one for each half of the 49 key keyboard; each bank divides its half into up to eight zones of
 * equal width which each play one sample, holds 57,088 bytes of companded audio for them and a
 * pitch table which sets the sample clock for every key. One multi-sample becomes one disk: its
 * zones are spread over the two halves and the pitch of every key follows from the root key and
 * the tuning of the zone which plays it. The audio is converted to mono, re-sampled to the 27,778
 * Hz of the sampler where its rate is higher and companded into the bytes which the AM6072 DAC of
 * the sampler expands. See documentation/design/EMULATOR1_FORMAT.md.
 * <p>
 * The disk is written as an image for the HxC floppy emulators (HFE), which is how it gets into a
 * sampler with a floppy emulator, or as a raw sector image (EMUFD). The operating system of the
 * sampler, which lives on the first two tracks of every disk, is not part of this program: it is
 * copied from a system file or a disk image which the user names, and without one the disk is
 * written without a system.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator1Creator extends AbstractCreator<EmuDiskCreatorUI>
{
    private static final String   ENDING_HFE         = "hfe";
    private static final String   ENDING_RAW         = "emufd";
    /** The Emulator plays one channel per voice, so the audio has to be mixed down to mono. */
    private static final int      NUM_CHANNELS       = 1;
    private static final int []   ALLOWED_BIT_DEPTHS =
    {
        16
    };
    /** A sample which is shorter than this cannot be stored in a sensible way. */
    private static final int      MINIMUM_FRAMES     = 8;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator1Creator (final INotifier notifier)
    {
        super ("E-mu Emulator", "EI", notifier, new EmuDiskCreatorUI ("EI", "E1O", ENDING_RAW));
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || detectSettings.reduceBitDepth == 16)
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BIT_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16");
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean writeRaw = this.settingsConfiguration.isWriteRawImage ();
        final File outputFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (multisampleSource.getName ()), writeRaw ? ENDING_RAW : ENDING_HFE);
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        final byte [] image = new byte [Emulator1Constants.IMAGE_SIZE];
        final boolean lower = this.writeBank (image, multisampleSource, Emulator1Constants.LOWER_BANK_OFFSET, Emulator1Constants.LOWEST_KEY, Emulator1Constants.KEYS_PER_HALF, "lower");
        final boolean upper = this.writeBank (image, multisampleSource, Emulator1Constants.UPPER_BANK_OFFSET, Emulator1Constants.SPLIT_KEY, Emulator1Constants.NUM_KEYS - Emulator1Constants.KEYS_PER_HALF, "upper");
        if (!lower && !upper)
        {
            this.notifier.logError ("IDS_EI_NO_AUDIO", multisampleSource.getName ());
            return;
        }

        this.writeOperatingSystem (image);
        System.arraycopy (Emulator1Constants.createEmptySequencerTrack (), 0, image, Emulator1Constants.SEQUENCER_OFFSET, Emulator1Constants.TRACK_SIZE);

        if (writeRaw)
            Files.write (outputFile.toPath (), image);
        else
            EmuFmDisk.writeImage (outputFile, image, Emulator1Constants.NUM_TRACKS, 1, EmuFmEncoder.LAYOUT_EMULATOR);
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Copy the operating system onto the first two tracks of the image, if a system file or a disk
     * image is configured.
     *
     * @param image The disk image
     */
    private void writeOperatingSystem (final byte [] image)
    {
        final String path = this.settingsConfiguration.getOperatingSystemPath ();
        if (path.isBlank ())
        {
            this.notifier.log ("IDS_EMU_NO_OPERATING_SYSTEM");
            return;
        }

        final File file = new File (path);
        try
        {
            final Optional<byte []> system = this.readOperatingSystem (file);
            if (system.isPresent ())
                System.arraycopy (system.get (), 0, image, 0, Emulator1Constants.OS_SIZE);
            else
                this.notifier.logError ("IDS_EMU_OPERATING_SYSTEM_INVALID", file.getAbsolutePath (), Functions.getMessage ("IDS_EMU_OPERATING_SYSTEM_WRONG_SIZE"));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_EMU_OPERATING_SYSTEM_INVALID", file.getAbsolutePath (), ex.getMessage ());
        }
    }


    /**
     * Read the operating system from a system file (E1O), which holds the first two tracks of a
     * disk, or from a raw or HxC disk image.
     *
     * @param file The file
     * @return The two tracks or empty if the file is neither
     * @throws IOException Could not read the file
     */
    private Optional<byte []> readOperatingSystem (final File file) throws IOException
    {
        final byte [] data;
        if (file.getName ().toLowerCase (Locale.US).endsWith (".hfe"))
        {
            final Optional<byte []> disk = EmuFmDisk.readImage (this.notifier, file, Emulator1Constants.NUM_TRACKS, 1);
            if (disk.isEmpty ())
                return Optional.empty ();
            data = disk.get ();
        }
        else
        {
            if (file.length () != Emulator1Constants.OS_SIZE && file.length () != Emulator1Constants.IMAGE_SIZE)
                return Optional.empty ();
            data = Files.readAllBytes (file.toPath ());
        }
        final byte [] system = new byte [Emulator1Constants.OS_SIZE];
        System.arraycopy (data, 0, system, 0, Emulator1Constants.OS_SIZE);
        return Optional.of (system);
    }


    /**
     * Write the bank of one half of the keyboard.
     *
     * @param image The disk image
     * @param multisampleSource The source
     * @param bankOffset The position of the bank in the image
     * @param lowestKey The MIDI note of the lowest key of the half
     * @param numKeys The number of keys of the half
     * @param halfName The name of the half for messages
     * @return True if at least one zone was written
     * @throws IOException Could not convert the sample data
     */
    private boolean writeBank (final byte [] image, final IMultisampleSource multisampleSource, final int bankOffset, final int lowestKey, final int numKeys, final String halfName) throws IOException
    {
        final String half = Functions.getMessage (halfName.equals ("lower") ? "IDS_EI_LOWER_HALF" : "IDS_EI_UPPER_HALF");

        // The zone which each key of the half plays
        final ISampleZone [] zoneByKey = new ISampleZone [numKeys];
        final List<ISampleZone> distinct = new ArrayList<> ();
        for (int index = 0; index < numKeys; index++)
        {
            zoneByKey[index] = findZone (multisampleSource, lowestKey + index);
            if (zoneByKey[index] != null && !distinct.contains (zoneByKey[index]))
                distinct.add (zoneByKey[index]);
        }
        if (distinct.isEmpty ())
            return false;

        if (distinct.size () > Emulator1Constants.MAX_ZONES)
            this.notifier.logError ("IDS_EI_TOO_MANY_ZONES", half, multisampleSource.getName (), Integer.toString (distinct.size ()));
        final int numZones = Emulator1Constants.getZoneCount (distinct.size ());
        final int zoneWidth = Emulator1Constants.KEYS_PER_HALF / numZones;

        // Each zone of the sampler plays the zone of the source which covers most of its keys
        final ISampleZone [] zoneOfSlot = new ISampleZone [numZones];
        for (int slot = 0; slot < numZones; slot++)
        {
            final int firstIndex = slot * zoneWidth;
            final int lastIndex = slot == numZones - 1 ? numKeys - 1 : firstIndex + zoneWidth - 1;
            final Map<ISampleZone, Integer> coverage = new HashMap<> ();
            for (int index = firstIndex; index <= lastIndex; index++)
                if (zoneByKey[index] != null)
                    coverage.merge (zoneByKey[index], Integer.valueOf (1), Integer::sum);
            ISampleZone best = null;
            for (final Map.Entry<ISampleZone, Integer> entry: coverage.entrySet ())
                if (best == null || entry.getValue ().intValue () > coverage.get (best).intValue ())
                    best = entry.getKey ();
            zoneOfSlot[slot] = best;
        }
        // A slot which no zone covers plays the sample of its neighbour so that no key is silent
        for (int slot = 0; slot < numZones; slot++)
            if (zoneOfSlot[slot] == null)
                zoneOfSlot[slot] = findNeighbour (zoneOfSlot, slot);

        // Convert and store the samples, one block per distinct zone
        final Map<ISampleZone, SampleBlock> blocks = new HashMap<> ();
        int address = Emulator1Constants.SAMPLE_MEMORY_START;
        for (int slot = 0; slot < numZones; slot++)
        {
            final ISampleZone zone = zoneOfSlot[slot];
            if (blocks.containsKey (zone))
                continue;
            final SampleBlock block = this.convertSample (zone, half);
            if (block == null)
                continue;
            final int available = Emulator1Constants.SAMPLE_MEMORY_END - address - Emulator1Constants.SAMPLE_ALIGNMENT;
            if (available < MINIMUM_FRAMES)
            {
                this.notifier.logError ("IDS_EI_ZONE_DROPPED", half, zone.getName ());
                continue;
            }
            if (block.audio.length > available)
            {
                block.truncate (available / Emulator1Constants.SAMPLE_ALIGNMENT * Emulator1Constants.SAMPLE_ALIGNMENT);
                this.notifier.logError ("IDS_EI_SAMPLE_MEMORY_FULL", half, zone.getName (), Integer.toString (block.audio.length), formatDouble (block.audio.length / (double) block.sampleRate, 2));
            }
            block.start = address;
            System.arraycopy (block.audio, 0, image, bankOffset + address - Emulator1Constants.BANK_ADDRESS, block.audio.length);
            address += block.audio.length + Emulator1Constants.SAMPLE_ALIGNMENT;
            blocks.put (zone, block);
        }
        if (blocks.isEmpty ())
            return false;
        // A zone whose sample did not fit plays the sample of its neighbour
        for (int slot = 0; slot < numZones; slot++)
            if (!blocks.containsKey (zoneOfSlot[slot]))
            {
                for (int distance = 1; distance < numZones; distance++)
                {
                    final int left = slot - distance;
                    final int right = slot + distance;
                    if (left >= 0 && blocks.containsKey (zoneOfSlot[left]))
                    {
                        zoneOfSlot[slot] = zoneOfSlot[left];
                        break;
                    }
                    if (right < numZones && blocks.containsKey (zoneOfSlot[right]))
                    {
                        zoneOfSlot[slot] = zoneOfSlot[right];
                        break;
                    }
                }
                if (!blocks.containsKey (zoneOfSlot[slot]))
                    zoneOfSlot[slot] = blocks.keySet ().iterator ().next ();
            }

        // The header: the records of the zones, the record of the selected sample and the pitch
        // table
        final int header = bankOffset;
        for (int slot = 0; slot < numZones; slot++)
        {
            final int record = header + Emulator1Constants.RECORD_FIRST_ZONE + slot * Emulator1Constants.RECORD_SIZE;
            final int count = slot == 0 ? numZones : slot == 1 ? zoneWidth : 0;
            writeRecord (image, record, count, zoneOfSlot[slot], blocks.get (zoneOfSlot[slot]), Emulator1Constants.BANK_ADDRESS + Emulator1Constants.TABLE_OFFSET);
        }
        if (numZones == 1)
            image[header + Emulator1Constants.RECORD_FIRST_ZONE + Emulator1Constants.RECORD_SIZE] = 0x19;
        final SampleBlock selected = blocks.get (zoneOfSlot[0]);
        writeRecord (image, header, Emulator1Constants.FLAG_MULTISAMPLE | (selected.hasLoop ? 0 : Emulator1Constants.FLAG_LOOP_OFF), zoneOfSlot[0], selected, 0);

        this.writePitchTable (image, header + Emulator1Constants.TABLE_OFFSET, zoneOfSlot, blocks, zoneWidth, numKeys, lowestKey, multisampleSource.getName ());
        return true;
    }


    /**
     * Find the zone of the source which plays a key. If several groups play the key, the one with
     * the highest velocity wins since the sampler has no velocity layers.
     *
     * @param multisampleSource The source
     * @param key The MIDI note
     * @return The zone or null if no zone plays the key
     */
    private static ISampleZone findZone (final IMultisampleSource multisampleSource, final int key)
    {
        ISampleZone best = null;
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getKeyLow () <= key && key <= zone.getKeyHigh () && zone.getSampleData ().isPresent () && (best == null || zone.getVelocityHigh () > best.getVelocityHigh ()))
                    best = zone;
        return best;
    }


    /**
     * Find the closest slot which has a zone.
     *
     * @param zoneOfSlot The zones of the slots
     * @param slot The slot which has none
     * @return The zone of the closest slot
     */
    private static ISampleZone findNeighbour (final ISampleZone [] zoneOfSlot, final int slot)
    {
        for (int distance = 1; distance < zoneOfSlot.length; distance++)
        {
            if (slot - distance >= 0 && zoneOfSlot[slot - distance] != null)
                return zoneOfSlot[slot - distance];
            if (slot + distance < zoneOfSlot.length && zoneOfSlot[slot + distance] != null)
                return zoneOfSlot[slot + distance];
        }
        return null;
    }


    /**
     * Convert the audio of a zone into the companded bytes of the sampler. The audio is mixed down
     * to mono and re-sampled to the rate of the sampler when its rate is higher; a lower rate is
     * kept and compensated with the pitch table, which saves sample memory.
     *
     * @param zone The zone
     * @param half The name of the half of the keyboard for messages
     * @return The block or null if the zone holds no audio
     * @throws IOException Could not convert the sample data
     */
    private SampleBlock convertSample (final ISampleZone zone, final String half) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA", zone.getName ());
            return null;
        }

        final int sourceRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
        final DestinationAudioFormat destinationFormat = new DestinationAudioFormat (ALLOWED_BIT_DEPTHS, Emulator1Constants.SAMPLE_RATE, false);
        this.logResampling (zone, destinationFormat);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), destinationFormat);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        final int sampleRate = waveFile.getFormatChunk ().getSampleRate ();
        final byte [] wavData = waveFile.getDataChunk ().getData ();
        int numFrames = wavData.length / (2 * numChannels);
        if (numFrames < MINIMUM_FRAMES)
            return null;

        final SampleBlock block = new SampleBlock ();
        block.sampleRate = sampleRate;
        if (numFrames > Emulator1Constants.SAMPLE_MEMORY_SIZE - Emulator1Constants.SAMPLE_ALIGNMENT)
        {
            numFrames = Emulator1Constants.SAMPLE_MEMORY_SIZE - Emulator1Constants.SAMPLE_ALIGNMENT;
            this.notifier.logError ("IDS_EI_SAMPLE_TOO_LONG", zone.getName (), formatDouble (numFrames / (double) sampleRate, 2));
        }
        // Samples are stored in units of four bytes
        final int storedFrames = (numFrames + Emulator1Constants.SAMPLE_ALIGNMENT - 1) / Emulator1Constants.SAMPLE_ALIGNMENT * Emulator1Constants.SAMPLE_ALIGNMENT;
        block.audio = new byte [storedFrames];
        for (int frame = 0; frame < storedFrames; frame++)
        {
            final int source = Math.min (frame, numFrames - 1);
            int sum = 0;
            for (int channel = 0; channel < numChannels; channel++)
            {
                final int offset = (source * numChannels + channel) * 2;
                sum += (short) (wavData[offset] & 0xFF | (wavData[offset + 1] & 0xFF) << 8);
            }
            block.audio[frame] = (byte) EmuCompanding.compand (sum / numChannels);
        }

        // The audio was re-sampled to the rate of the sampler, so the loop moves with it
        final double rateRatio = sampleRate / (double) sourceRate;
        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                final int loopEnd = Math.clamp ((int) Math.round (loop.getEnd () * rateRatio), 0, numFrames);
                final int loopStart = Math.clamp ((int) Math.round (loop.getStart () * rateRatio), 0, loopEnd);
                if (loopEnd - loopStart > Emulator1Constants.NO_LOOP_LENGTH)
                {
                    block.hasLoop = true;
                    block.loopStart = loopStart;
                    block.loopEnd = loopEnd;
                }
                break;
            }
        return block;
    }


    /**
     * Write one record of the header.
     *
     * @param image The disk image
     * @param record The position of the record in the image
     * @param firstByte The first byte of the record: the flags of the selected sample or, for the
     *            records of the zones, the number of zones in the first one and the number of keys
     *            per zone in the second one
     * @param zone The zone
     * @param block The stored sample of the zone
     * @param tableAddress The address of the pitch table or 0 for the record of the selected
     *            sample
     */
    private static void writeRecord (final byte [] image, final int record, final int firstByte, final ISampleZone zone, final SampleBlock block, final int tableAddress)
    {
        image[record + Emulator1Constants.RECORD_FLAGS] = (byte) firstByte;
        image[record + Emulator1Constants.RECORD_FILTER] = (byte) (getFilterSetting (zone) << 5);
        writeAddress (image, record + Emulator1Constants.RECORD_TABLE_ADDRESS, tableAddress);

        final int numFrames = block.audio.length;
        // A sample which does not loop carries a loop of two frames right behind its audio
        final int loopStart = block.hasLoop ? block.loopStart : numFrames;
        final int loopLength = block.hasLoop ? block.loopEnd - block.loopStart : Emulator1Constants.NO_LOOP_LENGTH;
        final int loopEnd = loopStart + loopLength;
        writeAddress (image, record + Emulator1Constants.RECORD_SAMPLE_START, block.start);
        writeAddress (image, record + Emulator1Constants.RECORD_LOOP_START_REL, loopStart - 1);
        writeAddress (image, record + Emulator1Constants.RECORD_LOOP_START, block.start + loopStart);
        writeAddress (image, record + Emulator1Constants.RECORD_LOOP_LENGTH, loopLength - 1);
        writeAddress (image, record + Emulator1Constants.RECORD_LOOP_END, block.start + loopEnd);
        writeAddress (image, record + Emulator1Constants.RECORD_RELEASE_LENGTH, numFrames + Emulator1Constants.SAMPLE_END_GUARD - loopEnd);
    }


    /**
     * Get the filter setting of a zone: the one whose cutoff frequency comes closest to the low
     * pass filter of the zone, or 0 - fully open - if it has none.
     *
     * @param zone The zone
     * @return The setting, 0 to 7
     */
    private static int getFilterSetting (final ISampleZone zone)
    {
        final Optional<IFilter> filter = zone.getFilter ();
        if (filter.isEmpty () || filter.get ().getType () != FilterType.LOW_PASS)
            return 0;
        final double cutoff = filter.get ().getCutoff ();
        if (cutoff <= 0 || cutoff >= Emulator1Constants.FILTER_CUTOFF[1])
            return 0;
        return Emulator1Constants.getFilterSetting (cutoff);
    }


    /**
     * Write the pitch table of a bank: the sample clock of every key of the half of the keyboard,
     * which follows from the root key and the tuning of the zone which plays it and from the rate
     * its audio is stored with.
     *
     * @param image The disk image
     * @param table The position of the table in the image
     * @param zoneOfSlot The zone of each zone of the sampler
     * @param blocks The stored samples of the zones
     * @param zoneWidth The number of keys of a zone of the sampler
     * @param numKeys The number of keys of the half
     * @param lowestKey The MIDI note of the lowest key of the half
     * @param name The name of the source for messages
     */
    private void writePitchTable (final byte [] image, final int table, final ISampleZone [] zoneOfSlot, final Map<ISampleZone, SampleBlock> blocks, final int zoneWidth, final int numKeys, final int lowestKey, final String name)
    {
        int largestTransposition = 0;
        for (int index = 0; index < Emulator1Constants.TABLE_ENTRIES; index++)
        {
            final int slot = Math.min (index / zoneWidth, zoneOfSlot.length - 1);
            final ISampleZone zone = zoneOfSlot[slot];
            final SampleBlock block = blocks.get (zone);
            final int key = lowestKey + index;
            final double semitones = zone.getKeyTracking () < 0.5 ? 0 : key - getRootKey (zone);
            // A sample which is stored at a lower rate than the sampler plays is slowed down
            final double rateCents = 1200.0 * Math.log (block.sampleRate / (double) Emulator1Constants.SAMPLE_RATE) / Math.log (2);
            final double cents = (semitones + zone.getTuning ()) * 100.0 + rateCents;
            final int value = Emulator1Constants.getPitchValue (cents);
            if (index < numKeys && Emulator1Constants.getPitchCents (value) < cents - 50)
                largestTransposition = Math.max (largestTransposition, (int) Math.round (cents / 100.0));

            // The last entry of the lower half and the extra key of the upper half belong to the
            // top zone
            final int firstIndexOfSlot = slot * zoneWidth;
            final int slotSize = slot == zoneOfSlot.length - 1 ? Emulator1Constants.TABLE_ENTRIES - firstIndexOfSlot : zoneWidth;
            final int code = Emulator1Constants.getPitchCode (Math.min (slotSize, Emulator1Constants.KEYS_PER_HALF / zoneOfSlot.length), index - firstIndexOfSlot);
            writeAddress (image, table + index * 2, code << Emulator1Constants.PITCH_CODE_SHIFT | value & Emulator1Constants.PITCH_VALUE_MASK);
        }
        if (largestTransposition > 0)
            this.notifier.log ("IDS_EI_TRANSPOSITION_TOO_LARGE", name, Integer.toString (largestTransposition));
    }


    /**
     * Get the root key of a zone, which is the centre of its key range when the zone has none.
     *
     * @param zone The zone
     * @return The root key
     */
    private static int getRootKey (final ISampleZone zone)
    {
        final int root = zone.getKeyRoot ();
        return root < 0 ? (zone.getKeyLow () + zone.getKeyHigh ()) / 2 : root;
    }


    /**
     * Write a 16 bit little-endian value.
     *
     * @param image The disk image
     * @param offset The position of the value
     * @param value The value
     */
    private static void writeAddress (final byte [] image, final int offset, final int value)
    {
        image[offset] = (byte) (value & 0xFF);
        image[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /** The audio of one zone as it is stored in the sample memory of a bank. */
    private static class SampleBlock
    {
        int     start;
        int     sampleRate;
        byte [] audio;
        boolean hasLoop;
        int     loopStart;
        int     loopEnd;


        /**
         * Shorten the audio.
         *
         * @param numFrames The new number of frames
         */
        void truncate (final int numFrames)
        {
            this.audio = Arrays.copyOf (this.audio, numFrames);
            if (this.hasLoop && this.loopEnd > numFrames)
            {
                this.loopEnd = numFrames;
                if (this.loopEnd - this.loopStart <= Emulator1Constants.NO_LOOP_LENGTH)
                    this.hasLoop = false;
            }
        }
    }
}
