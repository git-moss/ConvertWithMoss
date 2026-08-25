// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
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
 * Detects and reads the sound disks of the E-mu Emulator, the first Emulator of 1981. A disk holds
 * the operating system, the two banks of the lower and the upper half of the keyboard and the
 * memory of the sequencer; it is read either from a raw sector image (EMUFD, IMG) or from a HxC
 * floppy emulator image (HFE) - the Emulator writes a FM track format which no PC floppy controller
 * can read, so an image is the only way to get such a disk onto a computer.
 * <p>
 * One disk becomes one multi-sample: each bank divides its half of the keyboard into up to eight
 * zones of equal width which each play one sample, and its pitch table gives the pitch of every
 * key, from which the root key and the tuning of a zone follow. A bank which was made before the
 * multi-sampling software existed holds one sample for its whole half. The audio is expanded from
 * the companded bytes which the sampler feeds to its AM6072 DAC. See
 * documentation/design/EMULATOR1_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator1Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ENDING_HFE = ".hfe";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator1Detector (final INotifier notifier)
    {
        super ("E-mu Emulator", "EI", notifier, new MetadataSettingsUI ("EI"), ".emufd", ".img", ENDING_HFE);
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final Optional<byte []> image = this.readImage (sourceFile);
            // Images of other formats are silently ignored, they belong to other detectors
            if (image.isEmpty ())
                return Collections.emptyList ();
            return this.parseDisk (sourceFile, image.get ());
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
     * @return The image or empty if the file is not an Emulator disk
     * @throws IOException Could not read the file
     */
    private Optional<byte []> readImage (final File sourceFile) throws IOException
    {
        if (sourceFile.getName ().toLowerCase (Locale.US).endsWith (ENDING_HFE))
            return EmuFmDisk.readImage (this.notifier, sourceFile, Emulator1Constants.NUM_TRACKS, 1);

        // An IMG file of another size belongs to another format
        if (sourceFile.length () != Emulator1Constants.IMAGE_SIZE)
        {
            if (sourceFile.getName ().toLowerCase (Locale.US).endsWith (".img"))
                return Optional.empty ();
            throw new IOException (Functions.getMessage ("IDS_EMU_UNEXPECTED_IMAGE_SIZE", Integer.toString (Emulator1Constants.IMAGE_SIZE), Long.toString (sourceFile.length ())));
        }
        return Optional.of (Files.readAllBytes (sourceFile.toPath ()));
    }


    /**
     * Parse the two banks of a disk image into one multi-sample.
     *
     * @param sourceFile The file the image came from
     * @param image The raw sector image
     * @return The multi-sample, or an empty list if the disk holds no sample
     */
    private List<IMultisampleSource> parseDisk (final File sourceFile, final byte [] image)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        zones.addAll (parseBank (image, Emulator1Constants.LOWER_BANK_OFFSET, Emulator1Constants.LOWEST_KEY, Emulator1Constants.KEYS_PER_HALF, Emulator1Constants.DEFAULT_ROOT_LOWER, "Lower"));
        zones.addAll (parseBank (image, Emulator1Constants.UPPER_BANK_OFFSET, Emulator1Constants.SPLIT_KEY, Emulator1Constants.NUM_KEYS - Emulator1Constants.KEYS_PER_HALF, Emulator1Constants.DEFAULT_ROOT_UPPER, "Upper"));
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_EI_NO_SAMPLES", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final IGroup group = new DefaultGroup ("Layer 1");
        for (final ISampleZone zone: zones)
            group.addSampleZone (zone);
        final String name = FileUtils.getNameWithoutType (sourceFile);
        return Collections.singletonList (this.createMultisampleSource (sourceFile, name, Collections.singletonList (group)));
    }


    /**
     * Parse one bank into the zones of its half of the keyboard.
     *
     * @param image The raw sector image
     * @param bankOffset The position of the bank in the image
     * @param lowestKey The MIDI note of the lowest key of the half
     * @param numKeys The number of keys of the half
     * @param defaultRoot The root key of a single sample bank
     * @param zoneName The name of the zones of the bank
     * @return The zones
     */
    private static List<ISampleZone> parseBank (final byte [] image, final int bankOffset, final int lowestKey, final int numKeys, final int defaultRoot, final String zoneName)
    {
        final List<ISampleZone> zones = new ArrayList<> ();
        if (bankOffset + Emulator1Constants.HEADER_SIZE > image.length)
            return zones;

        final int flags = image[bankOffset + Emulator1Constants.RECORD_FLAGS] & 0xFF;
        if ((flags & Emulator1Constants.FLAG_MULTISAMPLE) == 0)
        {
            // A bank which was made before the multi-sampling software existed holds one sample,
            // which starts right behind its record
            final ISampleZone zone = createZone (image, bankOffset, Emulator1Constants.RECORD_SELECTED, zoneName, lowestKey, lowestKey + numKeys - 1);
            if (zone != null)
            {
                zone.setKeyRoot (defaultRoot);
                zones.add (zone);
            }
            return zones;
        }

        final List<Integer> records = findZoneRecords (image, bankOffset);
        if (records.isEmpty ())
            return zones;

        final int numZones = records.size ();
        final int zoneWidth = Emulator1Constants.KEYS_PER_HALF / numZones;
        final int tableOffset = readAddress (image, bankOffset + records.get (0).intValue () + Emulator1Constants.RECORD_TABLE_ADDRESS) - Emulator1Constants.BANK_ADDRESS;
        final boolean hasTable = tableOffset >= Emulator1Constants.RECORD_FIRST_ZONE && tableOffset + Emulator1Constants.TABLE_ENTRIES * 2 <= Emulator1Constants.HEADER_SIZE;

        for (int index = 0; index < numZones; index++)
        {
            final int lowKey = lowestKey + index * zoneWidth;
            // The last zone also takes the keys which the division leaves over, which is the 25th
            // key of the upper half
            final int highKey = index == numZones - 1 ? lowestKey + numKeys - 1 : lowKey + zoneWidth - 1;
            final ISampleZone zone = createZone (image, bankOffset, records.get (index).intValue (), zoneName + " " + (index + 1), lowKey, highKey);
            if (zone == null)
                continue;
            if (hasTable)
                applyPitchTable (image, bankOffset + tableOffset, lowestKey, zone);
            else
                zone.setKeyRoot (defaultRoot);
            zones.add (zone);
        }
        return zones;
    }


    /**
     * Find the records of the zones of a multi-sample bank. They follow the record of the selected
     * sample and each of them carries the address of the pitch table, which lies inside the header
     * of the bank, and the address of its sample.
     *
     * @param image The raw sector image
     * @param bankOffset The position of the bank in the image
     * @return The offsets of the records relative to the bank
     */
    private static List<Integer> findZoneRecords (final byte [] image, final int bankOffset)
    {
        final List<Integer> records = new ArrayList<> ();
        for (int zoneRecord = Emulator1Constants.RECORD_FIRST_ZONE; records.size () < Emulator1Constants.MAX_ZONES; zoneRecord += Emulator1Constants.RECORD_SIZE)
        {
            final int tableAddress = readAddress (image, bankOffset + zoneRecord + Emulator1Constants.RECORD_TABLE_ADDRESS);
            final int sampleStart = readAddress (image, bankOffset + zoneRecord + Emulator1Constants.RECORD_SAMPLE_START);
            if (tableAddress < Emulator1Constants.BANK_ADDRESS + Emulator1Constants.RECORD_FIRST_ZONE || tableAddress >= Emulator1Constants.BANK_ADDRESS + Emulator1Constants.HEADER_SIZE || sampleStart < Emulator1Constants.BANK_ADDRESS + Emulator1Constants.RECORD_SIZE || sampleStart >= Emulator1Constants.SAMPLE_MEMORY_END)
                break;
            records.add (Integer.valueOf (zoneRecord));
        }
        return records;
    }


    /**
     * Create the zone of a record.
     *
     * @param image The raw sector image
     * @param bankOffset The position of the bank in the image
     * @param zoneRecord The offset of the record relative to the bank
     * @param name The name of the zone
     * @param lowKey The lowest key of the zone
     * @param highKey The highest key of the zone
     * @return The zone or null if the record points at no usable audio
     */
    private static ISampleZone createZone (final byte [] image, final int bankOffset, final int zoneRecord, final String name, final int lowKey, final int highKey)
    {
        final int recordOffset = bankOffset + zoneRecord;
        final int start = readAddress (image, recordOffset + Emulator1Constants.RECORD_SAMPLE_START);
        final int loopStart = readAddress (image, recordOffset + Emulator1Constants.RECORD_LOOP_START_REL) + 1;
        final int loopLength = readAddress (image, recordOffset + Emulator1Constants.RECORD_LOOP_LENGTH) + 1;
        final int releaseLength = readAddress (image, recordOffset + Emulator1Constants.RECORD_RELEASE_LENGTH);
        if (start < Emulator1Constants.BANK_ADDRESS + Emulator1Constants.RECORD_SIZE || start >= Emulator1Constants.SAMPLE_MEMORY_END)
            return null;

        // The record stores the end of a sample as the loop end plus the bytes behind the loop,
        // which lies three bytes behind the audio
        final int loopEnd = loopStart + loopLength;
        int numFrames = Math.min (loopEnd + releaseLength - Emulator1Constants.SAMPLE_END_GUARD, Emulator1Constants.SAMPLE_MEMORY_END - start);
        final int audioOffset = bankOffset + start - Emulator1Constants.BANK_ADDRESS;
        numFrames = Math.min (numFrames, image.length - audioOffset);
        if (numFrames <= 0)
            return null;

        final ISampleZone zone = new DefaultSampleZone (name, lowKey, highKey);
        zone.setSampleData (createSampleData (image, audioOffset, numFrames));

        if (loopLength > Emulator1Constants.NO_LOOP_LENGTH && loopEnd <= numFrames)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (loopStart);
            loop.setEnd (loopEnd);
            zone.addLoop (loop);
        }

        final int filterSetting = (image[recordOffset + Emulator1Constants.RECORD_FILTER] & 0xFF) >> 5;
        if (filterSetting > 0)
            zone.setFilter (new DefaultFilter (FilterType.LOW_PASS, 2, Math.min (Emulator1Constants.FILTER_CUTOFF[filterSetting], IFilter.MAX_FREQUENCY), 0));
        return zone;
    }


    /**
     * Set the root key and the tuning of a zone from the pitch table: the root key is the key which
     * plays the sample closest to the rate it was recorded with and the tuning is what remains.
     *
     * @param image The raw sector image
     * @param tableOffset The position of the pitch table in the image
     * @param lowestKey The MIDI note of the first entry of the table
     * @param zone The zone
     */
    private static void applyPitchTable (final byte [] image, final int tableOffset, final int lowestKey, final ISampleZone zone)
    {
        int root = -1;
        double rootCents = 0;
        double previousCents = 0;
        boolean tracksKeys = true;
        for (int key = zone.getKeyLow (); key <= zone.getKeyHigh (); key++)
        {
            final int index = key - lowestKey;
            if (index < 0 || index >= Emulator1Constants.TABLE_ENTRIES)
                break;
            final double cents = Emulator1Constants.getPitchCents (readAddress (image, tableOffset + index * 2) & Emulator1Constants.PITCH_VALUE_MASK);
            if (root < 0 || Math.abs (cents) < Math.abs (rootCents))
            {
                root = key;
                rootCents = cents;
            }
            // A table in which the keys do not rise by semi-tones plays the sample at a fixed pitch
            if (key > zone.getKeyLow () && Math.abs (cents - previousCents) < 50)
                tracksKeys = false;
            previousCents = cents;
        }
        if (root < 0)
            return;

        zone.setKeyRoot (root);
        zone.setTuning (Math.clamp (rootCents / 100.0, -0.5, 0.5));
        if (!tracksKeys && zone.getKeyHigh () > zone.getKeyLow ())
            zone.setKeyTracking (0);
    }


    /**
     * Expand the companded bytes of a sample into 16 bit audio.
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
        return new InMemorySampleData (new DefaultAudioMetadata (1, Emulator1Constants.SAMPLE_RATE, 16, numFrames), pcm);
    }


    /**
     * Read a 16 bit little-endian value.
     *
     * @param image The raw sector image
     * @param offset The position of the value
     * @return The value
     */
    private static int readAddress (final byte [] image, final int offset)
    {
        if (offset < 0 || offset + 1 >= image.length)
            return 0;
        return image[offset] & 0xFF | (image[offset + 1] & 0xFF) << 8;
    }
}
