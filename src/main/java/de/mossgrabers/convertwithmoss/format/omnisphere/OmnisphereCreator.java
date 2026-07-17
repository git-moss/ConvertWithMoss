// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.omnisphere;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IEnvelopeModulator;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.PlayLogic;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.settings.EmptySettingsUI;
import de.mossgrabers.convertwithmoss.core.utils.HTMLUtils;
import de.mossgrabers.convertwithmoss.exception.ParseException;
import de.mossgrabers.convertwithmoss.file.wav.WaveFile;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;


/**
 * Creator for Omnisphere multi-sample ZMAP/DB files. A ZMAP file is a description file encoded in
 * XML. The related samples are stored in DB files in the same folder.
 *
 * @author Jürgen Moßgraber
 */
public class OmnisphereCreator extends AbstractCreator<EmptySettingsUI>
{
    private static final String                    LEVEL                 = "Level";
    private static final String                    ATTRIB_VALUE_DATA_TAG = "ATTRIB_VALUE_DATA";
    private static final String                    DEFAULT_3F800000      = "3f800000";
    private static final String                    DEFAULT_3F000000      = "3f000000";
    private static final String                    HTML_RETURN           = "&#13;";
    private static final String                    TEMPLATE              = "de/mossgrabers/convertwithmoss/templates/prt_omn/Template.xml";

    private static final Map<String, List<String>> ATTRIBUTE_ORDER       = new HashMap<> ();
    static
    {
        final List<String> hitVelocityList = new ArrayList<> ();
        Collections.addAll (hitVelocityList, LEVEL, "Minimum", "Maximum");
        ATTRIBUTE_ORDER.put ("HitVelocity", hitVelocityList);
        final List<String> sampleWaveformList = new ArrayList<> ();
        Collections.addAll (sampleWaveformList, "RoundRobinSequenceNum", "BaseNote", "AudioFilePath", LEVEL, "A440");
        ATTRIBUTE_ORDER.put ("SampleWaveform", sampleWaveformList);
        final List<String> sampledInstrumentList = new ArrayList<> ();
        Collections.addAll (sampledInstrumentList, ATTRIB_VALUE_DATA_TAG, "PitchedInstr", LEVEL);
        ATTRIBUTE_ORDER.put ("SampledInstrument", sampledInstrumentList);
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public OmnisphereCreator (final INotifier notifier)
    {
        super ("Spectrasonics Omnisphere 3", "Omnisphere", notifier, EmptySettingsUI.INSTANCE);
    }


    /** {@inheritDoc} */
    @Override
    public void createPreset (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String multiSampleName = createSafeFilename (multisampleSource.getName ());
        final File destFolder = new File (destinationFolder, multiSampleName).getAbsoluteFile ();
        if (!destFolder.exists () && !destFolder.mkdirs ())
            throw new IOException (Functions.getMessage ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", destFolder.getAbsolutePath ()));

        final File multiFile = this.createUniqueFilename (destFolder, multiSampleName, "zmap");
        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath () + " / .prt_omn");

        // Store all samples
        safeCreateDirectory (destFolder);
        final long size = this.writeSamples (destFolder, multisampleSource, multisampleSource.getAllSampleZones (false));

        final Optional<String> zmapXml = this.createZmapDocument (multisampleSource, size);
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            if (!zmapXml.isEmpty ())
                writer.write (zmapXml.get ());
        }

        this.createPresetFile (destFolder, FileUtils.getNameWithoutType (multiFile), multisampleSource);

        this.progress.notifyDone ();
    }


