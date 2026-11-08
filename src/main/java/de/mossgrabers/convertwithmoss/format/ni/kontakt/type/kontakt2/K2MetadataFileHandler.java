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

import de.mossgrabers.convertwithmoss.core.INotifier;
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
}
