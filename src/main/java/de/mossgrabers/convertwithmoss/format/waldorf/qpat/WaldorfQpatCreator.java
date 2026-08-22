// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.waldorf.qpat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.sound.sampled.UnsupportedAudioFileException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.LoopZeroSnapper;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
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
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.StringUtils;


/**
 * Creator for Waldorf Quantum/Iridium files.
 *
 * @author Jürgen Moßgraber
 */
public class WaldorfQpatCreator extends AbstractWavCreator<WaldorfQpatCreatorUI>
{
    private static final String                                TAG_ACTIVE             = "Active";
    private static final String                                TAG_DELAY              = "Delay";
    private static final String                                TAG_ATTACK             = "Attack";
    private static final String                                TAG_DECAY              = "Decay";
    private static final String                                TAG_PERCUSSIVE         = "Percussive";

    private static final String                                AMP_ENV                = "AmpEnv";

    private static final String                                SLOPE_RC               = "RC";
    private static final String                                SLOPE_LINEAR           = "Lin";
    private static final String                                SLOPE_EXP              = "Exp";
    private static final String                                SLOPE_EXP_ALT          = "Exp alt";

    private static final int                                   PRESET_VERSION         = 14;

    /** The size of the header of a patch, which every layer of a patch has as well. */
    private static final int                                   HEADER_SIZE            = 512;
    /** The size of one parameter record: the value plus the name and the hint. */
    private static final int                                   PARAMETER_SIZE         = 4 + 2 * WaldorfQpatConstants.MAX_STRING_LENGTH;
    /** The number of oscillators of one layer, each of which can play one sample map. */
    private static final int                                   MAX_OSCILLATORS        = 3;
    /**
     * The maximum number of layers which is written. The MK2 generation of the instruments stores
     * four layers, but that layer count has only ever been observed in files of the format version
     * 15, while two layers are stored the same way from the version 8 on - so a patch with two
     * layers plays on every instrument of the family.
     */
    private static final int                                   MAX_LAYERS             = 2;
    /** Layer count: two layers, the file offset of the 2nd one is stored at 432. */
    private static final int                                   LAYER_COUNT_TWO        = 1;
    /** The header holds the file offsets of the layers 2, 3 and 4. */
    private static final int                                   NUM_LAYER_OFFSETS      = 3;
    /**
     * TimbreMode: [2] - all active layers sound simultaneously over the whole keyboard range. The
     * parameter of the device is labelled "Layered"; the manual of the MK2 calls the page which
     * holds it "Multi" and offers the round-robin variants next to it in MultiAllocMode. The
     * device has no velocity range for a layer, therefore this is the only mode in which all the
     * layers of a converted multi-sample can be heard.
     */
    private static final float                                 TIMBRE_MODE_MULTI      = 2.0f;

    /** What the import screen of an Iridium MK2 can show of a file name, minus a small margin. */
    private static final int                                   FILE_NAME_BUDGET       = 40;
    /** The length of the '.qpat' file ending. */
    private static final int                                   FILE_ENDING_LENGTH     = 5;
    /** The length of the import number prefix, e.g. '05002-'. */
    private static final int                                   NUMBER_PREFIX_LENGTH   = 6;
    private static final WaldorfQpatResourceHeader             EMPTY_RESOURCE_HEADER  = new WaldorfQpatResourceHeader ();
    /** The shortest amplitude attack/release which the device renders without a click. */
    private static final double                                DECLICK_SECONDS        = 0.07;
    /** The share of the peak level at which a step in the audio becomes audible as a click. */
    private static final double                                AUDIBLE_STEP_RATIO     = 0.02;

