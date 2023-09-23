// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.nki.type.kontakt2;

import de.mossgrabers.convertwithmoss.core.INotifier;
import de.mossgrabers.convertwithmoss.core.model.enumeration.TriggerType;
import de.mossgrabers.convertwithmoss.format.nki.AbstractNKIMetadataFileHandler;
import de.mossgrabers.convertwithmoss.format.nki.type.DecodedPath;
import de.mossgrabers.tools.XMLUtils;
import de.mossgrabers.tools.ui.Functions;

import org.w3c.dom.Element;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


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
    protected Element [] findProgramElements (final Element top)
    {
        final Element [] rootContainers;
        if (this.tags.rootContainer ().equals (top.getNodeName ()))
        {
            rootContainers = new Element [1];
            rootContainers[0] = top;
        }
        else
        {
            rootContainers = XMLUtils.getChildElementsByName (top, this.tags.rootContainer (), true);
            if (rootContainers == null)
                return new Element [0];
        }

        final List<Element> programElements = new ArrayList<> ();

        for (final Element rootContainer: rootContainers)
        {
            final Element programsElement = XMLUtils.getChildElementByName (rootContainer, K2Tag.K2_PROGRAMS);
            if (programsElement == null)
                return new Element [0];
            programElements.addAll (Arrays.asList (XMLUtils.getChildElementsByName (programsElement, this.tags.program (), false)));
        }

        return programElements.toArray (new Element [programElements.size ()]);
    }


    /** {@inheritDoc} */
    @Override
    protected String getModulationTarget (final Element modulator)
    {
        final Element targetsElement = XMLUtils.getChildElementByName (modulator, K2Tag.K2_TARGETS_ELEMENT);
        if (targetsElement == null)
            return null;
        for (final Element targetElement: XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false))
        {
            for (final Element valueElement: XMLUtils.getChildElementsByName (targetElement, this.tags.value (), false))
            {
                // We only support 1 target!
                if (this.tags.targetParam ().equals (valueElement.getAttribute (this.tags.valueNameAttribute ())))
                    return valueElement.getAttribute (this.tags.valueValueAttribute ());
            }
        }
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
        {
            for (final Element valueElement: XMLUtils.getChildElementsByName (targetElement, this.tags.value (), false))
            {
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

        final Element [] targetElements = XMLUtils.getChildElementsByName (targetsElement, K2Tag.K2_TARGET_ELEMENT, false);
        if (targetElements == null)
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
    protected DecodedPath decodeEncodedSampleFileName (final String encodedSampleFileName) throws IOException
    {
        final DecodedPath decodedPath = new DecodedPath ();
        final StringBuilder relativePath = new StringBuilder ();
        final char [] fractionBuffer = new char [11];
        final StringReader reader = new StringReader (encodedSampleFileName);
        int id;
        while ((id = reader.read ()) != -1)
        {
            switch (id)
            {
                // Indicator for relative path
                case '@':
                    break;

                case 'b':
                    relativePath.append ("../");
                    break;

                // A directory
                case 'd':
                    relativePath.append (readFolder (reader, encodedSampleFileName)).append ('/');
                    break;

                // The sample's file name
                case 'F':
                    if (reader.read (fractionBuffer) != fractionBuffer.length)
                        error (encodedSampleFileName);
                    int c;
                    while ((c = reader.read ()) != -1)
                        relativePath.append ((char) c);
                    decodedPath.setRelativePath (relativePath.toString ());
                    return decodedPath;

                // A NKS library
                case 'm':
                    relativePath.append (readFolder (reader, encodedSampleFileName));
                    decodedPath.setLibrary (relativePath.toString ());
                    relativePath.setLength (0);
                    break;

                default:
                    error (encodedSampleFileName);
            }
        }

        error (encodedSampleFileName);
        // Never reached
        return null;
    }


    private static void error (final String encodedSampleFileName) throws IOException
    {
        throw new IOException (Functions.getMessage ("IDS_NKI_UNEXPECTED_STATE", encodedSampleFileName));
    }


    private static String readFolder (final StringReader reader, final String encodedSampleFileName) throws IOException
    {
        final char [] lengthBuffer = new char [3];
        if (reader.read (lengthBuffer) != lengthBuffer.length)
            error (encodedSampleFileName);
        final int length = Integer.parseInt (new String (lengthBuffer));
        final char [] folder = new char [length];
        if (reader.read (folder) != length)
            error (encodedSampleFileName);
        return new String (folder);
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
