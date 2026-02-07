// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010.bento;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.IPerformanceSource;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetector;
import de.mossgrabers.convertwithmoss.core.detector.DefaultInstrumentSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.detector.DefaultPerformanceSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.core.settings.MetadataSettingsUI;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.music1010.Music1010Tag;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively 1010music Bento patch files in folders. Files must be named <i>patch.xml</i>.
 *
 * @author Jürgen Moßgraber
 */
public class BentoDetector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public BentoDetector (final INotifier notifier)
    {
        super ("1010music bento", "Bento", notifier, new MetadataSettingsUI ("Bento"), ".xml");
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readPresetFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final String filename = file.getName ();
        final boolean isProject = "project.xml".equals (filename);
        if (!isProject && !("patch.xml".equals (filename)))
            return Collections.emptyList ();

        final String basePath = isProject ? file.getParentFile ().getParentFile ().getParent () : file.getParent ();
        final IPerformanceSource performanceSource = this.processPresetFile (file, basePath);
        if (performanceSource == null)
            return Collections.emptyList ();

        final List<IInstrumentSource> instruments = performanceSource.getInstruments ();
        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final IInstrumentSource instrument: instruments)
            multisampleSources.add (instrument.getMultisampleSource ());
        return multisampleSources;
    }


    /** {@inheritDoc} */
    @Override
    protected List<IPerformanceSource> readPerformanceFile (final File file)
    {
        if (this.waitForDelivery () || !"project.xml".equals (file.getName ()))
            return null;
        // Step out of the Projects folder; Patches folder is expected on that level!
        final String basePath = file.getParentFile ().getParentFile ().getParent ();
        final IPerformanceSource processPresetFile = this.processPresetFile (file, basePath);
        return processPresetFile == null ? Collections.emptyList () : Collections.singletonList (processPresetFile);
    }


    /**
     * Reads and processes the 1010music Bento patch/project file.
     *
     * @param file The preset file
     * @param basePath The base path to look for samples
     * @return The processed multi-sample (singleton list)
     */
    private IPerformanceSource processPresetFile (final File file, final String basePath)
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            final String content = StreamUtils.readUTF8 (in);
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, basePath, document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return null;
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param sourceFile The preset or library file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private IPerformanceSource parseMetadataFile (final File sourceFile, final String basePath, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (!Music1010Tag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return null;
        }

        final Element sessionElement = XMLUtils.getChildElementByName (top, Music1010Tag.SESSION);
        if (sessionElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return null;
        }
        final String versionAttribute = sessionElement.getAttribute (Music1010Tag.ATTR_VERSION);
        if (versionAttribute == null || !"1".equals (versionAttribute))
        {
            this.notifier.logError ("IDS_1010_MUSIC_WRONG_VERSION", versionAttribute == null ? "'none'" : versionAttribute);
            return null;
        }

        final List<Element> trackElements = XMLUtils.getChildElementsByName (sessionElement, Music1010Tag.TRACK);
        if (trackElements.isEmpty ())
            return null;

        final DefaultPerformanceSource performanceSource = new DefaultPerformanceSource ();
        // Use the parent folders name since all presets are called preset.xml
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile.getParentFile ()));

        for (final Element trackElement: trackElements)
        {
            if (!"multisamtrack".equals (trackElement.getAttribute (Music1010Tag.ATTR_TYPE)))
                continue;

            final List<Element> assetElements = new ArrayList<> ();
            Element instElement = null;

            for (final Element cellElement: XMLUtils.getChildElementsByName (trackElement, Music1010Tag.CELL))
            {
                switch (cellElement.getAttribute (Music1010Tag.ATTR_TYPE))
                {
                    case "saminst":
                        instElement = cellElement;
                        break;
                    case "samasst":
                        assetElements.add (cellElement);
                        break;
                    default:
                        break;
                }
            }
            if (instElement == null || assetElements.isEmpty ())
                return null;

            final Optional<IInstrumentSource> instrumentSource = this.parseMultisample (sourceFile, performanceSource.getName (), trackElement, instElement, assetElements, basePath);
            if (instrumentSource.isPresent ())
                performanceSource.addInstrument (instrumentSource.get ());
        }

        return performanceSource;
    }


    private Optional<IInstrumentSource> parseMultisample (final File multiSampleFile, final String defaultName, final Element trackElement, final Element instElement, final List<Element> assetElements, final String basePath)
    {
        final Element trackParamsElement = XMLUtils.getChildElementByName (trackElement, Music1010Tag.PARAMS);
        final Element instParamsElement = XMLUtils.getChildElementByName (instElement, Music1010Tag.PARAMS);
        if (trackParamsElement == null || instParamsElement == null)
            return Optional.empty ();

        String pathPrefix = trackElement.getAttribute (Music1010Tag.ATTR_CELLNAME);
        if (pathPrefix == null || pathPrefix.isBlank () || "Track 1".equals (pathPrefix))
            pathPrefix = defaultName;

        final File pathPrefixFile = new File (pathPrefix);
        final String name = pathPrefixFile.getName ();
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IGroup group = new DefaultGroup ("Group");
        multisampleSource.setGroups (Collections.singletonList (group));

        // 0 is Off, 1-16
        final int midiChannel = XMLUtils.getIntegerAttribute (trackParamsElement, Music1010Tag.ATTR_MIDI_INPUT_CHANNEL, 1) - 1;

        final double ampEnvAttack = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_AMPEG_ATTACK, 0), 9.0);
        final double ampEnvDecay = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_AMPEG_DECAY, 0), 38.0);
        final double ampEnvSustain = XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_AMPEG_SUSTAIN, 1) / 1000.0;
        final double ampEnvRelease = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_AMPEG_RELEASE, 0), 38.0);

        final int loopMode = XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_LOOP_MODE, 0);
        ISampleLoop defaultLoop = null;
        if (loopMode > 0)
        {
            defaultLoop = new DefaultSampleLoop ();
            defaultLoop.setType (loopMode == 2 ? LoopType.ALTERNATING : LoopType.FORWARDS);
        }

        final boolean isReversed = XMLUtils.getIntegerAttribute (instParamsElement, Music1010Tag.ATTR_REVERSE, 0) > 0;

        for (final Element assetElement: assetElements)
        {
            final Element paramsElement = XMLUtils.getChildElementByName (assetElement, Music1010Tag.PARAMS);
            if (paramsElement == null)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE);
                return Optional.empty ();
            }

            final String filename = paramsElement.getAttribute (Music1010Tag.ATTR_FILENAME);
            if (filename != null && !filename.isBlank ())
                this.parseSampleData (group, paramsElement, basePath, filename, ampEnvAttack, ampEnvDecay, ampEnvSustain, ampEnvRelease, defaultLoop, isReversed);
        }

        parseEffects (instParamsElement, multisampleSource);
        readVelocityModulators (instElement, group);

        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Optional.of (new DefaultInstrumentSource (multisampleSource, midiChannel));
    }


    private static void readVelocityModulators (final Element instElement, final IGroup group)
    {
        Double gainMod = null;
        Double cutoffMod = null;
        for (final Element modElement: XMLUtils.getChildElementsByName (instElement, Music1010Tag.MOD_SOURCE))
        {
            final String source = modElement.getAttribute (Music1010Tag.ATTR_MOD_SOURCE);
            if ("velocity".equals (source))
            {
                final int amount = XMLUtils.getIntegerAttribute (modElement, Music1010Tag.ATTR_MOD_AMOUNT, 0);
                if (amount != 0)
                {
                    final double normalizedAmount = MathUtils.normalize (amount, -1000, 1000);
                    final String destination = modElement.getAttribute (Music1010Tag.ATTR_MOD_DESTINATION);
                    if ("gaindb".equals (destination))
                        gainMod = Double.valueOf (normalizedAmount);
                    else if ("dualfilcutoff".equals (destination))
                        cutoffMod = Double.valueOf (normalizedAmount);
                }
            }
        }

        if (gainMod == null && cutoffMod == null)
            return;

        for (final ISampleZone zone: group.getSampleZones ())
        {
            if (gainMod != null)
                zone.getAmplitudeVelocityModulator ().setDepth (gainMod.doubleValue ());
            if (cutoffMod != null)
            {
                final Optional<IFilter> optFilter = zone.getFilter ();
                if (optFilter.isPresent ())
                    optFilter.get ().getCutoffVelocityModulator ().setDepth (cutoffMod.doubleValue ());
            }
        }
    }


    /**
     * Parse a asset cell into a sample data object.
     *
     * @param group The object to fill in the data
     * @param paramsElement The XML parameter element of an asset
     * @param basePath The base path of the samples
     * @param sampleName The filename of the sample asset
     * @param ampEnvAttack The amplitude attack value
     * @param ampEnvDecay The amplitude decay value
     * @param ampEnvSustain The amplitude sustain value
     * @param ampEnvRelease The amplitude release value
     * @param defaultLoop The default loop
     * @param isReversed True if play-back is reversed
     */
    private void parseSampleData (final IGroup group, final Element paramsElement, final String basePath, final String sampleName, final double ampEnvAttack, final double ampEnvDecay, final double ampEnvSustain, final double ampEnvRelease, final ISampleLoop defaultLoop, final boolean isReversed)
    {
        File sampleFile = new File (basePath, sampleName);
        // If the file does not exist, try to find it outside of the Presets folder
        if (!sampleFile.exists ())
        {
            this.notifier.logError ("IDS_ERR_SAMPLE_FILE_DOES_NOT_EXIST", sampleFile.getAbsolutePath ());
            return;
        }

        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
        final ISampleData sampleData;
        try
        {
            if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                return;
            sampleData = new WavFileSampleData (sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return;
        }

        final DefaultSampleZone sampleZone = new DefaultSampleZone (zoneName, sampleData);

        // No trigger
        // No start - set from Sample chunk in addMetadata below
        // No stop - set from Sample chunk in addMetadata below
        // No reverse
        // No gain - could be set from instrument chunk
        // No tune - set from Sample chunk in addMetadata below
        // No zone logic
        // No Key Tracking

        sampleZone.setKeyRoot (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_NOTE, -1));
        sampleZone.setKeyLow (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_NOTE, -1));
        sampleZone.setKeyHigh (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_NOTE, -1));

        final int velLow = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_VEL, -1);
        final int velHigh = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_VEL, -1);
        if (velLow > 0)
            sampleZone.setVelocityLow (velLow);
        if (velHigh > 0)
            sampleZone.setVelocityHigh (velHigh);

        /////////////////////////////////////////////////////
        // Play-range & Loops

        sampleZone.setStart (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_START, 0));
        final int stop = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_LENGTH, -1);
        if (stop > 0)
            sampleZone.setStop (stop);

        if (defaultLoop != null)
        {
            final int loopStart = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_START, -1);
            final int loopEnd = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, -1);
            if (loopStart >= 0 && loopEnd > 0)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setCrossfade (loop.getCrossfade ());
                loop.setType (loop.getType ());
                loop.setStart (loopStart);
                loop.setEnd (loopEnd);
                sampleZone.addLoop (loop);
            }
        }

        sampleZone.setReversed (isReversed);

        /////////////////////////////////////////////////////
        // Volume envelope

        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeEnvelopeModulator ().getSource ();
        amplitudeEnvelope.setAttackTime (ampEnvAttack);
        amplitudeEnvelope.setDecayTime (ampEnvDecay);
        amplitudeEnvelope.setSustainLevel (ampEnvSustain);
        amplitudeEnvelope.setReleaseTime (ampEnvRelease);

        group.addSampleZone (sampleZone);
    }


    /**
     * Parse the effects on the top level.
     *
     * @param paramsElement The parameter element of the sample cell
     * @param multisampleSource The multi-sample to fill
     */
    private static void parseEffects (final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        if (multisampleSource.getGlobalFilter ().isPresent ())
            return;

        final int frequency = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_FILTER_CUTOFF, 0);
        if (frequency == 0)
            return;

        final FilterType type = frequency < 0 ? FilterType.LOW_PASS : FilterType.HIGH_PASS;
        final double normalizedFrequency = Math.clamp ((frequency < 0 ? frequency + 1000 : frequency) / 1000.0, 0.0, 1.0);
        final double cutoff = MathUtils.denormalizeFrequency (normalizedFrequency, IFilter.MAX_FREQUENCY);

        // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // be logarithmic).
        final int resonance = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_FILTER_RESONANCE, 0);
        multisampleSource.setGlobalFilter (new DefaultFilter (type, 4, cutoff, Math.clamp (resonance / 1000.0, 0, 1.0)));
    }
}
