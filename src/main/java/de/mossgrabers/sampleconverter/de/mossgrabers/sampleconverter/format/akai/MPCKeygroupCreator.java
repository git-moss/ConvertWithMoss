// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.akai;

import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.ISampleMetadata;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.creator.AbstractCreator;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.transform.TransformerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Creator for Akai MPC keygroup files. Keygroups have a description file and related samples in one
 * folder. A keygroup program offers up to 128 keygroups, and each keygroup can hold up to four
 * samples (Layers 1–4). This is a total of 512 samples.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class MPCKeygroupCreator extends AbstractCreator
{
    private static final String DOUBLE_ZERO  = "0.000000";
    private static final String FILE_VERSION = "2.1";
    private static final String APP_VERSION  = "v2.10";

    private static final double MINUS_12_DB  = 0.353000;
    private static final double PLUS_6_DB    = 1.0;
    private static final double VALUE_RANGE  = PLUS_6_DB - MINUS_12_DB;


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public MPCKeygroupCreator (final INotifier notifier)
    {
        super ("MPC Keygroup", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        final String sampleName = createSafeFilename (multisampleSource.getName ());

        // Store all samples and metadata file in one folder
        final File sampleFolder = new File (destinationFolder, sampleName);
        if (sampleFolder.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", sampleFolder.getAbsolutePath ());
            return;
        }
        safeCreateDirectory (sampleFolder);

        // Create the metadata file
        final File multiFile = new File (sampleFolder, sampleName + ".xpm");
        final Optional<String> metadata = this.createMetadata (multisampleSource, sampleName);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata.get ());
        }

        // Store all samples
        int outputCount = 0;
        final List<IVelocityLayer> sampleMetadata = multisampleSource.getSampleMetadata ();
        for (final IVelocityLayer layer: sampleMetadata)
        {
            for (final ISampleMetadata info: layer.getSampleMetadata ())
            {
                final Optional<String> filename = info.getUpdatedFilename ();
                if (filename.isEmpty ())
                    continue;
                String fn = filename.get ();
                // Ensure upper case WAV ending, which seems to be required
                if (fn.endsWith (".wav"))
                    fn = fn.substring (0, fn.length () - 4) + ".WAV";
                try (final FileOutputStream fos = new FileOutputStream (new File (sampleFolder, fn)))
                {
                    this.notifier.log ("IDS_NOTIFY_PROGRESS");
                    outputCount++;
                    if (outputCount % 80 == 0)
                        this.notifier.log ("IDS_NOTIFY_LINE_FEED");

                    info.writeSample (fos);
                }
            }
        }

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create the text of the description file.
     *
     * @param multisampleSource The multi-sample
     * @param sampleName The name of the multi sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final IMultisampleSource multisampleSource, final String sampleName)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element multisampleElement = document.createElement ("MPCVObject");
        document.appendChild (multisampleElement);
        final Element versionElement = document.createElement ("Version");
        XMLUtils.addTextElement (document, versionElement, "File_Version", FILE_VERSION);

        final Element programElement = createProgramElement (multisampleSource, document);
        multisampleElement.appendChild (programElement);
        final Element instrumentsElement = document.createElement ("Instruments");
        programElement.appendChild (instrumentsElement);

        final Map<String, List<Keygroup>> keygroupsMap = new HashMap<> ();

        for (final IVelocityLayer velocityLayer: multisampleSource.getSampleMetadata ())
        {
            for (final ISampleMetadata sampleMetadata: velocityLayer.getSampleMetadata ())
            {
                final Optional<Keygroup> keygroupOpt = getKeygroup (keygroupsMap, sampleMetadata, document, instrumentsElement);
                if (keygroupOpt.isEmpty ())
                {
                    this.notifier.logError ("IDS_MPC_MORE_THAN_4_LAYERS", Integer.toString (sampleMetadata.getKeyLow ()), Integer.toString (sampleMetadata.getKeyHigh ()), Integer.toString (sampleMetadata.getVelocityLow ()), Integer.toString (sampleMetadata.getVelocityHigh ()));
                    continue;
                }

                final Keygroup keygroup = keygroupOpt.get ();
                keygroup.addLayer (createLayerElement (sampleName, document, keygroup.getLayerCount (), sampleMetadata));
            }
        }

        final int size = calcInstrumentNumber (keygroupsMap) - 1;
        if (size > 128)
            this.notifier.logError ("IDS_MPC_MORE_THAN_128_KEYGROUPS", Integer.toString (size));
        XMLUtils.addTextElement (document, programElement, "KeygroupNumKeygroups", Integer.toString (size));

        try
        {
            return Optional.of (XMLUtils.toString (document));
        }
        catch (final TransformerException ex)
        {
            this.notifier.logError (ex);
            return Optional.empty ();
        }
    }


    private static Element createProgramElement (final IMultisampleSource multisampleSource, final Document document)
    {
        final Element programElement = document.createElement ("Program");
        programElement.setAttribute ("type", "Keygroup");
        XMLUtils.addTextElement (document, programElement, "ProgramName", multisampleSource.getName ());
        final Element programPadsElement = document.createElement ("ProgramPads-" + APP_VERSION);
        programElement.appendChild (programPadsElement);

        // Pitchbend 2 semitones up/down
        XMLUtils.addTextElement (document, programElement, "KeygroupPitchBendRange", "0.160000");

        // Vibrato on Modulation Wheel
        XMLUtils.addTextElement (document, programElement, "KeygroupWheelToLfo", "1.000000");
        return programElement;
    }


    private static Element createLayerElement (final String sampleName, final Document document, final int l, final ISampleMetadata sampleMetadata)
    {
        final Element layerElement = document.createElement ("Layer");
        layerElement.setAttribute ("number", Integer.toString (l + 1));

        XMLUtils.addTextElement (document, layerElement, "Active", "True");
        XMLUtils.addTextElement (document, layerElement, "Volume", Double.toString (convertGain (sampleMetadata.getGain ())));
        XMLUtils.addTextElement (document, layerElement, "Pan", "0.500000");
        XMLUtils.addTextElement (document, layerElement, "Pitch", DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, "TuneCoarse", "0");
        XMLUtils.addTextElement (document, layerElement, "TuneFine", Double.toString (sampleMetadata.getTune ()));
        XMLUtils.addTextElement (document, layerElement, "VelStart", Integer.toString (sampleMetadata.getVelocityLow ()));
        XMLUtils.addTextElement (document, layerElement, "VelEnd", Integer.toString (sampleMetadata.getVelocityHigh ()));

        final Optional<String> filename = sampleMetadata.getUpdatedFilename ();
        if (filename.isEmpty ())
            return layerElement;

        // Add the name of the multisample to the wave file to make it 'more unique' if
        // necessary
        String fn = filename.get ();
        if (!fn.startsWith (sampleName))
        {
            fn = sampleName + "_" + fn;
            sampleMetadata.setCombinedName (fn);
        }
        if (fn.endsWith (".wav"))
            fn = fn.substring (0, fn.length () - 4);

        XMLUtils.addTextElement (document, layerElement, "SampleStart", "0");
        XMLUtils.addTextElement (document, layerElement, "SampleEnd", "0");
        XMLUtils.addTextElement (document, layerElement, "LoopStart", "0");
        XMLUtils.addTextElement (document, layerElement, "LoopEnd", "0");
        XMLUtils.addTextElement (document, layerElement, "LoopCrossfadeLength", "0");
        XMLUtils.addTextElement (document, layerElement, "LoopTune", "0");
        // The root note is strangely one more then the lower upper keys!
        XMLUtils.addTextElement (document, layerElement, "RootNote", Integer.toString (sampleMetadata.getKeyRoot () + 1));
        XMLUtils.addTextElement (document, layerElement, "KeyTrack", "False");
        XMLUtils.addTextElement (document, layerElement, "SampleName", fn);
        XMLUtils.addTextElement (document, layerElement, "PitchRandom", DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, "VolumeRandom", DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, "PanRandom", DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, "OffsetRandom", DOUBLE_ZERO);
        XMLUtils.addTextElement (document, layerElement, "SampleFile", "");
        XMLUtils.addTextElement (document, layerElement, "SliceIndex", "129");
        XMLUtils.addTextElement (document, layerElement, "Direction", "0");
        XMLUtils.addTextElement (document, layerElement, "Offset", "0");
        XMLUtils.addTextElement (document, layerElement, "SliceStart", Integer.toString (sampleMetadata.getStart ()));
        XMLUtils.addTextElement (document, layerElement, "SliceEnd", Integer.toString (sampleMetadata.getStop ()));

        final List<SampleLoop> loops = sampleMetadata.getLoops ();
        if (loops.isEmpty ())
            return layerElement;

        // Format can store only 1 loop
        final SampleLoop sampleLoop = loops.get (0);
        XMLUtils.addTextElement (document, layerElement, "SliceLoopStart", Integer.toString (sampleLoop.getStart ()));
        XMLUtils.addTextElement (document, layerElement, "SliceLoop", sampleMetadata.isReversed () ? "3" : "1");
        XMLUtils.addTextElement (document, layerElement, "SliceLoopCrossFadeLength", Double.toString (sampleLoop.getCrossfade ()));
        XMLUtils.addTextElement (document, layerElement, "SliceTailPosition", "0.500000");
        XMLUtils.addTextElement (document, layerElement, "SliceTailLength", DOUBLE_ZERO);

        return layerElement;
    }


    // LFO for vibrato on Modulation Wheel
    private static Element createLfoElement (final Document document)
    {
        final Element lfoElement = document.createElement ("LFO");
        lfoElement.setAttribute ("LfoNum", "0");
        XMLUtils.addTextElement (document, lfoElement, "Type", "Sine");
        XMLUtils.addTextElement (document, lfoElement, "Rate", "0.700000");
        XMLUtils.addTextElement (document, lfoElement, "LfoPitch", "0.044000");
        return lfoElement;
    }


    private static Optional<Keygroup> getKeygroup (final Map<String, List<Keygroup>> keygroupsMap, final ISampleMetadata sampleMetadata, final Document document, final Element instrumentsElement)
    {
        final int keyLow = sampleMetadata.getKeyLow ();
        final int keyHigh = sampleMetadata.getKeyHigh ();
        final String rangeKey = keyLow + "-" + keyHigh;
        final boolean isSequence = sampleMetadata.getPlayLogic () == PlayLogic.ROUND_ROBIN;
        final List<Keygroup> keygroups = keygroupsMap.computeIfAbsent (rangeKey, key -> new ArrayList<> ());

        // Check if a keygroup exists to which the layer can be added
        for (final Keygroup keygroup: keygroups)
        {
            // Look for velocity or sequence keygroups (type must match)
            if (keygroup.isSequence () == isSequence)
            {
                // Velocity range must match as well for sequences
                if (keygroup.isSequence () && isSequence && (sampleMetadata.getVelocityLow () != keygroup.getVelocityLow () || sampleMetadata.getVelocityHigh () != keygroup.getVelocityHigh ()))
                    continue;

                // Matching keygroup with free layer found
                if (keygroup.getLayerCount () < 4)
                    return Optional.of (keygroup);

                // Can only have 1 sequence keygroup since round robin is per keygroup
                if (isSequence)
                    return Optional.empty ();
            }
        }

        // No existing keygroup found, create a new one (Instrument is a keygroup)

        final Element instrumentElement = document.createElement ("Instrument");
        instrumentElement.setAttribute ("number", Integer.toString (calcInstrumentNumber (keygroupsMap)));
        instrumentsElement.appendChild (instrumentElement);
        XMLUtils.addTextElement (document, instrumentElement, "LowNote", Integer.toString (keyLow));
        XMLUtils.addTextElement (document, instrumentElement, "HighNote", Integer.toString (keyHigh));
        XMLUtils.addTextElement (document, instrumentElement, "VolumeRelease", "0.63");
        XMLUtils.addTextElement (document, instrumentElement, "ZonePlay", ZonePlay.from (sampleMetadata.getPlayLogic ()).getID ());
        instrumentElement.appendChild (createLfoElement (document));
        final Element layersElement = document.createElement ("Layers");
        instrumentElement.appendChild (layersElement);

        final Keygroup keygroup;
        if (isSequence)
            keygroup = new Keygroup (instrumentElement, layersElement, sampleMetadata.getVelocityLow (), sampleMetadata.getVelocityHigh ());
        else
            keygroup = new Keygroup (instrumentElement, layersElement);
        keygroups.add (keygroup);
        return Optional.of (keygroup);
    }


    private static int calcInstrumentNumber (final Map<String, List<Keygroup>> keygroupsMap)
    {
        int count = 1;
        for (final List<Keygroup> keygroups: keygroupsMap.values ())
            count += keygroups.size ();
        return count;
    }


    /**
     * Convert a volume in the range of [-12dB..12dB] to a range of [0..1] which represent
     * [-Inf..6dB].
     *
     * @param volumeDB The volume to convert
     * @return The converted volume
     */
    private static double convertGain (final double volumeDB)
    {
        final double v = 12 + (volumeDB > 6 ? 6 : volumeDB);
        final double result = VALUE_RANGE * v / 18.0;
        return MINUS_12_DB + result;
    }
}