    private void createPresetFile (final File destFolder, final String filename, final IMultisampleSource multisampleSource)
    {
        try
        {
            String text = Functions.textFileFor (TEMPLATE);

            final ISampleZone firstSampleZone = multisampleSource.getAllSampleZones (true).get (0);

            text = text.replace ("%PRESET_NAME%", XMLUtils.escapeAttribute (multisampleSource.getName ()));
            text = text.replace ("%PRESET_METADATA%", XMLUtils.escapeAttribute (createMetadata (multisampleSource.getMetadata ()) + ";Osc Type=Sample Only;Soundsource=" + filename + ";"));
            text = text.replace ("%PITCHBEND_UP%", toHexFloat (Math.abs (firstSampleZone.getBendUp ()) / 5000.0));
            text = text.replace ("%PITCHBEND_DOWN%", toHexFloat (Math.abs (firstSampleZone.getBendDown ()) / 5000.0));
            text = text.replace ("%SOUND_SOURCE%", filename);
            text = text.replace ("%KEY_TRACKING%", firstSampleZone.getKeyTracking () > 0 ? DEFAULT_3F800000 : "0");

            final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
            if (globalFilter.isEmpty ())
            {
                text = text.replace ("%FILTER_TYPE%", toHexFloat (OmnisphereFilterUtils.getFilterIndex (new DefaultFilter (FilterType.LOW_PASS, 4, 0, 0))));
                text = text.replace ("%FILTER_IS_ACTIVE%", "0");
                text = text.replace ("%FILTER_FREQ%", DEFAULT_3F800000);
                text = text.replace ("%FILTER_RES%", "0");

                text = text.replace ("%FENV_ATTACK_SEC%", "0");
                text = text.replace ("%FENV_DECAY_SEC%", "0");
                text = text.replace ("%FENV_RELEASE_SEC%", "0");

                text = text.replace ("%FENV_ATTACK%", "0");
                text = text.replace ("%FENV_HOLD%", "3e8fb823");
                text = text.replace ("%FENV_DECAY%", "3e8fb823");
                text = text.replace ("%FENV_SUSTAIN%", DEFAULT_3F800000);
                text = text.replace ("%FENV_RELEASE%", "3d5794a3");

                text = text.replace ("%FENV_ATTACK_SLOPE%", DEFAULT_3F000000);
                text = text.replace ("%FENV_DECAY_SLOPE%", DEFAULT_3F000000);
                text = text.replace ("%FENV_RELEASE_SLOPE%", DEFAULT_3F000000);

                text = text.replace ("%FENV_VEL_SENS%", DEFAULT_3F000000);
                text = text.replace ("%FENV_DEPTH%", DEFAULT_3F800000);

                text = text.replace ("%FILTER_KEY%", "0");
                text = text.replace ("%FILTER_KEY_INV%", "0");
            }
            else
            {
                final IFilter filter = globalFilter.get ();
                final IEnvelopeModulator cutoffEnvelopeModulator = filter.getCutoffEnvelopeModulator ();
                final IEnvelope cutoffEnvelope = cutoffEnvelopeModulator.getSource ();
                text = text.replace ("%FILTER_TYPE%", toHexFloat (OmnisphereFilterUtils.getFilterIndex (filter)));
                text = text.replace ("%FILTER_IS_ACTIVE%", DEFAULT_3F800000);
                text = text.replace ("%FILTER_FREQ%", toHexFloat (OmnisphereFilterUtils.hertzToNormalized (filter.getCutoff ())));
                text = text.replace ("%FILTER_RES%", toHexFloat (filter.getResonance ()));

                text = text.replace ("%FENV_ATTACK_SEC%", toHexFloat (cutoffEnvelope.getAttackTime () / 100.0));
                text = text.replace ("%FENV_DECAY_SEC%", toHexFloat ((Math.max (0, cutoffEnvelope.getHoldTime ()) + Math.max (0, cutoffEnvelope.getDecayTime ())) / 100.0));
                text = text.replace ("%FENV_RELEASE_SEC%", toHexFloat (cutoffEnvelope.getReleaseTime () / 100.0));

                text = text.replace ("%FENV_ATTACK%", toTime (cutoffEnvelope.getAttackTime ()));
                text = text.replace ("%FENV_HOLD%", toTime (cutoffEnvelope.getHoldTime ()));
                text = text.replace ("%FENV_DECAY%", toTime (cutoffEnvelope.getDecayTime ()));
                text = text.replace ("%FENV_SUSTAIN%", toHexFloat (cutoffEnvelope.getSustainLevel ()));
                text = text.replace ("%FENV_RELEASE%", toTime (cutoffEnvelope.getReleaseTime ()));
                text = text.replace ("%FENV_ATTACK_SLOPE%", toSlope (cutoffEnvelope.getAttackSlope ()));
                text = text.replace ("%FENV_DECAY_SLOPE%", toSlope (cutoffEnvelope.getDecaySlope ()));
                text = text.replace ("%FENV_RELEASE_SLOPE%", toSlope (cutoffEnvelope.getReleaseSlope ()));

                text = text.replace ("%FENV_VEL_SENS%", toHexFloat (filter.getCutoffVelocityModulator ().getDepth ()));
                // The range is 0..1 but it seems like 0.5 already opens the filter completely
                text = text.replace ("%FENV_DEPTH%", toHexFloat (cutoffEnvelopeModulator.getDepth () / 2.0));

                final double cutoffKeyTracking = filter.getCutoffKeyTracking ();
                text = text.replace ("%FILTER_KEY%", toHexFloat (Math.abs (cutoffKeyTracking)));
                text = text.replace ("%FILTER_KEY_INV%", toHexFloat (cutoffKeyTracking < 0 ? 1.0 : 0.0));
            }

            // Amplitude envelope
            final IEnvelope ampEnvelope = firstSampleZone.getAmplitudeEnvelopeModulator ().getSource ();
            text = text.replace ("%AMP_ATTACK_SEC%", toHexFloat (ampEnvelope.getAttackTime () / 100.0));
            text = text.replace ("%AMP_DECAY_SEC%", toHexFloat ((Math.max (0, ampEnvelope.getHoldTime ()) + Math.max (0, ampEnvelope.getDecayTime ())) / 100.0));
            text = text.replace ("%AMP_RELEASE_SEC%", toHexFloat (ampEnvelope.getReleaseTime () / 100.0));
            text = text.replace ("%AMP_ATTACK%", toTime (ampEnvelope.getAttackTime ()));
            text = text.replace ("%AMP_HOLD%", toTime (ampEnvelope.getHoldTime ()));
            text = text.replace ("%AMP_DECAY%", toTime (ampEnvelope.getDecayTime ()));
            text = text.replace ("%AMP_SUSTAIN%", toHexFloat (ampEnvelope.getSustainLevel ()));
            text = text.replace ("%AMP_RELEASE%", toTime (ampEnvelope.getReleaseTime ()));
            text = text.replace ("%AMP_ATTACK_SLOPE%", toSlope (ampEnvelope.getAttackSlope ()));
            text = text.replace ("%AMP_DECAY_SLOPE%", toSlope (ampEnvelope.getDecaySlope ()));
            text = text.replace ("%AMP_RELEASE_SLOPE%", toSlope (ampEnvelope.getReleaseSlope ()));
            text = text.replace ("%AMP_VEL_SENS%", toHexFloat (firstSampleZone.getAmplitudeVelocityModulator ().getDepth ()));

            // Pitch Envelope
            final IEnvelopeModulator pitchEnvelopeModulator = firstSampleZone.getPitchEnvelopeModulator ();
            final IEnvelope modEnvelope = pitchEnvelopeModulator.getSource ();
            final double pitchEnvelopeDepth = pitchEnvelopeModulator.getDepth ();
            final boolean hasModEnvelope = pitchEnvelopeDepth > 0;
            text = text.replace ("%PITCH_MOD_SOURCE%", hasModEnvelope ? "ModEnv1" : "off");
            text = text.replace ("%PITCH_MOD_TARGET%", hasModEnvelope ? "A tune" : "off");
            // 0..1 ~ -4800..4800
            text = text.replace ("%PITCH_MOD_AMOUNT%", toHexFloat ((pitchEnvelopeDepth + 1.0) / 2.0));
            text = text.replace ("%PITCH_MOD_INTENSITY%", toHexFloat (1.0));

            // Stored in MODENV
            text = text.replace ("%MOD_ATTACK_SEC%", toHexFloat (modEnvelope.getAttackTime () / 100.0));
            text = text.replace ("%MOD_DECAY_SEC%", toHexFloat ((Math.max (0, modEnvelope.getHoldTime ()) + Math.max (0, modEnvelope.getDecayTime ())) / 100.0));
            text = text.replace ("%MOD_RELEASE_SEC%", toHexFloat (modEnvelope.getReleaseTime () / 100.0));
            text = text.replace ("%MOD_ATTACK_SLOPE%", toSlope (modEnvelope.getAttackSlope ()));
            text = text.replace ("%MOD_DECAY_SLOPE%", toSlope (modEnvelope.getDecaySlope ()));
            text = text.replace ("%MOD_RELEASE_SLOPE%", toSlope (modEnvelope.getReleaseSlope ()));
            // Stored in MODENVPARAMS
            text = text.replace ("%MOD_ATTACK%", toTime (modEnvelope.getAttackTime ()));
            text = text.replace ("%MOD_HOLD%", toTime (modEnvelope.getHoldTime ()));
            text = text.replace ("%MOD_DECAY%", toTime (modEnvelope.getDecayTime ()));
            text = text.replace ("%MOD_SUSTAIN%", toHexFloat (modEnvelope.getSustainLevel ()));
            text = text.replace ("%MOD_RELEASE%", toTime (modEnvelope.getReleaseTime ()));

            final File presetFile = new File (destFolder, filename + ".prt_omn");
            Files.write (presetFile.toPath (), text.getBytes (StandardCharsets.UTF_8));
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ex);
        }
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @param size The size of all DB files without their headers
     * @return The XML structure
     */
    private Optional<String> createZmapDocument (final IMultisampleSource multisampleSource, final long size)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("InstrumentMultisample");
        document.appendChild (rootElement);
        rootElement.setAttribute (ATTRIB_VALUE_DATA_TAG, createMetadata (multisampleSource.getMetadata ()) + "Size=" + size + ";");

