// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.tx16wx;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;
import javafx.geometry.Orientation;
import javafx.scene.Node;


/**
 * Creator for TX16Wx multi-sample files. A txprog file has a description file encoded in XML. The
 * related samples are in a separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class TX16WxCreator extends AbstractCreator
{
    private static final Map<String, String>     CATEGORY_ICONS = new HashMap<> ();
    private static final Map<LoopType, String>   LOOP_MODES     = new EnumMap<> (LoopType.class);
    private static final Map<FilterType, String> FILTER_TYPES   = new EnumMap<> (FilterType.class);
    private static final Map<Integer, String>    FILTER_SLOPES  = new HashMap<> ();
    static
    {
        CATEGORY_ICONS.put ("brass", "trumpet");
        CATEGORY_ICONS.put ("bugle", "bugle");
        CATEGORY_ICONS.put ("cornet", "cornet");
        CATEGORY_ICONS.put ("horn", "french-horn");
        CATEGORY_ICONS.put ("trombone", "trombone");
        CATEGORY_ICONS.put ("trumpet", "trumpet");
        CATEGORY_ICONS.put ("tuba", "tuba");

        CATEGORY_ICONS.put ("chromatic percussion", "xylophone");
        CATEGORY_ICONS.put ("marimba", "xylophone");
        CATEGORY_ICONS.put ("xylophone", "xylophone");
        CATEGORY_ICONS.put ("vibraphone", "xylophone");
        CATEGORY_ICONS.put ("glockenspiel", "xylophone");
        CATEGORY_ICONS.put ("celesta", "xylophone");
        CATEGORY_ICONS.put ("mallet", "xylophone");
        CATEGORY_ICONS.put ("kalimba", "xylophone");

        CATEGORY_ICONS.put ("kick", "bass-drum");
        CATEGORY_ICONS.put ("bass-drum", "bass-drum");
        CATEGORY_ICONS.put ("bassdrum", "bass-drum");
        CATEGORY_ICONS.put ("BD", "bass-drum");
        CATEGORY_ICONS.put ("cymbal", "cymbals");
        CATEGORY_ICONS.put ("gong", "cymbals");
        CATEGORY_ICONS.put ("ride", "cymbals");
        CATEGORY_ICONS.put ("hi-hat", "cymbals");
        CATEGORY_ICONS.put ("hihat", "cymbals");
        CATEGORY_ICONS.put ("hh", "cymbals");
        CATEGORY_ICONS.put ("hats", "cymbals");

        CATEGORY_ICONS.put ("drum-Set", "drum-set");
        CATEGORY_ICONS.put ("drumset", "drum-set");
        CATEGORY_ICONS.put ("kit", "drum-set");
        CATEGORY_ICONS.put ("808", "drum-set");
        CATEGORY_ICONS.put ("909", "drum-set");
        CATEGORY_ICONS.put ("acoustic drum", "drum-set");
        CATEGORY_ICONS.put ("snare", "side-drum");
        CATEGORY_ICONS.put ("tom", "side-drum");
        CATEGORY_ICONS.put ("timpani", "timpani");

        CATEGORY_ICONS.put ("guitar", "guitar");
        CATEGORY_ICONS.put ("rajao", "guitar");
        CATEGORY_ICONS.put ("banjo", "guitar");

        CATEGORY_ICONS.put ("organ", "accordion");
        CATEGORY_ICONS.put ("tonewheel", "accordion");
        CATEGORY_ICONS.put ("accordion", "accordion");
        CATEGORY_ICONS.put ("hammond", "accordion");
        CATEGORY_ICONS.put ("farfisa", "accordion");
        CATEGORY_ICONS.put ("gospel", "accordion");
        CATEGORY_ICONS.put ("b3", "accordion");
        CATEGORY_ICONS.put ("c3", "accordion");

        CATEGORY_ICONS.put ("percussion", "maracas");
        CATEGORY_ICONS.put ("tambourine", "maracas");
        CATEGORY_ICONS.put ("woodblock", "maracas");
        CATEGORY_ICONS.put ("triangle", "maracas");
        CATEGORY_ICONS.put ("cowbell", "maracas");
        CATEGORY_ICONS.put ("timbale", "maracas");
        CATEGORY_ICONS.put ("maracas", "maracas");
        CATEGORY_ICONS.put ("djembe", "maracas");
        CATEGORY_ICONS.put ("shaker", "maracas");
        CATEGORY_ICONS.put ("agogo", "maracas");
        CATEGORY_ICONS.put ("bongo", "maracas");
        CATEGORY_ICONS.put ("chimes", "maracas");
        CATEGORY_ICONS.put ("conga", "maracas");
        CATEGORY_ICONS.put ("cuica", "maracas");
        CATEGORY_ICONS.put ("tabla", "maracas");

        CATEGORY_ICONS.put ("piano", "piano");
        CATEGORY_ICONS.put ("grand", "piano");
        CATEGORY_ICONS.put ("electric piano", "piano");
        CATEGORY_ICONS.put ("e-piano", "piano");
        CATEGORY_ICONS.put ("upright", "piano");
        CATEGORY_ICONS.put ("digital Piano", "piano");
        CATEGORY_ICONS.put ("klavier", "piano");
        CATEGORY_ICONS.put ("clav", "piano");
        CATEGORY_ICONS.put ("suitcase", "piano");
        CATEGORY_ICONS.put ("whirly", "piano");
        CATEGORY_ICONS.put ("wurlitz", "piano");
        CATEGORY_ICONS.put ("mark I", "piano");
        CATEGORY_ICONS.put ("rhodes", "piano");
        CATEGORY_ICONS.put ("ep", "piano");

        CATEGORY_ICONS.put ("church", "pipe-organ");

        CATEGORY_ICONS.put ("pluck", "balalaika");
        CATEGORY_ICONS.put ("balalaika", "balalaika");
        CATEGORY_ICONS.put ("Dulcimer", "balalaika");
        CATEGORY_ICONS.put ("Mandolin", "balalaika");
        CATEGORY_ICONS.put ("Sitar", "balalaika");
        CATEGORY_ICONS.put ("Koto", "balalaika");
        CATEGORY_ICONS.put ("Oud", "balalaika");
        CATEGORY_ICONS.put ("harp", "harp");
        CATEGORY_ICONS.put ("lyre", "lyre");

        CATEGORY_ICONS.put ("strings", "violin");
        CATEGORY_ICONS.put ("string", "violin");
        CATEGORY_ICONS.put ("viola", "violin");
        CATEGORY_ICONS.put ("violin", "violin");
        CATEGORY_ICONS.put ("cello", "violin");
        CATEGORY_ICONS.put ("double Bass", "violin");
        CATEGORY_ICONS.put ("pizzicato", "violin");
        CATEGORY_ICONS.put ("arco", "violin");
        CATEGORY_ICONS.put ("str.", "violin");
        CATEGORY_ICONS.put ("fiddle", "violin");
        CATEGORY_ICONS.put ("bowed", "violin");
        CATEGORY_ICONS.put ("score", "violin");

        CATEGORY_ICONS.put ("synth", "electronic-music");
        CATEGORY_ICONS.put ("electronic-music", "electronic-music");
        CATEGORY_ICONS.put ("sequence", "electronic-music");
        CATEGORY_ICONS.put ("sweep", "electronic-music");
        CATEGORY_ICONS.put ("swell", "electronic-music");
        CATEGORY_ICONS.put ("mini", "electronic-music");
        CATEGORY_ICONS.put ("moog", "electronic-music");
        CATEGORY_ICONS.put ("syn", "electronic-music");
        CATEGORY_ICONS.put ("dj", "electronic-music");

        CATEGORY_ICONS.put ("vocal", "microphone");
        CATEGORY_ICONS.put ("choir", "microphone");
        CATEGORY_ICONS.put ("vox", "microphone");
        CATEGORY_ICONS.put ("voice", "microphone");
        CATEGORY_ICONS.put ("vocode", "microphone");
        CATEGORY_ICONS.put ("choral", "microphone");
        CATEGORY_ICONS.put ("gregorian", "microphone");
        CATEGORY_ICONS.put ("ahh", "microphone");
        CATEGORY_ICONS.put ("whisper", "microphone");
        CATEGORY_ICONS.put ("shout", "microphone");
        CATEGORY_ICONS.put ("sing", "microphone");
        CATEGORY_ICONS.put ("microphone", "microphone");

        CATEGORY_ICONS.put ("clarinet", "clarinet");
        CATEGORY_ICONS.put ("klarinette", "clarinet");
        CATEGORY_ICONS.put ("oboe", "clarinet");
        CATEGORY_ICONS.put ("bassoon", "clarinet");
        CATEGORY_ICONS.put ("Musette", "clarinet");
        CATEGORY_ICONS.put ("Woodwind", "clarinet");
        CATEGORY_ICONS.put ("flute", "flute");
        CATEGORY_ICONS.put ("didgeridoo", "flute");
        CATEGORY_ICONS.put ("Whistle", "flute");
        CATEGORY_ICONS.put ("Recorder", "flute");
        CATEGORY_ICONS.put ("bag pipe", "flute");
        CATEGORY_ICONS.put ("bagpipe", "flute");
        CATEGORY_ICONS.put ("harmonica", "harmonica");
        CATEGORY_ICONS.put ("piccolo", "piccolo");
        CATEGORY_ICONS.put ("sax", "saxophone");

        LOOP_MODES.put (LoopType.FORWARDS, "Forward");
        LOOP_MODES.put (LoopType.BACKWARDS, "Backward");
        LOOP_MODES.put (LoopType.ALTERNATING, "Bidirectional");

        FILTER_TYPES.put (FilterType.LOW_PASS, "LowPass");
        FILTER_TYPES.put (FilterType.HIGH_PASS, "HighPass");
        FILTER_TYPES.put (FilterType.BAND_PASS, "BandPass");
        FILTER_TYPES.put (FilterType.BAND_REJECTION, "Notch");

        FILTER_SLOPES.put (Integer.valueOf (4), "24dB");
        FILTER_SLOPES.put (Integer.valueOf (2), "12dB");
        FILTER_SLOPES.put (Integer.valueOf (1), "6dB");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public TX16WxCreator (final INotifier notifier)
    {
        super ("CWITEC TX16Wx", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);
        this.addWavChunkOptions (panel);
        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.loadWavChunkSettings (config, "TX16Wx");
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        this.saveWavChunkSettings (config, "TX16Wx");
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final String relativeFolderName = sampleName + FOLDER_POSTFIX;

        final Optional<String> metadata = this.createMetadata (relativeFolderName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        final File multiFile = this.createUniqueFilename (destinationFolder, sampleName, "txprog");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePreset (relativeFolderName, destinationFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a TX16Wx sampler file.
     *
     * @param relativeFolderName A relative path for the samples
     * @param destinationFolder Where to store the preset file
     * @param multisampleSource The multi-sample to store in the library
     * @param multiFile Where to write the file to
     * @param metadata The metadata description file
     * @throws IOException Could not store the file
     */
    private void storePreset (final String relativeFolderName, final File destinationFolder, final IMultisampleSource multisampleSource, final File multiFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        final File sampleFolder = new File (destinationFolder, relativeFolderName);
        safeCreateDirectory (sampleFolder);
        this.writeSamples (sampleFolder, multisampleSource);
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     * @throws IOException Could not create the metadata
     */
    private Optional<String> createMetadata (final String folderName, final IMultisampleSource multisampleSource) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element programElement = document.createElement (TX16WxTag.PROGRAM);
        document.appendChild (programElement);
        programElement.setAttribute (TX16WxTag.PROGRAM_CREATED_BY, "30601");
        programElement.setAttribute (TX16WxTag.PROGRAM_QUALITY, "Default");

        // No metadata at all, except program name and icon
        programElement.setAttribute (TX16WxTag.NAME, multisampleSource.getName ());
        final String category = multisampleSource.getMetadata ().getCategory ();
        if (category != null)
        {
            final String icon = CATEGORY_ICONS.get (category.toLowerCase ());
            if (icon != null)
                programElement.setAttribute (TX16WxTag.PROGRAM_ICON, "#" + icon);
        }

        programElement.setAttribute ("xsi:schemaLocation", "http://www.tx16wx.com/3.0/ program");
        programElement.setAttribute ("xmlns:tx", "http://www.tx16wx.com/3.0/program");
        programElement.setAttribute ("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (true);
        final List<Element> groupElements = new ArrayList<> ();
        final List<Element> soundshapeElements = new ArrayList<> ();
        for (final IGroup group: groups)
        {
            final Element groupElement = document.createElement (TX16WxTag.GROUP);
            groupElements.add (groupElement);
            final Element soundshapeElement = document.createElement (TX16WxTag.SOUND_SHAPE);
            soundshapeElements.add (soundshapeElement);
            soundshapeElement.setAttribute (TX16WxTag.NAME, "");

            // Link sound-shape to group
            final String uuid = UUID.randomUUID ().toString ();
            groupElement.setAttribute (TX16WxTag.SOUND_SHAPE, uuid);
            soundshapeElement.setAttribute (TX16WxTag.SOUND_SHAPE_ID, uuid);

            groupElement.setAttribute (TX16WxTag.NAME, group.getName ());
            if (group.getTrigger () == TriggerType.RELEASE)
                groupElement.setAttribute (TX16WxTag.GROUP_PLAYMODE, "Release");
            groupElement.setAttribute (TX16WxTag.GROUP_OUTPUT, "--");

            final List<ISampleZone> zones = group.getSampleZones ();
            for (int i = 0; i < zones.size (); i++)
                createSample (document, folderName, programElement, groupElement, zones.get (i), i);

            addGroupModulationAttributes (document, soundshapeElement, zones);
        }

        final Element globalBoundsElement = XMLUtils.addElement (document, programElement, TX16WxTag.BOUNDS);
        XMLUtils.setIntegerAttribute (globalBoundsElement, TX16WxTag.LO_NOTE, 0);
        XMLUtils.setIntegerAttribute (globalBoundsElement, TX16WxTag.HI_NOTE, 127);
        XMLUtils.setIntegerAttribute (globalBoundsElement, TX16WxTag.LO_VEL, 0);
        XMLUtils.setIntegerAttribute (globalBoundsElement, TX16WxTag.HI_VEL, 127);

        for (final Element soundshapeElement: soundshapeElements)
            programElement.appendChild (soundshapeElement);
        for (final Element groupElement: groupElements)
            programElement.appendChild (groupElement);

        return this.createXMLString (document);
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param programElement The program element
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @param zoneIndex The index of the zone
     */
    private static void createSample (final Document document, final String folderName, final Element programElement, final Element groupElement, final ISampleZone zone, final int zoneIndex)
    {
        final int keyLow = zone.getKeyLow ();
        final int keyHigh = zone.getKeyHigh ();
        final int velocityLow = zone.getVelocityLow ();
        final int velocityHigh = zone.getVelocityHigh ();
        final int noteCrossfadeLow = zone.getNoteCrossfadeLow ();
        final int noteCrossfadeHigh = zone.getNoteCrossfadeHigh ();
        final int velocityCrossfadeLow = zone.getVelocityCrossfadeLow ();
        final int velocityCrossfadeHigh = zone.getVelocityCrossfadeHigh ();

        // The wave description
        final Element waveElement = XMLUtils.addElement (document, programElement, TX16WxTag.SAMPLE);
        waveElement.setAttribute (TX16WxTag.PATH, AbstractCreator.formatFileName (folderName, zone.getName () + ".wav"));
        XMLUtils.setIntegerAttribute (waveElement, TX16WxTag.SAMPLE_ID, zoneIndex);
        XMLUtils.setIntegerAttribute (waveElement, TX16WxTag.START, Math.max (0, zone.getStart ()));
        final int stop = zone.getStop ();
        if (stop >= 0)
            XMLUtils.setIntegerAttribute (waveElement, TX16WxTag.END, stop);
        XMLUtils.setIntegerAttribute (waveElement, TX16WxTag.ROOT, limitToDefault (zone.getKeyRoot (), keyLow));

        // The loop info of the wave description
        final Element loopElement = XMLUtils.addElement (document, waveElement, TX16WxTag.SAMPLE_LOOP);
        final List<ISampleLoop> loops = zone.getLoops ();
        if (loops.isEmpty ())
        {
            loopElement.setAttribute (TX16WxTag.LOOP_MODE, "None");
            loopElement.setAttribute (TX16WxTag.LOOP_START, "0");
            loopElement.setAttribute (TX16WxTag.LOOP_END, "0");
        }
        else
        {
            final ISampleLoop loop = loops.get (0);
            loopElement.setAttribute (TX16WxTag.LOOP_MODE, LOOP_MODES.get (loop.getType ()));
            loopElement.setAttribute (TX16WxTag.NAME, "1");
            XMLUtils.setIntegerAttribute (loopElement, TX16WxTag.LOOP_START, loop.getStart ());
            XMLUtils.setIntegerAttribute (loopElement, TX16WxTag.LOOP_END, loop.getEnd ());
            XMLUtils.setIntegerAttribute (loopElement, TX16WxTag.LOOP_CROSSFADE, loop.getCrossfadeInSamples ());
        }

        // The region containing all other info
        final Element regionElement = XMLUtils.addElement (document, groupElement, TX16WxTag.REGION);
        XMLUtils.setIntegerAttribute (regionElement, TX16WxTag.SAMPLE, zoneIndex);
        regionElement.setAttribute (TX16WxTag.ATTENUATION, String.format (Locale.US, "%.2f dB", Double.valueOf (zone.getGain ())));
        XMLUtils.setDoubleAttribute (regionElement, TX16WxTag.PANORAMA, zone.getPanorama (), 2);
        regionElement.setAttribute (TX16WxTag.SAMPLE_LOOP, "0");
        XMLUtils.setBooleanAttribute (regionElement, TX16WxTag.REVERSE, zone.isReversed ());

        // No key tracking

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        final Element boundsElement = XMLUtils.addElement (document, regionElement, TX16WxTag.BOUNDS);
        final Element fadesElement = XMLUtils.addElement (document, regionElement, TX16WxTag.FADE_BOUNDS);

        XMLUtils.setIntegerAttribute (boundsElement, TX16WxTag.LO_NOTE, limitToDefault (keyLow, 0));
        XMLUtils.setIntegerAttribute (boundsElement, TX16WxTag.HI_NOTE, limitToDefault (keyHigh, 127));
        XMLUtils.setIntegerAttribute (boundsElement, TX16WxTag.LO_VEL, limitToDefault (velocityLow, 1));
        XMLUtils.setIntegerAttribute (boundsElement, TX16WxTag.HI_VEL, limitToDefault (velocityHigh, 127));

        if (noteCrossfadeLow > 0)
            XMLUtils.setIntegerAttribute (fadesElement, TX16WxTag.LO_NOTE, Math.max (0, keyLow - noteCrossfadeLow));
        if (noteCrossfadeHigh > 0)
            XMLUtils.setIntegerAttribute (fadesElement, TX16WxTag.HI_NOTE, Math.min (127, keyHigh + noteCrossfadeHigh));
        if (velocityCrossfadeLow > 0)
            XMLUtils.setIntegerAttribute (fadesElement, TX16WxTag.LO_VEL, Math.max (0, velocityLow - velocityCrossfadeLow));
        if (velocityCrossfadeHigh > 0)
            XMLUtils.setIntegerAttribute (fadesElement, TX16WxTag.HI_VEL, Math.min (127, velocityHigh + velocityCrossfadeHigh));

        final double tune = zone.getTune ();
        if (tune != 0)
        {
            int transpose = (int) tune;
            final double fine = tune - transpose;
            int fineCents = (int) (fine * 100.0);
            if (fineCents < -50)
            {
                transpose -= 1;
                fineCents = 100 + fineCents;
            }
            else if (fineCents > 50)
            {
                transpose += 1;
                fineCents = fineCents - 100;
            }

            final Element soundOffsetsElement = XMLUtils.addElement (document, regionElement, TX16WxTag.SOUND_OFFSETS);
            XMLUtils.setIntegerAttribute (soundOffsetsElement, TX16WxTag.TUNING_COARSE, transpose);
            XMLUtils.setIntegerAttribute (soundOffsetsElement, TX16WxTag.TUNING_FINE, fineCents);
        }
    }


    private static void addGroupModulationAttributes (final Document document, final Element soundshapeElement, final List<ISampleZone> zones)
    {
        final Element modulationElement = document.createElement (TX16WxTag.MODULATION);
        Element filterElement = null;

        final ISampleZone zone = zones.isEmpty () ? null : zones.get (0);
        if (zone != null)
        {
            // Add amplitude envelope
            final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
            final Element aegElement = XMLUtils.addElement (document, soundshapeElement, TX16WxTag.AMP_ENVELOPE);

            // The whole group can be delayed which is basically the same as the amplitude delay
            soundshapeElement.setAttribute (TX16WxTag.GROUP_DELAY, formatTime (amplitudeEnvelope.getDelayTime ()));

            //////////////////////////////////////////////
            // Amplitude

            final double sustain = amplitudeEnvelope.getSustainLevel ();
            final String sustainLevel = sustain < 0 ? "1" : Double.toString (Math.clamp (sustain, 0, 1));
            aegElement.setAttribute (TX16WxTag.AMP_ENV_ATTACK, formatTime (amplitudeEnvelope.getAttackTime ()));
            aegElement.setAttribute (TX16WxTag.AMP_ENV_LEVEL1, "0 dB");
            aegElement.setAttribute (TX16WxTag.AMP_ENV_DECAY1, formatTime (amplitudeEnvelope.getHoldTime ()));
            aegElement.setAttribute (TX16WxTag.AMP_ENV_LEVEL2, "0 dB");
            aegElement.setAttribute (TX16WxTag.AMP_ENV_DECAY2, formatTime (amplitudeEnvelope.getDecayTime ()));
            aegElement.setAttribute (TX16WxTag.AMP_ENV_SUSTAIN, sustainLevel);
            aegElement.setAttribute (TX16WxTag.AMP_ENV_RELEASE, formatTime (amplitudeEnvelope.getReleaseTime ()));
            XMLUtils.setDoubleAttribute (aegElement, TX16WxTag.AMP_ENV_ATTACK_SHAPE, amplitudeEnvelope.getAttackSlope (), 6);
            XMLUtils.setDoubleAttribute (aegElement, TX16WxTag.AMP_ENV_DECAY2_SHAPE, amplitudeEnvelope.getDecaySlope (), 6);
            XMLUtils.setDoubleAttribute (aegElement, TX16WxTag.AMP_ENV_RELEASE_SHAPE, amplitudeEnvelope.getReleaseSlope (), 6);

            final double ampVelocityDepth = zone.getAmplitudeVelocityModulator ().getDepth ();
            if (ampVelocityDepth != 0)
                XMLUtils.setDoubleAttribute (soundshapeElement, TX16WxTag.AMP_VELOCITY, ampVelocityDepth, 6);

            // Pitch-bend
            int pitchbend = Math.abs (zone.getBendUp ());
            pitchbend = pitchbend <= 0 ? 200 : pitchbend;
            addModulationEntry (document, modulationElement, "Pitchbend", "Pitch", pitchbend + "Ct");

            //////////////////////////////////////////////
            // Filter

            final Optional<IFilter> optFilter = zone.getFilter ();
            if (optFilter.isPresent ())
            {
                final IFilter filter = optFilter.get ();
                filterElement = document.createElement (TX16WxTag.FILTER1);

                final String filterType = FILTER_TYPES.get (filter.getType ());
                if (filterType == null)
                    return;
                filterElement.setAttribute (TX16WxTag.FILTER_TYPE, filterType);
                filterElement.setAttribute (TX16WxTag.FILTER_CUTOFF, filter.getCutoff () + "Hz");
                filterElement.setAttribute (TX16WxTag.FILTER_RESONANCE, Math.round (filter.getResonance () * 100) + "%");

                final String slopeValue = FILTER_SLOPES.get (Integer.valueOf (filter.getPoles ()));
                if (slopeValue != null)
                    filterElement.setAttribute (TX16WxTag.FILTER_SLOPE, slopeValue);

                final IEnvelopeModulator cutoffModulator = filter.getCutoffEnvelopeModulator ();
                final double filterModDepth = cutoffModulator.getDepth ();
                if (filterModDepth != 0)
                {
                    final Element envElement = XMLUtils.addElement (document, soundshapeElement, TX16WxTag.ENVELOPE_1);
                    final IEnvelope filterEnvelope = cutoffModulator.getSource ();
                    envElement.setAttribute (TX16WxTag.ENV_LEVEL0, (int) (filterEnvelope.getStartLevel () * 100.0) + "%");
                    envElement.setAttribute (TX16WxTag.ENV_TIME1, formatTime (filterEnvelope.getAttackTime ()));
                    envElement.setAttribute (TX16WxTag.ENV_LEVEL1, (int) (filterEnvelope.getSustainLevel () * 100.0) + "%");
                    envElement.setAttribute (TX16WxTag.ENV_TIME2, formatTime (filterEnvelope.getDecayTime ()));
                    envElement.setAttribute (TX16WxTag.ENV_LEVEL2, (int) (filterEnvelope.getSustainLevel () * 100.0) + "%");
                    envElement.setAttribute (TX16WxTag.ENV_TIME3, formatTime (filterEnvelope.getReleaseTime ()));
                    envElement.setAttribute (TX16WxTag.ENV_LEVEL3, (int) (filterEnvelope.getEndLevel () * 100.0) + "%");
                    XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE1, amplitudeEnvelope.getAttackSlope (), 6);
                    XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE2, amplitudeEnvelope.getDecaySlope (), 6);
                    XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE3, amplitudeEnvelope.getReleaseSlope (), 6);
                    addModulationEntry (document, modulationElement, "ENV1", "Filter 1 Freq", (int) (filterModDepth * IEnvelope.MAX_ENVELOPE_DEPTH) + "Ct");
                }

                final double filterVelocityDepth = filter.getCutoffVelocityModulator ().getDepth ();
                if (filterVelocityDepth != 0)
                    addModulationEntry (document, modulationElement, "Vel", "Filter 1 Freq", (int) (filterVelocityDepth * IEnvelope.MAX_ENVELOPE_DEPTH) + "Ct");
            }

            //////////////////////////////////////////////
            // Pitch

            final IEnvelopeModulator pitchModulator = zone.getPitchModulator ();
            final double pitchModDepth = pitchModulator.getDepth ();
            if (pitchModDepth != 0)
            {
                final Element envElement = XMLUtils.addElement (document, soundshapeElement, TX16WxTag.ENVELOPE_2);
                final IEnvelope pitchEnvelope = pitchModulator.getSource ();
                envElement.setAttribute (TX16WxTag.ENV_LEVEL0, (int) (pitchEnvelope.getStartLevel () * 100.0) + "%");
                envElement.setAttribute (TX16WxTag.ENV_TIME1, formatTime (pitchEnvelope.getAttackTime ()));
                envElement.setAttribute (TX16WxTag.ENV_LEVEL1, (int) (pitchEnvelope.getSustainLevel () * 100.0) + "%");
                envElement.setAttribute (TX16WxTag.ENV_TIME2, formatTime (pitchEnvelope.getDecayTime ()));
                envElement.setAttribute (TX16WxTag.ENV_LEVEL2, (int) (pitchEnvelope.getSustainLevel () * 100.0) + "%");
                envElement.setAttribute (TX16WxTag.ENV_TIME3, formatTime (pitchEnvelope.getReleaseTime ()));
                envElement.setAttribute (TX16WxTag.ENV_LEVEL3, (int) (pitchEnvelope.getEndLevel () * 100.0) + "%");
                XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE1, amplitudeEnvelope.getAttackSlope (), 6);
                XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE2, amplitudeEnvelope.getDecaySlope (), 6);
                XMLUtils.setDoubleAttribute (envElement, TX16WxTag.ENV_SHAPE3, amplitudeEnvelope.getReleaseSlope (), 6);
                addModulationEntry (document, modulationElement, "ENV2", "Pitch", (int) (pitchModDepth * IEnvelope.MAX_ENVELOPE_DEPTH) + "Ct");
            }
        }

        // Needs to be added here to fulfill the required order
        if (filterElement != null)
            soundshapeElement.appendChild (filterElement);
        soundshapeElement.appendChild (modulationElement);
    }


    private static void addModulationEntry (final Document document, final Element modulationElement, final String source, final String destination, final String amount)
    {
        final Element modulationEntryElement = XMLUtils.addElement (document, modulationElement, TX16WxTag.MODULATION_ENTRY);
        modulationEntryElement.setAttribute (TX16WxTag.MODULATION_SOURCE, source);
        modulationEntryElement.setAttribute (TX16WxTag.MODULATION_DESTINATION, destination);
        modulationEntryElement.setAttribute (TX16WxTag.MODULATION_AMOUNT, amount);
        final Element modulationSrcCurveElement = XMLUtils.addElement (document, modulationEntryElement, TX16WxTag.MODULATION_SRC_CURVE);
        modulationSrcCurveElement.setAttribute (TX16WxTag.MODULATION_SMOOTH, "true");
        modulationSrcCurveElement.setAttribute (TX16WxTag.MODULATION_SHAPE, "Linear");
        final Element modulationViaCurveElement = XMLUtils.addElement (document, modulationEntryElement, TX16WxTag.MODULATION_VIA_CURVE);
        modulationViaCurveElement.setAttribute (TX16WxTag.MODULATION_SMOOTH, "true");
        modulationViaCurveElement.setAttribute (TX16WxTag.MODULATION_SHAPE, "Linear");
    }


    private static String formatTime (final double valueInMilliSeconds)
    {
        final double v = valueInMilliSeconds <= 0 ? 0 : Math.round (valueInMilliSeconds * 1000.0);
        return v + " ms";
    }
}