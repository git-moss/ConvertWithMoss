// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultEnvelope;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.model.implementation.InMemorySampleData;
import de.mossgrabers.convertwithmoss.format.emu.emulator4.Emu3DiskImage;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects E-mu EIII bank files (*.e3x, *.e3b, *.esi) as well as CD-ROM and hard disk images of the
 * EIII, EIIIX and ESI samplers (*.iso, *.img, *.hda) which use the proprietary E-mu disk filesystem
 * and contain such banks. A bank holds up to 256 presets and 999 samples. Every preset becomes one
 * multi-sample source: it maps each of the 88 keys to a note zone, which references up to two
 * zones - the primary and the secondary layer - and every layer becomes one group. A preset which
 * links to another one collects the layers of the whole chain, which is how the samplers build more
 * than two velocity layers. The format was reverse-engineered by the emu3bm project, see
 * documentation/design/EIII_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator3Detector extends AbstractDetector<Emulator3DetectorUI>
{
    private static final String IDS_EIII_MALFORMED_SAMPLE = "IDS_EIII_MALFORMED_SAMPLE";


    /** Holds the parsed information of one sample of a bank. */
    private static class Sample
    {
        String  name;
        byte [] bankData;
        int     dataOffset;
        int     numFrames;
        int     sampleRate;
        boolean isStereo;
        boolean hasLoop;
        boolean loopInRelease;
        int     loopStart;
        int     loopEnd;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator3Detector (final INotifier notifier)
    {
        super ("E-mu Emulator III", "EIII", notifier, new Emulator3DetectorUI ("EIII"), ".e3x", ".e3b", ".esi", ".iso", ".img", ".hda");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            if (Emu3DiskImage.isEmu3Image (readMagic (sourceFile)))
                return this.parseImage (sourceFile);

            final byte [] data = Files.readAllBytes (sourceFile.toPath ());
            final Emulator3BankFormat bankFormat = Emulator3BankFormat.get (data);
            if (bankFormat == null)
            {
                // Images of other formats are silently ignored, they belong to other detectors
                final String lowerCaseName = sourceFile.getName ().toLowerCase (Locale.US);
                if (lowerCaseName.endsWith (".e3x") || lowerCaseName.endsWith (".e3b") || lowerCaseName.endsWith (".esi"))
                    this.notifier.logError ("IDS_EIII_NOT_A_BANK", sourceFile.getName ());
                return Collections.emptyList ();
            }
            if (Emulator3FloppySet.isFloppyDisk (data))
                return this.parseFloppyDisk (sourceFile, data, bankFormat);
            return this.parseBank (sourceFile, FileUtils.getNameWithoutType (sourceFile), data, bankFormat);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Read the first bytes of a file to identify its type.
     *
     * @param sourceFile The file to read from
     * @return The first 4 bytes
     * @throws IOException Could not read the file
     */
    private static byte [] readMagic (final File sourceFile) throws IOException
    {
        try (final InputStream in = Files.newInputStream (sourceFile.toPath ()))
        {
            return in.readNBytes (4);
        }
    }


    /**
     * Parse all banks of an E-mu disk image and create one multi-sample source per preset.
     *
     * @param sourceFile The image file
     * @return The multi-sample sources
     * @throws IOException Could not read the image
     */
    private List<IMultisampleSource> parseImage (final File sourceFile) throws IOException
    {
        final List<IMultisampleSource> results = new ArrayList<> ();
        int numBanks = 0;
        for (final Emu3DiskImage.ImageFile imageFile: Emu3DiskImage.readFiles (sourceFile))
        {
            // Skip the files which are not EIII banks, e.g. the operating system of the sampler or
            // the banks of the newer EOS samplers, which share the filesystem but not the format
            final byte [] content = imageFile.getContent ();
            final Emulator3BankFormat bankFormat = Emulator3BankFormat.get (content);
            if (bankFormat == null)
                continue;
            numBanks++;
            results.addAll (this.parseBank (sourceFile, imageFile.getName (), content, bankFormat));

            if (this.waitForDelivery ())
                break;
        }
        if (numBanks == 0)
            this.notifier.logError ("IDS_EIII_NO_BANKS_IN_IMAGE", sourceFile.getName ());
        else
            this.notifier.log ("IDS_EIII_READING_IMAGE", sourceFile.getName (), Integer.toString (numBanks));
        return results;
    }


    /**
     * Parse a floppy disk of a bank set. The EIIIX and ESI samplers save a bank onto one or more
     * floppy disks; the set is assembled and converted into a bank when its first disk is read,
     * the other disks of the set are skipped. All disks of a set have to be in the same folder;
     * they are recognized by the bank name of their disk headers since the file names of the sets
     * in the wild number their disks in different ways.
     *
     * @param sourceFile The file which contains the first disk
     * @param data The content of the disk
     * @param bankFormat The format of the bank
     * @return The multi-sample sources
     * @throws IOException Could not read a continuation disk
     */
    private List<IMultisampleSource> parseFloppyDisk (final File sourceFile, final byte [] data, final Emulator3BankFormat bankFormat) throws IOException
    {
        // The floppy sets of the original Emulator III are not documented - no set to verify
        // against has surfaced so far
        if (bankFormat.isCompact ())
        {
            this.notifier.logError ("IDS_EIII_FLOPPY_E3_NOT_SUPPORTED", sourceFile.getName ());
            return Collections.emptyList ();
        }

        final int totalDisks = Emulator3FloppySet.getTotalDisks (data);
        final int diskNumber = Emulator3FloppySet.getDiskNumber (data);
        if (diskNumber != 1)
        {
            this.notifier.log ("IDS_EIII_CONTINUATION_DISK_IGNORED", Integer.toString (diskNumber), Integer.toString (totalDisks));
            return Collections.emptyList ();
        }

        final byte [] [] disks = new byte [totalDisks] [];
        disks[0] = data;
        for (int diskIndex = 2; diskIndex <= totalDisks; diskIndex++)
        {
            final File continuationFile = this.findContinuationDisk (sourceFile, data, diskIndex);
            if (continuationFile == null)
            {
                this.notifier.logError ("IDS_EIII_CONTINUATION_DISK_NOT_FOUND", Integer.toString (diskIndex), Integer.toString (totalDisks));
                return Collections.emptyList ();
            }
            this.notifier.log ("IDS_EIII_CONTINUATION_DISK_FOUND", Integer.toString (diskIndex), Integer.toString (totalDisks));
            disks[diskIndex - 1] = Files.readAllBytes (continuationFile.toPath ());
        }

        final String bankName = Emulator3Constants.decodeName (data, Emulator3Constants.BANK_NAME);
        final byte [] bank = Emulator3FloppySet.createBank (disks, bankFormat, bankName, this.notifier);
        if (bank == null)
        {
            this.notifier.logError ("IDS_EIII_NOT_A_BANK", sourceFile.getName ());
            return Collections.emptyList ();
        }
        return this.parseBank (sourceFile, bankName, bank, bankFormat);
    }


    /**
     * Find the disk with the given number of the same floppy set in the folder of the first disk.
     *
     * @param sourceFile The file which contains the first disk
     * @param firstDisk The content of the first disk
     * @param diskNumber The 1-based number of the wanted disk
     * @return The file or null if it is not in the folder
     * @throws IOException Could not read a candidate file
     */
    private File findContinuationDisk (final File sourceFile, final byte [] firstDisk, final int diskNumber) throws IOException
    {
        final File [] files = sourceFile.getParentFile ().listFiles ();
        if (files == null)
            return null;
        for (final File file: files)
        {
            if (!file.isFile () || file.length () != Emulator3FloppySet.FLOPPY_SIZE || file.equals (sourceFile))
                continue;
            try (final InputStream in = Files.newInputStream (file.toPath ()))
            {
                if (Emulator3FloppySet.isContinuationDisk (firstDisk, in.readNBytes (0x200), diskNumber))
                    return file;
            }
        }
        return null;
    }


    /**
     * Parse a bank and create one multi-sample source per preset.
     *
     * @param sourceFile The file which contains the bank (the bank file itself or a disk image)
     * @param bankName The name of the bank
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @return The multi-sample sources
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final String bankName, final byte [] data, final Emulator3BankFormat bankFormat)
    {
        final int maxPresets = bankFormat.getMaxPresets ();
        final int maxSamples = bankFormat.getMaxSamples ();
        final int presetTable = bankFormat.getPresetTableOffset ();
        final int sampleTable = bankFormat.getSampleTableOffset ();
        if (sampleTable + (maxSamples + 1) * 4 > data.length)
        {
            this.notifier.logError ("IDS_EIII_NOT_A_BANK", bankName);
            return Collections.emptyList ();
        }

        // The samples start one filler byte behind the last preset
        final long presetAreaSize = Emulator3Constants.getU32 (data, presetTable + maxPresets * 4) - bankFormat.getPresetAddressBias ();
        final long sampleAreaStart = bankFormat.getPresetAreaOffset () + 1 + presetAreaSize;

        // Deleting a sample leaves its entry empty, so the table has to be walked to its end
        // instead of stopping at the first empty entry - the samples behind such a hole are still
        // in the bank and are still referenced by the presets
        final Map<Integer, Sample> samplesByIndex = new HashMap<> ();
        for (int i = 0; i < maxSamples; i++)
        {
            final long entry = Emulator3Constants.getU32 (data, sampleTable + i * 4);
            if (entry == 0)
                continue;
            final Sample sample = this.parseSample (data, sampleAreaStart + entry - Emulator3Constants.SAMPLE_ADDRESS_OFFSET, i + 1, bankName);
            if (sample != null)
                samplesByIndex.put (Integer.valueOf (i + 1), sample);
        }

        // Presets which another preset links to are layered on top of it and are therefore not
        // converted on their own
        final Set<Integer> linkedPresets = new HashSet<> ();
        for (int i = 0; i < maxPresets; i++)
        {
            if (!isPresetPresent (data, bankFormat, i))
                continue;
            final int presetOffset = getPresetOffset (data, bankFormat, i);
            if (presetOffset < 0)
                continue;
            // The link is a single byte, not the 16 bit value emu3bm reads: the byte behind it is
            // an independent parameter which about 1% of the library presets set, which made
            // their links look out of range and lose the layered preset
            final int link = data[presetOffset + Emulator3Constants.PRESET_LINK] & 0xFF;
            if (link > 0 && link - 1 < maxPresets && link - 1 != i)
                linkedPresets.add (Integer.valueOf (link - 1));
        }

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (int i = 0; i < maxPresets; i++)
        {
            if (!isPresetPresent (data, bankFormat, i) || linkedPresets.contains (Integer.valueOf (i)))
                continue;
            final IMultisampleSource multisampleSource = this.parsePreset (sourceFile, bankName, data, bankFormat, i, maxPresets, samplesByIndex);
            if (multisampleSource != null)
                results.add (multisampleSource);
        }
        if (results.isEmpty ())
            this.notifier.logError ("IDS_EIII_NO_PRESETS", bankName);
        else
            this.notifier.log ("IDS_EIII_READING_BANK", bankName, bankFormat.getDeviceName (), Integer.toString (results.size ()), Integer.toString (samplesByIndex.size ()));
        return results;
    }


    /**
     * Check whether a slot of the preset address table holds a preset. A preset which was deleted
     * leaves an empty slot behind, which has the same address as its successor - the presets behind
     * such a slot are still in the bank.
     *
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param presetIndex The index of the preset
     * @return True if the slot holds a preset
     */
    private static boolean isPresetPresent (final byte [] data, final Emulator3BankFormat bankFormat, final int presetIndex)
    {
        final int presetTable = bankFormat.getPresetTableOffset ();
        return Emulator3Constants.getU32 (data, presetTable + presetIndex * 4) != Emulator3Constants.getU32 (data, presetTable + (presetIndex + 1) * 4);
    }


    /**
     * Get the offset of a preset in the bank.
     *
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param presetIndex The index of the preset
     * @return The offset or -1 if it is outside of the bank
     */
    private static int getPresetOffset (final byte [] data, final Emulator3BankFormat bankFormat, final int presetIndex)
    {
        final long address = bankFormat.getPresetAreaOffset () + Emulator3Constants.getU32 (data, bankFormat.getPresetTableOffset () + presetIndex * 4) - bankFormat.getPresetAddressBias ();
        return address < 0 || address + Emulator3Constants.PRESET_SIZE > data.length ? -1 : (int) address;
    }


    /**
     * Parse the header of a sample. The audio data is not copied here, it stays in the bank until a
     * zone requests it.
     *
     * @param data The content of the bank
     * @param address The address of the sample
     * @param sampleIndex The 1-based index of the sample
     * @param bankName The name of the bank
     * @return The sample or null if its header is malformed
     */
    private Sample parseSample (final byte [] data, final long address, final int sampleIndex, final String bankName)
    {
        if (address < 0 || address + Emulator3Constants.SAMPLE_HEADER_SIZE > data.length)
        {
            this.notifier.logError (IDS_EIII_MALFORMED_SAMPLE, Integer.toString (sampleIndex), bankName);
            return null;
        }
        final int offset = (int) address;

        final int options = Emulator3Constants.getU16 (data, offset + Emulator3Constants.SAMPLE_OPTIONS);
        final boolean isStereo = (options & Emulator3Constants.OPTION_STEREO) == Emulator3Constants.OPTION_STEREO;
        // Some samples only hold their right channel, they use the second set of positions
        final boolean hasLeft = (options & Emulator3Constants.OPTION_CHANNEL_LEFT) > 0;
        final int endField = hasLeft ? Emulator3Constants.SAMPLE_END_LEFT : Emulator3Constants.SAMPLE_END_RIGHT;
        final int loopStartField = hasLeft ? Emulator3Constants.SAMPLE_LOOP_START_LEFT : Emulator3Constants.SAMPLE_LOOP_START_RIGHT;
        final int loopEndField = hasLeft ? Emulator3Constants.SAMPLE_LOOP_END_LEFT : Emulator3Constants.SAMPLE_LOOP_END_RIGHT;

        // All positions are byte offsets which are relative to the start of the sample header
        final long end = Emulator3Constants.getU32 (data, offset + endField);
        final int numFrames = (int) ((end + 2 - Emulator3Constants.SAMPLE_HEADER_SIZE) / 2);
        final int sampleRate = (int) Emulator3Constants.getU32 (data, offset + Emulator3Constants.SAMPLE_RATE);
        final long dataSize = (long) numFrames * 2 * (isStereo ? 2 : 1);
        if (numFrames <= 0 || sampleRate <= 0 || offset + Emulator3Constants.SAMPLE_HEADER_SIZE + dataSize > data.length)
        {
            this.notifier.logError (IDS_EIII_MALFORMED_SAMPLE, Integer.toString (sampleIndex), bankName);
            return null;
        }

        final Sample sample = new Sample ();
        sample.name = Emulator3Constants.decodeName (data, offset);
        if (sample.name.isBlank ())
            sample.name = "Sample " + sampleIndex;
        sample.bankData = data;
        sample.dataOffset = offset + Emulator3Constants.SAMPLE_HEADER_SIZE;
        sample.numFrames = numFrames;
        sample.sampleRate = sampleRate;
        sample.isStereo = isStereo;

        if ((options & Emulator3Constants.OPTION_LOOP) > 0)
        {
            sample.loopStart = (int) ((Emulator3Constants.getU32 (data, offset + loopStartField) - Emulator3Constants.SAMPLE_HEADER_SIZE) / 2);
            // The stored position is the frame before the last one of the loop while the model
            // counts the end as inclusive. Measuring the step at the loop seam of 2798 looped
            // samples of the library CD-ROMs shows a clear optimum at this one frame: the median
            // step falls from 4.6% to 0.2% of the peak amplitude and the share of seams which
            // step by more than a third of it from 13% to 2%.
            sample.loopEnd = (int) ((Emulator3Constants.getU32 (data, offset + loopEndField) - Emulator3Constants.SAMPLE_HEADER_SIZE) / 2) + 1;
            sample.hasLoop = sample.loopStart >= 0 && sample.loopEnd > sample.loopStart && sample.loopStart < numFrames;
            sample.loopEnd = Math.min (sample.loopEnd, numFrames - 1);
            // Without this flag the loop stops as soon as the key is released
            sample.loopInRelease = (options & Emulator3Constants.OPTION_LOOP_IN_RELEASE) > 0;
        }
        return sample;
    }


    /**
     * Parse a preset and the presets which it links to into one multi-sample source.
     *
     * @param sourceFile The file which contains the bank
     * @param bankName The name of the bank
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param presetIndex The index of the preset
     * @param numPresets The number of presets of the bank
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @return The multi-sample source or null if the preset has no usable zones
     */
    private IMultisampleSource parsePreset (final File sourceFile, final String bankName, final byte [] data, final Emulator3BankFormat bankFormat, final int presetIndex, final int numPresets, final Map<Integer, Sample> samplesByIndex)
    {
        final int presetOffset = getPresetOffset (data, bankFormat, presetIndex);
        if (presetOffset < 0)
            return null;
        final String presetName = Emulator3Constants.decodeName (data, presetOffset);

        // Follow the chain of linked presets; each of them contributes its layers
        final Set<Integer> missingSampleIndices = new TreeSet<> ();
        final List<IGroup> groups = new ArrayList<> ();
        final Set<Integer> visited = new HashSet<> ();
        int currentIndex = presetIndex;
        while (currentIndex >= 0 && visited.add (Integer.valueOf (currentIndex)))
        {
            final int offset = getPresetOffset (data, bankFormat, currentIndex);
            if (offset < 0)
                break;
            this.parseLayers (data, bankFormat, offset, groups, samplesByIndex, missingSampleIndices, bankName);
            final int link = data[offset + Emulator3Constants.PRESET_LINK] & 0xFF;
            currentIndex = link > 0 && link - 1 < numPresets ? link - 1 : -1;
        }

        // A bank can hold presets which map no key at all, they are simply dropped
        if (groups.isEmpty ())
            return null;
        for (final Integer missingSampleIndex: missingSampleIndices)
            this.notifier.logError ("IDS_EIII_SAMPLE_MISSING", missingSampleIndex.toString (), presetName);

        final boolean prependBankName = !(this.settingsConfiguration instanceof final Emulator3DetectorUI settings) || settings.prependBankName ();
        final String name = presetName.isBlank () ? FileUtils.getNameWithoutType (sourceFile) : presetName;
        final IMultisampleSource multisampleSource = this.createMultisampleSource (sourceFile, createInstrumentName (bankName, name, prependBankName), groups);
        // Formats which have a field of their own for the bank (e.g. the Waldorf Quantum/Iridium,
        // which shows it next to the preset name) take it from the description
        if (!bankName.isBlank ())
        {
            multisampleSource.getMetadata ().setDescription (bankName);
            // Keep the presets of a bank together if the folder structure of the source is created
            multisampleSource.setSubPath (addBankFolder (multisampleSource.getSubPath (), bankName));
        }
        return multisampleSource;
    }


    /**
     * Parse the primary and the secondary layer of a preset and add them as groups.
     *
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param presetOffset The offset of the preset
     * @param groups Where to add the created groups
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @param missingSampleIndices Where to collect the indices of referenced but absent samples
     * @param bankName The name of the bank
     */
    private void parseLayers (final byte [] data, final Emulator3BankFormat bankFormat, final int presetOffset, final List<IGroup> groups, final Map<Integer, Sample> samplesByIndex, final Set<Integer> missingSampleIndices, final String bankName)
    {
        final int numNoteZones = data[presetOffset + Emulator3Constants.PRESET_NUM_NOTE_ZONES] & 0xFF;
        if (numNoteZones == 0)
            return;
        final int noteZoneOffset = presetOffset + Emulator3Constants.PRESET_SIZE;
        final int zoneOffset = noteZoneOffset + numNoteZones * Emulator3Constants.NOTE_ZONE_SIZE;
        if (zoneOffset > data.length)
            return;

        final IGroup [] layerGroups = new IGroup [2];
        for (int layer = 0; layer < 2; layer++)
        {
            final int layerField = layer == 0 ? Emulator3Constants.NOTE_ZONE_PRIMARY : Emulator3Constants.NOTE_ZONE_SECONDARY;
            final IGroup group = new DefaultGroup ("Layer " + (groups.size () + layer + 1));

            for (int noteZoneIndex = 0; noteZoneIndex < numNoteZones; noteZoneIndex++)
            {
                final int noteZone = noteZoneOffset + noteZoneIndex * Emulator3Constants.NOTE_ZONE_SIZE;
                if (noteZone + Emulator3Constants.NOTE_ZONE_SIZE > data.length)
                    break;
                final int zoneIndex = data[noteZone + layerField] & 0xFF;
                if (zoneIndex == Emulator3Constants.UNUSED)
                    continue;

                // The key range of a note zone is given by the keys which map to it
                int keyLow = -1;
                int keyHigh = -1;
                for (int key = 0; key < Emulator3Constants.NUM_KEYS; key++)
                    if ((data[presetOffset + Emulator3Constants.PRESET_KEY_MAPPINGS + key] & 0xFF) == noteZoneIndex)
                    {
                        if (keyLow < 0)
                            keyLow = key;
                        keyHigh = key;
                    }
                if (keyLow < 0)
                    continue;

                final int zone = zoneOffset + zoneIndex * Emulator3Constants.ZONE_SIZE;
                if (zone + Emulator3Constants.ZONE_SIZE > data.length)
                    continue;
                final ISampleZone sampleZone = this.parseZone (data, bankFormat, zone, presetOffset, keyLow + Emulator3Constants.KEY_OFFSET, keyHigh + Emulator3Constants.KEY_OFFSET, samplesByIndex, missingSampleIndices, bankName);
                if (sampleZone != null)
                {
                    group.addSampleZone (sampleZone);

                    // The chorus doubles the zone with a second, slightly detuned voice on its own
                    // channel. The width of the detune is not documented anywhere; the pair is
                    // detuned by +-7 cents, which keeps it centered on the original pitch
                    if ((data[zone + Emulator3Constants.ZONE_FLAGS] & Emulator3Constants.ZONE_FLAG_CHORUS) > 0)
                    {
                        final ISampleZone chorusZone = new DefaultSampleZone (sampleZone);
                        final Optional<ISampleData> chorusSampleData = sampleZone.getSampleData ();
                        if (chorusSampleData.isPresent ())
                            chorusZone.setSampleData (chorusSampleData.get ());
                        sampleZone.setTuning (sampleZone.getTuning () - 0.07);
                        chorusZone.setTuning (chorusZone.getTuning () + 0.07);
                        group.addSampleZone (chorusZone);
                    }
                }
            }

            if (!group.getSampleZones ().isEmpty ())
                layerGroups[layer] = group;
        }

        // The velocity range of a layer only splits the keyboard when the preset really has both
        // of them. Presets exist which declare a split - the primary layer at 0-63 and the
        // secondary at 64-127 - but never fill in the secondary one; applying the window there
        // would leave everything above velocity 63 silent
        final boolean bothLayers = layerGroups[0] != null && layerGroups[1] != null;
        for (int layer = 0; layer < 2; layer++)
        {
            final IGroup group = layerGroups[layer];
            if (group == null)
                continue;
            if (bothLayers)
                for (final ISampleZone sampleZone: group.getSampleZones ())
                    applyVelocityRange (data, presetOffset, layer, sampleZone);
            groups.add (group);
        }
    }


    /**
     * Apply the velocity range which the preset assigns to one of its two layers. A range which is
     * not set covers the full velocity, which is how the samplers stack the two layers.
     *
     * @param data The content of the bank
     * @param presetOffset The offset of the preset
     * @param layer 0 for the primary and 1 for the secondary layer
     * @param sampleZone The zone to apply the range to
     */
    private static void applyVelocityRange (final byte [] data, final int presetOffset, final int layer, final ISampleZone sampleZone)
    {
        final int lowField = layer == 0 ? Emulator3Constants.PRESET_VELOCITY_PRIMARY_LOW : Emulator3Constants.PRESET_VELOCITY_SECONDARY_LOW;
        final int highField = layer == 0 ? Emulator3Constants.PRESET_VELOCITY_PRIMARY_HIGH : Emulator3Constants.PRESET_VELOCITY_SECONDARY_HIGH;
        final int low = data[presetOffset + lowField] & 0xFF;
        final int high = data[presetOffset + highField] & 0xFF;
        if (high == 0 || high > 127 || low > high)
            return;
        sampleZone.setVelocityLow (Math.max (1, low));
        sampleZone.setVelocityHigh (high);
    }


    /**
     * Parse one zone of a preset.
     *
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param offset The offset of the zone
     * @param presetOffset The offset of the preset which holds the zone
     * @param keyLow The lowest key of the zone
     * @param keyHigh The highest key of the zone
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @param missingSampleIndices Where to collect the indices of referenced but absent samples
     * @param bankName The name of the bank
     * @return The zone or null if its sample is not in the bank
     */
    private ISampleZone parseZone (final byte [] data, final Emulator3BankFormat bankFormat, final int offset, final int presetOffset, final int keyLow, final int keyHigh, final Map<Integer, Sample> samplesByIndex, final Set<Integer> missingSampleIndices, final String bankName)
    {
        final int sampleIndex = Emulator3Constants.getU16 (data, offset + Emulator3Constants.ZONE_SAMPLE_INDEX) & Emulator3Constants.ZONE_SAMPLE_INDEX_MASK;
        if (sampleIndex == 0)
            return null;
        final Sample sample = samplesByIndex.get (Integer.valueOf (sampleIndex));
        if (sample == null)
        {
            missingSampleIndices.add (Integer.valueOf (sampleIndex));
            return null;
        }

        final int flags = data[offset + Emulator3Constants.ZONE_FLAGS] & 0xFF;
        final boolean disableLeft = (flags & Emulator3Constants.ZONE_FLAG_DISABLE_LEFT) > 0;
        final boolean disableRight = (flags & Emulator3Constants.ZONE_FLAG_DISABLE_RIGHT) > 0;

        final ISampleZone zone = new DefaultSampleZone (sample.name, keyLow, keyHigh);
        zone.setSampleData (createSampleData (sample, disableLeft, disableRight, bankName, this.notifier));
        zone.setKeyRoot ((data[offset + Emulator3Constants.ZONE_ORIGINAL_KEY] & 0xFF) + Emulator3Constants.KEY_OFFSET);
        zone.setStart (0);
        zone.setStop (sample.numFrames);

        // The tuning covers one semitone up and down in 128 steps
        zone.setTuning (data[offset + Emulator3Constants.ZONE_NOTE_TUNING] * 1.5625 / 100.0);
        final int level = data[offset + Emulator3Constants.ZONE_VCA_LEVEL];
        zone.setGain (level <= 0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10 (level / (double) Emulator3Constants.FULL_LEVEL));
        zone.setPanning (Emulator3Constants.getPanning (data[offset + Emulator3Constants.ZONE_VCA_PAN] & 0xFF));
        if ((flags & Emulator3Constants.ZONE_FLAG_NON_TRANSPOSE) > 0)
            zone.setKeyTracking (0);

        // The loop belongs to the sample but a zone can switch it off
        if (sample.hasLoop && (flags & Emulator3Constants.ZONE_FLAG_DISABLE_LOOP) == 0)
        {
            final ISampleLoop loop = new DefaultSampleLoop ();
            loop.setType (LoopType.FORWARDS);
            loop.setStart (sample.loopStart);
            loop.setEnd (sample.loopEnd);
            loop.setLoopUntilRelease (!sample.loopInRelease);
            zone.getLoops ().add (loop);
        }

        zone.getAmplitudeEnvelopeModulator ().setSource (parseEnvelope (data, offset + Emulator3Constants.ZONE_VCA_ENVELOPE));
        zone.getAmplitudeVelocityModulator ().setDepth (Math.clamp (data[offset + Emulator3Constants.ZONE_VELOCITY_TO_VCA_LEVEL] / 127.0, -1, 1));

        // The auxiliary envelope is a free envelope of which only the pitch destination has a
        // counterpart in the model
        if ((data[offset + Emulator3Constants.ZONE_AUX_ENVELOPE_DESTINATION] & 0xFF) == 1)
        {
            final double depth = data[offset + Emulator3Constants.ZONE_AUX_ENVELOPE_AMOUNT] / 127.0;
            if (depth != 0)
            {
                final IEnvelopeModulator pitchModulator = zone.getPitchEnvelopeModulator ();
                pitchModulator.setSource (parseEnvelope (data, offset + Emulator3Constants.ZONE_AUX_ENVELOPE));
                pitchModulator.setDepth (Math.clamp (depth, -1, 1));
            }
        }

        final IFilter filter = createFilter (data, bankFormat, offset);
        if (filter != null)
            zone.setFilter (filter);

        // The pitch bend range belongs to the preset and applies to all of its zones
        final int pitchBendRange = data[presetOffset + Emulator3Constants.PRESET_PITCH_BEND_RANGE];
        if (pitchBendRange > 0)
        {
            zone.setBendUp (pitchBendRange * 100);
            zone.setBendDown (-pitchBendRange * 100);
        }
        return zone;
    }


    /**
     * Create the filter of a zone. A low-pass above the audible range without resonance, envelope
     * or key tracking is the bypass state of the samplers and creates no filter. The effect filters
     * of the ESI samplers (phasers, flangers, vocal formants, swept EQs) have no model equivalent
     * and create no filter either.
     *
     * @param data The content of the bank
     * @param bankFormat The format of the bank
     * @param offset The offset of the zone
     * @return The filter or null if the zone does not use one
     */
    private static IFilter createFilter (final byte [] data, final Emulator3BankFormat bankFormat, final int offset)
    {
        final int filterTypeAndShape = data[offset + Emulator3Constants.ZONE_VCF_TYPE_LFO_SHAPE] & 0xFF;
        final FilterType filterType = Emulator3Constants.getFilterType (filterTypeAndShape, bankFormat);
        if (filterType == null)
            return null;

        final int cutoff = data[offset + Emulator3Constants.ZONE_VCF_CUTOFF] & 0xFF;
        final int resonance = data[offset + Emulator3Constants.ZONE_VCF_Q] & 0x7F;
        final double envelopeDepth = Math.clamp (data[offset + Emulator3Constants.ZONE_VCF_ENVELOPE_AMOUNT] / 127.0, -1, 1);
        // The tracking covers -2..2 of which only a positive amount has a model counterpart
        final double keyTracking = Math.clamp (data[offset + Emulator3Constants.ZONE_VCF_TRACKING] / 127.0 * 2.0, 0, 1);

        final double velocityDepth = Math.clamp (data[offset + Emulator3Constants.ZONE_VELOCITY_TO_VCF_CUTOFF] / 127.0, -1, 1);
        // A low-pass which sits above the audible range and which nothing pulls down again removes
        // nothing which can be heard, which is how the samplers switch their filter off. The
        // cutoff parameter reaches up to 74 kHz and the banks park it at several values there -
        // 0xEF and 0xFF are the two most common ones - so the frequency decides and not one value
        final double cutoffFrequency = Emulator3Constants.getCutoffFrequency (cutoff);
        if (filterType == FilterType.LOW_PASS && cutoffFrequency >= Emulator3Constants.INAUDIBLE_CUTOFF_HERTZ && resonance == 0 && envelopeDepth == 0 && keyTracking == 0 && velocityDepth == 0)
            return null;

        final IFilter filter = new DefaultFilter (filterType, Emulator3Constants.getFilterPoles (filterTypeAndShape, bankFormat), cutoffFrequency, resonance / 127.0);
        filter.setCutoffKeyTracking (keyTracking);
        filter.getCutoffVelocityModulator ().setDepth (velocityDepth);

        if (envelopeDepth != 0)
        {
            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            cutoffModulator.setSource (parseEnvelope (data, offset + Emulator3Constants.ZONE_VCF_ENVELOPE));
            cutoffModulator.setDepth (envelopeDepth);
        }
        return filter;
    }


    /**
     * Parse one of the three envelopes of a zone.
     *
     * @param data The content of the bank
     * @param offset The offset of the envelope
     * @return The envelope
     */
    private static IEnvelope parseEnvelope (final byte [] data, final int offset)
    {
        final IEnvelope envelope = new DefaultEnvelope ();
        envelope.setAttackTime (Emulator3Constants.getEnvelopeTime (data[offset + Emulator3Constants.ENVELOPE_ATTACK] & 0xFF));
        envelope.setHoldTime (Emulator3Constants.getEnvelopeTime (data[offset + Emulator3Constants.ENVELOPE_HOLD] & 0xFF));
        envelope.setDecayTime (Emulator3Constants.getEnvelopeTime (data[offset + Emulator3Constants.ENVELOPE_DECAY] & 0xFF));
        envelope.setSustainLevel (Math.clamp (data[offset + Emulator3Constants.ENVELOPE_SUSTAIN] / (double) Emulator3Constants.FULL_LEVEL, 0, 1));
        envelope.setReleaseTime (Emulator3Constants.getEnvelopeTime (data[offset + Emulator3Constants.ENVELOPE_RELEASE] & 0xFF));
        return envelope;
    }


    /**
     * Create the audio data of a sample. The two channels of a stereo sample are stored one after
     * the other in the bank and are interleaved here. A zone which mutes one of the two sides only
     * gets the other one.
     *
     * @param sample The sample
     * @param disableLeft True if the zone mutes the left channel
     * @param disableRight True if the zone mutes the right channel
     * @param bankName The name of the bank
     * @param notifier Where to report a truncated sample
     * @return The audio data
     */
    private static InMemorySampleData createSampleData (final Sample sample, final boolean disableLeft, final boolean disableRight, final String bankName, final INotifier notifier)
    {
        final int numFrames = sample.numFrames;
        final byte [] bankData = sample.bankData;
        final boolean useBothChannels = sample.isStereo && !disableLeft && !disableRight;
        final int numChannels = useBothChannels ? 2 : 1;
        final byte [] pcm = new byte [numFrames * 2 * numChannels];

        if (useBothChannels)
        {
            final int rightOffset = sample.dataOffset + numFrames * 2;
            for (int i = 0; i < numFrames; i++)
            {
                pcm[i * 4] = bankData[sample.dataOffset + i * 2];
                pcm[i * 4 + 1] = bankData[sample.dataOffset + i * 2 + 1];
                pcm[i * 4 + 2] = bankData[rightOffset + i * 2];
                pcm[i * 4 + 3] = bankData[rightOffset + i * 2 + 1];
            }
        }
        else
        {
            // A muted left side leaves the right channel, which follows the left one in the bank
            final int channelOffset = sample.isStereo && disableLeft ? sample.dataOffset + numFrames * 2 : sample.dataOffset;
            System.arraycopy (bankData, channelOffset, pcm, 0, numFrames * 2);
        }

        if (notifier != null && bankName != null && numFrames <= 0)
            notifier.logError (IDS_EIII_MALFORMED_SAMPLE, sample.name, bankName);
        return new InMemorySampleData (new DefaultAudioMetadata (numChannels, sample.sampleRate, 16, numFrames), pcm);
    }


    /**
     * Create the name of the multi-sample source of a preset. The presets of the E-mu libraries are
     * often named after the articulation or the variation they provide, while the instrument they
     * actually play is only given by the name of their bank. The bank name is therefore prepended,
     * except when the preset name already starts with it.
     *
     * @param bankName The name of the bank
     * @param presetName The name of the preset
     * @param prependBankName True to prepend the name of the bank
     * @return The name to use for the multi-sample source
     */
    private static String createInstrumentName (final String bankName, final String presetName, final boolean prependBankName)
    {
        if (!prependBankName || bankName.isBlank ())
            return presetName;
        return reduceToLettersAndDigits (presetName).startsWith (reduceToLettersAndDigits (bankName)) ? presetName : bankName + " - " + presetName;
    }


    /**
     * Reduce a name to its lower case letters and digits, which allows to match names which only
     * differ in their spaces and punctuation.
     *
     * @param text The text to reduce
     * @return The reduced text
     */
    private static String reduceToLettersAndDigits (final String text)
    {
        final StringBuilder sb = new StringBuilder (text.length ());
        for (int i = 0; i < text.length (); i++)
        {
            final char c = text.charAt (i);
            if (Character.isLetterOrDigit (c))
                sb.append (Character.toLowerCase (c));
        }
        return sb.toString ();
    }


    /**
     * Add the bank as the innermost sub-folder of the path parts. The first entry of the array is
     * the name of the multi-sample and not part of the path.
     *
     * @param parts The path parts
     * @param bankName The name of the bank
     * @return The extended path parts
     */
    private static String [] addBankFolder (final String [] parts, final String bankName)
    {
        final String [] result = new String [parts.length + 1];
        result[0] = parts[0];
        result[1] = bankName;
        System.arraycopy (parts, 1, result, 2, parts.length - 1);
        return result;
    }
}
