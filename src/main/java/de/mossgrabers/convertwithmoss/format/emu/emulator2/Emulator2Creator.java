// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator2;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
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
 * Creates a sound bank disk of the E-mu Emulator II. A bank holds up to 100 voices - a sample with
 * its loop and its settings - and up to 100 presets which assign a voice, a transposition and
 * optionally a second voice to ranges of the 61 keys; all of it shares the 494,592 bytes of a
 * floppy disk with the audio, which is companded into the bytes the AM6072 DAC of the sampler
 * expands, at the fixed 27,777 Hz of the machine. Each multi-sample becomes one preset, the first
 * group of its zones the voices of the key ranges and the second group their second voices, so a
 * library of multi-samples becomes one disk. See documentation/design/EMULATOR2_FORMAT.md.
 * <p>
 * The settings of a voice - filter, envelopes, LFO, level - are not decoded; they are taken from a
 * voice of the factory library, an unfiltered piano, so that a written voice plays with sensible
 * defaults. The disk is written as an image for the HxC floppy emulators (HFE) or as a raw sector
 * image (EMUIIFD). The operating system, which lives on the first 22 tracks, is not part of this
 * program: it is copied from a system file or a disk image which the user names; without one the
 * disk holds the bank alone, which the sampler loads once it has booted from another disk.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator2Creator extends AbstractCreator<EmuDiskCreatorUI>
{
    private static final String  ENDING_HFE           = "hfe";
    private static final String  ENDING_RAW           = "emuiifd";
    private static final int []  ALLOWED_BIT_DEPTHS   =
    {
        16
    };
    /** The Emulator II plays one channel per voice, so the audio has to be mixed down to mono. */
    private static final int     MINIMUM_FRAMES       = 8;
    /** A loop shorter than this does not survive the companding of the audio. */
    private static final int     MINIMUM_LOOP_LENGTH  = 8;
    /** The loop length which a voice without a loop carries, which reserves a bit of slot. */
    private static final int     UNLOOPED_LOOP_LENGTH = 64;
    /** The largest transposition a key range entry can hold. */
    private static final int     MAX_TRANSPOSE        = 0x3F;
    /** The level of a key range, which is what the factory library uses throughout. */
    private static final int     RANGE_LEVEL          = 0x70;
    /** The flags of a voice record without its loop bit. */
    private static final int     VOICE_FLAGS_DEFAULT  = 0x24;
    /** The sample memory of a bank starts this far behind the end of its records. */
    private static final int     SAMPLE_MEMORY_GAP    = 0x95FE;
    /** The memory address behind the sample memory, to which the negative counters count. */
    private static final int     COUNTER_BASE         = 0x500000;
    /** The size of the operating system of the disk: tracks 0 to 21. */
    private static final int     OS_SIZE              = Emulator2Constants.BANK_OFFSET;
    /**
     * The size of the system file (E2O) which the EMXP project publishes: the OS without the empty
     * rest of its tracks.
     */
    private static final int     OS_FILE_SIZE         = 72704;

    /** The header which starts every preset record. */
    private static final byte [] PRESET_HEADER        =
    {
        0x01,
        0x04,
        0x00,
        0x00,
        0x00,
        0x00,
        0x02,
        0x03,
        (byte) 0x89
    };
    /** The parameters of a preset, the most frequent setting of the factory library. */
    private static final byte [] PRESET_PARAMETERS    =
    {
        0x00,
        0x0B,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x50,
        0x04
    };

    /**
     * A voice record of the factory library - the voice 'piano A2' of the disk 'Grand Piano' -
     * whose settings are used for every written voice. The fields which are decoded - the name, the
     * addresses, the loop and the pointers into the record - are overwritten.
     */
    private static final int []  VOICE_TEMPLATE       =
    {
        0x04,
        0x03,
        0xE7,
        0xF9,
        0x00,
        0x02,
        0xF8,
        0x37,
        0x4F,
        0x01,
        0xEF,
        0xC1,
        0x01,
        0x00,
        0xC4,
        0xD9,
        0x4F,
        0x01,
        0x00,
        0xC4,
        0xD9,
        0x4F,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x01,
        0x61,
        0x00,
        0x80,
        0x00,
        0xFF,
        0xA5,
        0x9B,
        0x00,
        0x04,
        0x90,
        0x9B,
        0x00,
        0x1C,
        0x00,
        0x1C,
        0x00,
        0x1C,
        0x00,
        0x7F,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xFF,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0xE9,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0xFF,
        0xFF,
        0x00,
        0x04,
        0x00,
        0x97,
        0x9B,
        0xFF,
        0xFF,
        0x00,
        0x00,
        0x00,
        0x97,
        0x00,
        0x01,
        0x01,
        0xFD,
        0x00,
        0x00,
        0xF3,
        0x0F,
        0x06,
        0x06,
        0xFF,
        0xFF,
        0x07,
        0xAC,
        0x9B,
        0xFF,
        0xFF,
        0x00,
        0x07,
        0x00,
        0xAC,
        0x00,
        0x01,
        0x01,
        0xFD,
        0x07,
        0x00,
        0xF3,
        0x0F,
        0x70,
        0x69,
        0x61,
        0x6E,
        0x6F,
        0x20,
        0x41,
        0x32,
        0x20,
        0x20,
        0x20,
        0x20,
        0x24,
        0xE8,
        0xF9,
        0x00,
        0x48,
        0xEE,
        0x00,
        0xF0,
        0xC1,
        0x01,
        0x3C,
        0x26,
        0x00,
        0xE8,
        0xF9,
        0x00,
        0x48,
        0xEE,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F,
        0x0F
    };
    /** The high bytes of the pointers of a voice record into itself, which name the record. */
    private static final int []  VOICE_POINTER_BYTES  =
    {
        0x23,
        0x27,
        0x96,
        0xAA
    };
    /** The memory address of the first voice record, to which the pointers refer. */
    private static final int     VOICE_RECORD_ADDRESS = Emulator2Constants.BANK_ADDRESS + Emulator2Constants.VOICE_TABLE;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator2Creator (final INotifier notifier)
    {
        super ("E-mu Emulator II", "EII", notifier, new EmuDiskCreatorUI ("EII", "E2O", ENDING_RAW));
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
        this.writeDisk (destinationFolder, List.of (multisampleSource), multisampleSource.getName ());
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
            this.writeDisk (destinationFolder, multisampleSources, libraryName);
    }


    /**
     * Write all given sources into one disk.
     *
     * @param destinationFolder Where to create the file
     * @param multisampleSources The sources to convert
     * @param name The name of the disk
     * @throws IOException Could not write the disk
     */
    private void writeDisk (final File destinationFolder, final List<IMultisampleSource> multisampleSources, final String name) throws IOException
    {
        final boolean writeRaw = this.settingsConfiguration.isWriteRawImage ();
        final File outputFile = this.createUniqueFilename (destinationFolder, FileUtils.createSafeFilename (name), writeRaw ? ENDING_RAW : ENDING_HFE);
        this.notifier.log ("IDS_NOTIFY_STORING", outputFile.getAbsolutePath ());

        final BankBuilder builder = new BankBuilder ();
        for (final IMultisampleSource multisampleSource: multisampleSources)
        {
            if (builder.presets.size () >= Emulator2Constants.MAX_VOICES)
            {
                this.notifier.logError ("IDS_EII_TOO_MANY_PRESETS", multisampleSource.getName ());
                break;
            }
            final Preset preset = this.createPresetData (multisampleSource, builder);
            if (preset != null)
                builder.presets.add (preset);
        }
        if (builder.presets.isEmpty ())
        {
            this.notifier.logError ("IDS_EII_NO_AUDIO", name);
            return;
        }

        final byte [] image = new byte [Emulator2Constants.IMAGE_SIZE];
        this.writeOperatingSystem (image);
        if (!this.writeBank (image, builder))
            return;

        if (writeRaw)
            Files.write (outputFile.toPath (), image);
        else
            EmuFmDisk.writeImage (outputFile, image, Emulator2Constants.CYLINDERS, Emulator2Constants.HEADS, EmuFmEncoder.LAYOUT_EMULATOR_II);
        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Copy the operating system onto the first 22 tracks of the image, if a system file or a disk
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
                System.arraycopy (system.get (), 0, image, 0, system.get ().length);
            else
                this.notifier.logError ("IDS_EMU_OPERATING_SYSTEM_INVALID", file.getAbsolutePath (), Functions.getMessage ("IDS_EMU_OPERATING_SYSTEM_WRONG_SIZE"));
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_EMU_OPERATING_SYSTEM_INVALID", file.getAbsolutePath (), ex.getMessage ());
        }
    }


    /**
     * Read the operating system from a system file (E2O), which holds the first tracks of a disk,
     * or from a raw or HxC disk image.
     *
     * @param file The file
     * @return The system tracks or empty if the file is neither
     * @throws IOException Could not read the file
     */
    private Optional<byte []> readOperatingSystem (final File file) throws IOException
    {
        final byte [] data;
        if (file.getName ().toLowerCase (Locale.US).endsWith (".hfe"))
        {
            final Optional<byte []> disk = EmuFmDisk.readImage (this.notifier, file, Emulator2Constants.CYLINDERS, Emulator2Constants.HEADS);
            if (disk.isEmpty ())
                return Optional.empty ();
            data = disk.get ();
        }
        else
        {
            if (file.length () != OS_FILE_SIZE && file.length () != Emulator2Constants.IMAGE_SIZE)
                return Optional.empty ();
            data = Files.readAllBytes (file.toPath ());
        }
        return Optional.of (Arrays.copyOf (data, Math.min (OS_SIZE, data.length)));
    }


    /**
     * Convert one multi-sample source into a preset.
     *
     * @param multisampleSource The source
     * @param builder The bank which is being built
     * @return The preset or null if none of its zones could be converted
     * @throws IOException Could not convert the sample data
     */
    private Preset createPresetData (final IMultisampleSource multisampleSource, final BankBuilder builder) throws IOException
    {
        // The zone which each key plays in the first two groups, the second becomes the second
        // voice of a key range - the dual voice of the sampler
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        final ISampleZone [] [] zonesByKey = new ISampleZone [2] [Emulator2Constants.NUM_KEYS];
        int dropped = 0;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (zone.getSampleData ().isEmpty ())
                    continue;
                for (int key = Math.max (0, zone.getKeyLow () - Emulator2Constants.LOWEST_KEY); key <= Math.min (Emulator2Constants.NUM_KEYS - 1, zone.getKeyHigh () - Emulator2Constants.LOWEST_KEY); key++)
                    if (zonesByKey[0][key] == null)
                        zonesByKey[0][key] = zone;
                    else if (zonesByKey[1][key] == null)
                        zonesByKey[1][key] = zone;
                    else
                        dropped++;
            }
        if (dropped > 0)
            this.notifier.logError ("IDS_EII_TOO_MANY_LAYERS", multisampleSource.getName ());

        final Preset preset = new Preset ();
        preset.name = multisampleSource.getName ();
        final Map<ISampleZone, Integer> voiceNumbers = new HashMap<> ();
        int largestTransposition = 0;
        int key = 0;
        while (key < Emulator2Constants.NUM_KEYS)
        {
            final ISampleZone primary = zonesByKey[0][key];
            final ISampleZone secondary = zonesByKey[1][key];
            // A range runs as long as the same zones play and stays inside the transposition range
            int last = key;
            while (last + 1 < Emulator2Constants.NUM_KEYS && zonesByKey[0][last + 1] == primary && zonesByKey[1][last + 1] == secondary && last + 1 - key < Emulator2Constants.ENTRY_COUNT_MASK)
                last++;

            final KeyRange range = new KeyRange ();
            range.firstKey = key;
            range.numKeys = last - key + 1;
            if (primary != null)
            {
                range.voice = this.getVoiceNumber (primary, voiceNumbers, builder);
                range.transpose = getTransposition (primary, key);
                largestTransposition = Math.max (largestTransposition, Emulator2Constants.TRANSPOSE_UNITY + Emulator2Constants.LOWEST_KEY + last - getRootKey (primary) - MAX_TRANSPOSE);
            }
            if (secondary != null)
            {
                range.secondVoice = this.getVoiceNumber (secondary, voiceNumbers, builder);
                range.secondTranspose = getTransposition (secondary, key);
            }
            preset.ranges.add (range);
            key = last + 1;
        }
        if (largestTransposition > 0)
            this.notifier.log ("IDS_EII_TRANSPOSITION_TOO_LARGE", multisampleSource.getName (), Integer.toString (largestTransposition));

        boolean playsVoice = false;
        for (final KeyRange range: preset.ranges)
            playsVoice |= range.voice > 0 || range.secondVoice > 0;
        return playsVoice ? preset : null;
    }


    /**
     * Get the transposition of the first key of a range.
     *
     * @param zone The zone the range plays
     * @param firstKey The first key of the range
     * @return The transposition, limited to the range of an entry
     */
    private static int getTransposition (final ISampleZone zone, final int firstKey)
    {
        return Math.clamp (Emulator2Constants.TRANSPOSE_UNITY + Emulator2Constants.LOWEST_KEY + firstKey - (long) getRootKey (zone), 0, MAX_TRANSPOSE);
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
     * Get the voice number of a zone, creating the voice when the zone is used for the first time.
     *
     * @param zone The zone
     * @param voiceNumbers The voices which were already created for this preset
     * @param builder The bank which is being built
     * @return The 1-based voice number or 0 if the zone holds no audio which still fits
     * @throws IOException Could not convert the sample data
     */
    private int getVoiceNumber (final ISampleZone zone, final Map<ISampleZone, Integer> voiceNumbers, final BankBuilder builder) throws IOException
    {
        final Integer existing = voiceNumbers.get (zone);
        if (existing != null)
            return existing.intValue ();
        if (builder.voices.size () >= Emulator2Constants.MAX_VOICES)
        {
            this.notifier.logError ("IDS_EII_TOO_MANY_VOICES", zone.getName ());
            voiceNumbers.put (zone, Integer.valueOf (0));
            return 0;
        }

        final Voice voice = this.convertSample (zone);
        if (voice == null)
        {
            voiceNumbers.put (zone, Integer.valueOf (0));
            return 0;
        }

        // Re-use a voice with identical audio and loop, e.g. when the same sample is mapped to
        // several key ranges or is played by several presets of the library
        final Object contentKey = List.of (ByteBuffer.wrap (voice.audio), Boolean.valueOf (voice.hasLoop), Integer.valueOf (voice.loopStart), Integer.valueOf (voice.loopLength));
        final Integer existingNumber = builder.voiceNumbersByContent.get (contentKey);
        if (existingNumber != null)
        {
            voiceNumbers.put (zone, existingNumber);
            return existingNumber.intValue ();
        }

        builder.voices.add (voice);
        final int number = builder.voices.size ();
        voiceNumbers.put (zone, Integer.valueOf (number));
        builder.voiceNumbersByContent.put (contentKey, Integer.valueOf (number));
        return number;
    }


    /**
     * Convert the audio of a zone into a voice: mixed down to mono, re-sampled to the rate of the
     * sampler and companded into its bytes.
     *
     * @param zone The zone
     * @return The voice or null if the zone holds no audio
     * @throws IOException Could not convert the sample data
     */
    private Voice convertSample (final ISampleZone zone) throws IOException
    {
        final Optional<ISampleData> sampleData = zone.getSampleData ();
        if (sampleData.isEmpty ())
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA", zone.getName ());
            return null;
        }

        final int sourceRate = sampleData.get ().getAudioMetadata ().getSampleRate ();
        final DestinationAudioFormat destinationFormat = new DestinationAudioFormat (ALLOWED_BIT_DEPTHS, Emulator2Constants.SAMPLE_RATE, true);
        this.logResampling (zone, destinationFormat);
        final WaveFile waveFile = AudioFileUtils.convertToWav (sampleData.get (), destinationFormat);
        final int numChannels = waveFile.getFormatChunk ().getNumberOfChannels ();
        final byte [] wavData = waveFile.getDataChunk ().getData ();
        final int numFrames = wavData.length / (2 * numChannels);
        if (numFrames < MINIMUM_FRAMES)
            return null;

        final Voice voice = new Voice ();
        voice.name = zone.getName ();
        voice.audio = new byte [numFrames];
        for (int frame = 0; frame < numFrames; frame++)
        {
            int sum = 0;
            for (int channel = 0; channel < numChannels; channel++)
            {
                final int offset = (frame * numChannels + channel) * 2;
                sum += (short) (wavData[offset] & 0xFF | (wavData[offset + 1] & 0xFF) << 8);
            }
            voice.audio[frame] = (byte) EmuCompanding.compand (sum / numChannels);
        }

        // The audio was re-sampled to the rate of the sampler, so the loop moves with it
        final double rateRatio = Emulator2Constants.SAMPLE_RATE / (double) sourceRate;
        for (final ISampleLoop loop: zone.getLoops ())
            if (loop.getType () == LoopType.FORWARDS || loop.getType () == LoopType.ALTERNATING)
            {
                final int loopEnd = Math.clamp ((int) Math.round (loop.getEnd () * rateRatio), 0, numFrames);
                final int loopStart = Math.clamp ((int) Math.round (loop.getStart () * rateRatio), 0, Math.max (0, loopEnd - MINIMUM_LOOP_LENGTH));
                if (loopEnd - loopStart >= MINIMUM_LOOP_LENGTH)
                {
                    voice.hasLoop = true;
                    voice.loopStart = loopStart;
                    voice.loopLength = loopEnd - loopStart;
                }
                break;
            }
        if (!voice.hasLoop)
            voice.loopLength = Math.min (UNLOOPED_LOOP_LENGTH, numFrames);
        return voice;
    }


    /**
     * Write the bank into the image: the key maps of the first preset, the voice list, the voice
     * records, the preset records and the audio.
     *
     * @param image The disk image
     * @param builder The bank which was built
     * @return False if nothing fits onto the disk
     */
    private boolean writeBank (final byte [] image, final BankBuilder builder)
    {
        final int bank = Emulator2Constants.BANK_OFFSET;
        final List<Voice> voices = builder.voices;
        final List<Preset> presets = builder.presets;

        // The records: the voices, then the chain of presets with the length of the first one in
        // front of it
        final int recordsEnd = bank + Emulator2Constants.VOICE_TABLE + voices.size () * Emulator2Constants.VOICE_SIZE;
        int position = recordsEnd + 2;
        final int [] presetPositions = new int [presets.size ()];
        final byte [] [] presetRecords = new byte [presets.size ()] [];
        for (int index = 0; index < presets.size (); index++)
        {
            presetRecords[index] = createPresetRecord (presets.get (index));
            presetPositions[index] = position;
            position += presetRecords[index].length;
        }
        final int chainEnd = position;

        // The audio, one slot per voice which holds the sample, its loop and 4 bytes
        int address = chainEnd - bank + SAMPLE_MEMORY_GAP;
        for (final Voice voice: voices)
        {
            final int slot = voice.audio.length + voice.loopLength + Emulator2Constants.VOICE_SLOT_PADDING;
            final int available = Emulator2Constants.BANK_SIZE - address;
            if (slot > available)
            {
                final int frames = available - voice.loopLength - Emulator2Constants.VOICE_SLOT_PADDING;
                if (frames < MINIMUM_FRAMES)
                {
                    this.notifier.logError ("IDS_EII_SAMPLE_MEMORY_FULL", voice.name);
                    voice.audio = new byte [0];
                    continue;
                }
                voice.truncate (frames);
                this.notifier.logError ("IDS_EII_SAMPLE_SHORTENED", voice.name, Integer.toString (frames), formatDouble (frames / (double) Emulator2Constants.SAMPLE_RATE, 2));
            }
            voice.start = address;
            System.arraycopy (voice.audio, 0, image, bank + address, voice.audio.length);
            address += voice.audio.length + voice.loopLength + Emulator2Constants.VOICE_SLOT_PADDING;
        }

        for (int index = 0; index < voices.size (); index++)
            writeVoiceRecord (image, bank + Emulator2Constants.VOICE_TABLE + index * Emulator2Constants.VOICE_SIZE, index, voices.get (index));

        writeWord (image, recordsEnd, presetRecords[0].length);
        for (int index = 0; index < presets.size (); index++)
        {
            System.arraycopy (presetRecords[index], 0, image, presetPositions[index], presetRecords[index].length);
            // Each record ends with the length of the next one
            writeWord (image, presetPositions[index] + presetRecords[index].length - 2, index + 1 < presets.size () ? presetRecords[index + 1].length : 0);
        }

        // The first preset is the selected one: its record, the list of the voices of the bank
        // and the directory of the presets
        Arrays.fill (image, bank, bank + Emulator2Constants.PRESET_NAME_LENGTH, (byte) ' ');
        System.arraycopy (presetRecords[0], 0, image, bank + Emulator2Constants.SELECTED_PRESET, Emulator2Constants.PRESET_ENTRIES_OFFSET);
        image[bank + Emulator2Constants.SELECTED_PRESET + Emulator2Constants.PRESET_ENTRIES_OFFSET] = 1;
        for (int index = 0; index < voices.size (); index++)
            image[bank + Emulator2Constants.VOICE_LIST + index] = (byte) (Emulator2Constants.VOICE_ID_BASE + index);
        int directory = bank + Emulator2Constants.VOICE_LIST + Emulator2Constants.VOICE_LIST_SIZE;
        for (int index = 0; index + 1 < presets.size (); index++)
        {
            // The address of the length word at the end of the record, which links to the next one
            final int link = Emulator2Constants.BANK_ADDRESS + presetPositions[index] + presetRecords[index].length - 2 - bank;
            image[directory] = (byte) (link >> 8 & 0xFF);
            image[directory + 1] = (byte) (link & 0xFF);
            directory += 2;
        }
        writeKeyMaps (image, bank, presets.get (0));
        return true;
    }


    /**
     * Write the expanded key maps of the selected preset: for every key its voice number, the
     * identifier of the voice, the transposition, the level and the marker of the start of a range,
     * in the eleven tables which the sampler keeps for its playing state.
     *
     * @param image The disk image
     * @param bank The position of the bank in the image
     * @param preset The selected preset
     */
    private static void writeKeyMaps (final byte [] image, final int bank, final Preset preset)
    {
        final int tableSize = Emulator2Constants.NUM_KEYS;
        final int transposeCopy = bank + Emulator2Constants.KEY_MAP_TRANSPOSE + 3 * tableSize;
        final int level = bank + Emulator2Constants.KEY_MAP_TRANSPOSE + 4 * tableSize;
        final int secondLevel = bank + Emulator2Constants.KEY_MAP_TRANSPOSE + 5 * tableSize;
        final int rangeStart = bank + Emulator2Constants.KEY_MAP_TRANSPOSE + 7 * tableSize;
        Arrays.fill (image, rangeStart, rangeStart + tableSize, (byte) 0x0F);
        for (final KeyRange range: preset.ranges)
            for (int key = range.firstKey; key < range.firstKey + range.numKeys; key++)
            {
                final int offset = key - range.firstKey;
                image[bank + Emulator2Constants.KEY_MAP_VOICE_NUMBER + key] = (byte) range.voice;
                image[bank + Emulator2Constants.KEY_MAP_VOICE_ID + key] = (byte) (range.voice > 0 ? Emulator2Constants.VOICE_ID_BASE + range.voice - 1 : 0);
                final int transpose = Math.min (range.transpose + offset, 0xFF);
                image[bank + Emulator2Constants.KEY_MAP_TRANSPOSE + key] = (byte) transpose;
                image[transposeCopy + key] = (byte) transpose;
                image[level + key] = (byte) (range.voice > 0 ? RANGE_LEVEL : 0);
                image[secondLevel + key] = (byte) (range.secondVoice > 0 ? RANGE_LEVEL : 0);
                if (offset == 0)
                    image[rangeStart + key] = 0;
            }
    }


    /**
     * Create the record of a preset: the header, the name, the parameters, one entry per key range,
     * the end marker and the room for the length of the next record.
     *
     * @param preset The preset
     * @return The record
     */
    private static byte [] createPresetRecord (final Preset preset)
    {
        int size = Emulator2Constants.PRESET_ENTRIES_OFFSET + Emulator2Constants.ENTRY_END_SIZE + 2;
        for (final KeyRange range: preset.ranges)
            size += Emulator2Constants.ENTRY_SIZE + (range.secondVoice > 0 ? Emulator2Constants.ENTRY_SECONDARY_SIZE : 0);

        final byte [] presetRecord = new byte [size];
        System.arraycopy (PRESET_HEADER, 0, presetRecord, 0, PRESET_HEADER.length);
        final byte [] name = pad (preset.name, Emulator2Constants.PRESET_NAME_LENGTH);
        System.arraycopy (name, 0, presetRecord, Emulator2Constants.PRESET_NAME_OFFSET, Emulator2Constants.PRESET_NAME_LENGTH);
        System.arraycopy (PRESET_PARAMETERS, 0, presetRecord, Emulator2Constants.PRESET_NAME_OFFSET + Emulator2Constants.PRESET_NAME_LENGTH, PRESET_PARAMETERS.length);

        int position = Emulator2Constants.PRESET_ENTRIES_OFFSET;
        for (final KeyRange range: preset.ranges)
        {
            final int mode = range.voice == 0 ? 1 : range.secondVoice > 0 ? Emulator2Constants.ENTRY_MODE_DUAL : 2;
            presetRecord[position] = (byte) (mode << Emulator2Constants.ENTRY_MODE_SHIFT | range.numKeys);
            presetRecord[position + 1] = 0x08;
            presetRecord[position + Emulator2Constants.ENTRY_VOICE] = (byte) range.voice;
            presetRecord[position + Emulator2Constants.ENTRY_TRANSPOSE] = (byte) range.transpose;
            presetRecord[position + Emulator2Constants.ENTRY_LEVEL] = (byte) (range.voice > 0 ? RANGE_LEVEL : 0);
            position += Emulator2Constants.ENTRY_SIZE;
            if (range.secondVoice > 0)
            {
                presetRecord[position] = (byte) range.secondVoice;
                presetRecord[position + 1] = (byte) range.secondTranspose;
                presetRecord[position + 2] = (byte) RANGE_LEVEL;
                position += Emulator2Constants.ENTRY_SECONDARY_SIZE;
            }
        }
        // The end marker: an entry of no keys at the key behind the keyboard
        presetRecord[position + 1] = (byte) Emulator2Constants.NUM_KEYS;
        return presetRecord;
    }


    /**
     * Write a voice record: the template of the factory library with the name, the addresses of the
     * audio and the loop, the loop flag and the pointers of the record.
     *
     * @param image The disk image
     * @param voiceRecord The position of the record
     * @param index The index of the record
     * @param voice The voice
     */
    private static void writeVoiceRecord (final byte [] image, final int voiceRecord, final int index, final Voice voice)
    {
        for (int i = 0; i < Emulator2Constants.VOICE_SIZE; i++)
            image[voiceRecord + i] = (byte) VOICE_TEMPLATE[i];
        for (final int pointer: VOICE_POINTER_BYTES)
            image[voiceRecord + pointer] = (byte) (VOICE_RECORD_ADDRESS + index * Emulator2Constants.VOICE_SIZE >> 8);

        final byte [] name = pad (voice.name, Emulator2Constants.VOICE_NAME_LENGTH);
        System.arraycopy (name, 0, image, voiceRecord + Emulator2Constants.VOICE_NAME, Emulator2Constants.VOICE_NAME_LENGTH);

        final int end = voice.start + voice.audio.length;
        final int slot = voice.audio.length + voice.loopLength + Emulator2Constants.VOICE_SLOT_PADDING;
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_START_MINUS_ONE, voice.start - 1);
        writeAddress (image, voiceRecord + 0x06, COUNTER_BASE - voice.audio.length);
        writeAddress (image, voiceRecord + 0x0A, end - 1);
        writeAddress (image, voiceRecord + 0x0E, COUNTER_BASE - voice.loopLength);
        writeAddress (image, voiceRecord + 0x13, COUNTER_BASE - voice.loopLength);
        image[voiceRecord + Emulator2Constants.VOICE_FLAGS] = (byte) (VOICE_FLAGS_DEFAULT | (voice.hasLoop ? Emulator2Constants.VOICE_FLAG_LOOP : 0));
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_SAMPLE_START, voice.start);
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_SLOT_SIZE, slot);
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_SAMPLE_END, end);
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_LOOP_LENGTH, voice.loopLength);
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_LOOP_START, voice.start + voice.loopStart);
        writeAddress (image, voiceRecord + Emulator2Constants.VOICE_LOOP_START + 3, slot);
    }


    /**
     * Pad a name to a fixed length with spaces, keeping only ASCII characters.
     *
     * @param text The name
     * @param length The length
     * @return The padded name
     */
    private static byte [] pad (final String text, final int length)
    {
        final byte [] result = new byte [length];
        Arrays.fill (result, (byte) ' ');
        final byte [] bytes = text.getBytes (StandardCharsets.US_ASCII);
        for (int i = 0; i < Math.min (length, bytes.length); i++)
            result[i] = bytes[i] >= 0x20 && bytes[i] < 0x7F ? bytes[i] : (byte) ' ';
        return result;
    }


    /**
     * Write a 24 bit little-endian value.
     *
     * @param image The disk image
     * @param offset The position of the value
     * @param value The value
     */
    private static void writeAddress (final byte [] image, final int offset, final int value)
    {
        image[offset] = (byte) (value & 0xFF);
        image[offset + 1] = (byte) (value >> 8 & 0xFF);
        image[offset + 2] = (byte) (value >> 16 & 0xFF);
    }


    /**
     * Write a 16 bit little-endian value.
     *
     * @param image The disk image
     * @param offset The position of the value
     * @param value The value
     */
    private static void writeWord (final byte [] image, final int offset, final int value)
    {
        image[offset] = (byte) (value & 0xFF);
        image[offset + 1] = (byte) (value >> 8 & 0xFF);
    }


    /** A voice of the bank which is being built. */
    private static class Voice
    {
        String  name;
        byte [] audio;
        int     start;
        boolean hasLoop;
        int     loopStart;
        int     loopLength;


        /**
         * Shorten the audio.
         *
         * @param numFrames The new number of frames
         */
        void truncate (final int numFrames)
        {
            this.audio = Arrays.copyOf (this.audio, numFrames);
            if (this.hasLoop && this.loopStart + this.loopLength > numFrames)
            {
                this.loopLength = numFrames - this.loopStart;
                if (this.loopLength < MINIMUM_LOOP_LENGTH)
                {
                    this.hasLoop = false;
                    this.loopStart = 0;
                    this.loopLength = Math.min (UNLOOPED_LOOP_LENGTH, numFrames);
                }
            }
        }
    }


    /** A range of keys of a preset which plays one or two voices. */
    private static class KeyRange
    {
        int firstKey;
        int numKeys;
        int voice;
        int transpose;
        int secondVoice;
        int secondTranspose;
    }


    /** A preset of the bank which is being built. */
    private static class Preset
    {
        String               name   = "";
        final List<KeyRange> ranges = new ArrayList<> ();
    }


    /** The bank which is being built. */
    private static class BankBuilder
    {
        final List<Voice>          voices                = new ArrayList<> ();
        final List<Preset>         presets               = new ArrayList<> ();
        final Map<Object, Integer> voiceNumbersByContent = new HashMap<> ();
    }
}
