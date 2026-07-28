// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.emu.emulator4;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
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
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.tools.FileUtils;


/**
 * Detects E-mu Emulator IV bank files (*.e4b) as well as CD-ROM and hard disk images of the EOS
 * samplers (*.iso, *.img, *.hda) which use the proprietary E-mu disk filesystem and contain such
 * banks. A bank contains up to 1000 presets and 1000 samples; every preset becomes one multi-sample
 * source. A preset is a list of voices, each of which maps a set of zones (key/velocity ranges
 * referencing a sample) and carries the tuning, volume, filter and envelope settings for them;
 * every voice becomes one group. The format was reverse-engineered by the mpc2emu project, see
 * documentation/design/E4B_FORMAT.md.
 *
 * @author Jürgen Moßgraber
 */
public class Emulator4Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final Pattern NOTE_SUFFIX_PATTERN = Pattern.compile ("_([A-G]#?)(-?\\d+)$");


    /** Holds the parsed information of one E3S1 sample chunk. */
    private static class Sample
    {
        String             name;
        InMemorySampleData sampleData;
        int                numFrames;
        int                rootKey;
        double             tuning;
        boolean            hasLoop;
        int                loopStart;
        int                loopEnd;
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Emulator4Detector (final INotifier notifier)
    {
        super ("E-mu Emulator IV", "E4B", notifier, new Emulator4DetectorUI ("E4B"), ".e4b", ".iso", ".img", ".hda");
    }


    /** {@inheritDoc} */
    @Override
    public List<IMultisampleSource> readPresetFile (final File sourceFile)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final byte [] magic = readMagic (sourceFile);
            if (Emu3DiskImage.isEmu3Image (magic))
                return this.parseImage (sourceFile);

            if (!Emulator4Constants.hasMagic (magic, 0, Emulator4Constants.FORM_MAGIC))
            {
                // Images of other formats are silently ignored, they belong to other detectors
                if (sourceFile.getName ().toLowerCase (Locale.US).endsWith (".e4b"))
                    this.notifier.logError ("IDS_E4B_NOT_A_BANK", sourceFile.getName ());
                return Collections.emptyList ();
            }

            final byte [] data = Files.readAllBytes (sourceFile.toPath ());
            return this.parseBank (sourceFile, FileUtils.getNameWithoutType (sourceFile), data);
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Parse all banks of an EOS disk image and create one multi-sample source per preset.
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
            // Skip files which are not Emulator IV banks, e.g. the banks of the older EIII
            // samplers which use the same filesystem
            final byte [] content = imageFile.getContent ();
            if (!Emulator4Constants.hasMagic (content, 0, Emulator4Constants.FORM_MAGIC) || !Emulator4Constants.hasMagic (content, 8, Emulator4Constants.FORM_TYPE))
                continue;
            numBanks++;
            results.addAll (this.parseBank (sourceFile, imageFile.getName (), content));
        }
        if (numBanks == 0)
            this.notifier.logError ("IDS_E4B_NO_BANKS_IN_IMAGE", sourceFile.getName ());
        else
            this.notifier.log ("IDS_E4B_READING_IMAGE", sourceFile.getName (), Integer.toString (numBanks));
        return results;
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
     * Parse a bank and create one multi-sample source per preset.
     *
     * @param sourceFile The file which contains the bank (the bank file itself or a disk image)
     * @param bankName The name of the bank
     * @param data The content of the bank
     * @return The multi-sample sources
     */
    private List<IMultisampleSource> parseBank (final File sourceFile, final String bankName, final byte [] data)
    {
        if (data.length < 12 || !Emulator4Constants.hasMagic (data, 0, Emulator4Constants.FORM_MAGIC) || !Emulator4Constants.hasMagic (data, 8, Emulator4Constants.FORM_TYPE))
        {
            this.notifier.logError ("IDS_E4B_NOT_A_BANK", bankName);
            return Collections.emptyList ();
        }

        // Walk the chunks sequentially instead of trusting the TOC offsets, which is more robust
        // against third-party files. Note that the FORM size uses the EOS convention (4 less than
        // standard IFF) and therefore ends inside the trailing EMSt chunk, which is not needed
        final List<byte []> presetChunks = new ArrayList<> ();
        final Map<Integer, Sample> samplesByIndex = new HashMap<> ();
        final Set<String> usedNames = new HashSet<> ();
        int position = 12;
        while (position + 8 <= data.length)
        {
            final long size = Emulator4Constants.getU32BE (data, position + 4);
            final long end = position + 8 + size;
            if (size < 0 || end > data.length)
                break;
            if (Emulator4Constants.hasMagic (data, position, Emulator4Constants.PRESET_TAG))
            {
                final byte [] body = new byte [(int) size];
                System.arraycopy (data, position + 8, body, 0, (int) size);
                presetChunks.add (body);
            }
            else if (Emulator4Constants.hasMagic (data, position, Emulator4Constants.SAMPLE_TAG))
                this.parseSample (data, position + 8, (int) size, samplesByIndex, usedNames);
            // TOC1, E4Ma and EMSt chunks are ignored
            position = (int) (end + (size % 2 == 0 ? 0 : 1));
        }

        final List<IMultisampleSource> results = new ArrayList<> ();
        for (final byte [] presetChunk: presetChunks)
        {
            final IMultisampleSource multisampleSource = this.parsePreset (sourceFile, bankName, presetChunk, samplesByIndex);
            if (multisampleSource != null)
                results.add (multisampleSource);
        }
        if (results.isEmpty ())
            this.notifier.logError ("IDS_E4B_NO_PRESETS", bankName);
        else
            this.notifier.log ("IDS_E4B_READING_BANK", bankName, Integer.toString (results.size ()), Integer.toString (samplesByIndex.size ()));
        return results;
    }


    /**
     * Parse an E3S1 sample chunk. The chunk holds a 94 byte header (all fields little-endian except
     * the sample index) followed by 16-bit little-endian mono PCM data. Loop positions are stored
     * as byte offsets relative to the 92 byte EOS sample struct.
     *
     * @param data The bank content
     * @param offset The offset of the chunk body
     * @param size The size of the chunk body
     * @param samplesByIndex Where to add the parsed sample by its 1-based index
     * @param usedNames All sample names used so far, to make the zone names unique
     */
    private void parseSample (final byte [] data, final int offset, final int size, final Map<Integer, Sample> samplesByIndex, final Set<String> usedNames)
    {
        if (size < Emulator4Constants.SAMPLE_HEADER_SIZE)
        {
            this.notifier.logError ("IDS_E4B_MALFORMED_SAMPLE", Integer.toString (size));
            return;
        }

        final int sampleIndex = Emulator4Constants.getU16BE (data, offset);
        final String displayName = Emulator4Constants.decodeName (data, offset + 2);
        final int sampleRate = (int) Emulator4Constants.getU32LE (data, offset + 54);
        final int pitchOffset = (short) Emulator4Constants.getU16LE (data, offset + 58);
        final int options = Emulator4Constants.getU16LE (data, offset + 60);

        // This is the sample structure of the Emulator III, which stores all of its positions
        // twice - once for each channel. Some samples only hold their right channel and use the
        // second set of positions, exactly as the Emulator III detector already handles it
        final boolean hasLeftChannel = (options & Emulator4Constants.OPTION_CHANNEL_LEFT) > 0;
        final long loopStartOffset = Emulator4Constants.getU32LE (data, offset + (hasLeftChannel ? 38 : 42));
        final long loopEndOffset = Emulator4Constants.getU32LE (data, offset + (hasLeftChannel ? 46 : 50));

        final int pcmLength = (size - Emulator4Constants.SAMPLE_HEADER_SIZE) / 2 * 2;
        final int numFrames = pcmLength / 2;
        if (numFrames <= 0 || sampleRate <= 0)
        {
            this.notifier.logError ("IDS_E4B_MALFORMED_SAMPLE", displayName);
            return;
        }
        final byte [] pcm = new byte [pcmLength];
        System.arraycopy (data, offset + Emulator4Constants.SAMPLE_HEADER_SIZE, pcm, 0, pcmLength);

        final Sample sample = new Sample ();
        sample.sampleData = new InMemorySampleData (new DefaultAudioMetadata (1, sampleRate, 16, numFrames), pcm);
        sample.numFrames = numFrames;

        // The sampler always plays a sample back at 44.1 kHz and compensates a different sample
        // rate with the pitch offset, which the sample data itself already does here. Whatever the
        // offset holds beyond that compensation is a fine tuning of the sample. A larger deviation
        // means that the bank was written without maintaining the field at all (which makes the
        // sample play transposed on the hardware) and is ignored instead of transposing the sample
        final double pitchDeviation = (pitchOffset - Emulator4Constants.calculatePitchOffset (sampleRate)) / 64.0;
        sample.tuning = Math.abs (pitchDeviation) <= 1.0 ? pitchDeviation : 0;

        // The root note is conventionally appended to the name, e.g. 'Piano_C3' for MIDI note 60.
        // The zone entries carry the authoritative root key, this one is only the fallback
        String baseName = displayName;
        sample.rootKey = 60;
        final Matcher matcher = NOTE_SUFFIX_PATTERN.matcher (displayName);
        if (matcher.find ())
        {
            final int midiNote = Emulator4Constants.lookupNote (matcher.group (1), Integer.parseInt (matcher.group (2)));
            if (midiNote >= 0)
            {
                baseName = displayName.substring (0, matcher.start ());
                sample.rootKey = midiNote;
            }
        }
        // Prefer the suffix-stripped base name; on a collision keep the full display name and as
        // the last resort append the unique sample index
        String name = baseName.isBlank () ? displayName : baseName;
        if (!usedNames.add (name))
        {
            name = displayName;
            if (!usedNames.add (name))
            {
                name = displayName + " " + sampleIndex;
                usedNames.add (name);
            }
        }
        sample.name = name;

        if ((options & Emulator4Constants.OPTION_LOOP) > 0)
        {
            sample.loopStart = (int) (loopStartOffset - Emulator4Constants.SAMPLE_STRUCT_SIZE) / 2;
            // The stored position is the frame before the last one of the loop while the model
            // counts the end as inclusive. Measuring the step at the loop seam shows a clear
            // optimum at this one frame: the share of seams which step by more than a third of
            // the peak amplitude falls to zero and the share of clean ones rises from 78% to 95%.
            sample.loopEnd = Math.min ((int) (loopEndOffset - Emulator4Constants.SAMPLE_STRUCT_SIZE) / 2 + 1, numFrames - 1);
            sample.hasLoop = sample.loopStart >= 0 && sample.loopStart < numFrames && sample.loopEnd > sample.loopStart;
        }

        samplesByIndex.put (Integer.valueOf (sampleIndex), sample);
    }


    /**
     * Parse an E4P1 preset chunk into a multi-sample source. Every voice of the preset becomes one
     * group; the voice parameters (tuning, volume, filter, envelopes, modulation cords) are applied
     * to all zones of the voice.
     *
     * @param sourceFile The bank file
     * @param bankName The name of the bank which contains the preset
     * @param body The content of the preset chunk
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @return The multi-sample source or null if the preset contains no usable zones
     */
    private IMultisampleSource parsePreset (final File sourceFile, final String bankName, final byte [] body, final Map<Integer, Sample> samplesByIndex)
    {
        if (body.length < Emulator4Constants.PRESET_HEADER_SIZE)
            return null;

        final String presetName = Emulator4Constants.decodeName (body, 2);
        final int numVoices = Emulator4Constants.getU16BE (body, 20);

        final Set<Integer> missingSampleIndices = new TreeSet<> ();
        final List<IGroup> groups = new ArrayList<> ();
        int offset = Emulator4Constants.PRESET_HEADER_SIZE;
        for (int voiceIndex = 0; voiceIndex < numVoices; voiceIndex++)
        {
            if (offset + Emulator4Constants.VOICE_SIZE > body.length)
            {
                this.notifier.logError ("IDS_E4B_MALFORMED_PRESET", presetName);
                break;
            }

            // The offset of the end of the zone table relative to the voice start is how the
            // hardware locates the next voice; it also implies the number of zones
            final int zoneTableEnd = Emulator4Constants.getU16BE (body, offset + 2);
            final int numZones = (zoneTableEnd - Emulator4Constants.VOICE_SIZE) / Emulator4Constants.ZONE_ENTRY_SIZE;
            if (zoneTableEnd < Emulator4Constants.VOICE_SIZE || offset + Emulator4Constants.VOICE_SIZE + (long) numZones * Emulator4Constants.ZONE_ENTRY_SIZE > body.length)
            {
                this.notifier.logError ("IDS_E4B_MALFORMED_PRESET", presetName);
                break;
            }

            final IGroup group = new DefaultGroup ("Voice " + (voiceIndex + 1));
            parseVoice (body, offset, numZones, samplesByIndex, missingSampleIndices, group);
            if (!group.getSampleZones ().isEmpty ())
                groups.add (group);

            offset += Emulator4Constants.VOICE_SIZE + numZones * Emulator4Constants.ZONE_ENTRY_SIZE;
            // Only the last voice is followed by 2 trailing zero bytes but they do not matter here
        }

        // Every bank of the commercial EOS libraries ends with an unusable placeholder preset
        // which references a sample that is not in the bank. Such a preset is simply dropped;
        // only a preset which does contribute zones is worth a warning about its lost ones
        if (groups.isEmpty ())
            return null;
        for (final Integer missingSampleIndex: missingSampleIndices)
            this.notifier.logError ("IDS_E4B_SAMPLE_MISSING", missingSampleIndex.toString (), presetName);

        // The generic ISO detector runs this detector with its own settings, which have no option
        // of this format; prepending the bank name is the default there as well
        final boolean prependBankName = !(this.settingsConfiguration instanceof final Emulator4DetectorUI settings) || settings.prependBankName ();
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
     * Create the name of the multi-sample source of a preset. The presets of the EOS libraries are
     * named after the articulation or the variation they provide ('Dark Tremolo', 'Long Release',
     * ...) while the instrument they actually play is only given by the name of their bank. The
     * bank name is therefore prepended, except when the preset name already starts with it.
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
     * differ in their spaces and punctuation, e.g. 'GreekBazoukiMute' and 'Greek Bazouki'.
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


    /**
     * Parse one voice block and add its zones to the given group.
     *
     * @param body The content of the preset chunk
     * @param offset The offset of the voice block
     * @param numZones The number of zone entries of the voice
     * @param samplesByIndex The samples of the bank by their 1-based index
     * @param missingSampleIndices Where to collect the indices of referenced but absent samples
     * @param group Where to add the created zones
     */
    private static void parseVoice (final byte [] body, final int offset, final int numZones, final Map<Integer, Sample> samplesByIndex, final Set<Integer> missingSampleIndices, final IGroup group)
    {
        // Per-voice tuning: key transpose and coarse tune in semi-tones. Both always apply to all
        // zones of the voice, there is no per-zone counterpart for them
        final double coarseTuning = body[offset + 34] + (double) body[offset + 35];
        final boolean isFixedPitch = body[offset + 38] == 1;

        // Fine tuning, volume and panning exist twice: at the voice level and in every zone entry.
        // The two are alternatives and are never combined - a voice with a single zone stores them
        // in its voice parameters and leaves the zone entry at zero, a voice with several zones
        // stores the absolute value of each zone in its zone entry and leaves the voice parameters
        // at zero (confirmed on E4XT hardware, see git-moss/ConvertWithMoss#220)
        final boolean isMultiZone = numZones > 1;
        final double voiceFineTune = body[offset + 36] / 64.0;
        final int voiceVolume = body[offset + 54];
        final double voicePanning = Math.clamp (body[offset + 55] / 64.0, -1, 1);

        // The key and velocity window of the voice, which restrict all of its zones
        final int voiceKeyLow = body[offset + 14] & 0xFF;
        final int voiceKeyHigh = body[offset + 17] & 0xFF;
        final int voiceVelocityLow = body[offset + 18] & 0xFF;
        final int voiceVelocityHigh = body[offset + 21] & 0xFF;

        // The modulation cord table provides the depths of the fixed routings
        final int modOffset = offset + Emulator4Constants.VOICE_MOD_OFFSET;
        double velocityToAmplitude = 0;
        double filterEnvelopeDepth = 0;
        double filterKeyTracking = 0;
        for (int slot = 0; slot < Emulator4Constants.NUM_MOD_CORDS; slot++)
        {
            final int cordOffset = modOffset + slot * Emulator4Constants.MOD_CORD_SIZE;
            final int source = body[cordOffset + Emulator4Constants.MOD_CORD_SOURCE] & 0xFF;
            final int destination = body[cordOffset + Emulator4Constants.MOD_CORD_DESTINATION] & 0xFF;
            final int amount = body[cordOffset + Emulator4Constants.MOD_CORD_AMOUNT];
            if (amount == 0)
                continue;
            // Velocity sources: 0x0A add, 0x0B centered, 0x0C subtract
            if (destination == Emulator4Constants.MOD_DEST_AMPLIFIER && source >= Emulator4Constants.MOD_SOURCE_VELOCITY_FIRST && source <= Emulator4Constants.MOD_SOURCE_VELOCITY_LAST)
                velocityToAmplitude = Math.clamp (Math.abs (amount) / 127.0, 0, 1);
            else if (destination == Emulator4Constants.MOD_DEST_CUTOFF && source == Emulator4Constants.MOD_SOURCE_FILTER_ENVELOPE)
                filterEnvelopeDepth = Math.clamp (amount / 127.0, -1, 1);
            else if (destination == Emulator4Constants.MOD_DEST_CUTOFF && source == Emulator4Constants.MOD_SOURCE_KEY)
                filterKeyTracking = Math.clamp (amount / 127.0 * Emulator4Constants.FULL_KEY_TRACKING, 0, 1);
        }

        // The amplitude envelope: 6 rate/level stages in the primary zone table, which are attack
        // 1, attack 2, decay 1, decay 2, release 1 and release 2. The model has one attack, hold,
        // decay and release stage, so the pairs are added up; a decay 1 stage which stays at the
        // level of attack 2 is a plateau and therefore the hold stage. This is the same envelope
        // as the one of the Emulator X and is mapped in the same way. Stopping at decay 1 loses
        // the decay of more than half of the EOS library, and a voice whose decay 1 keeps the peak
        // - a plucked instrument which rings for a while and then fades away - even sustains
        // forever instead of ever decaying
        final int pztOffset = offset + Emulator4Constants.VOICE_PZT_OFFSET;
        final double [] times = new double [6];
        final int [] levels = new int [6];
        for (int stage = 0; stage < 6; stage++)
        {
            times[stage] = Emulator4Constants.envelopeRateToTime (body[pztOffset + stage * 2] & 0xFF);
            levels[stage] = body[pztOffset + stage * 2 + 1];
        }

        final IEnvelope amplitudeEnvelope = new DefaultEnvelope ();
        amplitudeEnvelope.setAttackTime (times[0] + times[1]);
        final boolean isPlateau = levels[2] == levels[1];
        amplitudeEnvelope.setHoldTime (isPlateau ? times[2] : 0);
        amplitudeEnvelope.setDecayTime (isPlateau ? times[3] : times[2] + times[3]);
        amplitudeEnvelope.setSustainLevel (Math.clamp (levels[3] / 127.0, 0, 1));
        amplitudeEnvelope.setReleaseTime (times[4] + times[5]);

        final IFilter filter = createFilter (body, offset, pztOffset, filterEnvelopeDepth, filterKeyTracking);

        for (int zoneIndex = 0; zoneIndex < numZones; zoneIndex++)
        {
            final int entryOffset = offset + Emulator4Constants.VOICE_SIZE + zoneIndex * Emulator4Constants.ZONE_ENTRY_SIZE;
            // The sample indices are 1-based, index 0 means that the zone has no sample assigned
            final int sampleIndex = Emulator4Constants.getU16BE (body, entryOffset + 10);
            if (sampleIndex == 0)
                continue;
            final Sample sample = samplesByIndex.get (Integer.valueOf (sampleIndex));
            if (sample == null)
            {
                missingSampleIndices.add (Integer.valueOf (sampleIndex));
                continue;
            }

            // The zone entry and the voice each carry a key and a velocity window and the range
            // which sounds is their intersection. Many presets leave the zone entry wide open at
            // 0-127 and do the whole key split on the voice, so ignoring the voice window maps
            // every sample across the keyboard and they all sound together on any note
            final int keyLow = Math.max (body[entryOffset + 2] & 0xFF, voiceKeyLow);
            final int keyHigh = Math.min (body[entryOffset + 5] & 0xFF, voiceKeyHigh);
            if (keyLow > keyHigh)
                continue;
            final ISampleZone zone = new DefaultSampleZone (sample.name, Math.min (keyLow, 127), Math.min (keyHigh, 127));
            zone.setSampleData (sample.sampleData);

            final int velocityLow = Math.max (body[entryOffset + 6] & 0xFF, voiceVelocityLow);
            final int velocityHigh = Math.min (body[entryOffset + 9] & 0xFF, voiceVelocityHigh);
            if (velocityLow <= velocityHigh && velocityHigh > 0)
            {
                zone.setVelocityLow (Math.max (1, velocityLow));
                zone.setVelocityHigh (Math.min (127, velocityHigh));
            }

            final int rootKey = body[entryOffset + 14] & 0xFF;
            zone.setKeyRoot (rootKey > 0 && rootKey < 128 ? rootKey : sample.rootKey);
            zone.setStart (0);
            zone.setStop (sample.numFrames);

            // Fine tuning, volume and panning: for a voice with several zones they are the
            // absolute values of the zone, which corrects the recorded pitch of the individual
            // sample, balances the zones against each other and places them in the stereo field;
            // a voice with a single zone has them in its voice parameters instead
            final double fineTune = isMultiZone ? (short) Emulator4Constants.getU16BE (body, entryOffset + 12) / 64.0 : voiceFineTune;
            zone.setTuning (coarseTuning + fineTune + sample.tuning);
            zone.setGain (isMultiZone ? body[entryOffset + 15] : voiceVolume);
            zone.setPanning (isMultiZone ? Math.clamp (body[entryOffset + 16] / 64.0, -1, 1) : voicePanning);
            if (isFixedPitch)
                zone.setKeyTracking (0);

            if (sample.hasLoop)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (LoopType.FORWARDS);
                loop.setStart (sample.loopStart);
                loop.setEnd (sample.loopEnd);
                zone.getLoops ().add (loop);
            }

            zone.getAmplitudeEnvelopeModulator ().setSource (amplitudeEnvelope);
            zone.getAmplitudeVelocityModulator ().setDepth (velocityToAmplitude);
            if (filter != null)
                zone.setFilter (filter);

            group.addSampleZone (zone);
        }
    }


    /**
     * Create the filter of a voice. The 'wide open' default (4-pole low-pass at full frequency
     * without resonance, envelope or key tracking) is the EOS bypass state and creates no filter.
     * The effect and morph filter types of the EOS (phasers, flangers, vocal formants, EQ morphs)
     * have no model equivalent and create no filter either. A cutoff which is parked at the end of
     * its range and only opened up again by a modulation which is lost creates no filter either,
     * see {@link #isCutoffParked}.
     *
     * @param body The content of the preset chunk
     * @param offset The offset of the voice block
     * @param pztOffset The offset of the primary zone table of the voice
     * @param filterEnvelopeDepth The depth of the filter envelope to cutoff modulation (-1..1)
     * @param filterKeyTracking The key tracking of the filter cutoff (0..1)
     * @return The filter or null if the voice does not use one
     */
    private static IFilter createFilter (final byte [] body, final int offset, final int pztOffset, final double filterEnvelopeDepth, final double filterKeyTracking)
    {
        final int filterType = body[offset + 58] & 0xFF;
        final int cutoff = body[offset + 60] & 0xFF;
        final int resonance = body[offset + 61] & 0xFF;

        final FilterType type;
        final int poles;
        switch (filterType)
        {
            case 0x00:
                type = FilterType.LOW_PASS;
                poles = 4;
                break;
            case 0x01:
                type = FilterType.LOW_PASS;
                poles = 2;
                break;
            case 0x02:
                type = FilterType.LOW_PASS;
                poles = 6;
                break;
            case 0x08:
                type = FilterType.HIGH_PASS;
                poles = 2;
                break;
            case 0x09:
                type = FilterType.HIGH_PASS;
                poles = 4;
                break;
            case 0x10:
                type = FilterType.BAND_PASS;
                poles = 2;
                break;
            case 0x11:
                type = FilterType.BAND_PASS;
                poles = 4;
                break;
            case 0x12:
                // 'Contrary band-pass' is the closest EOS type to a notch
                type = FilterType.BAND_REJECTION;
                poles = 2;
                break;
            default:
                return null;
        }

        // A fully open low-pass without any modulation is the bypass state
        if (filterType == 0x00 && cutoff == 255 && resonance == 0 && filterEnvelopeDepth == 0 && filterKeyTracking == 0)
            return null;

        final double cutoffHertz = Emulator4Constants.cutoffToHertz (cutoff);
        final int parkedEnd = isCutoffParked (type, cutoffHertz);
        // The filter envelope is converted, so an envelope which pulls the cutoff away from that
        // end does open the voice up again in the model as well and its filter is kept
        if (parkedEnd != 0 && filterEnvelopeDepth * parkedEnd <= 0 && hasLostCutoffModulation (body, offset))
            return null;

        final IFilter filter = new DefaultFilter (type, poles, cutoffHertz, Math.clamp (resonance / 127.0, 0, 1));
        filter.setCutoffKeyTracking (filterKeyTracking);

        if (filterEnvelopeDepth != 0)
        {
            final IEnvelope envelope = new DefaultEnvelope ();
            envelope.setAttackTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 14] & 0xFF));
            envelope.setDecayTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 18] & 0xFF));
            envelope.setSustainLevel (Math.clamp (body[pztOffset + 19] / 127.0, 0, 1));
            envelope.setReleaseTime (Emulator4Constants.envelopeRateToTime (body[pztOffset + 22] & 0xFF));

            final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
            cutoffModulator.setSource (envelope);
            cutoffModulator.setDepth (filterEnvelopeDepth);
        }

        return filter;
    }


    /**
     * Test whether the cutoff is parked at an end of its range at which the filter suppresses the
     * whole signal: a low-pass which is closed down to the bottom, a high-pass which is opened up
     * to the top and a band-pass at either of the two.
     *
     * @param type The type of the filter
     * @param cutoffHertz The cutoff frequency in Hertz
     * @return 1 if the cutoff is parked at the bottom, -1 if it is parked at the top and 0 if it is
     *         not parked; the sign is the direction a modulation has to take to open it up again
     */
    private static int isCutoffParked (final FilterType type, final double cutoffHertz)
    {
        // A band-rejection at an end of its range passes everything instead of nothing
        if (type == FilterType.BAND_REJECTION)
            return 0;
        if (cutoffHertz <= Emulator4Constants.CLOSED_LOW_PASS_HERTZ && type != FilterType.HIGH_PASS)
            return 1;
        if (cutoffHertz >= Emulator4Constants.CLOSED_HIGH_PASS_HERTZ && type != FilterType.LOW_PASS)
            return -1;
        return 0;
    }


    /**
     * Test whether a modulation cord routes something into the filter cutoff which the model cannot
     * hold. Only the filter envelope and the key tracking have a counterpart there; velocity, the
     * assignable MIDI controllers, the LFOs and the auxiliary envelope are lost. Presets of the EOS
     * libraries commonly park the cutoff at an end of its range and open it up again with such a
     * cord. Once it is lost, what is left is a filter which removes the whole signal - on 'Producer
     * Series Vol. 7 - Old World Instruments' every single one of the 2062 low-pass voices sits at
     * the bottom of the range - so leaving the filter out is much closer to the original than the
     * silence it would create.
     *
     * @param body The content of the preset chunk
     * @param offset The offset of the voice block
     * @return True if such a cord exists
     */
    private static boolean hasLostCutoffModulation (final byte [] body, final int offset)
    {
        final int modOffset = offset + Emulator4Constants.VOICE_MOD_OFFSET;
        for (int slot = 0; slot < Emulator4Constants.NUM_MOD_CORDS; slot++)
        {
            final int cordOffset = modOffset + slot * Emulator4Constants.MOD_CORD_SIZE;
            if ((body[cordOffset + Emulator4Constants.MOD_CORD_DESTINATION] & 0xFF) != Emulator4Constants.MOD_DEST_CUTOFF || body[cordOffset + Emulator4Constants.MOD_CORD_AMOUNT] == 0)
                continue;
            final int source = body[cordOffset + Emulator4Constants.MOD_CORD_SOURCE] & 0xFF;
            if (source != Emulator4Constants.MOD_SOURCE_FILTER_ENVELOPE && source != Emulator4Constants.MOD_SOURCE_KEY)
                return true;
        }
        return false;
    }
}
