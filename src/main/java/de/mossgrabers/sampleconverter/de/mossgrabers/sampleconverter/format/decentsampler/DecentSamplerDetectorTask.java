// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2021
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.sampleconverter.format.decentsampler;

import de.mossgrabers.sampleconverter.core.DefaultSampleMetadata;
import de.mossgrabers.sampleconverter.core.IMultisampleSource;
import de.mossgrabers.sampleconverter.core.INotifier;
import de.mossgrabers.sampleconverter.core.IVelocityLayer;
import de.mossgrabers.sampleconverter.core.PlayLogic;
import de.mossgrabers.sampleconverter.core.SampleLoop;
import de.mossgrabers.sampleconverter.core.VelocityLayer;
import de.mossgrabers.sampleconverter.core.detector.AbstractDetectorTask;
import de.mossgrabers.sampleconverter.core.detector.MultisampleSource;
import de.mossgrabers.sampleconverter.util.TagDetector;
import de.mossgrabers.sampleconverter.util.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * Detects recursively DecentSampler preset and library files in folders. Files must end with
 * <i>.dspreset</i> or <i>.dslibrary</i>.
 *
 * @author J&uuml;rgen Mo&szlig;graber
 */
public class DecentSamplerDetectorTask extends AbstractDetectorTask
{
    private static final String ENDING_DSLIBRARY = ".dslibrary";
    private static final String ENDING_DSPRESET  = ".dspreset";


    /**
     * Constructor.
     *
     * @param notifier The notifier
     * @param consumer The consumer that handles the detected multisample sources
     * @param sourceFolder The top source folder for the detection
     */
    protected DecentSamplerDetectorTask (final INotifier notifier, final Consumer<IMultisampleSource> consumer, final File sourceFolder)
    {
        super (notifier, consumer, sourceFolder, ENDING_DSPRESET, ENDING_DSLIBRARY);
    }


    /** {@inheritDoc} */
    @Override
    protected List<IMultisampleSource> readFile (final File file)
    {
        if (this.waitForDelivery ())
            return Collections.emptyList ();

        final List<IMultisampleSource> result = file.getName ().endsWith (ENDING_DSPRESET) ? this.processPresetFile (file) : this.processLibraryFile (file);

        this.printUnsupportedElements ();
        this.printUnsupportedAttributes ();

        return result;
    }


    /**
     * Reads a DecentSampler library file and processes all presets it contains.
     *
     * @param file The library file
     * @return The processed multi samples
     */
    private List<IMultisampleSource> processLibraryFile (final File file)
    {
        final List<IMultisampleSource> result = new ArrayList<> ();

        try (final ZipFile zipFile = new ZipFile (file))
        {
            for (final ZipEntry entry: Collections.list (zipFile.entries ()))
            {
                final String name = entry.getName ();
                if (name != null && name.endsWith (ENDING_DSPRESET))
                {
                    String parent = new File (name).getParent ();
                    if (parent == null)
                        parent = "";

                    try (final InputStream in = zipFile.getInputStream (entry))
                    {
                        final Document document = XMLUtils.parseDocument (new InputSource (in));
                        result.addAll (this.parseMetadataFile (file, parent, true, document));
                    }
                    catch (final SAXException ex)
                    {
                        this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
                    }
                }
            }
        }
        catch (final IOException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
        }

        return result;
    }


    /**
     * Reads and processes the Decent Sampler preset file.
     *
     * @param file The preset file
     * @return The processed multi sample (singleton list)
     */
    private List<IMultisampleSource> processPresetFile (final File file)
    {
        try (final FileReader reader = new FileReader (file, StandardCharsets.UTF_8))
        {
            final Document document = XMLUtils.parseDocument (new InputSource (reader));
            return this.parseMetadataFile (file, file.getParent (), false, document);
        }
        catch (final IOException | SAXException ex)
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_LOAD_FILE", ex);
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
     * @return The parsed multisample source
     */
    private List<IMultisampleSource> parseMetadataFile (final File multiSampleFile, final String basePath, final boolean isLibrary, final Document document)
    {
        final Element top = document.getDocumentElement ();

        if (!DecentSamplerTag.DECENTSAMPLER.equals (top.getNodeName ()))
        {
            this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
            return Collections.emptyList ();
        }

        this.checkAttributes (DecentSamplerTag.DECENTSAMPLER, top.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.DECENTSAMPLER));
        this.checkChildTags (DecentSamplerTag.DECENTSAMPLER, DecentSamplerTag.TOP_LEVEL_TAGS, XMLUtils.getChildElements (top));

        final List<IVelocityLayer> velocityLayers = this.parseVelocityLayers (top, basePath, isLibrary ? multiSampleFile : null);

        final String name = getNameWithoutType (multiSampleFile);
        final String [] parts = createPathParts (multiSampleFile.getParentFile (), this.sourceFolder, name);

        final MultisampleSource multisampleSource = new MultisampleSource (multiSampleFile, parts, name, this.subtractPaths (this.sourceFolder, multiSampleFile));

        // Use same guessing on the filename...
        multisampleSource.setCategory (TagDetector.detectCategory (parts));
        multisampleSource.setKeywords (TagDetector.detectKeywords (parts));

        multisampleSource.setVelocityLayers (velocityLayers);

        return Collections.singletonList (multisampleSource);
    }