    /**
     * The modulation matrix slot which routes the low frequency oscillator of the vibrato. The
     * slots 1-3 are already used for the pitch envelopes of the 3 oscillators, see
     * {@link #createPitchEnvelopeModulator(List, IEnvelopeModulator, int)}.
     */
    private static final int                                   MATRIX_SLOT_VIBRATO    = 4;
    /** The modulation matrix slot which routes the low frequency oscillator of the tremolo. */
    private static final int                                   MATRIX_SLOT_TREMOLO    = 5;
    /** The low frequency oscillator which plays the vibrato. */
    private static final int                                   LFO_VIBRATO            = 1;
    /** The low frequency oscillator which plays the tremolo. */
    private static final int                                   LFO_TREMOLO            = 2;
    /** MatrixSrc: [7] "LFO 1" [8] "LFO 2" [9] "LFO 3" [10] "LFO 4" [11] "LFO 5" [12] "LFO 6". */
    private static final int                                   MATRIX_SRC_FIRST_LFO   = 7;
    /** MatrixDst: [1] "Pitch" - the pitch of all three oscillators at once. */
    private static final int                                   MATRIX_DST_PITCH       = 1;
    /** MatrixDst: [117] "VCA" - the amplifier of the voice. */
    private static final int                                   MATRIX_DST_VCA         = 117;
    /** The pitch which one modulation matrix slot can reach, in semi-tones. */
    private static final double                                MATRIX_PITCH_RANGE     = 24.0;
    /**
     * The lowest rate of a low frequency oscillator in Hertz, which is one cycle in 240 seconds.
     */
    private static final double                                LFO_MINIMUM_RATE       = 1.0 / 240.0;
    /** The highest rate of a low frequency oscillator in Hertz. */
    private static final double                                LFO_MAXIMUM_RATE       = 100.0;
    /** The longest delay of a low frequency oscillator in seconds. */
    private static final double                                LFO_MAXIMUM_DELAY      = 20.0;
    /** The longest attack (fade-in) of a low frequency oscillator in seconds. */
    private static final double                                LFO_MAXIMUM_ATTACK     = 10.0;
    /** From this phase on the device runs the low frequency oscillator freely. */
    private static final double                                LFO_FREE_PHASE         = 0.9986;
    /** LfoXShape: the waveforms of a low frequency oscillator in the order of the device. */
    private static final String []                             LFO_SHAPES             = new String []
    {
        "Sine",
        "Triangle",
        "Square",
        "Saw (down)",
        "Saw (up)",
        "S&H"
    };

