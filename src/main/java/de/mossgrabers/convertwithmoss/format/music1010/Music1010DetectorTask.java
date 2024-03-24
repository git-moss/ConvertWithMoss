// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.MathUtils;
import de.mossgrabers.convertwithmoss.core.detector.AbstractDetectorTask;
import de.mossgrabers.convertwithmoss.core.detector.DefaultMultisampleSource;
import de.mossgrabers.convertwithmoss.core.model.IEnvelope;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleData;
import de.mossgrabers.convertwithmoss.core.model.ISampleLoop;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.convertwithmoss.core.model.enumeration.LoopType;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultFilter;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultGroup;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleLoop;
import de.mossgrabers.convertwithmoss.core.model.implementation.DefaultSampleZone;
import de.mossgrabers.convertwithmoss.file.AudioFileUtils;
import de.mossgrabers.convertwithmoss.file.StreamUtils;
import de.mossgrabers.convertwithmoss.format.wav.WavFileSampleData;
import de.mossgrabers.convertwithmoss.ui.IMetadataConfig;
import de.mossgrabers.tools.FileUtils;
import de.mossgrabers.tools.XMLUtils;


/**
 * Detects recursively 1010music preset files in folders. Files must be named <i>preset.xml</i>.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010DetectorTask extends AbstractDetectorTask
{
    private static final String ERR_BAD_METADATA_FILE = "IDS_NOTIFY_ERR_BAD_METADATA_FILE";

    private static final String ERR_LOAD_FILE         = "IDS_NOTIFY_ERR_LOAD_FILE";
    private static final String ENDING_PRESET         = ".xml";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     * @param metadata Additional metadata configuration parameters
     */
    protected Music1010DetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder, final IMetadataConfig metadata)
    {
        super (notifier, consumer, sourceFolder, metadata, ENDING_PRESET);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery () || !"preset.xml".equals (file.getName ()))
            return Collections.emptyList ();

        return this.processPresetFile (file);
    }


    /**
     * Reads and processes the 1010music preset file.
     *
     * @param file The preset file
     * @return The processed multi-sample (singleton list)
     */
    private List<IMultisampleSource> processPresetFile (final File file)
    {
        try (final FileInputStream in = new FileInputStream (file))
        {
            // There is a null byte at the end of the which gets dropped by trim
            final String content = StreamUtils.readUTF8 (in).trim ();
            final Document document = XMLUtils.parseDocument (new InputSource (new StringReader (content)));
            return this.parseMetadataFile (file, file.getParent (), false, document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError (ERR_LOAD_FILE, ex);
            return Collections.emptyList ();
        }
    }


    /**
     * Load and parse the metadata description file.
     *
     * @param multiSampleFile The preset or library file
     * @param basePath The parent folder, in case of a library the relative folder in the ZIP
     *            directory structure
     * @param isLibrary If it is a library otherwise a preset
     * @param document The XML document to parse
     * @return The parsed multi-sample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String basePath, final boolean isLibrary, final Document document)
    {
        final Element top = document.getDocumentElement ();
        if (!Music1010Tag.ROOT.equals (top.getNodeName ()))
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element sessionElement = XMLUtils.getChildElementByName (top, Music1010Tag.SESSION);
        if (sessionElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
            return Collections.emptyList ();
        }

        final Element [] cellElements = XMLUtils.getChildElementsByName (sessionElement, Music1010Tag.CELL, false);
        final List<Element> multisampleElements = new ArrayList<> ();
        final List<Element> sampleElements = new ArrayList<> ();
        final List<Element> assetElements = new ArrayList<> ();
        filterCells (cellElements, multisampleElements, sampleElements, assetElements);
        if (multisampleElements.isEmpty ())
        {
            this.notifier.log ("IDS_1010_MUSIC_NO_MULTISAMPLE");
            final Optional<IMultisampleSource> multisample = this.parseAggregatedMultisample (multiSampleFile, sampleElements, basePath);
            return multisample.isPresent () ? Collections.singletonList (multisample.get ()) : Collections.emptyList ();
        }

        final List<IMultisampleSource> multisampleSources = new ArrayList<> ();
        for (final Element sampleElement: multisampleElements)
        {
            final Optional<IMultisampleSource> multisample = this.parseMultisample (multiSampleFile, sampleElement, assetElements, basePath);
            if (multisample.isPresent ())
                multisampleSources.add (multisample.get ());
        }
        return multisampleSources;
    }


    private Optional<IMultisampleSource> parseAggregatedMultisample (final File multiSampleFile, final List<Element> sampleElements, final String basePath)
    {
        final File parentFile = multiSampleFile.getParentFile ();
        final String name = parentFile.getName ();
        final String [] parts = AudioFileUtils.createPathParts (parentFile, this.sourceFolder, name);

        final DefaultMultisampleSource multisampleSource = new DefaultMultisampleSource (multiSampleFile, parts, name, AudioFileUtils.subtractPaths (this.sourceFolder, multiSampleFile));
        final IGroup group = new DefaultGroup ("Group");
        multisampleSource.setGroups (Collections.singletonList (group));

        for (final Element sampleElement: sampleElements)
        {
            final int sampleIndex = XMLUtils.getIntegerAttribute (sampleElement, Music1010Tag.ATTR_ROW, 0) * 4 + XMLUtils.getIntegerAttribute (sampleElement, Music1010Tag.ATTR_COLUMN, 0);

            final Element paramsElement = XMLUtils.getChildElementByName (sampleElement, Music1010Tag.PARAMS);
            if (paramsElement == null)
                continue;

            final String filename = sampleElement.getAttribute (Music1010Tag.ATTR_FILENAME);
            if (filename == null || filename.isBlank ())
                continue;

            File sampleFile = new File (basePath, filename);
            // If the file does not exist, try to find it outside of the Presets folder
            if (!sampleFile.exists ())
                sampleFile = new File (basePath + "/../..", filename);

            final String zoneName = FileUtils.getNameWithoutType (sampleFile);
            final ISampleData sampleData;
            try
            {
                if (!AudioFileUtils.checkSampleFile (sampleFile, this.notifier))
                    continue;
                sampleData = new WavFileSampleData (sampleFile);
            }
            catch (final IOException ex)
            {
                this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
                continue;
            }

            final DefaultSampleZone sampleZone = new DefaultSampleZone (zoneName, sampleData);

            // No trigger

            final int start = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_START, 0);
            sampleZone.setStart (start);
            sampleZone.setStop (start + XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_SAMPLE_LENGTH, 0));
            sampleZone.setReversed (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_REVERSE, 0) == 1);

            // Gain - "gaindb" -> unknown conversion

            sampleZone.setTune (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_PITCH, 0) / 1000.0);
            sampleZone.setPanorama (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_PANORAMA, 0) / 1000.0);

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

            final int loopMode = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_MODE, -1);
            if (loopMode > 0)
            {
                final ISampleLoop loop = new DefaultSampleLoop ();
                loop.setType (loopMode == 2 ? LoopType.ALTERNATING : LoopType.FORWARD);
                loop.setStart (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_START, 0));
                loop.setEnd (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, 0));
                loop.setCrossfade (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_LOOP_END, 0) / 1000.0);
                sampleZone.addLoop (loop);
            }

            /////////////////////////////////////////////////////
            // Volume envelope

            final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeModulator ().getSource ();
            amplitudeEnvelope.setAttack (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_ATTACK, 0), 9.0));
            amplitudeEnvelope.setDecay (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_DECAY, 0), 38.0));
            amplitudeEnvelope.setSustain (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_SUSTAIN, 1) / 1000.0);
            amplitudeEnvelope.setRelease (MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_RELEASE, 0), 38.0));

            parseEffects (paramsElement, multisampleSource);

            group.addSampleZone (sampleZone);
        }

        return Optional.of (multisampleSource);
    }


    private Optional<IMultisampleSource> parseMultisample (final File multiSampleFile, final Element sampleElement, final List<Element> assetElements, final String basePath)
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
        if (paramsElement == null)
            return Optional.empty ();

        final double ampEnvAttack = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_ATTACK, 0), 9.0);
        final double ampEnvDecay = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_DECAY, 0), 38.0);
        final double ampEnvSustain = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_SUSTAIN, 1) / 1000.0;
        final double ampEnvRelease = MathUtils.denormalizeTime (XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_AMPEG_RELEASE, 0), 38.0);

        for (final Element assetElement: assetElements)
        {
            final String filename = assetElement.getAttribute (Music1010Tag.ATTR_FILENAME);
            if (filename != null && !filename.isBlank () && filename.startsWith (pathPrefix))
                this.parseSampleData (group, assetElement, basePath, filename, ampEnvAttack, ampEnvDecay, ampEnvSustain, ampEnvRelease);
        }

        this.createMetadata (multisampleSource.getMetadata (), this.getFirstSample (multisampleSource.getGroups ()), parts);

        parseEffects (paramsElement, multisampleSource);

        return Optional.of (multisampleSource);
    }


    /**
     * Parse a asset cell into a sample data object.
     *
     * @param group The object to fill in the data
     * @param assetElement The XML asset element
     * @param basePath The base path of the samples
     * @param sampleName The filename of the sample asset
     * @param ampEnvAttack The amplitude attack value
     * @param ampEnvDecay The amplitude decay value
     * @param ampEnvSustain The amplitude sustain value
     * @param ampEnvRelease The amplitude release value
     */
    private void parseSampleData (final IGroup group, final Element assetElement, final String basePath, final String sampleName, final double ampEnvAttack, final double ampEnvDecay, final double ampEnvSustain, final double ampEnvRelease)
    {
        File sampleFile = new File (basePath, sampleName);
        // If the file does not exist, try to find it outside of the Presets folder
        if (!sampleFile.exists ())
            sampleFile = new File (basePath + "/../..", sampleName);

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

        final Element paramsElement = XMLUtils.getChildElementByName (assetElement, Music1010Tag.PARAMS);
        if (paramsElement == null)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE);
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
        // Loops

        try
        {
            sampleZone.getSampleData ().addMetadata (sampleZone, false, true);
        }
        catch (final IOException ex)
        {
            this.notifier.logError (ERR_BAD_METADATA_FILE, ex);
        }

        /////////////////////////////////////////////////////
        // Volume envelope

        final IEnvelope amplitudeEnvelope = sampleZone.getAmplitudeModulator ().getSource ();
        amplitudeEnvelope.setAttack (ampEnvAttack);
        amplitudeEnvelope.setDecay (ampEnvDecay);
        amplitudeEnvelope.setSustain (ampEnvSustain);
        amplitudeEnvelope.setRelease (ampEnvRelease);

        group.addSampleZone (sampleZone);
    }


    /**
     * Parse the effects on the top level.
     *
     * @param paramsElement The parameter element of the sample cell
     * @param multisampleSource The multisample to fill
     */
    private static void parseEffects (final Element paramsElement, final DefaultMultisampleSource multisampleSource)
    {
        if (multisampleSource.getGlobalFilter () != null)
            return;

        final int frequency = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_FILTER_CUTOFF, 0);
        if (frequency == 0)
            return;

        final FilterType type = frequency < 0 ? FilterType.LOW_PASS : FilterType.HIGH_PASS;
        final double cutoff = MathUtils.denormalizeFrequency (frequency < 0 ? frequency + 1000 : frequency, IFilter.MAX_FREQUENCY);

        // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // be logarithmic).
        final int resonance = XMLUtils.getIntegerAttribute (paramsElement, Music1010Tag.ATTR_FILTER_RESONANCE, 0);
        multisampleSource.setGlobalFilter (new DefaultFilter (type, 4, cutoff, MathUtils.denormalize (resonance / 1000.0, 0, 40.0)));
    }


    /**
     * Filter (multi-)sample and asset cells.
     *
     * @param cellElements All cell elements
     * @param multisampleElements The cell elements with type 'sample' and multi-sample mode enabled
     * @param sampleElements The cell elements with type 'sample', multi-sample mode disabled and
     *            cell-mode 0
     * @param assetElements The cell elements with type 'asset'
     */
    private static void filterCells (final Element [] cellElements, final List<Element> multisampleElements, final List<Element> sampleElements, final List<Element> assetElements)
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
                    else if ("0".equals (paramsElement.getAttribute (Music1010Tag.ATTR_CELL_MODE)))
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