    /**
     * Parses all velocity layers (groups).
     *
     * @param top The top XML element
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @return All parsed layers
     */
    private List<IVelocityLayer> parseVelocityLayers (final Element top, final String basePath, final File libraryFile)
    {
        final Node [] groupNodes = XMLUtils.getChildrenByName (top, DecentSamplerTag.GROUP);
        final List<IVelocityLayer> layers = new ArrayList<> (groupNodes.length);
        int groupCounter = 1;
        for (final Node groupNode: groupNodes)
        {
            if (groupNode instanceof final Element groupElement)
            {
                this.checkAttributes (DecentSamplerTag.GROUP, groupElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.GROUP));

                final String k = groupElement.getAttribute ("name");
                final String layerName = k == null || k.isBlank () ? "Velocity Layer " + groupCounter : k;
                final VelocityLayer velocityLayer = new VelocityLayer (layerName);

                final double groupVolumeOffset = parseVolume (groupElement, DecentSamplerTag.VOLUME);
                final double groupTuningOffset = XMLUtils.getDoubleAttribute (groupElement, DecentSamplerTag.GROUP_TUNING, 0);

                this.parseVelocityLayer (velocityLayer, groupElement, basePath, libraryFile, groupVolumeOffset, groupTuningOffset);
                layers.add (velocityLayer);
                groupCounter++;
            }
            else
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
                return Collections.emptyList ();
            }
        }
        return layers;
    }


    /**
     * Parse a velocity layer (group).
     *
     * @param velocityLayer The object to fill in the data
     * @param groupElement The XML group element
     * @param basePath The base path of the samples
     * @param libraryFile If it is a library otherwise null
     * @param groupVolumeOffset The volume offset
     * @param groupTuningOffset The tuning offset
     */
    private void parseVelocityLayer (final VelocityLayer velocityLayer, final Element groupElement, final String basePath, final File libraryFile, final double groupVolumeOffset, final double groupTuningOffset)
    {
        for (final Element sampleElement: XMLUtils.getChildElementsByName (groupElement, DecentSamplerTag.SAMPLE))
        {
            this.checkAttributes (DecentSamplerTag.SAMPLE, sampleElement.getAttributes (), DecentSamplerTag.getAttributes (DecentSamplerTag.SAMPLE));
            this.checkChildTags (DecentSamplerTag.SAMPLE, DecentSamplerTag.SAMPLE_TAGS, XMLUtils.getChildElements (sampleElement));

            final String sampleName = sampleElement.getAttribute (DecentSamplerTag.PATH);
            if (sampleName == null || sampleName.isBlank ())
            {
                this.notifier.logError ("IDS_NOTIFY_ERR_BAD_METADATA_FILE");
                return;
            }

            final DefaultSampleMetadata sampleMetadata;
            if (libraryFile == null)
            {
                final File sampleFile = new File (basePath, sampleName);
                if (!this.checkSampleFile (sampleFile))
                    return;
                sampleMetadata = new DefaultSampleMetadata (sampleFile);
            }
            else
                sampleMetadata = new DefaultSampleMetadata (libraryFile, new File (basePath, sampleName).getPath ().replace ('\\', '/'));

            sampleMetadata.setStart ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.START, -1)));
            sampleMetadata.setStop ((int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.END, -1)));
            sampleMetadata.setGain (groupVolumeOffset + parseVolume (sampleElement, DecentSamplerTag.VOLUME));
            sampleMetadata.setTune ((XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.TUNING, 0) + groupTuningOffset) * 100.0);

            final String zoneLogic = sampleElement.getAttribute (DecentSamplerTag.SEQ_MODE);
            sampleMetadata.setPlayLogic (zoneLogic != null && "round_robin".equals (zoneLogic) ? PlayLogic.ROUND_ROBIN : PlayLogic.ALWAYS);

            sampleMetadata.setKeyTracking (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.PITCH_KEY_TRACK, 1));
            sampleMetadata.setKeyRoot (XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.ROOT_NOTE, -1));
            sampleMetadata.setKeyLow (XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.LO_NOTE, -1));
            sampleMetadata.setKeyHigh (XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.HI_NOTE, -1));
            sampleMetadata.setVelocityLow (XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.LO_VEL, -1));
            sampleMetadata.setVelocityHigh (XMLUtils.getIntegerAttribute (sampleElement, DecentSamplerTag.HI_VEL, -1));

            /////////////////////////////////////////////////////
            // Loops

            final int loopStart = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_START, -1));
            final int loopEnd = (int) Math.round (XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_END, -1));
            final double loopCrossfade = XMLUtils.getDoubleAttribute (sampleElement, DecentSamplerTag.LOOP_CROSSFADE, 0);

            if (loopStart >= 0 || loopEnd > 0 || loopCrossfade > 0)
            {
                final SampleLoop loop = new SampleLoop ();
                loop.setStart (loopStart);
                loop.setEnd (loopEnd);
                final int loopLength = loopEnd - loopStart;
                if (loopLength > 0)
                    loop.setCrossfade (loopCrossfade / loopLength);
                sampleMetadata.addLoop (loop);
            }

            this.loadMissingValues (sampleMetadata);

            velocityLayer.addSampleMetadata (sampleMetadata);
        }
    }


    /**
     * Parses a volume value from the given tag.
     *
     * @param element The element which contains the volume attribute
     * @param tag The tag name of the attribute containing the volume
     * @return The volume in dB
     */
    private static double parseVolume (final Element element, final String tag)
    {
        String attribute = element.getAttribute (tag);
        if (attribute == null)
            return 0;

        attribute = attribute.trim ();
        if (attribute.isBlank ())
            return 0;

        // Is the value in dB?
        if (attribute.endsWith ("dB"))
            return Double.parseDouble (attribute.substring (0, attribute.length () - 2));

        // The value is in the range of [0..1] but it is not specified what 0 and 1 means, lets
        // scale it to [0..6] dB.
        return Double.parseDouble (attribute) * 6.0;
    }
}