        // Add all sample zones
        for (final ISampleZone sampleZone: multisampleSource.getAllSampleZones (false))
        {
            final Element zoneElement = XMLUtils.addElement (document, rootElement, "MultisampleZone");
            zoneElement.setAttribute ("MinPitch", Integer.toString (sampleZone.getKeyLow ()));
            zoneElement.setAttribute ("MaxPitch", Integer.toString (sampleZone.getKeyHigh ()));
            zoneElement.setAttribute ("MinVelocity", Integer.toString (sampleZone.getVelocityLow ()));
            zoneElement.setAttribute ("MaxVelocity", Integer.toString (sampleZone.getVelocityHigh ()));
            zoneElement.setAttribute ("HitKind", Integer.toString (sampleZone.getKeyRoot ()));

            zoneElement.setAttribute ("Volume", toHexFloat (dbToLinear (sampleZone.getGain ())));
            zoneElement.setAttribute ("Pitch", toHexFloat (fromCents (sampleZone.getTuning ())));

            final Element soundGroupElement = XMLUtils.addElement (document, zoneElement, "SoundGroupWithNames");
            soundGroupElement.setAttribute ("UnderKitSession", "0");
            soundGroupElement.setAttribute ("LibraryName", ".");
            soundGroupElement.setAttribute ("SampledInstrumentName", createSafeFilename (sampleZone.getName ()));
        }

