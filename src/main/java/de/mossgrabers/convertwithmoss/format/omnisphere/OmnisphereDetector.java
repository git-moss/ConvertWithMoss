// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.utils.NameValueParser;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.format.TagDetector;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Detects recursively Omnisphere ZMAP files in folders. Files must end with <i>.zmap</i>.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereDetector extends AbstractDetector<OmnisphereDetectorUI>
{
    private static final String    FILE_ENDING_ZMAP                     = ".zmap";
    private static final String    IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND = "IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND";
    private static final String    IDS_NOTIFY_ERR_BAD_METADATA_FILE     = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String    SOURCE_TAG                           = "source";
    private static final String    TARGET_TAG                           = "target";
    private static final String    AMOUNT_TAG                           = "hi";

    private static final String [] PRESET_ENDINGS                       =
    {
        ".prt_omn"
    };

    private static final String [] MULTISAMPLE_ENDINGS                  =
    {
        FILE_ENDING_ZMAP
    };


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OmnisphereDetector (final INotifier notifier)
    {
        super ("Spectrasonics Omnisphere 3", "Omnisphere", notifier, new OmnisphereDetectorUI ("Omnisphere"));
    }


    /** {@inheritDoc} */
    @Override
    protected void configureFileEndings (final boolean detectPerformances)
    {
        this.fileEndings = this.settingsConfiguration.usePresetFiles () ? PRESET_ENDINGS : MULTISAMPLE_ENDINGS;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        try
        {
            final String content = this.loadTextFile (file).trim ();
            if (this.settingsConfiguration.usePresetFiles ())
                return this.readOmniPresetFile (file, content);
            final Optional<IMultisampleSource> multisampleSource = this.parseZmapFile (file, content, file.getParentFile ());
            return multisampleSource.isEmpty () ? Collections.emptyList () : Collections.singletonList (multisampleSource.get ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
            return Collections.emptyList ();
        }
    }


    private List<IMultisampleSource> readOmniPresetFile (final File presetFile, final String content)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final Optional<File> userSampleFolder = findUserSampleFolder (presetFile);
        if (userSampleFolder.isEmpty ())
        {
            this.notifier.logError ("IDS_OMNISPHERE_NO_USER_SAMPLE_FOLDER");
            return Collections.emptyList ();
        }

        try
        {
            final Document document = OmnisphereAggregatedFile.parseXml (content);
            return this.parsePresetDescription (presetFile, document, userSampleFolder.get ());
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return Collections.emptyList ();
    }


    private List<IMultisampleSource> parsePresetDescription (final File presetFile, final Document document, final File userSampleFolder) throws IOException
    {
        // Navigate down to the voice and multi-sample elements...

        final Element rootElement = document.getDocumentElement ();
        if (!"AmberPart".equals (rootElement.getNodeName ()))
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unknown Root");
            return Collections.emptyList ();
        }
        final Element synthEngineElement = XMLUtils.getChildElementByName (rootElement, "SynthEngine");
        if (synthEngineElement == null)
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing element 'SynthEngine'."));

        final Element synthEngElement = XMLUtils.getChildElementByName (synthEngineElement, "SYNTHENG");
        if (synthEngElement == null)
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing element 'SYNTHENG'."));

        final Element modEnvParamsElement = XMLUtils.getChildElementByName (synthEngElement, "MODENVPARAMS");
        if (modEnvParamsElement == null)
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing element 'MODENVPARAMS'."));
        final List<Element> modEnvElements = XMLUtils.getChildElementsByName (synthEngElement, "MODENV");
        if (modEnvElements.isEmpty ())
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing element 'MODENV'."));
        final Element modEnvElement = modEnvElements.get (0);

        // Get the voice and multi-sample elements and combine them...
        final List<Boolean> hasTracking = new ArrayList<> ();
        final List<Object> voiceElements = new ArrayList<> ();
        for (final Element voiceElement: XMLUtils.getChildElementsByName (synthEngElement, "VOICE"))
        {
            final Element oscElement = XMLUtils.getChildElementByName (voiceElement, "OSC");

            if (oscElement != null && XMLUtils.getIntegerAttribute (oscElement, "kind", 0) == 4)
            {
                voiceElements.add (voiceElement);
                hasTracking.add (Boolean.valueOf (!"0".equals (oscElement.getAttribute ("reptch"))));
            }
            else
            {
                voiceElements.add (new Object ());
                hasTracking.add (Boolean.FALSE);
            }
        }
        if (voiceElements.size () != 4)
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unsound document."));

        final List<Object> multisampleElements = new ArrayList<> ();
        for (final Element multisampleElement: XMLUtils.getChildElementsByName (synthEngElement, "MULTISAMPLE"))
        {
            final Element msImElement = XMLUtils.getChildElementByName (multisampleElement, "MS_IM_0");
            if (msImElement != null && "User".equals (msImElement.getAttribute ("library")))
                multisampleElements.add (msImElement.getAttribute ("name"));
            else
                multisampleElements.add (new Object ());
        }
        if (multisampleElements.size () != 4)
            throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unsound document."));

        final IMultisampleSource multisampleSource = this.createMultisampleSource (presetFile, FileUtils.getNameWithoutType (presetFile));

        // 5000.0 = 100 * 100 / 2
        final int pitchBendUp = (int) Math.round (parseFloatAttribute (synthEngElement, "pbup", 0.04f) * 5000.0);
        final int pitchBendDown = (int) -Math.round (parseFloatAttribute (synthEngElement, "pbdn", 0.04f) * 5000.0);

        final Element entryDescriptionElement = XMLUtils.getChildElementByName (synthEngElement, "ENTRYDESCR");
        if (entryDescriptionElement != null)
            createMetadata (multisampleSource.getMetadata (), entryDescriptionElement.getAttribute ("ATTRIB_VALUE_DATA"));

        final Element modMatrixElement = XMLUtils.getChildElementByName (synthEngElement, "MOD_MATRIX");
        float pitchEnvelopeAmount = 0;
        if (modMatrixElement != null)
            for (int row = 0; modMatrixElement.hasAttribute (SOURCE_TAG + row); row++)
                if ("ModEnv1".equals (modMatrixElement.getAttribute (SOURCE_TAG + row)) && "A tune".equals (modMatrixElement.getAttribute (TARGET_TAG + row)) && !isMuted (modMatrixElement, row))
                {
                    // The amount is relative to the range of the coarse tuning (-4800..4800 cents),
                    // 'defV' only mirrors the coarse tuning itself
                    final double amount = parseFloatAttribute (modMatrixElement, AMOUNT_TAG + row, 0);
                    pitchEnvelopeAmount = (float) Math.clamp (amount * OmnisphereLfoUtils.PITCH_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH, -1.0, 1.0);
                    break;
                }

        final Element lfoSetElement = XMLUtils.getChildElementByName (synthEngElement, "LFO_SET");
        final List<Element> lfoElements = lfoSetElement == null ? Collections.emptyList () : XMLUtils.getChildElementsByName (lfoSetElement, "LFO");

        final List<IGroup> allGroups = new ArrayList<> ();
        for (int i = 0; i < 4; i++)
            if (voiceElements.get (i) instanceof final Element voiceElement && multisampleElements.get (i) instanceof final String soundSourceName)
            {
                final String soundSourceFilename = soundSourceName + FILE_ENDING_ZMAP;
                this.notifier.log ("IDS_OMNISPHERE_NO_LOOKING_UP_SOUND_SOURCE", soundSourceFilename);
                final Optional<File> soundsourceFileOpt = findFileRecursively (userSampleFolder, soundSourceFilename);
                if (soundsourceFileOpt.isEmpty ())
                {
                    this.notifier.logError ("IDS_OMNISPHERE_SOUND_SOURCE_NOT_FOUND", soundSourceFilename);
                    return Collections.emptyList ();
                }

                final File soundsourceFile = soundsourceFileOpt.get ();
                final String content = this.loadTextFile (soundsourceFile).trim ();
                final Optional<IMultisampleSource> msSource = this.parseZmapFile (soundsourceFile, content, userSampleFolder);
                if (msSource.isPresent ())
                {
                    final List<IGroup> groups = new ArrayList<> (msSource.get ().getNonEmptyGroups (false));
                    applyParameters (groups, voiceElement, pitchBendUp, pitchBendDown, hasTracking.get (i).booleanValue (), pitchEnvelopeAmount, modEnvElement, modEnvParamsElement);
                    if (modMatrixElement != null)
                        applyLfoModulations (groups, voiceElement, modMatrixElement, lfoElements, (char) ('A' + i));
                    allGroups.addAll (groups);
                }
            }

        multisampleSource.setGroups (allGroups);
        return Collections.singletonList (multisampleSource);
    }


    private static void applyParameters (final List<IGroup> groups, final Element voiceElement, final int pitchBendUp, final int pitchBendDown, final boolean hasKeyTracking, final float pitchEnvelopeAmount, final Element modEnvElement, final Element modEnvParamsElement)
    {
        IFilter filter = null;
        final Element filterElement = XMLUtils.getChildElementByName (voiceElement, "FILTER");
        if (filterElement != null)
        {
            filter = OmnisphereFilterUtils.getFilter (parseFloatAttribute (filterElement, "type1", OmnisphereFilterUtils.DEFAULT_FILTER_INDEX));
            final double frequency = OmnisphereFilterUtils.normalizedToHertz (parseFloatAttribute (filterElement, "freq", 1.0f));
            final float normalizedResonance = parseFloatAttribute (filterElement, "res", 0.0f);
            filter = new DefaultFilter (filter.getType (), filter.getPoles (), frequency, normalizedResonance);

            final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
            cutoffEnvelopeModulator.setDepth (parseFloatAttribute (filterElement, "envdpth", 1.0f));

            final IEnvelope filterEnvelope = cutoffEnvelopeModulator.getSource ();
            final Element filterEnvElement = XMLUtils.getChildElementByName (voiceElement, "FENV");
            final List<Element> filterEnvelopeElements = XMLUtils.getChildElementsByName (filterEnvElement, "p");
            final boolean isFilterEnvSlopePresent = filterEnvelopeElements.size () == 4;
            filterEnvelope.setAttackSlope (isFilterEnvSlopePresent ? parseSlopeAttribute (filterEnvelopeElements.get (0)) : 0);
            filterEnvelope.setDecaySlope (isFilterEnvSlopePresent ? parseSlopeAttribute (filterEnvelopeElements.get (1)) : 0);
            filterEnvelope.setReleaseSlope (isFilterEnvSlopePresent ? parseSlopeAttribute (filterEnvelopeElements.get (2)) : 0);
            final Element filterParamsElement = XMLUtils.getChildElementByName (voiceElement, "FENVPARAMS");
            filterEnvelope.setAttackTime (parseTimeAttribute (filterParamsElement, "attk", 0.0f));
            filterEnvelope.setHoldTime (parseTimeAttribute (filterParamsElement, "hold", 0.0f));
            filterEnvelope.setDecayTime (parseTimeAttribute (filterParamsElement, "decy", 0.0f));
            filterEnvelope.setReleaseTime (parseTimeAttribute (filterParamsElement, "rels", 0.0f));
            filterEnvelope.setSustainLevel (parseFloatAttribute (filterParamsElement, "sust", 1.0f));

            final float modByKey = parseFloatAttribute (filterElement, "key", 0);
            final float modByKeyInv = parseFloatAttribute (filterElement, "keyinv", 0);
            filter.setCutoffKeyTracking (modByKeyInv > 0 ? -modByKey : modByKey);

            filter.getCutoffVelocityModulator ().setDepth (parseFloatAttribute (filterParamsElement, "velsens", 1.0f));
        }

        final Element ampEnvElement = XMLUtils.getChildElementByName (voiceElement, "AENV");
        final List<Element> ampEnvelopeElements = XMLUtils.getChildElementsByName (ampEnvElement, "p");
        final boolean isAmpEnvSlopePresent = ampEnvelopeElements.size () == 4;
        final double ampAttackSlope = isAmpEnvSlopePresent ? parseSlopeAttribute (ampEnvelopeElements.get (0)) : 0;
        final double ampDecaySlope = isAmpEnvSlopePresent ? parseSlopeAttribute (ampEnvelopeElements.get (1)) : 0;
        final double ampReleaseSlope = isAmpEnvSlopePresent ? parseSlopeAttribute (ampEnvelopeElements.get (2)) : 0;
        final Element ampParamsElement = XMLUtils.getChildElementByName (voiceElement, "AENVPARAMS");
        final double ampAttackTime = parseTimeAttribute (ampParamsElement, "attk", 0.0f);
        final double ampHoldTime = parseTimeAttribute (ampParamsElement, "hold", 0.0f);
        final double ampDecayTime = parseTimeAttribute (ampParamsElement, "decy", 0.0f);
        final double ampReleaseTime = parseTimeAttribute (ampParamsElement, "rels", 0.0f);
        final double ampSustainLevel = parseFloatAttribute (ampParamsElement, "sust", 1.0f);
        final double ampVelocitySensitivity = parseFloatAttribute (ampParamsElement, "velsens", 1.0f);

        final List<Element> envelopeElements = XMLUtils.getChildElementsByName (modEnvElement, "p");
        final boolean isModEnvSlopePresent = envelopeElements.size () == 4;
        final double modAttackSlope = isModEnvSlopePresent ? parseSlopeAttribute (envelopeElements.get (0)) : 0;
        final double modDecaySlope = isModEnvSlopePresent ? parseSlopeAttribute (envelopeElements.get (1)) : 0;
        final double modReleaseSlope = isModEnvSlopePresent ? parseSlopeAttribute (envelopeElements.get (2)) : 0;
        final double modAttackTime = parseTimeAttribute (modEnvParamsElement, "attk", 0.0f);
        final double modHoldTime = parseTimeAttribute (modEnvParamsElement, "hold", 0.0f);
        final double modDecayTime = parseTimeAttribute (modEnvParamsElement, "decy", 0.0f);
        final double modReleaseTime = parseTimeAttribute (modEnvParamsElement, "rels", 0.0f);
        final double modSustainLevel = parseFloatAttribute (modEnvParamsElement, "sust", 1.0f);

        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                zone.setBendUp (pitchBendUp);
                zone.setBendDown (pitchBendDown);
                zone.setKeyTracking (hasKeyTracking ? 1 : 0);

                final IEnvelope amplitudeEnvelope = zone.getAmplitudeEnvelopeModulator ().getSource ();
                amplitudeEnvelope.setAttackTime (ampAttackTime);
                amplitudeEnvelope.setHoldTime (ampHoldTime);
                amplitudeEnvelope.setDecayTime (ampDecayTime);
                amplitudeEnvelope.setReleaseTime (ampReleaseTime);
                amplitudeEnvelope.setSustainLevel (ampSustainLevel);
                amplitudeEnvelope.setAttackSlope (ampAttackSlope);
                amplitudeEnvelope.setDecaySlope (ampDecaySlope);
                amplitudeEnvelope.setReleaseSlope (ampReleaseSlope);
                zone.getAmplitudeVelocityModulator ().setDepth (ampVelocitySensitivity);

                final IEnvelopeModulator pitchEnvelopeModulator = zone.getPitchEnvelopeModulator ();
                final IEnvelope pitchEnvelope = pitchEnvelopeModulator.getSource ();
                pitchEnvelopeModulator.setDepth (pitchEnvelopeAmount);
                pitchEnvelope.setAttackTime (modAttackTime);
                pitchEnvelope.setHoldTime (modHoldTime);
                pitchEnvelope.setDecayTime (modDecayTime);
                pitchEnvelope.setReleaseTime (modReleaseTime);
                pitchEnvelope.setSustainLevel (modSustainLevel);
                pitchEnvelope.setAttackSlope (modAttackSlope);
                pitchEnvelope.setDecaySlope (modDecaySlope);
                pitchEnvelope.setReleaseSlope (modReleaseSlope);

                if (filter != null)
                    zone.setFilter (filter);
            }
    }


    /**
     * Apply the LFO modulations of the modulation matrix which target the pitch, filter cutoff or
     * amplitude of the given layer.
     *
     * @param groups The groups of the layer
     * @param voiceElement The voice element of the layer
     * @param modMatrixElement The modulation matrix element
     * @param lfoElements The LFO elements
     * @param layer The letter of the layer, A to D
     */
    private static void applyLfoModulations (final List<IGroup> groups, final Element voiceElement, final Element modMatrixElement, final List<Element> lfoElements, final char layer)
    {
        final Element oscElement = XMLUtils.getChildElementByName (voiceElement, "OSC");
        final Element filterElement = XMLUtils.getChildElementByName (voiceElement, "FILTER");

        for (int row = 0; modMatrixElement.hasAttribute (SOURCE_TAG + row); row++)
        {
            final String source = modMatrixElement.getAttribute (SOURCE_TAG + row);
            final double amount = parseFloatAttribute (modMatrixElement, AMOUNT_TAG + row, 0);
            if (!source.startsWith ("LFO") || amount <= 0 || isMuted (modMatrixElement, row))
                continue;
            final int lfoNumber = parseLfoNumber (source);
            if (lfoNumber < 1 || lfoNumber > lfoElements.size ())
                continue;

            // The depth of the LFO is set by its 'swing' parameter, which is usually modulated by
            // the modulation wheel
            final Element lfoElement = lfoElements.get (lfoNumber - 1);
            final double lfoDepth = Math.clamp (parseFloatAttribute (lfoElement, "swing", 1.0f), 0.0, 1.0);
            final double wheelAmount = Math.min (getWheelAmount (modMatrixElement, source + " swing"), 1.0 - lfoDepth);

            final String target = modMatrixElement.getAttribute (TARGET_TAG + row);
            if (target.equals (layer + " tune"))
            {
                final double depth = amount * OmnisphereLfoUtils.PITCH_RANGE_CENTS / IEnvelope.MAX_ENVELOPE_DEPTH;
                for (final IGroup group: groups)
                    for (final ISampleZone zone: group.getSampleZones ())
                        applyLfo (zone.getPitchLfoModulator (), lfoElement, depth, lfoDepth, wheelAmount);
            }
            else if (target.equals (layer + " freq"))
            {
                // All zones share the same filter
                final Optional<IFilter> filter = groups.isEmpty () || groups.get (0).getSampleZones ().isEmpty () ? Optional.empty () : groups.get (0).getSampleZones ().get (0).getFilter ();
                if (filter.isPresent ())
                {
                    final double cents = OmnisphereLfoUtils.cutoffAmountToCents (parseFloatAttribute (filterElement, "freq", 1.0f), amount);
                    applyLfo (filter.get ().getCutoffLfoModulator (), lfoElement, cents / IEnvelope.MAX_ENVELOPE_DEPTH, lfoDepth, wheelAmount);
                }
            }
            else if (target.equals (layer + " atrm"))
            {
                final double db = OmnisphereLfoUtils.amplitudeAmountToDb (parseFloatAttribute (oscElement, "atrm", 1.0f), amount);
                final double depth = Math.min (1.0, db / ILfoModulator.MAX_VOLUME_DEPTH);
                for (final IGroup group: groups)
                    for (final ISampleZone zone: group.getSampleZones ())
                        applyLfo (zone.getAmplitudeLfoModulator (), lfoElement, depth, lfoDepth, wheelAmount);
            }
        }
    }


    /**
     * Set the depths of the LFO modulator and the settings of its LFO.
     *
     * @param modulator The LFO modulator
     * @param lfoElement The LFO element
     * @param depth The modulation depth with the full LFO depth
     * @param lfoDepth The depth of the LFO in the range of [0..1]
     * @param wheelAmount The amount of the LFO depth which is added by the modulation wheel in the
     *            range of [0..1]
     */
    private static void applyLfo (final ILfoModulator modulator, final Element lfoElement, final double depth, final double lfoDepth, final double wheelAmount)
    {
        modulator.setDepth (Math.clamp (depth * lfoDepth, -1.0, 1.0));
        modulator.getDepthModWheelModulator ().setDepth (Math.clamp (depth * wheelAmount, -1.0, 1.0));

        final ILfo lfo = modulator.getSource ();
        lfo.setWaveform (OmnisphereLfoUtils.getWaveform (parseFloatAttribute (lfoElement, "type", 0)));
        // A tempo synchronized rate (attribute 'sync') is read like an unsynchronized one
        lfo.setRate (OmnisphereLfoUtils.normalizedToHertz (parseFloatAttribute (lfoElement, "rate", 0.18f)));
        lfo.setStartPhase (Math.clamp (parseFloatAttribute (lfoElement, "phase", 0), 0.0, 1.0));
        lfo.setKeySync (parseFloatAttribute (lfoElement, "resettr", 0) > 0);
    }


    /**
     * Get the sum of the amounts of all modulation wheel routings to the given target.
     *
     * @param modMatrixElement The modulation matrix element
     * @param target The target, e.g. 'LFO1 swing'
     * @return The summed amount
     */
    private static double getWheelAmount (final Element modMatrixElement, final String target)
    {
        double amount = 0;
        for (int row = 0; modMatrixElement.hasAttribute (SOURCE_TAG + row); row++)
            if ("Wheel".equals (modMatrixElement.getAttribute (SOURCE_TAG + row)) && target.equals (modMatrixElement.getAttribute (TARGET_TAG + row)) && !isMuted (modMatrixElement, row))
                amount += parseFloatAttribute (modMatrixElement, AMOUNT_TAG + row, 0);
        return Math.max (0, amount);
    }


    /**
     * Check if a row of the modulation matrix is muted.
     *
     * @param modMatrixElement The modulation matrix element
     * @param row The index of the row
     * @return True if muted
     */
    private static boolean isMuted (final Element modMatrixElement, final int row)
    {
        return parseFloatAttribute (modMatrixElement, "mute" + row, 0) > 0;
    }


    /**
     * Parse the number of an LFO source, e.g. 'LFO3'.
     *
     * @param source The source
     * @return The number of the LFO or -1 if it is not a valid LFO source
     */
    private static int parseLfoNumber (final String source)
    {
        try
        {
            return Integer.parseInt (source.substring (3).trim ());
        }
        catch (final NumberFormatException _)
        {
            return -1;
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The file
     * @param content The content of the file
     * @param userSampleFolder The user sample folder
     * @return The result
     */
    private Optional<IMultisampleSource> parseZmapFile (final File multiSampleFile, final String content, final File userSampleFolder)
    {
        if (this.waitForDelivery ())
            return Optional.empty ();

        try
        {
            final Document document = OmnisphereAggregatedFile.parseXml (content);
            return this.parseZmapDescription (multiSampleFile, document, userSampleFolder);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex, false);
        }
        return Optional.empty ();
    }


    /**
     * Process the ZMAP file and the related wave (*.db) files.
     *
     * @param sourceFile The multi-sample file
     * @param document The metadata XML document
     * @param userSampleFolder The user sample folder
     * @return The parsed multi-sample source
     * @throws IOException Could not parse the description
     */
    private Optional<IMultisampleSource> parseZmapDescription (final File sourceFile, final Document document, final File userSampleFolder) throws IOException
    {
        final Element instrumentElement = document.getDocumentElement ();
        if (!"InstrumentMultisample".equals (instrumentElement.getNodeName ()))
        {
            this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Unknown Root");
            return Optional.empty ();
        }

        final String multiSampleName = FileUtils.getNameWithoutType (sourceFile.getName ());
        final File parentFolder = sourceFile.getParentFile ();
        final String [] parts = AudioFileUtils.createPathParts (parentFolder, userSampleFolder, multiSampleName);
        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (sourceFile, parts, multiSampleName);

        createMetadata (multisampleSource.getMetadata (), instrumentElement.getAttribute ("ATTRIB_VALUE_DATA"));

        final List<ISampleZone> sampleZones = new ArrayList<> ();
        for (final Element zoneElement: XMLUtils.getChildElementsByName (instrumentElement, "MultisampleZone", false))
        {
            final int minPitch = XMLUtils.getIntegerAttribute (zoneElement, "MinPitch", 0);
            final int maxPitch = XMLUtils.getIntegerAttribute (zoneElement, "MaxPitch", 127);
            final ISampleZone sampleZone = new DefaultSampleZone (multiSampleName, minPitch, maxPitch);
            sampleZone.setVelocityLow (XMLUtils.getIntegerAttribute (zoneElement, "MinVelocity", 0));
            sampleZone.setVelocityHigh (XMLUtils.getIntegerAttribute (zoneElement, "MaxVelocity", 127));
            sampleZone.setKeyRoot (XMLUtils.getIntegerAttribute (zoneElement, "HitKind", 60));

            sampleZone.setGain (linearToDb (parseHexFloat (zoneElement.getAttribute ("Volume"), 1f)));
            sampleZone.setTuning (toSemitones (parseHexFloat (zoneElement.getAttribute ("Pitch"), 1f)));

            final Element soundGroupElement = XMLUtils.getChildElementByName (zoneElement, "SoundGroupWithNames");
            if (soundGroupElement == null)
            {
                this.notifier.logError (IDS_NOTIFY_ERR_BAD_METADATA_FILE, "Missing SoundGroupWithNames tag");
                return Optional.empty ();
            }

            @SuppressWarnings("unused")
            final int underKitSession = XMLUtils.getIntegerAttribute (soundGroupElement, "UnderKitSession", 0);
            @SuppressWarnings("unused")
            final String libraryName = soundGroupElement.getAttribute ("LibraryName");
            final String sampledInstrumentName = soundGroupElement.getAttribute ("SampledInstrumentName");
            sampleZone.setName (sampledInstrumentName);

            sampleZones.add (sampleZone);
        }
        if (sampleZones.isEmpty ())
            return Optional.empty ();

        final List<IGroup> groups = this.detectAndLoadSamples (parentFolder, sampleZones);
        if (groups.isEmpty ())
            return Optional.empty ();
        multisampleSource.setGroups (groups);

        final IMetadata metadata = multisampleSource.getMetadata ();
        createMetadata (this.settingsConfiguration, metadata, AbstractDetector.getFirstSample (groups), parts);
        updateCreationDateTime (metadata, sourceFile);

        return Optional.of (multisampleSource);
    }


    private static void createMetadata (final IMetadata metadata, final String metadataString)
    {
        if (metadataString == null)
            return;
        final Map<String, ArrayList<String>> properties = NameValueParser.parse (metadataString);

        final List<String> keywords = new ArrayList<> ();
        final List<String> techniqueKeywords = properties.get ("Technique");
        if (techniqueKeywords != null)
            keywords.addAll (techniqueKeywords);
        final List<String> timbreKeywords = properties.get ("Timbre");
        if (timbreKeywords != null)
            keywords.addAll (timbreKeywords);
        if (!keywords.isEmpty ())
            metadata.setKeywords (keywords.toArray (new String [keywords.size ()]));

        final List<String> authors = properties.get ("Author");
        if (authors != null && !authors.isEmpty ())
            metadata.setCreator (authors.get (0));

        final List<String> types = properties.get ("Type");
        if (types != null)
            metadata.setCategory (TagDetector.detectCategory (types));

        // Build description
        final StringBuilder sb = new StringBuilder ();
        final List<String> descriptions = properties.get ("Description");
        if (descriptions != null && !descriptions.isEmpty ())
        {
            final String description = descriptions.get (0).trim ();
            sb.append (description);
            if (!description.endsWith ("."))
                sb.append ('.');
        }
        final List<String> specs = properties.get ("Specs");
        if (specs != null && !specs.isEmpty ())
        {
            final String specification = specs.get (0).trim ();
            if (!specification.isBlank ())
            {
                if (!sb.isEmpty ())
                    sb.append (' ');
                sb.append (specification);
                if (!specification.endsWith ("."))
                    sb.append ('.');
            }
        }

        final List<String> url = properties.get ("URL");
        if (url != null && !url.isEmpty ())
        {
            final String locator = url.get (0).trim ();
            if (!locator.isBlank ())
            {
                if (!sb.isEmpty ())
                    sb.append (' ');
                sb.append ("URL: ").append (locator);
            }
        }

        if (!sb.isEmpty ())
            metadata.setDescription (sb.toString ());
    }


    private List<IGroup> detectAndLoadSamples (final File parentFolder, final List<ISampleZone> sampleZones) throws IOException
    {
        final List<IGroup> groups = new ArrayList<> ();
        final IGroup defaultGroup = new DefaultGroup ();

        for (final ISampleZone sampleZone: sampleZones)
        {
            final String sampleName = sampleZone.getName ();
            final File sampleFile = new File (parentFolder, sampleName + ".db");
            if (!sampleFile.exists ())
                throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, sampleFile.getName ()));

            final OmnisphereAggregatedFile aggregatedFile = new OmnisphereAggregatedFile ();
            aggregatedFile.read (sampleFile);

            // There can be multiple WAV files for round-robin! Just read all WAVs -> the current
            // SampleZone needs to be duplicated and all of them need to be added to a group
            final Map<String, byte []> wavFiles = aggregatedFile.getWavFiles ();
            if (wavFiles.isEmpty ())
                throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, sampleName + ".wav"));

            // First assign pitch on/off, otherwise we would need to do it for all of the copied
            // sample-zones
            final Collection<Document> sampledInstrumentXmlFiles = aggregatedFile.getXmlFiles ("SampledInstrument").values ();
            if (sampledInstrumentXmlFiles.isEmpty ())
                throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, sampleFile.getName ()));
            final Document sampledInstrumentXmlDocument = sampledInstrumentXmlFiles.iterator ().next ();
            final Element sampledInstrumentXmlRootElement = sampledInstrumentXmlDocument.getDocumentElement ();
            sampleZone.setKeyTracking (XMLUtils.getIntegerAttribute (sampledInstrumentXmlRootElement, "PitchedInstr", 1) > 0 ? 1.0 : 0);

            // Process round-robin (= stack) XML documents
            for (final Entry<String, Document> xmlFiles: aggregatedFile.getXmlFiles ("LayerHitStack").entrySet ())
            {
                final String xmlFilename = xmlFiles.getKey ();
                final Document xmlDocument = xmlFiles.getValue ();
                final Element layerHitStackElement = xmlDocument.getDocumentElement ();
                if (layerHitStackElement == null)
                    continue;
                final Element hitVelocityElement = XMLUtils.getChildElementByName (layerHitStackElement, "HitVelocity");
                if (hitVelocityElement == null)
                    continue;

                final List<ISampleZone> roundRobinZones = new ArrayList<> ();
                final List<Element> waveformTags = XMLUtils.getChildElementsByName (hitVelocityElement, "SampleWaveform");
                for (final Element sampleWaveformElement: waveformTags)
                {
                    final String wavFileName = new File (sampleWaveformElement.getAttribute ("AudioFilePath")).getName ();
                    final byte [] wavFileData = wavFiles.get (wavFileName);
                    if (wavFileData == null)
                        throw new IOException (Functions.getMessage (IDS_NOTIFY_ERR_SAMPLE_FILE_NOT_FOUND, wavFileName));

                    final ISampleZone rrSampleZone = new DefaultSampleZone (sampleZone);
                    rrSampleZone.setName (FileUtils.getNameWithoutType (wavFileName));
                    try
                    {
                        final ISampleData sampleData = new WavFileSampleData (new ByteArrayInputStream (wavFileData));
                        sampleData.addZoneData (rrSampleZone, false, true);
                        rrSampleZone.setSampleData (sampleData);
                        roundRobinZones.add (rrSampleZone);
                    }
                    catch (final IOException ex)
                    {
                        String localizedMessage = ex.getLocalizedMessage ();
                        if (localizedMessage == null || localizedMessage.isBlank ())
                            localizedMessage = ex.getClass ().getName ();
                        this.notifier.logError ("IDS_OMNISPHERE_ERR_READING_WAV", wavFileName, localizedMessage);
                    }

                    rrSampleZone.setKeyRoot (XMLUtils.getIntegerAttribute (sampleWaveformElement, "BaseNote", 60));

                    // Level & A440 attributes not used

                    if (waveformTags.size () > 1)
                    {
                        rrSampleZone.setSequencePosition (XMLUtils.getIntegerAttribute (sampleWaveformElement, "RoundRobinSequenceNum", 0) + 1);
                        rrSampleZone.setPlayLogic (PlayLogic.ROUND_ROBIN);
                    }
                }

                if (roundRobinZones.isEmpty ())
                    continue;
                if (roundRobinZones.size () == 1)
                    defaultGroup.addSampleZone (roundRobinZones.get (0));
                else
                {
                    final IGroup rrGroup = new DefaultGroup (roundRobinZones);
                    groups.add (rrGroup);
                    rrGroup.setName (FileUtils.getNameWithoutType (xmlFilename));
                }
            }
        }

        if (!defaultGroup.getSampleZones ().isEmpty ())
            groups.add (defaultGroup);

        return groups;
    }


    private static double parseSlopeAttribute (final Element element)
    {
        return XMLUtils.getDoubleAttribute (element, "c", 0);
    }


    private static double parseTimeAttribute (final Element element, final String attributeName, final float defaultValue)
    {
        if (element == null)
            return defaultValue;
        final float normalizedTime = parseFloatAttribute (element, attributeName, defaultValue);
        return normalizedTime * normalizedTime * 20.0;
    }


    private static float parseFloatAttribute (final Element element, final String attributeName, final float defaultValue)
    {
        if (element == null)
            return defaultValue;
        return parseHexFloat (element.getAttribute (attributeName), defaultValue);
    }


    private static float parseHexFloat (final String hex, final float defaultValue)
    {
        if (hex == null || hex.isBlank ())
            return defaultValue;
        // Parse hex string as unsigned 32-bit integer and reinterpret bits as float
        final float value = Float.intBitsToFloat ((int) Long.parseLong (hex, 16));
        return Float.isNaN (value) ? defaultValue : value;
    }


    private static Optional<File> findUserSampleFolder (final File presetFile)
    {
        File folder = presetFile;
        while ((folder = folder.getParentFile ()) != null)
        {
            final Set<String> children = new HashSet<> ();
            Collections.addAll (children, folder.list ());
            if (children.contains ("Soundsources"))
            {
                final File userSampleFolder = new File (new File (folder, "Soundsources"), "User");
                return userSampleFolder.exists () ? Optional.of (userSampleFolder) : Optional.empty ();
            }
        }

        // If the required structure is not present check if the files are in the same folder
        boolean hasDbFile = false;
        boolean hasZmapFile = false;
        final File parentFile = presetFile.getParentFile ();
        for (final File file: parentFile.listFiles ())
        {
            if (!file.isFile ())
                continue;
            if (file.getName ().endsWith (".db"))
                hasDbFile = true;
            if (file.getName ().endsWith (FILE_ENDING_ZMAP))
                hasZmapFile = true;
            if (hasDbFile && hasZmapFile)
                return Optional.of (parentFile);
        }

        return Optional.empty ();
    }


    private static final double LOG2 = Math.log (2.0);


    private static double toSemitones (final double value)
    {
        return -12.0 * (Math.log (value) / LOG2);
    }


    /**
     * Converts linear amplitude to dB. Returns -Infinity for 0 input.
     *
     * @param linear The linear value
     * @return The absolute dB value
     */
    public static double linearToDb (final double linear)
    {
        if (linear <= 0.0)
            return Double.NEGATIVE_INFINITY;
        return 20.0 * Math.log10 (linear);
    }
}
