// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.synthstrom;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.file.wav.DataChunk;
import de.mossgrabers.convertwithmoss.file.wav.FormatChunk;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Creator for Synthstrom Deluge synth (sound) preset files. A preset is an XML file (placed in a
 * <i>SYNTHS</i> folder) which references its samples (placed in a <i>SAMPLES</i> folder) by a path
 * relative to the SD card root. The multi-sample is written as a single sample oscillator with one
 * sample range per zone.
 * <p>
 * Only features available on the official v4 firmware are written so that the created instruments
 * load on both the official and the community firmware. The element based file structure is used
 * which is understood by all firmware versions.
 * <p>
 * The Deluge has no loop cross-fade parameter of its own. Like the Renoise creator, a loop
 * cross-fade - set with the 'Set fixed loop-crossfade' processing option or read from the source -
 * is therefore baked into the looped sample audio. No extra option is needed for this; the
 * cross-fade is applied automatically whenever a forward loop carries one.
 *
 * @author Jürgen Moßgraber
 */
public class DelugeCreator extends AbstractWavCreator<DelugeCreatorUI>
{
    private static final String SYNTHS_FOLDER            = "SYNTHS";
    private static final String KITS_FOLDER              = "KITS";
    private static final String SAMPLES_FOLDER           = "SAMPLES";

    /** The Deluge's decay/release rate table tops out at about 5.9 seconds. */
    private static final double MAX_DELUGE_DECAY_SECONDS = 5.9;

    /** The Deluge's default kit master volume (unity gain). */
    private static final int    DEFAULT_KIT_VOLUME       = 0x3504F334;

    /** The maximum number of drums in a kit (one per note from 36 up to 127). */
    private static final int    MAX_KIT_DRUMS            = 92;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DelugeCreator (final INotifier notifier)
    {
        super ("Synthstrom Deluge", "Deluge", notifier, new DelugeCreatorUI ("Deluge"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final boolean createKit = this.settingsConfiguration.getOutputType () == DelugeCreatorUI.OutputType.KIT;

        // Both a synth and a kit use one zone per note: the synth maps that single layer across
        // the keyboard, a kit turns each note into its own drum. Velocity layers and round-robins
        // (several zones on the same note) are consolidated to the loudest layer since a Deluge
        // drum is a single sample
        final List<ISampleZone> zones = getMappedZones (multisampleSource);
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_DELUGE_NO_SAMPLES");
            return;
        }

        // A kit holds at most one drum per note (36 up to 127); drop the surplus and say so
        if (createKit && zones.size () > MAX_KIT_DRUMS)
        {
            this.notifier.logError ("IDS_DELUGE_KIT_TOO_MANY_DRUMS", Integer.toString (zones.size ()), Integer.toString (MAX_KIT_DRUMS));
            zones.subList (MAX_KIT_DRUMS, zones.size ()).clear ();
        }

        makeUniqueSampleNames (zones);

        // The Deluge resolves sample paths (SAMPLES/...) relative to the SD card root, and presets
        // live in a SYNTHS (or KITS) folder directly below that root. If the chosen output folder
        // already is that instrument folder or a sub-folder of it, write the preset there and place
        // the samples in the card-root SAMPLES folder (the same place the detector reads them
        // from). Otherwise treat the chosen folder as the card root and create the SYNTHS
        // sub-folder below.
        final Optional<File> instrumentFolderOpt = findInstrumentFolder (destinationFolder);
        final File presetFolder;
        final File cardRootFolder;
        String sampleSubPath = "";
        if (instrumentFolderOpt.isEmpty ())
        {
            presetFolder = new File (destinationFolder, createKit ? KITS_FOLDER : SYNTHS_FOLDER);
            cardRootFolder = destinationFolder;
        }
        else
        {
            final File instrumentFolder = instrumentFolderOpt.get ();
            presetFolder = destinationFolder;
            cardRootFolder = instrumentFolder.getParentFile ();
            // Mirror the preset's sub-folder path (below SYNTHS/KITS) into the SAMPLES folder so a
            // bank written to e.g. SYNTHS/ORBIT keeps its samples grouped under SAMPLES/ORBIT
            // rather than scattered directly in the SAMPLES root.
            if (!instrumentFolder.equals (destinationFolder))
                sampleSubPath = instrumentFolder.toPath ().relativize (destinationFolder.toPath ()).toString ().replace (File.separatorChar, '/') + "/";
        }

        // Create a unique preset file inside the preset folder
        safeCreateDirectory (presetFolder);
        final File presetFile = this.createUniqueFilename (presetFolder, createSafeFilename (multisampleSource.getName ()), "xml");
        final String baseName = FileUtils.getNameWithoutType (presetFile);
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile.getAbsolutePath ());

        final String relativeSampleFolder = SAMPLES_FOLDER + "/" + sampleSubPath + baseName;

        // Write the samples into <card root>/SAMPLES/<sub-path>/<name>/
        final File sampleFolder = new File (cardRootFolder, relativeSampleFolder);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, createTemporarySource (multisampleSource, zones));

