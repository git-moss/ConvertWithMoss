// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.ni.kontakt.type.kontakt2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.IInstrumentSource;
import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.IMetadata;
import de.mossgrabers.convertwithmoss.format.ni.kontakt.AbstractNKIMetadataFileHandler;
import de.mossgrabers.tools.XMLUtils;


/**
 * Parses a NKI XML file in K2 format.
 *
 * @author Jürgen Moßgraber
 * @author Philip Stolz
 */
public class K2MetadataFileHandler extends AbstractNKIMetadataFileHandler
{
    /**
     * Constructor.
     *
     * @param notifier Where to report errors
     */
    public K2MetadataFileHandler (final INotifier notifier)
    {
        super (new K2Tag (), notifier);
    }


    /** {@inheritDoc} */
    @Override
    protected List<Element> findProgramElements (final Element top)
    {
        final List<Element> rootContainers;
        if (this.tags.rootContainer ().equals (top.getNodeName ()))
            rootContainers = Collections.singletonList (top);
        else
            rootContainers = XMLUtils.getChildElementsByName (top, this.tags.rootContainer (), true);

        final List<Element> programElements = new ArrayList<> ();
        for (final Element rootContainer: rootContainers)
        {
            final Element programsElement = XMLUtils.getChildElementByName (rootContainer, K2Tag.K2_PROGRAMS);
            if (programsElement != null)
                programElements.addAll (XMLUtils.getChildElementsByName (programsElement, this.tags.program ()));
        }
        return programElements;
    }


    /** {@inheritDoc} */
    @Override
    protected Optional<String> getModulationTarget (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return Optional.empty ();
        for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
            for (final Element valueElement: XMLUtils.getChildElementsByName (targetElement, this.tags.value (), false))
                // We only support 1 target!
                if (this.tags.targetParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
                    return Optional.of (valueElement.getAttribute (this.tags.valueValueAttribute ()));
        return Optional.empty ();
    }


    /** {@inheritDoc} */
    @Override
    protected double getModulationIntensity (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return 0;
        for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
            for (final Element valueElement: XMLUtils.getChildElementsByName (targetElement, this.tags.value (), false))
                // We only support 1 target!
                if (this.tags.intensityParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
                {
                    final String attribute = valueElement.getAttribute (this.tags.valueValueAttribute ());
                    try
                    {
                        return attribute == null ? 0 : Double.parseDouble (attribute);
                    }
                    catch (final NumberFormatException _)
                    {
                        return 0;
                    }
                }
        return 0;
    }


    /** {@inheritDoc} */
    @Override
    protected int getModulationTargetIndex (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return -1;
        for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
        {
            // We only support 1 target!
            final Map<String, String> targetParameters = this.readValueMap (targetElement);
            if (targetParameters.containsKey (this.tags.targetParam ()))
                return parseIndex (targetParameters.get (this.tags.targetObjectIndexParam ()));
        }
        return -1;
    }


    /** {@inheritDoc} */
    @Override
    protected boolean isAdditiveTuning (final Element programElement)
    {
        // Kontakt 2 (program version 0.60) stores the program and group tuning as a frequency ratio
        // (1 is neutral) but Kontakt 3 and 4.1 (program version 0.80) store it as an offset (0 is
        // neutral). The zone tuning is always a frequency ratio
        try
        {
            return Double.parseDouble (programElement.getAttribute (this.tags.versionAttribute ())) >= 0.8;
        }
        catch (final NumberFormatException _)
        {
            return false;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected boolean isValidTopLevelElement (final Element top)
    {
        return this.tags.rootContainer ().equals (top.getNodeName ()) || K2Tag.K2_BANK_ELEMENT.equals (top.getNodeName ());
    }


    /** {@inheritDoc} */
    @Override
    protected double normalizePanning (final double panningValue)
    {
        // Doesn't need to be normalized for K2 format
        return panningValue;
    }


    /** {@inheritDoc} */
    @Override
    protected String getTemplatePrefix ()
    {
        return "Kontakt2";
    }


    /** {@inheritDoc} */
    @Override
    protected String replaceMetadataPlaceholders (final String text, final IMetadata metadata)
    {
        final String [] keywords = metadata.getKeywords ();
        return text.replace ("%PROGRAM_CATEGORY_1%", Integer.toString (Kontakt2Header.getCategory1Index (metadata.getCategory ()))).replace ("%PROGRAM_CATEGORY_2%", Integer.toString (Kontakt2Header.getCategory2Index (keywords))).replace ("%PROGRAM_CATEGORY_3%", Integer.toString (Kontakt2Header.getCategory3Index (keywords)));
    }


    /**
     * {@inheritDoc}
     *
     * Kontakt 2 stores the parameters of all 64 slots in the bank. The MIDI channel of an empty
     * slot is its number.
     */
    @Override
    protected String createBankSlotParameters (final List<IInstrumentSource> instrumentSources)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < 64; i++)
        {
            final int midiChannel = i < instrumentSources.size () ? toKontaktMidiChannel (instrumentSources.get (i)) : i + 1;
            final String slot = String.format ("%03d", Integer.valueOf (i));
            appendValue (sb, "midiChannel_slot" + slot, Integer.toString (midiChannel));
            appendValue (sb, "solo_slot" + slot, "no");
            appendValue (sb, "mute_slot" + slot, "no");
            for (int aux = 0; aux < 4; aux++)
                appendValue (sb, "auxSendLevel" + aux + "_slot" + slot, "0");
            appendValue (sb, "reorderIdx_" + slot, Integer.toString (i % 16));
        }
        return sb.toString ();
    }


    private static void appendValue (final StringBuilder sb, final String name, final String value)
    {
        if (!sb.isEmpty ())
            sb.append ("\r\n");
        sb.append ("    <V name=\"").append (name).append ("\" value=\"").append (value).append ("\"/>");
    }


    /**
     * {@inheritDoc}
     *
     * Kontakt 2 encodes the path: it starts with '@', each folder is stored as 'd' followed by the
     * 3-digit length of its name and the name. The file name is stored as 'F' followed by 11
     * characters ('-0001', the 3-digit length of the name and '000') and the name.
     */
    @Override
    protected String formatSampleFileName (final String safeSampleFolderName, final String sampleFileName)
    {
        final StringBuilder sb = new StringBuilder ("@");
        for (final String folder: safeSampleFolderName.replace ('\\', '/').split ("/"))
            if (!folder.isEmpty ())
            {
                final String encodedFolder = encodeCharacters (folder);
                sb.append ('d').append (String.format ("%03d", Integer.valueOf (encodedFolder.length ()))).append (encodedFolder);
            }
        final String encodedFileName = encodeCharacters (sampleFileName);
        sb.append ("F-0001").append (String.format ("%03d", Integer.valueOf (encodedFileName.length ()))).append ("000").append (encodedFileName);
        return sb.toString ();
    }


    /**
     * Encodes all non-ASCII characters as '%' followed by the 4-digit hexadecimal code.
     *
     * @param text The text to encode
     * @return The encoded text
     */
    private static String encodeCharacters (final String text)
    {
        final StringBuilder sb = new StringBuilder ();
        for (int i = 0; i < text.length (); i++)
        {
            final char c = text.charAt (i);
            if (c < 0x20 || c > 0x7E || c == '%')
                sb.append (String.format ("%%%04x", Integer.valueOf (c)));
            else
                sb.append (c);
        }
        return sb.toString ();
    }
}
