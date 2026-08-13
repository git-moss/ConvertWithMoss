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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.hfe.DiskImageBuilder;
import de.mossgrabers.convertwithmoss.file.hfe.EmuFmDecoder;
import de.mossgrabers.convertwithmoss.file.hfe.HfeFile;
import de.mossgrabers.convertwithmoss.file.hfe.Sector;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects and reads the sound banks of the E-mu Emulator II. A bank is stored in the second part of
 * a floppy disk, which is read either from a raw sector image (IMG, EMUIIFD) or from a HxC floppy
 * emulator image (HFE) - the Emulator II writes a FM track format which no PC floppy controller can
 * read, so an image is the only way to get such a disk onto a computer.
 * <p>
 * One disk becomes one multi-sample: the key map of the bank assigns a voice and a transposition to
 * each of the 61 keys, which gives the zones with their key ranges and root keys, and each voice
 * points at its audio, its loop and its name. The audio is expanded from the companded bytes which
 * the sampler feeds to its AM6072 DAC. See documentation/design/EMULATOR2_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator2Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final int    CYLINDERS = 80;
    private static final int    HEADS     = 2;
    private static final String ENDING_HFE = ".hfe";


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
            final byte [] image = this.readImage (sourceFile);
            // Images of other formats are silently ignored, they belong to other detectors
            if (image == null)
                return Collections.emptyList ();
            return this.parseBank (sourceFile, image);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Get the raw sector image of a disk, decoding a HFE container if necessary.
     *
     * @param sourceFile The file to read
     * @return The image or null if the file is not an Emulator II disk
     * @throws IOException Could not read the file
     */
    private byte [] readImage (final File sourceFile) throws IOException
    {
        if (sourceFile.getName ().toLowerCase (Locale.US).endsWith (ENDING_HFE))
        {
            final HfeFile hfeFile = new HfeFile (sourceFile);
            if (hfeFile.getTrackEncoding () != HfeFile.ENCODING_EMU_FM)
                return null;
            final List<Sector> sectors = hfeFile.decodeSectors ();
            if (sectors.isEmpty ())
                return null;
            return DiskImageBuilder.buildImage (sectors, CYLINDERS, HEADS, 1, EmuFmDecoder.SECTOR_SIZE, true);
        }

        if (sourceFile.length () != Emulator2Constants.IMAGE_SIZE)
            return null;
        return Files.readAllBytes (sourceFile.toPath ());
    }


    /**
     * Parse the bank of a disk image into one multi-sample.
     *
     * @param sourceFile The file the image came from
     * @param image The raw sector image
     * @return The multi-sample, or an empty list if the bank maps no key at all
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final byte [] image)
    {
        final int bank = Emulator2Constants.BANK_OFFSET;
        if (image.length < bank + Emulator2Constants.VOICE_TABLE)
            return Collections.emptyList ();

        final List<ISampleZone> zones = new ArrayList<> ();
        final Set<Integer> incomplete = new HashSet<> ();
        int key = 0;
        while (key < Emulator2Constants.NUM_KEYS)
        {
            final int voiceID = image[bank + Emulator2Constants.KEY_MAP_VOICE_ID + key] & 0xFF;
            if (voiceID < Emulator2Constants.VOICE_ID_BASE)
            {
                key++;
                continue;
            }

            // A zone runs while the voice stays the same and the transposition keeps rising by one
            // semitone per key; where it restarts, the same voice is mapped again at another root
            int last = key;
            while (last + 1 < Emulator2Constants.NUM_KEYS && (image[bank + Emulator2Constants.KEY_MAP_VOICE_ID + last + 1] & 0xFF) == voiceID && transpose (image, last + 1) == transpose (image, last) + 1)
                last++;

            final int voiceIndex = voiceID - Emulator2Constants.VOICE_ID_BASE;
            final ISampleZone zone = this.createZone (image, voiceIndex, key, last);
            if (zone == null)
                incomplete.add (Integer.valueOf (voiceIndex));
            else
                zones.add (zone);
            key = last + 1;
        }

        if (!incomplete.isEmpty ())
            this.notifier.logError ("IDS_EII_INCOMPLETE_BANK", sourceFile.getName (), Integer.toString (incomplete.size ()));
        if (zones.isEmpty ())
            return Collections.emptyList ();

        final String name = readName (image, bank + findPresetName (image), FileUtils.getNameWithoutType (sourceFile));
        final IGroup group = new DefaultGroup ("Layer 1");
        for (final ISampleZone zone: zones)
            group.addSampleZone (zone);

        return Collections.singletonList (this.createMultisampleSource (sourceFile, name, Collections.singletonList (group)));
    }


    /**
     * Create one zone from a run of keys which all play the same voice.
     *
     * @param image The raw sector image
     * @param voiceIndex The zero based index of the voice record
     * @param lowKey The first key of the run
     * @param highKey The last key of the run
     * @return The zone or null if the voice points at no usable audio
     */
    private ISampleZone createZone (final byte [] image, final int voiceIndex, final int lowKey, final int highKey)
    {
        final int bank = Emulator2Constants.BANK_OFFSET;
        final int record = bank + Emulator2Constants.VOICE_TABLE + voiceIndex * Emulator2Constants.VOICE_SIZE;
        if (record + Emulator2Constants.VOICE_SIZE > image.length)
            return null;

        final int start = readAddress (image, record + Emulator2Constants.VOICE_SAMPLE_START);
        final int end = readAddress (image, record + Emulator2Constants.VOICE_SAMPLE_END);
        final int numFrames = end - start;
        if (start <= 0 || numFrames <= 0 || bank + end > image.length)
            return null;

        final String name = readName (image, record, "Voice " + (voiceIndex + 1));
        // The transposition tells how far the key is from the pitch the voice was recorded at
        final int root = lowKey - (transpose (image, lowKey) - Emulator2Constants.TRANSPOSE_UNITY);

        final ISampleZone zone = new DefaultSampleZone (name, lowKey + Emulator2Constants.LOWEST_KEY, highKey + Emulator2Constants.LOWEST_KEY);
        zone.setKeyRoot (root + Emulator2Constants.LOWEST_KEY);
        zone.setSampleData (createSampleData (image, bank + start, numFrames));

        final int loopStart = readAddress (image, record + Emulator2Constants.VOICE_LOOP_START);
        final int loopLength = readAddress (image, record + Emulator2Constants.VOICE_LOOP_LENGTH);
        if (loopLength > 0 && loopStart >= start && loopStart + loopLength <= end)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (loopStart - start);
            loop.setEnd (loopStart - start + loopLength);
            zone.addLoop (loop);
        }
        return zone;
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
            final short value = Emulator2Constants.expand (image[offset + i]);
            pcm[i * 2] = (byte) (value & 0xFF);
            pcm[i * 2 + 1] = (byte) (value >> 8 & 0xFF);
        }
        return new InMemorySampleData (new DefaultAudioMetadata (1, Emulator2Constants.SAMPLE_RATE, 16, numFrames), pcm);
    }


    /**
     * Get the transposition which is stored for a key.
     *
     * @param image The raw sector image
     * @param key The key
     * @return The transposition
     */
    private static int transpose (final byte [] image, final int key)
    {
        return image[Emulator2Constants.BANK_OFFSET + Emulator2Constants.KEY_MAP_TRANSPOSE + key] & 0xFF;
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
        return (image[offset] & 0xFF) | (image[offset + 1] & 0xFF) << 8 | (image[offset + 2] & 0xFF) << 16;
    }


    /**
     * Read one of the fixed length names of the bank.
     *
     * @param image The raw sector image
     * @param offset The position of the name
     * @param fallback The name to use if there is none
     * @return The name
     */
    private static String readName (final byte [] image, final int offset, final String fallback)
    {
        if (offset < 0 || offset + Emulator2Constants.VOICE_NAME_LENGTH > image.length)
            return fallback;
        final String name = new String (image, offset, Emulator2Constants.VOICE_NAME_LENGTH, StandardCharsets.US_ASCII).trim ();
        return name.isEmpty () ? fallback : name;
    }


    /**
     * Find the name of the first preset of the bank, which names the disk.
     *
     * @param image The raw sector image
     * @return The offset of the name relative to the bank or -1 if there is no preset
     */
    private static int findPresetName (final byte [] image)
    {
        final byte [] signature = Emulator2Constants.PRESET_SIGNATURE;
        for (int i = Emulator2Constants.BANK_OFFSET; i < image.length - signature.length - Emulator2Constants.VOICE_NAME_LENGTH; i++)
        {
            int k = 0;
            while (k < signature.length && image[i + k] == signature[k])
                k++;
            if (k == signature.length)
                return i + Emulator2Constants.PRESET_NAME_OFFSET - Emulator2Constants.BANK_OFFSET;
        }
        return -1;
    }
}
