package de.mossgrabers.convertwithmoss.format.music1010;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.DetectSettings;
import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.algorithm.LayerSplitter;
import de.mossgrabers.convertwithmoss.core.algorithm.MathUtils;
import de.mossgrabers.convertwithmoss.core.creator.AbstractWavCreator;
import de.mossgrabers.convertwithmoss.core.creator.DestinationAudioFormat;
import de.mossgrabers.convertwithmoss.core.model.IFilter;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.enumeration.FilterType;
import de.mossgrabers.tools.Pair;
import de.mossgrabers.tools.XMLUtils;


/**
 * Base class for the different sample formats of 1010music.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractMusic1010Creator extends AbstractWavCreator<Music1010CreatorUI>
{
    protected static final DestinationAudioFormat OPTIMIZED_AUDIO_FORMAT = new DestinationAudioFormat (new int []
    {
        24
    }, 48000, true);
    protected static final DestinationAudioFormat DEFAULT_AUDIO_FORMAT   = new DestinationAudioFormat ();

    protected static final Set<Integer>           SUPPORTED_BIT_DEPTHS   = new HashSet<> ();
    static
    {
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (16));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (24));
        SUPPORTED_BIT_DEPTHS.add (Integer.valueOf (32));
    }


    /**
     * Constructor.
     *
     * @param name The name of the creator.
     * @param prefix The prefix to use for the metadata properties tags
     * @param notifier The notifier
     */
    protected AbstractMusic1010Creator (final String name, final String prefix, final INotifier notifier)
    {
        super (name, prefix, notifier, new Music1010CreatorUI (prefix));
    }


    /** {@inheritDoc} */
    @Override
    public boolean supportsPerformances ()
    {
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean checkProcessingCompatibility (final DetectSettings detectSettings)
    {
        if (detectSettings.reduceBitDepth <= 0 || SUPPORTED_BIT_DEPTHS.contains (Integer.valueOf (detectSettings.reduceBitDepth)))
            return true;
        this.notifier.log ("IDS_PROCESSING_REDUCE_BIT_DEPTH_NOT_SUPPORTED", Integer.toString (detectSettings.reduceBitDepth), "16, 24");
        return false;
    }


    /**
     * Store the preset file.
     *
     * @param multiFile The file to store the preset
     * @param metadata The preset metadata description file
     * @throws IOException Could not store the file
     */
    protected static void storePreset (final File multiFile, final String metadata) throws IOException
    {
        Files.writeString (multiFile.toPath (), metadata);
    }


    protected static void createModulators (final Document document, final Element slotElement, final IMultisampleSource multisampleSource)
    {
        createModulator (document, slotElement, "lfo1", "pitch", 128);
        createModulator (document, slotElement, "modwheel", "lfoamount", 328);

        final Optional<Double> globalAmplitudeVelocity = multisampleSource.getGlobalAmplitudeVelocity ();
        if (globalAmplitudeVelocity.isPresent ())
        {
            final double depth = globalAmplitudeVelocity.get ().doubleValue ();
            createModulator (document, slotElement, "velocity", "gaindb", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
        }

        final Optional<IFilter> globalFilter = multisampleSource.getGlobalFilter ();
        if (globalFilter.isPresent ())
        {
            final double depth = globalFilter.get ().getCutoffVelocityModulator ().getDepth ();
            createModulator (document, slotElement, "velocity", "dualfilcutoff", (int) Math.round (MathUtils.denormalize (depth, -1000, 1000)));
        }
    }


    private static void createModulator (final Document document, final Element firstSlot, final String source, final String destination, final int modAmount)
    {
        final Element modSourceElement = XMLUtils.addElement (document, firstSlot, Music1010Tag.MOD_SOURCE);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_DESTINATION, destination);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SOURCE, source);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SLOT, "0");
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_AMOUNT, Integer.toString (modAmount));
    }


    /**
     * Creates the filter effect elements.
     *
     * @param paramsElement Where to add the effect elements
     * @param multisampleSource The multi-sample
     */
    protected static void createFilter (final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        if (optFilter.isEmpty ())
            return;

        final IFilter filter = optFilter.get ();
        final FilterType type = filter.getType ();
        if (type != FilterType.LOW_PASS && type != FilterType.HIGH_PASS)
            return;

        // Negative values for frequency represent a low-pass filter, positive values a high-pass.
        // Note: no poles supported
        final double normalizedFrequency = MathUtils.normalizeFrequency (filter.getCutoff (), IFilter.MAX_FREQUENCY);
        int frequency = (int) Math.round (normalizedFrequency * 1000.0);
        if (type == FilterType.LOW_PASS)
            frequency -= 1000;
        paramsElement.setAttribute (Music1010Tag.ATTR_FILTER_CUTOFF, Integer.toString (frequency));

        // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // be logarithmic).
        final int resonance = (int) Math.round (filter.getResonance () * 1000.0);
        paramsElement.setAttribute (Music1010Tag.ATTR_FILTER_RESONANCE, Integer.toString (resonance));
    }


    /**
     * Create the basic structure for a 1010music session document.
     *
     * @return The document and the session element
     */
    protected Pair<Document, Element> createSessionDocument ()
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return null;
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement (Music1010Tag.ROOT);
        document.appendChild (rootElement);
        final Element sessionElement = XMLUtils.addElement (document, rootElement, Music1010Tag.SESSION);
        sessionElement.setAttribute (Music1010Tag.ATTR_VERSION, "1");

        return new Pair<> (document, sessionElement);
    }


    protected List<IGroup> cleanGroups (final IMultisampleSource multisampleSource) throws IOException
    {
        List<IGroup> groups = this.combineSplitStereo (multisampleSource);
        if (!groups.isEmpty () && this.checkOverlappingRanges (groups))
        {
            final List<List<IGroup>> splitLayers = LayerSplitter.splitIntoNonOverlappingLayers (groups);
            if (splitLayers.size () > 1)
            {
                this.notifier.logError ("IDS_1010_SOURCE_CONTAINS_OVERLAPS");
                groups = splitLayers.get (0);
            }
        }
        multisampleSource.setGroups (groups);
        return groups;
    }
}