        return Optional.of (OmnisphereXmlUtil.documentToXmlString (document, false, null));
    }


    private static String createMetadata (final IMetadata metadata)
    {
        final StringBuilder sb = new StringBuilder ();

        for (final String keyword: metadata.getKeywords ())
            sb.append ("Timbre=").append (keyword).append (';');

        final String creator = metadata.getCreator ();
        if (!creator.isBlank ())
            sb.append ("Author=").append (creator).append (';');

        final String type = metadata.getCategory ();
        if (!type.isBlank ())
            sb.append ("Type=").append (type).append (';');

        final String description = metadata.getDescription ();
        if (!description.isBlank ())
        {
            final Optional<String> html = HTMLUtils.unicodeToHTML (description);
            if (html.isPresent ())
                sb.append ("Description=").append (html.get ().replace ("\r\n", HTML_RETURN).replace ("\r", HTML_RETURN).replace ("\n", HTML_RETURN)).append (';');
        }

        return sb.toString ();
    }


    protected long writeSamples (final File sampleFolder, final IMultisampleSource multisampleSource, final List<ISampleZone> sampleZones) throws IOException
    {
        long overallDbFileSize = 0;
        for (int zoneIndex = 0; zoneIndex < sampleZones.size (); zoneIndex++)
        {
            if (this.isCancelled ())
                return 0;

            final OmnisphereAggregatedFile aggregator = new OmnisphereAggregatedFile ();

            final ISampleZone sampleZone = sampleZones.get (zoneIndex);

            this.progress.notifyProgress ();

            // LinkedHashMap guarantees order
            final byte [] wavFileData = this.serializeWavFile (multisampleSource, sampleZone);
            aggregator.addFile (this.createSampleFilename (sampleZone, -1, ".wav"), wavFileData);

            try
            {
                aggregator.addXmlFile ("HitBundle.xml", this.createHitBundleDocument (sampleZone));
                // Round-robin groups could be aggregated to Sub-layers but there are only 4!
                aggregator.addXmlFile ("Layer.xml", this.createLayerHitStackDocument (Collections.singletonList (sampleZone)), ATTRIBUTE_ORDER);
                aggregator.addXmlFile ("SampledInstrument.xml", this.createSampledInstrumentDocument (sampleZone), ATTRIBUTE_ORDER);
                overallDbFileSize += aggregator.write (new File (sampleFolder, this.createSampleFilename (sampleZone, zoneIndex, ".db")));
            }
            catch (final NoSuchFileException | FileNotFoundException | TransformerException ex)
            {
                this.notifier.logError ("IDS_NOTIFY_FILE_NOT_FOUND", ex);
            }
        }
        return overallDbFileSize;
    }


    private Document createHitBundleDocument (final ISampleZone sampleZone) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("HitBundle");
        document.appendChild (rootElement);
        rootElement.setAttribute ("BundleKind", Integer.toString (sampleZone.getKeyRoot ()));
        rootElement.setAttribute (LEVEL, DEFAULT_3F800000);

        return document;
    }


    /**
     * Adds 1 sample zone or multiple in case of round-robin to a layer hit stack document.
     *
     * @param sampleZones The round-robin sample zones
     * @return The document
     * @throws IOException Could not create the document
     */
    private Document createLayerHitStackDocument (final List<ISampleZone> sampleZones) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("LayerHitStack");
        document.appendChild (rootElement);
        rootElement.setAttribute (LEVEL, DEFAULT_3F800000);

        final Element hitVelocityElement = XMLUtils.addElement (document, rootElement, "HitVelocity");
        hitVelocityElement.setAttribute (LEVEL, DEFAULT_3F800000);
        // No idea about this value but the sample is not found if it is set to the minimum velocity
        hitVelocityElement.setAttribute ("Minimum", "0");
        hitVelocityElement.setAttribute ("Maximum", Integer.toString (sampleZones.get (0).getVelocityHigh ()));

        for (final ISampleZone sampleZone: sampleZones)
        {
            final Element sampleWaveformElement = XMLUtils.addElement (document, hitVelocityElement, "SampleWaveform");
            final String roundRobinIndex = sampleZone.getPlayLogic () == PlayLogic.ROUND_ROBIN ? Integer.toString (sampleZone.getSequencePosition () - 1) : "0";
            sampleWaveformElement.setAttribute ("RoundRobinSequenceNum", roundRobinIndex);
            sampleWaveformElement.setAttribute ("BaseNote", Integer.toString (sampleZone.getKeyRoot ()));
            sampleWaveformElement.setAttribute ("AudioFilePath", this.createSampleFilename (sampleZone, -1, ".wav"));
            sampleWaveformElement.setAttribute (LEVEL, DEFAULT_3F800000);
            sampleWaveformElement.setAttribute ("A440", DEFAULT_3F800000);
        }

        return document;
    }


    private Document createSampledInstrumentDocument (final ISampleZone sampleZone) throws IOException
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            throw new IOException ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement ("SampledInstrument");
        document.appendChild (rootElement);
        rootElement.setAttribute (ATTRIB_VALUE_DATA_TAG, "");
        rootElement.setAttribute ("PitchedInstr", sampleZone.getKeyTracking () == 0 ? "0" : "1");
        rootElement.setAttribute (LEVEL, DEFAULT_3F800000);

        return document;
    }


    private byte [] serializeWavFile (final IMultisampleSource multisampleSource, final ISampleZone zone) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream ();
        if (zone.getStart () > 0)
            this.rewriteFile (multisampleSource, zone, out, DESTINATION_FORMAT, true);
        else
        {
            final Optional<ISampleData> sampleData = zone.getSampleData ();
            if (sampleData.isEmpty ())
            {
                this.notifier.logError (IDS_NOTIFY_ERR_MISSING_SAMPLE_DATA, zone.getName (), zone.getName ());
                this.notifier.logText ("\n");
            }
            else
            {
                sampleData.get ().writeSample (out);
                final WaveFile wavFile = new WaveFile ();
                try
                {
                    // Make sure that the loops are up to date in the WAV file
                    wavFile.read (new ByteArrayInputStream (out.toByteArray ()), true);
                    AbstractWavCreator.updateSampleChunk (zone, wavFile);
                    out.reset ();
                    wavFile.write (out);
                }
                catch (final ParseException ex)
                {
                    throw new IOException (ex);
                }
            }
        }
        return out.toByteArray ();
    }


    private static String toSlope (final double slope)
    {
        return toHexFloat ((slope + 1.0) / 2.0);
    }


    private static String toTime (final double seconds)
    {
        return toHexFloat (Math.sqrt (seconds / 20.0));
    }


    private static String toHexFloat (final double value)
    {
        return Integer.toHexString (Float.floatToIntBits ((float) value));
    }


    private static double fromCents (final double cents)
    {
        return Math.pow (2.0, -cents / 1200.0);
    }


    /**
     * Converts dB gain (-INF to +24dB) to linear amplitude. 0 dB = 1.0 (unity gain).
     *
     * @param db The dB value
     * @return The relative value
     */
    public static double dbToLinear (final double db)
    {
        return Math.pow (10.0, db / 20.0);
    }
}