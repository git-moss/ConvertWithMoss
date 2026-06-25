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
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IAudioMetadata;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.settings.WavChunkSettingsUI;
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
 *
 * @author Jürgen Moßgraber
 */
public class DelugeCreator extends AbstractWavCreator<WavChunkSettingsUI>
{
    private static final String SYNTHS_FOLDER  = "SYNTHS";
    private static final String SAMPLES_FOLDER = "SAMPLES";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public DelugeCreator (final INotifier notifier)
    {
        super ("Synthstrom Deluge", "Deluge", notifier, new WavChunkSettingsUI ("Deluge"));
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final List<ISampleZone> zones = getMappedZones (multisampleSource);
        if (zones.isEmpty ())
        {
            this.notifier.logError ("IDS_DELUGE_NO_SAMPLES");
            return;
        }
        makeUniqueSampleNames (zones);

        // Create the SYNTHS folder and a unique preset file inside it
        final File synthsFolder = new File (destinationFolder, SYNTHS_FOLDER);
        safeCreateDirectory (synthsFolder);
        final File presetFile = this.createUniqueFilename (synthsFolder, createSafeFilename (multisampleSource.getName ()), "xml");
        final String baseName = FileUtils.getNameWithoutType (presetFile);
        this.notifier.log ("IDS_NOTIFY_STORING", presetFile.getAbsolutePath ());

        final String relativeSampleFolder = SAMPLES_FOLDER + "/" + baseName;

        // Write the samples into SAMPLES/<name>/
        final File sampleFolder = new File (destinationFolder, relativeSampleFolder);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, createTemporarySource (multisampleSource, zones));

        // Create and store the XML document referencing the samples by their card-relative path
        final Optional<String> metadata = this.createSoundDocument (zones, relativeSampleFolder);
        if (metadata.isEmpty ())
            return;
        try (final FileWriter writer = new FileWriter (presetFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata.get ());
        }

        this.progress.notifyDone ();
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

        final DefaultMultisampleSource source = new DefaultMultisampleSource ();
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
            this.writeZone (document, osc1, zones.get (0), relativeSampleFolder, -1);
        else
        {
            final Element sampleRanges = XMLUtils.addElement (document, osc1, DelugeTag.SAMPLE_RANGES);
            for (int i = 0; i < zones.size (); i++)
            {
                final Element sampleRange = XMLUtils.addElement (document, sampleRanges, DelugeTag.SAMPLE_RANGE);
                // The last (highest) range does not store the top note - it covers everything above
                final int topNote = i == zones.size () - 1 ? -1 : limitToDefault (zones.get (i).getKeyHigh (), 127);
                this.writeZone (document, sampleRange, zones.get (i), relativeSampleFolder, topNote);
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

        this.writeDefaultParams (document, soundElement, zones.get (0));

        return this.createXMLString (document);
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
    private void writeZone (final Document document, final Element parent, final ISampleZone zone, final String relativeSampleFolder, final int topNote)
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
    private void writeDefaultParams (final Document document, final Element soundElement, final ISampleZone firstZone)
    {
        final Element defaultParams = XMLUtils.addElement (document, soundElement, DelugeTag.DEFAULT_PARAMS);

        XMLUtils.addTextElement (document, defaultParams, DelugeTag.OSC_A_VOLUME, DelugeValues.formatHex (DelugeValues.PARAM_MAX));
        XMLUtils.addTextElement (document, defaultParams, DelugeTag.OSC_B_VOLUME, DelugeValues.formatHex (DelugeValues.PARAM_MIN));

        writeFilter (document, defaultParams, firstZone.getFilter ().orElse (null));

        final Element envelope1 = XMLUtils.addElement (document, defaultParams, DelugeTag.ENVELOPE1);
        final IEnvelope ampEnvelope = firstZone.getAmplitudeEnvelopeModulator ().getSource ();
        XMLUtils.addTextElement (document, envelope1, DelugeTag.ATTACK, DelugeValues.formatHex (DelugeValues.timeToParam (ampEnvelope.getAttackTime ())));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.DECAY, DelugeValues.formatHex (envelopeDecay (ampEnvelope)));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.SUSTAIN, DelugeValues.formatHex (envelopeSustain (ampEnvelope)));
        XMLUtils.addTextElement (document, envelope1, DelugeTag.RELEASE, DelugeValues.formatHex (DelugeValues.timeToParam (ampEnvelope.getReleaseTime ())));

        // Make the sound velocity sensitive (matches the firmware's default for sample based sounds)
        final Element patchCables = XMLUtils.addElement (document, defaultParams, DelugeTag.PATCH_CABLES);
        final Element patchCable = XMLUtils.addElement (document, patchCables, DelugeTag.PATCH_CABLE);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.SOURCE, DelugeTag.SOURCE_VELOCITY);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.DESTINATION, DelugeTag.DESTINATION_VOLUME);
        XMLUtils.addTextElement (document, patchCable, DelugeTag.AMOUNT, DelugeValues.formatHex (DelugeValues.PATCH_CABLE_FULL));
    }


    /**
     * Write the low-pass and high-pass filter frequency/resonance. If a filter is set it is mapped,
     * otherwise the filter is left fully open.
     *
     * @param document The XML document
     * @param defaultParams The default parameters element
     * @param filter The filter or null
     */
    private static void writeFilter (final Document document, final Element defaultParams, final IFilter filter)
    {
        int lpfFrequency = DelugeValues.PARAM_MAX;
        int lpfResonance = DelugeValues.PARAM_MIN;
        int hpfFrequency = DelugeValues.PARAM_MIN;
        int hpfResonance = DelugeValues.PARAM_MIN;

        if (filter != null)
        {
            final int frequency = DelugeValues.cutoffToParam (filter.getCutoff ());
            final int resonance = DelugeValues.levelToParam (filter.getResonance ());
            if (filter.getType () == FilterType.HIGH_PASS)
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


    /**
     * Get the end sample position of a zone. If the zone does not specify an end the total number of
     * samples is used.
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
            final IAudioMetadata audioMetadata = zone.getSampleData ().getAudioMetadata ();
            final int numberOfSamples = audioMetadata.getNumberOfSamples ();
            if (numberOfSamples > 0)
                return numberOfSamples;
        }
        catch (final IOException ex)
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
        return envelope.getDecayTime () < 0 && envelope.getHoldTime () < 0 ? DelugeValues.userValueToParam (20) : DelugeValues.timeToParam (decayTime);
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
}
