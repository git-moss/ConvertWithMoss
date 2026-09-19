// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.SafeFileNames;
import de.mossgrabers.convertwithmoss.core.algorithm.AudioSampleReducer;
import de.mossgrabers.convertwithmoss.core.algorithm.LoopZeroSnapper;
import de.mossgrabers.convertwithmoss.core.algorithm.SincResampler;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LfoWaveform;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Waldorf Quantum/Iridium files.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreator extends AbstractWavCreator<WaldorfQpatCreatorUI>
{
    private static final String                                TAG_SEQUENCED           = "Sequenced";
    private static final String                                TAG_LAYERED             = "Layered";
    private static final String                                TAG_ACTIVE              = "Active";
    private static final String                                TAG_DELAY               = "Delay";
    private static final String                                TAG_ATTACK              = "Attack";
    private static final String                                TAG_DECAY               = "Decay";
    private static final String                                TAG_PERCUSSIVE          = "Percussive";

    private static final String                                AMP_ENV                 = "AmpEnv";

    private static final String                                SLOPE_RC                = "RC";
    private static final String                                SLOPE_LINEAR            = "Lin";
    private static final String                                SLOPE_EXP               = "Exp";
    private static final String                                SLOPE_EXP_ALT           = "Exp alt";
    /**
     * The index of the option 'Exp alt' of the decay and release curves. The device turns the value
     * of an enumeration into its option with rounding when it shows the option name; the index
     * itself is unambiguous, whatever the sound engine does with a value in between.
     */
    private static final int                                   SLOPE_EXP_ALT_INDEX     = 1;

    /** The sample rate which the device plays and to which this creator re-samples. */
    private static final int                                   DESTINATION_SAMPLE_RATE = 44100;
    /**
     * The format version of a patch with one or two layers, which every instrument of the family
     * loads.
     */
    private static final int                                   PRESET_VERSION          = 14;
    /**
     * The format version of a patch with the four-layer layout. The loader clears the file offsets
     * of the layers 3 and 4 in files up to the version 14, so four layers need the version of the
     * MK2 generation.
     */
    private static final int                                   PRESET_VERSION_FOUR     = 15;

    /** The size of the header of a patch, which every layer of a patch has as well. */
    private static final int                                   HEADER_SIZE             = 512;
    /** The size of one parameter record: the value plus the name and the hint. */
    private static final int                                   PARAMETER_SIZE          = 4 + 2 * WaldorfQpatConstants.MAX_STRING_LENGTH;
    /** The number of oscillators of one layer, each of which can play one sample map. */
    private static final int                                   MAX_OSCILLATORS         = 3;
    /**
     * The line feed and the NUL byte which end every sample map, like the maps the device writes;
     * the device does not stop reading a map at its length.
     */
    private static final String                                MAP_TERMINATOR          = "\n\0";
    /**
     * The number of entries which a sample map of the device holds at most; every further entry is
     * dropped (Iridium MK2 firmware 4.0.6, SampleMap::addSample).
     */
    private static final int                                   MAX_MAP_ENTRIES         = 1024;
    /**
     * The maximum number of layers which is written. Two layers are stored the same way from the
     * format version 8 on, so a patch with two layers plays on every instrument of the family. Four
     * layers are the layout of the MK2 generation (Iridium MK2, Quantum MK2 and first-generation
     * instruments upgraded to the MK2 hardware): the layer count 2, the format version 15 and
     * always four stored layers, of which the unused ones are switched off.
     */
    private static final int                                   MAX_LAYERS              = 4;
    /** The number of layers which a patch with three or four layers stores. */
    private static final int                                   MAX_LAYERS_TWO          = 2;
    /** Layer count: two layers, the file offset of the 2nd one is stored at 432. */
    private static final int                                   LAYER_COUNT_TWO         = 1;
    /**
     * Layer count: four layers, the file offsets of the layers 2, 3 and 4 are stored at 432, 440
     * and 444.
     */
    private static final int                                   LAYER_COUNT_FOUR        = 2;
    /** The header holds the file offsets of the layers 2, 3 and 4. */
    private static final int                                   NUM_LAYER_OFFSETS       = 3;
    /**
     * TimbreMode: [2] - all active layers sound simultaneously over the whole keyboard range. The
     * parameter of the device is labelled "Layered"; the manual of the MK2 calls the page which
     * holds it "Multi" and offers the round-robin variants next to it in MultiAllocMode. The device
     * has no velocity range for a layer, therefore this is the only mode in which all the layers of
     * a converted multi-sample can be heard.
     */
    private static final float                                 TIMBRE_MODE_MULTI       = 2.0f;

    /** What the import screen of an Iridium MK2 can show of a file name, minus a small margin. */
    private static final int                                   FILE_NAME_BUDGET        = 40;
    /** The length of the '.qpat' file ending. */
    private static final int                                   FILE_ENDING_LENGTH      = 5;
    /** The length of the import number prefix, e.g. '05002-'. */
    private static final int                                   NUMBER_PREFIX_LENGTH    = 6;
    private static final WaldorfQpatResourceHeader             EMPTY_RESOURCE_HEADER   = new WaldorfQpatResourceHeader ();
    /**
     * The shortest amplitude attack/release which the device renders without a click. The hardware
     * test which established it wrote 0.07 seconds with the display law of the envelope times,
     * which the sound engine plays as 0.01 seconds, see {@link #convertFromTime(double)}.
     */
    private static final double                                DECLICK_SECONDS         = 0.01;
    /**
     * The time which the sound engine subtracts from the curve of the envelope times, so that the
     * value 0 of a stage is instant, see {@link #convertFromTime(double)}.
     */
    private static final double                                ENVELOPE_TIME_OFFSET    = 0.06;
    /** The longest envelope stage of the device in seconds, the value 1 of a stage. */
    private static final double                                MAX_ENVELOPE_TIME       = 59.94;
    /** The share of the peak level at which a step in the audio becomes audible as a click. */
    private static final double                                AUDIBLE_STEP_RATIO      = 0.02;
    /** The lowest cutoff frequency of the filter of the device, the value 0 of Filter1CutOff. */
    private static final double                                MIN_CUTOFF_FREQUENCY    = 8.1758;
    /** The highest cutoff frequency of the filter of the device, the value 1 of Filter1CutOff. */
    private static final double                                MAX_CUTOFF_FREQUENCY    = 19912.2;

    /** The low frequency oscillator which plays the vibrato. */
    private static final int                                   LFO_VIBRATO             = 1;
    /** The low frequency oscillator which plays the tremolo. */
    private static final int                                   LFO_TREMOLO             = 2;
    /** The low frequency oscillator which modulates the cutoff of the filter. */
    private static final int                                   LFO_CUTOFF              = 3;
    /** GlideRate: [0..1] ~ [0..2] seconds, the longest glide of the device. */
    private static final double                                GLIDE_MAXIMUM_TIME      = 2.0;
    /**
     * The lowest rate of a low frequency oscillator in Hertz, which is one cycle in 240 seconds.
     */
    private static final double                                LFO_MINIMUM_RATE        = 1.0 / 240.0;
    /** The highest rate of a low frequency oscillator in Hertz. */
    private static final double                                LFO_MAXIMUM_RATE        = 100.0;
    /** The longest delay of a low frequency oscillator in seconds. */
    private static final double                                LFO_MAXIMUM_DELAY       = 20.0;
    /** The longest attack (fade-in) of a low frequency oscillator in seconds. */
    private static final double                                LFO_MAXIMUM_ATTACK      = 10.0;
    /** From this phase on the device runs the low frequency oscillator freely. */
    private static final double                                LFO_FREE_PHASE          = 0.9986;
    /** LfoXShape: the waveforms of a low frequency oscillator in the order of the device. */
    private static final String []                             LFO_SHAPES              = new String []
    {
        "Sine",
        "Triangle",
        "Square",
        "Saw (down)",
        "Saw (up)",
        "S&H"
    };

    private static final DestinationAudioFormat                OPTIMIZED_AUDIO_FORMAT  = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, true);
    private static final DestinationAudioFormat                DEFAULT_AUDIO_FORMAT    = new DestinationAudioFormat ();

    private static final Map<Integer, WaldorfQpatResourceType> TYPE_LOOKUP             = HashMap.newHashMap (3);
    static
    {
        TYPE_LOOKUP.put (Integer.valueOf (0), WaldorfQpatResourceType.USER_SAMPLE_MAP1);
        TYPE_LOOKUP.put (Integer.valueOf (1), WaldorfQpatResourceType.USER_SAMPLE_MAP2);
        TYPE_LOOKUP.put (Integer.valueOf (2), WaldorfQpatResourceType.USER_SAMPLE_MAP3);
    }

    /**
     * The attributes of the factory sound sets of the device in their spelling - the vocabulary of
     * the 1,787 factory patches of an Iridium MK2, the most frequent first. The device lists the
     * attributes next to the name and filters the patches by them, and it lists 'Keys', 'KEYS' and
     * 'keys' as three entries which each find a part of the sounds. Therefore every attribute is
     * matched against this list regardless of its case and written in the spelling of the list.
     */
    private static final String []           FACTORY_ATTRIBUTES = new String []
    {
        "Synth",
        "Pad",
        "Atmo",
        "Keys",
        "FX",
        TAG_PERCUSSIVE,
        "Epic",
        "PPG",
        "Lead",
        "Bass",
        "Arp",
        "Noise",
        "Strings",
        TAG_SEQUENCED,
        "Granular",
        "Vocal",
        "FM",
        "Cinematic",
        "Resonator",
        "Organ",
        "Loop",
        "Bells",
        "Kernel FM",
        "Mono",
        "Drum",
        "Kernels",
        "Sample",
        "Monophon",
        "Wavetable",
        "Piano",
        "Brass",
        "Experimental",
        "Space",
        "Drone",
        "World",
        "Pipe",
        "Winds",
        "Chromatic Percussion",
        "Pluck",
        "DX7"
    };

    /**
     * The spelling of the factory sound sets for every word which this application, the analysis or
     * a source pack spells differently: the categories of the analysis (e.g. 'Keyboard' and
     * 'Bell'), the tags of source packs ('DRUMS', 'ATMOSPHERIC') and the keywords of the analysis
     * ('seq'). The upper case word is the key. Everything else - Bass, Pad, Organ, Piano, Strings,
     * Synth, Vocal, Lead, Drum, FX, Pipe, Winds, Pluck, Brass, Drone, World, Chromatic Percussion -
     * is spelled identically and only gets its case corrected.
     */
    private static final Map<String, String> ATTRIBUTE_LOOKUP   = HashMap.newHashMap (80);
    static
    {
        for (final String attribute: FACTORY_ATTRIBUTES)
            ATTRIBUTE_LOOKUP.put (attribute.toUpperCase (Locale.US), attribute);
        // Abbreviations which the factory sets do not use keep their capitals
        for (final String abbreviation: new String []
        {
            "MPE",
            "EBM",
            "EDM"
        })
            ATTRIBUTE_LOOKUP.put (abbreviation, abbreviation);

        // The categories of the analysis
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_KEYBOARD.toUpperCase (Locale.US), "Keys");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_BELL.toUpperCase (Locale.US), "Bells");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_PERCUSSION.toUpperCase (Locale.US), TAG_PERCUSSIVE);
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_LOOPS.toUpperCase (Locale.US), "Loop");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_ACOUSTIC_DRUM.toUpperCase (Locale.US), "Drum");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_MONOSYNTH.toUpperCase (Locale.US), "Monophon");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_ORCHESTRAL.toUpperCase (Locale.US), "Cinematic");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_ENSEMBLE.toUpperCase (Locale.US), "Strings");
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_DESTRUCTION.toUpperCase (Locale.US), "Experimental");
        // The device has one attribute for all drum sounds which are not a full kit
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_HI_HAT.toUpperCase (Locale.US), TAG_PERCUSSIVE);
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_KICK.toUpperCase (Locale.US), TAG_PERCUSSIVE);
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_SNARE.toUpperCase (Locale.US), TAG_PERCUSSIVE);
        ATTRIBUTE_LOOKUP.put (TagDetector.CATEGORY_CLAP.toUpperCase (Locale.US), TAG_PERCUSSIVE);

        // The tags of source packs and the keywords of the analysis
        ATTRIBUTE_LOOKUP.put ("KEYBOARDS", "Keys");
        ATTRIBUTE_LOOKUP.put ("PADS", "Pad");
        ATTRIBUTE_LOOKUP.put ("DRUMS", "Drum");
        ATTRIBUTE_LOOKUP.put ("EFFECT", "FX");
        ATTRIBUTE_LOOKUP.put ("EFFECTS", "FX");
        ATTRIBUTE_LOOKUP.put ("SFX", "FX");
        ATTRIBUTE_LOOKUP.put ("ARPEGGIO", "Arp");
        ATTRIBUTE_LOOKUP.put ("ARPEGGIATED", "Arp");
        ATTRIBUTE_LOOKUP.put ("ARPEGGIATOR", "Arp");
        ATTRIBUTE_LOOKUP.put ("SEQ", TAG_SEQUENCED);
        ATTRIBUTE_LOOKUP.put ("SEQUENCE", TAG_SEQUENCED);
        ATTRIBUTE_LOOKUP.put ("SEQUENCER", TAG_SEQUENCED);
        ATTRIBUTE_LOOKUP.put ("ATMOSPHERE", "Atmo");
        ATTRIBUTE_LOOKUP.put ("ATMOSPHERIC", "Atmo");
        ATTRIBUTE_LOOKUP.put ("MONOPHONIC", "Mono");
        ATTRIBUTE_LOOKUP.put ("SAMPLES", "Sample");
        ATTRIBUTE_LOOKUP.put ("WAVETABLES", "Wavetable");
    }

    private int nextImportNumber = 0;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public WaldorfQpatCreator (final INotifier notifier)
    {
        super ("Waldorf Quantum/Iridium", "QPAT", notifier, new WaldorfQpatCreatorUI ("QPAT"));
    }


    /** {@inheritDoc} */
    @Override
    public void clearCancelled ()
    {
        super.clearCancelled ();

        this.nextImportNumber = this.settingsConfiguration.getNumberPrefixStart ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        // Snapping a loop boundary to a zero-crossing only holds if the audio which is written is
        // the audio which was snapped. The snapping happens in the processing stage, while this
        // creator re-samples to 44.1 kHz when its option is set - which moves every boundary off
        // the zero-crossing it was snapped to and brings the loop click back. So the up-sampling
        // has to happen before the snapping, which is what the 'always re-sample' option does.
        // Nothing is logged here: this runs before the conversion, where the graphical interface
        // shows a message as a modal dialog. The processing stage announces the up-sampling with
        // its "Always re-sample" line anyway, and if the sample rate does not match, the creator
        // reports the re-sampling it has to do for every zone.
        if (detectSettings.snapLoopsToZero && this.settingsConfiguration.limitTo16441 () && detectSettings.reduceFrequency == DESTINATION_SAMPLE_RATE)
            detectSettings.alwaysResample = true;
        return super.checkProcessingCompatibility (detectSettings);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        // Zones which only sound on note-off are skipped (see removeReleaseZones), so there needs
        // to be at least one other zone - there is none, e.g. if no sample of the source was found
        if (multisampleSource.getNonEmptyGroups (true).isEmpty ())
        {
            this.notifier.logError ("IDS_ERR_NO_GROUPS_IN_SOURCE");
            return;
        }

        // The name which the device displays. The file name normally keeps the full name of the
        // source instead, which tells the presets of different banks apart on the computer, but is
        // longer than what the import screen of the device can show
        final String deviceName = this.createDeviceName (multisampleSource);
        final String sampleName = SafeFileNames.create (this.settingsConfiguration.useShortFileNames () ? this.limitToFileNameBudget (deviceName) : multisampleSource.getName ());
        final String fileName;
        if (this.settingsConfiguration.addNumberPrefix ())
        {
            // Mirrors the naming of the device's own preset export, a 5-digit number (e.g.
            // '05002-Name.qpat'); on import the device assigns the preset to that number
            fileName = String.format ("%05d-%s", Integer.valueOf (this.nextImportNumber), sampleName);
            this.nextImportNumber++;
        }
        else
            fileName = sampleName;
        final File multiFile = this.createUniqueFilename (destinationFolder, fileName, "qpat");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        // The sample folder carries the counter which the file name got when a preset of the same
        // name was already in the destination folder, otherwise the samples of the two presets
        // end up in one folder and those with the same name overwrite each other
        final String relativeSamplePath = "samples/" + sampleName + getUniqueSuffix (multiFile, fileName, "qpat");

        final List<IGroup> playableGroups = this.removeReleaseZones (this.combineSplitStereo (multisampleSource));
        final List<IGroup> separateGroups = mergeCompatibleGroups (splitLayers (playableGroups));
        final int maximumLayers = this.settingsConfiguration.getMaximumLayers ();
        this.checkLayersFit (multisampleSource.getName (), separateGroups, maximumLayers);
        final List<List<IGroup>> layers = distributeToLayers (separateGroups, maximumLayers);
        final List<IGroup> groups = new ArrayList<> ();
        for (final List<IGroup> layerGroups: layers)
            groups.addAll (layerGroups);
        multisampleSource.setGroups (groups);
        if (layers.size () > 1)
            this.notifier.log ("IDS_QPAT_NOTIFY_LAYERS", Integer.toString (layers.size ()), Integer.toString (groups.size ()));

        final List<String> shadowSamples = collectShadowSamples (layers);
        if (!shadowSamples.isEmpty ())
            this.notifier.log ("IDS_QPAT_NOTIFY_SHADOW_SAMPLES", Integer.toString (shadowSamples.size ()));

        final boolean doLimit = this.settingsConfiguration.limitTo16441 ();
        this.storeMultisample (multisampleSource, multiFile, layers, shadowSamples, relativeSamplePath, deviceName, doLimit ? OPTIMIZED_AUDIO_FORMAT.getMaxSampleRate () : -1);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeSamplePath);
        safeCreateDirectory (sampleFolder);

        if (doLimit)
            this.recalculateSamplePositions (multisampleSource, OPTIMIZED_AUDIO_FORMAT.getMaxSampleRate ());
        this.writeSamples (sampleFolder, multisampleSource, doLimit ? OPTIMIZED_AUDIO_FORMAT : DEFAULT_AUDIO_FORMAT);

        this.progress.notifyDone ();
    }


    /**
     * Create the name to write into the name field of the preset, which is the name the device
     * displays. The device has a field of its own for the bank, so the name does not need to repeat
     * the bank which a preset of a bank carries in front of its name - only the file name keeps it,
     * for the user to tell the files apart. An explicit bank from the settings replaces the bank of
     * the source, which is then no longer written anywhere, so in that case the name keeps it - but
     * only as long as the qualified name fits into the name field. What does not fit is cut off,
     * and that is exactly the part which tells the presets of one bank apart: 'Full Arco String -
     * Arco Strings Lo' and '... Hi' both end up as 'Full Arco String - Arco Strings' on the display
     * of the device. Losing the bank is the better trade in that case, since the preset name is
     * what is looked for and the bank of the source is still in the file name.
     *
     * @param multisampleSource The source to name
     * @return The name for the name field
     */
    private String createDeviceName (final IMultisampleSource multisampleSource)
    {
        final String nameWithoutBank = createNameWithoutBank (multisampleSource);
        final String bank = this.settingsConfiguration.getBank ();
        if (bank == null || bank.isBlank ())
            return nameWithoutBank;
        return this.fitIntoNameField (multisampleSource.getName (), nameWithoutBank);
    }


    /**
     * Cut a name down so that the whole file name stays readable on the import screen of the
     * device. Measured on the display of an Iridium MK2, the file list of the import screen shows
     * about 43 characters, everything longer is cut off at the edge of the list. A budget of 40 for
     * the complete file name leaves a little margin; the name shares it with the '.qpat' ending and
     * - when enabled - the 6 characters of the import number prefix.
     *
     * @param name The name to limit
     * @return The limited name
     */
    private String limitToFileNameBudget (final String name)
    {
        final int budget = FILE_NAME_BUDGET - FILE_ENDING_LENGTH - (this.settingsConfiguration.addNumberPrefix () ? NUMBER_PREFIX_LENGTH : 0);
        final String strippedName = name.strip ();
        return strippedName.length () <= budget ? strippedName : strippedName.substring (0, budget).strip ();
    }


    /**
     * Choose the name to write into the name field of the preset. The field holds a fixed number of
     * characters and everything beyond that is cut off, therefore the alternative is used as soon
     * as the preferred name does not fit.
     *
     * @param preferredName The name to use if it fits into the field
     * @param alternativeName The name to use otherwise
     * @return The name to write
     */
    private String fitIntoNameField (final String preferredName, final String alternativeName)
    {
        // The name is converted to ASCII before it is written, which can change its length
        if (StringUtils.fixASCII (preferredName).length () <= WaldorfQpatConstants.MAX_STRING_LENGTH)
            return preferredName;
        this.notifier.log ("IDS_QPAT_NOTIFY_NAME_WITHOUT_BANK", preferredName, alternativeName);
        return alternativeName;
    }


    /**
     * Create the QPAT file and store it.
     *
     * @param multisampleSource The multi-sample source
     * @param multiFile The file in which to store
     * @param layers The layers
     * @param shadowSamples The names of the samples which only the layers beyond the first
     *            reference, see {@link #collectShadowSamples(List)}
     * @param relativeSamplePath The relative sample path
     * @param deviceName The name to write into the name field, which the device displays
     * @param targetSampleRate The sample rate to which the samples are converted when they are
     *            written, -1 if they keep their sample rate
     * @throws IOException Could not store the file
     */
    private void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final List<List<IGroup>> layers, final List<String> shadowSamples, final String relativeSamplePath, final String deviceName, final int targetSampleRate) throws IOException
    {
        final IMetadata metadata = multisampleSource.getMetadata ();
        final String author = this.settingsConfiguration.getAuthor ();
        if (author != null && !author.isBlank ())
            metadata.setCreator (author);
        // The device has a field of its own for the bank, so the preset name does not need to
        // repeat it - only the file name keeps it, for the user to tell the files apart. An
        // explicit bank from the settings replaces the source's one, which is then no longer
        // written anywhere, so in that case the name keeps it - but only as long as the qualified
        // name fits into the name field. What does not fit is cut off, and that is exactly the part
        // which tells the presets of one bank apart: 'Full Arco String - Arco Strings Lo' and
        // '... Hi' both end up as 'Full Arco String - Arco Strings' on the display of the device.
        // Losing the bank is the better trade in that case, since the preset name is what is looked
        // for and the bank of the source is still in the file name.
        final String bank = this.settingsConfiguration.getBank ();
        final boolean replacesSourceBank = bank != null && !bank.isBlank ();
        if (replacesSourceBank)
            metadata.setDescription (bank);

        final int numLayers = layers.size ();
        // One or two layers are stored the way every instrument of the family stores them; three
        // or four layers need the layout of the MK2 generation, which always stores four layers -
        // the loader checks the offsets of all of them - and switches the unused ones off
        final boolean fourLayerLayout = numLayers > MAX_LAYERS_TWO;
        int layerCount = 0;
        if (numLayers > 1)
            layerCount = fourLayerLayout ? LAYER_COUNT_FOUR : LAYER_COUNT_TWO;
        final int version = fourLayerLayout ? PRESET_VERSION_FOUR : PRESET_VERSION;
        final int numStoredLayers = fourLayerLayout ? MAX_LAYERS : numLayers;
        if (fourLayerLayout)
            this.notifier.log ("IDS_QPAT_NOTIFY_FOUR_LAYERS", Integer.toString (numLayers));

        // The content of every layer has to be known before the first one can be written, since
        // the header holds the file offset of the following layer
        final List<List<WaldorfQpatParameter>> layerParameters = new ArrayList<> ();
        final List<List<byte []>> layerSampleMaps = new ArrayList<> ();
        final int [] layerSizes = new int [numStoredLayers];
        for (int i = 0; i < numStoredLayers; i++)
        {
            if (i >= numLayers)
            {
                // A stored layer which is not used: switched off, without oscillators and maps
                final List<WaldorfQpatParameter> parameters = createInactiveLayerParameters ();
                layerParameters.add (parameters);
                layerSampleMaps.add (new ArrayList<> ());
                layerSizes[i] = HEADER_SIZE + parameters.size () * PARAMETER_SIZE;
                continue;
            }

            final List<IGroup> groups = layers.get (i);
            // A zero-attack/zero-decay amplitude envelope that sustains below full level makes the
            // device pop at the start of each note: it snaps to the 100% attack peak and then
            // instantly drops to the sustain level. Such an envelope is meant to be flat, so write
            // a full sustain and fold the sustain level into the zone gain instead.
            final double ampGainFold = computeFlatAmpEnvelopeLevel (groups);
            final List<WaldorfQpatParameter> parameters = createParameters (groups, ampGainFold < 1.0, numLayers > 1, multisampleSource, version);
            // The samples of the later layers are referenced from the first layer as well
            final List<String> layerShadowSamples = i == 0 ? shadowSamples : Collections.emptyList ();
            final List<byte []> sampleMaps = new ArrayList<> ();
            for (final String sampleMap: createSampleMaps (groups, relativeSamplePath, ampGainFold, targetSampleRate, layerShadowSamples))
                sampleMaps.add (sampleMap.getBytes ());
            layerParameters.add (parameters);
            layerSampleMaps.add (sampleMaps);
            this.checkMapSizes (groups, layerShadowSamples.size ());

            int size = HEADER_SIZE + parameters.size () * PARAMETER_SIZE;
            for (final byte [] sampleMap: sampleMaps)
                size += sampleMap.length;
            layerSizes[i] = size;
        }

        // The absolute file offsets of the layers 2, 3 and 4, each the sum of the sizes of the
        // layers in front of it; a layer which is not stored keeps 0
        final int [] layerOffsets = new int [NUM_LAYER_OFFSETS];
        for (int i = 1; i < numStoredLayers; i++)
            layerOffsets[i - 1] = layerOffsets[Math.max (0, i - 2)] + layerSizes[i - 1];

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            for (int i = 0; i < numStoredLayers; i++)
                writeLayer (out, metadata, deviceName, version, layerParameters.get (i), layerSampleMaps.get (i), layerCount, numLayers == 1 ? 0 : (int) TIMBRE_MODE_MULTI, layerOffsets);
        }
    }


    /**
     * Create the parameters of a stored layer which is not used. The four-layer layout always
     * stores four layers, so a patch with three sounding layers stores a fourth one which is
     * switched off; its oscillators are left unwritten, which the device reads as Off.
     *
     * @return The parameters
     */
    private static List<WaldorfQpatParameter> createInactiveLayerParameters ()
    {
        final List<WaldorfQpatParameter> parameters = new ArrayList<> ();
        parameters.add (new WaldorfQpatParameter ("TimbreMode", TAG_LAYERED, TIMBRE_MODE_MULTI));
        parameters.add (new WaldorfQpatParameter ("MultiAllocMode", TAG_LAYERED, 0));
        parameters.add (new WaldorfQpatParameter ("LayerActive", "Off", 0));
        return parameters;
    }


    /**
     * Write one layer of the patch: its header, its parameters and its sample maps. A layer is a
     * complete patch of its own; the layers of a patch are simply stored one after the other.
     *
     * @param out The output stream to write to
     * @param metadata The metadata of the multi-sample
     * @param deviceName The name to write into the name field, which the device displays
     * @param version The format version of the patch
     * @param parameters The parameters of the layer
     * @param sampleMaps The sample maps of the layer
     * @param layerCount The number of layers of the patch, coded as the device does
     * @param timbreMode The mode in which the layers are combined
     * @param layerOffsets The absolute file offsets of the layers 2, 3 and 4
     * @throws IOException Could not write the layer
     */
    private static void writeLayer (final OutputStream out, final IMetadata metadata, final String deviceName, final int version, final List<WaldorfQpatParameter> parameters, final List<byte []> sampleMaps, final int layerCount, final int timbreMode, final int [] layerOffsets) throws IOException
    {
        writeHeader (out, metadata, deviceName, version);

        StreamUtils.writeUnsigned16 (out, parameters.size (), false);
        StreamUtils.padBytes (out, 2);

        // Write up to 3 sample maps (the groups of a layer have already been reduced to a max. of
        // 3). Each map's offset is relative to the start of the concatenated resource data written
        // further down, so it must accumulate the lengths of the preceding maps. Without this,
        // maps 2 and 3 keep the default offset 0 and are read overlapping map 1, so the device
        // cannot locate their samples and shows the "Find Sample Map" screen for multi-oscillator
        // patches.
        int resourceOffset = 0;
        for (int i = 0; i < sampleMaps.size (); i++)
        {
            final byte [] sampleMapBytes = sampleMaps.get (i);
            final WaldorfQpatResourceHeader resourceHeader = new WaldorfQpatResourceHeader ();
            resourceHeader.type = TYPE_LOOKUP.get (Integer.valueOf (i));
            resourceHeader.offset = resourceOffset;
            resourceHeader.length = sampleMapBytes.length;
            resourceHeader.write (out);
            resourceOffset += sampleMapBytes.length;
        }
        // .... and pad with empty resources
        for (int i = 0; i < WaldorfQpatConstants.MAX_RESOURCES - sampleMaps.size (); i++)
            EMPTY_RESOURCE_HEADER.write (out);

        // The number of layers and the mode in which they are combined
        StreamUtils.writeUnsigned16 (out, layerCount, false);
        StreamUtils.writeUnsigned16 (out, timbreMode, false);
        // The file offset of the 2nd layer
        StreamUtils.writeUnsigned32 (out, layerOffsets[0], false);
        // Instrument type on which the patch was saved last. Set to Quantum.
        out.write (0);
        StreamUtils.padBytes (out, 3);
        // The file offsets of the layers 3 and 4
        StreamUtils.writeUnsigned32 (out, layerOffsets[1], false);
        StreamUtils.writeUnsigned32 (out, layerOffsets[2], false);
        // Padding up to 512 bytes.
        StreamUtils.padBytes (out, 64);

        // Write all parameters
        for (final WaldorfQpatParameter param: parameters)
            param.write (out);

        // Write resource(s)
        for (final byte [] sampleMap: sampleMaps)
            out.write (sampleMap);
    }


    /**
     * Warn if the groups need more oscillators than the layers which the option allows provide.
     * Each group needs an oscillator of its own since it either sounds at the same time as the
     * others or needs other settings of the oscillator. The groups which do not fit are added to
     * the sample map of the last oscillator, where entries which overlap alternate on successive
     * notes instead of sounding together, see {@link #reduceGroups(List, int)}.
     *
     * @param name The name of the multi-sample
     * @param groups The groups, each for an oscillator of its own
     * @param maximumLayers The maximum number of layers of the option
     */
    private void checkLayersFit (final String name, final List<IGroup> groups, final int maximumLayers)
    {
        final int layers = Math.clamp (maximumLayers, 1, MAX_LAYERS);
        final int oscillators = layers * MAX_OSCILLATORS;
        if (groups.size () <= oscillators)
            return;

        // A sample which several groups play is named once
        final Set<String> foldedNames = new LinkedHashSet<> ();
        for (final IGroup group: groups.subList (oscillators, groups.size ()))
            foldedNames.add ("'" + getDisplayName (group) + "'");
        final String targetName = "'" + getDisplayName (groups.get (oscillators - 1)) + "'";
        final String folded = String.join (", ", foldedNames);
        final int neededLayers = (groups.size () + MAX_OSCILLATORS - 1) / MAX_OSCILLATORS;
        if (neededLayers <= MAX_LAYERS)
            this.notifier.log ("IDS_QPAT_NOTIFY_LAYERS_DO_NOT_FIT", name, Integer.toString (groups.size ()), Integer.toString (layers), Integer.toString (oscillators), folded, targetName, Integer.toString (neededLayers));
        else
            this.notifier.log ("IDS_QPAT_NOTIFY_LAYERS_EXCEED_DEVICE", name, Integer.toString (groups.size ()), Integer.toString (MAX_LAYERS), Integer.toString (MAX_LAYERS * MAX_OSCILLATORS), folded, targetName);
    }


    /**
     * Get the name by which a group is recognized: the name of its first zone, which is usually
     * the name of its sample, or the name of the group if it has no zones.
     *
     * @param group The group
     * @return The name
     */
    private static String getDisplayName (final IGroup group)
    {
        final List<ISampleZone> zones = group.getSampleZones ();
        return zones.isEmpty () ? group.getName () : zones.get (0).getName ();
    }


    /**
     * Distribute the groups across the layers of the patch. Each layer plays up to 3 groups, one on
     * each of its oscillators, so a patch reaches 3 groups with one layer and 6 with two. Groups
     * which do not fit into the available layers are added to the last group, as they are when only
     * one layer is written.
     * <p>
     * The layers are combined in the Multi mode, in which all of them sound over the whole keyboard
     * range: the device can split its layers by key or cycle them, but it has no velocity range for
     * a layer, so a velocity split has to stay inside the sample maps - which is where
     * {@link #mergeCompatibleGroups(List)} put it, since only zones which sound at the same time
     * are separated into groups.
     *
     * @param groups The groups, one for each set of zones which sound at the same time
     * @param maximumLayers The maximum number of layers to use, at most {@link #MAX_LAYERS}
     * @return The groups of each layer
     */
    private static List<List<IGroup>> distributeToLayers (final List<IGroup> groups, final int maximumLayers)
    {
        final List<IGroup> reducedGroups = reduceGroups (groups, Math.clamp (maximumLayers, 1, MAX_LAYERS) * MAX_OSCILLATORS);
        final List<List<IGroup>> layers = new ArrayList<> ();
        for (int i = 0; i < reducedGroups.size (); i += MAX_OSCILLATORS)
            layers.add (new ArrayList<> (reducedGroups.subList (i, Math.min (i + MAX_OSCILLATORS, reducedGroups.size ()))));
        if (layers.isEmpty ())
            layers.add (new ArrayList<> ());
        return layers;
    }


    /**
     * Remove the zones which only sound on note-off. The device has no release trigger for a sample
     * map, so such a zone would sound on note-on, stacked on the attack samples of the same key. A
     * group which holds nothing else is dropped.
     *
     * @param groups The groups
     * @return The groups without release-triggered zones
     */
    private List<IGroup> removeReleaseZones (final List<IGroup> groups)
    {
        final List<IGroup> result = new ArrayList<> ();
        int removed = 0;
        for (final IGroup group: groups)
        {
            final List<ISampleZone> zones = new ArrayList<> ();
            for (final ISampleZone zone: group.getSampleZones ())
                if (zone.getTrigger () == TriggerType.RELEASE)
                    removed++;
                else
                    zones.add (zone);
            if (zones.size () == group.getSampleZones ().size ())
                result.add (group);
            else if (!zones.isEmpty ())
            {
                final DefaultGroup keptGroup = copyGroupSettings (group);
                for (final ISampleZone zone: zones)
                    keptGroup.addSampleZone (zone);
                result.add (keptGroup);
            }
        }
        if (removed > 0)
            this.notifier.log ("IDS_QPAT_NOTIFY_RELEASE_ZONES", Integer.toString (removed));
        return result;
    }


    /**
     * Merge the groups which never sound at the same time, so that they share one oscillator. To
     * the device they are one sample map: it selects the entry by key and velocity and alternates
     * the entries which overlap. The velocity layers of an instrument, the round robins which a
     * source keeps in separate groups (an SFZ file often holds one group per sequence position) and
     * key ranges which follow each other therefore all fit into one map. Only a stack of layers -
     * zones which sound together on the same note - needs an oscillator per layer, and the
     * oscillators are scarce: three per layer of the patch. Without this, an SFZ file with one
     * group per round-robin position played its first two positions on every note and cycled only
     * the rest.
     * <p>
     * Groups can only share an oscillator if the settings which the oscillator takes from its group
     * agree: the key tracking and the panning, which the device does not read from the entries of a
     * sample map.
     *
     * @param groups The groups, each free of internal stacks (see {@link #splitLayers(List)})
     * @return The merged groups, in the order of the first group of each
     */
    private static List<IGroup> mergeCompatibleGroups (final List<IGroup> groups)
    {
        final List<IGroup> merged = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            IGroup target = null;
            for (final IGroup candidate: merged)
                if (canShareMap (candidate, group))
                {
                    target = candidate;
                    break;
                }
            if (target == null)
                merged.add (group);
            else
                mergeInto (target, group);
        }
        return merged;
    }


    /**
     * Test if two groups can share one sample map: no zone of one sounds at the same time as a zone
     * of the other, the oscillator settings agree and the map stays within the entry limit of the
     * device.
     *
     * @param a The first group
     * @param b The second group
     * @return True if they can share a map
     */
    private static boolean canShareMap (final IGroup a, final IGroup b)
    {
        final List<ISampleZone> zonesA = a.getSampleZones ();
        final List<ISampleZone> zonesB = b.getSampleZones ();
        if ((zonesA.size () + zonesB.size () > MAX_MAP_ENTRIES) || (Math.abs (getOscillatorKeyTracking (zonesA) - getOscillatorKeyTracking (zonesB)) > 0.0001) || (Math.abs (getGroupPanningOffset (a) - getGroupPanningOffset (b)) > 0.0001))
            return false;
        for (final ISampleZone zoneA: zonesA)
            for (final ISampleZone zoneB: zonesB)
                if (zonesOverlap (zoneA, zoneB))
                    return false;
        return true;
    }


    /**
     * Add the zones of a group to another one.
     *
     * @param target The group to add to
     * @param group The group whose zones are added
     */
    private static void mergeInto (final IGroup target, final IGroup group)
    {
        // The zones carry the (flattened) offsets of their own group. When the offsets of the two
        // groups differ, the oscillator cannot hold them and the sample map carries them in full
        // instead, exactly as reduceGroups does
        if (target.getGain () != group.getGain () || target.getPanning () != group.getPanning () || target.getTuning () != group.getTuning ())
        {
            target.setGain (0);
            target.setPanning (0);
            target.setTuning (0);
        }
        for (final ISampleZone zone: group.getSampleZones ())
            target.addSampleZone (zone);
    }


    /**
     * Create an empty group with the settings of another one.
     *
     * @param group The group to copy the settings from
     * @return The new group
     */
    private static DefaultGroup copyGroupSettings (final IGroup group)
    {
        final DefaultGroup copy = new DefaultGroup (group.getName ());
        copy.setTrigger (group.getTrigger ());
        copy.setGain (group.getGain ());
        copy.setPanning (group.getPanning ());
        copy.setTuning (group.getTuning ());
        return copy;
    }


    /**
     * Determines the amplitude gain to fold into the zone gains when the amplitude envelope must be
     * flattened. When the envelope has (effectively) no attack and no decay but sustains below full
     * level, the device snaps to the 100% attack peak and then instantly drops to the sustain level
     * at the start of each note, which is audible as a click/pop. Such an envelope is meant to be
     * flat at the sustain level, so it is written with a full sustain (see createEnvelope) and the
     * sustain level is applied to the zone gain instead.
     *
     * @param groups The groups
     * @return The gain factor to fold into the zone gains [0..1], or 1.0 if no flattening is needed
     */
    private static double computeFlatAmpEnvelopeLevel (final List<IGroup> groups)
    {
        if (groups.isEmpty () || groups.get (0).getSampleZones ().isEmpty ())
            return 1.0;

        final IEnvelope envelope = groups.get (0).getSampleZones ().get (0).getAmplitudeEnvelopeModulator ().getSource ();
        if (envelope == null)
            return 1.0;

        // An attack and a decay which are over within a few hundredths of a second leave the
        // envelope at its sustain level from the start of the note, which is how such an envelope
        // is meant.
        final double attackTime = envelope.getAttackTime ();
        final double decayTime = Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ());
        double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel == -1)
            sustainLevel = 1;
        if (attackTime <= 0.06 && decayTime <= 0.06 && sustainLevel > 0 && sustainLevel < 1)
            return sustainLevel;
        return 1.0;
    }


    /**
     * Create a sample map for each group. A sample map is a text file which describes a basic
     * multi-sample configuration.
     *
     * @param groups The groups
     * @param relativeSamplePath The relative path to the samples
     * @param gainFactor A linear gain factor applied to every zone (used to fold a flattened
     *            amplitude envelope's sustain level into the gain)
     * @param targetSampleRate The sample rate to which the samples are converted when they are
     *            written, -1 if they keep their sample rate
     * @param shadowSamples The names of the samples which are added to the last map as entries
     *            which never play, see {@link #collectShadowSamples(List)}
     * @return The sample maps
     * @throws IOException Could not read the necessary audio metadata of a sample
     */
    private static List<String> createSampleMaps (final List<IGroup> groups, final String relativeSamplePath, final double gainFactor, final int targetSampleRate, final List<String> shadowSamples) throws IOException
    {
        final List<String> sampleMaps = new ArrayList<> ();

        for (final IGroup group: groups)
        {
            final StringBuilder sb = new StringBuilder ();

            // The detectors flatten the group gain and panning into each of their zones (see
            // IGroup#getGain). Since these offsets are written to the oscillator volume and panning
            // (see createParameters), they must be removed here again, otherwise the device applies
            // them a second time.
            final double gainOffset = getGroupGainOffset (group);
            final double panningOffset = getGroupPanningOffset (group);

            // Entries which overlap alternate in the order of the map, so the zones of a round
            // robin are written in the order of their sequence positions
            final List<ISampleZone> zones = new ArrayList<> (group.getSampleZones ());
            zones.sort (Comparator.comparingInt ((final ISampleZone zone) -> zone.getPlayLogic () == PlayLogic.ALWAYS ? 0 : Math.max (0, zone.getSequencePosition ())));
            for (final ISampleZone zone: zones)
            {
                if (!sb.isEmpty ())
                    sb.append ('\n');

                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isEmpty ())
                    throw new IOException ("Empty sample data in zone: " + zone.getName ());
                final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
                final double numSampleFrames = audioMetadata.getNumberOfSamples ();

                // Sample path, written relative to the preset (no leading drive number). The device
                // resolves it against the folder the preset itself was loaded from, so it locates
                // the samples on whatever drive the preset sits on. A leading drive number was
                // written here before (an absolute path such as "4:samples/..."), but the device
                // then prepends its own drive again when you use "Export -> With Samples",
                // producing an invalid, doubled path (e.g. "3:2:samples/...") so the samples could
                // not be backed up. A relative path both loads and exports/backs up cleanly
                // (confirmed on Iridium OS 4).
                // The name must be sanitized exactly like the sample file which is written for the
                // zone (see AbstractCreator.createSampleFilename), otherwise the device cannot
                // resolve the sample and shows the "Find Sample Map" screen. The folder part of the
                // path is created with the same method as well.
                sb.append ('"').append (relativeSamplePath).append ('/').append (SafeFileNames.create (zone.getName ())).append (".wav\"\t");

                // Pitch - tuning needs to be subtracted since the sample plays high if the root
                // note is lower!
                sb.append (formatMapDouble (zone.getKeyRoot () - zone.getTuning ())).append ('\t');

                // FromNote / ToNote - a negative range was not set by the source, it covers the
                // full range like in the layer distribution (see zonesOverlap) instead of being
                // written as -1, which the device takes over as the range of the entry
                sb.append (limitToDefault (zone.getKeyLow (), 0)).append ('\t').append (limitToDefault (zone.getKeyHigh (), 127)).append ('\t');

                // Gain
                final double v = Math.clamp (zone.getGain () - gainOffset, Double.NEGATIVE_INFINITY, 20);
                sb.append (formatMapDouble (Math.pow (10, v / 20) * gainFactor)).append ('\t');

                // FromVelo / ToVelo - same as the note range. The lowest velocity is written as 0,
                // which is how the patches of the device cover all velocities: the device plays a
                // very soft note with the velocity 0, which an entry from 1 on does not play
                final int velocityLow = zone.getVelocityLow ();
                sb.append (velocityLow <= 1 ? 0 : velocityLow).append ('\t').append (limitToDefault (zone.getVelocityHigh (), 127)).append ('\t');

                // Pan - CURRENTLY IGNORED
                sb.append (formatMapDouble (Math.clamp ((zone.getPanning () - panningOffset + 1.0) / 2.0, 0, 1))).append ('\t');

                // Start / End - a zone whose start/stop was never set keeps the model default of
                // -1, which would otherwise be written as a negative position (the device then
                // shows a sample start/end of -1). Treat an unset start/stop as the full sample.
                final double startFrame = zone.getStart () < 0 ? 0 : zone.getStart ();
                final double stopFrame = zone.getStop () <= 0 ? numSampleFrames : zone.getStop ();
                // The stop of a zone is the frame behind its last one, the device stores the last
                // one
                sb.append (formatMapPosition (startFrame, numSampleFrames)).append ('\t');
                sb.append (formatMapPosition (stopFrame - 1, numSampleFrames)).append ('\t');

                // Loop mode, start, stop
                final List<ISampleLoop> loops = zone.getLoops ();
                if (loops.isEmpty ())
                    sb.append ("0\t0\t").append (formatMapPosition (stopFrame - 1, numSampleFrames)).append ('\t');
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (loop.getType () == LoopType.ALTERNATING ? 2 : 1).append ('\t');
                    final String [] loopPositions = getLoopPositions (zone, loop, audioMetadata, targetSampleRate);
                    sb.append (loopPositions[0]).append ('\t');
                    sb.append (loopPositions[1]).append ('\t');
                }

                // Direction
                sb.append (zone.isReversed () ? 1 : 0).append ('\t');

                if (loops.isEmpty ())
                    sb.append ("0\t");
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (formatMapDouble (loop.getCrossfade ())).append ('\t');
                }

                // TrackPitch
                sb.append (zone.getKeyTracking () == 0 ? "0" : "1");
            }

            // End the last line with a line feed and a NUL, which count as part of the resource,
            // like every map the device writes. The device does not stop reading at the length of
            // the resource: behind an unterminated map it appended the digits which followed in
            // its memory to the TrackPitch flag of the last entry ('1' became e.g. '10', '19' or
            // '1000000'), and such an entry plays every key with the same pitch
            if (!sb.isEmpty ())
                sb.append (MAP_TERMINATOR);
            sampleMaps.add (sb.toString ());
        }

        if (!sampleMaps.isEmpty () && !shadowSamples.isEmpty ())
        {
            // The entries go in front of the line feed and the NUL which end the map
            String lastMap = sampleMaps.removeLast ();
            if (lastMap.endsWith (MAP_TERMINATOR))
                lastMap = lastMap.substring (0, lastMap.length () - MAP_TERMINATOR.length ());
            final StringBuilder sb = new StringBuilder (lastMap);
            for (final String shadowSample: shadowSamples)
            {
                if (!sb.isEmpty ())
                    sb.append ('\n');
                appendShadowEntry (sb, relativeSamplePath, shadowSample);
            }
            sb.append (MAP_TERMINATOR);
            sampleMaps.add (sb.toString ());
        }

        return sampleMaps;
    }


    /**
     * Collect the samples which only the layers beyond the first reference. When the device imports
     * a patch, it copies the samples into its internal memory - but it collects them from the
     * sample maps of the first layer only: the importer reads the resource table of the first
     * header of the file, walks its maps and copies each file they name (Iridium MK2 firmware
     * 4.0.6, PatchLib::importPatch). A sample which only a later layer names is never copied, and
     * when the patch is loaded that layer reports 'loading samples/&lt;patch&gt;/&lt;file&gt;.wav
     * failed' and stays silent - which is exactly what an Iridium MK2 showed for every two-layer
     * patch whose second layer brought samples of its own, while a second layer which re-uses the
     * samples of the first plays. The first layer therefore names every such sample in an entry
     * which never plays, see {@link #appendShadowEntry(StringBuilder, String, String)}. Verified on
     * an Iridium MK2 (4.0.6): with the entry a second layer plays its own sample after the import,
     * without it the same patch fails to load that sample.
     *
     * @param layers The groups of each layer
     * @return The names of the samples of the later layers which the first layer does not
     *         reference, without the file ending, in the order of their first use
     */
    private static List<String> collectShadowSamples (final List<List<IGroup>> layers)
    {
        final Set<String> firstLayerSamples = new HashSet<> ();
        final Set<String> shadowSamples = new LinkedHashSet<> ();
        for (int i = 0; i < layers.size (); i++)
            for (final IGroup group: layers.get (i))
                for (final ISampleZone zone: group.getSampleZones ())
                {
                    // The zone name is what the sample map references, sanitized exactly as the
                    // sample file is written
                    final String name = SafeFileNames.create (zone.getName ());
                    if (i == 0)
                        firstLayerSamples.add (name);
                    else if (!firstLayerSamples.contains (name))
                        shadowSamples.add (name);
                }
        return new ArrayList<> (shadowSamples);
    }


    /**
     * Append a sample map entry which names a sample but never plays. The importer of the device
     * only needs the name of the file to copy it. The entry is made unplayable three times over:
     * its velocity window is 0 to 0, which no note-on reaches - the device compares the velocity of
     * a note as an integer from 0 to 127 against both ends of the window, and a note-on with the
     * velocity 0 is a note-off - its gain is 0 and it covers only the key 0. The velocity window
     * keeps the entry out of the lookup which picks the entries for a note, so it costs no voice
     * and does not take part in the alternation of overlapping entries; the single key keeps it out
     * of the per-key entry lists of the other keys, which hold at most 128 entries each.
     *
     * @param sb Where to append the entry
     * @param relativeSamplePath The relative path to the samples
     * @param sampleName The name of the sample without the file ending
     */
    private static void appendShadowEntry (final StringBuilder sb, final String relativeSamplePath, final String sampleName)
    {
        sb.append ('"').append (relativeSamplePath).append ('/').append (sampleName).append (".wav\"\t");
        // Pitch, FromNote, ToNote, Gain, FromVelo, ToVelo, Pan
        sb.append (formatMapDouble (60)).append ("\t0\t0\t").append (formatMapDouble (0)).append ("\t0\t0\t").append (formatMapDouble (0.5)).append ('\t');
        // Start, End, LoopMode, LoopStart, LoopEnd, Direction, CrossFade, TrackPitch
        sb.append (formatMapDouble (0)).append ('\t').append (formatMapDouble (1)).append ("\t0\t").append (formatMapDouble (0)).append ('\t').append (formatMapDouble (1)).append ("\t0\t").append (formatMapDouble (0)).append ("\t0");
    }


    /**
     * Log a note for every sample map which holds more entries than the device accepts. The device
     * stores at most {@link #MAX_MAP_ENTRIES} entries per map and drops every further one with the
     * error 'Number of entries in sample map exceeded' (Iridium MK2 firmware 4.0.6,
     * SampleMap::addSample).
     *
     * @param groups The groups of a layer, one sample map each
     * @param shadowEntries The number of entries which are added to the last map
     */
    private void checkMapSizes (final List<IGroup> groups, final int shadowEntries)
    {
        for (int i = 0; i < groups.size (); i++)
        {
            final int entries = groups.get (i).getSampleZones ().size () + (i == groups.size () - 1 ? shadowEntries : 0);
            if (entries > MAX_MAP_ENTRIES)
                this.notifier.logError ("IDS_QPAT_NOTIFY_TOO_MANY_ENTRIES", Integer.toString (i + 1), Integer.toString (entries), Integer.toString (MAX_MAP_ENTRIES));
        }
    }


    /**
     * Reduces the groups of the multi-sample to the given maximum. The sample zones of all other
     * groups are added to the last group which fits.
     *
     * @param groups The groups
     * @param maximumGroups The maximum number of groups to keep
     * @return The reduced groups
     */
    private static List<IGroup> reduceGroups (final List<IGroup> groups, final int maximumGroups)
    {
        if (groups.size () > maximumGroups)
        {
            // Add all sample zones of the groups which do not fit to the last one which does
            final IGroup lastGroup = groups.get (maximumGroups - 1);
            // The added zones already carry the (flattened) offsets of their own group, which
            // differ from the ones of the target group. Clear the offsets of the target group so
            // that gain and panning are stored completely per zone in the sample map.
            lastGroup.setGain (0);
            lastGroup.setPanning (0);
            lastGroup.setTuning (0);
            for (int i = maximumGroups; i < groups.size (); i++)
                for (final ISampleZone zone: groups.get (i).getSampleZones ())
                    lastGroup.addSampleZone (zone);
            // Remove the groups which were merged
            final int count = groups.size () - maximumGroups;
            for (int i = 0; i < count; i++)
                groups.removeLast ();
        }
        return groups;
    }


    /**
     * Checks if the zones are one key map which is played across the keyboard rather than a stack
     * of layers. Sources sometimes give the zones of a key map ranges which overlap slightly - the
     * E-mu Xtreme Lead 1 'Air Age' maps its lowest sample up to key 54 while the next one starts at
     * 43 - and splitting those into separate oscillators leaves each of them with a hole: 'Air Age'
     * ended up with an oscillator which is silent from key 55 to 71, which is where the instrument
     * is played.
     *
     * A key map is recognized by both its root notes and its lower key limits rising from zone to
     * zone: the zones follow each other up the keyboard, so however their ranges overlap they are
     * meant to be one map. A stack does not look like that - its layers sound on the same notes, so
     * they share a lower key limit, and usually a root note as well.
     *
     * @param zones The zones of a group
     * @return True if the zones are one key map
     */
    private static boolean isAscendingKeyMap (final List<ISampleZone> zones)
    {
        if (zones.size () < 2)
            return false;
        final List<ISampleZone> sorted = new ArrayList<> (zones);
        sorted.sort (Comparator.comparingInt ((final ISampleZone zone) -> limitToDefault (zone.getKeyLow (), 0)));
        for (int i = 1; i < sorted.size (); i++)
        {
            final ISampleZone previous = sorted.get (i - 1);
            final ISampleZone zone = sorted.get (i);
            if ((limitToDefault (previous.getKeyLow (), 0) >= limitToDefault (zone.getKeyLow (), 0)) || (previous.getKeyRoot () >= zone.getKeyRoot ()))
                return false;
        }
        return true;
    }


    /**
     * Split each group whose zones stack (overlap in both key and velocity) into separate layers,
     * so a layered preset maps to several oscillators instead of collapsing into one. Groups
     * without an internal overlap are kept unchanged. The largest layer is placed first so it
     * drives the main oscillator.
     *
     * @param groups The groups
     * @return The groups with stacked layers separated into individual groups
     */
    private static List<IGroup> splitLayers (final List<IGroup> groups)
    {
        final List<IGroup> result = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            final List<List<ISampleZone>> layers = partitionLayers (group.getSampleZones ());
            if (layers.size () < 2)
            {
                result.add (group);
                continue;
            }
            layers.sort (Comparator.<List<ISampleZone>> comparingInt (List::size).reversed ());
            for (final List<ISampleZone> layer: layers)
            {
                // All zones of a layer stem from the same group, therefore they all carry the same
                // flattened offsets and the group offsets stay valid for each layer.
                final DefaultGroup layerGroup = copyGroupSettings (group);
                for (final ISampleZone zone: layer)
                    layerGroup.addSampleZone (zone);
                result.add (layerGroup);
            }
        }
        return result;
    }


    /**
     * Greedily split zones into layers so that within a layer no two zones overlap in both key and
     * velocity (i.e. never sound at the same time on the same note). The overlap depth equals the
     * number of layers.
     *
     * @param zones The zones of a group
     * @return The layers of non-overlapping zones
     */
    private static List<List<ISampleZone>> partitionLayers (final List<ISampleZone> zones)
    {
        // A key map whose ranges overlap is not a stack, see isAscendingKeyMap
        if (isAscendingKeyMap (zones))
        {
            final List<List<ISampleZone>> single = new ArrayList<> ();
            single.add (new ArrayList<> (zones));
            return single;
        }

        final List<ISampleZone> sorted = new ArrayList<> (zones);
        // Place the widest zones first so a full-range layer does not scatter narrow zones.
        sorted.sort (Comparator.comparingInt ((final ISampleZone zone) -> limitToDefault (zone.getKeyLow (), 0)).thenComparing (Comparator.comparingInt ((final ISampleZone zone) -> limitToDefault (zone.getKeyHigh (), 127)).reversed ()));

        final List<List<ISampleZone>> layers = new ArrayList<> ();
        for (final ISampleZone zone: sorted)
        {
            List<ISampleZone> target = null;
            for (final List<ISampleZone> layer: layers)
            {
                boolean overlaps = false;
                for (final ISampleZone other: layer)
                    if (zonesOverlap (zone, other))
                    {
                        overlaps = true;
                        break;
                    }
                if (!overlaps)
                {
                    target = layer;
                    break;
                }
            }
            if (target == null)
            {
                target = new ArrayList<> ();
                layers.add (target);
            }
            target.add (zone);
        }
        return layers;
    }


    /**
     * Test if two zones overlap in both their key and their velocity range, i.e. they can sound at
     * the same time on the same note.
     *
     * @param a The first zone
     * @param b The second zone
     * @return True if they overlap
     */
    private static boolean zonesOverlap (final ISampleZone a, final ISampleZone b)
    {
        // Entries which overlap inside one sample map alternate on successive notes (a round
        // robin, confirmed on the device), so zones which are meant to alternate never sound at
        // the same time and stay together in one map - unless both carry the same sequence
        // position, which makes them members of two round-robin sets which sound together, a
        // stack of two alternating instruments
        if (a.getPlayLogic () != PlayLogic.ALWAYS && b.getPlayLogic () != PlayLogic.ALWAYS)
        {
            final int positionA = a.getSequencePosition ();
            final int positionB = b.getSequencePosition ();
            if (positionA < 1 || positionB < 1 || positionA != positionB)
                return false;
        }
        final boolean keyOverlap = limitToDefault (a.getKeyLow (), 0) <= limitToDefault (b.getKeyHigh (), 127) && limitToDefault (b.getKeyLow (), 0) <= limitToDefault (a.getKeyHigh (), 127);
        final boolean velocityOverlap = limitToDefault (a.getVelocityLow (), 1) <= limitToDefault (b.getVelocityHigh (), 127) && limitToDefault (b.getVelocityLow (), 1) <= limitToDefault (a.getVelocityHigh (), 127);
        return keyOverlap && velocityOverlap;
    }


    /**
     * Create the parameters of one layer.
     *
     * @param groups The groups of the layer, one for each oscillator
     * @param flattenAmpEnvelope True to write the amplitude envelope with a full sustain, see
     *            {@link #computeFlatAmpEnvelopeLevel(List)}
     * @param isMultiLayer True if the patch has more than one layer
     * @param multisampleSource The multi-sample source
     * @param version The format version of the patch
     * @return The parameters
     */
    private static List<WaldorfQpatParameter> createParameters (final List<IGroup> groups, final boolean flattenAmpEnvelope, final boolean isMultiLayer, final IMultisampleSource multisampleSource, final int version)
    {
        final List<WaldorfQpatParameter> parameters = new ArrayList<> ();

        // PolyMonoMode: [0] "Poly", [1] "Mono" - a monophonic source plays one voice at a time. The
        // device has no other limit for the voices of a patch - LayerVoices is only used in the
        // split mode, which divides the voices of the device among its layers
        final boolean isMonophonicLegato = multisampleSource.isMonophonicLegato ();
        if (isMonophonicLegato || multisampleSource.getPolyphony () == 1)
            parameters.add (new WaldorfQpatParameter ("PolyMonoMode", "Mono", 1.0f));

        createGlideParameters (parameters, multisampleSource.getPortamentoTime (), isMonophonicLegato);

        if (isMultiLayer)
        {
            // All layers sound simultaneously over the whole keyboard range
            parameters.add (new WaldorfQpatParameter ("TimbreMode", TAG_LAYERED, TIMBRE_MODE_MULTI));
            parameters.add (new WaldorfQpatParameter ("MultiAllocMode", TAG_LAYERED, 0));
            parameters.add (new WaldorfQpatParameter ("LayerActive", TAG_ACTIVE, 1));
        }

        // The slots of the modulation matrix are filled from the first one on, the modulation wheel
        // first, see MatrixSlots. The filter belongs to the layer and is read from the first zone
        // of its first group, like in createFilterParameters below
        final MatrixSlots slots = new MatrixSlots ();
        createCutoffWheelModulator (parameters, groups.get (0).getSampleZones ().get (0).getFilter (), version, slots);

        for (int i = 0; i < groups.size (); i++)
        {
            final String groupIndex = Integer.toString (i + 1);

            final List<ISampleZone> sampleZones = groups.get (i).getSampleZones ();
            // Empty groups are already removed!
            final ISampleZone firstZone = sampleZones.get (0);

            // Particle
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Type", "Particle", 2.0f));

            // Osc1ParticleSampleMode: [2] "Normal" selects normal, key-tracked sample playback.
            // Without it the oscillator defaults to a mode that plays a single sample at a fixed
            // pitch, so a sample mapped across the keyboard does not follow the played note.
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "ParticleSampleMode", "Normal", 2.0f));

            // Osc1CoarsePitch / Osc1FinePitch: already set in the sample maps!
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "CoarsePitch", "+0 semi", 24.0f));
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "FinePitch", "+0.0 cents", 0.5f));

            // Osc1PitchBendRange: [0..48] ~ [-24..24], the options are named like '+2' and '-2'
            final int pitchbend = Math.clamp (Math.round (firstZone.getBendUp () / 100.0), -24, 24);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "PitchBendRange", (pitchbend < 0 ? "" : "+") + pitchbend, pitchbend + 24.0f));

            // Osc1Keytrack: [0..1] ~ [-200..200] %, 0.75 is +100 %. It scales the tracking of the
            // entries of the sample map which follow the keyboard; an entry with a fixed pitch is
            // marked in the map (TrackPitch)
            final double keyTracking = getOscillatorKeyTracking (sampleZones);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Keytrack", String.format (Locale.US, "%+.1f", Double.valueOf (keyTracking * 100.0)), (float) (0.5 + keyTracking / 4.0)));

            // Osc1Vol: [0..1] ~ [-inf dB..0.000 dB]. The oscillator is the group, so the group's
            // gain offset is stored here and the remainder per zone in the sample map. A source
            // without a group gain keeps the neutral 0 dB and stores everything in the sample map.
            final double gainOffset = getGroupGainOffset (groups.get (i));
            final String volumeStr = (gainOffset < 0 ? "" : "+") + StringUtils.formatDouble (gainOffset, 3, " dB");
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Vol", volumeStr, (float) convertFromDecibels (gainOffset)));

            // Osc1Pan: [0..1] ~ [L..R]. Same as the volume above: the group's panning offset is
            // stored here, the remainder per zone in the sample map.
            final double panningOffset = getGroupPanningOffset (groups.get (i));
            final String panningStr = panningOffset == 0 ? "Center" : StringUtils.formatPercent (panningOffset, 2);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Pan", panningStr, (float) ((panningOffset + 1.0) / 2.0)));

            createPitchEnvelopeModulator (parameters, firstZone.getPitchEnvelopeModulator (), i + 1, version, slots);

            if (i == 0)
            {
                createFilterParameters (parameters, firstZone.getFilter ());

                final IEnvelopeModulator amplitudeEnvelopeModulator = firstZone.getAmplitudeEnvelopeModulator ();
                final IEnvelope envelope = amplitudeEnvelopeModulator.getSource ();
                // The audio is only inspected when the attack is short enough to be affected at all
                final double sourceAttackTime = envelope.getAttackTime ();
                final boolean allowInstantAttack = sourceAttackTime > 0 && sourceAttackTime < DECLICK_SECONDS && !startsWithAudibleStep (groups);
                createEnvelope (parameters, envelope, AMP_ENV, AMP_ENV, flattenAmpEnvelope, allowInstantAttack);

                // AmpVeloAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
                final double ampVeloAmount = firstZone.getAmplitudeVelocityModulator ().getDepth ();
                parameters.add (new WaldorfQpatParameter ("AmpVeloAmount", StringUtils.formatPercent (ampVeloAmount, 2), (float) ((ampVeloAmount + 1.0) / 2.0)));

                createLfoModulators (parameters, firstZone, version, slots);
            }
        }

        return parameters;
    }


    /**
     * Create the parameters of the glide. The device glides from the pitch of the previous note to
     * the pitch of the new one in the time of GlideRate, whatever the interval: the time is 2 x
     * rate^2 seconds (Iridium MK2 firmware 4.0.6, the step of the glide is the interval x 128 /
     * (88200 x rate^2) per block of 128 samples, the same law as the display). Without a portamento
     * nothing is written, which leaves the glide off.
     *
     * @param parameters Where to add the parameters
     * @param portamentoTime The portamento time in seconds, 0 if there is none
     * @param isMonophonicLegato True if the source glides only to notes which are played legato
     */
    private static void createGlideParameters (final List<WaldorfQpatParameter> parameters, final double portamentoTime, final boolean isMonophonicLegato)
    {
        if (portamentoTime <= 0)
            return;

        // GlideOnOff: [0] "Off" [1] "On"
        parameters.add (new WaldorfQpatParameter ("GlideOnOff", "On", 1.0f));
        // GlideRate: [0..1] ~ [0..2] seconds
        final double glideTime = Math.min (portamentoTime, GLIDE_MAXIMUM_TIME);
        parameters.add (new WaldorfQpatParameter ("GlideRate", formatSeconds (glideTime), (float) Math.sqrt (glideTime / GLIDE_MAXIMUM_TIME)));
        // GlideType: [0] "Onset" glides to every new note, [1] "Legato" only to a note which is
        // played while another one is still held
        parameters.add (new WaldorfQpatParameter ("GlideType", isMonophonicLegato ? "Legato" : "Onset", isMonophonicLegato ? 1.0f : 0.0f));
    }


    /**
     * Get the key tracking to write into the oscillator of a group. The oscillator holds one key
     * tracking for all entries of its sample map which follow the keyboard, therefore a tracking
     * other than 100 % can only be written if all such zones share it.
     *
     * @param zones The zones of the group
     * @return The key tracking in the range of [0..2], 1 is the tracking of the keyboard
     */
    private static double getOscillatorKeyTracking (final List<ISampleZone> zones)
    {
        double keyTracking = -1;
        for (final ISampleZone zone: zones)
        {
            // A zone without key tracking is written as an entry with a fixed pitch
            final double zoneKeyTracking = zone.getKeyTracking ();
            if (zoneKeyTracking <= 0)
                continue;
            if (keyTracking < 0)
                keyTracking = zoneKeyTracking;
            else if (Math.abs (zoneKeyTracking - keyTracking) > 0.0001)
                return 1;
        }
        return keyTracking < 0 ? 1 : Math.clamp (keyTracking, 0, 2);
    }


    /**
     * Create the pitch envelope of an oscillator: the free envelope of the same index, routed
     * through the next free slot of the matrix.
     *
     * @param parameters Where to add the parameters
     * @param pitchEnvelopeModulator The pitch envelope modulator of the group
     * @param oscIndex The index of the oscillator [1..3]
     * @param version The format version of the patch, which decides the index of a destination
     * @param slots The slots of the modulation matrix
     */
    private static void createPitchEnvelopeModulator (final List<WaldorfQpatParameter> parameters, final IEnvelopeModulator pitchEnvelopeModulator, final int oscIndex, final int version, final MatrixSlots slots)
    {
        final double depth = pitchEnvelopeModulator.getDepth ();
        if (depth == 0)
            return;

        // MatrixSrcX: [4] "Free Env1" [5] "Free Env2" [6] "Free Env3" - the free envelope with the
        // index of the oscillator plays its pitch envelope
        // MatrixDstX: [2] "Osc1 Pitch" [3] "Osc2 Pitch" [4] "Osc3 Pitch"
        final String destination = WaldorfQpatModulationMatrix.getOscillatorPitchDestination (oscIndex);
        createModulationMatrixEntry (parameters, slots.take (), "Free Env" + oscIndex, WaldorfQpatModulationMatrix.SOURCE_FIRST_FREE_ENVELOPE + oscIndex - 1, destination, WaldorfQpatModulationMatrix.getDestinationIndex (destination, version), convertFromPitchDepth (depth));

        final String prefix = "FreeEnv" + oscIndex;
        createEnvelope (parameters, pitchEnvelopeModulator.getSource (), prefix, prefix, false, false);
    }


    /**
     * Create the parameters of the vibrato, of the tremolo and of the modulation of the filter
     * cutoff by a low frequency oscillator. The device has 6 low frequency oscillators, of which
     * the LFOs 1-3 are used in this order, the LFOs 4-6 stay free for the user. Each modulation
     * takes the next free slot of the matrix, see {@link MatrixSlots}.
     * <p>
     * The vibrato modulates the destination "Pitch", which is the pitch of all three oscillators at
     * once. This costs one slot instead of one slot per oscillator and matches a vibrato of a
     * multi-sample, which is a property of the whole instrument and not of a single layer.
     *
     * @param parameters Where to add the parameters
     * @param zone The zone which carries the modulators
     * @param version The format version of the patch, which decides the index of a destination
     * @param slots The slots of the modulation matrix
     */
    private static void createLfoModulators (final List<WaldorfQpatParameter> parameters, final ISampleZone zone, final int version, final MatrixSlots slots)
    {
        // Vibrato - the pitch swings around the played note, therefore the LFO stays bipolar
        final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
        final ILfo pitchLfo = pitchLfoModulator.getSource ();
        final double pitchDepth = pitchLfoModulator.getDepth ();
        if (pitchDepth != 0 && pitchLfo.isSet ())
        {
            final String destination = WaldorfQpatModulationMatrix.DESTINATION_PITCH;
            createModulationMatrixEntry (parameters, slots.take (), LFO_VIBRATO, destination, WaldorfQpatModulationMatrix.getDestinationIndex (destination, version), convertFromPitchDepth (pitchDepth));
            createLfo (parameters, pitchLfo, LFO_VIBRATO, false);
        }

        // Tremolo - the amplifier already plays at its full level, therefore the LFO is unipolar
        // and the amount is negative so that the modulation only attenuates. A bipolar LFO would
        // press the first half of every cycle against the upper end of the amplifier and turn the
        // waveform into its rectified half.
        final ILfoModulator amplitudeLfoModulator = zone.getAmplitudeLfoModulator ();
        final ILfo amplitudeLfo = amplitudeLfoModulator.getSource ();
        final double amplitudeDepth = amplitudeLfoModulator.getDepth ();
        if (amplitudeDepth != 0 && amplitudeLfo.isSet ())
        {
            // A negative depth only turns the tremolo by half a cycle, which is not audible on its
            // own, therefore only its magnitude is written
            final double decibels = Math.abs (amplitudeDepth) * ILfoModulator.MAX_VOLUME_DEPTH;
            final double amount = convertFromDecibels (-decibels) - 1.0;
            final String destination = WaldorfQpatModulationMatrix.DESTINATION_VCA;
            createModulationMatrixEntry (parameters, slots.take (), LFO_TREMOLO, destination, WaldorfQpatModulationMatrix.getDestinationIndex (destination, version), amount);
            createLfo (parameters, amplitudeLfo, LFO_TREMOLO, true);
        }

        // Filter cutoff - the cutoff swings around its value, therefore the LFO stays bipolar. The
        // device adds the modulation to the cutoff in the units of Filter1CutOff, whose range
        // covers WaldorfQpatModulationMatrix#CUTOFF_RANGE semi-tones, and does not square its
        // amount. The modulation is only written with a filter which is written as active, see
        // createFilterParameters
        final Optional<IFilter> optFilter = zone.getFilter ();
        if (optFilter.isEmpty () || optFilter.get ().getType () == FilterType.BAND_REJECTION)
            return;
        final ILfoModulator cutoffLfoModulator = optFilter.get ().getCutoffLfoModulator ();
        final ILfo cutoffLfo = cutoffLfoModulator.getSource ();
        final double cutoffDepth = cutoffLfoModulator.getDepth ();
        if (cutoffDepth != 0 && cutoffLfo.isSet ())
        {
            // The depth of the model covers IEnvelope#MAX_ENVELOPE_DEPTH cent
            final double semitones = cutoffDepth * IEnvelope.MAX_ENVELOPE_DEPTH / 100.0;
            final double amount = Math.clamp (semitones / WaldorfQpatModulationMatrix.CUTOFF_RANGE, -1.0, 1.0);
            final String destination = WaldorfQpatModulationMatrix.DESTINATION_FILTER1_CUTOFF;
            createModulationMatrixEntry (parameters, slots.take (), LFO_CUTOFF, destination, WaldorfQpatModulationMatrix.getDestinationIndex (destination, version), amount);
            createLfo (parameters, cutoffLfo, LFO_CUTOFF, false);
        }
    }


    /**
     * Create the modulation of the filter cutoff by the modulation wheel: the next free slot of the
     * matrix - the first one, since the wheel is written before all other modulations - with the
     * source 'Wheel' on the destination 'Filter1 Cutoff'. The device adds wheel x amount to the
     * cutoff in the units of Filter1CutOff, whose range of 0 to 1 covers the whole range of the
     * filter, which is the unit of the depth of the model as well. Like the modulation by a low
     * frequency oscillator it is only written with a filter which is written as active, see
     * createFilterParameters.
     *
     * @param parameters Where to add the parameters
     * @param optFilter The filter of the layer
     * @param version The format version of the patch, which decides the index of a destination
     * @param slots The slots of the modulation matrix
     */
    private static void createCutoffWheelModulator (final List<WaldorfQpatParameter> parameters, final Optional<IFilter> optFilter, final int version, final MatrixSlots slots)
    {
        if (optFilter.isEmpty () || optFilter.get ().getType () == FilterType.BAND_REJECTION)
            return;
        final double depth = optFilter.get ().getCutoffModWheelModulator ().getDepth ();
        if (depth == 0)
            return;
        final String destination = WaldorfQpatModulationMatrix.DESTINATION_FILTER1_CUTOFF;
        createModulationMatrixEntry (parameters, slots.take (), WaldorfQpatModulationMatrix.SOURCE_NAME_WHEEL, WaldorfQpatModulationMatrix.SOURCE_WHEEL, destination, WaldorfQpatModulationMatrix.getDestinationIndex (destination, version), depth);
    }


    /**
     * Activate one slot of the modulation matrix which a low frequency oscillator drives.
     *
     * @param parameters Where to add the parameters
     * @param slot The index of the modulation matrix slot [1..40]
     * @param lfoIndex The index of the low frequency oscillator which drives the slot [1..6]
     * @param destinationName The name of the destination as the device spells it
     * @param destination The index of the destination
     * @param amount The modulation amount in the range of [-1..1]
     */
    private static void createModulationMatrixEntry (final List<WaldorfQpatParameter> parameters, final int slot, final int lfoIndex, final String destinationName, final int destination, final double amount)
    {
        // MatrixSrcX: [7] "LFO 1" ... [12] "LFO 6"
        createModulationMatrixEntry (parameters, slot, "LFO " + lfoIndex, WaldorfQpatModulationMatrix.SOURCE_FIRST_LFO + lfoIndex - 1, destinationName, destination, amount);
    }


    /**
     * Activate one slot of the modulation matrix.
     *
     * @param parameters Where to add the parameters
     * @param slot The index of the modulation matrix slot [1..40]
     * @param sourceName The name of the source as the device spells it
     * @param source The index of the source
     * @param destinationName The name of the destination as the device spells it
     * @param destination The index of the destination
     * @param amount The modulation amount in the range of [-1..1]
     */
    private static void createModulationMatrixEntry (final List<WaldorfQpatParameter> parameters, final int slot, final String sourceName, final int source, final String destinationName, final int destination, final double amount)
    {
        // MatrixOnOffX: [0] "Disabled" [1] "Active"
        parameters.add (new WaldorfQpatParameter ("MatrixOnOff" + slot, TAG_ACTIVE, 1.0f));

        // MatrixSrcX: the sources which are written keep their index in all format versions, see
        // WaldorfQpatModulationMatrix
        parameters.add (new WaldorfQpatParameter ("MatrixSrc" + slot, sourceName, source));

        // MatrixDstX: the device resolves the destination by its name, the index is the one of the
        // format version, see WaldorfQpatModulationMatrix
        parameters.add (new WaldorfQpatParameter ("MatrixDst" + slot, destinationName, destination));

        // MatrixAmountX: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        parameters.add (new WaldorfQpatParameter ("MatrixAmount" + slot, StringUtils.formatPercent (amount, 2), (float) ((amount + 1.0) / 2.0)));
    }


    /**
     * Create all parameters of one low frequency oscillator. Every parameter which the modulation
     * depends on is written, so that the result does not depend on what the previously loaded
     * preset left in that oscillator.
     *
     * @param parameters Where to add the parameters
     * @param lfo The low frequency oscillator to write
     * @param lfoIndex The index of the low frequency oscillator [1..6]
     * @param isUnipolar True to let the oscillator swing from zero to its full level instead of
     *            around zero
     */
    private static void createLfo (final List<WaldorfQpatParameter> parameters, final ILfo lfo, final int lfoIndex, final boolean isUnipolar)
    {
        final String prefix = "Lfo" + lfoIndex;

        // LfoXSpeed: [0..1] ~ [1/240 Hz..100 Hz]
        final double rate = Math.clamp (lfo.getRate (), LFO_MINIMUM_RATE, LFO_MAXIMUM_RATE);
        parameters.add (new WaldorfQpatParameter (prefix + "Speed", StringUtils.formatDouble (rate, 3, " Hz"), (float) convertFromLfoRate (rate)));

        // LfoXSync: [0] "Off" [1] "On" - the rate of the model is in Hertz and not a note length
        parameters.add (new WaldorfQpatParameter (prefix + "Sync", "Off", 0.0f));

        // LfoXGlobal: [0] "Poly" [1] "Global" [2] "Single Trig". A vibrato and a tremolo belong to
        // the played note, therefore the per-voice oscillator is used. The key synchronization of
        // the model is not read, since no source format fills it and the free running variant
        // would smear the modulation across the voices of every converted preset.
        parameters.add (new WaldorfQpatParameter (prefix + "Global", "Poly", 0.0f));

        // LfoXShape: [0] "Sine" [1] "Triangle" [2] "Square" [3] "Saw (down)" [4] "Saw (up)" [5]
        // "S&H"
        final int shape = convertFromWaveform (lfo.getWaveform ());
        parameters.add (new WaldorfQpatParameter (prefix + "Shape", LFO_SHAPES[shape], shape));

        // LfoXPolarity: [0] "Bipolar" [1] "Unipolar"
        parameters.add (new WaldorfQpatParameter (prefix + "Polarity", isUnipolar ? "Unipolar" : "Bipolar", isUnipolar ? 1.0f : 0.0f));

        // LfoXPhase: [0..1] ~ [0..360 degrees]. From LFO_FREE_PHASE on the device runs the
        // oscillator freely, therefore a full cycle wraps back to its start.
        final double phase = lfo.getStartPhase ();
        final double startPhase = phase < 0 ? 0 : Math.min (phase % 1.0, LFO_FREE_PHASE);
        parameters.add (new WaldorfQpatParameter (prefix + "Phase", Math.round (startPhase * 360.0) + " deg", (float) startPhase));

        // LfoXDelay: [0..1] ~ [0..20] seconds
        final double delay = Math.clamp (lfo.getDelay (), 0, LFO_MAXIMUM_DELAY);
        parameters.add (new WaldorfQpatParameter (prefix + TAG_DELAY, formatSeconds (delay), (float) convertFromLfoTime (delay, LFO_MAXIMUM_DELAY)));

        // LfoXAttack: [0..1] ~ [0..10] seconds - the fade-in of the model
        final double attack = Math.clamp (lfo.getFadeIn (), 0, LFO_MAXIMUM_ATTACK);
        parameters.add (new WaldorfQpatParameter (prefix + TAG_ATTACK, formatSeconds (attack), (float) convertFromLfoTime (attack, LFO_MAXIMUM_ATTACK)));

        // LfoXDecay: [1] is off. The model has no fade-out, therefore the oscillator keeps its
        // level until the note ends.
        parameters.add (new WaldorfQpatParameter (prefix + TAG_DECAY, "Off", 1.0f));
    }


    /**
     * Convert a waveform of the model to the index of the shape of the device.
     *
     * @param waveform The waveform
     * @return The index in {@link #LFO_SHAPES}
     */
    private static int convertFromWaveform (final LfoWaveform waveform)
    {
        return switch (waveform)
        {
            case SINE -> 0;
            case TRIANGLE -> 1;
            case SQUARE -> 2;
            case SAWTOOTH_DOWN -> 3;
            case SAWTOOTH_UP -> 4;
            case RANDOM -> 5;
        };
    }


    /**
     * Convert the rate of a low frequency oscillator into the parameter value of the device. The
     * sound engine calculates the rate as 1/240 Hz + (100 Hz - 1/240 Hz) * x^4.
     *
     * @param rate The rate in Hertz
     * @return The parameter value in the range of [0..1]
     */
    private static double convertFromLfoRate (final double rate)
    {
        return Math.clamp (Math.pow ((rate - LFO_MINIMUM_RATE) / (LFO_MAXIMUM_RATE - LFO_MINIMUM_RATE), 0.25), 0, 1);
    }


    /**
     * Convert a time of a low frequency oscillator into the parameter value of the device. The
     * sound engine calculates all of these times as maximum * x^2.
     *
     * @param seconds The time in seconds
     * @param maximum The time in seconds which the parameter value 1 gives
     * @return The parameter value in the range of [0..1]
     */
    private static double convertFromLfoTime (final double seconds, final double maximum)
    {
        return Math.clamp (Math.sqrt (seconds / maximum), 0, 1);
    }


    /**
     * Create all filter parameters.
     *
     * @param parameters Where to add the filter parameters
     * @param optFilter The filter for which to create the parameters
     */
    private static void createFilterParameters (final List<WaldorfQpatParameter> parameters, final Optional<IFilter> optFilter)
    {
        if (optFilter.isEmpty () || optFilter.get ().getType () == FilterType.BAND_REJECTION)
        {
            parameters.add (new WaldorfQpatParameter ("FilterState", "Bypass", 1));
            return;
        }

        final IFilter filter = optFilter.get ();

        // FilterState: [0] "Active" [1] "Bypass" [2] "Off"
        parameters.add (new WaldorfQpatParameter ("FilterState", TAG_ACTIVE, 0));

        // Filter12Type: [0] "12dB LP" [1] "12dB sat. LP" [2] "12dB dirty LP" [3] "24dB LP" [4]
        // "24dB sat. LP" [5] "24dB dirty LP" [6] "12dB HP" [7] "12dB sat. HP" [8] "12dB dirty HP"
        // [9] "24dB HP" [10] "24dB sat. HP" [11] "24dB dirty HP" [12] "12dB BP" [13] "12dB sat. BP"
        // [14] "12dB dirty BP" [15] "24dB BP" [16] "24dB sat. BP" [17] "24dB dirty BP"
        int pos;
        String filterName;
        switch (filter.getType ())
        {
            default:
            case LOW_PASS:
                filterName = "dB LP";
                pos = 0;
                break;
            case HIGH_PASS:
                filterName = "dB HP";
                pos = 6;
                break;
            case BAND_PASS:
                filterName = "dB BP";
                pos = 12;
                break;
        }
        final boolean is24 = filter.getPoles () == 4;
        if (is24)
            pos += 3;
        parameters.add (new WaldorfQpatParameter ("Filter12Type", (is24 ? "24" : "12") + filterName, pos));

        // Filter1CutOff: [0.00] "8.1758 Hz" ... [1.00] "19912.2 Hz"
        final double cutoffFrequency = Math.clamp (filter.getCutoff (), MIN_CUTOFF_FREQUENCY, MAX_CUTOFF_FREQUENCY);
        final double cutoff = Math.log (cutoffFrequency / MIN_CUTOFF_FREQUENCY) / (Math.log (2) * 11.25);
        parameters.add (new WaldorfQpatParameter ("Filter1CutOff", StringUtils.formatDouble (cutoffFrequency, 4, " Hz"), (float) cutoff));

        // Filter1Reso: [0.00] "0.00 %" ... [1.00] "100.00 %"
        final double resonance = filter.getResonance ();
        parameters.add (new WaldorfQpatParameter ("Filter1Reso", StringUtils.formatPercent (resonance, 2), (float) resonance));

        // Filter1EnvAmount: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final double filterVeloAmount = filter.getCutoffVelocityModulator ().getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1VeloAmount", StringUtils.formatPercent (filterVeloAmount, 2), (float) ((filterVeloAmount + 1.0) / 2.0)));

        final IEnvelopeModulator modulator = filter.getCutoffEnvelopeModulator ();
        final double filterEnvAmount = modulator.getDepth ();
        parameters.add (new WaldorfQpatParameter ("Filter1EnvAmount", StringUtils.formatPercent (filterEnvAmount, 2), (float) ((filterEnvAmount + 1.0) / 2.0)));

        // Filter1Keytrack: [0.00] "-200.00 %" ... [0.50] "0.00 %" ... [0.75] "+100.00 %" ...
        // [1.00] "+200.00 %" - the same scale which the key tracking of an oscillator uses, where
        // +100 % is the 1:1 tracking the manual describes. Full tracking is therefore 0.75 and not
        // the end of the range.
        final double keyTracking = filter.getCutoffKeyTracking ();
        parameters.add (new WaldorfQpatParameter ("Filter1Keytrack", StringUtils.formatPercent (keyTracking, 2), (float) Math.clamp ((keyTracking + 2.0) / 4.0, 0, 1)));

        createEnvelope (parameters, modulator.getSource (), "Filter1Env", "Filter1", false, false);
    }


    private static void createEnvelope (final List<WaldorfQpatParameter> parameters, final IEnvelope envelope, final String prefix, final String slopePrefix, final boolean flattenSustain, final boolean allowInstantAttack)
    {
        final boolean isPitch = prefix.startsWith ("Free");
        // Only the amplitude envelope gates the VCA, so only it can click when a stage is instant;
        // a short filter or pitch envelope stage is left unchanged.
        final boolean isAmplitude = AMP_ENV.equals (prefix);
        // A pitch envelope which starts at a level of its own falls from it to the sustain level
        // during its attack time. The start level -1 means that the source does not set one, such
        // an envelope rises from zero through its attack and decay like every other one
        final boolean startsAtLevel = isPitch && envelope.getStartLevel () > 0;

        if (startsAtLevel)
        {
            // xxxEnvDelay
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DELAY, formatSeconds (0), 0));
            // xxxEnvAttack
            parameters.add (new WaldorfQpatParameter (prefix + TAG_ATTACK, formatSeconds (0), 0));
            // xxxEnvDecay
            final double decayTime = Math.clamp (envelope.getAttackTime (), 0, MAX_ENVELOPE_TIME);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DECAY, formatSeconds (decayTime), (float) convertFromTime (decayTime)));
        }
        else
        {
            // xxxEnvDelay
            final double delayTime = Math.clamp (envelope.getDelayTime (), 0, 2);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DELAY, formatSeconds (delayTime), (float) convertFromDelayTime (delayTime)));
            // xxxEnvAttack
            final double attackTime = declickAmpTime (isAmplitude && !allowInstantAttack, Math.clamp (envelope.getAttackTime (), 0, MAX_ENVELOPE_TIME));
            parameters.add (new WaldorfQpatParameter (prefix + TAG_ATTACK, formatSeconds (attackTime), (float) convertFromTime (attackTime)));
            // xxxEnvDecay
            final double decayTime = Math.clamp (Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ()), 0, MAX_ENVELOPE_TIME);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DECAY, formatSeconds (decayTime), (float) convertFromTime (decayTime)));
        }

        // xxxEnvRelease
        final double releaseTime = declickAmpRelease (isAmplitude, Math.clamp (envelope.getReleaseTime (), 0, MAX_ENVELOPE_TIME));
        parameters.add (new WaldorfQpatParameter (prefix + "Release", formatSeconds (releaseTime), (float) convertFromTime (releaseTime)));

        // xxxEnvSustain - a flattened amplitude envelope sustains at full level; its level is
        // folded into the zone gain instead (see computeFlatAmpEnvelopeLevel)
        double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel == -1)
            sustainLevel = isPitch ? 0 : 1;
        if (flattenSustain)
            sustainLevel = 1;
        parameters.add (new WaldorfQpatParameter (prefix + "Sustain", StringUtils.formatPercent (sustainLevel, 2), (float) sustainLevel));

        if (startsAtLevel)
        {
            // xxxDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double decaySlope = envelope.getAttackSlope ();
            String decaySlopeStr = SLOPE_LINEAR;
            double decaySlopeValue = 2;
            if (decaySlope == -1)
            {
                decaySlopeStr = SLOPE_EXP;
                decaySlopeValue = 0;
            }
            else if (decaySlope < 0)
            {
                decaySlopeStr = SLOPE_EXP_ALT;
                decaySlopeValue = SLOPE_EXP_ALT_INDEX;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "DecayCurve", decaySlopeStr, (float) decaySlopeValue));
        }
        else
        {
            // xxxAttackCurve: [0] "Exp" [1] "RC" [2] "Lin"
            final double attackSlope = envelope.getAttackSlope ();
            String attackSlopeStr = SLOPE_LINEAR;
            double attackSlopeValue = 2;
            if (attackSlope > 0)
            {
                attackSlopeStr = SLOPE_EXP;
                attackSlopeValue = 0;
            }
            else if (attackSlope < 0)
            {
                attackSlopeStr = SLOPE_RC;
                attackSlopeValue = 1;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "AttackCurve", attackSlopeStr, (float) attackSlopeValue));

            // xxxDecayCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double decaySlope = envelope.getDecaySlope ();
            String decaySlopeStr = SLOPE_LINEAR;
            double decaySlopeValue = 2;
            if (decaySlope == -1)
            {
                decaySlopeStr = SLOPE_EXP;
                decaySlopeValue = 0;
            }
            else if (decaySlope < 0)
            {
                decaySlopeStr = SLOPE_EXP_ALT;
                decaySlopeValue = SLOPE_EXP_ALT_INDEX;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "DecayCurve", decaySlopeStr, (float) decaySlopeValue));

            // xxxReleaseCurve: [0] "Exp" [1] "Exp alt" [2] "Lin"
            final double releaseSlope = envelope.getReleaseSlope ();
            String releaseSlopeStr = SLOPE_LINEAR;
            double releaseSlopeValue = 2;
            if (releaseSlope == -1)
            {
                releaseSlopeStr = SLOPE_EXP;
                releaseSlopeValue = 0;
            }
            else if (releaseSlope < 0)
            {
                releaseSlopeStr = SLOPE_EXP_ALT;
                releaseSlopeValue = SLOPE_EXP_ALT_INDEX;
            }
            parameters.add (new WaldorfQpatParameter (slopePrefix + "ReleaseCurve", releaseSlopeStr, (float) releaseSlopeValue));
        }
    }


    /**
     * Writes the header information preceding the actual data.
     *
     * @param out The output stream to write to
     * @param metadata The metadata
     * @param name The name of the multi-sample
     * @param version The format version of the patch
     * @throws IOException Could not write
     */
    private static void writeHeader (final OutputStream out, final IMetadata metadata, final String name, final int version) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, WaldorfQpatConstants.MAGIC, false);
        StreamUtils.writeUnsigned32 (out, version, false);
        StreamUtils.writeAscii (out, StringUtils.fixASCII (name), WaldorfQpatConstants.MAX_STRING_LENGTH);
        // The author (offset 40) and bank (offset 72) fields are shown by the device. Use the
        // explicit creator settings when provided, otherwise fall back to the source metadata.
        StreamUtils.writeAscii (out, StringUtils.fixASCII (metadata.getCreator ()), WaldorfQpatConstants.MAX_STRING_LENGTH);
        StreamUtils.writeAscii (out, StringUtils.fixASCII (metadata.getDescription ()).replace ('\r', ' ').replace ('\n', ' '), WaldorfQpatConstants.MAX_STRING_LENGTH);

        writeAttributes (out, metadata);
    }


    /**
     * Write the four attributes of a patch: the category first, then the keywords. The device lists
     * them next to the name and filters the patches by them, therefore every attribute is written
     * in the spelling which the factory sound sets use, see {@link #toDeviceAttribute(String)} -
     * otherwise e.g. 'Keyboard' or 'KEYS' ends up in the filter list next to the 'Keys' of every
     * other patch and each only finds a part of the sounds. A category which was not detected is
     * left out instead of filling the filter list with the word 'Unknown'.
     *
     * @param out The output stream to write to
     * @param metadata The metadata
     * @throws IOException Could not write
     */
    private static void writeAttributes (final OutputStream out, final IMetadata metadata) throws IOException
    {
        final List<String> attributes = new ArrayList<> ();
        addAttribute (attributes, metadata.getCategory ());
        for (final String keyword: metadata.getKeywords ())
            addAttribute (attributes, keyword);

        for (int i = 0; i < 4; i++)
            StreamUtils.writeAscii (out, i < attributes.size () ? StringUtils.fixASCII (attributes.get (i)) : "", WaldorfQpatConstants.MAX_STRING_LENGTH);
    }


    /**
     * Add one attribute if there is room left for it, it says something and it is not already
     * present. The device shows the same attribute twice otherwise, since a keyword often repeats
     * the category.
     *
     * @param attributes The attributes collected so far
     * @param attribute The attribute to add
     */
    private static void addAttribute (final List<String> attributes, final String attribute)
    {
        if (attribute == null || attribute.isBlank () || attributes.size () >= 4 || TagDetector.CATEGORY_UNKNOWN.equalsIgnoreCase (attribute.trim ()))
            return;
        final String deviceAttribute = toDeviceAttribute (attribute);
        for (final String present: attributes)
            if (present.equalsIgnoreCase (deviceAttribute))
                return;
        attributes.add (deviceAttribute);
    }


    /**
     * Get the spelling of the factory sound sets for an attribute: the word of the vocabulary which
     * matches regardless of the case, e.g. 'Keys' for 'KEYS' or 'Keyboard' and 'Drum' for 'DRUMS'.
     * A word which the factory sets do not use is written capitalized - 'Vintage' for 'VINTAGE',
     * 'Digital' for 'digital' and 'Soft Attack' for 'soft_attack'.
     *
     * @param attribute The attribute as the analysis or the source spells it
     * @return The attribute as the device spells it
     */
    private static String toDeviceAttribute (final String attribute)
    {
        final String trimmed = attribute.trim ();
        final String known = ATTRIBUTE_LOOKUP.get (trimmed.toUpperCase (Locale.US));
        if (known != null)
            return known;

        final StringBuilder sb = new StringBuilder (trimmed.length ());
        boolean isWordStart = true;
        for (final char c: trimmed.toCharArray ())
        {
            if (c == '_' || c == ' ')
            {
                if (!sb.isEmpty () && sb.charAt (sb.length () - 1) != ' ')
                    sb.append (' ');
                isWordStart = true;
                continue;
            }
            sb.append (isWordStart ? Character.toUpperCase (c) : Character.toLowerCase (c));
            isWordStart = c == '-';
        }
        return sb.toString ().trim ();
    }


    /**
     * Get the gain offset of a group which is stored in the volume of the respective oscillator.
     * The offset is not applied for a fully silenced group, since subtracting negative infinity
     * from the (equally infinite) zone gain would result in a NaN which cannot be written.
     *
     * @param group The group
     * @return The gain offset in dB, 0 if there is none
     */
    private static double getGroupGainOffset (final IGroup group)
    {
        final double gain = group.getGain ();
        return Double.isFinite (gain) ? gain : 0;
    }


    /**
     * Get the panning offset of a group which is stored in the panning of the respective
     * oscillator.
     *
     * @param group The group
     * @return The panning offset in the range of [-1..1], 0 if there is none
     */
    private static double getGroupPanningOffset (final IGroup group)
    {
        final double groupPanning = Math.clamp (group.getPanning (), -1.0, 1.0);
        if (groupPanning != 0)
            return groupPanning;

        // The detectors flatten the panning of a group into each of its zones, therefore a layer
        // which the source panned as a whole arrives as a set of zones which all carry the same
        // panning. Since the device ignores the panning of the single entries of a sample map,
        // such a layer only stays where the source put it if it becomes the panning of the
        // oscillator which plays it
        final List<ISampleZone> zones = group.getSampleZones ();
        if (zones.isEmpty ())
            return 0;
        final double panning = zones.get (0).getPanning ();
        for (final ISampleZone zone: zones)
            if (Math.abs (zone.getPanning () - panning) > 0.0001)
                return 0;
        return Math.clamp (panning, -1.0, 1.0);
    }


    /**
     * Convert the depth of a pitch modulation of the model into the amount of a modulation matrix
     * slot. The depth of the model covers {@link IEnvelope#MAX_ENVELOPE_DEPTH} cent, while the
     * device squares the amount of a pitch destination, see
     * {@link WaldorfQpatModulationMatrix#PITCH_RANGE} - a modulation which asks for more than the
     * device can pitch is written at the end of its range.
     *
     * @param depth The modulation depth in the range of [-1..1]
     * @return The amount in the range of [-1..1]
     */
    private static double convertFromPitchDepth (final double depth)
    {
        return WaldorfQpatModulationMatrix.convertSemitonesToPitchAmount (depth * IEnvelope.MAX_ENVELOPE_DEPTH / 100.0);
    }


    private static double convertFromDecibels (final double db)
    {
        if (db == Double.NEGATIVE_INFINITY)
            return 0;
        return Math.clamp (Math.pow (10, db / 40), 0, 1);
    }


    private static double convertFromDelayTime (final double y)
    {
        return Math.sqrt (y / 2);
    }


    /**
     * A very short attack or release of the amplitude envelope opens or closes the VCA so fast that
     * it clicks on note-on/off for a sample which does not start or end at a zero crossing. Such a
     * time is lifted to the shortest length which renders without a click (0.01 seconds, verified
     * on Iridium hardware); a genuine zero stays instant. Only the amplitude envelope gates the
     * VCA, so a short filter or pitch envelope stage is left unchanged.
     *
     * @param declick True to lift the stage to the shortest audible length
     * @param seconds The envelope stage time in seconds
     * @return The de-clicked time in seconds
     */
    private static double declickAmpTime (final boolean declick, final double seconds)
    {
        return declick && seconds > 0 ? Math.max (seconds, DECLICK_SECONDS) : seconds;
    }


    /**
     * Test whether the audio of any zone starts with a step which is large enough to be heard as a
     * click when the amplitude envelope opens the VCA instantly. Only such a source is worth the
     * loss of its attack transient, which lifting the stage to the shortest length of the device
     * costs. The step is measured against the peak level of the same audio, so it does not depend
     * on the bit resolution, and it uses the ratio at which a step becomes audible at a loop wrap.
     *
     * @param groups The groups of the multi-sample
     * @return True if a zone starts with an audible step or its audio could not be read
     */
    private static boolean startsWithAudibleStep (final List<IGroup> groups)
    {
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int [] signal;
                try
                {
                    signal = LoopZeroSnapper.readMonoSignal (zone);
                }
                catch (final IOException | UnsupportedAudioFileException _)
                {
                    // The audio cannot be judged, therefore keep the variant which cannot click
                    return true;
                }
                if (signal.length == 0)
                    continue;

                int peak = 0;
                for (final int value: signal)
                    peak = Math.max (peak, Math.abs (value));
                if (peak == 0)
                    continue;

                final int start = Math.clamp (zone.getStart (), 0, signal.length - 1);
                if (Math.abs (signal[start]) > peak * AUDIBLE_STEP_RATIO)
                    return true;
            }
        return false;
    }


    /**
     * Get the release time of an amplitude envelope, which always needs to be long enough to be
     * audible. A release of zero gates the VCA off in one sample, and the waveform is cut wherever
     * it happens to stand - which is at full level for a looped zone, so the preset clicks on every
     * key release. Unlike the attack, which starts from silence at the beginning of the sample, a
     * release always has a sounding waveform to fade out, so the minimum is applied even when the
     * source leaves the release unset - that lands here as zero after clamping.
     *
     * @param isAmplitude True if this is the amplitude envelope
     * @param seconds The release time in seconds
     * @return The release time to write
     */
    private static double declickAmpRelease (final boolean isAmplitude, final double seconds)
    {
        return isAmplitude ? Math.max (seconds, DECLICK_SECONDS) : seconds;
    }


    /**
     * Convert the time of an envelope stage into the value of its parameter. The sound engine plays
     * the value x of an attack, a decay or a release as 60 x 10^(3 (x - 1)) - 0.06 seconds: 0 is
     * instant and 1 is 59.94 seconds. The display of the device shows the same curve minus 0.001
     * seconds instead, which is 59 ms longer than what is played. Measured on an Iridium MK2 with
     * OS 4.0.6 with linear pitch envelopes: stages written for 0.07, 0.1, 0.25, 0.5, 1 and 2
     * seconds with the display law took 0.011, 0.041, 0.190, 0.441, 0.940 and 1.940 seconds, alike
     * for the attack, the decay and the release.
     *
     * @param seconds The time in seconds
     * @return The parameter value in the range of [0..1]
     */
    private static double convertFromTime (final double seconds)
    {
        if (seconds <= 0)
            return 0;
        return Math.clamp (Math.log (1.0 + seconds / ENVELOPE_TIME_OFFSET) / Math.log (1000), 0, 1);
    }


    private static String formatMapDouble (final double value)
    {
        return String.format (Locale.US, "%.8f", Double.valueOf (value));
    }


    /**
     * Get the start and end of a loop as the positions of the sample which is written. The sample
     * map is created before the samples are converted to the target sample rate. A loop which is
     * kept intact by the conversion is not simply scaled, its position and the length of the
     * converted sample are calculated like the conversion does, see
     * AbstractCreator#recalculateSamplePositions.
     *
     * @param zone The zone which plays the loop
     * @param loop The loop
     * @param audioMetadata The metadata of the sample before its conversion
     * @param targetSampleRate The sample rate to which the sample is converted, -1 if it keeps its
     *            sample rate
     * @return The formatted start and end of the loop, see formatMapPosition
     */
    private static String [] getLoopPositions (final ISampleZone zone, final ISampleLoop loop, final IAudioMetadata audioMetadata, final int targetSampleRate)
    {
        final int numberOfSamples = audioMetadata.getNumberOfSamples ();
        final int sampleRate = audioMetadata.getSampleRate ();
        if (targetSampleRate > 0 && sampleRate != targetSampleRate)
        {
            final Optional<ISampleLoop> resampledLoop = AudioSampleReducer.getResampledLoop (zone.getLoops (), numberOfSamples);
            if (resampledLoop.isPresent () && resampledLoop.get () == loop)
            {
                final int [] positions = SincResampler.mapLoop (loop.getStart (), loop.getEnd (), sampleRate, targetSampleRate);
                final double length = SincResampler.getLength (numberOfSamples, loop.getStart (), loop.getEnd (), sampleRate, targetSampleRate);
                return new String []
                {
                    formatMapPosition (positions[0], length),
                    formatMapPosition (positions[1], length)
                };
            }
        }
        return new String []
        {
            formatMapPosition (loop.getStart (), numberOfSamples),
            formatMapPosition (loop.getEnd (), numberOfSamples)
        };
    }


    /**
     * Format a frame position as the fraction of the sample which the device plays as exactly this
     * frame. The firmware (Iridium MK2 4.0.5) converts a fraction into a frame in single precision:
     * (int) (0.001f + (float) (frames - 1) x fraction), so 1.0 is the last frame, and it writes the
     * fraction of an imported sample loop as frame / (frames - 1). Dividing by the number of frames
     * instead places nearly every position one frame early on the device, and even frame / (frames
     * - 1) lands one frame early when the single precision product falls marginally below the
     * frame. The fraction therefore points to the middle of the frame, which the conversion hits
     * exactly for every sample shorter than about 7.5 million frames.
     *
     * @param frame The index of the frame
     * @param numSampleFrames The number of frames of the sample
     * @return The formatted fraction
     */
    private static String formatMapPosition (final double frame, final double numSampleFrames)
    {
        if (numSampleFrames <= 1 || frame <= 0)
            return formatMapDouble (0);
        if (frame >= numSampleFrames - 1)
            return formatMapDouble (1);
        return formatMapDouble ((frame + 0.5) / (numSampleFrames - 1));
    }


    private static String formatSeconds (final double seconds)
    {
        return String.format (Locale.US, "%.2f secs", Double.valueOf (seconds));
    }


    /**
     * Hands out the slots of the modulation matrix of one layer, from the first one on. The device
     * has 40 slots and this application writes at most seven of them, in the order in which a
     * player looks for them: the modulation wheel first, then the pitch envelopes of the
     * oscillators, the vibrato, the tremolo and the modulation of the filter cutoff by a low
     * frequency oscillator. A patch therefore shows its modulations at the top of its matrix page
     * without gaps, and the remaining slots stay free for the user. The reader does not depend on
     * this order, it looks for each modulation by its source and destination in all slots.
     */
    private static class MatrixSlots
    {
        private int next = 1;


        /**
         * Take the next free slot.
         *
         * @return The index of the slot [1..40]
         */
        int take ()
        {
            return this.next++;
        }
    }
}