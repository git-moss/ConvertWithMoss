// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.hfe.EmuFmDisk;
import de.mossgrabers.convertwithmoss.format.emu.EmuCompanding;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects and reads the sound banks of the E-mu Emulator II. A bank is stored in the second part of
 * a floppy disk, which is read either from a raw sector image (IMG, EMUIIFD) or from a HxC floppy
 * emulator image (HFE) - the Emulator II writes a FM track format which no PC floppy controller can
 * read, so an image is the only way to get such a disk onto a computer. A bank file (EII) holds the
 * bank memory alone, as the Sound Designer software of the eighties received it from the sampler
 * and as EMXP writes it and Arturia's Emulator II V reads it.
 * <p>
 * A bank holds up to 100 voices - a sample with its loop and its settings - and up to 100 presets,
 * each of which assigns a voice, a transposition and optionally a second voice to ranges of the 61
 * keys. Every preset becomes one multi-sample: the key ranges give the zones with their root keys,
 * each voice contributes its name, its loop and its audio, which is expanded from the companded
 * bytes the sampler feeds to its AM6072 DAC, and the second voices of the ranges become a second
 * group. See documentation/design/EMULATOR2_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator2Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ENDING_HFE           = ".hfe";
    private static final String ENDING_BANK          = ".eii";
    /** A bank file holds at least the key maps and one voice record. */
    private static final int    MINIMUM_BANK_SIZE    = Emulator2Constants.VOICE_TABLE + Emulator2Constants.VOICE_SIZE;
    /** How far into a bank file the first voice records are searched for. */
    private static final int    BANK_SEARCH_LIMIT    = 0x2000;
    /** The memory page of the first voice record. */
    private static final int    FIRST_VOICE_PAGE     = Emulator2Constants.BANK_ADDRESS + Emulator2Constants.VOICE_TABLE >> 8;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator2Detector (final INotifier notifier)
    {
        super ("E-mu Emulator II", "EII", notifier, new MetadataSettingsUI ("EII"), ".img", ".emuiifd", ".eii", ENDING_HFE);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Optional<BankImage> image = this.readImage (sourceFile);
            // Images of other formats are silently ignored, they belong to other detectors
            if (image.isEmpty ())
                return Collections.emptyList ();
            return this.parseBank (sourceFile, image.get ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Get the bank of a file: the raw sector image of a disk, decoded from a HFE container if
     * necessary, or a bank file, which holds the bank memory alone.
     *
     * @param sourceFile The file to read
     * @return The image and the position of the bank in it, or empty if the file is not an
     *         Emulator II disk
     * @throws IOException Could not read the file
     */
    private Optional<BankImage> readImage (final File sourceFile) throws IOException
    {
        final String name = sourceFile.getName ().toLowerCase (Locale.US);
        if (name.endsWith (ENDING_HFE))
        {
            final Optional<byte []> image = EmuFmDisk.readImage (this.notifier, sourceFile, Emulator2Constants.CYLINDERS, Emulator2Constants.HEADS);
            return image.isEmpty () ? Optional.empty () : Optional.of (new BankImage (image.get (), Emulator2Constants.BANK_OFFSET));
        }

        final long length = sourceFile.length ();
        if (length == Emulator2Constants.IMAGE_SIZE)
            return Optional.of (new BankImage (Files.readAllBytes (sourceFile.toPath ()), Emulator2Constants.BANK_OFFSET));

        // A bank file holds the bank memory without the operating system; the ones which the
        // Sound Designer software wrote are one byte short of the memory size
        if (name.endsWith (ENDING_BANK))
        {
            if (length < MINIMUM_BANK_SIZE || length > Emulator2Constants.IMAGE_SIZE)
                throw new IOException (Functions.getMessage ("IDS_EMU_UNEXPECTED_IMAGE_SIZE", Integer.toString (Emulator2Constants.BANK_MEMORY_SIZE), Long.toString (length)));
            final byte [] image = Files.readAllBytes (sourceFile.toPath ());
            return Optional.of (new BankImage (image, findBankOffset (image)));
        }

        // An IMG file of another size belongs to another format
        if (name.endsWith (".img"))
            return Optional.empty ();
        throw new IOException (Functions.getMessage ("IDS_EMU_UNEXPECTED_IMAGE_SIZE", Integer.toString (Emulator2Constants.IMAGE_SIZE), Long.toString (length)));
    }


    /**
     * Find where the bank starts in a bank file. The voice records point into themselves with
     * their memory address, whose high byte is the page of the record - 0x9B for the first record,
     * one more for each further one - so every record which is found tells where the bank begins,
     * and a file with a header in front of the bank is read all the same.
     *
     * @param image The bank file
     * @return The position of the bank in the file, 0 if no voice record is found
     */
    private static int findBankOffset (final byte [] image)
    {
        final Map<Integer, Integer> votes = new HashMap<> ();
        for (int position = 0; position < BANK_SEARCH_LIMIT && position + Emulator2Constants.VOICE_SIZE <= image.length; position++)
        {
            if ((image[position] & 0xFF) != Emulator2Constants.VOICE_TAG_1 || (image[position + 1] & 0xFF) != Emulator2Constants.VOICE_TAG_2)
                continue;
            final int page = image[position + Emulator2Constants.VOICE_POINTER_PAGE] & 0xFF;
            if (page < FIRST_VOICE_PAGE || page != (image[position + Emulator2Constants.VOICE_POINTER_PAGE_2] & 0xFF))
                continue;
            final int offset = position - (Emulator2Constants.VOICE_TABLE + (page - FIRST_VOICE_PAGE) * Emulator2Constants.VOICE_SIZE);
            if (offset >= 0)
                votes.merge (Integer.valueOf (offset), Integer.valueOf (1), Integer::sum);
        }
        return votes.entrySet ().stream ().max (Map.Entry.comparingByValue ()).map (Map.Entry::getKey).orElse (Integer.valueOf (0)).intValue ();
    }


    /**
     * Parse a bank into one multi-sample per preset.
     *
     * @param sourceFile The file the image came from
     * @param image The image and the position of the bank in it
     * @return The multi-samples
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final BankImage image)
    {
        final Bank bank = new Bank (image.image, image.bankOffset);
        if (bank.voices.isEmpty ())
        {
            this.notifier.logError ("IDS_EII_NO_VOICES", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final Preset preset: bank.presets)
        {
            final Optional<IMultisampleSource> multisampleSource = this.createMultisample (sourceFile, bank, preset);
            if (multisampleSource.isPresent ())
                multisampleSources.add (multisampleSource.get ());
        }

        // A bank whose preset list cannot be read still has the key map of the selected preset
        if (multisampleSources.isEmpty ())
        {
            final Preset selected = bank.readSelectedPreset ();
            if (selected != null)
            {
                final Optional<IMultisampleSource> multisampleSource = this.createMultisample (sourceFile, bank, selected);
                if (multisampleSource.isPresent ())
                    multisampleSources.add (multisampleSource.get ());
            }
        }

        // The sampler saves its bank memory and not the whole of its address space, so a voice
        // which was sampled into the memory behind it lost that part
        if (bank.truncatedVoices > 0)
            this.notifier.log ("IDS_EII_INCOMPLETE_BANK", sourceFile.getName (), Integer.toString (bank.truncatedVoices), Integer.toString (Emulator2Constants.BANK_MEMORY_SIZE));
        if (multisampleSources.isEmpty ())
            this.notifier.logError ("IDS_EII_NO_PRESETS", sourceFile.getName ());
        return multisampleSources;
    }


    /**
     * Create the multi-sample of a preset.
     *
     * @param sourceFile The file the image came from
     * @param bank The bank
     * @param preset The preset
     * @return The multi-sample or empty if the preset plays no voice
     */
    private Optional<IMultisampleSource> createMultisample (final File sourceFile, final Bank bank, final Preset preset)
    {
        final List<IGroup> groups = new ArrayList<> ();
        for (int layer = 0; layer < 2; layer++)
        {
            final IGroup group = new DefaultGroup ("Layer " + (layer + 1));
            for (final KeyRange range: preset.ranges)
            {
                final int voiceNumber = layer == 0 ? range.voice : range.secondVoice;
                final int transpose = layer == 0 ? range.transpose : range.secondTranspose;
                final Voice voice = bank.getVoice (voiceNumber);
                if (voice == null)
                    continue;
                final ISampleData sampleData = bank.getSampleData (voice);
                if (sampleData == null)
                    continue;

                final ISampleZone zone = new DefaultSampleZone (voice.name, Emulator2Constants.LOWEST_KEY + range.firstKey, Emulator2Constants.LOWEST_KEY + range.firstKey + range.numKeys - 1);
                // The transposition tells how far the first key of the range is from the pitch the
                // voice was recorded at
                zone.setKeyRoot (Emulator2Constants.LOWEST_KEY + range.firstKey + Emulator2Constants.TRANSPOSE_UNITY - transpose);
                zone.setSampleData (sampleData);
                if (voice.hasLoop)
                {
                    final ISampleLoop loop = new DefaultSampleLoop ();
                    loop.setType (LoopType.FORWARDS);
                    loop.setStart (voice.loopStart);
                    loop.setEnd (voice.loopStart + voice.loopLength);
                    zone.addLoop (loop);
                }
                group.addSampleZone (zone);
            }
            if (!group.getSampleZones ().isEmpty ())
                groups.add (group);
        }
        if (groups.isEmpty ())
            return Optional.empty ();

        final String name = preset.name.isBlank () ? FileUtils.getNameWithoutType (sourceFile) : preset.name;
        return Optional.of (this.createMultisampleSource (sourceFile, name, groups));
    }


    /**
     * Expand the companded bytes of a voice into 16 bit audio.
     *
     * @param image The raw sector image
     * @param offset The position of the audio in the image
     * @param numFrames The number of frames
     * @return The sample data
     */
    private static InMemorySampleData createSampleData (final byte [] image, final int offset, final int numFrames)
    {
        final byte [] pcm = new byte [numFrames * 2];
        for (int i = 0; i < numFrames; i++)
        {
            final short value = EmuCompanding.expand (image[offset + i]);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) (value >> 8 & 0xFF);
        }
        return new InMemorySampleData (new DefaultAudioMetadata (1, Emulator2Constants.SAMPLE_RATE, 16, numFrames), pcm);
    }


    /**
     * Read an address, which is stored as 24 bit little-endian and is relative to the bank.
     *
     * @param image The raw sector image
     * @param offset The position of the address
     * @return The address
     */
    private static int readAddress (final byte [] image, final int offset)
    {
        if (offset < 0 || offset + 2 >= image.length)
            return -1;
        return (image[offset] & 0xFF) | (image[offset + 1] & 0xFF) << 8 | (image[offset + 2] & 0xFF) << 16;
    }


    /**
     * Read a 16 bit little-endian value.
     *
     * @param image The raw sector image
     * @param offset The position of the value
     * @return The value or -1 if the position is outside of the image
     */
    private static int readWord (final byte [] image, final int offset)
    {
        if (offset < 0 || offset + 1 >= image.length)
            return -1;
        return (image[offset] & 0xFF) | (image[offset + 1] & 0xFF) << 8;
    }


    /**
     * Read one of the fixed length names of the bank.
     *
     * @param image The raw sector image
     * @param offset The position of the name
     * @param length The length of the name
     * @return The name, empty if there is none or it is not text
     */
    private static String readName (final byte [] image, final int offset, final int length)
    {
        if (offset < 0 || offset + length > image.length || !isText (image, offset, length))
            return "";
        return new String (image, offset, length, StandardCharsets.US_ASCII).trim ();
    }


    /**
     * Test whether some bytes are printable ASCII.
     *
     * @param image The raw sector image
     * @param offset The position of the bytes
     * @param length The number of bytes
     * @return True if all of them are printable
     */
    private static boolean isText (final byte [] image, final int offset, final int length)
    {
        if (offset < 0 || offset + length > image.length)
            return false;
        for (int i = 0; i < length; i++)
        {
            final int c = image[offset + i] & 0xFF;
            if (c < 0x20 || c > 0x7E)
                return false;
        }
        return true;
    }


    /** A voice of the bank: a sample with its loop. */
    private static class Voice
    {
        String  name;
        /** The start of the playback relative to the bank. */
        int     start;
        /** The number of frames the voice plays, up to the end of its loop. */
        int     numFrames;
        boolean hasLoop;
        /** The start of the loop relative to the playback start. */
        int     loopStart;
        int     loopLength;
    }


    /** An image and the position of the bank in it. */
    private static class BankImage
    {
        final byte [] image;
        final int     bankOffset;


        /**
         * Constructor.
         *
         * @param image The image
         * @param bankOffset The position of the bank in the image
         */
        BankImage (final byte [] image, final int bankOffset)
        {
            this.image = image;
            this.bankOffset = bankOffset;
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


    /** A preset of the bank. */
    private static class Preset
    {
        String               name         = "";
        @SuppressWarnings("unused")
        int                  recordLength = 0;
        final List<KeyRange> ranges       = new ArrayList<> ();
    }


    /** The parsed structures of a bank. */
    private class Bank
    {
        private final byte []                 image;
        private final int                     bankOffset;
        private final int                     bankEnd;
        final List<Voice>                     voices     = new ArrayList<> ();
        final List<Preset>                    presets    = new ArrayList<> ();
        int                                   truncatedVoices;
        private final Map<Voice, ISampleData> sampleData = new HashMap<> ();


        /**
         * Parse the voices and presets of a bank.
         *
         * @param image The image
         * @param bankOffset The position of the bank in the image
         */
        Bank (final byte [] image, final int bankOffset)
        {
            this.image = image;
            this.bankOffset = bankOffset;
            this.bankEnd = Math.min (image.length, bankOffset + Emulator2Constants.BANK_MEMORY_SIZE);
            this.readHeap ();
        }


        /**
         * Read the voice records and the preset records, which share the memory behind the key
         * maps: voice records are allocated in slots of 256 bytes and a chain of preset records
         * starts with the length of its first record. Usually all voices come first and the presets
         * follow, but a disk which was saved with an empty preset selected starts with that preset,
         * and its voices follow in the next slot.
         */
        private void readHeap ()
        {
            final int table = this.bankOffset + Emulator2Constants.VOICE_TABLE;
            int position = table;
            while (position + Emulator2Constants.VOICE_SIZE <= this.bankEnd)
            {
                if (this.isVoiceRecord (position))
                {
                    if (this.voices.size () >= Emulator2Constants.MAX_VOICES)
                        break;
                    this.readVoice (position);
                    position += Emulator2Constants.VOICE_SIZE;
                    continue;
                }

                final int length = readWord (this.image, position);
                if (length <= Emulator2Constants.PRESET_ENTRIES_OFFSET || !this.isPresetRecord (position + 2))
                    break;
                position = this.readPresets (position + 2, length);
                // The next voice records start at the next slot
                final int slot = (position - table + Emulator2Constants.VOICE_SIZE - 1) / Emulator2Constants.VOICE_SIZE;
                position = table + slot * Emulator2Constants.VOICE_SIZE;
            }
        }


        /**
         * Test whether a voice record starts at a position: every record starts with the same two
         * tag bytes and its name is text.
         *
         * @param position The position
         * @return True if there is a voice record
         */
        private boolean isVoiceRecord (final int position)
        {
            return position + Emulator2Constants.VOICE_SIZE <= this.image.length && (this.image[position] & 0xFF) == Emulator2Constants.VOICE_TAG_1 && (this.image[position + 1] & 0xFF) == Emulator2Constants.VOICE_TAG_2;
        }


        /**
         * Test whether a preset record starts at a position: the last byte of its header has the
         * top bit set and its name is text.
         *
         * @param position The position
         * @return True if there is a preset record
         */
        private boolean isPresetRecord (final int position)
        {
            return position + Emulator2Constants.PRESET_ENTRIES_OFFSET <= this.image.length && (this.image[position + Emulator2Constants.PRESET_HEADER_SIZE - 1] & 0x80) != 0 && isText (this.image, position + Emulator2Constants.PRESET_NAME_OFFSET, Emulator2Constants.PRESET_NAME_LENGTH);
        }


        /**
         * Read one voice record.
         *
         * @param voiceRecord The position of the record
         */
        private void readVoice (final int voiceRecord)
        {
            final Voice voice = new Voice ();
            voice.name = readName (this.image, voiceRecord + Emulator2Constants.VOICE_NAME, Emulator2Constants.VOICE_NAME_LENGTH);
            if (voice.name.isEmpty ())
                voice.name = "Voice " + (this.voices.size () + 1);
            // The memory region of a voice holds the audio in front of the loop, which plays once,
            // the loop, which repeats when it is switched on and plays once when it is not, and a
            // few padding bytes. Playback starts at the region start unless the voice was set to
            // start later, e.g. a copy of a voice which plays only its sustained part
            final int regionStart = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_REGION_START);
            final int slot = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_SLOT_SIZE);
            int playStart = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_PLAY_START);
            if (playStart < regionStart || playStart >= regionStart + slot)
                playStart = regionStart;
            final int loopStart = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_LOOP_START) - playStart;
            final int loopLength = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_LOOP_LENGTH);
            final int playLength = readAddress (this.image, voiceRecord + Emulator2Constants.VOICE_PLAY_LENGTH);
            final boolean loopOn = (this.image[voiceRecord + Emulator2Constants.VOICE_FLAGS] & Emulator2Constants.VOICE_FLAG_LOOP) != 0 && loopLength > Emulator2Constants.VOICE_NO_LOOP_LENGTH + 1;
            final int padding = loopOn ? Emulator2Constants.VOICE_SLOT_PADDING : Emulator2Constants.VOICE_UNLOOPED_PADDING;

            // The audio ends at the end of the loop, which the length field of the voice and the
            // region confirm - or limit, since the loop fields of a voice which was cut out of a
            // shared recording are stale, and the loop start of some others lies behind the region
            int numFrames = Integer.MAX_VALUE;
            if (slot > Emulator2Constants.VOICE_UNLOOPED_PADDING)
                numFrames = regionStart + slot - Emulator2Constants.VOICE_UNLOOPED_PADDING - playStart;
            if (playLength > padding)
                numFrames = Math.min (numFrames, playLength - padding);
            if (loopOn)
                numFrames = Math.min (numFrames, loopStart + loopLength);
            if (numFrames == Integer.MAX_VALUE)
                numFrames = 0;

            // The sampler saves its bank memory and not the whole of its address space, so the
            // audio of a voice which was sampled into the memory behind it ends there
            final int available = this.bankEnd - this.bankOffset - playStart;
            if (numFrames > available)
            {
                numFrames = Math.max (0, available);
                this.truncatedVoices++;
            }

            voice.start = playStart;
            voice.numFrames = numFrames;
            voice.hasLoop = loopOn && loopStart >= 0 && loopStart + Emulator2Constants.VOICE_NO_LOOP_LENGTH + 1 < numFrames;
            voice.loopStart = loopStart;
            voice.loopLength = Math.min (loopLength, numFrames - loopStart);
            this.voices.add (voice);
        }


        /**
         * Read a chain of preset records. Each record ends with the length of the next one, zero
         * ends the chain.
         *
         * @param start The position of the first record
         * @param firstLength The length of the first record
         * @return The position behind the last record
         */
        private int readPresets (final int start, final int firstLength)
        {
            int position = start;
            int length = firstLength;
            while (length > Emulator2Constants.PRESET_ENTRIES_OFFSET && position + length <= this.bankEnd && this.presets.size () < 100 && this.isPresetRecord (position))
            {
                this.presets.add (this.readPreset (position, length));
                // The last two bytes of a record hold the length of the next one, zero ends the
                // list
                final int nextLength = readWord (this.image, position + length - 2);
                position += length;
                length = nextLength;
            }
            return position;
        }


        /**
         * Read one preset record.
         *
         * @param position The position of the record
         * @param length The length of the record
         * @return The preset
         */
        private Preset readPreset (final int position, final int length)
        {
            final Preset preset = new Preset ();
            preset.name = readName (this.image, position + Emulator2Constants.PRESET_NAME_OFFSET, Emulator2Constants.PRESET_NAME_LENGTH);
            preset.recordLength = length;

            int entry = position + Emulator2Constants.PRESET_ENTRIES_OFFSET;
            int key = 0;
            while (entry + Emulator2Constants.ENTRY_SIZE <= position + length && key < Emulator2Constants.NUM_KEYS)
            {
                final int first = this.image[entry] & 0xFF;
                final int mode = first >> Emulator2Constants.ENTRY_MODE_SHIFT;
                final int count = first & Emulator2Constants.ENTRY_COUNT_MASK;
                if (mode == Emulator2Constants.ENTRY_MODE_END || count == 0)
                    break;

                final KeyRange range = new KeyRange ();
                range.firstKey = key;
                range.numKeys = Math.min (count, Emulator2Constants.NUM_KEYS - key);
                range.voice = this.image[entry + Emulator2Constants.ENTRY_VOICE] & 0xFF;
                range.transpose = this.image[entry + Emulator2Constants.ENTRY_TRANSPOSE] & 0xFF;
                entry += Emulator2Constants.ENTRY_SIZE;
                if (mode == Emulator2Constants.ENTRY_MODE_DUAL)
                {
                    if (entry + Emulator2Constants.ENTRY_SECONDARY_SIZE <= position + length)
                    {
                        range.secondVoice = this.image[entry] & 0xFF;
                        range.secondTranspose = this.image[entry + 1] & 0xFF;
                    }
                    entry += Emulator2Constants.ENTRY_SECONDARY_SIZE;
                }
                if (range.voice > 0 || range.secondVoice > 0)
                    preset.ranges.add (range);
                key += count;
            }
            return preset;
        }


        /**
         * Read the selected preset from its expanded key maps, for a bank whose preset records
         * cannot be read.
         *
         * @return The preset or null if the key map is empty
         */
        Preset readSelectedPreset ()
        {
            final Preset preset = new Preset ();
            preset.name = readName (this.image, this.bankOffset + Emulator2Constants.SELECTED_PRESET + Emulator2Constants.PRESET_NAME_OFFSET, Emulator2Constants.PRESET_NAME_LENGTH);
            int key = 0;
            while (key < Emulator2Constants.NUM_KEYS)
            {
                final int voiceID = this.image[this.bankOffset + Emulator2Constants.KEY_MAP_VOICE_ID + key] & 0xFF;
                if (voiceID < Emulator2Constants.VOICE_ID_BASE)
                {
                    key++;
                    continue;
                }
                // A range runs while the voice stays the same and the transposition keeps rising by
                // one semitone per key; where it restarts, the voice is mapped again at another
                // root
                int last = key;
                while (last + 1 < Emulator2Constants.NUM_KEYS && (this.image[this.bankOffset + Emulator2Constants.KEY_MAP_VOICE_ID + last + 1] & 0xFF) == voiceID && this.transpose (last + 1) == this.transpose (last) + 1)
                    last++;
                final KeyRange range = new KeyRange ();
                range.firstKey = key;
                range.numKeys = last - key + 1;
                // The key map holds identifiers, the ranges of the records hold voice numbers
                range.voice = -(voiceID - Emulator2Constants.VOICE_ID_BASE + 1);
                range.transpose = this.transpose (key);
                preset.ranges.add (range);
                key = last + 1;
            }
            return preset.ranges.isEmpty () ? null : preset;
        }


        /**
         * Get the transposition which the selected preset stores for a key.
         *
         * @param key The key
         * @return The transposition
         */
        private int transpose (final int key)
        {
            return this.image[this.bankOffset + Emulator2Constants.KEY_MAP_TRANSPOSE + key] & 0xFF;
        }


        /**
         * Get a voice by its number. The number is the position of the voice in the voice list of
         * the bank, which holds the identifier of the record; a negative number addresses the
         * record directly.
         *
         * @param number The 1-based voice number or the negated 1-based record index
         * @return The voice or null if there is none
         */
        Voice getVoice (final int number)
        {
            if (number == 0)
                return null;
            int index;
            if (number < 0)
                index = -number - 1;
            else
            {
                final int listEntry = this.bankOffset + Emulator2Constants.VOICE_LIST + number - 1;
                final int id = number <= Emulator2Constants.VOICE_LIST_SIZE && listEntry < this.image.length ? this.image[listEntry] & 0xFF : 0;
                // A bank without a voice list numbers its records in their order
                index = id >= Emulator2Constants.VOICE_ID_BASE ? id - Emulator2Constants.VOICE_ID_BASE : number - 1;
            }
            return index >= 0 && index < this.voices.size () ? this.voices.get (index) : null;
        }


        /**
         * Get the audio of a voice, which is expanded once and shared by all presets that play the
         * voice.
         *
         * @param voice The voice
         * @return The audio or null if the voice points at no usable audio
         */
        ISampleData getSampleData (final Voice voice)
        {
            if (this.sampleData.containsKey (voice))
                return this.sampleData.get (voice);
            ISampleData data = null;
            if (voice.start > 0 && voice.numFrames > 0 && this.bankOffset + voice.start + voice.numFrames <= this.image.length)
                data = createSampleData (this.image, this.bankOffset + voice.start, voice.numFrames);
            this.sampleData.put (voice, data);
            return data;
        }
    }
}
