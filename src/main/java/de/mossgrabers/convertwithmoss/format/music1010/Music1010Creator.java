// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.music1010;

import de.mossgrabers.convertwithmoss.core.IMultisampleSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.creator.AbstractCreator;
import de.mossgrabers.convertwithmoss.core.model.IGroup;
import de.mossgrabers.convertwithmoss.core.model.ISampleZone;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.BasicConfig;
import de.mossgrabers.tools.ui.panel.BoxPanel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;


/**
 * Creator for preset files of the 1010music blackbox and nanobox tangerine. A preset has a
 * description file encoded in XML located in the Presets folder. The related samples are in a
 * separate folder.
 *
 * @author Jürgen Moßgraber
 */
public class Music1010Creator extends AbstractCreator
{
    private static final String              MUSIC_1010_INTERPOLATION_QUALITY = "Music1010InterpolationQuality";

    private ToggleGroup                      interpolationQualityGroup;
    private boolean                          isInterpolationQualityHigh;

    private static final Map<String, String> DEFAULT_PARAM_ATTRIBUTES         = new HashMap<> ();
    private static final Map<String, String> PARAM_ATTRIBUTES                 = new HashMap<> ();
    static
    {
        DEFAULT_PARAM_ATTRIBUTES.put ("gaindb", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("loopmode", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("loopmodes", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("reverse", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("cellmode", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("envattack", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("envdecay", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("envsus", "1000");
        DEFAULT_PARAM_ATTRIBUTES.put ("envrel", "200");
        DEFAULT_PARAM_ATTRIBUTES.put ("velamount", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("samstart", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("samlen", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("loopstart", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("loopend", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("quantsize", "3");
        DEFAULT_PARAM_ATTRIBUTES.put ("synctype", "5");
        DEFAULT_PARAM_ATTRIBUTES.put ("slicersync", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("slicestepmode", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("beatcount", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("fx1send", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("fx2send", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("playthru", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("slicerquantsize", "13");
        DEFAULT_PARAM_ATTRIBUTES.put ("deftemplate", "1");
        DEFAULT_PARAM_ATTRIBUTES.put ("recpresetlen", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("recquant", "3");
        DEFAULT_PARAM_ATTRIBUTES.put ("recinput", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("recusethres", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("recthresh", "-20000");
        DEFAULT_PARAM_ATTRIBUTES.put ("recmonoutbus", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("mute", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("pitch", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("dualfilcutoff", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("res", "500");
        DEFAULT_PARAM_ATTRIBUTES.put ("panpos", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("samtrigtype", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("polymode", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("polymodeslice", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("okegrp", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("midimode", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("padnote", "0");
        DEFAULT_PARAM_ATTRIBUTES.put ("rootnote", "0");

        PARAM_ATTRIBUTES.put ("gaindb", "0");
        PARAM_ATTRIBUTES.put ("pitch", "0");
        PARAM_ATTRIBUTES.put ("panpos", "0");
        PARAM_ATTRIBUTES.put ("samtrigtype", "1");
        PARAM_ATTRIBUTES.put ("loopmode", "0");
        PARAM_ATTRIBUTES.put ("loopmodes", "0");
        PARAM_ATTRIBUTES.put ("midimode", "1");
        PARAM_ATTRIBUTES.put ("midioutchan", "0");
        PARAM_ATTRIBUTES.put ("reverse", "0");
        PARAM_ATTRIBUTES.put ("cellmode", "0");
        PARAM_ATTRIBUTES.put ("envattack", "0");
        PARAM_ATTRIBUTES.put ("envdecay", "0");
        PARAM_ATTRIBUTES.put ("envsus", "1000");
        PARAM_ATTRIBUTES.put ("envrel", "103");
        PARAM_ATTRIBUTES.put ("samstart", "0");
        PARAM_ATTRIBUTES.put ("samlen", "1425505");
        PARAM_ATTRIBUTES.put ("loopstart", "0");
        PARAM_ATTRIBUTES.put ("loopend", "1425505");
        PARAM_ATTRIBUTES.put ("quantsize", "3");
        PARAM_ATTRIBUTES.put ("synctype", "5");
        PARAM_ATTRIBUTES.put ("actslice", "1");
        PARAM_ATTRIBUTES.put ("outputbus", "0");
        PARAM_ATTRIBUTES.put ("polymode", "5");
        PARAM_ATTRIBUTES.put ("slicestepmode", "0");
        PARAM_ATTRIBUTES.put ("chokegrp", "0");
        PARAM_ATTRIBUTES.put ("dualfilcutoff", "0");
        PARAM_ATTRIBUTES.put ("rootnote", "0");
        PARAM_ATTRIBUTES.put ("beatcount", "0");
        PARAM_ATTRIBUTES.put ("fx1send", "0");
        PARAM_ATTRIBUTES.put ("fx2send", "0");
        PARAM_ATTRIBUTES.put ("multisammode", "1");
        PARAM_ATTRIBUTES.put ("playthru", "0");
        PARAM_ATTRIBUTES.put ("slicerquantsize", "13");
        PARAM_ATTRIBUTES.put ("slicersync", "0");
        PARAM_ATTRIBUTES.put ("padnote", "0");
        PARAM_ATTRIBUTES.put ("loopfadeamt", "0");
        PARAM_ATTRIBUTES.put ("grainsize", "0");
        PARAM_ATTRIBUTES.put ("graincount", "3");
        PARAM_ATTRIBUTES.put ("gainspreadten", "0");
        PARAM_ATTRIBUTES.put ("grainreadspeed", "1000");
        PARAM_ATTRIBUTES.put ("recpresetlen", "0");
        PARAM_ATTRIBUTES.put ("recquant", "3");
        PARAM_ATTRIBUTES.put ("recinput", "0");
        PARAM_ATTRIBUTES.put ("recusethres", "0");
        PARAM_ATTRIBUTES.put ("recthresh", "-20000");
        PARAM_ATTRIBUTES.put ("recmonoutbus", "0");
    }


    /**
     * Constructor.
     *
     * @param notifier The notifier
     */
    public Music1010Creator (final INotifier notifier)
    {
        super ("1010 Music", notifier);
    }


    /** {@inheritDoc} */
    @Override
    public Node getEditPane ()
    {
        final BoxPanel panel = new BoxPanel (Orientation.VERTICAL);

        panel.createSeparator ("@IDS_1010_MUSIC_INTER_QUALITY");

        this.interpolationQualityGroup = new ToggleGroup ();
        final RadioButton order1 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_NORMAL");
        order1.setToggleGroup (this.interpolationQualityGroup);
        final RadioButton order2 = panel.createRadioButton ("@IDS_1010_MUSIC_INTERPOLATION_QUALITY_HIGH");
        order2.setToggleGroup (this.interpolationQualityGroup);

        return panel.getPane ();
    }


    /** {@inheritDoc} */
    @Override
    public void loadSettings (final BasicConfig config)
    {
        this.interpolationQualityGroup.selectToggle (this.interpolationQualityGroup.getToggles ().get (config.getBoolean (MUSIC_1010_INTERPOLATION_QUALITY, false) ? 1 : 0));
    }


    /** {@inheritDoc} */
    @Override
    public void saveSettings (final BasicConfig config)
    {
        config.setBoolean (MUSIC_1010_INTERPOLATION_QUALITY, this.isHighInterpolationQuality ());
    }


    /** {@inheritDoc} */
    @Override
    public void create (final File destinationFolder, final IMultisampleSource multisampleSource) throws IOException
    {
        this.setInterpolationQuality (this.isHighInterpolationQuality ());

        final String sampleName = createSafeFilename (multisampleSource.getName ());
        final File presetFolder = new File (destinationFolder, sampleName);
        if (!presetFolder.mkdir ())
            this.notifier.logError ("IDS_NOTIFY_FOLDER_COULD_NOT_BE_CREATED", presetFolder.getAbsolutePath ());

        final File multiFile = new File (presetFolder, "preset.xml");
        if (multiFile.exists ())
        {
            this.notifier.logError ("IDS_NOTIFY_ALREADY_EXISTS", multiFile.getAbsolutePath ());
            return;
        }

        final Optional<String> metadata = this.createMetadata (sampleName, multisampleSource);
        if (metadata.isEmpty ())
            return;

        this.notifier.log ("IDS_NOTIFY_STORING", multiFile.getAbsolutePath ());

        this.storePreset (presetFolder, multisampleSource, multiFile, metadata.get ());

        this.notifier.log ("IDS_NOTIFY_PROGRESS_DONE");
    }


    /**
     * Create a dspreset file.
     *
     * @param destinationFolder Where to store the preset file
     * @param multisampleSource The multi sample to store in the library
     * @param multiFile The file of the dslibrary
     * @param metadata The dspreset metadata description file
     * @throws IOException Could not store the file
     */
    private void storePreset (final File destinationFolder, final IMultisampleSource multisampleSource, final File multiFile, final String metadata) throws IOException
    {
        try (final FileWriter writer = new FileWriter (multiFile, StandardCharsets.UTF_8))
        {
            writer.write (metadata);
        }

        // Store all samples
        this.writeSamples (destinationFolder, multisampleSource);
    }


    /**
     * Create the text of the description file.
     *
     * @param folderName The name to use for the sample folder
     * @param multisampleSource The multi-sample
     * @return The XML structure
     */
    private Optional<String> createMetadata (final String folderName, final IMultisampleSource multisampleSource)
    {
        final Optional<Document> optionalDocument = this.createXMLDocument ();
        if (optionalDocument.isEmpty ())
            return Optional.empty ();
        final Document document = optionalDocument.get ();
        document.setXmlStandalone (true);

        final Element rootElement = document.createElement (Music1010Tag.ROOT);
        document.appendChild (rootElement);
        final Element sessionElement = XMLUtils.addElement (document, rootElement, Music1010Tag.SESSION);
        rootElement.appendChild (sessionElement);
        sessionElement.setAttribute (Music1010Tag.ATTR_VERSION, "2");

        // No metadata at all

        final List<IGroup> groups = multisampleSource.getNonEmptyGroups (false);

        // Create 16 slot cells, multi-sample goes into slot 1
        final Element firstSlot = this.createSlots (document, sessionElement);
        final String presetPath = "\\Presets\\" + multisampleSource.getName ();
        firstSlot.setAttribute (Music1010Tag.ATTR_FILENAME, presetPath);

        // Add all groups
        int sampleIndex = 0;
        for (final IGroup group: groups)
        {
            // No group support

            for (final ISampleZone sample: group.getSampleZones ())
            {
                createSample (document, folderName, presetPath, sessionElement, sample, sampleIndex);
                sampleIndex++;
            }
        }

        this.createEffects (document, rootElement, multisampleSource);

        return this.createXMLString (document);
    }


    /**
     * Create 16 slot cells, multi-sample goes into slot 1.
     *
     * @param document The document
     * @param sessionElement The session element to which to add the slots
     * @return The first slot element
     */
    private Element createSlots (final Document document, final Element sessionElement)
    {
        Element firstElement = null;
        for (int row = 0; row < 4; row++)
        {
            for (int column = 0; column < 4; column++)
            {
                final boolean isFirst = row == 0 && column == 0;

                final Element cellElement = XMLUtils.addElement (document, sessionElement, Music1010Tag.CELL);
                cellElement.setAttribute (Music1010Tag.ATTR_ROW, Integer.toString (row));
                cellElement.setAttribute (Music1010Tag.ATTR_COLUMN, Integer.toString (column));
                cellElement.setAttribute (Music1010Tag.ATTR_LAYER, Integer.toString (0));
                cellElement.setAttribute (Music1010Tag.ATTR_FILENAME, "");

                final Element paramsElement = XMLUtils.addElement (document, cellElement, Music1010Tag.PARAMS);
                final Set<Entry<String, String>> entrySet;
                if (isFirst)
                {
                    firstElement = cellElement;
                    cellElement.setAttribute (Music1010Tag.ATTR_TYPE, "sample");
                    entrySet = PARAM_ATTRIBUTES.entrySet ();
                }
                else
                {
                    cellElement.setAttribute (Music1010Tag.ATTR_TYPE, "samtempl");
                    entrySet = DEFAULT_PARAM_ATTRIBUTES.entrySet ();
                }
                for (final Map.Entry<String, String> entry: entrySet)
                    paramsElement.setAttribute (entry.getKey (), entry.getValue ());

                paramsElement.setAttribute (Music1010Tag.ATTR_INTERPOLATION_QUALITY, this.isInterpolationQualityHigh ? "1" : "0");
            }
        }
        return firstElement;
    }


    /**
     * Creates the metadata for one sample.
     *
     * @param document The XML document
     * @param folderName The name to use for the sample folder
     * @param presetPath The offset path to use
     * @param groupElement The element where to add the sample information
     * @param zone Where to get the sample info from
     * @param sampleIndex The index of the sample
     */
    private static void createSample (final Document document, final String folderName, final String presetPath, final Element groupElement, final ISampleZone zone, final int sampleIndex)
    {
        /////////////////////////////////////////////////////
        // Sample element and attributes

        final Element cellElement = XMLUtils.addElement (document, groupElement, Music1010Tag.CELL);
        final String filename = zone.getName () + ".wav";
        cellElement.setAttribute (Music1010Tag.ATTR_FILENAME, ".\\" + filename);
        cellElement.setAttribute (Music1010Tag.ATTR_ROW, Integer.toString (sampleIndex));
        cellElement.setAttribute (Music1010Tag.ATTR_TYPE, "asset");

        final Element paramsElement = XMLUtils.addElement (document, cellElement, Music1010Tag.PARAMS);
        paramsElement.setAttribute (Music1010Tag.ATTR_ASSET_SOURCE_ROW, "0");
        paramsElement.setAttribute (Music1010Tag.ATTR_ASSET_SOURCE_COLUMN, "0");

        // No zone.getGain ();
        // No zone.getStop ();
        // No zone.getTune ();
        // No zone.getTrigger ();
        // No info.isReversed ()

        /////////////////////////////////////////////////////
        // Key & Velocity attributes

        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_NOTE, check (zone.getKeyLow (), 0));
        // No fades info.getNoteCrossfadeLow ()
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_ROOT_NOTE, zone.getKeyRoot ());
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_NOTE, check (zone.getKeyHigh (), 127));
        // No fades info.getNoteCrossfadeHigh ()
        // No zone.getKeyTracking ()
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_LO_VEL, check (zone.getVelocityLow (), 0));
        // No fades info.getVelocityCrossfadeLow ()
        XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.ATTR_HI_VEL, check (zone.getVelocityHigh (), 127));
        // No fades info.getVelocityCrossfadeHigh ()

        /////////////////////////////////////////////////////
        // Loops

        // TODO Clarify with 1010music

        // final List<ISampleLoop> loops = zone.getLoops ();
        // if (!loops.isEmpty ())
        // {
        //
        // final ISampleLoop sampleLoop = loops.get (0);
        // paramsElement.setAttribute (Music1010Tag.LOOP_ENABLED, "true");
        // XMLUtils.setDoubleAttribute (paramsElement, Music1010Tag.LOOP_START, check
        // (sampleLoop.getStart (), 0), 3);
        // XMLUtils.setDoubleAttribute (paramsElement, Music1010Tag.LOOP_END, check
        // (sampleLoop.getEnd (), stop), 3);
        //
        // // Calculate the crossfade in frames/samples from a percentage of the loop length
        // final double crossfade = sampleLoop.getCrossfade ();
        // if (crossfade > 0)
        // {
        // final int loopLength = sampleLoop.getStart () - sampleLoop.getEnd ();
        // if (loopLength > 0)
        // XMLUtils.setIntegerAttribute (paramsElement, Music1010Tag.LOOP_CROSSFADE, (int)
        // Math.round (loopLength * crossfade));
        // }
        // }
    }


    /**
     * Set the interpolation quality to high or normal.
     *
     * @param isInterpolationQualityHigh True to set the interpolation quality to high
     */
    public void setInterpolationQuality (final boolean isInterpolationQualityHigh)
    {
        this.isInterpolationQualityHigh = isInterpolationQualityHigh;
    }


    /**
     * Check if the toggle setting is set to high interpolation quality.
     *
     * @return True if high quality
     */
    private boolean isHighInterpolationQuality ()
    {
        return this.interpolationQualityGroup.getToggles ().get (1).isSelected ();
    }


    /**
     * Creates the filter and reverb effect elements.
     *
     * @param document The XML document
     * @param rootElement Where to add the effect elements
     * @param multisampleSource The multi-sample
     */
    private void createEffects (final Document document, final Element rootElement, final IMultisampleSource multisampleSource)
    {
        // TODO set filter

        // <cell row="0" layer="3" type="delay">
        // <params delaymustime="6" feedback="400" dealybeatsync="1" delay="400"/>
        // </cell>
        // <cell row="1" layer="3" type="reverb">
        // <params decay="600" predelay="40" damping="500"/>
        // </cell>
        // <cell row="2" layer="3" type="filter">
        // <params cutoff="600" res="400" filtertype="0" fxtrigmode="0"/>
        // </cell>
        // <cell row="3" layer="3" type="bitcrusher">
        // <params/>
        // </cell>

        // final Optional<IFilter> optFilter = multisampleSource.getGlobalFilter ();
        //
        // final boolean lowPassFilterIsPresent = optFilter.isPresent () && optFilter.get ().getType
        // () == FilterType.LOW_PASS;
        // final boolean hasFilter = this.addFilterBox.isSelected () || lowPassFilterIsPresent;
        // final boolean hasReverb = this.addReverbBox.isSelected ();
        //
        // if (hasFilter || hasReverb)
        // {
        // final Element effectsElement = XMLUtils.addElement (document, rootElement,
        // Music1010Tag.EFFECTS);
        //
        // if (hasFilter)
        // {
        // final Element filterElement = XMLUtils.addElement (document, effectsElement,
        // Music1010Tag.EFFECTS_EFFECT);
        // filterElement.setAttribute ("type", "lowpass_4pl");
        // if (lowPassFilterIsPresent)
        // {
        // // Note: this might not be a 4 pole low-pass but better than no filter...
        // final IFilter filter = optFilter.get ();
        // // Note: Resonance is in the range [0..1] but it is not documented what value 1
        // // represents. Therefore, we assume 40dB maximum and a linear range (could also
        // // be logarithmic).
        // final double resonance = Math.min (40, filter.getResonance ());
        // filterElement.setAttribute ("resonance", formatDouble (resonance / 40.0, 3));
        // filterElement.setAttribute ("frequency", formatDouble (filter.getCutoff (), 2));
        // }
        // else
        // {
        // filterElement.setAttribute ("resonance", "0.5");
        // filterElement.setAttribute ("frequency", "22000");
        // }
        // }
        //
        // if (hasReverb)
        // {
        // final Element reverbElement = XMLUtils.addElement (document, effectsElement,
        // Music1010Tag.EFFECTS_EFFECT);
        // reverbElement.setAttribute ("type", "reverb");
        // }
        // }
    }
}