    private static final DestinationAudioFormat                OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        16
    }, 44100, true);
    private static final DestinationAudioFormat                DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat ();

    private static final Map<Integer, WaldorfQpatResourceType> TYPE_LOOKUP            = HashMap.newHashMap (3);
    static
    {
        TYPE_LOOKUP.put (Integer.valueOf (0), WaldorfQpatResourceType.USER_SAMPLE_MAP1);
        TYPE_LOOKUP.put (Integer.valueOf (1), WaldorfQpatResourceType.USER_SAMPLE_MAP2);
        TYPE_LOOKUP.put (Integer.valueOf (2), WaldorfQpatResourceType.USER_SAMPLE_MAP3);
    }

    /**
     * The categories for which the device uses a different word than this application. Everything
     * else - Bass, Pad, Organ, Piano, Strings, Synth, Vocal, Lead, Drum, FX, Pipe, Winds, Pluck,
     * Brass, Drone, World, Chromatic Percussion - is spelled identically in the factory sound sets
     * and is written unchanged.
     */
    private static final Map<String, String> ATTRIBUTE_NAMES = HashMap.newHashMap (14);
    static
    {
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_KEYBOARD, "Keys");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_BELL, "Bells");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_PERCUSSION, TAG_PERCUSSIVE);
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_LOOPS, "Loop");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_ACOUSTIC_DRUM, "Drum");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_MONOSYNTH, "Monophon");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_ORCHESTRAL, "Cinematic");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_ENSEMBLE, "Strings");
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_DESTRUCTION, "Experimental");
        // The device has one attribute for all drum sounds which are not a full kit
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_HI_HAT, TAG_PERCUSSIVE);
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_KICK, TAG_PERCUSSIVE);
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_SNARE, TAG_PERCUSSIVE);
        ATTRIBUTE_NAMES.put (TagDetector.CATEGORY_CLAP, TAG_PERCUSSIVE);
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
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        // The name which the device displays. The file name normally keeps the full name of the
        // source instead, which tells the presets of different banks apart on the computer, but is
        // longer than what the import screen of the device can show
        final String deviceName = this.createDeviceName (multisampleSource);
        final String sampleName = FileUtils.createSafeFilename (this.settingsConfiguration.useShortFileNames () ? this.limitToFileNameBudget (deviceName) : multisampleSource.getName ());
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

        final String relativeSamplePath = "samples/" + sampleName;

        final List<List<IGroup>> layers = distributeToLayers (splitLayers (this.combineSplitStereo (multisampleSource)), this.settingsConfiguration.getMaximumLayers ());
        final List<IGroup> groups = new ArrayList<> ();
        for (final List<IGroup> layerGroups: layers)
            groups.addAll (layerGroups);
        multisampleSource.setGroups (groups);
        if (layers.size () > 1)
            this.notifier.log ("IDS_QPAT_NOTIFY_LAYERS", Integer.toString (layers.size ()), Integer.toString (groups.size ()));

        this.storeMultisample (multisampleSource, multiFile, layers, relativeSamplePath, deviceName);

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeSamplePath);
        safeCreateDirectory (sampleFolder);

        final boolean doLimit = this.settingsConfiguration.limitTo16441 ();
        if (doLimit)
            recalculateSamplePositions (multisampleSource, 44100);
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
     * @param groups The pre-processed groups
     * @param relativeSamplePath The relative sample path
     * @param deviceName The name to write into the name field, which the device displays
     * @throws IOException Could not store the file
     */
    private void storeMultisample (final IMultisampleSource multisampleSource, final File multiFile, final List<List<IGroup>> layers, final String relativeSamplePath, final String deviceName) throws IOException
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
        final int layerCount = numLayers == 1 ? 0 : LAYER_COUNT_TWO;

        // The content of every layer has to be known before the first one can be written, since
        // the header holds the file offset of the following layer
        final List<List<WaldorfQpatParameter>> layerParameters = new ArrayList<> ();
        final List<List<byte []>> layerSampleMaps = new ArrayList<> ();
        final int [] layerSizes = new int [numLayers];
        for (int i = 0; i < numLayers; i++)
        {
            final List<IGroup> groups = layers.get (i);
            // A zero-attack/zero-decay amplitude envelope that sustains below full level makes the
            // device pop at the start of each note: it snaps to the 100% attack peak and then
            // instantly drops to the sustain level. Such an envelope is meant to be flat, so write
            // a full sustain and fold the sustain level into the zone gain instead.
            final double ampGainFold = computeFlatAmpEnvelopeLevel (groups);
            final List<WaldorfQpatParameter> parameters = createParameters (groups, ampGainFold < 1.0, numLayers > 1);
            final List<byte []> sampleMaps = new ArrayList<> ();
            for (final String sampleMap: createSampleMaps (groups, relativeSamplePath, ampGainFold))
                sampleMaps.add (sampleMap.getBytes ());
            layerParameters.add (parameters);
            layerSampleMaps.add (sampleMaps);

            int size = HEADER_SIZE + parameters.size () * PARAMETER_SIZE;
            for (final byte [] sampleMap: sampleMaps)
                size += sampleMap.length;
            layerSizes[i] = size;
        }

        // The absolute file offsets of the layers 2, 3 and 4; a layer which is not stored keeps 0
        final int [] layerOffsets = new int [NUM_LAYER_OFFSETS];
        for (int i = 0; i < numLayers - 1; i++)
            layerOffsets[i] = layerSizes[i];

        try (final FileOutputStream out = new FileOutputStream (multiFile))
        {
            for (int i = 0; i < numLayers; i++)
                writeLayer (out, metadata, deviceName, layerParameters.get (i), layerSampleMaps.get (i), layerCount, numLayers == 1 ? 0 : (int) TIMBRE_MODE_MULTI, layerOffsets);
        }
    }


    /**
     * Write one layer of the patch: its header, its parameters and its sample maps. A layer is a
     * complete patch of its own; the layers of a patch are simply stored one after the other.
     *
     * @param out The output stream to write to
     * @param metadata The metadata of the multi-sample
     * @param deviceName The name to write into the name field, which the device displays
     * @param parameters The parameters of the layer
     * @param sampleMaps The sample maps of the layer
     * @param layerCount The number of layers of the patch, coded as the device does
     * @param timbreMode The mode in which the layers are combined
     * @param layerOffsets The absolute file offsets of the layers 2, 3 and 4
     * @throws IOException Could not write the layer
     */
    private static void writeLayer (final OutputStream out, final IMetadata metadata, final String deviceName, final List<WaldorfQpatParameter> parameters, final List<byte []> sampleMaps, final int layerCount, final int timbreMode, final int [] layerOffsets) throws IOException
    {
        writeHeader (out, metadata, deviceName);

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
     * Distribute the groups across the layers of the patch. Each layer plays up to 3 groups, one
     * on each of its oscillators, so a patch reaches 3 groups with one layer and 6 with two.
     * Groups which do not fit into the available layers are added to the last group, as they are
     * when only one layer is written.
     * <p>
     * The layers are combined in the Multi mode, in which all of them sound over the whole
     * keyboard range: the device can split its layers by key or cycle them, but it has no velocity
     * range for a layer, so a velocity split has to stay inside the sample maps - which is where
     * the splitting of the source put it, since only zones which sound at the same time are
     * separated into layers.
     *
     * @param groups The groups
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

        // The minimum representable envelope time is 0.06 seconds; anything at or below that is
        // written as an instant stage (see convertFromTime).
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
     * @return The sample maps
     * @throws IOException Could not read the necessary audio metadata of a sample
     */
    private static List<String> createSampleMaps (final List<IGroup> groups, final String relativeSamplePath, final double gainFactor) throws IOException
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

            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (!sb.isEmpty ())
                    sb.append ('\n');

                final Optional<ISampleData> sampleData = zone.getSampleData ();
                if (sampleData.isEmpty ())
                    throw new IOException ("Empty sample data in zone: " + zone.getName ());
                final double numSampleFrames = sampleData.get ().getAudioMetadata ().getNumberOfSamples ();

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
                sb.append ('"').append (relativeSamplePath).append ('/').append (FileUtils.createSafeFilename (zone.getName ())).append (".wav\"\t");

                // Pitch - tuning needs to be subtracted since the sample plays high if the root
                // note is lower!
                sb.append (formatMapDouble (zone.getKeyRoot () - zone.getTuning ())).append ('\t');

                // FromNote / ToNote
                sb.append (zone.getKeyLow ()).append ('\t').append (zone.getKeyHigh ()).append ('\t');

                // Gain
                final double v = Math.clamp (zone.getGain () - gainOffset, Double.NEGATIVE_INFINITY, 20);
                sb.append (formatMapDouble (Math.pow (10, v / 20) * gainFactor)).append ('\t');

                // FromVelo / ToVelo
                sb.append (zone.getVelocityLow ()).append ('\t').append (zone.getVelocityHigh ()).append ('\t');

                // Pan - CURRENTLY IGNORED
                sb.append (formatMapDouble (Math.clamp ((zone.getPanning () - panningOffset + 1.0) / 2.0, 0, 1))).append ('\t');

                // Start / End - a zone whose start/stop was never set keeps the model default of
                // -1, which would otherwise be written as a negative position (the device then
                // shows a sample start/end of -1). Treat an unset start/stop as the full sample.
                final double startFrame = zone.getStart () < 0 ? 0 : zone.getStart ();
                final double stopFrame = zone.getStop () <= 0 ? numSampleFrames : zone.getStop ();
                sb.append (formatMapPosition (startFrame, numSampleFrames)).append ('\t');
                sb.append (formatMapPosition (stopFrame, numSampleFrames)).append ('\t');

                // Loop mode, start, stop
                final List<ISampleLoop> loops = zone.getLoops ();
                if (loops.isEmpty ())
                    sb.append ("0\t0\t").append (formatMapPosition (stopFrame, numSampleFrames)).append ('\t');
                else
                {
                    final ISampleLoop loop = loops.get (0);
                    sb.append (loop.getType () == LoopType.ALTERNATING ? 2 : 1).append ('\t');
                    sb.append (formatMapPosition (loop.getStart (), numSampleFrames)).append ('\t');
                    sb.append (formatMapPosition (loop.getEnd (), numSampleFrames)).append ('\t');
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

            sampleMaps.add (sb.toString ());
        }

        return sampleMaps;
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
                final DefaultGroup layerGroup = new DefaultGroup (group.getName ());
                layerGroup.setTrigger (group.getTrigger ());
                // All zones of a layer stem from the same group, therefore they all carry the same
                // flattened offsets and the group offsets stay valid for each layer.
                layerGroup.setGain (group.getGain ());
                layerGroup.setPanning (group.getPanning ());
                layerGroup.setTuning (group.getTuning ());
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
        final boolean keyOverlap = limitToDefault (a.getKeyLow (), 0) <= limitToDefault (b.getKeyHigh (), 127) && limitToDefault (b.getKeyLow (), 0) <= limitToDefault (a.getKeyHigh (), 127);
        final boolean velocityOverlap = limitToDefault (a.getVelocityLow (), 1) <= limitToDefault (b.getVelocityHigh (), 127) && limitToDefault (b.getVelocityLow (), 1) <= limitToDefault (a.getVelocityHigh (), 127);
        return keyOverlap && velocityOverlap;
    }


    private static List<WaldorfQpatParameter> createParameters (final List<IGroup> groups, final boolean flattenAmpEnvelope, final boolean isMultiLayer)
    {
        final List<WaldorfQpatParameter> parameters = new ArrayList<> ();

        if (isMultiLayer)
        {
            // All layers sound simultaneously over the whole keyboard range
            parameters.add (new WaldorfQpatParameter ("TimbreMode", "Layered", TIMBRE_MODE_MULTI));
            parameters.add (new WaldorfQpatParameter ("MultiAllocMode", "Layered", 0));
            parameters.add (new WaldorfQpatParameter ("LayerActive", "Active", 1));
        }

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

            // Osc1PitchBendRange: [0..48] ~ [-24..24]
            final int pitchbend = Math.clamp (Math.round (firstZone.getBendUp () / 100.0), -24, 24);
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "PitchBendRange", (pitchbend < 0 ? "-" : "+") + pitchbend, pitchbend + 24.0f));

            // Osc1Keytrack: [0..1] ~ [-200..200] - already set in the sample maps
            parameters.add (new WaldorfQpatParameter ("Osc" + groupIndex + "Keytrack", "+100.0", 0.75f));

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

            createPitchEnvelopeModulator (parameters, firstZone.getPitchEnvelopeModulator (), i + 1);

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

                createLfoModulators (parameters, firstZone);
            }
        }

        return parameters;
    }


    private static void createPitchEnvelopeModulator (final List<WaldorfQpatParameter> parameters, final IEnvelopeModulator pitchEnvelopeModulator, final int oscIndex)
    {
        // Use the matrix slots 1-3 and free envelopes 1-3 for the respective oscillator 1-3
        // modulation
        final double depth = pitchEnvelopeModulator.getDepth ();
        if (depth == 0)
            return;

        // MatrixOnOffX: [0] "Disabled" [1] "Active"
        parameters.add (new WaldorfQpatParameter ("MatrixOnOff" + oscIndex, TAG_ACTIVE, 1.0f));

        // MatrixSrcX: [4] "Free Env1" [5] "Free Env2" [6] "Free Env3"
        parameters.add (new WaldorfQpatParameter ("MatrixSrc" + oscIndex, "Free Env" + oscIndex, oscIndex + 3.0f));

        // MatrixDstX: [2] "Osc1 Pitch" [3] "Osc2 Pitch" [4] "Osc3 Pitch"
        parameters.add (new WaldorfQpatParameter ("MatrixDst" + oscIndex, "Osc" + oscIndex + " Pitch", oscIndex + 1.0f));

        // MatrixAmountX: [0.00] "-100.00 %" ... [1.00] "+100.00 %"
        final double amount = convertFromPitchDepth (depth);
        parameters.add (new WaldorfQpatParameter ("MatrixAmount" + oscIndex, StringUtils.formatPercent (amount, 2), (float) ((amount + 1.0) / 2.0)));

        final String prefix = "FreeEnv" + oscIndex;
        createEnvelope (parameters, pitchEnvelopeModulator.getSource (), prefix, prefix, false, false);
    }


    /**
     * Create the parameters of the vibrato and of the tremolo. The device has 6 low frequency
     * oscillators and 40 modulation matrix slots, of which this application only ever writes the
     * slots 1-5: the slots 1-3 carry the pitch envelope of the respective oscillator, therefore the
     * vibrato takes the slot 4 and the tremolo the slot 5. Nothing has to give way for them and the
     * slots 6-40 as well as the LFOs 3-6 stay free for the user.
     * <p>
     * The vibrato modulates the destination "Pitch", which is the pitch of all three oscillators at
     * once. This costs one slot instead of one slot per oscillator and matches a vibrato of a
     * multi-sample, which is a property of the whole instrument and not of a single layer.
     *
     * @param parameters Where to add the parameters
     * @param zone The zone which carries the modulators
     */
    private static void createLfoModulators (final List<WaldorfQpatParameter> parameters, final ISampleZone zone)
    {
        // Vibrato - the pitch swings around the played note, therefore the LFO stays bipolar
        final ILfoModulator pitchLfoModulator = zone.getPitchLfoModulator ();
        final ILfo pitchLfo = pitchLfoModulator.getSource ();
        final double pitchDepth = pitchLfoModulator.getDepth ();
        if (pitchDepth != 0 && pitchLfo.isSet ())
        {
            // The depth of the model covers IEnvelope#MAX_ENVELOPE_DEPTH cent, one matrix slot
            // reaches MATRIX_PITCH_RANGE semi-tones
            final double semitones = pitchDepth * IEnvelope.MAX_ENVELOPE_DEPTH / 100.0;
            final double amount = Math.clamp (semitones / MATRIX_PITCH_RANGE, -1.0, 1.0);
            createModulationMatrixEntry (parameters, MATRIX_SLOT_VIBRATO, LFO_VIBRATO, "Pitch", MATRIX_DST_PITCH, amount);
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
            createModulationMatrixEntry (parameters, MATRIX_SLOT_TREMOLO, LFO_TREMOLO, "VCA", MATRIX_DST_VCA, amount);
            createLfo (parameters, amplitudeLfo, LFO_TREMOLO, true);
        }
    }


    /**
     * Activate one slot of the modulation matrix.
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
        // MatrixOnOffX: [0] "Disabled" [1] "Active"
        parameters.add (new WaldorfQpatParameter ("MatrixOnOff" + slot, TAG_ACTIVE, 1.0f));

        // MatrixSrcX: [7] "LFO 1" ... [12] "LFO 6"
        parameters.add (new WaldorfQpatParameter ("MatrixSrc" + slot, "LFO " + lfoIndex, (MATRIX_SRC_FIRST_LFO + lfoIndex - 1)));

        // MatrixDstX: [1] "Pitch" ... [117] "VCA"
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
        final double cutoff = Math.log (filter.getCutoff () / 8.1758) / (Math.log (2) * 11.25);
        parameters.add (new WaldorfQpatParameter ("Filter1CutOff", StringUtils.formatDouble (cutoff, 4, " Hz"), (float) cutoff));

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

        if (isPitch && envelope.getStartLevel () != 0)
        {
            // xxxEnvDelay
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DELAY, formatSeconds (0), 0));
            // xxxEnvAttack
            parameters.add (new WaldorfQpatParameter (prefix + TAG_ATTACK, formatSeconds (0), 0));
            // xxxEnvDecay
            final double decayTime = Math.clamp (envelope.getAttackTime (), 0, 60);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DECAY, formatSeconds (decayTime), (float) convertFromTime (decayTime)));
        }
        else
        {
            // xxxEnvDelay
            final double delayTime = Math.clamp (envelope.getDelayTime (), 0, 2);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DELAY, formatSeconds (delayTime), (float) convertFromDelayTime (delayTime)));
            // xxxEnvAttack
            final double attackTime = declickAmpTime (isAmplitude && !allowInstantAttack, Math.clamp (envelope.getAttackTime (), 0, 60));
            parameters.add (new WaldorfQpatParameter (prefix + TAG_ATTACK, formatSeconds (attackTime), (float) convertFromTime (attackTime)));
            // xxxEnvDecay
            final double decayTime = Math.clamp (Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ()), 0, 60);
            parameters.add (new WaldorfQpatParameter (prefix + TAG_DECAY, formatSeconds (decayTime), (float) convertFromTime (decayTime)));
        }

        // xxxEnvRelease
        final double releaseTime = declickAmpRelease (isAmplitude, Math.clamp (envelope.getReleaseTime (), 0, 60));
        parameters.add (new WaldorfQpatParameter (prefix + "Release", formatSeconds (releaseTime), (float) convertFromTime (releaseTime)));

        // xxxEnvSustain - a flattened amplitude envelope sustains at full level; its level is
        // folded into the zone gain instead (see computeFlatAmpEnvelopeLevel)
        double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel == -1)
            sustainLevel = isPitch ? 0 : 1;
        if (flattenSustain)
            sustainLevel = 1;
        parameters.add (new WaldorfQpatParameter (prefix + "Sustain", StringUtils.formatPercent (sustainLevel, 2), (float) sustainLevel));

        if (isPitch && envelope.getStartLevel () != 0)
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
                decaySlopeValue = 0.5;
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
                decaySlopeValue = 0.5;
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
                releaseSlopeValue = 0.5;
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
     * @throws IOException Could not write
     */
    private static void writeHeader (final OutputStream out, final IMetadata metadata, final String name) throws IOException
    {
        StreamUtils.writeUnsigned32 (out, WaldorfQpatConstants.MAGIC, false);
        StreamUtils.writeUnsigned32 (out, PRESET_VERSION, false);
        StreamUtils.writeAscii (out, StringUtils.fixASCII (name), WaldorfQpatConstants.MAX_STRING_LENGTH);
        // The author (offset 40) and bank (offset 72) fields are shown by the device. Use the
        // explicit creator settings when provided, otherwise fall back to the source metadata.
        StreamUtils.writeAscii (out, StringUtils.fixASCII (metadata.getCreator ()), WaldorfQpatConstants.MAX_STRING_LENGTH);
        StreamUtils.writeAscii (out, StringUtils.fixASCII (metadata.getDescription ()).replace ('\r', ' ').replace ('\n', ' '), WaldorfQpatConstants.MAX_STRING_LENGTH);

        writeAttributes (out, metadata);
    }


    /**
     * Write the four attributes of a patch. The device lists them next to the name and filters the
     * patches by them, therefore the category is translated into the wording which the factory
     * sound sets use - otherwise e.g. 'Keyboard' ends up in the filter list next to the 'Keys' of
     * every other patch and both only find half of the sounds. A category which was not detected is
     * left out instead of filling the filter list with the word 'Unknown'.
     *
     * @param out The output stream to write to
     * @param metadata The metadata
     * @throws IOException Could not write
     */
    private static void writeAttributes (final OutputStream out, final IMetadata metadata) throws IOException
    {
        final List<String> attributes = new ArrayList<> ();
        final String category = metadata.getCategory ();
        addAttribute (attributes, ATTRIBUTE_NAMES.getOrDefault (category, category));
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
        if (attribute == null || attribute.isBlank () || attributes.size () >= 4 || TagDetector.CATEGORY_UNKNOWN.equals (attribute))
            return;
        for (final String present: attributes)
            if (present.equalsIgnoreCase (attribute))
                return;
        attributes.add (attribute);
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
     * slot. The depth of the model covers {@link IEnvelope#MAX_ENVELOPE_DEPTH} cent, while one slot
     * of the matrix reaches {@link #MATRIX_PITCH_RANGE} semi-tones - a modulation which asks for
     * more than the device can pitch is written at the end of its range.
     *
     * @param depth The modulation depth in the range of [-1..1]
     * @return The amount in the range of [-1..1]
     */
    private static double convertFromPitchDepth (final double depth)
    {
        final double semitones = depth * IEnvelope.MAX_ENVELOPE_DEPTH / 100.0;
        return Math.clamp (semitones / MATRIX_PITCH_RANGE, -1.0, 1.0);
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
     * The device plays an envelope stage with parameter value 0 as instant. For the amplitude
     * envelope a non-zero attack or release shorter than the ~0.06 second minimum would otherwise
     * collapse to instant and click on note-on/off for a sample that does not start or end at a
     * zero crossing. Clamp such a time up to the shortest audible length (0.07 seconds, verified on
     * Iridium hardware); a genuine zero stays instant. Only the amplitude envelope gates the VCA,
     * so a short filter or pitch envelope stage is left unchanged.
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


    private static double convertFromTime (final double y)
    {
        // The minimum representable time is 0.06 seconds (parameter value 0). Anything at or below
        // that - including a zero attack/decay/release - maps to 0. Without this guard the
        // logarithm returns negative values, and exactly 0 yields negative infinity, which would be
        // written as a corrupt float and produce e.g. a click at the start of every note.
        if (y <= 0.06)
            return 0;
        return Math.clamp (Math.log (y / 0.06) / Math.log (1000), 0, 1);
    }


    private static String formatMapDouble (final double value)
    {
        return String.format (Locale.US, "%.8f", Double.valueOf (value));
    }


    /**
     * Formats a sample position as a fraction of the number of frames of the sample. The device
     * expects positions in the range [0..1], so the fraction is clamped: source formats may
     * reference positions beyond the length of the audio data, e.g. loop points authored for the
     * original sample but stored with a lossy-compressed file which decodes to a slightly shorter
     * length.
     *
     * @param frames The position in sample frames
     * @param numSampleFrames The number of frames of the sample
     * @return The formatted position
     */
    private static String formatMapPosition (final double frames, final double numSampleFrames)
    {
        return formatMapDouble (Math.clamp (frames / numSampleFrames, 0, 1));
    }


    private static String formatSeconds (final double seconds)
    {
        return String.format (Locale.US, "%.2f secs", Double.valueOf (seconds));
    }
}