        // Create and store the XML document referencing the samples by their card-relative path
        final Optional<String> metadata = createKit ? this.createKitDocument (zones, relativeSampleFolder) : this.createSoundDocument (zones, relativeSampleFolder);
        if (metadata.isEmpty ())
            return;
        try (final FileWriter writer = new FileWriter (presetFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata.get ());
        }

        this.progress.notifyDone ();
    }


    /**
     * Find the Deluge instrument folder - a folder named <i>SYNTHS</i> or <i>KITS</i> - that the
     * given output folder is located in, either the folder itself or one of its parents. The SD
     * card root is the parent of that folder. Returns null if the output folder is not inside such
     * an instrument folder, in which case the output folder is treated as the card root.
     *
     * @param folder The chosen output folder
     * @return The instrument folder or null
     */
    private static Optional<File> findInstrumentFolder (final File folder)
    {
        File current = folder;
        while (current != null)
        {
            final String name = current.getName ();
            if (SYNTHS_FOLDER.equalsIgnoreCase (name) || KITS_FOLDER.equalsIgnoreCase (name))
                return Optional.of (current);
            current = current.getParentFile ();
        }
        return Optional.empty ();
    }


    /**
     * Collect the zones to write. The Deluge synth only supports a single layer mapped across the
     * keyboard, therefore zones from all groups are flattened and - if several zones share the same
     * upper key - only the one with the highest velocity is kept.
     *
     * @param multisampleSource The multi-sample source
     * @return The zones sorted by their upper key
     */
    private static List<ISampleZone> getMappedZones (final IMultisampleSource multisampleSource)
    {
        final List<ISampleZone> allZones = new ArrayList<> ();
        for (final IGroup group: multisampleSource.getNonEmptyGroups (true))
            allZones.addAll (group.getSampleZones ());

        allZones.sort (Comparator.comparingInt ((final ISampleZone zone) -> limitToDefault (zone.getKeyHigh (), 127)).thenComparing (Comparator.comparingInt ((final ISampleZone zone) -> limitToDefault (zone.getVelocityHigh (), 127)).reversed ()));

        final List<ISampleZone> result = new ArrayList<> ();
        int lastKeyHigh = Integer.MIN_VALUE;
        for (final ISampleZone zone: allZones)
        {
            final int keyHigh = limitToDefault (zone.getKeyHigh (), 127);
            if (keyHigh == lastKeyHigh)
                continue;
            result.add (zone);
            lastKeyHigh = keyHigh;
        }
        return result;
    }


    /**
     * Make the names of the zones unique (and file system safe) so that each one is written to its
     * own sample file.
     *
     * @param zones The zones to update
     */
    private static void makeUniqueSampleNames (final List<ISampleZone> zones)
    {
        final Set<String> usedNames = new HashSet<> ();
        for (final ISampleZone zone: zones)
        {
            final String base = createSafeFilename (zone.getName ());
            String name = base;
            int counter = 2;
            while (!usedNames.add (name.toLowerCase (Locale.ENGLISH)))
                name = base + "_" + counter++;
            zone.setName (name);
        }
    }


    /**
     * Create a temporary multi-sample source containing only the given zones in a single group so
     * that exactly these zones are written by {@link #writeSamples}.
     *
     * @param multisampleSource The original multi-sample source (used for the metadata)
     * @param zones The zones to write
     * @return The temporary source
     */
    private static IMultisampleSource createTemporarySource (final IMultisampleSource multisampleSource, final List<ISampleZone> zones)
    {
        final IGroup group = new DefaultGroup ();
        for (final ISampleZone zone: zones)
            group.addSampleZone (zone);

        final IMultisampleSource source = new DefaultMultisampleSource ();
        source.setGroups (Collections.singletonList (group));

        final IMetadata sourceMetadata = multisampleSource.getMetadata ();
        final IMetadata metadata = source.getMetadata ();
        metadata.setDescription (sourceMetadata.getDescription ());
        metadata.setCreator (sourceMetadata.getCreator ());
        metadata.setCategory (sourceMetadata.getCategory ());
        metadata.setCreationDateTime (sourceMetadata.getCreationDateTime ());
        return source;
    }


    /**
     * Create the XML structure of the synth preset.
     *
     * @param zones The zones to write
     * @param relativeSampleFolder The card-relative path of the sample folder
     * @return The XML document as a string
     */
    private Optional<String> createSoundDocument (final List<ISampleZone> zones, final String relativeSampleFolder)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element soundElement = document.createElement (DelugeTag.SOUND);
        document.appendChild (soundElement);
        soundElement.setAttribute (DelugeTag.FIRMWARE_VERSION, DelugeTag.FIRMWARE_VERSION_VALUE);
        soundElement.setAttribute (DelugeTag.EARLIEST_COMPATIBLE_FIRMWARE, DelugeTag.EARLIEST_COMPATIBLE_VALUE);

        writeSound (document, soundElement, zones, relativeSampleFolder);

        return this.createXMLString (document);
    }


    /**
     * Create the XML structure of a drum kit preset. Each zone becomes one drum (a
     * {@code <sound>} inside {@code <soundSources>}); the Deluge maps the drums to ascending
     * rows in the order they are written.
     *
     * @param zones The zones to write, one drum per zone
     * @param relativeSampleFolder The card-relative path of the sample folder
     * @return The XML document as a string
     */
    private Optional<String> createKitDocument (final List<ISampleZone> zones, final String relativeSampleFolder)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element kitElement = document.createElement (DelugeTag.KIT);
        document.appendChild (kitElement);
        kitElement.setAttribute (DelugeTag.FIRMWARE_VERSION, DelugeTag.FIRMWARE_VERSION_VALUE);
        kitElement.setAttribute (DelugeTag.EARLIEST_COMPATIBLE_FIRMWARE, DelugeTag.EARLIEST_COMPATIBLE_VALUE);

        XMLUtils.addTextElement (document, kitElement, DelugeTag.LPF_MODE, DelugeTag.LPF_MODE_24DB);

        writeKitDefaultParams (document, kitElement);

        final Element soundSources = XMLUtils.addElement (document, kitElement, DelugeTag.SOUND_SOURCES);
        for (final ISampleZone zone: zones)
        {
            final Element drumSound = XMLUtils.addElement (document, soundSources, DelugeTag.SOUND);
            XMLUtils.addTextElement (document, drumSound, DelugeTag.NAME, createSafeFilename (zone.getName ()));
            writeSound (document, drumSound, Collections.singletonList (zone), relativeSampleFolder);
        }

        return this.createXMLString (document);
    }


    /**
     * Write the body of one sound (a synth preset or one drum of a kit): the sample
     * oscillator(s), a silent second oscillator and the default parameters.
     *
     * @param document The XML document
     * @param soundElement The sound element (the root sound or a drum's {@code <sound>})
     * @param zones The zones of this sound (several key ranges for a synth, a single sample for
     *            a drum)
     * @param relativeSampleFolder The card-relative path of the sample folder
     */
    private static void writeSound (final Document document, final Element soundElement, final List<ISampleZone> zones, final String relativeSampleFolder)
    {
        final boolean anyLoop = zones.stream ().anyMatch (zone -> !zone.getLoops ().isEmpty ());
        final boolean reversed = zones.stream ().anyMatch (ISampleZone::isReversed);

        // Oscillator 1 with the sample(s)
        final Element osc1 = XMLUtils.addElement (document, soundElement, DelugeTag.OSC1);
        XMLUtils.addTextElement (document, osc1, DelugeTag.TYPE, DelugeTag.TYPE_SAMPLE);
        XMLUtils.addTextElement (document, osc1, DelugeTag.LOOP_MODE, Integer.toString (anyLoop ? DelugeTag.LOOP_MODE_LOOP : DelugeTag.LOOP_MODE_ONCE));
        XMLUtils.addTextElement (document, osc1, DelugeTag.REVERSED, reversed ? "1" : "0");
        XMLUtils.addTextElement (document, osc1, DelugeTag.TIME_STRETCH_ENABLE, "0");
        XMLUtils.addTextElement (document, osc1, DelugeTag.TIME_STRETCH_AMOUNT, "0");

        if (zones.size () == 1)
            writeZone (document, osc1, zones.get (0), relativeSampleFolder, -1);
        else
        {
            final Element sampleRanges = XMLUtils.addElement (document, osc1, DelugeTag.SAMPLE_RANGES);
            for (int i = 0; i < zones.size (); i++)
            {
                final Element sampleRange = XMLUtils.addElement (document, sampleRanges, DelugeTag.SAMPLE_RANGE);
                // The last (highest) range does not store the top note - it covers everything above
                final int topNote = i == zones.size () - 1 ? -1 : limitToDefault (zones.get (i).getKeyHigh (), 127);
                writeZone (document, sampleRange, zones.get (i), relativeSampleFolder, topNote);
            }
        }

        // Silent second oscillator
        final Element osc2 = XMLUtils.addElement (document, soundElement, DelugeTag.OSC2);
        XMLUtils.addTextElement (document, osc2, DelugeTag.TYPE, DelugeTag.TYPE_SQUARE);

        XMLUtils.addTextElement (document, soundElement, DelugeTag.POLYPHONIC, DelugeTag.POLYPHONIC_POLY);
        XMLUtils.addTextElement (document, soundElement, DelugeTag.VOICE_PRIORITY, "1");
        XMLUtils.addTextElement (document, soundElement, DelugeTag.MODE, DelugeTag.MODE_SUBTRACTIVE);
        XMLUtils.addTextElement (document, soundElement, DelugeTag.LPF_MODE, DelugeTag.LPF_MODE_24DB);

        final Element unison = XMLUtils.addElement (document, soundElement, DelugeTag.UNISON);
        XMLUtils.addTextElement (document, unison, DelugeTag.UNISON_NUM, "1");
        XMLUtils.addTextElement (document, unison, DelugeTag.UNISON_DETUNE, "8");

        writeDefaultParams (document, soundElement, zones.get (0));
    }


    /**
     * Write the kit-level default parameters (the master volume, panning, filters and the send
     * effects). These are left at their neutral values; the per-drum parameters carry the
     * actual sound.
     *
     * @param document The XML document
     * @param kitElement The kit element
     */
    private static void writeKitDefaultParams (final Document document, final Element kitElement)
    {
        final Element defaultParams = XMLUtils.addElement (document, kitElement, DelugeTag.DEFAULT_PARAMS);

        final Element delay = XMLUtils.addElement (document, defaultParams, DelugeTag.DELAY);
        XMLUtils.addTextElement (document, delay, DelugeTag.DELAY_RATE, DelugeValues.formatHex (0));
        XMLUtils.addTextElement (document, delay, DelugeTag.DELAY_FEEDBACK, DelugeValues.formatHex (DelugeValues.PARAM_MIN));

        XMLUtils.addTextElement (document, defaultParams, DelugeTag.REVERB_AMOUNT, DelugeValues.formatHex (DelugeValues.PARAM_MIN));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.VOLUME, DelugeValues.formatHex (DEFAULT_KIT_VOLUME));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.PAN, DelugeValues.formatHex (0));

        final Element lpf = XMLUtils.addElement (document, defaultParams, DelugeTag.LPF);
        XMLUtils.addTextElement (document, lpf, DelugeTag.FREQUENCY, DelugeValues.formatHex (DelugeValues.PARAM_MAX));
        XMLUtils.addTextElement (document, lpf, DelugeTag.RESONANCE, DelugeValues.formatHex (0));

        final Element hpf = XMLUtils.addElement (document, defaultParams, DelugeTag.HPF);
        XMLUtils.addTextElement (document, hpf, DelugeTag.FREQUENCY, DelugeValues.formatHex (DelugeValues.PARAM_MIN));
        XMLUtils.addTextElement (document, hpf, DelugeTag.RESONANCE, DelugeValues.formatHex (DelugeValues.PARAM_MIN));
    }


    /**
     * Write the file name, pitch and zone (sample positions) for one zone.
     *
     * @param document The XML document
     * @param parent The parent element (the oscillator for a single sample or a sample range)
     * @param zone The zone
     * @param relativeSampleFolder The card-relative path of the sample folder
     * @param topNote The top note of the range or -1 if it should not be written
     */
    private static void writeZone (final Document document, final Element parent, final ISampleZone zone, final String relativeSampleFolder, final int topNote)
    {
        if (topNote >= 0)
            XMLUtils.addTextElement (document, parent, DelugeTag.RANGE_TOP_NOTE, Integer.toString (topNote));

        XMLUtils.addTextElement (document, parent, DelugeTag.FILE_NAME, relativeSampleFolder + "/" + createSafeFilename (zone.getName ()) + ".wav");

        final double rootNote = (zone.getKeyRoot () < 0 ? limitToDefault (zone.getKeyHigh (), DelugeValues.REFERENCE_NOTE) : zone.getKeyRoot ()) + zone.getTuning ();
        final int [] transposeCents = DelugeValues.transposeCentsFromRootNote (rootNote);
        XMLUtils.addTextElement (document, parent, DelugeTag.TRANSPOSE_OSC, Integer.toString (transposeCents[0]));
        XMLUtils.addTextElement (document, parent, DelugeTag.CENTS, Integer.toString (transposeCents[1]));

        final Element zoneElement = XMLUtils.addElement (document, parent, DelugeTag.ZONE);
        XMLUtils.addTextElement (document, zoneElement, DelugeTag.START_SAMPLE_POS, Integer.toString (Math.max (0, zone.getStart ())));
        XMLUtils.addTextElement (document, zoneElement, DelugeTag.END_SAMPLE_POS, Integer.toString (getEndPosition (zone)));

        final List<ISampleLoop> loops = zone.getLoops ();
        if (!loops.isEmpty ())
        {
            final ISampleLoop loop = loops.get (0);
            XMLUtils.addTextElement (document, zoneElement, DelugeTag.START_LOOP_POS, Integer.toString (Math.max (0, loop.getStart ())));
            XMLUtils.addTextElement (document, zoneElement, DelugeTag.END_LOOP_POS, Integer.toString (Math.max (0, loop.getEnd ())));
        }
    }


    /**
     * Write the default parameters (oscillator volumes, filter, amplitude envelope and the velocity
     * to volume patch cable).
     *
     * @param document The XML document
     * @param soundElement The sound element
     * @param firstZone The first zone (used for the amplitude envelope and filter)
     */
    private static void writeDefaultParams (final Document document, final Element soundElement, final ISampleZone firstZone)
    {
        final Element defaultParams = XMLUtils.addElement (document, soundElement, DelugeTag.DEFAULT_PARAMS);

        XMLUtils.addTextElement (document, defaultParams, DelugeTag.OSC_A_VOLUME, DelugeValues.formatHex (DelugeValues.PARAM_MAX));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.OSC_B_VOLUME, DelugeValues.formatHex (DelugeValues.PARAM_MIN));

        // ------------------------------------
        // Amplifier

        final Element envelope1 = XMLUtils.addElement (document, defaultParams, DelugeTag.ENVELOPE1);
        final IEnvelopeModulator amplitudeEnvelopeModulator = firstZone.getAmplitudeEnvelopeModulator ();
        final IEnvelope ampEnvelope = amplitudeEnvelopeModulator.getSource ();
        XMLUtils.addTextElement (document, envelope1, DelugeTag.ATTACK, DelugeValues.formatHex (DelugeValues.attackTimeToParam (ampEnvelope.getAttackTime ())));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.DECAY, DelugeValues.formatHex (envelopeDecay (ampEnvelope)));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.SUSTAIN, DelugeValues.formatHex (amplitudeEnvelopeSustain (ampEnvelope, firstZone.getGain ())));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.RELEASE, DelugeValues.formatHex (DelugeValues.releaseTimeToParam (ampEnvelope.getReleaseTime ())));

        // Make the sound velocity sensitive (matches the firmware's default for sample based
        // sounds)
        final Element patchCables = XMLUtils.addElement (document, defaultParams, DelugeTag.PATCH_CABLES);

        final int ampModAmount = (int) DelugeValues.modulationDepthToPatchAmount (amplitudeEnvelopeModulator.getDepth ());
        createPatchCable (document, patchCables, DelugeTag.SOURCE_VELOCITY, DelugeTag.DESTINATION_VOLUME, ampModAmount);

        // ------------------------------------
        // Filter

        writeFilter (document, defaultParams, firstZone.getFilter (), patchCables);
    }


    /**
     * Write the low-pass and high-pass filter frequency/resonance. If a filter is set it is mapped,
     * otherwise the filter is left fully open.
     *
     * @param document The XML document
     * @param defaultParams The default parameters element
     * @param filterOpt The filter if set
     * @param patchCables The patch cables element to add modulations to
     */
    private static void writeFilter (final Document document, final Element defaultParams, final Optional<IFilter> filterOpt, final Element patchCables)
    {
        int lpfFrequency = DelugeValues.PARAM_MAX;
        int lpfResonance = DelugeValues.PARAM_MIN;
        int hpfFrequency = DelugeValues.PARAM_MIN;
        int hpfResonance = DelugeValues.PARAM_MIN;

        if (filterOpt.isPresent ())
        {
            final IFilter filter = filterOpt.get ();

            final FilterType type = filter.getType ();
            final boolean isHighPass = type == FilterType.HIGH_PASS;
            if (type == FilterType.LOW_PASS || isHighPass)
            {
                final int cutoffModAmount = (int) DelugeValues.modulationDepthToPatchAmount (filter.getCutoffKeyTracking ());
                createPatchCable (document, patchCables, DelugeTag.SOURCE_NOTE, isHighPass ? DelugeTag.HPF_FREQUENCY : DelugeTag.LPF_FREQUENCY, cutoffModAmount);
            }

            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
            final Element envelope2 = XMLUtils.addElement (document, defaultParams, DelugeTag.ENVELOPE2);
            XMLUtils.addTextElement (document, envelope2, DelugeTag.ATTACK, DelugeValues.formatHex (DelugeValues.attackTimeToParam (cutoffEnvelope.getAttackTime ())));
            XMLUtils.addTextElement (document, envelope2, DelugeTag.DECAY, DelugeValues.formatHex (envelopeDecay (cutoffEnvelope)));
            XMLUtils.addTextElement (document, envelope2, DelugeTag.SUSTAIN, DelugeValues.formatHex (envelopeSustain (cutoffEnvelope)));
            XMLUtils.addTextElement (document, envelope2, DelugeTag.RELEASE, DelugeValues.formatHex (DelugeValues.releaseTimeToParam (cutoffEnvelope.getReleaseTime ())));

            final int cutoffModAmount = (int) DelugeValues.modulationDepthToPatchAmount (cutoffEnvelopeModulator.getDepth ());
            createPatchCable (document, patchCables, DelugeTag.ENVELOPE2, isHighPass ? DelugeTag.HPF_FREQUENCY : DelugeTag.LPF_FREQUENCY, cutoffModAmount);

            final int frequency = DelugeValues.cutoffToParam (filter.getCutoff ());
            final int resonance = DelugeValues.levelToParam (filter.getResonance ());
            if (isHighPass)
            {
                hpfFrequency = frequency;
                hpfResonance = resonance;
            }
            else
            {
                lpfFrequency = frequency;
                lpfResonance = resonance;
            }
        }

        XMLUtils.addTextElement (document, defaultParams, DelugeTag.LPF_FREQUENCY, DelugeValues.formatHex (lpfFrequency));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.LPF_RESONANCE, DelugeValues.formatHex (lpfResonance));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.HPF_FREQUENCY, DelugeValues.formatHex (hpfFrequency));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.HPF_RESONANCE, DelugeValues.formatHex (hpfResonance));
    }


    private static void createPatchCable (final Document document, final Element patchCables, final String source, final String destination, final int amount)
    {
        final Element patchCable = XMLUtils.addElement (document, patchCables, DelugeTag.PATCH_CABLE);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.SOURCE, source);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.DESTINATION, destination);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.AMOUNT, DelugeValues.formatHex (amount));
    }


    /**
     * Get the end sample position of a zone. If the zone does not specify an end the total number
     * of samples is used.
     *
     * @param zone The zone
     * @return The end position in samples
     */
    private static int getEndPosition (final ISampleZone zone)
    {
        final int stop = zone.getStop ();
        if (stop > 0)
            return stop;
        try
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isPresent ())
            {
                final IAudioMetadata audioMetadata = sampleData.get ().getAudioMetadata ();
                final int numberOfSamples = audioMetadata.getNumberOfSamples ();
                if (numberOfSamples > 0)
                    return numberOfSamples;
            }
        }
        catch (final IOException _)
        {
            // Ignore and fall through
        }
        return Math.max (0, stop);
    }


    /**
     * Calculate the Deluge decay parameter value combining the hold and decay phases.
     *
     * @param envelope The envelope
     * @return The Deluge parameter value
     */
    private static int envelopeDecay (final IEnvelope envelope)
    {
        final double decayTime = Math.max (0, envelope.getHoldTime ()) + Math.max (0, envelope.getDecayTime ());
        return envelope.getDecayTime () < 0 && envelope.getHoldTime () < 0 ? DelugeValues.userValueToParam (20) : DelugeValues.releaseTimeToParam (decayTime);
    }


    /**
     * Calculate the Deluge sustain parameter value.
     *
     * @param envelope The envelope
     * @return The Deluge parameter value
     */
    private static int envelopeSustain (final IEnvelope envelope)
    {
        final double sustainLevel = envelope.getSustainLevel ();
        return sustainLevel < 0 ? DelugeValues.PARAM_MAX : DelugeValues.levelToParam (sustainLevel);
    }


    /**
     * Calculate the Deluge sustain parameter for an amplitude envelope. A SoundFont commonly fakes
     * a slow, sustaining pad with a (near) silent sustain plus a very long decay - e.g. a pad whose
     * amplitude decays over 44 seconds - relying on that long decay to keep the note up. The
     * Deluge's decay/release only reaches about 5.9 seconds, so such a note would instead collapse
     * to silence within a few seconds and sound far too quiet. When the sustain is (near) silent
     * but the decay is longer than the Deluge can represent, hold the note at the level implied by
     * the zone's attenuation so it sustains at the source's level instead of dropping out.
     *
     * @param envelope The amplitude envelope
     * @param zoneGainDecibels The zone gain in decibels (the source's attenuation, 0 or negative)
     * @return The Deluge sustain parameter value
     */
    private static int amplitudeEnvelopeSustain (final IEnvelope envelope, final double zoneGainDecibels)
    {
        final double sustainLevel = envelope.getSustainLevel ();
        if (sustainLevel >= 0 && sustainLevel < 0.01 && envelope.getDecayTime () > MAX_DELUGE_DECAY_SECONDS)
        {
            final double holdLevel = Math.pow (10.0, Math.min (0.0, zoneGainDecibels) / 20.0);
            return DelugeValues.levelToParam (holdLevel);
        }
        return envelopeSustain (envelope);
    }


    /** {@inheritDoc} */
    @Override
    protected boolean requiresRewrite (final DestinationAudioFormat destinationFormat)
    {
        // The Deluge reads all sample bounds and loop points from the XML, never from chunks in the
        // WAV file. Reconstructing the sample is therefore loss-less for it and lets a loop
        // cross-fade be baked into the audio - the same way the Renoise creator always re-encodes
        // its samples.
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected void additionalProcessing (final IMultisampleSource multisampleSource, final ISampleZone zone, final WaveFile wavFile)
    {
        super.additionalProcessing (multisampleSource, zone, wavFile);

        // The Deluge has no loop cross-fade parameter. A loop cross-fade (set via the 'Set fixed
        // loop-crossfade' processing option or read from the source) is baked into the sample audio
        // so that forward loops do not click at the loop point.
        final Optional<ISampleLoop> loopOpt = getCrossfadeLoop (zone);
        if (loopOpt.isEmpty ())
            return;

        final ISampleLoop loop = loopOpt.get ();
        applyLoopCrossfade (wavFile, loop.getStart (), loop.getEnd (), loop.getCrossfadeInSamples ());
    }


    /**
     * Get the (first) loop of a zone if a cross-fade should be baked into its audio. Only forward
     * loops with a cross-fade greater than zero are considered.
     *
     * @param zone The zone
     * @return The loop or null if no cross-fade should be applied
     */
    private static Optional<ISampleLoop> getCrossfadeLoop (final ISampleZone zone)
    {
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
            return Optional.empty ();
        final ISampleLoop loop = loops.get (0);
        if (loop.getCrossfade () > 0 && loop.getType () == LoopType.FORWARDS && loop.getStart () >= 0 && loop.getEnd () > loop.getStart ())
            return Optional.of (loop);
        return Optional.empty ();
    }


    /**
     * Bake a loop cross-fade into the audio of a forward loop. The cross-fade region at the end of
     * the loop is blended with the audio preceding the loop start so that the loop wraps
     * seamlessly.
     *
     * @param wavFile The WAV file whose audio data is modified in place
     * @param loopStart The loop start frame
     * @param loopEnd The loop end frame (exclusive)
     * @param crossfadeSamples The requested cross-fade length in frames
     */
    private static void applyLoopCrossfade (final WaveFile wavFile, final int loopStart, final int loopEnd, final int crossfadeSamples)
    {
        final FormatChunk formatChunk = wavFile.getFormatChunk ();
        final DataChunk dataChunk = wavFile.getDataChunk ();
        if (formatChunk == null || dataChunk == null || crossfadeSamples <= 0)
            return;

        final int channels = formatChunk.getNumberOfChannels ();
        final int bytesPerFrame = formatChunk.calculateBytesPerSample ();
        final int bytesPerChannel = channels == 0 ? 0 : bytesPerFrame / channels;
        if (bytesPerChannel != 2 && bytesPerChannel != 3)
            return;

        final byte [] data = dataChunk.getData ();
        final int totalFrames = data.length / bytesPerFrame;
        final int end = Math.min (loopEnd, totalFrames);

        // The cross-fade can neither be longer than the loop nor reach before the start of the
        // sample
        final int crossfade = Math.min (crossfadeSamples, Math.min (end - loopStart, loopStart));
        if (crossfade <= 0)
            return;

        for (int j = 0; j < crossfade; j++)
        {
            final int endFrame = end - crossfade + j;
            final int preFrame = loopStart - crossfade + j;
            final double mix = (j + 1.0) / crossfade;
            for (int c = 0; c < channels; c++)
            {
                final int endPos = endFrame * bytesPerFrame + c * bytesPerChannel;
                final int prePos = preFrame * bytesPerFrame + c * bytesPerChannel;
                final int blended = (int) Math.round (readSample (data, endPos, bytesPerChannel) * (1.0 - mix) + readSample (data, prePos, bytesPerChannel) * mix);
                writeSample (data, endPos, bytesPerChannel, blended);
            }
        }

        dataChunk.setData (data);
    }


    /**
     * Read a little-endian signed PCM sample.
     *
     * @param data The audio data
     * @param pos The byte position
     * @param bytesPerChannel The number of bytes per channel sample (2 or 3)
     * @return The signed sample value
     */
    private static int readSample (final byte [] data, final int pos, final int bytesPerChannel)
    {
        if (bytesPerChannel == 2)
            return (short) (data[pos] & 0xFF | (data[pos + 1] & 0xFF) << 8);
        return data[pos] & 0xFF | (data[pos + 1] & 0xFF) << 8 | data[pos + 2] << 16;
    }


    /**
     * Write a little-endian signed PCM sample.
     *
     * @param data The audio data
     * @param pos The byte position
     * @param bytesPerChannel The number of bytes per channel sample (2 or 3)
     * @param value The sample value
     */
    private static void writeSample (final byte [] data, final int pos, final int bytesPerChannel, final int value)
    {
        data[pos] = (byte) (value & 0xFF);
        data[pos + 1] = (byte) (value >> 8 & 0xFF);
        if (bytesPerChannel == 3)
            data[pos + 2] = (byte) (value >> 16 & 0xFF);
    }
}
