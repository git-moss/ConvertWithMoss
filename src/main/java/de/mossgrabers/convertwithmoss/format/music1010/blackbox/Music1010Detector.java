// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2025
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010.blackbox;

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
 * Detects recursively 1010music preset files in folders. Files must be named <i>preset.xml</i>.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Detector extends AbstractDetector<MetadataSettingsUI>
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";
    private static final String ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Music1010Detector (final INotifier notifier)
    {
        super ("1010music blackbox", "1010music", notifier, new MetadataSettingsUI ("1010music"), ".xml");
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
        if (this.waitForDelivery () || !"preset.xml".equals (file.getName ()))
            return Collections.emptyList ();

        final IPerformanceSource performanceSource = this.processPresetFile (file);
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
    protected List<IPerformanceSource> readPerformanceFiles (final File sourceFile)
    {
        if (this.waitForDelivery () || !"preset.xml".equals (sourceFile.getName ()))
            return null;
        final IPerformanceSource processPresetFile = this.processPresetFile (sourceFile);
        return processPresetFile == null ? Collections.emptyList () : Collections.singletonList (processPresetFile);
    }


    /**
     * Reads and processes the 1010music preset file.
     *
     * @param file The preset file
     * @return The processed multi-sample (singleton list)
     */
    private IPerformanceSource processPresetFile (final File file)
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            // There is a null byte at the end of the file which gets dropped by trim
            final String content = StreamUtils.readUTF8 (in).trim ();
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, file.getParent (), document);
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

        final List<Element> cellElements = XMLUtils.getChildElementsByName (sessionElement, Music1010Tag.CELL);
        final List<Element> multisampleElements = new ArrayList<> ();
        final List<Element> sampleElements = new ArrayList<> ();
        final List<Element> assetElements = new ArrayList<> ();
        filterCells (cellElements, multisampleElements, sampleElements, assetElements);

        final DefaultPerformanceSource performanceSource = new DefaultPerformanceSource ();
        // Use the parent folders name since all presets are called preset.xml
        performanceSource.setName (FileUtils.getNameWithoutType (sourceFile.getParentFile ()));
        if (multisampleElements.isEmpty ())
        {
            this.notifier.log ("IDS_1010_MUSIC_NO_MULTISAMPLE");
            performanceSource.addInstrument (this.parseAggregatedMultisample (sourceFile, sampleElements, basePath));
        }
        else
        {
            for (final Element sampleElement: multisampleElements)
            {
                final Optional<IInstrumentSource> instrumentSource = this.parseMultisample (sourceFile, sampleElement, assetElements, basePath);
                if (instrumentSource.isPresent ())
                    performanceSource.addInstrument (instrumentSource.get ());
            }
        }
        return performanceSource;
    }


    private IInstrumentSource parseAggregatedMultisample (final File multiSampleFile, final List<Element> sampleElements, final String basePath)
    {
        final File parentFile = multiSampleFile.getParentFile ();
        final String name = parentFile.getName ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);

        final IMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IGroup group = new DefaultGroup ("Group");
        multisampleSource.setGroups (Collections.singletonList (group));

        for (final Element sampleElement: sampleElements)
        {
            final Optional<ISampleZone> optZone = this.createSampleZone (multisampleSource, sampleElement, basePath);
            if (optZone.isPresent ())
                group.addSampleZone (optZone.get ());
        }

        return new DefaultInstrumentSource (multisampleSource, -1);
    }


    private Optional<ISampleZone> createSampleZone (final IMultisampleSource multisampleSource, final Element sampleElement, final String basePath)
    {
        final int sampleIndex = XMLUtils.getIntegerAttribute (sampleElement, Music1010Tag.ATTR_ROW, 0) * 4 + XMLUtils.getIntegerAttribute (sampleElement, Music1010Tag.ATTR_COLUMN, 0);

        final Element paramsElement = XMLUtils.getChildElementByName (sampleElement, Music1010Tag.PARAMS);
        if (paramsElement == null)
            return Optional.empty ();

        final String filename = sampleElement.getAttribute (Music1010Tag.ATTR_FILENAME);
        if (filename == null || filename.isBlank ())
            return Optional.empty ();

        File sampleFile = new File (basePath, filename);
        // If the file does not exist, try to find it outside of the Presets folder
        if (!sampleFile.exists ())
            sampleFile = new File (basePath + "/../..", filename);

        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
        final ISampleData sampleData;
        try
        {
            if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                return Optional.empty ();
            sampleData = new WavFileSampleData (sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return Optional.empty ();
        }

        final ISampleZone sampleZone = new DefaultSampleZone (zoneName, sampleData);

        // No trigger
        final boolean isOneShot = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_TRIGGER_TYPE, 1) == 0;

        final int start = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_START, 0);
        sampleZone.setStart (start);
        sampleZone.setStop (start + XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_LENGTH, 0));
        sampleZone.setReversed (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_REVERSE, 0) == 1);

        // Gain - "gaindb" -> unknown conversion

        sampleZone.setTuning (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_PITCH, 0) / 1000.0);
        sampleZone.setPanning (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_PANNING, 0) / 1000.0);

        // No zone logic

        sampleZone.setKeyTracking (0);
        final int rootNote = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_NOTE, -1);
        if (rootNote > 0)
            sampleZone.setKeyRoot (rootNote);
        sampleZone.setKeyLow (36 + sampleIndex);
        sampleZone.setKeyHigh (36 + sampleIndex);
        sampleZone.setVelocityLow (1);
        sampleZone.setVelocityHigh (127);

        /////////////////////////////////////////////////////
        // Loops

        if (!isOneShot)
        {
            final int loopMode = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_MODE, -1);
            if (loopMode > 0)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (loopMode == 2 ? LoopType.ALTERNATING : LoopType.FORWARDS);
                loop.setStart (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_START, 0));
                loop.setEnd (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, 0));
                loop.setCrossfade (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, 0) / 1000.0);
                sampleZone.addLoop (loop);
            }
        }

        /////////////////////////////////////////////////////
        // Volume envelope

        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeEnvelopeModulator ().getSource ();
        amplitudeEnvelope.setAttackTime (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_ATTACK, 0), 9.0));
        amplitudeEnvelope.setDecayTime (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_DECAY, 0), 38.0));
        amplitudeEnvelope.setSustainLevel (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_SUSTAIN, 1) / 1000.0);
        amplitudeEnvelope.setReleaseTime (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_RELEASE, 0), 38.0));

        parseEffects (paramsElement, multisampleSource);

        return Optional.of (sampleZone);
    }


    private Optional<IInstrumentSource> parseMultisample (final File multiSampleFile, final Element sampleElement, final List<Element> assetElements, final String basePath)
    {
        final String pathPrefix = sampleElement.getAttribute (Music1010Tag.ATTR_FILENAME);
        if (pathPrefix == null || pathPrefix.isBlank ())
            return Optional.empty ();

        final File pathPrefixFile = new File (pathPrefix);
        final String name = pathPrefixFile.getName ();
        final String [] parts = AudioFileUtils.createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IGroup group = new DefaultGroup ("Group");
        multisampleSource.setGroups (Collections.singletonList (group));

        final Element paramsElement = XMLUtils.getChildElementByName (sampleElement, Music1010Tag.PARAMS);
        int midiChannel = -1;
        if (paramsElement != null)
        {

            // MIDI-channel 1 (value == 1) acts as the OMNI mode. value == 0 is Off and will also be
            // set to MIDI channel 1.
            midiChannel = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_MIDI_MODE, 1) - 1;
            if (midiChannel == 0)
                midiChannel = -1;
            else if (midiChannel == -1)
                midiChannel = 0;

            final double ampEnvAttack = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_ATTACK, 0), 9.0);
            final double ampEnvDecay = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_DECAY, 0), 38.0);
            final double ampEnvSustain = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_SUSTAIN, 1) / 1000.0;
            final double ampEnvRelease = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_RELEASE, 0), 38.0);

            File previousFolder = null;
            for (final Element assetElement: assetElements)
            {
                final String filename = assetElement.getAttribute (Music1010Tag.ATTR_FILENAME);
                if (filename != null && !filename.isBlank ())
                    this.parseSampleData (group, assetElement, previousFolder, basePath, filename, ampEnvAttack, ampEnvDecay, ampEnvSustain, ampEnvRelease);
            }

            parseEffects (paramsElement, multisampleSource);
        }

        readVelocityModulators (sampleElement, group);
        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);
        return Optional.of (new DefaultInstrumentSource (multisampleSource, midiChannel));
    }


    private static void readVelocityModulators (final Element sampleElement, final IGroup group)
    {
        Double gainMod = null;
        Double cutoffMod = null;
        for (final Element modElement: XMLUtils.getChildElementsByName (sampleElement, Music1010Tag.MOD_SOURCE))
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
     * @param assetElement The XML asset element
     * @param previousFolder The folder in which previously samples were found
     * @param basePath The base path of the samples
     * @param sampleName The filename of the sample asset
     * @param ampEnvAttack The amplitude attack value
     * @param ampEnvDecay The amplitude decay value
     * @param ampEnvSustain The amplitude sustain value
     * @param ampEnvRelease The amplitude release value
     * @return The updated previous folder
     */
    private File parseSampleData (final IGroup group, final Element assetElement, final File previousFolder, final String basePath, final String sampleName, final double ampEnvAttack, final double ampEnvDecay, final double ampEnvSustain, final double ampEnvRelease)
    {
        File sampleFile = new File (basePath, sampleName);
        // If the file does not exist, try to find it outside of the Presets folder
        File prevFolder = previousFolder;
        if (!sampleFile.exists ())
        {
            // Find the sample file starting 2 folders up
            final int height = 2;
            sampleFile = AbstractDetector.findSampleFile (this.notifier, new File (basePath), prevFolder, sampleFile.getName (), height);
            if (sampleFile != null && sampleFile.exists ())
                prevFolder = sampleFile.getParentFile ();
        }

        final String zoneName = FileUtils.getNameWithoutType (sampleFile);
        final ISampleData sampleData;
        try
        {
            if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                return prevFolder;
            sampleData = new WavFileSampleData (sampleFile);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
            return prevFolder;
        }

        final Element paramsElement = XMLUtils.getChildElementByName (assetElement, Music1010Tag.PARAMS);
        if (paramsElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return prevFolder;
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
        // Loops

        try
        {
            sampleZone.getSampleData ().addZoneData (sampleZone, false, true);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }

        /////////////////////////////////////////////////////
        // Volume envelope

        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeEnvelopeModulator ().getSource ();
        amplitudeEnvelope.setAttackTime (ampEnvAttack);
        amplitudeEnvelope.setDecayTime (ampEnvDecay);
        amplitudeEnvelope.setSustainLevel (ampEnvSustain);
        amplitudeEnvelope.setReleaseTime (ampEnvRelease);

        group.addSampleZone (sampleZone);

        return prevFolder;
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


    /**
     * Filter multi-sample and asset cells.
     *
     * @param cellElements All cell elements
     * @param multisampleElements The cell elements with type 'sample' and multi-sample mode enabled
     * @param sampleElements The cell elements with type 'sample', multi-sample mode disabled and
     *            cell-mode 0
     * @param assetElements The cell elements with type 'asset'
     */
    private static void filterCells (final List<Element> cellElements, final List<Element> multisampleElements, final List<Element> sampleElements, final List<Element> assetElements)
    {
        for (final Element cellElement: cellElements)
            switch (cellElement.getAttribute (Music1010Tag.ATTR_TYPE))
            {
                case "sample":
                    final Element paramsElement = XMLUtils.getChildElementByName (cellElement, Music1010Tag.PARAMS);
                    if (paramsElement == null)
                        continue;
                    if ("1".equals (paramsElement.getAttribute (Music1010Tag.ATTR_MULTISAMPLE_MODE)))
                        multisampleElements.add (cellElement);
                    else
                        sampleElements.add (cellElement);
                    break;
                case "asset":
                    assetElements.add (cellElement);
                    break;
                default:
                    // Ignore
                    break;
            }
    }
}
