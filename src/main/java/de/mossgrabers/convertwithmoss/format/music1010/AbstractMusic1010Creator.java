// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

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
import de.mossgrabers.convertwithmoss.core.model.ILfo;
import de.mossgrabers.convertwithmoss.core.model.ILfoModulator;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
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

    private static final int                      MAX_MODULATION_SLOTS   = 3;

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


    /**
     * Creates the default routing of the low frequency oscillator and the velocity modulation
     * matrix entries.
     *
     * @param document The XML document
     * @param slotElement The element to which to add the modulation matrix entries
     * @param multisampleSource The multi-sample
     */
    protected static void createModulators (final Document document, final Element slotElement, final IMultisampleSource multisampleSource)
    {
        createDefaultLfoModulators (document, slotElement);
        createVelocityModulators (document, slotElement, multisampleSource);
    }


    /**
     * Creates the default routing of the low frequency oscillator: a vibrato which is controlled by
     * the modulation wheel.
     *
     * @param document The XML document
     * @param slotElement The element to which to add the modulation matrix entries
     */
    protected static void createDefaultLfoModulators (final Document document, final Element slotElement)
    {
        createModulator (document, slotElement, Music1010Lfo.SOURCE_LFO1, Music1010Lfo.DESTINATION_PITCH, 128);
        createModulator (document, slotElement, Music1010Lfo.SOURCE_MOD_WHEEL, Music1010Lfo.DESTINATION_LFO, 328);
    }


    /**
     * Creates the low frequency oscillator and its routings to the volume, the pitch and the filter
     * cutoff. The multi-sample engine has only 1 oscillator, therefore the settings of the first
     * active modulator (pitch, volume, cutoff) are used. The effective depth of a routing is the
     * depth of the oscillator multiplied by the amount of the routing, the modulation wheel can
     * increase the depth of the oscillator. If no modulator is active, the default routing is
     * created.
     *
     * @param document The XML document
     * @param slotElement The element to which to add the modulation matrix entries
     * @param paramsElement The parameters element of the instrument
     * @param multisampleSource The multi-sample
     */
    protected static void createLfoModulators (final Document document, final Element slotElement, final Element paramsElement, final IMultisampleSource multisampleSource)
    {
        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);
        if (groups.isEmpty ())
        {
            createDefaultLfoModulators (document, slotElement);
            return;
        }

        final ISampleZone zone = groups.get (0).getSampleZones ().get (0);
        final ILfoModulator pitchModulator = zone.getPitchLfoModulator ();
        final ILfoModulator gainModulator = zone.getAmplitudeLfoModulator ();
        ILfoModulator cutoffModulator = null;
        final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        if (optFilter.isPresent ())
        {
            final FilterType type = optFilter.get ().getType ();
            if (type == FilterType.LOW_PASS || type == FilterType.HIGH_PASS)
                cutoffModulator = optFilter.get ().getCutoffLfoModulator ();
        }

        final boolean isPitchActive = pitchModulator.isActive ();
        final boolean isGainActive = gainModulator.isActive ();
        final boolean isCutoffActive = cutoffModulator != null && cutoffModulator.isActive ();
        final ILfoModulator mainModulator;
        if (isPitchActive)
            mainModulator = pitchModulator;
        else if (isGainActive)
            mainModulator = gainModulator;
        else if (isCutoffActive)
            mainModulator = cutoffModulator;
        else
        {
            createDefaultLfoModulators (document, slotElement);
            return;
        }
        if (mainModulator == null)
            return;

        final ILfo lfo = mainModulator.getSource ();
        paramsElement.setAttribute (Music1010Tag.ATTR_LFO_WAVE, Integer.toString (Music1010Lfo.fromWaveform (lfo.getWaveform ())));
        if (lfo.getRate () > 0)
            paramsElement.setAttribute (Music1010Tag.ATTR_LFO_RATE, Integer.toString (Music1010Lfo.hertzToRate (lfo.getRate ())));
        paramsElement.setAttribute (Music1010Tag.ATTR_LFO_KEY_TRIGGER, lfo.isKeySync () ? "1" : "0");
        paramsElement.setAttribute (Music1010Tag.ATTR_LFO_BEAT_SYNC, "0");

        // Split the depth of the main modulator into the part which is always active and the part
        // which is added by the modulation wheel. The routing amounts are set to the maximum
        // depth (modulation wheel fully up).
        final double mainScale = getLfoScale (mainModulator, pitchModulator, gainModulator);
        final double mainDepth = mainModulator.getDepth () / mainScale;
        final double mainModWheelDepth = mainModulator.getDepthModWheelModulator ().getDepth () / mainScale;
        final double mainTotal = mainDepth + mainModWheelDepth;
        double lfoAmount = 1;
        double modWheelAmount = 0;
        if (mainTotal != 0 && Math.signum (mainDepth) * Math.signum (mainModWheelDepth) >= 0)
        {
            lfoAmount = mainDepth / mainTotal;
            modWheelAmount = mainModWheelDepth / mainTotal;
        }
        paramsElement.setAttribute (Music1010Tag.ATTR_LFO_AMOUNT, Integer.toString ((int) Math.round (Math.clamp (lfoAmount, 0, 1) * 1000.0)));
        if (modWheelAmount > 0)
            createModulator (document, slotElement, Music1010Lfo.SOURCE_MOD_WHEEL, Music1010Lfo.DESTINATION_LFO, (int) Math.round (Math.clamp (modWheelAmount, 0, 1) * 1000.0));

        final boolean includeModWheel = modWheelAmount > 0;
        if (isPitchActive)
            createLfoModulator (document, slotElement, Music1010Lfo.DESTINATION_PITCH, pitchModulator, Music1010Lfo.PITCH_SCALE, includeModWheel);
        if (isGainActive)
            createLfoModulator (document, slotElement, Music1010Lfo.DESTINATION_GAIN, gainModulator, Music1010Lfo.VOLUME_SCALE, includeModWheel);
        if (isCutoffActive)
            createLfoModulator (document, slotElement, Music1010Lfo.DESTINATION_CUTOFF, cutoffModulator, Music1010Lfo.CUTOFF_SCALE, includeModWheel);
    }


    /**
     * Get the scaling factor of a modulator.
     *
     * @param modulator The modulator
     * @param pitchModulator The pitch modulator
     * @param gainModulator The volume modulator
     * @return The scaling factor
     */
    private static double getLfoScale (final ILfoModulator modulator, final ILfoModulator pitchModulator, final ILfoModulator gainModulator)
    {
        if (modulator == pitchModulator)
            return Music1010Lfo.PITCH_SCALE;
        if (modulator == gainModulator)
            return Music1010Lfo.VOLUME_SCALE;
        return Music1010Lfo.CUTOFF_SCALE;
    }


    /**
     * Create a modulation matrix entry for a routing of the low frequency oscillator.
     *
     * @param document The XML document
     * @param slotElement The element to which to add the modulation matrix entry
     * @param destination The modulation destination
     * @param modulator The modulator
     * @param scale The scaling factor of the destination
     * @param includeModWheel If true the depth of the modulation wheel is added since the routing
     *            amount needs to represent the maximum depth
     */
    private static void createLfoModulator (final Document document, final Element slotElement, final String destination, final ILfoModulator modulator, final double scale, final boolean includeModWheel)
    {
        double depth = modulator.getDepth ();
        if (includeModWheel)
            depth += modulator.getDepthModWheelModulator ().getDepth ();
        final int amount = (int) Math.round (Math.clamp (depth / scale, -1.0, 1.0) * 1000.0);
        if (amount != 0)
            createModulator (document, slotElement, Music1010Lfo.SOURCE_LFO1, destination, amount);
    }


    /**
     * Creates the velocity modulation matrix entries for the volume and the filter cutoff.
     *
     * @param document The XML document
     * @param slotElement The element to which to add the modulation matrix entries
     * @param multisampleSource The multi-sample
     */
    protected static void createVelocityModulators (final Document document, final Element slotElement, final IMultisampleSource multisampleSource)
    {
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


    /**
     * Create a modulation matrix entry. Each destination can be modulated by up to 3 sources, which
     * are distinguished by the slot attribute. Entries with an amount of 0 are not created to keep
     * the slots free.
     *
     * @param document The XML document
     * @param firstSlot The element to which to add the modulation matrix entry
     * @param source The modulation source
     * @param destination The modulation destination
     * @param modAmount The modulation amount in the range of [-1000..1000]
     */
    private static void createModulator (final Document document, final Element firstSlot, final String source, final String destination, final int modAmount)
    {
        if (modAmount == 0)
            return;

        int slot = 0;
        for (final Element element: XMLUtils.getChildElementsByName (firstSlot, Music1010Tag.MOD_SOURCE))
            if (destination.equals (element.getAttribute (Music1010Tag.ATTR_MOD_DESTINATION)))
                slot++;
        if (slot >= MAX_MODULATION_SLOTS)
            return;

        final Element modSourceElement = XMLUtils.addElement (document, firstSlot, Music1010Tag.MOD_SOURCE);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_DESTINATION, destination);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SOURCE, source);
        modSourceElement.setAttribute (Music1010Tag.ATTR_MOD_SLOT, Integer.toString (slot));
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
     * @param version The version of the session format
     * @return The document and the session element
     */
    protected Optional<Pair<Document, Element>> createSessionDocument (final int version)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement (Music1010Tag.ROOT);
        document.appendChild (rootElement);
        final Element sessionElement = XMLUtils.addElement (document, rootElement, Music1010Tag.SESSION);
        sessionElement.setAttribute (Music1010Tag.ATTR_VERSION, Integer.toString (version));

        return Optional.of (new Pair<> (document, sessionElement));
    }


    /**
     * Test if all sample zones of all groups are one-shots, which means that a note-off is ignored
     * and the sample is always played to its end.
     *
     * @param groups The groups which contain the sample zones to test
     * @return True if there is at least one sample zone and all of them are one-shots
     */
    protected static boolean isOneShot (final List<IGroup> groups)
    {
        boolean hasZones = false;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                if (!zone.isOneShot ())
                    return false;
                hasZones = true;
            }
        return hasZones;
    }


    /**
     * Get the exclusive group which is shared by all sample zones of all groups. The choke group is
     * only available for the full instrument, therefore it can only be stored if all sample zones
     * agree on it.
     *
     * @param groups The groups which contain the sample zones to test
     * @return The common exclusive group, 0 if there is none or if the sample zones do not agree
     */
    protected static int getCommonExclusiveGroup (final List<IGroup> groups)
    {
        boolean hasZones = false;
        int commonExclusiveGroup = 0;
        for (final IGroup group: groups)
            for (final ISampleZone zone: group.getSampleZones ())
            {
                final int exclusiveGroup = Math.max (0, zone.getExclusiveGroup ());
                if (hasZones && commonExclusiveGroup != exclusiveGroup)
                    return 0;
                commonExclusiveGroup = exclusiveGroup;
                hasZones = true;
            }
        return commonExclusiveGroup;
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
