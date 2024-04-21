// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.format.nki.AbstractNKIMetadataFileHandler;
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
    protected String getModulationTarget (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return null;
        for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
            for (final Element valueElement: XMLUtils.getChildElementsByName (targetElement, this.tags.value (), false))
                // We only support 1 target!
                if (this.tags.targetParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
                    return valueElement.getAttribute (this.tags.valueValueAttribute ());
        return null;
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
                    catch (final NumberFormatException ex)
                    {
                        return 0;
                    }
                }
        return 0;
    }


    /** {@inheritDoc} */
    @Override
    protected String readPitchBendIntensity (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return null;

        final List<Element> targetElements = XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false);
        if (targetElements.isEmpty ())
            return null;

        final Element targetElement = this.findElementWithParameters (targetsElement, K2Tag.K2_TARGET_ELEMENT, this.tags.targetParam (), this.tags.pitchValue ());
        if (targetElement == null)
            return null;

        final Map<String, String> targetElementParams = this.readValueMap (targetElement);
        return targetElementParams.get (this.tags.intensityParam ());
    }


    /** {@inheritDoc} */
    @Override
    protected TriggerType getTriggerTypeFromGroupElement (final Map<String, String> groupParameters)
    {
        final String releaseTrigParam = groupParameters.get (K2Tag.K2_RELEASE_TRIGGER_PARAM);
        return releaseTrigParam != null && releaseTrigParam.equals (this.tags.yes ()) ? TriggerType.RELEASE : TriggerType.ATTACK;
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
